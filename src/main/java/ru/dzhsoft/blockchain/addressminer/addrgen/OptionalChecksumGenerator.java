package ru.dzhsoft.blockchain.addressminer.addrgen;

public interface OptionalChecksumGenerator<G extends AddressGenerator> {
	boolean isWithCheckSum();

	G getGeneratorWithCheckSum();
}
