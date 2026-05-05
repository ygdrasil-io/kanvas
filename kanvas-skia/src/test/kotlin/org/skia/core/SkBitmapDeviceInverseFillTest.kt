package org.skia.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkRect

/**
 * Slice 3.8 — `kInverseWinding` / `kInverseEvenOdd` fill rules in the
 * scanline rasterizer. The inverse rules paint the *complement* of the
 * standard rule's interior, clipped to the device bounds.
 *
 * Mirrors `SkPathFillType` semantics
 * (`include/core/SkPathTypes.h:21-23`) on the rasterizer side
 * (`src/core/SkScan_AntiPath.cpp` for reference; we re-implement the
 * extended scanline iteration here).
 */
class SkBitmapDeviceInverseFillTest {

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun isBlack(bitmap: SkBitmap, x: Int, y: Int, alphaMin: Int = 200): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) < 64 && SkColorGetG(px) < 64 && SkColorGetB(px) < 64 &&
            SkColorGetA(px) >= alphaMin
    }

    private fun isWhite(bitmap: SkBitmap, x: Int, y: Int): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) > 240 && SkColorGetG(px) > 240 && SkColorGetB(px) > 240
    }

    // --- kInverseWinding ------------------------------------------------

    @Test
    fun `kInverseWinding rect fills the complement of the rect inside the canvas`() {
        // Carve a 20x20 rect out of a 40x40 canvas. Pixels outside the
        // rect should be black; pixels inside should remain white.
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseWinding)
            .addRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
            .detach()
        val bitmap = render(40, 40) {
            drawPath(path, SkPaint().apply { color = SK_ColorBLACK })
        }
        // Outside the rect (within canvas) — black.
        assertTrue(isBlack(bitmap, 5, 5),  "top-left of clip should be black")
        assertTrue(isBlack(bitmap, 35, 5), "top-right of clip should be black")
        assertTrue(isBlack(bitmap, 5, 35), "bottom-left of clip should be black")
        assertTrue(isBlack(bitmap, 35, 35), "bottom-right of clip should be black")
        // Strictly inside the rect — untouched (white).
        assertTrue(isWhite(bitmap, 20, 20), "centre of rect should be white")
        assertTrue(isWhite(bitmap, 15, 15), "near-TL inside rect should be white")
        assertTrue(isWhite(bitmap, 25, 25), "near-BR inside rect should be white")
    }

    @Test
    fun `kInverseWinding empty path fills the whole clip`() {
        val empty = SkPathBuilder().setFillType(SkPathFillType.kInverseWinding).detach()
        val bitmap = render(20, 20) {
            drawPath(empty, SkPaint().apply { color = SK_ColorBLACK })
        }
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                assertTrue(isBlack(bitmap, x, y), "($x, $y) should be black")
            }
        }
    }

    // --- kInverseEvenOdd ------------------------------------------------

    @Test
    fun `kInverseEvenOdd of a single rect matches kInverseWinding`() {
        // For a single non-self-intersecting rect, both inverse rules agree.
        val rect = SkRect.MakeLTRB(10f, 10f, 30f, 30f)
        val pWind = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseWinding).addRect(rect).detach()
        val pEven = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseEvenOdd).addRect(rect).detach()
        val bw = render(40, 40) {
            drawPath(pWind, SkPaint().apply { color = SK_ColorBLACK })
        }
        val be = render(40, 40) {
            drawPath(pEven, SkPaint().apply { color = SK_ColorBLACK })
        }
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                val a = bw.getPixel(x, y)
                val b = be.getPixel(x, y)
                assertTrue(a == b, "kInverseWinding and kInverseEvenOdd disagree at ($x, $y)")
            }
        }
    }

    // --- regression: non-inverse fills still work -----------------------

    @Test
    fun `kWinding rect remains correct after the inverse code path`() {
        // Verifies the unified scanline walker (which now seeds an
        // initial-inside flag for inverse rules) still produces
        // bit-identical results for the non-inverse case.
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val bitmap = render(40, 40) {
            drawPath(path, SkPaint().apply { color = SK_ColorBLACK })
        }
        // Inside the rect — black.
        assertTrue(isBlack(bitmap, 20, 20))
        // Outside the rect — white.
        assertTrue(isWhite(bitmap, 5, 5))
        assertTrue(isWhite(bitmap, 35, 35))
    }

    // --- inverse + AA --------------------------------------------------

    @Test
    fun `kInverseWinding with AA produces edge-feathered complement`() {
        // Anti-aliased inverse fill: pixels well outside the rect are
        // fully black, pixels well inside are fully white. The 1-pixel
        // ring around the rect edge is partially covered.
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseWinding)
            .addRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
            .detach()
        val bitmap = render(40, 40) {
            drawPath(path, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Far outside — black.
        assertTrue(isBlack(bitmap, 2, 2))
        // Far inside — white.
        assertTrue(isWhite(bitmap, 20, 20))
    }
}
