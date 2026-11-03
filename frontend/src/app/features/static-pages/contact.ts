import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { apiErrorMessage } from '../../core/api/api-error';
import { StoreApi } from '../../core/api/store.api';
import { AuthService } from '../../core/auth/auth.service';
import { SITE } from '../../core/config/site.config';

@Component({
  selector: 'app-contact',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule],
  styleUrls: ['./static-page.scss', './contact.scss'],
  templateUrl: './contact.html',
})
export class Contact {
  private readonly api = inject(StoreApi);
  private readonly auth = inject(AuthService);

  protected readonly site = SITE;
  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.auth.user()?.fullName ?? '', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: [this.auth.user()?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(190)]],
    phone: [this.auth.user()?.phone ?? '', [Validators.pattern(/^[6-9]\d{9}$/)]],
    subject: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(150)]],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(5000)]],
    website: [''],
  });

  protected submit(): void {
    if (this.form.invalid || this.sending()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.sending.set(true);
    this.error.set(null);
    this.api
      .contact({
        name: v.name.trim(),
        email: v.email.trim(),
        phone: v.phone.trim() || null,
        subject: v.subject.trim(),
        message: v.message.trim(),
        website: v.website,
      })
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.sent.set(true);
        },
        error: (err: unknown) => {
          this.sending.set(false);
          this.error.set(apiErrorMessage(err, 'Your message could not be sent. Please try again.'));
        },
      });
  }
}
