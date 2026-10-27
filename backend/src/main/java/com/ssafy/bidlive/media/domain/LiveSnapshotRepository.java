package com.ssafy.bidlive.media.domain;

import java.util.Optional;

/** 낙찰 스냅샷 메타의 저장과 조회 계약입니다. */
public interface LiveSnapshotRepository {

	Optional<LiveSnapshot> findByAuctionId(Long auctionId);

	/**
	 * 경매당 스냅샷은 하나이므로 같은 경매의 재업로드는 새 행을 만들지 않고 URL과 캡처 시각을
	 * 덮어쓴다. S3 key도 경매 단위로 고정되어 있어 저장소와 메타가 함께 최신 이미지를 가리킨다.
	 */
	LiveSnapshot save(LiveSnapshot snapshot);
}
