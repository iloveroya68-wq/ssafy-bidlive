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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "member",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_member_normalized_nickname",
		columnNames = "normalized_nickname"
	)
)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;

	@Column(name = "nickname", length = 30, unique = true)
	private String nickname;

	@Column(name = "normalized_nickname", length = 30)
	private String normalizedNickname;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private MemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private MemberStatus status;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Member() {
	}

	private Member(MemberRole role, MemberStatus status) {
		this.role = role;
		this.status = status;
	}

	public static Member createOAuthMember() {
		return new Member(MemberRole.USER, MemberStatus.ACTIVE);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		synchronizeNormalizedNickname();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		synchronizeNormalizedNickname();
		updatedAt = Instant.now();
	}

	public void changeNickname(String nickname) {
		String normalizedNickname = NicknamePolicy.normalize(nickname);
		if (!NicknamePolicy.isValidFormat(normalizedNickname)) {
			throw new IllegalArgumentException("nickname format is invalid");
		}
		this.nickname = normalizedNickname;
		this.normalizedNickname = NicknamePolicy.normalizedKey(normalizedNickname);
	}

	private void synchronizeNormalizedNickname() {
		normalizedNickname = NicknamePolicy.normalizedKey(nickname);
	}

	public Long getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	public String getNormalizedNickname() {
		return normalizedNickname;
	}

	public MemberRole getRole() {
		return role;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
