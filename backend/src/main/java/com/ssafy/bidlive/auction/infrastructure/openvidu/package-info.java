/**
 * Application Service가 요청한 방송 세션 작업을 OpenVidu 미디어 서버 API 호출로 변환하고,
 * 그 결과를 애플리케이션 내부에서 사용할 수 있는 형태로 반환하는 연동 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>OpenVidu 세션 생성 및 종료</li>
 *     <li>Connection Token 발급</li>
 *     <li>참가자 연결 정보 조회</li>
 *     <li>OpenVidu 설정값 바인딩</li>
 *     <li>OpenVidu SDK 호출</li>
 *     <li>OpenVidu 응답을 내부 객체로 변환하는 코드</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>경매 시작 가능 여부 판정과 판매자 권한 검증</li>
 *     <li>경매 상태 변경</li>
 *     <li>Controller</li>
 *     <li>전체 방송 시작 유스케이스의 실행 흐름</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>Application 계층의 “방송 세션 생성” 요청에 대해 실제 OpenVidu API 호출만 담당합니다.</li>
 *     <li>OpenVidu 전용 타입과 예외가 Domain 계층으로 노출되지 않도록 변환합니다.</li>
 *     <li>경매 정책을 판단하지 않고 상위 계층이 결정한 명령을 수행합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code OpenViduClient}</li>
 *     <li>{@code OpenViduSessionAdapter}</li>
 *     <li>{@code OpenViduProperties}</li>
 *     <li>{@code OpenViduException}</li>
 * </ul>
 */
package com.ssafy.bidlive.auction.infrastructure.openvidu;
