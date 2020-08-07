package ru.dzhsoft.blockchain.addressminer.addrgen;

public interface AddressHash160Generator {
	void evaluateHash160(byte[] dst, int offset, ECPointData ecp);

	AddressHash160Generator copy();
}
