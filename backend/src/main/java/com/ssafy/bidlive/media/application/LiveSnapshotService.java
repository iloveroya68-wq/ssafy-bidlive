package com.ssafy.bidlive.media.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerification;
import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerifier;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageException;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageGateway;
import com.ssafy.bidlive.media.domain.LiveSnapshot;
import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;
import com.ssafy.bidlive.media.domain.LiveSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드된 낙찰 스냅샷을 낙찰 맥락 검증 후 저장소와 DB에 남기는 유스케이스입니다.
 *
 * <p>순서는 낙찰 맥락 검증 → 저장소 업로드 → 메타 저장으로 고정한다. 검증을 먼저 두는 이유는
 * 자격 없는 요청의 이미지를 저장소에 올리지 않기 위해서이고, 업로드를 메타 저장보다 앞에 두는
 * 이유는 업로드 실패 시 URL 없는 메타가 남지 않게 하기 위해서다. 업로드 실패는 예외로 올려
 * 트랜잭션을 롤백한다.
 *
 * <p>S3 key는 방과 경매 식별자만으로 만들며 클라이언트가 보낸 파일명을 쓰지 않는다. 같은 경매를
 * 다시 올리면 같은 key를 덮어쓰고 메타도 갱신되므로 경매당 스냅샷은 항상 하나다.
 */
@Service
public class LiveSnapshotService {

	/** 캡처 시각도 LIVE-01·LIVE-02와 같은 서비스 기준 오프셋을 사용한다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private static final String KEY_PATTERN = "snapshots/room-%s/auction-%d.%s";

	private final AuctionSnapshotVerifier auctionSnapshotVerifier;
	private final SnapshotStorageGateway snapshotStorageGateway;
	private final LiveSnapshotRepository liveSnapshotRepository;
	private final Clock clock;

	@Autowired
	public LiveSnapshotService(
		AuctionSnapshotVerifier auctionSnapshotVerifier,
		SnapshotStorageGateway snapshotStorageGateway,
		LiveSnapshotRepository liveSnapshotRepository
	) {
		this(auctionSnapshotVerifier, snapshotStorageGateway, liveSnapshotRepository,
			Clock.system(SERVICE_ZONE));
	}

	/** 캡처 시각을 고정해야 하는 테스트에서만 사용한다. */
	LiveSnapshotService(
		AuctionSnapshotVerifier auctionSnapshotVerifier,
		SnapshotStorageGateway snapshotStorageGateway,
		LiveSnapshotRepository liveSnapshotRepository,
		Clock clock
	) {
		this.auctionSnapshotVerifier = auctionSnapshotVerifier;
		this.snapshotStorageGateway = snapshotStorageGateway;
		this.liveSnapshotRepository = liveSnapshotRepository;
		this.clock = clock;
	}

	@Transactional
	public LiveSnapshot storeSnapshot(LiveSnapshotCommand command) {
		verifySettlementContext(command);

		String key = KEY_PATTERN.formatted(
			command.roomId(),
			command.auctionId(),
			command.image().type().extension()
		);
		String imageUrl = upload(key, command);

		return liveSnapshotRepository.save(new LiveSnapshot(
			command.roomId(),
			command.auctionId(),
			imageUrl,
			OffsetDateTime.now(clock)
		));
	}

	private void verifySettlementContext(LiveSnapshotCommand command) {
		AuctionSnapshotVerification verification = auctionSnapshotVerifier.verify(
			command.roomId(),
			command.auctionId(),
			command.requesterId()
		);

		switch (verification) {
			case ALLOWED -> {
			}
			case AUCTION_NOT_FOUND ->
				throw new LiveSnapshotRejectedException(LiveSnapshotFailureCode.AUCTION_NOT_FOUND);
			case REQUESTER_NOT_ALLOWED ->
				throw new LiveSnapshotRejectedException(
					LiveSnapshotFailureCode.NOT_SNAPSHOT_UPLOADER);
			case AUCTION_NOT_SETTLED ->
				throw new LiveSnapshotRejectedException(
					LiveSnapshotFailureCode.AUCTION_NOT_SETTLED);
		}
	}

	private String upload(String key, LiveSnapshotCommand command) {
		try {
			return snapshotStorageGateway.upload(key, command.image());
		} catch (SnapshotStorageException exception) {
			throw new LiveSnapshotRejectedException(
				LiveSnapshotFailureCode.SNAPSHOT_STORAGE_ERROR, exception);
		}
	}
}
