package com.ssafy.bidlive.common.websocket.subscription;

/** STOMP 구독 Destination의 공통 권한 판정 유형을 나타냅니다. */
public enum StompSubscriptionDestinationType {

	AUCTION_PUBLIC,
	AUCTION_QUESTIONS,
	USER_QUEUE,
	UNKNOWN
}
