package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.RuleBasedChatContentFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChatModerationConfigurationTest {

	@Autowired
	private ChatContentFilter chatContentFilter;

	@Autowired
	private InMemoryProhibitedWordProvider prohibitedWordProvider;

	@Autowired
	private ProhibitedWordCacheScheduler prohibitedWordCacheScheduler;

	@Test
	void registersChatModerationBeans() {
		assertThat(chatContentFilter)
			.isInstanceOf(RuleBasedChatContentFilter.class);
		assertThat(prohibitedWordProvider).isNotNull();
		assertThat(prohibitedWordCacheScheduler).isNotNull();
	}
}
