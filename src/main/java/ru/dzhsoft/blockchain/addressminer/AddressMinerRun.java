package ru.dzhsoft.blockchain.addressminer;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.System.err;
import static ru.dzhsoft.blockchain.addressminer.Constants.DEFAULT_SUBSEQLEN;
import static ru.dzhsoft.blockchain.addressminer.util.Helper.log;
import static ru.dzhsoft.blockchain.addressminer.util.Helper.parseECPoint;

public class AddressMinerRun {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			showUsageAndExit();
		}

		// parse options (miner settings)
		String rulesConfigFilePath = null;
		final MinerSettings settings = new MinerSettings();
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i];
			if (arg.startsWith("--")) {
				switch (arg.substring(2)) {
					case "debug":
						settings.setDebugOutput(true);
						break;

					case "prng":
						settings.setUsePrng(true);
						break;

					case "rndsrc":
						settings.setRandomSourceFilePath(getOptionParam(args, ++i));
						break;

					case "reusekeydata":
						settings.setReuseKeyData(true);
						break;

					case "statfreq":
						settings.setStatFreqSec(parseLong(getOptionParam(args, ++i)));
						break;

					case "threads":
						settings.setThreads(parseInt(getOptionParam(args, ++i)));
						break;

					case "subseqlen":
						settings.setSubSeqLen(parseInt(getOptionParam(args, ++i)));
						break;

					case "generator":
						final String value = getOptionParam(args, ++i);
						settings.setGenerator(parseECPoint(value));
						log("using generator: " + value);
						break;

					default:
						err.println("ERROR: Unknown option: " + arg);
						showUsageAndExit();
				}
			}
			else if (rulesConfigFilePath == null) {
				rulesConfigFilePath = arg;
			}
			else {
				err.println("ERROR: rules config file path is already specified (it is: " + rulesConfigFilePath + ")");
				showUsageAndExit();
			}
		}

		if (rulesConfigFilePath == null) {
			err.println("ERROR: no rules config file path specified");
			showUsageAndExit();
		}
		assert rulesConfigFilePath != null;

		// load rules
		final RulesConfig rulesConfig = new RulesConfig();
		if (rulesConfigFilePath.equals("-")) {
			rulesConfig.load(System.in);
		}
		else {
			rulesConfig.load(new FileInputStream(rulesConfigFilePath));
		}

		// ensure that random source is existed and readable (unless prng)
		if (!settings.isUsePrng()) {
			final File file = new File(settings.getRandomSourceFilePath());
			if (!file.exists()) {
				err.println("ERROR: no such file (random source): " + file);
				System.exit(2);
			}
			if (!file.canRead()) {
				err.println("ERROR: cannot read random source file: " + file);
				System.exit(2);
			}
		}

		// go!
		final AddressMiner miner = new AddressMiner(settings, rulesConfig);
		miner.start();

		// setup shutdown hook
		final CountDownLatch shutdowReleaseLatch = new CountDownLatch(1);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			log("INFO: shutdown!");
			miner.shutdown();

			// wait until release
			try {
				shutdowReleaseLatch.await();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}, "ShutdownHook"));

		// wait until shutdown
		miner.join();
		log("INFO: exit");

		// release shutdown thread
		shutdowReleaseLatch.countDown();
	}

	private static String getOptionParam(String[] args, int i) throws Exception {
		if (i >= args.length) {
			throw new Exception("option --" + args[i - 1] + " requires an argument");
		}
		return args[i];
	}

	private static void showUsageAndExit() {
		showUsage();
		System.exit(1);
	}

	private static void showUsage() {
		err.println("Usage: <java -jar file.jar> [OPTIONS] rulesConfig");
		err.println();
		err.println("  Available options:");
		err.println("    --debug             show debug information");
		err.println("    --prng              use PRNG instead of /dev/urandom");
		err.println("    --rndsrc <file>     use alternative random source (not /dev/urandom)");
		err.println("    --reusekeydata      use bytes from previous exponent in next one (decrease [P]RNG usage)");
		err.println("    --subseqlen <n>     use <n> subsequent exponents after one random is generated");
		err.println("                        (reduce EC point evaluation, default is " + DEFAULT_SUBSEQLEN + ")");
		err.println("    --statfreq <sec>    print statistic every <sec> seconds (0 to disable, it's default)");
		err.println("    --threads <n>       parallel workers count (default is CPU count)");
		err.println("    --generator <pub>	 use custom generator (points XY - 128 hex chars)");
		err.println();
		err.println("  Rules config format (prefixed by <#lineno: >, don't use it in a real config):");
		err.println("    #01: [CURRENCY_1{flags}, CURRENCY_2{flags}, ..., CURRENCY_N{flags}]");
		err.println("    #02: regex:pattern-12n-1");
		err.println("    #03: regex:pattern-12n-2");
		err.println("    #04: ");
		err.println("    #05: # just a comment");
		err.println("    #06: [CURRENCY_M{flags}]");
		err.println("    #07: regex:pattern-m-1");
		err.println("    #08: regex:pattern-m-2");
		err.println("    #09: regex:pattern-m-3");
		err.println("    #10: [...]");
		err.println();
		err.println("  Available currencies:");
		err.println("    * BTC | default flags {+checksum}");
		err.println("    * ETH | default flags {-checksum}");
		err.println("    * TRX | default flags {+checksum}");
		err.println("    * QTUM | default flags {+checksum}");
		err.println();
		err.println("  Available currencies' flags:");
		err.println("    * +checksum (generate address with checksum)");
		err.println("    * -checksum (generate address without checksum)");
		err.println();
		err.println("  Rules config example:");
		err.println("    # no checksum needed since we are checking only leading chars of addresses");
		err.println("    [BTC{-checksum}, TRX{-checksum}]");
		err.println("    regex:^.JustTest\\d");
		err.println("    regex:^.BestTest\\d");
		err.println();
		err.println("    [ETH]");
		err.println("    regex:^(.)\\\\1{7,}");
		err.println();
		err.println("    # use checksum (by default) since we are checking trailing chars as well");
		err.println("    [QTUM]");
		err.println("    regex:^QTUM.*MUTQ$");
		err.println();
		err.println("  Use '-' as rules config to pass config via stdin");
		err.println();
	}
}
