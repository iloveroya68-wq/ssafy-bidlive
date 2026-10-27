package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 저장이 커밋된 채팅을 원문 없이 비동기 검열 큐에 등록합니다. */
@Component
public class ChatModerationEnqueueListener {

	private static final Logger log = LoggerFactory.getLogger(
		ChatModerationEnqueueListener.class
	);
	private final ChatModerationJobQueue jobQueue;
	private final AuctionRoomDetailPersistence auctionPersistence;

	public ChatModerationEnqueueListener(
		ChatModerationJobQueue jobQueue,
		AuctionRoomDetailPersistence auctionPersistence
	) {
		this.jobQueue = jobQueue;
		this.auctionPersistence = auctionPersistence;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCreated(ChatMessageCreatedEvent event) {
		ChatMessage message = event.message();
		try {
			AuctionRoomDetailRow auction = message.isQuestion()
				? auctionPersistence.findByAuctionId(message.auctionId())
					.orElseThrow(() -> new IllegalStateException(
						"auction room context not found"
					))
				: null;
			jobQueue.enqueue(new ChatModerationJob(
				message.auctionId(),
				message.messageId(),
				message.memberId(),
				message.content().value(),
				message.isQuestion(),
				auction == null ? null : auction.title(),
				auction == null ? null : auction.productDescription(),
				auction == null ? null : auction.productCategoryCode(),
				message.createdAt()
			));
		} catch (RuntimeException exception) {
			log.error(
				"chat moderation enqueue failed: failureType={}",
				exception.getClass().getSimpleName()
			);
		}
	}
}
