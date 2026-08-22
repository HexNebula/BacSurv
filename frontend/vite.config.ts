import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The built files are copied into the Spring jar at package time, so the
// application ships as one artifact: nothing extra to install at a lycée, and
// no cross-origin setup because everything is served from the same port.
// In development Vite serves the pages and forwards the API to Spring.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
