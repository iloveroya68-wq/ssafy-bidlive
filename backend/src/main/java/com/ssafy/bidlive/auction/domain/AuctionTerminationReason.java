package com.ssafy.bidlive.auction.domain;

/**
 * 낙찰 없이 경매가 닫힌 사유다. `auction_result`의 두 컬럼을 한 쌍으로 들고 있다.
 *
 * <p>둘을 따로 넘기지 않는 이유: `ck_auction_result_status_fields`가 `NO_SALE`·`CANCELLED`에만
 * 낙찰자 NULL을 허용하므로 짝이 어긋나면 DB가 거절한다. 짝을 여기서 한 번 정해 두면 호출부가
 * 조합을 다시 고를 일이 없다.
 *
 * <p>`NO_BID`(마감까지 유효 입찰이 없었다)는 여기 없다 — 그건 종료를 <b>지시한 사람</b>이 아니라
 * 정산 결과라서 BID-07 엔진이 직접 기록한다. 이 enum은 「누가 끊었나」만 다룬다.
 */
public enum AuctionTerminationReason {

	/** DEAL-09 운영 강제 종료. 제재 검토 대상이라 결과 화면에서 재등록을 숨긴다 */
	FORCED("CANCELLED"),

	/** 판매자가 진행 콘솔에서 직접 끝냈다. 정상 마감이 아니므로 낙찰 없이 유찰로 닫는다 */
	HOST_LEFT("NO_SALE");

	private final String resultStatus;

	AuctionTerminationReason(String resultStatus) {
		this.resultStatus = resultStatus;
	}

	/** `auction_result.result_status`에 넣을 값 */
	public String resultStatus() {
		return resultStatus;
	}

	/** `auction_result.termination_reason`에 넣을 값 — enum 이름을 그대로 쓴다 */
	public String terminationReason() {
		return name();
	}
}
