package brobata.physiboard.ime

import brobata.physiboard.core.keys.AccidentalPressSettings
import brobata.physiboard.core.keys.BounceKeySettings
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * spec: keys-and-modifiers.md SS10 (bounce filter), SS11 (accidental-press filter). Before this
 * wiring, `:core:keys`' `BounceFilter`/`AccidentalPressFilter` were fully implemented and tested
 * in isolation but had no caller anywhere in `:ime`, so turning either setting on changed nothing
 * a real keystroke could observe.
 */
class KeyboardPipelineKeyFiltersTest {

    private val layout = TitanLayouts.titan2EliteQwerty()
    private val editor = EditorSnapshot(textBeforeCursor = "")

    private fun letterDown(c: Char, deviceId: Int = 1, timeMs: Long) = KeyStroke(KeyId.Letter(c), KeyEdge.DOWN, 0, timeMs, deviceId = deviceId)

    @Test
    fun `bounce filter rejects a same-key repeat inside the delay window`() {
        val pipeline = KeyboardPipeline(layout = layout, settings = KeyboardSettings(bounceKeys = BounceKeySettings(enabled = true, delayMs = 80)))
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        val first = pipeline.onKeyStroke(letterDown('A', timeMs = 0), editor)
        val second = pipeline.onKeyStroke(letterDown('A', timeMs = 50), editor)

        assertTrue(first.consumed)
        assertTrue(first.ops.isNotEmpty(), "the first press must still type normally")
        assertTrue(second.consumed)
        assertTrue(second.ops.isEmpty(), "the bounced press must produce no commit at all")
    }

    @Test
    fun `with the bounce filter off, the same repeat types normally`() {
        val pipeline = KeyboardPipeline(layout = layout) // bounceKeys defaults to disabled
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        pipeline.onKeyStroke(letterDown('A', timeMs = 0), editor)
        val second = pipeline.onKeyStroke(letterDown('A', timeMs = 50), editor)

        assertTrue(second.consumed)
        assertTrue(second.ops.isNotEmpty())
    }

    @Test
    fun `the accidental-press filter rejects a second key held down on the same device`() {
        val pipeline = KeyboardPipeline(layout = layout, settings = KeyboardSettings(overlappingKeys = AccidentalPressSettings(enabled = true)))
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        pipeline.onKeyStroke(letterDown('A', timeMs = 0), editor) // held, no up yet
        val second = pipeline.onKeyStroke(letterDown('S', timeMs = 10), editor)

        assertTrue(second.consumed)
        assertTrue(second.ops.isEmpty(), "the overlapping press must be swallowed, not typed")
    }

    @Test
    fun `keys on different devices never trigger the overlap rule`() {
        val pipeline = KeyboardPipeline(layout = layout, settings = KeyboardSettings(overlappingKeys = AccidentalPressSettings(enabled = true)))
        pipeline.onStartInput(FieldContext(FieldKind.NORMAL))

        pipeline.onKeyStroke(letterDown('A', deviceId = 1, timeMs = 0), editor)
        val second = pipeline.onKeyStroke(letterDown('S', deviceId = 2, timeMs = 10), editor)

        assertFalse(second.ops.isEmpty(), "a different device's key must not be swallowed")
    }
}
