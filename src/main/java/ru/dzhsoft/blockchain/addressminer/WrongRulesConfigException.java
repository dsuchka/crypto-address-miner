package ru.dzhsoft.blockchain.addressminer;

public class WrongRulesConfigException extends Exception {
	private static final long serialVersionUID = 1L;
	private int lineNo;

	public WrongRulesConfigException(int lineNo, String desc, Throwable cause) {
		super(formatErrorMessage(lineNo, desc), cause);
		this.lineNo = lineNo;
	}

	public WrongRulesConfigException(int lineNo, String desc) {
		super(formatErrorMessage(lineNo, desc));
		this.lineNo = lineNo;
	}

	private static String formatErrorMessage(int lineNo, String desc) {
		return "Wrong rules at line " + lineNo + ": " + desc;
	}

	public int getLineNo() {
		return lineNo;
	}
}
