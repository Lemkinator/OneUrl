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
`data/database/`; `domainMapper.kt` converts between `URLDb` ↔ `URL` domain model.

**`domain/`** — Use cases (`*UseCase.kt`), each doing one thing, injected by Hilt. `GenerateURLUseCase` is the core flow: checks internet →
checks URL against URLhaus → delegates to the selected provider's `getCreateRequest()`. URL generation uses Volley (`RequestQueueSingleton`)
for all HTTP calls.

**`ui/`** — Activities, adapters, and ViewModels. Activities interact with use cases through ViewModels (MainViewModel, AddURLViewModel, URLViewModel, GenerateQRCodeViewModel, ProviderViewModel), observing their state via coroutines and StateFlow rather than calling use cases directly.

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

## Key Constraints

- All standard AndroidX UI components (`appcompat`, `recyclerview`, `fragment`, etc.) are globally excluded — replaced by `oneui-design`
  equivalents. Don't add them back.
- `Locale.getDefault()` is cached at class-load time for the provider list (intentional — see comment in `ShortURLProviderCompanion`).
- `URL.equals()` / `hashCode()` are based solely on `shortURL`. Use `contentEquals()` for full field comparison.
- Supported locales are restricted to `en` and `de` (`androidResources.localeFilters`).
