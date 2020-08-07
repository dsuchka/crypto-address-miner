package ru.dzhsoft.blockchain.addressminer.util;

import java.util.Date;

import static java.lang.System.out;

public class Helper {
	private static final ThreadLocal<Date> DATE_THREAD_LOCAL = ThreadLocal.withInitial(Date::new);

	private Helper() {
	}

	// Simplify logging (SLF4J/pure log4j/logbak, etc)
	public static void log(String message) {
		final Date now = DATE_THREAD_LOCAL.get();
		now.setTime(System.currentTimeMillis());
		out.printf("[%1$tY-%1$tm-%1$td %1$tT] [%2$s] %3$s%n",
				now, Thread.currentThread().getName(), message);
		out.flush();
	}
}
