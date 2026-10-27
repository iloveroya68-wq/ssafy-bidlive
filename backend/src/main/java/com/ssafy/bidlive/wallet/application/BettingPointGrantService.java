package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.BETTING_WALLET_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.INVALID_GRANT_AMOUNT;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.INVALID_GRANT_TYPE;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.MEMBER_INACTIVE;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.BettingPointGrantErrorCode.POINT_LEDGER_WRITE_FAILED;

import java.util.UUID;

import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.GrantRecord;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.LockedBettingWallet;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.MemberRecord;
import com.ssafy.bidlive.wallet.application.BettingPointGrantPersistence.NewGrant;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입·출석·라이브 시청 보상을 BETTING 지갑과 GRANT 원장에 반영한다. */
@Service
public class BettingPointGrantService {

	private static final int MAX_SOURCE_ID_LENGTH = 100;

	private final BettingPointGrantPersistence persistence;
	private final ApplicationEventPublisher eventPublisher;

	public BettingPointGrantService(
		BettingPointGrantPersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public BettingPointGrantResult grant(
		BettingPointGrantCommand command
	) {
		validateStructure(command);

		try {
			persistence.lockGrantKey(
				command.memberId(),
				command.grantType(),
				command.sourceId()
			);
			return persistence.findByBusinessKey(
				command.memberId(),
				command.grantType(),
				command.sourceId()
			)
				.map(this::duplicatedResult)
				.orElseGet(() -> grantNew(command));
		} catch (BettingPointGrantException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new BettingPointGrantException(
				POINT_LEDGER_WRITE_FAILED,
				exception
			);
		}
	}

	private BettingPointGrantResult grantNew(
		BettingPointGrantCommand command
	) {
		validatePolicy(command);
		MemberRecord member = persistence.lockMember(command.memberId())
			.orElseThrow(() -> error(MEMBER_NOT_FOUND));
		if (member.status() != MemberStatus.ACTIVE) {
			throw error(MEMBER_INACTIVE);
		}

		LockedBettingWallet wallet = persistence
			.lockBettingWallet(command.memberId())
			.orElseThrow(() -> error(BETTING_WALLET_NOT_FOUND));
		long availableBalanceAfter;
		try {
			availableBalanceAfter = Math.addExact(
				wallet.availableBalance(),
				command.amount()
			);
		} catch (ArithmeticException exception) {
			throw new BettingPointGrantException(
				INVALID_GRANT_AMOUNT,
				exception
			);
		}

		GrantRecord saved = persistence.save(new NewGrant(
			UUID.randomUUID(),
			wallet.walletId(),
			command.memberId(),
			command.grantType(),
			command.sourceId(),
			command.amount(),
			command.policyVersion(),
			command.occurredAt(),
			availableBalanceAfter,
			wallet.heldBalance()
		));
		eventPublisher.publishEvent(new BettingWalletBalanceChangedEvent(
			saved.memberId(),
			saved.amount()
		));
		return result(saved, false);
	}

	private BettingPointGrantResult duplicatedResult(
		GrantRecord existing
	) {
		return result(existing, true);
	}

	private BettingPointGrantResult result(
		GrantRecord grant,
		boolean duplicated
	) {
		return new BettingPointGrantResult(
			grant.grantId(),
			grant.memberId(),
			grant.grantType(),
			grant.amount(),
			grant.balanceAfter(),
			grant.ledgerId(),
			duplicated,
			grant.grantedAt()
		);
	}

	private void validateStructure(BettingPointGrantCommand command) {
		if (command == null
			|| command.memberId() == null
			|| command.memberId() <= 0) {
			throw error(MEMBER_NOT_FOUND);
		}
		if (command.grantType() == null
			|| command.sourceId() == null
			|| command.sourceId().isBlank()
			|| !command.sourceId().equals(command.sourceId().trim())
			|| command.sourceId().length() > MAX_SOURCE_ID_LENGTH
			|| !BettingPointGrantPolicy.isValidSource(
				command.grantType(),
				command.memberId(),
				command.sourceId(),
				command.occurredAt()
			)) {
			throw error(INVALID_GRANT_TYPE);
		}
	}

	private void validatePolicy(BettingPointGrantCommand command) {
		if (!BettingPointGrantPolicy.matches(
			command.grantType(),
			command.amount(),
			command.policyVersion()
		)) {
			throw error(INVALID_GRANT_AMOUNT);
		}
	}

	private BettingPointGrantException error(
		BettingPointGrantErrorCode errorCode
	) {
		return new BettingPointGrantException(errorCode);
	}
}
