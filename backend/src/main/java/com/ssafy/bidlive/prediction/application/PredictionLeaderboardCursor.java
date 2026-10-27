package com.ssafy.bidlive.prediction.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 날짜·집계 버전과 마지막 순위 항목을 담는 일일 리더보드 불투명 커서다. */
public record PredictionLeaderboardCursor(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	long rank,
	long memberId
) {

	private static final String DELIMITER = "|";

	public PredictionLeaderboardCursor {
		if (rankingDate == null || calculatedAt == null) {
			throw new IllegalArgumentException(
				"rankingDate and calculatedAt must not be null"
			);
		}
		if (rank <= 0 || memberId <= 0) {
			throw new IllegalArgumentException(
				"rank and memberId must be positive"
			);
		}
	}

	public String encode() {
		String payload = String.join(
			DELIMITER,
			rankingDate.toString(),
			calculatedAt.toString(),
			Long.toString(rank),
			Long.toString(memberId)
		);
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	public static PredictionLeaderboardCursor decode(String encoded) {
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(encoded),
				StandardCharsets.UTF_8
			);
			String[] values = payload.split("\\|", -1);
			if (values.length != 4) {
				throw new IllegalArgumentException("prediction leaderboard cursor");
			}
			return new PredictionLeaderboardCursor(
				LocalDate.parse(values[0]),
				OffsetDateTime.parse(values[1]),
				Long.parseLong(values[2]),
				Long.parseLong(values[3])
			);
		} catch (DateTimeParseException | NumberFormatException exception) {
			throw new IllegalArgumentException(
				"prediction leaderboard cursor",
				exception
			);
		}
	}
}
