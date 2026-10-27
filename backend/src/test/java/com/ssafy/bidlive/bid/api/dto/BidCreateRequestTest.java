package com.ssafy.bidlive.bid.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 직접 입력한 입찰 금액의 필수 여부와 양수 제약을 검증한다. */
class BidCreateRequestTest {

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
	void 양수_금액을_허용한다() {
		BidCreateRequest request = new BidCreateRequest(82_000L);

		Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void 금액이_null이면_거부한다() {
		BidCreateRequest request = new BidCreateRequest(null);

		Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

		assertSingleAmountViolation(violations, NotNull.class);
	}

	@Test
	void 금액이_0이면_거부한다() {
		BidCreateRequest request = new BidCreateRequest(0L);

		Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

		assertSingleAmountViolation(violations, Positive.class);
	}

	@Test
	void 금액이_음수이면_거부한다() {
		BidCreateRequest request = new BidCreateRequest(-1L);

		Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

		assertSingleAmountViolation(violations, Positive.class);
	}

	private static void assertSingleAmountViolation(
		Set<ConstraintViolation<BidCreateRequest>> violations,
		Class<? extends Annotation> constraintType
	) {
		assertThat(violations).singleElement().satisfies(violation -> {
			assertThat(violation.getPropertyPath()).hasToString("amount");
			assertThat(violation.getConstraintDescriptor().getAnnotation())
				.isInstanceOf(constraintType);
		});
	}
}
