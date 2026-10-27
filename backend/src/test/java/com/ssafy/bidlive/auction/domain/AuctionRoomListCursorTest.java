package com.ssafy.bidlive.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** 커서가 상태·정렬 키·ID를 잃지 않고 왕복하는지, 깨진 값을 거부하는지 검증한다. */
class AuctionRoomListCursorTest {

	@Test
	void roundTripsStatusSortValueAndAuctionId() {
		AuctionRoomListCursor cursor =
			new AuctionRoomListCursor(AuctionStatus.LIVE, "2026-07-21T11:15:00Z", 100L);

		AuctionRoomListCursor decoded = AuctionRoomListCursor.decode(cursor.encode());

		assertThat(decoded).isEqualTo(cursor);
	}

	@Test
	void roundTripsNoStatusForIdOnlyPaging() {
		AuctionRoomListCursor cursor = new AuctionRoomListCursor(null, null, 42L);

		assertThat(AuctionRoomListCursor.decode(cursor.encode())).isEqualTo(cursor);
	}

	@Test
	void roundTripsInfinitySentinelUsedByRoomsWithoutSortTime() {
		AuctionRoomListCursor cursor =
			new AuctionRoomListCursor(AuctionStatus.WAITING, "infinity", 7L);

		assertThat(AuctionRoomListCursor.decode(cursor.encode())).isEqualTo(cursor);
	}

	@Test
	void rejectsValueThatIsNotBase64() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode("not*base64*"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsPayloadWithoutBothDelimiters() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("100")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("LIVE|100")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUnknownStatusInPayload() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("PAUSED|infinity|1")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * 안 끊으면 아무 문자열이 조회 쿼리의 `CAST(... AS TIMESTAMPTZ)`까지 내려가 PostgreSQL에서
	 * 터진다 — 잘못된 요청인데 400이 아니라 500이 나간다.
	 */
	@Test
	void rejectsSortValueThatIsNotATimestamp() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("LIVE|아무거나|1")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("LIVE|2026-07-21|1")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/** 구분자가 3개 이상이면 가운데가 `a|b`로 묶여 정렬 키에 들어온다 */
	@Test
	void rejectsPayloadWithExtraDelimiters() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("LIVE|a|b|1")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonNumericAuctionId() {
		assertThatThrownBy(() -> AuctionRoomListCursor.decode(encoded("LIVE|infinity|abc")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonPositiveAuctionId() {
		assertThatThrownBy(() -> new AuctionRoomListCursor(AuctionStatus.LIVE, "infinity", 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsStatusWithoutSortValueAndSortValueWithoutStatus() {
		assertThatThrownBy(() -> new AuctionRoomListCursor(AuctionStatus.LIVE, null, 1L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AuctionRoomListCursor(null, "infinity", 1L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static String encoded(String payload) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}
}
