package com.ssafy.bidlive.chat.domain;

@FunctionalInterface
public interface ChatContentFilter {

	FilterResult filter(String content);
}
