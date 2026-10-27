package com.ssafy.bidlive.product.application.port.out;

/**
 * 상품 텍스트와 이미지의 위험도 판정을 외부 AI 게이트웨이에 위임하는 포트입니다.
 *
 * <p>두 메서드 모두 판정을 만들지 못하면 {@link ContentModerationException}을 던진다. 빈 판정을
 * 정상 응답으로 돌려주지 않는 이유는, 호출 실패가 통과로 해석될 여지를 남기지 않기 위해서다.
 */
public interface ContentModerationGateway {

	ModerationJudgement inspectText(String text);

	ModerationJudgement inspectImage(String imageUrl);
}
