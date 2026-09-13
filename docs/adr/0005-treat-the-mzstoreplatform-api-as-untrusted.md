# ADR-0005: Treat the MZStorePlatform API as untrusted

- **Status:** Accepted; adapter package renamed by [ADR-0028](0028-package-boundaries-and-ports.md) (`integration.apple`); fixture handling amended by [ADR-0040](0040-capture-archive-and-test-fixtures.md) and [ADR-0041](0041-move-captures-into-wiremock-and-remove-stubs.md)
- **Date:** 2026-09-13 (prep)

- **Context:** Apple marks the lookup service as *Legacy* and documents it only through sample JSON. The samples already contradict each other and the live API:
  - `artwork` is an array with concrete URLs in one doc sample. In the other doc sample and live, it is an object with a URL template.
  - `id` is a number in the doc sample and a string live.
- **Options:** strict typed mapping that fails on surprises; tolerant mapping where every field is optional.
- **Decision:** Tolerant mapping behind an `apple/` adapter boundary, with boxed types, tested against real captured fixtures.
- **Consequences:** Mappers need more null handling, which pure-function tests cover. Apple changes stay confined to one package.
