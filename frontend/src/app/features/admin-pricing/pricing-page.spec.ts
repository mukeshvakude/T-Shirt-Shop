import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdminCharge } from '../../core/models/pricing.models';
import { PricingPage } from './pricing-page';

const charges: AdminCharge[] = [
  { id: 1, type: 'PRINT_PLACEMENT', code: 'FRONT', label: 'Front', amount: 50, active: true, description: 'Front print', updatedAt: null },
  { id: null, type: 'SIZE', code: 'L', label: 'L', amount: 0, active: false, description: null, updatedAt: null },
];

describe('PricingPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PricingPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(PricingPage);
    fixture.detectChanges();
    http.expectOne('/api/admin/pricing/charges').flush(charges);
    http.expectOne('/api/admin/pricing/shipping').flush({ flatCharge: 49, freeThreshold: 999 });
    fixture.detectChanges();
    return fixture;
  }

  it('groups charges and shows choices without a charge as "No charge"', () => {
    const el: HTMLElement = render().nativeElement;
    const headings = [...el.querySelectorAll('h2')].map((h) => h.textContent?.trim());
    expect(headings).toEqual(['Shipping', 'Print areas', 'Sizes']);
    expect(el.textContent).toContain('No charge');
    expect(el.querySelectorAll('button[mat-flat-button]').length).toBe(1); // only the shipping save button
  });

  it('saves a changed amount for a choice that had no charge yet', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelectorAll<HTMLInputElement>('td input')[1];
    input.value = '25';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const save = [...el.querySelectorAll<HTMLButtonElement>('td button')].find((b) => b.textContent?.includes('Save'))!;
    save.click();
    const req = http.expectOne('/api/admin/pricing/charges');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ type: 'SIZE', code: 'L', amount: 25, active: true });
    req.flush({ ...charges[1], id: 9, amount: 25, active: true });
    fixture.detectChanges();
    expect(el.textContent).toContain('+₹25');
  });
});
