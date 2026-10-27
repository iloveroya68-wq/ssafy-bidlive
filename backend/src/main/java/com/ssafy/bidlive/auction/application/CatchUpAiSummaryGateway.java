package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 확정 STT 문장을 상품 설명 중심의 짧은 불릿으로 요약하는 외부 AI 포트다. */
public interface CatchUpAiSummaryGateway {

	List<String> summarize(List<CatchUpTranscriptSegment> transcripts);
}
