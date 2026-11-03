import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { InrPipe } from '../../../shared/inr.pipe';

import { AccountApi } from '../../../core/api/account.api';
import { AuthService } from '../../../core/auth/auth.service';
import { Dashboard, ORDER_STATUS_LABELS, landmarkText } from '../../../core/models/account.models';

@Component({
  selector: 'app-account-dashboard',
  imports: [RouterLink, DatePipe, InrPipe],
  templateUrl: './account-dashboard.html',
  styleUrl: '../account-pages.scss',
})
export class AccountDashboard implements OnInit {
  private readonly api = inject(AccountApi);
  protected readonly auth = inject(AuthService);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal(false);
  protected readonly statusLabels = ORDER_STATUS_LABELS;
  protected readonly landmark = landmarkText;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(false);
    this.api.dashboard().subscribe({
      next: (d) => this.data.set(d),
      error: () => this.error.set(true),
    });
  }

  protected greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }
}
