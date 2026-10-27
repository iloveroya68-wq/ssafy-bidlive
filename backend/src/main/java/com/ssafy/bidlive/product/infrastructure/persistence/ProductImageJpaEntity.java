package com.ssafy.bidlive.product.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ssafy.bidlive.product.domain.ProductImageMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image")
public class ProductImageJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "product_image_id")
	private Long id;

	@Column(name = "product_id")
	private Long productId;

	@Column(name = "image_id", nullable = false, updatable = false, unique = true)
	private UUID imageId;

	@Column(name = "owner_member_id", nullable = false, updatable = false)
	private Long ownerMemberId;

	@Column(name = "image_url", nullable = false, updatable = false, length = 500)
	private String imageUrl;

	@Column(name = "storage_key", nullable = false, updatable = false, length = 500)
	private String storageKey;

	@Column(name = "content_type", nullable = false, updatable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "is_thumbnail", nullable = false)
	private boolean thumbnail;

	@Column(name = "moderation_status", nullable = false, length = 20)
	private String moderationStatus;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ProductImageJpaEntity() {
	}

	private ProductImageJpaEntity(ProductImageMetadata metadata) {
		this.imageId = metadata.imageId();
		this.ownerMemberId = metadata.ownerMemberId();
		this.imageUrl = metadata.imageUrl();
		this.storageKey = metadata.storageKey();
		this.contentType = metadata.contentType();
		this.sizeBytes = metadata.sizeBytes();
		this.sortOrder = 1;
		this.thumbnail = true;
		this.moderationStatus = "PENDING";
		this.createdAt = metadata.createdAt();
	}

	static ProductImageJpaEntity from(ProductImageMetadata metadata) {
		return new ProductImageJpaEntity(metadata);
	}

	ProductImageMetadata toDomain() {
		return new ProductImageMetadata(
			imageId,
			ownerMemberId,
			storageKey,
			imageUrl,
			contentType,
			sizeBytes,
			createdAt
		);
	}
}
