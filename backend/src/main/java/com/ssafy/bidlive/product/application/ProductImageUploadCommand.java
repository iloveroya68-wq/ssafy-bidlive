package com.ssafy.bidlive.product.application;

import com.ssafy.bidlive.product.domain.ProductImageFile;

public record ProductImageUploadCommand(Long ownerMemberId, ProductImageFile image) {
}
