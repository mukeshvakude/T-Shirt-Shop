import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { HomeData, ProductSummary } from '../../core/models/store.models';
import { Home, fullRows } from './home';

function product(id: number, overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id, name: `Tee ${id}`, slug: `tee-${id}`, categoryCode: 'TSHIRTS', categorySlug: 't-shirts', categoryName: 'T-Shirts',
    basePrice: 599, price: 599, discountPercent: 0, imageUrl: `/assets/products/tee-${id}.jpg`, avgRating: 0,
    reviewCount: 0, sizes: ['S', 'M'], colors: [], inStock: true, featured: false, newArrival: false,
    customizable: false, ...overrides,
  };
}

function home(overrides: Partial<HomeData> = {}): HomeData {
  return {
    categories: [{ id: 1, code: 'TSHIRTS', name: 'T-Shirts', slug: 't-shirts', sortOrder: 1 }],
    featured: [1, 2, 3, 4, 5].map((i) => product(i)),
    newArrivals: [],
    bestSellers: [product(6)],
    onSale: [],
    collections: [],
    customTshirt: { available: true, fromPrice: 299, tshirtTypes: ['Round Neck', 'Polo'] },
    offers: [],
    reviews: [],
    ...overrides,
  };
}

describe('Home page', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render(data: HomeData) {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    httpMock.expectOne('/api/home').flush(data);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const headings = (el: HTMLElement) => [...el.querySelectorAll('.section-head h2')].map((h) => h.textContent?.trim());

  it('renders only the sections that have content', () => {
    const el = render(home());
    expect(headings(el)).toEqual(['Shop by category', 'Featured', 'Best sellers']);
    expect(el.textContent).toContain('Your design. Printed on premium cotton.');
    expect(el.textContent).toContain('₹299');
    expect(el.textContent).not.toContain('What customers say');
    expect(el.textContent).not.toContain('Special offers');
  });

  it('shows whole rows of products', () => {
    const el = render(home());
    const featured = el.querySelectorAll('.section')[1].querySelectorAll('app-product-card');
    expect(featured.length).toBe(4);
  });

  it('shows offers and reviews when the API returns them', () => {
    const el = render(home({
      offers: [{ code: 'WELCOME10', headline: '10% off', description: 'First order', discountType: 'PERCENT',
        discountValue: 10, minOrderAmount: 499, validTo: '2027-12-31T00:00:00Z' }],
      reviews: [{ id: 1, reviewerName: 'Ananya S.', rating: 5, title: 'Great', comment: 'Lovely fabric.',
        productName: 'Tee 1', productSlug: 'tee-1', createdAt: '2026-11-01T00:00:00Z' }],
    }));
    expect(headings(el)).toContain('Special offers');
    expect(el.querySelector('.coupon .code')?.textContent).toContain('WELCOME10');
    expect(el.querySelector('.review figcaption')?.textContent).toContain('Ananya S.');
  });

  it('offers a retry when loading fails', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    httpMock.expectOne('/api/home').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('could not load');

    el.querySelector<HTMLButtonElement>('.error-state button')!.click();
    httpMock.expectOne('/api/home').flush(home());
    fixture.detectChanges();
    expect(headings(el)).toContain('Featured');
  });

  it('trims lists to full rows', () => {
    expect(fullRows([1, 2, 3])).toEqual([1, 2, 3]);
    expect(fullRows([1, 2, 3, 4, 5, 6])).toEqual([1, 2, 3, 4]);
    expect(fullRows([1, 2, 3, 4, 5, 6, 7, 8, 9])).toHaveLength(8);
  });
});
