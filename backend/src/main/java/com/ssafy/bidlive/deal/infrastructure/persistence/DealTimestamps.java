package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/** 네이티브 쿼리로 읽은 `TIMESTAMPTZ`를 흡수한다. 드라이버·Hibernate 조합에 따라 타입이 갈린다. */
final class DealTimestamps {

	private DealTimestamps() {
	}

	static Instant toInstant(Object value) {
		return switch (value) {
			case null -> null;
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}
}
