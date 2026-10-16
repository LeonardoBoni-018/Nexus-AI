import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const isWin = process.platform === 'win32'
const backendDir = resolve(fileURLToPath(new URL('.', import.meta.url)), '../backend')
const mvnw = isWin ? `"${resolve(backendDir, 'mvnw.cmd')}"` : resolve(backendDir, 'mvnw')

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure' },
  webServer: [
    { command: 'node e2e/mock-llm.mjs', url: 'http://127.0.0.1:4010/health', reuseExistingServer: false, timeout: 15_000, stdout: 'ignore' },
    {
      // Backend real com H2 em memoria, seed demo e IA apontando para o provedor simulado.
      command: `${mvnw} -q -B spring-boot:run -Dspring-boot.run.profiles=local`,
      cwd: backendDir,
      url: 'http://127.0.0.1:8080/actuator/health',
      timeout: 240_000,
      reuseExistingServer: false,
      env: {
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        AI_PROVIDER: 'openai',
        AI_MODEL: 'mock-model',
        AI_API_KEY: 'e2e-not-a-real-key',
        AI_BASE_URL: 'http://127.0.0.1:4010',
      },
    },
    { command: 'npx vite --host 127.0.0.1', url: 'http://127.0.0.1:5173', reuseExistingServer: false, timeout: 60_000, env: { VITE_API_PROXY: 'http://127.0.0.1:8080' } },
  ],
})
