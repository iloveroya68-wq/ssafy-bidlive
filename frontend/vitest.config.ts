import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig } from 'vitest/config'
// `tsconfig.node.json`이 module: nodenext라 확장자 없는 임포트가 해석되지 않는다.
// 같은 tsconfig의 allowImportingTsExtensions가 켜져 있어 `.ts`를 명시하는 게 정답이다.
import viteConfig from './vite.config.ts'

// vite.config.ts를 그대로 물려받는다 — `@` 별칭과 `.vue` SFC 트랜스폼이 여기서 온다.
// 별도로 다시 선언하면 두 곳이 갈라지므로 mergeConfig로 얹기만 한다.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      // 컴포넌트를 마운트하려면 DOM이 필요하다. 이게 없으면 `document is not defined`로 죽는다.
      environment: 'jsdom',
      // 테스트 파일은 소스 옆에 `*.spec.ts`로 둔다 (별도 tests/ 디렉터리를 만들지 않는다).
      include: ['src/**/*.spec.ts'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      // **테스트는 항상 목이다.** Vite가 `.env.local`을 읽으므로, 실 서버 연동 확인을 하느라
      // `VITE_API_MOCK=false`를 켜 두면 테스트가 조용히 `localhost:8080`으로 나간다 —
      // 38건이 `Network Error`로 떨어지고 원인이 코드처럼 보인다(실제로 겪었다).
      // 로컬 파일이 테스트 결과를 바꾸면 안 되므로 여기서 고정한다.
      env: { VITE_API_MOCK: 'true' },
    },
  }),
)
