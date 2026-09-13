# Challenge brief

A summary of the Relution Discovery Day task. The original text was provided by Relution; this is our condensed reference.

## Format

- **One day at the office with the team.** Implementation happens on the day. Setup commits made before the day are allowed and should be recognizable as such.
- **Git from the start, small commits.** The history should show how the solution evolved.
- **AI-assisted development is expected.** What counts:
  - owning the result;
  - judgment over generation;
  - at least one example of a rejected, corrected or reworked AI suggestion, explained.
- **Review:** a 10–15 min sprint-review-style presentation, then questions about the implementation, structure, architecture and code (including specific code places), then feedback.

## Task: iTunes Search API and MZStorePlatform API

### Part 1: core
- **Search:** search apps via the iTunes Search API by term.
- **Details:** query the MZStorePlatform API with the ids from the search:
  `…/MZStorePlatform.woa/wa/lookup?version=2&id=<id>&p=mdm-lockup&caller=MDM&platform=enterprisestore&cc=<cc>&l=<l>`, where `id`, `cc` and `l` are parameters.
- **Presentation:** results appear as a list, from which you navigate to app details.

### Part 2: production readiness
"Imagine your code is going to be merged into a large, long-lived product that many developers work on."
- **Error tolerance:** empty results, missing or inconsistent fields, slow, rate-limited or failing requests. Handle them deliberately and be ready to explain which cases you considered.
- **Tests:** for the parts most worth testing, with a justified choice.
- **Maintainability:** structure, naming and separation of concerns at pull-request quality. Generated code counts as your own code.

### Part 3: make it yours
Take one direction meaningfully further; depth beats breadth. The suggested ideas were caching or rate-limit handling, pagination, accessibility, i18n, observability, security hardening, resilience patterns, CI and developer experience, and books as a second media type. Be ready to explain the choice.

### Server requirements (backend position)
- **Two endpoints:** search (iTunes Search API) and details (MZStorePlatform API, with the app id, country and language).
- **Results transformed into client-friendly objects** that contain only the properties the client needs.
- **Upstream failures** (timeouts, errors, unexpected payloads) translated into meaningful responses.
- **If there is no client:** a simple authentication layer and API documentation.

### Client requirements (optional for a backend position; web = Angular + Angular Material)
- **Search:** a list with a search field; results show the app name and icon.
- **States:** acceptable handling of "no results", failing requests and slow responses.
- **Details:** a details view (bundle id, version, price, supported platforms, descriptions, what's new, …) loaded by a second API call.
- **Locale:** the current locale pre-fills `cc` and `l`.

## Our scope

- A backend position, so the **server is the graded core**.
- A time-boxed Angular client that must not come at the expense of the server ([ADR-0017](../adr/0017-add-an-angular-web-client-time-boxed.md)).
- Auth and OpenAPI are kept even though a client exists, because the API must stand on its own.
- **Part 3 direction:** resilience + caching ([ADR-0007](../adr/0007-part-3-direction-resilience-caching.md)).

## What reviewers look for

- Correctness and error tolerance of what was built, not how much.
- Judgment: noticing when something, including AI output, is wrong or doesn't fit, and acting on it.
- Fit for a large product: would they merge it and maintain it?
- Prioritization within a hard timebox, and the ability to explain it.
- Communication: presenting, defending decisions, taking critical questions.
