/**
 * HTTP 요청에서 인증 정보를 추출하고 검증하여 인증된 사용자를 식별하고,
 * 여러 도메인이 사용할 수 있는 공통 인증·인가 기반 기능을 제공하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>JWT 생성 및 검증</li>
 *     <li>JWT 인증 필터</li>
 *     <li>Spring Security 인증 객체</li>
 *     <li>인증 사용자 정보 표현</li>
 *     <li>Controller ArgumentResolver와 사용자 주입용 어노테이션</li>
 *     <li>인증 실패 처리와 공통 권한 검사 기반 코드</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>카카오 OAuth API 직접 호출과 Access Token 교환</li>
 *     <li>회원 가입 또는 로그인 유스케이스와 회원 Entity</li>
 *     <li>경매 판매자 여부 등 특정 도메인의 권한 판정</li>
 *     <li>Controller의 비즈니스 처리</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>카카오 로그인 흐름은 {@code auth} 패키지에서 처리하고, 로그인 이후 자체 JWT 검증은 이 패키지에서 처리합니다.</li>
 *     <li>인증된 사용자의 식별자와 최소 권한 정보만 다른 계층에 전달합니다.</li>
 *     <li>경매 판매자 또는 거래 당사자 여부 같은 도메인 권한은 해당 도메인에서 검증합니다.</li>
 *     <li>{@code auction}, {@code bid}, {@code wallet} 등의 Service에 직접 의존하지 않습니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code JwtAuthenticationFilter}</li>
 *     <li>{@code JwtTokenProvider}</li>
 *     <li>{@code AuthMember}</li>
 *     <li>{@code AuthMemberArgumentResolver}</li>
 *     <li>{@code AuthenticationEntryPoint}</li>
 *     <li>{@code AccessDeniedHandler}</li>
 *     <li>{@code SecurityPrincipal}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.security;
