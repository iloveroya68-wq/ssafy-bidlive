package com.ssafy.bidlive.auction.application;

import java.util.Optional;

/** 중간 입장 요약과 판매자 음성 업로드의 방 접근 정보를 읽는다. */
public interface CatchUpRoomAccessReader {

	Optional<CatchUpRoomAccess> findAccess(long auctionId);
}
