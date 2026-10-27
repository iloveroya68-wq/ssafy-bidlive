/**
 * 특정 도메인에 속하지 않으면서 여러 도메인에서 같은 의미로 재사용되는 작고 명확한
 * 보조 객체, 값 타입과 시스템 기능 추상화를 관리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>현재 시각 제공 추상화</li>
 *     <li>ID 생성 추상화</li>
 *     <li>멱등성 키 공통 타입</li>
 *     <li>범용 마스킹, 문자열 또는 날짜 변환 기능</li>
 *     <li>테스트하기 어려운 시스템 기능의 추상화</li>
 *     <li>여러 도메인에서 동일한 의미로 사용하는 작은 값 객체</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>{@code CommonUtil}처럼 역할이 불분명한 거대한 유틸리티 클래스</li>
 *     <li>비크리 최종가, 입찰 가능 여부, 포인트 정산, 배송 상태 등 도메인 계산과 정책</li>
 *     <li>Repository, Controller 및 Service</li>
 *     <li>Redis 또는 OpenVidu 직접 호출</li>
 *     <li>특정 도메인 한 곳에서만 사용하는 보조 코드</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>{@code support}는 특정 도메인의 규칙을 알거나 도메인 패키지에 의존하지 않습니다.</li>
 *     <li>가능한 한 상태가 없고 클래스 이름만으로 역할을 알 수 있는 작은 객체로 작성합니다.</li>
 *     <li>실제로 여러 곳에서 같은 의미로 반복 사용되기 전에는 공통 코드로 이동하지 않습니다.</li>
 *     <li>{@code Util}, {@code Helper}, {@code Manager} 같은 모호한 이름을 피합니다.</li>
 *     <li>Redis Key 생성은 각 도메인의 {@code infrastructure.redis}에 둡니다.</li>
 * </ul>
 *
 * <h2>배치 판단 예시</h2>
 * <ul>
 *     <li>서버 현재 시각 제공은 {@code common.support}에 배치할 수 있습니다.</li>
 *     <li>입찰 마감 가능 여부 계산은 {@code auction.domain}에 배치합니다.</li>
 *     <li>입찰 멱등성 키 공통 값 객체는 사용 범위에 따라 이 패키지에 배치할 수 있습니다.</li>
 *     <li>경매 참여자 닉네임 마스킹 정책은 실제 사용 범위에 따라 해당 도메인 또는 이 패키지에 둡니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code ClockHolder}</li>
 *     <li>{@code SystemClockHolder}</li>
 *     <li>{@code IdGenerator}</li>
 *     <li>{@code IdempotencyKey}</li>
 *     <li>{@code MaskingUtils}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.support;
