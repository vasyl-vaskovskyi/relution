import { DecimalPipe, Location } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatIcon } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, combineLatest, distinctUntilChanged, map, startWith, switchMap, tap } from 'rxjs';
import { AppDetails, DetailsCriteria, Platform } from '../../core/api/api.types';
import { AppsApiService } from '../../core/api/apps-api.service';
import { LoadState, withLoadState } from '../../core/api/load-state';
import { DebugLogService } from '../../core/debug/debug-log.service';
import { detailsProblemMessage } from '../../core/errors/problem-message';
import { LocaleService, servedLanguageDiffers } from '../../core/locale/locale.service';
import { LoadingPanelComponent, ProblemPanelComponent } from '../../shared/state-panels.component';

interface DetailsRequest extends DetailsCriteria {
  id: string;
}

interface DetailsView extends DetailsRequest {
  state: LoadState<AppDetails>;
}

function sameRequest(a: DetailsRequest, b: DetailsRequest): boolean {
  return a.id === b.id && a.cc === b.cc && a.l === b.l && a.platform === b.platform;
}

/** Links from Apple are shown only when they are plain https URLs. */
export function safeLink(url: string | null | undefined): string | null {
  return url && url.startsWith('https://') ? url : null;
}

@Component({
  selector: 'app-app-details',
  imports: [
    DecimalPipe,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatIcon,
    LoadingPanelComponent,
    ProblemPanelComponent,
  ],
  templateUrl: './app-details.component.html',
  styles: `
    .bar {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
    }
    header {
      display: flex;
      gap: 16px;
      align-items: center;
    }
    header img {
      width: 96px;
      height: 96px;
      border-radius: 22%;
    }
    h1 {
      font: var(--mat-sys-headline-small);
      margin: 0;
    }
    .muted {
      color: var(--mat-sys-on-surface-variant);
      margin: 4px 0 0;
    }
    .chip {
      display: inline-block;
      margin-top: 8px;
      padding: 2px 12px;
      border-radius: 16px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      font: var(--mat-sys-label-large);
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      margin: 24px 0;
    }
    dt {
      color: var(--mat-sys-on-surface-variant);
    }
    dd {
      margin: 0;
    }
    .apple-text {
      white-space: pre-line;
    }
    .links {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class AppDetailsComponent {
  private readonly api = inject(AppsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly locale = inject(LocaleService);
  private readonly log = inject(DebugLogService);
  private readonly retry$ = new Subject<void>();

  protected readonly view = toSignal(
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(([params, query]): DetailsRequest => {
        const prefill = this.locale.prefill();
        return {
          id: params.get('id') ?? '',
          cc: query.get('cc') ?? prefill.cc,
          l: query.get('l') ?? prefill.l,
          platform: query.get('platform') === 'mac' ? 'mac' : 'ios',
        };
      }),
      distinctUntilChanged(sameRequest),
      tap((request) => this.log.log('details: open', { ...request })),
      switchMap((request) =>
        this.retry$.pipe(
          startWith(undefined),
          switchMap(() => withLoadState(this.api.details(request.id, request))),
          map((state): DetailsView => ({ ...request, state })),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly problem = detailsProblemMessage;
  protected readonly languageDiffers = servedLanguageDiffers;
  protected readonly safeLink = safeLink;

  protected setPlatform(platform: Platform): void {
    this.log.log('details: platform toggle', { platform });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { platform },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected retry(): void {
    this.retry$.next();
  }

  protected back(): void {
    this.location.back();
  }
}
