package com.ssafy.bidlive.common.event;

/**
 * WebSocket으로 발행하는 모든 이벤트가 공유하는 공통 envelope입니다.
 *
 * <p>이 타입은 이벤트 종류와 상세 데이터를 구분하는 형태만 정의하고 도메인별 payload는 알지
 * 않는다. 구체적인 {@code data} 타입은 사건이 발생한 도메인이 소유하므로, 여러 도메인이 이
 * envelope을 함께 쓰더라도 도메인 사이에 의존이 생기지 않는다.
 *
 * <p>destination에 이미 담긴 식별자({@code auctionId}, {@code roomId} 등)와 destination 자체는
 * {@code data}에 중복해 넣지 않는다. 시간 동기화 필드는 이를 필요로 하는 이벤트의
 * {@code data}에만 둔다.
 *
 * @param <T> 이벤트 종류별 상세 데이터 타입
 */
public record WsEvent<T>(
	String type,
	T data
) {

	public WsEvent {
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("WebSocket event type must not be blank");
		}
		if (data == null) {
			throw new IllegalArgumentException("WebSocket event data must not be null");
		}
	}
}
