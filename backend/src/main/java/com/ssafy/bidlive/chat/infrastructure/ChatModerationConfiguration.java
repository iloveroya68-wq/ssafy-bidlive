package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.ProhibitedWordRepository;
import com.ssafy.bidlive.chat.domain.RuleBasedChatContentFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ChatModerationConfiguration {

	@Bean
	InMemoryProhibitedWordProvider prohibitedWordProvider(
		ProhibitedWordRepository repository
	) {
		return new InMemoryProhibitedWordProvider(repository);
	}

	@Bean
	ChatContentFilter chatContentFilter(
		InMemoryProhibitedWordProvider provider
	) {
		return new RuleBasedChatContentFilter(provider);
	}

	@Bean
	ProhibitedWordCacheScheduler prohibitedWordCacheScheduler(
		InMemoryProhibitedWordProvider provider
	) {
		return new ProhibitedWordCacheScheduler(provider);
	}
}
