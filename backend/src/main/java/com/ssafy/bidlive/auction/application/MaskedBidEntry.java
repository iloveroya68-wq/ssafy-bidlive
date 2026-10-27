package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 마스킹이 끝난 입찰 한 줄. 본인 행은 라벨이 {@code "나"}다 */
public record MaskedBidEntry(String bidderLabel, long amount, Instant submittedAt) {
}
