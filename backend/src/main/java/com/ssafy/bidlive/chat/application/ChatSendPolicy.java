package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.stereotype.Component;

/** 채팅 입력과 경매 상태·뮤트·슬로우 모드 결과를 CHAT-01 정책으로 검증한다. */
@Component
public class ChatSendPolicy {

	public ValidatedChatSend validateCommand(ChatSendCommand command) {
		if (command == null) {
			throw invalidContent(null);
		}
		if (command.auctionId() <= 0) {
			throw new ChatSendException(
				ChatSendErrorCode.AUCTION_CHAT_CLOSED
			);
		}
		if (command.memberId() <= 0) {
			throw new IllegalStateException(
				"authenticated memberId must be positive"
			);
		}

		try {
			return new ValidatedChatSend(
				command.auctionId(),
				command.memberId(),
				new ClientMessageId(command.clientMessageId()),
				new ChatMessageContent(command.content()),
				command.isQuestion()
			);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalidContent(exception);
		}
	}

	public void validateAuctionStatus(AuctionStatus status) {
		if (status != AuctionStatus.WAITING
			&& status != AuctionStatus.LIVE) {
			throw new ChatSendException(
				ChatSendErrorCode.AUCTION_CHAT_CLOSED
			);
		}
	}

	public void validateMemberStatus(MemberStatus status) {
		if (status != MemberStatus.ACTIVE) {
			throw new ChatSendException(ChatSendErrorCode.CHAT_MUTED);
		}
	}

	public void validateMutedUntil(
		Optional<Instant> mutedUntil,
		Instant requestedAt
	) {
		Objects.requireNonNull(mutedUntil, "mutedUntil");
		Objects.requireNonNull(requestedAt, "requestedAt");
		if (mutedUntil.filter(until -> until.isAfter(requestedAt)).isPresent()) {
			throw new ChatSendException(ChatSendErrorCode.CHAT_MUTED);
		}
	}

	public void validateSlowMode(ChatSlowModeResult result) {
		Objects.requireNonNull(result, "result");
		if (!result.allowed()) {
			throw new ChatSendException(
				ChatSendErrorCode.SLOW_MODE_LIMIT
			);
		}
	}

	private static ChatSendException invalidContent(Throwable cause) {
		return new ChatSendException(
			ChatSendErrorCode.CHAT_CONTENT_INVALID,
			cause
		);
	}
}
