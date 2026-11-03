import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Honest placeholder for storefront pages built in later phases
 * (listing, product details, designer, cart, account). Configured through route data.
 */
@Component({
  selector: 'app-coming-soon',
  imports: [RouterLink],
  styleUrl: './static-page.scss',
  template: `
    <section class="page-hero">
      <span class="eyebrow">Coming soon</span>
      <h1>{{ heading() }}</h1>
      <p>{{ description() }}</p>
      <div class="cta-row">
        <a routerLink="/">Back to the home page</a>
      </div>
    </section>
  `,
})
export class ComingSoon {
  /** Route data. */
  readonly title = input('This page');
  readonly description = input('This part of the shop is being built and will be available soon.');
  /** Query parameter on /search. */
  readonly q = input<string>();

  protected readonly heading = computed(() => (this.q() ? `Results for “${this.q()}”` : this.title()));
}
