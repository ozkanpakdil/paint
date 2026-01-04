import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.js'],
    deps: {
      inline: ['vitest-canvas-mock'],
    },
  },
})
