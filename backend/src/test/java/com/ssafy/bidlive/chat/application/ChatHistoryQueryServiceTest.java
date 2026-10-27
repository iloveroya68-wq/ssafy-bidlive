package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_CURSOR;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_PAGE_SIZE;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.ROOM_ACCESS_DENIED;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 채팅 이력 입력 검증과 경매 공개 범위·회원 상태별 접근 정책을 검증한다. */
class ChatHistoryQueryServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long MEMBER_ID = 7L;
	private static final String CURSOR =
		"01J8W3P2N9KQ4T123456789ABC";

	private ChatHistoryPersistence persistence;
	private AuctionRoomDetailPersistence auctionPersistence;
	private MemberPersistence memberPersistence;
	private ChatHistoryQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(ChatHistoryPersistence.class);
		auctionPersistence = mock(AuctionRoomDetailPersistence.class);
		memberPersistence = mock(MemberPersistence.class);
		service = new ChatHistoryQueryService(
			persistence,
			auctionPersistence,
			memberPersistence
		);
	}

	@Test
	void returnsHistoryForActiveMemberInPublicRoom() {
		ChatHistoryPage expected = new ChatHistoryPage(List.of(), false);
		allowPublicRoom(MemberStatus.ACTIVE);
		when(persistence.findBefore(
			AUCTION_ID,
			new ChatMessageId(CURSOR),
			20
		)).thenReturn(Optional.of(expected));

		ChatHistoryPage actual = service.getHistory(
			AUCTION_ID,
			MEMBER_ID,
			CURSOR,
			20
		);

		assertThat(actual).isSameAs(expected);
		verify(persistence).findBefore(
			AUCTION_ID,
			new ChatMessageId(CURSOR),
			20
		);
	}

	@Test
	void appliesDefaultPageSizeToFirstPage() {
		allowPublicRoom(MemberStatus.ACTIVE);
		when(persistence.findBefore(AUCTION_ID, null, 30))
			.thenReturn(Optional.of(new ChatHistoryPage(List.of(), false)));

		service.getHistory(AUCTION_ID, MEMBER_ID, null, null);

		verify(persistence).findBefore(AUCTION_ID, null, 30);
	}

	@Test
	void allowsActiveSellerToReadPrivateRoom() {
		Member activeMember = member(MemberStatus.ACTIVE);
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(MEMBER_ID, "PRIVATE", null, "PASSED")));
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(activeMember));
		when(persistence.findBefore(AUCTION_ID, null, 30))
			.thenReturn(Optional.of(new ChatHistoryPage(List.of(), false)));

		service.getHistory(AUCTION_ID, MEMBER_ID, null, null);

		verify(persistence).findBefore(AUCTION_ID, null, 30);
	}

	@Test
	void hidesPrivateRoomFromNonSeller() {
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(99L, "PRIVATE", null, "PASSED")));

		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			AUCTION_NOT_FOUND
		);
		verifyNoInteractions(memberPersistence, persistence);
	}

	@Test
	void hidesMissingDeletedAndBlockedRooms() {
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.empty());
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			AUCTION_NOT_FOUND
		);

		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(99L, "PUBLIC", java.time.Instant.EPOCH, "PASSED")));
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			AUCTION_NOT_FOUND
		);

		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(99L, "PUBLIC", null, "BLOCKED")));
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			AUCTION_NOT_FOUND
		);
	}

	@Test
	void deniesSuspendedWithdrawnAndMissingMembers() {
		Member suspendedMember = member(MemberStatus.SUSPENDED);
		Member withdrawnMember = member(MemberStatus.WITHDRAWN);
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(99L, "PUBLIC", null, "PASSED")));

		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(suspendedMember));
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			ROOM_ACCESS_DENIED
		);

		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(withdrawnMember));
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			ROOM_ACCESS_DENIED
		);

		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.empty());
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, null),
			ROOM_ACCESS_DENIED
		);
		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsUnauthenticatedAndInvalidInputsBeforePersistence() {
		assertError(
			() -> service.getHistory(AUCTION_ID, null, null, null),
			UNAUTHORIZED
		);
		assertError(
			() -> service.getHistory(0L, MEMBER_ID, null, null),
			INVALID_AUCTION_ID
		);
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, "invalid", null),
			INVALID_CURSOR
		);
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, 0),
			INVALID_PAGE_SIZE
		);
		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, null, 101),
			INVALID_PAGE_SIZE
		);
		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsCursorThatDoesNotBelongToAuctionHistory() {
		allowPublicRoom(MemberStatus.ACTIVE);
		when(persistence.findBefore(
			AUCTION_ID,
			new ChatMessageId(CURSOR),
			30
		)).thenReturn(Optional.empty());

		assertError(
			() -> service.getHistory(AUCTION_ID, MEMBER_ID, CURSOR, null),
			INVALID_CURSOR
		);
	}

	private void allowPublicRoom(MemberStatus status) {
		Member resolvedMember = member(status);
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room(99L, "PUBLIC", null, "PASSED")));
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(resolvedMember));
	}

	private static Member member(MemberStatus status) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		return member;
	}

	private static AuctionRoomDetailRow room(
		long sellerId,
		String visibility,
		java.time.Instant deletedAt,
		String moderationStatus
	) {
		return new AuctionRoomDetailRow(
			AUCTION_ID,
			sellerId,
			"경매방",
			AuctionStatus.LIVE,
			10_000L,
			null,
			30,
			visibility,
			deletedAt,
			11L,
			"상품",
			"설명",
			"ETC",
			"https://example.com/product.jpg",
			moderationStatus,
			"판매자",
			0
		);
	}

	private static void assertError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
		ChatHistoryErrorCode expected
	) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(ChatHistoryException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(expected)
			);
	}
}
