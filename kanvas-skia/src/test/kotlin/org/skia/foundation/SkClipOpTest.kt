package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkClipOpTest {

    @Test
    fun `two members in upstream order, ordinals matching kDifference=0 kIntersect=1`() {
        assertEquals(SkClipOp.kDifference, SkClipOp.entries[0])
        assertEquals(SkClipOp.kIntersect, SkClipOp.entries[1])
        assertEquals(0, SkClipOp.kDifference.ordinal)
        assertEquals(1, SkClipOp.kIntersect.ordinal)
    }

    @Test
    fun `entries returns exactly two values`() {
        assertEquals(2, SkClipOp.entries.size)
    }
}
