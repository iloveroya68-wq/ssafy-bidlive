package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** 일일 리더보드 요청의 선택 조건과 페이지 크기 범위를 검증한다. */
class PredictionLeaderboardRequestTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidatorFactory() {
		validatorFactory.close();
	}

	@Test
	void acceptsValidQueryConditions() {
		PredictionLeaderboardRequest request = new PredictionLeaderboardRequest(
			LocalDate.parse("2026-07-22"),
			20,
			"opaque-cursor"
		);

		Set<ConstraintViolation<PredictionLeaderboardRequest>> violations =
			validator.validate(request);

		assertThat(violations).isEmpty();
		assertThat(request.date()).isEqualTo(LocalDate.parse("2026-07-22"));
		assertThat(request.size()).isEqualTo(20);
		assertThat(request.cursor()).isEqualTo("opaque-cursor");
	}

	@Test
	void appliesDefaultPageSizeWhenOptionalConditionsAreOmitted() {
		PredictionLeaderboardRequest request =
			new PredictionLeaderboardRequest(null, null, null);

		assertThat(validator.validate(request)).isEmpty();
		assertThat(request.size()).isEqualTo(10);
		assertThat(request.cursor()).isNull();
	}

	@Test
	void rejectsPageSizeOutsideContractRange() {
		PredictionLeaderboardRequest tooSmall =
			new PredictionLeaderboardRequest(null, 0, null);
		PredictionLeaderboardRequest tooLarge =
			new PredictionLeaderboardRequest(null, 101, null);

		assertThat(validator.validate(tooSmall))
			.singleElement()
			.satisfies(violation ->
				assertThat(violation.getPropertyPath()).hasToString("size")
			);
		assertThat(validator.validate(tooLarge))
			.singleElement()
			.satisfies(violation ->
				assertThat(violation.getPropertyPath()).hasToString("size")
			);
	}
}
