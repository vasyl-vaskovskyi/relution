import { Observable, catchError, defer, map, merge, of, share, takeUntil, timer } from 'rxjs';

/** After this delay a running request shows "Still loading, the App Store is slow". */
export const SLOW_HINT_MS = 3000;

export type LoadState<T> =
  | { status: 'loading'; slow: boolean }
  | { status: 'ok'; value: T }
  | { status: 'error'; error: unknown };

/**
 * Turns one request into loading → (slow loading) → ok | error states. Unsubscribing (for example from
 * switchMap) cancels the HTTP request.
 */
export function withLoadState<T>(request: Observable<T>): Observable<LoadState<T>> {
  return defer(() => {
    const result = request.pipe(
      map((value): LoadState<T> => ({ status: 'ok', value })),
      catchError((error: unknown) => of<LoadState<T>>({ status: 'error', error })),
      share(),
    );
    const slowHint = timer(SLOW_HINT_MS).pipe(
      map((): LoadState<T> => ({ status: 'loading', slow: true })),
      takeUntil(result),
    );
    return merge(of<LoadState<T>>({ status: 'loading', slow: false }), slowHint, result);
  });
}
