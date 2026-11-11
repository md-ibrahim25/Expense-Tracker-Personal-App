# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal (single-user, sideloaded) Android expense tracker: Kotlin + Jetpack Compose + Room, local-only storage. Built for the repo owner's own use, not for Play Store distribution.

The project is split into two phases. **Phase 1 (done): foundations** — a runnable, themed, navigable app shell with a real database underneath. **Phase 2 (in progress): features**, built one milestone at a time; each milestone builds, passes tests and is committed before the next. Don't build Phase 2 items beyond the current milestone unless asked.

## Current status

Builds, `testDebugUnitTest` passes (16 `MoneyTest` + 8 `CanaraSmsParserTest`), and `lintDebug` has 0 errors. Build failures are regressions.

**Nothing has been run on a phone yet** — all on-device steps in the verification list at the bottom are unverified.

The Canara SMS parser was written from Canara's known wording, **not from real messages**. Its test bodies are invented. Real (account-masked) SMS samples from the owner's phone are the outstanding input; add them to `CanaraSmsParserTest` and fix the parser against them.

Toolchain: AGP 8.13.2 / Kotlin 2.2.10 / KSP `2.2.10-2.0.2` / Gradle 8.14.3, `compileSdk`/`targetSdk` 36.

**Version ceiling while on AGP 8:** Hilt ≥ 2.59, core-ktx ≥ 1.19, lifecycle ≥ 2.11 and hilt-navigation-compose ≥ 1.4 all require AGP 9.1+ and compileSdk 37, and fail the build (`checkDebugAarMetadata`). They are held at 2.57.2 / 1.17.0 / 2.9.4 / 1.3.0. Don't bump any of them except as part of a deliberate AGP 9 migration, which also needs the `android-37` SDK platform installed.

Use `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` — the `androidx.hilt.navigation.compose` one is deprecated. `-Xannotation-default-target=param-property` is set in `app/build.gradle.kts` (KT-73255), so `@ApplicationContext` / `@StringRes` on constructor properties don't warn.

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
- **`data/seed`** — `DatabaseSeeder`, idempotent first-launch data: default categories in every build, demo account + ₹450 transaction in debug only.
- **`data/sms`** — bank SMS capture. `CanaraSmsParser` (pure Kotlin, JVM-tested) → `SmsImporter` (maps to a `Transaction`, one app account per `Canara Bank ••<last digits>`, and back-fills from the inbox) → `SmsReceiver` (manifest `BroadcastReceiver` for live SMS). The receiver gets its dependencies through a Hilt `@EntryPoint`, not `@AndroidEntryPoint`: with the Hilt Gradle plugin an `@AndroidEntryPoint` receiver must call `super.onReceive()`, which Kotlin rejects because it's abstract.
- **`di`** — `DatabaseModule` (Room + DAO providers), `RepositoryModule` (`@Binds` interface → implementation).
- **`ui`** — `theme/`, `navigation/`, `components/` (shared `TransactionRow`), `screens/<screen>/`. ViewModels expose immutable state via `StateFlow`; Composables take state as parameters. Screens: Home (month totals + recent), Transactions (day-grouped list, in/out filter), Edit (add/edit/delete; route `transaction/edit?id=`, `-1` = new, bottom bar hidden), Settings (SMS permission + inbox import), Reports (still a placeholder).

### Invariants — do not violate these

**Money is always a `Long` count of minor units (paise).** Never `Double`, never `Float`. Binary floating point cannot represent decimal fractions exactly, so totals drift and stop reconciling, and there is no way to repair the stored history afterwards. All display goes through `formatMoney()` and all user input through `rupeesToMinor()` in `core/util/Money.kt`, so rounding happens in exactly one place. `rupeesToMinor()` returns `null` on invalid input rather than `0` — a silent zero would record a free transaction.

**`TransactionEntity.amountMinor` is unsigned in meaning**; direction is carried by the `direction` column (`DEBIT`/`CREDIT`), not by the sign. `rupeesToMinor()` accepts negatives, so every entry point must reject `<= 0` itself (the edit ViewModel and the SMS parser both do).

**Captured transactions are deduplicated by `externalId`** (unique index; SHA-256 of the SMS body, `sms:` prefix). The same SMS can arrive live and again via inbox import; `addIfNew` makes the second a no-op. Editing must preserve `source`/`externalId` (`copy()` on the loaded transaction), or the edited SMS would be re-imported as a duplicate. Multipart SMS bodies are joined before hashing so they match the inbox's stored body.

**SMS parsing is conservative.** Anything not clearly describing money that already moved (OTP, "will be debited", requests, failures) returns `null`. A missed SMS can be typed in by hand; a phantom transaction silently corrupts totals.

**Enums are persisted by `name`, not ordinal** (see `Mappers.kt`), so reordering an enum can't silently reinterpret existing rows. `enumValueOrDefault` handles unknown values.

**The UI never imports Room.** Screens and ViewModels depend only on the repository interfaces. This is the entire mechanism by which cloud sync can be added later — a new implementation plus a changed `@Binds` in `di/RepositoryModule.kt`, with no screen touched. Adding a Room import to `ui/` defeats it.

**No `fallbackToDestructiveMigration`.** Losing real expense history to a schema change is unacceptable. Write proper migrations. `exportSchema = true` and `app/schemas/` is committed so migrations are diffable rather than guesswork. Current schema is **v2**; 1→2 (`transactions.source`, `transactions.externalId`) is an `@AutoMigration`. Purely additive changes can keep using `AutoMigration` (new NOT NULL columns need a `@ColumnInfo(defaultValue = ...)`); renames/drops need an `AutoMigrationSpec` or a manual `Migration`. Bump `version` and add the migration in `ExpenseDatabase` for every entity change.

**Dates** are stored as `occurredAtEpochMillis` (`Long`) and converted to `java.time.Instant` in the mappers — there are no Room `TypeConverter`s, by choice. `minSdk = 26` means `java.time` is available natively without desugaring.

**Colour never carries meaning alone.** Debit/credit rows are prefixed with `-`/`+` as well as coloured, so they stay readable without colour vision. Money direction colours live in `LocalMoneyColors` (a `CompositionLocal` in `ui/theme/Theme.kt`), not hardcoded in screens, because Material's colour scheme has no slot for them. Dynamic colour is on for Android 12+, so the Material scheme is not fixed — don't assume specific primary/secondary values.

### Version management

`gradle/libs.versions.toml` is the single source of truth for every dependency version. Compose artifacts are versioned by the BOM (`androidx-compose-bom`) and must be declared without explicit versions. KSP's version is pinned to Kotlin's as `<kotlin>-<ksp>` and must be updated in lockstep with the `kotlin` entry.

`buildConfig = true` is set explicitly in `app/build.gradle.kts` — it is off by default in AGP 8 and is required by the `BuildConfig.DEBUG` check that gates demo seed data in `ExpenseTrackerApp`.

## Working preferences

The repo owner is building this to learn Android development, and is cost-conscious about token usage. Two consequences:

- **Hand back anything manual.** Installs, GUI wizards (Android Studio setup, SDK manager), shell/`~/.zshrc` config, phone settings, and device pairing are the owner's tasks — don't do them, just say what's needed.
- **Ask for the error, not the files.** When a build fails, request only the error text; the code is already available to read.

## Phase 2 — feature milestones

**Done (milestone 1):** manual entry (add/edit/delete), transactions list, Canara Bank SMS capture (live + inbox back-fill).

**Owner's decisions so far:** bank is **Canara Bank only** (SMS debit/credit). Cloud sync, when it's built, is a **Google Drive backup file** — needs a Google Cloud Console OAuth client set up by the owner first.

**Not started:** UPI app notification capture, budgets and overspend alerts, charts and reports (the Reports tab is still `ui/screens/placeholder/`), statement CSV/PDF import, receipt OCR, recurring-transaction detection, Google Drive backup, app lock/biometrics, home-screen widgets, export to Excel/Sheets. Also not built: managing accounts and categories in the app (they only come from the seeder and SMS import).

## On-device verification

1. `./gradlew :app:testDebugUnitTest` — passes.
2. `./gradlew :app:installDebug` — app appears in the launcher.
3. All four bottom-nav tabs switch without crashing.
4. The seeded ₹450 transaction is visible on Home; `adb shell am force-stop ...` then reopen, and it is still there. This proves Room writes to disk. An existing v1 install must also open without crashing, which proves the 1→2 migration.
5. Toggle system dark mode — the app follows with no unreadable text.
6. Add a transaction with +, edit it, delete it; the Home totals follow.
7. Settings → Allow SMS access → Import past SMS: Canara debits/credits appear, tagged "SMS". Run the import twice — the count added the second time must be 0 (dedupe).
8. Receive a real Canara SMS with the app closed — it appears without opening the app.
