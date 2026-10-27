package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자가 시작 전 경매방을 삭제할 수 있는지 판정하고 논리 삭제한다. */
@Service
public class AuctionRoomDeleteService {

	private final AuctionRoomDeletePersistence deletePersistence;

	public AuctionRoomDeleteService(AuctionRoomDeletePersistence deletePersistence) {
		this.deletePersistence = deletePersistence;
	}

	/**
	 * 판정 순서가 계약이다: 없음·삭제됨(404) → **비공개 + 비판매자(404)** → 판매자 아님(403)
	 * → 시작됨(409) → 참여 이력(409).
	 *
	 * <p>삭제 여부를 판매자 검사보다 **먼저** 보는 이유는 이미 삭제된 방의 판매자 정보를 흘리지 않기
	 * 위해서다. 명세가 "멱등하게 처리하거나 404로 통일한다"로 선택지를 준 부분은 **404로 통일**했다 —
	 * 삭제된 방을 다시 지우는 요청에 204를 주면 호출자는 방이 방금 있었다고 읽는다.
	 *
	 * <p>비공개 방을 남이 건드리면 403이 아니라 404다(S15P11A704-482). 상세 조회·목록·다가오는 방이
	 * 모두 비공개 방을 "없는 것"으로 다루는데 여기서만 403을 주면 **그 방의 존재가 드러난다.**
	 * 단 `visibility`는 삭제 자체를 막지 않는다 — 판매자 본인은 자기 비공개 방을 지울 수 있다.
	 */
	@Transactional
	public void deleteRoom(long auctionId, Long memberId) {
		if (memberId == null) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.UNAUTHORIZED);
		}
		AuctionRoomDeletionTarget target = deletePersistence.findDeletionTarget(auctionId)
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionRoomDeleteException(
				AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND
			));

		boolean seller = memberId.equals(target.sellerId());
		if (!seller && !AuctionRoomVisibility.isPublic(target.visibility())) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND);
		}
		if (!seller) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.NOT_AUCTION_SELLER);
		}
		// 시작 전 상태는 `WAITING`뿐이다. 명세는 `DRAFT`·`SCHEDULED`도 적었지만 두 값은 현재 스키마와
		// 상태 모델(WAITING·LIVE·ENDED)에 없다 — 알 수 없는 값도 "시작됨"으로 보수적으로 막는다.
		if (!AuctionStatus.WAITING.name().equals(target.status())) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.AUCTION_ALREADY_STARTED);
		}
		if (target.hasParticipation()) {
			throw new AuctionRoomDeleteException(
				AuctionRoomDeleteErrorCode.AUCTION_HAS_PARTICIPATION
			);
		}

		if (deletePersistence.markDeleted(auctionId) == 0) {
			// 판정과 삭제 사이에 다른 요청이 먼저 지웠다 — 결과적으로 없는 방이다
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND);
		}
	}
}
