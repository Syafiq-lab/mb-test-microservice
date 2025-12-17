package mb.be.common.logging;

import java.util.regex.Pattern;

public final class LogUtils {

	private LogUtils() {}

	private static final String EMPTY = "<empty>";
	private static final int DEFAULT_KEEP_LAST = 4;
	private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t\\p{Cntrl}]");

	public static String safeForLog(String input) {
		if (input == null) return EMPTY;

		String sanitized = CONTROL_CHARS.matcher(input).replaceAll("");
		sanitized = sanitized.trim();

		return sanitized.isEmpty() ? EMPTY : sanitized;
	}

	/** Mask all but last N chars (after log-sanitization). */
	private static String maskLastN(String value, int keepLast) {
		String s = safeForLog(value);
		if (EMPTY.equals(s)) return EMPTY;

		int len = s.length();
		if (len <= keepLast) {
			return "*".repeat(len);
		}
		return "*".repeat(len - keepLast) + s.substring(len - keepLast);
	}

	/** Mask an account number (keeps last 4 chars). */
	public static String maskAccountNumber(String accountNumber) {
		return maskLastN(accountNumber, DEFAULT_KEEP_LAST);
	}

	/** Mask an ID (keeps last 4 chars). */
	public static String maskId(String id) {
		return maskLastN(id, DEFAULT_KEEP_LAST);
	}

	/**
	 * Mask an email for logs. Example:
	 *  john.doe@example.com -> j******e@example.com
	 */
	public static String maskEmail(String email) {
		String s = safeForLog(email);
		if (EMPTY.equals(s)) return EMPTY;

		int at = s.indexOf('@');
		if (at <= 0 || at == s.length() - 1) {
			// Not a normal email; fall back to generic masking.
			return maskLastN(s, DEFAULT_KEEP_LAST);
		}

		String local = s.substring(0, at);
		String domain = s.substring(at + 1);

		String maskedLocal;
		if (local.length() <= 2) {
			maskedLocal = "*".repeat(local.length());
		} else {
			maskedLocal = local.substring(0, 1)
					+ "*".repeat(local.length() - 2)
					+ local.substring(local.length() - 1);
		}

		return maskedLocal + "@" + domain;
	}
}
