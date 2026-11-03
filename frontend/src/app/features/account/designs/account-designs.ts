import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';

import { DesignApi } from '../../../core/api/design.api';
import { DesignSummary, codeLabel } from '../../../core/models/design.models';
import { NotifyService } from '../../../core/notify.service';
import { confirmAction } from '../../../shared/confirm-dialog/confirm-dialog';

/** The customer's saved T-Shirt designs: open to edit, or delete. */
@Component({
  selector: 'app-account-designs',
  imports: [RouterLink, DatePipe],
  styleUrl: '../account-pages.scss',
  template: `
    <header class="page-head head-row">
      <div>
        <h1>My designs</h1>
        <p>Custom T-Shirts you have saved. Open one to keep editing.</p>
      </div>
      <a class="btn" routerLink="/customize">New design</a>
    </header>

    @if (loading()) {
      <section class="panel"><p class="muted">Loading…</p></section>
    } @else if (designs().length === 0) {
      <section class="panel">
        <div class="empty">
          <span class="material-symbols-outlined">draw</span>
          <p>No saved designs yet. Upload your artwork or add text, then save it here to order it later.</p>
          <a class="btn" routerLink="/customize">Design a T-Shirt</a>
        </div>
      </section>
    } @else {
      <div class="grid">
        @for (d of designs(); track d.code) {
          <article class="design">
            <a class="preview" [routerLink]="['/customize', d.code]" [attr.aria-label]="'Open ' + (d.name || d.code)">
              @if (d.previewFrontUrl) { <img [src]="d.previewFrontUrl" [alt]="'Front of ' + (d.name || d.code)" loading="lazy" /> }
              @if (d.previewBackUrl) { <img class="back" [src]="d.previewBackUrl" alt="" loading="lazy" /> }
            </a>
            <div class="body">
              <strong>{{ d.name || 'Untitled design' }}</strong>
              <small>{{ d.code }} · {{ label(d.tshirtType) }} · {{ label(d.colorCode) }} · {{ d.sizeCode }}</small>
              <small>{{ placementText(d) }}{{ d.premiumPrint ? ' · Premium print' : '' }}</small>
              <small class="muted">Updated {{ d.updatedAt | date: 'd MMM y, h:mm a' }}</small>
              @if (d.locked) { <span class="tag">In an order</span> }
            </div>
            <div class="actions">
              <a [routerLink]="['/customize', d.code]">{{ d.locked ? 'View' : 'Edit' }}</a>
              @if (!d.locked) {
                <button type="button" class="danger" (click)="remove(d)">Delete</button>
              }
            </div>
          </article>
        }
      </div>
    }
  `,
  styles: `
    .head-row { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 18px; }
    .design { border: 1px solid var(--shop-line); border-radius: 16px; overflow: hidden; background: #fff; display: flex; flex-direction: column; }
    .preview { position: relative; display: block; aspect-ratio: 4 / 5; background: #f3f1ec; }
    .preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .preview img.back { position: absolute; right: 8px; bottom: 8px; width: 34%; height: auto; border-radius: 8px;
      border: 2px solid #fff; box-shadow: 0 2px 8px rgb(0 0 0 / 15%); }
    .body { padding: 14px 16px 6px; display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .body small { color: #444; overflow-wrap: anywhere; }
    .tag { align-self: flex-start; margin-top: 6px; font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px; background: #fff6e0; color: #6b4e00; }
    .actions { display: flex; gap: 16px; padding: 10px 16px 16px; margin-top: auto; }
    .actions a, .actions button { font: 600 14px Inter, sans-serif; color: #111; background: none; border: 0; padding: 0; cursor: pointer; }
    .actions .danger { color: var(--shop-danger); }
  `,
})
export class AccountDesigns implements OnInit {
  private readonly api = inject(DesignApi);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  protected readonly designs = signal<DesignSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly label = codeLabel;

  ngOnInit(): void {
    this.load();
  }

  protected placementText(d: DesignSummary): string {
    return d.placements.map(codeLabel).join(' + ');
  }

  protected remove(design: DesignSummary): void {
    confirmAction(this.dialog, {
      title: 'Delete this design?',
      message: `${design.name || design.code} and its previews will be deleted. This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.delete(design.code).subscribe({
        next: () => {
          this.notify.success('Design deleted.');
          this.load();
        },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  private load(): void {
    this.api.mine().subscribe({
      next: (list) => {
        this.designs.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Your designs could not be loaded.');
      },
    });
  }
}
