import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { MESSAGES, problemMessage } from './problem-message';

function problem(
  status: number,
  slug: string | null,
  extra: Record<string, unknown> = {},
  headers: Record<string, string> = {},
): HttpErrorResponse {
  const body = slug === null ? null : { type: `urn:appstore:problem:${slug}`, status, ...extra };
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

describe('problemMessage', () => {
  it('maps invalid-request with the field messages', () => {
    const result = problemMessage(
      problem(400, 'invalid-request', {
        correlationId: 'c-1',
        errors: [{ field: 'cc', message: 'must be a supported storefront country code' }],
      }),
    );

    expect(result).toEqual({
      message: MESSAGES.invalidInput,
      details: ['cc: must be a supported storefront country code'],
      correlationId: 'c-1',
    });
  });

  it.each([
    ['unsupported-storefront', 400, MESSAGES.unsupportedStorefront],
    ['app-not-found', 404, MESSAGES.appNotFound],
    ['upstream-timeout', 504, MESSAGES.unavailable],
    ['upstream-error', 502, MESSAGES.unavailable],
    ['forbidden', 403, MESSAGES.forbidden],
    ['internal', 500, MESSAGES.internal],
  ])('maps %s', (slug, status, message) => {
    expect(problemMessage(problem(status, slug)).message).toBe(message);
  });

  it('includes Retry-After for upstream-unavailable', () => {
    const error = problem(503, 'upstream-unavailable', {}, { 'Retry-After': '30' });

    expect(problemMessage(error).message).toBe('Too many requests, try again in 30 s.');
  });

  it('asks to try later when upstream-unavailable has no usable Retry-After', () => {
    expect(problemMessage(problem(503, 'upstream-unavailable')).message).toBe(
      MESSAGES.tooManyRequestsLater,
    );
    const httpDate = problem(503, 'upstream-unavailable', {}, { 'Retry-After': 'Wed, 21 Oct' });
    expect(problemMessage(httpDate).message).toBe(MESSAGES.tooManyRequestsLater);
  });

  it('reports invalid credentials for unauthorized from the token endpoint', () => {
    expect(problemMessage(problem(401, 'unauthorized'), 'token').message).toBe(
      MESSAGES.invalidCredentials,
    );
  });

  it('reports an expired session for unauthorized from the API', () => {
    expect(problemMessage(problem(401, 'unauthorized'), 'api').message).toBe(
      MESSAGES.sessionExpired,
    );
  });

  it('chooses by problem type before status', () => {
    // A 400 carrying app-not-found still uses the type's message
    expect(problemMessage(problem(400, 'app-not-found')).message).toBe(MESSAGES.appNotFound);
  });

  it('falls back by status for unknown problem types', () => {
    expect(problemMessage(problem(422, 'something-new')).message).toBe(MESSAGES.invalidInput);
    expect(problemMessage(problem(599, 'something-new')).message).toBe(MESSAGES.unavailable);
  });

  it('falls back by status without a problem body', () => {
    const htmlGatewayError = new HttpErrorResponse({
      status: 502,
      error: '<html>Bad Gateway</html>',
    });

    expect(problemMessage(htmlGatewayError)).toEqual({
      message: MESSAGES.unavailable,
      details: [],
    });
    expect(problemMessage(problem(404, null)).message).toBe(MESSAGES.invalidInput);
  });

  it('parses a problem detail delivered as a string', () => {
    const error = new HttpErrorResponse({
      status: 404,
      error: JSON.stringify({ type: 'urn:appstore:problem:app-not-found' }),
    });

    expect(problemMessage(error).message).toBe(MESSAGES.appNotFound);
  });

  it('takes the correlation id from the header when the body has none', () => {
    const error = problem(500, 'internal', {}, { 'X-Correlation-Id': 'h-9' });

    expect(problemMessage(error).correlationId).toBe('h-9');
  });

  it('reports a network failure for status 0', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 })).message).toBe(MESSAGES.network);
  });

  it('handles non-HTTP errors', () => {
    expect(problemMessage(new Error('boom')).message).toBe(MESSAGES.internal);
  });
});
