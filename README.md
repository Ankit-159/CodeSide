# CodeSide

**Your AI. Your Code. Your Space.**

CodeSide is an open-source, Android-native agentic coding app. It brings a GUI chat, configurable AI providers, and an in-app sandboxed terminal to Android — without relying on Termux as an external app.

> Status: **Active development** — M1 foundation in place, M2 terminal in progress.

## Current features

- **GUI chat** — Compose chat UI with streaming responses, collapsed thinking/tool cards, and provider-backed conversations.
- **Bring your own API** — presets for OpenAI, Anthropic, and Google + editable Custom profiles (endpoint/auth/request/response mapping).
- **Encrypted settings** — provider profiles and API keys stored in app-private encrypted storage.
- **In-app terminal (M2 MVP+)** — JNI PTY shell, shared terminal manager (user/agent session), basic bootstrap/prefix environment, bootstrap ZIP install, LD_PRELOAD prefix shim groundwork, basic ANSI color rendering.
- **Android targets** — `minSdk 21`, `targetSdk 28`, ABI splits for `armeabi-v7a` and `arm64-v8a`.

## Build

Requires Android SDK + JDK 17:

```bash
gradle :app:assembleDebug
```

GitHub Actions builds debug APK artifacts on push/PR.

## Project layout

```text
app/                  Android app module (Kotlin + Jetpack Compose)
  src/main/cpp/       JNI PTY and exec shim
  src/main/java/      App, UI, providers, terminal, data stores
.github/workflows/    CI workflows
gradle/               Gradle version catalog / wrapper properties
```

## Roadmap

- [x] **M1 Foundation** — scaffold, CI, chat UI, provider adapters/settings, encrypted keys
- [~] **M2 Terminal** — JNI PTY, shared session, bootstrap/prefix, package install groundwork, terminal rendering
- [ ] **M3 Agent** — tool loop, permission UX, tool/thinking cards wired to real tools
- [ ] **M4 Polish** — SAF import/export/folder grants, files tab, hardening, release CI

## License

[GPL-3.0](LICENSE)
