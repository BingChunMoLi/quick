import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: { outDir: '../../../target/classes/codegen-ui', emptyOutDir: true },
  server: { proxy: { '/moli-codegen/api': 'http://127.0.0.1:8088' } },
})
