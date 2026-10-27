package com.ssafy.bidlive.media.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ssafy.bidlive.media.domain.LiveSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_snapshot")
public class LiveSnapshotJpaEntity {

	private static final int MAX_ROOM_ID_LENGTH = 100;
	private static final int MAX_IMAGE_URL_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "live_snapshot_id")
	private Long id;

	@Column(name = "room_id", nullable = false, length = MAX_ROOM_ID_LENGTH)
	private String roomId;

	@Column(name = "auction_id", nullable = false, updatable = false)
	private Long auctionId;

	@Column(name = "image_url", nullable = false, length = MAX_IMAGE_URL_LENGTH)
	private String imageUrl;

	@Column(name = "captured_at", nullable = false)
	private OffsetDateTime capturedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected LiveSnapshotJpaEntity() {
	}

	private LiveSnapshotJpaEntity(LiveSnapshot snapshot) {
		this.auctionId = snapshot.auctionId();
		apply(snapshot);
	}

	static LiveSnapshotJpaEntity from(LiveSnapshot snapshot) {
		return new LiveSnapshotJpaEntity(snapshot);
	}

	/** 경매당 스냅샷은 하나이므로 재업로드는 같은 행의 URL과 캡처 시각을 갱신한다. */
	void apply(LiveSnapshot snapshot) {
		String roomId = Objects.requireNonNull(snapshot.roomId(), "roomId").strip();
		if (roomId.length() > MAX_ROOM_ID_LENGTH) {
			throw new IllegalArgumentException("roomId는 100자를 초과할 수 없습니다.");
		}
		String imageUrl = Objects.requireNonNull(snapshot.imageUrl(), "imageUrl").strip();
		if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
			throw new IllegalArgumentException("imageUrl은 500자를 초과할 수 없습니다.");
		}

		this.roomId = roomId;
		this.imageUrl = imageUrl;
		this.capturedAt = Objects.requireNonNull(snapshot.capturedAt(), "capturedAt");
	}

	LiveSnapshot toDomain() {
		return new LiveSnapshot(roomId, auctionId, imageUrl, capturedAt);
	}

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = OffsetDateTime.now();
	}
}
