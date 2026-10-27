package com.ssafy.bidlive.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import com.ssafy.bidlive.auth.domain.OAuthMemberProvisioningErrorCode;
import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import com.ssafy.bidlive.auth.infrastructure.persistence.SocialAccountRepository;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import com.ssafy.bidlive.wallet.application.WalletPersistence;
import com.ssafy.bidlive.wallet.domain.Wallet;
import com.ssafy.bidlive.wallet.domain.WalletType;
import com.ssafy.bidlive.wallet.infrastructure.persistence.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** OAuth 신규 가입의 지갑·가입 보너스 생성, 재사용과 롤백을 검증한다. */
@SpringBootTest
@Transactional
class OAuthMemberProvisioningServiceIntegrationTest {

	@Autowired
	private OAuthMemberProvisioningService service;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private WalletPersistence walletPersistence;

	@Test
	void createsMemberSocialProfileAndTwoWalletsInOneLogin() {
		OAuthMemberProvisioningService.OAuthMemberResult result =
			service.findOrCreate(profile("kakao-user-100"));

		assertThat(result.isNewMember()).isTrue();
		assertThat(result.onboardingRequired()).isTrue();
		assertThat(memberRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);

		Member member = memberRepository.findById(result.memberId()).orElseThrow();
		assertThat(member.getNickname()).isNull();

		SocialAccount socialAccount = socialAccountRepository
			.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-user-100")
			.orElseThrow();
		assertThat(socialAccount.getMemberId()).isEqualTo(result.memberId());
		assertThat(socialAccount.getEmail()).isEqualTo("member@example.com");
		assertThat(socialAccount.getProviderNickname()).isEqualTo("카카오 닉네임");
		assertThat(socialAccount.getProfileImageUrl())
			.isEqualTo("https://example.com/profile.png");

		List<Wallet> wallets = walletRepository.findAllByMemberId(result.memberId());
		assertThat(wallets)
			.extracting(Wallet::getWalletType)
			.containsExactlyInAnyOrder(WalletType.TRADE, WalletType.BETTING);
		assertThat(wallets)
			.extracting(Wallet::getMemberId)
			.containsOnly(result.memberId());
		assertThat(walletAvailableBalance(result.memberId()))
			.isEqualTo(1_000L);
		assertThat(signupGrantCount(result.memberId())).isEqualTo(1);
		assertThat(signupLedger(result.memberId())).containsExactly(
			"GRANT",
			1_000L,
			0L,
			1_000L,
			0L
		);
	}

	@Test
	void reusesExistingMemberForSameProviderUserId() {
		OAuthMemberProvisioningService.OAuthMemberResult first =
			service.findOrCreate(profile("kakao-user-200"));
		OAuthMemberProvisioningService.OAuthMemberResult second =
			service.findOrCreate(profile("kakao-user-200"));

		assertThat(first.isNewMember()).isTrue();
		assertThat(second.isNewMember()).isFalse();
		assertThat(second.onboardingRequired()).isTrue();
		assertThat(second.memberId()).isEqualTo(first.memberId());
		assertThat(memberRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(walletRepository.count()).isEqualTo(2);
		assertThat(walletAvailableBalance(first.memberId()))
			.isEqualTo(1_000L);
		assertThat(signupGrantCount(first.memberId())).isEqualTo(1);
	}

	@Test
	void createsMemberWhenOptionalProviderProfileIsAbsent() {
		OAuthMemberProfile profile = new OAuthMemberProfile(
			SocialProvider.KAKAO,
			"kakao-user-without-profile",
			null,
			null,
			null
		);

		OAuthMemberProvisioningService.OAuthMemberResult result =
			service.findOrCreate(profile);

		assertThat(result.isNewMember()).isTrue();
		assertThat(result.onboardingRequired()).isTrue();
		SocialAccount socialAccount = socialAccountRepository
			.findByProviderAndProviderUserId(
				SocialProvider.KAKAO,
				"kakao-user-without-profile"
			)
			.orElseThrow();
		assertThat(socialAccount.getEmail()).isNull();
		assertThat(socialAccount.getProviderNickname()).isNull();
		assertThat(socialAccount.getProfileImageUrl()).isNull();
	}

	@Test
	void rejectsProfileWithoutRequiredProviderIdentity() {
		OAuthMemberProfile profile = new OAuthMemberProfile(
			SocialProvider.KAKAO,
			" ",
			"member@example.com",
			"카카오 닉네임",
			null
		);

		assertThatThrownBy(() -> service.findOrCreate(profile))
			.isInstanceOfSatisfying(
				OAuthMemberProvisioningException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(OAuthMemberProvisioningErrorCode.OAUTH_PROFILE_INSUFFICIENT)
			);
		assertThat(memberRepository.count()).isZero();
		assertThat(socialAccountRepository.count()).isZero();
		assertThat(walletRepository.count()).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void rollsBackMemberAndSocialAccountWhenWalletCreationFails() {
		doThrow(new IllegalStateException("wallet persistence failure"))
			.when(walletPersistence)
			.saveAllAndFlush(anyList());

		assertThatThrownBy(
			() -> service.findOrCreate(profile("kakao-user-rollback"))
		).isInstanceOfSatisfying(
			OAuthMemberProvisioningException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(OAuthMemberProvisioningErrorCode.MEMBER_CREATION_FAILED)
		);

		assertThat(memberRepository.count()).isZero();
		assertThat(socialAccountRepository.count()).isZero();
		assertThat(walletRepository.count()).isZero();
	}

	private OAuthMemberProfile profile(String providerUserId) {
		return new OAuthMemberProfile(
			SocialProvider.KAKAO,
			providerUserId,
			"member@example.com",
			"카카오 닉네임",
			"https://example.com/profile.png"
		);
	}

	private long walletAvailableBalance(Long memberId) {
		Long balance = jdbcTemplate.queryForObject(
			"""
				SELECT available_balance
				FROM wallet
				WHERE member_id = ?
					AND wallet_type = 'BETTING'
				""",
				Long.class,
				memberId
		);
		return balance == null ? 0L : balance;
	}

	private int signupGrantCount(Long memberId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM betting_point_grant
				WHERE member_id = ?
					AND grant_type = 'SIGNUP'
					AND source_id = ?
				""",
				Integer.class,
				memberId,
				"MEMBER:" + memberId
		);
		return count == null ? 0 : count;
	}

	private Object[] signupLedger(Long memberId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT
					ledger.transaction_type,
					ledger.available_delta,
					ledger.held_delta,
					ledger.available_balance_after,
					ledger.held_balance_after
				FROM betting_point_grant grant_history
				JOIN betting_point_ledger ledger
					ON ledger.betting_point_ledger_id = grant_history.ledger_id
				WHERE grant_history.member_id = ?
					AND grant_history.grant_type = 'SIGNUP'
					AND grant_history.source_id = ?
				""",
				(resultSet, rowNumber) -> new Object[] {
					resultSet.getString("transaction_type"),
					resultSet.getLong("available_delta"),
					resultSet.getLong("held_delta"),
					resultSet.getLong("available_balance_after"),
					resultSet.getLong("held_balance_after")
				},
				memberId,
				"MEMBER:" + memberId
		);
	}
}
