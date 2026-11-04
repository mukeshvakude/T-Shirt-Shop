// Browser smoke test of the main journeys against a running shop (frontend :4200, backend :8080).
//
//   PUPPETEER_DIR=/folder/with/node_modules/puppeteer node e2e/smoke.mjs [baseUrl]
//
// Puppeteer is not a project dependency. Admin steps read ADMIN_EMAIL / ADMIN_PASSWORD from the
// repository .env (never printed). Test accounts are named e2e+<time>@example.com; remove them with
// `node e2e/cleanup.mjs`. Exit code 0 means every step passed.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(process.env.PUPPETEER_DIR ?? process.cwd(), 'package.json'));
const puppeteer = require('puppeteer');
const base = process.argv[2] ?? 'http://localhost:4200';
const env = Object.fromEntries(fs.readFileSync(path.join(root, '.env'), 'utf8').split(/\r?\n/)
  .filter((l) => /^[A-Z_]+=/.test(l)).map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const stamp = Date.now();
const email = `e2e+${stamp}@example.com`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
const consoleErrors = [];
const serverErrors = [];

const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.setViewport({ width: 1366, height: 900 });
page.on('console', (m) => { if (m.type() === 'error' && !/ERR_NAME_NOT_RESOLVED|status of 4\d\d/.test(m.text())) consoleErrors.push(m.text()); });
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));
page.on('response', (r) => { if (r.status() >= 500) serverErrors.push(`${r.status()} ${r.request().method()} ${new URL(r.url()).pathname}`); });
page.on('dialog', (d) => d.accept());

async function step(name, fn) {
  const started = Date.now();
  try {
    const note = await fn();
    results.push({ name, ok: true, note: note ?? '', ms: Date.now() - started });
    console.log(`PASS  ${name}${note ? ' - ' + note : ''}`);
  } catch (e) {
    results.push({ name, ok: false, note: e.message, ms: Date.now() - started });
    console.log(`FAIL  ${name} - ${e.message}`);
  }
}
const go = (p) => page.goto(base + p, { waitUntil: 'networkidle0' });
const text = (sel) => page.$eval(sel, (e) => e.textContent.replace(/\s+/g, ' ').trim());
async function click(sel, t) {
  const ok = await page.$$eval(sel, (els, x) => { const el = els.find((e) => e.textContent.trim().startsWith(x)) ?? els.find((e) => e.textContent.includes(x)); if (el) el.click(); return !!el; }, t);
  if (!ok) throw new Error(`nothing to click: ${sel} "${t}"`);
}
async function set(sel, value) {
  await page.$eval(sel, (el, v) => { el.value = v; el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); }, value);
}
async function noOverflow(label) {
  const px = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (px > 0) throw new Error(`${label}: page is ${px}px wider than the screen`);
}
function expect(cond, message) {
  if (!cond) throw new Error(message);
}

let orderNumber = null;

await step('Home page', async () => {
  await go('/');
  expect((await page.title()).includes('T-Shirt Shop'), 'title');
  expect(await page.$('.hero, .home'), 'home content');
});
await step('Listing and search', async () => {
  await go('/shop');
  await page.waitForSelector('.product-card');
  const n = (await page.$$('.product-card')).length;
  await go('/search?q=polo');
  await page.waitForSelector('.product-card');
  return `${n} products, search ok`;
});
await step('Product page, guest bag', async () => {
  await go('/product/essential-crew-neck-tee');
  await click('.sizes button', 'M');
  await click('.buy button', 'Add to bag');
  await page.waitForFunction(() => document.querySelector('a.bag')?.getAttribute('aria-label')?.includes('1 item'));
});
await step('Register keeps the guest bag', async () => {
  await go('/register?returnUrl=%2Fcart');
  await page.type('[formcontrolname=fullName]', 'Smoke Tester');
  await page.type('[formcontrolname=email]', email);
  await page.type('[formcontrolname=phone]', '9' + String(stamp).slice(-9));
  await page.type('[formcontrolname=password]', 'Secret123');
  await page.type('[formcontrolname=confirmPassword]', 'Secret123');
  await page.click('mat-checkbox input');
  await page.click('button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/cart');
  await page.waitForSelector('article.item');
  expect((await page.$$('article.item')).length === 1, 'bag line moved to the account');
});
await step('Designer: add a custom tee to the bag', async () => {
  await go('/customize');
  await page.waitForSelector('.stage canvas.upper-canvas');
  await page.waitForSelector('.stage .loading', { hidden: true });
  await sleep(400);
  await click('button', 'Add text');
  await page.waitForSelector('.text-tools');
  await set('.text-tools input[type=text]', 'SMOKE');
  await sleep(400);
  await click('.finish button', 'Add to bag');
  await page.waitForFunction(() => location.pathname === '/cart', { timeout: 30000 });
  await page.waitForSelector('article.item');
  expect((await page.$$('article.item')).length === 2, 'two lines in the bag');
  return await text('.summary .total');
});
await step('Checkout with Cash on Delivery', async () => {
  await click('.summary button', 'Checkout');
  await page.waitForFunction(() => location.pathname === '/checkout');
  await page.waitForSelector('.summary .total');
  await sleep(400);
  await set('[formcontrolname=line1]', '1 Test Street');
  await set('[formcontrolname=city]', 'Pune');
  await set('[formcontrolname=state]', 'Maharashtra');
  await set('[formcontrolname=pincode]', '411001');
  await page.$$eval('.option', (os) => os.find((o) => o.textContent.includes('Cash on Delivery')).querySelector('input').click());
  await sleep(200);
  await page.click('.summary button.primary');
  await page.waitForFunction(() => location.pathname.startsWith('/order/'), { timeout: 20000 });
  orderNumber = new URL(page.url()).pathname.split('/').pop();
  await page.waitForSelector('.hero h1');
  expect((await text('.hero h1')).includes('Thank you'), 'confirmation');
  return orderNumber;
});
await step('Order history and invoice PDF', async () => {
  await go('/account/orders');
  await page.waitForSelector('a.order');
  expect((await text('.list')).includes(orderNumber), 'order listed');
  await go('/order/' + orderNumber);
  await page.waitForSelector('.actions');
  const pdf = page.waitForResponse((r) => r.url().includes('/invoice'));
  await click('.actions button', 'Download invoice');
  const res = await pdf;
  const bytes = await res.buffer();
  expect(res.status() === 200 && bytes.subarray(0, 5).toString() === '%PDF-', 'PDF invoice');
  return `${bytes.length} bytes`;
});
await step('Customer cancels the order', async () => {
  await click('.actions button', 'Cancel order');
  await page.waitForSelector('app-cancel-order-dialog');
  await click('app-cancel-order-dialog button', 'Cancel order');
  await page.waitForSelector('app-cancel-order-dialog', { hidden: true });
  await sleep(1000);
  expect((await text('.hero h1')).includes('cancelled'), 'cancelled');
});
await step('Phone layout', async () => {
  await page.setViewport({ width: 390, height: 844, isMobile: true });
  for (const p of ['/', '/shop', '/product/essential-crew-neck-tee', '/cart', '/account/orders', '/order/' + orderNumber]) {
    await go(p);
    await sleep(300);
    await noOverflow(p);
  }
  await page.setViewport({ width: 1366, height: 900 });
});
await step('Admin portal', async () => {
  if (!env.ADMIN_EMAIL || !env.ADMIN_PASSWORD) throw new Error('ADMIN_EMAIL / ADMIN_PASSWORD missing in .env');
  await page.evaluate(() => fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }));
  await go('/login');
  await page.type('[formcontrolname=email]', env.ADMIN_EMAIL);
  await page.type('[formcontrolname=password]', env.ADMIN_PASSWORD);
  await page.click('button[type=submit]');
  await page.waitForFunction(() => location.pathname === '/admin/dashboard', { timeout: 20000 });
  await page.waitForSelector('.kpi');
  await go('/admin/orders?q=' + orderNumber.slice(12));
  await page.waitForSelector('.data-table tbody tr');
  expect((await text('.data-table tbody')).includes(orderNumber), 'order in admin list');
  for (const p of ['/admin/payments', '/admin/customers', '/admin/messages', '/admin/products', '/admin/designs', '/admin/pricing', '/admin/inventory']) {
    await go(p);
    await page.waitForSelector('h1');
  }
  return 'dashboard, orders, payments, customers, messages, catalog pages';
});
await step('No browser or server errors', async () => {
  expect(consoleErrors.length === 0, 'console: ' + consoleErrors.slice(0, 3).join(' | '));
  expect(serverErrors.length === 0, 'server: ' + [...new Set(serverErrors)].join(' | '));
});

await browser.close();
const failedSteps = results.filter((r) => !r.ok);
console.log(`\n${results.length - failedSteps.length}/${results.length} steps passed. Test account: ${email}`);
process.exit(failedSteps.length ? 1 : 0);
