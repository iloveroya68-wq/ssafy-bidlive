package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;

/** CHAT-01 입력·경매 상태·뮤트·슬로우 모드 거부 정책을 검증한다. */
class ChatSendPolicyTest {

	private static final long AUCTION_ID = 100L;
	private static final long MEMBER_ID = 25L;
	private static final String CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667788";
	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	private final ChatSendPolicy policy = new ChatSendPolicy();

	@Test
	void normalizesValidCommand() {
		ValidatedChatSend validated = policy.validateCommand(
			command("  상품 상태가 궁금합니다.  ")
		);

		assertThat(validated.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(validated.memberId()).isEqualTo(MEMBER_ID);
		assertThat(validated.clientMessageId().value())
			.isEqualTo(CLIENT_MESSAGE_ID);
		assertThat(validated.content().value())
			.isEqualTo("상품 상태가 궁금합니다.");
		assertThat(validated.isQuestion()).isFalse();
	}

	@Test
	void acceptsExactlyTwoHundredFiftyUnicodeCharacters() {
		String content = "가".repeat(249) + "😀";

		assertThatCode(() -> policy.validateCommand(command(content)))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsMoreThanTwoHundredFiftyUnicodeCharacters() {
		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateCommand(
				command("가".repeat(250) + "😀")
			),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_CONTENT_INVALID);
	}

	@Test
	void rejectsBlankContentAsStableErrorCode() {
		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateCommand(command(" \t\n ")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_CONTENT_INVALID);
		assertThat(exception.getMessage())
			.isEqualTo("CHAT_CONTENT_INVALID");
	}

	@Test
	void rejectsInvalidClientMessageId() {
		ChatSendCommand invalid = new ChatSendCommand(
			AUCTION_ID,
			MEMBER_ID,
			"not-an-id",
			"안녕하세요.",
			false
		);

		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateCommand(invalid),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_CONTENT_INVALID);
	}

	@Test
	void rejectsInvalidAuctionIdAsClosed() {
		ChatSendCommand invalid = new ChatSendCommand(
			0L,
			MEMBER_ID,
			CLIENT_MESSAGE_ID,
			"안녕하세요.",
			false
		);

		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateCommand(invalid),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.AUCTION_CHAT_CLOSED);
	}

	@Test
	void rejectsInvalidAuthenticatedMemberAsInternalInvariant() {
		ChatSendCommand invalid = new ChatSendCommand(
			AUCTION_ID,
			0L,
			CLIENT_MESSAGE_ID,
			"안녕하세요.",
			false
		);

		assertThatThrownBy(() -> policy.validateCommand(invalid))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("authenticated memberId must be positive");
	}

	@Test
	void allowsWaitingAndLiveAuction() {
		assertThatCode(() ->
			policy.validateAuctionStatus(AuctionStatus.WAITING)
		).doesNotThrowAnyException();
		assertThatCode(() ->
			policy.validateAuctionStatus(AuctionStatus.LIVE)
		).doesNotThrowAnyException();
	}

	@Test
	void rejectsEndedAndUnknownAuctionStatus() {
		assertAuctionClosed(AuctionStatus.ENDED);
		assertAuctionClosed(null);
	}

	@Test
	void allowsActiveMemberAndRejectsUnavailableMemberStatuses() {
		assertThatCode(() ->
			policy.validateMemberStatus(MemberStatus.ACTIVE)
		).doesNotThrowAnyException();

		assertMemberMuted(MemberStatus.SUSPENDED);
		assertMemberMuted(MemberStatus.WITHDRAWN);
		assertMemberMuted(null);
	}

	@Test
	void rejectsActiveMuteAndAllowsExpiredMute() {
		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateMutedUntil(
				Optional.of(REQUESTED_AT.plusSeconds(30)),
				REQUESTED_AT
			),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_MUTED);
		assertThatCode(() ->
			policy.validateMutedUntil(
				Optional.of(REQUESTED_AT),
				REQUESTED_AT
			)
		).doesNotThrowAnyException();
		assertThatCode(() ->
			policy.validateMutedUntil(Optional.empty(), REQUESTED_AT)
		).doesNotThrowAnyException();
	}

	@Test
	void rejectsSlowModeLimitAndAllowsFirstRequest() {
		assertThatCode(() ->
			policy.validateSlowMode(ChatSlowModeResult.permit())
		).doesNotThrowAnyException();

		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateSlowMode(
				ChatSlowModeResult.limited(3)
			),
			ChatSendException.class
		);
		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.SLOW_MODE_LIMIT);
	}

	private static ChatSendCommand command(String content) {
		return new ChatSendCommand(
			AUCTION_ID,
			MEMBER_ID,
			CLIENT_MESSAGE_ID,
			content,
			false
		);
	}

	private void assertAuctionClosed(AuctionStatus status) {
		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateAuctionStatus(status),
			ChatSendException.class
		);
		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.AUCTION_CHAT_CLOSED);
	}

	private void assertMemberMuted(MemberStatus status) {
		ChatSendException exception = catchThrowableOfType(
			() -> policy.validateMemberStatus(status),
			ChatSendException.class
		);
		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_MUTED);
	}
}
