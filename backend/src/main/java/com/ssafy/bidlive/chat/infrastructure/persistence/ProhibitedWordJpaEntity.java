package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "prohibited_word")
public class ProhibitedWordJpaEntity {

	private static final int MAX_WORD_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "prohibited_word_id")
	private Long id;

	@Column(name = "word", nullable = false, length = MAX_WORD_LENGTH)
	private String word;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProhibitedWordJpaEntity() {
	}

	private ProhibitedWordJpaEntity(String word, boolean active) {
		String normalizedWord = Objects.requireNonNull(word, "word").strip();
		if (normalizedWord.isBlank()) {
			throw new IllegalArgumentException(
				"word는 비어 있을 수 없습니다."
			);
		}
		if (normalizedWord.length() > MAX_WORD_LENGTH) {
			throw new IllegalArgumentException(
				"word는 100자를 초과할 수 없습니다."
			);
		}

		this.word = normalizedWord;
		this.active = active;
	}

	static ProhibitedWordJpaEntity active(String word) {
		return new ProhibitedWordJpaEntity(word, true);
	}

	static ProhibitedWordJpaEntity inactive(String word) {
		return new ProhibitedWordJpaEntity(word, false);
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
}
