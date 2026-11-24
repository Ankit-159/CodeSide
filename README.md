# CodeSide

**Your AI. Your Code. Your Space.**

CodeSide is an open-source, Android-native agentic coding app. It brings a GUI chat, configurable AI providers, and an in-app sandboxed terminal to Android — without relying on Termux as an external app.

> Status: **M1 foundation + M2 terminal MVP in progress** (chat/providers/settings scaffold, JNI PTY terminal shell, CI builds).

## Highlights (planned)

- **GUI-first chat** — markdown/code/diff rendering, tool calls and thinking shown as collapsed expandable cards.
- **Bring your own API** — presets for OpenAI, Anthropic, and Google + editable Custom profiles (endpoint/auth/request/response mapping).
- **In-app terminal** — mini-Termux style PTY shell shared by the user and AI, with package installs from Termux repos (Android 7+ full terminal; Android 5/6 limited fallback).
- **Security by default** — app-private workspace jail, SAF system picker imports/folder grants, permission-gated commands, encrypted API keys.
- **Android 5.0+** — `minSdk 21`, `targetSdk 28`, ABI splits for `armeabi-v7a` and `arm64-v8a`.

## Build

Requires Android SDK + JDK 17:

```bash
gradle :app:assembleDebug
```

GitHub Actions builds debug APK artifacts on push/PR.

## Project layout

```text
app/                  Android app module (Kotlin + Jetpack Compose)
.github/workflows/    CI workflows
gradle/               Gradle version catalog / wrapper properties
```

## Roadmap

- **M1 — Foundation**: scaffold, CI, chat UI, provider adapters/settings, encrypted keys
- **M2 — Terminal**: JNI PTY, bootstrap, terminal view, package install
- **M3 — Agent**: tool loop, permission UX, tool/thinking cards
- **M4 — Polish**: SAF import/export/folder grants, files tab, hardening, release CI

## License

[GPL-3.0](LICENSE)

@coderabbit review
