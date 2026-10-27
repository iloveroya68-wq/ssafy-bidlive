package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode.AUCTION_DELETED;
import static com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode.INVALID_AUCTION_ID;

import java.time.Clock;

import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 정책과 선택 인증 정보를 적용해 경매방 상세 조회를 조율합니다. */
@Service
public class AuctionRoomDetailQueryService {

	private static final String BLOCKED_MODERATION_STATUS = "BLOCKED";

	private final AuctionRoomDetailPersistence detailPersistence;
	private final ViewerCountQueryService viewerCountQueryService;
	private final Clock clock;

	@Autowired
	public AuctionRoomDetailQueryService(
		AuctionRoomDetailPersistence detailPersistence,
		ViewerCountQueryService viewerCountQueryService
	) {
		this(
			detailPersistence,
			viewerCountQueryService,
			Clock.systemDefaultZone()
		);
	}

	AuctionRoomDetailQueryService(
		AuctionRoomDetailPersistence detailPersistence,
		ViewerCountQueryService viewerCountQueryService,
		Clock clock
	) {
		this.detailPersistence = detailPersistence;
		this.viewerCountQueryService = viewerCountQueryService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AuctionRoomDetailResult getRoom(
		long auctionId,
		AuctionRoomViewerContext viewer
	) {
		if (auctionId <= 0) {
			throw new AuctionRoomDetailException(INVALID_AUCTION_ID);
		}

		AuctionRoomDetailRow room = detailPersistence.findByAuctionId(auctionId)
			.orElseThrow(() -> new AuctionRoomDetailException(AUCTION_NOT_FOUND));
		if (room.deletedAt() != null) {
			throw new AuctionRoomDetailException(AUCTION_DELETED);
		}
		if (!AuctionRoomVisibility.isPublic(room.visibility())
			|| BLOCKED_MODERATION_STATUS.equals(room.productModerationStatus())) {
			throw new AuctionRoomDetailException(AUCTION_NOT_FOUND);
		}

		boolean isSeller = viewer.memberId() != null
			&& viewer.memberId() == room.sellerId();
		Integer notificationCount = isSeller || viewer.admin()
			? room.notificationCount()
			: null;
		int viewerCount = Math.toIntExact(
			viewerCountQueryService.activeViewerCount(auctionId)
		);

		return new AuctionRoomDetailResult(
			clock.instant(),
			room.auctionId(),
			room.title(),
			room.status(),
			room.reservePrice(),
			room.scheduledAt(),
			room.durationMinutes(),
			viewerCount,
			isSeller,
			notificationCount,
			new AuctionRoomDetailResult.Product(
				room.productId(),
				room.productTitle(),
				room.productDescription(),
				room.productCategoryCode(),
				room.productImageUrl()
			),
			new AuctionRoomDetailResult.Seller(
				SellerNicknameMask.of(room.sellerNickname()),
				null
			),
			canEnter(room.status())
		);
	}

	private static boolean canEnter(AuctionStatus status) {
		return status == AuctionStatus.WAITING || status == AuctionStatus.LIVE;
	}
}
