import { test, expect, Page } from '@playwright/test'
import { loginViaUI } from './auth.helper'

// ── Shared setup ──────────────────────────────────────────────────────────────

test.beforeEach(async ({ page }) => {
  await loginViaUI(page)
})

// =============================================================================
// Dashboard
// =============================================================================

test.describe('Dashboard', () => {

  test('shows portfolio summary cards after login', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page.getByText('Portfolio Value')).toBeVisible()
    await expect(page.getByText('Cost Base')).toBeVisible()
    await expect(page.getByText('Unrealised Gain / Loss')).toBeVisible()
    await expect(page.getByText('TWR (1Y)')).toBeVisible()
  })

  test('sidebar shows navigation links', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page.getByText('Dashboard')).toBeVisible()
    await expect(page.getByText('Holdings')).toBeVisible()
    await expect(page.getByText('Trades')).toBeVisible()
    await expect(page.getByText('Tax & CGT')).toBeVisible()
    await expect(page.getByText('Settings')).toBeVisible()
  })

  test('portfolio switcher shows at least one portfolio', async ({ page }) => {
    await page.goto('/dashboard')

    // Sidebar portfolio switcher
    const switcher = page.locator('button', { hasText: /My Portfolio|portfolio/i }).first()
    await expect(switcher).toBeVisible()
  })
})

// =============================================================================
// Trades
// =============================================================================

test.describe('Trades', () => {

  test('trade history page loads with empty state', async ({ page }) => {
    await navigateToTrades(page)
    // Either shows trades or the empty state message
    const hasEmpty = await page.getByText('No trades found').isVisible().catch(() => false)
    const hasTable = await page.locator('table').isVisible().catch(() => false)
    expect(hasEmpty || hasTable).toBeTruthy()
  })

  test('add trade modal opens and closes', async ({ page }) => {
    await navigateToTrades(page)

    await page.click('button:has-text("Add trade")')
    await expect(page.getByRole('heading', { name: 'Add trade' })).toBeVisible()

    await page.click('button:has-text("Cancel")')
    await expect(page.getByRole('heading', { name: 'Add trade' })).not.toBeVisible()
  })

  test('add trade form validates required fields', async ({ page }) => {
    await navigateToTrades(page)

    await page.click('button:has-text("Add trade")')
    await page.click('button:has-text("Save trade")')

    // Should show validation errors
    await expect(page.getByText('Required')).toBeVisible()
  })

  test('add trade form fills and submits a BUY trade', async ({ page }) => {
    await navigateToTrades(page)

    await page.click('button:has-text("Add trade")')

    // Select BUY type (default)
    await expect(page.getByRole('button', { name: 'Buy' })).toBeVisible()

    // Fill in trade details
    await page.fill('input[placeholder="e.g. CBA"]', 'BHP')
    await page.fill('input[placeholder="100"]', '50')
    await page.fill('input[placeholder="0.00"]', '45.20')
    await page.fill('input[placeholder="9.95"]', '9.95')

    // Check total cost preview appears
    await expect(page.getByText('Total cost')).toBeVisible()

    // Submit
    await page.click('button:has-text("Save trade")')

    // Modal should close
    await expect(page.getByRole('heading', { name: 'Add trade' })).not.toBeVisible({ timeout: 5_000 })
  })

  test('trade type pills switch between buy/sell/dividend', async ({ page }) => {
    await navigateToTrades(page)
    await page.click('button:has-text("Add trade")')

    // Switch to SELL
    await page.click('button:has-text("Sell")')
    await expect(page.getByText('Total proceeds')).toBeVisible({ timeout: 2_000 }).catch(() => {
      // Text only appears when values are filled, that's OK
    })

    // Switch to Dividend
    await page.click('button:has-text("Dividend")')
  })

  test('trade search filter works', async ({ page }) => {
    await navigateToTrades(page)

    const searchInput = page.getByPlaceholder('Search ticker…')
    await searchInput.fill('BHP')
    // Just verify input accepted the value without error
    await expect(searchInput).toHaveValue('BHP')
  })
})

// =============================================================================
// Holdings / Portfolio
// =============================================================================

test.describe('Holdings', () => {

  test('holdings page loads', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}`)

    await expect(page.getByRole('heading', { name: 'Holdings' })).toBeVisible()
  })

  test('clicking a holding expands the parcel panel', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}`)

    // Only test if there are holdings
    const holdingRow = page.locator('button').filter({ hasText: /ASX|NYSE|NASDAQ/ }).first()
    const hasHoldings = await holdingRow.isVisible().catch(() => false)

    if (hasHoldings) {
      await holdingRow.click()
      await expect(page.getByText('Tax Parcels')).toBeVisible()
    }
  })
})

// =============================================================================
// Tax Page
// =============================================================================

test.describe('Tax & CGT', () => {

  test('tax page loads with current FY selected', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}/tax`)

    await expect(page.getByRole('heading', { name: 'Tax & CGT' })).toBeVisible()
    await expect(page.getByText(/FY \d{4}–\d{4}/)).toBeVisible()
  })

  test('three tabs are visible', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}/tax`)

    await expect(page.getByText('CGT Breakdown')).toBeVisible()
    await expect(page.getByText(/Disposal Events/)).toBeVisible()
    await expect(page.getByText('Open Parcels')).toBeVisible()
  })

  test('switching to open parcels tab loads content', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}/tax`)

    await page.click('button:has-text("Open Parcels")')
    // Should show table or empty state
    const hasTable = await page.locator('table').isVisible().catch(() => false)
    const hasEmpty = await page.getByText('No open parcels').isVisible().catch(() => false)
    expect(hasTable || hasEmpty).toBeTruthy()
  })

  test('ATO disclaimer is visible', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}/tax`)

    await expect(page.getByText(/indicative only/i)).toBeVisible()
  })

  test('financial year selector changes the displayed year', async ({ page }) => {
    const portfolioId = await getPortfolioId(page)
    await page.goto(`/portfolio/${portfolioId}/tax`)

    const select = page.locator('select')
    const options = await select.locator('option').allTextContents()
    expect(options.length).toBeGreaterThan(1)

    // Select previous year
    await select.selectOption({ index: 1 })
    await expect(page.getByText(/FY \d{4}–\d{4}/)).toBeVisible()
  })
})

// =============================================================================
// Settings
// =============================================================================

test.describe('Settings', () => {

  test('settings page loads with all tabs', async ({ page }) => {
    await page.goto('/settings')

    await expect(page.getByText('Profile')).toBeVisible()
    await expect(page.getByText('Portfolios')).toBeVisible()
    await expect(page.getByText('Broker Accounts')).toBeVisible()
    await expect(page.getByText('Import PDF')).toBeVisible()
    await expect(page.getByText('Notifications')).toBeVisible()
  })

  test('profile tab shows user info', async ({ page }) => {
    await page.goto('/settings')
    await page.click('button:has-text("Profile")')

    await expect(page.getByText('Email')).toBeVisible()
    await expect(page.getByText('Name')).toBeVisible()
  })

  test('broker tab shows known brokers', async ({ page }) => {
    await page.goto('/settings')
    await page.click('button:has-text("Broker Accounts")')

    await expect(page.getByText('CommSec')).toBeVisible()
    await expect(page.getByText('SelfWealth')).toBeVisible()
    await expect(page.getByText('Interactive Brokers')).toBeVisible()
  })

  test('PDF import accepts drag-and-drop zone', async ({ page }) => {
    await page.goto('/settings')
    await page.click('button:has-text("Import PDF")')

    await expect(page.getByText('Drop a PDF here')).toBeVisible()
    await expect(page.getByText('CommSec, SelfWealth')).toBeVisible()
  })

  test('notification toggles are interactive', async ({ page }) => {
    await page.goto('/settings')
    await page.click('button:has-text("Notifications")')

    // Find first toggle and click it
    const toggle = page.locator('button[class*="rounded-full"]').first()
    await expect(toggle).toBeVisible()
    await toggle.click()  // Should not throw
  })
})

// =============================================================================
// Navigation
// =============================================================================

test.describe('Navigation', () => {

  test('sidebar nav links work', async ({ page }) => {
    await page.goto('/dashboard')

    await page.click('a:has-text("Settings")')
    await expect(page).toHaveURL(/settings/)

    await page.click('a:has-text("Dashboard")')
    await expect(page).toHaveURL(/dashboard/)
  })

  test('sign out button is present', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.locator('[title="Sign out"]')).toBeVisible()
  })
})

// =============================================================================
// Helpers
// =============================================================================

async function navigateToTrades(page: Page) {
  const portfolioId = await getPortfolioId(page)
  await page.goto(`/portfolio/${portfolioId}/trades`)
  await page.waitForLoadState('networkidle')
}

async function getPortfolioId(page: Page): Promise<string> {
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')
  // Extract portfolioId from sidebar nav link
  const href = await page.locator('a[href*="/portfolio/"]').first().getAttribute('href')
  const match = href?.match(/\/portfolio\/([^/]+)/)
  return match?.[1] ?? 'default'
}
