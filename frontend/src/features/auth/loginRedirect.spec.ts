import { beforeEach, describe, expect, it } from 'vitest'
import { rememberLoginRedirect, takeLoginRedirect } from './loginRedirect'

// 이 모듈은 **로그인 왕복 사이의 유일한 상태 전달자**다. 두 가지가 조용히 깨질 수 있다:
//   ① 꺼낸 뒤 비우지 않으면 다음 로그인이 이전 목적지로 간다
//   ② 외부 URL을 그대로 돌려주면 열린 리다이렉트가 된다 (인증 직후라 특히 위험하다)
// 둘 다 화면상으로는 "그냥 어디론가 이동한" 것처럼 보여서 눈으로 잡히지 않는다.

describe('loginRedirect', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('보관한 경로를 그대로 돌려준다', () => {
    rememberLoginRedirect('/rooms/1/live')
    expect(takeLoginRedirect()).toBe('/rooms/1/live')
  })

  it('꺼내면 비운다 — 다음 로그인이 이전 목적지를 물려받지 않는다', () => {
    rememberLoginRedirect('/me')
    expect(takeLoginRedirect()).toBe('/me')
    expect(takeLoginRedirect()).toBe('/')
  })

  it('보관한 적이 없으면 루트', () => {
    expect(takeLoginRedirect()).toBe('/')
  })

  it.each([
    ['외부 URL', 'https://evil.example.com/steal'],
    ['프로토콜 상대 경로', '//evil.example.com'],
    // 브라우저가 `\`를 `/`로 정규화해 `//evil.example.com`이 된다
    ['백슬래시 변형', '/\\evil.example.com'],
    ['백슬래시 두 개', '\\\\evil.example.com'],
    ['스킴', 'javascript:alert(1)'],
    ['상대 경로', 'rooms/1'],
  ])('%s는 보관하지 않는다', (_label, value) => {
    rememberLoginRedirect(value)
    expect(takeLoginRedirect()).toBe('/')
  })

  it.each([
    ['프로토콜 상대 경로', '//evil.example.com'],
    ['백슬래시 변형', '/\\evil.example.com'],
  ])('보관된 값이 나중에 %s로 오염돼도 꺼낼 때 다시 검증한다', (_label, value) => {
    // 세션 스토리지는 다른 스크립트도 쓸 수 있다 — 쓰기 시점 검증만 믿지 않는다
    sessionStorage.setItem('bidlive.loginRedirect', value)
    expect(takeLoginRedirect()).toBe('/')
  })

  it('쿼리가 붙은 경로도 유지한다', () => {
    rememberLoginRedirect('/rooms?state=ended')
    expect(takeLoginRedirect()).toBe('/rooms?state=ended')
  })
})
