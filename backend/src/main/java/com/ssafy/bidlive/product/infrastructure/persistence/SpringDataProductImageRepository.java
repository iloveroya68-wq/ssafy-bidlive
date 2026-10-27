package com.ssafy.bidlive.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProductImageRepository
	extends JpaRepository<ProductImageJpaEntity, Long> {
}
