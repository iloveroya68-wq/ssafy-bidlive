package com.ssafy.bidlive.chat.application;

import java.time.Instant;

/** 경매·회원별 검열 위반을 누적하고 뮤트 만료 시각을 반환합니다. */
public interface ChatModerationPenaltyPort {

	ModerationPenaltyResult recordViolation(
		long auctionId,
		long memberId,
		Instant now
	);
}
