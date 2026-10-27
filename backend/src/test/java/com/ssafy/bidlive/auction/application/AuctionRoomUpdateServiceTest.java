package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.product.application.ProductSnapshot;
import com.ssafy.bidlive.product.application.ProductUpdateException;
import com.ssafy.bidlive.product.application.ProductUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 수정 판정 순서, 예약 시각 해석, 상품 도메인 오류 변환을 검증한다. */
class AuctionRoomUpdateServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");
	private static final Instant FUTURE = Instant.parse("2026-07-21T12:00:00Z");
	private static final Instant PAST = Instant.parse("2026-07-21T09:00:00Z");
	private static final long AUCTION_ID = 100L;
	private static final long PRODUCT_ID = 501L;
	private static final long SELLER_ID = 25L;

	private AuctionRoomUpdatePersistence persistence;
	private ProductUpdateService productUpdateService;
	private AuctionRoomUpdateService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomUpdatePersistence.class);
		productUpdateService = mock(ProductUpdateService.class);
		service = new AuctionRoomUpdateService(
			persistence,
			productUpdateService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(persistence.findLastUpdatedAt(anyLong())).thenReturn(NOW);
		when(productUpdateService.update(any())).thenReturn(new ProductSnapshot(
			PRODUCT_ID, SELLER_ID, "현재 제목", "현재 설명", "ELECTRONICS", "https://cdn/x.png"
		));
		givenTarget(target("WAITING", false, "IMMEDIATE", null));
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.updateRoom(command(null, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.UNAUTHORIZED));
	}

	@Test
	void rejectsRequestWithoutAnyField() {
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b)))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.EMPTY_UPDATE_REQUEST));
		verify(persistence, never()).findUpdateTarget(anyLong());
	}

	@Test
	void rejectsMissingOrDeletedRoom() {
		when(persistence.findUpdateTarget(AUCTION_ID)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND));

		givenTarget(target("WAITING", true, "IMMEDIATE", null));
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND));
	}

	@Test
	void rejectsMemberWhoIsNotTheSeller() {
		assertThatThrownBy(() -> service.updateRoom(command(999L, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.NOT_AUCTION_SELLER));
	}

	@Test
	void rejectsRoomThatIsNotWaiting() {
		for (String status : new String[] {"LIVE", "ENDED", "CANCELLED"}) {
			givenTarget(target(status, false, "IMMEDIATE", null));
			assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.title("t"))))
				.satisfies(e -> assertThat(codeOf(e))
					.isEqualTo(AuctionRoomUpdateErrorCode.AUCTION_UPDATE_NOT_ALLOWED));
		}
	}

	@Test
	void keepsUnsentAuctionValues() {
		givenTarget(target("WAITING", false, "SCHEDULED", FUTURE));

		service.updateRoom(command(SELLER_ID, b -> b.reservePrice(70_000L)));

		ArgumentCaptor<Long> price = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Integer> duration = ArgumentCaptor.forClass(Integer.class);
		verify(persistence).updateAuctionRoom(
			anyLong(), price.capture(), duration.capture(), any(), any()
		);
		assertThat(price.getValue()).isEqualTo(70_000L);
		assertThat(duration.getValue()).isEqualTo(15);
	}

	@Test
	void rejectsNonPositiveAmounts() {
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.reservePrice(0L))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.duration(0))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));
	}

	/** DB 제약: `IMMEDIATE`면 `scheduled_at IS NULL`이어야 한다 */
	@Test
	void clearsScheduleWhenSwitchingToImmediate() {
		givenTarget(target("WAITING", false, "SCHEDULED", FUTURE));

		service.updateRoom(command(SELLER_ID, b -> b.startType("IMMEDIATE")));

		verify(persistence).updateAuctionRoom(
			AUCTION_ID, 50_000L, 15, AuctionStartType.IMMEDIATE, null
		);
	}

	/** `IMMEDIATE`인데 예약 시각을 함께 보내면 모순이다 — 조용히 버리면 예약된 줄 오해한다 */
	@Test
	void rejectsImmediateWithScheduledAt() {
		assertThatThrownBy(() -> service.updateRoom(command(
			SELLER_ID,
			b -> b.startType("IMMEDIATE").scheduledAt(FUTURE)
		)))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));
	}

	@Test
	void requiresFutureScheduleWhenSwitchingToScheduled() {
		// 현재가 IMMEDIATE(예약 시각 없음)인데 시각 없이 SCHEDULED로만 바꾸면 제약을 못 지킨다
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.startType("SCHEDULED"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));

		assertThatThrownBy(() -> service.updateRoom(command(
			SELLER_ID,
			b -> b.startType("SCHEDULED").scheduledAt(PAST)
		)))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));

		service.updateRoom(command(SELLER_ID, b -> b.startType("SCHEDULED").scheduledAt(FUTURE)));
		verify(persistence).updateAuctionRoom(
			AUCTION_ID, 50_000L, 15, AuctionStartType.SCHEDULED, FUTURE
		);
	}

	/**
	 * 예약 시각이 이미 지난 대기방에서 **무관한 필드만** 바꿔도 400이 되던 문제 (MR !197 리뷰).
	 * 방송 시작(472)이 아직 없어 예약 시각이 지나도 자동 시작하지 않으므로 흔한 상태이고,
	 * 그 과거 값은 이미 DB에 있어 쓰기를 막아도 지켜지는 게 없다.
	 */
	@Test
	void allowsEditingRoomWhoseStoredScheduleAlreadyPassed() {
		givenTarget(target("WAITING", false, "SCHEDULED", PAST));

		service.updateRoom(command(SELLER_ID, b -> b.reservePrice(70_000L)));

		verify(persistence).updateAuctionRoom(
			AUCTION_ID, 70_000L, 15, AuctionStartType.SCHEDULED, PAST
		);
	}

	/** 반면 **새 시각을 지정**하면 미래여야 한다 — 유지와 지정은 다르게 다룬다 */
	@Test
	void stillRejectsNewScheduleInThePast() {
		givenTarget(target("WAITING", false, "SCHEDULED", PAST));

		assertThatThrownBy(() -> service.updateRoom(command(
			SELLER_ID,
			b -> b.scheduledAt(PAST)
		)))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));
	}

	/**
	 * 비공개 방을 남이 수정하려 하면 403이 아니라 404다 (S15P11A704-482) — 403을 주면
	 * 상세·목록이 감춘 "그 방이 존재한다"는 사실이 드러난다.
	 */
	@Test
	void hidesPrivateRoomFromNonSellerWithNotFound() {
		givenTarget(target("WAITING", "PRIVATE", false, "IMMEDIATE", null));

		assertThatThrownBy(() -> service.updateRoom(command(999L, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND));
	}

	/** 판매자 본인은 자기 비공개 방을 고칠 수 있다 — visibility는 수정을 막는 조건이 아니다 */
	@Test
	void allowsSellerToUpdateOwnPrivateRoom() {
		givenTarget(target("WAITING", "PRIVATE", false, "IMMEDIATE", null));

		AuctionRoomUpdateResult result =
			service.updateRoom(command(SELLER_ID, b -> b.title("새 제목")));

		assertThat(result.auctionId()).isEqualTo(AUCTION_ID);
	}

	@Test
	void rejectsUnknownStartType() {
		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.startType("LATER"))))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST));
	}

	/** 상품만 고치는 요청은 경매방 행을 건드리지 않는다 */
	@Test
	void doesNotTouchAuctionRoomWhenOnlyProductChanges() {
		service.updateRoom(command(SELLER_ID, b -> b.title("새 제목")));

		verify(persistence, never()).updateAuctionRoom(anyLong(), anyLong(), anyInt(), any(), any());
		verify(productUpdateService).update(any());
	}

	@Test
	void mapsProductDomainFailures() {
		assertProductReasonMapsTo(
			ProductUpdateException.Reason.INVALID_REQUEST,
			AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
		);
		assertProductReasonMapsTo(
			ProductUpdateException.Reason.IMAGE_NOT_CLAIMABLE,
			AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST
		);
		assertProductReasonMapsTo(
			ProductUpdateException.Reason.PRODUCT_NOT_FOUND,
			AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND
		);
		assertProductReasonMapsTo(
			ProductUpdateException.Reason.NOT_PRODUCT_OWNER,
			AuctionRoomUpdateErrorCode.NOT_AUCTION_SELLER
		);
	}

	@Test
	void returnsResolvedValuesWithLastUpdatedAt() {
		givenTarget(target("WAITING", false, "SCHEDULED", FUTURE));
		Instant stamped = Instant.parse("2026-07-21T10:30:00Z");
		when(persistence.findLastUpdatedAt(AUCTION_ID)).thenReturn(stamped);

		AuctionRoomUpdateResult result =
			service.updateRoom(command(SELLER_ID, b -> b.reservePrice(70_000L)));

		assertThat(result.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(result.productId()).isEqualTo(PRODUCT_ID);
		assertThat(result.status()).isEqualTo("WAITING");
		assertThat(result.reservePrice()).isEqualTo(70_000L);
		assertThat(result.startType()).isEqualTo(AuctionStartType.SCHEDULED);
		assertThat(result.scheduledAt()).isEqualTo(FUTURE);
		assertThat(result.categoryCode()).isEqualTo("ELECTRONICS");
		assertThat(result.updatedAt()).isEqualTo(stamped);
	}

	/**
	 * `doThrow`를 쓰는 이유: 이미 예외를 던지도록 스텁된 목에 `when(mock.method(...))`를 다시 걸면
	 * 스텁을 등록하는 그 호출에서 앞서 지정한 예외가 먼저 터진다.
	 */
	private void assertProductReasonMapsTo(
		ProductUpdateException.Reason reason,
		AuctionRoomUpdateErrorCode expected
	) {
		org.mockito.Mockito.doThrow(new ProductUpdateException(reason))
			.when(productUpdateService).update(any());

		assertThatThrownBy(() -> service.updateRoom(command(SELLER_ID, b -> b.title("새 제목"))))
			.satisfies(e -> assertThat(codeOf(e)).isEqualTo(expected));
	}

	private void givenTarget(AuctionRoomUpdateTarget target) {
		when(persistence.findUpdateTarget(AUCTION_ID)).thenReturn(Optional.of(target));
	}

	private static AuctionRoomUpdateTarget target(
		String status,
		boolean deleted,
		String startType,
		Instant scheduledAt
	) {
		return target(status, "PUBLIC", deleted, startType, scheduledAt);
	}

	private static AuctionRoomUpdateTarget target(
		String status,
		String visibility,
		boolean deleted,
		String startType,
		Instant scheduledAt
	) {
		return new AuctionRoomUpdateTarget(
			AUCTION_ID, PRODUCT_ID, SELLER_ID, status, visibility, deleted,
			50_000L, 15, startType, scheduledAt
		);
	}

	private static AuctionRoomUpdateCommand command(
		Long memberId,
		java.util.function.UnaryOperator<Builder> customizer
	) {
		return customizer.apply(new Builder(memberId)).build();
	}

	/** 필드가 10개라 테스트마다 나열하지 않도록 최소 빌더를 둔다 */
	private static final class Builder {
		private final Long memberId;
		private String title;
		private Long reservePrice;
		private Integer durationMinutes;
		private String startType;
		private Instant scheduledAt;

		private Builder(Long memberId) {
			this.memberId = memberId;
		}

		Builder title(String value) {
			this.title = value;
			return this;
		}

		Builder reservePrice(Long value) {
			this.reservePrice = value;
			return this;
		}

		Builder duration(Integer value) {
			this.durationMinutes = value;
			return this;
		}

		Builder startType(String value) {
			this.startType = value;
			return this;
		}

		Builder scheduledAt(Instant value) {
			this.scheduledAt = value;
			return this;
		}

		AuctionRoomUpdateCommand build() {
			return new AuctionRoomUpdateCommand(
				AUCTION_ID, memberId, title, null, null, null,
				reservePrice, durationMinutes, startType, scheduledAt
			);
		}
	}

	private static AuctionRoomUpdateErrorCode codeOf(Throwable exception) {
		return ((AuctionRoomUpdateException) exception).getErrorCode();
	}
}
