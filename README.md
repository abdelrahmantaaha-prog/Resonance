# Resonance — a player that learns

AI music player that builds your playlist two songs ahead. Gemini learns your taste from four signals:

| Signal | Meaning |
|---|---|
| Finish a song | You like it — genre and artist gain weight |
| Skip | Not your type — steers away |
| ♥ Heart | All-time favorite, weighted 3x, joins the **Close to my heart** playlist |
| Thumbs down | Genre banned permanently |

Plus a neutral **next — teaches nothing** button, a **♥ favorites-only mode** (header pill), manual anchoring of your existing playlists (Taste Model → *add songs manually*), and taste-profile export/import.

## 📡 Stations — "more like this"

Press **📡** on any song to turn that one track into the whole queue. Everything
after it is picked for its resemblance to the seed: same subgenre, era, language,
instrumentation, tempo and mood, ordered from near-twins outward. With a Last.fm
key the candidate pool comes from what real listeners actually play alongside that
exact track, and Gemini only curates it.

You can start a station from the now-playing card, from any song in **Sound DNA**,
or from a **🔍 Search** result (`s` on a keyboard). A banner shows what the station
is built on; **✕ end station** returns to normal taste-based discovery. Stations
replace favorites-only mode, the 🎲 surprise lane and the 📻 radio lane while
running — they're all mutually exclusive ways of choosing the next song.

## 🚗 In the car

`car/` is a Capacitor wrapper that ships this same app as an Android APK with a
real media session — steering-wheel buttons, lock-screen controls and background
audio, none of which a browser PWA can do on Android. The wheel mapping is
deliberate:

| Wheel button | Does | Learns |
|---|---|---|
| **⏮ Previous** | **♥ Favorite** — keeps playing | yes, 3× weight |
| **⏭ Next** | Skip onward | **nothing** |

Built by GitHub Actions (**Actions → Cassette car APK → Run workflow**), no local
Android SDK needed. See [car/README.md](car/README.md).

## Setup

You need two free API keys, entered on first run (saved on-device, never in this repo):

1. **Gemini** — required — https://aistudio.google.com
2. **YouTube Data API v3** — recommended — https://console.cloud.google.com (enables reliable track search)

## Deploy (GitHub Pages)

```bash
git init
git add .
git commit -m "Resonance v1"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/resonance.git
git push -u origin main
```

Then: repo **Settings → Pages → Source: GitHub Actions**. The included workflow deploys automatically on every push. Your app lives at `https://YOUR_USERNAME.github.io/resonance/` — open it on your phone and *Add to Home Screen* / *Install app*.

## Notes

- Must be served over HTTPS — YouTube's embedded player refuses `file://` (Error 153).
- Taste profile and keys live in each device's browser storage. Use export/import (⚙ Settings) to move the profile between devices.
- Background playback and steering-wheel next/prev are limited by YouTube's embedded-player architecture; play/pause from lock screen/car works via YouTube's own media session.
