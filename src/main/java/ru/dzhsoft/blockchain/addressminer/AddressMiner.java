package ru.dzhsoft.blockchain.addressminer;

import org.bouncycastle.util.encoders.Hex;
import ru.dzhsoft.blockchain.addressminer.RulesConfig.RulesBlock;
import ru.dzhsoft.blockchain.addressminer.addrgen.AddressGenerator;
import ru.dzhsoft.blockchain.addressminer.addrgen.AddressHash160Generator;
import ru.dzhsoft.blockchain.addressminer.addrgen.ECPointData;
import ru.dzhsoft.blockchain.addressminer.addrgen.OptionalChecksumGenerator;
import ru.dzhsoft.blockchain.addressminer.util.FastRandom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.identityHashCode;
import static ru.dzhsoft.blockchain.addressminer.util.Helper.log;

public class AddressMiner {
	private final Lock sharedLock = new ReentrantLock();
	private final Condition shutdownCond = sharedLock.newCondition();

	private volatile boolean running;

	private final AtomicLong totalExponentsScanned = new AtomicLong(0);
	private final AtomicLong randomFillTimeNanos = new AtomicLong(0);
	private final AtomicLong ecPointEvaluatingTimeNanos = new AtomicLong(0);
	private final AtomicLong addressGenerationTimeNanos = new AtomicLong(0);
	private final AtomicLong regexMatchingTimeNanos = new AtomicLong(0);
	private final NanoTimeProvider nanoTimeProvider;

	private final FastRandom fastRandom;
	private final MinerSettings settings;
	private final RulesConfig rulesConfig;

	private final List<MinerWorker> minerWorkers = new ArrayList<>();
	private final StatLogger statLogger;

	public AddressMiner(MinerSettings settings, RulesConfig rulesConfig) {
		this.settings = settings;
		this.rulesConfig = rulesConfig;
		this.statLogger = settings.getStatFreqSec() > 0 ? new StatLogger("StatLogger") : null;
		this.nanoTimeProvider = settings.getStatFreqSec() > 0 ? System::nanoTime : () -> 0;
		this.fastRandom = new FastRandom("RandomSourceReader", settings.isReuseKeyData(),
				settings.getRandomSourceFilePath());
	}

	@FunctionalInterface
	private interface NanoTimeProvider {
		long getTimeNanos();
	}

	public void start() {
		running = true;

		// start RandomSourceReader of FastRandom (unless PRNG only is used)
		if (!settings.isUsePrng()) {
			log("INFO: start random source reader (read from: " + settings.getRandomSourceFilePath() + ")");
			fastRandom.start();
		}

		// start all address miner workers
		{
			final int workersCount = settings.getThreads();
			log("INFO: start " + workersCount + " parallel address miner workers");
			for (int i = 0; i < workersCount; i++) {
				minerWorkers.add(new MinerWorker(i));
			}
			minerWorkers.forEach(Thread::start);
		}

		// start statistics logger
		if (statLogger != null) {
			log("INFO: start statistics logger (every " + settings.getStatFreqSec() + " seconds)");
			statLogger.start();
		}
	}

	public void shutdown() {
		running = false;
		sharedLock.lock();
		shutdownCond.signalAll();
		sharedLock.unlock();
		fastRandom.shutdown();
	}

	public void join() throws InterruptedException {
		for (Thread worker : minerWorkers) {
			worker.join();
		}
		if (!settings.isUsePrng()) {
			fastRandom.join();
		}
		if (statLogger != null) {
			statLogger.join();
		}
	}

	private class MinerWorker extends Thread {
		private final byte[] exponent = new byte[32];
		private final ECPointData ecp = new ECPointData();

		public MinerWorker(int idx) {
			super("MinerWorker-" + (idx + 1));
		}

		@Override
		public void run() {
			final RulesBlock[] blocks = rulesConfig.getRulesBlocks().stream()
					.map(RulesBlock::copy) // will copy generators as well
					.toArray(RulesBlock[]::new);

			// prepare matchers for patterns (as plain array)
			final Matcher[] matchers;
			{
				final List<Matcher> matcherList = new ArrayList<>();
				for (RulesBlock block : blocks) {
					for (Pattern p : block.getRegexPatterns()) {
						matcherList.add(p.matcher(""));
					}
				}
				matchers = matcherList.toArray(new Matcher[0]);
			}

			// All generators are copies to avoid data messing, since they are thread unsafe,
			// but now each generator references to its own copy of hash160 generator,
			// which can be shared to reduce CPU usage. Just pick one instance of each type (class)
			// of hash160 generator and setup it to among all address generators having the corresponding type.
			{
				final Map<Class<?>, AddressHash160Generator> cls2hashGen = new HashMap<>();
				for (RulesBlock block : blocks) {
					for (AddressGenerator gen : block.getGenerators()) {
						final AddressHash160Generator origHashGen = gen.getAddressHash160Generator();
						cls2hashGen.putIfAbsent(origHashGen.getClass(), origHashGen);
						gen.setAddressHash160Generator(cls2hashGen.get(origHashGen.getClass()));
					}
				}
			}

			// show debug info
			if (settings.isDebugOutput()) {
				sharedLock.lock();
				try {
					for (RulesBlock block : blocks) {
						final StringBuilder message = new StringBuilder()
								.append("DEBUG: next configured block:\n");
						for (AddressGenerator gen : block.getGenerators()) {
							final AddressHash160Generator hashGen = gen.getAddressHash160Generator();
							message.append("\t[").append(gen.getCurrencyName()).append("]\n")
									.append("\t\tHash160Generator: identity = ")
									.append(String.format("0x%08X\n", identityHashCode(hashGen)))
									.append("\t\tAddressGenerator: identity = ")
									.append(String.format("0x%08X\n", identityHashCode(gen)));
						}
						message.append("\tPatterns:\n");
						for (Pattern pattern : block.getRegexPatterns()) {
							message.append("\t\tregex:").append(pattern).append("\n");
						}
						log(message.toString());
					}
				}
				finally {
					sharedLock.unlock();
				}
			}

			runMainLoop(blocks, matchers);
		}

		@SuppressWarnings("ForLoopReplaceableByForEach")
		private void runMainLoop(RulesBlock[] blocks, Matcher[] matchers) {
			final AtomicReference<CharSequence> finalAddressRef = new AtomicReference<>();
			long timerNanos;
			int subseqs = 0;
			boolean restartSubseq = true;
			while (running) {
				if (restartSubseq) {
					// fill next random exponent
					timerNanos = -nanoTimeProvider.getTimeNanos();
					fastRandom.fillFast(exponent);
					timerNanos += nanoTimeProvider.getTimeNanos();
					randomFillTimeNanos.getAndAdd(timerNanos);
					subseqs = 0;
				}
				else {
					// increment previous exponent
					increment(exponent);
					subseqs++;
				}

				// evaluate EC point for the exponent
				boolean ecUpdated;
				timerNanos = -nanoTimeProvider.getTimeNanos();
				if (restartSubseq) {
					// use new generated exponent (evaluate EC point from scratch)
					ecUpdated = ecp.update(exponent);
				}
				else {
					// use previous exponent incremented by 1
					ecUpdated = ecp.updateNextSubsequent();
				}
				timerNanos += nanoTimeProvider.getTimeNanos();
				ecPointEvaluatingTimeNanos.getAndAdd(timerNanos);

				// always restart with new exponent when failed
				if (!ecUpdated) {
					restartSubseq = true;
					continue;
				}

				// restart when max is reached
				restartSubseq = (subseqs >= settings.getSubSeqLen());

				// generate addresses & check for matching for specified patterns
				int matcherIdx = 0; // matcher index
				for (RulesBlock block : blocks) {
					final List<AddressGenerator> generators = block.getGenerators();
					final List<Pattern> patterns = block.getRegexPatterns();
					for (int genIdx = 0; genIdx < generators.size(); genIdx++) {
						final AddressGenerator generator = generators.get(genIdx);

						// generate address
						timerNanos = -nanoTimeProvider.getTimeNanos();
						final CharSequence address = generator.generateAddress(ecp);
						timerNanos += nanoTimeProvider.getTimeNanos();
						addressGenerationTimeNanos.getAndAdd(timerNanos);

						// check matching for specifed patterns
						for (int pIdx = 0, mIdx = matcherIdx; pIdx < patterns.size(); pIdx++, mIdx++) {
							timerNanos = -nanoTimeProvider.getTimeNanos();
							final Matcher m = matchers[mIdx].reset(address);
							final boolean found = m.find();
							timerNanos += nanoTimeProvider.getTimeNanos();
							regexMatchingTimeNanos.getAndAdd(timerNanos);

							// check result & output matched value
							if (!found) {
								continue;
							}

							// recheck with checksum if necessary
							finalAddressRef.set(address);
							if (generator instanceof OptionalChecksumGenerator<?>
									&& !((OptionalChecksumGenerator<?>) generator).isWithCheckSum()
									&& !recheckMatchWithCheckSum(generator, m, finalAddressRef)) {
								continue;
							}

							log(String.format(
									"INFO: found address: currency[%s] 0x%s => %s (matches regex '%s' => %s)",
									generator.getCurrencyName(), Hex.toHexString(exponent).toUpperCase(),
									finalAddressRef.get(), patterns.get(pIdx).pattern(), m.group()));
						}
					}
					matcherIdx += patterns.size();
				}
				totalExponentsScanned.getAndIncrement();
			}
		}

		private boolean recheckMatchWithCheckSum(
				AddressGenerator generator,
				Matcher matcher,
				AtomicReference<CharSequence> addressRef
		) {
			final OptionalChecksumGenerator<?> optCSGen = (OptionalChecksumGenerator<?>) generator;
			if (optCSGen.isWithCheckSum()) {
				return true;
			}
			final CharSequence addressWithCheckSum = optCSGen.getGeneratorWithCheckSum().generateAddress(ecp);
			final CharSequence addressWithoutCheckSum = addressRef.get();
			matcher.reset(addressWithCheckSum);
			addressRef.lazySet(addressWithCheckSum);
			if (matcher.find()) {
				return true;
			}
			if (settings.isDebugOutput()) {
				log(String.format(
						"DEBUG: rejected address (no matching after checksum): currency[%s] 0x%s => "
								+ "%s (without checksum) / %s (with checksum) [pattern: %s]",
						generator.getCurrencyName(), Hex.toHexString(exponent).toUpperCase(),
						addressWithoutCheckSum, addressWithCheckSum,
						matcher.pattern().pattern()));
			}
			return false;
		}
	}

	private class StatLogger extends Thread {
		public StatLogger(String name) {
			super(name);
		}

		@Override
		public void run() {
			final StringBuilder message = new StringBuilder(0x1000);
			final long statFreqMs = settings.getStatFreqSec() * 1000;
			long now = currentTimeMillis();
			long nextScheduledTime = now - (now % statFreqMs) + statFreqMs;
			long lastTotalExponentsScanned = 0;

			while (running) {
				now = currentTimeMillis();
				long delay = nextScheduledTime - now;
				if (delay > 0) {
					sharedLock.lock();
					try {
						try {
							shutdownCond.await(delay, TimeUnit.MILLISECONDS);
						}
						catch (InterruptedException ignore) {
						}
						continue;
					}
					finally {
						sharedLock.unlock();
					}
				}

				// get metrics snapshot
				final long currentTotalExponentsScanned = totalExponentsScanned.get();
				final long currentRandomFillTimeNanos = randomFillTimeNanos.get();
				final long currentEcPointEvaluatingTimeNanos = ecPointEvaluatingTimeNanos.get();
				final long currentAddressGenerationTimeNanos = addressGenerationTimeNanos.get();
				final long currentRegexMatchingTimeNanos = regexMatchingTimeNanos.get();
				final long currentTotalTimeNanos = currentRandomFillTimeNanos + currentEcPointEvaluatingTimeNanos
						+ currentAddressGenerationTimeNanos + currentRegexMatchingTimeNanos;
				final long deltaTotalExponentsScanned = currentTotalExponentsScanned - lastTotalExponentsScanned;

				// build message
				message.setLength(0);
				message.append("INFO: total exponents scanned: ").append(currentTotalExponentsScanned)
						.append(" (+").append(deltaTotalExponentsScanned).append(")  ")
						.append(String.format(
								"[fill rand: %5.02f%%] [EC point: %5.02f%%] [addr gen: %5.02f%%] [regex: %5.02f%%]",
								100d * currentRandomFillTimeNanos / (double) currentTotalTimeNanos,
								100d * currentEcPointEvaluatingTimeNanos / (double) currentTotalTimeNanos,
								100d * currentAddressGenerationTimeNanos / (double) currentTotalTimeNanos,
								100d * currentRegexMatchingTimeNanos / (double) currentTotalTimeNanos
						));

				// log
				log(message.toString());

				// update last values
				lastTotalExponentsScanned = currentTotalExponentsScanned;

				// consume metrics
				randomFillTimeNanos.getAndUpdate((x) -> x - currentRandomFillTimeNanos);
				ecPointEvaluatingTimeNanos.getAndUpdate((x) -> x - currentEcPointEvaluatingTimeNanos);
				addressGenerationTimeNanos.getAndUpdate((x) -> x - currentAddressGenerationTimeNanos);
				regexMatchingTimeNanos.getAndUpdate((x) -> x - currentRegexMatchingTimeNanos);

				// update next scheduled time (assume: now >= nextScheduledTime)
				now = currentTimeMillis();
				nextScheduledTime += statFreqMs * (1 + (now - nextScheduledTime) / statFreqMs);
			}
		}
	}

	private static void increment(byte[] exponent) {
		for (int v = 1, i = exponent.length - 1; (i >= 0) && (v != 0); i--) {
			v = (exponent[i] & 0xFF) + v;
			exponent[i] = (byte) v;
			v >>>= 8;
		}
	}
}
