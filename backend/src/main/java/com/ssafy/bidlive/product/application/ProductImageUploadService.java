package com.ssafy.bidlive.product.application;

import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_REQUIRED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_SIZE_EXCEEDED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_STORAGE_UNAVAILABLE;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.UNSUPPORTED_IMAGE_TYPE;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.product.application.port.out.ProductImageMetadataPersistence;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageException;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageGateway;
import com.ssafy.bidlive.product.domain.ProductImageFile;
import com.ssafy.bidlive.product.domain.ProductImageMetadata;
import com.ssafy.bidlive.product.domain.ProductImageUploadPolicy;
import org.springframework.transaction.annotation.Transactional;

/** 상품 이미지를 서버 관리 경로에 저장하고 소유자 메타데이터를 기록합니다. */
public class ProductImageUploadService {

	private final MemberPersistence memberPersistence;
	private final ProductImageStorageGateway storageGateway;
	private final ProductImageMetadataPersistence metadataPersistence;
	private final ProductImageUploadPolicy policy;
	private final Clock clock;

	public ProductImageUploadService(
		MemberPersistence memberPersistence,
		ProductImageStorageGateway storageGateway,
		ProductImageMetadataPersistence metadataPersistence,
		ProductImageUploadPolicy policy,
		Clock clock
	) {
		this.memberPersistence = Objects.requireNonNull(memberPersistence, "memberPersistence");
		this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
		this.metadataPersistence = Objects.requireNonNull(
			metadataPersistence, "metadataPersistence");
		this.policy = Objects.requireNonNull(policy, "policy");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Transactional
	public ProductImageUploadResult upload(ProductImageUploadCommand command) {
		if (command == null || command.image() == null) {
			throw new ProductImageUploadException(IMAGE_REQUIRED);
		}

		Long memberId = command.ownerMemberId();
		if (memberId == null || memberId <= 0) {
			throw new ProductImageUploadException(UNAUTHORIZED);
		}
		memberPersistence.findById(memberId)
			.filter(member -> member.getStatus() == MemberStatus.ACTIVE)
			.orElseThrow(() -> new ProductImageUploadException(UNAUTHORIZED));

		ProductImageFile image = command.image();
		if (image.size() > policy.maxSizeBytes()) {
			throw new ProductImageUploadException(IMAGE_SIZE_EXCEEDED);
		}
		if (!policy.allows(image.type())) {
			throw new ProductImageUploadException(UNSUPPORTED_IMAGE_TYPE);
		}

		UUID imageId = UUID.randomUUID();
		String storageKey = "%s/%d/%s.%s".formatted(
			policy.keyPrefix(),
			memberId,
			imageId,
			image.type().extension()
		);

		String imageUrl;
		try {
			imageUrl = storageGateway.upload(storageKey, image);
		} catch (ProductImageStorageException exception) {
			throw new ProductImageUploadException(IMAGE_STORAGE_UNAVAILABLE, exception);
		}

		ProductImageMetadata metadata = new ProductImageMetadata(
			imageId,
			memberId,
			storageKey,
			imageUrl,
			image.type().contentType(),
			image.size(),
			OffsetDateTime.now(clock)
		);

		try {
			return ProductImageUploadResult.from(metadataPersistence.save(metadata));
		} catch (RuntimeException exception) {
			deleteQuietly(storageKey);
			throw new ProductImageUploadException(IMAGE_STORAGE_UNAVAILABLE, exception);
		}
	}

	private void deleteQuietly(String storageKey) {
		try {
			storageGateway.delete(storageKey);
		} catch (RuntimeException ignored) {
			// DB 저장 실패가 원인이므로 보상 삭제 실패가 원래 예외를 가리지 않게 합니다.
		}
	}
}
