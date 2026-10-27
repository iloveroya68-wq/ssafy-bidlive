package com.ssafy.bidlive.product.api.dto;

import java.util.List;

import com.ssafy.bidlive.product.domain.InspectionContent;
import jakarta.validation.constraints.Size;

/**
 * 검열 요청 본문입니다. 세 필드 중 최소 하나에 검열할 내용이 있어야 하며, 비어 있는지는
 * {@link InspectionContent}가 정규화 후 판단합니다.
 */
public record ProductInspectionRequest(
	@Size(max = 100) String title,
	@Size(max = 5000) String description,
	@Size(max = 20) List<@Size(max = 2048) String> imageUrls
) {

	public InspectionContent toContent() {
		return new InspectionContent(title, description, imageUrls);
	}
}
