# main 화면 설계 (S15P11A704-309 · 화면 틀 슬라이스 2)

로그인 홈 `/`(HomeGate → `MainView`)을 와이어프레임 `data-screen="main"` 기준으로 구현한다.
현재는 슬라이스 0이 깔아둔 stub(508B)이다.

## 정본 구조 — 3블록

```
.wrap
 ├─ .ticker[data-ticker]           오늘 예측 수익률 리더보드 (무한 마퀴, 클릭 → 마이페이지)
 ├─ 헤더 행                          소구 문구 + .serif 34px "지금 열려 있는 라이브" + 방 개설 CTA
 └─ .heroRow  (grid 1fr / 332px)
     ├─ .hero-all                  "전체 보기 →" (배너 우상단 바깥)
     ├─ .heroCar  470px            히어로 캐러셀 — 슬라이드 4장, 자동 3초, hover 화살표, dots
     └─ .endcol                    마감 임박 4장
```

### 연출 (정본 `init.main`)

| 요소 | 정본 동작 |
| --- | --- |
| 티커 | `tk` 38s linear 무한, `:hover`에 `animation-play-state: paused` |
| 캐러셀 | 3초마다 자동 전환, `translateX(-i*100%)`, dots 동기화, hover 시 화살표 노출 |
| 슬라이드 타이머 | 슬라이드별 `data-left`를 1초마다 감소, 0이면 "마감" |
| 입찰 틱 | 3.2초마다 **현재 슬라이드만** 입찰 건수 +1, 색 플래시 |
| 마감 임박 | `data-t0` 초를 1초마다 감소 |

## 정본 위에 얹는 마감

### 1. 타이머는 `endsAt` 절대 시각 기준으로 (규칙 5)

정본은 `data-t0`에 **남은 초**를 박고 `setInterval`로 1씩 깎는다. 이건 AGENTS.md 규칙 5가 금지하는
"클라이언트 자체 계산"이다. 서버가 준 절대 마감 시각과 어긋나도 화면은 멀쩡히 흘러간다.

기존 `shared/lib/useCountdown`을 쓴다 — `endsAt`(epoch ms) − `now`로 **표시만** 하고,
구독자가 몇이든 모듈 스코프 시계 하나를 공유한다(카드 N개에 인터벌 N개가 생기지 않는다).
`mocks/rooms.ts`가 이미 `endsAt`을 절대 시각으로 들고 있어 목 데이터도 그대로 맞는다.
`RoomCard`가 쓰는 방식과 같다.

### 2. 자동 전환은 hover·포커스에서 멈춘다

정본은 3초 자동 전환을 무조건 돌린다. 캐러셀 안에 링크·버튼이 있는데 자동으로 넘어가면
클릭 목표가 도망간다 — 정본도 티커에는 같은 이유로 hover 정지를 걸어 놨다(주석에 명시).
같은 원칙을 캐러셀에도 적용한다: `mouseenter`·`focusin`에 정지, 벗어나면 재개.

### 3. reduced-motion 항목별 처리 (랜딩과 같은 기준)

- 티커 마퀴 정지, 캐러셀 자동 전환 정지 — 수동 조작(화살표·dots)은 살린다
- 입찰 틱 색 플래시 생략, 숫자 갱신은 유지
- 랜딩에서 쓴 판단과 같다: **루프는 멈추고 상태 변화는 남긴다**

### 4. `#818181` → `text-dim`, `.serif` → `font-bold tracking`

랜딩(S15P11A704-308)에서 확정한 것과 동일. `.serif`는 서체가 아니라
`font-weight:700; letter-spacing:-.028em`이다.

### 5. 반응형 — 브라우저 창 폭 기준

`heroRow`의 `1fr 332px`는 `lg` 이상에서만. 그 아래는 1열로 쌓고 캐러셀 높이를 줄인다.
`.hero-all`의 `position:absolute; top:-21px; right:362px`는 332px 컬럼을 전제한 좌표라
1열에서는 성립하지 않는다 — 1열에서는 흐름 배치로 되돌린다.
**모바일 실기기는 범위 밖**(웹 전용 합의, 2026-07-25).

### 6. picsum은 랜딩과 같은 폴백

히어로 배경·마감 임박 썸네일이 `picsum.photos`다. 랜딩에서 한 대로 마지막 레이어에
토큰 배경을 깔아 못 불러와도 깨진 박스가 아니게 한다. 로컬 에셋 전환은 S15P11A704-314.

## 데이터

전부 **목 + 정적**이다(API 연동은 범위 밖).

| 블록 | 소스 |
| --- | --- |
| 티커 | 정본 `LEADERS` 10명을 `mocks/`에 옮긴다 |
| 히어로 4장 | 정본 `HEROES`(방 인덱스 + 태그 + 판매자) × `mockRooms` |
| 마감 임박 4장 | `mockRooms`에서 `state === 'live'`를 `endsAt` 오름차순으로 4개 |

마감 임박 정렬은 **명세와도 일치한다** — 「경매방 목록 조회」가 "`LIVE`는 `endAt` 오름차순"으로
정렬 정책을 명시했다. 실 API 전환 시 `status=LIVE`로 받으면 그대로 맞는다.

## 컴포넌트 분리

| 파일 | 이유 |
| --- | --- |
| `MainView.vue` | 화면 본체. 티커·헤더·캐러셀·마감 임박 조립 |
| `features/home/HomeTicker.vue` | 마퀴 로직(복제 트랙·hover 정지·reduced-motion)이 뭉치는 단위 |
| `features/home/LiveHeroCarousel.vue` | 자동 전환·dots·화살표·슬라이드별 타이머 |
| `features/home/EndingSoonCard.vue` | 마감 임박 1장. `useCountdown` 사용 |

**`RoomCard`는 재사용하지 않는다.** 그리드용 세로 카드(썸네일 152px + 배지 + 메타 3줄)이고
`.endcard`는 56px 썸네일 + 2줄 가로 리스트다. 골격이 달라 prop으로 흡수하면 둘 다 나빠진다.
`useCountdown`은 재사용한다.

티커는 정본에서 `main`·`livelist` 두 화면에 나오지만 **이번 슬라이스에는 main만** 들어온다.
컨벤션상 공용 추출은 두 번째 등장 시점이므로 `features/home/`에 두고, `/rooms`(livelist)를
할 때 `shared/ui/`로 올린다.

## 완료 조건

- `format:check` · `lint` · `build` 통과
- 와이어프레임 `data-screen="main"`과 1:1 대조
- 색·폰트 하드코딩 없음 (토큰 클래스만)
- 카운트다운이 `endsAt` 기준 — `Date.now()` 단독 계산 없음 (규칙 5)
- 브라우저: 티커 흐름·hover 정지 / 캐러셀 자동·수동·hover 정지 / 마감 임박 4장 카운트다운
- 브라우저 회귀: **비로그인 `/`(landing)** — HomeGate 분기 무손상
- 창 폭 축소 시 1열 전환 확인

## 범위 밖

- API 연동 (목 데이터로만)
- 티커 클릭 → 마이페이지 리더보드 탭: `/me`에 해당 탭이 아직 없다. 링크만 걸고 탭 구현은 Phase 5
- 로컬 에셋 전환 (S15P11A704-314)
