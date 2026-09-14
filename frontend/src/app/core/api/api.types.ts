// Client types derived from the generated OpenAPI types (generated/openapi.ts, ADR-0052).
// Field names and value types come from docs/api/openapi.json. The generated schemas mark no field
// as required or nullable, so presence and nullability follow docs/api/README.md here.
// Clients must ignore unknown fields and tolerate unknown enum values and problem types, so the
// enum-like response fields stay plain strings.
import { components, operations } from './generated/openapi';

type Schemas = components['schemas'];

/** Every documented field is present; the keys in `NeverNull` are never null, all others may be. */
type Present<T, NeverNull extends keyof T = never> = {
  [K in keyof T]-?: K extends NeverNull ? Exclude<T[K], undefined> : Exclude<T[K], undefined> | null;
};

/** Replaces nested schema references with their client types; `never` if a key isn't in the schema. */
type Override<T, O> = keyof O extends keyof T ? Omit<T, keyof O> & O : never;

export type Price = Present<Schemas['PriceDto']>;

export type Rating = Present<Schemas['RatingDto']>;

export type AppSummary = Override<
  Present<Schemas['Item'], 'id' | 'name' | 'kind'>,
  { price: Price | null; rating: Rating | null }
>;

export type SearchStorefront = Present<Schemas['SearchStorefront'], 'cc'>;

export type SearchResponse = Override<
  Present<Schemas['AppSearchResponse'], 'items' | 'count' | 'storefront'>,
  { items: AppSummary[]; storefront: SearchStorefront }
>;

export type SearchCriteria = Required<operations['search']['parameters']['query']>;

export type DetailsCriteria = Required<operations['details']['parameters']['query']>;

export type Platform = DetailsCriteria['platform'];

export type AppLinks = Present<Schemas['Links']>;

export type DetailsStorefront = Present<Schemas['DetailsStorefront'], 'cc'>;

export type AppDetails = Override<
  Present<
    Schemas['AppDetailsResponse'],
    'id' | 'name' | 'kind' | 'platforms' | 'universal' | 'genres' | 'storefront'
  >,
  { price: Price | null; rating: Rating | null; links: AppLinks | null; storefront: DetailsStorefront }
>;

/** Not described by the contract yet (`POST /auth/token` documents an untyped object). */
export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

/** One invalid parameter in `errors[]`; not described by the contract yet. */
export interface FieldError {
  field: string;
  message: string;
}

/**
 * RFC 9457 problem detail as the service writes it. Every member is optional for the client.
 * The contract shows Spring's `properties` map, but the service writes those members flat.
 */
export type ProblemDetail = Omit<Schemas['ProblemDetail'], 'properties'> & {
  correlationId?: string;
  errors?: FieldError[];
};
