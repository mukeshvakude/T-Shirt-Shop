import { ProductDetail, ProductImageView } from '../../../core/models/store.models';
import { autoSize, colorAvailable, defaultColor, galleryFor, sizeAvailable, stockMessage } from './product-options';

function product(overrides: Partial<ProductDetail> = {}): ProductDetail {
  return {
    id: 1, name: 'Tee', slug: 'tee', category: { id: 1, code: 'TSHIRTS', name: 'T-Shirts', slug: 't-shirts', sortOrder: 1 },
    basePrice: 599, price: 599, discountPercent: 0, avgRating: 0, reviewCount: 0, featured: false, newArrival: false,
    customizable: false, inStock: true, images: [],
    sizes: [
      { id: 1, code: 'S', label: 'Small', sizeGroup: 'APPAREL', sortOrder: 1 },
      { id: 2, code: 'M', label: 'Medium', sizeGroup: 'APPAREL', sortOrder: 2 },
    ],
    colors: [
      { id: 10, code: 'WHITE', name: 'White', hex: '#fff', sortOrder: 1 },
      { id: 11, code: 'BLACK', name: 'Black', hex: '#111', sortOrder: 2 },
    ],
    variants: [
      { sizeCode: 'S', colorCode: 'WHITE', sku: 'S-W', availableQuantity: 0, inStock: false, lowStock: false },
      { sizeCode: 'M', colorCode: 'WHITE', sku: 'M-W', availableQuantity: 0, inStock: false, lowStock: false },
      { sizeCode: 'S', colorCode: 'BLACK', sku: 'S-B', availableQuantity: 3, inStock: true, lowStock: true },
      { sizeCode: 'M', colorCode: 'BLACK', sku: 'M-B', availableQuantity: 20, inStock: true, lowStock: false },
    ],
    ...overrides,
  };
}

const img = (id: number, colorCode?: string, primary = false, sortOrder = id): ProductImageView =>
  ({ id, url: `/i/${id}.jpg`, colorCode, primary, sortOrder });

describe('product options', () => {
  it('starts with the first colour that has stock', () => {
    expect(defaultColor(product())).toBe('BLACK');
    const soldOut = product({ variants: product().variants.map((v) => ({ ...v, inStock: false })) });
    expect(defaultColor(soldOut)).toBe('WHITE');
  });

  it('knows which sizes and colours can be bought', () => {
    const p = product();
    expect(colorAvailable(p, 'WHITE')).toBe(false);
    expect(colorAvailable(p, 'BLACK')).toBe(true);
    expect(sizeAvailable(p, 'S', 'WHITE')).toBe(false);
    expect(sizeAvailable(p, 'S', 'BLACK')).toBe(true);
  });

  it('auto-selects the only size', () => {
    const one = product({ sizes: [product().sizes[1]] });
    expect(autoSize(one, 'BLACK')).toBe('M');
    expect(autoSize(product(), 'BLACK')).toBeNull();
  });

  it('shows colour images first, then neutral detail shots', () => {
    const images = [img(1, 'WHITE', true), img(2, 'BLACK'), img(3, undefined, false, 99), img(4, 'BLACK')];
    expect(galleryFor(images, 'BLACK').map((i) => i.id)).toEqual([2, 4, 3]);
    expect(galleryFor(images, 'RED').map((i) => i.id)).toEqual([1, 2, 4, 3]);
    expect(galleryFor(images, null)[0].id).toBe(1);
  });

  it('describes stock in plain words', () => {
    const p = product();
    expect(stockMessage(p, null, 'BLACK')).toEqual({ tone: 'hint', text: 'Select a size' });
    expect(stockMessage(p, 'S', 'BLACK')).toEqual({ tone: 'low', text: 'Only 3 left' });
    expect(stockMessage(p, 'M', 'BLACK').tone).toBe('ok');
    expect(stockMessage(p, 'M', 'WHITE').tone).toBe('out');
    expect(stockMessage(product({ inStock: false }), 'M', 'BLACK').text).toBe('Out of stock');
  });
});
