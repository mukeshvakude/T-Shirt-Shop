import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { DesignApi } from '../../core/api/design.api';
import { Design, DesignSummary, codeLabel } from '../../core/models/design.models';
import { NotifyService } from '../../core/notify.service';

/** Downloads the print package (design.json, previews, original artwork) as CODE.zip. */
function downloadZip(api: DesignApi, notify: NotifyService, code: string, busy: (on: boolean) => void): void {
  busy(true);
  api.adminDownload(code).subscribe({
    next: (blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${code}.zip`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      busy(false);
    },
    error: (err: unknown) => {
      busy(false);
      notify.error(err, 'The design could not be downloaded.');
    },
  });
}

/** Full design for the print team: previews, print areas, text and original artwork. */
@Component({
  selector: 'app-design-detail-dialog',
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule, DatePipe],
  template: `
    <h2 mat-dialog-title>{{ design.name || 'Untitled design' }} <small>{{ design.code }}</small></h2>
    <mat-dialog-content>
      <div class="previews">
        @if (design.previewFrontUrl) {
          <figure><img [src]="design.previewFrontUrl" alt="Front preview" /><figcaption>Front</figcaption></figure>
        }
        @if (design.previewBackUrl) {
          <figure><img [src]="design.previewBackUrl" alt="Back preview" /><figcaption>Back</figcaption></figure>
        }
      </div>
      <dl>
        <dt>Style</dt><dd>{{ label(design.tshirtType) }}</dd>
        <dt>Colour / size</dt><dd>{{ label(design.colorCode) }} / {{ design.sizeCode }}</dd>
        <dt>Print areas</dt><dd>{{ placements }}</dd>
        <dt>Print</dt><dd>{{ design.premiumPrint ? 'Premium' : 'Standard' }}</dd>
        <dt>Status</dt><dd>{{ label(design.status) }}{{ design.locked ? ' (in an order)' : '' }}</dd>
        <dt>Updated</dt><dd>{{ design.updatedAt | date: 'd MMM y, h:mm a' }}</dd>
      </dl>
      @if (design.textItems?.length) {
        <h3>Text</h3>
        <table>
          <thead><tr><th>Side</th><th>Text</th><th>Font</th><th>Size</th><th>Colour</th></tr></thead>
          <tbody>
            @for (t of design.textItems; track $index) {
              <tr>
                <td>{{ label(t.side) }}</td><td [style.font-family]="t.font">{{ t.text }}</td><td>{{ t.font }}</td><td>{{ t.size }}</td>
                <td><span class="dot" [style.background]="t.color"></span>{{ t.color }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
      @if (design.assets.length) {
        <h3>Original artwork</h3>
        <ul class="assets">
          @for (a of design.assets; track a.id) {
            <li>
              <a [href]="a.url" target="_blank" rel="noopener"><img [src]="a.url" [alt]="a.originalFilename" /></a>
              <span>{{ a.originalFilename }}<small>{{ a.width }} × {{ a.height }} px · {{ (a.sizeBytes / 1024).toFixed(0) }} KB</small></span>
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
      <button mat-flat-button (click)="download()" [disabled]="busy()">
        @if (busy()) { <mat-spinner diameter="16" /> } Download ZIP
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    h2 small { font-size: 13px; font-weight: 500; color: #666; margin-left: 8px; }
    .previews { display: flex; gap: 12px; flex-wrap: wrap; }
    figure { margin: 0; flex: 0 1 200px; }
    figure img { width: 100%; max-height: 250px; object-fit: contain; border-radius: 10px; background: #f3f1ec; display: block; }
    figcaption { font-size: 12px; color: #666; text-align: center; margin-top: 4px; }
    dl { display: grid; grid-template-columns: 120px 1fr; gap: 6px 12px; margin: 16px 0; font-size: 14px; }
    dt { color: #666; }
    dd { margin: 0; }
    h3 { font-size: 15px; margin: 18px 0 8px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #eee; }
    .dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; border: 1px solid #ccc; margin-right: 6px; vertical-align: middle; }
    .assets { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
    .assets li { display: flex; gap: 12px; align-items: center; font-size: 13px; }
    .assets img { width: 56px; height: 56px; object-fit: contain; border: 1px solid #eee; border-radius: 8px;
      background: repeating-conic-gradient(#eee 0 25%, #fff 0 50%) 0 0 / 12px 12px; }
    .assets small { display: block; color: #666; }
  `,
})
export class DesignDetailDialog {
  protected readonly design = inject<Design>(MAT_DIALOG_DATA);
  private readonly api = inject(DesignApi);
  private readonly notify = inject(NotifyService);
  protected readonly busy = signal(false);
  protected readonly label = codeLabel;
  protected readonly placements = this.design.placements.map(codeLabel).join(' + ');

  protected download(): void {
    downloadZip(this.api, this.notify, this.design.code, (on) => this.busy.set(on));
  }
}

/** Admin: every customer design, newest first, with details and the print ZIP. */
@Component({
  selector: 'app-design-list',
  imports: [MatPaginatorModule, MatProgressBarModule, MatButtonModule, DatePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Custom designs</h1>
          <p>{{ total() }} saved design{{ total() === 1 ? '' : 's' }}. Open one for print details or download the print files.</p>
        </div>
      </header>

      <section class="card list-card">
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        @if (!loading() && rows().length === 0) {
          <p class="empty">No customer designs yet.</p>
        }
        <div class="grid">
          @for (d of rows(); track d.code) {
            <article>
              <button type="button" class="preview" (click)="open(d)" [attr.aria-label]="'Open ' + d.code">
                @if (d.previewFrontUrl) { <img [src]="d.previewFrontUrl" [alt]="'Preview of ' + d.code" loading="lazy" /> }
              </button>
              <div class="body">
                <strong>{{ d.name || 'Untitled design' }}</strong>
                <small>{{ d.code }} · {{ label(d.tshirtType) }} · {{ label(d.colorCode) }} · {{ d.sizeCode }}</small>
                <small>{{ placementText(d) }}{{ d.premiumPrint ? ' · Premium' : '' }}</small>
                <small class="muted">{{ d.customerName }} · {{ d.customerEmail }}</small>
                <small class="muted">{{ d.updatedAt | date: 'd MMM y, h:mm a' }}</small>
              </div>
              <div class="actions">
                <button mat-button type="button" (click)="open(d)">View</button>
                <button mat-button type="button" (click)="download(d)" [disabled]="busy() === d.code">Download ZIP</button>
              </div>
            </article>
          }
        </div>
        <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()" [pageSizeOptions]="[24, 48, 96]"
                       (page)="page($event)" aria-label="Designs pages" />
      </section>
    </div>
  `,
  styles: `
    .list-card { padding: 0; overflow: hidden; }
    .empty { padding: 24px; color: #666; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 16px; padding: 16px; }
    article { border: 1px solid #e6e6e6; border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; background: #fff; }
    .preview { border: 0; padding: 0; cursor: pointer; aspect-ratio: 4 / 5; background: #f3f1ec; display: block; width: 100%; }
    .preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .body { padding: 10px 12px 4px; display: flex; flex-direction: column; gap: 3px; min-width: 0; }
    .body small { overflow-wrap: anywhere; }
    .muted { color: #666; }
    .actions { display: flex; justify-content: space-between; padding: 4px 4px 8px; margin-top: auto; }
  `,
})
export class DesignList implements OnInit {
  private readonly api = inject(DesignApi);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  protected readonly rows = signal<DesignSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(24);
  protected readonly loading = signal(true);
  protected readonly busy = signal<string | null>(null);
  protected readonly label = codeLabel;

  ngOnInit(): void {
    this.load();
  }

  protected placementText(d: DesignSummary): string {
    return d.placements.map(codeLabel).join(' + ');
  }

  protected page(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected open(summary: DesignSummary): void {
    this.api.adminGet(summary.code).subscribe({
      next: (design) => this.dialog.open(DesignDetailDialog, { data: design, width: '720px', maxWidth: '96vw' }),
      error: (err: unknown) => this.notify.error(err),
    });
  }

  protected download(d: DesignSummary): void {
    downloadZip(this.api, this.notify, d.code, (on) => this.busy.set(on ? d.code : null));
  }

  private load(): void {
    this.loading.set(true);
    this.api.adminList(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Designs could not be loaded.');
      },
    });
  }
}
