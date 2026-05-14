package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.math.SkRect

/**
 * S7-A verification suite for [SkPathBuilder.computeBounds] — the
 * builder-side bbox helper that lets callers skip the
 * `builder.snapshot().computeBounds()` allocation. Covers the
 * empty-builder edge case + every primitive verb (line / quad /
 * conic / cubic / oval / rect) and asserts the result matches
 * `SkPath.computeBounds()` of the equivalent snapshot.
 */
class SkPathBuilderComputeBoundsTest {

    @Test
    fun `empty builder yields the empty origin rect`() {
        val builder = SkPathBuilder()
        val bounds = builder.computeBounds()
        assertEquals(SkRect.MakeLTRB(0f, 0f, 0f, 0f), bounds)
    }

    @Test
    fun `lines bbox matches snapshot computeBounds`() {
        val builder = SkPathBuilder()
            .moveTo(2f, 3f)
            .lineTo(10f, 4f)
            .lineTo(5f, 12f)
        val expected = builder.snapshot().computeBounds()
        assertEquals(expected, builder.computeBounds())
    }

    @Test
    fun `quad bbox matches snapshot computeBounds`() {
        val builder = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(5f, 10f, 10f, 0f)
        val expected = builder.snapshot().computeBounds()
        assertEquals(expected, builder.computeBounds())
    }

    @Test
    fun `cubic bbox matches snapshot computeBounds`() {
        val builder = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(2f, 8f, 6f, 12f, 10f, 0f)
        val expected = builder.snapshot().computeBounds()
        assertEquals(expected, builder.computeBounds())
    }

    @Test
    fun `conic bbox matches snapshot computeBounds`() {
        val builder = SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(5f, 5f, 10f, 0f, 0.7f)
        val expected = builder.snapshot().computeBounds()
        assertEquals(expected, builder.computeBounds())
    }

    @Test
    fun `addRect bbox matches the rect itself`() {
        val rect = SkRect.MakeLTRB(2f, 4f, 14f, 22f)
        val builder = SkPathBuilder().addRect(rect)
        assertEquals(rect, builder.computeBounds())
        assertEquals(builder.snapshot().computeBounds(), builder.computeBounds())
    }

    @Test
    fun `addOval bbox matches the bounding rect`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 20f, 10f)
        val builder = SkPathBuilder().addOval(rect)
        // Oval verbs touch the rect corners as conic controls so the
        // conservative bound matches the bounding rect exactly.
        assertEquals(rect, builder.computeBounds())
        assertEquals(builder.snapshot().computeBounds(), builder.computeBounds())
    }

    @Test
    fun `mixed contour bbox matches snapshot bbox after a close`() {
        val builder = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .quadTo(15f, 5f, 10f, 10f)
            .cubicTo(5f, 12f, 0f, 12f, -2f, 6f)
            .close()
        val expected = builder.snapshot().computeBounds()
        assertEquals(expected, builder.computeBounds())
    }
}
