package com.ssafy.bidlive.product.application.port.out;

/** AI 검열 판정을 얻지 못한 원인이며, 보류와 오류 응답을 구분하는 기준입니다. */
public enum ModerationFailureKind {

	/** 응답이 오지 않았거나 허용 시간을 넘겼다. */
	TIMEOUT,

	/** 연결 실패나 5xx처럼 게이트웨이 자체 오류다. */
	TRANSPORT,

	/** 응답은 왔지만 강제한 JSON 스키마를 지키지 않았다. */
	INVALID_RESPONSE,

	/**
	 * Credit이 소진되어 더 호출할 수 없다.
	 *
	 * <p>콘텐츠 판단의 모호함이 아니라 운영자가 개입해야 하는 상태이므로 보류로 감추지 않고
	 * 오류 응답으로 드러낸다.
	 */
	CREDIT_EXHAUSTED
}
