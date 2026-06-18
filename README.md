# KRANK STUDIO

[![Build](https://github.com/KingKaonix/krank-studio/actions/workflows/build.yml/badge.svg)](https://github.com/KingKaonix/krank-studio/actions/workflows/build.yml)
[![Release](https://img.shields.io/badge/release-v1.6.0-22d3ee)](https://github.com/KingKaonix/krank-studio/releases)
[![License](https://img.shields.io/badge/license-MIT-22c55e)](LICENSE)

**KRANK STUDIO** is a professional-grade, low-latency guitar effects processor and audio toolkit for Android. Built from the ground up with a hybrid C++ audio engine and a premium hardware-amp-inspired UI, it delivers studio-quality sound processing directly on your phone or tablet — no cloud, no latency, no compromises.

---

## Why KRANK STUDIO?

### vs. Guitar Pro
| Feature | KRANK STUDIO | Guitar Pro Mobile |
|---|---|---|
| **Live Effects Processing** | 8 effects in chain + cab sim IR loader | ❌ |
| **Chromatic Tuner** | 12 tuning presets + strobe display | Basic tuner only |
| **Audio-to-Tab Transcription** | Real-time & file-based pitch detection | ❌ |
| **Tab Import** | MIDI + MusicXML | Own format only |
| **Tab Export** | MIDI, MusicXML, GP5, ABC notation | GP format only |
| **Tone Matcher** | WAV/MP3 analysis → auto-effect tuning | ❌ |
| **Metronome** | Tap tempo 40-240 BPM | ❌ |
| **Looper** | Record/play/overdub/clear | ❌ |
| **IR Cab Sim** | Convolution impulse response loader | ❌ |
| **MIDI Footswitch** | Learn mode + looper toggle | ❌ |
| **BLE Foot Controller** | Scan & connect | ❌ |
| **Audio Monitoring** | Real-time headphone monitoring | ❌ |
| **WAV Recording** | 32-bit float direct-to-FLAC | ❌ |
| **File Support** | WAV + MP3 + AAC + OGG + FLAC | GP only |
| **Price** | Free | $12.99/month |

### vs. ToneStack Pro
| Feature | KRANK STUDIO | ToneStack Pro |
|---|---|---|
| **Latency** | < 10ms (Oboe exclusive mode) | 15-25ms |
| **Chromatic Tuner** | 12 tuning presets + cents needle | Basic tuner |
| **Tone Matcher** | WAV/MP3 analysis → auto-effect tuning | Manual only |
| **Song Transcriber** | Audio → tablature generation | ❌ |
| **IR Loader** | Convolution cab sim | ❌ |
| **WAV Recording** | Direct-to-flac float recording | Limited |
| **UI** | Hardware amp aesthetic, glass cards | Generic icons |
| **File Support** | WAV + MP3 + AAC + OGG + FLAC | WAV only |
| **Mic Recording** | Live capture → analyze/transcribe | ❌ |
| **Tab Import/Playback** | MIDI + MusicXML with backing audio | ❌ |
| **Price** | Free | $9.99+ per amp pack |

### vs. AmpliTube Mobile
- **8 effects in one signal chain** vs AmpliTube's 4-slot limit on mobile
- **Real-time transcription** — play a riff, get tablature instantly
- **Tone matching from any audio file** — dial in any tone you hear
- **No in-app purchases** — every feature is included
- **Open source** — inspect the DSP, contribute, customize

### vs. BIAS FX Mobile
- **Native C++ engine** with Oboe — lower latency than BIAS's cross-platform layer
- **Full-duplex processing** — use your phone as a live rig
- **Chromatic tuner with strobe-style display** — accurate to ±1 cent
- **Song transcription from MP3/WAV** — learn songs by loading the track

---

## Features

### Audio Engine
- **< 10ms round-trip latency** — Oboe in exclusive mode with 256-frame buffer
- **48 kHz / 32-bit float** internal processing
- **Full-duplex** input/output — play and hear yourself in real time
- **Convolution reverb** with impulse response loading (WAV cab sims)

### Effects Chain — 8 Studio Effects
| Effect | Parameters | Accent Color |
|---|---|---|
| **Distortion** | Drive, Tone, Level | Red |
| **Amp Sim** | Drive, Tone, Level | Teal |
| **EQ** | Bass, Mid, Treble | Blue |
| **Chorus** | Rate, Depth, Mix | Sage |
| **Noise Gate** | Threshold, Attack, Release | Purple |
| **Compressor** | Threshold, Ratio, Attack, Release | Pink |
| **Delay** | Mix, Feedback, Time | Yellow |
| **Reverb** | Size, Mix | Gold |

Each effect has independent bypass switching and parameter control via precision sliders with real-time value readouts.

### 5 Amp Presets
Clean → Crunch → Lead → Metal → Ambient — instant tone switching with per-preset effect configurations.

### Chromatic Tuner
- 12 tuning presets: Standard, Drop D, Drop C, Open D, Open G, Open E, DADGAD, Half-Step Down, Full-Step Down, Drop B, Open A, Custom
- Strobe-style note display with pulsing glow ring
- Needle cents indicator with color zones (green = in tune, yellow = close, red = off)
- Frequency readout accurate to 0.1 Hz
- Real-time note detection with audio processing
- **Tuner mute** — cuts dry signal to amp for silent tuning

### Tone Matcher
Load any audio file (WAV, MP3, AAC, OGG, FLAC) or record from your mic — KRANK analyzes the spectral content and recommends optimal effect settings to match the target tone:
- Distortion drive/tone/level
- Amp Sim gain/tone/master
- EQ bass/mid/treble
- Chorus rate/depth/mix
- Delay mix/feedback/time
- Reverb size/mix

### Song Transcriber
Load a WAV or MP3 of a guitar part — the YIN-based monophonic pitch detection engine transcribes audio into standard tablature with fret positions, string assignments, and timing.

### Tab Import & Playback
Open MIDI (.mid) or MusicXML (.xml) tab files directly in the app:
- **MIDI import** — pitch-to-string mapping with fret assignment
- **MusicXML import** — reads fret/string notation
- **Backing audio mixing** — play tabs in time with the original track
- **Scrolling fretboard visualization** with real-time note highlighting

### Tab Export
Export transcribed tablature to multiple industry-standard formats:
- **MIDI** (.mid) — compatible with Guitar Pro, TuxGuitar, MuseScore, DAWs
- **Guitar Pro 5** (.gp5) — native Guitar Pro format, readable by GP5+, TuxGuitar, MuseScore, and any GP-compatible software
- **MusicXML** (.xml) — readable by Sibelius, Finale, Dorico, MuseScore
- **ABC notation** (.abc) — plain-text format for quick sharing

### Metronome
- Tap tempo input (40-240 BPM)
- Visual beat indicator
- Precision timing reference for practice and recording

### Looper Pedal
- Record, play, overdub, and clear loop layers
- Full-duplex operation — play over your loops in real time
- MIDI footswitch assignable for hands-free control

### MIDI Footswitch Support
- Learn mode — map any MIDI CC to any action
- Looper toggle, effect bypass, tuner, presets
- Compatible with standard MIDI USB controllers

### Bluetooth LE Foot Controller
- Scan and connect to BLE foot controllers
- Low-latency wireless control for live performance

### Audio Input Monitoring
- Real-time headphone monitoring with zero added latency
- Hear your processed signal through the effects chain

### WAV Recording
Record your playing directly to high-quality WAV files (32-bit float, 48 kHz) with a single tap.

### IR Loader (Cab Sim)
Load impulse response WAV files for convolution-based cabinet simulation.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Audio Engine** | C++17, Oboe (Google's low-latency audio API) |
| **Effects** | Custom DSP: distortion, amp sim, biquad EQ, chorus, noise gate, compressor, delay (feedback), reverb (Schroeder/Moorer), convolution |
| **Pitch Detection** | YIN algorithm (monophonic) |
| **FFT Analysis** | Tone matching via spectral comparison |
| **FFT Library** | PocketFFT (header-only, MIT) |
| **Tab Parsing** | MIDI (pitches → strings/frets), MusicXML (fret/string notation) |
| **JNI Bridge** | Custom C++/Kotlin interop layer |
| **UI** | Kotlin, Jetpack Compose, Material 3 |
| **Navigation** | Jetpack Navigation Compose |
| **Architecture** | Android ViewModel + Observe pattern |
| **Audio Decoding** | MediaExtractor (MP3, AAC, OGG, FLAC) |
| **Build System** | Gradle + CMake |
| **Target SDK** | 34 (Android 14) |
| **NDK** | 27.0.12077973 |

---

## Screenshots

| Channel Strip | Chromatic Tuner | Tone Matcher | Transcriber |
|---|---|---|---|
| 8 hardware-style effect cards with LED indicators, precision sliders, and per-effect accent colors | Glowing note display with needle cents meter, 12 tuning presets, frequency readout | WAV/MP3 analysis dashboard with effect recommendations | Audio-to-tablature engine with scrolling fretboard visualization |

---

## Building from Source

```bash
git clone https://github.com/KingKaonix/krank-studio.git
cd krank-studio
./gradlew assembleDebug
```

**Prerequisites:**
- Android SDK 34
- NDK 27.0.12077973
- Java 17+
- CMake 3.22.1+

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Download

[![Latest Release](https://img.shields.io/badge/download-latest%20release-22d3ee)](https://github.com/KingKaonix/krank-studio/releases/latest)

1. Go to the [Releases page](https://github.com/KingKaonix/krank-studio/releases)
2. Download the latest APK
3. Enable "Install from unknown sources" on your device
4. Install and KRANK

---

## Roadmap

- [x] MIDI footswitch support
- [x] Tuner output to amp (mute dry signal)
- [x] Preset save/load from files
- [x] Polyphonic transcription (ML source separation)
- [x] Looper pedal
- [x] Metronome with tap tempo
- [x] Audio input monitoring via headphones
- [x] Bluetooth LE foot controller
- [x] Tab import (MIDI + MusicXML)
- [x] Tab playback with backing audio
- [x] Multi-format tab export (MIDI, MusicXML, GP5, ABC)
- [x] Guitar Pro format export
- [ ] Internal audio capture (streaming)
- [ ] Multi-track looper

---

## License

MIT — use it, modify it, ship it. If you build something cool with KRANK, we'd love to hear about it.

---

*Built with feedback from real guitarists, for real guitarists. No subscription. No microtransactions. Just great tone.*
