package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: rebuild-from-scratch.md "The editor is not a reliable narrator" points 2 and 3. */
class EditorTrustTest {

    @Test
    fun `full trust allows context rules and a composing region`() {
        assertTrue(EditorTrust.FULL.contextRulesAllowed)
        assertTrue(EditorTrust.FULL.composingRegionAllowed)
    }

    @Test
    fun `possibly stale reads turn off context rules`() {
        val trust = EditorTrust(reads = EditorReadTrust.POSSIBLY_STALE)
        assertFalse(trust.contextRulesAllowed)
    }

    @Test
    fun `unavailable reads turn off context rules`() {
        val trust = EditorTrust(reads = EditorReadTrust.UNAVAILABLE)
        assertFalse(trust.contextRulesAllowed)
    }

    @Test
    fun `composing safety is independent of read trust`() {
        // A field can answer reads reliably while still mishandling a composing region: the plan's
        // own failure table lists these as two separate ways an editor misbehaves.
        val trust = EditorTrust(reads = EditorReadTrust.RELIABLE, composing = ComposingSafety.UNSAFE)
        assertTrue(trust.contextRulesAllowed)
        assertFalse(trust.composingRegionAllowed)
    }
}
