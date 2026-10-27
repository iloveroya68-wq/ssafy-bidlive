package com.ssafy.bidlive.auth.application;

import java.time.Duration;

public interface OneTimeLoginCodeStore {

	void save(String loginCode, Long memberId, Duration ttl);

	LoginCodeConsumeResult consume(String loginCode);
}
