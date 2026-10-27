package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.chat.application.PinnedNoticePersistence;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** chat_message.is_pinned를 방별 단일 고정 공지 source of truth로 관리합니다. */
@Repository
public class JpaPinnedNoticePersistenceAdapter
	implements PinnedNoticePersistence {

	private static final String LOCK_AUCTION_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(CONCAT('bidlive:chat:', :auctionId, ':pinned-notice'), 0)
		)
		""";

	private static final String FIND_MESSAGE_QUERY = """
		SELECT message_id, content
		FROM chat_message
		WHERE auction_room_id = :auctionId
			AND message_id = :messageId
			AND is_deleted = FALSE
		""";

	private static final String FIND_PINNED_QUERY = """
		SELECT message_id, content
		FROM chat_message
		WHERE auction_room_id = :auctionId
			AND is_pinned = TRUE
			AND is_deleted = FALSE
		ORDER BY created_at DESC, message_id DESC
		LIMIT 1
		""";

	private static final String CLEAR_OTHER_PINNED_QUERY = """
		UPDATE chat_message
		SET is_pinned = FALSE
		WHERE auction_room_id = :auctionId
			AND is_pinned = TRUE
			AND message_id <> :messageId
		""";

	private static final String PIN_MESSAGE_QUERY = """
		UPDATE chat_message
		SET is_pinned = TRUE
		WHERE auction_room_id = :auctionId
			AND message_id = :messageId
			AND is_deleted = FALSE
		""";

	private static final String CLEAR_PINNED_QUERY = """
		UPDATE chat_message
		SET is_pinned = FALSE
		WHERE auction_room_id = :auctionId
			AND is_pinned = TRUE
		""";

	private final EntityManager entityManager;

	public JpaPinnedNoticePersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockAuction(long auctionId) {
		entityManager.createNativeQuery(LOCK_AUCTION_QUERY)
			.setParameter("auctionId", auctionId)
			.getSingleResult();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PinnedNotice> findMessageForPin(
		long auctionId,
		ChatMessageId messageId
	) {
		return singleNotice(entityManager.createNativeQuery(FIND_MESSAGE_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("messageId", messageId.value()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PinnedNotice> findPinned(long auctionId) {
		return singleNotice(entityManager.createNativeQuery(FIND_PINNED_QUERY)
			.setParameter("auctionId", auctionId));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean replacePinned(long auctionId, ChatMessageId messageId) {
		entityManager.createNativeQuery(CLEAR_OTHER_PINNED_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("messageId", messageId.value())
			.executeUpdate();
		return entityManager.createNativeQuery(PIN_MESSAGE_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("messageId", messageId.value())
			.executeUpdate() == 1;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean clearPinned(long auctionId) {
		return entityManager.createNativeQuery(CLEAR_PINNED_QUERY)
			.setParameter("auctionId", auctionId)
			.executeUpdate() > 0;
	}

	private static Optional<PinnedNotice> singleNotice(Query query) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = rows.getFirst();
		return Optional.of(new PinnedNotice(
			(String) row[0],
			(String) row[1]
		));
	}
}
