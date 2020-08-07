package ru.dzhsoft.blockchain.addressminer.addrgen;

public interface AddressGenerator {
	String getCurrencyName();

	CharSequence generateAddress(ECPointData ecp);

	AddressHash160Generator getAddressHash160Generator();

	void setAddressHash160Generator(AddressHash160Generator hashGen);

	AddressGenerator copy();
}
