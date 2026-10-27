package com.ssafy.bidlive.chat.domain;

import java.util.Set;

@FunctionalInterface
public interface ProhibitedWordProvider {

	Set<String> getProhibitedWords();
}
