package com.ssafy.bidlive.media.infrastructure.persistence;

import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaLiveSnapshotRepositoryAdapter implements LiveSnapshotRepository {

	private final SpringDataLiveSnapshotRepository repository;

	public JpaLiveSnapshotRepositoryAdapter(SpringDataLiveSnapshotRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LiveSnapshot> findByAuctionId(Long auctionId) {
		return repository.findByAuctionId(auctionId).map(LiveSnapshotJpaEntity::toDomain);
	}

	@Override
	@Transactional
	public LiveSnapshot save(LiveSnapshot snapshot) {
		LiveSnapshotJpaEntity entity = repository.findByAuctionId(snapshot.auctionId())
			.map(existing -> {
				existing.apply(snapshot);
				return existing;
			})
			.orElseGet(() -> LiveSnapshotJpaEntity.from(snapshot));

		return repository.save(entity).toDomain();
	}
}
