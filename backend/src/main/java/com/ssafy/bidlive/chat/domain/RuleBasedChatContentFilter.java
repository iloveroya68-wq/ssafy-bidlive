package com.ssafy.bidlive.chat.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleBasedChatContentFilter implements ChatContentFilter {

	private static final int MIN_ACCOUNT_DIGITS = 10;
	private static final int MAX_ACCOUNT_DIGITS = 16;

	private static final Pattern PHONE_PATTERN = Pattern.compile(
		"(?<!\\d)01[016789](?:[-.\\s]?\\d{3,4})[-.\\s]?\\d{4}(?!\\d)"
	);
	private static final Pattern MESSENGER_ID_PATTERN = Pattern.compile(
		"(?:카카오톡|카톡|kakao(?:\\s*talk)?|텔레그램|telegram|라인|line)"
			+ "\\s*(?:아이디|id)?\\s*[:：]?\\s*"
			+ "(@?[a-z0-9][a-z0-9._-]{3,31})",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern LABELED_ACCOUNT_PATTERN = Pattern.compile(
		"(?:계좌(?:번호)?|입금\\s*계좌)\\s*[:：]?\\s*(\\d{10,16})"
	);
	private static final Pattern SEPARATED_ACCOUNT_PATTERN = Pattern.compile(
		"(?<!\\d)\\d{2,6}(?:[-\\s]\\d{2,6}){1,3}(?!\\d)"
	);
	private static final Pattern MOBILE_PHONE_DIGITS_PATTERN = Pattern.compile(
		"01[016789]\\d{7,8}"
	);
	private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");

	private final ProhibitedWordProvider prohibitedWordProvider;

	public RuleBasedChatContentFilter(
		ProhibitedWordProvider prohibitedWordProvider
	) {
		this.prohibitedWordProvider = Objects.requireNonNull(
			prohibitedWordProvider,
			"prohibitedWordProvider"
		);
	}

	@Override
	public FilterResult filter(String content) {
		Objects.requireNonNull(content, "content");

		List<MaskedRange> detectedRanges = new ArrayList<>();
		detectProhibitedWords(content, detectedRanges);
		detectPatternMatches(
			content,
			PHONE_PATTERN,
			0,
			MaskingCategory.CONTACT,
			detectedRanges
		);
		detectPatternMatches(
			content,
			MESSENGER_ID_PATTERN,
			1,
			MaskingCategory.CONTACT,
			detectedRanges
		);
		detectPatternMatches(
			content,
			LABELED_ACCOUNT_PATTERN,
			1,
			MaskingCategory.ACCOUNT,
			detectedRanges
		);
		detectSeparatedAccounts(content, detectedRanges);

		List<MaskedRange> validRanges = detectedRanges.stream()
			.filter(range -> range.endExclusive() <= content.length())
			.toList();
		EnumSet<MaskingCategory> maskedFields = validRanges.stream()
			.map(MaskedRange::category)
			.collect(
				() -> EnumSet.noneOf(MaskingCategory.class),
				EnumSet::add,
				EnumSet::addAll
			);

		return new FilterResult(
			mask(content, mergeRanges(validRanges)),
			maskedFields
		);
	}

	private void detectProhibitedWords(
		String content,
		List<MaskedRange> detectedRanges
	) {
		Set<String> prohibitedWords = Objects.requireNonNull(
			prohibitedWordProvider.getProhibitedWords(),
			"prohibitedWords"
		);

		for (String prohibitedWord : prohibitedWords) {
			if (prohibitedWord == null || prohibitedWord.isBlank()) {
				continue;
			}

			Pattern pattern = Pattern.compile(
				Pattern.quote(prohibitedWord),
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
			);
			detectPatternMatches(
				content,
				pattern,
				0,
				MaskingCategory.PROHIBITED_WORD,
				detectedRanges
			);
		}
	}

	private static void detectSeparatedAccounts(
		String content,
		List<MaskedRange> detectedRanges
	) {
		Matcher matcher = SEPARATED_ACCOUNT_PATTERN.matcher(content);

		while (matcher.find()) {
			String digits = NON_DIGIT_PATTERN.matcher(matcher.group())
				.replaceAll("");
			if (digits.length() < MIN_ACCOUNT_DIGITS
				|| digits.length() > MAX_ACCOUNT_DIGITS) {
				continue;
			}
			if (MOBILE_PHONE_DIGITS_PATTERN.matcher(digits).matches()) {
				continue;
			}

			detectedRanges.add(
				new MaskedRange(
					matcher.start(),
					matcher.end(),
					MaskingCategory.ACCOUNT
				)
			);
		}
	}

	private static void detectPatternMatches(
		String content,
		Pattern pattern,
		int group,
		MaskingCategory category,
		List<MaskedRange> detectedRanges
	) {
		Matcher matcher = pattern.matcher(content);

		while (matcher.find()) {
			detectedRanges.add(
				new MaskedRange(
					matcher.start(group),
					matcher.end(group),
					category
				)
			);
		}
	}

	private static List<TextRange> mergeRanges(
		List<MaskedRange> detectedRanges
	) {
		if (detectedRanges.isEmpty()) {
			return List.of();
		}

		List<MaskedRange> sortedRanges = detectedRanges.stream()
			.sorted(
				Comparator.comparingInt(MaskedRange::startInclusive)
					.thenComparingInt(MaskedRange::endExclusive)
			)
			.toList();
		List<TextRange> mergedRanges = new ArrayList<>();
		int currentStart = sortedRanges.getFirst().startInclusive();
		int currentEnd = sortedRanges.getFirst().endExclusive();

		for (int index = 1; index < sortedRanges.size(); index++) {
			MaskedRange next = sortedRanges.get(index);
			if (next.startInclusive() <= currentEnd) {
				currentEnd = Math.max(currentEnd, next.endExclusive());
				continue;
			}

			mergedRanges.add(new TextRange(currentStart, currentEnd));
			currentStart = next.startInclusive();
			currentEnd = next.endExclusive();
		}

		mergedRanges.add(new TextRange(currentStart, currentEnd));
		return List.copyOf(mergedRanges);
	}

	private static String mask(
		String content,
		List<TextRange> maskedRanges
	) {
		StringBuilder filteredContent = new StringBuilder(content);

		for (TextRange range : maskedRanges) {
			for (int index = range.startInclusive();
				 index < range.endExclusive();
				 index++) {
				filteredContent.setCharAt(index, '*');
			}
		}

		return filteredContent.toString();
	}

	private record TextRange(
		int startInclusive,
		int endExclusive
	) {
	}
}
