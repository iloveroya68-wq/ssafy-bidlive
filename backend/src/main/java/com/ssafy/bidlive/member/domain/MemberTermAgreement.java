package com.ssafy.bidlive.member.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 회원이 특정 약관 버전에 표시한 동의 여부를 저장한다. */
@Entity
@Table(
	name = "member_terms_agreement",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_member_terms_agreement",
		columnNames = {"member_id", "terms_id"}
	)
)
public class MemberTermAgreement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_terms_agreement_id")
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "member_id",
		insertable = false,
		updatable = false,
		foreignKey = @ForeignKey(name = "fk_member_terms_agreement_member")
	)
	private Member member;

	@Column(name = "terms_id", nullable = false)
	private Long termId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "terms_id",
		insertable = false,
		updatable = false,
		foreignKey = @ForeignKey(name = "fk_member_terms_agreement_terms")
	)
	private Term term;

	@Column(name = "is_agreed", nullable = false)
	private boolean agreed;

	@Column(name = "agreed_at", nullable = false)
	private Instant agreedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MemberTermAgreement() {
	}

	private MemberTermAgreement(Long memberId, Long termId, boolean agreed, Instant agreedAt) {
		this.memberId = memberId;
		this.termId = termId;
		this.agreed = agreed;
		this.agreedAt = agreedAt;
	}

	public static MemberTermAgreement record(
		Long memberId,
		Long termId,
		boolean agreed,
		Instant agreedAt
	) {
		return new MemberTermAgreement(memberId, termId, agreed, agreedAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getMemberId() {
		return memberId;
	}

	public Long getTermId() {
		return termId;
	}

	public boolean isAgreed() {
		return agreed;
	}

	public Instant getAgreedAt() {
		return agreedAt;
	}

	public void changeDecision(boolean agreed, Instant agreedAt) {
		if (this.agreed == agreed) {
			return;
		}
		this.agreed = agreed;
		this.agreedAt = agreedAt;
	}
}
