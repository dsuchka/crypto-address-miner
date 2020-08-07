package ru.dzhsoft.blockchain.addressminer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.lang.System.nanoTime;

public class FastRandom extends Thread {
	public static final String DEFAULT_RANDOM_SOURCE_FILE_PATH = "/dev/urandom";

	private static final ThreadLocal<Random> RANDOM_THREAD_LOCAL = ThreadLocal.withInitial(Random::new);
	private FileInputStream randomInputStream;

	private static final int ALIGN_SIZE = 0x4;
	private static final int BATCH_SIZE = 0x1000;
	private static final int BUFFER_SIZE = BATCH_SIZE * 0x10;

	private volatile boolean running = false;
	private volatile boolean reusePrevData;
	private final String randomSourceFilePath;

	private final byte[] buffer = new byte[BUFFER_SIZE];
	private final AtomicInteger srcPos = new AtomicInteger(0);
	private final AtomicInteger endPos = new AtomicInteger(0);
	private final Lock lock = new ReentrantLock();
	private final Condition cond = lock.newCondition();

	private final AtomicLong randomSourceBytes = new AtomicLong(0);
	private final AtomicLong filledBytes = new AtomicLong(0);
	private final AtomicLong prngBytes = new AtomicLong(0);

	static {
		//noinspection ConstantConditions
		if ((BUFFER_SIZE % BATCH_SIZE) != 0) {
			throw new RuntimeException("Wrong constants: BATCH_SIZE must be multiplier of BUFFER_SIZE");
		}
	}

	public FastRandom(String name) {
		this(name, true, DEFAULT_RANDOM_SOURCE_FILE_PATH);
	}

	public FastRandom(String name, boolean reusePrevData, String randomSourceFilePath) {
		super(name);
		this.reusePrevData = reusePrevData;
		this.randomSourceFilePath = randomSourceFilePath;
	}

	public long getTotalRandomBytesFromSource() {
		return randomSourceBytes.get();
	}

	public long getTotalFilledBytes() {
		return filledBytes.get();
	}

	public long getTotalGeneratedPseudoRandomBytes() {
		return prngBytes.get();
	}

	public long getTotalReusedBytes() {
		long count = getTotalFilledBytes()
				- getTotalRandomBytesFromSource()
				- getTotalGeneratedPseudoRandomBytes()
				- getReadyBytesCount();
		return max(count, 0);
	}

	public void resetMetricsAndCachedData() {
		randomSourceBytes.set(0);
		filledBytes.set(0);
		prngBytes.set(0);
		srcPos.set(0);
		endPos.set(0);
	}

	public void setReusePrevData(boolean reusePrevData) {
		this.reusePrevData = reusePrevData;
	}

	public void fillFast(byte[] data) {
		fillFast(data, 0, data.length);
	}

	public void fillFast(byte[] data, int offset, int count) {
		// check array bounds
		assert ((offset + count) <= data.length)
				: String.format("offset=%d, count=%d: out of bound for data.len=%d", offset, count, data.length);

		// get Random for PRNG (used when random source is lacking new data)
		final Random rnd = RANDOM_THREAD_LOCAL.get();

		// Main Loop
		int srcIdx, endIdx;
		int spinCount = 0;
		while (count > 0) {
			srcIdx = srcPos.get() % BUFFER_SIZE; // points to the start of region being read
			endIdx = endPos.get() % BUFFER_SIZE; // points to the end of region being read

			// is region splitted due to modulo?
			if (endIdx < srcIdx) {
				// [def....abc] => [...][....abc][def] (since modulo)
				endIdx += BUFFER_SIZE;
			}

			// get count of ready bytes
			final int readyCount = min(endIdx - srcIdx, count);

			// is region has data to fill given storage?
			if (readyCount > 0) {
				// try to reserve data being copied, repeat if failed
				if (!srcPos.compareAndSet(srcIdx, nextSrcPos(srcIdx, readyCount))) {
					continue;
				}
				fillReady(data, offset, readyCount, srcIdx);
				count -= readyCount;
				offset += readyCount;
				filledBytes.addAndGet(readyCount);
			}

			// no, try to wait a little bit and then use custom pseudo RNG
			else {
				// Signal about data lacking if random reader is running (for first time only)
				if (running && (spinCount == 0)) {
					lock.lock();
					cond.signal();
					lock.unlock();
				}

				// Lacking data in the random source (or reader is not running),
				// using custom pseudo RNG
				if (!running || (++spinCount >= 2)) {
					long rndLong = rnd.nextLong() * 0x10001 + nanoTime();
					final int n = min(8, count);
					for (int i = 0; i < n; i++) {
						data[offset++] = (byte) (rndLong >>> (i << 3));
					}
					prngBytes.addAndGet(n);
					filledBytes.addAndGet(n);
					count -= n;

					// re-seed random
					rnd.setSeed((rndLong * 0x1c1 + count) * 0x1b1 + (rndLong ^ nanoTime()));
				}
			}
		}
	}

	private int nextSrcPos(int srcIdx, int count) {
		if (reusePrevData) {
			// when reuse: reserve always next chunk of ALIGN_SIZE
			count = ALIGN_SIZE;
		}
		else {
			// add with alignment
			count = (count + ALIGN_SIZE - 1) & -ALIGN_SIZE;
		}
		return (srcIdx + count) % BUFFER_SIZE;
	}

	private void fillReady(byte[] data, int offset, int count, int srcIdx) {
		// is region splitted?
		if ((srcIdx + count) <= BUFFER_SIZE) {
			// no: just copy all the data
			arraycopy(buffer, srcIdx, data, offset, count);
		}
		else {
			// yes: first copy from tail, then from head
			int firstChunkSize = BUFFER_SIZE - srcIdx;
			arraycopy(buffer, srcIdx, data, offset, firstChunkSize);
			arraycopy(buffer, 0, data, offset + firstChunkSize, count - firstChunkSize);
		}
	}

	@Override
	public void run() {
		while (running) {
			if (isBufferedDataEnough()) {
				lock.lock();
				try {
					if (isBufferedDataEnough()) {
						try {
							cond.await();
						}
						catch (InterruptedException e) {
							continue;
						}
					}
				}
				finally {
					lock.unlock();
				}
			}

			// Since BATCH_SIZE is multiplier of BUFFER_SIZE
			// there is not necessary to warry about overflow
			fillFromRandomSource(buffer, endPos.get(), BATCH_SIZE);
			endPos.set((endPos.get() + BATCH_SIZE) % BUFFER_SIZE);
			randomSourceBytes.addAndGet(BATCH_SIZE);
		}
	}

	@Override
	public synchronized void start() {
		running = true;
		super.start();
	}

	public void shutdown() {
		running = false;
		lock.lock();
		cond.signal();
		lock.unlock();
	}

	private boolean isBufferedDataEnough() {
		return getReadyBytesCount() < (BUFFER_SIZE >>> 2);
	}

	private int getReadyBytesCount() {
		int srcIdx = srcPos.get() % BUFFER_SIZE;
		int endIdx = endPos.get() % BUFFER_SIZE;
		if (endIdx < srcIdx) {
			endIdx += BUFFER_SIZE;
		}
		return endIdx - srcIdx;
	}

	private void fillFromRandomSource(byte[] data, int offset, int count) {
		// check array bounds
		assert ((offset + count) <= data.length)
				: String.format("offset=%d, count=%d: out of bound for data.len=%d", offset, count, data.length);
		int repeats = 0;
		Exception lastError;
		do {
			// get cached FileInputStream for this thread (if any, otherwise create a new one)
			try {
				if (randomInputStream == null) {
					// create & set a new one if ThreadLocal has no value
					randomInputStream = new FileInputStream(new File(randomSourceFilePath));
				}

				// read random data
				final int result = randomInputStream.read(data, offset, count);

				// ensure that read exactly {count} bytes
				if (result != count) {
					throw new IOException(String.format("read only %d of %d expected bytes", result, count));
				}

				return;
			}
			catch (IOException e) {
				// reset ThreadLocal (random file input stream)
				if (randomInputStream != null) {
					try {
						randomInputStream.close();
					}
					catch (IOException ignore) {
					}
					randomInputStream = null;
				}
				lastError = e;
			}
		} while (++repeats < 5); // try up to 5 times, then abort
		throw new RuntimeException("could not read random source", lastError);
	}
}
