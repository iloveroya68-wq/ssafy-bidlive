package com.ssafy.bidlive.chat.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import org.junit.jupiter.api.Test;

/** Verifies that a question moderation job contains only masked chat content and room context. */
class ChatModerationEnqueueListenerTest {

	@Test
	void enqueuesMaskedContentWithQuestionRoomContext() {
		ChatModerationJobQueue queue = mock(ChatModerationJobQueue.class);
		AuctionRoomDetailPersistence auctionPersistence =
			mock(AuctionRoomDetailPersistence.class);
		ChatModerationEnqueueListener listener =
			new ChatModerationEnqueueListener(queue, auctionPersistence);
		Instant createdAt = Instant.parse("2026-08-02T01:00:00Z");
		when(auctionPersistence.findByAuctionId(42L)).thenReturn(Optional.of(
			new AuctionRoomDetailRow(
				42L,
				99L,
				"Keyboard auction",
				AuctionStatus.LIVE,
				10_000L,
				createdAt,
				30,
				"PUBLIC",
				null,
				200L,
				"Keyboard",
				"Lubed switches and used condition.",
				"ELECTRONICS",
				"https://example.com/product.jpg",
				"PASSED",
				"seller",
				0
			)
		));
		ChatMessage message = new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			new ClientMessageId("018f6f4e-2a61-7c00-a1b2-334455667788"),
			42L,
			7L,
			"viewer",
			new ChatMessageContent("masked content ********"),
			true,
			Set.of(),
			createdAt
		);

		listener.onCreated(new ChatMessageCreatedEvent(message));

		verify(queue).enqueue(new ChatModerationJob(
			42L,
			message.messageId(),
			7L,
			"masked content ********",
			true,
			"Keyboard auction",
			"Lubed switches and used condition.",
			"ELECTRONICS",
			createdAt
		));
	}

	@Test
	void doesNotLoadRoomContextForRegularChat() {
		ChatModerationJobQueue queue = mock(ChatModerationJobQueue.class);
		AuctionRoomDetailPersistence auctionPersistence =
			mock(AuctionRoomDetailPersistence.class);
		ChatModerationEnqueueListener listener =
			new ChatModerationEnqueueListener(queue, auctionPersistence);
		ChatMessage message = new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABD"),
			new ClientMessageId("018f6f4e-2a61-7c00-a1b2-334455667789"),
			42L,
			7L,
			"viewer",
			new ChatMessageContent("hello"),
			false,
			Set.of(),
			Instant.parse("2026-08-02T01:00:00Z")
		);

		listener.onCreated(new ChatMessageCreatedEvent(message));

		verify(queue).enqueue(new ChatModerationJob(
			42L,
			message.messageId(),
			7L,
			"hello",
			false,
			message.createdAt()
		));
	}
}
