package com.ssafy.bidlive.auction.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 목록 페이징 커서다. 발급 당시의 상태 필터와 정렬 키, 경매방 ID를 함께 담는다.
 *
 * <p>{@code status}를 담는 이유는 **정렬 기준이 상태마다 다르기** 때문이다. `LIVE`에서 받은
 * 커서를 `ENDED`로 보내면 `ends_at` 값이 `ended_at` 비교에 쓰여 방이 조용히 누락된다 —
 * 커서만 보고는 어느 정렬에서 나온 값인지 알 수 없어서, 값을 넣어 대조한다.
 *
 * <p>{@code sortValue}는 정렬 기준 시각의 ISO-8601 표현이며, 값이 없는 방(예약 없는 대기방 등)은
 * PostgreSQL이 그대로 해석하는 {@code infinity}·{@code -infinity} 문자열을 담아 항상 뒤로 밀린다.
 * 상태 필터가 없으면 ID만 쓰므로 {@code null}이다.
 */
public record AuctionRoomListCursor(AuctionStatus status, String sortValue, long auctionId) {

	/** ISO-8601 시각과 상태 enum 이름에는 없는 문자여서 구분자로 안전하다. */
	private static final char DELIMITER = '|';

	/**
	 * 정렬 기준 시각이 없는 방을 뒤로 밀 때 쓰는 값. PostgreSQL이 `TIMESTAMPTZ`로 그대로 해석하며,
	 * 조회 쿼리의 `COALESCE`와 이 커서가 **같은 문자열을 써야** 정렬과 커서 비교가 어긋나지 않는다.
	 */
	public static final String LAST_SENTINEL = "infinity";

	/** 내림차순 정렬에서 값이 없는 방을 뒤로 밀 때 쓰는 값 ({@link #LAST_SENTINEL}의 역방향). */
	public static final String LAST_SENTINEL_DESC = "-infinity";

	public AuctionRoomListCursor {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId");
		}
		if ((status == null) != (sortValue == null)) {
			// 상태 필터가 있으면 정렬 키가 반드시 있고, 없으면 ID만 쓴다
			throw new IllegalArgumentException("sortValue");
		}
		verifySortValue(sortValue);
	}

	/**
	 * 정렬 키가 실제로 시각인지 여기서 끊는다. 안 끊으면 아무 문자열이 그대로 조회 쿼리의
	 * `CAST(... AS TIMESTAMPTZ)`까지 내려가 PostgreSQL에서 터지고, 잘못된 요청인데 **400이 아니라
	 * 500이 나간다.**
	 *
	 * <p>`DateTimeParseException`은 `IllegalArgumentException` 계열이 **아니라서**
	 * (`DateTimeException` → `RuntimeException`) 그대로 던지면 호출자의 커서 오류 처리에 걸리지
	 * 않는다. 그래서 바꿔 던진다.
	 */
	private static void verifySortValue(String sortValue) {
		if (sortValue == null
			|| LAST_SENTINEL.equals(sortValue)
			|| LAST_SENTINEL_DESC.equals(sortValue)) {
			return;
		}
		try {
			Instant.parse(sortValue);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("sortValue", exception);
		}
	}

	public String encode() {
		String payload = (status == null ? "" : status.name())
			+ DELIMITER + (sortValue == null ? "" : sortValue)
			+ DELIMITER + auctionId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws IllegalArgumentException 커서 형식이 어긋날 때. 호출자가 `INVALID_CURSOR`로 변환한다
	 */
	public static AuctionRoomListCursor decode(String encoded) {
		byte[] decoded = Base64.getUrlDecoder().decode(encoded);
		String payload = new String(decoded, StandardCharsets.UTF_8);
		int first = payload.indexOf(DELIMITER);
		int last = payload.lastIndexOf(DELIMITER);
		if (first < 0 || first == last) {
			throw new IllegalArgumentException("cursor");
		}
		String status = payload.substring(0, first);
		String sortValue = payload.substring(first + 1, last);
		long auctionId = Long.parseLong(payload.substring(last + 1));
		return new AuctionRoomListCursor(
			status.isEmpty() ? null : AuctionStatus.valueOf(status),
			sortValue.isEmpty() ? null : sortValue,
			auctionId
		);
	}
}
