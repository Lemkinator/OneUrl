# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About

OneURL is an Android URL-shortener app with Samsung OneUI design. It integrates multiple third-party URL shortening services via their
public APIs, checks URLs against URLhaus before shortening, and generates QR codes.

## Build & Run

Requires GitHub credentials to access private Maven packages (`oneui-design`, `common-utils`). Provide via any of:

- `github.properties` in project root: `ghUsername=...` / `ghAccessToken=...` (needs `read:packages` scope)
- Global Gradle properties: `ghUsername` / `ghAccessToken`
- Environment variables: `GH_USERNAME` / `GH_ACCESS_TOKEN`

Secret keys (API keys for Kurzelinks, URLhaus) go in `secrets.properties` (see `secrets.defaults.properties` for keys).

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config in gradle.properties)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lint

# Run tests
./gradlew test
```

Debug APK has `applicationId = "de.lemke.oneurl.debug"` so it can coexist with release.

## Architecture

Clean Architecture with three layers:

**`data/`** — Repositories wrapping Room (`URLRepository`) and DataStore (`UserSettingsRepository`). All DB entities live in
`data/database/`; `DomainMapper.kt` converts between `URLDb` ↔ `URL` domain model.

**`domain/`** — Use cases (`*UseCase.kt`), each doing one thing, injected by Hilt. `GenerateURLUseCase` is the core flow: checks internet →
checks URL against URLhaus → delegates to the selected provider's `getCreateRequest()`. URL generation uses Volley (`RequestQueueSingleton`)
for all HTTP calls.

**`ui/`** — Activities, adapters, and ViewModels. Activities interact with use cases through ViewModels (MainViewModel, AddURLViewModel,
URLViewModel, GenerateQRCodeViewModel, ProviderViewModel), observing their state via coroutines and StateFlow rather than calling use cases
directly.

**`domain/model/`** — Each shortener service is an `object` (or nested objects for grouped services like `Tly`, `Kurzelinks`) implementing
`ShortURLProvider`. `ShortURLProviderCompanion` holds the master list; providers marked `//disabled` are instantiated but filtered out of
`enabled`.

**DI** — Single Hilt module (`PersistenceModule`) provides Room DB, URLDao, and DataStore.

## Adding a New URL Provider

1. Create `app/src/main/java/de/lemke/oneurl/domain/model/ProviderName.kt` implementing `ShortURLProvider`.
2. Implement `getCreateRequest()` using Volley — follow the pattern in `Dagd.kt` (parse error body strings for specific `GenerateURLError`
   subtypes).
3. Add to the `provider` list in `ShortURLProviderCompanion` in `ShortURLProvider.kt`.
4. If the provider supports aliases, implement `AliasConfig`.

## Static Analysis

Three tools run as part of `./gradlew build`:

- **Spotless** — enforces formatting via ktlint (sole ktlint driver; Detekt has no ktlint wrapper). Fix violations with
  `./gradlew spotlessApply`.
- **Detekt** — static analysis; config at `config/detekt/detekt.yml`. `autoCorrect = false` — fixes are manual.
- **Konsist** — architecture rules in `app/src/test/java/de/lemke/oneurl/ArchitectureTest.kt`. Enforces `data/domain/ui` layering
  (`data` may depend on `domain.model`'s shared value types, never on use cases). Runs as part of `./gradlew test`.

**Pre-commit hook** — blocks commits with formatting violations. Opt in once per clone:

```bash
git config core.autocrlf input           # Windows: prevents CRLF violations
git config core.hooksPath .githooks
```

The hook runs `spotlessCheck` and exits 1 with a `./gradlew spotlessApply` reminder on failure. It also fails fast with a targeted
message if `core.autocrlf=true` is detected. The hook does not run `lintDebug` (too slow for every commit) — always run it manually
after touching `libs.versions.toml` or a dependency block.

**After any change** — run the full local CI suite before declaring work done:

```bash
./gradlew spotlessCheck detekt lintDebug test assembleDebug
```

If `spotlessCheck` fails, fix with `./gradlew spotlessApply` then re-run.

**Dependency analysis** — manual hygiene tool (not gated in CI). Invoke with:

```bash
./gradlew buildHealth
```

Report at `build/reports/dependency-analysis/build-health-report.txt`. Review findings case-by-case — autofixing creates breakage.
