package com.ssafy.bidlive.product.infrastructure.storage;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageException;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageGateway;
import com.ssafy.bidlive.product.domain.ProductImageFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** 상품 이미지를 공통 S3 버킷에 저장하는 어댑터입니다. */
public class S3ProductImageStorageGateway implements ProductImageStorageGateway {

	private final S3Client s3Client;
	private final S3Properties s3Properties;

	public S3ProductImageStorageGateway(S3Client s3Client, S3Properties s3Properties) {
		this.s3Client = s3Client;
		this.s3Properties = s3Properties;
	}

	@Override
	public String upload(String key, ProductImageFile image) {
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(s3Properties.getBucket())
			.key(key)
			.contentType(image.type().contentType())
			.contentLength(image.size())
			.build();

		try {
			s3Client.putObject(request, RequestBody.fromBytes(image.content()));
		} catch (SdkException exception) {
			throw new ProductImageStorageException(
				"Failed to upload product image", exception);
		}
		return s3Properties.publicObjectUrl(key);
	}

	@Override
	public void delete(String key) {
		DeleteObjectRequest request = DeleteObjectRequest.builder()
			.bucket(s3Properties.getBucket())
			.key(key)
			.build();
		try {
			s3Client.deleteObject(request);
		} catch (SdkException exception) {
			throw new ProductImageStorageException(
				"Failed to delete product image", exception);
		}
	}

}
