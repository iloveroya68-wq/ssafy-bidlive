package com.ssafy.bidlive.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.product.domain.InspectionCategory;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;
import com.ssafy.bidlive.product.domain.ProductInspectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * JSONB 상세가 왕복 직렬화되고, 캐시 조회가 보류 기록을 제외한 최신 확정 판정만 돌려주는지
 * 검증합니다.
 */
@DataJpaTest
@Import({JpaProductInspectionRepositoryAdapter.class, ObjectMapper.class})
class JpaProductInspectionRepositoryAdapterTest {

	private static final String CONTENT_HASH = "a".repeat(64);
	private static final OffsetDateTime INSPECTED_AT =
		OffsetDateTime.parse("2026-07-20T21:00:00+09:00");

	@Autowired
	private ProductInspectionRepository productInspectionRepository;

	@Autowired
	private EntityManager entityManager;

	private Long requesterId;

	@BeforeEach
	void insertMember() {
		requesterId = (Long) entityManager
			.createNativeQuery("""
				insert into member (nickname, role, status)
				values ('판매자', 'USER', 'ACTIVE')
				returning member_id
				""")
			.getSingleResult();
	}

	@Test
	void storesAndReadsBackUnitDetailsFromJsonb() {
		productInspectionRepository.save(inspection(
			InspectionVerdict.REJECTED,
			InspectionUnitResult.judged(InspectionVerdict.PASSED, null, List.of(), 0.1, "정상"),
			List.of(InspectionUnitResult.judged(
				InspectionVerdict.REJECTED,
				"https://img/a.jpg",
				List.of(InspectionCategory.COUNTERFEIT),
				0.93,
				"위조품"
			)),
			INSPECTED_AT
		));
		flushAndClear();

		ProductInspection stored =
			productInspectionRepository.findReusableByContentHash(CONTENT_HASH).orElseThrow();

		assertThat(stored.verdict()).isEqualTo(InspectionVerdict.REJECTED);
		assertThat(stored.textResult().verdict()).isEqualTo(InspectionVerdict.PASSED);
		assertThat(stored.imageResults()).singleElement().satisfies(result -> {
			assertThat(result.target()).isEqualTo("https://img/a.jpg");
			assertThat(result.flaggedCategories()).containsExactly(InspectionCategory.COUNTERFEIT);
			assertThat(result.maxScore()).isEqualTo(0.93);
		});
	}

	/** 보류는 판정을 얻지 못한 상태일 수 있어 캐시 재사용 대상이 아니다. */
	@Test
	void excludesHeldInspectionFromCacheLookup() {
		productInspectionRepository.save(inspection(
			InspectionVerdict.NEEDS_REVIEW,
			InspectionUnitResult.heldForReview(null, "TIMEOUT"),
			List.of(),
			INSPECTED_AT
		));
		flushAndClear();

		assertThat(productInspectionRepository.findReusableByContentHash(CONTENT_HASH)).isEmpty();
	}

	/** 이력은 덮어쓰지 않고 쌓이며, 캐시는 가장 최근 확정 판정을 쓴다. */
	@Test
	void returnsLatestConfirmedInspectionWhenHistoryAccumulates() {
		productInspectionRepository.save(inspection(
			InspectionVerdict.PASSED,
			InspectionUnitResult.judged(InspectionVerdict.PASSED, null, List.of(), 0.1, "이전"),
			List.of(),
			INSPECTED_AT
		));
		productInspectionRepository.save(inspection(
			InspectionVerdict.REJECTED,
			InspectionUnitResult.judged(InspectionVerdict.REJECTED, null, List.of(), 0.9, "최신"),
			List.of(),
			INSPECTED_AT.plusMinutes(10)
		));
		flushAndClear();

		ProductInspection stored =
			productInspectionRepository.findReusableByContentHash(CONTENT_HASH).orElseThrow();

		assertThat(stored.verdict()).isEqualTo(InspectionVerdict.REJECTED);
		assertThat(stored.textResult().reason()).isEqualTo("최신");
	}

	@Test
	void returnsEmptyForUnknownContentHash() {
		assertThat(productInspectionRepository.findReusableByContentHash("b".repeat(64))).isEmpty();
	}

	private ProductInspection inspection(
		InspectionVerdict verdict,
		InspectionUnitResult textResult,
		List<InspectionUnitResult> imageResults,
		OffsetDateTime inspectedAt
	) {
		return new ProductInspection(
			requesterId, CONTENT_HASH, verdict, textResult, imageResults, inspectedAt);
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
