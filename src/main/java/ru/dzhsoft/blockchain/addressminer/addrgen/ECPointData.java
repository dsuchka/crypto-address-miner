package ru.dzhsoft.blockchain.addressminer.addrgen;

import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.math.ec.custom.sec.SecP256K1FieldElement;

import java.lang.reflect.Field;
import java.math.BigInteger;

import static ru.dzhsoft.blockchain.addressminer.Constants.CURVE;

public class ECPointData {
	private final FixedPointCombMultiplier multiplier = new FixedPointCombMultiplier();

	public final byte[] publicX = new byte[32];
	public final byte[] publicY = new byte[32];

	private final Field fieldData;

	private int modCount = 0;

	{
		try {
			fieldData = SecP256K1FieldElement.class.getDeclaredField("x");
		}
		catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
		fieldData.setAccessible(true);
	}

	public byte getYParity() {
		return (byte) (0x02 | (publicY[31] & 0x01));
	}

	public boolean update(byte[] exponent) {
		assert (exponent.length == 32) : "Wrong exponent length=" + exponent.length;

		// mk private key for an exponent (just build the BigInteger)
		final BigInteger privKey = new BigInteger(1, exponent);

		// is Pkey valid?
		if (privKey.compareTo(CURVE.getN()) >= 0) {
			return false;
		}

		// multiply G x Pkey
		final ECPoint key = multiplier.multiply(CURVE.getG(), privKey).normalize();

		// get X & Y
		final int[] x, y;
		try {
			x = (int[]) fieldData.get(key.getXCoord());
			y = (int[]) fieldData.get(key.getYCoord());
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		repackFieldInt2Byte(x, publicX);
		repackFieldInt2Byte(y, publicY);
		modCount++;

		// all done
		return true;
	}

	public int getModCount() {
		return modCount;
	}

	@SuppressWarnings("PointlessArithmeticExpression")
	private static void repackFieldInt2Byte(int[] src, byte[] dst) {
		assert (src.length * 4 == dst.length)
				: String.format("src.len=%d (x4) vs dst.len=%d (x1)", src.length, dst.length);
		for (int i = 0; i < src.length; i++) {
			final int j = (src.length - i - 1) << 2;
			final int v = src[i];
			dst[j + 0] = (byte) (v >>> 24);
			dst[j + 1] = (byte) (v >>> 16);
			dst[j + 2] = (byte) (v >>> 8);
			dst[j + 3] = (byte) v;
		}
	}
}
