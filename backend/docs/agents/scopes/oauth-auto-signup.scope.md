# OAuth Auto Signup Scope

이 scope는 카카오 OAuth Callback에서 신규 회원과 소셜 계정, 기본 지갑을 하나의
트랜잭션으로 생성하는 `MEM-01` 내부 처리에 사용한다.

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Scope ID | `oauth-auto-signup` |
| 실제 작업 담당자 | 신희원 |
| Notion 명세 담당자 | 신희원 |
| 담당 기능 | OAuth 회원 자동 가입 처리 |
| 관련 도메인 | `auth`, `member`, `wallet` |

## 담당 책임

- 카카오 OAuth 프로필 검증과 소셜 계정 조회
- 신규 Member와 SocialAccount 생성
- TRADE/BETTING 기본 지갑 생성
- 신규 가입 전체 트랜잭션과 중복 소셜 계정 오류 처리
- 내부 가입 결과와 Callback 오류 코드 매핑

## 담당 API 명세

| 항목 | 값 |
| --- | --- |
| 로컬 명세 파일 | `docs/api/auth/mem-01-internal-internal-951776eb.md` |
| 기능 ID | `MEM-01` |
| 구현 가능한 상태 | `확정` |
| Notion 담당자 | 신희원 |

Callback 연동 동작은
`docs/api/auth/mem-01-get-login-oauth2-code-kakao-aa6776eb.md`를 읽기 전용으로
확인한다.

## 수정 허용 경로

```text
src/main/java/com/ssafy/bidlive/auth/application/OAuthMember*
src/main/java/com/ssafy/bidlive/auth/application/SocialAccountStore.java
src/main/java/com/ssafy/bidlive/auth/domain/OAuthMemberProvisioningErrorCode.java
src/main/java/com/ssafy/bidlive/auth/domain/SocialAccount.java
src/main/java/com/ssafy/bidlive/auth/infrastructure/oauth2/KakaoOAuth2AuthenticationSuccessHandler.java
src/main/java/com/ssafy/bidlive/auth/infrastructure/persistence/SocialAccount*
src/main/java/com/ssafy/bidlive/member/application/OAuthMember*
src/main/java/com/ssafy/bidlive/member/application/MemberPersistence.java
src/main/java/com/ssafy/bidlive/member/infrastructure/persistence/MemberPersistenceAdapter.java
src/main/java/com/ssafy/bidlive/wallet/application/WalletInitializationService.java
src/main/java/com/ssafy/bidlive/wallet/application/WalletPersistence.java
src/main/java/com/ssafy/bidlive/wallet/domain/Wallet.java
src/main/java/com/ssafy/bidlive/wallet/infrastructure/persistence/WalletPersistenceAdapter.java
src/test/java/com/ssafy/bidlive/auth/application/OAuthMember*
src/test/java/com/ssafy/bidlive/auth/api/KakaoOAuthCallbackIntegrationTest.java
src/test/java/com/ssafy/bidlive/auth/infrastructure/oauth2/KakaoOAuth2AuthenticationSuccessHandlerTest.java
docs/api/auth/mem-01-internal-internal-951776eb.md
```

## 읽기 전용 경로

```text
src/main/java/com/ssafy/bidlive/member/domain/Member.java
src/main/java/com/ssafy/bidlive/member/infrastructure/persistence/MemberRepository.java
src/main/java/com/ssafy/bidlive/wallet/domain/WalletType.java
src/main/java/com/ssafy/bidlive/wallet/infrastructure/persistence/WalletRepository.java
docs/api/auth/mem-01-get-login-oauth2-code-kakao-aa6776eb.md
docs/api/wallet/pnt-05-internal-internal-fa7776eb.md
```

## 수정 금지 경로

```text
build.gradle
settings.gradle
AGENTS.md
docs/agents/**
docs/architecture/package-boundaries.md
src/main/resources/application.yml
src/main/java/com/ssafy/bidlive/common/**
frontend/**
```

이 scope를 등록하는 별도 문서 관리 변경 외에는 구현 중 `docs/agents/**`를 수정하지
않는다.

## 공유 경로와 수정 조건

없음. 필요한 다중 도메인 파일을 위 수정 허용 경로에 개별 등록했다.

## 새 파일 생성 기준

- OAuth 프로필과 가입 결과 계약은 `auth.application`에 둔다.
- 회원 생성 공개 계약은 `member.application`에 둔다.
- 기본 지갑 생성 공개 계약은 `wallet.application`에 둔다.
- Application 계층은 Spring Data Repository를 직접 참조하지 않고 Persistence
  Port를 사용한다.
- JPA Adapter는 각 소유 도메인의 `infrastructure.persistence`에 둔다.

## 검증 명령

```bash
./gradlew test --tests '*OAuthMember*' --tests '*KakaoOAuthCallback*'
./gradlew test
./gradlew build
```

## 완료 보고

- 프로필 입력과 내부 가입 결과 구현 내용
- 신규·기존 회원 처리와 지갑 생성 결과
- 오류 코드의 내부 처리 및 Callback 매핑
- 트랜잭션 롤백과 DB 유니크 제약 검증
- 실행한 관련 테스트, 전체 테스트와 빌드 결과
- 작성 중인 초기 포인트 지급 정책을 구현하지 않았다는 사실
