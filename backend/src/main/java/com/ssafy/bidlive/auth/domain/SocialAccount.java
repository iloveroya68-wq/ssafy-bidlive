package com.ssafy.bidlive.auth.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** OAuth 제공자 계정과 BidLive 회원의 연결 및 선택 프로필을 저장한다. */
@Entity
@Table(
	name = "social_account",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_social_account_provider_user",
			columnNames = {"provider", "provider_user_id"}
		),
		@UniqueConstraint(
			name = "uq_social_account_member_provider",
			columnNames = {"member_id", "provider"}
		)
	}
)
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "social_account_id")
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "member_id",
		insertable = false,
		updatable = false,
		foreignKey = @ForeignKey(name = "fk_social_account_member")
	)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Column(name = "email", length = 320)
	private String email;

	@Column(name = "provider_nickname", length = 100)
	private String providerNickname;

	@Column(name = "profile_image_url", length = 2048)
	private String profileImageUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SocialAccount() {
	}

	private SocialAccount(
		Long memberId,
		SocialProvider provider,
		String providerUserId,
		String email,
		String providerNickname,
		String profileImageUrl
	) {
		this.memberId = memberId;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
		this.providerNickname = providerNickname;
		this.profileImageUrl = profileImageUrl;
	}

	public static SocialAccount connect(
		Long memberId,
		SocialProvider provider,
		String providerUserId,
		String email,
		String providerNickname,
		String profileImageUrl
	) {
		return new SocialAccount(
			memberId,
			provider,
			providerUserId,
			email,
			providerNickname,
			profileImageUrl
		);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getEmail() {
		return email;
	}

	public String getProviderNickname() {
		return providerNickname;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}
}
