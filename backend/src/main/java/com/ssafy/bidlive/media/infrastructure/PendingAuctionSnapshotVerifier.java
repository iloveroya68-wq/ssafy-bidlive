package com.ssafy.bidlive.media.infrastructure;

import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerification;
import com.ssafy.bidlive.media.application.port.out.AuctionSnapshotVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * auction 도메인 검증 구현이 생기기 전까지 낙찰 스냅샷 업로드를 기본 차단하는 임시 구현체입니다.
 *
 * <p>경매 상태와 요청자 자격을 확인할 방법이 없는 상태에서 업로드를 허용하면 "언제·누가·어느
 * 경매에" 올릴 수 있는지에 대한 서버 통제가 사라진다. 그래서 기본 판정은
 * {@code AUCTION_NOT_SETTLED}이며, 프론트 연동 확인이 필요한 환경에서만 설정으로 우회한다.
 *
 * <p>TODO(LIVE-06): auction 도메인이 {@link AuctionSnapshotVerifier}를 구현하면 이 클래스와
 * {@link LiveSnapshotProperties}, 그리고 관련 설정 키를 함께 제거한다. 두 구현이 동시에 등록되면
 * 애플리케이션 시작이 실패하므로 교체 시점이 드러난다.
 */
@Component
@EnableConfigurationProperties(LiveSnapshotProperties.class)
public class PendingAuctionSnapshotVerifier implements AuctionSnapshotVerifier {

	private static final Logger log =
		LoggerFactory.getLogger(PendingAuctionSnapshotVerifier.class);

	private final LiveSnapshotProperties properties;

	public PendingAuctionSnapshotVerifier(LiveSnapshotProperties properties) {
		this.properties = properties;
	}

	@Override
	public AuctionSnapshotVerification verify(String roomId, Long auctionId, Long requesterId) {
		if (properties.isSkipAuctionVerification()) {
			log.warn(
				"Snapshot auction verification skipped by configuration: roomId={}, auctionId={}",
				roomId, auctionId);
			return AuctionSnapshotVerification.ALLOWED;
		}

		log.info(
			"Snapshot rejected because auction verification is not implemented yet:"
				+ " roomId={}, auctionId={}", roomId, auctionId);
		return AuctionSnapshotVerification.AUCTION_NOT_SETTLED;
	}
}
