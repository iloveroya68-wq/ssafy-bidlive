package com.ssafy.bidlive.media.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 경매당 스냅샷이 하나로 유지되고 재업로드가 같은 행의 URL과 캡처 시각을 갱신하는지 검증합니다. */
@DataJpaTest
@Import(JpaLiveSnapshotRepositoryAdapter.class)
class JpaLiveSnapshotRepositoryAdapterTest {

	private static final String ROOM_ID = "room-123";
	private static final Long AUCTION_ID = 102L;
	private static final OffsetDateTime CAPTURED_AT =
		OffsetDateTime.parse("2026-07-20T21:10:00+09:00");

	@Autowired
	private SpringDataLiveSnapshotRepository springDataRepository;

	@Autowired
	private LiveSnapshotRepository liveSnapshotRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void storesSnapshotMetadata() {
		liveSnapshotRepository.save(snapshot("https://s3.test/first.jpg", CAPTURED_AT));
		flushAndClear();

		LiveSnapshot stored = liveSnapshotRepository.findByAuctionId(AUCTION_ID).orElseThrow();

		assertThat(stored.roomId()).isEqualTo(ROOM_ID);
		assertThat(stored.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(stored.imageUrl()).isEqualTo("https://s3.test/first.jpg");
		assertThat(stored.capturedAt()).isEqualTo(CAPTURED_AT);
	}

	@Test
	void overwritesSameAuctionSnapshotInsteadOfInsertingAnotherRow() {
		liveSnapshotRepository.save(snapshot("https://s3.test/first.jpg", CAPTURED_AT));
		flushAndClear();

		OffsetDateTime recapturedAt = OffsetDateTime.parse("2026-07-20T21:20:00+09:00");
		liveSnapshotRepository.save(snapshot("https://s3.test/second.jpg", recapturedAt));
		flushAndClear();

		LiveSnapshot stored = liveSnapshotRepository.findByAuctionId(AUCTION_ID).orElseThrow();

		assertThat(springDataRepository.count()).isEqualTo(1);
		assertThat(stored.imageUrl()).isEqualTo("https://s3.test/second.jpg");
		assertThat(stored.capturedAt()).isEqualTo(recapturedAt);
	}

	@Test
	void keepsSnapshotsOfDifferentAuctionsSeparately() {
		liveSnapshotRepository.save(snapshot("https://s3.test/first.jpg", CAPTURED_AT));
		liveSnapshotRepository.save(new LiveSnapshot(
			ROOM_ID, 103L, "https://s3.test/other.jpg", CAPTURED_AT));
		flushAndClear();

		assertThat(springDataRepository.count()).isEqualTo(2);
		assertThat(liveSnapshotRepository.findByAuctionId(103L).orElseThrow().imageUrl())
			.isEqualTo("https://s3.test/other.jpg");
	}

	@Test
	void returnsEmptyForAuctionWithoutSnapshot() {
		assertThat(liveSnapshotRepository.findByAuctionId(AUCTION_ID)).isEmpty();
	}

	private LiveSnapshot snapshot(String imageUrl, OffsetDateTime capturedAt) {
		return new LiveSnapshot(ROOM_ID, AUCTION_ID, imageUrl, capturedAt);
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
