package com.ssafy.bidlive.prediction.domain;

import java.util.Arrays;
import java.util.Optional;

public enum PredictionAchievementGrade {

	ORACLE_1(0L, "시세 예언자 I"),
	ORACLE_2(300L, "시세 예언자 II"),
	ORACLE_3(1_000L, "시세 예언자 III"),
	ORACLE_4(2_500L, "시세 예언자 IV"),
	ORACLE_5(5_000L, "시세 예언자 V");

	private final long minimumScore;
	private final String displayName;

	PredictionAchievementGrade(long minimumScore, String displayName) {
		this.minimumScore = minimumScore;
		this.displayName = displayName;
	}

	public long minimumScore() {
		return minimumScore;
	}

	public String displayName() {
		return displayName;
	}

	public static Optional<PredictionAchievementGrade> fromCode(String code) {
		return Arrays.stream(values())
			.filter(grade -> grade.name().equals(code))
			.findFirst();
	}
}
