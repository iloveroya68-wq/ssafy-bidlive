package com.ssafy.bidlive.media.application.port.out;

import com.ssafy.bidlive.media.application.ViewerCountUpdate;

/** 변경된 경매별 활성 STOMP 세션 수를 외부 채널로 발행하는 계약입니다. */
public interface ViewerCountPublisher {

	void publish(ViewerCountUpdate update);
}
