package com.ssafy.bidlive.product.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageException;
import com.ssafy.bidlive.product.domain.ProductImageFile;
import com.ssafy.bidlive.product.domain.ProductImageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ProductImageStorageGatewayTest {

	private static final String KEY = "products/25/image.jpg";

	private final S3Client s3Client = mock(S3Client.class);

	@Test
	void uploadsVerifiedImageAndBuildsPublicUrl() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		String imageUrl = gateway().upload(KEY, image());

		ArgumentCaptor<PutObjectRequest> request =
			ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("bidlive-bucket");
		assertThat(request.getValue().key()).isEqualTo(KEY);
		assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
		assertThat(request.getValue().contentLength()).isEqualTo(3L);
		assertThat(imageUrl)
			.isEqualTo("https://bidlive-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY);
	}

	@Test
	void returnsPublicProxyUrlWhileUploadingThroughInternalMinioEndpoint() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());
		S3Properties properties = properties();
		properties.setEndpoint(URI.create("http://minio:9000"));
		properties.setPublicBaseUrl(URI.create("https://api.example.com/files/"));

		String imageUrl = new S3ProductImageStorageGateway(
			s3Client,
			properties
		).upload(KEY, image());

		assertThat(imageUrl).isEqualTo("https://api.example.com/files/" + KEY);
	}

	@Test
	void deletesObjectForCompensation() {
		when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
			.thenReturn(DeleteObjectResponse.builder().build());

		gateway().delete(KEY);

		ArgumentCaptor<DeleteObjectRequest> request =
			ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client).deleteObject(request.capture());
		assertThat(request.getValue().key()).isEqualTo(KEY);
	}

	@Test
	void hidesStorageDetailsAndWrapsSdkFailure() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.create("credential detail"));

		assertThatThrownBy(() -> gateway().upload(KEY, image()))
			.isInstanceOf(ProductImageStorageException.class)
			.hasMessage("Failed to upload product image")
			.hasMessageNotContaining(KEY);
	}

	private S3ProductImageStorageGateway gateway() {
		return new S3ProductImageStorageGateway(s3Client, properties());
	}

	private S3Properties properties() {
		S3Properties properties = new S3Properties();
		properties.setBucket("bidlive-bucket");
		properties.setRegion("ap-northeast-2");
		return properties;
	}

	private ProductImageFile image() {
		return new ProductImageFile(
			new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
			ProductImageType.JPEG
		);
	}
}
