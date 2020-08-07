package ru.dzhsoft.blockchain.addressminer.util;

public class ReusableCharSequence implements CharSequence {
	private final char[] buffer;
	private int len = 0;

	public ReusableCharSequence(int size) {
		this.buffer = new char[size];
	}

	public char[] getBuffer() {
		return buffer;
	}

	public void setLen(int len) {
		if ((len < 0) || (len > buffer.length)) {
			throw new IndexOutOfBoundsException(String.format(
					"len=%d is out of bounds of [0..%d]", len, buffer.length));
		}
		this.len = len;
	}

	@Override
	public int length() {
		return len;
	}

	@Override
	public char charAt(int index) {
		if ((index < 0) || (index >= len)) {
			throw new IndexOutOfBoundsException(String.format(
					"index=%d is out of bounds of [0..%d]", index, len));
		}
		return buffer[index];
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		if ((end < start) || (start > buffer.length) || (end > buffer.length)) {
			throw new IllegalArgumentException(String.format(
					"buffer.len=%d: subseq for start=%d, end=%d",
					buffer.length, start, end));
		}
		return new String(buffer, start, end - start);
	}

	@Override
	public String toString() {
		return new String(buffer, 0, len);
	}
}
