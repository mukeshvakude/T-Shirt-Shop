import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { AuthResponse, AuthUser } from '../auth/auth.models';
import { Address, AddressRequest, Dashboard } from '../models/account.models';

/** The signed-in customer's account: /api/account/** */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/account';
  private states$?: Observable<string[]>;

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`);
  }

  updateProfile(fullName: string, phone: string | null, whatsappOptIn?: boolean): Observable<AuthUser> {
    return this.http.put<AuthUser>(`${this.base}/profile`, { fullName, phone: phone ?? '', whatsappOptIn });
  }

  changeEmail(newEmail: string, currentPassword: string): Observable<AuthUser> {
    return this.http.put<AuthUser>(`${this.base}/email`, { newEmail, currentPassword });
  }

  /** Returns a fresh session; the browser receives a new refresh cookie. */
  changePassword(currentPassword: string, newPassword: string): Observable<AuthResponse> {
    return this.http.put<AuthResponse>(`${this.base}/password`, { currentPassword, newPassword }, { withCredentials: true });
  }

  addresses(): Observable<Address[]> {
    return this.http.get<Address[]>(`${this.base}/addresses`);
  }

  createAddress(body: AddressRequest): Observable<Address> {
    return this.http.post<Address>(`${this.base}/addresses`, body);
  }

  updateAddress(id: number, body: AddressRequest): Observable<Address> {
    return this.http.put<Address>(`${this.base}/addresses/${id}`, body);
  }

  makeDefault(id: number): Observable<Address> {
    return this.http.put<Address>(`${this.base}/addresses/${id}/default`, null);
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/addresses/${id}`);
  }

  states(): Observable<string[]> {
    this.states$ ??= this.http.get<string[]>(`${this.base}/states`).pipe(shareReplay(1));
    return this.states$;
  }
}
