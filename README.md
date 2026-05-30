# MyAgent-Android

MyAgent-Android is an Android app built with Kotlin + Jetpack Compose for running AI conversations with:

- Standard assistant mode
- NPC personas
- Agent workflows with custom tool-call simulation
- OpenAI-compatible API endpoints

It includes rich chat UX (streaming output, thinking/tool sections, session management) and a LAN port-forwarding feature so devices on the same network can call your configured LLM endpoint through your phone.

## Highlights

- Compose-based modern UI
- Room database persistence for settings, sessions, messages, NPCs, agents, and tools
- OpenAI-compatible `chat/completions` streaming support
- API endpoint history in settings
- Session preview uses assistant output summary (tool/thinking tags removed)
- Stable LAN forwarding service on fixed port `8787`
	- Foreground service with persistent notification
	- Health check endpoint: `GET /health`
	- Streaming pass-through for `stream=true`

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Android Architecture Components (ViewModel, Lifecycle)
- Room
- Coroutines + Flow
- OkHttp + Moshi

## Project Structure

- `app/src/main/java/com/example/ui/`
	- Main screens and `MainViewModel`
- `app/src/main/java/com/example/data/`
	- Database models/DAO/repository
	- Network service
	- Local forwarding server and foreground service
- `app/src/main/AndroidManifest.xml`
	- Permissions and service declarations

## Local Development

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK for the configured compile/target SDK
- Java 11+ and `JAVA_HOME` configured

### Run

1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. Run on an emulator or physical Android device.

Debug signing note:

- `debug.keystore` is restored from `debug.keystore.base64` automatically when needed.

## API Configuration

In Settings:

1. Set your OpenAI-compatible Base URL.
2. Set API key.
3. Run connectivity test to fetch available models.

## LAN Port Forwarding

When enabled in Settings, the app starts a foreground forwarding service on fixed port `8787`.

- Access URL format: `http://<phone-ip>:8787/...`
- Health check: `GET http://<phone-ip>:8787/health`
- GET requests are forwarded to your configured Base URL path.
- POST chat requests are forwarded to `<base-url>/chat/completions`.
- If request body includes `stream=true`, response is streamed back with chunked transfer.

Example:

- `http://<phone-ip>:8787/chat/completion` can be used as local entry.
- Upstream target is your configured API endpoint `.../chat/completions`.

## Notes

- If VPN is enabled on the phone, LAN access may fail unless the VPN allows local network/LAN bypass.
- On Android 13+, notification permission may affect foreground notification visibility.

## License

Add your preferred license information here.
