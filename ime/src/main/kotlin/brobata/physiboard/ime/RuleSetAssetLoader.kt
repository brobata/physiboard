package brobata.physiboard.ime

import android.content.res.AssetManager
import android.util.Log
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.RuleSetCodec
import java.io.IOException

/**
 * Reads a bundled `common/autocorrect/auto_corrections_<code>.json` asset and parses it into a
 * [RuleSet]. spec: autocorrect-suggestions.md SS8.1: bundled sets live at that path for codes
 * `it`, `en`, `es`, `fr`, `de`, `pl`; only `auto_corrections_en.json` ships in this build, so
 * [load] for any other code is a silent miss, same degradation [DictionaryAssetLoader] uses for a
 * dictionary that is not there: the keyboard already runs with no bundled set for that code, only
 * the user's own custom rules (if any) for it.
 *
 * Unlike a dictionary, one of these files is a few kilobytes, so reading it is synchronous, on
 * the caller's thread, and done once at session start rather than backgrounded.
 */
internal class RuleSetAssetLoader(private val assets: AssetManager) {
    fun load(code: String): RuleSet? {
        val body = try {
            assets.open("common/autocorrect/auto_corrections_$code.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return null
        }
        val ruleSet = RuleSetCodec.parse(code, body)
        if (ruleSet == null) Log.e(TAG, "common/autocorrect/auto_corrections_$code.json failed to parse")
        return ruleSet
    }

    /** [load] for every code in [codes], keyed by code, silently dropping every code with no shipped file. */
    fun loadAll(codes: Set<String>): Map<String, RuleSet> = codes.mapNotNull { code -> load(code)?.let { code to it } }.toMap()

    private companion object {
        const val TAG = "PhysiBoardDict"
    }
}
