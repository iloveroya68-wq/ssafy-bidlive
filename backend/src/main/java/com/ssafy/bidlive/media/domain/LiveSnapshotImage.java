package com.ssafy.bidlive.media.domain;

/**
 * 업로드된 스냅샷 이미지의 내용과 형식이며, 허용 형식과 크기 정책을 생성 시점에 강제합니다.
 *
 * <p>클라이언트가 보낸 파일명이나 선언 형식을 그대로 신뢰하지 않기 위해, 저장 확장자는 파일명이
 * 아니라 검증을 통과한 {@link SnapshotImageType}에서만 얻는다.
 */
public record LiveSnapshotImage(
	byte[] content,
	SnapshotImageType type
) {

	/** Spring multipart 한도보다 낮게 두어 형식 검증 이후에도 이 정책이 실제로 적용되게 한다. */
	public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

	public LiveSnapshotImage {
		if (content == null || content.length == 0) {
			throw new IllegalArgumentException("Snapshot image content must not be empty");
		}
		if (type == null) {
			throw new IllegalArgumentException("Snapshot image type must not be null");
		}
		if (content.length > MAX_SIZE_BYTES) {
			throw new IllegalArgumentException(
				"Snapshot image must not exceed " + MAX_SIZE_BYTES + " bytes");
		}
	}

	public long size() {
		return content.length;
	}
}
