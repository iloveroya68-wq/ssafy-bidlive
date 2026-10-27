package com.ssafy.bidlive.prediction.application;

/** 회원의 정산 완료 예측 참여·적중 건수를 제공하는 공개 조회 계약이다. */
public interface MemberPredictionActivityQuery {

	MemberPredictionActivity query(Long memberId);
}
