import { TestBed } from '@angular/core/testing';

import { CART_STORAGE_KEY, CartLine, GuestCartService } from './guest-cart.service';

const line = (sku: string, quantity: number, maxQuantity = 20): CartLine => ({
  sku, productId: 1, slug: 'tee', name: 'Tee', sizeCode: 'M', colorCode: 'BLACK', colorName: 'Black',
  unitPrice: 499, quantity, maxQuantity,
});

describe('GuestCartService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('adds lines, merges quantities and persists them', () => {
    const cart = TestBed.inject(GuestCartService);
    cart.add(line('A', 2));
    cart.add(line('A', 3));
    cart.add(line('B', 1));
    expect(cart.count()).toBe(6);
    expect(cart.quantityOf('A')).toBe(5);
    expect(JSON.parse(localStorage.getItem(CART_STORAGE_KEY)!)).toHaveLength(2);
  });

  it('never exceeds the available quantity', () => {
    const cart = TestBed.inject(GuestCartService);
    expect(cart.add(line('A', 2, 3))).toBe(2);
    expect(cart.add(line('A', 5, 3))).toBe(3);
  });

  it('restores the bag and ignores corrupted storage', () => {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify([line('A', 2), { sku: 'bad' }, null]));
    expect(TestBed.inject(GuestCartService).count()).toBe(2);

    TestBed.resetTestingModule();
    localStorage.setItem(CART_STORAGE_KEY, '{not json');
    expect(TestBed.inject(GuestCartService).count()).toBe(0);
  });

  it('removes and clears lines', () => {
    const cart = TestBed.inject(GuestCartService);
    cart.add(line('A', 1));
    cart.add(line('B', 1));
    cart.remove('A');
    expect(cart.lines().map((l) => l.sku)).toEqual(['B']);
    cart.clear();
    expect(cart.count()).toBe(0);
  });
});
