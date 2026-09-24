package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.keys.Action
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Types real English words, through the real bundled dictionary asset, all the way through
 * [TextInputPipeline]'s boundary handling, and proves the project's standing invariant (spec:
 * autocorrect-suggestions.md §10, "a correctly spelled word is never overwritten") on the actual
 * shipped list rather than a small synthetic one. [KeyboardPipelineTest]-style fixtures elsewhere
 * prove the ordering rules with a handful of hand-picked entries; this file exists because a
 * coverage regression in the real 80,000-word list (exactly the defect §10 measured and fixed) is
 * invisible to those hand-picked fixtures.
 */
class EnglishDictionaryInvariantTest {

    private val normalField = FieldContext(FieldKind.NORMAL)
    private val settings = TextInputSettingsBundle(
        autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2),
        lengthChangeAllowance = 2,
    )

    private val dictionary: DictionaryIndex? by lazy {
        repoRoot()
            ?.resolve("app/src/main/assets/dictionaries/en.pbd")
            ?.takeIf { it.isFile }
            ?.readBytes()
            ?.let { DictionaryIndex.fromPbdBytes(it) }
    }

    @Test
    fun `T-words the old list once missed are typed as-is, not corrected to something else`() {
        val index = assertNotNull(dictionary, "app/src/main/assets/dictionaries/en.pbd is missing or failed to parse")
        // spec: autocorrect-suggestions.md SS10 names the exact false corrections the old list
        // produced: salve -> slave, lithe -> litre, dowdy -> dowry, flout -> flour.
        for (word in listOf("salve", "lithe", "dowdy", "flout", "gaunt", "glean", "canny", "imbue", "jostle", "loathe", "shirk", "spurn", "vex", "ember")) {
            assertEquals("$word ", typeThenSpace(word, index), "expected '$word' to survive a boundary untouched")
        }
    }

    @Test
    fun `T-a genuine typo one edit from a known word is still corrected`() {
        // Control: the invariant above is not simply "autocorrect never fires". "wierd" is one
        // transposition from "weird", which the real list carries at a high enough frequency,
        // and unlike "teh" (which completes to the more common "tehran" instead) has no
        // competing completion to out-score the fuzzy match.
        val index = assertNotNull(dictionary)
        assertEquals("weird ", typeThenSpace("wierd", index))
    }

    private fun typeThenSpace(word: String, index: DictionaryIndex): String {
        val resources = TextInputResources(dictionaries = listOf(index))
        var state = TextInputState()
        val field = ReplayField()
        for (ch in word) {
            val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(ch.toString())), normalField, settings, resources, state, field.snapshot())
            field.apply(result.ops)
            state = result.state
        }
        val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Commit(" ")), normalField, settings, resources, state, field.snapshot())
        field.apply(result.ops)
        return field.text
    }

    /** Minimal stand-in for a real `InputConnection`: replays [EditorOp]s against an in-memory string. */
    private class ReplayField {
        var text: String = ""
            private set
        private var cursor: Int = 0

        fun apply(ops: List<EditorOp>) {
            for (op in ops) {
                when (op) {
                    is EditorOp.CommitText -> {
                        text = text.substring(0, cursor) + op.text + text.substring(cursor)
                        cursor += op.text.length
                    }
                    is EditorOp.DeleteSurrounding -> {
                        val before = minOf(op.before, cursor)
                        val after = minOf(op.after, text.length - cursor)
                        text = text.substring(0, cursor - before) + text.substring(cursor + after)
                        cursor -= before
                    }
                    is EditorOp.ReplaceBeforeCursor -> {
                        val count = minOf(op.count, cursor)
                        text = text.substring(0, cursor - count) + op.text + text.substring(cursor)
                        cursor = cursor - count + op.text.length
                    }
                    is EditorOp.SetSelection -> cursor = op.end
                    else -> Unit
                }
            }
        }

        fun snapshot(limit: Int = 240): EditorSnapshot = EditorSnapshot(textBeforeCursor = text.substring(maxOf(0, cursor - limit), cursor))
    }

    private fun repoRoot(): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return null
    }
}
