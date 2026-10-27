package com.ssafy.bidlive.auction.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis에서 확정한 연장 상태를 PostgreSQL에 반영해 BID-03 저장 흐름을 조율한다. */
@Service
public class AuctionExtensionService {

	private final AuctionExtensionProcessor extensionProcessor;
	private final AuctionExtensionPersistence extensionPersistence;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public AuctionExtensionService(
		AuctionExtensionProcessor extensionProcessor,
		AuctionExtensionPersistence extensionPersistence,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			extensionProcessor,
			extensionPersistence,
			eventPublisher,
			Clock.systemUTC()
		);
	}

	AuctionExtensionService(
		AuctionExtensionProcessor extensionProcessor,
		AuctionExtensionPersistence extensionPersistence,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.extensionProcessor = Objects.requireNonNull(
			extensionProcessor,
			"extensionProcessor"
		);
		this.extensionPersistence = Objects.requireNonNull(
			extensionPersistence,
			"extensionPersistence"
		);
		this.eventPublisher = Objects.requireNonNull(
			eventPublisher,
			"eventPublisher"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** Redis 원자 연장이 성공한 경우에만 그 결과를 PostgreSQL 최종 상태로 반영한다. */
	@Transactional
	public AuctionExtensionResult extend(long auctionId, long requesterId) {
		Instant requestedAt = clock.instant();
		AuctionExtensionProcessingResult processingResult =
			extensionProcessor.extend(new AuctionExtensionCommand(
				auctionId,
				requesterId,
				requestedAt
			));
		if (!processingResult.accepted()) {
			throw new AuctionExtensionException(
				processingResult.failureCode()
			);
		}

		extensionPersistence.synchronizeFinalState(
			auctionId,
			processingResult.endAt(),
			processingResult.extensionCount()
		);
		eventPublisher.publishEvent(new AuctionExtendedEvent(
			auctionId,
			processingResult.endAt(),
			processingResult.extensionCount(),
			processingResult.remainingExtensions()
		));
		return new AuctionExtensionResult(
			auctionId,
			processingResult.previousEndAt(),
			processingResult.endAt(),
			processingResult.extensionCount(),
			processingResult.remainingExtensions()
		);
	}
}
