package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPath
import org.skia.math.SkPoint3
import org.skia.math.SkRect

/**
 * R-suivi.50 — verify the default [SkCanvas.drawShadow] virtual delegates
 * to [org.skia.utils.SkShadowUtils.DrawShadow]. The shadow path actually
 * renders pixels into the bitmap (the shadow body for an opaque white
 * background is two dark blurred fills) — so a successful delegation
 * shows up as **non-white** pixels in the path's neighbourhood.
 */
class SkCanvasDrawShadowTest {

    @Test
    fun `drawShadow delegates to SkShadowUtils and writes pixels`() {
        val bm = SkBitmap(80, 80).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)

        val path = SkPath.Rect(SkRect.MakeXYWH(20f, 20f, 40f, 40f))

        canvas.drawShadow(
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f),
            lightPos = SkPoint3(40f, 40f, 200f),
            lightRadius = 50f,
            ambientColor = SkColorSetARGB(0x80, 0x00, 0x00, 0x00),
            spotColor = SkColorSetARGB(0x80, 0x00, 0x00, 0x00),
            flags = 0,
        )

        // The shadow should darken at least one pixel near the path bounds.
        var nonWhite = 0
        for (y in 0 until 80) {
            for (x in 0 until 80) {
                if (bm.getPixel(x, y) != SK_ColorWHITE) nonWhite++
            }
        }
        assertTrue(nonWhite > 0, "drawShadow should produce at least one non-white pixel")
    }

    @Test
    fun `drawShadow is open and overridable to a no-op`() {
        // Defines a custom subclass that swallows drawShadow — proves the
        // method is `open` at the SkCanvas level (would refuse to compile
        // otherwise).
        class NoOpCanvas(bm: SkBitmap) : SkCanvas(bm) {
            var calls = 0
            override fun drawShadow(
                path: SkPath,
                zPlaneParams: SkPoint3,
                lightPos: SkPoint3,
                lightRadius: Float,
                ambientColor: Int,
                spotColor: Int,
                flags: Int,
            ) {
                calls++
                // Don't delegate — verify no pixels are written.
            }
        }

        val bm = SkBitmap(40, 40).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = NoOpCanvas(bm)

        canvas.drawShadow(
            path = SkPath.Rect(SkRect.MakeXYWH(10f, 10f, 20f, 20f)),
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(20f, 20f, 100f),
            lightRadius = 20f,
            ambientColor = SK_ColorBLACK,
            spotColor = SK_ColorBLACK,
        )

        assertEquals(1, canvas.calls, "Override should have been invoked exactly once")
        // Every pixel should still be white — no delegation to the
        // shadow-utils default impl.
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                assertEquals(SK_ColorWHITE, bm.getPixel(x, y), "Pixel ($x, $y) should be unchanged")
            }
        }
    }
}
