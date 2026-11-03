import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { apiErrorMessage } from '../../core/api/api-error';
import { StoreApi } from '../../core/api/store.api';

@Component({
  selector: 'app-newsletter-form',
  imports: [ReactiveFormsModule],
  template: `
    @if (done()) {
      <p class="done" role="status">
        <span class="material-symbols-outlined">check_circle</span>
        Thanks for subscribing. Look out for new drops and members-only offers.
      </p>
    } @else {
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label class="visually-hidden" for="newsletter-email">Email address</label>
        <input id="newsletter-email" type="email" formControlName="email" placeholder="Your email address"
               autocomplete="email" [attr.aria-invalid]="showError()">
        <button type="submit" [disabled]="sending()">{{ sending() ? 'Joining…' : 'Subscribe' }}</button>
      </form>
      @if (showError()) { <p class="error" role="alert">Enter a valid email address.</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    }
  `,
  styles: `
    form { display: flex; gap: 8px; max-width: 460px; }
    input {
      flex: 1; min-width: 0; height: 48px; padding: 0 16px; border-radius: 999px; font: inherit;
      border: 1px solid var(--newsletter-border, #d6d6d6); background: var(--newsletter-bg, #fff); color: inherit;
    }
    input:focus { outline: 2px solid currentColor; outline-offset: 1px; }
    button {
      height: 48px; padding: 0 22px; border-radius: 999px; border: none; cursor: pointer; font: inherit; font-weight: 600;
      background: var(--newsletter-button, #111); color: var(--newsletter-button-text, #fff);
    }
    button:disabled { opacity: 0.6; cursor: default; }
    .error { margin: 8px 0 0; font-size: 13px; color: var(--newsletter-error, #b3261e); }
    .done { display: flex; align-items: center; gap: 8px; margin: 0; }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 480px) { form { flex-direction: column; } }
  `,
})
export class NewsletterForm {
  private readonly api = inject(StoreApi);

  protected readonly sending = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly submitted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
  });

  protected showError(): boolean {
    return this.submitted() && this.form.invalid;
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.api.subscribe(this.form.getRawValue().email).subscribe({
      next: () => {
        this.sending.set(false);
        this.done.set(true);
      },
      error: (err: unknown) => {
        this.sending.set(false);
        this.error.set(apiErrorMessage(err, 'Could not subscribe right now. Please try again.'));
      },
    });
  }
}
