# SoundpadPlus

Desktop app (Kotlin + Swing/FlatLaf) that searches audios on online sources, downloads them and adds them to
[Soundpad](https://www.leppsoft.com/soundpad/) through its Remote Control API. Sources are plugins, so anyone can add one.

## Requirements

- Windows 10/11 with **Soundpad running** (Remote Control enabled).
- JDK 21 (Gradle downloads it automatically if missing).
- `yt-dlp` and `ffmpeg` for the YouTube source. The packaged app **bundles both**; when running from source,
  `gradlew fetchTools` downloads them into `tools/` and checks their SHA-256 against the published checksums
  (or install them yourself: `winget install yt-dlp ffmpeg`). Lookup order: next to the app jar, `tools/`, WinGet folder, `PATH`.

## Run and build

| Command | Result |
|---|---|
| `gradlew fetchTools` | Downloads and verifies `yt-dlp.exe` and `ffmpeg.exe` into `tools/` (~130 MB, once) |
| `gradlew run` | Starts the app |
| `gradlew test` | Runs the tests (also builds the example plugin and loads it) |
| `gradlew build` | Compiles and tests everything |
| `gradlew packageApp` | Downloads the tools if needed, then builds a portable app with its own JRE in `build/dist/SoundpadPlus/` |
| `gradlew packageInstaller` | `.exe` installer in `build/dist/` (needs [WiX 3](https://github.com/wixtoolset/wix3/releases): unzip `wix314-binaries.zip` into `%LOCALAPPDATA%\wix3`) |

The bundled `ffmpeg` is the GPL "essentials" build from gyan.dev: check its license terms before redistributing the installer.
Bump `appVersion` in `build.gradle.kts` on every release: Windows only upgrades to a higher version.

## Adding sources (plugin API)

A source only has to **search** and say **where each audio is**. The app handles the UI, the Soundpad category,
adding the sound, playing it and removing it. There are two kinds of plugin, both dropped in
`%USERPROFILE%\.soundpadplus\plugins\` (restart the app afterwards).

| | JSON plugin (recommended) | Jar plugin |
|---|---|---|
| Contains code | No | Yes (Kotlin/Java) |
| Can do | Call HTTPS URLs on the hosts it declares, read a JSON list, download the audios it points to | Anything |
| Permissions | Only those | **Everything the user can do** |
| Loaded | Automatically | Only after the user approves its SHA-256 |

### JSON plugin

Use it when the source has a JSON API that returns direct links to audio files. See `examples/json-source/example.json`.

```json
{
  "id": "my-source",
  "name": "My Source",
  "defaultCategory": "Effects",
  "searchHint": "Search my source…",
  "hosts": ["api.example.com", "cdn.example.com"],
  "searchUrl": "https://api.example.com/search?q={query}",
  "resultsPath": "data.items",
  "fields": { "title": "name", "subtitle": "tags", "duration": "length", "audioUrl": "files.mp3" }
}
```

- `hosts`: the only hosts (and their subdomains) the plugin may talk to. Search URL and audio URLs must be `https` and match one.
- `resultsPath`: dotted path to the list in the answer. `fields`: dotted path of each value inside an item
  (`title` and `audioUrl` are required). Up to 50 results are used.
- Preview (double click) is offered for `.mp3` links.

### Jar plugin

Use it when you need real code (scraping, a local tool, a non-JSON API).

```kotlin
class MySource : AudioSource {
    override val id = "my-source"                // unique and stable: settings are stored under it
    override val name = "My Source"              // shown in the source list
    override val defaultCategory = "Effects"     // Soundpad category until the user changes it
    override val searchHint = "Search my source…"

    override fun search(query: String): List<Entry> =       // runs on a background thread
        listOf(Entry("Title", "Subtitle", previewUrl = "https://…/a.mp3", duration = "0:05") { progress ->
            Downloads.fromUrl("https://…/a.mp3", "Title", progress)   // returns the saved File
        })
}
```

- `Entry.download` must save the audio and return the `File`. Report progress with `progress(0..100)`, or `progress(-1)` if unknown.
  Use `Downloads.dir` and `Downloads.safeName(...)` for files (Soundpad only understands ANSI paths).
- Always use `Downloads.open / readText / fromUrl` for network access: they enforce HTTPS, block private/loopback
  addresses, check every redirect and cap sizes (2 MB text, 100 MB audio).
- Throw an exception with a readable message to show an error in the status bar.
- Gson and JLayer are on the classpath.

Packaging:

1. Create a Gradle project with `compileOnly` on the app (see `examples/example-source/build.gradle.kts`;
   outside this repo use `compileOnly(files("path/to/SoundpadPlus.jar"))`).
2. Register the class in `src/main/resources/META-INF/services/com.emprecio.soundpadplus.api.AudioSource`
   (one fully qualified class name per line). It needs a public no-argument constructor.
3. Build the jar and copy it to the plugins folder. On first start the app shows the file name and its SHA-256 and asks
   for permission. The approval is stored for that exact file: any change to the jar asks again.

A plugin with an id already in use, or one that fails to load, is skipped and the reason is printed to stderr.
The packaged app ships a trimmed JRE: if your plugin needs extra JDK modules, add them with `--add-modules` in the `jpackage` call.

## Security

What the app does to keep plugins, the network and Soundpad safe:

- **No silent code execution**: jar plugins never load without the user's approval of their exact hash, and the approved
  file is copied before hashing so it cannot be swapped in between. JSON plugins are data, not code.
- **Network**: every request (built-in sources and plugins) is HTTPS only, rejects credentials in URLs and private,
  loopback, link-local and multicast addresses (no access to the local network), re-checks each redirect and has size and time limits.
- **Text from sources is never trusted**: titles, errors and names are stripped of control characters and HTML before
  being shown (Swing would otherwise render `<html>` and load remote images).
- **Soundpad pipe**: category names cannot contain quotes, backslashes or control characters, so they cannot add extra commands.
- **Tools**: `yt-dlp` is called with `--` before the URL and a validated video id; executables are only taken from the app folder,
  `tools/`, WinGet or the `PATH` as absolute paths (never from the current directory). Downloads of the bundled tools are checked against the published SHA-256.

Limits you should know about: a **jar plugin cannot be sandboxed** on the JVM (the SecurityManager is gone), so once approved it has
the user's full permissions: approve only code you trust. The SHA-256 of `yt-dlp`/`ffmpeg` is fetched from the same site as the file,
which detects corruption and tampering of the file but not a compromised release. Resolved DNS addresses are checked before connecting,
not pinned, so a hostile DNS server could still race the check.

## Languages

Texts live in `src/main/resources/i18n/<code>.properties` (UTF-8, `{0}`, `{1}`… are arguments). English is the fallback for
missing keys. To add a language, copy `en.properties`, translate the values and set `language.name`. Either:

- put it in `src/main/resources/i18n/` and add its code to `bundled` in `I18n.kt`; or
- (for users) drop it in `%USERPROFILE%\.soundpadplus\lang\<code>.properties`; no rebuild needed.

The language is chosen in Settings → General (default: system language) and applies on next start.
A test checks that every bundled language has the same keys as English.

## Settings

Stored in `~/.soundpadplus.properties`: `cat.<source id>` (destination category), `deleteFile`, `language`, `approvedPlugins` (hashes).
