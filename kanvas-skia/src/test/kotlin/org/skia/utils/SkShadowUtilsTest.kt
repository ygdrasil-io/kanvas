package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkMatrix
import org.skia.math.SkPoint3
import org.skia.math.SkRect

/**
 * Unit tests for [SkShadowUtils] — verify the flag bitmask, the
 * deterministic [SkShadowUtils.ComputeTonalColors] formula (compared
 * against numbers spot-checked against upstream
 * `SkShadowUtils.cpp::ComputeTonalColors`), and that [SkShadowUtils.DrawShadow]
 * paints non-zero pixels on a simple rectangular occluder.
 */
class SkShadowUtilsTest {

    private val transparent: SkColor = 0

    @Test
    fun `kAll_ShadowFlag equals 0x0F (bit-or of the four named flags)`() {
        val expected = SkShadowUtils.kTransparentOccluder_ShadowFlag or
            SkShadowUtils.kGeometricOnly_ShadowFlag or
            SkShadowUtils.kDirectionalLight_ShadowFlag or
            SkShadowUtils.kConcaveBlurOnly_ShadowFlag
        assertEquals(0x0F, expected)
        assertEquals(expected, SkShadowUtils.kAll_ShadowFlag)
    }

    @Test
    fun `individual flag values match upstream SkShadowFlags`() {
        assertEquals(0x00, SkShadowUtils.kNone_ShadowFlag)
        assertEquals(0x01, SkShadowUtils.kTransparentOccluder_ShadowFlag)
        assertEquals(0x02, SkShadowUtils.kGeometricOnly_ShadowFlag)
        assertEquals(0x04, SkShadowUtils.kDirectionalLight_ShadowFlag)
        assertEquals(0x08, SkShadowUtils.kConcaveBlurOnly_ShadowFlag)
    }

    @Test
    fun `ComputeTonalColors is deterministic`() {
        val outA1 = IntArray(1)
        val outS1 = IntArray(1)
        val outA2 = IntArray(1)
        val outS2 = IntArray(1)
        val ambient = (0x80808080).toInt() // alpha=0x80, gray=0x80
        val spot = (0xC0FF0000).toInt()    // alpha=0xC0, red

        SkShadowUtils.ComputeTonalColors(ambient, spot, outA1, outS1)
        SkShadowUtils.ComputeTonalColors(ambient, spot, outA2, outS2)
        assertEquals(outA1[0], outA2[0])
        assertEquals(outS1[0], outS2[0])
    }

    @Test
    fun `ComputeTonalColors forces ambient RGB to black and preserves alpha`() {
        val outA = IntArray(1)
        val outS = IntArray(1)
        val ambient = (0xAABBCCDD).toInt() // alpha=0xAA, rgb=BBCCDD
        SkShadowUtils.ComputeTonalColors(ambient, ambient, outA, outS)
        // Ambient out : alpha preserved, rgb forced to 0.
        assertEquals(0xAA, SkColorGetA(outA[0]))
        assertEquals(0, SkColorGetR(outA[0]))
        assertEquals(0, SkColorGetG(outA[0]))
        assertEquals(0, SkColorGetB(outA[0]))
    }

    @Test
    fun `ComputeTonalColors spot for fully-transparent input yields fully-transparent output`() {
        val outA = IntArray(1)
        val outS = IntArray(1)
        SkShadowUtils.ComputeTonalColors(0, 0, outA, outS)
        // origA = 0 ⇒ both colorAlpha (via alphaAdjust=0) and greyscaleAlpha
        // collapse to 0, so tonalAlpha = 0 ⇒ output alpha = 0.
        assertEquals(0, SkColorGetA(outS[0]))
        assertEquals(0, SkColorGetA(outA[0]))
    }

    @Test
    fun `ComputeTonalColors with a pure black opaque spot keeps alpha at 255 and RGB at 0`() {
        val outA = IntArray(1)
        val outS = IntArray(1)
        val opaqueBlack = (0xFF000000).toInt()
        SkShadowUtils.ComputeTonalColors(opaqueBlack, opaqueBlack, outA, outS)
        // luminance = 0 ⇒ colorAlpha = 0 ⇒ colorScale = 0 ;
        // greyscaleAlpha = 1*(1-0) = 1 ⇒ tonalAlpha = 1.
        assertEquals(0xFF, SkColorGetA(outS[0]))
        assertEquals(0, SkColorGetR(outS[0]))
        assertEquals(0, SkColorGetG(outS[0]))
        assertEquals(0, SkColorGetB(outS[0]))
    }

    @Test
    fun `DrawShadow on a simple rect paints non-zero pixels on the canvas`() {
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val path = SkPath.Rect(rect)
        val ambient = 0x40000000 // 25% alpha black
        val spot = (0x80000000).toInt()    // 50% alpha black

        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f),     // flat plane @ z=8
            lightPos = SkPoint3(32f, 32f, 600f),     // light above the rect
            lightRadius = 80f,
            ambientColor = ambient,
            spotColor = spot,
            flags = 0,
        )

        // Scan the bitmap for any non-transparent pixel — the shadow must
        // have written somewhere even if it's heavily blurred.
        var painted = false
        outer@ for (y in 0 until 64) {
            for (x in 0 until 64) {
                if (canvas.bitmap.getPixel(x, y) != transparent) {
                    painted = true
                    break@outer
                }
            }
        }
        assertTrue(painted, "DrawShadow must paint at least one non-transparent pixel")
    }

    @Test
    fun `DrawShadow on an empty path is a no-op`() {
        val canvas = SkCanvas(SkBitmap(16, 16).also { it.eraseColor(transparent) })
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = SkPathBuilder().detach(),
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(8f, 8f, 100f),
            lightRadius = 4f,
            ambientColor = (0xFF000000).toInt(),
            spotColor = (0xFF000000).toInt(),
        )
        // No pixel was touched.
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                assertEquals(transparent, canvas.bitmap.getPixel(x, y))
            }
        }
    }

    @Test
    fun `OptimizeForSurface returns true`() {
        val canvas = SkCanvas(SkBitmap(4, 4))
        val path = SkPath.Rect(SkRect.MakeLTRB(0f, 0f, 4f, 4f))
        assertTrue(
            SkShadowUtils.OptimizeForSurface(
                canvas, path,
                SkPoint3(0f, 0f, 10f), 5f,
            ),
        )
    }

    @Test
    fun `GetLocalBounds populates an outset rect for a non-empty path`() {
        val out: Array<SkRect?> = arrayOf(null)
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val ok = SkShadowUtils.GetLocalBounds(
            ctm = SkMatrix(),
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(20f, 20f, 200f),
            lightRadius = 6f,
            flags = 0,
            bounds = out,
        )
        assertTrue(ok)
        val r = out[0]!!
        // Result must contain the original path bounds.
        assertTrue(r.left <= 10f)
        assertTrue(r.top <= 10f)
        assertTrue(r.right >= 30f)
        assertTrue(r.bottom >= 30f)
    }

    @Test
    fun `GetLocalBounds returns false on empty path`() {
        val out: Array<SkRect?> = arrayOf(null)
        val ok = SkShadowUtils.GetLocalBounds(
            ctm = SkMatrix(),
            path = SkPathBuilder().detach(),
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(0f, 0f, 100f),
            lightRadius = 4f,
            flags = 0,
            bounds = out,
        )
        assertFalse(ok)
        assertEquals(null, out[0])
    }

    @Test
    fun `DrawShadow with kGeometricOnly flag paints a hard-edged silhouette`() {
        // R-suivi.30 — the analytic mesh honours kGeometricOnly :
        // there should be a sharp transition between fully-painted
        // umbra and fully-transparent background (no soft falloff).
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })
        val rect = SkRect.MakeLTRB(16f, 16f, 48f, 48f)
        val path = SkPath.Rect(rect)
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f),
            lightPos = SkPoint3(32f, 32f, 600f),
            lightRadius = 16f,
            ambientColor = (0xFF000000).toInt(),
            spotColor = 0,
            flags = SkShadowUtils.kGeometricOnly_ShadowFlag or
                SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )
        // Inside the rect must be painted (alpha > 0).
        val inside = canvas.bitmap.getPixel(32, 32)
        assertTrue(SkColorGetA(inside) > 0, "geometric-only must paint the silhouette interior")
    }

    @Test
    fun `DrawShadow mesh path falls back to legacy for non-convex paths`() {
        // R-suivi.30 — concave paths must still produce output (via
        // the legacy blur path). Build a concave "L" shape.
        val concave = SkPathBuilder()
            .moveTo(20f, 20f)
            .lineTo(40f, 20f)
            .lineTo(40f, 30f)
            .lineTo(30f, 30f)
            .lineTo(30f, 40f)
            .lineTo(20f, 40f)
            .close()
            .detach()
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = concave,
            zPlaneParams = SkPoint3(0f, 0f, 8f),
            lightPos = SkPoint3(32f, 32f, 600f),
            lightRadius = 16f,
            ambientColor = (0xFF000000).toInt(),
            spotColor = (0x80000000).toInt(),
        )
        // Some pixel must be painted (the legacy blur fallback fired).
        var painted = false
        outer@ for (y in 0 until 64) {
            for (x in 0 until 64) {
                if (canvas.bitmap.getPixel(x, y) != transparent) {
                    painted = true
                    break@outer
                }
            }
        }
        assertTrue(painted, "non-convex path must still produce shadow output via the legacy fallback")
    }

    @Test
    fun `ComputeTonalColors with a red opaque spot produces a non-grey spot color`() {
        // Sanity check : the spot output for a red input keeps a red-leaning
        // hue (R channel ≥ G/B) — verifies the luminance-based per-channel
        // scaling didn't accidentally collapse to grey.
        val outA = IntArray(1)
        val outS = IntArray(1)
        val opaqueRed = (0xFFFF0000).toInt()
        SkShadowUtils.ComputeTonalColors(opaqueRed, opaqueRed, outA, outS)
        val r = SkColorGetR(outS[0])
        val g = SkColorGetG(outS[0])
        val b = SkColorGetB(outS[0])
        assertTrue(r >= g, "expected R ≥ G for a red-input spot (got R=$r, G=$g)")
        assertTrue(r >= b, "expected R ≥ B for a red-input spot (got R=$r, B=$b)")
        // And the tonal alpha must be > 0 (the input was opaque).
        assertNotEquals(0, SkColorGetA(outS[0]))
    }
}
