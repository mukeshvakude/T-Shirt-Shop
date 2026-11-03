import { ProductDetail, ProductImageView, VariantAvailability } from '../../../core/models/store.models';

/** First colour (in display order) that has stock in any size; otherwise the first colour. */
export function defaultColor(product: ProductDetail): string | null {
  const inStock = product.colors.find((c) => product.variants.some((v) => v.colorCode === c.code && v.inStock));
  return inStock?.code ?? product.colors[0]?.code ?? null;
}

export function findVariant(product: ProductDetail, size: string | null, color: string | null): VariantAvailability | null {
  if (!size || !color) return null;
  return product.variants.find((v) => v.sizeCode === size && v.colorCode === color) ?? null;
}

/** A size is selectable for a colour when that combination exists and is in stock. */
export function sizeAvailable(product: ProductDetail, size: string, color: string | null): boolean {
  return product.variants.some((v) => v.sizeCode === size && v.inStock && (color === null || v.colorCode === color));
}

export function colorAvailable(product: ProductDetail, color: string): boolean {
  return product.variants.some((v) => v.colorCode === color && v.inStock);
}

/** If only one size exists and it is available, pick it for the customer. */
export function autoSize(product: ProductDetail, color: string | null): string | null {
  return product.sizes.length === 1 && sizeAvailable(product, product.sizes[0].code, color) ? product.sizes[0].code : null;
}

/**
 * Images for the chosen colour plus images not tied to a colour (detail shots).
 * Falls back to every image when the colour has none of its own. Main image first.
 */
export function galleryFor(images: ProductImageView[], color: string | null): ProductImageView[] {
  const ordered = [...images].sort((a, b) => Number(b.primary) - Number(a.primary) || a.sortOrder - b.sortOrder);
  if (!color) return ordered;
  const forColor = ordered.filter((i) => i.colorCode === color);
  if (forColor.length === 0) return ordered;
  return [...forColor, ...ordered.filter((i) => !i.colorCode)];
}

export type StockMessage = { tone: 'ok' | 'low' | 'out' | 'hint'; text: string };

export function stockMessage(product: ProductDetail, size: string | null, color: string | null): StockMessage {
  if (!product.inStock) return { tone: 'out', text: 'Out of stock' };
  if (!size) return { tone: 'hint', text: 'Select a size' };
  const variant = findVariant(product, size, color);
  if (!variant || !variant.inStock) return { tone: 'out', text: 'This size is out of stock in this colour' };
  if (variant.lowStock) return { tone: 'low', text: `Only ${variant.availableQuantity} left` };
  return { tone: 'ok', text: 'In stock, ready to ship' };
}
