package com.ssafy.bidlive.media.domain;

/** 낙찰 스냅샷 저장이 거부되는 사유이며 이름이 그대로 오류 응답의 `code`가 됩니다. */
public enum LiveSnapshotFailureCode {

	IMAGE_REQUIRED,
	UNSUPPORTED_IMAGE_TYPE,
	IMAGE_TOO_LARGE,
	AUCTION_NOT_FOUND,
	NOT_SNAPSHOT_UPLOADER,
	AUCTION_NOT_SETTLED,
	SNAPSHOT_STORAGE_ERROR
}
