package com.ssafy.bidlive.product.application.port.out;

/**
 * AI 검열 게이트웨이가 판정을 만들어 주지 못한 상황을 원인과 함께 전달하는 포트 예외입니다.
 *
 * <p>어댑터의 기술별 예외를 이 타입으로 묶어 두면 `application`이 특정 HTTP 클라이언트나 AI
 * 제공자 SDK를 import하지 않고도 실패 원인별 정책을 적용할 수 있다.
 */
public class ContentModerationException extends RuntimeException {

	private final ModerationFailureKind kind;

	public ContentModerationException(ModerationFailureKind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public ContentModerationException(ModerationFailureKind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public ModerationFailureKind getKind() {
		return kind;
	}
}
