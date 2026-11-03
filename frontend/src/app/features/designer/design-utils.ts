import {
  CanvasJson,
  DesignAsset,
  DesignFont,
  DesignSide,
  DesignerOptions,
  PrintPlacement,
  TextItem,
} from '../../core/models/design.models';

/** Mockup images are 800 x 1000; print areas are fractions of that. */
export const MOCKUP_W = 800;
export const MOCKUP_H = 1000;
export const SIDES: DesignSide[] = ['FRONT', 'BACK'];
export const DRAFT_KEY = 'tshirt-designer-draft';

const ALLOWED_TYPES = new Set(['image', 'itext', 'i-text', 'textbox', 'text']);
const TEXT_TYPES = new Set(['itext', 'i-text', 'textbox', 'text']);

export function mockupUrl(mockup: string, side: DesignSide): string {
  return `/assets/mockups/${mockup}-white-${side.toLowerCase()}.png`;
}

/** Logical canvas size (mockup pixels) for a print area. */
export function areaSize(p: PrintPlacement): { width: number; height: number } {
  return { width: Math.round(p.w * MOCKUP_W), height: Math.round(p.h * MOCKUP_H) };
}

export function isInStock(options: DesignerOptions, type: string, color: string, size: string): boolean {
  return options.availability.some((a) => a.type === type && a.color === color && a.size === size && a.inStock);
}

/** True when the colour has at least one size in stock for this style. */
export function colorAvailable(options: DesignerOptions, type: string, color: string): boolean {
  return options.availability.some((a) => a.type === type && a.color === color && a.inStock);
}

/**
 * Keeps only what the server accepts: image and text objects, image sources as same-origin
 * paths (Fabric stores absolute URLs), and no clip paths.
 */
export function normalizeCanvas(json: CanvasJson | null | undefined, origin: string): CanvasJson | null {
  if (!json || !Array.isArray(json.objects)) return null;
  const objects = json.objects
    .filter((o) => ALLOWED_TYPES.has(String(o['type'] ?? '').toLowerCase()))
    .map((o) => {
      const copy: Record<string, unknown> = { ...o };
      delete copy['clipPath'];
      if (typeof copy['src'] === 'string') copy['src'] = samePath(copy['src'] as string, origin);
      return copy;
    });
  const result: CanvasJson = { version: json.version, objects };
  return result;
}

function samePath(src: string, origin: string): string {
  try {
    const url = new URL(src, origin);
    return url.origin === origin ? url.pathname : src;
  } catch {
    return src;
  }
}

/** A text object with visible text (custom text is charged once per design). */
export function isTextObject(o: Record<string, unknown>): boolean {
  return TEXT_TYPES.has(String(o['type'] ?? '').toLowerCase()) && String(o['text'] ?? '').trim().length > 0;
}

export function hasObjects(json: CanvasJson | null | undefined): boolean {
  return !!json && Array.isArray(json.objects) && json.objects.length > 0;
}

/** The structured text list the print team and the order use (one entry per text object). */
export function textItemsFrom(side: DesignSide, json: CanvasJson | null, fonts: DesignFont[]): TextItem[] {
  if (!json) return [];
  const families = new Set(fonts.map((f) => f.family));
  return json.objects
    .filter((o) => TEXT_TYPES.has(String(o['type'] ?? '').toLowerCase()))
    .map((o) => {
      const family = String(o['fontFamily'] ?? '');
      const size = Number(o['fontSize'] ?? 40) * Number(o['scaleY'] ?? 1);
      return {
        side,
        text: String(o['text'] ?? '').slice(0, 100),
        font: families.has(family) ? family : (fonts[0]?.family ?? family),
        size: Math.min(300, Math.max(8, Math.round(size))),
        color: toHex(o['fill']),
      };
    })
    .filter((t) => t.text.trim().length > 0);
}

/** Uploaded images actually used by the canvases. */
export function usedAssetIds(canvases: (CanvasJson | null)[], assets: DesignAsset[]): number[] {
  const srcs = new Set<string>();
  for (const c of canvases) {
    for (const o of c?.objects ?? []) {
      if (typeof o['src'] === 'string') srcs.add(o['src'] as string);
    }
  }
  return assets.filter((a) => srcs.has(a.url)).map((a) => a.id);
}

export function toHex(value: unknown): string {
  if (typeof value !== 'string') return '#000000';
  const v = value.trim();
  if (/^#[0-9a-f]{6}$/i.test(v)) return v.toUpperCase();
  if (/^#[0-9a-f]{3}$/i.test(v)) return ('#' + [...v.slice(1)].map((c) => c + c).join('')).toUpperCase();
  const m = v.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
  if (m) return '#' + [m[1], m[2], m[3]].map((n) => Number(n).toString(16).padStart(2, '0')).join('').toUpperCase();
  return '#000000';
}

/**
 * Moves and resizes objects when the print area of a side changes so the design keeps the
 * same relative position and fits the new area.
 */
export function rescaleObjects(json: CanvasJson, from: { width: number; height: number }, to: { width: number; height: number }): CanvasJson {
  const k = Math.min(to.width / from.width, to.height / from.height);
  const objects = json.objects.map((o) => {
    const left = Number(o['left'] ?? 0);
    const top = Number(o['top'] ?? 0);
    return {
      ...o,
      left: (left / from.width) * to.width,
      top: (top / from.height) * to.height,
      scaleX: Number(o['scaleX'] ?? 1) * k,
      scaleY: Number(o['scaleY'] ?? 1) * k,
    };
  });
  return { ...json, objects };
}

export interface DesignerDraft {
  code: string | null;
  name: string;
  tshirtType: string;
  colorCode: string;
  sizeCode: string;
  placements: string[];
  front: CanvasJson | null;
  back: CanvasJson | null;
  premiumPrint: boolean;
  assets: DesignAsset[];
  savedAt: number;
}

export function readDraft(): DesignerDraft | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    if (!raw) return null;
    const draft = JSON.parse(raw) as DesignerDraft;
    return draft && typeof draft.tshirtType === 'string' ? draft : null;
  } catch {
    return null;
  }
}

export function writeDraft(draft: DesignerDraft): void {
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // Storage full or blocked: the draft is a convenience only.
  }
}

export function clearDraft(): void {
  try {
    localStorage.removeItem(DRAFT_KEY);
  } catch {
    // ignore
  }
}

/** Dark garments get light default text. */
export function isDark(hex: string | null | undefined): boolean {
  if (!hex || !/^#[0-9a-f]{6}$/i.test(hex)) return false;
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  return 0.299 * r + 0.587 * g + 0.114 * b < 140;
}
