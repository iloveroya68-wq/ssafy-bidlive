package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 종료 후에만 공개하는 입찰 한 건. 라벨 마스킹은 서버 몫이다(AGENTS.md 규칙 6). */
public record MaskedBidRow(long memberId, String nickname, long amount, Instant submittedAt) {
}
