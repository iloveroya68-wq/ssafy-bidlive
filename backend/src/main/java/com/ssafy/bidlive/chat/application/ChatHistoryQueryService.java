package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_CURSOR;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.INVALID_PAGE_SIZE;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.ROOM_ACCESS_DENIED;
import static com.ssafy.bidlive.chat.application.ChatHistoryErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 경매방 접근 권한을 확인하고 채팅 이력 커서 조회를 조율한다. */
@Service
public class ChatHistoryQueryService {

	static final int DEFAULT_PAGE_SIZE = 30;
	static final int MAX_PAGE_SIZE = 100;
	private static final String BLOCKED_MODERATION_STATUS = "BLOCKED";

	private final ChatHistoryPersistence persistence;
	private final AuctionRoomDetailPersistence auctionPersistence;
	private final MemberPersistence memberPersistence;

	public ChatHistoryQueryService(
		ChatHistoryPersistence persistence,
		AuctionRoomDetailPersistence auctionPersistence,
		MemberPersistence memberPersistence
	) {
		this.persistence = persistence;
		this.auctionPersistence = auctionPersistence;
		this.memberPersistence = memberPersistence;
	}

	@Transactional(readOnly = true)
	public ChatHistoryPage getHistory(
		long auctionId,
		Long memberId,
		String before,
		Integer size
	) {
		long authenticatedMemberId = requireMemberId(memberId);
		validateAuctionId(auctionId);
		ChatMessageId cursor = parseCursor(before);
		int pageSize = normalizeSize(size);
		verifyRoomAccess(auctionId, authenticatedMemberId);
		return persistence.findBefore(auctionId, cursor, pageSize)
			.orElseThrow(() -> new ChatHistoryException(INVALID_CURSOR));
	}

	private void verifyRoomAccess(long auctionId, long memberId) {
		AuctionRoomDetailRow room = auctionPersistence.findByAuctionId(auctionId)
			.filter(found -> found.deletedAt() == null)
			.filter(found -> !BLOCKED_MODERATION_STATUS.equals(
				found.productModerationStatus()
			))
			.orElseThrow(() -> new ChatHistoryException(AUCTION_NOT_FOUND));

		if (!AuctionRoomVisibility.isPublic(room.visibility())
			&& room.sellerId() != memberId) {
			throw new ChatHistoryException(AUCTION_NOT_FOUND);
		}

		memberPersistence.findById(memberId)
			.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new ChatHistoryException(ROOM_ACCESS_DENIED));
	}

	private static long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new ChatHistoryException(UNAUTHORIZED);
		}
		return memberId;
	}

	private static void validateAuctionId(long auctionId) {
		if (auctionId <= 0) {
			throw new ChatHistoryException(INVALID_AUCTION_ID);
		}
	}

	private static ChatMessageId parseCursor(String before) {
		if (before == null) {
			return null;
		}
		try {
			return new ChatMessageId(before);
		} catch (IllegalArgumentException exception) {
			throw new ChatHistoryException(INVALID_CURSOR, exception);
		}
	}

	private static int normalizeSize(Integer size) {
		int normalized = size == null ? DEFAULT_PAGE_SIZE : size;
		if (normalized < 1 || normalized > MAX_PAGE_SIZE) {
			throw new ChatHistoryException(INVALID_PAGE_SIZE);
		}
		return normalized;
	}
}
