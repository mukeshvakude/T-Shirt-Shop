import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute } from '@angular/router';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { MessageRow, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';

/** Contact form inbox: read, reply by email, and mark as replied or closed. */
@Component({
  selector: 'app-admin-message-list',
  imports: [DatePipe, MatProgressBarModule],
  template: `
    <div class="page">
      <header class="page-header"><div><h1>Messages</h1><p>Messages sent through the Contact us page.</p></div></header>
      <section class="card table-card">
        <div class="filters">
          @for (s of tabs; track s.value) {
            <button type="button" class="tab" [class.on]="status() === s.value" (click)="setStatus(s.value)">{{ s.label }}</button>
          }
        </div>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <ul class="messages">
          @for (m of rows(); track m.id) {
            <li [class.new]="m.status === 'NEW'">
              <div class="top">
                <strong>{{ m.subject }}</strong>
                <span class="status" [attr.data-s]="m.status">{{ labels[m.status] }}</span>
                <small>{{ m.createdAt | date: 'd MMM y, h:mm a' }}</small>
              </div>
              <div class="from">{{ m.name }} · <a [href]="replyLink(m)" (click)="mark(m, 'REPLIED')">{{ m.email }}</a>
                @if (m.phone) { · {{ m.phone }} }</div>
              <p>{{ m.message }}</p>
              <div class="acts">
                @if (m.status === 'NEW') { <button type="button" (click)="mark(m, 'READ')">Mark read</button> }
                @if (m.status !== 'REPLIED') { <button type="button" (click)="mark(m, 'REPLIED')">Mark replied</button> }
                @if (m.status !== 'CLOSED') { <button type="button" (click)="mark(m, 'CLOSED')">Close</button> }
              </div>
            </li>
          } @empty { <li class="muted">No messages here.</li> }
        </ul>
        <div class="pager">
          <button type="button" (click)="go(page() - 1)" [disabled]="page() === 0">Previous</button>
          <button type="button" (click)="go(page() + 1)" [disabled]="last()">Next</button>
        </div>
      </section>
    </div>
  `,
  styles: `
    .table-card { padding: 0; overflow: hidden; }
    .tab { border: 1px solid #d6d6d6; background: #fff; border-radius: 999px; padding: 6px 14px; cursor: pointer; font: inherit;
      &.on { background: #111; color: #fff; border-color: #111; } }
    .messages { list-style: none; margin: 0; padding: 0;
      li { padding: 16px; border-bottom: 1px solid #f0f0f0; &.new { background: #fffdf5; } }
      .top { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; small { color: #777; margin-left: auto; } }
      .from { font-size: 13px; color: #555; margin-top: 4px; }
      p { white-space: pre-wrap; margin: 10px 0; font-size: 14px; }
      .acts { display: flex; gap: 8px; button { border: 1px solid #d6d6d6; background: #fff; border-radius: 8px; padding: 5px 12px; cursor: pointer; font: inherit; font-size: 13px; } } }
  `,
})
export class AdminMessageList implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);

  protected readonly tabs = [
    { value: 'NEW', label: 'New' }, { value: 'READ', label: 'Read' }, { value: 'REPLIED', label: 'Replied' },
    { value: 'CLOSED', label: 'Closed' }, { value: '', label: 'All' },
  ];
  protected readonly labels = STATUS_LABELS;
  protected readonly rows = signal<MessageRow[]>([]);
  protected readonly status = signal('NEW');
  protected readonly page = signal(0);
  protected readonly last = signal(true);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    const s = this.route.snapshot.queryParamMap.get('status');
    if (s !== null) this.status.set(s);
    this.load();
  }

  protected setStatus(s: string): void {
    this.status.set(s);
    this.page.set(0);
    this.load();
  }

  protected go(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected replyLink(m: MessageRow): string {
    return `mailto:${encodeURIComponent(m.email)}?subject=${encodeURIComponent('Re: ' + m.subject)}`;
  }

  protected mark(m: MessageRow, status: string): void {
    this.api.setMessageStatus(m.id, status).subscribe({
      next: () => this.load(),
      error: (err: unknown) => this.notify.error(err),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.api.messages(this.status(), this.page()).subscribe({
      next: (p) => {
        this.rows.set(p.content);
        this.last.set(p.last);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Messages could not be loaded.');
      },
    });
  }
}
