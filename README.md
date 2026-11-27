# Ajadi Keys

A compact Android keyboard for Russian, English and Hebrew, designed for offline typing and local personalization. The main keyboard is less than 1 MB and has no internet permission, telemetry or account requirement.

**[Download APK 0.10.5](https://github.com/ajadi/AjadiKeys/releases/download/v0.10.5/AjadiKeys-0.10.5-debug.apk)** · [All releases](https://github.com/ajadi/AjadiKeys/releases) · [Инструкция на русском](INSTALL-RU.md)

## Features

- On-device word suggestions, contextual autocorrection, optional sentence capitalization, personal dictionary and learning.
- Three languages, swipe on the spacebar to switch, optional language key and number row.
- Emoji search in Russian and English, contextual suggestions and frequently used emoji.
- Clipboard history, saved phrases, text shortcuts, layout correction and settings export/import.
- Configurable Backspace gestures and optional PC keys (Ctrl, Alt, Esc, function keys, arrows); expanded layout for tablets.
- Light, dark, system, Material You, custom colors and experimental translucent themes.
- Optional offline voice input and translation; separate optional AI companion.

## Install

Requires Android 8.0 (API 26) or newer.

1. Download and install the keyboard APK from the link above.
2. Open Ajadi Keys, enable it in Android's keyboard settings, then select it as your input method.
3. In **Settings → Languages and dictionaries → Language packs**, install the dictionaries you need.
4. Configure input, appearance and optional features in Settings. The core keyboard works without add-ons.

## Downloads

Install only the modules you need. The keyboard and each add-on update independently.

| Component | Download |
| --- | --- |
| Keyboard | [APK 0.10.5](https://github.com/ajadi/AjadiKeys/releases/download/v0.10.5/AjadiKeys-0.10.5-debug.apk) |
| Russian dictionary | [APK 1.1.0](https://github.com/ajadi/AjadiKeys/releases/download/language-packs-v2/AjadiKeys-language-ru-1.1.0.apk) |
| English dictionary | [APK 1.0.0](https://github.com/ajadi/AjadiKeys/releases/download/language-packs-v1/AjadiKeys-language-en-1.0.0.apk) |
| Hebrew dictionary | [APK 1.0.0](https://github.com/ajadi/AjadiKeys/releases/download/language-packs-v1/AjadiKeys-language-he-1.0.0.apk) |
| Offline voice engine | [APK for ARM64](https://github.com/ajadi/AjadiKeys/releases/download/offline-engines-v1/AjadiKeys-voice-engine-0.6.0-arm64-v8a.apk) · [Other architectures](https://github.com/ajadi/AjadiKeys/releases/tag/offline-engines-v1) |
| Whisper models | [Base](https://github.com/ajadi/AjadiKeys/releases/download/offline-engines-v1/AjadiKeys-voice-base-v1.zip) · [Small](https://github.com/ajadi/AjadiKeys/releases/download/offline-engines-v1/AjadiKeys-voice-small-v1.zip) |
| Offline translation engine | [APK for ARM64](https://github.com/ajadi/AjadiKeys/releases/download/offline-engines-v1/AjadiKeys-translation-engine-0.6.0-arm64-v8a.apk) · [Other architectures](https://github.com/ajadi/AjadiKeys/releases/tag/offline-engines-v1) |
| Translation models | [Model ZIP](https://github.com/ajadi/AjadiKeys/releases/download/translation-models-v1/AjadiKeys-translation-models-v1.zip) |
| AI companion | [APK](https://github.com/ajadi/AjadiKeys/releases/download/ai-actions-v1/AjadiKeys-ai-companion-0.7.0.apk) |

For voice or translation, install the engine APK and import its model ZIP through the corresponding settings section. Use the ARM64 engine on most modern phones; other builds are available in the engine release. Installed models can be removed separately. The AI companion connects to a service configured by the user.

## Privacy

The keyboard itself has no network permission. Predictions, personal dictionaries and learning stay on the device. Protected editors and platform-marked sensitive clipboard items are excluded from recent clipboard history. Explicit backups exclude passwords, AI credentials and recent clipboard history. The optional AI companion requires network access and sends only text explicitly selected for an AI action.

## Build from source

Requires JDK 17, Android SDK 35 and Build Tools 34.0.0. For the native voice engine, install NDK 27.2.12479018 and CMake 3.22.1.

```bash
git clone https://github.com/ajadi/AjadiKeys.git
cd AjadiKeys
git submodule update --init
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :language-pack:assembleDebug :translation-engine:assembleDebug :voice-engine:assembleDebug :ai-companion:assembleDebug
```

Set `ANDROID_HOME` or create your own `local.properties`. On Windows use `gradlew.bat`. All components must use the same signing key for signature-based local communication. A self-built APK signed with another key cannot update the published APK.

## Third-party content

[Dictionary attribution](language-data/words/ATTRIBUTION.txt) · [Unicode CLDR emoji license](app/src/main/assets/emoji/LICENSE.txt). Model manifests and runtime notices are shipped with the respective components. Their third-party licenses apply.

[Report an issue](https://github.com/ajadi/AjadiKeys/issues)
