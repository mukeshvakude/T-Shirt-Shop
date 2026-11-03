import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { AccountApi } from '../../../core/api/account.api';
import { apiErrorCode, apiErrorMessage } from '../../../core/api/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { NotifyService } from '../../../core/notify.service';
import { PASSWORD_VALIDATORS, passwordsMatch } from '../../auth/register/register';

@Component({
  selector: 'app-account-profile',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './account-profile.html',
  styleUrls: ['../account-pages.scss', './account-profile.scss'],
})
export class AccountProfile {
  private readonly api = inject(AccountApi);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifyService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly user = this.auth.user;
  protected readonly savingDetails = signal(false);
  protected readonly savingEmail = signal(false);
  protected readonly savingPassword = signal(false);

  protected readonly details = this.fb.group({
    fullName: [this.user()?.fullName ?? '', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: [this.user()?.phone ?? '', [Validators.pattern(/^[6-9]\d{9}$/)]],
  });

  protected readonly email = this.fb.group({
    newEmail: ['', [Validators.required, Validators.email, Validators.maxLength(190)]],
    currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected readonly password = this.fb.group(
    {
      currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
      password: ['', PASSWORD_VALIDATORS],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected saveDetails(): void {
    if (this.details.invalid || this.savingDetails()) {
      this.details.markAllAsTouched();
      return;
    }
    const v = this.details.getRawValue();
    this.savingDetails.set(true);
    this.api.updateProfile(v.fullName.trim(), v.phone.trim() || null).subscribe({
      next: (user) => {
        this.auth.updateUser(user);
        this.details.reset({ fullName: user.fullName, phone: user.phone ?? '' });
        this.savingDetails.set(false);
        this.notify.success('Your details were saved.');
      },
      error: (err: unknown) => {
        this.savingDetails.set(false);
        if (apiErrorCode(err) === 'PHONE_ALREADY_REGISTERED') {
          this.details.controls.phone.setErrors({ taken: true });
        }
        this.notify.error(err, 'Could not save your details.');
      },
    });
  }

  protected saveEmail(): void {
    if (this.email.invalid || this.savingEmail()) {
      this.email.markAllAsTouched();
      return;
    }
    const v = this.email.getRawValue();
    this.savingEmail.set(true);
    this.api.changeEmail(v.newEmail.trim(), v.currentPassword).subscribe({
      next: (user) => {
        this.auth.updateUser(user);
        this.email.reset();
        this.savingEmail.set(false);
        this.notify.success(`Your sign-in email is now ${user.email}.`);
      },
      error: (err: unknown) => {
        this.savingEmail.set(false);
        const code = apiErrorCode(err);
        if (code === 'INVALID_CURRENT_PASSWORD') this.email.controls.currentPassword.setErrors({ wrong: true });
        if (code === 'EMAIL_ALREADY_REGISTERED') this.email.controls.newEmail.setErrors({ taken: true });
        this.notify.error(err, 'Could not change your email.');
      },
    });
  }

  protected savePassword(): void {
    if (this.password.invalid || this.savingPassword()) {
      this.password.markAllAsTouched();
      return;
    }
    const v = this.password.getRawValue();
    this.savingPassword.set(true);
    this.api.changePassword(v.currentPassword, v.password).subscribe({
      next: (session) => {
        this.auth.applySession(session);
        this.password.reset();
        this.savingPassword.set(false);
        this.notify.success('Password changed. You have been signed out on your other devices.');
      },
      error: (err: unknown) => {
        this.savingPassword.set(false);
        const code = apiErrorCode(err);
        if (code === 'INVALID_CURRENT_PASSWORD') this.password.controls.currentPassword.setErrors({ wrong: true });
        if (code === 'PASSWORD_UNCHANGED') this.password.controls.password.setErrors({ same: true });
        this.notify.error(apiErrorMessage(err, 'Could not change your password.'));
      },
    });
  }
}
