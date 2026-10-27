package com.ssafy.bidlive.common.application;

import java.util.Objects;

/** realtime-state 조회 실패를 안정적인 사유로 전달합니다. */
public class RealtimeAuctionStateQueryException extends RuntimeException {

	private final Reason reason;

	public RealtimeAuctionStateQueryException(Reason reason) {
		super(Objects.requireNonNull(reason, "reason").name());
		this.reason = reason;
	}

	public RealtimeAuctionStateQueryException(Reason reason, Throwable cause) {
		super(Objects.requireNonNull(reason, "reason").name(), cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	/** API 오류 코드로 변환할 수 있는 application 계층 실패 사유입니다. */
	public enum Reason {
		AUCTION_NOT_FOUND,
		AUCTION_STATE_UNAVAILABLE
	}
}
