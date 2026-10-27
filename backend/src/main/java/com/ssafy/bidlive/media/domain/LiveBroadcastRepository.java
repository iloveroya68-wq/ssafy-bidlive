package com.ssafy.bidlive.media.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 방 단위 라이브 방송 기록의 조회와 상태 전이 저장 계약입니다. */
public interface LiveBroadcastRepository {

	Optional<LiveBroadcast> findByRoomId(String roomId);

	/**
	 * 방송 개시를 기록한다. 이미 진행 중인 방송은 개시자와 시작 시각을 유지하고,
	 * 종료된 방송만 새 개시자로 다시 LIVE 상태가 된다.
	 */
	void startBroadcast(String roomId, Long broadcasterId, OffsetDateTime startedAt);

	/**
	 * LIVE 상태인 방송만 ENDED로 바꾸는 조건부 갱신이며, 동시에 들어온 종료 요청 중
	 * 하나만 {@code true}를 받는다. 이미 종료된 방송이면 갱신 건수가 0이므로
	 * {@code false}를 반환한다.
	 */
	boolean endBroadcast(String roomId, OffsetDateTime endedAt);
}
