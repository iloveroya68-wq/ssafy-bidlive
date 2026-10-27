package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import com.ssafy.bidlive.media.domain.LiveSessionToken;
import com.ssafy.bidlive.media.domain.LiveTokenFailureCode;
import org.junit.jupiter.api.Test;

/**
 * 송출은 방 판매자만, 시청은 공개 방이거나 판매자 본인만 토큰을 받는지와
 * 발급이 세션 확보·방송 개시 기록까지 이어지는지 검증합니다.
 */
class LiveSessionServiceTest {

	private static final String ROOM_ID = "102";
	private static final Long SELLER_ID = 25L;
	private static final Long OTHER_MEMBER_ID = 77L;
	private static final Clock FIXED_CLOCK =
		Clock.fixed(Instant.parse("2026-07-20T12:05:00Z"), ZoneId.of("Asia/Seoul"));

	private final RecordingLiveSessionGateway gateway = new RecordingLiveSessionGateway();
	private final RecordingLiveBroadcastRepository liveBroadcastRepository =
		new RecordingLiveBroadcastRepository();
	private final StubRoomAccessReader roomAccessReader = new StubRoomAccessReader();
	private final LiveSessionService liveSessionService =
		new LiveSessionService(gateway, liveBroadcastRepository, roomAccessReader, FIXED_CLOCK);

	@Test
	void issuesPublisherTokenForSessionDerivedFromRoomId() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		LiveSessionToken sessionToken =
			liveSessionService.issuePublisherToken(ROOM_ID, SELLER_ID);

		assertThat(sessionToken.roomId()).isEqualTo(ROOM_ID);
		assertThat(sessionToken.sessionId()).isEqualTo(ROOM_ID);
		assertThat(sessionToken.token()).isEqualTo("token-PUBLISHER");
		assertThat(sessionToken.role()).isEqualTo(LiveSessionRole.PUBLISHER);
		assertThat(sessionToken.createdAt())
			.isEqualTo(OffsetDateTime.parse("2026-07-20T21:05:00+09:00"));
		assertThat(gateway.calls)
			.containsExactly("ensureSession:102", "issueToken:102:PUBLISHER");
	}

	/** LIVE-02가 종료 대상과 종료 권한자를 찾을 수 있게 발급 시각이 방송 시작 시각으로 남는다. */
	@Test
	void recordsBroadcastStartWhenPublisherTokenIsIssued() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		liveSessionService.issuePublisherToken(ROOM_ID, SELLER_ID);

		assertThat(liveBroadcastRepository.calls)
			.containsExactly("startBroadcast:102:25:2026-07-20T21:05+09:00");
	}

	/**
	 * 판매자가 아닌 회원은 토큰도 받지 못하고 방송 개시 행도 선점하지 못해야 한다.
	 * 선점되면 실제 판매자의 LIVE-02 종료가 `NOT_BROADCAST_OWNER`로 막힌다.
	 */
	@Test
	void rejectsPublisherTokenForMemberWhoIsNotSeller() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		assertThatThrownBy(() -> liveSessionService.issuePublisherToken(ROOM_ID, OTHER_MEMBER_ID))
			.isInstanceOf(LiveTokenRejectedException.class)
			.extracting(exception -> ((LiveTokenRejectedException) exception).getFailureCode())
			.isEqualTo(LiveTokenFailureCode.NOT_AUCTION_SELLER);

		assertThat(gateway.calls).isEmpty();
		assertThat(liveBroadcastRepository.calls).isEmpty();
	}

	@Test
	void issuesSubscriberTokenForAnyMemberOnPublicRoom() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		LiveSessionToken sessionToken =
			liveSessionService.issueSubscriberToken(ROOM_ID, OTHER_MEMBER_ID);

		assertThat(sessionToken.sessionId()).isEqualTo(ROOM_ID);
		assertThat(sessionToken.token()).isEqualTo("token-SUBSCRIBER");
		assertThat(sessionToken.role()).isEqualTo(LiveSessionRole.SUBSCRIBER);
		assertThat(liveBroadcastRepository.calls).isEmpty();
	}

	/** 비공개 방은 존재 자체를 감춘다 — 403이 아니라 조회 실패와 같은 코드로 거절한다. */
	@Test
	void hidesPrivateRoomFromMemberWhoIsNotSeller() {
		roomAccessReader.privateRoomOwnedBy(SELLER_ID);

		assertThatThrownBy(() -> liveSessionService.issueSubscriberToken(ROOM_ID, OTHER_MEMBER_ID))
			.isInstanceOf(LiveTokenRejectedException.class)
			.extracting(exception -> ((LiveTokenRejectedException) exception).getFailureCode())
			.isEqualTo(LiveTokenFailureCode.ROOM_NOT_FOUND);

		assertThat(gateway.calls).isEmpty();
	}

	@Test
	void issuesSubscriberTokenForSellerOfPrivateRoom() {
		roomAccessReader.privateRoomOwnedBy(SELLER_ID);

		LiveSessionToken sessionToken =
			liveSessionService.issueSubscriberToken(ROOM_ID, SELLER_ID);

		assertThat(sessionToken.role()).isEqualTo(LiveSessionRole.SUBSCRIBER);
	}

	@Test
	void stampsCreatedAtInServiceZoneWhenUsingSystemClock() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		LiveSessionToken sessionToken =
			new LiveSessionService(gateway, liveBroadcastRepository, roomAccessReader)
				.issuePublisherToken(ROOM_ID, SELLER_ID);

		assertThat(sessionToken.createdAt().getOffset())
			.isEqualTo(ZoneId.of("Asia/Seoul").getRules().getOffset(Instant.now()));
	}

	/** roomId가 수치형 경매방 식별자가 아니면 조회 실패와 같게 다뤄 500으로 새지 않게 한다. */
	@Test
	void rejectsNonNumericRoomIdAsNotFound() {
		assertThatThrownBy(() -> liveSessionService.issueSubscriberToken("room-123", SELLER_ID))
			.isInstanceOf(LiveTokenRejectedException.class)
			.extracting(exception -> ((LiveTokenRejectedException) exception).getFailureCode())
			.isEqualTo(LiveTokenFailureCode.ROOM_NOT_FOUND);

		assertThat(gateway.calls).isEmpty();
	}

	@Test
	void rejectsBlankRoomIdBeforeCallingMediaServer() {
		assertThatThrownBy(() -> liveSessionService.issuePublisherToken(" ", SELLER_ID))
			.isInstanceOf(LiveTokenRejectedException.class);

		assertThat(gateway.calls).isEmpty();
		assertThat(liveBroadcastRepository.calls).isEmpty();
	}

	@Test
	void rejectsUnknownRoomBeforeCallingMediaServer() {
		assertThatThrownBy(() -> liveSessionService.issuePublisherToken(ROOM_ID, SELLER_ID))
			.isInstanceOf(LiveTokenRejectedException.class)
			.extracting(exception -> ((LiveTokenRejectedException) exception).getFailureCode())
			.isEqualTo(LiveTokenFailureCode.ROOM_NOT_FOUND);

		assertThat(gateway.calls).isEmpty();
		assertThat(liveBroadcastRepository.calls).isEmpty();
	}

	/** 삭제된 방도 존재하지 않는 방과 같게 다룬다. */
	@Test
	void rejectsDeletedRoom() {
		roomAccessReader.deletedRoomOwnedBy(SELLER_ID);

		assertThatThrownBy(() -> liveSessionService.issueSubscriberToken(ROOM_ID, SELLER_ID))
			.isInstanceOf(LiveTokenRejectedException.class)
			.extracting(exception -> ((LiveTokenRejectedException) exception).getFailureCode())
			.isEqualTo(LiveTokenFailureCode.ROOM_NOT_FOUND);
	}

	@Test
	void rejectsMissingBroadcasterBeforeCallingMediaServer() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		assertThatThrownBy(() -> liveSessionService.issuePublisherToken(ROOM_ID, null))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(gateway.calls).isEmpty();
		assertThat(liveBroadcastRepository.calls).isEmpty();
	}

	@Test
	void rejectsMissingViewerBeforeCallingMediaServer() {
		roomAccessReader.publicRoomOwnedBy(SELLER_ID);

		assertThatThrownBy(() -> liveSessionService.issueSubscriberToken(ROOM_ID, null))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(gateway.calls).isEmpty();
	}

	private static final class StubRoomAccessReader implements CatchUpRoomAccessReader {

		private final Map<Long, CatchUpRoomAccess> rooms = new HashMap<>();

		private void publicRoomOwnedBy(Long sellerId) {
			put(sellerId, "PUBLIC", false);
		}

		private void privateRoomOwnedBy(Long sellerId) {
			put(sellerId, "PRIVATE", false);
		}

		private void deletedRoomOwnedBy(Long sellerId) {
			put(sellerId, "PUBLIC", true);
		}

		private void put(Long sellerId, String visibility, boolean deleted) {
			long auctionId = Long.parseLong(ROOM_ID);
			rooms.put(auctionId, new CatchUpRoomAccess(
				auctionId,
				sellerId,
				"LIVE",
				visibility,
				deleted,
				null
			));
		}

		@Override
		public Optional<CatchUpRoomAccess> findAccess(long auctionId) {
			return Optional.ofNullable(rooms.get(auctionId));
		}
	}

	private static final class RecordingLiveSessionGateway implements LiveSessionGateway {

		private final List<String> calls = new ArrayList<>();

		@Override
		public String ensureSession(String roomId) {
			calls.add("ensureSession:" + roomId);
			return roomId;
		}

		@Override
		public String issueToken(String sessionId, LiveSessionRole role) {
			calls.add("issueToken:" + sessionId + ":" + role);
			return "token-" + role;
		}

		@Override
		public void closeSession(String sessionId) {
			calls.add("closeSession:" + sessionId);
		}
	}

	private static final class RecordingLiveBroadcastRepository implements LiveBroadcastRepository {

		private final List<String> calls = new ArrayList<>();

		@Override
		public Optional<LiveBroadcast> findByRoomId(String roomId) {
			calls.add("findByRoomId:" + roomId);
			return Optional.empty();
		}

		@Override
		public void startBroadcast(String roomId, Long broadcasterId, OffsetDateTime startedAt) {
			calls.add("startBroadcast:" + roomId + ":" + broadcasterId + ":" + startedAt);
		}

		@Override
		public boolean endBroadcast(String roomId, OffsetDateTime endedAt) {
			calls.add("endBroadcast:" + roomId + ":" + endedAt);
			return true;
		}
	}
}
