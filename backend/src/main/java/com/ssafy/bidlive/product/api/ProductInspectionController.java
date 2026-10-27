package com.ssafy.bidlive.product.api;

import com.ssafy.bidlive.product.api.dto.ProductInspectionRequest;
import com.ssafy.bidlive.product.api.dto.ProductInspectionResponse;
import com.ssafy.bidlive.product.application.ProductInspectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 텍스트·이미지 검열 요청을 받는 진입점입니다.
 *
 * <p>검열 판정은 상품 등록을 직접 막지 않는다. 등록 흐름이 이 결과를 참조하는 구조이므로 이
 * API는 판정과 이력만 남긴다.
 */
@RestController
@RequestMapping("/api/v1/ai/products")
public class ProductInspectionController {

	private final ProductInspectionService productInspectionService;

	public ProductInspectionController(ProductInspectionService productInspectionService) {
		this.productInspectionService = productInspectionService;
	}

	@PostMapping("/inspect")
	public ResponseEntity<ProductInspectionResponse> inspect(
		@Valid @RequestBody ProductInspectionRequest request,
		Authentication authentication
	) {
		Long requesterId = authenticatedMemberId(authentication);
		return ResponseEntity.ok(ProductInspectionResponse.from(
			productInspectionService.inspect(requesterId, request.toContent())
		));
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ProductInspectionUnauthenticatedException();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new ProductInspectionUnauthenticatedException(exception);
		}
	}
}
