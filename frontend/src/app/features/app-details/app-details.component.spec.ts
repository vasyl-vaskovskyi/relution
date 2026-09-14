import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AppDetails } from '../../core/api/api.types';
import { SLOW_HINT_MS } from '../../core/api/load-state';
import { DEBUG_LOG_CONFIG } from '../../core/debug/debug-log.service';
import { provideAppIcons } from '../../core/icons/app-icons';
import { BROWSER_LANGUAGES } from '../../core/locale/locale.service';
import { AppDetailsComponent } from './app-details.component';

const PAGES_PATH = '/api/v1/apps/361309726';

function details(overrides: Partial<AppDetails> = {}): AppDetails {
  return {
    id: '361309726',
    name: 'Pages',
    kind: 'SOFTWARE',
    subtitle: 'Documents that stand apart',
    developer: 'Apple',
    seller: 'Apple Distribution International Ltd.',
    bundleId: 'com.apple.Pages',
    watchBundleId: null,
    version: '14.4',
    minimumOsVersion: '17.0',
    firstReleaseDate: '2010-04-01',
    price: { amount: '0.00', currency: 'EUR', formatted: 'Free' },
    platforms: ['ios', 'mac'],
    universal: true,
    description: 'Line one\nLine two',
    whatsNew: 'Bug fixes',
    iconUrl: 'https://example.test/pages.png',
    genres: ['Productivity'],
    rating: { average: 4.25, count: 1234 },
    links: {
      store: 'https://apps.apple.com/de/app/pages/id361309726',
      support: 'javascript:alert(1)',
      privacyPolicy: 'https://www.apple.com/legal/privacy/',
    },
    storefront: { cc: 'de', language: 'de-de', platform: 'ios' },
    ...overrides,
  };
}

describe('AppDetailsComponent', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAppIcons(),
        provideRouter([{ path: 'apps/:id', component: AppDetailsComponent }]),
        { provide: BROWSER_LANGUAGES, useValue: ['en-US'] },
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

  async function open(url: string): Promise<TestRequest> {
    const created = RouterTestingHarness.create(url);
    await vi.advanceTimersByTimeAsync(0);
    harness = await created;
    harness.fixture.detectChanges();
    return backend.expectOne((request) => request.url === PAGES_PATH);
  }

  function element(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  /** The definition list as label → value. */
  function facts(): Record<string, string> {
    const result: Record<string, string> = {};
    for (const term of element().querySelectorAll('dt')) {
      result[term.textContent?.trim() ?? ''] =
        term.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    }
    return result;
  }

  function button(label: string): HTMLButtonElement | undefined {
    return [...element().querySelectorAll('button')].find((candidate) =>
      candidate.textContent?.includes(label),
    );
  }

  function alertMessage(): string | null | undefined {
    return element().querySelector('[role="alert"] .message')?.textContent;
  }

  it('requests the details with the storefront from the URL', async () => {
    const request = await open('/apps/361309726?cc=de&l=de&platform=mac');

    expect(request.request.params.get('cc')).toBe('de');
    expect(request.request.params.get('l')).toBe('de');
    expect(request.request.params.get('platform')).toBe('mac');
    request.flush(details());
  });

  it('falls back to the browser locale and iOS when the URL has no parameters', async () => {
    const request = await open('/apps/361309726');

    expect(request.request.params.get('cc')).toBe('us');
    expect(request.request.params.get('l')).toBe('en');
    expect(request.request.params.get('platform')).toBe('ios');
    request.flush(details({ storefront: { cc: 'us', language: 'en-us', platform: 'ios' } }));
  });

  it('shows a loading state, then the slow hint after 3 s', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');

    expect(element().querySelector('mat-progress-bar[aria-label="Loading"]')).not.toBeNull();
    await advance(SLOW_HINT_MS);
    expect(element().textContent).toContain('Still loading, the App Store is slow');

    request.flush(details());
    await advance();
    expect(element().querySelector('mat-progress-bar')).toBeNull();
  });

  it('renders the app details', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details());
    await advance();

    expect(element().querySelector('h1')?.textContent).toBe('Pages');
    expect(element().querySelector('header img')?.getAttribute('src')).toBe(
      'https://example.test/pages.png',
    );
    expect(element().textContent).toContain('Documents that stand apart');
    expect(facts()).toEqual({
      'Bundle id': 'com.apple.Pages',
      Version: '14.4',
      Price: 'Free',
      Platforms: 'ios, mac',
      'Minimum OS': '17.0',
      'First release': '2010-04-01',
      Genres: 'Productivity',
      Rating: '4.3 (1234 ratings)',
      Storefront: 'de · de-de · ios',
    });
    const texts = [...element().querySelectorAll('.apple-text')].map((p) => p.textContent);
    expect(texts).toEqual(['Line one\nLine two', 'Bug fixes']);
  });

  it('shows placeholders for missing values and hides optional sections', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(
      details({
        subtitle: null,
        iconUrl: null,
        bundleId: null,
        version: null,
        price: null,
        platforms: [],
        minimumOsVersion: null,
        firstReleaseDate: null,
        genres: [],
        rating: null,
        description: null,
        whatsNew: null,
        links: null,
        storefront: { cc: 'de', language: null, platform: null },
      }),
    );
    await advance();

    expect(facts()).toEqual({
      'Bundle id': '–',
      Version: '–',
      Price: '–',
      Platforms: '–',
      'Minimum OS': '–',
      'First release': '–',
      Storefront: 'de · – · ios',
    });
    expect(element().querySelector('header img')).toBeNull();
    expect(element().querySelector('h2')).toBeNull();
    expect(element().querySelectorAll('.links a').length).toBe(0);
  });

  it('keeps Apple text as text, never as HTML', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details({ description: '<img src=x onerror="alert(1)">' }));
    await advance();

    expect(element().querySelector('.apple-text img')).toBeNull();
    expect(element().querySelector('.apple-text')?.textContent).toBe(
      '<img src=x onerror="alert(1)">',
    );
  });

  it('links only to https URLs, in a new tab without an opener', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details());
    await advance();

    const links = [...element().querySelectorAll('.links a')] as HTMLAnchorElement[];
    expect(links.map((link) => link.textContent?.trim())).toEqual(['App Store', 'Privacy policy']);
    for (const link of links) {
      expect(link.getAttribute('href')).toMatch(/^https:\/\//);
      expect(link.getAttribute('target')).toBe('_blank');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    }
  });

  it('notes when Apple served another language than requested', async () => {
    const request = await open('/apps/361309726?cc=de&l=fr');
    request.flush(details({ storefront: { cc: 'de', language: 'de-de', platform: 'ios' } }));
    await advance();

    expect(element().querySelector('.chip')?.textContent).toBe('Requested fr · served de-de');
  });

  it('shows no language note when the served language matches', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details({ storefront: { cc: 'de', language: 'de-de', platform: 'ios' } }));
    await advance();

    expect(element().querySelector('.chip')).toBeNull();
  });

  it('switches the platform through the URL and loads the Mac details', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details());
    await advance();
    const toggle = (label: string) =>
      [...element().querySelectorAll('mat-button-toggle')].find((candidate) =>
        candidate.textContent?.includes(label),
      );
    expect(toggle('iOS')?.classList).toContain('mat-button-toggle-checked');

    toggle('Mac')?.querySelector('button')?.click();
    await advance();

    expect(TestBed.inject(Router).url).toBe('/apps/361309726?cc=de&l=de&platform=mac');
    const mac = backend.expectOne((r) => r.url === PAGES_PATH);
    expect(mac.request.params.get('platform')).toBe('mac');
    expect(mac.request.params.get('cc')).toBe('de');
    mac.flush(details({ storefront: { cc: 'de', language: 'de-de', platform: 'mac' } }));
    await advance();

    expect(toggle('Mac')?.classList).toContain('mat-button-toggle-checked');
    expect(facts()['Storefront']).toBe('de · de-de · mac');
  });

  it('goes back in the browser history on Back', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(details());
    await advance();
    const back = vi.spyOn(TestBed.inject(Location), 'back');

    button('Back')?.click();

    expect(back).toHaveBeenCalledTimes(1);
  });

  it('shows the not-found message', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(
      { type: 'urn:appstore:problem:app-not-found', status: 404, correlationId: 'corr-404' },
      { status: 404, statusText: 'Not Found' },
    );
    await advance();

    expect(alertMessage()).toBe('App not found in this storefront.');
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('Reference: corr-404');
    expect(element().querySelector('article')).toBeNull();
  });

  it('shows the unsupported-storefront message', async () => {
    const request = await open('/apps/361309726?cc=kp&l=ko');
    request.flush(
      { type: 'urn:appstore:problem:unsupported-storefront', status: 400 },
      { status: 400, statusText: 'Bad Request' },
    );
    await advance();

    expect(alertMessage()).toBe('The App Store is not available in this country.');
  });

  it('shows the invalid-request details for a bad country', async () => {
    const request = await open('/apps/361309726?cc=d1&l=de');
    request.flush(
      {
        type: 'urn:appstore:problem:invalid-request',
        errors: [{ field: 'cc', message: 'must be two letters' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await advance();

    expect(alertMessage()).toBe('Please check your input.');
    expect(element().querySelector('[role="alert"] li')?.textContent).toBe(
      'cc: must be two letters',
    );
  });

  it('retries the same request on Retry after an error', async () => {
    const request = await open('/apps/361309726?cc=de&l=de');
    request.flush(
      { type: 'urn:appstore:problem:upstream-timeout' },
      { status: 504, statusText: 'Gateway Timeout' },
    );
    await advance();
    expect(alertMessage()).toBe('The App Store is currently unavailable.');

    button('Retry')?.click();
    await advance();

    const retried = backend.expectOne((r) => r.url === PAGES_PATH);
    expect(retried.request.params.get('cc')).toBe('de');
    retried.flush(details());
    await advance();

    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(element().querySelector('h1')?.textContent).toBe('Pages');
  });
});
