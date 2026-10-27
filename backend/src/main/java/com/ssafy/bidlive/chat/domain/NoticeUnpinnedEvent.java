package com.ssafy.bidlive.chat.domain;

/** 기존 고정 공지가 해제된 사실을 전달합니다. */
public record NoticeUnpinnedEvent(long auctionId) {

	public NoticeUnpinnedEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
	}
}
