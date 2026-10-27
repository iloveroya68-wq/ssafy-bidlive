package com.ssafy.bidlive.common.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.common.api.dto.RealtimeAuctionStateResponse.PinnedNoticeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/** WS-03 응답의 필드·값 집합·null 정책과 외부 비노출 경계를 검증합니다. */
@JsonTest
class RealtimeAuctionStateResponseTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void serializesConfirmedSnapshotContract() {
		RealtimeAuctionStateResponse response = response(
			52_000L,
			null,
			null,
			RealtimeParticipationType.BID,
			new PinnedNoticeResponse("01J8NOTICE", "택배비 포함가입니다")
		);

		JsonNode json = objectMapper.valueToTree(response);

		assertThat(json.properties()).extracting(entry -> entry.getKey())
			.containsExactlyInAnyOrder(
				"stateVersion",
				"auctionStatus",
				"endAt",
				"remainingSeconds",
				"extendCount",
				"remainingExtensions",
				"bidCount",
				"bidParticipantCount",
				"viewerCount",
				"myBidAmount",
				"myPredictedPrice",
				"myStakeAmount",
				"myParticipationType",
				"pinnedNotice",
				"amIMuted",
				"mutedUntil"
			);
		assertThat(json.get("stateVersion").asLong()).isEqualTo(184L);
		assertThat(json.get("auctionStatus").asText()).isEqualTo("LIVE");
		assertThat(json.get("endAt").asText())
			.isEqualTo("2026-07-20T21:15:00+09:00");
		assertThat(json.get("myParticipationType").asText()).isEqualTo("BID");
		assertThat(json.get("pinnedNotice").get("messageId").asText())
			.isEqualTo("01J8NOTICE");
		assertThat(json.has("serverTime")).isFalse();
		assertThat(json.get("remainingExtensions").asInt()).isEqualTo(2);
		assertThat(json.get("mutedUntil").isNull()).isTrue();
		assertThat(json.has("predictionBuckets")).isFalse();
	}

	@Test
	void preservesNullablePersonalStateAndNotice() {
		RealtimeAuctionStateResponse response = response(
			null,
			null,
			null,
			RealtimeParticipationType.NONE,
			null
		);

		JsonNode json = objectMapper.valueToTree(response);

		assertThat(json.get("myBidAmount").isNull()).isTrue();
		assertThat(json.get("myPredictedPrice").isNull()).isTrue();
		assertThat(json.get("myStakeAmount").isNull()).isTrue();
		assertThat(json.get("pinnedNotice").isNull()).isTrue();
		assertThat(json.get("myParticipationType").asText()).isEqualTo("NONE");
	}

	@Test
	void exposesOnlyConfirmedStatusAndParticipationValues() {
		assertThat(Set.of(RealtimeAuctionStatus.values()))
			.containsExactlyInAnyOrder(
				RealtimeAuctionStatus.WAITING,
				RealtimeAuctionStatus.LIVE,
				RealtimeAuctionStatus.ENDED
			);
		assertThat(Set.of(RealtimeParticipationType.values()))
			.containsExactlyInAnyOrder(
				RealtimeParticipationType.NONE,
				RealtimeParticipationType.BID,
				RealtimeParticipationType.PREDICTION
			);
	}

	@Test
	void rejectsInvalidRequiredAndCountFields() {
		assertThatThrownBy(() -> new RealtimeAuctionStateResponse(
			-1L,
			RealtimeAuctionStatus.LIVE,
			OffsetDateTime.parse("2026-07-20T21:15:00+09:00"),
			431L,
			1,
			2,
			7L,
			5L,
			23L,
			null,
			null,
			null,
			RealtimeParticipationType.NONE,
			null,
			false,
			null
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("stateVersion");

		assertThatThrownBy(() -> response(
			null,
			null,
			null,
			null,
			null
		)).isInstanceOf(NullPointerException.class)
			.hasMessageContaining("myParticipationType");
	}

	@Test
	void rejectsBlankPinnedNoticeFields() {
		assertThatThrownBy(() -> new PinnedNoticeResponse(" ", "공지"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messageId");
		assertThatThrownBy(() -> new PinnedNoticeResponse("01J8NOTICE", " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("content");
	}

	private static RealtimeAuctionStateResponse response(
		Long myBidAmount,
		Long myPredictedPrice,
		Long myStakeAmount,
		RealtimeParticipationType participationType,
		PinnedNoticeResponse pinnedNotice
	) {
		return new RealtimeAuctionStateResponse(
			184L,
			RealtimeAuctionStatus.LIVE,
			OffsetDateTime.parse("2026-07-20T21:15:00+09:00"),
			431L,
			1,
			2,
			7L,
			5L,
			23L,
			myBidAmount,
			myPredictedPrice,
			myStakeAmount,
			participationType,
			pinnedNotice,
			false,
			null
		);
	}

	@Test
	void rejectsInconsistentMuteState() {
		assertThatThrownBy(() -> new RealtimeAuctionStateResponse(
			184L,
			RealtimeAuctionStatus.LIVE,
			OffsetDateTime.parse("2026-07-20T21:15:00+09:00"),
			431L,
			1,
			2,
			7L,
			5L,
			23L,
			null,
			null,
			null,
			RealtimeParticipationType.NONE,
			null,
			false,
			OffsetDateTime.parse("2026-07-20T21:16:00+09:00")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("amIMuted");
	}

	@Test
	void serializesMuteExpirationAsIso8601() {
		OffsetDateTime mutedUntil =
			OffsetDateTime.parse("2026-07-20T21:16:00+09:00");
		RealtimeAuctionStateResponse response = new RealtimeAuctionStateResponse(
			184L,
			RealtimeAuctionStatus.LIVE,
			OffsetDateTime.parse("2026-07-20T21:15:00+09:00"),
			431L,
			1,
			2,
			7L,
			5L,
			23L,
			null,
			null,
			null,
			RealtimeParticipationType.NONE,
			null,
			true,
			mutedUntil
		);

		JsonNode json = objectMapper.valueToTree(response);

		assertThat(json.get("mutedUntil").asText())
			.isEqualTo("2026-07-20T21:16:00+09:00");
	}
}
