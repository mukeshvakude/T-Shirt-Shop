// Generates the sample product illustrations used by the development catalog
// (Flyway V900 points at /assets/products/<slug>-<colour>.jpg) plus home page art
// and blank designer mockups. Replace them with real photography before launch.
//
// Usage (needs Puppeteer once, it is not a project dependency):
//   PUPPETEER_DIR=/path/to/a/folder/with/node_modules/puppeteer node scripts/generate-sample-images.mjs
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'assets');
const require = createRequire(path.join(process.env.PUPPETEER_DIR ?? process.cwd(), 'package.json'));
const puppeteer = require('puppeteer');

// Colours from Flyway V2 (black/charcoal lifted slightly so folds stay visible).
const COLORS = {
  WHITE: '#f5f4f0', BLACK: '#1d1d1f', RED: '#c62828', BLUE: '#3d5f8f', GREEN: '#2e7d32', YELLOW: '#f9c80e',
  NAVY: '#1f2a48', GREY: '#8e8e8e', MAROON: '#6d1a2a', OLIVE: '#556b2f', BEIGE: '#d9c8a9', SKY_BLUE: '#8ec5ea',
  KHAKI: '#b5a27a', CHARCOAL: '#3a4650',
};

// ------------------------------------------------------------------ garment shapes (viewBox 800 x 1000)
const shapes = {
  crew: {
    body: 'M310,195 C350,240 450,240 490,195 L615,240 L690,400 L595,445 L560,380 L555,820 Q400,835 245,820 L240,380 L205,445 L110,400 L185,240 Z',
    trim: '<path d="M310,195 C350,240 450,240 490,195" fill="none" stroke="TRIM" stroke-width="14" stroke-linecap="round"/>',
    folds: 'M300,500 Q330,620 290,760 M500,470 Q470,600 515,770 M400,560 Q410,650 395,780',
  },
  vneck: {
    body: 'M310,195 L400,290 L490,195 L615,240 L690,400 L595,445 L560,380 L555,820 Q400,835 245,820 L240,380 L205,445 L110,400 L185,240 Z',
    trim: '<path d="M310,195 L400,290 L490,195" fill="none" stroke="TRIM" stroke-width="14" stroke-linejoin="round"/>',
    folds: 'M300,500 Q330,620 290,760 M500,470 Q470,600 515,770',
  },
  polo: {
    body: 'M320,190 C360,215 440,215 480,190 L615,240 L690,400 L595,445 L560,380 L555,820 Q400,835 245,820 L240,380 L205,445 L110,400 L185,240 Z',
    trim: `<path d="M320,190 L370,250 L400,215 Z M480,190 L430,250 L400,215 Z" fill="BASE" stroke="TRIM" stroke-width="5" stroke-linejoin="round"/>
           <rect x="384" y="215" width="32" height="125" rx="4" fill="BASE" stroke="TRIM" stroke-width="4"/>
           <circle cx="400" cy="258" r="6" fill="TRIM"/><circle cx="400" cy="300" r="6" fill="TRIM"/>
           <path d="M205,445 L110,400" stroke="TRIM" stroke-width="10"/><path d="M595,445 L690,400" stroke="TRIM" stroke-width="10"/>`,
    folds: 'M300,500 Q330,620 290,760 M500,470 Q470,600 515,770',
  },
  oversized: {
    body: 'M315,190 C355,235 445,235 485,190 L635,265 L705,470 L585,505 L570,420 L585,860 Q400,872 215,860 L230,420 L215,505 L95,470 L165,265 Z',
    trim: '<path d="M315,190 C355,235 445,235 485,190" fill="none" stroke="TRIM" stroke-width="16" stroke-linecap="round"/>',
    folds: 'M290,520 Q320,660 280,820 M510,500 Q480,650 525,830 M150,330 Q180,380 165,450 M650,330 Q620,380 635,450',
  },
  shirt: {
    body: 'M330,190 L195,235 L125,745 L195,762 L245,400 L250,840 Q400,880 550,840 L555,400 L605,762 L675,745 L605,235 L470,190 Z',
    trim: `<path d="M330,190 L400,252 L362,292 L316,214 Z M470,190 L400,252 L438,292 L484,214 Z" fill="BASE" stroke="TRIM" stroke-width="5" stroke-linejoin="round"/>
           <path d="M400,252 L400,860" stroke="TRIM" stroke-width="4"/>
           <g fill="TRIM">CIRCLES</g>
           <path d="M128,715 L196,730 M672,715 L604,730" stroke="TRIM" stroke-width="5"/>
           POCKET`,
    folds: 'M300,520 Q320,650 295,800 M505,520 Q485,650 510,800 M175,420 Q165,560 160,700 M625,420 Q635,560 640,700',
  },
  camp: {
    body: 'M320,190 L185,240 L110,420 L210,460 L245,390 L250,840 L550,840 L555,390 L590,460 L690,420 L615,240 L480,190 Z',
    trim: `<path d="M320,190 L400,330 L355,330 L300,215 Z M480,190 L400,330 L445,330 L500,215 Z" fill="BASE" stroke="TRIM" stroke-width="5" stroke-linejoin="round"/>
           <path d="M400,330 L400,840" stroke="TRIM" stroke-width="4"/>
           <g fill="TRIM"><circle cx="412" cy="420" r="6"/><circle cx="412" cy="530" r="6"/><circle cx="412" cy="640" r="6"/><circle cx="412" cy="750" r="6"/></g>`,
    folds: 'M300,520 Q320,650 295,800 M505,520 Q485,650 510,800',
  },
  pants: {
    body: 'M265,170 L535,170 L548,880 L428,880 L400,350 L372,880 L252,880 Z',
    trim: `<rect x="262" y="150" width="276" height="40" rx="6" fill="BASE" stroke="TRIM" stroke-width="5"/>
           <g fill="TRIM"><rect x="292" y="146" width="10" height="48" rx="3"/><rect x="395" y="146" width="10" height="48" rx="3"/><rect x="498" y="146" width="10" height="48" rx="3"/></g>
           <path d="M400,190 Q404,280 400,345" fill="none" stroke="TRIM" stroke-width="4"/>
           <path d="M282,192 L318,290 M518,192 L482,290" stroke="TRIM" stroke-width="4"/>
           EXTRA`,
    folds: 'M320,420 Q330,600 312,860 M480,420 Q470,600 488,860',
  },
};

function garment(shapeName, hex, options = {}) {
  const shape = shapes[shapeName];
  const light = isLight(hex);
  const trim = shade(hex, light ? -0.14 : 0.18);
  const stroke = light ? shade(hex, -0.2) : shade(hex, -0.35);
  let body = shape.body;
  let extra = shape.trim;
  if (options.taper) body = 'M265,170 L535,170 L522,880 L425,880 L400,350 L375,880 L278,880 Z';
  if (options.wide) body = 'M255,170 L545,170 L575,880 L432,880 L400,350 L368,880 L225,880 Z';
  extra = extra
    .replace('CIRCLES', [320, 420, 520, 620, 720, 820].map((y) => `<circle cx="412" cy="${y}" r="6"/>`).join(''))
    .replace('POCKET', options.pocket ? `<path d="M445,330 h70 v78 l-35,10 l-35,-10 Z" fill="none" stroke="TRIM" stroke-width="4"/>` : '')
    .replace('EXTRA', options.cargo
      ? `<rect x="215" y="500" width="70" height="110" rx="6" fill="BASE" stroke="TRIM" stroke-width="4"/><path d="M215,525 h70" stroke="TRIM" stroke-width="4"/>
         <rect x="515" y="500" width="70" height="110" rx="6" fill="BASE" stroke="TRIM" stroke-width="4"/><path d="M515,525 h70" stroke="TRIM" stroke-width="4"/>`
      : options.jogger
        ? `<rect x="276" y="842" width="104" height="40" rx="8" fill="TRIM"/><rect x="420" y="842" width="104" height="40" rx="8" fill="TRIM"/>
           <path d="M385,192 q-10,60 -22,90 M415,192 q10,60 22,90" stroke="${light ? '#fff' : '#ddd'}" stroke-width="5" fill="none" stroke-linecap="round"/>`
        : options.crease ? '<path d="M314,200 L314,875 M486,200 L486,875" stroke="TRIM" stroke-width="3" opacity="0.6"/>' : '')
    .replaceAll('TRIM', trim)
    .replaceAll('BASE', hex);
  if (options.denim) {
    extra += `<g fill="none" stroke="#c98a3a" stroke-width="3" stroke-dasharray="8 6">
      <rect x="290" y="330" width="80" height="85" rx="4"/><rect x="430" y="330" width="80" height="85" rx="4"/>
      <path d="M290,355 h80 M430,355 h80"/></g>`;
  }
  if (options.logo) {
    const mark = light ? '#1d1d1f' : '#f5f4f0';
    extra += `<g transform="translate(440 300)"><rect width="56" height="56" rx="6" fill="none" stroke="${mark}" stroke-width="4"/>
      <text x="28" y="38" font-family="Georgia,serif" font-size="26" font-weight="700" fill="${mark}" text-anchor="middle">T</text></g>`;
  }
  return `<g filter="url(#soft)">
      <path d="${body}" fill="${hex}" stroke="${stroke}" stroke-width="3" stroke-linejoin="round"/>
      <path d="${body}" fill="url(#shade)"/>
      <path d="${shape.folds}" fill="none" stroke="${light ? '#000' : '#fff'}" stroke-opacity="${light ? 0.07 : 0.08}" stroke-width="10" stroke-linecap="round"/>
      ${extra}
    </g>`;
}

function scene(content, { width = 800, height = 1000, transparent = false } = {}) {
  const bg = transparent ? '' : `<rect width="${width}" height="${height}" fill="url(#bg)"/>
      <ellipse cx="${width / 2}" cy="${height * 0.9}" rx="${width * 0.33}" ry="26" fill="#000" opacity="0.08" filter="url(#blur)"/>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    <defs>
      <radialGradient id="bg" cx="50%" cy="40%" r="75%"><stop offset="0" stop-color="#f6f4f0"/><stop offset="1" stop-color="#e4e0d9"/></radialGradient>
      <linearGradient id="shade" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity="0.16"/><stop offset="0.45" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.16"/></linearGradient>
      <filter id="soft" x="-10%" y="-10%" width="120%" height="120%"><feDropShadow dx="0" dy="14" stdDeviation="16" flood-opacity="0.16"/></filter>
      <filter id="blur"><feGaussianBlur stdDeviation="14"/></filter>
    </defs>${bg}${content}</svg>`;
}

function fabric(hex, kind) {
  const light = isLight(hex);
  const line = light ? 'rgba(0,0,0,0.10)' : 'rgba(255,255,255,0.10)';
  const patterns = {
    knit: `<pattern id="p" width="18" height="14" patternUnits="userSpaceOnUse"><path d="M0,0 L9,10 L18,0" fill="none" stroke="${line}" stroke-width="3"/></pattern>`,
    twill: `<pattern id="p" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(35)"><rect width="6" height="14" fill="${line}"/></pattern>`,
    oxford: `<pattern id="p" width="12" height="12" patternUnits="userSpaceOnUse"><rect width="6" height="6" fill="${line}"/><rect x="6" y="6" width="6" height="6" fill="${line}"/></pattern>`,
    fleece: `<pattern id="p" width="10" height="10" patternUnits="userSpaceOnUse"><circle cx="3" cy="4" r="2" fill="${line}"/><circle cx="8" cy="8" r="1.5" fill="${line}"/></pattern>`,
  };
  return `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="1000" viewBox="0 0 800 1000">
    <defs>${patterns[kind]}
      <radialGradient id="v" cx="45%" cy="40%" r="80%"><stop offset="0.5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.35"/></radialGradient>
      <linearGradient id="l" x1="0" x2="1" y1="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="0.18"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    </defs>
    <rect width="800" height="1000" fill="${hex}"/><rect width="800" height="1000" fill="url(#p)"/>
    <path d="M-20,640 C200,600 420,700 820,610" fill="none" stroke="${shade(hex, light ? -0.18 : 0.22)}" stroke-width="5" stroke-dasharray="14 9"/>
    <path d="M-20,660 C200,620 420,720 820,630" fill="none" stroke="#000" stroke-opacity="0.12" stroke-width="18"/>
    <rect width="800" height="1000" fill="url(#l)"/><rect width="800" height="1000" fill="url(#v)"/></svg>`;
}

// ------------------------------------------------------------------ catalogue (mirrors Flyway V900)
const products = [
  { slug: 'essential-crew-neck-tee', shape: 'crew', colors: ['WHITE', 'BLACK', 'NAVY'], fabric: 'knit' },
  { slug: 'monochrome-logo-tee', shape: 'crew', colors: ['BLACK', 'WHITE'], fabric: 'knit', logo: true },
  { slug: 'oversized-street-tee', shape: 'oversized', colors: ['BLACK', 'BEIGE', 'OLIVE'], fabric: 'knit' },
  { slug: 'classic-v-neck-tee', shape: 'vneck', colors: ['WHITE', 'GREY'], fabric: 'knit' },
  { slug: 'pique-polo-tee', shape: 'polo', colors: ['NAVY', 'WHITE', 'MAROON'], fabric: 'oxford' },
  { slug: 'oxford-button-down-shirt', shape: 'shirt', colors: ['WHITE', 'SKY_BLUE'], fabric: 'oxford', pocket: true },
  { slug: 'linen-blend-resort-shirt', shape: 'camp', colors: ['BEIGE', 'WHITE'], fabric: 'twill' },
  { slug: 'slim-fit-formal-shirt', shape: 'shirt', colors: ['WHITE', 'SKY_BLUE', 'BLACK'], fabric: 'oxford' },
  { slug: 'denim-overshirt', shape: 'shirt', colors: ['BLUE'], fabric: 'twill', denim: true },
  { slug: 'stretch-slim-chinos', shape: 'pants', colors: ['KHAKI', 'NAVY', 'BLACK'], fabric: 'twill', taper: true },
  { slug: 'relaxed-cargo-pants', shape: 'pants', colors: ['OLIVE', 'BLACK'], fabric: 'twill', wide: true, cargo: true },
  { slug: 'tailored-trousers', shape: 'pants', colors: ['CHARCOAL', 'BLACK'], fabric: 'twill', crease: true },
  { slug: 'everyday-joggers', shape: 'pants', colors: ['GREY', 'BLACK'], fabric: 'fleece', taper: true, jogger: true },
];
const mockups = { 'round-neck': 'crew', 'v-neck': 'vneck', polo: 'polo', oversized: 'oversized' };

// ------------------------------------------------------------------ render
const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage();
let count = 0;

async function render(svg, file, { width = 800, height = 1000, png = false } = {}) {
  await page.setViewport({ width, height, deviceScaleFactor: 1 });
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  await page.screenshot({
    path: file, type: png ? 'png' : 'jpeg', quality: png ? undefined : 84,
    omitBackground: png, clip: { x: 0, y: 0, width, height },
  });
  count++;
}

for (const p of products) {
  for (const color of p.colors) {
    await render(scene(garment(p.shape, COLORS[color], p)), path.join(root, 'products', `${p.slug}-${color.toLowerCase()}.jpg`));
  }
  await render(fabric(COLORS[p.colors[0]], p.fabric), path.join(root, 'products', `${p.slug}-detail.jpg`));
}
for (const [name, shape] of Object.entries(mockups)) {
  await render(scene(garment(shape, COLORS.WHITE), { transparent: true }), path.join(root, 'mockups', `${name}-white-front.png`), { png: true });
}

// Home page art: hero trio and the custom T-Shirt promo.
const hero = scene(`
  <g transform="translate(600 150) scale(0.74) rotate(-9 400 500)">${garment('crew', COLORS.WHITE)}</g>
  <g transform="translate(1010 150) scale(0.74) rotate(9 400 500)">${garment('crew', COLORS.GREY)}</g>
  <g transform="translate(790 90) scale(0.84)">${garment('oversized', COLORS.BLACK)}</g>`, { width: 1600, height: 1000 });
await render(hero, path.join(root, 'home', 'hero.jpg'), { width: 1600, height: 1000 });

const art = `<g transform="translate(400 460)">
  <circle r="120" fill="#f9c80e"/><circle cx="-60" cy="40" r="70" fill="#c62828" opacity="0.9"/>
  <circle cx="70" cy="60" r="55" fill="#1e4fb8" opacity="0.85"/>
  <text y="200" font-family="Georgia,serif" font-size="44" font-weight="700" fill="#1d1d1f" text-anchor="middle">YOUR DESIGN</text></g>`;
await render(scene(garment('crew', COLORS.WHITE) + art, { width: 800, height: 1000 }), path.join(root, 'home', 'custom-tshirt.jpg'));

await browser.close();
console.log(`Generated ${count} images in ${root}`);

// ------------------------------------------------------------------ colour helpers
function isLight(hex) {
  const [r, g, b] = rgb(hex);
  return 0.299 * r + 0.587 * g + 0.114 * b > 150;
}
function shade(hex, amount) {
  const mix = amount < 0 ? 0 : 255;
  const t = Math.abs(amount);
  return '#' + rgb(hex).map((c) => Math.round(c + (mix - c) * t).toString(16).padStart(2, '0')).join('');
}
function rgb(hex) {
  const v = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(v.slice(i, i + 2), 16));
}
