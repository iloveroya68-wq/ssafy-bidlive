package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.INVALID_MESSAGE_ID;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.MESSAGE_NOT_FOUND;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.NOT_AUCTION_HOST;
import static com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.NoticePinnedEvent;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자의 고정 공지 등록·교체·멱등 해제를 트랜잭션으로 조율합니다. */
@Service
public class PinnedNoticeCommandService {

	private final PinnedNoticePersistence persistence;
	private final AuctionOwnershipReader ownershipReader;
	private final AuctionRoomDetailPersistence auctionReader;
	private final ApplicationEventPublisher eventPublisher;

	public PinnedNoticeCommandService(
		PinnedNoticePersistence persistence,
		AuctionOwnershipReader ownershipReader,
		AuctionRoomDetailPersistence auctionReader,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.ownershipReader = ownershipReader;
		this.auctionReader = auctionReader;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public PinnedNotice pin(
		long auctionId,
		Long memberId,
		ChatMessageId messageId
	) {
		validateAuctionId(auctionId);
		if (messageId == null) {
			throw new PinnedNoticeException(INVALID_MESSAGE_ID);
		}
		long authenticatedMemberId = requireMemberId(memberId);
		if (!ownershipReader.isSeller(auctionId, authenticatedMemberId)) {
			throw new PinnedNoticeException(NOT_AUCTION_HOST);
		}

		persistence.lockAuction(auctionId);
		PinnedNotice target = persistence.findMessageForPin(auctionId, messageId)
			.orElseThrow(() -> new PinnedNoticeException(MESSAGE_NOT_FOUND));
		if (persistence.findPinned(auctionId)
			.map(PinnedNotice::messageId)
			.filter(target.messageId()::equals)
			.isPresent()) {
			return target;
		}
		if (!persistence.replacePinned(auctionId, messageId)) {
			throw new PinnedNoticeException(MESSAGE_NOT_FOUND);
		}

		eventPublisher.publishEvent(new NoticePinnedEvent(auctionId, target));
		return target;
	}

	@Transactional
	public void unpin(long auctionId, Long memberId) {
		validateAuctionId(auctionId);
		long authenticatedMemberId = requireMemberId(memberId);
		boolean auctionExists = auctionReader.findByAuctionId(auctionId)
			.filter(room -> room.deletedAt() == null)
			.isPresent();
		if (!auctionExists) {
			throw new PinnedNoticeException(AUCTION_NOT_FOUND);
		}
		if (!ownershipReader.isSeller(auctionId, authenticatedMemberId)) {
			throw new PinnedNoticeException(NOT_AUCTION_HOST);
		}

		persistence.lockAuction(auctionId);
		if (persistence.clearPinned(auctionId)) {
			eventPublisher.publishEvent(new NoticeUnpinnedEvent(auctionId));
		}
	}

	private static void validateAuctionId(long auctionId) {
		if (auctionId <= 0) {
			throw new PinnedNoticeException(INVALID_AUCTION_ID);
		}
	}

	private static long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new PinnedNoticeException(UNAUTHORIZED);
		}
		return memberId;
	}
}
