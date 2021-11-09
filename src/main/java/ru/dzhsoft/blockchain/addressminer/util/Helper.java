package ru.dzhsoft.blockchain.addressminer.util;

import org.bouncycastle.math.ec.ECPoint;
import ru.dzhsoft.blockchain.addressminer.Constants;

import java.util.Date;

import static java.lang.System.out;

public class Helper {
	private static final ThreadLocal<Date> DATE_THREAD_LOCAL = ThreadLocal.withInitial(Date::new);

	private Helper() {
	}

	// Simplify logging (SLF4J/pure log4j/logbak, etc)
	public static void log(String message) {
		final Date now = DATE_THREAD_LOCAL.get();
		now.setTime(System.currentTimeMillis());
		out.printf("[%1$tY-%1$tm-%1$td %1$tT] [%2$s] %3$s%n",
				now, Thread.currentThread().getName(), message);
		out.flush();
	}

	public static ECPoint parseECPoint(String xy) {
		if ((xy.length() != (65 * 2)) && (xy.length() != (33 * 2))) {
			throw new IllegalArgumentException(
					"Wrong public key format (expected compressed or uncompressed key)");
		}
		byte[] encoded = new byte[xy.length() / 2];
		for (int i = 0; i < encoded.length; i++) {
			encoded[i] = (byte) Integer.parseInt(xy.substring(i * 2, i * 2 + 2), 16);
		}
		return Constants.CURVE.getCurve().decodePoint(encoded);
	}
}
