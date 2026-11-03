import { CurrencyPipe, Location } from '@angular/common';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Canvas, FabricImage, FabricObject, IText, Point } from 'fabric';
import { catchError, debounceTime, distinctUntilChanged, firstValueFrom, of, switchMap, tap } from 'rxjs';

import { DesignApi } from '../../core/api/design.api';
import { apiErrorMessage } from '../../core/api/api-error';
import { PricingApi } from '../../core/api/pricing.api';
import { AuthService } from '../../core/auth/auth.service';
import {
  CanvasJson,
  Design,
  DesignAsset,
  DesignRequest,
  DesignSide,
  DesignerOptions,
  PrintPlacement,
} from '../../core/models/design.models';
import { ChargeType, PriceEstimateRequest, PriceQuote, PricingInfo } from '../../core/models/pricing.models';
import { NotifyService } from '../../core/notify.service';
import { CartService } from '../../core/cart/cart.service';
import { drawTintedShirt, loadImage, renderPreview } from './design-renderer';
import {
  DesignerDraft,
  MOCKUP_W,
  SIDES,
  areaSize,
  clearDraft,
  colorAvailable,
  hasObjects,
  isTextObject,
  isDark,
  isInStock,
  mockupUrl,
  normalizeCanvas,
  readDraft,
  rescaleObjects,
  textItemsFrom,
  usedAssetIds,
  writeDraft,
} from './design-utils';

interface Selected {
  kind: 'text' | 'image';
  text: string;
  font: string;
  color: string;
  size: number;
}

const DEFAULT_AREA: Record<DesignSide, string> = { FRONT: 'FRONT', BACK: 'BACK' };
const FONT_CSS = 'designer-fonts';

/**
 * Custom T-Shirt designer (Phase 9). Fabric.js edits one side at a time inside its print
 * area; the other side is kept as JSON. Saving validates on the server, which also stores
 * the PNG previews and keeps the original artwork for printing.
 */
@Component({
  selector: 'app-designer',
  imports: [RouterLink, CurrencyPipe, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './designer.html',
  styleUrl: './designer.scss',
})
export class Designer implements AfterViewInit {
  private readonly api = inject(DesignApi);
  private readonly pricingApi = inject(PricingApi);
  private readonly cartService = inject(CartService);
  protected readonly addingToBag = signal(false);
  protected readonly auth = inject(AuthService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly destroyRef = inject(DestroyRef);

  private readonly stageEl = viewChild.required<ElementRef<HTMLDivElement>>('stage');
  private readonly shirtEl = viewChild.required<ElementRef<HTMLCanvasElement>>('shirt');
  private readonly fabricEl = viewChild.required<ElementRef<HTMLCanvasElement>>('fabric');
  private readonly fileEl = viewChild.required<ElementRef<HTMLInputElement>>('file');

  protected readonly options = signal<DesignerOptions | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly ready = signal(false);

  protected readonly code = signal<string | null>(null);
  protected readonly locked = signal(false);
  protected readonly name = signal('');
  protected readonly type = signal('');
  protected readonly color = signal('');
  protected readonly size = signal('');
  protected readonly premium = signal(false);
  protected readonly side = signal<DesignSide>('FRONT');
  protected readonly areas = signal<Record<DesignSide, string>>({ ...DEFAULT_AREA });
  protected readonly assets = signal<DesignAsset[]>([]);
  protected readonly counts = signal<Record<DesignSide, number>>({ FRONT: 0, BACK: 0 });
  protected readonly selected = signal<Selected | null>(null);
  protected readonly uploading = signal(false);
  protected readonly saving = signal(false);
  protected readonly dirty = signal(false);
  protected readonly texts = signal<Record<DesignSide, number>>({ FRONT: 0, BACK: 0 });
  protected readonly quantity = signal(1);
  protected readonly pricing = signal<PricingInfo | null>(null);
  protected readonly quoting = signal(false);
  protected readonly priceError = signal<string | null>(null);

  protected readonly sides = SIDES;
  protected readonly typeInfo = computed(() => this.options()?.tshirtTypes.find((t) => t.code === this.type()) ?? null);
  protected readonly colorInfo = computed(() => this.options()?.colors.find((c) => c.code === this.color()) ?? null);
  protected readonly sideAreas = computed(() => (this.options()?.placements ?? []).filter((p) => p.side === this.side()));
  protected readonly area = computed(() => this.placementOf(this.side(), this.areas()));
  protected readonly inStock = computed(() => {
    const o = this.options();
    return !!o && isInStock(o, this.type(), this.color(), this.size());
  });
  protected readonly accept = computed(() => (this.options()?.limits.allowedTypes ?? ['image/png', 'image/jpeg']).join(','));
  protected readonly maxUploadMb = computed(() => Math.round((this.options()?.limits.maxUploadBytes ?? 5242880) / 1048576));

  protected readonly maxQuantity = computed(() => this.pricing()?.maxQuantityPerItem ?? 20);

  /** Everything that changes the price; the server calculates it (never the browser). */
  protected readonly priceRequest = computed<PriceEstimateRequest | null>(() => {
    if (!this.ready() || !this.type() || !this.color() || !this.size()) return null;
    const counts = this.counts();
    const areas = this.areas();
    const texts = this.texts();
    return {
      tshirtType: this.type(),
      colorCode: this.color(),
      sizeCode: this.size(),
      placements: SIDES.filter((s) => counts[s] > 0).map((s) => areas[s]),
      hasText: texts.FRONT + texts.BACK > 0,
      premiumPrint: this.premium(),
      quantity: this.quantity(),
    };
  });

  protected readonly quote = toSignal<PriceQuote | null>(
    toObservable(this.priceRequest).pipe(
      debounceTime(200),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      tap((request) => this.quoting.set(!!request)),
      switchMap((request) =>
        !request
          ? of(null)
          : this.pricingApi.estimate(request).pipe(
              tap(() => this.priceError.set(null)),
              catchError((error: unknown) => {
                this.priceError.set(apiErrorMessage(error, 'The price could not be calculated. Please try again.'));
                return of(null);
              }),
            ),
      ),
      tap(() => this.quoting.set(false)),
    ),
    { initialValue: null },
  );

  private canvas?: Canvas;
  private stored: Record<DesignSide, CanvasJson | null> = { FRONT: null, BACK: null };
  private draftTimer?: ReturnType<typeof setTimeout>;
  private loadingSide = false;

  async ngAfterViewInit(): Promise<void> {
    this.destroyRef.onDestroy(() => {
      clearTimeout(this.draftTimer);
      if (this.dirty()) this.saveDraft();
      void this.canvas?.dispose();
    });
    this.pricingApi.info().subscribe({ next: (info) => this.pricing.set(info), error: () => this.pricing.set(null) });
    try {
      const options = await firstValueFrom(this.api.options());
      this.options.set(options);
      await this.loadFonts(options);
      this.createCanvas();
      await this.restore(options);
      this.observeStage();
      this.ready.set(true);
    } catch (error) {
      const status = (error as { status?: number })?.status;
      this.loadError.set(
        status === 404
          ? 'This design was not found. It may have been deleted, or it belongs to another account.'
          : 'The designer could not be loaded. Check your connection and try again.',
      );
    }
  }

  // ---------------------------------------------------------------- setup

  private async loadFonts(options: DesignerOptions): Promise<void> {
    if (!document.getElementById(FONT_CSS)) {
      const link = document.createElement('link');
      link.id = FONT_CSS;
      link.rel = 'stylesheet';
      const families = options.fonts.map((f) => 'family=' + f.family.replace(/ /g, '+')).join('&');
      link.href = `https://fonts.googleapis.com/css2?${families}&display=swap`;
      document.head.appendChild(link);
    }
    // Text renders on a canvas, so wait for the font files (but never block the designer for long).
    await Promise.race([
      Promise.allSettled(options.fonts.map((f) => document.fonts.load(`40px "${f.family}"`))),
      new Promise((resolve) => setTimeout(resolve, 2500)),
    ]);
  }

  private createCanvas(): void {
    const canvas = new Canvas(this.fabricEl().nativeElement, {
      preserveObjectStacking: true,
      selection: false,
      enableRetinaScaling: true,
    });
    const sync = () => this.syncSelected();
    canvas.on('selection:created', sync);
    canvas.on('selection:updated', sync);
    canvas.on('selection:cleared', sync);
    canvas.on('object:modified', () => {
      this.syncSelected();
      this.changed();
    });
    canvas.on('text:changed', () => {
      this.syncSelected();
      this.changed();
    });
    canvas.on('object:moving', (e) => this.keepInside(e.target));
    this.canvas = canvas;
  }

  private observeStage(): void {
    const observer = new ResizeObserver(() => this.layout());
    observer.observe(this.stageEl().nativeElement);
    this.destroyRef.onDestroy(() => observer.disconnect());
    this.layout();
  }

  /** Initial state: saved design (/customize/:code), else the local draft, else defaults. */
  private async restore(options: DesignerOptions): Promise<void> {
    const code = this.route.snapshot.paramMap.get('code');
    if (code) {
      this.applyDesign(await firstValueFrom(this.api.get(code)));
    } else {
      const draft = readDraft();
      if (draft) {
        this.applyDraft(draft);
      } else {
        const wanted = options.tshirtTypes.find((t) => t.code === this.route.snapshot.queryParamMap.get('type'));
        this.type.set((wanted ?? options.tshirtTypes[0]).code);
        this.color.set(options.colors.find((c) => c.code === 'WHITE')?.code ?? options.colors[0].code);
        this.size.set(options.sizes.find((s) => s.code === 'M')?.code ?? options.sizes[0].code);
      }
    }
    this.fixVariant();
    await this.loadSide(this.side());
  }

  private applyDesign(design: Design): void {
    this.code.set(design.code);
    this.locked.set(design.locked);
    this.name.set(design.name ?? '');
    this.type.set(design.tshirtType);
    this.color.set(design.colorCode);
    this.size.set(design.sizeCode);
    this.premium.set(design.premiumPrint);
    this.assets.set(design.assets);
    this.stored = { FRONT: design.front, BACK: design.back };
    this.areas.set(this.areasFrom(design.placements));
    this.side.set(hasObjects(design.front) || !hasObjects(design.back) ? 'FRONT' : 'BACK');
  }

  private applyDraft(d: DesignerDraft): void {
    this.name.set(d.name ?? '');
    this.type.set(d.tshirtType);
    this.color.set(d.colorCode);
    this.size.set(d.sizeCode);
    this.premium.set(!!d.premiumPrint);
    this.assets.set(d.assets ?? []);
    this.stored = { FRONT: d.front, BACK: d.back };
    this.areas.set(this.areasFrom(d.placements ?? []));
    this.dirty.set(hasObjects(d.front) || hasObjects(d.back));
  }

  private areasFrom(codes: string[]): Record<DesignSide, string> {
    const areas = { ...DEFAULT_AREA };
    for (const code of codes) {
      const p = this.options()?.placements.find((x) => x.code === code);
      if (p) areas[p.side] = p.code;
    }
    return areas;
  }

  // ---------------------------------------------------------------- layout and drawing

  private layout(): void {
    const width = this.stageEl().nativeElement.clientWidth;
    const p = this.area();
    if (!width || !this.canvas || !p) return;
    const zoom = width / MOCKUP_W;
    const size = areaSize(p);
    this.canvas.setDimensions({ width: Math.round(size.width * zoom), height: Math.round(size.height * zoom) });
    this.canvas.setZoom(zoom);
    this.canvas.requestRenderAll();
    void this.drawShirt();
  }

  private async drawShirt(): Promise<void> {
    const t = this.typeInfo();
    const c = this.colorInfo();
    if (!t || !c) return;
    const el = this.shirtEl().nativeElement;
    const ratio = Math.min(2, window.devicePixelRatio || 1);
    const width = Math.round((el.clientWidth || MOCKUP_W) * ratio);
    const height = Math.round(width * 1.25);
    const img = await loadImage(mockupUrl(t.mockup, this.side()));
    el.width = width;
    el.height = height;
    drawTintedShirt(el.getContext('2d')!, img, c.hex ?? '#ffffff', width, height);
  }

  private placementOf(side: DesignSide, areas: Record<DesignSide, string>): PrintPlacement | null {
    return this.options()?.placements.find((p) => p.code === areas[side]) ?? null;
  }

  private snapshot(): CanvasJson | null {
    return this.canvas ? (this.canvas.toJSON() as CanvasJson) : null;
  }

  private async loadSide(side: DesignSide): Promise<void> {
    if (!this.canvas) return;
    this.loadingSide = true;
    try {
      this.canvas.discardActiveObject();
      const json = this.stored[side];
      if (json && hasObjects(json)) {
        await this.canvas.loadFromJSON(json);
      } else {
        this.canvas.clear();
      }
      this.applyLock();
      this.layout();
    } catch {
      this.canvas.clear();
      this.notify.error('Part of this design could not be loaded. Re-upload any missing images.');
    } finally {
      this.loadingSide = false;
      this.syncSelected();
    }
  }

  private applyLock(): void {
    const locked = this.locked();
    this.canvas?.getObjects().forEach((o) => o.set({ selectable: !locked, evented: !locked }));
    this.canvas?.requestRenderAll();
  }

  /** Keeps the centre of a dragged object inside the print area. */
  private keepInside(target: FabricObject | undefined): void {
    const p = this.area();
    if (!target || !p) return;
    const { width, height } = areaSize(p);
    const center = target.getCenterPoint();
    const x = Math.min(width, Math.max(0, center.x));
    const y = Math.min(height, Math.max(0, center.y));
    if (x !== center.x || y !== center.y) {
      target.setPositionByOrigin(new Point(x, y), 'center', 'center');
    }
  }

  // ---------------------------------------------------------------- choices

  protected async chooseType(code: string): Promise<void> {
    if (this.locked() || code === this.type()) return;
    this.type.set(code);
    this.fixVariant();
    this.changed();
    await this.drawShirt();
  }

  protected async chooseColor(code: string): Promise<void> {
    if (this.locked() || !this.colorEnabled(code)) return;
    this.color.set(code);
    this.fixVariant();
    this.changed();
    await this.drawShirt();
  }

  protected chooseSize(code: string): void {
    if (this.locked() || !this.sizeEnabled(code)) return;
    this.size.set(code);
    this.changed();
  }

  /** Extra charge for a choice (0 when there is none), for the "+ INR" hints. */
  protected charge(type: ChargeType, code: string): number {
    return this.pricing()?.charges.find((c) => c.type === type && c.code === code)?.amount ?? 0;
  }

  protected setQuantity(value: number): void {
    const q = Math.round(Number(value));
    this.quantity.set(Number.isFinite(q) ? Math.min(this.maxQuantity(), Math.max(1, q)) : 1);
  }

  protected colorEnabled(code: string): boolean {
    const o = this.options();
    return !!o && colorAvailable(o, this.type(), code);
  }

  protected sizeEnabled(code: string): boolean {
    const o = this.options();
    return !!o && isInStock(o, this.type(), this.color(), code);
  }

  /** After a style or colour change, move to an in-stock colour and size if needed. */
  private fixVariant(): void {
    const o = this.options();
    if (!o) return;
    if (!colorAvailable(o, this.type(), this.color())) {
      const color = o.colors.find((c) => colorAvailable(o, this.type(), c.code));
      if (color) this.color.set(color.code);
    }
    if (!isInStock(o, this.type(), this.color(), this.size())) {
      const size = o.sizes.find((s) => isInStock(o, this.type(), this.color(), s.code));
      if (size) this.size.set(size.code);
    }
  }

  protected async chooseSide(side: DesignSide): Promise<void> {
    if (side === this.side() || this.loadingSide) return;
    this.stored[this.side()] = this.snapshot();
    this.side.set(side);
    await this.loadSide(side);
  }

  protected async chooseArea(code: string): Promise<void> {
    if (this.locked() || this.loadingSide) return;
    const side = this.side();
    const from = this.area();
    const to = this.options()?.placements.find((p) => p.code === code);
    if (!from || !to || from.code === to.code) return;
    const json = this.snapshot();
    this.areas.update((a) => ({ ...a, [side]: code }));
    this.stored[side] = json && hasObjects(json) ? rescaleObjects(json, areaSize(from), areaSize(to)) : json;
    await this.loadSide(side);
    this.changed();
  }

  // ---------------------------------------------------------------- elements

  protected pickFile(): void {
    if (this.locked()) return;
    if (!this.auth.isLoggedIn()) {
      this.toLogin('Sign in to upload your artwork. Your design is kept on this device.');
      return;
    }
    this.fileEl().nativeElement.click();
  }

  protected async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const o = this.options();
    if (!file || !o || !this.canvas) return;
    if (!o.limits.allowedTypes.includes(file.type)) {
      this.notify.error('Please upload a PNG or JPG image.');
      return;
    }
    if (file.size > o.limits.maxUploadBytes) {
      this.notify.error(`The image is larger than ${this.maxUploadMb()} MB. Please upload a smaller file.`);
      return;
    }
    if (!this.roomForMore()) return;
    this.uploading.set(true);
    try {
      const asset = await firstValueFrom(this.api.upload(file));
      this.assets.update((list) => [...list, asset]);
      await this.addImage(asset);
    } catch (error) {
      this.notify.error(error, 'The image could not be uploaded.');
    } finally {
      this.uploading.set(false);
    }
  }

  protected async reuse(asset: DesignAsset): Promise<void> {
    if (this.locked() || !this.roomForMore()) return;
    try {
      await this.addImage(asset);
    } catch {
      this.notify.error('This image is no longer available. Please upload it again.');
    }
  }

  private async addImage(asset: DesignAsset): Promise<void> {
    const p = this.area();
    if (!this.canvas || !p) return;
    const img = await FabricImage.fromURL(asset.url);
    const { width, height } = areaSize(p);
    const scale = Math.min((width * 0.8) / (img.width || 1), (height * 0.8) / (img.height || 1));
    img.set({ originX: 'center', originY: 'center', left: width / 2, top: height / 2, scaleX: scale, scaleY: scale });
    this.canvas.add(img);
    this.canvas.setActiveObject(img);
    this.canvas.requestRenderAll();
    this.changed();
  }

  protected addText(): void {
    const p = this.area();
    const o = this.options();
    if (!this.canvas || !p || !o || this.locked() || !this.roomForMore()) return;
    const { width, height } = areaSize(p);
    const text = new IText('Your text', {
      originX: 'center',
      originY: 'center',
      left: width / 2,
      top: height / 2,
      fontFamily: o.fonts[0]?.family ?? 'Montserrat',
      fontSize: Math.max(16, Math.round(width / 8)),
      fill: isDark(this.colorInfo()?.hex) ? '#FFFFFF' : '#111111',
      textAlign: 'center',
    });
    this.canvas.add(text);
    this.canvas.setActiveObject(text);
    this.canvas.requestRenderAll();
    this.changed();
  }

  private roomForMore(): boolean {
    const max = this.options()?.limits.maxObjectsPerSide ?? 50;
    if ((this.canvas?.getObjects().length ?? 0) >= max) {
      this.notify.error(`A side can hold up to ${max} elements.`);
      return false;
    }
    return true;
  }

  protected updateText(patch: Partial<Selected>): void {
    const obj = this.canvas?.getActiveObject();
    if (!(obj instanceof IText) || this.locked()) return;
    const max = this.options()?.limits.maxTextLength ?? 100;
    if (patch.text !== undefined) obj.set({ text: patch.text.slice(0, max) });
    if (patch.font) obj.set({ fontFamily: patch.font });
    if (patch.color && /^#[0-9a-f]{6}$/i.test(patch.color)) obj.set({ fill: patch.color.toUpperCase() });
    if (patch.size) obj.set({ fontSize: Math.min(300, Math.max(8, patch.size)), scaleX: 1, scaleY: 1 });
    obj.setCoords();
    this.canvas!.requestRenderAll();
    this.syncSelected();
    this.changed();
  }

  protected removeSelected(): void {
    const obj = this.canvas?.getActiveObject();
    if (!obj || this.locked()) return;
    this.canvas!.remove(obj);
    this.canvas!.discardActiveObject();
    this.canvas!.requestRenderAll();
    this.syncSelected();
    this.changed();
  }

  protected async duplicateSelected(): Promise<void> {
    const obj = this.canvas?.getActiveObject();
    if (!obj || this.locked() || !this.roomForMore()) return;
    const copy = await obj.clone();
    copy.set({ left: (obj.left ?? 0) + 16, top: (obj.top ?? 0) + 16 });
    this.canvas!.add(copy);
    this.canvas!.setActiveObject(copy);
    this.canvas!.requestRenderAll();
    this.changed();
  }

  protected layer(direction: 'up' | 'down'): void {
    const obj = this.canvas?.getActiveObject();
    if (!obj || this.locked()) return;
    if (direction === 'up') this.canvas!.bringObjectForward(obj);
    else this.canvas!.sendObjectBackwards(obj);
    this.canvas!.requestRenderAll();
    this.changed();
  }

  protected centerSelected(): void {
    const obj = this.canvas?.getActiveObject();
    const p = this.area();
    if (!obj || !p || this.locked()) return;
    const { width, height } = areaSize(p);
    obj.setPositionByOrigin(new Point(width / 2, height / 2), 'center', 'center');
    obj.setCoords();
    this.canvas!.requestRenderAll();
    this.changed();
  }

  /** Keep the latest change when the tab is reloaded or closed before the delayed draft save. */
  @HostListener('window:pagehide')
  protected onPageHide(): void {
    if (this.dirty()) {
      clearTimeout(this.draftTimer);
      this.saveDraft();
    }
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (event.key !== 'Delete' && event.key !== 'Backspace') return;
    const target = event.target as HTMLElement | null;
    if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return;
    const obj = this.canvas?.getActiveObject();
    if (obj && !(obj instanceof IText && obj.isEditing)) {
      event.preventDefault();
      this.removeSelected();
    }
  }

  private syncSelected(): void {
    const obj = this.canvas?.getActiveObject();
    if (!obj) {
      this.selected.set(null);
    } else if (obj instanceof IText) {
      this.selected.set({
        kind: 'text',
        text: obj.text ?? '',
        font: obj.fontFamily ?? '',
        color: typeof obj.fill === 'string' ? obj.fill.toUpperCase() : '#111111',
        size: Math.round((obj.fontSize ?? 40) * (obj.scaleY ?? 1)),
      });
    } else {
      this.selected.set({ kind: 'image', text: '', font: '', color: '', size: 0 });
    }
    this.updateCounts();
  }

  private updateCounts(): void {
    const side = this.side();
    const live = this.canvas?.getObjects().length ?? 0;
    const other: DesignSide = side === 'FRONT' ? 'BACK' : 'FRONT';
    const counts = { FRONT: 0, BACK: 0 };
    counts[side] = live;
    counts[other] = this.stored[other]?.objects?.length ?? 0;
    this.counts.set(counts);
    const texts = { FRONT: 0, BACK: 0 };
    texts[side] = this.canvas?.getObjects().filter((o) => o instanceof IText && (o.text ?? '').trim()).length ?? 0;
    texts[other] = (this.stored[other]?.objects ?? []).filter(isTextObject).length;
    this.texts.set(texts);
  }

  private changed(): void {
    if (this.loadingSide) return;
    this.dirty.set(true);
    this.updateCounts();
    clearTimeout(this.draftTimer);
    this.draftTimer = setTimeout(() => this.saveDraft(), 800);
  }

  // ---------------------------------------------------------------- draft and save

  private currentCanvases(): Record<DesignSide, CanvasJson | null> {
    const origin = window.location.origin;
    const live = this.snapshot();
    const front = this.side() === 'FRONT' ? live : this.stored.FRONT;
    const back = this.side() === 'BACK' ? live : this.stored.BACK;
    return { FRONT: normalizeCanvas(front, origin), BACK: normalizeCanvas(back, origin) };
  }

  /** Unsaved new designs are kept on this device (also across sign-in). */
  private saveDraft(): void {
    if (this.code() || !this.options()) return;
    const canvases = this.currentCanvases();
    writeDraft({
      code: null,
      name: this.name(),
      tshirtType: this.type(),
      colorCode: this.color(),
      sizeCode: this.size(),
      placements: SIDES.map((s) => this.areas()[s]),
      front: canvases.FRONT,
      back: canvases.BACK,
      premiumPrint: this.premium(),
      assets: this.assets(),
      savedAt: Date.now(),
    });
  }

  protected nameChanged(value: string): void {
    this.name.set(value.slice(0, 100));
    this.changed();
  }

  protected premiumChanged(value: boolean): void {
    this.premium.set(value);
    this.changed();
  }

  protected async startOver(): Promise<void> {
    clearDraft();
    this.dirty.set(false);
    if (this.code()) {
      await this.router.navigateByUrl('/customize');
      return;
    }
    this.stored = { FRONT: null, BACK: null };
    this.name.set('');
    this.areas.set({ ...DEFAULT_AREA });
    await this.loadSide(this.side());
    this.dirty.set(false);
  }

  private toLogin(message: string): void {
    this.saveDraft();
    this.notify.success(message);
    void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }

  /** Saves the design if needed, then adds it to the bag in the chosen size and quantity. */
  protected async addToBag(): Promise<void> {
    if (!this.auth.isLoggedIn()) {
      this.toLogin('Sign in to add your design to the bag. It is kept on this device until then.');
      return;
    }
    if (this.addingToBag() || this.saving()) return;
    this.addingToBag.set(true);
    try {
      if (!this.code() || this.dirty() || this.locked()) {
        await this.save();
        if (!this.code() || this.dirty()) return; // save failed and showed why
      }
      await this.cartService.addDesign(this.code()!, this.size(), this.quantity());
      this.notify.success('Added to your bag.');
      void this.router.navigateByUrl('/cart');
    } catch (error) {
      this.notify.error(error, 'Your design could not be added to the bag.');
    } finally {
      this.addingToBag.set(false);
    }
  }

  /** Save (or, for a design that is part of an order, save a copy). */
  protected async save(): Promise<void> {
    const o = this.options();
    if (!o || this.saving()) return;
    if (!this.auth.isLoggedIn()) {
      this.toLogin('Sign in to save your design. It is kept on this device until then.');
      return;
    }
    const canvases = this.currentCanvases();
    const used = SIDES.filter((s) => hasObjects(canvases[s]));
    if (used.length === 0) {
      this.notify.error('Add an image or some text before saving.');
      return;
    }
    if (!this.inStock()) {
      this.notify.error('This colour and size is out of stock. Please choose another.');
      return;
    }
    this.saving.set(true);
    try {
      const hex = this.colorInfo()?.hex ?? '#ffffff';
      const mockup = this.typeInfo()!.mockup;
      const areas = this.areas();
      const previewFront = await renderPreview(mockupUrl(mockup, 'FRONT'), hex, this.placementOf('FRONT', areas), canvases.FRONT);
      const previewBack = hasObjects(canvases.BACK)
        ? await renderPreview(mockupUrl(mockup, 'BACK'), hex, this.placementOf('BACK', areas), canvases.BACK)
        : null;
      const body: DesignRequest = {
        name: this.name().trim() || null,
        tshirtType: this.type(),
        colorCode: this.color(),
        sizeCode: this.size(),
        placements: used.map((s) => areas[s]),
        front: hasObjects(canvases.FRONT) ? canvases.FRONT : null,
        back: hasObjects(canvases.BACK) ? canvases.BACK : null,
        textItems: [...textItemsFrom('FRONT', canvases.FRONT, o.fonts), ...textItemsFrom('BACK', canvases.BACK, o.fonts)],
        assetIds: usedAssetIds([canvases.FRONT, canvases.BACK], this.assets()),
        premiumPrint: this.premium(),
        previewFront,
        previewBack,
      };
      const existing = this.code();
      const asCopy = this.locked();
      const saved = await firstValueFrom(existing && !asCopy ? this.api.update(existing, body) : this.api.create(body));
      clearDraft();
      this.dirty.set(false);
      this.code.set(saved.code);
      this.locked.set(saved.locked);
      this.assets.set(saved.assets);
      this.applyLock();
      this.location.replaceState(`/customize/${saved.code}`);
      this.notify.success(asCopy ? `Saved as a new design, ${saved.code}.` : existing ? 'Design updated.' : `Design saved as ${saved.code}.`);
    } catch (error) {
      this.notify.error(error, 'The design could not be saved.');
    } finally {
      this.saving.set(false);
    }
  }
}
