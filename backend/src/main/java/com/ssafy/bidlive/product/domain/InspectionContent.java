package com.ssafy.bidlive.product.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 검열 대상 콘텐츠이며, 같은 내용의 재요청을 알아보기 위한 해시를 스스로 계산합니다.
 *
 * <p>해시는 Credit 절감용 캐시 키다. 이미지 순서만 다른 요청을 같은 콘텐츠로 보기 위해 URL을
 * 정렬해서 넣고, 필드 구분자를 둬 서로 다른 필드 조합이 같은 해시가 되지 않게 한다.
 */
public record InspectionContent(
	String title,
	String description,
	List<String> imageUrls
) {

	private static final String HASH_ALGORITHM = "SHA-256";
	/** 본문에 나타나지 않는 제어문자를 사용해 필드 경계가 내용과 섞이지 않게 한다. */
	private static final String FIELD_SEPARATOR = "\u001F";

	public InspectionContent(String title, String description, List<String> imageUrls) {
		this.title = normalizeText(title);
		this.description = normalizeText(description);
		this.imageUrls = normalizeUrls(imageUrls);

		if (isEmpty()) {
			throw new IllegalArgumentException(
				"Inspection content must have at least one of title, description or imageUrls");
		}
	}

	/** 텍스트와 이미지가 모두 없으면 검열할 대상이 없다. */
	private boolean isEmpty() {
		return title.isEmpty() && description.isEmpty() && imageUrls.isEmpty();
	}

	public boolean hasText() {
		return !title.isEmpty() || !description.isEmpty();
	}

	/** 제목과 설명을 한 덩어리로 검열해 문맥이 끊기지 않게 한다. */
	public String text() {
		if (title.isEmpty()) {
			return description;
		}
		if (description.isEmpty()) {
			return title;
		}
		return title + "\n\n" + description;
	}

	public String contentHash() {
		String source = String.join(
			FIELD_SEPARATOR,
			title,
			description,
			String.join(",", imageUrls)
		);

		try {
			byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
				.digest(source.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(HASH_ALGORITHM + " is not available", exception);
		}
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.strip();
	}

	private static List<String> normalizeUrls(List<String> values) {
		if (values == null) {
			return List.of();
		}

		return values.stream()
			.filter(value -> value != null && !value.isBlank())
			.map(String::strip)
			.distinct()
			.sorted()
			.toList();
	}
}
