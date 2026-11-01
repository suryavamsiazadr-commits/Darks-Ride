# Building, testing and releasing

## Build on your computer

1. Open the project folder in **Android Studio** (it uses the Gradle wrapper; JDK 25 toolchain, AGP 9, Kotlin 2.2).
2. Connect a phone with USB debugging on (LG phones need the LG USB driver on Windows).
3. Choose the **release** build variant (much smoother than debug) and press **Run ▶**. Do it for both phones.

Get the latest code:

```
git fetch origin
git reset --hard origin/main
```

## Signing

Debug and release builds share one key, `app/darksride.jks` (password `darksride`), committed on purpose so builds from Android Studio and from GitHub install over each other. This is fine for a private, personal app. If the repository is ever made public, or for a Play Store edition, create a new private key and keep it out of the repository.

## Automatic builds (GitHub Actions)

`.github/workflows/build.yml` runs on every push to `main`:

- **build** — `assembleDebug assembleRelease`; Kotlin errors appear as annotations on the run. The APKs are attached to the run.
- **screens** — renders the ride screens with Robolectric (`app/src/test/.../ScreenshotTest.kt`) at real sizes: LG Wing portrait 411×937 dp and landscape 937×411, the Wing's small screen ~472×411, split screen beside Maps (half ~465×411, third ~300×411), the S25 Ultra 412×891; Glass and Soft, light and dark, with music, routes, messages and calls. The PNGs are pushed to the **`screens`** branch (`git fetch origin screens`), so layouts can be checked without a phone.

## Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then push a commit to `main` whose message contains **`[release]`**. The build publishes a GitHub Release named after `versionName` (e.g. `v1.1`) with `DarksRide-v1.1.apk` attached. Pushing a tag `v…` also publishes a release.

## Play Store (not planned for now)

Possible, but it would need a separate edition:

- no auto-tap helper (Google allows the accessibility API only for accessibility tools),
- a declaration or removal of the full-screen call alert (restricted to calling / alarm apps) and of the direct battery-optimisation request,
- foreground-service declaration, privacy policy, Data safety form, in-app explanations before sensitive permissions,
- a new private upload key with Play App Signing,
- new personal developer accounts must run a closed test with 12 testers for 14 days before production.

## Useful places when something goes wrong

- **Activity** card in Settings on each phone: every link, music, call, navigation and tethering step, with date and time, kept permanently.
- **Setup** checklist: anything not allowed yet.
- The `screens` branch for how the current build looks.
