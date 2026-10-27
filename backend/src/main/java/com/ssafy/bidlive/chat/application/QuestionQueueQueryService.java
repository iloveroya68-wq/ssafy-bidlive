package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.INVALID_CURSOR;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.INVALID_PAGE_SIZE;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.NOT_AUCTION_HOST;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.UNAUTHORIZED;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 권한·커서·페이지 크기를 검증하고 PENDING 질문 페이지를 조회한다. */
@Service
public class QuestionQueueQueryService {

	static final int DEFAULT_PAGE_SIZE = 20;
	static final int MAX_PAGE_SIZE = 100;

	private final QuestionQueuePersistence persistence;
	private final AuctionOwnershipReader ownershipReader;

	public QuestionQueueQueryService(
		QuestionQueuePersistence persistence,
		AuctionOwnershipReader ownershipReader
	) {
		this.persistence = persistence;
		this.ownershipReader = ownershipReader;
	}

	@Transactional(readOnly = true)
	public QuestionQueuePage getPendingQuestions(
		long auctionId,
		Long memberId,
		String cursor,
		Integer size
	) {
		validateAuctionId(auctionId);
		long authenticatedMemberId = requireMemberId(memberId);
		if (!ownershipReader.isSeller(auctionId, authenticatedMemberId)) {
			throw new QuestionQueueException(NOT_AUCTION_HOST);
		}

		int pageSize = normalizePageSize(size);
		Optional<QuestionQueueCursor> resolvedCursor = resolveCursor(
			auctionId,
			cursor
		);
		List<QuestionQueueItem> fetched = persistence.findPendingAfter(
			auctionId,
			resolvedCursor,
			pageSize + 1
		);
		boolean hasNext = fetched.size() > pageSize;
		List<QuestionQueueItem> items = hasNext
			? List.copyOf(fetched.subList(0, pageSize))
			: List.copyOf(fetched);
		String nextCursor = items.isEmpty()
			? null
			: items.getLast().questionId().value();
		return new QuestionQueuePage(items, nextCursor, hasNext);
	}

	private Optional<QuestionQueueCursor> resolveCursor(
		long auctionId,
		String cursor
	) {
		if (cursor == null || cursor.isBlank()) {
			return Optional.empty();
		}
		ChatMessageId questionId;
		try {
			questionId = new ChatMessageId(cursor.strip());
		} catch (IllegalArgumentException exception) {
			throw new QuestionQueueException(INVALID_CURSOR, exception);
		}
		return Optional.of(persistence.findCursor(auctionId, questionId)
			.orElseThrow(() -> new QuestionQueueException(INVALID_CURSOR)));
	}

	private static int normalizePageSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new QuestionQueueException(INVALID_PAGE_SIZE);
		}
		return size;
	}

	private static void validateAuctionId(long auctionId) {
		if (auctionId <= 0) {
			throw new QuestionQueueException(INVALID_AUCTION_ID);
		}
	}

	private static long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new QuestionQueueException(UNAUTHORIZED);
		}
		return memberId;
	}
}
