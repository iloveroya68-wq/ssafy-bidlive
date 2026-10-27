package com.ssafy.bidlive.product.application;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 등록된 상품의 부분 수정과 대표 이미지 교체를 검증하고 반영합니다. */
@Service
public class ProductUpdateService {

	private static final int MAX_TITLE_LENGTH = 100;
	private static final int MAX_IMAGE_URL_LENGTH = 500;

	private final ProductUpdatePersistence persistence;

	public ProductUpdateService(ProductUpdatePersistence persistence) {
		this.persistence = persistence;
	}

	/**
	 * @return 수정 후의 상품 값. 바꿀 값이 없으면 현재 값을 그대로 돌려줍니다
	 *     (경매방 쪽만 수정하는 요청도 있으므로 "변경 없음"은 오류가 아닙니다)
	 */
	@Transactional
	public ProductSnapshot update(ProductUpdateCommand command) {
		ProductUpdateCommand normalized = normalize(command);
		ProductSnapshot current = persistence.findSnapshot(normalized.productId())
			.orElseThrow(() -> new ProductUpdateException(
				ProductUpdateException.Reason.PRODUCT_NOT_FOUND
			));
		if (!current.sellerMemberId().equals(normalized.sellerMemberId())) {
			throw new ProductUpdateException(ProductUpdateException.Reason.NOT_PRODUCT_OWNER);
		}
		if (normalized.hasNoChange()) {
			return current;
		}

		String categoryCode = normalized.categoryCode() == null
			? current.categoryCode()
			: normalized.categoryCode();
		if (normalized.categoryCode() != null
			&& !persistence.existsActiveCategory(categoryCode)) {
			throw new ProductUpdateException(ProductUpdateException.Reason.INVALID_REQUEST);
		}

		String title = normalized.title() == null ? current.title() : normalized.title();
		String description = normalized.description() == null
			? current.description()
			: normalized.description();
		persistence.updateFields(normalized.productId(), title, description, categoryCode);

		String imageUrl = current.imageUrl();
		if (normalized.imageUrl() != null && !normalized.imageUrl().equals(imageUrl)) {
			boolean replaced = persistence.replaceRepresentativeImage(
				normalized.productId(),
				normalized.sellerMemberId(),
				normalized.imageUrl()
			);
			if (!replaced) {
				throw new ProductUpdateException(
					ProductUpdateException.Reason.IMAGE_NOT_CLAIMABLE
				);
			}
			imageUrl = normalized.imageUrl();
		}

		return new ProductSnapshot(
			normalized.productId(),
			current.sellerMemberId(),
			title,
			description,
			categoryCode,
			imageUrl
		);
	}

	private ProductUpdateCommand normalize(ProductUpdateCommand command) {
		if (command == null
			|| command.productId() == null
			|| command.productId() <= 0
			|| command.sellerMemberId() == null
			|| command.sellerMemberId() <= 0) {
			throw new ProductUpdateException(ProductUpdateException.Reason.INVALID_REQUEST);
		}

		String title = blankToNull(command.title());
		String description = blankToNull(command.description());
		String categoryCode = blankToNull(command.categoryCode());
		String imageUrl = blankToNull(command.imageUrl());
		// 빈 문자열로 지우려는 시도는 유지와 구분되지 않으므로 형식 오류로 본다.
		// `title`·`description`은 DB가 NOT NULL이고, 카테고리·이미지도 없는 상태가 유효하지 않다.
		if (hasBlankAttempt(command.title(), title)
			|| hasBlankAttempt(command.description(), description)
			|| hasBlankAttempt(command.categoryCode(), categoryCode)
			|| hasBlankAttempt(command.imageUrl(), imageUrl)
			|| (title != null && title.length() > MAX_TITLE_LENGTH)
			|| (imageUrl != null && imageUrl.length() > MAX_IMAGE_URL_LENGTH)) {
			throw new ProductUpdateException(ProductUpdateException.Reason.INVALID_REQUEST);
		}

		return new ProductUpdateCommand(
			command.productId(),
			command.sellerMemberId(),
			title,
			description,
			categoryCode == null ? null : categoryCode.toUpperCase(Locale.ROOT),
			imageUrl
		);
	}

	/** 값을 보냈는데 공백뿐이었다 — "안 보냄"(유지)과 다르게 취급한다 */
	private static boolean hasBlankAttempt(String raw, String normalized) {
		return raw != null && normalized == null;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
