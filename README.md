# Guitarix 🎸

[![Build](https://github.com/KingKaonix/guitarix/actions/workflows/build.yml/badge.svg)](https://github.com/KingKaonix/guitarix/actions/workflows/build.yml)

**Guitarix** is a low-latency guitar effects processor for Android, built with C++ (Oboe) and Kotlin (Jetpack Compose).

## Features

- 6 effects: Distortion, Amp Sim, EQ, Chorus, Delay, Reverb
- 5 presets: Clean, Crunch, Lead, Metal, Ambient
- Low-latency audio engine (< 10ms)
- Dark glass UI with tactile rotary controls
- Chromatic tuner with drop tuning support
- Tone matching — load a WAV sample and auto-tune effects

## Download

Grab the latest APK from the [Releases](https://github.com/KingKaonix/guitarix/releases) page.

## Building

```bash
git clone https://github.com/KingKaonix/guitarix.git
cd guitarix
./gradlew assembleDebug
```

Requires Android SDK 34 + NDK 27.0.12077973.
