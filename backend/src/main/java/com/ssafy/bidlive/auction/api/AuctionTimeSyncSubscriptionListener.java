package com.ssafy.bidlive.auction.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** 인증 사용자의 경매 메인 토픽 구독 직후 현재 서버 시간을 한 번 동기화한다. */
@Component
public class AuctionTimeSyncSubscriptionListener {

	private static final Logger log = LoggerFactory.getLogger(
		AuctionTimeSyncSubscriptionListener.class
	);
	private static final Pattern MAIN_TOPIC_PATTERN = Pattern.compile(
		"^/topic/auctions/([1-9]\\d*)$"
	);

	private final AuctionTimeSyncService timeSyncService;

	public AuctionTimeSyncSubscriptionListener(
		AuctionTimeSyncService timeSyncService
	) {
		this.timeSyncService = Objects.requireNonNull(
			timeSyncService,
			"timeSyncService"
		);
	}

	@EventListener
	public void onSubscribe(SessionSubscribeEvent event) {
		StompHeaderAccessor accessor = accessor(event.getMessage());
		if (accessor == null || accessor.getUser() == null) {
			return;
		}

		Long auctionId = auctionId(accessor.getDestination());
		if (auctionId == null) {
			return;
		}

		try {
			timeSyncService.synchronizeIfLive(auctionId);
		} catch (RuntimeException exception) {
			log.warn(
				"Auction TIME_SYNC after subscription failed: auctionId={}, failureType={}",
				auctionId,
				exception.getClass().getSimpleName()
			);
		}
	}

	private static StompHeaderAccessor accessor(Message<?> message) {
		return MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
	}

	private static Long auctionId(String destination) {
		if (destination == null) {
			return null;
		}
		Matcher matcher = MAIN_TOPIC_PATTERN.matcher(destination);
		if (!matcher.matches()) {
			return null;
		}
		try {
			return Long.parseLong(matcher.group(1));
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
