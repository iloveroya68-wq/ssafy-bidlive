/**
 * Spring과 외부 기술을 애플리케이션 전체에서 일관되게 사용할 수 있도록 실행 환경,
 * 공통 Bean과 프레임워크 수준의 설정을 구성하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>Spring Security와 CORS 설정</li>
 *     <li>JPA 공통 설정</li>
 *     <li>Redis 연결 및 직렬화 설정</li>
 *     <li>WebSocket/STOMP 공통 설정</li>
 *     <li>비동기 Executor와 Jackson JSON 변환 설정</li>
 *     <li>Swagger/OpenAPI 설정과 공통 Bean 등록</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>비즈니스 로직과 경매 또는 입찰 규칙</li>
 *     <li>특정 유스케이스 실행 코드</li>
 *     <li>Controller, Entity 및 Repository 조회 로직</li>
 *     <li>외부 서비스 호출 흐름</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>애플리케이션 실행 환경과 여러 패키지에서 공유할 Bean 구성만 담당합니다.</li>
 *     <li>설정 클래스에서 도메인 비즈니스 로직을 실행하거나 특정 도메인 Service를 직접 호출하지 않습니다.</li>
 *     <li>{@code @Configuration} 사용 여부가 아니라 설정의 적용 범위가 프로젝트 공통인지 판단하여 배치합니다.</li>
 *     <li>특정 기술의 복잡한 설정은 해당 도메인의 {@code infrastructure} 하위 패키지로 분리할 수 있습니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code JpaConfig}</li>
 *     <li>{@code RedisConfig}</li>
 *     <li>{@code WebSocketConfig}</li>
 *     <li>{@code SecurityConfig}</li>
 *     <li>{@code AsyncConfig}</li>
 *     <li>{@code JacksonConfig}</li>
 *     <li>{@code OpenApiConfig}</li>
 *     <li>{@code CorsConfig}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.config;
