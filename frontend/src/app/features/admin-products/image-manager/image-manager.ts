import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { concatMap, from, toArray } from 'rxjs';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { Color, ProductImage } from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';
import { ImgFallback } from '../../../shared/img-fallback.directive';
import { confirmAction } from '../../../shared/confirm-dialog/confirm-dialog';

export const MAX_IMAGES = 12;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['image/png', 'image/jpeg'];

/** Quick client-side checks; the server re-validates every file by content. */
export function checkImageFile(file: File): string | null {
  if (!ALLOWED_TYPES.includes(file.type)) {
    return `${file.name}: only PNG and JPG images are allowed.`;
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return `${file.name}: images must be 5 MB or smaller.`;
  }
  return null;
}

@Component({
  selector: 'app-image-manager',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressBarModule, ImgFallback],
  templateUrl: './image-manager.html',
  styleUrl: './image-manager.scss',
})
export class ImageManager {
  private readonly api = inject(AdminCatalogApi);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly productId = input.required<number>();
  readonly productName = input.required<string>();
  readonly images = input.required<ProductImage[]>();
  readonly colors = input.required<Color[]>();
  /** Emitted after any change so the parent reloads the product. */
  readonly changed = output<void>();

  protected readonly uploading = signal(false);
  protected readonly dragOver = signal(false);
  protected readonly remaining = computed(() => MAX_IMAGES - this.images().length);
  protected readonly maxImages = MAX_IMAGES;

  protected colorId(code: string | undefined): number | null {
    return this.colors().find((c) => c.code === code)?.id ?? null;
  }

  protected onFiles(list: FileList | null): void {
    const files = Array.from(list ?? []);
    if (!files.length) {
      return;
    }
    if (files.length > this.remaining()) {
      this.notify.error(`You can add ${this.remaining()} more image(s) to this product.`);
      return;
    }
    const problems = files.map(checkImageFile).filter((p): p is string => p !== null);
    if (problems.length) {
      this.notify.error(problems.join(' '));
      return;
    }
    this.uploading.set(true);
    from(files)
      .pipe(
        concatMap((file) => this.api.uploadImage(this.productId(), file, null, this.productName())),
        toArray(),
      )
      .subscribe({
        next: (uploaded) => {
          this.uploading.set(false);
          this.notify.success(`${uploaded.length} image${uploaded.length === 1 ? '' : 's'} uploaded.`);
          this.changed.emit();
        },
        error: (err: unknown) => {
          this.uploading.set(false);
          this.notify.error(err, 'Upload failed.');
          this.changed.emit();
        },
      });
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.onFiles(event.dataTransfer?.files ?? null);
  }

  protected makePrimary(image: ProductImage): void {
    this.update(image, { colorId: this.colorId(image.colorCode), primary: true }, 'Main image updated.');
  }

  protected setColor(image: ProductImage, colorId: number | null): void {
    this.update(image, { colorId }, 'Colour updated.');
  }

  protected setAltText(image: ProductImage, altText: string): void {
    if (altText.trim() === (image.altText ?? '')) {
      return;
    }
    this.update(image, { colorId: this.colorId(image.colorCode), altText }, 'Description saved.');
  }

  protected move(image: ProductImage, delta: number): void {
    const ordered = [...this.images()];
    const index = ordered.findIndex((i) => i.id === image.id);
    const target = index + delta;
    if (target < 0 || target >= ordered.length) {
      return;
    }
    [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
    from(ordered.map((img, position) => ({ img, position })))
      .pipe(
        concatMap(({ img, position }) =>
          this.api.updateImage(this.productId(), img.id, { colorId: this.colorId(img.colorCode), sortOrder: position }),
        ),
        toArray(),
      )
      .subscribe({ next: () => this.changed.emit(), error: (err: unknown) => this.notify.error(err) });
  }

  protected remove(image: ProductImage): void {
    confirmAction(this.dialog, {
      title: 'Delete image?',
      message: 'The image will be removed from the product and deleted from storage.',
      confirmLabel: 'Delete',
      danger: true,
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deleteImage(this.productId(), image.id).subscribe({
        next: () => {
          this.notify.success('Image deleted.');
          this.changed.emit();
        },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  private update(image: ProductImage, body: Parameters<AdminCatalogApi['updateImage']>[2], message: string): void {
    this.api.updateImage(this.productId(), image.id, body).subscribe({
      next: () => {
        this.notify.success(message);
        this.changed.emit();
      },
      error: (err: unknown) => this.notify.error(err),
    });
  }
}
