package com.ssafy.bidlive.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.ssafy.bidlive.product.application.port.out.ContentModerationException;
import com.ssafy.bidlive.product.application.port.out.ContentModerationGateway;
import com.ssafy.bidlive.product.application.port.out.ModerationFailureKind;
import com.ssafy.bidlive.product.application.port.out.ModerationJudgement;
import com.ssafy.bidlive.product.domain.CategoryScore;
import com.ssafy.bidlive.product.domain.InspectionCategory;
import com.ssafy.bidlive.product.domain.InspectionContent;
import com.ssafy.bidlive.product.domain.InspectionPolicy;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;
import com.ssafy.bidlive.product.domain.ProductInspectionFailureCode;
import com.ssafy.bidlive.product.domain.ProductInspectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * AI 실패가 절대 통과로 바뀌지 않는지, 같은 콘텐츠가 AI 호출 없이 재사용되는지,
 * Credit 소진만 보류가 아닌 오류로 올라가는지 검증합니다.
 */
class ProductInspectionServiceTest {

	private static final Long REQUESTER_ID = 25L;
	private static final Clock FIXED_CLOCK =
		Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
	private static final OffsetDateTime EXPECTED_INSPECTED_AT =
		OffsetDateTime.parse("2026-07-20T21:00:00+09:00");

	private final StubContentModerationGateway gateway = new StubContentModerationGateway();
	private final RecordingProductInspectionRepository repository =
		new RecordingProductInspectionRepository();
	private final Executor directExecutor = Runnable::run;
	private final ProductInspectionService service = new ProductInspectionService(
		gateway,
		repository,
		new InspectionPolicy(0.8, 0.4),
		directExecutor,
		10,
		FIXED_CLOCK
	);

	@Test
	void passesWhenTextAndEveryImageAreClean() {
		gateway.score = 0.0;

		ProductInspection inspection = service.inspect(REQUESTER_ID, content(
			List.of("https://img/a.jpg", "https://img/b.jpg")));

		assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.PASSED);
		assertThat(inspection.textResult().verdict()).isEqualTo(InspectionVerdict.PASSED);
		assertThat(inspection.imageResults()).hasSize(2);
		assertThat(inspection.inspectedAt()).isEqualTo(EXPECTED_INSPECTED_AT);
		assertThat(gateway.calls).containsExactly(
			"text", "image:https://img/a.jpg", "image:https://img/b.jpg");
	}

	@Test
	void rejectsWhenAnyUnitCrossesRejectThreshold() {
		gateway.score = 0.0;
		gateway.imageScoreOverrides.put("https://img/bad.jpg", 0.95);

		ProductInspection inspection = service.inspect(REQUESTER_ID, content(
			List.of("https://img/a.jpg", "https://img/bad.jpg")));

		assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.REJECTED);
		assertThat(inspection.imageResults())
			.anySatisfy(result -> assertThat(result.flaggedCategories())
				.contains(InspectionCategory.ILLEGAL_GOODS));
	}

	/** GMS 장애·타임아웃·스키마 위반 모두 자동 통과가 아니라 보류가 되어야 한다. */
	@ParameterizedTest
	@EnumSource(value = ModerationFailureKind.class,
		names = {"TIMEOUT", "TRANSPORT", "INVALID_RESPONSE"})
	void holdsForReviewWhenModerationFails(ModerationFailureKind kind) {
		gateway.failure = new ContentModerationException(kind, "moderation unavailable");

		ProductInspection inspection = service.inspect(REQUESTER_ID, content(
			List.of("https://img/a.jpg")));

		assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.NEEDS_REVIEW);
		assertThat(inspection.textResult().failureReason()).isEqualTo(kind.name());
		assertThat(inspection.imageResults())
			.allSatisfy(result -> assertThat(result.verdict())
				.isEqualTo(InspectionVerdict.NEEDS_REVIEW));
	}

	/** 한 장이 실패해도 나머지 판정을 버리지 않지만 최종 판정은 통과가 아니다. */
	@Test
	void keepsOtherImageJudgementsWhenOneImageFails() {
		gateway.score = 0.0;
		gateway.imageFailures.put("https://img/broken.jpg",
			new ContentModerationException(ModerationFailureKind.TRANSPORT, "unreachable"));

		ProductInspection inspection = service.inspect(REQUESTER_ID, content(
			List.of("https://img/a.jpg", "https://img/broken.jpg")));

		assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.NEEDS_REVIEW);
		assertThat(inspection.imageResults())
			.filteredOn(result -> "https://img/a.jpg".equals(result.target()))
			.singleElement()
			.satisfies(result -> assertThat(result.verdict()).isEqualTo(InspectionVerdict.PASSED));
	}

	/** Credit 소진은 운영 개입이 필요한 상태라 보류로 감추지 않는다. */
	@Test
	void reportsCreditExhaustionAsFailureInsteadOfReview() {
		gateway.failure = new ContentModerationException(
			ModerationFailureKind.CREDIT_EXHAUSTED, "credit exhausted");

		assertThatThrownBy(() -> service.inspect(REQUESTER_ID, content(List.of())))
			.isInstanceOf(ProductInspectionRejectedException.class)
			.extracting(exception ->
				((ProductInspectionRejectedException) exception).getFailureCode())
			.isEqualTo(ProductInspectionFailureCode.AI_CREDIT_EXHAUSTED);

		assertThat(repository.saved).isEmpty();
	}

	@Test
	void reusesConfirmedVerdictForSameContentWithoutCallingAi() {
		InspectionContent content = content(List.of("https://img/a.jpg"));
		repository.reusable = new ProductInspection(
			REQUESTER_ID,
			content.contentHash(),
			InspectionVerdict.REJECTED,
			InspectionUnitResult.judged(InspectionVerdict.REJECTED, null, List.of(), 0.9, "이전 판정"),
			List.of(),
			EXPECTED_INSPECTED_AT
		);

		ProductInspection inspection = service.inspect(REQUESTER_ID, content);

		assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.REJECTED);
		assertThat(gateway.calls).isEmpty();
		assertThat(repository.saved).isEmpty();
	}

	@Test
	void rejectsRequestWithMoreImagesThanAllowed() {
		ProductInspectionService limited = new ProductInspectionService(
			gateway, repository, new InspectionPolicy(0.8, 0.4), directExecutor, 1, FIXED_CLOCK);

		assertThatThrownBy(() -> limited.inspect(REQUESTER_ID, content(
			List.of("https://img/a.jpg", "https://img/b.jpg"))))
			.isInstanceOf(ProductInspectionRejectedException.class)
			.extracting(exception ->
				((ProductInspectionRejectedException) exception).getFailureCode())
			.isEqualTo(ProductInspectionFailureCode.INVALID_INSPECTION_REQUEST);

		assertThat(gateway.calls).isEmpty();
	}

	/** 보류는 판정을 얻지 못한 상태일 수 있으므로 캐시로 재사용하지 않는다. */
	@Test
	void doesNotMarkHeldInspectionAsReusable() {
		gateway.failure = new ContentModerationException(
			ModerationFailureKind.TIMEOUT, "timed out");

		ProductInspection inspection = service.inspect(REQUESTER_ID, content(List.of()));

		assertThat(inspection.isReusable()).isFalse();
	}

	private InspectionContent content(List<String> imageUrls) {
		return new InspectionContent("정상 상품", "설명입니다", imageUrls);
	}

	private static final class StubContentModerationGateway implements ContentModerationGateway {

		private final List<String> calls = new ArrayList<>();
		private final java.util.Map<String, Double> imageScoreOverrides = new java.util.HashMap<>();
		private final java.util.Map<String, ContentModerationException> imageFailures =
			new java.util.HashMap<>();
		private double score = 0.0;
		private ContentModerationException failure;

		@Override
		public ModerationJudgement inspectText(String text) {
			calls.add("text");
			if (failure != null) {
				throw failure;
			}
			return judgement(score);
		}

		@Override
		public ModerationJudgement inspectImage(String imageUrl) {
			calls.add("image:" + imageUrl);
			if (imageFailures.containsKey(imageUrl)) {
				throw imageFailures.get(imageUrl);
			}
			if (failure != null) {
				throw failure;
			}
			return judgement(imageScoreOverrides.getOrDefault(imageUrl, score));
		}

		private ModerationJudgement judgement(double value) {
			return new ModerationJudgement(
				List.of(new CategoryScore(InspectionCategory.ILLEGAL_GOODS, value)),
				"근거"
			);
		}
	}

	private static final class RecordingProductInspectionRepository
		implements ProductInspectionRepository {

		private final List<ProductInspection> saved = new ArrayList<>();
		private ProductInspection reusable;

		@Override
		public Optional<ProductInspection> findReusableByContentHash(String contentHash) {
			return Optional.ofNullable(reusable);
		}

		@Override
		public ProductInspection save(ProductInspection inspection) {
			saved.add(inspection);
			return inspection;
		}
	}
}
