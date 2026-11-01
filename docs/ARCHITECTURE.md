# How Dark's Ride works

Kotlin, Jetpack Compose, one module (`app`). Package `com.darksoul.darksride`. minSdk 30 (Android 11), target 37.

## The two roles

The same APK runs on both phones; the first screen asks which role this phone has (`AppPrefs.mode`).

| | Pocket Sherpa (`AppMode.MAIN`) | Handlebar HQ (`AppMode.RIDE`) |
|---|---|---|
| Bluetooth | client: connects to the bike | server: waits for the main phone |
| Sends | music, messages, calls, destinations, "net_ready" | button presses, dismissals, internet status |
| Screens | tabs: Ride, Garage, Documents, Settings | ride dashboard, Settings, Wing panel |

## The link (`link/`)

- `LinkService` — foreground service (type `connectedDevice`) holding one Bluetooth RFCOMM socket (UUID `6f1d2c3b-8a4e-4b7f-9c2d-1e5a7b9c3d42`). Reconnects with back-off (15 s, then 60 s after 5 minutes), heartbeat every 10 s, rebuilds the link after 45 s of silence. `START_STICKY`; restarted after boot by `StartupReceiver`.
- Messages are one JSON object per line, field `t` is the type:

| `t` | Direction | Meaning |
|---|---|---|
| `hello`, `ping` | both | who's there; keep-alive |
| `media` | main → bike | now playing: app, title, artist, playing, volume, `pos`/`dur`/`speed`, album art (only when the track changes) |
| `cmd` | bike → main | button: `toggle`, `prev`, `next`, `vol_up`, `vol_down`, `answer`, `reject`, `end` |
| `notif`, `notif_gone` | main → bike | a message arrived / was cleared on the main phone |
| `notif_clear` | bike → main | dismissed on the bike: clear it on the main phone |
| `call` | main → bike | `ringing`, `active`, `ended` (+ `missed`) |
| `nav` | main → bike | destination: name, address, via, from, query, url, lat/lng, sentAt |
| `net`, `net_need`, `net_ready` | both | bike internet status and the tethering hand-shake |

- `LinkState` — live status for the screens, plus the permanent activity log (`activity.log`, newest 500 shown, 5,000 kept).

## Main phone (`phone/`, `media/`, `nav/`)

- `PhoneListenerService` — notification listener: the key to media sessions (`MediaRelay`), WhatsApp / SMS messages (`NotifyBridge`) and call-style notifications (WhatsApp calls → `CallRelay`).
- `MediaRelay` — follows the active `MediaController`, sends `media`, carries out `cmd` (media keys, volume).
- `CallRelay` — phone state via `TelephonyCallback`; answer / end via `TelecomManager`; WhatsApp call buttons via the notification's actions.
- `Destination` + `ShareToBikeActivity` — "Send to bike" in the share menu. Reads place or route text ("From A to B via C"), follows the short link for coordinates and the place name, falls back to the phone's address lookup.

## Bike phone

- `RideMedia`, `RideNotifications`, `RideCall`, `RideNav` — hold what the main phone sent, as `StateFlow`s for the UI.
- `RideOverlay` — floating cards over Google Maps (`TYPE_APPLICATION_OVERLAY`), plain Android views so they work outside the app. Hidden while Dark's Ride is on screen (`AppVisibility`).
- `RideNav` — opens Google Maps navigation (`google.navigation:q=…&mode=l`) on the main display, or in the other half when in split screen (`FLAG_ACTIVITY_LAUNCH_ADJACENT`).
- `panel/` — LG Wing: the ride panel on the second display (`ActivityOptions.setLaunchDisplayId`), always a fresh window, closes itself if it lands on the main display.

## Bike internet (`net/`)

- `BikeNet` — tracks Bluetooth / Wi-Fi internet with `ConnectivityManager` callbacks, runs the hand-shake (`net_need` → main ensures tethering → `net_ready` → bike connects), retries at most 3 times in a row, notifications as the fallback. Tries the hidden PAN `connect` first (most phones refuse it for normal apps).
- `AutoTap` / `AutoTapService` — the opt-in accessibility service. Only looks at `com.android.settings`, only while a job runs: finds "Bluetooth tethering" (main) or the main phone's row → ⚙ → "Internet access" (bike), taps, presses Back only while Settings is in front.

## Screens (`ui/`)

- `MainShell` (main phone tabs), `Screens.LinkScreen` (Ride page / setup), `SettingsUi`.
- `RideDashboard` — the bike phone while linked. Picks `PORTRAIT`, `WIDE` or `COMPACT` from the available size; builds the card list from the rider's order (`AppPrefs.cardOrder`) with live cards lifted to the top (call → new message → playing music); full-screen music (`MusicFullScreen`) with pop-ups over it.
- `MusicUi` — portrait card, `MusicPanel` (fills a given space), full-screen player, live `WaveProgress`.
- `Components` — page background, `rideSurface` / `rideButtonSurface` (Glass or Soft), `neu` (neumorphic double shadow), `CollapsibleCard` (closed on each app start via `CardMemory`, attention dot).
- `theme/` — `ThemeController` (light / dark from the light sensor with hysteresis, style, palette), `Theme` (`GlassColors` per style × light/dark × palette), `Palette` (Ember, Redline, Glacier, Volt).

## Garage and Documents (`garage/`, `docs/`)

- `GarageStore` — `garage.json`: bike, fuel fills, expenses, reminders; `GarageMath` for mileage, cost per km, range.
- `DocVault` — files encrypted with AES-256-GCM, key in Android Keystore (`darksride_vault`); index encrypted, only expiry dates in plain text for reminders. Unlocked by `BiometricPrompt` (fingerprint or screen lock), locked again when the app is left.
- `Backup` — Garage + Documents in one file encrypted with a key derived from your password (PBKDF2), format `DRB1`.

## Settings storage

`AppPrefs` (SharedPreferences `darks_ride`): role, bike phone, ride mode, notification filters, spotlight seconds, card order, full-screen music, peer address / name. `ThemeController` uses the same file for theme choice, style and palette.
