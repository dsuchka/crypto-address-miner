import org.junit.Ignore;
import ru.dzhsoft.blockchain.addressminer.util.FastRandom;

import java.security.SecureRandom;

import static java.lang.System.nanoTime;
import static ru.dzhsoft.blockchain.addressminer.util.Helper.log;

@Ignore
public class TestFastRandomVsSecureRandom {
	final static SecureRandom secureRandom = new SecureRandom();
	final static FastRandom fastRandom = new FastRandom("RandomSourceReader");

	final static byte[] buffer = new byte[32];

	public static void main(String[] args) throws InterruptedException {
		long start, end;
		final int iters = 10000000;

		log("warming up...");
		for (int i = 0; i < 5; i++) {
			generateNextSecureRandom(10000);
			generateNextFastRandom(10000);
		}
		log("done");
		log("");

		log("----------------");
		log("test SecureRandom for " + iters + " iters");
		start = nanoTime();
		generateNextSecureRandom(iters);
		end = nanoTime();
		log("total time: " + ((end - start) / 1000) + " us");
		log("");

		log("----------------");
		log("test FastRandom(prng only) for " + iters + " iters");
		fastRandom.resetMetricsAndCachedData();
		start = nanoTime();
		generateNextFastRandom(iters);
		end = nanoTime();
		log("total time: " + ((end - start) / 1000) + " us");
		showFastRandomStats();
		log("");

		// start RandomSourceReader
		fastRandom.start();
		generateNextFastRandom(1000); // warm up

		log("----------------");
		log("test FastRandom(no reuse) for " + iters + " iters");
		fastRandom.resetMetricsAndCachedData();
		fastRandom.setReusePrevData(false);
		start = nanoTime();
		generateNextFastRandom(iters);
		end = nanoTime();
		log("total time: " + ((end - start) / 1000) + " us");
		showFastRandomStats();
		log("");

		log("----------------");
		log("test FastRandom(with reuse) for " + iters + " iters");
		fastRandom.resetMetricsAndCachedData();
		fastRandom.setReusePrevData(true);
		start = nanoTime();
		generateNextFastRandom(iters);
		end = nanoTime();
		log("total time: " + ((end - start) / 1000) + " us");
		showFastRandomStats();
		log("");

		fastRandom.shutdown();
		fastRandom.join();
	}

	private static void showFastRandomStats() {
		log("FastRandom /dev/urandom: " + fastRandom.getTotalRandomBytesFromSource());
		log("FastRandom prng........: " + fastRandom.getTotalGeneratedPseudoRandomBytes());
		log("FastRandom total filled: " + fastRandom.getTotalFilledBytes());
		log("FastRandom reused bytes: " + fastRandom.getTotalReusedBytes());
	}

	private static void generateNextSecureRandom(int count) {
		for (int i = 0; i < count; i++) {
			secureRandom.nextBytes(buffer);
		}
	}

	private static void generateNextFastRandom(int count) {
		for (int i = 0; i < count; i++) {
			fastRandom.fillFast(buffer);
		}
	}
}
