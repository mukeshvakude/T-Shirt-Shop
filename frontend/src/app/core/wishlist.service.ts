import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from './auth/auth.service';
import { ProductSummary } from './models/store.models';
import { NotifyService } from './notify.service';

/**
 * Saved products of the signed-in customer. Hearts update instantly (optimistic) and
 * roll back if the server rejects the change. Anonymous users are sent to sign in.
 */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  private readonly ids = signal<ReadonlySet<number>>(new Set());
  readonly count = computed(() => this.ids().size);

  constructor() {
    // Load saved ids whenever someone signs in; clear them on sign-out.
    effect(() => {
      const user = this.auth.user();
      if (!user) {
        this.ids.set(new Set());
        return;
      }
      this.http.get<number[]>('/api/account/wishlist/ids').subscribe({
        next: (ids) => this.ids.set(new Set(ids)),
        error: () => this.ids.set(new Set()),
      });
    });
  }

  has(productId: number): boolean {
    return this.ids().has(productId);
  }

  list(): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>('/api/account/wishlist');
  }

  toggle(productId: number): void {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url, reason: 'wishlist' } });
      return;
    }
    const saved = this.has(productId);
    this.setSaved(productId, !saved);
    const request = saved
      ? this.http.delete<void>(`/api/account/wishlist/${productId}`)
      : this.http.put<void>(`/api/account/wishlist/${productId}`, null);
    request.subscribe({
      next: () => this.notify.success(saved ? 'Removed from your wishlist.' : 'Saved to your wishlist.'),
      error: (err: unknown) => {
        this.setSaved(productId, saved);
        this.notify.error(err, 'Could not update your wishlist.');
      },
    });
  }

  private setSaved(productId: number, saved: boolean): void {
    const next = new Set(this.ids());
    if (saved) {
      next.add(productId);
    } else {
      next.delete(productId);
    }
    this.ids.set(next);
  }
}
