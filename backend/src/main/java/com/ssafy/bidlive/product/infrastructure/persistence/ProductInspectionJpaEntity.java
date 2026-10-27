package com.ssafy.bidlive.product.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.product.domain.InspectionVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_inspection")
public class ProductInspectionJpaEntity {

	private static final int HASH_LENGTH = 64;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "product_inspection_id")
	private Long id;

	@Column(name = "requester_member_id", nullable = false, updatable = false)
	private Long requesterMemberId;

	@Column(name = "content_hash", nullable = false, updatable = false, length = HASH_LENGTH)
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "verdict", nullable = false, updatable = false, length = 20)
	private InspectionVerdict verdict;

	/** 단위 판정 상세는 조회 조건이 아니라 감사 근거이므로 JSONB 한 컬럼에 보관한다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "detail", nullable = false, updatable = false)
	private String detail;

	@Column(name = "inspected_at", nullable = false, updatable = false)
	private OffsetDateTime inspectedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ProductInspectionJpaEntity() {
	}

	private ProductInspectionJpaEntity(
		Long requesterMemberId,
		String contentHash,
		InspectionVerdict verdict,
		String detail,
		OffsetDateTime inspectedAt
	) {
		this.requesterMemberId = Objects.requireNonNull(requesterMemberId, "requesterMemberId");
		this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
		this.verdict = Objects.requireNonNull(verdict, "verdict");
		this.detail = Objects.requireNonNull(detail, "detail");
		this.inspectedAt = Objects.requireNonNull(inspectedAt, "inspectedAt");
	}

	static ProductInspectionJpaEntity of(
		Long requesterMemberId,
		String contentHash,
		InspectionVerdict verdict,
		String detail,
		OffsetDateTime inspectedAt
	) {
		return new ProductInspectionJpaEntity(
			requesterMemberId, contentHash, verdict, detail, inspectedAt);
	}

	Long requesterMemberId() {
		return requesterMemberId;
	}

	String contentHash() {
		return contentHash;
	}

	InspectionVerdict verdict() {
		return verdict;
	}

	String detail() {
		return detail;
	}

	OffsetDateTime inspectedAt() {
		return inspectedAt;
	}

	@PrePersist
	void onCreate() {
		createdAt = OffsetDateTime.now();
	}
}
