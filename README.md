# Dark's Ride

A free, personal Android app that turns a spare phone on your motorcycle into a riding screen for your main phone. Not for sale, no accounts, no server: everything travels over Bluetooth between your two phones.

**On the bike phone (Ride screen)**
- Google Maps on the screen, with calls and messages floating on top
- Now Playing with album art and big buttons: previous, play/pause, next, volume
- WhatsApp and SMS messages, plus missed calls
- Incoming calls with huge **Answer** and **Reject** buttons, and **End call** during a call
- Destinations sent from the main phone start Google Maps navigation (two-wheeler mode)
- LG Wing: a ride panel on the small screen when the phone is swivelled

**On the phone in your pocket (Main phone)**
- Sends music, messages and calls to the bike, and carries out the buttons pressed there
- **Send to bike** in the share menu: share a place from Google Maps and the bike starts navigating

## What you need

- Two Android phones, Android 12 or newer. Any brands.
- The two phones paired once in Bluetooth settings.

## Install

1. On each phone, open the **Releases** page of this repository and download the latest `DarksRide-v….apk`.
2. Open the file. If Android asks, allow installing apps from your browser or file manager.
3. Do the same on the other phone.

Updating later: install the new APK over the old one. Your settings stay.

## First setup

Pair the two phones once: **Settings → Bluetooth** on the main phone, tap the bike phone, confirm the code on both.

### Bike phone

1. Open Dark's Ride, choose **Ride screen**.
2. **Allow access** (Bluetooth and notifications).
3. **Allow background running**.
4. **Allow display over other apps** (calls and messages appear on top of Maps).

### Main phone

1. Open Dark's Ride, choose **Main phone**.
2. **Allow access**, then tap the bike phone in the list.
3. **Allow background running**.
4. **Allow call access** (see who is calling, answer and reject from the bike).
5. **Allow notification access** (music and messages). If the switch is greyed out: **Settings → Apps → Dark's Ride → ⋮ → Allow restricted settings**, then try again.
6. Optional: **Allow for WhatsApp calls** (display over other apps).

When both phones show a green dot with **Connected**, you're done. The checklist at the top of the app shows anything still missing.

**Xiaomi, Redmi, Poco, Oppo, Realme, Vivo, OnePlus and similar:** these phones stop background apps. In **App info → Battery**, choose **No restrictions**, and turn on **Autostart** if you see it. The app shows a button for this.

## Riding

- Internet for the bike phone: turn on **Bluetooth tethering** on the main phone (Settings → Connections → Mobile Hotspot and Tethering). It uses less battery than a Wi-Fi hotspot.
- Download **offline maps** for your route in Google Maps on the bike phone.
- Dark's Ride starts by itself when the phones come near each other, and after a phone restart.

## Turning it off

Tap **Turn off Dark's Ride** in the app, or **Turn off** on its notification. It closes completely and stays off, even after a restart, until you open the app again. Do it on both phones.

## Privacy

Notifications, calls and music info go only from one of your phones to the other over Bluetooth. Nothing is uploaded anywhere. The only internet use is the main phone opening a Google Maps link you shared, to find the exact location.

## Building it yourself

Open the project in Android Studio and press Run, or let GitHub build it: every push to `main` builds an APK (Actions tab), and pushing a tag like `v1.0` publishes it on the Releases page.
