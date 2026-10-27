# BidLive Backend

BidLive 서비스의 Spring Boot 백엔드 프로젝트입니다. 도메인별 패키지와 계층별
`package-info.java`를 통해 코드의 배치 기준과 의존성 원칙을 관리합니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.16
- Gradle
- Spring Web, Validation
- Spring Security, OAuth2 Client
- Spring Data JPA, PostgreSQL
- Spring Data Redis
- WebSocket/STOMP
- Spring WebFlux (OpenVidu REST 호출 전용 WebClient)
- Lombok

## 실행 전 준비

다음 프로그램이 필요합니다.

- JDK 21
- PostgreSQL
- Redis를 사용하는 기능을 개발하거나 실행하는 경우 Redis

기본 데이터베이스 연결 정보는 다음과 같습니다.

- URL: `jdbc:postgresql://localhost:5432/bidlive`
- 사용자명: `root`
- 비밀번호: 환경변수 `DB_PASSWORD`로 반드시 지정

Redis의 기본 연결 정보는 다음과 같습니다.

- Host: `localhost`
- Port: `6379`

## 환경변수

| 변수 | 필수 여부 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `DB_URL` | 선택 | `jdbc:postgresql://localhost:5432/bidlive` | PostgreSQL JDBC URL |
| `DB_USERNAME` | 선택 | `root` | PostgreSQL 사용자명 |
| `DB_PASSWORD` | 필수 | 없음 | PostgreSQL 비밀번호 |
| `REDIS_HOST` | 선택 | `localhost` | Redis 호스트 |
| `REDIS_PORT` | 선택 | `6379` | Redis 포트 |
| `REDIS_CONNECT_TIMEOUT` | 선택 | `2s` | Redis 연결 제한 시간 |
| `REDIS_TIMEOUT` | 선택 | `2s` | Redis 명령 제한 시간 |
| `KAKAO_CLIENT_ID` | 필수 | 없음 | 카카오 앱의 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 필수 | 없음 | 카카오 앱의 Client Secret |
| `FRONTEND_OAUTH_CALLBACK_URI` | 선택 | `http://localhost:5173/oauth/callback` | OAuth 처리 후 이동할 프론트 주소 |
| `OAUTH_LOGIN_CODE_TTL` | 선택 | `2m` | 일회용 로그인 코드 Redis 만료 시간 |
| `JWT_ISSUER` | 선택 | `bidlive` | JWT 발급자 |
| `JWT_SECRET_BASE64` | 필수 | 없음 | 32바이트 이상의 JWT HMAC 키를 Base64로 인코딩한 값 |
| `JWT_ACCESS_TOKEN_TTL` | 선택 | `30m` | Access Token 만료 시간 |
| `JWT_REFRESH_TOKEN_TTL` | 선택 | `14d` | Refresh Token 만료 시간 |
| `OPENVIDU_URL` | 필수 | 없음 | OpenVidu 서버의 Base URL |
| `OPENVIDU_SECRET` | 필수 | 없음 | OpenVidu 서버의 Secret |
| `MINIO_ROOT_USER` | MinIO 사용 시 필수 | 없음 | MinIO 관리 계정 ID |
| `MINIO_ROOT_PASSWORD` | MinIO 사용 시 필수 | 없음 | MinIO 관리 계정 비밀번호 |
| `MINIO_DATA_PATH` | 선택 | `bidlive-minio-data` | MinIO 데이터 저장 Docker Volume 또는 EC2 절대 경로 |
| `AWS_S3_BUCKET` | 필수 | 없음 | 상품 이미지와 방송 스냅샷을 저장할 버킷 |
| `AWS_S3_REGION` | 선택 | `ap-northeast-2` | S3 SDK 리전 |
| `AWS_S3_ENDPOINT` | MinIO 사용 시 필수 | 없음 | 백엔드가 파일 저장에 사용하는 내부 MinIO 주소 |
| `AWS_S3_PUBLIC_BASE_URL` | 선택 | 없음 | 프론트에 반환할 이미지 공개 Base URL |
| `AWS_ACCESS_KEY_ID` | 필수 | 없음 | 백엔드 전용 MinIO 또는 AWS Access Key |
| `AWS_SECRET_ACCESS_KEY` | 필수 | 없음 | 백엔드 전용 MinIO 또는 AWS Secret Key |
| `GMS_BASE_URL` | AI 사용 시 필수 | 없음 | OpenAI 호환 GMS Base URL. SSAFY GMS는 `https://gms.ssafy.io/gmsapi/api.openai.com/v1` |
| `GMS_API_KEY` | AI 사용 시 필수 | 없음 | GMS 인증 키. 저장소와 로그에 기록하지 않음 |
| `GMS_STT_ENABLED` | 선택 | `false` | GMS Whisper 음성 변환 활성화 여부. 마이크 수집과 Credit 사용 승인 후 활성화 |
| `GMS_TRANSCRIPTION_PATH` | 선택 | `/audio/transcriptions` | OpenAI 호환 음성 변환 경로 |
| `GMS_TRANSCRIPTION_MODEL` | 선택 | `whisper-1` | GMS에서 허용한 음성 변환 모델 |
| `GMS_CATCH_UP_SUMMARY_ENABLED` | 선택 | `true` | 확정 STT 문장의 AI 요약 활성화 여부 |
| `GMS_CATCH_UP_SUMMARY_MODEL` | 선택 | `gpt-4o-mini` | 중간 입장 요약에 사용할 텍스트 모델 |

저장소의 `.env.example`을 복사하여 로컬 설정 파일을 만듭니다.

```bash
cp .env.example .env
```

`.env`의 값을 로컬 환경에 맞게 수정한 후 환경변수로 로드합니다.

```bash
set -a
source .env
set +a
```

Spring Boot는 별도 라이브러리 없이 `.env` 파일을 자동으로 읽지 않습니다.
따라서 위와 같이 셸에 로드하거나 IntelliJ IDEA의 Run/Debug Configuration에서
Environment variables에 직접 등록해야 합니다.

`.env`는 Git에서 제외되며 실제 비밀번호, JWT Secret, OAuth Client Secret 등의
비밀정보를 저장소에 커밋하면 안 됩니다. `.env.example`에는 변수명과 안전한 예시만
작성합니다.

## 방송 중간 입장 요약

`GET /api/v1/auction-rooms/{auctionId}/catch-up-summary`는 공개 가능한 입찰·참여·연장
집계를 Redis에서 읽고 최대 2분 캐시합니다. 판매자 발화가 있으면 GMS로 요약하고,
GMS 또는 STT가 실패하면 RoomEvent 집계만 반환합니다. 아무 소스도 없으면 `204 No Content`이며
방 입장, 시청, 입찰은 계속 동작합니다.

SSAFY GMS는 OpenAI 호환 `whisper-1` 음성 변환을 지원합니다. 백엔드는
`https://gms.ssafy.io/gmsapi/api.openai.com/v1/audio/transcriptions`에
`multipart/form-data`의 `file`, `model=whisper-1`을 전송합니다. 인증은
`Authorization: Bearer {GMS_API_KEY}` 헤더를 사용합니다.

기본값은 의도하지 않은 마이크 수집과 GMS Credit 사용을 막기 위해 비활성화되어 있습니다.
배포 환경의 Secret에 `GMS_API_KEY`를 주입한 뒤 백엔드 `GMS_STT_ENABLED=true`와 프론트
`VITE_STT_CAPTURE_ENABLED=true`를 모두 설정해야 실제 판매자 음성 청크가 수집·변환됩니다.
`VITE_STT_CAPTURE_ENABLED`는 프론트 빌드 시점에 주입한 뒤 다시 빌드해야 반영됩니다.

## Redis 로컬 개발 환경

Redis만 실행할 때는 다음 명령을 사용합니다.

```bash
docker compose up -d redis
```

컨테이너가 정상 상태인지 확인하고 Redis 응답을 점검합니다.

```bash
docker compose ps redis
docker compose exec redis redis-cli ping
```

두 번째 명령의 결과가 `PONG`이면 Redis가 요청을 받을 수 있는 상태입니다. Redis
데이터는 `bidlive-redis-data` 이름의 Docker Volume에 저장되며 컨테이너를 다시
생성해도 유지됩니다. 데이터를 포함해 개발 환경을 초기화해야 할 때만 다음 명령을
사용합니다.

```bash
docker compose down -v
```

이 명령은 PostgreSQL, Redis와 기본 Docker Volume을 사용하는 MinIO 이미지 파일을
모두 삭제하므로 필요한 데이터가 없는지 확인한 후 실행해야 합니다. EC2에서
`MINIO_DATA_PATH`를 호스트 절대 경로로 지정한 경우 해당 디렉터리는
`docker compose down -v`로 삭제되지 않지만 별도 백업은 필요합니다.

Spring Boot를 호스트에서 실행할 때는 기본값인 `localhost:6379`에 연결합니다.
전체 Compose 환경에서 실행하는 backend 컨테이너에는 `REDIS_HOST=redis`가 자동으로
적용됩니다.

공통 `RedisTemplate<String, Object>`은 Key와 Hash Key를 UTF-8 문자열로, Value와
Hash Value를 타입 정보가 포함된 JSON으로 저장합니다. 문자열만 저장하는 기능은
Spring Boot가 제공하는 `StringRedisTemplate`을 사용할 수 있습니다. JDK 직렬화
형식은 사용하지 않습니다.

### Redis Key 네이밍 규칙

새 Redis Key는 다음 형식을 사용합니다.

```text
bidlive:{domain}:{aggregate-id}:{purpose}
```

- 모든 구간은 영문 소문자 kebab-case와 숫자를 사용하고 `:`로 구분합니다.
- `domain`에는 `auction`, `bid`, `deal`처럼 Key를 소유한 도메인을 사용합니다.
- 관련 Key를 Redis Cluster의 같은 슬롯에 배치해야 하면 실제 식별자를 중괄호로
  감싼 Hash Tag를 사용합니다.
- Key 문자열은 각 도메인의 `infrastructure.redis`에 위치한 Key 생성 객체 한 곳에서
  관리하며 여러 Adapter에 하드코딩하지 않습니다.
- 세션, 잠금, 멱등성 Key 같은 임시 데이터에는 만료 시간(TTL)을 반드시 지정합니다.
- 토큰, 이메일, 전화번호와 같은 비밀정보나 개인정보를 Key에 직접 포함하지 않습니다.
- 운영 코드에서 `KEYS` 명령으로 전체 Key를 탐색하지 않습니다.

예시는 다음과 같습니다.

```text
bidlive:auction:{42}:state
bidlive:auction:{42}:timer
bidlive:bid:{42}:highest
bidlive:deal:{42}:settlement-lock
```

위 예시에서 `{42}`는 문서용 자리표시자가 아니라 Redis Cluster Hash Tag를 나타내는
실제 중괄호입니다.

## MinIO 이미지 저장소

AWS S3가 제공되지 않는 환경에서는 Docker Compose가 EC2 안에 S3 호환 MinIO를
실행합니다. 기존 AWS SDK 기반 상품 이미지와 방송 스냅샷 코드는
`AWS_S3_ENDPOINT`만 MinIO로 지정해 그대로 사용합니다.

`.env.example`을 참고해 관리자 계정과 백엔드 전용 계정을 서로 다른 값으로
설정합니다. `minio-init` 컨테이너는 다음 작업을 한 번 수행한 뒤 정상 종료합니다.

- `AWS_S3_BUCKET` 버킷 생성
- 백엔드 전용 사용자 생성 및 `readwrite` 정책 연결
- 브라우저 이미지 조회를 위한 버킷 익명 다운로드 허용

MinIO와 버킷 초기화만 실행하려면 다음 명령을 사용합니다.

```bash
docker compose up -d minio
docker compose run --rm minio-init
docker compose ps -a minio minio-init
```

로컬에서 Spring Boot를 직접 실행할 때는 다음 주소를 사용합니다.

```env
AWS_S3_ENDPOINT=http://localhost:9000
AWS_S3_PUBLIC_BASE_URL=http://localhost:9000/bidlive
```

Compose의 backend 컨테이너에는 내부 저장 주소
`http://minio:9000`이 자동 적용됩니다. MinIO API와 관리 콘솔은 각각 호스트의
`127.0.0.1:9000`, `127.0.0.1:9001`에만 바인딩되어 외부 네트워크에 직접
노출되지 않습니다.

### EC2 배포

EC2에서는 컨테이너를 삭제하거나 다시 만들어도 파일이 남도록 `.env`에 호스트의
절대 경로를 지정합니다. 해당 디렉터리는 Docker의 MinIO 컨테이너가 읽고 쓸 수
있는 권한이어야 합니다.

```env
MINIO_DATA_PATH=/srv/bidlive/minio-data
AWS_S3_ENDPOINT=http://minio:9000
AWS_S3_PUBLIC_BASE_URL=https://your-service-domain/files
```

Nginx는 공개 이미지 요청만 MinIO 버킷으로 전달합니다.

```nginx
location /files/ {
    proxy_pass http://127.0.0.1:9000/bidlive/;
    proxy_set_header Host $host;
}
```

MinIO의 `9000`, `9001` 포트를 EC2 보안 그룹에서 직접 공개하지 않습니다. 관리
콘솔이 필요하면 SSH 포트 포워딩을 사용합니다. EC2 한 대의 디스크에만 저장되는
구성이므로 운영 데이터는 별도의 주기적 백업 대상에 포함해야 합니다.

## OpenAPI와 Swagger UI

애플리케이션 실행 후 다음 주소에서 API 문서를 확인할 수 있습니다.

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Swagger UI는 HTTP Method 순서로 Operation을, 이름순으로 Tag를 정렬합니다. 공통 문서
메타데이터는 `OpenApiConfig`에서 관리하며 Springdoc이 등록된 Spring MVC Controller를
기준으로 OpenAPI 문서를 생성합니다.

경로 설정은 `application.yml`의 `springdoc` 항목에서 관리합니다. 개별 API의 요청,
응답 또는 설명을 변경할 때는 해당 도메인의 확정된 로컬 API 명세와 활성 기능 scope를
먼저 확인해야 합니다.

## 카카오 OAuth 설정

카카오 디벨로퍼스에서 카카오 로그인을 활성화하고 다음 Redirect URI를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/kakao
```

카카오 앱의 REST API 키를 `KAKAO_CLIENT_ID`, Client Secret을
`KAKAO_CLIENT_SECRET` 환경변수로 주입합니다. 로그인 시작 경로는 다음과 같으며,
Spring Security OAuth2 Client가 `state`를 생성하고 카카오 인증 화면으로
리다이렉트합니다.

```text
GET http://localhost:8080/oauth2/authorization/kakao
```

이 경로는 이메일이나 비밀번호를 받는 API가 아닙니다. 프론트는 로그인 버튼 클릭 시
폼 데이터를 전송하지 않고 브라우저를 이 경로로 이동시킵니다.

카카오 인증이 끝나면 Spring Security가 Callback에서 `state`와 Authorization Code를
검증하고 카카오 사용자 정보를 조회합니다. 처음 확인된 카카오 계정이면 회원,
소셜 계정, TRADE/BETTING 지갑을 하나의 DB 트랜잭션으로 생성합니다. 이후 Redis에
짧게 저장한 일회용 로그인 코드만 프론트 URL에 포함합니다.

```text
http://localhost:5173/oauth/callback?code={oneTimeLoginCode}
```

카카오 Access Token은 DB에 저장하거나 프론트에 전달하지 않습니다. 프론트는
일회용 코드를 `POST /api/v1/auth/oauth2/token`으로 교환합니다.

```http
POST /api/v1/auth/oauth2/token
Content-Type: application/json

{
  "loginCode": "one-time-code"
}
```

교환에 성공하면 Access Token과 회원 요약 정보를 응답 본문으로 반환하고,
Refresh Token은 `HttpOnly`, `Secure`, `SameSite=Lax` 쿠키로 발급합니다.
FE의 로그인 직후 권한 및 이용 상태 판단을 위해 `member.status`와
`member.role`을 함께 반환합니다.

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJ...",
  "expiresIn": 1800,
  "member": {
    "memberId": 25,
    "nickname": null,
    "onboardingRequired": true,
    "status": "ACTIVE",
    "role": "USER"
  }
}
```

`SUSPENDED` 회원은 로그인할 수 있으며 상태가 응답에 포함됩니다. 실제 입찰,
베팅, 채팅 제한은 각 보호 API에서도 서버 인가 규칙으로 적용해야 합니다.
`WITHDRAWN` 회원은 토큰을 발급하지 않습니다.

개발용 JWT 키는 다음 PowerShell 명령으로 만들 수 있습니다.

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

## OpenVidu 라이브 스트리밍

`openvidu.url`과 `openvidu.secret`은 `OPENVIDU_URL`, `OPENVIDU_SECRET` 환경변수로
주입합니다. 백엔드는 OpenVidu Java SDK 대신 REST API를 직접 호출하며, 호출 전용
WebClient는 `media.infrastructure.openvidu.OpenViduConfiguration`에서 등록합니다.

인증 헤더는 `OPENVIDUAPP:{OPENVIDU_SECRET}`을 Base64로 인코딩한 Basic 인증을
사용합니다. 현재 OpenVidu 서버가 self-signed 인증서를 사용하므로 이 WebClient만
인증서 신뢰 검증과 호스트명 검증을 우회하며, 다른 외부 연동에는 재사용하지 않습니다.
정식 인증서를 적용하면 `OpenViduConfiguration.insecureHttpClient()`와 이를 사용하는
`clientConnector(...)` 호출을 함께 제거해 기본 검증을 복구합니다.

OpenVidu 세션의 `customSessionId`로 `roomId`를 그대로 사용하므로 같은 방의
Publisher와 Subscriber는 항상 같은 세션에 연결됩니다. 세션이 이미 있어 OpenVidu가
409 CONFLICT를 반환하면 기존 세션을 재사용합니다.

```http
POST /api/v1/rooms/{roomId}/live/publisher
POST /api/v1/rooms/{roomId}/live/subscriber
```

두 API 모두 프로젝트 공통 `type`·`data` 형식으로 응답합니다. `type`은 Publisher가
`LIVE_PUBLISHER_TOKEN_ISSUED`, Subscriber가 `LIVE_SUBSCRIBER_TOKEN_ISSUED`입니다.

```json
{
  "type": "LIVE_PUBLISHER_TOKEN_ISSUED",
  "data": {
    "roomId": "room-123",
    "sessionId": "room-123",
    "token": "wss://i15a704.p.ssafy.io:4443?sessionId=room-123&token=tok_...",
    "role": "PUBLISHER",
    "createdAt": "2026-07-20T21:05:00+09:00"
  }
}
```

`roomId`는 PathVariable과 응답 모두 String으로 확정했습니다. `docs/api/media`의
LIVE-01 명세는 아직 숫자 예시와 갱신 전 상태이며, 확정된 계약을 추후 문서에
반영합니다.

인증·인가와 방 상태 검증은 아직 적용되지 않았습니다. Seller 권한 검증, 인증 사용자
검증, 방 상태 확인은 `LiveSessionService`의 `TODO(LIVE-01)` 위치에 추가합니다.

## 실행

```bash
./gradlew bootRun
```

Windows에서는 다음 명령을 사용합니다.

```powershell
.\gradlew.bat bootRun
```

로컬에서 PostgreSQL 없이 먼저 서버를 띄울 때는 `local` 프로파일을 사용합니다.
이 프로파일은 H2 메모리 DB를 사용하므로 별도 DB 설치 없이 실행할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

IntelliJ IDEA에서는 다음처럼 설정합니다.

- Gradle JVM: `Eclipse Temurin 21`
- Use Gradle from: `gradle-wrapper.properties`
- Run Configuration의 Active profiles: `local`

## 테스트

```bash
./gradlew test
```

## 로그 분석 자동화

Spring Boot 요청 로그를 PowerShell 분석기, Codex Skill과 Stop Hook으로 확인할 수
있습니다. 팀원별 최초 설정, IntelliJ Terminal 사용법과 동작 검증 절차는
[`spring-log-triage 사용 가이드`](../.agents/skills/spring-log-triage/README.md)를
참고합니다.

## 빌드

```bash
./gradlew clean build
```

빌드 결과는 `build/`에 생성되며 Git에서 제외됩니다.

## 패키지 구조

기능별 최상위 패키지는 `src/main/java/com/ssafy/bidlive/` 아래에 위치합니다.

```text
com.ssafy.bidlive
├── auction
├── auth
├── bid
├── chat
├── deal
├── media
├── member
├── prediction
├── product
├── report
├── wallet
└── common
```

각 기능 패키지는 다음 계층을 기준으로 구성합니다.

| 패키지 | 역할 |
| --- | --- |
| `api` | REST 및 WebSocket 요청을 받는 진입점 |
| `api.dto` | 요청·응답 및 메시지 전달 객체 |
| `application` | 유스케이스 실행과 트랜잭션 흐름 조율 |
| `domain` | 핵심 상태, 행위 및 비즈니스 규칙 |
| `infrastructure` | DB, Redis, OpenVidu 등 외부 기술 구현 |

`common`에는 특정 도메인 하나에 속하지 않고 여러 도메인이 함께 사용하는 설정,
예외, 응답, 보안, 이벤트 및 보조 코드만 배치합니다.

각 패키지의 상세 배치 기준은 해당 디렉터리의 `package-info.java`에서 확인할 수
있습니다. IntelliJ IDEA에서는 Javadoc 왼쪽의 Toggle Rendered View를 누르거나
`Ctrl+Alt+Q`를 사용하면 HTML 태그 없이 렌더링된 문서로 볼 수 있습니다.

## 설정 파일 관리 원칙

- `application.yml`은 공통 설정과 환경변수 참조만 포함하여 Git에 커밋합니다.
- 실제 비밀값은 환경변수 또는 배포 환경의 Secret 관리 기능으로 주입합니다.
- 개인용 Spring 설정은 Git에서 제외된 `application-local.yml` 등에 둘 수 있습니다.
- 비밀값이 저장소에 커밋되었다면 파일에서 삭제하는 것만으로 끝내지 말고 해당 값을 즉시 교체합니다.
