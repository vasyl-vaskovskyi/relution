import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { DEBUG_LOG_CONFIG } from '../debug/debug-log.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: DEBUG_LOG_CONFIG,
          useValue: { debugLogging: false, debugLoggingOverride: false },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  function logIn(token = 'token-1') {
    auth.login('client', 'secret').subscribe();
    backend
      .expectOne('/auth/token')
      .flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });
  }

  it('sends the client credentials as HTTP Basic, not as a bearer token', () => {
    auth.login('client', 'secret').subscribe();

    const request = backend.expectOne('/auth/token');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe(`Basic ${btoa('client:secret')}`);
    request.flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    expect(auth.authenticated()).toBe(true);
  });

  it('attaches the bearer token to API requests', () => {
    logIn('token-1');

    http.get('/api/v1/apps').subscribe();

    const request = backend.expectOne('/api/v1/apps');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush({});
  });

  it('adds no header without a token', () => {
    http.get('/api/v1/apps').subscribe();

    const request = backend.expectOne('/api/v1/apps');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('adds no bearer token to /auth/token, even when logged in', () => {
    logIn('token-1');

    auth.login('client', 'other').subscribe({ error: () => undefined });

    const request = backend.expectOne('/auth/token');
    expect(request.request.headers.get('Authorization')).toMatch(/^Basic /);
    request.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('clears the token and navigates to /login on a 401 from the API', () => {
    logIn();
    let failed = false;

    http.get('/api/v1/apps/1').subscribe({ error: () => (failed = true) });
    backend
      .expectOne('/api/v1/apps/1')
      .flush(
        { type: 'urn:appstore:problem:unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(failed).toBe(true);
    expect(auth.token()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('stays on the form on a 401 from /auth/token', () => {
    let failed = false;

    auth.login('client', 'wrong').subscribe({ error: () => (failed = true) });
    backend
      .expectOne('/auth/token')
      .flush(
        { type: 'urn:appstore:problem:unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(failed).toBe(true);
    expect(auth.authenticated()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps the token on other errors', () => {
    logIn();

    http.get('/api/v1/apps').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/apps').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(auth.authenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
