import { Component, computed, input } from '@angular/core';

/** Read-only star rating, e.g. 4.5 shows four full stars and one half star. */
@Component({
  selector: 'app-rating-stars',
  template: `
    <span class="stars" role="img" [attr.aria-label]="label()">
      @for (star of stars(); track $index) {
        <span class="material-symbols-outlined" [class.filled]="star !== 'empty'" aria-hidden="true">
          {{ star === 'half' ? 'star_half' : 'star' }}
        </span>
      }
    </span>
  `,
  styles: `
    .stars { display: inline-flex; color: #111; }
    .material-symbols-outlined { font-size: var(--star-size, 16px); color: #c9c9c9; }
    .filled { color: #111; font-variation-settings: 'FILL' 1; }
  `,
})
export class RatingStars {
  readonly rating = input.required<number>();
  readonly count = input<number | null>(null);

  protected readonly stars = computed(() => {
    const value = Math.max(0, Math.min(5, this.rating()));
    return Array.from({ length: 5 }, (_, i) => {
      if (value >= i + 1) return 'full';
      if (value >= i + 0.5) return 'half';
      return 'empty';
    });
  });

  protected readonly label = computed(() => {
    const base = `Rated ${this.rating().toFixed(1)} out of 5`;
    const count = this.count();
    return count ? `${base} from ${count} review${count === 1 ? '' : 's'}` : base;
  });
}
