package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals

class EditDistanceTest {

    @Test
    fun `T-identical strings are distance 0`() {
        assertEquals(0, EditDistance.osaDistance("cat", "cat"))
    }

    @Test
    fun `T-kitten to sitting is the textbook distance 3`() {
        assertEquals(3, EditDistance.osaDistance("kitten", "sitting"))
    }

    @Test
    fun `T-an adjacent transposition costs 1`() {
        assertEquals(1, EditDistance.osaDistance("ab", "ba"))
    }

    @Test
    fun `T-a single insertion costs 1`() {
        assertEquals(1, EditDistance.osaDistance("cat", "cats"))
    }

    @Test
    fun `T-a single deletion costs 1`() {
        assertEquals(1, EditDistance.osaDistance("cats", "cat"))
    }

    @Test
    fun `T-distance against the empty string is the other string's length`() {
        assertEquals(3, EditDistance.osaDistance("", "cat"))
        assertEquals(3, EditDistance.osaDistance("cat", ""))
    }

    @Test
    fun `T-a bounded query reports past the bound without computing the exact distance`() {
        val bounded = EditDistance.osaDistance("a", "abcdef", maxDistance = 1)
        assertEquals(2, bounded) // maxDistance + 1: the length gap (5) alone exceeds the bound
    }
}
