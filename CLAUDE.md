# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal (single-user, sideloaded) Android expense tracker: Kotlin + Jetpack Compose + Room, local-only storage. Built for the repo owner's own use, not for Play Store distribution.

The project is deliberately split into two phases. **Phase 1 (current): foundations only** — a runnable, themed, navigable app shell with a real database underneath and no product features. **Phase 2 (not started): behaviour and features.** Do not add features from the Phase 2 list below unless asked; the foundation is intentionally feature-free so it doesn't get bent around guesses.

## Current status

The scaffold **compiles and `./gradlew :app:testDebugUnitTest` passes** (16 money tests), and that state is committed — it is the known-good baseline. Build failures from here on are regressions.

Not yet verified: on-device steps 2–5 of the verification list at the bottom (install, tab switching, seed persistence across force-stop, dark mode).

Toolchain: AGP 8.13.2 / Kotlin 2.2.10 / KSP `2.2.10-2.0.2` / Gradle 8.14.3, `compileSdk`/`targetSdk` 36.

**Version ceiling while on AGP 8:** Hilt ≥ 2.59, core-ktx ≥ 1.19, lifecycle ≥ 2.11 and hilt-navigation-compose ≥ 1.4 all require AGP 9.1+ and compileSdk 37, and fail the build (`checkDebugAarMetadata`). They are held at 2.57.2 / 1.17.0 / 2.9.4 / 1.3.0. Don't bump any of them except as part of a deliberate AGP 9 migration, which also needs the `android-37` SDK platform installed.

Known warnings (non-blocking): `hiltViewModel()` has moved to `androidx.hilt.lifecycle.viewmodel.compose`; `@StringRes` on a constructor property in `Destinations.kt` triggers the KT-73255 annotation-target warning.

## Commands

All commands are run from the repo root.

```sh
./gradlew :app:assembleDebug          # build
./gradlew :app:installDebug           # build + install to connected device
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lint                   # Android lint
./gradlew --stop                      # kill daemons after dependency/version changes
```

Run a single test class or method:

```sh
./gradlew :app:testDebugUnitTest --tests "com.mdibrahim.expensetracker.core.util.MoneyTest"
./gradlew :app:testDebugUnitTest --tests "*MoneyTest.whole rupees convert to paise"
```

Device workflow (physical phone over USB is the intended target; `ANDROID_HOME` is `~/Library/Android/sdk`):

```sh
adb devices                                                    # must show 'device', not 'unauthorized'
adb logcat --pid=$(adb shell pidof com.mdibrahim.expensetracker.debug)
adb shell am force-stop com.mdibrahim.expensetracker.debug     # for persistence checks 
```

Note the debug build carries `applicationIdSuffix = ".debug"`, so the installed package is `com.mdibrahim.expensetracker.debug`.

## Architecture

Single Gradle module (`:app`) with package-based layering — chosen over multi-module to keep build times low for a solo developer, while keeping the package boundaries clean enough to split later.

Dependencies point strictly inward: `ui` → `data/repository` (interfaces) → `core/database` → `core/model`.

- **`core/model`** — domain models as plain Kotlin. No Room, no Compose annotations.
- **`core/database`** — Room entities, DAOs, `ExpenseDatabase`, and `Mappers.kt`. Entities are separate types from domain models; `Mappers.kt` converts between them.
- **`core/util`** — `Money.kt`, the money formatting/parsing boundary.
- **`data/repository`** — `Repositories.kt` holds the interfaces; `RoomRepositories.kt` holds the Room-backed implementations. DAO `Flow`s are mapped to domain models here.
- **`data/seed`** — `DatabaseSeeder`, idempotent first-launch data.
- **`di`** — `DatabaseModule` (Room + DAO providers), `RepositoryModule` (`@Binds` interface → implementation).
- **`ui`** — `theme/`, `navigation/`, `screens/<screen>/`. ViewModels expose immutable state via `StateFlow`; Composables are stateless and take state as parameters.

### Invariants — do not violate these

**Money is always a `Long` count of minor units (paise).** Never `Double`, never `Float`. Binary floating point cannot represent decimal fractions exactly, so totals drift and stop reconciling, and there is no way to repair the stored history afterwards. All display goes through `formatMoney()` and all user input through `rupeesToMinor()` in `core/util/Money.kt`, so rounding happens in exactly one place. `rupeesToMinor()` returns `null` on invalid input rather than `0` — a silent zero would record a free transaction.

**`TransactionEntity.amountMinor` is unsigned in meaning**; direction is carried by the `direction` column (`DEBIT`/`CREDIT`), not by the sign.

**Enums are persisted by `name`, not ordinal** (see `Mappers.kt`), so reordering an enum can't silently reinterpret existing rows. `enumValueOrDefault` handles unknown values.

**The UI never imports Room.** Screens and ViewModels depend only on the repository interfaces. This is the entire mechanism by which cloud sync can be added later — a new implementation plus a changed `@Binds` in `di/RepositoryModule.kt`, with no screen touched. Adding a Room import to `ui/` defeats it.

**No `fallbackToDestructiveMigration`.** Losing real expense history to a schema change is unacceptable. Write proper migrations. `exportSchema = true` and `app/schemas/` is committed so migrations are diffable rather than guesswork; the schema JSON appears there after the first successful build.

**Dates** are stored as `occurredAtEpochMillis` (`Long`) and converted to `java.time.Instant` in the mappers — there are no Room `TypeConverter`s, by choice. `minSdk = 26` means `java.time` is available natively without desugaring.

**Colour never carries meaning alone.** Debit/credit rows are prefixed with `-`/`+` as well as coloured, so they stay readable without colour vision. Money direction colours live in `LocalMoneyColors` (a `CompositionLocal` in `ui/theme/Theme.kt`), not hardcoded in screens, because Material's colour scheme has no slot for them. Dynamic colour is on for Android 12+, so the Material scheme is not fixed — don't assume specific primary/secondary values.

### Version management

`gradle/libs.versions.toml` is the single source of truth for every dependency version. Compose artifacts are versioned by the BOM (`androidx-compose-bom`) and must be declared without explicit versions. KSP's version is pinned to Kotlin's as `<kotlin>-<ksp>` and must be updated in lockstep with the `kotlin` entry.

`buildConfig = true` is set explicitly in `app/build.gradle.kts` — it is off by default in AGP 8 and is required by the `BuildConfig.DEBUG` check that gates seed data in `ExpenseTrackerApp`.

## Working preferences

The repo owner is building this to learn Android development, and is cost-conscious about token usage. Two consequences:

- **Hand back anything manual.** Installs, GUI wizards (Android Studio setup, SDK manager), shell/`~/.zshrc` config, phone settings, and device pairing are the owner's tasks — don't do them, just say what's needed.
- **Ask for the error, not the files.** When a build fails, request only the error text; the code is already available to read.

## Phase 2 — deferred, do not build unprompted

Bank/UPI SMS and notification capture, manual-entry UX, budgets and overspend alerts, charts and reports, statement CSV/PDF import, receipt OCR, recurring-transaction detection, cloud sync, app lock/biometrics, home-screen widgets, export to Excel/Sheets.

The four bottom-nav destinations exist as placeholders (`ui/screens/placeholder/`); only Home is real, and it exists to prove Room persistence and theming work end to end.

## Verification that the foundation works

1. `./gradlew :app:testDebugUnitTest` — money conversion tests pass.
2. `./gradlew :app:installDebug` — app appears in the launcher.
3. All four bottom-nav tabs switch without crashing.
4. The seeded ₹450 transaction is visible on Home; `adb shell am force-stop ...` then reopen, and it is still there. This is what proves Room writes to disk rather than holding state in memory.
5. Toggle system dark mode — the app follows with no unreadable text.
