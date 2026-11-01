# Dark's Ride

A free, personal Android app that turns a spare phone on your motorcycle into a riding screen for the phone in your pocket. No accounts, no server, nothing uploaded: the two phones talk to each other over Bluetooth.

- **Pocket Sherpa** — your main phone (the one with the SIM). It stays in your pocket and carries music, calls, messages and internet up to the bike.
- **Handlebar HQ** — the phone on the bike. It runs Google Maps and shows everything else on top of it or beside it.

Built and ridden on a Samsung Galaxy S25 Ultra (Pocket Sherpa) and an LG Wing (Handlebar HQ) on a Royal Enfield Himalayan 450. Works with any two Android 12+ phones.

> Latest release: **v1.0.4** — see the [Releases](../../releases) page.
> What changed and when: [docs/CHANGELOG.md](docs/CHANGELOG.md) · How it works inside: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · Building and releasing: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)

---

## Features

### On the bike (Handlebar HQ)
- **Music** — sharp album cover on a glow in the song's own colours, title and artist, a live progress wave with running time, big previous / play / next, segmented volume. Works with YouTube Music, Spotify and any app that shows a media notification on the main phone.
- **Full-screen music** — beside Google Maps or on the LG Wing's small screen, music fills the screen while it plays. A call or a new message slides in over it.
- **Calls** — incoming call card with huge Decline / Answer buttons, a running call timer and End call. Missed calls appear as messages.
- **Messages** — WhatsApp and SMS (and other apps if you choose). A new message rises to the top for your spotlight time (5–60 s, default 10). Dismissing it here clears it on the main phone too.
- **Navigation** — places sent from the main phone start Google Maps in two-wheeler mode. Shows the destination name, the road ("via NH 44"), where you started and when it was sent, plus a recent list (tap to go again).
- **Floating cards over Google Maps** — when Dark's Ride is in the background, calls and messages float over Maps. When the app is open they stay inside it.
- **Live card order** — your own order of cards (Settings → Card order). A call goes on top until it ends, a new message for its spotlight time, music while it plays; then each returns to its place. Nothing to scroll for while riding.
- **Layouts for every screen** — portrait list; landscape with the top card big on the left and the rest on the right; a compact full-width layout for split screen beside Maps and for the LG Wing's swivel screen.
- **Bike internet without a hotspot** — automatic Bluetooth tethering from the main phone (see below).

### In your pocket (Pocket Sherpa)
- **Ride tab** — Plan a ride: the last place sent with full details, recently sent places (tap to send again), bike internet status.
- **Send to bike** — in Google Maps, Share → Send to bike. Works for places and whole routes; a place shared while the bike is away is sent when it connects.
- **Garage** — fuel log with mileage, cost per km and range, expenses, service / document reminders with a "Due soon" card.
- **Documents** — licence, RC, insurance, PUC: scanned or imported, encrypted on this phone only, behind fingerprint / PIN. Shortcuts (also behind fingerprint / PIN) to Samsung Pass / Wallet IDs, DigiLocker and mParivahan. Password-protected backup of Garage and Documents.

### Everywhere
- **Two styles:** **Glass** (see-through cards, stars at night) and **Soft** (neumorphism: raised, pillowy cards). Settings → Appearance.
- **Four ride colours:** Ember (orange), Redline (red, kind to night eyes), Glacier (ice blue), Volt (hi-vis lime).
- **Light / dark automatically** from the light sensor, like the bike's dash, or fixed.
- Cards start closed every time you open the app; a small dot marks the ones that need a look.
- Activity log on each phone, kept permanently, for troubleshooting.

---

## What you need

- Two Android phones with Android 12 or newer.
- The two phones paired once in Bluetooth settings.

## Install

1. On each phone, download `DarksRide-v….apk` from the [Releases](../../releases) page.
2. Open it; if Android asks, allow installing apps from your browser or file manager.
3. Open Dark's Ride and choose the phone's role: **Pocket Sherpa** or **Handlebar HQ**.

Updating: install the new APK over the old one; your settings stay.

## First setup

Each phone has a **Setup** checklist (Settings, and on the Ride page while something is missing). Tap any step without a tick and it explains what to allow and opens the right Android screen.

**Pocket Sherpa:** Bluetooth access · pick the bike phone · background running · music and messages (notification access) · calls · optional: answer WhatsApp calls, bike internet, auto-tap helper.

**Handlebar HQ:** Bluetooth access · background running · cards over Google Maps · optional: internet from your Pocket Sherpa, auto-tap helper.

Bike internet and the auto-tap helper are set up from **Settings → Bike internet** on each phone.

**Greyed-out switch ("Restricted setting")** for notification access, display over other apps or the auto-tap helper: App info → ⋮ → **Allow restricted settings**, then try again. This happens because the app is installed from a file, not the Play Store.

**Xiaomi, Redmi, Poco, Oppo, Realme, Vivo, OnePlus…** stop background apps: App info → Battery → **No restrictions**, and turn on **Autostart**.

## Bike internet (Bluetooth tethering)

The bike phone gets internet over Bluetooth from the main phone, so the Wi-Fi hotspot can stay off (less battery).

Android doesn't let ordinary apps switch tethering on, so Dark's Ride uses an opt-in **auto-tap helper** (an accessibility service that only acts inside the Settings app, only when Dark's Ride asks):
1. When the phones link up, the bike checks its internet. If there is none, it asks the main phone.
2. The main phone makes sure **Bluetooth tethering** is on (taps the switch itself if the phone is unlocked; otherwise shows a notification that opens it).
3. The bike connects **Internet access** to the main phone (opens Bluetooth settings for a second, taps it, goes back to Maps).

It retries at most 3 times in a row, so Settings never keeps popping up while you ride. The **Bike internet** card shows the state and has **Connect now**.

Bluetooth is slower than a hotspot (around 1–2 Mbps): download **offline maps** in Google Maps on the bike phone and switch off Play Store auto-updates there.

## Riding beside Google Maps (split screen)

Google Maps can't be drawn inside another app, so Dark's Ride uses Android's split screen: Maps in one half, Dark's Ride in the other.

Bike phone → Settings → **Side by side with Maps** → **Open Maps beside**. If Maps opens full screen instead: Recents → tap the Dark's Ride icon → **Split screen** → pick Maps. In split screen, destinations open in the Maps half and music goes full screen while it plays (switch in the same card).

## Turning it off

Use the **Ride mode** switch (or **Turn off** on the notification). Everything in the background stops and stays off after restarts until you switch it on again; the rest of the app (Garage, Documents…) still works.

## Privacy

- Notifications, call info and music info go only from one of your phones to the other, over Bluetooth.
- Documents are encrypted with a key kept in the phone's secure hardware (Android Keystore) and never leave the phone, except in a backup file you create with your own password.
- Internet is used only to open a Google Maps link you shared (to find the exact place) and, for the bike phone, through your own tethering.
- No analytics, no ads, no accounts.

## Not done yet / ideas

- Answering WhatsApp calls from the bike (Android blocks starting WhatsApp's call screen from the background in some cases).
- Trip meters from GPS, odometer photo reading, reminder notifications for Garage.
- A Play Store edition (would need the auto-tap helper removed and some permission paperwork; see docs/DEVELOPMENT.md).

## License

Personal, non-commercial project. Share it with riding friends; not for sale.
