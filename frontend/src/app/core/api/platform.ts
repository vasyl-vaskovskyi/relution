import { Platform } from './api.types';

/**
 * The details platform that fits a search result (docs/architecture/frontend.md#details): Mac apps open on Mac,
 * everything else, including unknown kinds, on iOS.
 */
export function platformForKind(kind: string | null | undefined): Platform {
  return kind === 'MAC_APP' ? 'mac' : 'ios';
}
