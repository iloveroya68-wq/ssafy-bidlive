package com.ssafy.bidlive.media.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLiveSnapshotRepository
	extends JpaRepository<LiveSnapshotJpaEntity, Long> {

	Optional<LiveSnapshotJpaEntity> findByAuctionId(Long auctionId);
}
