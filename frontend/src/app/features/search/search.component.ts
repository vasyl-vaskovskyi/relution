import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel, MatPrefix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import {
  MatListItem,
  MatListItemAvatar,
  MatListItemLine,
  MatListItemTitle,
  MatNavList,
} from '@angular/material/list';
import { MatOption, MatSelect } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { SearchCriteria, SearchResponse } from '../../core/api/api.types';
import { AppsApiService } from '../../core/api/apps-api.service';
import { platformForKind } from '../../core/api/platform';
import { LoadState, withLoadState } from '../../core/api/load-state';
import { problemMessage } from '../../core/errors/problem-message';
import { LocaleService } from '../../core/locale/locale.service';
import { LoadingPanelComponent, ProblemPanelComponent } from '../../shared/state-panels.component';

export const SEARCH_DEBOUNCE_MS = 400;
export const MIN_TERM_LENGTH = 2;
const MAX_TERM_LENGTH = 100;
const DEFAULT_LIMIT = 25;
const LIMITS = [10, 25, 50];
const COUNTRY = /^[a-zA-Z]{2}$/;
const LANGUAGE = /^[a-zA-Z]{2}([-_][a-zA-Z]{2})?$/;

interface SearchView {
  criteria: SearchCriteria;
  state: LoadState<SearchResponse>;
}

function sameCriteria(a: SearchCriteria | null, b: SearchCriteria | null): boolean {
  return a?.term === b?.term && a?.cc === b?.cc && a?.limit === b?.limit;
}

@Component({
  selector: 'app-search',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatError,
    MatFormField,
    MatHint,
    MatIcon,
    MatInput,
    MatLabel,
    MatListItem,
    MatListItemAvatar,
    MatListItemLine,
    MatListItemTitle,
    MatNavList,
    MatOption,
    MatPrefix,
    MatSelect,
    LoadingPanelComponent,
    ProblemPanelComponent,
  ],
  templateUrl: './search.component.html',
  styles: `
    form {
      display: flex;
      flex-wrap: wrap;
      gap: 0 12px;
    }
    .term {
      flex: 1 1 280px;
    }
    .short {
      width: 110px;
    }
    .empty {
      margin: 16px 0;
    }
  `,
})
export class SearchComponent {
  private readonly api = inject(AppsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly retry$ = new Subject<void>();

  protected readonly limits = LIMITS;
  protected readonly form = this.createForm();

  /** Debounced, cancels outdated requests (switchMap) and protects Apple's rate budget. */
  protected readonly view = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      debounceTime(SEARCH_DEBOUNCE_MS),
      tap(() => this.syncQueryParams()),
      map(() => this.validCriteria()),
      distinctUntilChanged(sameCriteria),
      switchMap((criteria) =>
        criteria === null
          ? of(null)
          : this.retry$.pipe(
              startWith(undefined),
              switchMap(() => withLoadState(this.api.search(criteria))),
              map((state): SearchView => ({ criteria, state })),
            ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly problem = problemMessage;
  protected readonly platformFor = platformForKind;

  protected retry(): void {
    this.retry$.next();
  }

  private createForm() {
    const query = this.route.snapshot.queryParamMap;
    const prefill = inject(LocaleService).prefill();
    return inject(NonNullableFormBuilder).group({
      term: [query.get('term') ?? '', Validators.maxLength(MAX_TERM_LENGTH)],
      cc: [query.get('cc') ?? prefill.cc, [Validators.required, Validators.pattern(COUNTRY)]],
      l: [query.get('l') ?? prefill.l, [Validators.required, Validators.pattern(LANGUAGE)]],
      limit: [DEFAULT_LIMIT],
    });
  }

  private validCriteria(): SearchCriteria | null {
    const { term, cc, limit } = this.form.getRawValue();
    const trimmed = term.trim();
    if (trimmed.length < MIN_TERM_LENGTH || this.form.invalid) {
      return null;
    }
    return { term: trimmed, cc: cc.toLowerCase(), limit };
  }

  /** Keeps term, cc and l in the URL, so a search can be reloaded or shared. */
  private syncQueryParams(): void {
    const { term, cc, l } = this.form.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { term: term.trim() || null, cc: cc || null, l: l || null },
      replaceUrl: true,
    });
  }
}
