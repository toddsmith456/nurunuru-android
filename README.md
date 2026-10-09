<div align="center">

# Nurunuru — Android

**A simple and intuitive Nostr client for Android**

[![License](https://img.shields.io/badge/license-Unlicense-blue)](LICENSE)
[![Nostr](https://img.shields.io/badge/Nostr-protocol-purple)](https://nostr.com)

</div>

---

Nostr is an open social protocol that does not depend on any single company or server. Accounts are controlled by cryptographic keys, so you are not locked in to any one service. **Nurunuru** provides an easy, familiar interface for getting started.

This repository contains the **Android app only**. It is an English translation of the Android client from [tami1A84/null--nostr](https://github.com/tami1A84/null--nostr) (release v1.5.4), which is archived. The original project is written in Japanese and also includes a web app and an iOS app; those are not included here.

## Features

- **Timeline** — Network-first timeline with follow and relay views, infinite scroll, and NIP-65 relay hints
- **Posting** — Up to 3 images per post, with per-post relay selection
- **Chat** — End-to-end encrypted direct messages and group chats (NIP-EE / MLS)
- **Zaps** — Fetch Lightning invoices from a recipient's Lightning address
- **Search** — Full-text search with operators such as `#tag`, `from:`, `since:`, `until:`, and `filter:`
- **Mini-apps** — Custom emoji, profile badges, relay settings, mute lists, backup and export, deletion requests, and cache settings
- **Login** — Passkeys, `nsec` keys, and external signers such as Amber (NIP-55)

## Project layout

```
app/                     Android application (Kotlin, Jetpack Compose)
rust-engine/
  nurunuru-core/         Core Nostr engine (Rust)
  nurunuru-ffi/          UniFFI bindings used by Android (Kotlin)
    android/             Gradle module that wraps the native library
    bindgen/             Scripts to build the .so and generate Kotlin bindings
zapstore.yaml            Zapstore publishing metadata
```

## Building

Requirements: Android Studio (or the Android SDK), JDK 17+, Rust (with the Android targets), and the Android NDK.

1. **Build the Rust engine and generate the Kotlin bindings:**

   ```sh
   cd rust-engine/nurunuru-ffi/bindgen
   make android-all   # cross-compile the .so for each Android ABI
   make kotlin        # generate the Kotlin bindings
   ```

2. **Open the project** in Android Studio, or build from the command line:

   ```sh
   ./gradlew :app:assembleDebug
   ```

`settings.gradle.kts` includes the `nurunuru-ffi` module. The prebuilt `arm64-v8a` library in `rust-engine/nurunuru-ffi/android/libs/` is included so you can build without first compiling Rust.

## Notes

- Code comments and some internal developer notes are still in Japanese. The user-facing text (UI, error messages, and notifications) has been translated into English.
- The Rust engine's `#[cfg(target_os = "ios")]` branches are kept as-is. They do not apply to Android builds.

## Credits

- Original project: [tami1A84/null--nostr](https://github.com/tami1A84/null--nostr) (Unlicense). Supported by an OpenSats Nostr Grant.
- Translation into English: this repository.

## License

This project is released under the [Unlicense](LICENSE), the same license as the original.
