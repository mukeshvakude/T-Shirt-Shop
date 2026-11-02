import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { apiErrorMessage } from './api/api-error';

/** Snackbar notifications with consistent styling and durations. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3000 });
  }

  error(errorOrMessage: unknown, fallback?: string): void {
    const message = typeof errorOrMessage === 'string' ? errorOrMessage : apiErrorMessage(errorOrMessage, fallback);
    this.snackBar.open(message, 'Dismiss', { duration: 7000, panelClass: 'snack-error' });
  }
}
