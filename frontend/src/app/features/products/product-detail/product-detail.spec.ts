import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { GuestCartService } from '../../../core/cart/guest-cart.service';
import { ProductDetail as Product } from '../../../core/models/store.models';
import { ProductDetail } from './product-detail';

const PRODUCT: Product = {
  id: 1, name: 'Essential Crew Neck Tee', slug: 'essential-crew-neck-tee',
  category: { id: 1, code: 'TSHIRTS', name: 'T-Shirts', slug: 't-shirts', sortOrder: 1 },
  shortDescription: 'The everyday premium tee', basePrice: 599, discountPrice: 449, price: 449, discountPercent: 25,
  avgRating: 0, reviewCount: 0, featured: true, newArrival: false, customizable: false, inStock: true,
  images: [{ id: 1, url: '/i/black.jpg', colorCode: 'BLACK', sortOrder: 0, primary: true }],
  sizes: [
    { id: 1, code: 'S', label: 'Small', sizeGroup: 'APPAREL', sortOrder: 1 },
    { id: 2, code: 'M', label: 'Medium', sizeGroup: 'APPAREL', sortOrder: 2 },
  ],
  colors: [{ id: 11, code: 'BLACK', name: 'Black', hex: '#111', sortOrder: 1 }],
  variants: [
    { sizeCode: 'S', colorCode: 'BLACK', sku: 'TS-S-B', availableQuantity: 0, inStock: false, lowStock: false },
    { sizeCode: 'M', colorCode: 'BLACK', sku: 'TS-M-B', availableQuantity: 2, inStock: true, lowStock: true },
  ],
};

describe('ProductDetail page', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  function render(product: Product | null, status = 200) {
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.componentRef.setInput('slug', 'essential-crew-neck-tee');
    fixture.detectChanges();
    TestBed.tick();
    const req = httpMock.expectOne('/api/products/essential-crew-neck-tee');
    if (product) {
      req.flush(product);
      httpMock.expectOne((r) => r.url.endsWith('/related')).flush([]);
    } else {
      req.flush({ code: 'RESOURCE_NOT_FOUND' }, { status, statusText: 'Not Found' });
    }
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const button = (el: HTMLElement, text: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(text)) as HTMLButtonElement;

  it('shows price, discount and disables sold-out sizes', () => {
    const { el } = render(PRODUCT);
    expect(el.querySelector('h1')?.textContent).toContain('Essential Crew Neck Tee');
    expect(el.querySelector('.price strong')?.textContent).toContain('₹449');
    expect(el.querySelector('.save')?.textContent).toContain('₹150');
    const sizes = [...el.querySelectorAll<HTMLButtonElement>('.sizes button')];
    expect(sizes.map((b) => b.disabled)).toEqual([true, false]);
  });

  it('asks for a size, then adds to the bag within the available quantity', () => {
    const { el, fixture } = render(PRODUCT);
    const cart = TestBed.inject(GuestCartService);
    button(el, 'Add to bag').click();
    fixture.detectChanges();
    expect(el.querySelector('.field-error')?.textContent).toContain('choose a size');
    expect(cart.count()).toBe(0);

    button(el, 'M').click();
    fixture.detectChanges();
    expect(el.querySelector('.stock')?.textContent).toContain('Only 2 left');
    el.querySelector<HTMLButtonElement>('[aria-label="Increase quantity"]')!.click();
    fixture.detectChanges();
    expect(el.querySelector<HTMLButtonElement>('[aria-label="Increase quantity"]')!.disabled).toBe(true);

    button(el, 'Add to bag').click();
    expect(cart.lines()).toEqual([expect.objectContaining({ sku: 'TS-M-B', quantity: 2, unitPrice: 449, colorName: 'Black' })]);
  });

  it('buy now adds the item and opens the bag', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { el, fixture } = render(PRODUCT);
    button(el, 'M').click();
    fixture.detectChanges();
    button(el, 'Buy now').click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/cart'));
    expect(TestBed.inject(GuestCartService).count()).toBe(1);
  });

  it('shows a friendly page for unknown products', () => {
    const { el } = render(null, 404);
    expect(el.textContent).toContain('This product is not available');
  });
});
