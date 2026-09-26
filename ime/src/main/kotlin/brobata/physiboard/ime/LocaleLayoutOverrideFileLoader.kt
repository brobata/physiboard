package brobata.physiboard.ime

import android.content.Context
import brobata.physiboard.core.subtype.LocaleLayoutMapping
import java.io.File

/**
 * Reads `locale_layout_mapping.json`, the per-locale layout override the Input Languages screen's
 * "System" rows write (`:app`'s `LocaleLayoutOverrideStore`). spec: dictionaries-languages.md SS10.
 *
 * Before this class existed, the file was written but never read anywhere under `:ime`: a base
 * subtype's overridden layout looked saved (the screen shows "Layout mapping updated") but the
 * running keyboard kept typing with the locale's hardcoded default. `:core:subtype`'s
 * [LocaleLayoutMapping.decodeOverride] is the shared parser, the same split
 * `CtrlMappingCodec`/`CtrlMappingFileLoader` uses for the Fn Layer map.
 *
 * The file is a handful of short JSON lines at most (one entry per overridden base-subtype
 * locale), so this reads it synchronously on the caller's thread, the same reasoning
 * [CtrlMappingFileLoader] gives for `ctrl_key_mappings.json`.
 */
internal class LocaleLayoutOverrideFileLoader(private val context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    /** An unreadable or absent file decodes to an empty override (never throws), matching [LocaleLayoutMapping.decodeOverride]. */
    fun load(): Map<String, String> {
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
        return LocaleLayoutMapping.decodeOverride(text)
    }

    private companion object {
        const val FILE_NAME = "locale_layout_mapping.json"
    }
}
