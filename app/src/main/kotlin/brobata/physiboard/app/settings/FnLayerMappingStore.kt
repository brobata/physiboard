package brobata.physiboard.app.settings

import android.content.Context
import brobata.physiboard.core.keys.CtrlMappingCodec
import brobata.physiboard.core.keys.CtrlMappingTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The file half of the Fn Layer key grid: reads and writes `ctrl_key_mappings.json` in the app's
 * private files directory, the same file the keyboard loads (trackpad-caret-nav.md SS5.4, SS5.9).
 * `:core:keys`' [CtrlMappingCodec] is the shared parser; only `:app` touches [File] or [Context].
 */
class FnLayerMappingStore(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    /** SS5.4: "loaded from the private files directory when it exists, else from the assets." */
    suspend fun load(): CtrlMappingTable = withContext(Dispatchers.IO) {
        val text = runCatching { if (file.exists()) file.readText() else assetText() }.getOrNull()
        CtrlMappingCodec.decode(text)
    }

    /** SS5.8: "Save writes the whole 26-key map to the private file." */
    suspend fun save(table: CtrlMappingTable): Boolean = withContext(Dispatchers.IO) {
        runCatching { file.writeText(CtrlMappingCodec.encode(table)) }.isSuccess
    }

    /** SS5.8 "Revert to Default": deletes the private mapping file, so the next load falls back to the asset. */
    suspend fun revertToDefault(): CtrlMappingTable = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        CtrlMappingCodec.decode(assetText())
    }

    private fun assetText(): String? = runCatching { context.assets.open(ASSET_PATH).use { it.readBytes().decodeToString() } }.getOrNull()

    private companion object {
        const val FILE_NAME = "ctrl_key_mappings.json"
        const val ASSET_PATH = "common/ctrl/ctrl_key_mappings.json"
    }
}
