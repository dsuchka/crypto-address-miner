package ru.dzhsoft.blockchain.addressminer;

import ru.dzhsoft.blockchain.addressminer.addrgen.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static ru.dzhsoft.blockchain.addressminer.Constants.*;

public class RulesConfig {
	private static final int FLAG_WITH_CHECKSUM = 0x0001;
	private static final int FLAG_WITHOUT_CHECKSUM = 0x0002;

	private static final Map<String, Integer> CURRENCIES_SUPPORTED_FLAGS = new HashMap<>();
	private static final Map<String, Integer> CURRENCIES_DEFAULT_FLAGS = new HashMap<>();

	static {
		final int BOTH_CHECKSUM_FLAGS = FLAG_WITH_CHECKSUM | FLAG_WITHOUT_CHECKSUM;
		CURRENCIES_SUPPORTED_FLAGS.put("BTC", BOTH_CHECKSUM_FLAGS);
		CURRENCIES_SUPPORTED_FLAGS.put("ETH", BOTH_CHECKSUM_FLAGS);
		CURRENCIES_SUPPORTED_FLAGS.put("TRX", BOTH_CHECKSUM_FLAGS);
		CURRENCIES_SUPPORTED_FLAGS.put("QTUM", BOTH_CHECKSUM_FLAGS);

		CURRENCIES_DEFAULT_FLAGS.put("BTC", FLAG_WITH_CHECKSUM);
		CURRENCIES_DEFAULT_FLAGS.put("ETH", FLAG_WITHOUT_CHECKSUM);
		CURRENCIES_DEFAULT_FLAGS.put("TRX", FLAG_WITH_CHECKSUM);
		CURRENCIES_DEFAULT_FLAGS.put("QTUM", FLAG_WITH_CHECKSUM);
	}

	private final BTCLikeAddressHash160Generator btcHash160Gen = new BTCLikeAddressHash160Generator();
	private final ETHLikeAddressHash160Generator ethHash160Gen = new ETHLikeAddressHash160Generator();

	private final List<RulesBlock> rulesBlocks = new ArrayList<>();
	private final Pattern currencyFormat = Pattern.compile("^([^{}]+)(?:\\{([^{}]*)})?$");

	public List<RulesBlock> getRulesBlocks() {
		return rulesBlocks;
	}

	public void load(InputStream input) throws IOException, WrongRulesConfigException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
			RulesBlock block = null;
			int lineNo = 1;
			for (String line; (line = br.readLine()) != null; lineNo++) {
				line = line.trim();
				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}

				// is it currencies block?
				if (line.startsWith("[") && line.endsWith("]")) {
					final String value = line.substring(1, line.length() - 1);
					block = new RulesBlock(parseGenerators(lineNo, value));
					rulesBlocks.add(block);
				}

				// is it regex pattern?
				else if (line.startsWith("regex:")) {
					if (block == null) {
						throw new WrongRulesConfigException(lineNo, "pattern without currencies block");
					}
					try {
						block.getRegexPatterns().add(Pattern.compile(line.substring(6)));
					}
					catch (PatternSyntaxException e) {
						throw new WrongRulesConfigException(lineNo, "invalid regex", e);
					}
				}

				// unknown format
				else {
					throw new WrongRulesConfigException(lineNo, "unknown format: " + line);
				}
			}
		}

		// clean up (drop empty blocks)
		rulesBlocks.removeIf(RulesBlock::isEmpty);
	}

	private List<AddressGenerator> parseGenerators(int lineNo, String list) throws WrongRulesConfigException {
		list = list.trim();
		if (list.isEmpty()) {
			throw new WrongRulesConfigException(lineNo, "empty currencies block");
		}
		final String[] currencies = Arrays.stream(list.split(","))
				.map(String::trim)
				.toArray(String[]::new);
		final List<AddressGenerator> generators = new ArrayList<>();
		for (String currencyFmt : currencies) {
			final Matcher m = currencyFormat.matcher(currencyFmt);
			if (!m.matches()) {
				throw new WrongRulesConfigException(lineNo, "invalid currency format: " + currencyFmt);
			}
			final String currency = m.group(1);
			final String flags = m.group(2);

			// parse flags (if any, otherwise get default)
			int parsedFlags;
			try {
				parsedFlags = parseFlags(currency, flags);
			}
			catch (Exception e) {
				throw new WrongRulesConfigException(lineNo, e.getMessage(), e);
			}

			// check supported flags
			final Integer supportedFlags = CURRENCIES_SUPPORTED_FLAGS.get(currency);
			if (supportedFlags == null) {
				throw new WrongRulesConfigException(lineNo, "unknown currency: " + currency);
			}
			if ((parsedFlags & ~supportedFlags) != 0) {
				throw new WrongRulesConfigException(lineNo, "there are unsuppored flags for " + currency);
			}

			AddressGenerator generator;
			final boolean withCheckSum = ((parsedFlags & FLAG_WITHOUT_CHECKSUM) == 0)
					|| ((parsedFlags & FLAG_WITH_CHECKSUM) != 0);
			switch (currency) {
				case "BTC": {
					generator = new BTCAddressGenerator(currency, BTC_P2PKH_VERSION, btcHash160Gen, withCheckSum);
					break;
				}

				case "ETH": {
					generator = new ETHAddressGenerator(currency, ethHash160Gen, withCheckSum);
					break;
				}

				case "TRX": {
					// Tron uses keccak256 (like ETH) for hash160, but address itself is formatted like BTC
					generator = new BTCAddressGenerator(currency, TRX_VERSION, ethHash160Gen, withCheckSum);
					break;
				}

				case "QTUM": {
					generator = new BTCAddressGenerator(currency, QTUM_VERSION, btcHash160Gen, withCheckSum);
					break;
				}

				default:
					throw new WrongRulesConfigException(lineNo, "unknown currency: " + currency);
			}
			generators.add(generator);
		}

		return generators;
	}

	private int parseFlags(String currency, String flags) {
		if ((flags == null) || flags.isEmpty()) {
			final Integer defaultFlags = CURRENCIES_DEFAULT_FLAGS.get(currency);
			if (defaultFlags == null) {
				throw new IllegalArgumentException("unknown currency: " + currency);
			}
			return defaultFlags;
		}
		int bitmap = 0;
		for (String value : flags.split("\\s+")) {
			switch (value) {
				case "+checksum": {
					bitmap |= FLAG_WITH_CHECKSUM;
					break;
				}

				case "-checksum": {
					bitmap |= FLAG_WITHOUT_CHECKSUM;
					break;
				}

				default:
					throw new IllegalArgumentException("unknown flag: " + value);
			}
		}

		// check incompatible flags
		final int CHECKSUM_INCOMPAT_FLAGS = FLAG_WITH_CHECKSUM | FLAG_WITHOUT_CHECKSUM;
		if ((bitmap & CHECKSUM_INCOMPAT_FLAGS) == CHECKSUM_INCOMPAT_FLAGS) {
			throw new IllegalArgumentException("incompatible flags: +checksum -checksum");
		}

		return bitmap;
	}

	public static class RulesBlock {
		private final List<AddressGenerator> generators;
		private final List<Pattern> regexPatterns;

		public RulesBlock(List<AddressGenerator> generators) {
			this(generators, new ArrayList<>());
		}

		public RulesBlock(List<AddressGenerator> generators, List<Pattern> regexPatterns) {
			Objects.requireNonNull(generators);
			Objects.requireNonNull(regexPatterns);
			this.generators = generators;
			this.regexPatterns = regexPatterns;
		}

		public List<AddressGenerator> getGenerators() {
			return generators;
		}

		public List<Pattern> getRegexPatterns() {
			return regexPatterns;
		}

		public boolean isEmpty() {
			return generators.isEmpty() || regexPatterns.isEmpty();
		}

		public RulesBlock copy() {
			final List<AddressGenerator> genCopies = new ArrayList<>();
			for (AddressGenerator gen : this.generators) {
				genCopies.add(gen.copy());
			}
			return new RulesBlock(genCopies, new ArrayList<>(regexPatterns));
		}
	}
}
