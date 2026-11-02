import { Directive, ElementRef, HostListener, inject } from '@angular/core';

/** Neutral garment silhouette shown when a product image is missing or fails to load. */
export const PLACEHOLDER_IMAGE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 100">' +
      '<rect width="80" height="100" fill="#f1f1f1"/>' +
      '<path d="M28 22l-14 8 5 12 7-3v37h28V39l7 3 5-12-14-8c-2 5-6 8-12 8s-10-3-12-8z" fill="#d6d6d6"/>' +
      '</svg>',
  );

/** Usage: <img [src]="url" appImgFallback> */
@Directive({ selector: 'img[appImgFallback]' })
export class ImgFallback {
  private readonly img = inject<ElementRef<HTMLImageElement>>(ElementRef).nativeElement;

  @HostListener('error')
  onError(): void {
    if (this.img.src !== PLACEHOLDER_IMAGE) {
      this.img.src = PLACEHOLDER_IMAGE;
    }
  }
}
