package com.ssafy.bidlive.media.application.port.out;

import com.ssafy.bidlive.media.domain.LiveSnapshotImage;

/** 스냅샷 이미지를 외부 오브젝트 스토리지에 저장하고 접근 URL을 돌려주는 포트입니다. */
public interface SnapshotStorageGateway {

	/**
	 * 같은 key로 다시 올리면 기존 오브젝트를 덮어쓴다.
	 *
	 * @return 저장된 오브젝트의 접근 URL
	 * @throws SnapshotStorageException 저장소 호출이 실패한 경우
	 */
	String upload(String key, LiveSnapshotImage image);
}
