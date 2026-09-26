package brobata.physiboard.app.settings

import android.content.Context
import brobata.physiboard.core.keys.LayoutFileCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads, imports and deletes `files/keyboard_layouts/<name>.json`, the user's own custom layouts
 * (layers-sym-alt.md SS9.3). Parsing is [LayoutFileCodec]'s own pure work; this class only owns
 * the directory. "A custom file with the same name as a bundled layout takes precedence" (SS9.3)
 * is the caller's own concern (the layout catalogue merge), not this store's.
 */
class CustomLayoutStore(context: Context) {
    private val dir = File(context.filesDir, "keyboard_layouts").apply { mkdirs() }

    /** One custom layout this device already has: its stored name and the [LayoutFileCodec.ParsedLayout] its file still parses to (or null when the file has gone stale and no longer parses). */
    data class CustomLayout(val name: String, val parsed: LayoutFileCodec.ParsedLayout?)

    /** SS9.3: "the available list is the union of custom names and bundled names, sorted", this half of it. */
    suspend fun list(): List<CustomLayout> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .sortedBy { it.nameWithoutExtension }
            .map { file -> CustomLayout(file.nameWithoutExtension, LayoutFileCodec.decode(runCatching { file.readText() }.getOrNull())) }
    }

    /**
     * SS9.3's "Import from file": [text] is the picked document's already-read contents. On a
     * successful parse, "the name is the file's `name` field, else `imported_<epoch ms>`; the raw
     * text is saved after a successful parse." Returns the resolved name on success, null when
     * [text] does not parse (caller shows "Failed to import layout").
     */
    suspend fun import(text: String): String? = withContext(Dispatchers.IO) {
        val parsed = LayoutFileCodec.decode(text) ?: return@withContext null
        val name = parsed.name?.takeIf { it.isNotBlank() } ?: "imported_${System.currentTimeMillis()}"
        runCatching { File(dir, "$name.json").writeText(text) }.getOrNull() ?: return@withContext null
        name
    }

    /** SS9.3: "Delete: only custom layouts (never `qwerty`)"; the caller enforces that, this just removes the file. */
    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        File(dir, "$name.json").let { !it.exists() || it.delete() }
    }
}
