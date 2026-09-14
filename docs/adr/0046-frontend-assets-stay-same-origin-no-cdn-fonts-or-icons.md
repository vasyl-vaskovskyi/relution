# ADR-0046: Frontend assets stay same-origin: no CDN fonts or icons

- **Status:** Accepted
- **Date:** 2026-09-14 (Discovery Day, frontend block)

- **Context:**
  - `ng add @angular/material` put four links into `frontend/src/index.html`: preconnects to `fonts.googleapis.com` and `fonts.gstatic.com`, the Roboto stylesheet and the Material Icons font.
  - The CSP from [ADR-0034](0034-security-and-privacy-hardening.md) (`default-src 'self'`, `style-src 'self' 'unsafe-inline'`) blocks those origins, so the fonts would silently fail in the nginx image.
  - Loading them from Google would also send every visitor's IP address to a third party. For a service operated in the EU (GDPR), that needs a legal basis and consent.
  - The client needs only a handful of icons (search, back, retry, logout, external link, error, iPhone, Mac).
- **Options:**
  1. Allow the Google origins in the CSP.
  2. Add npm packages that ship the font files (for example `@fontsource/roboto`, `material-icons`) and serve them from nginx.
  3. Use a system font stack for text, and register the few icons as inline SVG through `MatIconRegistry`.
- **Decision:** Option 3.
  - **Text:** the Material theme's typography uses a system font stack (`system-ui`, `-apple-system`, `Segoe UI`, `Roboto`, `Helvetica Neue`, `Arial`, `sans-serif`), so no font file is downloaded.
  - **Icons:** `core/icons/app-icons.ts` registers each icon once with `MatIconRegistry.addSvgIconLiteral`; templates use `<mat-icon svgIcon="…">`. The SVG paths are copied from Material Symbols (Apache License 2.0) and are constants in the bundle, never user input, so trusting them with `DomSanitizer.bypassSecurityTrustHtml` is safe.
  - **Rule:** the web client loads nothing from a third-party origin except app icons from `https://*.mzstatic.com`, which the CSP already allows.
  - No new npm dependencies.
- **Consequences:**
  - The CSP stays as strict as ADR-0034 defined it, and no visitor data reaches Google.
  - Text looks slightly different per operating system; for a demo client that is acceptable.
  - Each new icon means adding an SVG path to `app-icons.ts`. If the client grows to many icons, option 2 is the path forward (with the maintainer's approval for the dependency).
