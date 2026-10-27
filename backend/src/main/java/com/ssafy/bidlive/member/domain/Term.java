package com.ssafy.bidlive.member.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 개정 이력을 보존하는 버전 단위 약관이다. */
@Entity
@Table(
	name = "terms",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_terms_purpose_code_version",
		columnNames = {"purpose", "terms_type", "version"}
	)
)
public class Term {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "terms_id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 30)
	private TermsPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "terms_type", nullable = false, length = 30)
	private TermCode code;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "version", nullable = false, length = 30)
	private String version;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "effective_at", nullable = false)
	private Instant effectiveAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Term() {
	}

	private Term(
		TermsPurpose purpose,
		TermCode code,
		String title,
		String content,
		String version,
		boolean required,
		int displayOrder,
		Instant effectiveAt,
		Instant expiresAt
	) {
		this.purpose = purpose;
		this.code = code;
		this.title = title;
		this.content = content;
		this.version = version;
		this.required = required;
		this.active = true;
		this.displayOrder = displayOrder;
		this.effectiveAt = effectiveAt;
		this.expiresAt = expiresAt;
	}

	public static Term create(
		TermsPurpose purpose,
		TermCode code,
		String title,
		String content,
		String version,
		boolean required,
		int displayOrder,
		Instant effectiveAt,
		Instant expiresAt
	) {
		return new Term(
			purpose,
			code,
			title,
			content,
			version,
			required,
			displayOrder,
			effectiveAt,
			expiresAt
		);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public TermsPurpose getPurpose() {
		return purpose;
	}

	public TermCode getCode() {
		return code;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getVersion() {
		return version;
	}

	public boolean isRequired() {
		return required;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public Instant getEffectiveAt() {
		return effectiveAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isActiveAt(Instant instant) {
		return !effectiveAt.isAfter(instant)
			&& (expiresAt == null || expiresAt.isAfter(instant));
	}
}
