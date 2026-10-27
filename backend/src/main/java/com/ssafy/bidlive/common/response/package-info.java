/**
 * 여러 REST API에서 반복 사용하는 성공, 오류, 페이지네이션 및 메타데이터 응답 구조를
 * 특정 도메인 타입과 분리하여 관리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>공통 성공 및 오류 응답</li>
 *     <li>커서 기반 페이지 응답</li>
 *     <li>일반 페이지네이션 응답</li>
 *     <li>빈 성공 응답</li>
 *     <li>공통 메타데이터 응답</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>경매 상세, 입찰 결과, 회원 프로필 등 특정 도메인의 응답 DTO</li>
 *     <li>Entity와 비즈니스 규칙</li>
 *     <li>Repository 조회 결과의 가공 로직</li>
 *     <li>서비스 실행 또는 Entity 변환 로직</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>공통 응답 래퍼는 특정 도메인 타입에 의존하지 않는 제네릭 구조로 작성합니다.</li>
 *     <li>도메인별 응답 내용은 각 도메인의 {@code api.dto.response}에 둡니다.</li>
 *     <li>{@code ApiResponse<AuctionDetailResponse>}에서 {@code ApiResponse}만 이 패키지에 속합니다.</li>
 *     <li>모든 응답의 래핑을 강제하지 않고 현재 프로젝트의 API 응답 정책을 따릅니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code ApiResponse}</li>
 *     <li>{@code CursorResponse}</li>
 *     <li>{@code PageResponse}</li>
 *     <li>{@code ErrorResponse}</li>
 *     <li>{@code NoContentResponse}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.response;
