import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatButtonModule, MatMenuModule],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell {
  protected readonly auth = inject(AuthService);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly initials = computed(() =>
    (this.auth.user()?.fullName ?? 'A').split(' ').map((p) => p.charAt(0)).join('').slice(0, 2).toUpperCase(),
  );

  protected readonly nav: NavItem[] = [
    { path: '/admin/products', label: 'Products', icon: 'apparel' },
    { path: '/admin/categories', label: 'Categories', icon: 'category' },
    { path: '/admin/designs', label: 'Custom designs', icon: 'draw' },
    { path: '/admin/pricing', label: 'Pricing', icon: 'sell' },
    { path: '/admin/inventory', label: 'Low stock', icon: 'inventory_2' },
  ];
}
