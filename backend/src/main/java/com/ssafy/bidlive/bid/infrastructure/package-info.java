/**
 * 입찰 기능에서 DB, Redis, OpenVidu 및 외부 API 같은 기술과 실제로 연결되는 코드를
 * 관리하며, 기술 교체의 영향을 이 계층 안으로 제한하는 인프라 계층 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>영속성 및 Redis 구현</li>
 *     <li>OpenVidu와 외부 API 연동 클라이언트</li>
 *     <li>기술별 설정 및 변환 객체</li>
 *     <li>Domain 또는 Application 계층에서 정의한 인터페이스의 구현체</li>
 *     <li>{@code infrastructure.persistence}, {@code infrastructure.redis}, {@code infrastructure.openvidu} 하위 패키지</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>Controller와 HTTP Request/Response DTO</li>
 *     <li>핵심 비즈니스 규칙 및 도메인 상태 전이 규칙</li>
 *     <li>유스케이스 전체 흐름</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>상위 계층이 정의한 추상화를 구현하되 기술 세부사항이 상위 계층으로 새어 나가지 않도록 합니다.</li>
 *     <li>사용 기술이 바뀔 때 해당 구현만 함께 교체할 수 있도록 구성합니다.</li>
 *     <li>{@code api}를 호출하거나 유스케이스의 실행 순서를 직접 결정하지 않습니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code BidPersistenceAdapter}</li>
 *     <li>{@code BidRedisRepository}</li>
 *     <li>{@code BidOpenViduAdapter}</li>
 * </ul>
 */
package com.ssafy.bidlive.bid.infrastructure;
