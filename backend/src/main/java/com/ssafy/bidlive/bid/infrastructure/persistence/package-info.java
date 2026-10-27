/**
 * PostgreSQL과 JPA를 이용하여 입찰 데이터를 저장하고 조회하며, 영속성 기술의
 * 세부 구현을 Domain 및 Application 계층으로부터 분리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>Spring Data JPA Repository</li>
 *     <li>QueryDSL Repository와 복잡한 조회 구현체</li>
 *     <li>Persistence Adapter</li>
 *     <li>DB 조회 전용 Projection</li>
 *     <li>필요한 경우 영속성 객체와 Domain 객체 사이의 Mapper</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>Controller와 HTTP DTO</li>
 *     <li>Redis 상태 저장과 OpenVidu 연동</li>
 *     <li>입찰 규칙 판정</li>
 *     <li>유스케이스 전체 흐름</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>Domain 또는 Application 계층이 요구하는 저장·조회 계약을 JPA로 구현합니다.</li>
 *     <li>JPA Entity와 Domain Entity를 동일하게 사용한다면 불필요한 변환 계층을 만들지 않습니다.</li>
 *     <li>단순한 저장과 조회는 Spring Data JPA Repository로 바로 선언할 수 있습니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code BidRepository}</li>
 *     <li>{@code JpaBidQueryRepository}</li>
 *     <li>{@code BidPersistenceAdapter}</li>
 * </ul>
 */
package com.ssafy.bidlive.bid.infrastructure.persistence;
