package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.INVALID_CURSOR;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.INVALID_LEDGER_FILTER;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.WALLET_NOT_FOUND;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ssafy.bidlive.wallet.application.PointLedgerQueryResult.Item;
import com.ssafy.bidlive.wallet.domain.PointLedgerCursor;
import com.ssafy.bidlive.wallet.domain.PointLedgerDescriptionPolicy;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 거래·베팅 포인트 원장을 필터와 커서 기준으로 조회한다. */
@Service
public class PointLedgerQueryService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 100;

	private final PointLedgerQueryPersistence persistence;

	public PointLedgerQueryService(PointLedgerQueryPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional(readOnly = true)
	public PointLedgerQueryResult query(
		Long memberId,
		String walletType,
		String transactionType,
		String cursor,
		Integer size
	) {
		validateMemberId(memberId);
		WalletType parsedWalletType = parseWalletType(walletType);
		PointLedgerTransactionType parsedTransactionType =
			parseTransactionType(transactionType);
		PointLedgerCursor parsedCursor = parseCursor(cursor);
		int parsedSize = parseSize(size);

		validateRequiredWallets(
			persistence.findWalletTypes(memberId),
			parsedWalletType
		);
		PointLedgerQueryPage page = persistence.find(
			new PointLedgerQueryCriteria(
				memberId,
				parsedWalletType,
				parsedTransactionType,
				parsedCursor,
				parsedSize
			)
		);
		return new PointLedgerQueryResult(
			toItems(page.rows()),
			page.nextCursor(),
			page.hasNext()
		);
	}

	private List<Item> toItems(List<PointLedgerQueryRow> rows) {
		return rows.stream()
			.map(row -> new Item(
				row.ledgerId(),
				row.walletType(),
				row.transactionType(),
				row.availableChange(),
				row.heldChange(),
				row.availableBalanceAfter(),
				row.heldBalanceAfter(),
				PointLedgerDescriptionPolicy.describe(
					row.transactionType(),
					row.referenceType()
				),
				row.referenceType(),
				row.referenceId(),
				row.createdAt()
			))
			.toList();
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(UNAUTHORIZED);
		}
	}

	private WalletType parseWalletType(String walletType) {
		if (walletType == null || walletType.isBlank()) {
			return null;
		}
		try {
			return WalletType.valueOf(
				walletType.trim().toUpperCase(Locale.ROOT)
			);
		} catch (IllegalArgumentException exception) {
			throw new PointLedgerQueryException(
				INVALID_LEDGER_FILTER,
				exception
			);
		}
	}

	private PointLedgerTransactionType parseTransactionType(
		String transactionType
	) {
		try {
			return PointLedgerTransactionType.from(transactionType);
		} catch (IllegalArgumentException exception) {
			throw new PointLedgerQueryException(
				INVALID_LEDGER_FILTER,
				exception
			);
		}
	}

	private PointLedgerCursor parseCursor(String cursor) {
		if (cursor == null) {
			return null;
		}
		if (cursor.isBlank()) {
			throw error(INVALID_CURSOR);
		}
		try {
			return PointLedgerCursor.decode(cursor.trim());
		} catch (IllegalArgumentException exception) {
			throw new PointLedgerQueryException(
				INVALID_CURSOR,
				exception
			);
		}
	}

	private int parseSize(Integer size) {
		if (size == null) {
			return DEFAULT_SIZE;
		}
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw error(INVALID_LEDGER_FILTER);
		}
		return size;
	}

	private void validateRequiredWallets(
		Set<WalletType> existingWallets,
		WalletType requestedWallet
	) {
		Set<WalletType> required = requestedWallet == null
			? EnumSet.allOf(WalletType.class)
			: EnumSet.of(requestedWallet);
		if (!existingWallets.containsAll(required)) {
			throw error(WALLET_NOT_FOUND);
		}
	}

	private PointLedgerQueryException error(
		PointLedgerQueryErrorCode errorCode
	) {
		return new PointLedgerQueryException(errorCode);
	}
}
