package com.ssafy.bidlive.media.application.port.out;

/**
 * 낙찰 스냅샷을 "언제·누가·어느 경매에" 올릴 수 있는지 auction 도메인에 확인하는 포트입니다.
 *
 * <p>업로드 요청이 보낸 값만으로는 낙찰 여부와 요청자 자격을 알 수 없으므로, 이미지와 경매의
 * 연결은 이 판정을 통과한 뒤에만 성립한다. 판매자·낙찰자 중 누구를 허용하는지와 같은 자격
 * 정책은 경매 정보를 소유한 쪽이 정해야 하므로 이 포트는 결과만 받는다.
 *
 * <p>TODO(LIVE-06): auction 도메인의 공개 Application 계약이 만들어지면 그 도메인에서 이
 * 포트를 구현하고, media의 임시 구현체
 * ({@code com.ssafy.bidlive.media.infrastructure.PendingAuctionSnapshotVerifier})를 제거한다.
 */
public interface AuctionSnapshotVerifier {

	AuctionSnapshotVerification verify(String roomId, Long auctionId, Long requesterId);
}
