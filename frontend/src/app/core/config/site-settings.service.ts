import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';

/** Storefront settings that live on the server (in .env), such as the WhatsApp chat number. */
@Injectable({ providedIn: 'root' })
export class SiteSettings {
  /** Digits with country code (e.g. 919876543210), or null when no chat number is set. */
  readonly whatsappNumber = toSignal(
    inject(HttpClient)
      .get<{ whatsappNumber: string | null }>('/api/site')
      .pipe(
        map((s) => s.whatsappNumber),
        catchError(() => of(null)),
      ),
    { initialValue: null },
  );

  /** A wa.me link that opens a chat with the shop, with the message already typed. */
  chatLink(text: string): string | null {
    const number = this.whatsappNumber();
    return number ? `https://wa.me/${number}?text=${encodeURIComponent(text)}` : null;
  }
}
