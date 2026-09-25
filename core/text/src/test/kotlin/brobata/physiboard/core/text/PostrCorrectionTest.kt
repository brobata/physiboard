package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.Action
import kotlin.test.Test
import kotlin.test.assertEquals

/** Titan, 2026-09-25: "postr" + Space in Messages committed "postr " although the strip offered poster first. */
class PostrCorrectionTest {
    private val en = LanguageCode.of("en")!!

    @Test
    fun `postr and a space becomes poster`() {
        val index = DictionaryIndex.build(en, listOf("poster" to 132, "posts" to 144, "poser" to 94, "post" to 164).map { WordFrequency(it.first, it.second) })
        val resources = TextInputResources(dictionaries = listOf(index))
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, useKeyboardProximity = true), rankingOptions = RankingOptions(useKeyboardProximity = true), lengthChangeAllowance = LengthChangeAllowance.forLanguage("en"))
        var state = TextInputState()
        var text = ""
        fun key(action: Action): TextInputResult {
            val editor = EditorSnapshot(textBeforeCursor = text)
            val result = TextInputPipeline.handle(TextInputRequest.Key(action), FieldContext(FieldKind.NORMAL), settings, resources, state, editor)
            state = result.state
            for (op in result.ops) when (op) {
                is EditorOp.CommitText -> text += op.text
                is EditorOp.ReplaceBeforeCursor -> text = text.dropLast(op.count) + op.text
                is EditorOp.DeleteSurrounding -> text = text.dropLast(op.before)
                else -> Unit
            }
            return result
        }
        for (ch in "postr") key(Action.Commit(ch.toString()))
        val result = key(Action.Commit(" "))
        assertEquals("poster ", text, "ops at the space: ${result.ops}; settings: ${settings.autocorrect}")
    }
}
