package brobata.physiboard.ime

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.NgramPrefix
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.LengthChangeAllowance
import brobata.physiboard.core.text.RankingOptions
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextInputSettingsBundle
import brobata.physiboard.device.titan.TitanLayouts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Next-word learning (autocorrect-suggestions.md SS4) when the mix-up fix (SS10's exception)
 * rewrites the word before the one just finished: the pairs learned must be the fixed words', and
 * the pair learned one word earlier with the slip must be taken back. Typed key by key through
 * [KeyboardPipeline] on the shipped English dictionary and word-pair table.
 */
class MixupLearningTest {

    private companion object {
        fun assets(): File {
            var dir: File? = File(".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
            return File(checkNotNull(dir), "app/src/main/assets/dictionaries")
        }

        val dictionary: DictionaryIndex by lazy { assertNotNull(DictionaryIndex.fromPbdBytes(File(assets(), "en.pbd").readBytes())) }
        val model: ContextModel by lazy { assertNotNull(ContextModel.read(File(assets(), "en.bigrams").readBytes())) }
    }

    /** Net count per (prefix, word) from every learn and unlearn the pipeline reported. */
    private val counts = linkedMapOf<Pair<String, String>, Int>()

    private val pipeline = KeyboardPipeline(
        layout = TitanLayouts.titan2EliteQwerty(),
        resources = TextInputResources(dictionaries = listOf(dictionary), contextModel = model),
        settings = KeyboardSettings(
            textInput = TextInputSettingsBundle(
                lengthChangeAllowance = LengthChangeAllowance.ENGLISH,
                autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, fixWordMixups = true),
                rankingOptions = RankingOptions(useKeyboardProximity = true),
            ),
        ),
    ).also { p ->
        p.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.chat"))
        p.onBigramLearned = { _, prefix, word -> counts.merge(prefix to NgramPrefix.of(word), 1, Int::plus) }
        p.onBigramUnlearned = { _, prefix, word -> counts.merge(prefix to NgramPrefix.of(word), -1, Int::plus) }
    }

    private var text = ""
    private var now = 1_000L

    private fun press(key: KeyId) {
        val result = pipeline.onKeyStroke(KeyStroke(key, KeyEdge.DOWN, 0, now), EditorSnapshot(textBeforeCursor = text, nowMs = now))
        for (op in result.ops) {
            when (op) {
                is EditorOp.CommitText -> text += op.text
                is EditorOp.DeleteSurrounding -> text = text.dropLast(op.before)
                is EditorOp.ReplaceBeforeCursor -> text = text.dropLast(op.count) + op.text
                else -> Unit
            }
        }
        pipeline.onKeyStroke(KeyStroke(key, KeyEdge.UP, 0, now + 1), EditorSnapshot(textBeforeCursor = text, nowMs = now + 1))
        // Far enough apart that no two Spaces read as a double-space.
        now += 1_000
    }

    private fun type(keys: String) {
        for (ch in keys) press(if (ch == ' ') KeyId.Control(ControlKey.SPACE) else KeyId.Letter(ch.uppercaseChar()))
    }

    private fun net(prefix: String, word: String): Int = counts[prefix to word] ?: 0

    @Test
    fun `after a mix-up fix the fixed words are learned and the slip's pairs are not`() {
        type("it is bigger then mine ")
        assertEquals("it is bigger than mine ", text)
        assertEquals(1, net("bigger", "than"))
        assertEquals(1, net("than", "mine"))
        // The pair learned with the slip one word earlier is taken back, and none is learned after it.
        assertEquals(0, net("bigger", "then"))
        assertEquals(0, net("then", "mine"))
    }

    /** Every learn and take-back the pipeline reported, in order. app-shell.md SS31. */
    private val calls = mutableListOf<String>()

    @Test
    fun `in private mode the mix-up fix still happens but nothing is learned or taken back`() {
        pipeline.onBigramLearned = { _, prefix, word -> calls += "learn $prefix $word" }
        pipeline.onBigramUnlearned = { _, prefix, word -> calls += "unlearn $prefix $word" }
        pipeline.learningAllowed = false
        type("it is bigger then mine ")
        assertEquals("it is bigger than mine ", text, "private mode changes what is remembered, not how text is corrected")
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `a pair learned before private mode started is not taken back by a fix made during it`() {
        type("it is bigger then ")
        assertEquals(1, net("bigger", "then"))
        pipeline.learningAllowed = false
        type("mine ")
        assertEquals("it is bigger than mine ", text)
        assertEquals(1, net("bigger", "then"), "the take-back is a learn too; it waits for private mode to end")
        assertEquals(0, net("bigger", "than"))
        assertEquals(0, net("than", "mine"))
    }
}
