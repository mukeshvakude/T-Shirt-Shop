import { Component, inject, input, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { apiErrorCode, apiErrorMessage } from '../../../core/api/api-error';
import { safeReturnUrl } from '../../../core/auth/auth.guards';
import { AuthService } from '../../../core/auth/auth.service';
import { SITE } from '../../../core/config/site.config';

/** Mirrors the backend password rules: 8–64 characters with a letter and a digit. */
export const PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(8),
  Validators.maxLength(64),
  Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).*$/),
];

export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return confirm && password !== confirm ? { mismatch: true } : null;
}

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatCheckboxModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './register.html',
  styleUrl: '../auth-page.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly site = SITE;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
      phone: ['', [Validators.pattern(/^[6-9]\d{9}$/)]],
      password: ['', PASSWORD_VALIDATORS],
      confirmPassword: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth
      .register({ fullName: v.fullName.trim(), email: v.email.trim(), phone: v.phone.trim() || null, password: v.password })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          const target = safeReturnUrl(this.returnUrl(), '/');
          void this.router.navigateByUrl(target.startsWith('/admin') ? '/' : target);
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          const code = apiErrorCode(err);
          if (code === 'EMAIL_ALREADY_REGISTERED') {
            this.form.controls.email.setErrors({ taken: true });
          } else if (code === 'PHONE_ALREADY_REGISTERED') {
            this.form.controls.phone.setErrors({ taken: true });
          }
          this.error.set(apiErrorMessage(err, 'Could not create your account. Please try again.'));
        },
      });
  }
}
