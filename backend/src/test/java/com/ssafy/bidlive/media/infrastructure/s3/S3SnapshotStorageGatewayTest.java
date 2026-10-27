package com.ssafy.bidlive.media.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageException;
import com.ssafy.bidlive.media.domain.LiveSnapshotImage;
import com.ssafy.bidlive.media.domain.SnapshotImageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 업로드 요청이 검증된 이미지 형식과 길이를 그대로 담고, SDK 실패가 포트 예외로 변환되며
 * 오브젝트 URL이 설정에 따라 만들어지는지 검증합니다.
 */
class S3SnapshotStorageGatewayTest {

	private static final String KEY = "snapshots/room-room-123/auction-102.jpg";

	private final S3Client s3Client = mock(S3Client.class);

	@Test
	void putsObjectWithVerifiedContentTypeAndLength() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		String url = gateway(properties(null)).upload(KEY, image());

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(request.capture(), any(RequestBody.class));

		assertThat(request.getValue().bucket()).isEqualTo("bidlive-bucket");
		assertThat(request.getValue().key()).isEqualTo(KEY);
		assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
		assertThat(request.getValue().contentLength()).isEqualTo(3L);
		assertThat(url)
			.isEqualTo("https://bidlive-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY);
	}

	@Test
	void buildsUrlFromOverriddenEndpointWhenConfigured() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		String url = gateway(properties("http://localhost:9000/")).upload(KEY, image());

		assertThat(url).isEqualTo("http://localhost:9000/bidlive-bucket/" + KEY);
	}

	@Test
	void prefersPublicBaseUrlOverInternalEndpoint() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());
		S3Properties properties = properties("http://minio:9000/");
		properties.setPublicBaseUrl(URI.create("https://api.example.com/files/"));

		String url = gateway(properties).upload(KEY, image());

		assertThat(url).isEqualTo("https://api.example.com/files/" + KEY);
	}

	@Test
	void wrapsServiceErrorIntoStorageException() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(S3Exception.builder().message("AccessDenied").build());

		assertThatThrownBy(() -> gateway(properties(null)).upload(KEY, image()))
			.isInstanceOf(SnapshotStorageException.class)
			.hasMessageContaining(KEY);
	}

	/** 서비스 응답 오류뿐 아니라 통신 오류도 같은 포트 예외로 올려야 502로 이어진다. */
	@Test
	void wrapsClientErrorIntoStorageException() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.create("Unable to connect"));

		assertThatThrownBy(() -> gateway(properties(null)).upload(KEY, image()))
			.isInstanceOf(SnapshotStorageException.class)
			.hasMessageContaining(KEY);
	}

	private S3SnapshotStorageGateway gateway(S3Properties properties) {
		return new S3SnapshotStorageGateway(s3Client, properties);
	}

	private S3Properties properties(String endpoint) {
		S3Properties properties = new S3Properties();
		properties.setBucket("bidlive-bucket");
		properties.setRegion("ap-northeast-2");
		if (endpoint != null) {
			properties.setEndpoint(URI.create(endpoint));
		}
		return properties;
	}

	private LiveSnapshotImage image() {
		return new LiveSnapshotImage(new byte[] {1, 2, 3}, SnapshotImageType.JPEG);
	}
}
