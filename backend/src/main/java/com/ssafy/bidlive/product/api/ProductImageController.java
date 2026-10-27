package com.ssafy.bidlive.product.api;

import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_REQUIRED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_SIZE_EXCEEDED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.UNSUPPORTED_IMAGE_TYPE;

import java.io.IOException;

import com.ssafy.bidlive.product.api.dto.ProductImageUploadResponse;
import com.ssafy.bidlive.product.application.ProductImageUploadCommand;
import com.ssafy.bidlive.product.application.ProductImageUploadException;
import com.ssafy.bidlive.product.application.ProductImageUploadService;
import com.ssafy.bidlive.product.domain.ProductImageFile;
import com.ssafy.bidlive.product.domain.ProductImageType;
import com.ssafy.bidlive.product.domain.ProductImageUploadPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 경매방 생성 전에 사용할 상품 대표 이미지를 업로드합니다. */
@RestController
@RequestMapping("/api/v1/product-images")
public class ProductImageController {

	private final ProductImageUploadService uploadService;
	private final ProductImageUploadPolicy uploadPolicy;

	public ProductImageController(
		ProductImageUploadService uploadService,
		ProductImageUploadPolicy uploadPolicy
	) {
		this.uploadService = uploadService;
		this.uploadPolicy = uploadPolicy;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ProductImageUploadResponse> upload(
		@RequestParam("image") MultipartFile image,
		Authentication authentication
	) {
		Long memberId = authenticatedMemberId(authentication);
		ProductImageUploadResponse response = ProductImageUploadResponse.from(
			uploadService.upload(new ProductImageUploadCommand(
				memberId,
				validatedImage(image)
			))
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	private ProductImageFile validatedImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new ProductImageUploadException(IMAGE_REQUIRED);
		}
		if (image.getSize() > uploadPolicy.maxSizeBytes()) {
			throw new ProductImageUploadException(IMAGE_SIZE_EXCEEDED);
		}

		byte[] content;
		try {
			content = image.getBytes();
		} catch (IOException exception) {
			throw new ProductImageUploadException(IMAGE_REQUIRED, exception);
		}

		ProductImageType actualType = ProductImageType.detect(content)
			.filter(type -> type.matchesContentType(image.getContentType()))
			.filter(uploadPolicy::allows)
			.orElseThrow(() -> new ProductImageUploadException(
				UNSUPPORTED_IMAGE_TYPE));
		return new ProductImageFile(content, actualType);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ProductImageUploadException(UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new ProductImageUploadException(UNAUTHORIZED, exception);
		}
	}
}
