package com.ssafy.bidlive.chat.domain;

import java.util.Set;

@FunctionalInterface
public interface ProhibitedWordRepository {

	Set<String> findAll();
}
