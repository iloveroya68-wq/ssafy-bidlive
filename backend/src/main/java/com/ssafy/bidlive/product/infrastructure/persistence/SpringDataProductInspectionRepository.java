package com.ssafy.bidlive.product.infrastructure.persistence;

import java.util.Optional;

import com.ssafy.bidlive.product.domain.InspectionVerdict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataProductInspectionRepository
	extends JpaRepository<ProductInspectionJpaEntity, Long> {

	/** 같은 콘텐츠에 대한 확정 판정 중 가장 최근 것만 캐시로 재사용한다. */
	@Query("""
		select inspection
		from ProductInspectionJpaEntity inspection
		where inspection.contentHash = :contentHash
			and inspection.verdict <> :excludedVerdict
		order by inspection.inspectedAt desc
		limit 1
		""")
	Optional<ProductInspectionJpaEntity> findLatestReusable(
		@Param("contentHash") String contentHash,
		@Param("excludedVerdict") InspectionVerdict excludedVerdict
	);
}
