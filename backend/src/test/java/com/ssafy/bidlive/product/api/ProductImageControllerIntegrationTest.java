package com.ssafy.bidlive.product.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageException;
import com.ssafy.bidlive.product.application.port.out.ProductImageStorageGateway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT, multipart, Flyway와 PostgreSQL을 거쳐 상품 이미지 API 계약을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductImageControllerIntegrationTest {

	private static final String PATH = "/api/v1/product-images";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private ProductImageStorageGateway storageGateway;

	@Test
	void uploadsVerifiedImageAndPersistsOwnerMetadata() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		when(storageGateway.upload(any(), any())).thenReturn(
			"https://cdn.example.com/products/generated.jpg");

		mockMvc.perform(multipart(PATH)
			.file(jpeg("image/jpeg"))
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.imageId").isString())
			.andExpect(jsonPath("$.imageUrl")
				.value("https://cdn.example.com/products/generated.jpg"))
			.andExpect(jsonPath("$.createdAt").isString());

		Object[] row = (Object[]) entityManager.createNativeQuery("""
			SELECT owner_member_id, image_url, storage_key, content_type, size_bytes, product_id
			FROM product_image
			WHERE owner_member_id = :memberId
			""")
			.setParameter("memberId", member.getId())
			.getSingleResult();
		org.assertj.core.api.Assertions.assertThat(((Number) row[0]).longValue())
			.isEqualTo(member.getId());
		org.assertj.core.api.Assertions.assertThat(row[1])
			.isEqualTo("https://cdn.example.com/products/generated.jpg");
		org.assertj.core.api.Assertions.assertThat(row[2].toString())
			.startsWith("products/" + member.getId() + "/")
			.endsWith(".jpg");
		org.assertj.core.api.Assertions.assertThat(row[3]).isEqualTo("image/jpeg");
		org.assertj.core.api.Assertions.assertThat(((Number) row[4]).longValue())
			.isEqualTo(4L);
		org.assertj.core.api.Assertions.assertThat(row[5]).isNull();
	}

	@Test
	void rejectsMissingImageAndUnauthenticatedRequest() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());

		mockMvc.perform(multipart(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMAGE_REQUIRED"));

		mockMvc.perform(multipart(PATH).file(jpeg("image/jpeg")))
			.andExpect(status().isUnauthorized());
		verify(storageGateway, never()).upload(any(), any());
	}

	@Test
	void rejectsMimeAndActualSignatureMismatch() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());

		mockMvc.perform(multipart(PATH)
			.file(jpeg("image/png"))
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
		verify(storageGateway, never()).upload(any(), any());
	}

	@Test
	void rejectsImageLargerThanConfiguredLimit() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		byte[] oversizedJpeg = new byte[5 * 1024 * 1024 + 1];
		oversizedJpeg[0] = (byte) 0xFF;
		oversizedJpeg[1] = (byte) 0xD8;
		oversizedJpeg[2] = (byte) 0xFF;

		mockMvc.perform(multipart(PATH)
			.file(new MockMultipartFile(
				"image",
				"large.jpg",
				"image/jpeg",
				oversizedJpeg
			))
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("IMAGE_SIZE_EXCEEDED"));
		verify(storageGateway, never()).upload(any(), any());
	}

	@Test
	void mapsStorageFailureToServiceUnavailable() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		when(storageGateway.upload(any(), any()))
			.thenThrow(new ProductImageStorageException(
				"Failed to upload product image",
				new IllegalStateException("storage failure")
			));

		mockMvc.perform(multipart(PATH)
			.file(jpeg("image/jpeg"))
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("IMAGE_STORAGE_UNAVAILABLE"));
	}

	private MockMultipartFile jpeg(String contentType) {
		return new MockMultipartFile(
			"image",
			"original-name.jpg",
			contentType,
			new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
		);
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
