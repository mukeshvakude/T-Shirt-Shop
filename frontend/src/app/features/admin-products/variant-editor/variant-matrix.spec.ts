import { AdminVariant, Color, Size } from '../../../core/models/catalog.models';
import {
  activeColorIds,
  buildMatrix,
  cellKey,
  parseQuantity,
  sizeGroupForCategory,
  toVariantRequests,
  validateCells,
} from './variant-matrix';

const sizes: Size[] = [
  { id: 1, code: 'S', label: 'Small', sizeGroup: 'APPAREL', sortOrder: 1 },
  { id: 2, code: 'M', label: 'Medium', sizeGroup: 'APPAREL', sortOrder: 2 },
];
const colors: Color[] = [
  { id: 10, code: 'WHITE', name: 'White', hex: '#FFFFFF', sortOrder: 1 },
  { id: 11, code: 'BLACK', name: 'Black', hex: '#111111', sortOrder: 2 },
  { id: 12, code: 'RED', name: 'Red', hex: '#C62828', sortOrder: 3 },
];

function variant(sizeId: number, colorId: number, onHand: number, active = true, reserved = 0): AdminVariant {
  return {
    id: sizeId * 100 + colorId, sku: `SKU-${sizeId}-${colorId}`, sizeId, sizeCode: '', colorId, colorCode: '',
    colorName: '', colorHex: '', quantityOnHand: onHand, quantityReserved: reserved, available: onHand - reserved,
    lowStockThreshold: 3, lowStock: false, active, productId: 1, productName: 'Tee',
  };
}

describe('variant matrix', () => {
  it('uses waist sizes only for pants', () => {
    expect(sizeGroupForCategory('PANTS')).toBe('WAIST');
    expect(sizeGroupForCategory('TSHIRTS')).toBe('APPAREL');
    expect(sizeGroupForCategory(undefined)).toBe('APPAREL');
  });

  it('lists colours of active variants in display order', () => {
    const ids = activeColorIds([variant(1, 12, 1), variant(1, 10, 1), variant(2, 11, 1, false)], colors);
    expect(ids).toEqual([10, 12]);
  });

  it('fills the grid from existing variants and leaves inactive ones blank', () => {
    const cells = buildMatrix(sizes, [10, 11], [variant(1, 10, 7), variant(2, 11, 4, false, 0)]);
    expect(cells.size).toBe(4);
    expect(cells.get(cellKey(1, 10))).toMatchObject({ quantity: 7, threshold: 3, sku: 'SKU-1-10' });
    expect(cells.get(cellKey(2, 11))?.quantity).toBeNull();
    expect(cells.get(cellKey(2, 10))).toMatchObject({ quantity: null, threshold: 5, reserved: 0 });
  });

  it('parses quantities strictly', () => {
    expect(parseQuantity('')).toBeNull();
    expect(parseQuantity('  ')).toBeNull();
    expect(parseQuantity('12')).toBe(12);
    expect(parseQuantity('0')).toBe(0);
    expect(parseQuantity('-1')).toBe('invalid');
    expect(parseQuantity('2.5')).toBe('invalid');
    expect(parseQuantity('ten')).toBe('invalid');
  });

  it('turns offered cells into active variant requests', () => {
    const cells = buildMatrix(sizes, [10], [variant(1, 10, 7)]);
    cells.set(cellKey(2, 10), { ...cells.get(cellKey(2, 10))!, quantity: 0 });

    expect(toVariantRequests(cells.values())).toEqual([
      { sizeId: 1, colorId: 10, quantityOnHand: 7, lowStockThreshold: 3, active: true },
      { sizeId: 2, colorId: 10, quantityOnHand: 0, lowStockThreshold: 5, active: true },
    ]);
  });

  it('reports stock set below reserved units', () => {
    const cells = buildMatrix(sizes, [11], [variant(1, 11, 10, true, 4)]);
    cells.set(cellKey(1, 11), { ...cells.get(cellKey(1, 11))!, quantity: 2 });

    expect(validateCells(cells.values(), sizes, colors)).toEqual([
      'S / Black: at least 4 are reserved by pending orders.',
    ]);
  });
});
