<p align="center"><img src="docs/icon.png" width="128" alt="Haze icon"></p>

<h1 align="center">Haze</h1>

<p align="center">
A fast, local-only gallery for Android. AMOLED-minimal, no network, no tracking.
</p>

## Features

- Grid or masonry timeline, fast scroller, search by name, album or month
- Albums with pin, rename, hide and delete; Favorites and Videos albums; Trash with days-left badges and delete-all
- Viewer with zoom, swipeable thumbnail strip, info sheet, Lens search, wallpaper / contact photo, blurred ambient background
- Vault with its own lock (PIN, pattern or password)
- Photo editor: crop, rotate, straighten, adjustments, filters, undo / redo (saves a copy)
- Video editor: trim, crop, rotate, adjustments, filters, mute, undo / redo (exports a copy)
- Metadata: edit the date taken, remove location, and photos are shared without their GPS position
- Video player on Media3: gestures, playback queue with loop/shuffle/autoplay, subtitles (including loading .srt / .vtt files), audio tracks, resume, background play, PiP, mirror
- Theming: AMOLED / Dark / Light, custom accent color

## Install

Grab the latest APK from [Releases](../../releases). Requires Android 10+.

Haze asks for media access and, optionally, All files access (used for faster folder operations, EXIF from the real file and the 30-day trash purge).

## Build

```
./gradlew assembleRelease
```

Release builds use the debug key unless `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set. Tagging `v*` runs the release workflow, which signs with the repository secrets of the same names (plus `KEYSTORE_BASE64`).

## Stack

Kotlin, Jetpack Compose, Room, DataStore, Coil, Media3. Package `sleepless_index.haze`, min SDK 29.

## Notes

- Android 10 has no system trash, so deleting there is permanent after the system prompt.
- Rare audio codecs (DTS, some E-AC3) depend on the device's decoders.
