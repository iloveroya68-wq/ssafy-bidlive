package com.ssafy.bidlive.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductRegistrationServiceTest {

	@Mock
	private ProductRegistrationPersistence persistence;

	private ProductRegistrationService service;

	@BeforeEach
	void setUp() {
		service = new ProductRegistrationService(persistence);
	}

	@Test
	void normalizesProductInformationAndRegistersOwnedImage() {
		ProductRegistrationCommand command = new ProductRegistrationCommand(
			25L,
			"  키크론 Q1 Pro  ",
			"  구성품 포함  ",
			" electronics ",
			" https://cdn.example.com/products/image.jpg "
		);
		when(persistence.existsActiveCategory("ELECTRONICS")).thenReturn(true);
		when(persistence.saveProductWithOwnedImage(
			new ProductRegistrationCommand(
				25L,
				"키크론 Q1 Pro",
				"구성품 포함",
				"ELECTRONICS",
				"https://cdn.example.com/products/image.jpg"
			)
		)).thenReturn(501L);

		ProductRegistrationResult result = service.register(command);

		assertThat(result.productId()).isEqualTo(501L);
	}

	@Test
	void rejectsBlankRequiredProductField() {
		ProductRegistrationCommand command = new ProductRegistrationCommand(
			25L,
			" ",
			"상품 설명",
			"ELECTRONICS",
			"https://cdn.example.com/products/image.jpg"
		);

		assertThatThrownBy(() -> service.register(command))
			.isInstanceOf(ProductRegistrationException.class);
		verify(persistence, never()).existsActiveCategory("ELECTRONICS");
	}

	@Test
	void rejectsInactiveOrUnknownCategory() {
		ProductRegistrationCommand command = new ProductRegistrationCommand(
			25L,
			"상품명",
			"상품 설명",
			"UNKNOWN",
			"https://cdn.example.com/products/image.jpg"
		);
		when(persistence.existsActiveCategory("UNKNOWN")).thenReturn(false);

		assertThatThrownBy(() -> service.register(command))
			.isInstanceOf(ProductRegistrationException.class);
		verify(persistence, never()).saveProductWithOwnedImage(command);
	}
}
