/**
 * 지갑 기능에서 실시간 미디어 연동이 필요한 경우, 상위 계층의 요청을 OpenVidu API
 * 호출로 변환하고 그 결과를 내부에서 사용할 수 있는 형태로 반환하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>OpenVidu 세션 생성 및 종료</li>
 *     <li>Connection Token 발급</li>
 *     <li>참가자 연결 정보 조회</li>
 *     <li>OpenVidu 설정값 바인딩과 SDK 호출</li>
 *     <li>OpenVidu 응답을 내부 객체로 변환하는 코드</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>지갑 기능의 권한 및 가능 여부 판정</li>
 *     <li>도메인 상태 변경</li>
 *     <li>Controller</li>
 *     <li>전체 유스케이스의 실행 흐름</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>Application 계층이 요청한 미디어 작업에 대해 실제 OpenVidu API 호출만 담당합니다.</li>
 *     <li>OpenVidu 전용 타입과 예외가 Domain 계층으로 노출되지 않도록 변환합니다.</li>
 *     <li>미디어 연동이 필요하지 않은 기능에서는 구현 클래스를 억지로 추가하지 않습니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code WalletOpenViduAdapter}</li>
 *     <li>{@code OpenViduProperties}</li>
 *     <li>{@code OpenViduException}</li>
 * </ul>
 */
package com.ssafy.bidlive.wallet.infrastructure.openvidu;
