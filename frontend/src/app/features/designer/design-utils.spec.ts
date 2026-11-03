import { DesignerOptions } from '../../core/models/design.models';
import { codeLabel } from '../../core/models/design.models';
import {
  DRAFT_KEY,
  areaSize,
  clearDraft,
  colorAvailable,
  hasObjects,
  isDark,
  isInStock,
  mockupUrl,
  normalizeCanvas,
  readDraft,
  rescaleObjects,
  textItemsFrom,
  toHex,
  usedAssetIds,
  writeDraft,
} from './design-utils';

const fonts = [
  { code: 'ANTON', label: 'Anton', family: 'Anton' },
  { code: 'LOBSTER', label: 'Lobster', family: 'Lobster' },
];

const options = {
  availability: [
    { type: 'ROUND_NECK', color: 'BLACK', size: 'M', inStock: true },
    { type: 'ROUND_NECK', color: 'BLACK', size: 'L', inStock: false },
    { type: 'ROUND_NECK', color: 'RED', size: 'M', inStock: false },
  ],
} as unknown as DesignerOptions;

describe('design-utils', () => {
  it('builds mockup URLs and print area sizes', () => {
    expect(mockupUrl('v-neck', 'BACK')).toBe('/assets/mockups/v-neck-white-back.png');
    expect(areaSize({ code: 'FRONT', label: 'Front', side: 'FRONT', x: 0.32, y: 0.26, w: 0.36, h: 0.36, excludes: [] })).toEqual({
      width: 288,
      height: 360,
    });
  });

  it('checks stock per variant and per colour', () => {
    expect(isInStock(options, 'ROUND_NECK', 'BLACK', 'M')).toBe(true);
    expect(isInStock(options, 'ROUND_NECK', 'BLACK', 'L')).toBe(false);
    expect(colorAvailable(options, 'ROUND_NECK', 'BLACK')).toBe(true);
    expect(colorAvailable(options, 'ROUND_NECK', 'RED')).toBe(false);
  });

  it('keeps only images and text, makes image sources same-origin paths and drops clip paths', () => {
    const json = normalizeCanvas(
      {
        version: '7.4.0',
        objects: [
          { type: 'Image', src: 'http://localhost:4200/media/designs/a.png', clipPath: { type: 'Rect' } },
          { type: 'IText', text: 'Hi' },
          { type: 'Rect', width: 10 },
          { type: 'Image', src: 'https://evil.example/x.png' },
        ],
      },
      'http://localhost:4200',
    )!;
    expect(json.objects.map((o) => o['type'])).toEqual(['Image', 'IText', 'Image']);
    expect(json.objects[0]['src']).toBe('/media/designs/a.png');
    expect(json.objects[0]['clipPath']).toBeUndefined();
    expect(json.objects[2]['src']).toBe('https://evil.example/x.png'); // left for the server to reject
    expect(normalizeCanvas(null, 'http://x')).toBeNull();
    expect(hasObjects(json)).toBe(true);
    expect(hasObjects({ objects: [] })).toBe(false);
  });

  it('extracts text items with the effective size, a known font and a hex colour', () => {
    const items = textItemsFrom(
      'BACK',
      {
        objects: [
          { type: 'IText', text: 'Team 2026', fontFamily: 'Lobster', fontSize: 40, scaleY: 1.5, fill: 'rgb(255, 0, 0)' },
          { type: 'Textbox', text: 'x', fontFamily: 'Unknown', fontSize: 2, fill: '#abc' },
          { type: 'IText', text: '   ', fontFamily: 'Anton' },
          { type: 'Image', src: '/media/designs/a.png' },
        ],
      },
      fonts,
    );
    expect(items).toEqual([
      { side: 'BACK', text: 'Team 2026', font: 'Lobster', size: 60, color: '#FF0000' },
      { side: 'BACK', text: 'x', font: 'Anton', size: 8, color: '#AABBCC' },
    ]);
  });

  it('lists only the uploaded images used by the canvases', () => {
    const assets = [
      { id: 1, url: '/media/designs/a.png' },
      { id: 2, url: '/media/designs/b.png' },
    ] as never[];
    expect(usedAssetIds([{ objects: [{ type: 'Image', src: '/media/designs/b.png' }] }, null], assets)).toEqual([2]);
  });

  it('converts colours to #RRGGBB', () => {
    expect(toHex('#ffffff')).toBe('#FFFFFF');
    expect(toHex('rgba(0, 128, 255, 0.5)')).toBe('#0080FF');
    expect(toHex(null)).toBe('#000000');
  });

  it('rescales objects when the print area changes', () => {
    const out = rescaleObjects({ objects: [{ type: 'IText', left: 144, top: 180, scaleX: 1, scaleY: 1 }] }, { width: 288, height: 360 }, { width: 112, height: 140 });
    expect(out.objects[0]['left']).toBeCloseTo(56);
    expect(out.objects[0]['top']).toBeCloseTo(70);
    expect(out.objects[0]['scaleX']).toBeCloseTo(112 / 288);
  });

  it('detects dark garments and formats codes', () => {
    expect(isDark('#1D1D1F')).toBe(true);
    expect(isDark('#F5F4F0')).toBe(false);
    expect(codeLabel('SKY_BLUE')).toBe('Sky blue');
    expect(codeLabel('FULL_FRONT')).toBe('Full front');
  });

  it('stores and clears the local draft, ignoring broken data', () => {
    clearDraft();
    expect(readDraft()).toBeNull();
    writeDraft({
      code: null, name: 'Tee', tshirtType: 'POLO', colorCode: 'BLACK', sizeCode: 'M', placements: ['FRONT', 'BACK'],
      front: null, back: null, premiumPrint: false, assets: [], savedAt: 1,
    });
    expect(readDraft()?.tshirtType).toBe('POLO');
    localStorage.setItem(DRAFT_KEY, '{not json');
    expect(readDraft()).toBeNull();
    clearDraft();
  });
});
