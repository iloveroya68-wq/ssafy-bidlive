/**
 * 프로젝트 전반에서 여러 도메인이 함께 사용하는 횡단 관심사와 기반 코드를 관리하며,
 * 특정 도메인 하나에만 속하는 규칙이나 구현이 모이지 않도록 경계를 제공하는 최상위 공통 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>공통 설정과 공통 예외 처리</li>
 *     <li>공통 API 응답 형식</li>
 *     <li>인증 및 보안 기반 기능</li>
 *     <li>공통 이벤트 기반 코드</li>
 *     <li>특정 도메인에 속하지 않는 보조 객체와 추상화</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>경매, 입찰, 회원, 지갑, 거래 등 특정 도메인의 비즈니스 규칙</li>
 *     <li>특정 도메인의 Controller, Service, Entity 및 Repository</li>
 *     <li>특정 외부 기술을 사용하는 도메인별 구현 코드</li>
 *     <li>여러 곳에서 사용할 가능성만으로 공통화한 코드</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>여러 도메인 패키지는 {@code common}을 사용할 수 있습니다.</li>
 *     <li>{@code common}은 {@code auction}, {@code bid}, {@code member}, {@code wallet} 등 특정 도메인을 직접 참조하지 않습니다.</li>
 *     <li>공통 코드와 도메인 코드 사이에 순환 의존성이 생기지 않도록 합니다.</li>
 *     <li>특정 기능에서만 필요한 코드는 해당 도메인의 {@code domain}, {@code application}, {@code infrastructure}에 둡니다.</li>
 * </ul>
 *
 * <h2>대표 하위 패키지</h2>
 * <ul>
 *     <li>{@code common.config}</li>
 *     <li>{@code common.exception}</li>
 *     <li>{@code common.response}</li>
 *     <li>{@code common.security}</li>
 *     <li>{@code common.event}</li>
 *     <li>{@code common.support}</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code BusinessException}</li>
 *     <li>{@code ApiResponse}</li>
 *     <li>{@code SecurityPrincipal}</li>
 *     <li>{@code DomainEvent}</li>
 *     <li>{@code ClockHolder}</li>
 * </ul>
 */
package com.ssafy.bidlive.common;
