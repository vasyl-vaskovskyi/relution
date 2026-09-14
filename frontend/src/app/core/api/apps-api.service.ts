import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppDetails, DetailsCriteria, SearchCriteria, SearchResponse } from './api.types';

const BASE_PATH = '/api/v1/apps';

/** Calls the two API endpoints on the same origin (nginx or the dev-server proxy forwards them). */
@Injectable({ providedIn: 'root' })
export class AppsApiService {
  private readonly http = inject(HttpClient);

  search(criteria: SearchCriteria): Observable<SearchResponse> {
    // Parameters go through HttpParams, never into the URL string, so the debug log can drop the term
    const params = new HttpParams({
      fromObject: { term: criteria.term, cc: criteria.cc, limit: criteria.limit },
    });
    return this.http.get<SearchResponse>(BASE_PATH, { params });
  }

  details(id: string, criteria: DetailsCriteria): Observable<AppDetails> {
    const params = new HttpParams({
      fromObject: { cc: criteria.cc, l: criteria.l, platform: criteria.platform },
    });
    return this.http.get<AppDetails>(`${BASE_PATH}/${encodeURIComponent(id)}`, { params });
  }
}
