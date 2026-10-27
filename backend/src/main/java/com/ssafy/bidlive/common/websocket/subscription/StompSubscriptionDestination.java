package com.ssafy.bidlive.common.websocket.subscription;

import java.util.Objects;

/** 해석된 STOMP 구독 유형과 권한 판정에 필요한 경매 식별자를 표현합니다. */
public record StompSubscriptionDestination(
	StompSubscriptionDestinationType type,
	Long auctionId
) {

	public StompSubscriptionDestination {
		Objects.requireNonNull(type, "type");

		if (requiresAuctionId(type)) {
			if (auctionId == null || auctionId <= 0) {
				throw new IllegalArgumentException(
					"경매 Destination에는 양수 auctionId가 필요합니다."
				);
			}
		} else if (auctionId != null) {
			throw new IllegalArgumentException(
				"경매 Destination이 아니면 auctionId를 가질 수 없습니다."
			);
		}
	}

	public static StompSubscriptionDestination auctionPublic(Long auctionId) {
		return new StompSubscriptionDestination(
			StompSubscriptionDestinationType.AUCTION_PUBLIC,
			auctionId
		);
	}

	public static StompSubscriptionDestination auctionQuestions(Long auctionId) {
		return new StompSubscriptionDestination(
			StompSubscriptionDestinationType.AUCTION_QUESTIONS,
			auctionId
		);
	}

	public static StompSubscriptionDestination userQueue() {
		return new StompSubscriptionDestination(
			StompSubscriptionDestinationType.USER_QUEUE,
			null
		);
	}

	public static StompSubscriptionDestination unknown() {
		return new StompSubscriptionDestination(
			StompSubscriptionDestinationType.UNKNOWN,
			null
		);
	}

	private static boolean requiresAuctionId(
		StompSubscriptionDestinationType type
	) {
		return type == StompSubscriptionDestinationType.AUCTION_PUBLIC
			|| type == StompSubscriptionDestinationType.AUCTION_QUESTIONS;
	}
}
