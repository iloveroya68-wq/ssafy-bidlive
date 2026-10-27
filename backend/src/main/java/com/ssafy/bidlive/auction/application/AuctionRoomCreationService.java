package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_AUCTION_REQUEST;
import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_PRODUCT_REQUEST;
import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_SCHEDULE_TIME;
import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.UNAUTHORIZED;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.product.application.ProductRegistrationCommand;
import com.ssafy.bidlive.product.application.ProductRegistrationException;
import com.ssafy.bidlive.product.application.ProductRegistrationResult;
import com.ssafy.bidlive.product.application.ProductRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 등록과 대기 상태 경매방 생성을 단일 트랜잭션으로 조율합니다. */
@Service
public class AuctionRoomCreationService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final MemberPersistence memberPersistence;
	private final ProductRegistrationService productRegistrationService;
	private final AuctionRoomCreationPersistence persistence;
	private final Clock clock;

	@Autowired
	public AuctionRoomCreationService(
		MemberPersistence memberPersistence,
		ProductRegistrationService productRegistrationService,
		AuctionRoomCreationPersistence persistence
	) {
		this(
			memberPersistence,
			productRegistrationService,
			persistence,
			Clock.system(SERVICE_ZONE)
		);
	}

	AuctionRoomCreationService(
		MemberPersistence memberPersistence,
		ProductRegistrationService productRegistrationService,
		AuctionRoomCreationPersistence persistence,
		Clock clock
	) {
		this.memberPersistence = memberPersistence;
		this.productRegistrationService = productRegistrationService;
		this.persistence = persistence;
		this.clock = clock;
	}

	@Transactional
	public AuctionRoomCreationResult create(AuctionRoomCreationCommand command) {
		validateSeller(command);
		ValidatedAuction auction = validateAuction(command);

		ProductRegistrationResult product;
		try {
			product = productRegistrationService.register(
				productCommand(command)
			);
		} catch (ProductRegistrationException exception) {
			throw new AuctionRoomCreationException(
				INVALID_PRODUCT_REQUEST,
				exception
			);
		}

		OffsetDateTime createdAt = OffsetDateTime.now(clock);
		// 시작 방식과 무관하게 생성 직후는 항상 `WAITING`이다(명세 「경매방 생성」).
		//
		// 이 불변식은 값이 하나뿐이던 `AuctionRoomStatus` enum이 타입으로 들고 있던 것인데,
		// `AuctionStatus`(WAITING·LIVE·ENDED)로 통합하면서 타입이 강제하던 부분이 사라졌다.
		// 이제 그 보장은 이 한 줄과, 생성 응답의 `status`를 검증하는
		// `AuctionRoomCreationControllerIntegrationTest`에 있다.
		// 값이 하나인 enum을 유지하는 비용(같은 개념의 enum 둘이 같은 패키지에서 헷갈리는 자리)이
		// 더 크다고 판단했다 — MR !202 리뷰에서 합의.
		AuctionStatus status = AuctionStatus.WAITING;
		Long auctionId = persistence.save(
			product.productId(),
			auction.reservePrice(),
			auction.durationMinutes(),
			auction.startType(),
			auction.scheduledAt(),
			status,
			createdAt
		);

		return new AuctionRoomCreationResult(
			auctionId,
			product.productId(),
			command.sellerId(),
			status,
			auction.reservePrice(),
			auction.durationMinutes(),
			auction.startType(),
			auction.scheduledAt(),
			createdAt
		);
	}

	private void validateSeller(AuctionRoomCreationCommand command) {
		if (command == null
			|| command.sellerId() == null
			|| command.sellerId() <= 0) {
			throw new AuctionRoomCreationException(UNAUTHORIZED);
		}
		memberPersistence.findById(command.sellerId())
			.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new AuctionRoomCreationException(UNAUTHORIZED));
	}

	private ValidatedAuction validateAuction(AuctionRoomCreationCommand command) {
		if (command.reservePrice() == null
			|| command.reservePrice() <= 0
			|| command.durationMinutes() == null
			|| command.durationMinutes() <= 0) {
			throw new AuctionRoomCreationException(INVALID_AUCTION_REQUEST);
		}

		AuctionStartType startType;
		try {
			startType = AuctionStartType.from(command.startType());
		} catch (IllegalArgumentException exception) {
			throw new AuctionRoomCreationException(
				INVALID_AUCTION_REQUEST,
				exception
			);
		}

		OffsetDateTime scheduledAt = parseScheduledAt(command.scheduledAt());
		if (startType == AuctionStartType.IMMEDIATE && scheduledAt != null) {
			throw new AuctionRoomCreationException(INVALID_AUCTION_REQUEST);
		}
		if (startType == AuctionStartType.SCHEDULED && scheduledAt == null) {
			throw new AuctionRoomCreationException(INVALID_AUCTION_REQUEST);
		}
		if (scheduledAt != null && !scheduledAt.isAfter(OffsetDateTime.now(clock))) {
			throw new AuctionRoomCreationException(INVALID_SCHEDULE_TIME);
		}

		return new ValidatedAuction(
			command.reservePrice(),
			command.durationMinutes(),
			startType,
			scheduledAt
		);
	}

	private OffsetDateTime parseScheduledAt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value.trim());
		} catch (DateTimeParseException exception) {
			throw new AuctionRoomCreationException(
				INVALID_SCHEDULE_TIME,
				exception
			);
		}
	}

	private ProductRegistrationCommand productCommand(
		AuctionRoomCreationCommand command
	) {
		if (command.product() == null) {
			throw new AuctionRoomCreationException(INVALID_PRODUCT_REQUEST);
		}
		return new ProductRegistrationCommand(
			command.sellerId(),
			command.product().title(),
			command.product().description(),
			command.product().categoryCode(),
			command.product().imageUrl()
		);
	}

	private record ValidatedAuction(
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		OffsetDateTime scheduledAt
	) {
	}
}
