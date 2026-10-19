# Aura Chat

A premium native Android chat app (Kotlin, Jetpack Compose, Material 3) with a
glassmorphism design. It talks to any OpenAI-compatible API with real streaming
replies, stores chats locally in Room and keeps settings in DataStore.

## Features
- **Live Call**: a real, continuous voice call. The microphone stays on and
  restarts itself after every silence, recognition error or timeout. Replies are
  spoken with the official **Gemini text-to-speech** voices — no Android/system
  TTS engine is used anywhere in the app.
- **Call controls**: auto / speaker / earpiece / Bluetooth routing (auto uses the
  proximity sensor), mute, front and back camera preview, camera switch, and a
  screen-share option ready for the next update. Say “end call” in any supported
  language and Aura speaks a full farewell sentence before hanging up.
- **Long-term memory**, like Gemini and Claude: durable facts are extracted after
  chats and calls, stored in SQLite and mirrored to real Markdown files under the
  app's private `files/memory/` folder. Browse, search, pin, edit, delete or
  export everything from the Memory screen. Memory survives restarts, disconnects
  and calls.
- **Claude-style skills**: install a skill by pasting a GitHub repository link (or
  any raw Markdown URL). Aura looks for `SKILL.md`, `skills/*.md`, then
  `README.md`, parses front-matter and injects enabled skills into every chat and
  call. Skills start empty and are added manually.
- **Custom connectors**: MCP (JSON-RPC over HTTP with tool discovery), REST APIs
  and application deep links. Add them in the Connectors screen or by telling
  Aura in chat. Enabled connectors become real tools the model can call.
- **Rich chat**: attach any file type (PDFs including scanned pages, Word, Excel,
  PowerPoint, ZIP, code, images), record or import any voice note (including
  WhatsApp Opus/OGG) and play it back with automatic WAV conversion. Replies have
  copy, Gemini voice playback, share, like and dislike actions. Links appear in a
  private, non-clickable box with an explicit copy button.
- 18 screens: splash, onboarding, home, chat, personas, search, settings,
  providers, provider editor, model picker, appearance, prompt library, about,
  Live Call, Memory, Skills, Connectors plus the studio tools: **Compare Lab**
  (graphite and amber, two models raced with live timing), **Insights** (emerald
  bento dashboard from your real chat history), **Translate** (sunset gradient, 18
  languages, listen/copy/share) and **Writing Studio** (editorial serif, 8
  rewrite voices).
- Live token streaming, stop / regenerate, Markdown with copyable code blocks
- Providers: OpenAI, OpenRouter, Groq, Gemini (OpenAI endpoint), DeepSeek,
  Together, Ollama or any custom base URL, with connection test and model list
- Local chat history: search, pin, rename, delete, export as Markdown
- 7 personas, reusable prompt library, per-chat system prompt
- Themes (system / dark / light), 6 accent palettes, adjustable glass intensity
- Urdu / RTL friendly text direction in chat
- System back navigates between pages; on the home screen it sends the app to the
  background instead of closing it.

## Get an APK from GitHub (no computer tools needed)
1. Create a new GitHub repository and upload / push everything in this folder
   (the `.github` folder must be included).
2. Open the **Actions** tab. The *Build APK* workflow starts by itself on push.
3. When it finishes, open **Releases** in the repo and download
   `AuraChat-debug-N.apk` (or the `release` one) to your phone and install it.

### Build again later by commenting
- Open any issue (or any commit) and comment `/build`. The workflow runs and
  replies with the download link. You can also press **Run workflow** in Actions.
- Only the repo owner, members and collaborators can trigger it by comment.

## First launch
Settings → Providers → add a provider (pick a preset, paste your API key, tap
**Test connection**), then choose a model and start chatting.

For spoken replies, open **Settings → Voice & calls** and add a Gemini API key
(or use a Gemini provider as the active provider). Pick one of the 30 official
Gemini voices there.

## Local build
```
gradle :app:assembleDebug      # or add the Gradle wrapper with: gradle wrapper
```
Requires JDK 17 and the Android SDK (platform 34).

## Permissions
- **Microphone** for continuous speech recognition during Live Call and for
  recording voice notes.
- **Camera** (optional) for the front/back camera preview during a call.
- **Bluetooth connect** (Android 12+) for Bluetooth call audio.
- **Modify audio settings** for speaker / earpiece / Bluetooth routing.
- Speech recognition uses the phone's recognition service; spoken output is
  generated only by Gemini.

## Notes
- The release APK is signed with the debug key so it installs directly. Use your
  own keystore before publishing to Google Play.
- API keys are stored in the app's private storage (backup is disabled).
- Memory files live in the app's private folder and are exported as Markdown.
- Cleartext HTTP is allowed so you can reach local servers such as Ollama.
