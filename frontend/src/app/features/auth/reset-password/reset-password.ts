import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { apiErrorMessage } from '../../../core/api/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { SITE } from '../../../core/config/site.config';
import { PASSWORD_VALIDATORS, passwordsMatch } from '../register/register';

/** Opened from the emailed link: /reset-password?token=... */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule],
  styleUrl: '../auth-page.scss',
  template: `
    <main class="auth">
      <section class="brand">
        <a routerLink="/" class="logo">{{ site.wordmark }}</a>
        <h1>Choose a new password.</h1>
        <p>For your security, you will be signed out on all your devices.</p>
      </section>
      <section class="panel">
        @if (!token()) {
          <div class="result" role="alert">
            <span class="material-symbols-outlined">link_off</span>
            <h2>Link incomplete</h2>
            <p>This reset link is missing its code. Please use the full link from your email, or request a new one.</p>
            <a routerLink="/forgot-password">Request a new link</a>
          </div>
        } @else if (done()) {
          <div class="result" role="status">
            <span class="material-symbols-outlined">task_alt</span>
            <h2>Password updated</h2>
            <p>You can now sign in with your new password.</p>
            <a routerLink="/login">Sign in</a>
          </div>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <h2>New password</h2>
            <mat-form-field>
              <mat-label>New password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password" required>
              <mat-hint>At least 8 characters, with a letter and a number</mat-hint>
              @if (form.controls.password.invalid) { <mat-error>At least 8 characters, with a letter and a number</mat-error> }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Confirm new password</mat-label>
              <input matInput type="password" formControlName="confirmPassword" autocomplete="new-password" required>
            </mat-form-field>
            @if (form.hasError('mismatch') && form.controls.confirmPassword.touched) {
              <p class="error">The passwords do not match.</p>
            }
            @if (error()) {
              <p class="error" role="alert">{{ error() }} <a routerLink="/forgot-password">Request a new link</a></p>
            }
            <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Update password' }}</button>
          </form>
        }
      </section>
    </main>
  `,
})
export class ResetPassword {
  private readonly auth = inject(AuthService);

  /** Query parameter. */
  readonly token = input<string>();

  protected readonly site = SITE;
  protected readonly saving = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    { password: ['', PASSWORD_VALIDATORS], confirmPassword: ['', Validators.required] },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token()!, this.form.getRawValue().password).subscribe({
      next: () => {
        this.saving.set(false);
        this.done.set(true);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Could not update the password.'));
      },
    });
  }
}
