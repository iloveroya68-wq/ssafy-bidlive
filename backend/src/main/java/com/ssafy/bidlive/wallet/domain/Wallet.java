package com.ssafy.bidlive.wallet.domain;

import java.time.Instant;

import com.ssafy.bidlive.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/** 회원이 보유한 거래 또는 베팅 포인트 잔액을 관리한다. */
@Entity
@Table(
	name = "wallet",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_wallet_member_type",
		columnNames = {"member_id", "wallet_type"}
	)
)
@Check(constraints = "available_balance >= 0 AND held_balance >= 0")
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "wallet_id")
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "member_id",
		insertable = false,
		updatable = false,
		foreignKey = @ForeignKey(name = "fk_wallet_member")
	)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "wallet_type", nullable = false, length = 20)
	private WalletType walletType;

	@Column(name = "available_balance", nullable = false)
	private long availableBalance;

	@Column(name = "held_balance", nullable = false)
	private long heldBalance;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Wallet() {
	}

	private Wallet(Long memberId, WalletType walletType) {
		this.memberId = memberId;
		this.walletType = walletType;
	}

	public static Wallet empty(Long memberId, WalletType walletType) {
		return new Wallet(memberId, walletType);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getMemberId() {
		return memberId;
	}

	public WalletType getWalletType() {
		return walletType;
	}
}
