# Cassette in the car — Capacitor Android wrapper

Wraps the same `../cassette` app in an Android shell that owns a real
`MediaSessionCompat` plus a `mediaPlayback` foreground service. That is what gives
you steering-wheel buttons, a lock-screen/notification player, and audio that
survives the screen going off — none of which the plain PWA can do, because
Android's WebView has no Media Session API at all.

There is one source of truth: `webDir` points straight at `../cassette`, so the
APK ships exactly what GitHub Pages serves. Nothing is duplicated.

## Steering-wheel mapping

| Wheel / Bluetooth button | What Cassette does | Teaches the model? |
|---|---|---|
| **⏮ Previous** | **♥ Favorite the current song** — keeps playing, no track change | Yes — 3× weight, joins *Close to my heart* |
| **⏭ Next** | Skip to the next queued song | **No. Nothing at all.** |
| ▶ / ⏸ Play-pause | Play / pause | No |
| ⏹ Stop | Pause | No |
| Seek (head-unit scrubber) | Seek | No |

Previous is remapped on purpose: favoriting is the one judgement worth making at
100 km/h, and "go back a track" isn't. Next stays deliberately dumb so that
skipping past a song because someone got in the car never poisons the taste
profile — that's what the in-app **Skip · not my type** button is for.

The notification's previous-button icon is overridden with a heart
(`app/src/main/res/drawable/ic_baseline_skip_previous_24.xml`) so the on-screen
control matches what it actually does.

## Building the APK

Nothing to run locally — GitHub Actions builds it. Push this repo, then:

**Actions → "Cassette car APK" → Run workflow.** It also runs automatically on
any push to `main` touching `cassette/` or `car/`. Download `app-debug.apk` from
the run's Artifacts, copy it to the phone, and install (you'll need to allow
"install unknown apps" once).

The APK is debug-signed, which is fine for sideloading and means every rebuild
installs over the last one without uninstalling.

### If you ever want to build it yourself

Needs JDK 17 and the Android SDK (compileSdk 34), then:

```bash
npm ci && npx cap sync android && cd android && ./gradlew assembleDebug
```

Output lands at `android/app/build/outputs/apk/debug/app-debug.apk`.

## First run in the car

1. Launch Cassette, allow the notification prompt (Android 13+ hides the media
   notification otherwise — and that notification is what the head unit reads).
2. Paste your Gemini key + seed as usual, press **Start listening**. Keys live in
   the app's own storage, separate from the browser's.
3. Once a track is playing the notification appears and the media session goes
   live. Now connect Bluetooth / plug in USB.

## Version pinning, deliberately

Capacitor **6**, not 8. `@jofr/capacitor-media-session@4` declares
`@capacitor/core@^6`, and its native side is built against AGP 8.2.1 / compileSdk
34 / Java 17. Capacitor 7+ moves all three. Bumping Capacitor means either
verifying the plugin against the newer Android Gradle Plugin or replacing it.

That plugin is **GPL-3.0-or-later**. Irrelevant for a personal sideloaded build;
it would matter if you ever distributed the APK.

## Known limits

- **Playback is still the YouTube IFrame player inside a WebView.** Videos whose
  owners disable embedding can't play; the track resolver already filters on
  `videoEmbeddable=true`, and `onError` swaps in the next pick.
- **Radio mode blocks skipping** (by design in the app), so the wheel's Next
  button does nothing while 📻 is on air.
- **No Android Auto projection.** This is a phone app with a media session, which
  is what Bluetooth/USB steering-wheel controls talk to. A projected Android Auto
  UI needs a `MediaBrowserService` and Google review — a different project.
