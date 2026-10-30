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

### Baseline Profile & Benchmarks

The baseline profile is generated automatically as part of every `assembleRelease` — no manual step
and nothing committed to git (`app/build.gradle.kts`'s `baselineProfile { variants { create("release") { ... } } }`).
PR CI passes `-Pandroidx.baselineprofile.skipgeneration` so a PR's `assembleRelease` never boots the
GMD; the release workflow and a weekly smoke test (`baseline-profile.yml`) don't, so they always
generate fresh. `./gradlew :app:generateBaselineProfile` still works standalone as a local diagnostic
(same GMD device — image already cached if you ran instrumented tests) — run it in the background,
not a foreground shell with a short timeout; it takes ~9-10 minutes:

```bash
./gradlew :app:generateBaselineProfile -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Run macrobenchmarks manually on a **connected physical device**, never the GMD (the library flags an
emulator as an `EMULATOR` error condition) — never in CI, only after touching the startup path or a
benchmarked journey:

```bash
./gradlew :benchmarks:connectedBenchmarkReleaseAndroidTest
```

Read the delta between `startupBaselineProfile` and `startupNoCompilation` (same device, so noise
cancels) — that delta is what the profile is worth. Don't chase absolute ms, don't gate on them,
don't store history.

## Architecture

Clean Architecture with three layers:

**`data/`** — `URLRepository` is an interface; `DefaultURLRepository` implements it over Room. `UserSettings` wraps SharedPreferences,
bound as `SettingsRepository`. All DB entities live in `data/database/`; `DomainMapper.kt` converts between `URLDb` ↔ `URL` domain model.
Framework and network work sits behind interfaces with a `Default*` implementation: `QRCodeRenderer` (OneUI `QrEncoder`),
`VolleyQueue` (the app's one Volley `RequestQueue`), and the three network clients built on it: `UrlhausClient`,
`PageTitleFetcher` and `ShortURLClient` (create a short URL, read its visit count). `NetworkMonitor` reads validated connectivity. `TlyKeyStore` keeps the user's optional T.LY API key (`DefaultTlyKeyStore` over the `SecretStore` interface, whose `KSafeSecretStore` is the only KSafe adapter, in `noBackupFilesDir`, so the key is not part of any backup; a failing store reads as `StoredTlyKey.Unavailable`, which fails the link with `TlyKeyRejected` and sends no request); `DefaultShortURLClient` hands it to the T.LY providers only. Click counts come from the provider API only; never store them or derive them from local link data.

**`domain/`** — Use cases (`*UseCase.kt`), each doing one thing, injected by Hilt. `GenerateURLUseCase` is the core flow: checks internet →
checks URL against URLhaus → delegates to the selected provider's `getCreateRequest()`. It reads connectivity through `NetworkMonitor`, checks
through `CheckURLSafetyUseCase` over `UrlhausClient`, and creates through `ShortURLClient`. Every HTTP call goes through `VolleyQueue`.

**`ui/`** — Activities, adapters, and ViewModels. Activities interact with use cases through ViewModels (MainViewModel, AddURLViewModel,
URLViewModel, GenerateQRCodeViewModel, QRBottomSheetViewModel, ProviderViewModel), observing their state via coroutines and StateFlow rather
than calling use cases directly.

**QR code export** — `GenerateQRCodeViewModel`, `URLViewModel` and `QRBottomSheetViewModel` each compose a common-utils
`BitmapExportStateHolder`, which runs save, copy, share and the document-picker write through the common-utils `BitmapExporter` and
exposes them as `export: StateFlow<BitmapExport>`. `Context.launchBitmapExport` is the one screen-side reaction to a result.
`GetQRCodeUseCase` serves the full-size QR code of a short URL from `QRCodeCache` and generates it on a miss.

**Backup** — `data/backup/` holds `UrlBackupSection`, the one `urls` section of a common-utils backup, over `UrlBackupEntry` (a serializable DTO,
never `URLDb`). Merge keys by short URL and keeps the phone's entry, favorite when either side is; the `added` stamp travels as ISO-8601 text with
its offset. `RoomBackupTransaction` runs the engine in one Room transaction. OneUrl signs nothing, so its `.oneurl` files stay plain zips. The
Backup category comes from `commonutilsR.xml.preferences_backup` in the list `MainActivity` passes to `setupCommonUtilsSettingsActivity`.

**`domain/model/`** — Each shortener service is an `object` (or nested objects for grouped services like `Tly`, `Kurzelinks`) implementing
`ShortURLProvider`. `ShortURLProviderCompanion` holds the master list; providers with `enabled = false` are instantiated but filtered out of
`enabled`.

**DI** — `di/` holds the `SingletonComponent` Hilt modules: `PersistenceModule` (Room DB, `URLDao`), `SettingsModule.kt`
(`UserSettings`, bound as `SettingsRepository`), `DispatchersModule` (includes the common-utils `CommonUtilsCoroutinesModule`: `@DefaultDispatcher`, `@IoDispatcher`, `@MainDispatcher`, `@ApplicationScope`),
`ProviderModule` (`@EnabledProviders`), `URLRepositoryModule` (binds `DefaultURLRepository`), `NetworkModule` (binds `VolleyQueue`,
`NetworkMonitor`, `UrlhausClient`, `PageTitleFetcher` and `ShortURLClient`), `QRCodeRendererModule` (binds `DefaultQRCodeRenderer`), `SecretsModule` (provides `KSafe`, binds `TlyKeyStore`),
`BitmapExporterModule` (provides the common-utils `DefaultBitmapExporter`; screen tests uninstall it to bind an exporter on a pausable
IO dispatcher), `InAppReviewModule` (includes the common-utils `CommonUtilsInAppReviewModule`, which binds `PlayInAppReview` as `InAppReview`)
and `BackupModule` (includes the common-utils `CommonUtilsBackupModule`; binds `UrlBackupSection`, `RoomBackupTransaction`, the `urls` label and the `BackupApp` with the `.oneurl` extension).

**Test doubles** — `testFixtures` holds the `@TestInstallIn` modules that every Hilt test, local or instrumented, loads:
`TestPersistenceModule`, `TestSettingsModule`, `TestDispatchersModule`, `TestInAppReviewModule` (a singleton `FakeInAppReview`), `TestSecretsModule` (a singleton `FakeTlyKeyStore`, because the Android Keystore is missing under Robolectric) and `TestNetworkModule`. `TestNetworkModule` binds the singleton
fakes `FakeNetworkMonitor`, `FakeUrlhausClient`, `FakePageTitleFetcher` and `FakeShortURLClient`, so no Hilt test reaches the network;
a test injects a fake to set its answers. ViewModel and use-case tests run real use cases over fakes such as `FakeURLRepository`,
`FakeQRCodeRenderer` and `FakeVolleyQueue`. `TlyKeyStoreContract` (testFixtures) runs against `DefaultTlyKeyStore` over a `FakeSecretStore` and, on a device, over `KSafeSecretStore`. `URLRepositoryContract` runs against both `DefaultURLRepository` and `FakeURLRepository`.
MockK stays only for framework values (`Bitmap`, `Uri`, `ClipData`) and `Log` in plain JVM Kotest specs, which cannot build them.

## Adding a New URL Provider

1. Create `app/src/main/java/de/lemke/oneurl/domain/model/ProviderName.kt` implementing `ShortURLProvider`.
2. Implement `getCreateRequest()` using Volley and send each request through the `queue` parameter — follow the pattern in `Dagd.kt`
   (parse error body strings for specific `GenerateURLError`
   subtypes). A provider maps a 2xx body that is not a JSON object to `ServiceTemporarilyUnavailable(baseURL)`: the `ParseError` of a
   `JsonObjectRequest`, or the `JSONException` from `JSONObject(response)` in a `StringRequest` listener.
3. Add to the `provider` list in `ShortURLProviderCompanion` in `ShortURLProvider.kt`.
4. If the provider supports aliases, implement `AliasConfig`.

## Static Analysis

Three tools run as part of `./gradlew build`:

- **Spotless** — enforces formatting via ktlint (sole ktlint driver; Detekt has no ktlint wrapper). Fix violations with
  `./gradlew spotlessApply`.
- **Detekt** — static analysis; config at `config/detekt/detekt.yml`. `autoCorrect = false` — fixes are manual.
- **Konsist** — architecture rules in `app/src/test/java/de/lemke/oneurl/ArchitectureTest.kt`. Enforces `data/domain/ui` layering
  (`data` may depend on `domain.model`'s shared value types, never on use cases). ViewModel files expose state only, without
  `Channel` or `SharedFlow`. Runs as part of `./gradlew test`.
  `CodingConventionsTest.kt` also enforces the common-utils launch latch through `assertLaunchLatchConventions()` from the
  common-utils testFixtures: it bans raw activity launches and result registration by name, and a `show`/`showNow` call whose
  receiver is not `Snackbar`, `Toast`, `PopupMenu`, `TipPopup` or `AddFab`. Launch through `singleLaunchActivity`,
  `transformToActivity` or `registerForSingleLaunchResult`; wrap taps in the input helpers (`onSingleLaunchClick`,
  `singleLaunchMenuItem`, `onSingleLaunchItemSelected`, `singleLaunch`); show dialogs with `showOnce(tag)`.
  The Konsist rules come from the common-utils testFixtures: `ArchitectureTest.kt` calls `assertArchitecture`, and
  `CodingConventionsTest.kt` also calls `assertClassBodyOrder`, `assertNoCommonUtilsTypeExtensions` and
  `assertHiltTestRuleConventions`. The last requires every `@HiltAndroidTest` to declare exactly one
  `@get:Rule(order = 0) val hiltRule = HiltTestRule(this)` (common-utils testFixtures), ordered before every other rule,
  and no raw `HiltAndroidRule`. `HiltTestRule` runs `HiltAndroidRule`, exposes `inject()`, and closes every resource
  registered with `closeAfterTest`, such as each in-memory `AppDatabase` the test built.

**Dependency analysis** — `./gradlew buildHealth` runs in the `check-formatting` CI job. It fails on unused
dependencies, incorrect configurations and unused annotation processors; other advice warns. The `dependencyAnalysis {}`
block in the root `build.gradle.kts` holds the facade bundles and the narrow guava exclude. Each bundle names its primary and only the transitive
internals the report lists, so it never masks a declared artifact. Report:
`build/reports/dependency-analysis/build-health-report.txt`.

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
./gradlew spotlessCheck detekt buildHealth lintDebug testDebugUnitTest koverVerifyDebug koverHtmlReportDebug verifyRoborazziDebug pixel9Api35DebugAndroidTest assembleRelease
```

If `spotlessCheck` fails, fix with `./gradlew spotlessApply` then re-run. If a screenshot golden mismatch is expected
(intentional UI change), re-record it with `./gradlew recordRoborazziDebug -Proborazzi.record=true`.
