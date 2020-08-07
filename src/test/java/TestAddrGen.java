import org.junit.Ignore;
import ru.dzhsoft.blockchain.addressminer.addrgen.*;

import static java.lang.System.out;

@Ignore
public class TestAddrGen {
	public static void main(String[] args) {
		final ECPointData ecp = new ECPointData();
		final BTCLikeAddressHash160Generator btcHashGen = new BTCLikeAddressHash160Generator();
		final ETHLikeAddressHash160Generator ethHashGen = new ETHLikeAddressHash160Generator();
		final BTCAddressGenerator btcGen =
				new BTCAddressGenerator("BTC", (byte) 0x00, btcHashGen, false);
		final ETHAddressGenerator ethGen =
				new ETHAddressGenerator("ETH", ethHashGen, false);
		final BTCAddressGenerator trxGen =
				new BTCAddressGenerator("TRX", (byte) 0x41, ethHashGen, false);

		// evaluate EC point
		final byte[] exponent = new byte[32];
		exponent[0] = 0x11;
		exponent[1] = 0x22;
		ecp.update(exponent);

		out.println("BTC (-checksum): " + btcGen.generateAddress(ecp));
		out.println("BTC (+checksum): " + btcGen.getGeneratorWithCheckSum().generateAddress(ecp));
		out.println("ETH (-checksum): " + ethGen.generateAddress(ecp));
		out.println("ETH (+checksum): " + ethGen.getGeneratorWithCheckSum().generateAddress(ecp));
		out.println("TRX (-checksum): " + trxGen.generateAddress(ecp));
		out.println("TRX (+checksum): " + trxGen.getGeneratorWithCheckSum().generateAddress(ecp));
	}
}
