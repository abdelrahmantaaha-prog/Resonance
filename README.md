# Resonance — a player that learns

AI music player that builds your playlist two songs ahead. Gemini learns your taste from four signals:

| Signal | Meaning |
|---|---|
| Finish a song | You like it — genre and artist gain weight |
| Skip | Not your type — steers away |
| ♥ Heart | All-time favorite, weighted 3x, joins the **Close to my heart** playlist |
| Thumbs down | Genre banned permanently |

Plus a neutral **next — teaches nothing** button, a **♥ favorites-only mode** (header pill), manual anchoring of your existing playlists (Taste Model → *add songs manually*), and taste-profile export/import.

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
