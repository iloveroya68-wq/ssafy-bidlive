/**
 * Redis를 이용한 채팅 기능의 실시간 상태 저장, 동시성 제어, 원자적 처리와 메시지
 * 연동을 구현하며 Redis 기술 세부사항을 상위 계층으로부터 분리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>{@code RedisTemplate} 사용 코드</li>
 *     <li>Lua Script 실행기와 Redis Key 생성 규칙</li>
 *     <li>실시간 상태 저장 및 조회</li>
 *     <li>원자 연산과 멱등성 키 관리</li>
 *     <li>Pub/Sub 또는 Redis Stream 연동</li>
 *     <li>Redis 명령 및 결과 변환 객체</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>Controller와 HTTP DTO</li>
 *     <li>채팅 기능의 핵심 계산 및 상태 전이 정책</li>
 *     <li>OpenVidu 연동과 PostgreSQL Repository</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>Application 또는 Domain 계층이 정의한 계약을 Redis 기반으로 구현합니다.</li>
 *     <li>Redis 자료구조, Key 형식 및 Lua 실행 세부사항이 상위 계층으로 노출되지 않도록 합니다.</li>
 *     <li>원자성은 Redis 명령이나 Lua Script로 보장하되 비즈니스 정책은 Domain 계층에 둡니다.</li>
 * </ul>
 *
 * <h2>Lua Script 위치</h2>
 * <p>Lua 파일 자체는 Java 패키지가 아니라 {@code src/main/resources/redis/lua/}에 배치합니다.</p>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code ChatRedisRepository}</li>
 *     <li>{@code ChatLuaScriptExecutor}</li>
 *     <li>{@code ChatRedisKey}</li>
 *     <li>{@code ChatRedisResult}</li>
 * </ul>
 */
package com.ssafy.bidlive.chat.infrastructure.redis;
