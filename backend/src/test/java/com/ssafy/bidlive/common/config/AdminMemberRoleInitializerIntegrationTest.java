package com.ssafy.bidlive.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `ADMIN_MEMBER_IDS`가 <b>실제로 채워진 채</b> 컨텍스트를 띄우고, 승격이 DB에 반영되는지 본다.
 *
 * <p>단위 테스트는 `promote()`를 직접 불러 트랜잭션을 통과시켜 버려 — 값이 비었을 때만 부팅하는
 * 상태를 전부 초록으로 통과시켰다. 여기서는 <b>클래스에 `@Transactional`을 걸지 않는다.</b>
 * 걸면 테스트 트랜잭션이 주변에 깔려 `run()`에 트랜잭션이 없어도 통과해 회귀를 못 잡는다.
 */
@SpringBootTest(properties = "ADMIN_MEMBER_IDS=" + AdminMemberRoleInitializerIntegrationTest.MEMBER_ID)
class AdminMemberRoleInitializerIntegrationTest {

	static final String MEMBER_ID = "9301";

	@Autowired
	private AdminMemberRoleInitializer initializer;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertMember() {
		jdbcTemplate.update("DELETE FROM member WHERE member_id = ?", Long.valueOf(MEMBER_ID));
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (?, '승격대상9301', 'USER', 'ACTIVE')
			""", Long.valueOf(MEMBER_ID));
	}

	@AfterEach
	void deleteMember() {
		jdbcTemplate.update("DELETE FROM member WHERE member_id = ?", Long.valueOf(MEMBER_ID));
	}

	/**
	 * 스프링이 부팅 때 호출하는 것과 같은 진입점(프록시의 `run()`)을 부른다 — 트랜잭션이 빠지면
	 * 여기서 `TransactionRequiredException`이 난다. 부팅 시점에는 이 회원이 아직 없어 아무 일도
	 * 일어나지 않으므로 승격 여부는 이 호출로 확인한다.
	 */
	@Test
	void promotesConfiguredMemberToAdmin() {
		initializer.run(null);

		assertThat(role()).isEqualTo("ADMIN");
	}

	@Test
	void isIdempotent() {
		initializer.run(null);
		initializer.run(null);

		assertThat(role()).isEqualTo("ADMIN");
	}

	private String role() {
		return jdbcTemplate.queryForObject(
			"SELECT role FROM member WHERE member_id = ?",
			String.class,
			Long.valueOf(MEMBER_ID)
		);
	}
}
