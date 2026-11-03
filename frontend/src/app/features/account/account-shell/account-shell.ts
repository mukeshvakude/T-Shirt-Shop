import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

/** Two-column account area inside the storefront: navigation plus the current page. */
@Component({
  selector: 'app-account-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="account">
      <aside>
        <div class="who">
          <span class="avatar" aria-hidden="true">{{ initials() }}</span>
          <div>
            <strong>{{ auth.user()?.fullName }}</strong>
            <small>{{ auth.user()?.email }}</small>
          </div>
        </div>
        <nav aria-label="My account">
          <a routerLink="/account" [routerLinkActiveOptions]="{ exact: true }" routerLinkActive="active">
            <span class="material-symbols-outlined">dashboard</span>Dashboard</a>
          <a routerLink="/account/orders" routerLinkActive="active">
            <span class="material-symbols-outlined">package_2</span>Orders</a>
          <a routerLink="/account/designs" routerLinkActive="active">
            <span class="material-symbols-outlined">draw</span>My designs</a>
          <a routerLink="/account/profile" routerLinkActive="active">
            <span class="material-symbols-outlined">person</span>Profile &amp; security</a>
          <a routerLink="/account/addresses" routerLinkActive="active">
            <span class="material-symbols-outlined">home_pin</span>Addresses</a>
          <a routerLink="/wishlist">
            <span class="material-symbols-outlined">favorite</span>Wishlist</a>
          @if (auth.isAdmin()) {
            <a routerLink="/admin"><span class="material-symbols-outlined">admin_panel_settings</span>Admin portal</a>
          }
          <button type="button" (click)="auth.logout('/')">
            <span class="material-symbols-outlined">logout</span>Sign out</button>
        </nav>
      </aside>
      <section class="content"><router-outlet /></section>
    </div>
  `,
  styles: `
    .account { max-width: 1240px; margin: 0 auto; padding: 32px 24px 0; display: grid;
      grid-template-columns: 260px minmax(0, 1fr); gap: 40px; align-items: start; }
    aside { position: sticky; top: 90px; }
    .who { display: flex; gap: 12px; align-items: center; margin-bottom: 20px; min-width: 0;
      div { display: flex; flex-direction: column; min-width: 0; }
      small { color: var(--shop-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
    .avatar { width: 44px; height: 44px; border-radius: 50%; background: #111; color: #fff; display: grid;
      place-items: center; font-weight: 700; flex: none; }
    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a, nav button { display: flex; align-items: center; gap: 12px; padding: 11px 12px; border-radius: 10px;
      text-decoration: none; color: #333; font: inherit; font-weight: 500; background: none; border: none;
      cursor: pointer; text-align: left; white-space: nowrap; }
    nav a:hover, nav button:hover { background: #f4f4f4; }
    nav a.active { background: #111; color: #fff; }
    .content { min-width: 0; }
    @media (max-width: 900px) {
      .account { grid-template-columns: minmax(0, 1fr); gap: 20px; padding: 20px 16px 0; }
      aside { position: static; min-width: 0; }
      nav { flex-direction: row; overflow-x: auto; gap: 6px; padding-bottom: 4px; }
      nav a, nav button { padding: 9px 14px; border: 1px solid var(--shop-line); border-radius: 999px; }
    }
  `,
})
export class AccountShell {
  protected readonly auth = inject(AuthService);

  protected initials(): string {
    return (this.auth.user()?.fullName ?? '?').trim().split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  }
}
