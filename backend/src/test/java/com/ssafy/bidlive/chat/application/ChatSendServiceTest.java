package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ChatMessageIdGenerator;
import com.ssafy.bidlive.chat.domain.FilterResult;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** CHAT-01 신규·중복 전송의 검증·저장·후속 처리 순서를 검증한다. */
@ExtendWith(MockitoExtension.class)
class ChatSendServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long MEMBER_ID = 25L;
	private static final String CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667788";
	private static final ChatMessageId MESSAGE_ID =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");
	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	@Mock
	private AuctionRoomDetailPersistence auctionPersistence;

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private ChatMessagePersistence messagePersistence;

	@Mock
	private ChatMuteReader muteReader;

	@Mock
	private ChatSlowModeChecker slowModeChecker;

	@Mock
	private ChatContentFilter contentFilter;

	@Mock
	private ChatMessageIdGenerator messageIdGenerator;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ChatSendService service;

	@BeforeEach
	void setUp() {
		service = new ChatSendService(
			new ChatSendPolicy(),
			auctionPersistence,
			memberPersistence,
			messagePersistence,
			muteReader,
			slowModeChecker,
			contentFilter,
			messageIdGenerator,
			eventPublisher,
			Clock.fixed(REQUESTED_AT, ZoneOffset.UTC)
		);
	}

	@ParameterizedTest
	@EnumSource(
		value = AuctionStatus.class,
		names = {"WAITING", "LIVE"}
	)
	void rejectsContactDetailsForChatOpenAuction(
		AuctionStatus status
	) {
		givenOpenAuction(status);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.empty());
		when(contentFilter.filter("연락은 010-1234-5678"))
			.thenReturn(
				new FilterResult(
					"연락은 *************",
					Set.of(MaskingCategory.CONTACT)
				)
			);
		when(muteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(
			slowModeChecker.checkAndConsume(
				AUCTION_ID,
				MEMBER_ID,
				REQUESTED_AT
			)
		).thenReturn(ChatSlowModeResult.permit());

		ChatSendException exception = catchThrowableOfType(
			() -> service.send(command("연락은 010-1234-5678")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_CONTENT_INVALID);
		verify(messagePersistence, never()).saveIfAbsent(any(ChatMessage.class));
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void doesNotRequestAuditForUnmaskedMessage() {
		givenOpenAuction(AuctionStatus.LIVE);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.empty());
		when(muteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(slowModeChecker.checkAndConsume(
			AUCTION_ID,
			MEMBER_ID,
			REQUESTED_AT
		)).thenReturn(ChatSlowModeResult.permit());
		when(contentFilter.filter("안녕하세요."))
			.thenReturn(new FilterResult("안녕하세요.", Set.of()));
		when(messageIdGenerator.nextId()).thenReturn(MESSAGE_ID);
		when(messagePersistence.saveIfAbsent(any(ChatMessage.class)))
			.thenReturn(ChatMessagePersistenceResult.created(MESSAGE_ID));

		service.send(command("안녕하세요."));

		verify(eventPublisher).publishEvent(any(ChatMessageCreatedEvent.class));
		verify(eventPublisher, never()).publishEvent(
			any(ChatModerationAuditRequestedEvent.class)
		);
	}

	@Test
	void doesNotPublishCreatedEventWhenPersistenceFails() {
		givenOpenAuction(AuctionStatus.LIVE);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.empty());
		when(muteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(
			slowModeChecker.checkAndConsume(
				AUCTION_ID,
				MEMBER_ID,
				REQUESTED_AT
			)
		).thenReturn(ChatSlowModeResult.permit());
		when(contentFilter.filter("안녕하세요."))
			.thenReturn(new FilterResult("안녕하세요.", Set.of()));
		when(messageIdGenerator.nextId()).thenReturn(MESSAGE_ID);
		IllegalStateException persistenceFailure =
			new IllegalStateException("persistence failure");
		when(messagePersistence.saveIfAbsent(any(ChatMessage.class)))
			.thenThrow(persistenceFailure);

		assertThatThrownBy(() -> service.send(command("안녕하세요.")))
			.isSameAs(persistenceFailure);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void returnsExistingResultWithoutConsumingNewMessagePolicies() {
		givenOpenAuction(AuctionStatus.LIVE);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.of(MESSAGE_ID));

		ChatSendResult result = service.send(command("안녕하세요."));

		assertThat(result).isEqualTo(ChatSendResult.existing(MESSAGE_ID));
		verify(muteReader, never()).findMutedUntil(anyLong(), anyLong());
		verify(slowModeChecker, never())
			.checkAndConsume(anyLong(), anyLong(), any());
		verify(contentFilter, never()).filter(any());
		verify(messagePersistence, never()).saveIfAbsent(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void handlesConcurrentPersistenceConflictAsExistingMessage() {
		givenOpenAuction(AuctionStatus.LIVE);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.empty());
		when(muteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.empty());
		when(
			slowModeChecker.checkAndConsume(
				AUCTION_ID,
				MEMBER_ID,
				REQUESTED_AT
			)
		).thenReturn(ChatSlowModeResult.permit());
		when(contentFilter.filter("안녕하세요."))
			.thenReturn(new FilterResult("안녕하세요.", Set.of()));
		when(messageIdGenerator.nextId()).thenReturn(MESSAGE_ID);
		ChatMessageId existingId =
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABD");
		when(messagePersistence.saveIfAbsent(any(ChatMessage.class)))
			.thenReturn(ChatMessagePersistenceResult.existing(existingId));

		ChatSendResult result = service.send(command("안녕하세요."));

		assertThat(result).isEqualTo(ChatSendResult.existing(existingId));
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void rejectsEndedAuctionBeforeIdempotencyLookup() {
		givenOpenAuction(AuctionStatus.ENDED);

		ChatSendException exception = catchThrowableOfType(
			() -> service.send(command("안녕하세요.")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.AUCTION_CHAT_CLOSED);
		verify(messagePersistence, never())
			.findMessageId(anyLong(), any());
	}

	@Test
	void rejectsNonSellerFromPrivateAuction() {
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(
				Optional.of(
					auction(AuctionStatus.WAITING, 999L, "PRIVATE")
				)
			);

		ChatSendException exception = catchThrowableOfType(
			() -> service.send(command("안녕하세요.")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.AUCTION_CHAT_CLOSED);
		verify(memberPersistence, never()).findById(anyLong());
	}

	@Test
	void rejectsSuspendedMemberWithoutStoringMessage() {
		givenOpenAuction(AuctionStatus.LIVE);
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.SUSPENDED);
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member));

		ChatSendException exception = catchThrowableOfType(
			() -> service.send(command("안녕하세요.")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_MUTED);
		verify(messagePersistence, never()).saveIfAbsent(any());
	}

	@Test
	void doesNotPublishMessageWhenMutePolicyRejectsNewRequest() {
		givenOpenAuction(AuctionStatus.LIVE);
		givenActiveMember("경매참여자");
		when(messagePersistence.findMessageId(anyLong(), any()))
			.thenReturn(Optional.empty());
		when(muteReader.findMutedUntil(AUCTION_ID, MEMBER_ID))
			.thenReturn(Optional.of(REQUESTED_AT.plusSeconds(10)));

		ChatSendException exception = catchThrowableOfType(
			() -> service.send(command("안녕하세요.")),
			ChatSendException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(ChatSendErrorCode.CHAT_MUTED);
		verify(contentFilter, never()).filter(any());
		verify(messagePersistence, never()).saveIfAbsent(any());
		verify(slowModeChecker, never())
			.checkAndConsume(anyLong(), anyLong(), any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	private void givenOpenAuction(AuctionStatus status) {
		when(auctionPersistence.findByAuctionId(AUCTION_ID))
			.thenReturn(
				Optional.of(auction(status, 999L, "PUBLIC"))
			);
	}

	private void givenActiveMember(String nickname) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(member.getNickname()).thenReturn(nickname);
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member));
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

	private static AuctionRoomDetailRow auction(
		AuctionStatus status,
		long sellerId,
		String visibility
	) {
		return new AuctionRoomDetailRow(
			AUCTION_ID,
			sellerId,
			"테스트 경매",
			status,
			10_000L,
			REQUESTED_AT.plusSeconds(3_600),
			30,
			visibility,
			null,
			200L,
			"테스트 상품",
			"상품 설명",
			"ETC",
			"https://example.com/product.jpg",
			"APPROVED",
			"판매자",
			0
		);
	}
}
