package com.ssafy.bidlive.member.infrastructure;

import java.util.Set;

import com.ssafy.bidlive.chat.domain.ProhibitedWordProvider;
import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import org.springframework.stereotype.Component;

/** 채팅 도메인이 공개한 활성 금칙어 캐시를 회원 닉네임 검사 포트에 연결한다. */
@Component
public class CachedNicknameProhibitedWordSource
	implements NicknameProhibitedWordSource {

	private final ProhibitedWordProvider prohibitedWordProvider;

	public CachedNicknameProhibitedWordSource(
		ProhibitedWordProvider prohibitedWordProvider
	) {
		this.prohibitedWordProvider = prohibitedWordProvider;
	}

	@Override
	public Set<String> getActiveWords() {
		return prohibitedWordProvider.getProhibitedWords();
	}
}
