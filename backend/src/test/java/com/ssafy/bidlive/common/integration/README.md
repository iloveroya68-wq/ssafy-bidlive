# 실시간 P0 통합 테스트

Docker Desktop 또는 Testcontainers가 연결할 수 있는 Docker Engine이 필요합니다.
테스트는 PostgreSQL 17과 Redis 7.4 컨테이너를 자동으로 시작하고, 각 테스트 전에
두 저장소의 데이터를 초기화합니다.

## 빠른 환경 검증

```powershell
.\gradlew.bat test --tests "com.ssafy.bidlive.common.integration.RealtimeIntegrationTestEnvironmentTest"
```

## P0 전체 회귀

```powershell
.\gradlew.bat test --tests "com.ssafy.bidlive.common.integration.*"
```

## 장시간 STOMP 경로 분리 실행

```powershell
.\gradlew.bat test --tests "com.ssafy.bidlive.common.integration.RealtimeCriticalPathIntegrationTest"
```

## 저장소 정합성만 실행

```powershell
.\gradlew.bat test --tests "com.ssafy.bidlive.common.integration.RealtimeStateConsistencyIntegrationTest"
```

첫 실행은 Docker image 다운로드 때문에 시간이 더 걸릴 수 있습니다. 이후 실행은
로컬 image를 재사용합니다.
