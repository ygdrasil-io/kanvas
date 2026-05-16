package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkRect
import kotlin.math.floor
import kotlin.math.ln

/**
 * Phase G10 — unit-level cover for [SkImage.withDefaultMipmaps] and
 * the LOD-selection plumbing in [org.skia.foundation.SkBitmapShader] /
 * [org.skia.core.SkBitmapDevice.drawImageRect].
 */
class SkImageMipmapTest {

    private fun expectedLevels(w: Int, h: Int): Int {
        val m = minOf(w, h)
        if (m <= 1) return 1
        return floor(ln(m.toDouble()) / ln(2.0)).toInt() + 1
    }

    /** Build a 64×64 RGBA-8888 image with a recognisable per-pixel pattern. */
    private fun buildCheckerImage(): SkImage {
        val w = 64
        val h = 64
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Two-colour checker, 4-pixel cells.
                val on = ((x / 4) + (y / 4)) and 1 == 0
                val c = if (on) SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
                else SkColorSetARGB(0xFF, 0, 0, 0)
                bitmap.setPixel(x, y, c)
            }
        }
        return SkImage.Make(bitmap)
    }

    @Test
    fun `withDefaultMipmaps returns image with expected levelCount`() {
        val image = buildCheckerImage()
        assertEquals(1, image.levelCount(), "raw image has a single level")
        val mipped = image.withDefaultMipmaps()
        // 64 → 32 → 16 → 8 → 4 → 2 → 1 → 7 levels total.
        assertEquals(expectedLevels(64, 64), mipped.levelCount())
        assertEquals(7, mipped.levelCount())
    }

    @Test
    fun `withDefaultMipmaps handles non-power-of-two dimensions`() {
        // 100 × 50 — log2(50) = 5.64 → 6 levels (100/50, 50/25, 25/12, 12/6, 6/3, 3/1 ⇒ 7 actually).
        // floor(log2(50)) + 1 = 5 + 1 = 6 ; the loop terminates once both
        // dims fall to 1, so 100 × 50 produces : 50×25 → 25×12 → 12×6 →
        // 6×3 → 3×1 → 1×1 (six halvings = seven entries).
        val w = 100
        val h = 50
        val bm = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888).also { it.eraseColor(SK_ColorWHITE) }
        val img = SkImage.Make(bm).withDefaultMipmaps()
        // The loop halves until both dims are 1, so the level count must
        // satisfy `floor(log2(max(w, h))) + 1`.
        val expected = floor(ln(maxOf(w, h).toDouble()) / ln(2.0)).toInt() + 1
        assertEquals(expected, img.levelCount())
    }

    @Test
    fun `withDefaultMipmaps preserves level-0 pixels`() {
        val image = buildCheckerImage()
        val mipped = image.withDefaultMipmaps()
        // Level 0 must be bit-identical to the source.
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                assertEquals(image.peekPixel(x, y), mipped.peekPixel(x, y),
                    "level-0 pixel ($x, $y) preserved")
            }
        }
    }

    @Test
    fun `mip pyramid box-filter averages match expected greys`() {
        // For a 4-pixel-cell checker, level 1 (32×32) averages 2×2 cells :
        // the on/off alternation persists at 4-pixel granularity but each
        // pair is averaged. Level 2 (16×16) drops the checker to a flat
        // mid-grey (~128).
        val image = buildCheckerImage().withDefaultMipmaps()
        // Sample a "middle of an averaged region" pixel at level 2 via
        // the SkBitmapShader's mip selection : drawImageRect into a 8×8
        // dest forces a 8× minification, which lands on level 3.
        val info = SkImageInfo.MakeN32(8, 8, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(info)
        val canvas: SkCanvas = surface.canvas
        canvas.clear(SK_ColorWHITE)
        val paint = SkPaint()
        canvas.drawImageRect(
            image,
            SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()),
            SkRect.MakeWH(8f, 8f),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
            paint,
        )
        val snapshot = surface.makeImageSnapshot()
        // 64×64 → 8×8 is an 8× minification ⇒ level 3 (= log2(8)).
        // Each 8×8 source-cell input averages to mid-grey ~128.
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val r = SkColorGetR(snapshot.peekPixel(x, y))
                // Mid-grey ±70 — band-limit + per-cell averaging variance.
                assertTrue(r in 50..200,
                    "expected mid-grey at ($x, $y), got R=$r")
            }
        }
    }

    @Test
    fun `mipmap sampling differs visibly from non-mipped`() {
        // Build a high-frequency pattern : the top half of the image is
        // pure white, the bottom half is pure black, with a thin (1px)
        // black bar at row 0 to break the bilinear-equality coincidence
        // a perfectly axis-aligned half-split produces. Without mips the
        // 8× minification taps a tiny 2×2 neighbourhood at the half-line
        // boundary ; with mips the kLinear-mip filter integrates over an
        // 8×8 neighbourhood at level 3 — so the half-line edge softens
        // visibly compared to the no-mip bilinear sample.
        val w = 64
        val h = 64
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val on = y < h / 2 && y != 0
                val c = if (on) SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
                else SkColorSetARGB(0xFF, 0, 0, 0)
                bitmap.setPixel(x, y, c)
            }
        }
        val raw = SkImage.Make(bitmap)
        val mipped = raw.withDefaultMipmaps()
        val info = SkImageInfo.MakeN32(8, 8, SkAlphaType.kPremul)
        val s1 = SkSurface.MakeRaster(info)
        val s2 = SkSurface.MakeRaster(info)
        s1.canvas.clear(SK_ColorWHITE)
        s2.canvas.clear(SK_ColorWHITE)
        val noMip = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNone)
        val withMip = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)
        s1.canvas.drawImageRect(raw,
            SkRect.MakeWH(raw.width.toFloat(), raw.height.toFloat()),
            SkRect.MakeWH(8f, 8f), noMip, SkPaint())
        s2.canvas.drawImageRect(mipped,
            SkRect.MakeWH(mipped.width.toFloat(), mipped.height.toFloat()),
            SkRect.MakeWH(8f, 8f), withMip, SkPaint())
        val a = s1.makeImageSnapshot()
        val b = s2.makeImageSnapshot()
        // At least one pixel must differ — the two paths take different
        // code branches and should produce visibly different results.
        var differs = false
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (a.peekPixel(x, y) != b.peekPixel(x, y)) { differs = true; break }
            }
            if (differs) break
        }
        assertTrue(differs, "mip-on and mip-off must produce different output for an 8× minification")
    }
}
