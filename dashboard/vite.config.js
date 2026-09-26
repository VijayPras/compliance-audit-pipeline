import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Plain Vite + React setup, no extra tooling -- the dashboard is a thin
// real-time view, it doesn't need a framework beyond React + SSE.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
})
