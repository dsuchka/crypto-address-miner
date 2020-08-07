package ru.dzhsoft.blockchain.addressminer.addrgen;

import ru.dzhsoft.blockchain.addressminer.util.ReusableCharSequence;

import java.security.DigestException;
import java.security.MessageDigest;

import static ru.dzhsoft.blockchain.addressminer.Constants.MD_KECCAK256_THREAD_LOCAL;

public class ETHAddressGenerator
		extends BasicAddressGenerator
		implements OptionalChecksumGenerator<ETHAddressGenerator> {
	private final static char[] HEX_CHARS_LOWER = "0123456789abcdef".toCharArray();

	private final String currencyName;
	private final boolean withCheckSum;
	private final byte[] addressBytes = new byte[20];
	private final ReusableCharSequence addressCharSeq = new ReusableCharSequence(addressBytes.length * 2);
	private final char[] addressChars = addressCharSeq.getBuffer();
	private final byte[] checksumHash256 = new byte[32];

	public ETHAddressGenerator(String currencyName, AddressHash160Generator hashGen) {
		this(currencyName, hashGen, true);
	}

	public ETHAddressGenerator(String currencyName, AddressHash160Generator hashGen, boolean withCheckSum) {
		this.currencyName = currencyName;
		this.withCheckSum = withCheckSum;
		setAddressHash160Generator(hashGen);
		addressCharSeq.setLen(addressChars.length);
	}

	@SuppressWarnings({ "PointlessBitwiseExpression" })
	@Override
	protected CharSequence generateAddress0(ECPointData ecp) {
		prepare(ecp);

		// format bytes to hex chars
		for (int i = 0; i < addressBytes.length; i++) {
			addressChars[(i << 1) | 0] = HEX_CHARS_LOWER[(addressBytes[i] >> 4) & 0x0F];
			addressChars[(i << 1) | 1] = HEX_CHARS_LOWER[(addressBytes[i] >> 0) & 0x0F];
		}

		// checksum:
		// uppercase letters for which tetrades of the checksum (keccak256) has high (4th) bit is set
		if (withCheckSum) {
			final MessageDigest sha3keccak256 = MD_KECCAK256_THREAD_LOCAL.get();
			sha3keccak256.reset();
			for (int i = 0; i < (addressBytes.length * 2); i++) {
				sha3keccak256.update((byte) addressChars[i]);
			}
			try {
				sha3keccak256.digest(checksumHash256, 0, checksumHash256.length);
			}
			catch (DigestException e) {
				throw new RuntimeException(e);
			}
			for (int i = 0; i < (addressBytes.length * 2); i++) {
				if ((addressChars[i] >= 'a') && (addressChars[i] <= 'f')
						&& ((checksumHash256[i >>> 1] & (0x08 << (((i + 1) & 0x01) * 4))) != 0)) {
					addressChars[i] += 'A' - 'a';
				}
			}
		}
		return addressCharSeq;
	}

	void prepare(ECPointData ecp) {
		getAddressHash160Generator().evaluateHash160(addressBytes, 0, ecp);
	}

	@Override
	public String getCurrencyName() {
		return currencyName;
	}

	@Override
	public AddressGenerator copy() {
		return new ETHAddressGenerator(currencyName, getAddressHash160Generator().copy(), withCheckSum);
	}

	@Override
	public boolean isWithCheckSum() {
		return withCheckSum;
	}

	@Override
	public ETHAddressGenerator getGeneratorWithCheckSum() {
		if (withCheckSum) {
			return this;
		}
		// reuse the same hash160 generator to avoid hash recomputation
		return new ETHAddressGenerator(currencyName, getAddressHash160Generator(), true);
	}
}