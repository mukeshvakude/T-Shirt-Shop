import { Routes } from '@angular/router';

import { adminGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  // The storefront home page arrives in Phase 6; until then the root opens the admin portal.
  { path: '', pathMatch: 'full', redirectTo: 'admin' },
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in | T-Shirt Shop',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
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
