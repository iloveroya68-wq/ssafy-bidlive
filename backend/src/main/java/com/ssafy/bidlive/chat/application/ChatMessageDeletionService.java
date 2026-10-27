package com.ssafy.bidlive.chat.application;

import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.CHAT_DELETE_FORBIDDEN;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.INVALID_MESSAGE_ID;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.MESSAGE_NOT_FOUND;
import static com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode.UNAUTHORIZED;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 회원 권한으로 채팅 삭제를 검증하고 소프트 삭제와 후속 이벤트를 조율한다. */
@Service
public class ChatMessageDeletionService {

	private final ChatMessageDeletionPersistence persistence;
	private final AuctionOwnershipReader ownershipReader;
	private final MemberPersistence memberPersistence;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public ChatMessageDeletionService(
		ChatMessageDeletionPersistence persistence,
		AuctionOwnershipReader ownershipReader,
		MemberPersistence memberPersistence,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			persistence,
			ownershipReader,
			memberPersistence,
			eventPublisher,
			Clock.systemUTC()
		);
	}

	ChatMessageDeletionService(
		ChatMessageDeletionPersistence persistence,
		AuctionOwnershipReader ownershipReader,
		MemberPersistence memberPersistence,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.persistence = Objects.requireNonNull(persistence, "persistence");
		this.ownershipReader = Objects.requireNonNull(
			ownershipReader,
			"ownershipReader"
		);
		this.memberPersistence = Objects.requireNonNull(
			memberPersistence,
			"memberPersistence"
		);
		this.eventPublisher = Objects.requireNonNull(
			eventPublisher,
			"eventPublisher"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Transactional
	public void deleteByMember(
		long auctionId,
		Long memberId,
		ChatMessageId messageId
	) {
		validateTarget(auctionId, messageId);
		long authenticatedMemberId = requireMemberId(memberId);
		Member member = memberPersistence.findById(authenticatedMemberId)
			.filter(found -> found.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new ChatMessageDeletionException(UNAUTHORIZED));

		ChatMessageDeletionReason reason = deletionReason(
			member,
			auctionId,
			authenticatedMemberId
		);
		ChatMessageDeletionResult result = persistence.delete(
			new ChatMessageDeletionCommand(
				auctionId,
				messageId,
				authenticatedMemberId,
				reason,
				Instant.now(clock)
			)
		);
		handleResult(auctionId, messageId, result);
	}

	private ChatMessageDeletionReason deletionReason(
		Member member,
		long auctionId,
		long memberId
	) {
		if (member.getRole() == MemberRole.ADMIN) {
			return ChatMessageDeletionReason.ADMIN_DELETED;
		}
		if (ownershipReader.isSeller(auctionId, memberId)) {
			return ChatMessageDeletionReason.SELLER_DELETED;
		}
		throw new ChatMessageDeletionException(CHAT_DELETE_FORBIDDEN);
	}

	private void handleResult(
		long auctionId,
		ChatMessageId messageId,
		ChatMessageDeletionResult result
	) {
		if (result.status() == ChatMessageDeletionResult.Status.NOT_FOUND) {
			throw new ChatMessageDeletionException(MESSAGE_NOT_FOUND);
		}
		if (!result.deleted()) {
			return;
		}
		eventPublisher.publishEvent(new ChatDeletedEvent(auctionId, messageId));
		if (result.pinned()) {
			eventPublisher.publishEvent(new NoticeUnpinnedEvent(auctionId));
		}
		if (result.question()) {
			eventPublisher.publishEvent(new QuestionRemovedEvent(
				auctionId,
				messageId,
				QuestionRemovalReason.DELETED
			));
		}
	}

	private static void validateTarget(
		long auctionId,
		ChatMessageId messageId
	) {
		if (auctionId <= 0) {
			throw new ChatMessageDeletionException(INVALID_AUCTION_ID);
		}
		if (messageId == null) {
			throw new ChatMessageDeletionException(INVALID_MESSAGE_ID);
		}
	}

	private static long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new ChatMessageDeletionException(UNAUTHORIZED);
		}
		return memberId;
	}
}
