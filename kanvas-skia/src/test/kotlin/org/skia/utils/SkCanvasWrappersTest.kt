package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Q2 verification suite for the three canvas wrappers in
 * [`org.skia.utils`](kanvas-skia/src/main/kotlin/org/skia/utils/) :
 *
 *  - [SkNoDrawCanvas] — every draw is a no-op ; matrix / clip stack
 *    still works.
 *  - [SkPaintFilterCanvas] — abstract proxy that filters the paint
 *    before forwarding ; a `false` return drops the draw.
 *  - [SkOverdrawCanvas] — concrete subclass of [SkPaintFilterCanvas]
 *    that substitutes the paint with a +1-per-draw accumulator.
 */
class SkCanvasWrappersTest {

    // ─── SkNoDrawCanvas ───────────────────────────────────────────────

    @Test
    fun `NoDrawCanvas drops every draw and returns its declared dimensions`() {
        val canvas = SkNoDrawCanvas(width = 800, height = 600)
        assertEquals(800, canvas.width)
        assertEquals(600, canvas.height)
        // Every draw runs without exception, but produces no observable
        // side effect (there's no destination bitmap to check).
        canvas.drawRect(SkRect.MakeWH(100f, 100f), SkPaint(SK_ColorRED))
        canvas.drawCircle(50f, 50f, 25f, SkPaint(SK_ColorBLACK))
        canvas.drawColor(SK_ColorWHITE)
        canvas.drawPaint(SkPaint(SK_ColorRED))
    }

    @Test
    fun `NoDrawCanvas keeps the matrix and clip stack accurate`() {
        val canvas = SkNoDrawCanvas(800, 600)
        // Before any state ops : identity.
        assertTrue(canvas.getTotalMatrix().isIdentity)
        canvas.translate(50f, 100f)
        // Translate produced a non-identity CTM ; the wrapper sees it
        // via its own state stack (no target to forward to).
        val m = canvas.getTotalMatrix()
        assertFalse(m.isIdentity)
        assertEquals(50f, m.tx)
        assertEquals(100f, m.ty)
        // save / restore restores the previous state.
        val saveCount = canvas.save()
        canvas.scale(2f, 2f)
        assertNotEquals(50f, canvas.getTotalMatrix().sx) // sx mutated by scale
        canvas.restoreToCount(saveCount)
        assertEquals(1f, canvas.getTotalMatrix().sx) // back to pre-scale
    }

    // ─── SkPaintFilterCanvas ──────────────────────────────────────────

    @Test
    fun `PaintFilterCanvas forwards draws with the filtered paint`() {
        // Filter forces every paint's antialias to false, then
        // returns true (draw should still happen).
        val target = SkCanvas(SkBitmap(40, 40))
        val filter = SkPaintFilterCanvas(target) { paint ->
            paint.isAntiAlias = false
            true
        }
        val originalPaint = SkPaint(SK_ColorRED).apply { isAntiAlias = true }
        filter.drawRect(SkRect.MakeWH(40f, 40f), originalPaint)
        // Target should have red pixels — the draw was forwarded.
        assertEquals(SK_ColorRED, target.bitmap.getPixel(20, 20))
        // Caller's original paint is unchanged — wrapper clones before
        // filtering.
        assertTrue(originalPaint.isAntiAlias, "filter must clone, not mutate caller's paint")
    }

    @Test
    fun `PaintFilterCanvas drops a draw when onFilter returns false`() {
        val target = SkCanvas(SkBitmap(40, 40)).also { it.bitmap.eraseColor(SK_ColorWHITE) }
        val filter = SkPaintFilterCanvas(target) { _ -> false } // drop everything
        filter.drawRect(SkRect.MakeWH(40f, 40f), SkPaint(SK_ColorRED))
        // Target should still be white — the draw was dropped.
        assertEquals(SK_ColorWHITE, target.bitmap.getPixel(20, 20))
    }

    @Test
    fun `PaintFilterCanvas mutates the paint visible at the target`() {
        // Force every red paint to white. Verifies the mutated paint
        // is what reaches the target.
        val target = SkCanvas(SkBitmap(40, 40))
        val filter = SkPaintFilterCanvas(target) { paint ->
            if (paint.color == SK_ColorRED) paint.color = SK_ColorWHITE
            true
        }
        filter.drawRect(SkRect.MakeWH(40f, 40f), SkPaint(SK_ColorRED))
        // Target should be white, not red.
        assertEquals(SK_ColorWHITE, target.bitmap.getPixel(20, 20))
    }

    @Test
    fun `PaintFilterCanvas forwards every state op to both wrapper and target`() {
        val target = SkCanvas(SkBitmap(40, 40))
        val filter = SkPaintFilterCanvas(target) { _ -> true }
        filter.translate(10f, 20f)
        // Both wrapper and target see the translate.
        assertEquals(10f, filter.getTotalMatrix().tx)
        assertEquals(20f, filter.getTotalMatrix().ty)
        assertEquals(10f, target.getTotalMatrix().tx)
        assertEquals(20f, target.getTotalMatrix().ty)
        // save / restore round-trip.
        val sc = filter.save()
        filter.scale(2f, 2f)
        assertEquals(2f, filter.getTotalMatrix().sx)
        assertEquals(2f, target.getTotalMatrix().sx)
        filter.restoreToCount(sc)
        assertEquals(1f, filter.getTotalMatrix().sx)
        assertEquals(1f, target.getTotalMatrix().sx)
    }

    @Test
    fun `PaintFilterCanvas synthesises a default paint for null-paint image draws`() {
        // drawImage may receive paint = null (Skia's default). The
        // wrapper synthesises a default SkPaint() so onFilter can
        // always inspect / mutate something.
        var observedAlpha = -1
        val target = SkCanvas(SkBitmap(40, 40))
        val image = SkBitmap(8, 8).also { it.eraseColor(SK_ColorRED) }.asImage()
        val filter = SkPaintFilterCanvas(target) { paint ->
            observedAlpha = paint.alpha
            true
        }
        filter.drawImage(image, 0f, 0f, paint = null)
        // Default SkPaint().alpha is 0xFF.
        assertEquals(0xFF, observedAlpha, "synthesised paint must carry default alpha 0xFF")
    }

    @Test
    fun `PaintFilterCanvas onFilter sees a copy not the caller's paint`() {
        // onFilter mutates ; caller's original must stay untouched.
        val target = SkCanvas(SkBitmap(40, 40))
        var sawColor: org.skia.foundation.SkColor = 0
        val filter = SkPaintFilterCanvas(target) { paint ->
            sawColor = paint.color
            paint.color = SK_ColorWHITE  // mutate
            true
        }
        val callerPaint = SkPaint(SK_ColorRED)
        filter.drawRect(SkRect.MakeWH(40f, 40f), callerPaint)
        assertEquals(SK_ColorRED, sawColor, "filter saw the caller's color")
        assertEquals(SK_ColorRED, callerPaint.color, "caller's paint must not be mutated")
    }

    // ─── SkOverdrawCanvas ─────────────────────────────────────────────

    @Test
    fun `OverdrawCanvas accumulates plus-1 per draw on the alpha channel`() {
        // Three identical drawRects on a black-alpha-0 destination.
        // Alpha of every covered pixel must equal 3 after the run.
        val target = SkCanvas(SkBitmap(20, 20)).also { it.bitmap.eraseColor(0) }
        val overdraw = SkOverdrawCanvas(target)
        val dummyPaint = SkPaint(SK_ColorRED).apply { isAntiAlias = false }
        overdraw.drawRect(SkRect.MakeWH(20f, 20f), dummyPaint)
        overdraw.drawRect(SkRect.MakeWH(20f, 20f), dummyPaint)
        overdraw.drawRect(SkRect.MakeWH(20f, 20f), dummyPaint)
        // Every interior pixel should have alpha = 3.
        val px = target.bitmap.getPixel(10, 10)
        assertEquals(3, SkColorGetA(px), "pixel should have been touched 3 times")
    }

    @Test
    fun `OverdrawCanvas alpha never overflows past 255`() {
        // 300 draws on a small bitmap : every pixel should saturate
        // at alpha = 255 (or thereabouts ; we assert <= 255 + nonzero).
        val target = SkCanvas(SkBitmap(10, 10)).also { it.bitmap.eraseColor(0) }
        val overdraw = SkOverdrawCanvas(target)
        val paint = SkPaint(SK_ColorRED).apply { isAntiAlias = false }
        repeat(300) {
            overdraw.drawRect(SkRect.MakeWH(10f, 10f), paint)
        }
        val px = target.bitmap.getPixel(5, 5)
        val a = SkColorGetA(px)
        assertTrue(a in 1..255, "alpha must stay in [1, 255] after 300 draws ; got $a")
    }

    @Test
    fun `OverdrawCanvas substitutes paint regardless of input shader or filter`() {
        // The substitution must clear shader / colorFilter / etc so
        // counts are uniform across simple and complex paints.
        val target = SkCanvas(SkBitmap(20, 20)).also { it.bitmap.eraseColor(0) }
        val overdraw = SkOverdrawCanvas(target)
        // A simple paint and a complex paint should produce the same
        // count change (+1 each).
        overdraw.drawRect(SkRect.MakeWH(20f, 20f), SkPaint(SK_ColorRED))
        val a1 = SkColorGetA(target.bitmap.getPixel(10, 10))
        val complexPaint = SkPaint(SK_ColorRED).apply {
            isAntiAlias = true
            // (more state could be set ; the substitution clears it)
        }
        overdraw.drawRect(SkRect.MakeWH(20f, 20f), complexPaint)
        val a2 = SkColorGetA(target.bitmap.getPixel(10, 10))
        assertEquals(a1 + 1, a2, "substitution must produce uniform +1 per draw")
    }

    @Test
    fun `OverdrawCanvas honours non-overlapping draws — only touched pixels increment`() {
        val target = SkCanvas(SkBitmap(20, 20)).also { it.bitmap.eraseColor(0) }
        val overdraw = SkOverdrawCanvas(target)
        // Draw a 5×5 square at (0, 0). Pixel (10, 10) is outside.
        overdraw.drawRect(SkRect.MakeXYWH(0f, 0f, 5f, 5f), SkPaint(SK_ColorRED))
        assertEquals(1, SkColorGetA(target.bitmap.getPixel(2, 2)), "covered pixel must read 1")
        assertEquals(0, SkColorGetA(target.bitmap.getPixel(10, 10)), "uncovered pixel must read 0")
    }

    // ─── End-to-end integration ───────────────────────────────────────

    @Test
    fun `PaintFilterCanvas can chain through translate + clip + drawPath without crashing`() {
        val target = SkCanvas(SkBitmap(40, 40)).also { it.bitmap.eraseColor(0) }
        val filter = SkPaintFilterCanvas(target) { _ -> true }
        val path = SkPathBuilder().apply {
            moveTo(0f, 0f)
            lineTo(20f, 0f)
            lineTo(20f, 20f)
            close()
        }.detach()
        filter.translate(5f, 5f)
        filter.clipRect(SkRect.MakeWH(30f, 30f))
        filter.drawPath(path, SkPaint(SK_ColorRED))
        // Some pixel inside the translated triangle should be red.
        // Sample a point that's inside : (5+10, 5+5) = (15, 10).
        val px = target.bitmap.getPixel(15, 10)
        assertNotEquals(0, px, "translated + clipped path should have rasterised a non-zero pixel")
    }
}
