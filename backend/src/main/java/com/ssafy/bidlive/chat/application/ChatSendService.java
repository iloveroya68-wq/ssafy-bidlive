package com.ssafy.bidlive.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ChatMessageIdGenerator;
import com.ssafy.bidlive.chat.domain.FilterResult;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 검증·멱등 저장·마스킹과 커밋 후 후속 처리를 조율한다. */
public class ChatSendService {

	private final ChatSendPolicy policy;
	private final AuctionRoomDetailPersistence auctionPersistence;
	private final MemberPersistence memberPersistence;
	private final ChatMessagePersistence messagePersistence;
	private final ChatMuteReader muteReader;
	private final ChatSlowModeChecker slowModeChecker;
	private final ChatContentFilter contentFilter;
	private final ChatMessageIdGenerator messageIdGenerator;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public ChatSendService(
		ChatSendPolicy policy,
		AuctionRoomDetailPersistence auctionPersistence,
		MemberPersistence memberPersistence,
		ChatMessagePersistence messagePersistence,
		ChatMuteReader muteReader,
		ChatSlowModeChecker slowModeChecker,
		ChatContentFilter contentFilter,
		ChatMessageIdGenerator messageIdGenerator,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			policy,
			auctionPersistence,
			memberPersistence,
			messagePersistence,
			muteReader,
			slowModeChecker,
			contentFilter,
			messageIdGenerator,
			eventPublisher,
			Clock.systemUTC()
		);
	}

	ChatSendService(
		ChatSendPolicy policy,
		AuctionRoomDetailPersistence auctionPersistence,
		MemberPersistence memberPersistence,
		ChatMessagePersistence messagePersistence,
		ChatMuteReader muteReader,
		ChatSlowModeChecker slowModeChecker,
		ChatContentFilter contentFilter,
		ChatMessageIdGenerator messageIdGenerator,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.policy = Objects.requireNonNull(policy, "policy");
		this.auctionPersistence = Objects.requireNonNull(
			auctionPersistence,
			"auctionPersistence"
		);
		this.memberPersistence = Objects.requireNonNull(
			memberPersistence,
			"memberPersistence"
		);
		this.messagePersistence = Objects.requireNonNull(
			messagePersistence,
			"messagePersistence"
		);
		this.muteReader = Objects.requireNonNull(muteReader, "muteReader");
		this.slowModeChecker = Objects.requireNonNull(
			slowModeChecker,
			"slowModeChecker"
		);
		this.contentFilter = Objects.requireNonNull(
			contentFilter,
			"contentFilter"
		);
		this.messageIdGenerator = Objects.requireNonNull(
			messageIdGenerator,
			"messageIdGenerator"
		);
		this.eventPublisher = Objects.requireNonNull(
			eventPublisher,
			"eventPublisher"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Transactional
	public ChatSendResult send(ChatSendCommand command) {
		ValidatedChatSend validated = policy.validateCommand(command);
		requireOpenAuction(validated.auctionId(), validated.memberId());
		Member member = requireActiveMember(validated.memberId());

		Optional<ChatMessageId> existingMessageId =
			messagePersistence.findMessageId(
				validated.memberId(),
				validated.clientMessageId()
			);
		if (existingMessageId.isPresent()) {
			return ChatSendResult.existing(existingMessageId.get());
		}

		Instant requestedAt = clock.instant();
		policy.validateMutedUntil(
			muteReader.findMutedUntil(
				validated.auctionId(),
				validated.memberId()
			),
			requestedAt
		);
		policy.validateSlowMode(
			slowModeChecker.checkAndConsume(
				validated.auctionId(),
				validated.memberId(),
				requestedAt
			)
		);

		ChatMessage message = createMessage(validated, member, requestedAt);
		ChatMessagePersistenceResult persistenceResult =
			messagePersistence.saveIfAbsent(message);
		if (!persistenceResult.created()) {
			return ChatSendResult.existing(persistenceResult.messageId());
		}
		if (!persistenceResult.messageId().equals(message.messageId())) {
			throw new IllegalStateException(
				"created chat messageId does not match persistence result"
			);
		}

		eventPublisher.publishEvent(new ChatMessageCreatedEvent(message));
		if (!message.maskedFields().isEmpty()) {
			eventPublisher.publishEvent(
				new ChatModerationAuditRequestedEvent(
					new ChatModerationAuditContext(
						message.messageId(),
						message.auctionId(),
						message.memberId()
					),
					validated.content().value(),
					message.maskedFields(),
					message.createdAt()
				)
			);
		}
		return ChatSendResult.created(message.messageId());
	}

	private AuctionRoomDetailRow requireOpenAuction(
		long auctionId,
		long memberId
	) {
		AuctionRoomDetailRow auction = auctionPersistence
			.findByAuctionId(auctionId)
			.filter(row -> row.deletedAt() == null)
			.orElseThrow(() ->
				new ChatSendException(ChatSendErrorCode.AUCTION_CHAT_CLOSED)
			);
		policy.validateAuctionStatus(auction.status());
		if (auction.sellerId() != memberId
			&& !AuctionRoomVisibility.isPublic(auction.visibility())) {
			throw new ChatSendException(
				ChatSendErrorCode.AUCTION_CHAT_CLOSED
			);
		}
		return auction;
	}

	private Member requireActiveMember(long memberId) {
		Member member = memberPersistence.findById(memberId)
			.orElseThrow(() ->
				new IllegalStateException(
					"authenticated member does not exist"
				)
			);
		policy.validateMemberStatus(member.getStatus());
		if (member.getNickname() == null || member.getNickname().isBlank()) {
			throw new IllegalStateException(
				"authenticated member nickname is unavailable"
			);
		}
		return member;
	}

	private ChatMessage createMessage(
		ValidatedChatSend validated,
		Member member,
		Instant requestedAt
	) {
		FilterResult filtered = Objects.requireNonNull(
			contentFilter.filter(validated.content().value()),
			"contentFilter result"
		);
		if (filtered.blocked()) {
			throw new ChatSendException(ChatSendErrorCode.CHAT_CONTENT_INVALID);
		}
		return new ChatMessage(
			messageIdGenerator.nextId(),
			validated.clientMessageId(),
			validated.auctionId(),
			validated.memberId(),
			member.getNickname(),
			new ChatMessageContent(filtered.filteredContent()),
			validated.isQuestion(),
			filtered.maskedFields(),
			requestedAt
		);
	}
}
