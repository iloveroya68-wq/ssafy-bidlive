# 룰 기반 채팅 필터

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [룰 기반 채팅 필터](https://app.notion.com/127776ebfd688366bcba81389911a396)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-03` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

동기 마스킹; 원문은 암호화·접근 감사·보존기간 정책 적용

> **내부 동기 필터 명세** — DB·LLM 호출 없이 금칙어·연락처·계좌 정보를 즉시 마스킹한다.
## 호출 위치
```java
FilterResult filter(String content)
```
## 처리 규칙
- 금칙어는 5분 주기 인메모리 캐시에서 매칭
- 전화번호·메신저 ID·계좌 패턴은 설정된 규칙으로 탐지
- 탐지 구간은 `*`로 치환하고 `maskedFields`에 유형을 기록한 뒤 `CHAT_CONTENT_INVALID`로 즉시 거부
- 금칙어·연락처·계좌가 포함된 메시지는 저장·송출하지 않음
- 캐시 갱신 실패 시 마지막 정상 캐시 유지
- 필터·감사 로그 실패가 채팅 전송 트랜잭션을 롤백하지 않음
## 결과 예시
```json
{
  "filteredContent": "카톡 ******으로 연락 주세요",
  "maskedFields": ["CONTACT"],
  "blocked": true
}
```
## 감사 로그 보안
- 원문은 애플리케이션 일반 로그에 출력하지 않음
- `CHAT_MODERATION_LOG.originalContent`는 애플리케이션 계층 암호화 후 저장
- 복호화 권한은 운영 보안 역할로 제한하고 모든 조회를 감사 기록
- 보존 기간은 `moderation.audit-retention-days` 설정값으로 관리하며 기본 30일 후 삭제
- 분석·모니터링에는 원문 대신 messageId, 탐지 유형, 해시만 사용
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`CHAT_CONTENT_EMPTY`</td>
<td>공백 입력</td>
</tr>
<tr>
<td>`CHAT_CONTENT_TOO_LONG`</td>
<td>최대 길이 초과</td>
</tr>
</table>
## 필수 테스트
- 금칙어·전화번호·메신저·계좌 및 겹치는 패턴
- 민감정보·금칙어 탐지 시 저장·송출 전 즉시 거부
- 정상 문장 무변경
- 캐시 무중단 교체
- DB·애플리케이션 로그의 평문 원문 미노출
- 권한 없는 감사 원문 조회 차단과 보존기간 삭제

## 구현 메모 (2026-08-02, S15P11A704-252)

- `maskedFields`가 비어 있지 않은 신규 채팅만 감사 대상으로 등록한다. 멱등 재전송과
  정상 문장은 감사 레코드를 추가하지 않는다.
- 채팅 메시지가 커밋된 뒤 원문을 AES-256-GCM으로 암호화해 `chat_moderation_log`에
  저장한다. `messageId`, `auctionId`, `memberId`를 AAD로 결합하므로 암호문을 다른 채팅
  문맥으로 옮겨 복호화할 수 없다.
- `message_id` 고유 제약과 `ON CONFLICT DO NOTHING`으로 같은 메시지의 감사 레코드를
  한 건만 유지한다. 감사 보존기간을 원본 채팅의 물리 생명주기와 분리하기 위해
  `chat_message` 외래 키는 두지 않는다.
- 감사 암호화·저장 실패는 원문이나 내부 오류 메시지 없이 실패 유형만 기록하며, 이미
  커밋된 채팅을 롤백하거나 전송 결과를 실패로 바꾸지 않는다.
- 암호화 키는 `CHAT_MODERATION_AUDIT_ENCRYPTION_KEY_BASE64`에 32바이트 키의 Base64 값을,
  키 버전은 `CHAT_MODERATION_AUDIT_ENCRYPTION_KEY_VERSION`에 설정한다. 키가 없거나 잘못된
  경우 평문 저장으로 우회하지 않고 해당 감사 기록만 안전하게 실패한다.
- 보존기간은 `moderation.audit-retention-days`로 설정하며 기본값은 30일이다. 만료 정리
  주기는 `moderation.audit-cleanup-delay`로 설정하며 기본값은 1시간이다.
- 운영 코드에는 감사 원문 조회 API가 없다. 복호화 조회가 필요해지면 운영 보안 역할과
  조회 행위 자체의 별도 감사 계약을 먼저 추가해야 한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [룰 기반 채팅 필터 원본](https://app.notion.com/127776ebfd688366bcba81389911a396)

