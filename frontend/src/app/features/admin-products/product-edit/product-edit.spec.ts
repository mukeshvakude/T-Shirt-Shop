import { FormControl, FormGroup } from '@angular/forms';

import { checkImageFile } from '../image-manager/image-manager';
import { discountBelowBase } from './product-edit';

describe('product form rules', () => {
  const form = (base: number | null, discount: number | string | null) =>
    new FormGroup({ basePrice: new FormControl(base), discountPrice: new FormControl(discount) });

  it('accepts no discount or a lower discount', () => {
    expect(discountBelowBase(form(799, null))).toBeNull();
    expect(discountBelowBase(form(799, ''))).toBeNull();
    expect(discountBelowBase(form(799, 599))).toBeNull();
  });

  it('rejects a discount equal to or above the price', () => {
    expect(discountBelowBase(form(799, 799))).toEqual({ discountNotLower: true });
    expect(discountBelowBase(form(799, 999))).toEqual({ discountNotLower: true });
  });
});

describe('image file pre-checks', () => {
  const file = (name: string, type: string, size: number) =>
    new File([new Uint8Array(size)], name, { type });

  it('accepts PNG and JPG up to 5 MB', () => {
    expect(checkImageFile(file('a.png', 'image/png', 10))).toBeNull();
    expect(checkImageFile(file('a.jpg', 'image/jpeg', 5 * 1024 * 1024))).toBeNull();
  });

  it('rejects other types and large files', () => {
    expect(checkImageFile(file('a.gif', 'image/gif', 10))).toContain('only PNG and JPG');
    expect(checkImageFile(file('a.svg', 'image/svg+xml', 10))).toContain('only PNG and JPG');
    expect(checkImageFile(file('big.png', 'image/png', 5 * 1024 * 1024 + 1))).toContain('5 MB');
  });
});
