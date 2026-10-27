package com.ssafy.bidlive.media.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.ssafy.bidlive.media.domain.LiveBroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataLiveBroadcastRepository
	extends JpaRepository<LiveBroadcastJpaEntity, Long> {

	Optional<LiveBroadcastJpaEntity> findByRoomId(String roomId);

	/**
	 * 상태를 where 조건에 포함한 단일 UPDATE이므로 종료 요청이 동시에 들어와도 한 번만 1을 반환한다.
	 * 벌크 연산이라 영속성 컨텍스트를 건너뛰므로 갱신 후 컨텍스트를 비운다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update LiveBroadcastJpaEntity broadcast
		set broadcast.status = :endedStatus,
			broadcast.endedAt = :endedAt
		where broadcast.roomId = :roomId
			and broadcast.status = :liveStatus
		""")
	int markEnded(
		@Param("roomId") String roomId,
		@Param("endedAt") OffsetDateTime endedAt,
		@Param("liveStatus") LiveBroadcastStatus liveStatus,
		@Param("endedStatus") LiveBroadcastStatus endedStatus
	);
}
