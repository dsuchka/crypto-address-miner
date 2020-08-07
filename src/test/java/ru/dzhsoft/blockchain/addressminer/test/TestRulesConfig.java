package ru.dzhsoft.blockchain.addressminer.test;

import org.junit.Assert;
import org.junit.Test;
import ru.dzhsoft.blockchain.addressminer.RulesConfig;
import ru.dzhsoft.blockchain.addressminer.RulesConfig.RulesBlock;
import ru.dzhsoft.blockchain.addressminer.WrongRulesConfigException;
import ru.dzhsoft.blockchain.addressminer.addrgen.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class TestRulesConfig {
	@Test
	public void testWrong() throws IOException {
		final RulesConfig config = new RulesConfig();

		try {
			config.load(new ByteArrayInputStream("blabla".getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(1, e.getLineNo());
			assertTrue(e.getMessage().contains("unknown format: blabla"));
		}

		try {
			config.load(new ByteArrayInputStream("\nregex:abc".getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(2, e.getLineNo());
			assertTrue(e.getMessage().contains("pattern without currencies block"));
		}

		try {
			config.load(new ByteArrayInputStream("[btc]".getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(1, e.getLineNo());
			assertTrue(e.getMessage().contains("unknown currency: btc"));
		}

		try {
			config.load(new ByteArrayInputStream("[BTC{]".getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(1, e.getLineNo());
			assertTrue(e.getMessage().contains("invalid currency format"));
		}

		try {
			config.load(new ByteArrayInputStream("[BTC{+checksum -checksum}]".getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(1, e.getLineNo());
			assertTrue(e.getMessage().contains("incompatible flags: +checksum -checksum"));
		}

		try {
			config.load(new ByteArrayInputStream(("[BTC]\n"
					+ "regex:1*}(").getBytes(UTF_8)));
			Assert.fail();
		}
		catch (WrongRulesConfigException e) {
			assertEquals(2, e.getLineNo());
			assertTrue(e.getMessage().contains("invalid regex"));
		}
	}

	@Test
	public void testNormal() throws IOException, WrongRulesConfigException {
		final RulesConfig config = new RulesConfig();
		config.load(new ByteArrayInputStream(getNormalConfigText().getBytes(UTF_8)));

		final List<RulesBlock> blocks = config.getRulesBlocks();

		assertEquals(2, blocks.size());

		final RulesBlock block1 = blocks.get(0);
		assertEquals(4, block1.getGenerators().size());
		assertEquals(2, block1.getRegexPatterns().size());
		{
			// BTC
			AddressGenerator gen0 = block1.getGenerators().get(0);
			assertSame(BTCAddressGenerator.class, gen0.getClass());
			assertSame(BTCLikeAddressHash160Generator.class, gen0.getAddressHash160Generator().getClass());
			assertTrue(((OptionalChecksumGenerator<?>) gen0).isWithCheckSum());

			// TRX
			AddressGenerator gen1 = block1.getGenerators().get(1);
			assertSame(BTCAddressGenerator.class, gen1.getClass());
			assertSame(ETHLikeAddressHash160Generator.class, gen1.getAddressHash160Generator().getClass());
			assertTrue(((OptionalChecksumGenerator<?>) gen1).isWithCheckSum());

			// QTUM
			AddressGenerator gen2 = block1.getGenerators().get(2);
			assertSame(BTCAddressGenerator.class, gen2.getClass());
			assertSame(BTCLikeAddressHash160Generator.class, gen2.getAddressHash160Generator().getClass());
			assertTrue(((OptionalChecksumGenerator<?>) gen2).isWithCheckSum());

			// QTUM
			AddressGenerator gen3 = block1.getGenerators().get(3);
			assertSame(ETHAddressGenerator.class, gen3.getClass());
			assertSame(ETHLikeAddressHash160Generator.class, gen3.getAddressHash160Generator().getClass());
			assertFalse(((OptionalChecksumGenerator<?>) gen3).isWithCheckSum());
		}

		final RulesBlock block2 = blocks.get(1);
		assertEquals(3, block2.getGenerators().size());
		assertEquals(1, block2.getRegexPatterns().size());
		{
			// BTC
			AddressGenerator gen0 = block2.getGenerators().get(0);
			assertSame(BTCAddressGenerator.class, gen0.getClass());
			assertSame(BTCLikeAddressHash160Generator.class, gen0.getAddressHash160Generator().getClass());
			assertFalse(((OptionalChecksumGenerator<?>) gen0).isWithCheckSum());

			// TRX
			AddressGenerator gen1 = block2.getGenerators().get(1);
			assertSame(BTCAddressGenerator.class, gen1.getClass());
			assertSame(ETHLikeAddressHash160Generator.class, gen1.getAddressHash160Generator().getClass());
			assertFalse(((OptionalChecksumGenerator<?>) gen1).isWithCheckSum());

			// ETH
			AddressGenerator gen2 = block2.getGenerators().get(2);
			assertSame(ETHAddressGenerator.class, gen2.getClass());
			assertSame(ETHLikeAddressHash160Generator.class, gen2.getAddressHash160Generator().getClass());
			assertTrue(((OptionalChecksumGenerator<?>) gen2).isWithCheckSum());
		}
	}

	private static String getNormalConfigText() {
		return ""
				+ "\n"
				+ "# Test without flags\n"
				+ " [BTC, TRX, QTUM, ETH]\n"
				+ "regex:^.Test1.*X$\n"
				+ "regex:^.Test2.*Y$\n"
				+ "\n"
				+ "# Test with flags\n"
				+ " [BTC{-checksum}, TRX{-checksum}, ETH{+checksum}]\n"
				+ "regex:(.)\\1{10,}\n"
				+ "\n"
				+ "# Test without patterns (should be skipped)\n"
				+ "[BTC, ETH]\n"
				+ "\n"
				;
	}
}
