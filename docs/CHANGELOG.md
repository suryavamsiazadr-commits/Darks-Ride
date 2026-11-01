# Changelog

How Dark's Ride grew, step by step. Dates are when the change landed on `main`.

## v1.0 — 2026-11-01

First release. Everything below is in it.

### 2026-10-31 — Two styles, full-screen music, live progress
- **Soft (neumorphism) style** alongside **Glass**: raised cards, buttons, music player and floating cards; works with all four colours, light and dark. Settings → Appearance → Style.
- **Full-screen music** beside Maps and on the Wing's small screen while music plays; calls and new messages slide in over it. ⤢ on the music card opens it any time; "Cards ▾" goes back.
- **Music redesign:** album art blurred behind the card, gently pulsing art while playing, a moving progress wave with running time and length (the main phone now sends song position and length, and sharper art).
- **Cards start closed** each time the app opens; a dot marks cards that need a look (setup steps left, unread messages, a fresh route, bike internet off, garage items due).

### 2026-10-31 — Ride dashboard, card order, landscape and split screen
- One ride screen that fits the space: portrait list; landscape with the top card big on the left and every other card on the right; compact full-width cards beside Maps and on the Wing's small screen.
- **Card order** in Settings (▲ / ▼). Live cards jump to the top and return afterwards: call until it ends, new message for its spotlight time, music while it plays.
- **Side by side with Maps** (Android split screen) with "Open Maps beside"; destinations open in the Maps half while in split screen.
- Screenshot tests (Robolectric) render every layout in both styles on each build, to check alignment without a phone.

### 2026-10-31 — Bike internet and smarter pop-ups
- **Automatic Bluetooth tethering:** the bike watches its own internet and asks the main phone; the opt-in **auto-tap helper** switches on Bluetooth tethering (main) and Internet access (bike). Checklist steps and a Bike internet card on both phones.
- Floating cards (calls, messages) only when the app is closed; inside the app they're cards.
- New message **spotlight** time setting (5–60 s).
- **Four ride colours:** Ember, Redline, Glacier, Volt.
- Modern floating cards: letter avatar with a pulsing ring for calls, app tags, shrinking time bar, big pill buttons.
- First music card redesign: blurred art, glowing play button, segmented volume, equalizer.

### 2026-10-29 — Navigation details, documents lock, daylight glass
- Real destination names for shared routes ("From … to … via …"), area lookup from coordinates.
- Navigation details and history on the main phone too (Plan a ride, send again).
- Fingerprint / PIN before opening Samsung Pass, DigiLocker and mParivahan; shortcut to the IDs screen inside Samsung Wallet.
- Light theme gets the glossy glass look (pearl background, warm glow, soft shadows).
- Activity log kept permanently on each phone; version shown on the bike's home page; the empty Messages card no longer opens and closes.

### 2026-10-28 — Garage, Documents, Settings, design
- True black background with faint stars; a Settings page so the Ride page stays clean; version above the tabs; role names Pocket Sherpa / Handlebar HQ.
- **Garage:** fuel log, mileage, range, expenses, reminders.
- **Documents:** encrypted on the phone, fingerprint / PIN, scanner and import, viewer, password backup.
- Ride mode switch instead of killing the app; three tabs on the main phone.
- Glass-lite design, faster touch (buttons react on touch-down, volume repeats), auto light/dark from the light sensor, mountain-road icon and splash.
- Fixed the Wing panel appearing over Maps on the main screen; navigation details and history.

### 2026-10-28 — The core, in eight steps
1. Phone roles and the Bluetooth link.
2. Link starts by itself (boot, reconnect with back-off, battery exemption).
3. Music control from the ride screen (media sessions on the main phone).
4. WhatsApp and SMS messages on the ride screen; clearing syncs both ways.
5. Off switch on both phones.
6. Incoming calls: answer / reject / end from the bike; floating call and message cards over Google Maps.
7. Send to bike from Google Maps' share menu.
8. LG Wing ride panel on the small screen, setup checklist, automatic GitHub builds.

### 2026-10-27 — First sample
Dark welcome screen and a second-screen test on the LG Wing.
