import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';

const comingSoon = () => import('./features/static-pages/coming-soon').then((m) => m.ComingSoon);
const SHOP = 'T-Shirt Shop';

export const routes: Routes = [
  // ------------------------------------------------------------ storefront
  {
    path: '',
    loadComponent: () => import('./layout/store-shell/store-shell').then((m) => m.StoreShell),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: `${SHOP} | Premium T-Shirts, Shirts, Pants & Custom Tees`,
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      { path: 'about', title: `About us | ${SHOP}`, loadComponent: () => import('./features/static-pages/about').then((m) => m.About) },
      { path: 'contact', title: `Contact us | ${SHOP}`, loadComponent: () => import('./features/static-pages/contact').then((m) => m.Contact) },
      {
        path: 'privacy-policy',
        title: `Privacy policy | ${SHOP}`,
        loadComponent: () => import('./features/static-pages/privacy-policy').then((m) => m.PrivacyPolicy),
      },
      { path: 'terms', title: `Terms & conditions | ${SHOP}`, loadComponent: () => import('./features/static-pages/terms').then((m) => m.Terms) },
      {
        path: 'returns-policy',
        title: `Returns & refunds | ${SHOP}`,
        loadComponent: () => import('./features/static-pages/returns-policy').then((m) => m.ReturnsPolicy),
      },

      // Pages delivered in later phases (listing and details: 7, account: 8, designer: 9, cart: 11).
      {
        path: 'shop/:category',
        title: SHOP,
        loadComponent: comingSoon,
        data: { title: 'The full collection is almost here', description: 'Browsing with filters and sorting is coming very soon. Meanwhile, explore the highlights on our home page.' },
      },
      {
        path: 'product/:slug',
        title: SHOP,
        loadComponent: comingSoon,
        data: { title: 'Product pages are almost here', description: 'Detailed product pages with sizes, colours and photos are coming very soon.' },
      },
      {
        path: 'search',
        title: `Search | ${SHOP}`,
        loadComponent: comingSoon,
        data: { title: 'Search', description: 'Product search is coming very soon. Meanwhile, explore the highlights on our home page.' },
      },
      {
        path: 'customize',
        title: `Design your T-Shirt | ${SHOP}`,
        loadComponent: comingSoon,
        data: { title: 'The T-Shirt studio is almost ready', description: 'Upload artwork, add text and see the price update live. Our custom T-Shirt designer opens very soon.' },
      },
      {
        path: 'cart',
        title: `Your bag | ${SHOP}`,
        loadComponent: comingSoon,
        data: { title: 'Your bag', description: 'Online ordering opens very soon.' },
      },
      {
        path: 'account',
        canActivate: [authGuard],
        title: `My account | ${SHOP}`,
        loadComponent: comingSoon,
        data: { title: 'My account', description: 'Your profile, addresses and orders will appear here very soon.' },
      },
    ],
  },

  // ------------------------------------------------------------ authentication (full-screen pages)
  { path: 'login', canActivate: [guestGuard], title: `Sign in | ${SHOP}`, loadComponent: () => import('./features/auth/login/login').then((m) => m.Login) },
  { path: 'register', canActivate: [guestGuard], title: `Create account | ${SHOP}`, loadComponent: () => import('./features/auth/register/register').then((m) => m.Register) },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    title: `Forgot password | ${SHOP}`,
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    title: `Reset password | ${SHOP}`,
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },

  // ------------------------------------------------------------ admin portal
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./layout/admin-shell/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'products',
        title: 'Products | Admin',
        loadComponent: () => import('./features/admin-products/product-list/product-list').then((m) => m.ProductList),
      },
      {
        path: 'products/new',
        title: 'New product | Admin',
        loadComponent: () => import('./features/admin-products/product-edit/product-edit').then((m) => m.ProductEdit),
      },
      {
        path: 'products/:id',
        title: 'Edit product | Admin',
        loadComponent: () => import('./features/admin-products/product-edit/product-edit').then((m) => m.ProductEdit),
      },
      {
        path: 'categories',
        title: 'Categories | Admin',
        loadComponent: () =>
          import('./features/admin-categories/category-list/category-list').then((m) => m.CategoryList),
      },
      {
        path: 'inventory',
        title: 'Low stock | Admin',
        loadComponent: () => import('./features/admin-inventory/low-stock/low-stock').then((m) => m.LowStock),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
