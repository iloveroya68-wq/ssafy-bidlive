package com.ssafy.bidlive.product.infrastructure.gms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 검열 판정 임계값 설정이며 값 검증은 도메인 정책이 담당합니다. */
@ConfigurationProperties(prefix = "ai.inspection")
public class InspectionPolicyProperties {

	/** 이 값 이상이면 REJECTED다. */
	private double rejectThreshold = 0.8;

	/** 이 값 이상이고 거부 임계값 미만이면 NEEDS_REVIEW다. */
	private double reviewThreshold = 0.4;

	public double getRejectThreshold() {
		return rejectThreshold;
	}

	public void setRejectThreshold(double rejectThreshold) {
		this.rejectThreshold = rejectThreshold;
	}

	public double getReviewThreshold() {
		return reviewThreshold;
	}

	public void setReviewThreshold(double reviewThreshold) {
		this.reviewThreshold = reviewThreshold;
	}
}
