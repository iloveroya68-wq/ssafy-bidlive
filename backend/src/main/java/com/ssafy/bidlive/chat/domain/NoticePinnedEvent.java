package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 고정 공지가 등록되거나 다른 메시지로 교체된 사실을 전달합니다. */
public record NoticePinnedEvent(
	long auctionId,
	PinnedNotice notice
) {

	public NoticePinnedEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(notice, "notice");
	}
}
