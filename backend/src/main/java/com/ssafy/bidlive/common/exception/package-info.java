/**
 * 프로젝트 전체에서 사용하는 예외의 공통 표현과 API 오류 변환 방식을 정의하여,
 * 예외 응답이 일관된 형식과 정책을 따르도록 하는 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>공통 비즈니스 예외 기반 클래스</li>
 *     <li>공통 오류 코드 인터페이스와 기반 타입</li>
 *     <li>전역 예외 처리기</li>
 *     <li>Validation 및 요청 형식 오류 처리</li>
 *     <li>예상하지 못한 서버 오류 처리</li>
 *     <li>공통 오류 응답 변환</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>정상적인 비즈니스 흐름과 Controller 로직</li>
 *     <li>DB 조회, Redis 처리 및 외부 API 호출</li>
 *     <li>특정 도메인에만 해당하는 모든 오류 코드의 무분별한 집중</li>
 *     <li>제어 흐름을 대신하기 위해 사용하는 예외</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>{@code GlobalExceptionHandler}는 예외를 일관된 API 오류 응답으로 변환합니다.</li>
 *     <li>{@code AuctionErrorCode}, {@code BidErrorCode}, {@code WalletErrorCode} 같은 전용 오류 코드는 각 도메인에 둘 수 있습니다.</li>
 *     <li>{@code common.exception}은 특정 도메인의 Service나 Entity를 직접 호출하지 않습니다.</li>
 *     <li>오류 응답 형식이 분리되어 있다면 {@code common.response}의 공통 타입과 단방향으로 협력합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code GlobalExceptionHandler}</li>
 *     <li>{@code BusinessException}</li>
 *     <li>{@code CommonErrorCode}</li>
 *     <li>{@code ErrorCode}</li>
 *     <li>{@code ErrorResponse}</li>
 *     <li>{@code ValidationErrorResponse}</li>
 * </ul>
 */
package com.ssafy.bidlive.common.exception;
