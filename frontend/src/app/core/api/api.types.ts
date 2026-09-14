// Hand-written mirror of docs/api/README.md. Clients must ignore unknown fields and tolerate unknown
// enum values and problem types, so the enum-like fields are plain strings.

export interface Price {
  amount: string | null;
  currency: string | null;
  formatted: string | null;
}

export interface Rating {
  average: number | null;
  count: number | null;
}

export interface AppSummary {
  id: string;
  name: string;
  developer: string | null;
  iconUrl: string | null;
  kind: string;
  price: Price | null;
  rating: Rating | null;
}

export interface SearchResponse {
  items: AppSummary[];
  count: number;
  storefront: { cc: string };
}

export type Platform = 'ios' | 'mac';

export interface SearchCriteria {
  term: string;
  cc: string;
  limit: number;
}

export interface DetailsCriteria {
  cc: string;
  l: string;
  platform: Platform;
}

export interface AppLinks {
  store: string | null;
  support: string | null;
  privacyPolicy: string | null;
}

export interface AppDetails {
  id: string;
  name: string;
  kind: string;
  subtitle: string | null;
  developer: string | null;
  seller: string | null;
  bundleId: string | null;
  watchBundleId: string | null;
  version: string | null;
  minimumOsVersion: string | null;
  firstReleaseDate: string | null;
  price: Price | null;
  platforms: string[];
  universal: boolean;
  description: string | null;
  whatsNew: string | null;
  iconUrl: string | null;
  genres: string[];
  rating: Rating | null;
  links: AppLinks | null;
  storefront: { cc: string; language: string | null; platform: string | null };
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface FieldError {
  field: string;
  message: string;
}

/** RFC 9457 problem detail as the service writes it. Every member is optional for the client. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errors?: FieldError[];
}
