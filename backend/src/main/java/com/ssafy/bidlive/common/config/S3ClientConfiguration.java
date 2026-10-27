package com.ssafy.bidlive.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** 상품 이미지와 라이브 스냅샷이 함께 사용하는 S3 클라이언트를 등록합니다. */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3ClientConfiguration {

	@Bean
	public S3Client s3Client(S3Properties s3Properties) {
		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(s3Properties.getRegion()))
			.credentialsProvider(DefaultCredentialsProvider.create());

		if (s3Properties.getEndpoint() != null) {
			builder.endpointOverride(s3Properties.getEndpoint())
				.forcePathStyle(true);
		}

		return builder.build();
	}
}
