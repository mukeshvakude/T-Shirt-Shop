import { PriceQuote } from './pricing.models';

/** Custom T-Shirt designer (Phase 9). Mirrors com.shop.dto.design. */
export type DesignSide = 'FRONT' | 'BACK';

export interface TshirtType {
  code: string;
  label: string;
  productSlug: string;
  basePrice: number;
  /** Mockup base name: /assets/mockups/{mockup}-white-{front|back}.png */
  mockup: string;
}

export interface DesignChoice {
  code: string;
  name: string;
  hex: string | null;
}

/** Print area as fractions (0..1) of the 800 x 1000 mockup. */
export interface PrintPlacement {
  code: string;
  label: string;
  side: DesignSide;
  x: number;
  y: number;
  w: number;
  h: number;
  excludes: string[];
}

export interface DesignFont {
  code: string;
  label: string;
  family: string;
}

export interface VariantAvailability {
  type: string;
  color: string;
  size: string;
  inStock: boolean;
}

export interface DesignerLimits {
  maxUploadBytes: number;
  maxDimensionPx: number;
  allowedTypes: string[];
  maxObjectsPerSide: number;
  maxTextLength: number;
}

export interface DesignerOptions {
  tshirtTypes: TshirtType[];
  colors: DesignChoice[];
  sizes: DesignChoice[];
  placements: PrintPlacement[];
  fonts: DesignFont[];
  availability: VariantAvailability[];
  limits: DesignerLimits;
}

export interface DesignAsset {
  id: number;
  url: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
}

export interface TextItem {
  side: DesignSide;
  text: string;
  font: string;
  size: number;
  color: string;
}

/** Fabric.js canvas JSON (objects only images and text). */
export interface CanvasJson {
  version?: string;
  objects: Record<string, unknown>[];
  [key: string]: unknown;
}

export interface DesignRequest {
  name: string | null;
  tshirtType: string;
  colorCode: string;
  sizeCode: string;
  placements: string[];
  front: CanvasJson | null;
  back: CanvasJson | null;
  textItems: TextItem[];
  assetIds: number[];
  premiumPrint: boolean;
  previewFront: string;
  previewBack: string | null;
}

export interface Design {
  code: string;
  name: string | null;
  tshirtType: string;
  colorCode: string;
  sizeCode: string;
  placements: string[];
  front: CanvasJson | null;
  back: CanvasJson | null;
  textItems: TextItem[] | null;
  premiumPrint: boolean;
  previewFrontUrl: string | null;
  previewBackUrl: string | null;
  assets: DesignAsset[];
  status: string;
  locked: boolean;
  productSlug: string;
  price: PriceQuote | null;
  createdAt: string;
  updatedAt: string;
}

export interface DesignSummary {
  code: string;
  name: string | null;
  tshirtType: string;
  colorCode: string;
  sizeCode: string;
  placements: string[];
  premiumPrint: boolean;
  previewFrontUrl: string | null;
  previewBackUrl: string | null;
  status: string;
  locked: boolean;
  updatedAt: string;
  customerId: number | null;
  customerName: string | null;
  customerEmail: string | null;
}

/** ROUND_NECK -> Round neck, SKY_BLUE -> Sky blue, FULL_FRONT -> Full front. */
export function codeLabel(code: string | null | undefined): string {
  if (!code) return '';
  const words = code.toLowerCase().split('_');
  const text = words.join(' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}
