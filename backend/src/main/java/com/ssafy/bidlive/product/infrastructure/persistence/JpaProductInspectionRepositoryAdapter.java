package com.ssafy.bidlive.product.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.product.domain.InspectionCategory;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;
import com.ssafy.bidlive.product.domain.ProductInspectionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaProductInspectionRepositoryAdapter implements ProductInspectionRepository {

	private final SpringDataProductInspectionRepository repository;
	private final ObjectMapper objectMapper;

	public JpaProductInspectionRepositoryAdapter(
		SpringDataProductInspectionRepository repository,
		ObjectMapper objectMapper
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ProductInspection> findReusableByContentHash(String contentHash) {
		return repository
			.findLatestReusable(contentHash, InspectionVerdict.NEEDS_REVIEW)
			.map(this::toDomain);
	}

	@Override
	@Transactional
	public ProductInspection save(ProductInspection inspection) {
		ProductInspectionJpaEntity saved = repository.save(ProductInspectionJpaEntity.of(
			inspection.requesterId(),
			inspection.contentHash(),
			inspection.verdict(),
			serialize(inspection),
			inspection.inspectedAt()
		));
		return toDomain(saved);
	}

	private String serialize(ProductInspection inspection) {
		InspectionDetailPayload payload = new InspectionDetailPayload(
			toUnit(inspection.textResult()),
			inspection.imageResults().stream().map(this::toUnit).toList()
		);

		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize inspection detail", exception);
		}
	}

	private InspectionDetailPayload.Unit toUnit(InspectionUnitResult result) {
		if (result == null) {
			return null;
		}

		return new InspectionDetailPayload.Unit(
			result.verdict().name(),
			result.target(),
			result.flaggedCategories().stream().map(InspectionCategory::name).toList(),
			result.maxScore(),
			result.reason(),
			result.failureReason()
		);
	}

	private ProductInspection toDomain(ProductInspectionJpaEntity entity) {
		InspectionDetailPayload payload = deserialize(entity.detail());

		return new ProductInspection(
			entity.requesterMemberId(),
			entity.contentHash(),
			entity.verdict(),
			toUnitResult(payload.text()),
			payload.images() == null
				? List.of()
				: payload.images().stream().map(this::toUnitResult).toList(),
			entity.inspectedAt()
		);
	}

	private InspectionDetailPayload deserialize(String detail) {
		try {
			return objectMapper.readValue(detail, InspectionDetailPayload.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to read inspection detail", exception);
		}
	}

	private InspectionUnitResult toUnitResult(InspectionDetailPayload.Unit unit) {
		if (unit == null) {
			return null;
		}

		return new InspectionUnitResult(
			InspectionVerdict.valueOf(unit.verdict()),
			unit.target(),
			unit.flaggedCategories() == null
				? List.of()
				: unit.flaggedCategories().stream()
					.map(InspectionCategory::from)
					.flatMap(Optional::stream)
					.toList(),
			unit.maxScore(),
			unit.reason(),
			unit.failureReason()
		);
	}
}
