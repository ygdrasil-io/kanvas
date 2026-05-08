package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * Q3.2 verification suite for [SkPictureRecorder] / [SkPicture]
 * playback culling via the bounding-box hierarchy.
 *
 * Two contracts under test :
 *
 *  1. **Correctness — pixel parity** : a picture recorded with an
 *     [SkBBHFactory] must replay to the **same pixels** as one
 *     recorded without, regardless of the playback canvas's clip.
 *     The cull is a perf optimisation, never a semantic change.
 *
 *  2. **Effective cull** : when the playback clip is a strict
 *     sub-rect of the picture's [cullRect], the BBH must skip the
 *     ops that don't intersect. We verify by replaying through a
 *     counting [SkCanvas] subclass and comparing op counts with
 *     and without the BBH.
 */
class SkPictureBBHCullTest {

    private fun bitmap(w: Int = 80, h: Int = 80, bg: Int = SK_ColorWHITE): SkBitmap =
        SkBitmap(w, h).also { it.eraseColor(bg) }

    private fun paint(color: Int): SkPaint = SkPaint(color).apply { isAntiAlias = false }

    /**
     * Counting canvas — lets a test verify how many drawX calls
     * actually reach the canvas during playback. State ops aren't
     * counted (they always replay).
     */
    private class CountingCanvas(target: SkBitmap) : SkCanvas(target) {
        var drawCount: Int = 0
            private set

        override fun drawRect(rect: SkRect, paint: SkPaint) {
            drawCount++
            super.drawRect(rect, paint)
        }
        override fun drawOval(oval: SkRect, paint: SkPaint) {
            drawCount++
            super.drawOval(oval, paint)
        }
        override fun drawCircle(
            cx: org.skia.math.SkScalar,
            cy: org.skia.math.SkScalar,
            radius: org.skia.math.SkScalar,
            paint: SkPaint,
        ) {
            drawCount++
            super.drawCircle(cx, cy, radius, paint)
        }
    }

    // ─── Recording surface — picture carries the BBH ──────────────────

    @Test
    fun `recording without a factory produces a picture with no BBH`() {
        val recorder = SkPictureRecorder()
        recorder.beginRecording(80f, 80f).drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val pic = recorder.finishRecordingAsPicture()
        assertFalse(pic.hasBBH, "no factory → no BBH")
    }

    @Test
    fun `recording with SkRTreeFactory produces a picture carrying a BBH`() {
        val recorder = SkPictureRecorder()
        recorder.beginRecording(80f, 80f, SkRTreeFactory)
            .drawRect(SkRect.MakeWH(10f, 10f), paint(SK_ColorRED))
        val pic = recorder.finishRecordingAsPicture()
        assertTrue(pic.hasBBH, "factory present → BBH baked in")
    }

    @Test
    fun `empty recording with a factory still produces an empty picture without BBH`() {
        // Edge case — finishRecordingAsPicture on an unstarted recorder.
        val recorder = SkPictureRecorder()
        val pic = recorder.finishRecordingAsPicture()
        assertEquals(0, pic.opCount)
        assertFalse(pic.hasBBH, "empty record list → no BBH (no ops to index)")
    }

    // ─── Pixel parity — cull is invisible to the result ───────────────

    @Test
    fun `BBH playback is pixel-identical to non-BBH playback under full clip`() {
        // Three rects in different corners. Replay both pictures into
        // an 80×80 canvas (full clip → BBH search returns all ops).
        val recorderNoBBH = SkPictureRecorder().apply {
            beginRecording(80f, 80f).apply {
                drawRect(SkRect.MakeXYWH(0f, 0f, 20f, 20f), paint(SK_ColorRED))
                drawRect(SkRect.MakeXYWH(60f, 0f, 20f, 20f), paint(SK_ColorGREEN))
                drawRect(SkRect.MakeXYWH(0f, 60f, 20f, 20f), paint(SK_ColorBLUE))
            }
        }
        val noBBH = recorderNoBBH.finishRecordingAsPicture()

        val recorderBBH = SkPictureRecorder().apply {
            beginRecording(80f, 80f, SkRTreeFactory).apply {
                drawRect(SkRect.MakeXYWH(0f, 0f, 20f, 20f), paint(SK_ColorRED))
                drawRect(SkRect.MakeXYWH(60f, 0f, 20f, 20f), paint(SK_ColorGREEN))
                drawRect(SkRect.MakeXYWH(0f, 60f, 20f, 20f), paint(SK_ColorBLUE))
            }
        }
        val withBBH = recorderBBH.finishRecordingAsPicture()

        val noBBHResult = bitmap()
        val withBBHResult = bitmap()
        noBBH.playback(SkCanvas(noBBHResult))
        withBBH.playback(SkCanvas(withBBHResult))
        for (i in noBBHResult.pixels.indices) {
            assertEquals(
                noBBHResult.pixels[i],
                withBBHResult.pixels[i],
                "BBH playback must be pixel-identical at index $i",
            )
        }
    }

    @Test
    fun `BBH playback is pixel-identical to non-BBH playback under sub-rect clip`() {
        // Same three rects, but replay under a 30×30 clip in the upper-
        // left. Only the red rect intersects ; the green and blue must
        // be culled but the result must match the non-BBH walk.
        fun build(useBBH: Boolean): SkPicture {
            val recorder = SkPictureRecorder()
            val canvas = if (useBBH) {
                recorder.beginRecording(80f, 80f, SkRTreeFactory)
            } else {
                recorder.beginRecording(80f, 80f)
            }
            canvas.drawRect(SkRect.MakeXYWH(0f, 0f, 20f, 20f), paint(SK_ColorRED))
            canvas.drawRect(SkRect.MakeXYWH(60f, 0f, 20f, 20f), paint(SK_ColorGREEN))
            canvas.drawRect(SkRect.MakeXYWH(0f, 60f, 20f, 20f), paint(SK_ColorBLUE))
            return recorder.finishRecordingAsPicture()
        }

        val noBBH = build(useBBH = false)
        val withBBH = build(useBBH = true)

        fun playUnderClip(p: SkPicture): SkBitmap {
            val bm = bitmap()
            val c = SkCanvas(bm)
            c.clipRect(SkRect.MakeXYWH(0f, 0f, 30f, 30f))
            p.playback(c)
            return bm
        }

        val a = playUnderClip(noBBH)
        val b = playUnderClip(withBBH)
        for (i in a.pixels.indices) {
            assertEquals(a.pixels[i], b.pixels[i], "pixel $i must match under sub-rect clip")
        }
    }

    // ─── Effective cull — count draws that reach the playback canvas ──

    @Test
    fun `BBH culls draws whose bounds don't intersect the playback clip`() {
        // 3 rects across a 100-wide picture. Replay under a 20-wide
        // clip in the upper left → only the leftmost rect intersects.
        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 100f, SkRTreeFactory).apply {
            drawRect(SkRect.MakeXYWH(0f, 0f, 10f, 10f), paint(SK_ColorRED))
            drawRect(SkRect.MakeXYWH(40f, 0f, 10f, 10f), paint(SK_ColorGREEN))
            drawRect(SkRect.MakeXYWH(80f, 0f, 10f, 10f), paint(SK_ColorBLUE))
        }
        val pic = recorder.finishRecordingAsPicture()

        val counting = CountingCanvas(bitmap(100, 100))
        counting.clipRect(SkRect.MakeXYWH(0f, 0f, 20f, 20f))
        pic.playback(counting)

        assertEquals(
            1, counting.drawCount,
            "only the upper-left rect should be replayed (got ${counting.drawCount})",
        )
    }

    @Test
    fun `BBH does NOT cull when the playback clip covers the full cullRect`() {
        // Same picture as the cull test, but no clip → all 3 ops replay.
        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 100f, SkRTreeFactory).apply {
            drawRect(SkRect.MakeXYWH(0f, 0f, 10f, 10f), paint(SK_ColorRED))
            drawRect(SkRect.MakeXYWH(40f, 0f, 10f, 10f), paint(SK_ColorGREEN))
            drawRect(SkRect.MakeXYWH(80f, 0f, 10f, 10f), paint(SK_ColorBLUE))
        }
        val pic = recorder.finishRecordingAsPicture()

        val counting = CountingCanvas(bitmap(100, 100))
        pic.playback(counting)

        assertEquals(
            3, counting.drawCount,
            "no clip → all 3 ops should replay, got ${counting.drawCount}",
        )
    }

    @Test
    fun `BBH cull respects the recording-time CTM`() {
        // Translate (50, 0) before drawing a rect at (0,0,10,10).
        // The op's recorded device-space bounds = (50, 0, 60, 10).
        // Clipping the playback to the upper-left 20×20 must cull it.
        val recorder = SkPictureRecorder()
        recorder.beginRecording(100f, 100f, SkRTreeFactory).apply {
            translate(50f, 0f)
            drawRect(SkRect.MakeXYWH(0f, 0f, 10f, 10f), paint(SK_ColorRED))
        }
        val pic = recorder.finishRecordingAsPicture()

        val counting = CountingCanvas(bitmap(100, 100))
        counting.clipRect(SkRect.MakeXYWH(0f, 0f, 20f, 20f))
        pic.playback(counting)

        assertEquals(
            0, counting.drawCount,
            "translate-recorded rect at (50,0) must be culled by upper-left clip",
        )
    }

    @Test
    fun `BBH never under-includes — every visible rect still draws`() {
        // 100 small rects laid out in a 10×10 grid across (0..100, 0..100).
        // For any clip rect, the union of culled draws must include every
        // rect that overlaps the clip.
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(100f, 100f, SkRTreeFactory)
        for (j in 0 until 10) {
            for (i in 0 until 10) {
                canvas.drawRect(
                    SkRect.MakeXYWH(i * 10f, j * 10f, 8f, 8f),
                    paint(SK_ColorBLACK),
                )
            }
        }
        val pic = recorder.finishRecordingAsPicture()

        // Replay under a clip rect that overlaps a known sub-grid.
        val counting = CountingCanvas(bitmap(100, 100))
        // Clip = (15, 15) → (45, 45). Touches grid cells with i in {1, 2, 3, 4}
        // (because i*10..i*10+8 must overlap 15..45). i=1: 10..18 overlaps 15..45 ✓
        // i=2: 20..28 ✓. i=3: 30..38 ✓. i=4: 40..48 overlaps 15..45 (start 40, end 45) ✓
        // i=5: 50..58 vs 15..45 → miss. j same range. So 4×4 = 16 cells.
        counting.clipRect(SkRect.MakeXYWH(15f, 15f, 30f, 30f))
        pic.playback(counting)

        assertEquals(
            16, counting.drawCount,
            "16 cells should intersect a (15,15)→(45,45) clip in a 10-spaced grid",
        )
    }

    @Test
    fun `BBH cull preserves draw order`() {
        // Three overlapping rects, painted in order red → green → blue.
        // Each subsequent rect partially covers the previous. The final
        // pixel at (15, 15) must be blue regardless of culling.
        val recorder = SkPictureRecorder()
        recorder.beginRecording(80f, 80f, SkRTreeFactory).apply {
            drawRect(SkRect.MakeXYWH(0f, 0f, 30f, 30f), paint(SK_ColorRED))
            drawRect(SkRect.MakeXYWH(10f, 10f, 30f, 30f), paint(SK_ColorGREEN))
            drawRect(SkRect.MakeXYWH(20f, 20f, 30f, 30f), paint(SK_ColorBLUE))
        }
        val pic = recorder.finishRecordingAsPicture()

        val bm = bitmap()
        val c = SkCanvas(bm)
        // No clip — all three ops replay. Order matters : the last rect
        // covers (20, 20) → (50, 50), so pixel (25, 25) must be blue.
        pic.playback(c)
        assertEquals(SK_ColorBLUE, bm.getPixel(25, 25), "draw order preserved through BBH replay")
    }

    @Test
    fun `BBH cull short-circuits on empty playback clip`() {
        // Zero-area clip → no draws at all.
        val recorder = SkPictureRecorder()
        recorder.beginRecording(80f, 80f, SkRTreeFactory).apply {
            drawRect(SkRect.MakeXYWH(0f, 0f, 10f, 10f), paint(SK_ColorRED))
            drawRect(SkRect.MakeXYWH(20f, 20f, 10f, 10f), paint(SK_ColorGREEN))
        }
        val pic = recorder.finishRecordingAsPicture()

        val counting = CountingCanvas(bitmap())
        counting.clipRect(SkRect.MakeLTRB(0f, 0f, 0f, 0f))  // empty
        pic.playback(counting)
        assertEquals(0, counting.drawCount, "empty clip → zero ops dispatched")
    }
}
