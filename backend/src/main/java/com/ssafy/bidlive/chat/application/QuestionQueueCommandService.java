package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.NOT_AUCTION_HOST;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.QUESTION_NOT_FOUND;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 권한을 검증하고 PENDING 질문을 멱등하게 RESOLVED로 전이한다. */
@Service
public class QuestionQueueCommandService {

	private final QuestionQueuePersistence persistence;
	private final AuctionOwnershipReader ownershipReader;
	private final ApplicationEventPublisher eventPublisher;

	public QuestionQueueCommandService(
		QuestionQueuePersistence persistence,
		AuctionOwnershipReader ownershipReader,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.ownershipReader = ownershipReader;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public void resolve(
		long auctionId,
		Long memberId,
		ChatMessageId questionId
	) {
		validateAuctionId(auctionId);
		long authenticatedMemberId = requireMemberId(memberId);
		if (questionId == null) {
			throw new QuestionQueueException(QUESTION_NOT_FOUND);
		}
		if (!ownershipReader.isSeller(auctionId, authenticatedMemberId)) {
			throw new QuestionQueueException(NOT_AUCTION_HOST);
		}

		QuestionResolutionResult result = persistence.resolvePending(
			auctionId,
			questionId
		);
		switch (result) {
			case RESOLVED -> eventPublisher.publishEvent(
				new QuestionRemovedEvent(
					auctionId,
					questionId,
					QuestionRemovalReason.RESOLVED
				)
			);
			case ALREADY_HANDLED -> {
				// 멱등 성공: 상태와 이벤트를 다시 변경하지 않는다.
			}
			case NOT_FOUND -> throw new QuestionQueueException(
				QUESTION_NOT_FOUND
			);
		}
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
