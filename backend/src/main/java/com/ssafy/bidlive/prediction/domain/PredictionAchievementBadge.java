package com.ssafy.bidlive.prediction.domain;

import java.util.Arrays;
import java.util.Optional;

public enum PredictionAchievementBadge {

	FIRST_HIT("첫 적중"),
	PINPOINT("정밀 예측"),
	HIT_10("적중 10회"),
	PREDICTION_50("예측 정산 50회"),
	PINPOINT_10("5% 이내 적중 10회"),
	ORACLE_MASTER("최고 등급 달성");

	private final String displayName;

	PredictionAchievementBadge(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public static Optional<PredictionAchievementBadge> fromCode(String code) {
		return Arrays.stream(values())
			.filter(badge -> badge.name().equals(code))
			.findFirst();
	}
}
