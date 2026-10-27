package com.ssafy.bidlive.common.config;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * `ADMIN_MEMBER_IDS`에 적힌 회원을 부팅 시 `ADMIN`으로 올린다.
 *
 * <p><b>시드로 관리자 계정을 만들 수는 없다.</b> 로그인 경로가 카카오 OAuth뿐이고
 * `social_account`에 실제 `provider_user_id`가 붙어야 들어올 수 있다 — 비밀번호 개념이 없다.
 * 그래서 「계정 생성」이 아니라 <b>「이미 가입한 회원의 승격」</b>만 가능하다.
 *
 * <p>Flyway 마이그레이션에 `UPDATE ... WHERE member_id = N`을 넣지 않는 이유: 특정 개인의 id가
 * git 히스토리에 영구히 남고, <b>로컬 DB의 `member_id`는 사람마다 달라서 다른 사람 환경에서
 * 엉뚱한 회원이 ADMIN이 된다.</b>
 *
 * <p>⚠️ <b>승격 전용이다 — 회수는 하지 않는다.</b> `ADMIN_MEMBER_IDS`에서 id를 빼도 그 회원은
 * `ADMIN`으로 남는다. 되돌리려면 `UPDATE member SET role = 'USER' WHERE member_id = ?`를 직접
 * 쳐야 한다. 「env가 곧 권한」이 아니라 「env가 권한을 주기만 한다」로 읽어라 — 목록에 없는
 * `ADMIN`을 강등하는 문장을 넣으면 손으로 올려 둔 계정이 배포 때마다 조용히 내려간다.
 */
@Component
public class AdminMemberRoleInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminMemberRoleInitializer.class);

	private static final String PROMOTE = """
		UPDATE member
		SET role = 'ADMIN'
		WHERE member_id IN (:memberIds)
			AND role <> 'ADMIN'
		""";

	private final EntityManager entityManager;
	private final String configuredIds;

	public AdminMemberRoleInitializer(
		EntityManager entityManager,
		@Value("${ADMIN_MEMBER_IDS:}") String configuredIds
	) {
		this.entityManager = entityManager;
		this.configuredIds = configuredIds;
	}

	/**
	 * ⚠️ `@Transactional`은 <b>반드시 여기</b>에 있어야 한다. 스프링이 호출하는 진입점이 `run()`이라
	 * 트랜잭션 인터셉터도 이 메서드에만 걸린다 — `promote()`에만 달면 `this.promote()` 자기 호출이
	 * 프록시를 건너뛰어 트랜잭션이 없고, 공유 `EntityManager`의 `executeUpdate()`가
	 * `TransactionRequiredException`으로 부팅을 통째로 깬다.
	 */
	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		promote();
	}

	/**
	 * 값이 비었거나 쓸 수 있는 id가 없으면 아무것도 하지 않는다. 재실행에 안전하다.
	 *
	 * <p><b>결과를 반드시 로그로 남긴다.</b> 승격이 조용히 0건이면 운영자가 겪는 증상은
	 * 「승격했는데 403」인데, 그건 <b>재로그인을 안 했을 때와 똑같이 생겼다</b>(JWT의 `role`
	 * 클레임으로 판정하므로 DB만 바꿔서는 기존 토큰이 `USER`다). 로그가 없으면 오타로 id가
	 * 버려진 것을 재로그인 문제로 오진하고 로그아웃만 반복하게 된다.
	 * 갱신 행 수가 있으면 「id가 DB에 없음」·「이미 ADMIN」·「오타로 버려짐」이 바로 갈린다.
	 */
	public void promote() {
		List<Long> memberIds = parse(configuredIds);
		if (memberIds.isEmpty()) {
			if (configuredIds != null && !configuredIds.isBlank()) {
				log.warn(
					"ADMIN_MEMBER_IDS에 쓸 수 있는 회원 id가 없어 승격을 건너뛴다: {}",
					configuredIds
				);
			}
			return;
		}
		int promoted = entityManager.createNativeQuery(PROMOTE)
			.setParameter("memberIds", memberIds)
			.executeUpdate();
		log.info(
			"ADMIN 승격 대상 {}건 중 {}건 갱신 — 나머지는 이미 ADMIN이거나 DB에 없는 id다. {}",
			memberIds.size(),
			promoted,
			memberIds
		);
	}

	/** 숫자가 아니거나 0 이하인 항목은 버린다 — 오타 하나로 부팅을 막지 않는다. 버린 사실은 로그로 남는다. */
	private static List<Long> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.map(AdminMemberRoleInitializer::toPositiveLong)
			.filter(value -> value != null)
			.toList();
	}

	private static Long toPositiveLong(String value) {
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
