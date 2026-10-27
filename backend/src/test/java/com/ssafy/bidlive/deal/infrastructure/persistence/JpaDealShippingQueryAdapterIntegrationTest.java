package com.ssafy.bidlive.deal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.deal.application.DealShippingView;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 정보 조회의 **당사자 판정**과 미입력 상태 표현을 실제 PostgreSQL로 검증한다 (S15P11A704-694).
 *
 * <p>여기가 새면 구매자의 실명·전화번호·주소가 남에게 나간다. 화면에는 아무 티도 안 나므로
 * 조회 조건 자체를 잠근다.
 */
@SpringBootTest
@Transactional
class JpaDealShippingQueryAdapterIntegrationTest {

	@Autowired
	private JpaDealShippingQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		DealTestFixture.insert(entityManager);
	}

	/** 판매자가 보낼 곳을 본다 — 이 API가 생긴 이유 그 자체다. */
	@Test
	void givesSellerTheBuyerAddress() {
		DealShippingView view = adapter.findForParticipant(
			DealTestFixture.WAITING_SHIPMENT_DEAL_ID,
			DealTestFixture.SELLER_ID
		).orElseThrow();

		assertThat(view.viewerRole()).isEqualTo(DealRole.SELLER);
		assertThat(view.dealStatus()).isEqualTo(DealStatus.WAITING_SHIPMENT);
		assertThat(view.recipientName()).isEqualTo("김서연");
		assertThat(view.recipientPhone()).isEqualTo("010-1234-5678");
		assertThat(view.postalCode()).isEqualTo("06236");
		assertThat(view.address()).isEqualTo("서울시 강남구");
		assertThat(view.addressDetail()).isEqualTo("101동 202호");
		assertThat(view.hasAddress()).isTrue();
	}

	@Test
	void givesBuyerTheirOwnAddressWithTheirRole() {
		DealShippingView view = adapter.findForParticipant(
			DealTestFixture.WAITING_SHIPMENT_DEAL_ID,
			DealTestFixture.BUYER_ID
		).orElseThrow();

		assertThat(view.viewerRole()).isEqualTo(DealRole.BUYER);
		assertThat(view.recipientName()).isEqualTo("김서연");
	}

	/** 남의 거래는 **행 자체가 안 나온다.** 조회해 놓고 버리는 경로를 만들지 않는다. */
	@Test
	void returnsNothingForMembersOutsideTheDeal() {
		assertThat(adapter.findForParticipant(
			DealTestFixture.WAITING_SHIPMENT_DEAL_ID,
			DealTestFixture.STRANGER_ID
		)).isEmpty();
	}

	@Test
	void returnsNothingForUnknownDeal() {
		assertThat(adapter.findForParticipant(999_999L, DealTestFixture.SELLER_ID)).isEmpty();
	}

	/**
	 * 배송지 입력 전에는 `deal_shipping` 행이 아예 없다. 그래도 **거래는 찾은 것**이다 —
	 * 「아직 안 받았다」가 판매자에게 유효한 답이라 빈 값으로 내려간다.
	 */
	@Test
	void reportsMissingAddressInsteadOfHidingTheDeal() {
		DealShippingView view = adapter.findForParticipant(
			DealTestFixture.WAITING_ADDRESS_DEAL_ID,
			DealTestFixture.SELLER_ID
		).orElseThrow();

		assertThat(view.hasAddress()).isFalse();
		assertThat(view.recipientName()).isNull();
		assertThat(view.address()).isNull();
		assertThat(view.dealStatus()).isEqualTo(DealStatus.WAITING_ADDRESS);
	}

	/** 발송 뒤에는 발송 시각과 자동 확정 예정 시각이 함께 온다 — 판매자가 남은 기한을 본다. */
	@Test
	void exposesShipmentTimesWhileWaitingForReceipt() {
		DealShippingView view = adapter.findForParticipant(
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			DealTestFixture.SELLER_ID
		).orElseThrow();

		assertThat(view.shippedAt()).isEqualTo(Instant.parse("2026-07-20T01:00:00Z"));
		assertThat(view.autoConfirmAt()).isEqualTo(Instant.parse("2026-07-27T01:00:00Z"));
	}

	/** 완료된 거래도 당사자면 열린다 — 발송 뒤 분쟁·반품에서 다시 봐야 한다. */
	@Test
	void staysOpenAfterCompletion() {
		assertThat(adapter.findForParticipant(
			DealTestFixture.COMPLETED_DEAL_ID,
			DealTestFixture.SELLER_ID
		)).isPresent();
	}
}
