package ru.dzhsoft.blockchain.addressminer.addrgen;

import java.security.DigestException;
import java.security.MessageDigest;
import java.util.Arrays;

import static java.lang.System.arraycopy;
import static ru.dzhsoft.blockchain.addressminer.Constants.MD_KECCAK256_THREAD_LOCAL;

public class ETHLikeAddressHash160Generator
		implements AddressHash160Generator, Cloneable {
	private final byte[] rawPublicKey = new byte[64];
	private final byte[] hash256 = new byte[32];

	private final byte[] lastRawPublicKey = new byte[64];
	private final byte[] lastHash160 = new byte[20];

	@Override
	public void evaluateHash160(byte[] dst, int offset, ECPointData ecp) {
		// mk raw pubkey: '<X><Y>'
		arraycopy(ecp.publicX, 0, rawPublicKey, 0, 32);
		arraycopy(ecp.publicY, 0, rawPublicKey, 32, 32);
		if (!Arrays.equals(rawPublicKey, lastRawPublicKey)) {
			computeHash160();
		}
		arraycopy(lastHash160, 0, dst, offset, 20);
	}

	private void computeHash160() {
		try {
			// evaluate sha3.keccak256 (get only last 20 bytes (160 bit))
			final MessageDigest sha3keccak256 = MD_KECCAK256_THREAD_LOCAL.get();
			sha3keccak256.reset();
			sha3keccak256.update(rawPublicKey);
			sha3keccak256.digest(hash256, 0, hash256.length);
			arraycopy(hash256, 12, lastHash160, 0, 20);

			// save last raw pkey
			arraycopy(rawPublicKey, 0, lastRawPublicKey, 0, 64);
		}
		catch (DigestException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public AddressHash160Generator copy() {
		final ETHLikeAddressHash160Generator copy = new ETHLikeAddressHash160Generator();
		arraycopy(rawPublicKey, 0, copy.rawPublicKey, 0, rawPublicKey.length);
		arraycopy(hash256, 0, copy.hash256, 0, hash256.length);
		arraycopy(lastRawPublicKey, 0, copy.lastRawPublicKey, 0, lastRawPublicKey.length);
		arraycopy(lastHash160, 0, copy.lastHash160, 0, lastHash160.length);
		return copy;
	}
}
