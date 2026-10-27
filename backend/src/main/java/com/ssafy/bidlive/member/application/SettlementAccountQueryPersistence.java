package com.ssafy.bidlive.member.application;

import java.util.Optional;

/** 정산 계좌 조회 유스케이스가 요구하는 영속성 계약이다. */
public interface SettlementAccountQueryPersistence {

	Optional<SettlementAccountSnapshot> findByMemberId(Long memberId);
}
