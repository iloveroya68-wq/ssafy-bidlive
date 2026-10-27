package com.ssafy.bidlive.media.application.port.out;

/** 낙찰 스냅샷 업로드 가능 여부에 대한 auction 도메인의 판정 결과입니다. */
public enum AuctionSnapshotVerification {

	/** 방과 경매가 모두 존재하고, 낙찰이 확정되었으며, 요청자가 업로드 자격을 가진 상태. */
	ALLOWED,

	/** 방 또는 경매를 찾을 수 없는 상태. */
	AUCTION_NOT_FOUND,

	/** 경매는 있으나 요청자에게 스냅샷 업로드 자격이 없는 상태. */
	REQUESTER_NOT_ALLOWED,

	/** 경매가 아직 낙찰 확정 상태가 아닌 상태. */
	AUCTION_NOT_SETTLED
}
