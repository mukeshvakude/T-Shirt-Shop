import { StaticCanvas } from 'fabric';

import { CanvasJson, PrintPlacement } from '../../core/models/design.models';
import { MOCKUP_H, MOCKUP_W, areaSize, hasObjects } from './design-utils';

const images = new Map<string, Promise<HTMLImageElement>>();

export function loadImage(url: string): Promise<HTMLImageElement> {
  let pending = images.get(url);
  if (!pending) {
    pending = new Promise((resolve, reject) => {
      const img = new Image();
      img.decoding = 'async';
      img.onload = () => resolve(img);
      img.onerror = () => {
        images.delete(url);
        reject(new Error(`Could not load ${url}`));
      };
      img.src = url;
    });
    images.set(url, pending);
  }
  return pending;
}

/**
 * Draws the white mockup in the chosen colour, keeping its folds and shading
 * (multiply, then cut back to the garment outline).
 */
export function drawTintedShirt(ctx: CanvasRenderingContext2D, mockup: HTMLImageElement, hex: string, width: number, height: number): void {
  ctx.save();
  ctx.clearRect(0, 0, width, height);
  ctx.globalCompositeOperation = 'source-over';
  ctx.drawImage(mockup, 0, 0, width, height);
  ctx.globalCompositeOperation = 'multiply';
  ctx.fillStyle = hex;
  ctx.fillRect(0, 0, width, height);
  ctx.globalCompositeOperation = 'destination-in';
  ctx.drawImage(mockup, 0, 0, width, height);
  ctx.restore();
}

/** Renders a side's design at its logical (mockup pixel) size. */
export async function renderDesign(json: CanvasJson, placement: PrintPlacement): Promise<HTMLCanvasElement> {
  const { width, height } = areaSize(placement);
  const el = document.createElement('canvas');
  const canvas = new StaticCanvas(el, { width, height, enableRetinaScaling: false, renderOnAddRemove: false });
  try {
    await canvas.loadFromJSON(json);
    canvas.renderAll();
    const out = document.createElement('canvas');
    out.width = width;
    out.height = height;
    out.getContext('2d')!.drawImage(canvas.getElement(), 0, 0, width, height);
    return out;
  } finally {
    void canvas.dispose();
  }
}

/** PNG preview of one side: coloured T-Shirt with the design in its print area. */
export async function renderPreview(
  mockupUrl: string,
  hex: string,
  placement: PrintPlacement | null,
  json: CanvasJson | null,
  scale = 0.75,
): Promise<string> {
  const width = Math.round(MOCKUP_W * scale);
  const height = Math.round(MOCKUP_H * scale);
  const out = document.createElement('canvas');
  out.width = width;
  out.height = height;
  const ctx = out.getContext('2d')!;
  ctx.fillStyle = '#f3f1ec';
  ctx.fillRect(0, 0, width, height);

  const shirt = document.createElement('canvas');
  shirt.width = width;
  shirt.height = height;
  drawTintedShirt(shirt.getContext('2d')!, await loadImage(mockupUrl), hex, width, height);
  ctx.drawImage(shirt, 0, 0);

  if (placement && json && hasObjects(json)) {
    const art = await renderDesign(json, placement);
    ctx.drawImage(art, placement.x * width, placement.y * height, placement.w * width, placement.h * height);
  }
  return out.toDataURL('image/png');
}
