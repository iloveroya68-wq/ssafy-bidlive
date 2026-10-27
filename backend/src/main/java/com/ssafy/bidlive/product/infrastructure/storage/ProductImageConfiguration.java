package com.ssafy.bidlive.product.infrastructure.storage;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.product.application.ProductImageUploadService;
import com.ssafy.bidlive.product.application.port.out.ProductImageMetadataPersistence;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageGateway;
import com.ssafy.bidlive.product.domain.ProductImageType;
import com.ssafy.bidlive.product.domain.ProductImageUploadPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/** 상품 이미지 업로드 정책, 저장소와 유스케이스를 조립합니다. */
@Configuration
@EnableConfigurationProperties(ProductImageProperties.class)
public class ProductImageConfiguration {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public ProductImageUploadPolicy productImageUploadPolicy(
		ProductImageProperties properties
	) {
		Set<ProductImageType> allowedTypes = Arrays.stream(ProductImageType.values())
			.filter(type -> properties.getAllowedContentTypes().stream()
				.anyMatch(type::matchesContentType))
			.collect(Collectors.toUnmodifiableSet());

		return new ProductImageUploadPolicy(
			properties.getMaxSize().toBytes(),
			allowedTypes,
			properties.getKeyPrefix()
		);
	}

	@Bean
	public ProductImageStorageGateway productImageStorageGateway(
		S3Client s3Client,
		S3Properties s3Properties
	) {
		return new S3ProductImageStorageGateway(s3Client, s3Properties);
	}

	@Bean
	public ProductImageUploadService productImageUploadService(
		MemberPersistence memberPersistence,
		ProductImageStorageGateway storageGateway,
		ProductImageMetadataPersistence metadataPersistence,
		ProductImageUploadPolicy policy
	) {
		return new ProductImageUploadService(
			memberPersistence,
			storageGateway,
			metadataPersistence,
			policy,
			Clock.system(SERVICE_ZONE)
		);
	}
}
