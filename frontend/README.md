# Frontend (Angular)

Angular 22 single-page app with the **storefront** (home page, product listing, search,
product details, wishlist, content pages, customer sign-in and registration) and the
**admin portal** for product management. The customer portal arrives in Phase 8.

## Stack

| Item | Choice |
|------|--------|
| Framework | Angular 22.2 (standalone components, signals, zoneless change detection) |
| UI | Angular Material 22 with a black and white theme (`src/styles.scss`) |
| Forms | Reactive Forms |
| HTTP | `HttpClient` with a functional auth interceptor, RxJS |
| Tests | Vitest + jsdom through `ng test` |
| Node | 22.22+ or 24.15+ (developed on 24.22.2 LTS) |

## Run locally

The backend must be running on `http://localhost:8080` (see `../backend/README.md`).

```bash
cd frontend
npm install
npm start            # http://localhost:4200, proxies /api and /media to :8080
```

Sign in at http://localhost:4200/login with the admin account from `ADMIN_EMAIL` and
`ADMIN_PASSWORD` in the repository `.env`.

## Other commands

```bash
npm test -- --watch=false   # unit tests
npm run build               # production build into dist/tshirt-shop/browser
```

## Structure

```
src/app/
├── core/
│   ├── api/            # AdminCatalogApi, ReferenceApi, problem-detail error helpers
│   ├── auth/           # AuthService, auth interceptor, admin and guest guards
│   ├── models/         # TypeScript types matching the backend DTOs
│   └── notify.service.ts
├── layout/admin-shell/ # sidebar + top bar for /admin
├── shared/             # confirm dialog, image fallback directive
└── features/
    ├── auth/login/
    ├── admin-products/     # list, edit page, size/colour stock grid, image manager
    ├── admin-categories/   # list + create/edit dialog
    └── admin-inventory/    # low-stock report with quick restock
```

## Authentication

- The access token is held **in memory only**. The refresh token is an HttpOnly cookie the
  JavaScript code never sees.
- On start-up the app calls `/api/auth/refresh` to restore the session, so a page reload keeps
  you signed in.
- When an API call returns 401, the interceptor refreshes once and retries. Parallel requests share
  a single refresh. If the refresh fails, the user is sent to the login page.
- `/admin/**` is protected by `adminGuard` (ROLE_ADMIN). The backend enforces the same rule.

## Storefront (Phases 6 and 7)

| Route | Page |
|-------|------|
| `/` | Home: hero, category tiles, featured, new arrivals, best sellers, T-Shirt/Shirt/Pants collections, custom T-Shirt promotion, special offers, customer reviews, newsletter |
| `/about`, `/contact` | About us; contact form (stored server-side, honeypot against bots) |
| `/privacy-policy`, `/terms`, `/returns-policy` | Policy pages (review the wording for your business) |
| `/login`, `/register`, `/forgot-password`, `/reset-password` | Customer and admin sign-in, registration, password reset |
| `/shop`, `/shop/:category`, `/search?q=` | Listing with filters (price, size, colour, stock, sale), sorting and pagination, all kept in the URL |
| `/product/:slug` | Product page: colour-aware gallery, size and colour availability, quantity, size guide, add to bag, buy now, reviews, related products |
| `/wishlist` | Saved products (sign-in required) |
| `/customize`, `/cart`, `/account` | "Coming soon" pages until Phases 9, 11 and 8 |

The home page loads everything from `GET /api/home` and hides sections that have no content.

**Shop details.** Edit `src/app/core/config/site.config.ts` for brand name, support email,
phone, address, hours and social links. Empty values are simply not shown.

**Sample images.** `public/assets/products`, `public/assets/home` and `public/assets/mockups`
contain generated illustrations for the development catalog. Regenerate them with:

```bash
# Puppeteer is not a project dependency; point PUPPETEER_DIR at any folder where it is installed.
PUPPETEER_DIR=/path/to/folder/with/puppeteer node scripts/generate-sample-images.mjs
```

Replace them with real product photography (upload through the admin portal) before launch.

## Admin portal features (Phase 5)

| Page | What it does |
|------|--------------|
| Products | Search, filter by category and status, sort, paginate |
| Product edit | Details and prices with validation; publish, hide, archive, delete |
| Sizes, colours and stock | Grid of sizes × colours; enter stock to offer a combination, blank to stop; "Fill all" per colour |
| Images | Upload PNG/JPG (drag and drop), main image, order, alt text, colour link, delete |
| Categories | Create, edit, hide, delete (built-in categories are protected) |
| Low stock | Combinations at or below their alert level, add stock in place |

Sample products seeded for development reference images under `/assets/products/` that do not
exist yet; a neutral placeholder is shown until real photos are uploaded or added in Phase 6.
