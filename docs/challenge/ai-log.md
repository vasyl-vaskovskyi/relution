# AI log: rejected, corrected and reworked suggestions

This log records every AI suggestion that was rejected, corrected or significantly reworked. The point is to show judgment: where the AI was wrong, overengineered, subtly broken or a bad fit, and what we did instead. Write entries while working, not afterwards.

Entries 1–8 come from prep and are about process, facts, design and tooling. **The day must add at least one code-level entry.**

Template:

```
## N — Short title
- **When:** prep | day, H+x:xx, commit <sha> if any
- **Suggested:** what the AI proposed (quote or summarize)
- **Problem:** why it was wrong or a bad fit, and how it was noticed
- **Outcome:** rejected | corrected | reworked, and what we did instead
- **Lesson:** what to check next time
```

---

## 1 — Java 21 chosen because it was installed (prep)
- **When:** prep, 2026-09-13
- **Suggested:** The assistant proposed a Java 21 Gradle toolchain and called it the "safest LTS". The real reason was that Temurin 21 was already on the machine.
- **Problem:** Vasyl challenged it: "why should we start a project and from the start having tech debt?" Java 25 has been the current LTS since Sept 2025 and is supported by Spring Boot 4.1. The other option, Java 26, is non-LTS and is superseded by Java 27 this month.
- **Outcome:** Rejected. We use Java 25 LTS via a Gradle toolchain and Docker images, so the local JDK no longer dictates the version.
- **Lesson:** Choose versions by support status, not by what the local environment happens to have.

## 2 — Design built from live probing, not cross-checked against Apple's docs (prep)
- **When:** prep, 2026-09-13
- **Suggested:** A complete design (endpoints, error matrix, Part 3) based on live API probes and framework research.
- **Problem:** Vasyl asked whether the information from both API doc pages had been taken into account. It had not. Cross-checking found:
  - The ~20 calls/min limit applies **per source IP**, so our server shares one budget with all of its clients. The design only reacted to 429s instead of staying under the limit.
  - Apple's doc samples disagree with each other and with the live API. The artwork is an array with concrete URLs in one sample, but an object with a URL template in the other sample and live. The `id` is a number in the doc sample and a string live.
  - The docs contain explicit `null`s, not only missing fields.
  - The `platform` parameter changes the answer for universal apps. `enterprisestore` returns iOS metadata and `macappstore` returns Mac metadata (Pages 15.3 vs 15.3.1).
  - An empty `results` object has three documented causes, so a 404 must not claim that the app "does not exist".
- **Outcome:** Corrected:
  - an outbound rate limiter (later made a stretch goal, ADR-0017),
  - a mapper that accepts all artwork shapes and ids of both types,
  - null and missing fields treated the same,
  - an optional `platform` parameter,
  - new 404 wording.
- **Lesson:** Probe live **and** read the docs, then reconcile the two. Every disagreement becomes a test case.

## 3 — Error matrix treated a client input error as a server fault (prep)
- **When:** prep, 2026-09-13
- **Suggested:** The error matrix validated `cc`/`country` against Java's ISO country list. It mapped an MZ storefront mismatch to **502** ("should not happen after validation") and every Apple 4xx on Search to **502** ("our integration bug").
- **Problem:** A live check with valid ISO codes that have no App Store (`cu`, `kp`, `aq`) showed:
  - Search returns **400** `Invalid value(s) for key(s): [country]`.
  - MZ **silently serves the US storefront**, with wrong prices and names but a successful response.

  Both are client input errors. Reporting them as 502 would send clients to retry something that can never succeed. The ISO list is simply not Apple's list of storefronts.
- **Outcome:** Reworked:
  - An allowlist built from Apple's official list of 175 storefronts, checked before any call.
  - Runtime detection both ways: 400 plus internal logs when the list is outdated or incomplete, and a cached Apple verdict for codes missing from the list.
  - See ADR-0008.
- **Lesson:** "Should never happen" in an error matrix is a claim to test, not an assumption. Test boundary inputs that pass our validation but not Apple's.

## 4 — Unsupported "fact" about `resultCount` (prep)
- **When:** prep, 2026-09-13
- **Suggested:** The first brief stated "`resultCount` is unreliable" as an observed fact about the Search API.
- **Problem:** An independent review compared the claim with the captured responses. In every capture, `resultCount` equalled `results.length`. What the captures actually showed was different: `limit=0` and `limit=500` don't return errors, results are capped, and fewer results than `limit` can come back.
- **Outcome:** Corrected in CLAUDE.md and `docs/integrations/apple-api-behavior.md`. The code still iterates `results` rather than trusting the count, but the justification is now honest.
- **Lesson:** Every fact tagged "observed" must be traceable to a capture. AI summaries of probes can overstate.

## 5 — Schedule option that did not add up (prep)
- **When:** prep, 2026-09-13
- **Suggested:** To make room for the Angular client, the assistant offered this schedule option: "frontend 1:15, Part 3 trimmed to 1:00, OpenAPI 0:15, buffer 0:45". Vasyl accepted it.
- **Problem:** When writing the schedule table, the assistant noticed the blocks added up to **8:30**, not 8:00. The option had been offered without summing the budgets.
- **Outcome:** Corrected before anything was written. The assistant disclosed the error and asked again. The 30 minutes now come from three places:
  - a shorter kickoff (tooling and versions verified in prep);
  - a shorter backend Docker block;
  - the Details block, which reuses the Search patterns.

  The frontend was also moved after the backend blocks.
- **Lesson:** Check the arithmetic of any plan option before offering it. A plausible-sounding option is not a verified one.

## 6 — Scaffolded code and a script before implementation was requested (prep)
- **When:** prep, 2026-09-13
- **Suggested:** Vasyl chose "scaffold before the day" in a planning question. The assistant then ran `ng new` and `ng add @angular/material` straight away, added a proxy config, and committed nothing. Earlier it had also written an executable `scripts/smoke.sh`.
- **Problem:** Vasyl: "Why did you implement a Front end? I didn't ask you to implement yet. Remove files, leave only docs." Picking an option for the plan is not an instruction to generate code in the repo. The assistant acted on a planning answer without confirming the concrete action.
- **Outcome:** Rejected. The `frontend/` scaffold and `scripts/` were deleted. The repo holds docs plus `.gitignore`, `.nvmrc` and `stubs/` (test data). The frontend and smoke-script plans stay in the challenge plan for the day (ADR-0022).
- **Lesson:** Planning decisions and execution are separate steps. Before generating code or files, confirm the concrete action ("shall I run `ng new` now?"), especially in a repo whose history is being evaluated.

## 7 — AI-authored design had a package cycle, blocking cache and leaky API terms (prep)
- **When:** prep, 2026-09-13
- **Suggested:** The assistant's implementation brief planned:
  - packages `api`/`apps`/`apple`/`config`;
  - Spring `@Cacheable(sync = true)` caches with retries on 5xx (worst case ≈ 21.6 s inside the cache lock);
  - `platform=enterprisestore|macappstore` in the public API;
  - prices as doubles;
  - a single `DECISIONS.md`;
  - one CLAUDE.md that mixed durable rules with the day's schedule.
- **Problem:** Vasyl asked for a deep review against "merged into a large, long-lived product that many developers work on". Three independent reviews (architecture, operations/security, docs/process) found:
  - `apps` ↔ `apple` formed a dependency cycle, and nothing enforced the rules;
  - synchronous Caffeine loads can block unrelated keys, and not-found lookups weren't deduplicated;
  - Apple's channel names leaked into our API contract;
  - after a 429 the server kept calling Apple;
  - Actuator shared the public port, so a heap dump could expose the secret;
  - there was no CI, formatter or drift detection;
  - superseded decisions still read as current.
- **Outcome:** Reworked (ADR-0028 … ADR-0040). The changes:
  - ports in `catalog`, the adapter in `integration.apple`, ArchUnit enforcement;
  - native `AsyncCache` with a `LookupResult` expiry, connection-only retry bounded to 8 s;
  - `platform=ios|mac` and decimal-string prices;
  - the 429 short-circuit;
  - management port 8081;
  - the `appstore.*` config namespace;
  - security and privacy hardening;
  - CI, Spotless and Dependabot;
  - a nightly drift test;
  - per-file ADRs with statuses, and docs split into durable and challenge material.

  Before writing the new details, a research pass verified them and corrected two assumptions: `setAsyncCacheMode` doesn't apply to custom caches, and Spring 7 `@Retryable` has a `timeout` attribute.
- **Lesson:** Review AI-generated designs against the actual quality bar from several perspectives, and verify framework claims before encoding them in docs.

## 8 — A bulk-edit script silently truncated seven ADRs (prep)
- **When:** prep, 2026-09-14
- **Suggested:** To apply review fixes, the assistant wrote a Python script that replaced each historical ADR's status line with the regex `^- \*\*Status:\*\* .*$`. The same helper also ran other patterns with the `re.S` (DOTALL) flag.
- **Problem:** With DOTALL, `.*$` matched to the **end of the file**. The status updates replaced the whole body of ADR-0005, 0007, 0017, 0018, 0022, 0027 and 0033 with just the new status line. The script reported success. The damage only showed up because a later replacement in ADR-0033 couldn't find its text, and the assistant investigated instead of retrying. Nothing was committed yet, so git couldn't restore the files.
- **Outcome:** Corrected. The seven bodies were rebuilt from their last known content, applying the same migration rules. A completeness check now runs over every ADR (Date line, Decision section, minimum length), plus a full link and stale-value sweep.
- **Lesson:**
  - Never combine DOTALL with line-anchored patterns in bulk edits.
  - Prefer exact, count-checked string replacements.
  - Commit before any scripted mass edit, so `git diff` shows the damage and `git checkout` can undo it.
  - Treat "script reported success" as unverified.

<!-- Discovery Day entries below -->

## 9 — Spike test read a field through a CGLIB proxy (day)
- **When:** day, H+0:30, de-risking spike (throwaway project, no commit)
- **Suggested:** To prove that `@Retryable` accepts property placeholders, the assistant wrote a test bean `Flaky` with a public `AtomicInteger attempts` field, and the test read `flaky.attempts` directly.
- **Problem:** Both retry tests failed with a `NullPointerException`: `this.flaky.attempts` was null. `@EnableResilientMethods` had wrapped the bean in a CGLIB subclass (`Flaky$$SpringCGLIB$$0`). The injected object is the proxy, and a proxy's own fields are never initialized; only method calls reach the target. The failure looked like "retry is broken" but was a test bug. The stack trace pointed at the field access, not at the retry.
- **Outcome:** Corrected. The counter became private and is read through a public method, which the proxy delegates. The rerun proved the real behavior: 3 attempts for the included exception, 1 for others, and the timeout stops new attempts. The proxy rule is now written down in [`../architecture/caching-resilience.md`](../architecture/caching-resilience.md#retry).
- **Lesson:** A red test can be wrong about the thing it tests. Read the stack trace before concluding the framework misbehaves, and never access fields on Spring-proxied beans.
