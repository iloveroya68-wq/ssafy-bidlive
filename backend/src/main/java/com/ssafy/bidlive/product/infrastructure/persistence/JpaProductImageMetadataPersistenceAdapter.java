package com.ssafy.bidlive.product.infrastructure.persistence;

import com.ssafy.bidlive.product.application.port.out.ProductImageMetadataPersistence;
import com.ssafy.bidlive.product.domain.ProductImageMetadata;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProductImageMetadataPersistenceAdapter
	implements ProductImageMetadataPersistence {

	private final SpringDataProductImageRepository repository;

	public JpaProductImageMetadataPersistenceAdapter(
		SpringDataProductImageRepository repository
	) {
		this.repository = repository;
	}

	@Override
	public ProductImageMetadata save(ProductImageMetadata metadata) {
		return repository.saveAndFlush(ProductImageJpaEntity.from(metadata)).toDomain();
	}
}
