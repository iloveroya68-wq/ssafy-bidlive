package com.ssafy.bidlive.media.infrastructure.s3;

import com.ssafy.bidlive.common.config.S3Properties;
import com.ssafy.bidlive.media.application.port.out.SnapshotStorageGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/** 공통 S3 클라이언트를 사용하는 스냅샷 저장소 어댑터를 등록합니다. */
@Configuration
public class S3Configuration {

	@Bean
	public SnapshotStorageGateway snapshotStorageGateway(
		S3Client s3Client,
		S3Properties s3Properties
	) {
		return new S3SnapshotStorageGateway(s3Client, s3Properties);
	}
}
