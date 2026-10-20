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
./gradlew :app:bundleRelease          # app-release.aab for Google Play; needs keystore.properties (Play rejects the debug key)
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug builds may use plain HTTP to a local `wrangler dev`: build with
`-Ptasks.apiBaseUrl=http://10.0.2.2:8787/` (the emulator's view of the host). Release builds are HTTPS-only.

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
| Material 3 Expressive theme: wallpaper colours (Grape fallback), Bricolage Grotesque + Figtree, Material Symbols | `ui/theme/`, `res/font/` |
| Expressive pieces: scallop shape, connected lists, wavy ring, confetti, morphing button group | `ui/components/Expressive.kt` |

- **Reads** come from the cache (works offline); **completed tasks and searches** query the server, like the web app.
- **Writes** go straight to the API (no offline queue yet), then the cache refreshes.
- **Reminders set on the web** reach the phone on the next sync: app open, pull-to-refresh, or the 15-minute job.
  A reminder set less than ~15 minutes ahead may miss the phone; the web push still covers it.
- **Repeating reminders**: when one fires, the phone arms the next occurrence itself; the worker's cron advances the
  server copy too, and both compute the same time.
- Uses `SCHEDULE_EXACT_ALARM` (Play only allows `USE_EXACT_ALARM` for alarm clocks and calendars), so turn on
  Settings → Reminders → Exact timing once after installing; until then reminders may be a few minutes late.
  Before each Play upload, bump `versionCode` in `app/build.gradle.kts`. If notifications are also enabled in
  the phone's browser, turn those off to avoid duplicates.
