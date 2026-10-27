package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Optional;

/** 경매·회원별 채팅 제한 만료 시각을 조회하는 Application Port다. */
public interface ChatMuteReader {

	Optional<Instant> findMutedUntil(long auctionId, long memberId);
}
