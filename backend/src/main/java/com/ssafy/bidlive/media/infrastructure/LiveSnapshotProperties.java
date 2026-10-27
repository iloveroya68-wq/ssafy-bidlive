package com.ssafy.bidlive.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 낙찰 스냅샷 업로드의 임시 운영 스위치를 제공합니다. */
@ConfigurationProperties(prefix = "app.media.snapshot")
public class LiveSnapshotProperties {

	/**
	 * auction 도메인 검증 구현이 없는 동안 낙찰 맥락 검증을 건너뛸지 여부이며 기본값은 차단이다.
	 *
	 * <p>켜면 낙찰 확정 여부와 요청자 자격을 확인하지 않고 업로드를 허용하므로 로컬·개발 환경의
	 * 프론트 연동 확인 목적으로만 사용한다. 운영 환경에서 켜면 안 된다.
	 */
	private boolean skipAuctionVerification = false;

	public boolean isSkipAuctionVerification() {
		return skipAuctionVerification;
	}

	public void setSkipAuctionVerification(boolean skipAuctionVerification) {
		this.skipAuctionVerification = skipAuctionVerification;
	}
}
