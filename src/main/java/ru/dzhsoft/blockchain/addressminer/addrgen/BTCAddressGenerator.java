package ru.dzhsoft.blockchain.addressminer.addrgen;

import ru.dzhsoft.blockchain.addressminer.util.Base58Encoder;
import ru.dzhsoft.blockchain.addressminer.util.ReusableCharSequence;

import java.security.DigestException;
import java.security.MessageDigest;

import static java.lang.System.arraycopy;
import static ru.dzhsoft.blockchain.addressminer.Constants.MD_SHA256_THREAD_LOCAL;

public class BTCAddressGenerator
		extends BasicAddressGenerator
		implements OptionalChecksumGenerator<BTCAddressGenerator> {
	private final String currencyName;
	private final byte version;
	private final boolean withCheckSum;
	private final byte[] hash256 = new byte[32];
	private final byte[] addressData = new byte[25];
	private final ReusableCharSequence addressCharSeq = new ReusableCharSequence(36);

	public BTCAddressGenerator(
			String currencyName,
			byte version,
			AddressHash160Generator hashGen,
			boolean withCheckSum
	) {
		this.currencyName = currencyName;
		this.version = version;
		this.withCheckSum = withCheckSum;
		setAddressHash160Generator(hashGen);
	}

	@Override
	protected CharSequence generateAddress0(ECPointData ecp) {
		prepare(ecp);
		final int size = Base58Encoder.encodeInto(addressData, addressCharSeq.getBuffer(), 0);
		addressCharSeq.setLen(size);
		return addressCharSeq;
	}

	void prepare(ECPointData ecp) {
		try {
			// fill addr part: {<version>[????][????]}
			addressData[0] = version;

			// fill addr part: {[version]<hash160>[????]}
			getAddressHash160Generator().evaluateHash160(addressData, 1, ecp);

			// evaluate & fill checksum (if necessary)
			if (withCheckSum) {
				final MessageDigest sha256 = MD_SHA256_THREAD_LOCAL.get();
				sha256.reset();
				sha256.reset();
				sha256.update(addressData, 0, 21);
				sha256.digest(hash256, 0, hash256.length);
				sha256.update(hash256);
				sha256.digest(hash256, 0, hash256.length);

				// fill addr checksum: {[version][hash160]<CHECKSUM>}
				arraycopy(hash256, 0, addressData, 21, 4);
			}
		}
		catch (DigestException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getCurrencyName() {
		return currencyName;
	}

	@Override
	public AddressGenerator copy() {
		return new BTCAddressGenerator(currencyName, version, getAddressHash160Generator().copy(), withCheckSum);
	}

	@Override
	public boolean isWithCheckSum() {
		return withCheckSum;
	}

	@Override
	public BTCAddressGenerator getGeneratorWithCheckSum() {
		if (withCheckSum) {
			return this;
		}
		// reuse the same hash160 generator to avoid hash recomputation
		return new BTCAddressGenerator(currencyName, version, getAddressHash160Generator(), true);
	}
}
