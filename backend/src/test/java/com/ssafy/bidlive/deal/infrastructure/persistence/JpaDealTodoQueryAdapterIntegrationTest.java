package com.ssafy.bidlive.deal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.deal.application.DealTodoItem;
import com.ssafy.bidlive.deal.domain.DealRole;
import com.ssafy.bidlive.deal.domain.DealStatus;
import com.ssafy.bidlive.deal.domain.DealTodoAction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 액션 주체 판정·정렬·자동 확정 시각 노출을 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JpaDealTodoQueryAdapterIntegrationTest {

	@Autowired
	private JpaDealTodoQueryAdapter adapter;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		DealTestFixture.insert(entityManager);
	}

	/**
	 * 구매자에게는 배송지 입력·수령 확인만 보인다. 발송 대기는 판매자 차례이고 완료된 거래는 제외다.
	 *
	 * <p>정렬은 오래된 순이다 — 수령 대기(배송 정보 갱신 07-20)가 배송지 대기(거래 생성 07-21)보다
	 * 앞선다. 두 행의 정렬 키가 서로 다른 컬럼(`deal_shipping.updated_at` / `deal.created_at`)에서
	 * 오는데도 한 순서로 섞이는지가 이 단정의 요점이다.
	 */
	@Test
	void returnsOnlyDealsTheBuyerMustActOnOldestFirst() {
		List<DealTodoItem> todos = adapter.findActionableDeals(DealTestFixture.BUYER_ID);

		assertThat(todos).extracting(DealTodoItem::dealId).containsExactly(
			DealTestFixture.WAITING_RECEIPT_DEAL_ID,
			DealTestFixture.WAITING_ADDRESS_DEAL_ID
		);
		assertThat(todos).extracting(DealTodoItem::role)
			.containsOnly(DealRole.BUYER);
		assertThat(todos).extracting(DealTodoItem::action).containsExactly(
			DealTodoAction.CONFIRM_RECEIPT,
			DealTodoAction.SUBMIT_ADDRESS
		);
	}

	/**
	 * 판매자는 <b>발송 확인과 수령 대기 둘 다</b> 본다 (S15P11A704-694).
	 *
	 * <p>예전에는 발송 확인 하나뿐이었다 — 발송 확인을 누르는 순간 목록에서 사라져서 자기가 무엇을
	 * 어디로 보냈는지 다시 볼 데가 없었다. 수령 대기 행은 <b>액션이 없는</b> 표시 전용이다.
	 */
	@Test
	void returnsShipmentAndAwaitingReceiptForTheSeller() {
		List<DealTodoItem> todos = adapter.findActionableDeals(DealTestFixture.SELLER_ID);

		assertThat(todos).extracting(DealTodoItem::dealId).containsExactlyInAnyOrder(
			DealTestFixture.WAITING_SHIPMENT_DEAL_ID,
			DealTestFixture.WAITING_RECEIPT_DEAL_ID
		);
		assertThat(todos).extracting(DealTodoItem::role).containsOnly(DealRole.SELLER);
		assertThat(todos)
			.filteredOn(todo -> todo.dealId() == DealTestFixture.WAITING_SHIPMENT_DEAL_ID)
			.singleElement()
			.satisfies(todo -> {
				assertThat(todo.status()).isEqualTo(DealStatus.WAITING_SHIPMENT);
				assertThat(todo.action()).isEqualTo(DealTodoAction.CONFIRM_SHIPMENT);
				assertThat(todo.action().actionable()).isTrue();
			});
		assertThat(todos)
			.filteredOn(todo -> todo.dealId() == DealTestFixture.WAITING_RECEIPT_DEAL_ID)
			.singleElement()
			.satisfies(todo -> {
				assertThat(todo.status()).isEqualTo(DealStatus.WAITING_RECEIPT);
				// 같은 거래·같은 상태인데 구매자는 CONFIRM_RECEIPT다 — 역할이 액션을 가른다
				assertThat(todo.action()).isEqualTo(DealTodoAction.AWAIT_RECEIPT);
				assertThat(todo.action().actionable()).isFalse();
			});
	}

	@Test
	void returnsNothingForMembersOutsideTheDeal() {
		assertThat(adapter.findActionableDeals(DealTestFixture.STRANGER_ID)).isEmpty();
	}

	/** 카드에 필요한 값은 상품·경매방까지 조인해서 한 번에 가져온다. 대표 이미지도 여기서 고른다. */
	@Test
	void joinsProductTitleThumbnailAndAuctionEndTime() {
		DealTodoItem todo = adapter.findActionableDeals(DealTestFixture.BUYER_ID).stream()
			.filter(item -> item.dealId() == DealTestFixture.WAITING_ADDRESS_DEAL_ID)
			.findFirst()
			.orElseThrow();

		assertThat(todo.auctionId()).isEqualTo(9401L);
		assertThat(todo.productTitle()).isEqualTo("키크론 Q1 Pro");
		assertThat(todo.imageUrl()).isEqualTo("https://cdn.example.com/9401-thumbnail.png");
		assertThat(todo.finalPrice()).isEqualTo(DealTestFixture.FINAL_PRICE);
		assertThat(todo.endedAt()).isEqualTo(Instant.parse("2026-07-21T11:17:13Z"));
		assertThat(todo.updatedAt()).isEqualTo(Instant.parse("2026-07-21T11:18:00Z"));
	}

	/** 자동 확정 예정 시각은 수령 대기에서만 값이 있다 — 그 전 단계에는 아직 정해지지 않았다. */
	@Test
	void exposesAutoConfirmTimeOnlyWhileWaitingForReceipt() {
		List<DealTodoItem> todos = adapter.findActionableDeals(DealTestFixture.BUYER_ID);

		assertThat(todos).filteredOn(todo -> todo.status() == DealStatus.WAITING_RECEIPT)
			.singleElement()
			.satisfies(todo -> assertThat(todo.autoConfirmAt())
				.isEqualTo(Instant.parse("2026-07-27T01:00:00Z")));
		assertThat(todos).filteredOn(todo -> todo.status() == DealStatus.WAITING_ADDRESS)
			.singleElement()
			.satisfies(todo -> assertThat(todo.autoConfirmAt()).isNull());
	}

	/** 대표 이미지가 차단되면 남은 이미지로 떨어진다 — 목록 API와 같은 규칙이다. */
	@Test
	void fallsBackToNextImageWhenThumbnailIsBlocked() {
		DealTestFixture.execute(entityManager, """
			UPDATE product_image SET moderation_status = 'BLOCKED' WHERE product_image_id = 9402
			""");
		entityManager.flush();

		assertThat(adapter.findActionableDeals(DealTestFixture.BUYER_ID))
			.filteredOn(todo -> todo.dealId() == DealTestFixture.WAITING_ADDRESS_DEAL_ID)
			.singleElement()
			.satisfies(todo -> assertThat(todo.imageUrl())
				.isEqualTo("https://cdn.example.com/9401-first.png"));
	}
}
