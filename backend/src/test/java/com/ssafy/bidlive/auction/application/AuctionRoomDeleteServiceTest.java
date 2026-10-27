package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 삭제 판정 순서와 거절 사유별 오류 코드를 검증한다. */
class AuctionRoomDeleteServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;

	private AuctionRoomDeletePersistence persistence;
	private AuctionRoomDeleteService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomDeletePersistence.class);
		service = new AuctionRoomDeleteService(persistence);
		when(persistence.markDeleted(anyLong())).thenReturn(1);
	}

	@Test
	void marksRoomDeletedWhenSellerDeletesWaitingRoomWithoutParticipation() {
		givenTarget(target("WAITING", false, false));

		service.deleteRoom(AUCTION_ID, SELLER_ID);

		verify(persistence).markDeleted(AUCTION_ID);
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, null))
			.isInstanceOf(AuctionRoomDeleteException.class)
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.UNAUTHORIZED));
		verify(persistence, never()).findDeletionTarget(anyLong());
	}

	@Test
	void rejectsMissingRoom() {
		when(persistence.findDeletionTarget(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND));
	}

	/**
	 * 이미 삭제된 방은 판매자 검사보다 먼저 404다 — 남이 지운 방의 판매자 정보를 흘리지 않고,
	 * 두 번째 삭제에 204를 주면 호출자가 "방금 방이 있었다"로 읽는다.
	 */
	@Test
	void rejectsAlreadyDeletedRoomAsNotFoundEvenForOtherMembers() {
		givenTarget(target("WAITING", true, false));

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, 999L))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND));
		verify(persistence, never()).markDeleted(anyLong());
	}

	@Test
	void rejectsMemberWhoIsNotTheSeller() {
		givenTarget(target("WAITING", false, false));

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, 999L))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.NOT_AUCTION_SELLER));
		verify(persistence, never()).markDeleted(anyLong());
	}

	/**
	 * 비공개 방을 남이 지우려 하면 403이 아니라 404다 (S15P11A704-482) — 403을 주면 상세·목록이
	 * 감춘 "그 방이 존재한다"는 사실이 드러난다.
	 */
	@Test
	void hidesPrivateRoomFromNonSellerWithNotFound() {
		givenTarget(target("WAITING", "PRIVATE", false, false));

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, 999L))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND));
	}

	/** 반면 판매자 본인은 자기 비공개 방을 지울 수 있다 — visibility는 삭제를 막는 조건이 아니다 */
	@Test
	void allowsSellerToDeleteOwnPrivateRoom() {
		givenTarget(target("WAITING", "PRIVATE", false, false));

		service.deleteRoom(AUCTION_ID, SELLER_ID);

		verify(persistence).markDeleted(AUCTION_ID);
	}

	@Test
	void rejectsStartedOrEndedRoom() {
		for (String status : new String[] {"LIVE", "ENDED"}) {
			givenTarget(target(status, false, false));

			assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, SELLER_ID))
				.satisfies(e -> assertThat(codeOf(e))
					.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_ALREADY_STARTED));
		}
	}

	/** `auction_room.status`에 DB 제약이 없어 알 수 없는 값이 올 수 있다 — 보수적으로 막는다 */
	@Test
	void rejectsUnknownStatusConservatively() {
		givenTarget(target("CANCELLED", false, false));

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_ALREADY_STARTED));
	}

	@Test
	void rejectsRoomThatAlreadyHasParticipation() {
		givenTarget(target("WAITING", false, true));

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_HAS_PARTICIPATION));
		verify(persistence, never()).markDeleted(anyLong());
	}

	/** 판정과 삭제 사이에 다른 요청이 먼저 지운 경우 */
	@Test
	void rejectsWhenAnotherRequestDeletedItFirst() {
		givenTarget(target("WAITING", false, false));
		when(persistence.markDeleted(AUCTION_ID)).thenReturn(0);

		assertThatThrownBy(() -> service.deleteRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND));
	}

	private void givenTarget(AuctionRoomDeletionTarget target) {
		when(persistence.findDeletionTarget(AUCTION_ID)).thenReturn(Optional.of(target));
	}

	private static AuctionRoomDeletionTarget target(
		String status,
		boolean deleted,
		boolean hasParticipation
	) {
		return target(status, "PUBLIC", deleted, hasParticipation);
	}

	private static AuctionRoomDeletionTarget target(
		String status,
		String visibility,
		boolean deleted,
		boolean hasParticipation
	) {
		return new AuctionRoomDeletionTarget(
			AUCTION_ID,
			SELLER_ID,
			status,
			visibility,
			deleted,
			hasParticipation
		);
	}

	private static AuctionRoomDeleteErrorCode codeOf(Throwable exception) {
		return ((AuctionRoomDeleteException) exception).getErrorCode();
	}
}
