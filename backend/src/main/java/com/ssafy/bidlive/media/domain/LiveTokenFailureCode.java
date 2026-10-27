package com.ssafy.bidlive.media.domain;

/** 라이브 접속 토큰 발급이 거부되는 사유이며 이름이 그대로 오류 응답의 `code`가 됩니다. */
public enum LiveTokenFailureCode {

	/** 비공개 방을 비판매자에게 감출 때도 이 코드를 쓴다. 존재 여부를 드러내지 않기 위해서다. */
	ROOM_NOT_FOUND,
	NOT_AUCTION_SELLER
}
