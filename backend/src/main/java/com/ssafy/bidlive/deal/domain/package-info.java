/**
 * 특정 프레임워크나 외부 기술이 아니라 BidLive 거래 기능의 핵심 개념, 상태, 행위와
 * 비즈니스 규칙 자체를 표현하고 보호하는 도메인 계층 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>핵심 Entity, Value Object 및 Enum</li>
 *     <li>도메인 정책과 상태 전이 규칙</li>
 *     <li>계산기 및 판정 로직</li>
 *     <li>도메인 이벤트</li>
 *     <li>도메인 관점의 Repository 추상화가 필요한 경우 해당 인터페이스</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>Controller와 Request/Response DTO</li>
 *     <li>{@code RedisTemplate} 또는 OpenVidu SDK</li>
 *     <li>외부 API 호출 코드</li>
 *     <li>JSON 또는 HTTP 처리 코드</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>거래 규칙은 기술 구현과 분리하여 가능한 한 순수 Java로 표현합니다.</li>
 *     <li>{@code api}와 {@code infrastructure}에 의존하지 않습니다.</li>
 *     <li>상위 계층이 Domain 객체의 공개 행위를 통해 상태를 변경하도록 합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code Deal}</li>
 *     <li>{@code DealStatus}</li>
 *     <li>{@code DealPolicy}</li>
 *     <li>{@code DealEvent}</li>
 * </ul>
 */
package com.ssafy.bidlive.deal.domain;
