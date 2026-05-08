package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkRSXform
import org.skia.math.SkRect

/**
 * Phase I5.2 — `SkCanvas.drawAtlas` semantics.
 */
class DrawAtlasTest {

    /** 16×16 atlas : top-left quadrant red, top-right green, bottom-left blue, bottom-right white. */
    private fun makeAtlas(): SkBitmap {
        val bm = SkBitmap(16, 16)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val color: Int = when {
                    x < 8 && y < 8 -> 0xFFFF0000.toInt()  // red
                    x >= 8 && y < 8 -> 0xFF00FF00.toInt() // green
                    x < 8 && y >= 8 -> 0xFF0000FF.toInt() // blue
                    else -> 0xFFFFFFFF.toInt()            // white
                }
                bm.setPixel(x, y, color)
            }
        }
        return bm
    }

    @Test
    fun `empty xform array is a no-op`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val before = bm.pixels.copyOf()
        val atlas = makeAtlas().asImage()
        SkCanvas(bm).drawAtlas(atlas, emptyArray(), emptyArray())
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `mismatched xform and src sizes throw`() {
        val bm = SkBitmap(50, 50)
        val atlas = makeAtlas().asImage()
        assertThrows(IllegalArgumentException::class.java) {
            SkCanvas(bm).drawAtlas(
                atlas,
                arrayOf(SkRSXform.Identity),
                emptyArray(),
            )
        }
    }

    @Test
    fun `identity xform draws atlas at source coords`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        canvas.drawAtlas(
            atlas,
            arrayOf(SkRSXform.Identity),
            arrayOf(SkRect.MakeLTRB(0f, 0f, 16f, 16f)),
        )
        // (4, 4) maps to source (4, 4) — red quadrant.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(4, 4))
        // (12, 4) maps to source (12, 4) — green quadrant.
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(12, 4))
        // (4, 12) maps to source (4, 12) — blue quadrant.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(4, 12))
        // (20, 20) is outside the atlas — keeps background grey.
        assertEquals(0xFF808080.toInt(), bm.getPixel(20, 20))
    }

    @Test
    fun `translation-only xform shifts the sprite`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        // Translate the atlas to (20, 20) without any rotation/scale.
        canvas.drawAtlas(
            atlas,
            arrayOf(SkRSXform(1f, 0f, 20f, 20f)),
            arrayOf(SkRect.MakeLTRB(0f, 0f, 16f, 16f)),
        )
        // (4, 4) is now grey (atlas no longer there).
        assertEquals(0xFF808080.toInt(), bm.getPixel(4, 4))
        // (24, 24) maps to atlas (4, 4) — red.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(24, 24))
        // (32, 24) maps to atlas (12, 4) — green.
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(32, 24))
    }

    @Test
    fun `scale 2x doubles the sprite size`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        // 2× scale, no rotation, anchor at (0, 0).
        canvas.drawAtlas(
            atlas,
            arrayOf(SkRSXform(2f, 0f, 0f, 0f)),
            arrayOf(SkRect.MakeLTRB(0f, 0f, 16f, 16f)),
        )
        // Atlas now occupies (0..32, 0..32) at 2× scale.
        // (5, 5) in dst ↔ (2.5, 2.5) in atlas — red quadrant.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(5, 5))
        // (20, 5) in dst ↔ (10, 2.5) in atlas — green quadrant.
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(20, 5))
        // (40, 5) in dst is past the scaled atlas (32 px) — grey.
        assertEquals(0xFF808080.toInt(), bm.getPixel(40, 5))
    }

    @Test
    fun `multiple sprites land at independent positions`() {
        val bm = SkBitmap(60, 60).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        // Two sprites : one at (0, 0), one at (30, 30).
        canvas.drawAtlas(
            atlas,
            arrayOf(
                SkRSXform(1f, 0f, 0f, 0f),
                SkRSXform(1f, 0f, 30f, 30f),
            ),
            arrayOf(
                SkRect.MakeLTRB(0f, 0f, 16f, 16f),
                SkRect.MakeLTRB(0f, 0f, 16f, 16f),
            ),
        )
        // First sprite at (4, 4) — red.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(4, 4))
        // Second sprite at (34, 34) — red.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(34, 34))
        // Gap (20, 20) — grey.
        assertEquals(0xFF808080.toInt(), bm.getPixel(20, 20))
    }

    @Test
    fun `subrect of atlas selects the requested quadrant`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        // Pull only the bottom-right (white) quadrant : atlas src (8, 8, 16, 16).
        // Place it at dst (0, 0) via translation -8, -8 in atlas-local
        // anchor terms. The RSXform centres on src.left, src.top — so
        // tx=0, ty=0 puts the src origin at dst (0, 0).
        canvas.drawAtlas(
            atlas,
            arrayOf(SkRSXform(1f, 0f, 0f, 0f)),
            arrayOf(SkRect.MakeLTRB(8f, 8f, 16f, 16f)),
        )
        // (4, 4) in dst ↔ atlas (8+4, 8+4) = (12, 12) — bottom-right quadrant = white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(4, 4))
        // (10, 10) in dst is past the 8×8 sprite → grey.
        assertEquals(0xFF808080.toInt(), bm.getPixel(10, 10))
    }

    @Test
    fun `90 degree rotation places source quadrants at expected positions`() {
        val bm = SkBitmap(50, 50).also { it.eraseColor(0xFF808080.toInt()) }
        val canvas = SkCanvas(bm)
        val atlas = makeAtlas().asImage()
        // 90° counter-clockwise rotation : sCos=0, sSin=1. Place
        // atlas pivot (= src.left, src.top) at (16, 0) so the rotated
        // sprite lands inside the canvas.
        // Math : with sCos=0, sSin=1, src corners map as :
        //   (0, 0) → (16, 0)
        //   (16, 0) → (16, 16)
        //   (16, 16) → (0, 16)
        //   (0, 16) → (0, 0)
        // So the rotated atlas occupies (0..16, 0..16) but the
        // colour quadrants are rotated 90° CCW — top-left (red) is
        // now top-right.
        canvas.drawAtlas(
            atlas,
            arrayOf(SkRSXform(0f, 1f, 16f, 0f)),
            arrayOf(SkRect.MakeLTRB(0f, 0f, 16f, 16f)),
        )
        // After 90° rotation (src+90deg = dst) :
        //   src (4, 4) red → dst (12, 4) — but actually the
        //   rotation rule is dst.x = -sin·sy + cos·sx ; with cos=0,
        //   sin=1, dst = (-y, x), then +tx,+ty = (16-y, x).
        //   src (4, 4) → dst (12, 4). Red quadrant lands at dst x=12.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(12, 4))
        // src (12, 4) green → dst (12, 12)
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(12, 12))
        // src (4, 12) blue → dst (4, 4)
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(4, 4))
        // src (12, 12) white → dst (4, 12)
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(4, 12))
    }
}
