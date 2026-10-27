package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.chat.application.ChatMessagePersistence;
import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.application.ChatSendPolicy;
import com.ssafy.bidlive.chat.application.ChatSendService;
import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.ChatMessageIdGenerator;
import com.ssafy.bidlive.member.application.MemberPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 채팅 전송 유스케이스가 필요한 공개 계약과 Port로 조립되는지 검증한다. */
class ChatSendConfigurationTest {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withBean(
				ChatSendPolicy.class,
				() -> mock(ChatSendPolicy.class)
			)
			.withBean(
				AuctionRoomDetailPersistence.class,
				() -> mock(AuctionRoomDetailPersistence.class)
			)
			.withBean(
				MemberPersistence.class,
				() -> mock(MemberPersistence.class)
			)
			.withBean(
				ChatMessagePersistence.class,
				() -> mock(ChatMessagePersistence.class)
			)
			.withBean(
				ChatMuteReader.class,
				() -> mock(ChatMuteReader.class)
			)
			.withBean(
				ChatSlowModeChecker.class,
				() -> mock(ChatSlowModeChecker.class)
			)
			.withBean(
				ChatContentFilter.class,
				() -> mock(ChatContentFilter.class)
			)
			.withBean(
				ChatMessageIdGenerator.class,
				() -> mock(ChatMessageIdGenerator.class)
			)
			.withUserConfiguration(ChatSendConfiguration.class);

	@Test
	void createsChatSendService() {
		contextRunner.run(context ->
			assertThat(context).hasSingleBean(ChatSendService.class)
		);
	}
}
