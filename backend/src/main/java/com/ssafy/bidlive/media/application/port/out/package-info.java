/**
 * 미디어 유스케이스가 외부 시스템에 요청하는 기능의 계약을 정의하는 아웃바운드 포트 패키지입니다.
 *
 * <h2>포함 대상</h2>
 * <ul>
 *     <li>라이브 미디어 서버 연동 계약</li>
 *     <li>특정 유스케이스에서만 필요한 외부 기능 인터페이스</li>
 * </ul>
 *
 * <h2>포함하지 않는 대상</h2>
 * <ul>
 *     <li>OpenVidu REST 요청·응답 모델</li>
 *     <li>WebClient 등 호출 기술의 세부 구현</li>
 * </ul>
 *
 * <h2>의존성 원칙</h2>
 * <ul>
 *     <li>{@code application}은 이 포트를 통해서만 외부 기술을 사용합니다.</li>
 *     <li>{@code infrastructure}가 이 포트를 구현하여 의존성 방향을 안쪽으로 유지합니다.</li>
 * </ul>
 *
 * <h2>대표 클래스 예시</h2>
 * <ul>
 *     <li>{@code LiveSessionGateway}</li>
 * </ul>
 */
package com.ssafy.bidlive.media.application.port.out;
