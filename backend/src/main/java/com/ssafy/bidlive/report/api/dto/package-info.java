/**
 * HTTP, WebSocket 또는 외부 시스템 경계를 넘어 전달되는 신고 데이터 객체를 관리하며,
 * 요청과 응답 모델을 내부 도메인 모델로부터 분리하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>HTTP Request 및 Response DTO</li>
 *     <li>WebSocket 메시지 DTO</li>
 *     <li>외부 API 요청·응답 DTO</li>
 *     <li>조회 결과 전달용 DTO</li>
 *     <li>필요한 경우 {@code dto.request}, {@code dto.response} 하위 패키지</li>
 *     <li>가능한 한 Java {@code record}로 작성한 불변 데이터 객체</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>JPA Entity</li>
 *     <li>비즈니스 규칙 또는 상태 변경 로직</li>
 *     <li>Repository 및 Service</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>DTO는 계층 간 데이터 계약만 표현하고 도메인 행위를 수행하지 않습니다.</li>
 *     <li>영속성 모델을 그대로 외부에 노출하지 않으며 입력 검증 등 경계의 책임만 가집니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code ReportCreateRequest}</li>
 *     <li>{@code ReportDetailResponse}</li>
 *     <li>{@code ReportMessageRequest}</li>
 * </ul>
 */
package com.ssafy.bidlive.report.api.dto;
