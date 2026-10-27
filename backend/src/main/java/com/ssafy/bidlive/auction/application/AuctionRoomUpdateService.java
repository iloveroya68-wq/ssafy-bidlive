package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionRoomVisibility;
import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.product.application.ProductSnapshot;
import com.ssafy.bidlive.product.application.ProductUpdateCommand;
import com.ssafy.bidlive.product.application.ProductUpdateException;
import com.ssafy.bidlive.product.application.ProductUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 시작 전 경매방을 부분 수정한다. 상품 쪽 값은 `product` 도메인 계약에 위임하고
 * 경매 조건만 직접 다룬다 — 두 갱신이 한 트랜잭션으로 묶인다.
 */
@Service
public class AuctionRoomUpdateService {

	private final AuctionRoomUpdatePersistence updatePersistence;
	private final ProductUpdateService productUpdateService;
	private final Clock clock;

	@Autowired
	public AuctionRoomUpdateService(
		AuctionRoomUpdatePersistence updatePersistence,
		ProductUpdateService productUpdateService
	) {
		this(updatePersistence, productUpdateService, Clock.systemUTC());
	}

	AuctionRoomUpdateService(
		AuctionRoomUpdatePersistence updatePersistence,
		ProductUpdateService productUpdateService,
		Clock clock
	) {
		this.updatePersistence = updatePersistence;
		this.productUpdateService = productUpdateService;
		this.clock = clock;
	}

	/**
	 * 판정 순서가 계약이다: 인증(401) → 빈 요청(400) → 없음·삭제됨(404) → 판매자 아님(403)
	 * → 시작됨(409) → 값 오류(400).
	 */
	@Transactional
	public AuctionRoomUpdateResult updateRoom(AuctionRoomUpdateCommand command) {
		if (command.memberId() == null) {
			throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.UNAUTHORIZED);
		}
		if (command.isEmpty()) {
			throw new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.EMPTY_UPDATE_REQUEST
			);
		}

		AuctionRoomUpdateTarget target = updatePersistence.findUpdateTarget(command.auctionId())
			.filter(found -> !found.deleted())
			.orElseThrow(() -> new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND
			));
		boolean seller = command.memberId().equals(target.sellerId());
		// 비공개 방을 남이 건드리면 403이 아니라 404다 (S15P11A704-482) — 상세·목록이 비공개 방을
		// "없는 것"으로 다루는데 여기서만 403을 주면 그 방의 존재가 드러난다.
		// 수정 자체를 막는 조건은 아니다: 판매자 본인은 자기 비공개 방을 고칠 수 있다.
		if (!seller && !AuctionRoomVisibility.isPublic(target.visibility())) {
			throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND);
		}
		if (!seller) {
			throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.NOT_AUCTION_SELLER);
		}
		if (!AuctionStatus.WAITING.name().equals(target.status())) {
			throw new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.AUCTION_UPDATE_NOT_ALLOWED
			);
		}

		ResolvedAuction auction = resolveAuction(command, target);
		ProductSnapshot product = updateProduct(command, target);
		if (!command.hasNoAuctionChange()) {
			updatePersistence.updateAuctionRoom(
				command.auctionId(),
				auction.reservePrice(),
				auction.durationMinutes(),
				auction.startType(),
				auction.scheduledAt()
			);
		}

		return new AuctionRoomUpdateResult(
			target.auctionId(),
			target.productId(),
			target.status(),
			product.title(),
			product.description(),
			product.categoryCode(),
			product.imageUrl(),
			auction.reservePrice(),
			auction.durationMinutes(),
			auction.startType(),
			auction.scheduledAt(),
			updatePersistence.findLastUpdatedAt(command.auctionId())
		);
	}

	private ProductSnapshot updateProduct(
		AuctionRoomUpdateCommand command,
		AuctionRoomUpdateTarget target
	) {
		try {
			return productUpdateService.update(new ProductUpdateCommand(
				target.productId(),
				target.sellerId(),
				command.title(),
				command.description(),
				command.categoryCode(),
				command.imageUrl()
			));
		} catch (ProductUpdateException exception) {
			throw new AuctionRoomUpdateException(mapProductReason(exception), exception);
		}
	}

	private static AuctionRoomUpdateErrorCode mapProductReason(ProductUpdateException exception) {
		return switch (exception.getReason()) {
			// 경매방이 있으면 상품도 있어야 한다(FK) — 어긋났다면 방을 못 찾은 것과 같다
			case PRODUCT_NOT_FOUND -> AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND;
			case NOT_PRODUCT_OWNER -> AuctionRoomUpdateErrorCode.NOT_AUCTION_SELLER;
			// 없는 카테고리·형식 오류·클레임 못 한 이미지는 전부 요청 값 문제다
			case INVALID_REQUEST, IMAGE_NOT_CLAIMABLE ->
				AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST;
		};
	}

	/**
	 * `startType`과 `scheduledAt`은 따로 볼 수 없다 — DB 제약(`ck_auction_room_schedule`)이
	 * `IMMEDIATE`면 `scheduled_at IS NULL`, `SCHEDULED`면 `NOT NULL`을 요구한다.
	 */
	private ResolvedAuction resolveAuction(
		AuctionRoomUpdateCommand command,
		AuctionRoomUpdateTarget target
	) {
		long reservePrice = command.reservePrice() == null
			? target.reservePrice()
			: command.reservePrice();
		int durationMinutes = command.durationMinutes() == null
			? target.durationMinutes()
			: command.durationMinutes();
		if (reservePrice <= 0 || durationMinutes <= 0) {
			throw new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
			);
		}

		AuctionStartType startType = parseStartType(command.startType(), target.startType());
		if (startType == AuctionStartType.IMMEDIATE) {
			// 명세: "IMMEDIATE로 변경하면 scheduledAt은 제거한다". 그런데 같은 요청에 예약 시각이
			// 함께 오면 모순이다 — 조용히 버리면 예약이 걸렸다고 오해하므로 거절한다.
			if (command.scheduledAt() != null) {
				throw new AuctionRoomUpdateException(
					AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
				);
			}
			return new ResolvedAuction(reservePrice, durationMinutes, startType, null);
		}

		if (command.scheduledAt() == null) {
			// 저장된 값을 그대로 유지하는 경로다. **미래성을 다시 따지지 않는다** —
			// 예약 시각이 이미 지난 대기방(자동 시작이 없어 흔한 상태다)에서 하한가만 고치려는
			// 요청이 이유도 보이지 않는 400으로 막히고, 그 과거 값은 이미 DB에 있어 쓰기를 막아도
			// 지켜지는 게 없다. 미래여야 한다는 규칙은 **새 시각을 지정할 때** 적용한다.
			Instant kept = target.scheduledAt();
			if (kept == null) {
				// `SCHEDULED`인데 시각이 없으면 DB 제약(`ck_auction_room_schedule`)을 못 지킨다.
				// 즉시 시작에서 예약으로 바꾸면서 시각을 안 보낸 경우가 여기다.
				throw new AuctionRoomUpdateException(
					AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
				);
			}
			return new ResolvedAuction(reservePrice, durationMinutes, startType, kept);
		}

		Instant scheduledAt = command.scheduledAt();
		if (!scheduledAt.isAfter(clock.instant())) {
			throw new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
			);
		}
		return new ResolvedAuction(reservePrice, durationMinutes, startType, scheduledAt);
	}

	private static AuctionStartType parseStartType(String requested, String current) {
		try {
			return AuctionStartType.from(requested == null ? current : requested);
		} catch (IllegalArgumentException exception) {
			throw new AuctionRoomUpdateException(
				AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST,
				exception
			);
		}
	}

	private record ResolvedAuction(
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		Instant scheduledAt
	) {
	}
}
