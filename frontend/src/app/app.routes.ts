import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';

const comingSoon = () => import('./features/static-pages/coming-soon').then((m) => m.ComingSoon);
const designer = () => import('./features/designer/designer').then((m) => m.Designer);
const listing = () => import('./features/products/product-listing/product-listing').then((m) => m.ProductListing);
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

      // Catalog (Phase 7)
      { path: 'shop', title: `Shop all | ${SHOP}`, loadComponent: listing },
      { path: 'shop/:category', title: SHOP, loadComponent: listing },
      { path: 'search', title: `Search | ${SHOP}`, loadComponent: listing },
      {
        path: 'product/:slug',
        title: SHOP,
        loadComponent: () => import('./features/products/product-detail/product-detail').then((m) => m.ProductDetail),
      },
      {
        path: 'wishlist',
        canActivate: [authGuard],
        title: `Wishlist | ${SHOP}`,
        loadComponent: () => import('./features/products/wishlist-page/wishlist-page').then((m) => m.WishlistPage),
      },

      // Custom T-Shirt designer (Phase 9)
      { path: 'customize', title: `Design your T-Shirt | ${SHOP}`, loadComponent: designer },
      { path: 'customize/:code', title: `Edit your design | ${SHOP}`, loadComponent: designer },

      // Shopping bag (Phase 11)
      { path: 'cart', title: `Your bag | ${SHOP}`, loadComponent: () => import('./features/cart/cart-page').then((m) => m.CartPage) },

      // Delivered in the next phase (checkout: 12).
      {
        path: 'checkout',
        canActivate: [authGuard],
        title: `Checkout | ${SHOP}`,
        loadComponent: comingSoon,
        data: { title: 'Checkout is almost ready', description: 'Your bag is saved to your account. Address, payment with Razorpay and Cash on Delivery open in the next update.' },
      },

      // Customer portal (Phase 8)
      {
        path: 'account',
        canActivate: [authGuard],
        loadComponent: () => import('./features/account/account-shell/account-shell').then((m) => m.AccountShell),
        children: [
          {
            path: '',
            pathMatch: 'full',
            title: `My account | ${SHOP}`,
            loadComponent: () => import('./features/account/dashboard/account-dashboard').then((m) => m.AccountDashboard),
          },
          {
            path: 'orders',
            title: `My orders | ${SHOP}`,
            loadComponent: () => import('./features/account/orders/account-orders').then((m) => m.AccountOrders),
          },
          {
            path: 'profile',
            title: `Profile & security | ${SHOP}`,
            loadComponent: () => import('./features/account/profile/account-profile').then((m) => m.AccountProfile),
          },
          {
            path: 'designs',
            title: `My designs | ${SHOP}`,
            loadComponent: () => import('./features/account/designs/account-designs').then((m) => m.AccountDesigns),
          },
          {
            path: 'addresses',
            title: `Addresses | ${SHOP}`,
            loadComponent: () => import('./features/account/addresses/account-addresses').then((m) => m.AccountAddresses),
          },
        ],
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
        path: 'designs',
        title: 'Custom designs | Admin',
        loadComponent: () => import('./features/admin-designs/design-list').then((m) => m.DesignList),
      },
      {
        path: 'pricing',
        title: 'Pricing | Admin',
        loadComponent: () => import('./features/admin-pricing/pricing-page').then((m) => m.PricingPage),
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
