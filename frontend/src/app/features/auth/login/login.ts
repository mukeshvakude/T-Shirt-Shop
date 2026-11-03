import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { apiErrorMessage } from '../../../core/api/api-error';
import { safeReturnUrl } from '../../../core/auth/auth.guards';
import { AuthService } from '../../../core/auth/auth.service';
import { SITE } from '../../../core/config/site.config';

/** Sign-in for customers and admins. Admins land in the portal, customers return to where they were. */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './login.html',
  styleUrl: '../auth-page.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query parameters bound by the router. */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();

  protected readonly site = SITE;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (user) => {
        this.submitting.set(false);
        const isAdmin = user.roles.includes('ROLE_ADMIN');
        const target = safeReturnUrl(this.returnUrl(), isAdmin ? '/admin' : '/');
        if (target.startsWith('/admin') && !isAdmin) {
          void this.router.navigateByUrl('/');
          return;
        }
        void this.router.navigateByUrl(target);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(apiErrorMessage(err, 'Sign-in failed. Please try again.'));
      },
    });
  }
}
