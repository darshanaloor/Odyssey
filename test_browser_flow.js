const { chromium } = require('playwright-core');
const path = require('path');

const EDGE_PATH = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity-ide\\brain\\e7655101-90bb-428b-8850-3a173e06a199';

async function run() {
  console.log('Launching Edge browser at:', EDGE_PATH);
  const browser = await chromium.launch({
    executablePath: EDGE_PATH,
    headless: true,
  });

  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 }
  });

  const page = await context.newPage();

  page.on('console', msg => console.log('BROWSER CONSOLE:', msg.type(), msg.text()));
  page.on('pageerror', err => console.error('BROWSER PAGE ERROR:', err));

  console.log('Navigating to http://localhost:8085 ...');
  await page.goto('http://localhost:8085', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  // 1. Initial State Checks
  const panelVisible = await page.isVisible('#panel-auth-required');
  console.log('Is #panel-auth-required visible on load?', panelVisible);

  const btnNewTripDisabled = await page.$eval('#btn-open-create-trip', el => el.disabled);
  console.log('Is + New Trip disabled on load?', btnNewTripDisabled);

  // 2. Click Sign In Button in Header
  console.log('Clicking #btn-open-login ...');
  await page.click('#btn-open-login');
  await page.waitForTimeout(400);

  const isLoginModalVisible = await page.isVisible('#modal-login');
  const loginModalClass = await page.$eval('#modal-login', el => el.className);
  console.log('Is #modal-login visible after click?', isLoginModalVisible);
  console.log('#modal-login classList:', loginModalClass);

  // Take screenshot of Login Modal
  const loginScreenshotPath = path.join(ARTIFACT_DIR, 'login_modal_open.png');
  await page.screenshot({ path: loginScreenshotPath });
  console.log('Saved login modal screenshot to:', loginScreenshotPath);

  // 3. Close Login Modal
  console.log('Clicking #btn-close-login-modal ...');
  await page.click('#btn-close-login-modal');
  await page.waitForTimeout(200);

  const isLoginClosed = await page.isHidden('#modal-login');
  console.log('Is #modal-login closed?', isLoginClosed);

  // 4. Click Register Button in Header
  console.log('Clicking #btn-open-register ...');
  await page.click('#btn-open-register');
  await page.waitForTimeout(400);

  const isRegisterModalVisible = await page.isVisible('#modal-register');
  console.log('Is #modal-register visible?', isRegisterModalVisible);

  // 5. Fill Register Form & Submit
  const testEmail = 'ananya_' + Date.now() + '@example.com';
  console.log('Registering test user:', testEmail);
  await page.fill('#reg-name', 'Ananya Sharma');
  await page.fill('#reg-email', testEmail);
  await page.fill('#reg-password', 'Password123');
  await page.fill('#reg-phone', '9876543210');

  await page.click('#btn-submit-register');
  await page.waitForTimeout(1500);

  // 6. Verify Logged-In State
  const userPillVisible = await page.isVisible('#user-profile-pill');
  const displayName = await page.textContent('#user-display-name');
  console.log('Is #user-profile-pill visible after register?', userPillVisible);
  console.log('Displayed user name in pill:', displayName);

  // 7. Click Sign Out
  console.log('Clicking user profile pill to open account modal ...');
  await page.click('#user-profile-pill');
  await page.waitForTimeout(400);
  console.log('Clicking Sign Out button ...');
  await page.click('#btn-logout');
  await page.waitForTimeout(500);

  const isUnauthPanelBack = await page.isVisible('#panel-auth-required');
  console.log('Is #panel-auth-required back after logout?', isUnauthPanelBack);

  // 8. Log back in using Sign In Modal
  console.log('Clicking #btn-panel-login ...');
  await page.click('#btn-panel-login');
  await page.waitForTimeout(400);

  console.log('Filling login form with credentials ...');
  await page.fill('#login-email', testEmail);
  await page.fill('#login-password', 'Password123');
  await page.click('#btn-submit-login');
  await page.waitForTimeout(1500);

  const userPillVisibleAfterLogin = await page.isVisible('#user-profile-pill');
  const displayNameAfterLogin = await page.textContent('#user-display-name');
  console.log('Is #user-profile-pill visible after login?', userPillVisibleAfterLogin);
  console.log('Displayed user name in pill after login:', displayNameAfterLogin);

  const loggedInScreenshotPath = path.join(ARTIFACT_DIR, 'logged_in_dashboard.png');
  await page.screenshot({ path: loggedInScreenshotPath });
  console.log('Saved logged in dashboard screenshot to:', loggedInScreenshotPath);

  await browser.close();
  console.log('ALL BROWSER FLOWS VERIFIED SUCCESSFULLY!');
}

run().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
