import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatMenuModule } from '@angular/material/menu';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, map, of } from 'rxjs';

import { ReferenceApi } from '../../core/api/reference.api';
import { StoreApi } from '../../core/api/store.api';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { SiteSettings } from '../../core/config/site-settings.service';
import { SITE, categoryLink } from '../../core/config/site.config';
import { Category } from '../../core/models/catalog.models';
import { Offer } from '../../core/models/store.models';

@Component({
  selector: 'app-store-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatMenuModule],
  templateUrl: './store-shell.html',
  styleUrl: './store-shell.scss',
})
export class StoreShell {
  protected readonly auth = inject(AuthService);
  protected readonly bagCount = inject(CartService).count;
  private readonly router = inject(Router);

  protected readonly site = SITE;
  protected readonly year = new Date().getFullYear();
  protected readonly menuOpen = signal(false);

  protected readonly categories = toSignal(
    inject(ReferenceApi).categories().pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );
  private readonly offers = toSignal(
    inject(StoreApi).offers().pipe(catchError(() => of([] as Offer[]))),
    { initialValue: [] as Offer[] },
  );
  protected readonly announcement = computed(() => {
    const offer = this.offers()[0];
    return offer ? `${offer.headline} with code ${offer.code}${offer.description ? ' · ' + offer.description : ''}` : null;
  });

  /** Current URL, used as the login return address. */
  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );
  protected readonly returnUrl = computed(() => (this.url().startsWith('/login') ? '/' : this.url()));

  /** Floating "Chat on WhatsApp" link; mentions the order when on an order page. Hidden where it would cover controls. */
  private readonly settings = inject(SiteSettings);
  protected readonly whatsappLink = computed(() => {
    const url = this.url();
    if (/^\/(customize|checkout)/.test(url)) return null;
    const order = /^\/order\/(TS-\d{8}-\d{6})/.exec(url)?.[1];
    return this.settings.chatLink(
      order ? `Hi ${SITE.brandName}, I have a question about my order ${order}.` : `Hi ${SITE.brandName}, I have a question.`,
    );
  });

  protected link = categoryLink;

  constructor() {
    // Close the mobile menu after any navigation.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected search(raw: string): void {
    const q = raw.trim();
    if (q) {
      void this.router.navigate(['/search'], { queryParams: { q } });
    }
  }

  protected logout(): void {
    this.auth.logout('/');
  }
}
