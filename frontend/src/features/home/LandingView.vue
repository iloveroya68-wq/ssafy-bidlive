<script setup lang="ts">
// MEM-01 랜딩 (FE1) — 비로그인 홈. 와이어프레임 data-screen="landing" 1:1 구현 (S15P11A704-308).
//
// 다크 히어로 → 봉투 개봉 스테이지(핀 고정) → 비크리 카드 → 라이브 목업 → 예측 배당 → 테라 드렌치 CTA,
// 6개 섹션이 이어지는 내러티브다. 연출 구동은 전부 이 컴포넌트 로컬이다.
// 봉투 스테이지의 설계 근거·데모 대비 차이는 `docs/superpowers/specs/2026-08-06-랜딩-봉투-온보딩-design.md`.
//
// 정본과 달리 리빌 연출이 콘텐츠 가시성을 인질로 잡지 않는다 — 마크업 기본값은 보이는 상태고,
// 마운트 후 JS가 숨김(pre-*)→관찰→리빌을 얹는다. IO는 observe 직후 초기 교차 상태를 반드시
// 보고하므로 첫 화면 안 요소는 즉시 리빌되고, 만에 하나 IO가 안 돌아도 타임아웃 보험이 되돌린다.
// prefers-reduced-motion이면 연출 전체를 건너뛰고 최종 상태로 둔다.
// 예외는 봉투 스테이지다 — 기본값이 "닫힌 봉투"라 편지 본문은 겉면에 가려져 있다.
// 편지는 초대 카피(장식)라 가려져도 정보 손실이 없고, reduced-motion이 봉투를 걷어낸다.
import { onBeforeUnmount, onMounted, ref, type Directive } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import KakaoAuthButton from '@/features/auth/KakaoAuthButton.vue'
import { formatPoint } from '@/shared/lib/point'
import { useCountUp } from '@/shared/lib/useCountUp'

gsap.registerPlugin(ScrollTrigger)

const router = useRouter()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

// ── 히어로: 타이틀 마스크 슬라이드업 — 마운트 후 두 프레임 뒤 재생 (정본 hplay) ──
const heroPlay = ref(reducedMotion)
let heroFallback = 0
onMounted(() => {
  if (reducedMotion) return
  requestAnimationFrame(() => requestAnimationFrame(() => (heroPlay.value = true)))
  // 백그라운드 탭에선 rAF가 멈춘다 — 리빌 원칙(콘텐츠를 인질로 잡지 않는다)대로 보험을 둔다
  heroFallback = window.setTimeout(() => (heroPlay.value = true), 1500)
})

// ── 히어로: 부유 카드 마우스 패럴랙스 — data-depth 비례, rAF로 묶어 프레임당 1회만 ──
const heroEl = ref<HTMLElement | null>(null)
let fragEls: HTMLElement[] = []
let parallaxRaf = 0
const parallaxOn =
  !reducedMotion && typeof window !== 'undefined' && window.matchMedia('(pointer: fine)').matches

// 커서가 히어로를 벗어난 뒤에만 transition을 건다 — 추종 중에 걸면 매 프레임이 지연돼 끌린다
const parallaxResting = ref(false)

function onHeroMove(e: MouseEvent) {
  if (!parallaxOn) return
  if (parallaxResting.value) parallaxResting.value = false
  if (parallaxRaf !== 0) return
  parallaxRaf = requestAnimationFrame(() => {
    parallaxRaf = 0
    const host = heroEl.value
    if (!host) return
    const b = host.getBoundingClientRect()
    const dx = (e.clientX - b.left) / b.width - 0.5
    const dy = (e.clientY - b.top) / b.height - 0.5
    for (const f of fragEls) {
      const depth = Number(f.dataset.depth ?? '0')
      f.style.transform = `translate(${(dx * depth).toFixed(1)}px, ${(dy * depth).toFixed(1)}px)`
    }
  })
}

// 커서가 나가면 마지막 오프셋이 그대로 굳는다 — 원위치로 되돌린다
function onHeroLeave() {
  if (!parallaxOn) return
  if (parallaxRaf) {
    cancelAnimationFrame(parallaxRaf)
    parallaxRaf = 0
  }
  parallaxResting.value = true
  for (const f of fragEls) f.style.transform = ''
}

// ── 봉투 스테이지 — 스크롤 진행에 비례해 봉인이 풀리고 편지가 나와 확대된다 (GSAP, 핀 고정) ──
// 랜딩 스크롤러는 window가 아니라 <main class="landing">(overflow-y-auto)이다 — ScrollTrigger의
// scroller와 편지 확대 배율이 모두 이 요소를 기준으로 잡혀야 한다.
// 휠 하이재킹(ScrollToPlugin 스텝 스냅)은 채택하지 않는다 — 자유 스크롤 + scrub뿐이다.
// reduced-motion이면 타임라인을 아예 만들지 않고, CSS가 "개봉 완료" 정적 구도를 만든다.
const scrollerEl = ref<HTMLElement | null>(null)
const stageEl = ref<HTMLElement | null>(null)
let envCtx: ReturnType<typeof gsap.context> | null = null

function buildEnvelopeStage(stage: HTMLElement, scroller: HTMLElement) {
  envCtx = gsap.context(() => {
    const letter = stage.querySelector<HTMLElement>('.letter')
    if (!letter) return

    // 연출 전 상태는 CSS가 아니라 여기서 얹는다 — 마크업 기본값은 "펼쳐진 안감 + 본문이 보이는
    // 편지"고(겉은 닫힌 봉투가 덮는다), reduced-motion 분기가 그 기본값을 그대로 쓴다
    gsap.set('.flap-up', { scaleY: 0 })
    gsap.set('.letter-body', { autoAlpha: 0 })

    gsap
      .timeline({
        scrollTrigger: {
          trigger: stage,
          scroller,
          start: 'top top',
          // 핀 길이는 화면 높이에 비례해야 한다 — 고정 px이면 화면이 짧을수록 "안 내려가는" 구간이
          // 길어진다(2600px은 615px 화면에서 4.2화면, 랜드스케이프 폰에선 7.9화면이었다).
          // 2.2화면이 상한이다. 더 늘리면 스크롤이 막힌 것처럼 읽힌다.
          end: () => '+=' + scroller.clientHeight * 2.2,
          // 1이면 캐치업 꼬리가 1초라 프레임이 떨어질 때 뭉텅뭉텅 따라잡는 느낌이 난다
          scrub: 0.5,
          pin: true,
          anticipatePin: 1,
          invalidateOnRefresh: true, // 리사이즈 시 편지 확대 배율을 다시 잰다
          // 초대장을 다 읽고 핀을 빠져나가면 다음 화면으로 스르륵 이어 보낸다.
          // 핀 해제 직후는 화면 전환의 어중간한 중간 상태라, 여기서만 한 번 밀어준다 —
          // 빠른 플링으로 이미 반 화면 이상 지나쳤으면 사용자가 이긴 것이니 개입하지 않는다.
          onLeave: (self) => {
            if (scroller.scrollTop - self.end > scroller.clientHeight * 0.5) return
            gsap.to(scroller, {
              scrollTop: self.end + scroller.clientHeight,
              duration: 0.6,
              ease: 'power2.out',
              overwrite: true,
            })
          },
        },
        defaults: { ease: 'none' },
      })
      .to('.seal', { scale: 0.6, autoAlpha: 0, duration: 0.5 }, 0)
      // 실제 3D 회전은 90°까지만 — 90°에선 모로 서서 보이지 않는다
      .to(
        '.flap',
        { rotateX: 90, transformOrigin: '50% 0%', duration: 0.9, ease: 'power1.in' },
        0.2,
      )
      .set('.flap', { autoAlpha: 0 }, 1.1)
      // 열린 플랩(안감)이 힌지에서 위로 펼쳐진다 — 회전의 연속으로 읽힌다
      .fromTo('.flap-up', { scaleY: 0 }, { scaleY: 1, duration: 0.7, ease: 'power1.out' }, 1.1)
      .to('.stage-cap, .stage-note', { autoAlpha: 0, duration: 0.6 }, 1.4)
      .to('.letter', { yPercent: -66, force3D: false, duration: 1.6, ease: 'power1.inOut' }, 1.5)
      .set('.letter', { zIndex: 6 }, 2.6)
      .to('.envelope > :not(.letter)', { yPercent: 35, autoAlpha: 0, duration: 1.2 }, 2.9)
      .to(
        '.letter',
        {
          // 확대 종점은 스크롤러 정중앙 — 씬이 캡션 아래로 밀려 있어 고정 오프셋으론 어긋난다.
          // offset* 기반이라 transform과 무관하고, invalidateOnRefresh가 리사이즈마다 다시 잰다.
          yPercent: 0,
          y: () => {
            const scene = stage.querySelector<HTMLElement>('.scene')
            const naturalCenter =
              (scene?.offsetTop ?? 0) + letter.offsetTop + letter.offsetHeight / 2
            return scroller.clientHeight / 2 - naturalCenter
          },
          scale: () =>
            Math.min(
              scroller.clientWidth / letter.offsetWidth,
              scroller.clientHeight / letter.offsetHeight,
            ) * 0.92,
          // 3D 합성 레이어로 확대하면 1배 래스터 캐시가 늘어나 글자가 뭉개진다(사진처럼 보인 원인).
          // 2D 변환은 배율마다 재래스터라 페인트는 비싸지만 텍스트가 끝까지 선명하다.
          force3D: false,
          duration: 2,
          ease: 'power1.inOut',
        },
        3.1,
      )
      .to('.letter-head', { autoAlpha: 0, duration: 0.6 }, 4.4)
      .to('.letter-body', { autoAlpha: 1, duration: 0.8 }, 4.7)
      // 본문을 읽을 여유 구간 — 스크롤이 먹히는데 아무것도 안 움직이는 구간이라 짧게만 둔다
      .to({}, { duration: 0.3 })
  }, stage)
}

// ── 비크리 봉투 개봉 — 진입 시 4단 시퀀스, 이탈 시 되감기 (재진입하면 다시 재생) ──
// 정본 threshold .55는 섹션이 뷰포트보다 길어지는 모바일에서 영영 못 넘는다 → .35로 완화.
const vickEl = ref<HTMLElement | null>(null)
const vickStage = ref(0)
let vickTimers: number[] = []
let vickIo: IntersectionObserver | null = null

function vickReset() {
  vickTimers.forEach((t) => clearTimeout(t))
  vickTimers = []
  vickStage.value = 0
}
function vickPlay() {
  vickReset()
  if (reducedMotion) {
    vickStage.value = 4
    return
  }
  vickTimers = [250, 1050, 1850, 2600].map((ms, i) =>
    window.setTimeout(() => (vickStage.value = i + 1), ms),
  )
}

// ── 스크롤 리빌 — 요소는 마운트 후에만 숨겨지고, 교차 즉시 제자리로 돌아온다 ──
let revealIo: IntersectionObserver | null = null
const revealTimers = new Map<HTMLElement, number>()

function reveal(el: HTMLElement) {
  el.classList.remove('pre-up', 'pre-mock')
  const t = revealTimers.get(el)
  if (t !== undefined) {
    clearTimeout(t)
    revealTimers.delete(el)
  }
}
function getRevealIo(): IntersectionObserver {
  revealIo ??= new IntersectionObserver(
    (entries) =>
      entries.forEach((e) => {
        if (!e.isIntersecting) return
        reveal(e.target as HTMLElement)
        revealIo?.unobserve(e.target)
      }),
    { threshold: 0.25 },
  )
  return revealIo
}

const vReveal: Directive<HTMLElement, { delay?: number; kind?: 'mock' } | undefined> = {
  mounted(el, binding) {
    if (reducedMotion) return
    el.classList.add('rv', binding.value?.kind === 'mock' ? 'pre-mock' : 'pre-up')
    if (binding.value?.delay) el.style.transitionDelay = `${binding.value.delay}ms`
    getRevealIo().observe(el)
    // 보험 — 연출은 잃어도 콘텐츠는 잃지 않는다
    revealTimers.set(
      el,
      window.setTimeout(() => reveal(el), 3000),
    )
  },
  unmounted(el) {
    revealIo?.unobserve(el)
    const t = revealTimers.get(el)
    if (t !== undefined) clearTimeout(t)
    revealTimers.delete(el)
  },
}

// ── 배당 카드 — 교차하면 바가 차오르고 실수령 BP가 0→1,500으로 오른다 (정본 tween 1.1s) ──
const oddsEl = ref<HTMLElement | null>(null)
const oddsOn = ref(false)
let oddsIo: IntersectionObserver | null = null
const payout = useCountUp(() => (oddsOn.value ? 1500 : 0), { durationMs: 1100 })

onMounted(() => {
  fragEls = Array.from(heroEl.value?.querySelectorAll<HTMLElement>('[data-depth]') ?? [])

  if (!reducedMotion && stageEl.value && scrollerEl.value) {
    buildEnvelopeStage(stageEl.value, scrollerEl.value)
  }
  if (vickEl.value) {
    vickIo = new IntersectionObserver(
      (entries) => entries.forEach((e) => (e.isIntersecting ? vickPlay() : vickReset())),
      { threshold: 0.35 },
    )
    vickIo.observe(vickEl.value)
  }
  if (oddsEl.value) {
    oddsIo = new IntersectionObserver(
      (entries) =>
        entries.forEach((e) => {
          if (!e.isIntersecting) return
          oddsOn.value = true
          oddsIo?.disconnect()
        }),
      { threshold: 0.25 },
    )
    oddsIo.observe(oddsEl.value)
  }
})

onBeforeUnmount(() => {
  envCtx?.revert() // 컨텍스트 안에서 만든 트윈·ScrollTrigger·핀 스페이서를 한 번에 되돌린다
  if (parallaxRaf) cancelAnimationFrame(parallaxRaf)
  if (heroFallback) clearTimeout(heroFallback)
  vickTimers.forEach((t) => clearTimeout(t))
  vickIo?.disconnect()
  oddsIo?.disconnect()
  revealIo?.disconnect()
  revealTimers.forEach((t) => clearTimeout(t))
  revealTimers.clear()
})

function goSignup() {
  void router.push({ name: 'signup' })
}
</script>

<template>
  <!-- 전역 헤더(--header-h) 아래 한 화면을 스크롤러로 쓴다. 이 요소가 ScrollTrigger의 scroller다.
       CSS 스크롤 스냅은 쓰지 않는다 — ②의 핀 스페이서와 충돌한다. -->
  <main
    ref="scrollerEl"
    class="landing bg-cream h-[calc(100dvh-var(--header-h))] overflow-y-auto lg:snap-y lg:snap-proximity"
  >
    <!-- ① 히어로 — 어두운 무대. 부유하는 라이브 UI 조각 + 마스크 슬라이드업 타이틀 -->
    <section
      ref="heroEl"
      :class="{ hplay: heroPlay, 'parallax-rest': parallaxResting }"
      class="bg-stage relative flex min-h-full flex-col items-center justify-center overflow-hidden px-6 text-center text-white lg:snap-start"
      @mousemove="onHeroMove"
      @mouseleave="onHeroLeave"
    >
      <div class="hero-glow" aria-hidden="true"></div>

      <!-- 부유 카드 — 라이브 화면의 조각들. 장식이라 모바일에선 숨긴다 -->
      <div
        class="lx-frag hidden md:block"
        data-depth="22"
        style="top: 17%; left: 11%"
        aria-hidden="true"
      >
        <div class="frag-card">
          <div class="text-on-dark-dim text-[11px]">남은 시간</div>
          <div class="mt-0.5 text-[19px] font-bold tabular-nums">04:12</div>
        </div>
      </div>
      <div
        class="lx-frag hidden md:block"
        data-depth="34"
        style="top: 24%; right: 9%"
        aria-hidden="true"
      >
        <div class="frag-card" style="animation-delay: -3.1s">
          <div class="text-on-dark-dim text-[11px]">입찰 현황</div>
          <div class="mt-0.5 text-[19px] font-bold tabular-nums">12건 · 참여 9명</div>
        </div>
      </div>
      <div
        class="lx-frag hidden md:block"
        data-depth="28"
        style="bottom: 27%; left: 13%"
        aria-hidden="true"
      >
        <div class="frag-card" style="animation-delay: -2.1s">
          <div class="text-on-dark-dim text-[11px]">방금</div>
          <div class="mt-0.5 text-[13.5px] font-bold">누군가 베팅을 진행 중입니다! 🔥</div>
        </div>
      </div>
      <div
        class="lx-frag hidden md:block"
        data-depth="16"
        style="bottom: 19%; right: 12%"
        aria-hidden="true"
      >
        <div class="frag-card" style="animation-delay: -1.2s">
          <div class="text-on-dark-dim text-[11px]">내 입찰 — 나만 보여요</div>
          <div class="mt-0.5 text-[19px] font-bold tabular-nums">45,000P</div>
        </div>
      </div>

      <span class="text-on-dark-dim relative z-[2] inline-flex items-center gap-2 text-[13px]">
        <span class="ldot" aria-hidden="true"></span>실시간 비공개 경매 · BidLive
      </span>

      <h1
        class="relative z-[2] mt-5 text-[clamp(38px,6.4vw,84px)] leading-[1.13] font-bold tracking-[-0.03em] [text-wrap:balance]"
      >
        <span class="hero-line block overflow-hidden">
          <span class="block">당신이 얼마를 썼는지,</span>
        </span>
        <span class="hero-line block overflow-hidden">
          <span class="block">아무도 모릅니다</span>
        </span>
      </h1>

      <p class="hero-sub text-on-dark-dim relative z-[2] mt-[22px] text-base leading-[1.75]">
        서로의 입찰가가 보이지 않는 라이브 경매.<br />
        소신껏 지르세요 — 낙찰돼도 <b class="font-semibold text-white">2등 가격만</b> 냅니다.
      </p>

      <div class="hero-cta relative z-[2] mt-8 flex flex-wrap items-center justify-center gap-2.5">
        <RouterLink
          :to="{ name: 'signup' }"
          class="bg-terra hover:bg-terra-d rounded-xl px-[30px] py-[15px] text-[15px] font-semibold text-white transition"
        >
          3초 만에 시작하기 →
        </RouterLink>
        <RouterLink
          to="/rooms"
          class="rounded-xl border border-white/30 bg-white/5 px-[26px] py-[15px] text-[15px] font-semibold text-white transition hover:border-white/50 hover:bg-white/15"
        >
          라이브 구경하기
        </RouterLink>
      </div>

      <div
        class="text-on-dark-dim absolute bottom-[22px] left-1/2 z-[2] flex -translate-x-1/2 flex-col items-center gap-[7px] text-[11.5px]"
      >
        한 통의 봉투가 도착했습니다 — 스크롤로 열어보기<span class="chev" aria-hidden="true"></span>
      </div>
    </section>

    <!-- ② 봉투 스테이지 — 핀 고정. 스크롤 진행에 비례해 봉인이 풀리고 편지가 나와 확대된다.
         봉투 지오메트리는 데모 원본 그대로, 색만 중성 토큰(종이)/레드(왁스·안감·우표)로 매핑했다. -->
    <!-- 캡션은 absolute가 아니라 플로우에 둔다 — 615px 스크롤러에선 겹침이 표준 상태라서,
         겹친 흰 글자가 흰 봉투 위에 얹히면 안 보인다. 캡션이 공간을 소유하고 씬은 남은 공간에 뜬다. -->
    <section
      ref="stageEl"
      class="env-stage bg-stage relative flex min-h-full flex-col items-center overflow-hidden"
    >
      <div class="stage-vignette" aria-hidden="true"></div>

      <div class="stage-cap relative w-full px-6 pt-[7vh] text-center">
        <div class="stage-tag text-[12.5px] font-bold tracking-[0.08em]">비공개 비크리 경매</div>
        <h2
          class="mt-2.5 text-[clamp(24px,3.4vw,38px)] leading-[1.3] font-bold tracking-[-0.028em] [text-wrap:balance] text-white"
        >
          마감까지 아무도 모르고,<br />마감 후 봉투가 열립니다
        </h2>
      </div>

      <div class="scene">
        <div class="envelope">
          <div class="env-back grain" aria-hidden="true"></div>

          <!-- 편지 — 표지(letter-head)와 본문(letter-body)이 겹쳐 있다. 본문이 나중이라 기본은 본문이 보인다 -->
          <div class="letter grain">
            <div class="letter-face letter-head">
              <div class="crest" aria-hidden="true">B</div>
              <h3 class="text-[clamp(17px,2.6vw,23px)] leading-[1.5] font-bold tracking-[-0.02em]">
                BidLive 초대장
              </h3>
              <div class="letter-cap">INVITATION · LIVE AUCTION</div>
            </div>
            <div class="letter-face letter-body">
              <span class="rule" aria-hidden="true"></span>
              <h3 class="text-[clamp(19px,3vw,30px)] leading-[1.5] font-bold tracking-[-0.02em]">
                라이브 경매에<br />초대합니다
              </h3>
              <p class="text-dim max-w-[36em] text-[clamp(12.5px,1.7vw,15px)] leading-[1.9]">
                실물을 보면서, 물어보면서,<br />아무도 모르게 소신껏 지르는 경매 —<br />당신의
                자리가 준비되어 있습니다.
              </p>
              <span class="rule" aria-hidden="true"></span>
            </div>
          </div>

          <!-- 앞주머니 — 좌·우 날개와 아래 날개를 SVG로 정확히 겹친다 -->
          <div class="env-front grain" aria-hidden="true">
            <svg viewBox="0 0 160 100" preserveAspectRatio="none">
              <defs>
                <linearGradient id="lxWingL" x1="0" y1="0" x2="1" y2="0.3">
                  <stop offset="0" class="stop-p0" />
                  <stop offset="1" class="stop-p2" />
                </linearGradient>
                <linearGradient id="lxWingR" x1="1" y1="0" x2="0" y2="0.3">
                  <stop offset="0" class="stop-p2" />
                  <stop offset="1" class="stop-p3" />
                </linearGradient>
                <linearGradient id="lxPocket" x1="0" y1="1" x2="0" y2="0">
                  <stop offset="0" class="stop-p1" />
                  <stop offset="1" class="stop-p2" />
                </linearGradient>
              </defs>
              <path d="M0,0 L82,52 L0,100 Z" fill="url(#lxWingL)" />
              <path d="M160,0 L78,52 L160,100 Z" fill="url(#lxWingR)" />
              <path d="M0,100 L80,44 L160,100 Z" fill="url(#lxPocket)" />
              <path class="seam" d="M0,100 L80,44 L160,100" fill="none" stroke-width=".7" />
              <path class="seam-soft" d="M0,0 L80,51" fill="none" stroke-width=".6" />
              <path class="seam-soft" d="M160,0 L80,51" fill="none" stroke-width=".6" />
            </svg>
          </div>

          <!-- 봉투 표면 디테일 — 수신인·소인·우표 (아래쪽 절반이라 플랩에 안 가린다) -->
          <div class="env-deco" aria-hidden="true">
            <div class="addressee">
              <div class="to">입찰자 귀하</div>
              <div class="from">BIDLIVE · SEALED-BID AUCTION</div>
            </div>
            <div class="postmark">BIDLIVE<br />AUCTION<br />SEALED</div>
            <div class="stamp-mark">
              <div class="stamp-in">
                <span class="stamp-b">B</span><span class="stamp-p">2ND PRICE</span>
              </div>
            </div>
          </div>

          <div class="flap" aria-hidden="true">
            <div class="flap-face">
              <svg viewBox="0 0 160 53" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="lxFlap" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" class="stop-p0" />
                    <stop offset=".6" class="stop-p2" />
                    <stop offset="1" class="stop-p4" />
                  </linearGradient>
                </defs>
                <path d="M0,0 H160 L84,50 Q80,53 76,50 Z" fill="url(#lxFlap)" />
                <path
                  class="seam"
                  d="M0,0 L76,50 Q80,53 84,50 L160,0"
                  fill="none"
                  stroke-width=".6"
                />
              </svg>
            </div>
          </div>

          <!-- 열린 플랩 — 봉투 위로 펼쳐진 안감. 90° 지점에서 실제 플랩과 교대한다 -->
          <div class="flap-up" aria-hidden="true">
            <svg viewBox="0 0 160 53" preserveAspectRatio="none">
              <defs>
                <linearGradient id="lxLinerG" x1="0" y1="1" x2="0" y2="0">
                  <stop offset="0" class="stop-terra-d" />
                  <stop offset="1" class="stop-terra" />
                </linearGradient>
                <pattern
                  id="lxLinerP"
                  width="7"
                  height="7"
                  patternUnits="userSpaceOnUse"
                  patternTransform="rotate(45)"
                >
                  <rect width="7" height="7" fill="url(#lxLinerG)" />
                  <path class="liner-grid" d="M0 3.5 H7 M3.5 0 V7" stroke-width="1.1" />
                </pattern>
              </defs>
              <path class="liner-back" d="M0,53 H160 L84,3 Q80,0 76,3 Z" />
              <path d="M6,51 H154 L83,5.5 Q80,3.2 77,5.5 Z" fill="url(#lxLinerP)" />
            </svg>
          </div>

          <div class="seal" aria-hidden="true"><span class="seal-b">B</span></div>
        </div>
      </div>

      <div
        class="stage-note text-on-dark-dim absolute inset-x-0 bottom-[7%] text-center text-[12.5px]"
      >
        봉인은 마감까지 유지됩니다 — 계속 스크롤
      </div>
    </section>

    <!-- ③ 비크리 개봉 결과 — 최고가가 이기고 2등가만 낸다 (규칙 6·7 문구 유지) -->
    <section
      ref="vickEl"
      :class="{ s1: vickStage >= 1, s2: vickStage >= 2, s3: vickStage >= 3, s4: vickStage >= 4 }"
      class="mx-auto grid min-h-full w-full max-w-[1060px] grid-cols-1 content-center items-center gap-10 px-6 py-14 lg:snap-start lg:grid-cols-2 lg:gap-16 lg:py-0"
    >
      <div>
        <div class="text-terra text-[12.5px] font-bold tracking-[0.08em]">비공개 비크리 경매</div>
        <h2
          class="text-ink mt-3 text-[clamp(30px,3.4vw,40px)] leading-[1.3] font-bold tracking-[-0.028em] [text-wrap:balance]"
        >
          최고가가 이기고,<br />2등가만 냅니다
        </h2>
        <p class="text-dim mt-3.5 max-w-[46ch] text-[14.5px] leading-[1.8]">
          진행 중엔 입찰 건수만 보입니다. 종료되는 순간 전원의 화면에서 동시에 개봉 — 최고가를 쓴
          사람이 낙찰되고, 내는 돈은 <b class="text-ink">2등 가격</b>입니다.
        </p>
      </div>

      <div class="flex flex-col gap-3.5">
        <div class="lx-env e1">
          <span class="env-tag bg-line/45 text-dim">3위</span>
          <div class="text-dim text-xs">참여자 ●●3</div>
          <div class="mt-1 text-2xl font-bold tabular-nums">61,000P</div>
          <div class="veil" aria-hidden="true"><b>●●,●●●P</b>봉인된 입찰 봉투</div>
        </div>
        <div class="lx-env e2">
          <span class="env-tag bg-slate-tint text-slate-tint-text">2위 — 낙찰자가 내는 금액</span>
          <div class="text-dim text-xs">참여자 ●●7</div>
          <div class="mt-1 text-2xl font-bold tabular-nums">74,000P</div>
          <div class="veil" aria-hidden="true"><b>●●,●●●P</b>봉인된 입찰 봉투</div>
        </div>
        <div class="lx-env e3">
          <span class="env-tag bg-terra-tint text-terra-tint-text">1위 — 낙찰 🏆</span>
          <div class="text-dim text-xs">참여자 ●●1</div>
          <div class="mt-1 text-2xl font-bold tabular-nums">82,000P</div>
          <div class="veil" aria-hidden="true"><b>●●,●●●P</b>봉인된 입찰 봉투</div>
        </div>
        <div
          class="lx-stamp bg-terra self-start rounded-xl px-[18px] py-[13px] text-[15px] font-bold text-white tabular-nums"
        >
          낙찰자 지불액 — 2등가 74,000P
        </div>
      </div>
    </section>

    <!-- ④ 라이브 — 보면서, 물어보면서. 스테이지 목업 위로 Vibe 칩이 떠오른다 -->
    <section
      class="mx-auto flex min-h-full w-full max-w-[1060px] flex-col items-center justify-center px-6 py-14 text-center lg:snap-start lg:py-5"
    >
      <h2
        v-reveal
        class="text-ink text-[clamp(30px,3.4vw,40px)] leading-[1.3] font-bold tracking-[-0.028em] [text-wrap:balance]"
      >
        보면서, 물어보면서 지릅니다
      </h2>
      <p v-reveal="{ delay: 80 }" class="text-dim mt-3.5 text-[14.5px] leading-[1.8]">
        판매자가 실물을 라이브로 보여주고, 채팅으로 바로 답합니다.<br />
        타이머는 서버 동기화 — 전원이 같은 시간을 봅니다.
      </p>

      <div
        v-reveal="{ kind: 'mock' }"
        class="stagemock"
        role="img"
        aria-label="라이브 경매 방송 화면 목업"
      >
        <span
          class="bg-red absolute top-4 left-4 z-[2] inline-flex items-center gap-[5px] rounded-full px-2.5 py-1 text-[11px] font-bold text-white"
        >
          <span class="dot-blink" aria-hidden="true"></span>LIVE
        </span>
        <span
          class="bg-ink/80 text-on-dark absolute top-4 left-1/2 z-[2] -translate-x-1/2 rounded-full px-[15px] py-[7px] text-[12.5px] whitespace-nowrap tabular-nums"
        >
          남은 시간 <b class="text-white">04:12</b>
        </span>
        <span
          class="text-ink absolute right-4 bottom-4 z-[2] rounded-full bg-white/90 px-3.5 py-[7px] text-[12.5px] font-semibold"
        >
          Q. 뒷면도 보여주세요 — 답변 중
        </span>
        <div aria-hidden="true">
          <span class="vibe" style="--d: 0s">누군가 베팅을 진행 중입니다! 🔥</span>
          <span class="vibe" style="--d: 2s">새 입찰 +1건</span>
          <span class="vibe" style="--d: 4s">낙찰가 예측 +32명</span>
        </div>
      </div>
    </section>

    <!-- ⑤ 예측 베팅 — 구경만 해도 게임은 계속된다. 오차율 고정 배당 (규칙 7: 금액 입력 + ×5/×3/×1.5) -->
    <section
      class="mx-auto flex min-h-full w-full max-w-[1060px] flex-col justify-center px-6 py-14 lg:snap-start lg:py-5"
    >
      <div class="grid grid-cols-1 items-center gap-10 lg:grid-cols-2 lg:gap-16">
        <div v-reveal>
          <h2
            class="text-ink text-[clamp(30px,3.4vw,40px)] leading-[1.3] font-bold tracking-[-0.028em] [text-wrap:balance]"
          >
            구경만 해도,<br />게임은 계속됩니다
          </h2>
          <p class="text-dim mt-3.5 max-w-[46ch] text-[14.5px] leading-[1.8]">
            입찰이 부담스러우면 낙찰가를 예측하세요. 무료 재화
            <b class="text-slate">베팅 포인트</b>를 걸고, 오차가 작을수록 크게 돌려받습니다. 충전도
            환전도 없는 순수한 안목 게임.
          </p>
        </div>

        <div
          ref="oddsEl"
          v-reveal="{ delay: 120 }"
          :class="{ 'odds-in': oddsOn }"
          class="bg-surface border-line rounded-[20px] border p-6 shadow-[0_34px_80px_-34px_rgb(0_0_0/0.3)]"
        >
          <div class="flex items-baseline justify-between">
            <span class="text-dim text-[13px]">예측 47,000P · 스테이크 300BP</span>
            <span class="text-slate text-[40px] font-extrabold tabular-nums">
              +{{ formatPoint(payout, 'BETTING') }}
            </span>
          </div>
          <div class="mt-3.5">
            <div class="odds-row">
              <span class="text-dim">오차 5% 이내</span><b class="text-slate">×5</b>
              <span class="bar"><i style="--w: 88%; --d: 0.1s"></i></span>
              <b class="text-right tabular-nums">1,500BP</b>
            </div>
            <div class="odds-row">
              <span class="text-dim">10% 이내</span><b>×3</b>
              <span class="bar"><i style="--w: 55%; --d: 0.25s"></i></span>
              <span class="text-right tabular-nums">900BP</span>
            </div>
            <div class="odds-row">
              <span class="text-dim">20% 이내</span><b>×1.5</b>
              <span class="bar"><i style="--w: 22%; --d: 0.4s"></i></span>
              <span class="text-right tabular-nums">450BP</span>
            </div>
          </div>
          <div class="text-dim mt-3 text-[11.5px]">
            베팅 포인트(BP)는 무료 재화 — 충전·환전이 없습니다
          </div>
        </div>
      </div>

      <div
        v-reveal
        class="text-dim mt-[72px] flex flex-wrap justify-center gap-x-13 gap-y-4 text-[13px]"
      >
        <span>입찰 순간 <b class="text-ink">포인트 홀드</b> — 허위 입찰 원천 차단</span>
        <span>낙찰 대금은 <b class="text-ink">수령 확인 후</b> 판매자에게</span>
        <span>모든 증감은 <b class="text-ink">원장에 기록</b></span>
      </div>
    </section>

    <!-- ⑥ 피날레 — 테라 드렌치. 브랜드 색 위 반투명 검정, 워터마크 "2등가" -->
    <section
      class="lx-final relative flex min-h-full flex-col items-center justify-center overflow-hidden px-6 text-center text-white lg:snap-start"
    >
      <div class="final-water" aria-hidden="true">2등가</div>
      <h2
        v-reveal
        class="relative text-[clamp(38px,4.6vw,60px)] leading-[1.22] font-bold tracking-[-0.028em] [text-wrap:balance]"
      >
        이제, 진짜 가치를<br />알아볼 시간
      </h2>
      <p v-reveal="{ delay: 100 }" class="relative mt-[18px] text-base font-semibold text-white">
        가입 즉시 예측 베팅에 쓸 <b>1,000BP</b>를 드려요
      </p>
      <div v-reveal="{ delay: 200 }" class="relative w-full max-w-[330px]">
        <KakaoAuthButton label="카카오로 3초 만에 시작하기" @click="goSignup" />
      </div>
      <p v-reveal="{ delay: 280 }" class="relative mt-4 text-[13px] font-semibold text-white">
        이미 계정이 있다면
        <RouterLink to="/login" class="cursor-pointer font-bold underline">로그인</RouterLink>
      </p>
    </section>
  </main>
</template>

<style scoped>
/* ───────── 공용 리빌 — 기본은 보이는 상태, JS가 pre-*를 얹었다 떼어낸다 ───────── */
.rv {
  transition:
    opacity 0.8s var(--ease-out-quint),
    transform 0.8s var(--ease-out-quint);
}
.pre-up {
  opacity: 0;
  transform: translateY(30px);
}
.pre-mock {
  opacity: 0;
  transform: scale(0.93) translateY(26px);
}

/* ───────── ① 히어로 ───────── */
.hero-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(110% 80% at 50% 14%, rgb(255 255 255 / 0.07), transparent 46%),
    radial-gradient(130% 110% at 50% 60%, transparent 50%, rgb(0 0 0 / 0.55) 100%);
}
.ldot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-terra);
  box-shadow: 0 0 12px color-mix(in srgb, var(--color-terra) 90%, transparent);
  animation: lxblink 1.6s infinite;
}
@keyframes lxblink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

/* 타이틀 마스크 슬라이드업 — hplay가 붙으면 줄 단위로 올라온다. 둘째 줄은 반 박자 늦게 */
.hero-line > span {
  transform: translateY(112%);
  transition: transform 0.95s var(--ease-out-quint);
}
.hero-line:nth-child(2) > span {
  transition-delay: 0.14s;
}
.hplay .hero-line > span {
  transform: none;
}
.hero-sub,
.hero-cta {
  opacity: 0;
  transform: translateY(14px);
  transition:
    opacity 0.7s var(--ease-out-quint) 0.55s,
    transform 0.7s var(--ease-out-quint) 0.55s;
}
.hero-cta {
  transition-delay: 0.72s;
}
.hplay .hero-sub,
.hplay .hero-cta {
  opacity: 1;
  transform: none;
}

/* 부유 카드 — lfloat 부유 + (JS) 패럴랙스. 카드 자체는 반투명 잉크 위 블러 */
.lx-frag {
  position: absolute;
  z-index: 1;
  will-change: transform;
}
/* 커서가 히어로 밖으로 나갔을 때만 — 원위치 복귀를 부드럽게 (추종 중에는 걸지 않는다) */
.parallax-rest .lx-frag {
  transition: transform 520ms var(--ease-out-quint);
}
.frag-card {
  background: color-mix(in srgb, var(--color-ink) 72%, transparent);
  backdrop-filter: blur(12px);
  border: 1px solid rgb(255 255 255 / 0.13);
  border-radius: 14px;
  padding: 12px 16px;
  color: var(--color-on-dark);
  text-align: left;
  box-shadow: 0 18px 44px -14px rgb(0 0 0 / 0.6);
  animation: lfloat 5s ease-in-out infinite alternate;
}
@keyframes lfloat {
  from {
    transform: translateY(-7px);
  }
  to {
    transform: translateY(9px);
  }
}

.chev {
  width: 13px;
  height: 13px;
  border-right: 2px solid rgb(255 255 255 / 0.5);
  border-bottom: 2px solid rgb(255 255 255 / 0.5);
  transform: rotate(45deg);
  animation: chev 1.7s infinite;
}
@keyframes chev {
  0%,
  100% {
    opacity: 0.35;
    translate: 0 0;
  }
  50% {
    opacity: 1;
    translate: 0 5px;
  }
}

/* ───────── ② 봉투 스테이지 — 데모의 웜 테라코타 팔레트를 중성 토큰으로 매핑했다 ─────────
   종이 명암 사다리. @theme 토큰의 color-mix 파생일 뿐 새 토큰이 아니다(이 컴포넌트 로컬).
   실링왁스·안감·우표만 기능색 레드를 쓴다 — 규칙 1의 "따뜻한 색조를 섞지 않는다"를 지킨다. */
.env-stage {
  --paper-0: var(--color-surface);
  --paper-1: var(--color-cream);
  --paper-2: color-mix(in srgb, var(--color-line) 45%, var(--color-cream));
  --paper-3: var(--color-line);
  --paper-4: color-mix(in srgb, var(--color-line) 82%, var(--color-ink));
  --paper-seam: color-mix(in srgb, var(--color-ink) 24%, transparent);
  --paper-seam-soft: color-mix(in srgb, var(--color-ink) 15%, transparent);
}
.stage-vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    ellipse at 50% 62%,
    color-mix(in srgb, var(--color-terra) 12%, transparent),
    transparent 60%
  );
}
/* 순수 레드는 다크 스테이지 위에서 대비가 얕다 — on-dark를 섞어 한 단계 밝힌다 */
.stage-tag {
  color: color-mix(in srgb, var(--color-terra) 72%, var(--color-on-dark));
}
/* 좁고 낮은 화면에선 펼쳐진 플랩이 캡션 높이까지 올라온다 — 글자가 항상 위에 오게 못 박는다.
   연출 중엔 캡션이 먼저 autoAlpha 0(visibility:hidden)이라 편지를 가리지 않는다. */
.stage-cap,
.stage-note {
  z-index: 2;
}
.scene {
  position: relative;
  /* 높이가 폭에 종속(16/10)이라, 짧은 뷰포트(랜드스케이프 폰)에선 폭으로 높이 상한을 건다 —
     인플로우 캡션 몫 190px을 폭으로 환산(×1.6)하지 않으면 씬 바닥이 스테이지 밖으로 잘린다 */
  width: min(580px, 88%, calc((100dvh - var(--header-h) - 190px) * 1.6));
  aspect-ratio: 16 / 10;
  margin-block: auto; /* 캡션이 차지하고 남은 세로 공간의 중앙 */
}
/* perspective는 여기(.envelope)에 둔다 — 3D가 필요한 건 직계 자식 .flap의 rotateX뿐이다.
   데모처럼 .scene에 perspective + 여기에 preserve-3d를 주면 3D 렌더링 컨텍스트가 생겨
   바깥 .stage-cap의 z-index가 안 먹는다(캡션 둘째 줄이 봉투에 먹힌 원인). 평면 스태킹 유지. */
.envelope {
  position: absolute;
  inset: 0;
  perspective: 1600px;
}
/* 겉면 네 파트는 회전·이동·페이드만 한다(확대는 .letter뿐이라 여기엔 블러 대가가 없다).
   비싼 페인트를 안고 있어서 — .flap은 drop-shadow SVG, .env-deco는 소인 feTurbulence 마스크와
   우표 drop-shadow, .env-back은 70px 블러 그림자 — 승격 안 하면 매 프레임 그걸 다시 그린다 */
.env-back,
.env-front,
.env-deco,
.flap {
  will-change: transform, opacity;
}

/* 종이 질감 — feTurbulence 노이즈를 얇게 얹는다. 데모의 웜 매트릭스를 중성(0.3/0.3/0.3)으로 바꿨다.
   데모의 mix-blend-mode: multiply는 뺐다 — 블렌드가 걸린 레이어는 GPU 캐시가 안 돼서
   편지 확대 스크럽 매 프레임 재합성된다. 밝은 종이 위 저알파 노이즈라 일반 합성과 차이가 없다 */
.grain::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2'/%3E%3CfeColorMatrix values='0 0 0 0 0.3 0 0 0 0 0.3 0 0 0 0 0.3 0 0 0 0.05 0'/%3E%3C/filter%3E%3Crect width='240' height='240' filter='url(%23n)'/%3E%3C/svg%3E");
}

/* 봉투 안쪽 — 플랩이 열리면 보이는 면. 종이보다 한 톤 어두운 속지 */
.env-back {
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(0 0 0 / 0.28), transparent 46%),
    linear-gradient(165deg, var(--paper-2) 0%, var(--paper-3) 55%, var(--paper-4) 100%);
  box-shadow: 0 46px 70px -12px rgb(0 0 0 / 0.55);
}

.letter {
  position: absolute;
  left: 5.5%;
  right: 5.5%;
  top: 13%;
  bottom: 9%;
  z-index: 2;
  border-radius: 4px;
  overflow: hidden;
  background: var(--color-surface);
  color: var(--color-ink);
  text-align: center;
  box-shadow: 0 6px 24px rgb(0 0 0 / 0.28);
  /* will-change 승격 금지 — 승격하면 1배 크기로 래스터된 캐시를 2배로 늘려 그려서 글자가 뭉개진다.
     편지 트윈이 force3D: false라 2D 변환으로 남고, 배율이 바뀔 때마다 선명하게 재래스터된다. */
}
.letter::before {
  content: '';
  position: absolute;
  inset: 12px;
  z-index: 3;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--color-terra) 35%, transparent);
  border-radius: 2px;
}
/* 표지와 본문은 같은 자리에 겹친다. 배경이 불투명하고 본문이 나중이라, 봉투를 걷어내는
   reduced-motion 분기에선 본문이 읽힌다. 닫힌 봉투가 기본값이니 "JS 없이도 읽힌다"는 아니다 —
   편지는 초대 카피(장식)라 가려져도 잃는 정보가 없다. */
.letter-face {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 13px;
  padding: 26px;
  background: var(--color-surface);
}
/* 편지 높이는 뷰포트 폭에 종속인데 카피는 고정이라, ~350px 이하에선 여백을 줄여 잘림을 막는다 */
@media (max-width: 360px) {
  .letter-face {
    gap: 8px;
    padding: 12px;
  }
}
.crest {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1.5px solid var(--color-terra);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 800;
  color: var(--color-terra);
  box-shadow:
    inset 0 0 0 3px var(--color-surface),
    inset 0 0 0 4px color-mix(in srgb, var(--color-terra) 25%, transparent);
}
.letter-cap {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.1em;
  color: var(--color-dim);
}
.rule {
  flex: none;
  width: 52px;
  height: 1px;
  background: var(--color-terra);
}

.env-front {
  position: absolute;
  inset: 0;
  z-index: 3;
  border-radius: 8px;
  overflow: hidden;
  pointer-events: none;
}
.env-front svg,
.flap-face svg,
.flap-up svg {
  display: block;
  width: 100%;
  height: 100%;
}
/* SVG 색은 presentation attribute 대신 CSS로 — 그래야 토큰 var()가 모든 엔진에서 확실히 먹는다 */
.stop-p0 {
  stop-color: var(--paper-0);
}
.stop-p1 {
  stop-color: var(--paper-1);
}
.stop-p2 {
  stop-color: var(--paper-2);
}
.stop-p3 {
  stop-color: var(--paper-3);
}
.stop-p4 {
  stop-color: var(--paper-4);
}
.stop-terra {
  stop-color: var(--color-terra);
}
.stop-terra-d {
  stop-color: var(--color-terra-d);
}
.seam {
  stroke: var(--paper-seam);
}
.seam-soft {
  stroke: var(--paper-seam-soft);
}
.liner-back {
  fill: var(--paper-4);
}
.liner-grid {
  stroke: rgb(255 255 255 / 0.16);
}

.env-deco {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}
.addressee {
  position: absolute;
  left: 50%;
  top: 73%;
  transform: translate(-50%, -50%);
  text-align: center;
}
.addressee .to {
  font-size: clamp(15px, 2.4vw, 20px);
  font-weight: 700;
  letter-spacing: 0.32em;
  text-indent: 0.32em;
  color: var(--color-dim);
}
.addressee .from {
  margin-top: 7px;
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: var(--color-faint);
}
/* 소인 — 노이즈 마스크로 잉크가 닳은 도장처럼 찍힌다 (마스크는 무채색이라 그대로 가져왔다) */
.postmark {
  position: absolute;
  right: 13%;
  bottom: 17%;
  width: clamp(52px, 11%, 66px);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--color-ink) 42%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 7.5px;
  font-weight: 700;
  letter-spacing: 0.12em;
  line-height: 1.5;
  text-align: center;
  color: color-mix(in srgb, var(--color-ink) 48%, transparent);
  transform: rotate(-8deg);
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80'%3E%3Cfilter id='m'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.55' numOctaves='2'/%3E%3CfeColorMatrix values='0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1.6 -0.35'/%3E%3C/filter%3E%3Crect width='80' height='80' filter='url(%23m)'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='80' height='80'%3E%3Cfilter id='m'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.55' numOctaves='2'/%3E%3CfeColorMatrix values='0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1.6 -0.35'/%3E%3C/filter%3E%3Crect width='80' height='80' filter='url(%23m)'/%3E%3C/svg%3E");
}
.stamp-mark {
  position: absolute;
  right: 5.5%;
  bottom: 8%;
  width: clamp(44px, 9%, 56px);
  aspect-ratio: 4 / 5;
  padding: 4px;
  background: radial-gradient(circle, transparent 2.4px, var(--color-surface) 2.5px) -4px -4px / 8px
    8px;
  filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.18));
  transform: rotate(3deg);
}
.stamp-in {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--color-terra-tint);
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--color-terra) 86%, white),
    var(--color-terra) 60%,
    var(--color-terra-d)
  );
}
.stamp-b {
  font-size: clamp(15px, 2.4vw, 19px);
  font-weight: 800;
  line-height: 1;
}
.stamp-p {
  font-size: 7px;
  letter-spacing: 0.14em;
  opacity: 0.85;
}

.flap {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 55%;
  z-index: 4;
  transform-origin: top center;
  transform-style: preserve-3d;
}
.flap-face {
  position: absolute;
  inset: 0;
}
.flap-face svg {
  filter: drop-shadow(0 3px 4px rgb(0 0 0 / 0.16));
}
/* 열린 플랩 — 기본은 펼쳐진 상태고, 연출을 켤 때만 GSAP이 scaleY(0)으로 접어 둔다 */
.flap-up {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 100%;
  height: 55%;
  z-index: 1;
  transform-origin: 50% 100%;
}

/* 실링왁스 — 데모의 갈색 음영은 terra에 white/black을 섞어 만든다 */
.seal {
  position: absolute;
  left: 50%;
  top: 55%;
  z-index: 5;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%) rotate(-4deg);
  border-radius: 46% 54% 51% 49% / 53% 47% 56% 44%;
  background:
    radial-gradient(circle at 34% 28%, rgb(255 255 255 / 0.3), transparent 38%),
    radial-gradient(circle at 68% 78%, rgb(0 0 0 / 0.25), transparent 46%),
    radial-gradient(
      circle at 50% 50%,
      color-mix(in srgb, var(--color-terra) 88%, white) 30%,
      var(--color-terra-d) 62%,
      color-mix(in srgb, var(--color-terra-d) 74%, black) 100%
    );
  box-shadow:
    0 5px 12px rgb(0 0 0 / 0.4),
    inset 0 2px 3px rgb(255 255 255 / 0.22),
    inset 0 -3px 6px rgb(0 0 0 / 0.3);
}
.seal::before {
  content: '';
  position: absolute;
  inset: 7px;
  border-radius: inherit;
  border: 1.5px solid rgb(0 0 0 / 0.35);
  box-shadow: inset 0 1px 2px rgb(255 255 255 / 0.18);
}
.seal-b {
  font-size: 21px;
  font-weight: 800;
  color: var(--color-terra-tint);
  text-shadow:
    0 -1px 1px rgb(0 0 0 / 0.55),
    0 1px 1px rgb(255 255 255 / 0.25);
}

/* ───────── ③ 봉투 개봉 — s1~s3에 한 장씩 깨어나고 s4에 스탬프가 찍힌다 ───────── */
.lx-env {
  position: relative;
  background: var(--color-surface);
  border: 1px solid var(--color-line);
  border-radius: 16px;
  padding: 18px 20px;
  overflow: hidden;
  transform: translateY(18px);
  opacity: 0.5;
  transition:
    transform 0.6s var(--ease-out-quint),
    opacity 0.6s var(--ease-out-quint),
    border-color 0.4s,
    box-shadow 0.4s;
}
.env-tag {
  position: absolute;
  top: 16px;
  right: 16px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  padding: 4px 11px;
}
.veil {
  position: absolute;
  inset: 0;
  background: var(--color-stage);
  color: var(--color-on-dark-dim);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  font-size: 13px;
  transition: transform 0.7s var(--ease-out-quint);
}
.veil b {
  color: white;
  letter-spacing: 0.14em;
  font-size: 16px;
}
.s1 .e1,
.s2 .e2,
.s3 .e3 {
  transform: none;
  opacity: 1;
}
.s1 .e1 .veil,
.s2 .e2 .veil,
.s3 .e3 .veil {
  transform: translateY(-102%);
}
.s3 .e3 {
  border-color: var(--color-terra);
  box-shadow: 0 18px 44px -18px color-mix(in srgb, var(--color-terra) 35%, transparent);
}
/* 낙찰 도장 — 의미가 "찍힘"이라 정본의 오버슈트 이징을 그대로 둔다 */
.lx-stamp {
  margin-top: 6px;
  transform: scale(0.6) rotate(-6deg);
  opacity: 0;
  transition:
    transform 0.5s cubic-bezier(0.2, 1.4, 0.4, 1),
    opacity 0.3s;
}
.s4 .lx-stamp {
  transform: rotate(-3deg) scale(1);
  opacity: 1;
}

/* ───────── ④ 라이브 목업 ───────── */
.stagemock {
  position: relative;
  margin: 34px auto 0;
  width: 100%;
  max-width: min(820px, calc((100dvh - 340px) * 16 / 9));
  aspect-ratio: 16 / 9;
  border-radius: 22px;
  overflow: hidden;
  /* 마지막 레이어는 폴백 — picsum이 안 뜨면(오프라인·시연 중 네트워크 차단) 깨진 회색 박스 대신
     의도된 다크 스테이지로 읽힌다. 위에 얹은 LIVE 배지·타이머·질문 칩은 그대로 살아 있다. */
  background:
    linear-gradient(180deg, rgb(0 0 0 / 0.2), rgb(0 0 0 / 0.55)),
    url('https://picsum.photos/seed/bidlivestream/1280/760') center / cover,
    var(--color-stage);
  box-shadow: 0 50px 110px -40px rgb(0 0 0 / 0.55);
}
.dot-blink {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: white;
  animation: lxblink 1.4s infinite;
}
.vibe {
  position: absolute;
  left: 20px;
  bottom: -44px;
  background: color-mix(in srgb, var(--color-terra) 92%, transparent);
  color: white;
  font-weight: 600;
  font-size: 12.5px;
  border-radius: 999px;
  padding: 7px 14px;
  opacity: 0;
  animation: lxvibe 6s linear infinite;
  animation-delay: var(--d);
}
@keyframes lxvibe {
  0% {
    transform: translateY(0);
    opacity: 0;
  }
  8% {
    opacity: 1;
  }
  60% {
    opacity: 1;
  }
  78% {
    transform: translateY(-210px);
    opacity: 0;
  }
  100% {
    transform: translateY(-210px);
    opacity: 0;
  }
}

/* ───────── ⑤ 배당 카드 — odds-in이 붙으면 바가 --w까지 차오른다 ───────── */
.odds-row {
  display: grid;
  grid-template-columns: 86px 34px 1fr 78px;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  padding: 10px 0;
}
.bar {
  height: 7px;
  background: var(--color-line);
  border-radius: 4px;
  overflow: hidden;
}
.bar i {
  display: block;
  height: 100%;
  width: 0;
  background: var(--color-slate);
  border-radius: 4px;
  transition: width 1.1s var(--ease-out-quint) var(--d);
}
.odds-in .bar i {
  width: var(--w);
}

/* ───────── ⑥ 피날레 — 테라 위 반투명 검정 (밝은 빨강의 뜬금없음 없이 브랜드 색 유지) ───────── */
.lx-final {
  background: linear-gradient(rgb(0 0 0 / 0.52), rgb(0 0 0 / 0.52)), var(--color-terra);
}
.lx-final::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(110% 80% at 50% 20%, rgb(255 255 255 / 0.06), transparent 46%),
    radial-gradient(130% 110% at 50% 60%, transparent 50%, rgb(0 0 0 / 0.5) 100%);
}
.final-water {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -52%);
  font-weight: 800;
  font-size: clamp(200px, 30vw, 420px);
  letter-spacing: -0.02em;
  color: rgb(255 255 255 / 0.06);
  white-space: nowrap;
  pointer-events: none;
  user-select: none;
}

/* ───────── reduced motion — 항목별로: 루프는 정지, 전환은 즉시 ───────── */
@media (prefers-reduced-motion: reduce) {
  .ldot,
  .frag-card,
  .chev,
  .dot-blink {
    animation: none;
  }
  /* 봉투 스테이지 — GSAP 타임라인을 아예 만들지 않으므로 여기서 "개봉 완료" 정적 구도를 만든다.
     봉인·플랩과 편지를 덮는 앞주머니를 걷어내면 본문이 보이는 편지만 남는다.
     펼쳐진 안감(.flap-up)도 뺀다 — 안 그러면 영구히 떠 있는 캡션 첫 줄이 레드 안감 위에 얹힌다.
     스크롤을 재촉하는 안내문도 이 상태에선 거짓이라 뺀다. */
  .seal,
  .flap,
  .flap-up,
  .env-front,
  .env-deco,
  .stage-note {
    display: none;
  }
  .vibe {
    display: none; /* 장식 루프 — 정지 상태로 두면 하단에 겹쳐 쌓여 아예 숨긴다 */
  }
  .hero-line > span,
  .hero-sub,
  .hero-cta,
  .lx-env,
  .veil,
  .lx-stamp,
  .bar i,
  .rv {
    transition: none;
  }
}
</style>
