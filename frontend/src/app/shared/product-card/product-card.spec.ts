import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ProductSummary } from '../../core/models/store.models';
import { ProductCard } from './product-card';

const base: ProductSummary = {
  id: 1, name: 'Essential Crew Neck Tee', slug: 'essential-crew-neck-tee', categoryCode: 'TSHIRTS',
  categorySlug: 't-shirts', categoryName: 'T-Shirts', basePrice: 599, discountPrice: 449, price: 449,
  discountPercent: 25, imageUrl: '/assets/products/essential-crew-neck-tee-black.jpg', avgRating: 4.5,
  reviewCount: 12, sizes: ['S', 'M', 'L', 'XL', 'XXL'],
  colors: [
    { code: 'WHITE', name: 'White', hex: '#FFFFFF' }, { code: 'BLACK', name: 'Black', hex: '#111111' },
    { code: 'NAVY', name: 'Navy', hex: '#1B2340' }, { code: 'RED', name: 'Red', hex: '#C62828' },
    { code: 'GREY', name: 'Grey', hex: '#8E8E8E' }, { code: 'OLIVE', name: 'Olive', hex: '#556B2F' },
  ],
  inStock: true, featured: true, newArrival: true, customizable: false,
};

describe('ProductCard', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [ProductCard], providers: [provideRouter([])] }));

  function render(product: ProductSummary): HTMLElement {
    const fixture = TestBed.createComponent(ProductCard);
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('shows price, discount, rating, size range and colours', () => {
    const el = render(base);
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/product/essential-crew-neck-tee');
    expect(el.querySelector('.price strong')?.textContent).toContain('₹449');
    expect(el.querySelector('.price s')?.textContent).toContain('₹599');
    expect(el.querySelector('.badge.sale')?.textContent).toContain('25%');
    expect(el.querySelector('.sizes')?.textContent).toBe('S–XXL');
    expect(el.querySelectorAll('.swatch').length).toBe(5);
    expect(el.querySelector('.more')?.textContent).toBe('+1');
    expect(el.querySelector('[role=img]')?.getAttribute('aria-label')).toBe('Rated 4.5 out of 5 from 12 reviews');
  });

  it('marks sold-out products and hides the discount badge', () => {
    const el = render({ ...base, inStock: false });
    expect(el.querySelector('.badge.muted')?.textContent).toContain('Sold out');
    expect(el.querySelector('.badge.sale')).toBeNull();
  });

  it('sends customizable blanks to the designer and shows a from-price', () => {
    const el = render({ ...base, customizable: true, discountPrice: undefined, discountPercent: 0, price: 299 });
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/customize');
    expect(el.querySelector('.from')?.textContent).toBe('From');
  });

  it('shows waist sizes without the W prefix', () => {
    const el = render({ ...base, sizes: ['W28', 'W30', 'W38'] });
    expect(el.querySelector('.sizes')?.textContent).toBe('28–38');
  });
});
