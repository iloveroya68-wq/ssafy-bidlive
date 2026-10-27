package com.ssafy.bidlive.deal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 낙찰 결과의 Deal 생성·기존 행 재사용·대상 없음 실패를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionDealCreationServiceTest {

	@Mock
	private AuctionDealCreationPersistence persistence;

	private AuctionDealCreationService service;

	@BeforeEach
	void setUp() {
		service = new AuctionDealCreationService(persistence);
	}

	@Test
	void returnsCreatedOrExistingDealId() {
		when(persistence.createOrFind(900L))
			.thenReturn(OptionalLong.of(800L));

		assertThat(service.create(900L)).isEqualTo(800L);
		verify(persistence).createOrFind(900L);
	}

	@Test
	void rejectsNonSoldOrMissingAuctionResult() {
		when(persistence.createOrFind(900L))
			.thenReturn(OptionalLong.empty());

		assertThatThrownBy(() -> service.create(900L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sold auction result");
	}
}
