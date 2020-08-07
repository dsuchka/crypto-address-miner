package ru.dzhsoft.blockchain.addressminer.util;

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.arraycopy;

public class Base58Encoder {
	public static final char[] ALPHABET =
			"123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
	private static final char ENCODED_ZERO = ALPHABET[0];

	private static final ThreadLocal<Map<Integer, CachedData>> CACHED_DATA =
			ThreadLocal.withInitial(HashMap::new);

	private static class CachedData {
		final byte[] input;
		final char[] encoded;

		public CachedData(Integer size) {
			input = new byte[size];
			encoded = new char[size * 2];
		}
	}

	public static String encode(byte[] input) {
		final CachedData cd = CACHED_DATA.get().computeIfAbsent(input.length, CachedData::new);
		final int count = encodeInto(input, cd.encoded, 0);
		return new String(cd.encoded, cd.encoded.length - count, count);
	}

	public static int encodeInto(byte[] input, char[] output, int offset) {
		if (input.length == 0) {
			return 0;
		}

		// Count leading zeros.
		int zeros = 0;
		while (zeros < input.length && input[zeros] == 0) {
			++zeros;
		}

		// Get cached data (helper buffers)
		final CachedData cd = CACHED_DATA.get().computeIfAbsent(input.length, CachedData::new);
		arraycopy(input, 0, cd.input, 0, input.length);

		// Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
		int outputStart = cd.encoded.length;
		for (int inputStart = zeros; inputStart < cd.input.length; ) {
			cd.encoded[--outputStart] = ALPHABET[divmod(cd.input, inputStart, 256, 58)];
			if (cd.input[inputStart] == 0) {
				++inputStart; // optimization - skip leading zeros
			}
		}

		// Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
		while ((outputStart < cd.encoded.length) && (cd.encoded[outputStart] == ENCODED_ZERO)) {
			++outputStart;
		}
		while (--zeros >= 0) {
			cd.encoded[--outputStart] = ENCODED_ZERO;
		}

		// Copy encoded data into output buffer
		final int count = cd.encoded.length - outputStart;
		if ((offset + count) > output.length) {
			throw new IndexOutOfBoundsException(String.format(
					"output.len=%d, offset=%d, encoded.len=%d",
					output.length, offset, count));
		}
		if (cd.encoded != output) {
			arraycopy(cd.encoded, outputStart, output, offset, count);
		}
		return count;
	}

	/**
	 * Divides a number, represented as an array of bytes each containing a single digit
	 * in the specified base, by the given divisor. The given number is modified in-place
	 * to contain the quotient, and the return value is the remainder.
	 *
	 * @param number
	 * 		the number to divide
	 * @param firstDigit
	 * 		the index within the array of the first non-zero digit
	 * 		(this is used for optimization by skipping the leading zeros)
	 * @param base
	 * 		the base in which the number's digits are represented (up to 256)
	 * @param divisor
	 * 		the number to divide by (up to 256)
	 *
	 * @return the remainder of the division operation
	 */
	private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
		// this is just long division which accounts for the base of the input digits
		int remainder = 0;
		for (int i = firstDigit; i < number.length; i++) {
			int digit = (int) number[i] & 0xFF;
			int temp = remainder * base + digit;
			number[i] = (byte) (temp / divisor);
			remainder = temp % divisor;
		}
		return (byte) remainder;
	}
}
