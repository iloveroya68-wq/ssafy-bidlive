package com.ssafy.bidlive.media.domain;

import java.util.Arrays;
import java.util.Optional;

/** 낙찰 스냅샷으로 허용하는 이미지 형식이며 저장 확장자를 함께 결정합니다. */
public enum SnapshotImageType {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png");

	private final String contentType;
	private final String extension;

	SnapshotImageType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	/** 업로드가 보낸 Content-Type은 파라미터가 붙을 수 있으므로 미디어 타입만 비교한다. */
	public static Optional<SnapshotImageType> fromContentType(String rawContentType) {
		if (rawContentType == null || rawContentType.isBlank()) {
			return Optional.empty();
		}

		String mediaType = rawContentType.split(";", 2)[0].strip().toLowerCase();
		return Arrays.stream(values())
			.filter(type -> type.contentType.equals(mediaType))
			.findFirst();
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}
}
