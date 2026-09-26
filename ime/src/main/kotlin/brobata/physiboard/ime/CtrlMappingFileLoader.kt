package brobata.physiboard.ime

import android.content.Context
import brobata.physiboard.core.keys.CtrlMappingCodec
import brobata.physiboard.core.keys.CtrlMappingTable
import java.io.File

/**
 * Reads `ctrl_key_mappings.json`, the Fn Layer map the settings screen's key grid editor
 * (`:app`'s `FnLayerMappingStore`) and the running keyboard share. spec: trackpad-caret-nav.md
 * SS5.4 ("The mapping file is loaded from the private files directory when it exists, else from
 * the assets... If the file cannot be read, the asset is used"), SS5.9 ("`ctrl_key_mappings.json`
 * in the private files dir").
 *
 * `:app` depends on `:ime`, not the other way round (see [SettingsSource]'s own KDoc), so this
 * class cannot reuse `FnLayerMappingStore` directly even though both read the identical file from
 * the identical `context.filesDir` (the keyboard service and the settings app share one process).
 * `:core:keys`' [CtrlMappingCodec] is the shared parser either side decodes with, so a mapping
 * saved by the settings screen means the same thing to both readers.
 *
 * The file is a handful of short JSON lines (26 entries at most), so this reads it synchronously
 * on the caller's thread, the same way [KeyboardSession] already loads the bundled rule sets
 * (`RuleSetAssetLoader`) at construction: no background thread earns its keep for a file this
 * small, and the Fn Layer map has to be in place before the very first key event, not after a
 * posted callback lands.
 */
internal class CtrlMappingFileLoader(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    /** spec SS5.4: "loaded from the private files directory when it exists, else from the assets." An unreadable file or asset decodes to an empty table (every key `none`), never throws. */
    fun load(): CtrlMappingTable {
        val text = runCatching { if (file.exists()) file.readText() else assetText() }.getOrNull()
        return CtrlMappingCodec.decode(text)
    }

    private fun assetText(): String? = runCatching { context.assets.open(ASSET_PATH).use { it.readBytes().decodeToString() } }.getOrNull()

    private companion object {
        const val FILE_NAME = "ctrl_key_mappings.json"
        const val ASSET_PATH = "common/ctrl/ctrl_key_mappings.json"
    }
}
