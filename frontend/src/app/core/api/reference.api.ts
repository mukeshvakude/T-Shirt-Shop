import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { Category, Color, Size } from '../models/catalog.models';

/** Public reference data (sizes, colours, categories), cached for the session. */
@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);

  private readonly sizes$ = this.http.get<Size[]>('/api/reference/sizes').pipe(shareReplay(1));
  private readonly colors$ = this.http.get<Color[]>('/api/reference/colors').pipe(shareReplay(1));

  sizes(): Observable<Size[]> {
    return this.sizes$;
  }

  colors(): Observable<Color[]> {
    return this.colors$;
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }
}
