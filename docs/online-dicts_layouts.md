## Online dictionary download flow

1. **Manifest retrieval**
   - App queries `https://palsoftware.github.io/pastiera-dict/dicts-manifest.json` via `DictionaryRepositoryManager.fetchManifest()`.
   - Response is parsed into `DictionaryManifest`/`DictionaryItem`, logging success/failure and counting available entries.
   - Manifest errors or empty responses are reported in logs/snackbar and surface a warning message on the UI.

2. **Dictionary discovery**
   - `InstalledDictionariesScreen` runs on compose start and immediately triggers the manifest fetch via `LaunchedEffect`.
   - Local dictionaries (assets + previously imported) are loaded via `loadSerializedDictionaries()` and merged with the downloaded manifest.
   - `mergeDictionaries()` creates `UnifiedDictionaryItem`s: for each unique filename it prefers installed metadata, falls back to the manifest `name`, and ultimately uses `getLanguageDisplayName()` if nothing else is available.

3. **UI representation**
   - The unified list shows badges for `Installed`, `Imported`, and `Available online`.
   - Available-online dictionaries display the manifest-provided `name` (e.g., “Italian (Basic)”), the file size (`bytes`), and a download icon.
   - Download progress is presented via `LinearProgressIndicator`, and snackbars report success/errors.

4. **Download and validation**
   - When the user taps download, `DictionaryRepositoryManager.downloadDictionary()` requests the individual `url`, streams the file to a temp location, and reports progress via `onProgress`.
   - Upon completion the SHA256 checksum is computed and validated against `sha256` from the manifest.
   - The file is once validated via `validateDictionaryStream()` (deserializing `DictionaryIndex`) before being moved into `files/dictionaries_serialized/custom`.
   - Any failure (network, hash mismatch, serialization) returns a specific `DownloadResult` so the UI can display the proper `installed_dictionaries_*` string.

5. **Refresh & uninstall**
   - Users can refresh the manifest with the refresh icon, which reruns `fetchManifest()`.
   - Imported or downloaded dictionaries can be uninstalled, which deletes the corresponding file from `dictionaries_serialized/custom`. Built-in assets are not deletable.

6. **Logging**
   - Extensive logging covers manifest fetch, merge steps, installed dictionary loading, download progress, and uninstall attempts, each tagged with `InstalledDictionaries` or `DictionaryRepositoryManager`.

7. **Localization**
   - All new UI strings (download messages, uninstall confirmations, refresh, progress text) were added for every supported locale (`en`, `it`, `de`, `es`, `fr`, `pl`, `ru`, `hy`).

8. **Directory layout**
   - The dictionary-related Kotlin files now live under `it.palsoftware.pastiera.dictionaries` for cleaner separation.

## Online layout download flow
- The app queries `https://palsoftware.github.io/pastiera-dict/layouts-manifest.json` via `LayoutRepositoryManager.fetchManifest()` and parses it into `LayoutManifest`/`LayoutItem`.
- `KeyboardLayoutSettingsScreen` exposes the new cloud repository path via the `+` action, which now shows a popup giving the choice between importing from a local JSON (current behavior) and opening the cloud layout manager (`OnlineLayoutsActivity`).
- `OnlineLayoutsActivity` fetches the manifest on launch, lists manifest entries with badges for already installed layouts, and allows refresh/download/uninstall actions with the same SHA-256 + JSON validation pipeline built into `LayoutRepositoryManager.downloadLayout()`.
- Downloads stream into a cache file, validate hash and format via `LayoutFileStore`, and finally move files into `files/keyboard_layouts/<name>.json`; `LayoutMappingRepository.getAvailableLayouts()` automatically sees new files.
- Snackbars summarize success/failure states (`network`, `format`, `hash`, `copy`), and layout deletions ask for confirmation before removing the JSON file.
