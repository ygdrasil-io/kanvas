package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkRect

/**
 * Unit tests for [SkPathUtils.FillPathWithPaint]. Covers the three
 * pipeline branches : fill-only paint (passthrough), stroke paint
 * (stroker runs and produces a non-empty outline), and the
 * `dst` reset contract (existing verbs in the destination are
 * discarded before the new outline is written).
 */
class SkPathUtilsTest {

    private fun lineSegment() = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()

    @Test
    fun `fill paint copies the source path verbatim`() {
        val src = lineSegment()
        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            strokeWidth = 4f
        }
        val dst = SkPathBuilder()
        val ok = SkPathUtils.FillPathWithPaint(src, paint, dst)
        assertTrue(ok)
        val outline = dst.detach()
        // Fill-style + no path effect ⇒ identity passthrough.
        assertEquals(src.countVerbs(), outline.countVerbs())
        assertFalse(outline.isEmpty())
    }

    @Test
    fun `stroke paint produces a filled outline rectangle`() {
        val src = lineSegment()
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 4f
            strokeCap = SkPaint.Cap.kButt_Cap
            strokeJoin = SkPaint.Join.kMiter_Join
        }
        val dst = SkPathBuilder()
        val ok = SkPathUtils.FillPathWithPaint(src, paint, dst)
        assertTrue(ok)
        val outline = dst.detach()
        // A butt-capped stroke of a horizontal line yields a non-empty
        // closed quad — at least 4 distinct verbs (move + 3 lines + close).
        assertFalse(outline.isEmpty())
        assertTrue(outline.countVerbs() >= 4) { "expected >= 4 verbs, got ${outline.countVerbs()}" }
    }

    @Test
    fun `dst is reset before the outline is written`() {
        val src = lineSegment()
        val paint = SkPaint().apply { style = SkPaint.Style.kFill_Style }
        val dst = SkPathBuilder()
            .moveTo(100f, 100f).lineTo(200f, 100f) // pre-existing junk verbs
        SkPathUtils.FillPathWithPaint(src, paint, dst)
        val outline = dst.detach()
        // The pre-existing junk should be gone; verb count matches src.
        assertEquals(src.countVerbs(), outline.countVerbs())
    }

    @Test
    fun `cullRect argument is accepted without changing behaviour`() {
        val src = lineSegment()
        val paint = SkPaint().apply { style = SkPaint.Style.kFill_Style }
        val dst = SkPathBuilder()
        val ok = SkPathUtils.FillPathWithPaint(
            src, paint, dst,
            cullRect = SkRect.MakeLTRB(0f, 0f, 100f, 100f),
        )
        assertTrue(ok)
    }
}
