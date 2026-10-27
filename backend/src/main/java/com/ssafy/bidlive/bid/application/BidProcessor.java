package com.ssafy.bidlive.bid.application;

/** 입찰 검증과 상태 갱신을 하나의 원자 작업으로 수행하는 계약이다. */
public interface BidProcessor {

	BidProcessingResult process(BidCommand command);
}
