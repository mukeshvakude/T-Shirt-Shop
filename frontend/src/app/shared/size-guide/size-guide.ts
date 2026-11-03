import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

/** Approximate garment measurements in inches. */
const APPAREL = [
  { size: 'S', chest: 38, length: 27, shoulder: 17 },
  { size: 'M', chest: 40, length: 28, shoulder: 18 },
  { size: 'L', chest: 42, length: 29, shoulder: 19 },
  { size: 'XL', chest: 44, length: 30, shoulder: 20 },
  { size: 'XXL', chest: 46, length: 31, shoulder: 21 },
];
const WAIST = [28, 30, 32, 34, 36, 38].map((w) => ({ size: String(w), waist: w, hip: w + 8, inseam: 30 }));

@Component({
  selector: 'app-size-guide',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Size guide</h2>
    <mat-dialog-content>
      <p class="muted">Garment measurements in inches. For oversized styles, choose your usual size for the relaxed look.</p>
      @if (data.group === 'WAIST') {
        <table>
          <thead><tr><th>Size</th><th>Waist</th><th>Hip</th><th>Inseam</th></tr></thead>
          <tbody>@for (r of waist; track r.size) { <tr><td>{{ r.size }}</td><td>{{ r.waist }}</td><td>{{ r.hip }}</td><td>{{ r.inseam }}</td></tr> }</tbody>
        </table>
      } @else {
        <table>
          <thead><tr><th>Size</th><th>Chest</th><th>Length</th><th>Shoulder</th></tr></thead>
          <tbody>@for (r of apparel; track r.size) { <tr><td>{{ r.size }}</td><td>{{ r.chest }}</td><td>{{ r.length }}</td><td>{{ r.shoulder }}</td></tr> }</tbody>
        </table>
      }
      <p class="muted small">Measure a garment that fits you well, laid flat, and compare. Between sizes? Size up for comfort.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-flat-button mat-dialog-close>Done</button></mat-dialog-actions>
  `,
  styles: `
    table { width: 100%; border-collapse: collapse; margin: 8px 0 12px; }
    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--shop-line); }
    th { font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--shop-muted); }
    .small { font-size: 13px; }
  `,
})
export class SizeGuide {
  protected readonly data = inject<{ group: 'APPAREL' | 'WAIST' }>(MAT_DIALOG_DATA);
  protected readonly apparel = APPAREL;
  protected readonly waist = WAIST;
}
