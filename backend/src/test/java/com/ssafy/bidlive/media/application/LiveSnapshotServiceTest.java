package com.ssafy.bidlive.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerification;
import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerifier;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageException;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageGateway;
import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;
import com.ssafy.bidlive.media.domain.LiveSnapshotImage;
import com.ssafy.bidlive.media.domain.LiveSnapshotRepository;
import com.ssafy.bidlive.media.domain.SnapshotImageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 낙찰 맥락 검증을 통과한 요청만 저장소에 올라가고, S3 key가 요청 파일명이 아닌 방·경매
 * 식별자로 만들어지며, 저장소 실패가 메타 저장 없이 502로 이어지는지 검증합니다.
 */
class LiveSnapshotServiceTest {

	private static final String ROOM_ID = "room-123";
	private static final Long AUCTION_ID = 102L;
	private static final Long REQUESTER_ID = 25L;
	private static final Clock FIXED_CLOCK =
		Clock.fixed(Instant.parse("2026-07-20T12:10:00Z"), ZoneId.of("Asia/Seoul"));
	private static final OffsetDateTime EXPECTED_CAPTURED_AT =
		OffsetDateTime.parse("2026-07-20T21:10:00+09:00");

	private final StubAuctionSnapshotVerifier verifier = new StubAuctionSnapshotVerifier();
	private final RecordingSnapshotStorageGateway storage = new RecordingSnapshotStorageGateway();
	private final RecordingLiveSnapshotRepository repository = new RecordingLiveSnapshotRepository();
	private final LiveSnapshotService liveSnapshotService =
		new LiveSnapshotService(verifier, storage, repository, FIXED_CLOCK);

	@Test
	void storesSnapshotWithServerDecidedKeyAndCapturedAt() {
		LiveSnapshot snapshot = liveSnapshotService.storeSnapshot(command(SnapshotImageType.JPEG));

		assertThat(storage.uploadedKeys).containsExactly("snapshots/room-room-123/auction-102.jpg");
		assertThat(snapshot.roomId()).isEqualTo(ROOM_ID);
		assertThat(snapshot.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(snapshot.imageUrl()).isEqualTo("https://s3.test/snapshots/room-room-123/auction-102.jpg");
		assertThat(snapshot.capturedAt()).isEqualTo(EXPECTED_CAPTURED_AT);
		assertThat(repository.saved).hasSize(1);
	}

	/** 확장자는 클라이언트 파일명이 아니라 검증을 통과한 이미지 형식에서만 나온다. */
	@Test
	void derivesKeyExtensionFromVerifiedImageType() {
		liveSnapshotService.storeSnapshot(command(SnapshotImageType.PNG));

		assertThat(storage.uploadedKeys).containsExactly("snapshots/room-room-123/auction-102.png");
	}

	@Test
	void verifiesSettlementContextWithServerSideIdentifiers() {
		liveSnapshotService.storeSnapshot(command(SnapshotImageType.JPEG));

		assertThat(verifier.calls).containsExactly("verify:room-123:102:25");
	}

	@ParameterizedTest
	@CsvSource({
		"AUCTION_NOT_FOUND, AUCTION_NOT_FOUND",
		"REQUESTER_NOT_ALLOWED, NOT_SNAPSHOT_UPLOADER",
		"AUCTION_NOT_SETTLED, AUCTION_NOT_SETTLED"
	})
	void rejectsBeforeUploadWhenSettlementContextFails(
		AuctionSnapshotVerification verification,
		LiveSnapshotFailureCode expectedFailureCode
	) {
		verifier.result = verification;

		assertThatThrownBy(() -> liveSnapshotService.storeSnapshot(command(SnapshotImageType.JPEG)))
			.isInstanceOf(LiveSnapshotRejectedException.class)
			.extracting(exception -> ((LiveSnapshotRejectedException) exception).getFailureCode())
			.isEqualTo(expectedFailureCode);

		assertThat(storage.uploadedKeys).isEmpty();
		assertThat(repository.saved).isEmpty();
	}

	/** 업로드 실패가 예외로 올라가야 URL 없는 메타가 남지 않고 트랜잭션이 롤백된다. */
	@Test
	void reportsStorageFailureWithoutSavingMetadata() {
		storage.failure = new SnapshotStorageException("Failed to upload snapshot to S3");

		assertThatThrownBy(() -> liveSnapshotService.storeSnapshot(command(SnapshotImageType.JPEG)))
			.isInstanceOf(LiveSnapshotRejectedException.class)
			.hasCauseInstanceOf(SnapshotStorageException.class)
			.extracting(exception -> ((LiveSnapshotRejectedException) exception).getFailureCode())
			.isEqualTo(LiveSnapshotFailureCode.SNAPSHOT_STORAGE_ERROR);

		assertThat(repository.saved).isEmpty();
	}

	private LiveSnapshotCommand command(SnapshotImageType type) {
		return new LiveSnapshotCommand(
			ROOM_ID,
			AUCTION_ID,
			REQUESTER_ID,
			new LiveSnapshotImage(new byte[] {1, 2, 3}, type)
		);
	}

	private static final class StubAuctionSnapshotVerifier implements AuctionSnapshotVerifier {

		private final List<String> calls = new ArrayList<>();
		private AuctionSnapshotVerification result = AuctionSnapshotVerification.ALLOWED;

		@Override
		public AuctionSnapshotVerification verify(String roomId, Long auctionId, Long requesterId) {
			calls.add("verify:" + roomId + ":" + auctionId + ":" + requesterId);
			return result;
		}
	}

	private static final class RecordingSnapshotStorageGateway implements SnapshotStorageGateway {

		private final List<String> uploadedKeys = new ArrayList<>();
		private SnapshotStorageException failure;

		@Override
		public String upload(String key, LiveSnapshotImage image) {
			if (failure != null) {
				throw failure;
			}
			uploadedKeys.add(key);
			return "https://s3.test/" + key;
		}
	}

	private static final class RecordingLiveSnapshotRepository implements LiveSnapshotRepository {

		private final List<LiveSnapshot> saved = new ArrayList<>();

		@Override
		public Optional<LiveSnapshot> findByAuctionId(Long auctionId) {
			return saved.stream().filter(snapshot -> snapshot.auctionId().equals(auctionId))
				.findFirst();
		}

		@Override
		public LiveSnapshot save(LiveSnapshot snapshot) {
			saved.add(snapshot);
			return snapshot;
		}
	}
}
