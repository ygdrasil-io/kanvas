package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C4 verification suite for [SkDrawable] + [SkCanvas.drawDrawable].
 *
 * **Behaviour under test** :
 *  - [SkDrawable.draw] balances the canvas's save stack — the
 *    drawable cannot leak matrix / clip / save mutations to the
 *    caller.
 *  - [SkCanvas.drawDrawable] without a matrix renders the drawable
 *    into the caller's current CTM ; with a matrix, it pre-concats
 *    that matrix.
 *  - The (x, y) overload translates by `(x, y)` before drawing.
 *  - [SkDrawable.notifyDrawingChanged] mutates the generation id ;
 *    consecutive [SkDrawable.getGenerationID] calls without a
 *    notification return the same value.
 *  - [SkDrawable.onGetBounds] default returns an empty rect ;
 *    subclass override is honoured by [SkDrawable.getBounds].
 */
class SkDrawableTest {

    private fun bitmap(w: Int = 40, h: Int = 40, bg: Int = SK_ColorWHITE): SkBitmap =
        SkBitmap(w, h).also { it.eraseColor(bg) }

    /** Trivial drawable that fills a rectangle with a solid colour. */
    private class RectDrawable(
        private val rect: SkRect,
        private val color: Int,
    ) : SkDrawable() {
        var drawCount: Int = 0
            private set

        override fun onDraw(canvas: SkCanvas) {
            drawCount++
            canvas.drawRect(rect, SkPaint(color).apply { isAntiAlias = false })
        }

        override fun onGetBounds(): SkRect = rect
    }

    @Test
    fun `drawable's onDraw runs when drawDrawable is called`() {
        val d = RectDrawable(SkRect.MakeXYWH(10f, 10f, 10f, 10f), SK_ColorRED)
        val bm = bitmap()
        SkCanvas(bm).drawDrawable(d)
        assertEquals(1, d.drawCount)
        assertEquals(SK_ColorRED, bm.getPixel(15, 15), "drawable's rect was rasterised")
    }

    @Test
    fun `drawDrawable preserves the canvas save count`() {
        // A drawable that mutates the matrix without restoring should
        // not leak — SkDrawable.draw wraps the call in save/restoreToCount.
        val canvas = SkCanvas(bitmap())
        val sneaky = object : SkDrawable() {
            override fun onDraw(canvas: SkCanvas) {
                canvas.save()
                canvas.translate(50f, 50f)
                // (no matching restore — but draw() should still rebalance)
            }
        }
        val saveCountBefore = canvas.getSaveCount()
        canvas.drawDrawable(sneaky)
        assertEquals(saveCountBefore, canvas.getSaveCount(),
            "drawDrawable must rebalance save stack even if the drawable forgets to restore")
    }

    @Test
    fun `drawDrawable with matrix pre-concats before onDraw`() {
        // The drawable draws a 10×10 red square at (0, 0). Calling
        // drawDrawable with translate(15, 15) should land the square
        // at canvas (15, 15) → (25, 25).
        val d = RectDrawable(SkRect.MakeXYWH(0f, 0f, 10f, 10f), SK_ColorRED)
        val bm = bitmap()
        SkCanvas(bm).drawDrawable(d, SkMatrix.MakeTrans(15f, 15f))
        assertEquals(SK_ColorRED, bm.getPixel(20, 20),
            "translated drawable rect should hit (20, 20)")
        assertEquals(SK_ColorWHITE, bm.getPixel(5, 5),
            "untranslated origin should remain background")
    }

    @Test
    fun `drawDrawable xy overload translates by x,y before onDraw`() {
        val d = RectDrawable(SkRect.MakeXYWH(0f, 0f, 10f, 10f), SK_ColorBLUE)
        val bm = bitmap()
        SkCanvas(bm).drawDrawable(d, 20f, 25f)
        assertEquals(SK_ColorBLUE, bm.getPixel(25, 30),
            "xy translate should land the rect at (25, 30)")
    }

    @Test
    fun `drawDrawable preserves the canvas CTM after return`() {
        val canvas = SkCanvas(bitmap())
        canvas.translate(7f, 11f)
        val ctmBefore = (canvas.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity)
        val d = RectDrawable(SkRect.MakeXYWH(0f, 0f, 1f, 1f), SK_ColorBLACK)
        canvas.drawDrawable(d, SkMatrix.MakeTrans(100f, 200f))
        val ctmAfter = (canvas.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity)
        assertEquals(ctmBefore.tx, ctmAfter.tx, 0.0001f, "tx must be preserved across drawDrawable")
        assertEquals(ctmBefore.ty, ctmAfter.ty, 0.0001f, "ty must be preserved across drawDrawable")
    }

    @Test
    fun `getGenerationID is stable until notifyDrawingChanged`() {
        val d = RectDrawable(SkRect.MakeXYWH(0f, 0f, 1f, 1f), SK_ColorRED)
        val before = d.getGenerationID()
        // Multiple calls without mutation : same id.
        assertEquals(before, d.getGenerationID())
        assertEquals(before, d.getGenerationID())
        // Notify : id increments.
        d.notifyDrawingChanged()
        val after = d.getGenerationID()
        assertNotEquals(before, after, "notifyDrawingChanged must change the gen id")
    }

    @Test
    fun `each new drawable receives a unique generation id`() {
        val a = RectDrawable(SkRect.MakeWH(1f, 1f), SK_ColorRED)
        val b = RectDrawable(SkRect.MakeWH(1f, 1f), SK_ColorRED)
        // Two independent drawables of the same type must still be
        // distinguishable by gen id (process-wide unique counter).
        assertNotEquals(a.getGenerationID(), b.getGenerationID())
    }

    @Test
    fun `default onGetBounds returns empty rect`() {
        val plain = object : SkDrawable() {
            override fun onDraw(canvas: SkCanvas) {}
            // no override of onGetBounds
        }
        assertTrue(plain.getBounds().isEmpty, "default bounds must be empty")
    }

    @Test
    fun `subclass-overridden onGetBounds is honoured`() {
        val rect = SkRect.MakeXYWH(5f, 7f, 11f, 13f)
        val d = RectDrawable(rect, SK_ColorRED)
        val b = d.getBounds()
        assertEquals(rect.left, b.left)
        assertEquals(rect.top, b.top)
        assertEquals(rect.right, b.right)
        assertEquals(rect.bottom, b.bottom)
    }

    // ─── SkCanvas.drawAnnotation ───────────────────────────────────────

    @Test
    fun `drawAnnotation is a no-op on the raster canvas`() {
        // The bitmap must remain unchanged after drawAnnotation —
        // raster sinks ignore annotations entirely.
        val bm = bitmap()
        val before = bm.pixels.copyOf()
        SkCanvas(bm).drawAnnotation(
            SkRect.MakeXYWH(5f, 5f, 10f, 10f),
            "url",
            "https://example.org".toByteArray(),
        )
        assertTrue(bm.pixels.contentEquals(before),
            "drawAnnotation must not modify the raster bitmap")
    }

    @Test
    fun `drawAnnotation with null value is a no-op`() {
        val bm = bitmap()
        val before = bm.pixels.copyOf()
        SkCanvas(bm).drawAnnotation(SkRect.MakeWH(10f, 10f), "named-dest", null)
        assertTrue(bm.pixels.contentEquals(before))
    }

    @Test
    fun `drawAnnotation can be overridden by a subclass to capture metadata`() {
        // A subclass that records every annotation it receives. This
        // demonstrates the extension slot — PDF / SVG sinks would do
        // the same to embed link metadata.
        val canvas = CapturingCanvas(bitmap())
        canvas.drawAnnotation(SkRect.MakeXYWH(0f, 0f, 10f, 10f), "url",
            "https://test".toByteArray())
        canvas.drawAnnotation(SkRect.MakeXYWH(20f, 20f, 5f, 5f), "named-dest", null)
        assertEquals(2, canvas.keys.size)
        assertEquals("url", canvas.keys[0])
        assertEquals("named-dest", canvas.keys[1])
    }

    /**
     * Test fixture — a canvas subclass that captures every annotation
     * it receives instead of dropping it. Used to demonstrate that
     * [SkCanvas.drawAnnotation] is a real extension slot, not a sealed
     * no-op. Hoisted to a nested class because local Kotlin classes
     * cannot declare nested `data class`es.
     */
    private class CapturingCanvas(target: SkBitmap) : SkCanvas(target) {
        val keys: MutableList<String> = mutableListOf()
        val rects: MutableList<SkRect> = mutableListOf()
        val values: MutableList<ByteArray?> = mutableListOf()

        override fun drawAnnotation(rect: SkRect, key: String, value: ByteArray?) {
            rects += rect
            keys += key
            values += value
        }
    }
}
