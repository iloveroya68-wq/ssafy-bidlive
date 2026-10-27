package com.ssafy.bidlive.media.domain;

/** 라이브 방송 종료가 거부되는 사유이며 이름이 그대로 오류 응답의 `code`가 됩니다. */
public enum LiveBroadcastEndFailureCode {

	LIVE_NOT_FOUND,
	NOT_BROADCAST_OWNER,
	LIVE_NOT_ACTIVE,
	MEDIA_SERVER_ERROR
}
