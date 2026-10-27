package com.ssafy.bidlive.chat.application;

/** 프롬프트나 원문을 노출하지 않고 알림에 사용할 공개 판정 사유입니다. */
public enum ChatModerationReason {
	SAFE,
	OFF_PLATFORM_TRADE,
	TOXIC_CONTENT,
	PROHIBITED_GOODS,
	AUCTION_MANIPULATION,
	ADVERTISING_SPAM,
	DUPLICATE_QUESTION,
	OFF_TOPIC_QUESTION,
	NOT_A_QUESTION
}
