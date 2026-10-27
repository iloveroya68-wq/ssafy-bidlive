package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.chat.application.ChatMessagePersistence;
import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.application.ChatSendPolicy;
import com.ssafy.bidlive.chat.application.ChatSendService;
import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.domain.ChatContentFilter;
import com.ssafy.bidlive.chat.domain.ChatMessageIdGenerator;
import com.ssafy.bidlive.member.application.MemberPersistence;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 채팅 전송 유스케이스를 도메인 간 공개 계약과 채팅 Port 구현으로 조립한다. */
@Configuration(proxyBeanMethods = false)
public class ChatSendConfiguration {

	@Bean
	ChatSendService chatSendService(
		ChatSendPolicy policy,
		AuctionRoomDetailPersistence auctionPersistence,
		MemberPersistence memberPersistence,
		ChatMessagePersistence messagePersistence,
		ChatMuteReader muteReader,
		ChatSlowModeChecker slowModeChecker,
		ChatContentFilter contentFilter,
		ChatMessageIdGenerator messageIdGenerator,
		ApplicationEventPublisher eventPublisher
	) {
		return new ChatSendService(
			policy,
			auctionPersistence,
			memberPersistence,
			messagePersistence,
			muteReader,
			slowModeChecker,
			contentFilter,
			messageIdGenerator,
			eventPublisher
		);
	}
}
