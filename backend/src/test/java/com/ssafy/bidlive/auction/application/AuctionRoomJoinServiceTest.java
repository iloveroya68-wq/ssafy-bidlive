package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 입장 판정 순서, 상태별 권한·destination, 접속자 등록을 하지 않는다는 것을 검증한다. */
class AuctionRoomJoinServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-20T11:59:30Z");
	private static final Instant SCHEDULED_AT = Instant.parse("2026-07-20T12:00:00Z");
	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final long VIEWER_ID = 77L;

	private AuctionRoomJoinPersistence persistence;
	private MemberPersistence memberPersistence;
	private CatchUpSummaryAvailability catchUpSummaryAvailability;
	private AuctionRoomJoinService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomJoinPersistence.class);
		memberPersistence = mock(MemberPersistence.class);
		catchUpSummaryAvailability = mock(CatchUpSummaryAvailability.class);
		service = new AuctionRoomJoinService(
			persistence,
			memberPersistence,
			catchUpSummaryAvailability,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		givenMemberStatus(MemberStatus.ACTIVE);
		givenTarget(target("WAITING", "PUBLIC", false));
	}

	/** 대기방은 채팅만 열리고 구독 destination 셋을 준다 */
	@Test
	void returnsWaitingRoomWithChatOnlyAndDestinations() {
		AuctionRoomJoinResult result = service.joinRoom(AUCTION_ID, VIEWER_ID);

		assertThat(result.roomStatus()).isEqualTo(AuctionStatus.WAITING);
		assertThat(result.serverTime()).isEqualTo(NOW);
		assertThat(result.scheduledAt()).isEqualTo(SCHEDULED_AT);
		assertThat(result.chatAllowed()).isTrue();
		assertThat(result.bidAllowed()).isFalse();
		assertThat(result.predictionAllowed()).isFalse();
		assertThat(result.roomDestination()).isEqualTo("/topic/auctions/100");
		assertThat(result.chatDestination()).isEqualTo("/topic/auctions/100/chat");
		assertThat(result.viewersDestination()).isEqualTo("/topic/auctions/100/viewers");
		assertThat(result.catchUpSummaryAvailable()).isNull();
	}

	/** 진행 중 입장은 셋 다 열리고 destination 대신 요약 가능 여부를 준다 */
	@Test
	void returnsLiveRoomWithAllPermissions() {
		givenTarget(target("LIVE", "PUBLIC", false));

		AuctionRoomJoinResult result = service.joinRoom(AUCTION_ID, VIEWER_ID);

		assertThat(result.roomStatus()).isEqualTo(AuctionStatus.LIVE);
		assertThat(result.chatAllowed()).isTrue();
		assertThat(result.bidAllowed()).isTrue();
		assertThat(result.predictionAllowed()).isTrue();
		assertThat(result.scheduledAt()).isNull();
		assertThat(result.roomDestination()).isNull();
		// 아직 이벤트·STT 소스가 없으면 조회 버튼을 열지 않는다.
		assertThat(result.catchUpSummaryAvailable()).isFalse();
	}

	@Test
	void announcesCatchUpSummaryWhenRoomHasSummarySource() {
		givenTarget(target("LIVE", "PUBLIC", false));
		when(catchUpSummaryAvailability.isAvailable(AUCTION_ID)).thenReturn(true);

		assertThat(service.joinRoom(AUCTION_ID, VIEWER_ID).catchUpSummaryAvailable())
			.isTrue();
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, null))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomJoinErrorCode.UNAUTHORIZED));
		verify(persistence, never()).findJoinTarget(anyLong());
	}

	@Test
	void rejectsMissingOrDeletedRoom() {
		when(persistence.findJoinTarget(AUCTION_ID)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND));

		givenTarget(target("WAITING", "PUBLIC", true));
		assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND));
	}

	/**
	 * 비공개 방은 404로 감춘다 — 403을 주면 목록·상세가 감춘 "그 방이 존재한다"가 드러난다.
	 * 명세에 403 `ROOM_ACCESS_DENIED`가 있지만 그건 회원 상태로 막는 경우다.
	 */
	@Test
	void hidesPrivateRoomFromNonSellerWithNotFound() {
		givenTarget(target("WAITING", "PRIVATE", false));

		assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND));
	}

	@Test
	void allowsSellerToEnterOwnPrivateRoom() {
		givenTarget(target("WAITING", "PRIVATE", false));

		assertThat(service.joinRoom(AUCTION_ID, SELLER_ID).roomStatus())
			.isEqualTo(AuctionStatus.WAITING);
	}

	/** 회원 상태로 막는 것은 방의 존재와 무관하므로 403이다 */
	@Test
	void rejectsSuspendedOrWithdrawnMemberWithAccessDenied() {
		for (MemberStatus status : new MemberStatus[] {
			MemberStatus.SUSPENDED, MemberStatus.WITHDRAWN
		}) {
			givenMemberStatus(status);

			assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
				.satisfies(e -> assertThat(codeOf(e))
					.isEqualTo(AuctionRoomJoinErrorCode.ROOM_ACCESS_DENIED));
		}
	}

	@Test
	void rejectsUnknownMemberWithAccessDenied() {
		when(memberPersistence.findById(VIEWER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomJoinErrorCode.ROOM_ACCESS_DENIED));
	}

	/** 종료된 방과, DB 제약이 없어 들어온 알 수 없는 상태를 같이 막는다 */
	@Test
	void rejectsEndedOrUnknownStatus() {
		for (String status : new String[] {"ENDED", "CANCELLED"}) {
			givenTarget(target(status, "PUBLIC", false));

			assertThatThrownBy(() -> service.joinRoom(AUCTION_ID, VIEWER_ID))
				.satisfies(e -> assertThat(codeOf(e))
					.isEqualTo(AuctionRoomJoinErrorCode.AUCTION_ALREADY_ENDED));
		}
	}

	/**
	 * 접속자 집계는 STOMP 구독이 한다(249). 이 API가 등록을 흉내 내면 REST 재호출마다 수가 늘어
	 * 명세의 "중복 입장이 접속자 수를 중복 증가시키지 않는다"가 깨진다 — 아무것도 쓰지 않는 것이
	 * 이 서비스의 계약이다.
	 */
	@Test
	void writesNothingSoRepeatedJoinsCannotInflateViewerCount() {
		service.joinRoom(AUCTION_ID, VIEWER_ID);
		service.joinRoom(AUCTION_ID, VIEWER_ID);
		service.joinRoom(AUCTION_ID, VIEWER_ID);

		// 세 번 불러도 일어난 일은 조회뿐이다 — 쓰기 경로가 없다
		verify(persistence, org.mockito.Mockito.times(3)).findJoinTarget(AUCTION_ID);
		org.mockito.Mockito.verifyNoMoreInteractions(persistence);
	}

	private void givenTarget(AuctionRoomJoinTarget target) {
		when(persistence.findJoinTarget(AUCTION_ID)).thenReturn(Optional.of(target));
	}

	private void givenMemberStatus(MemberStatus status) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		when(memberPersistence.findById(anyLong())).thenReturn(Optional.of(member));
	}

	private static AuctionRoomJoinTarget target(
		String status,
		String visibility,
		boolean deleted
	) {
		return new AuctionRoomJoinTarget(
			AUCTION_ID, SELLER_ID, status, visibility, deleted, SCHEDULED_AT
		);
	}

	private static AuctionRoomJoinErrorCode codeOf(Throwable exception) {
		return ((AuctionRoomJoinException) exception).getErrorCode();
	}
}
