import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useMockDemoFlag } from './useMockDemoFlag'

// 쿼리 파싱 경계를 **여기 한 곳에서** 덮는다(!184 리뷰 2).
// 쓰는 쪽(ChatPanel·LiveStage) 테스트는 "쿼리를 켜면 뜬다"만 보면 되고, `?k`와 `?k=`의 차이는
// 이 헬퍼가 안다. 쓰는 쪽에 흩어 두면 픽스처가 실제 URL과 어긋나도 조용하다 — 실제로 그랬다:
// `?chatdemo`(등호 없음)를 `''`로 적어 뒀는데 vue-router는 그걸 `null`로 준다.
const query = ref<Record<string, string | null | (string | null)[]>>({})
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get query() {
        return query.value
      },
    }),
  }
})

const mock = ref(true)
vi.mock('@/shared/api/mock', () => ({ isMockApi: () => mock.value }))

/** composable은 setup 안에서만 useRoute()를 부를 수 있다 — 최소 컴포넌트로 감싼다. */
function read(key: string, defaultOn?: boolean): boolean {
  // withSetup 패턴 대신 직접 부른다 — 이 mock의 useRoute는 인젝션에 의존하지 않는다.
  return useMockDemoFlag(key, defaultOn).value
}

beforeEach(() => {
  query.value = {}
  mock.value = true
})

describe('useMockDemoFlag', () => {
  it('쿼리가 없으면 꺼져 있다', () => {
    expect(read('audiodemo')).toBe(false)
  })

  it('등호 없이 ?k만 붙여도 켜진다 — vue-router가 null로 준다', () => {
    // 손으로 치는 쿼리라 이 표기가 가장 흔하다. null과 ''를 혼동하면 조용히 안 켜진다.
    query.value = { audiodemo: null }
    expect(read('audiodemo')).toBe(true)
  })

  it('?k= (등호까지)도 켜진다', () => {
    query.value = { audiodemo: '' }
    expect(read('audiodemo')).toBe(true)
  })

  it('?k=1이면 켜진다', () => {
    query.value = { audiodemo: '1' }
    expect(read('audiodemo')).toBe(true)
  })

  it('?k=0은 끄는 표기다 — 쿼리를 지운 것과 같게', () => {
    query.value = { audiodemo: '0' }
    expect(read('audiodemo')).toBe(false)
  })

  it('실 모드에서는 쿼리를 켜도 항상 꺼져 있다 — 가드가 먼저 온다', () => {
    mock.value = false
    query.value = { audiodemo: '1' }
    expect(read('audiodemo')).toBe(false)
  })

  describe('defaultOn — 쿼리가 없을 때의 값 (S15P11A704-475)', () => {
    it('기본 켬이면 쿼리 없이도 켜진다', () => {
      // ?audiodemo가 이쪽이다 — 목 모드에 오디오 컨트롤이 원래 안 뜨는데 쿼리를 붙여야 보이면
      // 확인 비용이 위험보다 크다는 판단(475). 끄는 표기만 남긴다.
      expect(read('audiodemo', true)).toBe(true)
    })

    it('기본 켬이어도 ?k=0으로 끌 수 있다', () => {
      query.value = { audiodemo: '0' }
      expect(read('audiodemo', true)).toBe(false)
    })

    it('기본 켬이어도 실 모드에서는 꺼져 있다 — 가드가 defaultOn보다 먼저다', () => {
      mock.value = false
      expect(read('audiodemo', true)).toBe(false)
    })
  })

  it('키가 서로 간섭하지 않는다 — 트리거가 여러 개 공존한다', () => {
    query.value = { chatdemo: '1' }
    expect(read('chatdemo')).toBe(true)
    expect(read('audiodemo')).toBe(false)
  })
})
