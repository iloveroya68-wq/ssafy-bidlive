package com.ssafy.bidlive.product.application;

import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.IMAGE_STORAGE_UNAVAILABLE;
import static com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.product.application.port.out.ProductImageMetadataPersistence;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageGateway;
import com.ssafy.bidlive.product.domain.ProductImageFile;
import com.ssafy.bidlive.product.domain.ProductImageMetadata;
import com.ssafy.bidlive.product.domain.ProductImageType;
import com.ssafy.bidlive.product.domain.ProductImageUploadPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductImageUploadServiceTest {

	private static final Long MEMBER_ID = 25L;
	private static final Clock CLOCK = Clock.fixed(
		Instant.parse("2026-07-27T09:00:00Z"),
		ZoneId.of("Asia/Seoul")
	);

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private ProductImageStorageGateway storageGateway;

	@Mock
	private ProductImageMetadataPersistence metadataPersistence;

	private ProductImageUploadService service;

	@BeforeEach
	void setUp() {
		service = new ProductImageUploadService(
			memberPersistence,
			storageGateway,
			metadataPersistence,
			new ProductImageUploadPolicy(
				5 * 1024 * 1024,
				Set.of(ProductImageType.JPEG, ProductImageType.PNG, ProductImageType.WEBP),
				"products"
			),
			CLOCK
		);
	}

	@Test
	void uploadsWithGeneratedKeyAndPersistsOwnerMetadata() {
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
		when(storageGateway.upload(any(), any())).thenReturn(
			"https://cdn.example.com/products/image.jpg");
		when(metadataPersistence.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProductImageUploadResult result = service.upload(command());

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<ProductImageMetadata> metadata =
			ArgumentCaptor.forClass(ProductImageMetadata.class);
		verify(storageGateway).upload(key.capture(), any());
		verify(metadataPersistence).save(metadata.capture());

		assertThat(key.getValue())
			.startsWith("products/25/")
			.endsWith(".jpg");
		assertThat(key.getValue()).doesNotContain("original");
		assertThat(metadata.getValue().ownerMemberId()).isEqualTo(MEMBER_ID);
		assertThat(metadata.getValue().contentType()).isEqualTo("image/jpeg");
		assertThat(metadata.getValue().createdAt().toString()).endsWith("+09:00");
		assertThat(result.imageUrl())
			.isEqualTo("https://cdn.example.com/products/image.jpg");
	}

	@Test
	void rejectsUnknownMemberBeforeCallingStorage() {
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.upload(command()))
			.isInstanceOf(ProductImageUploadException.class)
			.extracting(exception -> ((ProductImageUploadException) exception).getErrorCode())
			.isEqualTo(UNAUTHORIZED);
		verify(storageGateway, never()).upload(any(), any());
	}

	@Test
	void deletesUploadedObjectWhenMetadataSaveFails() {
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
		when(storageGateway.upload(any(), any())).thenReturn(
			"https://cdn.example.com/products/image.jpg");
		when(metadataPersistence.save(any())).thenThrow(new IllegalStateException("DB failure"));

		assertThatThrownBy(() -> service.upload(command()))
			.isInstanceOf(ProductImageUploadException.class)
			.extracting(exception -> ((ProductImageUploadException) exception).getErrorCode())
			.isEqualTo(IMAGE_STORAGE_UNAVAILABLE);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storageGateway).delete(key.capture());
		assertThat(key.getValue()).startsWith("products/25/");
	}

	private ProductImageUploadCommand command() {
		return new ProductImageUploadCommand(
			MEMBER_ID,
			new ProductImageFile(
				new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
				ProductImageType.JPEG
			)
		);
	}

	private Member member() {
		Member member = Member.createOAuthMember();
		ReflectionTestUtils.setField(member, "id", MEMBER_ID);
		return member;
	}
}
