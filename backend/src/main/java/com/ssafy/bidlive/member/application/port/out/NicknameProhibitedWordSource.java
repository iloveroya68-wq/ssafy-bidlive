package com.ssafy.bidlive.member.application.port.out;

import java.util.Set;

@FunctionalInterface
public interface NicknameProhibitedWordSource {

	Set<String> getActiveWords();
}
