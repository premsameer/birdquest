# Birdquest

Android and browser birding prototype with microphone recording, BirdNET call recognition, location, timestamps, a species collection and CSV export.

Current Android version: 0.1.2. Microphone startup has received permission fixes; real-device accuracy and compatibility remain under testing. Rarity tiers are illustrative and newly detected species are Unrated.

## Source

- dist/: browser interface and local recognition worker
- android/: Android WebView application
- scripts/fetch-models.py: download pinned BirdNET models, verify upstream Git blob hashes, and fetch TensorFlow.js
- scripts/build-apk.py: workspace-specific offline SDK build script; paths currently refer to /workspace/toolchains
- tests/: JavaScript integration and asset-integrity tests
- online/: proposed Perch upload adapter and deployment notes; NOT active in the APK

## Build

Run python3 scripts/fetch-models.py to restore model/runtime assets. This downloads roughly 61 MB. Open android/ in Android Studio with SDK 35 and a compatible Java/Gradle installation. The Gradle project uses Android Gradle Plugin 8.9.1 (Gradle 8.11.1). Build the debug APK using Android Studio. Local signing keys and build artifacts are excluded from Git.

## Checks

node tests/app.test.cjs
node tests/recognition.test.cjs
node tests/assets.test.cjs

The first two suites simulate browser/model components and do not establish actual recognition accuracy. Asset tests require the downloaded model files.

## Data and recognition

Current recognition runs on-device. Sightings persist in browser/WebView local storage; audio playback is session-only. Bird photos and fonts load from external services. The online Perch provider is prepared but has no deployed endpoint. Google Cloud deployment and authentication remain pending.

## Attribution

BirdNET browser worker adapted from https://github.com/birdnet-team/real-time-pwa (MIT; see dist/BIRDNET-LICENSE.txt). Models are CC BY-SA 4.0 according to upstream; pinned source commit and asset hashes are recorded in dist/model-assets.json. TensorFlow.js is Apache-2.0, with its license fetched alongside the runtime. Preserve these notices when distributing the app.
