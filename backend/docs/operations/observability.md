# 실시간 기능 관측성 운영 안내

## Actuator 접근 정책

| 경로 | 접근 | 용도 |
| --- | --- | --- |
| `/actuator/health` | 공개, 상세 정보 비노출 | 인스턴스 생존 확인 |
| `/actuator/metrics` | `ADMIN` JWT | 등록된 Micrometer 지표 진단 |
| `/actuator/metrics/{name}` | `ADMIN` JWT | 개별 지표 진단 |
| `/actuator/prometheus` | `ADMIN` JWT | Prometheus 형식 수집 |

위 목록 이외의 Actuator endpoint는 노출하지 않는다. 운영 수집기가 JWT를 사용할 수
없는 환경에서는 애플리케이션 경로를 공개하지 말고 내부망 또는 reverse proxy에서
별도 인증을 적용한 뒤 접근 정책을 변경한다.

## 애플리케이션 메트릭

| Micrometer 이름 | 종류 | 태그 | 의미 |
| --- | --- | --- | --- |
| `bidlive.stomp.sessions.active` | Gauge | 없음 | 현재 연결된 인증 STOMP 세션 수 |
| `bidlive.stomp.connects` | Counter | `outcome`, `reason` | CONNECT 성공·만료·유효하지 않은 인증 수 |
| `bidlive.stomp.subscription.denials` | Counter | `reason` | 권한 정책에 의해 폐기된 SUBSCRIBE 수 |
| `bidlive.chat.messages` | Counter | `outcome` | 생성·멱등 재사용·정책 거부·실패 채팅 수 |
| `bidlive.chat.processing.duration` | Timer | `outcome` | STOMP 채팅 진입점 처리 지연 |
| `bidlive.redis.errors` | Counter | `operation` | 접속 세션 Redis 등록·삭제 실패 수 |
| `bidlive.llm.moderation.retries` | Counter | `result` | 비동기 검열 재시도 예약 수 |
| `bidlive.llm.moderation.dead.letters` | Counter | `result` | 비동기 검열 DLQ 전환 수 |

Prometheus에서는 이름의 `.`이 `_`로 바뀌고 Counter에는 `_total` 접미사가 붙는다.
예를 들어 `bidlive.stomp.connects`는 `bidlive_stomp_connects_total`로 조회한다.

## 초기 경보 기준

실서비스 기준선이 쌓이기 전까지 다음 값을 초기 경보 기준으로 사용하고, 부하 테스트와
운영 데이터에 따라 조정한다.

- 최근 5분 CONNECT 실패 비율이 20%를 넘으면 경고한다.
- 최근 5분 채팅 처리 지연 p95가 500ms를 넘으면 경고한다.
- Redis 오류가 1건이라도 발생하면 경고하고 5분 이상 계속되면 긴급으로 올린다.
- LLM DLQ가 1건이라도 증가하면 긴급으로 확인한다.
- 활성 STOMP 세션이 5분 안에 50% 이상 급감하면 배포·Redis·브로커 상태를 확인한다.
- SUBSCRIBE 거부는 기능 장애와 공격성 요청을 구분할 수 있도록 배포 시점과 함께 본다.

## correlationId와 구조화 로그

- REST는 유효한 `X-Request-ID` UUID를 전파하고 없거나 잘못된 값이면 새 UUID를 만든다.
- STOMP는 frame별 `X-Correlation-ID` UUID를 전파하며 잘못된 값은 새 UUID로 교체한다.
- 비동기 검열 Worker는 메시지 ULID를 correlationId로 사용해 재시도와 DLQ 로그를 잇는다.
- 구조화 필드는 `correlationId`, `stompSessionId`, `destination`, `eventType`,
  `auctionId`, `clientMessageId`, `messageId`, `outcome`, `errorCode` 중 필요한 값만 쓴다.
- JWT, Authorization header, 채팅 원문, 검열 원문, 이메일, 전화번호와 회원 상태는
  기록하지 않는다.
- 메트릭 태그에는 회원·경매·세션·메시지 식별자를 넣지 않는다.

## 로컬 확인

ADMIN Access Token을 준비한 뒤 다음 경로를 확인한다.

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" `
  http://localhost:8080/actuator/metrics
curl.exe -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" `
  http://localhost:8080/actuator/prometheus
```
