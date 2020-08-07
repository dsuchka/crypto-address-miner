package ru.dzhsoft.blockchain.addressminer.addrgen;

import java.util.Objects;

public abstract class BasicAddressGenerator implements AddressGenerator {
	private AddressHash160Generator hash160Generator;
	private ECPointData lastEcp;
	private int lastModCount;
	private CharSequence lastAddress;

	@Override
	public AddressHash160Generator getAddressHash160Generator() {
		return hash160Generator;
	}

	@Override
	public void setAddressHash160Generator(AddressHash160Generator hashGen) {
		Objects.requireNonNull(hashGen);
		this.hash160Generator = hashGen;
	}

	@Override
	public final CharSequence generateAddress(ECPointData ecp) {
		if ((lastEcp != ecp) || (lastModCount != ecp.getModCount())) {
			lastEcp = ecp;
			lastModCount = ecp.getModCount();
			lastAddress = generateAddress0(ecp);
		}
		return lastAddress;
	}

	protected abstract CharSequence generateAddress0(ECPointData ecp);
}
