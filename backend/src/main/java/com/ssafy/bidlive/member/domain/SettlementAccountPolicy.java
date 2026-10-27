package com.ssafy.bidlive.member.domain;

/** 정산 계좌 입력의 정규화, 형식 검증과 마스킹 규칙을 관리한다. */
public final class SettlementAccountPolicy {

	private static final int MIN_ACCOUNT_NUMBER_LENGTH = 10;
	private static final int MAX_ACCOUNT_NUMBER_LENGTH = 14;
	private static final int MAX_ACCOUNT_HOLDER_NAME_LENGTH = 50;
	private static final int VISIBLE_ACCOUNT_DIGITS = 3;

	private SettlementAccountPolicy() {
	}

	public static String normalizeAccountNumber(String accountNumber) {
		if (accountNumber == null) {
			return null;
		}
		return accountNumber.replaceAll("[\\s-]", "");
	}

	public static boolean isValidAccountNumber(String accountNumber) {
		if (accountNumber == null) {
			return false;
		}
		int length = accountNumber.length();
		return length >= MIN_ACCOUNT_NUMBER_LENGTH
			&& length <= MAX_ACCOUNT_NUMBER_LENGTH
			&& accountNumber.chars().allMatch(Character::isDigit);
	}

	public static String normalizeAccountHolderName(
		String accountHolderName
	) {
		if (accountHolderName == null) {
			return null;
		}
		return accountHolderName.trim().replaceAll("\\s+", " ");
	}

	public static boolean isValidAccountHolderName(
		String accountHolderName
	) {
		if (accountHolderName == null
			|| accountHolderName.isBlank()
			|| accountHolderName.length()
				> MAX_ACCOUNT_HOLDER_NAME_LENGTH) {
			return false;
		}
		return accountHolderName.chars()
			.noneMatch(Character::isISOControl);
	}

	public static String maskAccountNumber(String accountNumber) {
		if (!isValidAccountNumber(accountNumber)) {
			throw new IllegalArgumentException(
				"account number format is invalid"
			);
		}
		return accountNumber.substring(0, VISIBLE_ACCOUNT_DIGITS)
			+ "-****-**"
			+ accountNumber.substring(
				accountNumber.length() - VISIBLE_ACCOUNT_DIGITS
			);
	}
}
