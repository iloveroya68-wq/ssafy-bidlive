/**
 * 도메인 또는 애플리케이션 사이에서 발생한 사건을 전달할 때 공통으로 사용하는
 * 이벤트 계약, 발행 추상화와 메타데이터 기반 코드를 관리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>공통 도메인 이벤트 인터페이스</li>
 *     <li>이벤트 발행 추상화</li>
 *     <li>이벤트 식별자와 발생 시각</li>
 *     <li>공통 이벤트 메타데이터</li>
 *     <li>특정 도메인에 종속되지 않는 이벤트 처리 기반 코드</li>
 *     <li>도메인 payload 타입을 알지 않는 제네릭 이벤트 전송 envelope</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>모든 도메인의 구체적인 이벤트를 한곳에 모은 코드</li>
 *     <li>경매 종료 또는 입찰 완료 이벤트의 세부 필드</li>
 *     <li>이벤트를 소비하는 특정 도메인의 비즈니스 로직</li>
 *     <li>Kafka, Redis Pub/Sub 등 메시지 브로커별 구현 코드</li>
 *     <li>도메인별 WebSocket 전송 DTO와 이벤트 종류별 {@code data} 필드</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>구체적인 이벤트는 사건이 발생하는 도메인에 배치합니다.</li>
 *     <li>이벤트 처리 로직은 소비하는 도메인 또는 Application 계층에 둡니다.</li>
 *     <li>{@code common.event}는 특정 도메인 Entity나 Service에 의존하지 않습니다.</li>
 *     <li>메시징 기술 구현은 해당 도메인의 {@code infrastructure} 패키지에 둡니다.</li>
 * </ul>
 *
 * <h2>구체적 이벤트 배치 예시</h2>
 * <ul>
 *     <li>{@code AuctionEndedEvent}는 {@code auction.domain.event}에 배치할 수 있습니다.</li>
 *     <li>{@code BidSubmittedEvent}는 {@code bid.domain.event}에 배치할 수 있습니다.</li>
 *     <li>{@code PaymentCompletedEvent}는 관련 결제 또는 지갑 도메인에 배치합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code DomainEvent}</li>
 *     <li>{@code EventPublisher}</li>
 *     <li>{@code EventMetadata}</li>
 *     <li>{@code EventId}</li>
 *     <li>{@code WsEvent}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.event;
