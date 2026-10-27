package com.ssafy.bidlive.common.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공통 이미지 저장에 사용하는 S3 버킷, 리전과 선택적 엔드포인트 설정입니다. */
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

	private String bucket;
	private String region;

	/** MinIO 등 S3 호환 저장소를 쓸 때만 채우며 비어 있으면 AWS 기본 엔드포인트를 사용합니다. */
	private URI endpoint;

	/** 브라우저에 반환할 공개 주소이며, 비어 있으면 endpoint 또는 AWS S3 주소를 사용합니다. */
	private URI publicBaseUrl;

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = requireText(bucket, "bucket");
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = requireText(region, "region");
	}

	public URI getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(URI endpoint) {
		this.endpoint = endpoint;
	}

	public URI getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(URI publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public String publicObjectUrl(String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("S3 object key must not be blank");
		}
		if (publicBaseUrl != null) {
			return appendPath(publicBaseUrl.toString(), key);
		}
		if (endpoint != null) {
			return appendPath(endpoint.toString(), bucket + "/" + key);
		}
		return "https://%s.s3.%s.amazonaws.com/%s".formatted(
			bucket,
			region,
			key
		);
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("aws.s3." + name + " must not be blank");
		}
		return value.trim();
	}

	private String appendPath(String baseUrl, String path) {
		String normalizedBase = baseUrl.endsWith("/")
			? baseUrl.substring(0, baseUrl.length() - 1)
			: baseUrl;
		return normalizedBase + "/" + path;
	}
}
