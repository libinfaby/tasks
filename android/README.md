# Tasks for Android

Native client (Kotlin + Jetpack Compose) for the same Cloudflare Worker API and D1 database as the web app.
Its main job is reliable reminders: each open task with a reminder gets an exact on-device alarm.

## Build & install

Requires the Android SDK (`local.properties` → `sdk.dir`) and Android Studio's JDK.

```bash
export JAVA_HOME=/snap/android-studio/current/jbr
./gradlew :app:testDebugUnitTest      # recurrence rules (same cases as worker/test)
./gradlew :app:assembleDebug          # app-debug.apk, id dev.libinfaby.tasks.debug
./gradlew :app:assembleRelease        # R8-optimised; signed with keystore.properties if present, else the debug key
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug builds may use plain HTTP to a local `wrangler dev` (`http://10.0.2.2:8787/` from the emulator — set it under
**Advanced** on the sign-in screen). Release builds are HTTPS-only.

For a stable release signature, add `keystore.properties` (git-ignored) with `storeFile`, `storePassword`,
`keyAlias`, `keyPassword`.

## How it works

| Area | Where |
|---|---|
| API client (every worker route) | `data/api/` |
| Room cache of open tasks, tag types, groups | `data/db/`, `data/repo/TasksRepository.kt` |
| Encrypted token (Android Keystore) + settings | `data/settings/` |
| Exact alarms, reconcile on sync, re-arm on boot/time change | `reminders/` |
| Recurring reminders (mirror of `worker/src/utils/recurrence.ts`) | `domain/Recurrence.kt` |
| 15-minute background sync + token refresh | `sync/SyncWorker.kt` |
| Home-screen widget (today + overdue, tap to complete) | `widget/` |
| Theme = web design tokens (`frontend/css/tokens.md`) | `ui/theme/` |

- **Reads** come from the cache (works offline); **completed tasks and searches** query the server, like the web app.
- **Writes** go straight to the API (no offline queue yet), then the cache refreshes.
- **Reminders set on the web** reach the phone on the next sync: app open, pull-to-refresh, or the 15-minute job.
  A reminder set less than ~15 minutes ahead may miss the phone; the web push still covers it.
- **Repeating reminders**: when one fires, the phone arms the next occurrence itself; the worker's cron advances the
  server copy too, and both compute the same time.
- Uses `USE_EXACT_ALARM` (sideloaded app, so Play's restriction doesn't apply). If notifications are also enabled in
  the phone's browser, turn those off to avoid duplicates.
