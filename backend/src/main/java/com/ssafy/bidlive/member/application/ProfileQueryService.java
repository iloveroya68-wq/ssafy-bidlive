package com.ssafy.bidlive.member.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ssafy.bidlive.deal.application.MemberDealActivity;
import com.ssafy.bidlive.deal.application.MemberDealActivityQuery;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.member.domain.ProfileQueryErrorCode;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivity;
import com.ssafy.bidlive.prediction.application.MemberPredictionActivityQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 회원 상태와 도메인별 활동 통계를 조합해 마이페이지 프로필을 조회한다. */
@Service
public class ProfileQueryService {

	private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

	private final MemberPersistence memberPersistence;
	private final OnboardingStatusService onboardingStatusService;
	private final MemberDealActivityQuery dealActivityQuery;
	private final MemberPredictionActivityQuery predictionActivityQuery;

	public ProfileQueryService(
		MemberPersistence memberPersistence,
		OnboardingStatusService onboardingStatusService,
		MemberDealActivityQuery dealActivityQuery,
		MemberPredictionActivityQuery predictionActivityQuery
	) {
		this.memberPersistence = memberPersistence;
		this.onboardingStatusService = onboardingStatusService;
		this.dealActivityQuery = dealActivityQuery;
		this.predictionActivityQuery = predictionActivityQuery;
	}

	@Transactional(readOnly = true)
	public ProfileQueryResult query(Long memberId) {
		validateMemberId(memberId);
		Member member = memberPersistence.findById(memberId)
			.orElseThrow(() -> error(ProfileQueryErrorCode.MEMBER_NOT_FOUND));
		if (member.getStatus() == MemberStatus.WITHDRAWN) {
			throw error(ProfileQueryErrorCode.MEMBER_NOT_AVAILABLE);
		}

		try {
			if (onboardingStatusService.isOnboardingRequired(member)) {
				throw error(ProfileQueryErrorCode.ONBOARDING_NOT_COMPLETED);
			}

			MemberDealActivity dealActivity = dealActivityQuery.query(memberId);
			MemberPredictionActivity predictionActivity =
				predictionActivityQuery.query(memberId);
			ProfileActivitySummary activitySummary = new ProfileActivitySummary(
				dealActivity.salesCount(),
				dealActivity.purchaseCount(),
				predictionActivity.participationCount(),
				predictionActivity.hitCount(),
				hitRate(predictionActivity)
			);

			return new ProfileQueryResult(
				member.getId(),
				member.getNickname(),
				member.getStatus(),
				member.getRole(),
				activitySummary
			);
		} catch (ProfileQueryException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new ProfileQueryException(
				ProfileQueryErrorCode.PROFILE_SUMMARY_QUERY_FAILED,
				exception
			);
		}
	}

	private double hitRate(MemberPredictionActivity activity) {
		if (activity.participationCount() == 0) {
			return 0.0;
		}
		return BigDecimal.valueOf(activity.hitCount())
			.multiply(PERCENT)
			.divide(
				BigDecimal.valueOf(activity.participationCount()),
				2,
				RoundingMode.HALF_UP
			)
			.doubleValue();
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(ProfileQueryErrorCode.UNAUTHORIZED);
		}
	}

	private ProfileQueryException error(ProfileQueryErrorCode errorCode) {
		return new ProfileQueryException(errorCode);
	}
}
