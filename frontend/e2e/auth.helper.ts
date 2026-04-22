import { Page, expect } from '@playwright/test'
import axios from 'axios'

const KEYCLOAK_URL    = process.env.KEYCLOAK_URL    ?? 'http://localhost:8081'
const KEYCLOAK_REALM  = process.env.KEYCLOAK_REALM  ?? 'tradetracker'
const TEST_USER_EMAIL = process.env.TEST_USER_EMAIL  ?? 'e2e@tradetracker.test'
const TEST_USER_PASS  = process.env.TEST_USER_PASS   ?? 'E2eTest!2024'
const CLIENT_ID       = 'tradetracker-frontend'

/**
 * Fetches a Keycloak access token for the E2E test user via the password grant.
 * The test user must exist in the dev Keycloak realm.
 *
 * In CI, the test user is seeded by the keycloak-init job in docker-compose.test.yml.
 */
export async function getTestToken(): Promise<string> {
  const res = await axios.post(
    `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
    new URLSearchParams({
      grant_type: 'password',
      client_id:  CLIENT_ID,
      username:   TEST_USER_EMAIL,
      password:   TEST_USER_PASS,
      scope:      'openid',
    }),
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  )
  return res.data.access_token
}

/**
 * Injects an auth token directly into localStorage so Playwright tests
 * don't have to go through the Keycloak login UI on every test.
 *
 * Call this at the start of each test (or in beforeEach).
 */
export async function injectAuth(page: Page): Promise<void> {
  const token = await getTestToken()

  // Set the token in the format keycloak-js expects
  await page.addInitScript((t) => {
    window.__E2E_ACCESS_TOKEN__ = t
  }, token)
}

/**
 * Logs in via the Keycloak UI (slower but tests the real auth flow).
 * Use this for auth-specific tests only.
 */
export async function loginViaUI(page: Page): Promise<void> {
  await page.goto('/')
  await page.waitForURL('**/realms/tradetracker/**', { timeout: 10_000 })
  await page.fill('#username', TEST_USER_EMAIL)
  await page.fill('#password', TEST_USER_PASS)
  await page.click('#kc-login')
  await page.waitForURL('**/dashboard**', { timeout: 10_000 })
}
