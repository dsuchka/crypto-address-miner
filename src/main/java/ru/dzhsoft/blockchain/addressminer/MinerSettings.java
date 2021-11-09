package ru.dzhsoft.blockchain.addressminer;

import org.bouncycastle.math.ec.ECPoint;
import ru.dzhsoft.blockchain.addressminer.util.FastRandom;

public class MinerSettings {
	private boolean debugOutput;
	private boolean usePrng;
	private boolean reuseKeyData;
	private long statFreqSec;
	private int threads = Constants.CPU_COUNT;
	private int subSeqLen = Constants.DEFAULT_SUBSEQLEN;
	private String randomSourceFilePath = FastRandom.DEFAULT_RANDOM_SOURCE_FILE_PATH;
	private ECPoint generator = Constants.CURVE.getG();

	public boolean isDebugOutput() {
		return debugOutput;
	}

	public void setDebugOutput(boolean debugOutput) {
		this.debugOutput = debugOutput;
	}

	public boolean isUsePrng() {
		return usePrng;
	}

	public void setUsePrng(boolean usePrng) {
		this.usePrng = usePrng;
	}

	public boolean isReuseKeyData() {
		return reuseKeyData;
	}

	public void setReuseKeyData(boolean reuseKeyData) {
		this.reuseKeyData = reuseKeyData;
	}

	public long getStatFreqSec() {
		return statFreqSec;
	}

	public void setStatFreqSec(long statFreqSec) {
		this.statFreqSec = statFreqSec;
	}

	public int getThreads() {
		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public int getSubSeqLen() {
		return subSeqLen;
	}

	public void setSubSeqLen(int subSeqLen) {
		this.subSeqLen = subSeqLen;
	}

	public String getRandomSourceFilePath() {
		return randomSourceFilePath;
	}

	public void setRandomSourceFilePath(String randomSourceFilePath) {
		this.randomSourceFilePath = randomSourceFilePath;
	}

	public ECPoint getGenerator() {
		return generator;
	}

	public void setGenerator(ECPoint generator) {
		this.generator = generator;
	}
}
