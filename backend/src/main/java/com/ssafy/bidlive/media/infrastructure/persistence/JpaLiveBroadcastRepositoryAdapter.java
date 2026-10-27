package com.ssafy.bidlive.media.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.media.domain.LiveBroadcast;
import com.ssafy.bidlive.media.domain.LiveBroadcastRepository;
import com.ssafy.bidlive.media.domain.LiveBroadcastStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaLiveBroadcastRepositoryAdapter implements LiveBroadcastRepository {

	private final SpringDataLiveBroadcastRepository repository;

	public JpaLiveBroadcastRepositoryAdapter(SpringDataLiveBroadcastRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LiveBroadcast> findByRoomId(String roomId) {
		return repository.findByRoomId(roomId).map(LiveBroadcastJpaEntity::toDomain);
	}

	/**
	 * 방마다 행이 하나이므로 같은 방의 반복 발급은 새 행을 만들지 않는다. 진행 중인 방송은
	 * 개시자를 유지해 뒤늦게 토큰을 받은 회원이 종료 권한을 가져가지 못하게 한다.
	 */
	@Override
	@Transactional
	public void startBroadcast(String roomId, Long broadcasterId, OffsetDateTime startedAt) {
		Optional<LiveBroadcastJpaEntity> existing = repository.findByRoomId(roomId);
		if (existing.isEmpty()) {
			repository.save(LiveBroadcastJpaEntity.live(roomId, broadcasterId, startedAt));
			return;
		}

		LiveBroadcastJpaEntity broadcast = existing.get();
		if (broadcast.status() != LiveBroadcastStatus.LIVE) {
			broadcast.restart(broadcasterId, startedAt);
		}
	}

	@Override
	@Transactional
	public boolean endBroadcast(String roomId, OffsetDateTime endedAt) {
		int updatedRows = repository.markEnded(
			roomId,
			endedAt,
			LiveBroadcastStatus.LIVE,
			LiveBroadcastStatus.ENDED
		);
		return updatedRows > 0;
	}
}
