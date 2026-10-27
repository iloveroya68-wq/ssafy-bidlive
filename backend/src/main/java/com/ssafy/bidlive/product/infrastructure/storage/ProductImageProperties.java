package com.ssafy.bidlive.product.infrastructure.storage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** 상품 이미지 업로드의 운영 설정입니다. */
@ConfigurationProperties(prefix = "app.product-image")
public class ProductImageProperties {

	private DataSize maxSize = DataSize.ofMegabytes(5);
	private List<String> allowedContentTypes =
		List.of("image/jpeg", "image/png", "image/webp");
	private String keyPrefix = "products";

	public DataSize getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(DataSize maxSize) {
		this.maxSize = maxSize;
	}

	public List<String> getAllowedContentTypes() {
		return allowedContentTypes;
	}

	public void setAllowedContentTypes(List<String> allowedContentTypes) {
		this.allowedContentTypes = List.copyOf(allowedContentTypes);
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}
}
