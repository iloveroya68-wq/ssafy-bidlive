package com.ssafy.bidlive.chat.infrastructure;

import java.time.Clock;

import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.application.ChatModerationWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** due 검열 작업을 제한된 batch로 처리하고 만료된 최종 임대를 DLQ로 복구합니다. */
@Component
@ConditionalOnProperty(
	name = "chat.moderation.enabled",
	havingValue = "true"
)
public class ChatModerationWorkerScheduler {

	private static final Logger log = LoggerFactory.getLogger(
		ChatModerationWorkerScheduler.class
	);
	private final ChatModerationWorker worker;
	private final ChatModerationJobQueue jobQueue;
	private final ChatAsyncModerationProperties properties;
	private final Clock clock;

	// 생성자가 둘(운영용·테스트용 Clock 주입)이라 @Autowired 없이는 Spring이 기본 생성자로 후퇴해
	// 부팅이 죽는다. 이 빈은 chat.moderation.enabled=true에서만 조립돼 켜기 전까지 드러나지 않았다.
	@Autowired
	public ChatModerationWorkerScheduler(
		ChatModerationWorker worker,
		ChatModerationJobQueue jobQueue,
		ChatAsyncModerationProperties properties
	) {
		this(worker, jobQueue, properties, Clock.systemUTC());
	}

	ChatModerationWorkerScheduler(
		ChatModerationWorker worker,
		ChatModerationJobQueue jobQueue,
		ChatAsyncModerationProperties properties,
		Clock clock
	) {
		this.worker = worker;
		this.jobQueue = jobQueue;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${chat.moderation.poll-delay:1s}")
	public void poll() {
		try {
			int recovered = jobQueue.recoverExhaustedLeases(
				clock.instant().minus(properties.getLease()),
				properties.getMaxAttempts(),
				clock.instant()
			);
			if (recovered > 0) {
				log.warn("recovered exhausted moderation jobs: count={}", recovered);
			}
			for (int index = 0; index < properties.getBatchSize(); index++) {
				if (!worker.processNext()) {
					break;
				}
			}
		} catch (RuntimeException exception) {
			log.error(
				"chat moderation polling failed: failureType={}",
				exception.getClass().getSimpleName()
			);
		}
	}
}
