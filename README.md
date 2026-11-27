# Ajadi Keys

Ajadi Keys is an offline native Android input method for Russian, English and Hebrew.
The keyboard works without optional packages and has no internet permission, telemetry, or account.

## Installation

Download the keyboard APK from [Releases](https://github.com/ajadi/AjadiKeys/releases), install using Android's system package installer, open Ajadi Keys and enable/select it as an input method.
Install language packs from Settings → Languages and dictionaries → Language packs. Update the Russian pack to 1.1.0 to use the expanded dictionary. Older packs remain compatible.
The voice and translation engines and their model ZIPs are optional separate downloads. Follow [Russian installation instructions](INSTALL-RU.md).

## Build

JDK 17, SDK platform 35 and Android Build Tools 34.0.0 are required. Set ANDROID_HOME or provide your own local.properties. For the optional native voice engine install NDK 27.2.12479018 and CMake 3.22.1, then initialize the pinned submodule.

    git submodule update --init
    ./gradlew :app:testDebugUnitTest :app:assembleDebug
    ./gradlew :language-pack:assembleDebug :translation-engine:assembleDebug :voice-engine:assembleDebug :ai-companion:assembleDebug

Use gradlew.bat on Windows. All components must be signed with the same key for local signature-based IPC and language-pack recognition. The source repository includes no signing keys. A self-built APK signed with a different key cannot update installed release APKs.

## Features

- Three languages, primary language pair and swipe switching; optional number row and responsive computer-key panel (full layout on suitable tablets).
- Local word predictions, contextual autocorrection, style learning, explicit personal dictionary and shortcuts.
- Clipboard history in RAM, explicitly saved phrases and templates, emoji categories and offline Russian/English search.
- Configurable Backspace gestures, selected-text layout correction, optional sentence capitalization.
- Themes: light, dark, system, Material You, experimental translucent, and custom colors.
- Optional offline speech and translation; optional AI commands in a separate app that sends only explicitly selected text to the user-configured service.

## Privacy and third-party content

The IME has no network permission. Learning, dictionary and user settings are local. Explicit backup excludes login values, passwords, clipboard history and AI credentials. Platform-marked sensitive clipboard items and protected editors do not enter recent history. Saved phrases are explicit user-created content. The AI companion is the only component requiring network access.
Language data licenses and attribution are in [language-data/words/ATTRIBUTION.txt](language-data/words/ATTRIBUTION.txt). Emoji annotations use Unicode CLDR ([license](app/src/main/assets/emoji/LICENSE.txt)); original model manifests and runtime notices are shipped with their respective components. Third-party licenses apply to those components.
