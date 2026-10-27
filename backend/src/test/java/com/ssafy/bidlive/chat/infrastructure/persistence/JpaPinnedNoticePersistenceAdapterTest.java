package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 고정 공지 native persistence가 조회 결과와 변경 건수를 올바르게 변환하는지 검증합니다. */
class JpaPinnedNoticePersistenceAdapterTest {

	private static final ChatMessageId MESSAGE_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ABC"
	);

	private EntityManager entityManager;
	private Query query;
	private JpaPinnedNoticePersistenceAdapter adapter;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		query = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(query);
		adapter = new JpaPinnedNoticePersistenceAdapter(entityManager);
	}

	@Test
	void mapsPinnedMessageProjection() {
		when(query.getResultList()).thenReturn(Collections.singletonList(
			new Object[]{MESSAGE_ID.value(), "배송비 포함입니다."}
		));

		assertThat(adapter.findPinned(42L))
			.hasValueSatisfying(notice -> {
				assertThat(notice.messageId()).isEqualTo(MESSAGE_ID.value());
				assertThat(notice.content()).isEqualTo("배송비 포함입니다.");
			});
	}

	@Test
	void returnsEmptyWhenNoPinnedMessageExists() {
		when(query.getResultList()).thenReturn(List.of());

		assertThat(adapter.findPinned(42L)).isEmpty();
	}

	@Test
	void replacesPinnedMessageOnlyWhenTargetUpdateSucceeds() {
		when(query.executeUpdate()).thenReturn(1, 1);

		assertThat(adapter.replacePinned(42L, MESSAGE_ID)).isTrue();
		verify(query, org.mockito.Mockito.times(2)).executeUpdate();
		verify(query, org.mockito.Mockito.times(2))
			.setParameter("messageId", MESSAGE_ID.value());
	}

	@Test
	void reportsWhetherUnpinChangedState() {
		when(query.executeUpdate()).thenReturn(0);

		assertThat(adapter.clearPinned(42L)).isFalse();
	}

	@Test
	void acquiresTransactionScopedAuctionLock() {
		when(query.getSingleResult()).thenReturn(null);

		adapter.lockAuction(42L);

		verify(query).getSingleResult();
	}
}
