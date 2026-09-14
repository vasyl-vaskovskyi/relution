import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AppSummary, SearchResponse } from '../../core/api/api.types';
import { SLOW_HINT_MS } from '../../core/api/load-state';
import { DEBUG_LOG_CONFIG } from '../../core/debug/debug-log.service';
import { provideAppIcons } from '../../core/icons/app-icons';
import { SEARCH_DEBOUNCE_MS, SearchComponent } from './search.component';

@Component({ template: 'details page' })
class DetailsStubComponent {}

const SEARCH_PATH = '/api/v1/apps';

function app(id: string, name: string, developer: string | null = 'Apple'): AppSummary {
  return {
    id,
    name,
    developer,
    iconUrl: `https://example.test/${id}.png`,
    kind: 'SOFTWARE',
    price: null,
    rating: null,
  };
}

function response(items: AppSummary[], cc = 'de'): SearchResponse {
  return { items, count: items.length, storefront: { cc } };
}

describe('SearchComponent', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAppIcons(),
        provideRouter([
          { path: 'search', component: SearchComponent },
          { path: 'apps/:id', component: DetailsStubComponent },
        ]),
        {
          provide: DEBUG_LOG_CONFIG,
          useValue: { debugLogging: false, debugLoggingOverride: false },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Real timers first, so a failed verify doesn't leak fake timers into the next test
    vi.useRealTimers();
    backend.verify();
  });

  /** Lets timers, change detection and router navigations run. */
  async function advance(ms = 0) {
    await vi.advanceTimersByTimeAsync(ms);
    harness.fixture.detectChanges();
  }

  async function open(url = '/search') {
    const created = RouterTestingHarness.create(url);
    await vi.advanceTimersByTimeAsync(0);
    harness = await created;
  }

  function element(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function input(name: 'term' | 'cc' | 'l'): HTMLInputElement {
    return element().querySelector(`input[formcontrolname="${name}"]`) as HTMLInputElement;
  }

  function type(name: 'term' | 'cc' | 'l', value: string) {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
  }

  /** Types a term and waits for the debounce, which is when the request goes out. */
  async function search(term: string): Promise<TestRequest> {
    await open();
    type('term', term);
    await advance(SEARCH_DEBOUNCE_MS);
    return backend.expectOne((request) => request.url === SEARCH_PATH);
  }

  function alert(): HTMLElement | null {
    return element().querySelector('[role="alert"]');
  }

  it('pre-fills country and language from the browser locale and sends nothing without a term', async () => {
    await open();
    await advance(SEARCH_DEBOUNCE_MS);

    expect(input('cc').value).toBe('de');
    expect(input('l').value).toBe('de');
    backend.expectNone((request) => request.url === SEARCH_PATH);
  });

  it('sends nothing for a term shorter than two characters', async () => {
    await open();

    type('term', ' p ');
    await advance(SEARCH_DEBOUNCE_MS);

    backend.expectNone((request) => request.url === SEARCH_PATH);
    expect(element().querySelector('mat-progress-bar')).toBeNull();
  });

  it('debounces typing into one request with the trimmed term, country and limit', async () => {
    await open();

    type('term', 'pa');
    await advance(SEARCH_DEBOUNCE_MS - 100);
    type('term', ' pages ');
    await advance(SEARCH_DEBOUNCE_MS - 100);
    backend.expectNone((request) => request.url === SEARCH_PATH);
    await advance(100);

    const request = backend.expectOne((r) => r.url === SEARCH_PATH);
    expect(request.request.params.get('term')).toBe('pages');
    expect(request.request.params.get('cc')).toBe('de');
    expect(request.request.params.get('limit')).toBe('25');
    request.flush(response([]));
  });

  it('keeps the search in the URL', async () => {
    const request = await search('pages');
    request.flush(response([]));
    await advance();

    expect(TestBed.inject(Router).url).toBe('/search?term=pages&cc=de&l=de');
  });

  it('restores the search from the URL and lower-cases the country', async () => {
    await open('/search?term=numbers&cc=AT&l=de-AT');
    await advance(SEARCH_DEBOUNCE_MS);

    expect(input('term').value).toBe('numbers');
    const request = backend.expectOne((r) => r.url === SEARCH_PATH);
    expect(request.request.params.get('term')).toBe('numbers');
    expect(request.request.params.get('cc')).toBe('at');
    request.flush(response([], 'at'));
  });

  it('shows a loading state, then the slow hint after 3 s', async () => {
    const request = await search('pages');

    expect(element().querySelector('mat-progress-bar[aria-label="Loading"]')).not.toBeNull();
    expect(element().textContent).not.toContain('Still loading');

    await advance(SLOW_HINT_MS);
    expect(element().textContent).toContain('Still loading, the App Store is slow');

    request.flush(response([]));
    await advance();
    expect(element().querySelector('mat-progress-bar')).toBeNull();
  });

  it('shows "No results found" for an empty result', async () => {
    const request = await search('zzzz');
    request.flush(response([]));
    await advance();

    expect(element().querySelector('.empty')?.textContent).toBe('No results found for “zzzz”.');
    expect(element().querySelector('mat-nav-list')).toBeNull();
  });

  it('lists the results with name, developer, icon and a link to the details', async () => {
    const request = await search('pages');
    request.flush(response([app('361309726', 'Pages'), app('409203825', 'Numbers', null)]));
    await advance();

    const items = [...element().querySelectorAll('a[mat-list-item]')] as HTMLAnchorElement[];
    expect(items.map((item) => item.querySelector('[matListItemTitle]')?.textContent)).toEqual([
      'Pages',
      'Numbers',
    ]);
    expect(items[0].querySelector('[matListItemLine]')?.textContent).toBe('Apple');
    expect(items[0].querySelector('img')?.getAttribute('src')).toBe(
      'https://example.test/361309726.png',
    );
    expect(items[0].getAttribute('href')).toBe('/apps/361309726?cc=de&l=de');
    expect(element().querySelector('.empty')).toBeNull();
  });

  it('cancels an outdated request when the term changes', async () => {
    const first = await search('pages');

    type('term', 'numbers');
    await advance(SEARCH_DEBOUNCE_MS);

    expect(first.cancelled).toBe(true);
    const second = backend.expectOne((r) => r.url === SEARCH_PATH);
    expect(second.request.params.get('term')).toBe('numbers');
    second.flush(response([app('409203825', 'Numbers')]));
  });

  it('shows a field error and sends nothing for an invalid country', async () => {
    await open();

    type('cc', 'd1');
    input('cc').dispatchEvent(new Event('blur'));
    type('term', 'pages');
    await advance(SEARCH_DEBOUNCE_MS);

    expect(element().querySelector('mat-error')?.textContent).toContain('Two letters, e.g. de');
    backend.expectNone((request) => request.url === SEARCH_PATH);
  });

  it('shows a field error for an invalid language', async () => {
    await open();

    type('l', 'deutsch');
    input('l').dispatchEvent(new Event('blur'));
    await advance(SEARCH_DEBOUNCE_MS);

    expect(element().querySelector('mat-error')?.textContent).toContain('e.g. de or de-DE');
  });

  it.each<{
    name: string;
    status: number;
    body: object;
    headers: Record<string, string>;
    message: string;
  }>([
    {
      name: 'unsupported storefront',
      status: 400,
      body: { type: 'urn:appstore:problem:unsupported-storefront' },
      headers: {},
      message: 'The App Store is not available in this country.',
    },
    {
      name: 'rate limit with Retry-After',
      status: 503,
      body: { type: 'urn:appstore:problem:upstream-unavailable' },
      headers: { 'Retry-After': '30' },
      message: 'Too many requests, try again in 30 s.',
    },
    {
      name: 'upstream timeout',
      status: 504,
      body: { type: 'urn:appstore:problem:upstream-timeout' },
      headers: {},
      message: 'The App Store is currently unavailable.',
    },
    {
      name: 'upstream error',
      status: 502,
      body: { type: 'urn:appstore:problem:upstream-error' },
      headers: {},
      message: 'The App Store is currently unavailable.',
    },
    {
      name: 'forbidden',
      status: 403,
      body: { type: 'urn:appstore:problem:forbidden' },
      headers: {},
      message: "You don't have access to this function.",
    },
  ])('maps the $name problem to its message', async ({ status, body, headers, message }) => {
    const request = await search('pages');
    request.flush(body, { status, statusText: 'Error', headers });
    await advance();

    expect(alert()?.querySelector('.message')?.textContent).toBe(message);
    expect(element().querySelector('mat-nav-list')).toBeNull();
  });

  it('shows the field details and the reference of an invalid request', async () => {
    const request = await search('pages');
    request.flush(
      {
        type: 'urn:appstore:problem:invalid-request',
        correlationId: 'corr-1',
        errors: [{ field: 'limit', message: 'must be at most 50' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await advance();

    expect(alert()?.querySelector('.message')?.textContent).toBe('Please check your input.');
    expect(alert()?.querySelector('li')?.textContent).toBe('limit: must be at most 50');
    expect(alert()?.textContent).toContain('Reference: corr-1');
  });

  it('shows a network message and retries the same search on Retry', async () => {
    const request = await search('pages');
    request.error(new ProgressEvent('error'), { status: 0 });
    await advance();
    expect(alert()?.querySelector('.message')?.textContent).toBe('Cannot reach the server.');

    const retry = [...element().querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Retry'),
    );
    retry?.click();
    await advance();

    const retried = backend.expectOne((r) => r.url === SEARCH_PATH);
    expect(retried.request.params.get('term')).toBe('pages');
    retried.flush(response([app('361309726', 'Pages')]));
    await advance();

    expect(alert()).toBeNull();
    expect(element().querySelectorAll('a[mat-list-item]').length).toBe(1);
  });
});
