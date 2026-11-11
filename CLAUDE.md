# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal (single-user, sideloaded) Android **monthly money tracker**: Kotlin + Jetpack Compose + Room, local-only storage with Google Drive backup. Built for the repo owner's own use, not for Play Store distribution.

**The owner's workflow drives the design:** Canara Bank SMS are recorded automatically with only the bank's facts (amount, direction, payee, balance). The owner opens the app daily and reviews the new ones, adding a **category** and **what it was for**. The app's job is to show, per month, how much was spent and received, and the account's current balance.

The project is split into two phases. **Phase 1 (done): foundations** — a runnable, themed, navigable app shell with a real database underneath. **Phase 2 (in progress): features**, built one milestone at a time; each milestone builds, passes tests and is committed before the next. Don't build Phase 2 items beyond the current milestone unless asked.

## Current status

Builds, `testDebugUnitTest` passes (16 `MoneyTest` + 9 `CanaraSmsParserTest` + 7 `BackupFilesTest`), and `lintDebug` has 0 errors. Build failures are regressions.

**On-device so far** (owner's phone: vivo V2158, Android 14, adb serial `10BD1P092J0015L`): the app installs and launches without crashing, and the existing database migrated through v3 to v4 (header `user_version` read back each time). Everything else in the on-device list at the bottom is unverified; the owner plans to test it all at the end. The USB connection to this phone drops occasionally: if `installDebug` fails with "Unable to open sync connection", re-check `adb devices` and use `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The Canara SMS parser is built against **real messages from the owner's phone** (5 transaction formats + 3 non-transaction ones, listed in the `CanaraSmsParser` KDoc). Test bodies keep the real wording but have names, UPI refs and account digits replaced — **never commit real SMS text**, since the repo is on GitHub. When a real SMS parses wrongly, add it (anonymised) to the test first.

Known gap: in June 2026, Canara sometimes sent **two SMS for one UPI payment** (a "paid thru … to NAME" message plus a generic "An amount of INR … DEBITED" alert, same amount, same day). They have different bodies, so the hash dedupe doesn't catch them, and the payment is recorded twice. Later messages ("Acct … Dr. INR …") are one SMS per payment. Not handled automatically yet; the owner deletes the duplicate by hand.

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
- **`data/backup`** — Google Drive backup. `DriveAuthorizer` (Play services `AuthorizationClient`, scope `drive.file` only) → `BackupManager` (orchestrates) → `DriveClient` (five Drive v3 REST calls over `HttpURLConnection`, no Google API client lib) + `LocalDatabaseBackup` (snapshot/validate/replace). `BackupFiles` holds the pure, JVM-tested rules (naming, pruning, multipart body). `AutoBackupWorker` is a daily WorkManager job using a Hilt `@EntryPoint` (no hilt-work). Settings/last-result live in `BackupPreferences` (SharedPreferences).
- **`di`** — `DatabaseModule` (Room + DAO providers), `RepositoryModule` (`@Binds` interface → implementation).
- **`ui`** — `theme/`, `navigation/`, `components/`, `screens/<screen>/`. `components/` holds:
  - `TransactionRow` (category avatar, long-press)
  - `CategoryVisuals` (`iconKey` → icon, `CategoryAvatar`)
  - `AnimatedMoney` (count-up text)
  - `MonthSelector` and `SelectedMonth`, a `@Singleton` holding the month being viewed so Home and Transactions stay in sync

  Three bottom tabs: Home, Transactions, Settings. ViewModels expose immutable state via `StateFlow`; Composables take state as parameters. Screens:
  - **Home:** month switcher, spent/received/net for the month, current bank balance, "N to review" banner, 5 most recent.
  - **Transactions:** month switcher, month in/out totals, filters All / Money out / Money in / To review, grouped by day. Long-press starts multi-select (tap toggles, Select all, Delete with confirmation; Back exits). Selection is cleared on month or filter change and is always intersected with the visible rows, so Delete can't hit hidden ones.
  - **Edit:** add/edit/delete. Route `transaction/edit?id=&review=` (`id=-1` means new). In review mode it shows "Save & next" and "Skip" and steps through the month's uncategorised transactions, each replacing the previous one in the back stack.
  - **Settings:** SMS permission, inbox import, Drive backup.


### Invariants — do not violate these

**Money is always a `Long` count of minor units (paise).** Never `Double`, never `Float`. Binary floating point cannot represent decimal fractions exactly, so totals drift and stop reconciling, and there is no way to repair the stored history afterwards. All display goes through `formatMoney()` and all user input through `rupeesToMinor()` in `core/util/Money.kt`, so rounding happens in exactly one place. `rupeesToMinor()` returns `null` on invalid input rather than `0` — a silent zero would record a free transaction.

**`TransactionEntity.amountMinor` is unsigned in meaning**; direction is carried by the `direction` column (`DEBIT`/`CREDIT`), not by the sign. `rupeesToMinor()` accepts negatives, so every entry point must reject `<= 0` itself (the edit ViewModel and the SMS parser both do).

**Re-importing backfills, never overwrites.** When an SMS is already recorded, `SmsImporter` calls `fillMissingCapturedDetails`, which only sets `counterparty`/`balanceAfterMinor` where they're NULL (`COALESCE`). This is how rows captured before v3 get their balance: run Import past SMS again.

**Captured transactions are deduplicated by `externalId`** (unique index; SHA-256 of the SMS body, `sms:` prefix). The same SMS can arrive live and again via inbox import; `addIfNew` makes the second a no-op. Editing must preserve `source`/`externalId` (`copy()` on the loaded transaction), or the edited SMS would be re-imported as a duplicate. Multipart SMS bodies are joined before hashing so they match the inbox's stored body.

**Backups are append-only and restore is validate-first.** Each backup is a new timestamped file in the Drive folder "Expense Tracker Backups"; the newest 10 are kept. Never switch to overwriting one file — a backup taken after something went wrong would destroy the last good copy. Snapshots go through SQLite (`VACUUM INTO` on API 30+, checkpoint + copy under a write transaction below), never a raw copy of the `.db` file, because Room's WAL may hold recent writes. Restore downloads → `validate()` (integrity, has `transactions`, `user_version` between 1 and `ExpenseDatabase.VERSION`) → saves current data to `filesDir/pre-restore-backup.db` → closes Room → swaps the file → restarts the process. Room can't downgrade, so a backup from a newer schema must be rejected, not opened.

**Authorisation needs UI only the first time.** `DriveAuthorizer.authorize()` returns a token silently once granted, which is what the background worker relies on; if it returns `NeedsConsent`, the worker records an error rather than prompting.

**`note` is the user's, `counterparty` is the bank's.** `note` = "what was it for", written only by the user on review. `counterparty` = who was paid or who paid, and `balanceAfterMinor` = the balance the bank reported; both come from the SMS. The SMS importer leaves `note` empty. Don't put SMS text into `note` again: v3's migration (`V3MovePayeeOutOfNote`) exists to undo exactly that.

**Deleted SMS transactions stay deleted.** Every transaction delete goes through `TransactionDao.deleteRemembering`, which first copies the rows' `externalId`s into `ignored_external_ids` (in the same transaction), and `addIfNew` refuses ignored ids. Deleting with a plain `@Delete` or `DELETE` query would let the next inbox import resurrect the rows. Caveat: deleting an account still cascades to its transactions *without* tombstones; there's no account-delete UI yet, but route it through `deleteRemembering` when one is added.

**"Needs review" means `categoryId == null`** (`Transaction.needsReview`), whatever the source. The review queue (`nextNeedingReview`) is scoped to one month and walks forward in date order, so a skipped transaction isn't served again in the same pass.

**Current balance = the bank's own number**, taken from the most recent transaction on that account that carried one (`observeLatestBalances`). It isn't computed by summing transactions, because the app has no opening balance and misses non-SMS movements, so a computed figure would drift. It's shown with an "as of" time.

**Animated money still lands exactly.** `AnimatedMoney` animates a 0..1 progress value and interpolates in `Long` paise; its final frame shows the target `Long` itself. Never animate money through a `Float` (e.g. `animateFloatAsState` on the amount): a `Float` can't represent amounts above about ₹1.67 lakh in paise exactly.

**UI style:** Material 3 with dynamic colour. Corners come from the custom `Shapes` in `Theme.kt` (`extraLarge` 28dp for cards). The Home hero card is a primary→tertiary gradient with `onPrimary` text: the two are the same tone in M3, so that pairing stays readable under any dynamic scheme. There's no red/green on the hero card, so the +/- signs carry direction there. Use `LocalConfiguration.current.locales[0]`, not `Locale.getDefault()`, inside composables (lint `NonObservableLocale` is an error).

**SMS parsing is conservative.** Anything not clearly describing money that already moved (OTP, "will be debited", requests, failures) returns `null`. A missed SMS can be typed in by hand; a phantom transaction silently corrupts totals.

**Enums are persisted by `name`, not ordinal** (see `Mappers.kt`), so reordering an enum can't silently reinterpret existing rows. `enumValueOrDefault` handles unknown values.

**The UI never imports Room.** Screens and ViewModels depend only on the repository interfaces. This is the entire mechanism by which cloud sync can be added later — a new implementation plus a changed `@Binds` in `di/RepositoryModule.kt`, with no screen touched. Adding a Room import to `ui/` defeats it.

**No `fallbackToDestructiveMigration`.** Losing real expense history to a schema change is unacceptable. Write proper migrations. `exportSchema = true` and `app/schemas/` is committed so migrations are diffable rather than guesswork. Current schema is **v4** (`ExpenseDatabase.VERSION`). 1→2 (`source`, `externalId`) is a plain `@AutoMigration`; 2→3 (`counterparty`, `balanceAfterMinor`) is an `@AutoMigration` with an `AutoMigrationSpec` that moves SMS payees out of `note`; 3→4 adds the `ignored_external_ids` table (plain `@AutoMigration`). Purely additive changes can keep using `AutoMigration` (new NOT NULL columns need a `@ColumnInfo(defaultValue = ...)`); renames/drops need an `AutoMigrationSpec` or a manual `Migration`. Bump `version` and add the migration in `ExpenseDatabase` for every entity change.

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

**Done:** manual entry (add/edit/delete), transactions list, Canara Bank SMS capture (live + inbox back-fill), Google Drive backup/restore with optional daily auto-backup, monthly view (month switcher, month totals), daily review flow, and current balance from SMS.

**Owner's decisions so far:** bank is **Canara Bank only** (SMS debit/credit). Cloud sync is a **Google Drive backup file** (built). The owner reports the Google Cloud Console setup is done, but it hasn't been confirmed by a successful backup on the phone yet. What it requires: Drive API enabled, OAuth consent screen with the `drive.file` scope, and an **Android** OAuth client per build — package `com.mdibrahim.expensetracker.debug` + the debug keystore SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`), plus a separate client for the release package once a release keystore exists. No client ID goes in the code. Error code 10 (`DEVELOPER_ERROR`) in the app means the package/SHA-1 doesn't match a client. If the consent screen stays in "Testing", Google may expire the grant after 7 days and auto-backup will stop until the owner taps Back up now; `drive.file` is a non-sensitive scope, so publishing it "In production" needs no verification.

**Owner decided against:** a Reports tab or charts screen (removed, along with `ui/screens/placeholder/`). Don't re-add one unless asked.

**Not started:** UPI app notification capture, budgets and overspend alerts, statement CSV/PDF import, receipt OCR, recurring-transaction detection, app lock/biometrics, home-screen widgets, export to Excel/Sheets. Also not built: managing accounts and categories in the app (they only come from the seeder and SMS import).

## On-device verification

1. `./gradlew :app:testDebugUnitTest` — passes.
2. `./gradlew :app:installDebug` — app appears in the launcher.
3. All three bottom-nav tabs switch without crashing. (Home and Transactions have been screenshot-checked in dark mode on the owner's phone.)
4. The seeded ₹450 transaction is visible on Home; `adb shell am force-stop ...` then reopen, and it is still there. This proves Room writes to disk. An existing v1 install must also open without crashing, which proves the 1→2 migration.
5. Toggle system dark mode — the app follows with no unreadable text.
6. Add a transaction with +, edit it, delete it; the Home totals follow.
7. Settings → Allow SMS access → Import past SMS: Canara debits/credits appear, tagged "SMS". Run the import twice — the count added the second time must be 0 (dedupe).
8. Receive a real Canara SMS with the app closed — it appears without opening the app.
9. Settings → Back up now: consent screen appears the first time, then "Backed up"; the file shows in Drive under "Expense Tracker Backups". A second tap needs no consent.
10. Add a transaction, then Restore the earlier backup: the app restarts and the new transaction is gone (and `pre-restore-backup.db` exists in the app's files dir).
11. Turn on "Back up daily". To test without waiting a day: `adb shell cmd jobscheduler run -f com.mdibrahim.expensetracker.debug <jobId>` (find the id with `adb shell dumpsys jobscheduler | grep expensetracker`), then check "Last backup" updates.
12. Month switcher: ‹ › on Home moves the month and Transactions shows the same month; › stops at the current month.
13. After an inbox import, Home shows "N transactions to review"; Review → Save & next walks through them and returns Home at the end; the count drops. Skip moves on without saving.
14. Home "Current balance" equals the Bal figure in the newest Canara SMS.
15. Transactions: long-press a row, tap two more, Delete → confirm; all three disappear and the month totals drop. Delete an SMS one, run Import past SMS again; it must not come back.
