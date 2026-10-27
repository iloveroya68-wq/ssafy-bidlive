package com.ssafy.bidlive.product.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import com.ssafy.bidlive.product.application.port.out.ContentModerationException;
import com.ssafy.bidlive.product.application.port.out.ContentModerationGateway;
import com.ssafy.bidlive.product.application.port.out.ModerationFailureKind;
import com.ssafy.bidlive.product.application.port.out.ModerationJudgement;
import com.ssafy.bidlive.product.domain.InspectionContent;
import com.ssafy.bidlive.product.domain.InspectionPolicy;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;
import com.ssafy.bidlive.product.domain.ProductInspectionFailureCode;
import com.ssafy.bidlive.product.domain.ProductInspectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 텍스트와 이미지를 AI로 검열하고 판정을 집계해 이력까지 남기는 유스케이스입니다.
 *
 * <p>핵심 전제는 "검열 실패는 통과가 아니다"이다. AI 호출이 실패하거나 응답이 스키마를 어기면
 * 해당 단위를 NEEDS_REVIEW로 보류하고, 집계에서도 보류가 남아 있으면 최종 판정이 PASSED가 되지
 * 않는다. Credit 소진만은 콘텐츠 판단의 모호함이 아니라 운영 개입이 필요한 상태이므로 보류로
 * 감추지 않고 예외로 올린다.
 *
 * <p>이미지 검열은 주입된 executor의 병렬도만큼만 동시에 실행된다. 개별 호출의 허용 시간은
 * 게이트웨이 어댑터가 강제한다.
 */
public class ProductInspectionService {

	private static final Logger log = LoggerFactory.getLogger(ProductInspectionService.class);

	private final ContentModerationGateway contentModerationGateway;
	private final ProductInspectionRepository productInspectionRepository;
	private final InspectionPolicy inspectionPolicy;
	private final Executor imageInspectionExecutor;
	private final int maxImageCount;
	private final Clock clock;

	public ProductInspectionService(
		ContentModerationGateway contentModerationGateway,
		ProductInspectionRepository productInspectionRepository,
		InspectionPolicy inspectionPolicy,
		Executor imageInspectionExecutor,
		int maxImageCount,
		Clock clock
	) {
		this.contentModerationGateway = contentModerationGateway;
		this.productInspectionRepository = productInspectionRepository;
		this.inspectionPolicy = inspectionPolicy;
		this.imageInspectionExecutor = imageInspectionExecutor;
		this.maxImageCount = maxImageCount;
		this.clock = clock;
	}

	@Transactional
	public ProductInspection inspect(Long requesterId, InspectionContent content) {
		if (requesterId == null) {
			throw new IllegalArgumentException("requesterId must not be null");
		}
		if (content.imageUrls().size() > maxImageCount) {
			throw new ProductInspectionRejectedException(
				ProductInspectionFailureCode.INVALID_INSPECTION_REQUEST);
		}

		String contentHash = content.contentHash();
		Optional<ProductInspection> cached =
			productInspectionRepository.findReusableByContentHash(contentHash);
		if (cached.isPresent()) {
			return cached.get();
		}

		InspectionUnitResult textResult = content.hasText() ? inspectText(content.text()) : null;
		List<InspectionUnitResult> imageResults = inspectImages(content.imageUrls());

		return productInspectionRepository.save(new ProductInspection(
			requesterId,
			contentHash,
			aggregate(textResult, imageResults),
			textResult,
			imageResults,
			OffsetDateTime.now(clock)
		));
	}

	private InspectionVerdict aggregate(
		InspectionUnitResult textResult,
		List<InspectionUnitResult> imageResults
	) {
		List<InspectionVerdict> verdicts = new ArrayList<>();
		if (textResult != null) {
			verdicts.add(textResult.verdict());
		}
		imageResults.forEach(result -> verdicts.add(result.verdict()));
		return InspectionVerdict.aggregate(verdicts);
	}

	private InspectionUnitResult inspectText(String text) {
		return judge(null, () -> contentModerationGateway.inspectText(text));
	}

	/**
	 * 이미지가 없으면 호출을 만들지 않고, 있으면 executor 병렬도 안에서 동시에 검열한다.
	 * 한 장이 실패해도 나머지 판정을 버리지 않기 위해 실패는 단위 보류로 흡수한다.
	 */
	private List<InspectionUnitResult> inspectImages(List<String> imageUrls) {
		if (imageUrls.isEmpty()) {
			return List.of();
		}

		List<CompletableFuture<InspectionUnitResult>> futures = imageUrls.stream()
			.map(imageUrl -> CompletableFuture.supplyAsync(
				() -> judge(imageUrl, () -> contentModerationGateway.inspectImage(imageUrl)),
				imageInspectionExecutor
			))
			.toList();

		List<InspectionUnitResult> results = new ArrayList<>(futures.size());
		for (CompletableFuture<InspectionUnitResult> future : futures) {
			results.add(join(future));
		}
		return results;
	}

	/** Credit 소진은 보류로 감추지 않고 그대로 올려 오류 응답이 되게 한다. */
	private InspectionUnitResult join(CompletableFuture<InspectionUnitResult> future) {
		try {
			return future.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof ProductInspectionRejectedException rejected) {
				throw rejected;
			}
			throw exception;
		}
	}

	private InspectionUnitResult judge(String target, ModerationCall call) {
		ModerationJudgement judgement;
		try {
			judgement = call.execute();
		} catch (ContentModerationException exception) {
			if (exception.getKind() == ModerationFailureKind.CREDIT_EXHAUSTED) {
				throw new ProductInspectionRejectedException(
					ProductInspectionFailureCode.AI_CREDIT_EXHAUSTED, exception);
			}

			log.warn("Content moderation held for review: target={}, kind={}",
				target == null ? "TEXT" : target, exception.getKind());
			return InspectionUnitResult.heldForReview(target, exception.getKind().name());
		}

		return InspectionUnitResult.judged(
			inspectionPolicy.decide(judgement.scores()),
			target,
			inspectionPolicy.flaggedCategories(judgement.scores()),
			inspectionPolicy.maxScore(judgement.scores()),
			judgement.reason()
		);
	}

	/** 텍스트와 이미지 호출을 같은 실패 처리 흐름에 태우기 위한 내부 표현이다. */
	private interface ModerationCall {

		ModerationJudgement execute();
	}
}
