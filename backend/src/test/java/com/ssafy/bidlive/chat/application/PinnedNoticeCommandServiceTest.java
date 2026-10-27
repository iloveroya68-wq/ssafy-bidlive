package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.NoticePinnedEvent;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 고정 공지 등록·교체·멱등 해제와 판매자 권한 정책을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class PinnedNoticeCommandServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long SELLER_ID = 7L;
	private static final ChatMessageId MESSAGE_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ABC"
	);

	@Mock
	private PinnedNoticePersistence persistence;
	@Mock
	private AuctionOwnershipReader ownershipReader;
	@Mock
	private AuctionRoomDetailPersistence auctionReader;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private PinnedNoticeCommandService service;

	@BeforeEach
	void setUp() {
		service = new PinnedNoticeCommandService(
			persistence,
			ownershipReader,
			auctionReader,
			eventPublisher
		);
	}

	@Test
	void pinsMessageAndPublishesChangeEvent() {
		PinnedNotice notice = new PinnedNotice(
			MESSAGE_ID.value(),
			"배송비 포함입니다."
		);
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
		when(persistence.findMessageForPin(AUCTION_ID, MESSAGE_ID))
			.thenReturn(Optional.of(notice));
		when(persistence.findPinned(AUCTION_ID)).thenReturn(Optional.empty());
		when(persistence.replacePinned(AUCTION_ID, MESSAGE_ID)).thenReturn(true);

		assertThat(service.pin(AUCTION_ID, SELLER_ID, MESSAGE_ID))
			.isEqualTo(notice);
		verify(persistence).lockAuction(AUCTION_ID);
		verify(persistence).replacePinned(AUCTION_ID, MESSAGE_ID);
		verify(eventPublisher).publishEvent(new NoticePinnedEvent(AUCTION_ID, notice));
	}

	@Test
	void treatsSamePinnedMessageAsIdempotent() {
		PinnedNotice notice = new PinnedNotice(
			MESSAGE_ID.value(),
			"배송비 포함입니다."
		);
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
		when(persistence.findMessageForPin(AUCTION_ID, MESSAGE_ID))
			.thenReturn(Optional.of(notice));
		when(persistence.findPinned(AUCTION_ID)).thenReturn(Optional.of(notice));

		assertThat(service.pin(AUCTION_ID, SELLER_ID, MESSAGE_ID))
			.isEqualTo(notice);
		verify(persistence, never()).replacePinned(AUCTION_ID, MESSAGE_ID);
		verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsMessageFromAnotherAuctionOrDeletedMessage() {
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
		when(persistence.findMessageForPin(AUCTION_ID, MESSAGE_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.pin(AUCTION_ID, SELLER_ID, MESSAGE_ID))
			.isInstanceOfSatisfying(PinnedNoticeException.class, exception ->
				assertThat(exception.errorCode())
					.isEqualTo(PinnedNoticeErrorCode.MESSAGE_NOT_FOUND));
		verify(persistence, never()).replacePinned(AUCTION_ID, MESSAGE_ID);
	}

	@Test
	void rejectsNonSellerBeforeReadingMessage() {
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.pin(AUCTION_ID, SELLER_ID, MESSAGE_ID))
			.isInstanceOfSatisfying(PinnedNoticeException.class, exception ->
				assertThat(exception.errorCode())
					.isEqualTo(PinnedNoticeErrorCode.NOT_AUCTION_HOST));
		verify(persistence, never()).lockAuction(AUCTION_ID);
	}

	@Test
	void rejectsNullMessageIdBeforeCheckingOwnership() {
		assertThatThrownBy(() -> service.pin(AUCTION_ID, SELLER_ID, null))
			.isInstanceOfSatisfying(PinnedNoticeException.class, exception ->
				assertThat(exception.errorCode())
					.isEqualTo(PinnedNoticeErrorCode.INVALID_MESSAGE_ID));

		verify(ownershipReader, never()).isSeller(AUCTION_ID, SELLER_ID);
	}

	@Test
	void unpinsExistingNoticeAndPublishesEvent() {
		when(auctionReader.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room()));
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
		when(persistence.clearPinned(AUCTION_ID)).thenReturn(true);

		service.unpin(AUCTION_ID, SELLER_ID);

		verify(persistence).lockAuction(AUCTION_ID);
		verify(eventPublisher).publishEvent(new NoticeUnpinnedEvent(AUCTION_ID));
	}

	@Test
	void unpinWithoutNoticeSucceedsWithoutEvent() {
		when(auctionReader.findByAuctionId(AUCTION_ID))
			.thenReturn(Optional.of(room()));
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
		when(persistence.clearPinned(AUCTION_ID)).thenReturn(false);

		service.unpin(AUCTION_ID, SELLER_ID);

		verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void distinguishesMissingAuctionWhenUnpinning() {
		when(auctionReader.findByAuctionId(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.unpin(AUCTION_ID, SELLER_ID))
			.isInstanceOfSatisfying(PinnedNoticeException.class, exception ->
				assertThat(exception.errorCode())
					.isEqualTo(PinnedNoticeErrorCode.AUCTION_NOT_FOUND));
		verify(ownershipReader, never()).isSeller(AUCTION_ID, SELLER_ID);
	}

	private static AuctionRoomDetailRow room() {
		return new AuctionRoomDetailRow(
			AUCTION_ID,
			SELLER_ID,
			"경매",
			com.ssafy.bidlive.auction.domain.AuctionStatus.LIVE,
			10_000L,
			null,
			30,
			"PUBLIC",
			null,
			1L,
			"상품",
			"설명",
			"ELECTRONICS",
			null,
			"PASSED",
			"판매자",
			0
		);
	}
}
