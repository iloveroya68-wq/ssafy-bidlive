package com.ssafy.bidlive.member.application;

import java.util.Optional;

import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;
import com.ssafy.bidlive.member.domain.SettlementBank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 마스킹된 정산 계좌 요약을 조회한다. */
@Service
public class SettlementAccountQueryService {

	private final MemberPersistence memberPersistence;
	private final SettlementAccountQueryPersistence accountPersistence;

	public SettlementAccountQueryService(
		MemberPersistence memberPersistence,
		SettlementAccountQueryPersistence accountPersistence
	) {
		this.memberPersistence = memberPersistence;
		this.accountPersistence = accountPersistence;
	}

	@Transactional(readOnly = true)
	public SettlementAccountQueryResult query(Long memberId) {
		validateMemberId(memberId);
		try {
			if (memberPersistence.findById(memberId).isEmpty()) {
				throw error(
					SettlementAccountQueryErrorCode.MEMBER_NOT_FOUND
				);
			}

			Optional<SettlementAccountSnapshot> snapshot =
				accountPersistence.findByMemberId(memberId);
			return new SettlementAccountQueryResult(
				snapshot.map(this::toResult).orElse(null)
			);
		} catch (SettlementAccountQueryException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SettlementAccountQueryException(
				SettlementAccountQueryErrorCode
					.SETTLEMENT_ACCOUNT_UNAVAILABLE,
				exception
			);
		}
	}

	private SettlementAccountResult toResult(
		SettlementAccountSnapshot snapshot
	) {
		SettlementBank bank = SettlementBank.fromCode(snapshot.bankCode());
		return new SettlementAccountResult(
			bank.code(),
			bank.displayName(),
			snapshot.maskedAccountNumber(),
			snapshot.accountHolderName(),
			snapshot.verified(),
			snapshot.updatedAt()
		);
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(SettlementAccountQueryErrorCode.UNAUTHORIZED);
		}
	}

	private SettlementAccountQueryException error(
		SettlementAccountQueryErrorCode errorCode
	) {
		return new SettlementAccountQueryException(errorCode);
	}
}
