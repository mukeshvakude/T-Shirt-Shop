import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { apiErrorMessage } from '../../../core/api/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { SITE } from '../../../core/config/site.config';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule],
  styleUrl: '../auth-page.scss',
  template: `
    <main class="auth">
      <section class="brand">
        <a routerLink="/" class="logo">{{ site.wordmark }}</a>
        <h1>Forgot your password?</h1>
        <p>It happens. We will email you a secure link to choose a new one.</p>
      </section>
      <section class="panel">
        @if (sent()) {
          <div class="result" role="status">
            <span class="material-symbols-outlined">mark_email_read</span>
            <h2>Check your email</h2>
            <p>If an account exists for {{ form.controls.email.value }}, a reset link is on its way. The link is valid
              for 30 minutes.</p>
            <a routerLink="/login">Back to sign in</a>
          </div>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <h2>Reset password</h2>
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" required>
              @if (form.controls.email.invalid) { <mat-error>Enter a valid email address</mat-error> }
            </mat-form-field>
            @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
            <button mat-flat-button type="submit" [disabled]="sending()">{{ sending() ? 'Sending…' : 'Send reset link' }}</button>
            <p class="switch"><a routerLink="/login">Back to sign in</a></p>
          </form>
        }
      </section>
    </main>
  `,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);

  protected readonly site = SITE;
  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.sending()) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.auth.forgotPassword(this.form.getRawValue().email.trim()).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.set(true);
      },
      error: (err: unknown) => {
        this.sending.set(false);
        this.error.set(apiErrorMessage(err, 'Could not send the link. Please try again.'));
      },
    });
  }
}
