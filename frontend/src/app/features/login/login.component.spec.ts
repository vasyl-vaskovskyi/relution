import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../core/auth/auth.service';
import { DEBUG_LOG_CONFIG } from '../../core/debug/debug-log.service';
import { LoginComponent } from './login.component';

@Component({ template: 'target page' })
class TargetStubComponent {}

describe('LoginComponent', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'login', component: LoginComponent },
          { path: 'search', component: TargetStubComponent },
          { path: 'apps/:id', component: TargetStubComponent },
        ]),
        {
          provide: DEBUG_LOG_CONFIG,
          useValue: { debugLogging: false, debugLoggingOverride: false },
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function open(url = '/login') {
    harness = await RouterTestingHarness.create(url);
  }

  function element(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function input(name: 'clientId' | 'clientSecret'): HTMLInputElement {
    return element().querySelector(`input[formcontrolname="${name}"]`) as HTMLInputElement;
  }

  function type(name: 'clientId' | 'clientSecret', value: string) {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
  }

  function submitButton(): HTMLButtonElement {
    return element().querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  async function logIn(clientId = 'client', clientSecret = 'secret') {
    type('clientId', clientId);
    type('clientSecret', clientSecret);
    submitButton().click();
    await harness.fixture.whenStable();
  }

  function alertText(): string | undefined {
    return element().querySelector('[role="alert"]')?.textContent?.trim();
  }

  it('asks for both fields and sends nothing when the form is empty', async () => {
    await open();

    submitButton().click();
    await harness.fixture.whenStable();

    const errors = [...element().querySelectorAll('mat-error')].map((e) => e.textContent?.trim());
    expect(errors).toEqual(['Enter the client id.', 'Enter the client secret.']);
    backend.expectNone('/auth/token');
  });

  it('shows progress while logging in, then navigates to the search', async () => {
    await open();

    await logIn('client', 'secret');

    expect(element().querySelector('mat-progress-bar[aria-label="Logging in"]')).not.toBeNull();
    expect(submitButton().disabled).toBe(true);
    const request = backend.expectOne('/auth/token');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe(`Basic ${btoa('client:secret')}`);

    request.flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/search');
    expect(TestBed.inject(AuthService).authenticated()).toBe(true);
  });

  it('returns to a safe in-app return URL after login', async () => {
    await open('/login?returnUrl=%2Fapps%2F361309726%3Fcc%3Dde');

    await logIn();
    backend
      .expectOne('/auth/token')
      .flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/apps/361309726?cc=de');
  });

  it('ignores a return URL to another origin', async () => {
    await open('/login?returnUrl=%2F%2Fevil.example');

    await logIn();
    backend
      .expectOne('/auth/token')
      .flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/search');
  });

  it('shows the credentials message and clears only the secret on wrong credentials', async () => {
    await open();

    await logIn('client', 'wrong');
    backend
      .expectOne('/auth/token')
      .flush(
        { type: 'urn:appstore:problem:unauthorized', status: 401 },
        { status: 401, statusText: 'Unauthorized' },
      );
    await harness.fixture.whenStable();

    expect(alertText()).toBe('Invalid client id or secret.');
    expect(input('clientId').value).toBe('client');
    expect(input('clientSecret').value).toBe('');
    expect(submitButton().disabled).toBe(false);
    expect(element().querySelector('mat-progress-bar')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(TestBed.inject(AuthService).authenticated()).toBe(false);
  });

  it('shows a generic message on a server error', async () => {
    await open();

    await logIn();
    backend
      .expectOne('/auth/token')
      .flush(
        { type: 'urn:appstore:problem:internal', status: 500 },
        { status: 500, statusText: 'Internal Server Error' },
      );
    await harness.fixture.whenStable();

    expect(alertText()).toBe('Something went wrong. Please try again.');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('shows a network message when the server cannot be reached', async () => {
    await open();

    await logIn();
    backend.expectOne('/auth/token').error(new ProgressEvent('error'), { status: 0 });
    await harness.fixture.whenStable();

    expect(alertText()).toBe('Cannot reach the server.');
  });

  it('clears the previous message when submitting again', async () => {
    await open();
    await logIn('client', 'wrong');
    backend
      .expectOne('/auth/token')
      .flush(
        { type: 'urn:appstore:problem:unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await harness.fixture.whenStable();

    await logIn('client', 'secret');

    expect(alertText()).toBeUndefined();
    backend
      .expectOne('/auth/token')
      .flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
  });
});
