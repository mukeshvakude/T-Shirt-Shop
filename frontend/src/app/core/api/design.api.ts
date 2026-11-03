import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from '../models/catalog.models';
import { Design, DesignAsset, DesignRequest, DesignSummary, DesignerOptions } from '../models/design.models';

/** Custom T-Shirt designer: options, artwork uploads and saved designs. */
@Injectable({ providedIn: 'root' })
export class DesignApi {
  private readonly http = inject(HttpClient);

  options(): Observable<DesignerOptions> {
    return this.http.get<DesignerOptions>('/api/designer/options');
  }

  upload(file: File): Observable<DesignAsset> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<DesignAsset>('/api/designs/assets', body);
  }

  mine(): Observable<DesignSummary[]> {
    return this.http.get<DesignSummary[]>('/api/designs');
  }

  get(code: string): Observable<Design> {
    return this.http.get<Design>(`/api/designs/${encodeURIComponent(code)}`);
  }

  create(body: DesignRequest): Observable<Design> {
    return this.http.post<Design>('/api/designs', body);
  }

  update(code: string, body: DesignRequest): Observable<Design> {
    return this.http.put<Design>(`/api/designs/${encodeURIComponent(code)}`, body);
  }

  delete(code: string): Observable<void> {
    return this.http.delete<void>(`/api/designs/${encodeURIComponent(code)}`);
  }

  // ---------------------------------------------------------------- admin

  adminList(page = 0, size = 24): Observable<Page<DesignSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<DesignSummary>>('/api/admin/designs', { params });
  }

  adminGet(code: string): Observable<Design> {
    return this.http.get<Design>(`/api/admin/designs/${encodeURIComponent(code)}`);
  }

  /** ZIP with design.json, previews and the original artwork. */
  adminDownload(code: string): Observable<Blob> {
    return this.http.get(`/api/admin/designs/${encodeURIComponent(code)}/download`, { responseType: 'blob' });
  }
}
