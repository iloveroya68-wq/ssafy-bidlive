/**
 * 외부 클라이언트의 HTTP 및 WebSocket/STOMP 요청을 받아 채팅 유스케이스로 전달하고,
 * 처리 결과를 적절한 프로토콜 응답으로 반환하는 진입점 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>REST Controller</li>
 *     <li>WebSocket/STOMP Controller</li>
 *     <li>HTTP 요청값 검증과 인증 사용자 정보 전달</li>
 *     <li>Application Service 호출</li>
 *     <li>HTTP 상태 코드와 응답 반환</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>채팅의 핵심 비즈니스 규칙과 복잡한 데이터 가공</li>
 *     <li>DB 직접 조회 및 저장 코드</li>
 *     <li>Redis 또는 OpenVidu 직접 호출 코드</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>{@code api}는 유스케이스 실행을 위해 {@code application}을 호출할 수 있습니다.</li>
 *     <li>{@code infrastructure}를 직접 호출하지 않으며 기술 세부사항을 알지 않도록 합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code ChatController}</li>
 *     <li>{@code ChatWebSocketController}</li>
 * </ul>
 */
package com.ssafy.bidlive.chat.api;
