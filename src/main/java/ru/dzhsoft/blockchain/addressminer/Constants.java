package ru.dzhsoft.blockchain.addressminer;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.GeneralDigest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.FixedPointUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Constants {
	public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
	public static final int DEFAULT_SUBSEQLEN = 1_000_000;
	public static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
	public static final ECDomainParameters CURVE;
	public static final ThreadLocal<MessageDigest> MD_SHA256_THREAD_LOCAL =
			ThreadLocal.withInitial(() -> {
				try {
					return MessageDigest.getInstance("SHA-256");
				}
				catch (NoSuchAlgorithmException e) {
					throw new RuntimeException(e);
				}
			});
	public static final ThreadLocal<MessageDigest> MD_KECCAK256_THREAD_LOCAL =
			ThreadLocal.withInitial(Keccak.Digest256::new);
	public static final ThreadLocal<GeneralDigest> MD_RIPEMD160_THREAD_LOCAL =
			ThreadLocal.withInitial(RIPEMD160Digest::new);

	public static final byte BTC_P2PKH_VERSION = 0x00;
	public static final byte BTC_P2SH_VERSION = 0x05;
	public static final byte TRX_VERSION = 0x41;
	public static final byte QTUM_VERSION = 0x3a;

	static {
		// Tell Bouncy Castle to precompute data that's needed during secp256k1 calculations. Increasing the width
		// number makes calculations faster, but at a cost of extra memory usage and with decreasing returns.
		FixedPointUtil.precompute(CURVE_PARAMS.getG());
		CURVE = new ECDomainParameters(CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(),
				CURVE_PARAMS.getH());
	}

	private Constants() {
	}
}
