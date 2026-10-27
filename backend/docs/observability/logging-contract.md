# HTTP 요청 로그 계약

## 이벤트

- 이벤트명: `HTTP_REQUEST_COMPLETED`

## 필수 필드

- requestId
- httpMethod
- requestUri
- httpStatus
- durationMs

## 로그 레벨

- 2xx, 3xx: `INFO`
- 4xx: `WARN`
- 5xx 또는 처리되지 않은 예외: `ERROR`

## 기록 금지

- 비밀번호
- Authorization 헤더
- Access Token과 Refresh Token
- Cookie와 JSESSIONID
- OAuth authorization code
- 이메일과 전화번호 원문
