import { HttpErrorResponse } from '@angular/common/http';
import { Platform, ProblemDetail } from '../api/api.types';

export const PROBLEM_TYPE_PREFIX = 'urn:appstore:problem:';

/** `token` for POST /auth/token, `api` for everything else. */
export type ProblemContext = 'api' | 'token';

export interface UserMessage {
  message: string;
  /** Field messages from `errors[]` (invalid-request only). */
  details: string[];
  /** Shown so users can report a problem; never contains user input. */
  correlationId?: string;
}

export const MESSAGES = {
  invalidInput: 'Please check your input.',
  unsupportedStorefront: 'The App Store is not available in this country.',
  appNotFound: 'App not found in this storefront.',
  appNotFoundOnPlatform: (platform: Platform) =>
    platform === 'mac'
      ? 'App not found for Mac in this storefront. It may exist for iOS only.'
      : 'App not found for iOS in this storefront. It may exist for Mac only.',
  tooManyRequests: (seconds: number) => `Too many requests, try again in ${seconds} s.`,
  tooManyRequestsLater: 'Too many requests, try again later.',
  unavailable: 'The App Store is currently unavailable.',
  invalidCredentials: 'Invalid client id or secret.',
  sessionExpired: 'Your session has expired. Please log in again.',
  forbidden: "You don't have access to this function.",
  internal: 'Something went wrong. Please try again.',
  network: 'Cannot reach the server.',
} as const;

/**
 * Maps an HTTP error to a user message by problem type first, then by status
 * (docs/architecture/frontend.md#error-messages-problem-messagets).
 */
export function problemMessage(error: unknown, context: ProblemContext = 'api'): UserMessage {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: MESSAGES.internal, details: [] };
  }
  if (error.status === 0) {
    return { message: MESSAGES.network, details: [] };
  }
  const problem = readProblem(error.error);
  const correlationId = problem.correlationId ?? error.headers.get('X-Correlation-Id') ?? undefined;
  const result = (message: string, details: string[] = []): UserMessage =>
    correlationId === undefined ? { message, details } : { message, details, correlationId };

  switch (problemSlug(problem.type)) {
    case 'invalid-request':
      return result(
        MESSAGES.invalidInput,
        (problem.errors ?? []).map((fieldError) => `${fieldError.field}: ${fieldError.message}`),
      );
    case 'unsupported-storefront':
      return result(MESSAGES.unsupportedStorefront);
    case 'app-not-found':
      return result(MESSAGES.appNotFound);
    case 'upstream-unavailable': {
      const seconds = retryAfterSeconds(error.headers.get('Retry-After'));
      return result(
        seconds === null ? MESSAGES.tooManyRequestsLater : MESSAGES.tooManyRequests(seconds),
      );
    }
    case 'upstream-timeout':
    case 'upstream-error':
      return result(MESSAGES.unavailable);
    case 'unauthorized':
      return result(context === 'token' ? MESSAGES.invalidCredentials : MESSAGES.sessionExpired);
    case 'forbidden':
      return result(MESSAGES.forbidden);
    case 'internal':
      return result(MESSAGES.internal);
    default:
      // Unknown or missing problem type: fall back by status class
      return result(error.status >= 500 ? MESSAGES.unavailable : MESSAGES.invalidInput);
  }
}

/**
 * The details view asks for one platform. Apple answers `app-not-found` when the app exists only for the other one,
 * so the message names the platform (docs/architecture/frontend.md#details).
 */
export function detailsProblemMessage(error: unknown, platform: Platform): UserMessage {
  const result = problemMessage(error);
  if (
    error instanceof HttpErrorResponse &&
    problemSlug(readProblem(error.error).type) === 'app-not-found'
  ) {
    return { ...result, message: MESSAGES.appNotFoundOnPlatform(platform) };
  }
  return result;
}

function problemSlug(type: string | undefined): string | undefined {
  return type?.startsWith(PROBLEM_TYPE_PREFIX) ? type.slice(PROBLEM_TYPE_PREFIX.length) : undefined;
}

function readProblem(body: unknown): ProblemDetail {
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch {
      return {};
    }
  }
  return body !== null && typeof body === 'object' ? (body as ProblemDetail) : {};
}

/** Retry-After in delta-seconds; the service never sends the HTTP-date form. */
function retryAfterSeconds(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value.trim())) {
    return null;
  }
  return Number.parseInt(value.trim(), 10);
}
