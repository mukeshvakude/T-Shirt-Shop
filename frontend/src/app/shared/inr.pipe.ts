import { Pipe, PipeTransform } from '@angular/core';

const WHOLE = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const PAISE = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Rupees without decimals for whole amounts (₹898) and with paise otherwise (₹857.20). */
@Pipe({ name: 'inr' })
export class InrPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '';
    return (Number.isInteger(Math.round(value * 100) / 100) ? WHOLE : PAISE).format(value);
  }
}
