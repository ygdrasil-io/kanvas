package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkTextBlob] / [SkTextBlobBuilder] (Phase I1).
 *
 * Coverage :
 *  - Empty builder yields `null` from `make()`.
 *  - `allocRun` produces a `HorizontalSpread` run with the supplied
 *    `(x, y)` origin and zeroed glyph array.
 *  - `allocRunPosH` produces a `HorizontalPositions` run with
 *    per-glyph X array + constant Y.
 *  - `allocRunPos` produces a `FullPositions` run with `count*2`
 *    interleaved positions.
 *  - `make()` returns the blob and clears the builder ; a second
 *    `make()` returns `null`.
 *  - `bounds()` is a non-empty rect when at least one run was
 *    allocated.
 *  - The glyph array exposed by [SkTextBlobBuilder.Allocation] is
 *    captured by reference — mutating it after `allocRun` but
 *    before `make()` is reflected in the resulting blob.
 */
class SkTextBlobTest {

    @Test
    fun `empty builder make returns null`() {
        val builder = SkTextBlobBuilder()
        assertNull(builder.make())
    }

    @Test
    fun `allocRun produces HorizontalSpread run with origin`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRun(font, count = 3, x = 10f, y = 50f)
        assertEquals(3, rec.glyphs.size)
        rec.glyphs[0] = 65; rec.glyphs[1] = 66; rec.glyphs[2] = 67
        val blob = builder.make()!!
        assertEquals(1, blob.runs.size)
        val run = blob.runs[0] as SkTextBlob.Run.HorizontalSpread
        assertEquals(10f, run.x)
        assertEquals(50f, run.y)
        assertEquals(3, run.glyphIds.size)
        assertEquals(65, run.glyphIds[0])
    }

    @Test
    fun `allocRunPosH produces HorizontalPositions run with per-glyph X`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPosH(font, count = 4, y = 100f)
        assertEquals(4, rec.glyphs.size)
        assertEquals(4, rec.pos.size)
        rec.glyphs[0] = 70; rec.glyphs[1] = 71; rec.glyphs[2] = 72; rec.glyphs[3] = 73
        rec.pos[0] = 0f; rec.pos[1] = 10f; rec.pos[2] = 25f; rec.pos[3] = 40f
        val blob = builder.make()!!
        val run = blob.runs[0] as SkTextBlob.Run.HorizontalPositions
        assertEquals(100f, run.constY)
        assertEquals(0f, run.xs[0])
        assertEquals(40f, run.xs[3])
    }

    @Test
    fun `allocRunPos produces FullPositions run with interleaved positions`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 16f)
        val builder = SkTextBlobBuilder()
        val rec = builder.allocRunPos(font, count = 2)
        assertEquals(2, rec.glyphs.size)
        assertEquals(4, rec.pos.size)
        rec.glyphs[0] = 80; rec.glyphs[1] = 81
        rec.pos[0] = 5f; rec.pos[1] = 10f
        rec.pos[2] = 25f; rec.pos[3] = 30f
        val blob = builder.make()!!
        val run = blob.runs[0] as SkTextBlob.Run.FullPositions
        assertEquals(4, run.positions.size)
        assertEquals(5f, run.positions[0])
        assertEquals(30f, run.positions[3])
    }

    @Test
    fun `make clears the builder so second call returns null`() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        builder.allocRun(font, count = 1, x = 0f, y = 0f)
        assertNotNull(builder.make())
        assertNull(builder.make())
    }

    @Test
    fun `bounds is non-empty after a run is allocated`() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        builder.allocRun(font, count = 3, x = 10f, y = 50f)
        val blob = builder.make()!!
        val b = blob.bounds()
        assertTrue(b.width() > 0f && b.height() > 0f) {
            "bounds=$b expected non-empty"
        }
    }

    @Test
    fun `allocation glyph array is captured by reference`() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val rec = builder.allocRun(font, count = 2, x = 0f, y = 0f)
        // Set glyphs after allocation but before make — the blob
        // should observe the post-set values.
        rec.glyphs[0] = 100
        rec.glyphs[1] = 101
        val blob = builder.make()!!
        val run = blob.runs[0] as SkTextBlob.Run.HorizontalSpread
        assertEquals(100, run.glyphIds[0])
        assertEquals(101, run.glyphIds[1])
    }

    @Test
    fun `multiple runs accumulate in the blob`() {
        val builder = SkTextBlobBuilder()
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        builder.allocRun(font, count = 2, x = 0f, y = 0f)
        builder.allocRunPosH(font, count = 3, y = 50f)
        builder.allocRunPos(font, count = 1)
        val blob = builder.make()!!
        assertEquals(3, blob.runs.size)
        assertTrue(blob.runs[0] is SkTextBlob.Run.HorizontalSpread)
        assertTrue(blob.runs[1] is SkTextBlob.Run.HorizontalPositions)
        assertTrue(blob.runs[2] is SkTextBlob.Run.FullPositions)
    }
}
