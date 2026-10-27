package com.ssafy.bidlive.media.infrastructure.s3;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageException;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageGateway;
import com.ssafy.bidlive.media.domain.LiveSnapshotImage;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** 스냅샷 이미지를 S3 버킷에 올리고 오브젝트 접근 URL을 만드는 어댑터입니다. */
public class S3SnapshotStorageGateway implements SnapshotStorageGateway {

	private final S3Client s3Client;
	private final S3Properties s3Properties;

	public S3SnapshotStorageGateway(S3Client s3Client, S3Properties s3Properties) {
		this.s3Client = s3Client;
		this.s3Properties = s3Properties;
	}

	/**
	 * Content-Type은 클라이언트가 보낸 헤더가 아니라 검증을 통과한 이미지 형식에서 가져온다.
	 * Content-Length를 함께 넘겨 SDK가 스트림 길이를 추정하지 않게 하고, SDK 예외는 서비스 응답
	 * 오류와 통신 오류를 모두 포함하는 {@code SdkException} 하나로 묶어 포트 예외로 변환한다.
	 */
	@Override
	public String upload(String key, LiveSnapshotImage image) {
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(s3Properties.getBucket())
			.key(key)
			.contentType(image.type().contentType())
			.contentLength(image.size())
			.build();

		try {
			s3Client.putObject(request, RequestBody.fromBytes(image.content()));
		} catch (SdkException exception) {
			throw new SnapshotStorageException(
				"Failed to upload snapshot to S3 key=" + key, exception);
		}

		return s3Properties.publicObjectUrl(key);
	}
}
