# 비드라이브 Frontend

라이브 비크리 경매 + 낙찰가 예측 베팅 플랫폼의 프론트엔드.

## 스택

Vue 3 · TypeScript · Vite · Tailwind CSS v4 · Pinia · Vue Router

## 실행

```bash
npm install
npm run dev
```

## 구조

```
src/
├─ app/            # 앱 셸: router, 전역 스타일(디자인 토큰)
├─ shared/
│  ├─ ui/          # 공통 컴포넌트 (페이지 만들며 추출)
│  ├─ api/         # axios 인스턴스, API 모듈
│  └─ lib/         # 공용 유틸
├─ stores/         # Pinia — auth·wallet·room·prediction(FE1), live(FE2)
├─ features/       # 도메인별 화면+컴포넌트
│  ├─ auth/        # MEM (FE1)
│  ├─ wallet/      # PNT (FE1)
│  ├─ room/        # ROOM — 목록·개설·대기방 (FE1)
│  ├─ live/        # LIVE·BID·CHAT — 프레임 FE1, 미디어·소켓 FE2
│  ├─ prediction/  # PRED (FE1)
│  └─ result/      # 결과 리포트 (FE1)
└─ mocks/          # 아직 서버가 없는 것의 목 + 테스트용 가짜 서버(`*Server.ts`)
```

## 문서

- [스택 결정 기록](docs/01-스택-결정-기록.md) — 왜 이 스택·이 버전인지, 구조 접근 방식(A안), 디자인 단일 소스
- [협업 가이드](docs/02-협업-가이드.md) — FE1/FE2 작업 영역, 스토어 계약, 라우트 맵, 목킹 전략 · **처음 합류하면 여기부터**
- [작업 규칙 (AGENTS.md)](AGENTS.md) — 사람·AI 에이전트 공통 절대 규칙과 컨벤션. Claude Code·Copilot 등이 자동으로 읽는다

## 규칙

- **색·폰트는 Tailwind 토큰으로만** 사용 (`bg-cream`, `text-terra` 등). 하드코딩 hex 금지. 토큰 정의: `src/app/styles/main.css`
- 디자인 기준: 기획 레포의 `비드라이브 인터랙티브.html`
- FE2 작업 영역: `features/live/` + `stores/live.ts` — 그 외 영역과 파일 충돌 없음
