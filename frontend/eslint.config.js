import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueTsConfig from '@vue/eslint-config-typescript'
import prettierConfig from 'eslint-config-prettier'

// Flat config (ESLint v9+). 서식 규칙은 전부 Prettier가 담당하고,
// ESLint는 "코드가 잘못됐는지"만 본다 — 마지막의 prettierConfig가 서식 규칙을 꺼준다.
export default [
  {
    // public/mediapipe는 @mediapipe/tasks-vision에서 그대로 복사한 벤더 자산이다(S15P11A704-699) —
    // emscripten 글루라 no-undef만 200개 넘게 나오고, 우리가 고칠 코드가 아니다.
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', '.claude/**', 'public/mediapipe/**'],
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsConfig(),

  {
    rules: {
      // 컴포넌트 파일명은 PascalCase 단어 하나여도 된다 (LiveView, RoomCard 등 — AGENTS.md 컨벤션)
      'vue/multi-word-component-names': 'off',

      // 미사용 변수는 경고. _ 접두사는 의도적 무시로 허용
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],

      // any는 경고 (금지까지는 아니지만 눈에 띄게)
      '@typescript-eslint/no-explicit-any': 'warn',

      // 디버깅 잔재 방지 — console.warn/error는 허용
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'warn',
    },
  },

  // 목 데이터·설정 파일에서는 console 사용을 허용
  {
    files: ['src/mocks/**', '*.config.{js,ts}', 'vite.config.ts'],
    rules: { 'no-console': 'off' },
  },

  prettierConfig,
]
