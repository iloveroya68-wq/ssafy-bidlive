/**
 * 사용자의 하나의 기능 또는 유스케이스를 실행하면서 도메인 객체, 저장소 및 외부 기술의
 * 호출 순서와 트랜잭션 경계를 조율하는 애플리케이션 계층 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>Application Service</li>
 *     <li>유스케이스 단위의 트랜잭션 경계</li>
 *     <li>Repository를 통한 데이터 조회 및 저장 흐름</li>
 *     <li>Domain 객체의 메서드 호출</li>
 *     <li>여러 도메인 또는 외부 기술의 호출 순서 조율</li>
 *     <li>도메인 이벤트 발행</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>HTTP 요청·응답 처리</li>
 *     <li>{@code RedisTemplate} 또는 OpenVidu SDK 직접 사용</li>
 *     <li>JPA 세부 구현</li>
 *     <li>Domain 계층에 있어야 할 핵심 규칙을 반복 구현한 조건문</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>{@code application}은 {@code domain}을 사용합니다.</li>
 *     <li>외부 기술이 필요하면 정의된 포트 또는 {@code infrastructure}가 제공하는 구현체를 통해 사용합니다.</li>
 *     <li>{@code api}에 의존하지 않으며 HTTP나 WebSocket 세부사항을 알지 않도록 합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code AuctionCommandService}</li>
 *     <li>{@code AuctionQueryService}</li>
 *     <li>{@code AuctionSettlementService}</li>
 *     <li>{@code BidCommandService}</li>
 * </ul>
 */
package com.ssafy.bidlive.auction.application;
