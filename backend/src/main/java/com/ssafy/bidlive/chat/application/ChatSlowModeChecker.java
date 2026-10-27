package com.ssafy.bidlive.chat.application;

import java.time.Instant;

/** 경매·회원별 채팅 전송 간격을 원자적으로 검사하고 소비하는 Port다. */
public interface ChatSlowModeChecker {

	ChatSlowModeResult checkAndConsume(
		long auctionId,
		long memberId,
		Instant requestedAt
	);
}
