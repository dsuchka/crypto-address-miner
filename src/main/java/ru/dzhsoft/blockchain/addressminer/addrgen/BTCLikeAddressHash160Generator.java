package ru.dzhsoft.blockchain.addressminer.addrgen;

import org.bouncycastle.crypto.digests.GeneralDigest;

import java.security.DigestException;
import java.security.MessageDigest;
import java.util.Arrays;

import static java.lang.System.arraycopy;
import static ru.dzhsoft.blockchain.addressminer.Constants.MD_RIPEMD160_THREAD_LOCAL;
import static ru.dzhsoft.blockchain.addressminer.Constants.MD_SHA256_THREAD_LOCAL;

public class BTCLikeAddressHash160Generator
		implements AddressHash160Generator, Cloneable {
	private final byte[] compressedPublicKey = new byte[33];
	private final byte[] hash256 = new byte[32];

	private final byte[] lastCompressedPublicKey = new byte[33];
	private final byte[] lastHash160 = new byte[20];

	@Override
	public void evaluateHash160(byte[] dst, int offset, ECPointData ecp) {
		// mk compressed pubkey: '<Y_parity><X>'
		compressedPublicKey[0] = ecp.getYParity();
		arraycopy(ecp.publicX, 0, compressedPublicKey, 1, 32);

		// compute "<Y_parity><X>" hash160 unless it's already computed
		// (when no changes in pubkey from previous invocation, we can reuse it as is)
		if (!Arrays.equals(compressedPublicKey, lastCompressedPublicKey)) {
			computeHash160();
		}

		// write result
		arraycopy(lastHash160, 0, dst, offset, 20);
	}

	private void computeHash160() {
		try {
			// evaluate sha256 + ripemd160
			final MessageDigest sha256 = MD_SHA256_THREAD_LOCAL.get();
			sha256.reset();
			sha256.update(compressedPublicKey);
			sha256.digest(hash256, 0, hash256.length);
			final GeneralDigest ripemd160 = MD_RIPEMD160_THREAD_LOCAL.get();
			ripemd160.reset();
			ripemd160.update(hash256, 0, hash256.length);
			ripemd160.doFinal(lastHash160, 0);

			// save last compressed pkey
			arraycopy(compressedPublicKey, 0, lastCompressedPublicKey, 0, 33);
		}
		catch (DigestException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public AddressHash160Generator copy() {
		final BTCLikeAddressHash160Generator copy = new BTCLikeAddressHash160Generator();
		arraycopy(compressedPublicKey, 0, copy.compressedPublicKey, 0, compressedPublicKey.length);
		arraycopy(hash256, 0, copy.hash256, 0, hash256.length);
		arraycopy(lastCompressedPublicKey, 0, copy.lastCompressedPublicKey, 0, lastCompressedPublicKey.length);
		arraycopy(lastHash160, 0, copy.lastHash160, 0, lastHash160.length);
		return copy;
	}
}
