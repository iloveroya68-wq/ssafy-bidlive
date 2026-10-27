package com.ssafy.bidlive.product.application;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경매방과 함께 생성할 상품을 검증하고 업로드 이미지를 상품에 연결합니다. */
@Service
public class ProductRegistrationService {

	private static final int MAX_TITLE_LENGTH = 100;
	private static final int MAX_IMAGE_URL_LENGTH = 500;

	private final ProductRegistrationPersistence persistence;

	public ProductRegistrationService(ProductRegistrationPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional
	public ProductRegistrationResult register(ProductRegistrationCommand command) {
		ProductRegistrationCommand normalizedCommand = validateAndNormalize(command);
		if (!persistence.existsActiveCategory(normalizedCommand.categoryCode())) {
			throw new ProductRegistrationException();
		}

		return new ProductRegistrationResult(
			persistence.saveProductWithOwnedImage(normalizedCommand)
		);
	}

	private ProductRegistrationCommand validateAndNormalize(
		ProductRegistrationCommand command
	) {
		if (command == null
			|| command.sellerMemberId() == null
			|| command.sellerMemberId() <= 0) {
			throw new ProductRegistrationException();
		}

		String title = normalize(command.title());
		String description = normalize(command.description());
		String categoryCode = normalize(command.categoryCode());
		String imageUrl = normalize(command.imageUrl());
		if (title == null
			|| title.length() > MAX_TITLE_LENGTH
			|| description == null
			|| categoryCode == null
			|| imageUrl == null
			|| imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
			throw new ProductRegistrationException();
		}

		return new ProductRegistrationCommand(
			command.sellerMemberId(),
			title,
			description,
			categoryCode.toUpperCase(Locale.ROOT),
			imageUrl
		);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
