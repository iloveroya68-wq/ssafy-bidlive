package com.ssafy.bidlive.media.application.port.out;

/**
 * 스냅샷 저장소 연동 실패를 유스케이스가 다룰 수 있게 만든 포트 수준 예외입니다.
 *
 * <p>어댑터의 기술별 예외를 이 타입으로 묶어 두면 `application`이 특정 저장소 SDK를
 * import하지 않고도 외부 호출 실패만 골라 처리할 수 있다.
 */
public class SnapshotStorageException extends RuntimeException {

	public SnapshotStorageException(String message) {
		super(message);
	}

	public SnapshotStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
