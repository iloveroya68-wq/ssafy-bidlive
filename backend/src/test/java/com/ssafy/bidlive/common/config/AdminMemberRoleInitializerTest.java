package com.ssafy.bidlive.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * env로 지정한 회원만 ADMIN으로 올린다.
 *
 * <p>여기가 새면 아무나 경매를 강제 종료할 수 있게 된다 — 이 레포에서 role을 올리는 유일한 경로다.
 */
class AdminMemberRoleInitializerTest {

	private EntityManager entityManager;
	private Query query;

	@BeforeEach
	void setUp() {
		entityManager = mock(EntityManager.class);
		query = mock(Query.class);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), any())).thenReturn(query);
	}

	@Test
	void promotesConfiguredMembers() {
		new AdminMemberRoleInitializer(entityManager, "42,57").promote();

		// ⚠️ Mockito는 **원시값과 매처를 섞을 수 없다.** 하나라도 매처를 쓰면 전부 매처여야 한다 —
		// 그래서 이름 자리에도 `eq(...)`를 쓴다.
		ArgumentCaptor<Object> ids = ArgumentCaptor.forClass(Object.class);
		verify(query).setParameter(eq("memberIds"), ids.capture());
		assertThat(ids.getValue()).isEqualTo(List.of(42L, 57L));
		verify(query).executeUpdate();
	}

	@Test
	void ignoresBlankAndWhitespaceEntries() {
		// `.env`에 `ADMIN_MEMBER_IDS=42, ,57 `처럼 들어와도 죽지 않아야 한다.
		new AdminMemberRoleInitializer(entityManager, " 42 , ,57 ").promote();

		ArgumentCaptor<Object> ids = ArgumentCaptor.forClass(Object.class);
		verify(query).setParameter(eq("memberIds"), ids.capture());
		assertThat(ids.getValue()).isEqualTo(List.of(42L, 57L));
	}

	@Test
	void doesNothingWhenUnset() {
		new AdminMemberRoleInitializer(entityManager, "").promote();

		verify(entityManager, never()).createNativeQuery(anyString());
	}

	@Test
	void doesNothingWhenNoValidId() {
		// 숫자가 아니거나 0 이하면 조용히 버린다 — 부팅을 막지 않는다.
		new AdminMemberRoleInitializer(entityManager, "abc, 0, -3").promote();

		verify(entityManager, never()).createNativeQuery(anyString());
	}
}
