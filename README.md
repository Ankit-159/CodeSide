# CodeSide

**Your AI. Your Code. Your Space.**

CodeSide is an open-source, Android-native agentic coding app. It brings a GUI chat, configurable AI providers, and an in-app sandboxed terminal to Android — without relying on Termux as an external app.

> Status: **Active development** — M1-M4 core features implemented; polishing ongoing.

## Current features

- **GUI chat** — Compose chat UI, streaming responses, tool/thinking cards, basic markdown rendering, chat persistence.
- **Bring your own API** — OpenAI, Anthropic, Google presets + editable Custom profiles (endpoint/auth/request/response mapping).
- **Agent tools** — `run_command/read_file/write_file/edit_file/list_dir/search` plus granted-folder SAF tools, with approval gate and “Always allow” allowlist.
- **Encrypted settings** — provider profiles/API keys and app settings stored in app-private encrypted storage.
- **In-app terminal** — JNI PTY, shared terminal manager, prefix/bootstrap env, bootstrap ZIP install, `.deb` install, Termux repo package lookup, Termux VT terminal on Android 7+ with legacy fallback.
- **Files/workspace** — workspace file list, SAF import/export, rename/delete, SAF folder grants with persisted permissions.
- **Android targets** — `minSdk 21`, `targetSdk 28`, ABI splits for `armeabi-v7a` and `arm64-v8a`.

## Build

Requires Android SDK + JDK 17:

```bash
gradle :app:assembleDebug
```

GitHub Actions builds debug APK artifacts on push/PR. A manual release workflow is available when signing secrets are configured.

## Project layout

```text
app/                  Android app module (Kotlin + Jetpack Compose)
  src/main/cpp/       JNI PTY and exec shim
  src/main/java/      App, UI, providers, terminal, agent, data stores
.github/workflows/    CI workflows
gradle/               Gradle version catalog / wrapper properties
```

## Roadmap

- [x] **M1 Foundation** — scaffold, CI, chat UI, provider adapters/settings, encrypted keys
- [x] **M2 Terminal** — JNI PTY, shared session, bootstrap/prefix, package install groundwork, VT terminal
- [x] **M3 Agent** — tool loop, approval UX, tool/thinking cards wired to real tools
- [~] **M4 Polish** — SAF import/export/folder grants, files tab, persistence, hardening/release CI

## License

[GPL-3.0](LICENSE)
