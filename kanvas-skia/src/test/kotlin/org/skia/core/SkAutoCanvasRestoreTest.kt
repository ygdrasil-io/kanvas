package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Behaviour tests for the Kotlin idioms `withSave`, `withRestore`,
 * `withLayer` (the Skia `SkAutoCanvasRestore` analogues — Q1).
 *
 * Contract under test :
 *   1. on normal exit, the canvas's save count returns to its
 *      pre-block value ;
 *   2. on a thrown exception, same property holds ;
 *   3. mutations made inside the block (CTM, clip) do not leak past
 *      the helper.
 */
class SkAutoCanvasRestoreTest {

    private fun newCanvas(): SkCanvas =
        SkCanvas(SkBitmap(8, 8).also { it.eraseColor(SK_ColorWHITE) })

    @Test
    fun `withSave restores save count on normal exit`() {
        val c = newCanvas()
        val before = c.getSaveCount()
        c.withSave {
            translate(2f, 2f)
            // an extra explicit save inside the block should also be popped
            save()
            translate(3f, 3f)
            assertNotEquals(before, getSaveCount())
        }
        assertEquals(before, c.getSaveCount())
    }

    @Test
    fun `withSave restores save count even when block throws`() {
        val c = newCanvas()
        val before = c.getSaveCount()
        assertThrows(RuntimeException::class.java) {
            c.withSave {
                save()
                save()
                throw RuntimeException("boom")
            }
        }
        assertEquals(before, c.getSaveCount())
    }

    @Test
    fun `withSave reverts CTM mutations`() {
        val c = newCanvas()
        val before = (c.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity)
        c.withSave {
            translate(10f, 5f)
            scale(2f, 3f)
        }
        assertEquals(before, (c.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity))
    }

    @Test
    fun `withRestore captures depth without saving`() {
        val c = newCanvas()
        val before = c.getSaveCount()
        c.withRestore {
            // user does their own saves inside
            save()
            save()
            save()
        }
        assertEquals(before, c.getSaveCount())
    }

    @Test
    fun `withLayer restores save count on normal exit`() {
        val c = newCanvas()
        val before = c.getSaveCount()
        c.withLayer(SkRect.MakeWH(4f, 4f)) {
            translate(1f, 1f)
        }
        assertEquals(before, c.getSaveCount())
    }

    @Test
    fun `withLayer restores save count when block throws`() {
        val c = newCanvas()
        val before = c.getSaveCount()
        assertThrows(IllegalStateException::class.java) {
            c.withLayer {
                throw IllegalStateException("oops")
            }
        }
        assertEquals(before, c.getSaveCount())
    }

    @Test
    fun `withLayer applies layer paint on composite`() {
        // Sanity that withLayer wires through to saveLayer + restore so
        // the offscreen layer's content composites back. We draw a red
        // rect inside the layer with a 50% alpha layer paint, then check
        // that the parent device shows the modulated result.
        val bitmap = SkBitmap(4, 4).also { it.eraseColor(SK_ColorWHITE) }
        val c = SkCanvas(bitmap)
        val layerPaint = SkPaint().apply { alpha = 128 }
        c.withLayer(paint = layerPaint) {
            val redPaint = SkPaint().apply { color = SK_ColorRED }
            drawRect(SkRect.MakeWH(4f, 4f), redPaint)
        }
        // Layer's red @ 100% × layer alpha 128/255 SrcOver onto white
        // ⇒ ~ (255, 127, 127) range. Just verify it's *not* still pure
        // red and *not* still pure white — the modulation happened.
        val px = bitmap.getPixel(0, 0)
        assertNotEquals(SK_ColorRED, px)
        assertNotEquals(SK_ColorWHITE, px)
        assertNotEquals(SK_ColorBLACK, px)
    }
}
