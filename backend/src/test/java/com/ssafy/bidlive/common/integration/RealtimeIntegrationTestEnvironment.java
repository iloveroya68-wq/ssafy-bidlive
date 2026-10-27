package com.ssafy.bidlive.common.integration;

import java.util.stream.Stream;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/** 실시간 통합 테스트가 공유하는 PostgreSQL·Redis 컨테이너와 Spring 접속 정보를 관리합니다. */
abstract class RealtimeIntegrationTestEnvironment {

	private static final PostgreSQLContainer<?> POSTGRESQL =
		new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("bidlive_realtime_test")
			.withUsername("bidlive_test")
			.withPassword("bidlive_test");

	private static final GenericContainer<?> REDIS =
		new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
			.withExposedPorts(6379);

	static {
		Startables.deepStart(Stream.of(POSTGRESQL, REDIS)).join();
	}

	@DynamicPropertySource
	static void registerContainerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRESQL::getUsername);
		registry.add("spring.datasource.password", POSTGRESQL::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	static boolean containersAreRunning() {
		return POSTGRESQL.isRunning() && REDIS.isRunning();
	}
}
