/**
 * 미디어 유스케이스가 외부와 주고받는 계약을 방향별 하위 패키지로 묶는 경계 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>{@code port.out} 등 방향별 포트 하위 패키지</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>포트는 인터페이스만 정의하고 구현은 {@code infrastructure}에 둡니다.</li>
 *     <li>포트 선언에 OpenVidu, Redis, JPA 등 기술 타입을 노출하지 않습니다.</li>
 * </ul>
 */
package com.ssafy.bidlive.media.application.port;
