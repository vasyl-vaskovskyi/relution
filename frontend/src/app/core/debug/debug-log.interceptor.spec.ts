import { HttpClient, HttpParams, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { debugLogInterceptor } from './debug-log.interceptor';
import { DEBUG_LOG_CONFIG } from './debug-log.service';

describe('debugLogInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let logged: unknown[][];

  function setUp(debugLogging: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([debugLogInterceptor])),
        provideHttpClientTesting(),
        { provide: DEBUG_LOG_CONFIG, useValue: { debugLogging, debugLoggingOverride: false } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    logged = [];
    const capture = (...args: unknown[]) => logged.push(args);
    vi.spyOn(console, 'log').mockImplementation(capture);
    vi.spyOn(console, 'groupCollapsed').mockImplementation(capture);
    vi.spyOn(console, 'groupEnd').mockImplementation(() => undefined);
  }

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  const allOutput = () => JSON.stringify(logged);

  it('logs the term length, correlation id and item count, but never the term', () => {
    setUp(true);
    const params = new HttpParams({ fromObject: { term: 'secret diary', cc: 'de', limit: 25 } });
    http.get('/api/v1/apps', { params }).subscribe();

    backend
      .expectOne((req) => req.url === '/api/v1/apps')
      .flush(
        { items: [{ id: '1' }, { id: '2' }], count: 2, storefront: { cc: 'de' } },
        { headers: { 'X-Correlation-Id': 'abc-123' } },
      );

    expect(logged[0][0]).toMatch(/^\[appstore\] GET \/api\/v1\/apps 200 \d+ms$/);
    expect(allOutput()).toContain('"termLength":12');
    expect(allOutput()).toContain('abc-123');
    expect(allOutput()).toContain('"count":2');
    expect(allOutput()).not.toContain('secret diary');
  });

  it('logs the problem detail and Retry-After of an error response', () => {
    setUp(true);
    http.get('/api/v1/apps').subscribe({ error: () => undefined });

    backend
      .expectOne('/api/v1/apps')
      .flush(
        { type: 'urn:appstore:problem:upstream-unavailable', status: 503, correlationId: 'c-1' },
        { status: 503, statusText: 'Service Unavailable', headers: { 'Retry-After': '30' } },
      );

    expect(allOutput()).toContain('urn:appstore:problem:upstream-unavailable');
    expect(allOutput()).toContain('"30"');
  });

  it('never logs the access token or the Authorization header', () => {
    setUp(true);
    http
      .post('/auth/token', null, { headers: { Authorization: 'Basic Y2xpZW50OnNlY3JldA==' } })
      .subscribe();

    backend
      .expectOne('/auth/token')
      .flush({ accessToken: 'eyJ.secret.token', tokenType: 'Bearer', expiresIn: 900 });

    expect(allOutput()).toContain('"expiresIn":900');
    expect(allOutput()).not.toContain('eyJ.secret.token');
    expect(allOutput()).not.toContain('Y2xpZW50OnNlY3JldA==');
  });

  it('writes nothing when debug logging is off', () => {
    setUp(false);
    http.get('/api/v1/apps').subscribe();

    backend.expectOne('/api/v1/apps').flush({ items: [], count: 0, storefront: { cc: 'de' } });

    expect(logged).toEqual([]);
  });
});
