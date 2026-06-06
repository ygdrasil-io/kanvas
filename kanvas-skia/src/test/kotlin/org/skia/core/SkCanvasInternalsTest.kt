package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Unit tests for `SkCanvas.drawPaint` and `SkCanvas.saveLayer` — the two
 * primitives added in the savelayer/drawpaint slice. These tests don't go
 * through a reference image; they assert directly on the resulting bitmap
 * pixels so a regression has a tight, debuggable failure.
 *
 * The drawPaint tests cover both clipped and unclipped fills under SrcOver.
 * The saveLayer tests cover three regimes:
 *   1. paint=null, opaque draw: layer composite must equal direct draw.
 *   2. paint=alpha=128, opaque RED draw onto BLACK: must yield ~50/50 SrcOver.
 *   3. nested layers correctly stack and unwind without leaking pixels
 *      outside the layer bounds.
 */
class SkCanvasInternalsTest {

    private fun render(width: Int, height: Int, bg: SkColor = SK_ColorWHITE, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height).also { it.eraseColor(bg) }
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun assertColorEquals(expected: SkColor, actual: SkColor, tolerance: Int = 1, msg: String = "") {
        val dA = kotlin.math.abs(SkColorGetA(expected) - SkColorGetA(actual))
        val dR = kotlin.math.abs(SkColorGetR(expected) - SkColorGetR(actual))
        val dG = kotlin.math.abs(SkColorGetG(expected) - SkColorGetG(actual))
        val dB = kotlin.math.abs(SkColorGetB(expected) - SkColorGetB(actual))
        assertTrue(maxOf(dA, dR, dG, dB) <= tolerance) {
            "$msg expected ${"0x%08X".format(expected)}, got ${"0x%08X".format(actual)} (max diff ${maxOf(dA, dR, dG, dB)})"
        }
    }

    // -- drawPaint ----------------------------------------------------------

    @Test
    fun `drawPaint fills the entire canvas when no clip is set`() {
        val bitmap = render(8, 8, bg = SK_ColorBLACK) {
            drawPaint(SkPaint(SK_ColorRED))
        }
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(SK_ColorRED, bitmap.getPixel(x, y), "($x,$y) must be red")
            }
        }
    }

    @Test
    fun `drawPaint respects the active clip and leaves outside pixels untouched`() {
        val bitmap = render(10, 10, bg = SK_ColorWHITE) {
            save()
            clipRect(SkRect.MakeLTRB(2f, 3f, 7f, 8f))
            drawPaint(SkPaint(SK_ColorBLUE))
            restore()
        }
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if (x in 2 until 7 && y in 3 until 8) SK_ColorBLUE else SK_ColorWHITE
                assertEquals(expected, bitmap.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `drawPaint with semi-transparent paint composites SrcOver onto destination`() {
        // 50%-alpha red over opaque black ⇒ ~ (128, 0, 0, 255) in non-premul.
        val bitmap = render(4, 4, bg = SK_ColorBLACK) {
            drawPaint(SkPaint(SkColorSetARGB(128, 255, 0, 0)))
        }
        val px = bitmap.getPixel(0, 0)
        // SrcOver in our integer pipeline: outA=255, outR ≈ (255*128) / 255 ≈ 128.
        assertEquals(0xFF, SkColorGetA(px))
        assertTrue(SkColorGetR(px) in 126..129) { "R = ${SkColorGetR(px)}" }
        assertEquals(0, SkColorGetG(px))
        assertEquals(0, SkColorGetB(px))
    }

    @Test
    fun `drawPaint with fully transparent paint is a no-op`() {
        val bitmap = render(4, 4, bg = SK_ColorWHITE) {
            drawPaint(SkPaint(SK_ColorTRANSPARENT))
        }
        for (y in 0 until 4) {
            for (x in 0 until 4) assertEquals(SK_ColorWHITE, bitmap.getPixel(x, y))
        }
    }

    // -- saveLayer ----------------------------------------------------------

    @Test
    fun `saveLayer with null paint is equivalent to drawing directly`() {
        val direct = render(20, 20, bg = SK_ColorWHITE) {
            drawRect(SkRect.MakeLTRB(4f, 4f, 16f, 16f), SkPaint(SK_ColorRED))
        }
        val layered = render(20, 20, bg = SK_ColorWHITE) {
            saveLayer(null, null)
            drawRect(SkRect.MakeLTRB(4f, 4f, 16f, 16f), SkPaint(SK_ColorRED))
            restore()
        }
        for (i in direct.pixels.indices) {
            assertEquals(direct.pixels[i], layered.pixels[i],
                "pixel $i: direct=${"0x%08X".format(direct.pixels[i])}, layered=${"0x%08X".format(layered.pixels[i])}")
        }
    }

    @Test
    fun `saveLayer with bounds restricts the layer to those device pixels`() {
        // Draw a RED rect that overflows the layer bounds — only the portion
        // inside the layer must show up in the parent.
        val bitmap = render(20, 20, bg = SK_ColorWHITE) {
            saveLayer(SkRect.MakeLTRB(5f, 5f, 15f, 15f), null)
            // Paint a much larger rect; only [5,5)..[15,15) should land.
            drawRect(SkRect.MakeLTRB(0f, 0f, 20f, 20f), SkPaint(SK_ColorBLUE))
            restore()
        }
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                val expected = if (x in 5 until 15 && y in 5 until 15) SK_ColorBLUE else SK_ColorWHITE
                assertEquals(expected, bitmap.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `drawRect imageFilter captures source outside output clip`() {
        val bitmap = render(90, 50, bg = SK_ColorWHITE) {
            save()
            clipRect(SkRect.MakeLTRB(40f, 0f, 80f, 40f))
            drawRect(
                SkRect.MakeLTRB(0f, 0f, 40f, 40f),
                SkPaint(SK_ColorRED).apply {
                    imageFilter = SkImageFilters.Offset(40f, 0f, input = null)
                },
            )
            restore()
        }

        assertEquals(SK_ColorWHITE, bitmap.getPixel(35, 5), "source outside output clip")
        assertEquals(SK_ColorRED, bitmap.getPixel(45, 5), "offset source inside output clip")
        assertEquals(SK_ColorWHITE, bitmap.getPixel(85, 5), "outside output clip")
    }

    @Test
    fun `drawRect crop offset imageFilter captures source before output clip`() {
        val bitmap = render(90, 50, bg = SK_ColorWHITE) {
            save()
            clipRect(SkRect.MakeLTRB(40f, 0f, 80f, 40f))
            drawRect(
                SkRect.MakeLTRB(0f, 0f, 40f, 40f),
                SkPaint(SK_ColorRED).apply {
                    imageFilter = SkImageFilters.Offset(
                        dx = 40f,
                        dy = 0f,
                        input = null,
                        cropRect = SkRect.MakeLTRB(40f, 0f, 80f, 40f),
                    )
                },
            )
            restore()
        }

        assertEquals(SK_ColorWHITE, bitmap.getPixel(35, 5), "source outside output clip")
        assertEquals(SK_ColorRED, bitmap.getPixel(45, 5), "crop offset source inside output clip")
        assertEquals(SK_ColorWHITE, bitmap.getPixel(85, 5), "outside output clip")
    }

    @Test
    fun `saveLayer forwards F16 color type flag to raster layer allocation`() {
        val device = RecordingLayerDevice(SkBitmapDevice(SkBitmap(8, 8)))
        SkCanvas(device).apply {
            saveLayer(SaveLayerRec(flags = F16_COLOR_TYPE_FLAG))
            drawPaint(SkPaint(SK_ColorRED))
            restore()
        }

        assertEquals(SkColorType.kRGBA_F16Norm, device.lastRequestedLayerColorType)
    }

    @Test
    fun `saveLayer with alpha=128 paint composites the layer at 50 percent over destination`() {
        // Layer paint with alpha=128, opaque RED inside; black background.
        val bitmap = render(8, 8, bg = SK_ColorBLACK) {
            val layerPaint = SkPaint(SkColorSetARGB(128, 0, 0, 0))
            saveLayer(null, layerPaint)
            drawPaint(SkPaint(SK_ColorRED))
            restore()
        }
        // After SrcOver: outA=255, outR ≈ 128, G=B=0.
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val px = bitmap.getPixel(x, y)
                assertEquals(0xFF, SkColorGetA(px), "($x,$y) alpha")
                assertTrue(SkColorGetR(px) in 126..130) { "($x,$y) R = ${SkColorGetR(px)}" }
                assertEquals(0, SkColorGetG(px))
                assertEquals(0, SkColorGetB(px))
            }
        }
    }

    @Test
    fun `saveLayer is isolated — draws inside the layer don't leak through restore for transparent paint`() {
        // alpha=0 layer paint ⇒ restore composites nothing.
        val bitmap = render(8, 8, bg = SK_ColorWHITE) {
            saveLayer(null, SkPaint(SK_ColorTRANSPARENT))
            drawPaint(SkPaint(SK_ColorRED))
            restore()
        }
        for (i in bitmap.pixels.indices) {
            assertEquals(SK_ColorWHITE, bitmap.pixels[i], "pixel $i should be untouched")
        }
    }

    @Test
    fun `nested saveLayer calls unwind correctly`() {
        // Outer layer covers everything; inner layer covers a 4×4 region.
        // Inner draws GREEN; outer also has a RED post-draw inside outer-inner gap.
        val bitmap = render(10, 10, bg = SK_ColorWHITE) {
            saveLayer(null, null)
            drawRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f), SkPaint(SK_ColorRED))
            saveLayer(SkRect.MakeLTRB(3f, 3f, 7f, 7f), null)
            drawPaint(SkPaint(SK_ColorGREEN))
            restore() // pop inner — GREEN composites onto RED inside [3,7)x[3,7)
            restore() // pop outer — final image composites onto WHITE
        }
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if (x in 3 until 7 && y in 3 until 7) SK_ColorGREEN else SK_ColorRED
                assertEquals(expected, bitmap.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `saveLayer preserves CTM translate so source-space coordinates land in the same parent pixels`() {
        // With a translate(3, 4), drawing a rect at source (0..2, 0..2) must
        // land at parent pixels (3..5, 4..6) regardless of whether a saveLayer
        // is in flight.
        val direct = render(10, 10, bg = SK_ColorWHITE) {
            translate(3f, 4f)
            drawRect(SkRect.MakeWH(2f, 2f), SkPaint(SK_ColorBLUE))
        }
        val layered = render(10, 10, bg = SK_ColorWHITE) {
            translate(3f, 4f)
            saveLayer(null, null)
            drawRect(SkRect.MakeWH(2f, 2f), SkPaint(SK_ColorBLUE))
            restore()
        }
        for (i in direct.pixels.indices) {
            assertEquals(direct.pixels[i], layered.pixels[i], "pixel $i diverged after saveLayer")
        }
    }

    @Test
    fun `saveLayer with empty bounds drops draws and leaves parent untouched`() {
        // bounds entirely outside the clip ⇒ empty layer.
        val bitmap = render(8, 8, bg = SK_ColorWHITE) {
            saveLayer(SkRect.MakeLTRB(100f, 100f, 200f, 200f), null)
            drawPaint(SkPaint(SK_ColorRED))
            restore()
        }
        for (i in bitmap.pixels.indices) {
            assertEquals(SK_ColorWHITE, bitmap.pixels[i])
        }
    }

    @Test
    fun `restore without matching save is a no-op`() {
        val bitmap = render(4, 4, bg = SK_ColorWHITE) {
            // Extra restores below the root state must not crash or pop the root.
            restore()
            restore()
            drawRect(SkRect.MakeWH(4f, 4f), SkPaint(SK_ColorRED))
        }
        for (i in bitmap.pixels.indices) {
            assertEquals(SK_ColorRED, bitmap.pixels[i])
        }
    }

    @Test
    fun `saveLayer with paint blendMode kSrc replaces destination`() {
        // kSrc: out = src. Layer paints opaque GREEN. Restore via paint.blendMode=kSrc
        // must overwrite the WHITE background entirely inside the layer bounds.
        val bitmap = render(8, 8, bg = SK_ColorWHITE) {
            val layerPaint = SkPaint(SK_ColorBLACK).apply {
                blendMode = org.skia.foundation.SkBlendMode.kSrc
            }
            saveLayer(null, layerPaint)
            drawPaint(SkPaint(SK_ColorGREEN))
            restore()
        }
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertColorEquals(SK_ColorGREEN, bitmap.getPixel(x, y), msg = "($x,$y)")
            }
        }
    }

    @Test
    fun `saveLayer with paint blendMode kPlus adds layer onto destination`() {
        // kPlus: out = clamp(src + dst). Layer is opaque RED (255,0,0,255) over
        // a background of dark BLUE (0,0,128,255). After kPlus composite the
        // result is (255, 0, 128, 255).
        val bg = SkColorSetARGB(0xFF, 0, 0, 0x80)
        val bitmap = render(8, 8, bg = bg) {
            val layerPaint = SkPaint(SK_ColorBLACK).apply {
                blendMode = org.skia.foundation.SkBlendMode.kPlus
            }
            saveLayer(null, layerPaint)
            drawPaint(SkPaint(SK_ColorRED))
            restore()
        }
        val expected = SkColorSetARGB(0xFF, 0xFF, 0, 0x80)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertColorEquals(expected, bitmap.getPixel(x, y), msg = "($x,$y)")
            }
        }
    }

    @Test
    fun `saveLayer with paint blendMode kClear zeroes destination inside layer bounds`() {
        // kClear: out = 0 — even where the layer carries no coverage. We still
        // zero only inside the layer bounds; pixels outside remain WHITE.
        val bitmap = render(10, 10, bg = SK_ColorWHITE) {
            val layerPaint = SkPaint(SK_ColorBLACK).apply {
                blendMode = org.skia.foundation.SkBlendMode.kClear
            }
            saveLayer(SkRect.MakeLTRB(2f, 2f, 8f, 8f), layerPaint)
            // Don't draw inside the layer at all — kClear must still zero the
            // destination within the layer bounds at restore.
            restore()
        }
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if (x in 2 until 8 && y in 2 until 8) SK_ColorTRANSPARENT else SK_ColorWHITE
                assertColorEquals(expected, bitmap.getPixel(x, y), msg = "($x,$y)")
            }
        }
    }

    @Test
    fun `saveLayer with three-args overload accepts a flags argument`() {
        // The flags overload is the API-compatibility surface; we just
        // confirm it accepts `0` and degrades to the no-flags semantics.
        val a = render(8, 8) {
            saveLayer(null, null)
            drawPaint(SkPaint(SK_ColorBLUE))
            restore()
        }
        val b = render(8, 8) {
            saveLayer(null, null, 0)
            drawPaint(SkPaint(SK_ColorBLUE))
            restore()
        }
        for (i in a.pixels.indices) {
            assertEquals(a.pixels[i], b.pixels[i])
        }
        // And we should distinguish from no-saveLayer-at-all (sanity check).
        val baseline = render(8, 8) { /* no draw */ }
        assertNotEquals(a.pixels[0], baseline.pixels[0])
    }

    @Test
    fun `drawImageRect clips source outside image without changing user kClamp shader`() {
        val image = twoByTwoRightBlueImage()
        val src = SkRect.MakeLTRB(1f, 0f, 3f, 2f)
        val dst = SkRect.MakeXYWH(0f, 0f, 20f, 20f)

        val imageRect = render(24, 20, bg = SK_ColorRED) {
            drawImageRect(image, src, dst, SkSamplingOptions.nearest())
        }

        assertEquals(SK_ColorBLUE, imageRect.getPixel(5, 10), "drawImageRect intersected source")
        assertEquals(SK_ColorRED, imageRect.getPixel(15, 10), "drawImageRect clipped destination")

        val shaderClamp = render(24, 20, bg = SK_ColorRED) {
            val localMatrix = SkMatrix.MakeRectToRect(src, dst, SkMatrix.ScaleToFit.kFill_ScaleToFit)!!
            val paint = SkPaint().apply {
                shader = image.makeShader(
                    tileX = SkTileMode.kClamp,
                    tileY = SkTileMode.kClamp,
                    sampling = SkSamplingOptions.nearest(),
                    localMatrix = localMatrix,
                )
            }
            drawPath(SkPath.Rect(dst), paint)
        }

        assertEquals(SK_ColorBLUE, shaderClamp.getPixel(5, 10), "kClamp shader samples the image")
        assertEquals(SK_ColorBLUE, shaderClamp.getPixel(15, 10), "user kClamp shader keeps edge extension")
    }

    private fun twoByTwoRightBlueImage(): SkImage {
        val bitmap = SkBitmap(2, 2).also { it.eraseColor(SK_ColorGREEN) }
        bitmap.setPixel(1, 0, SK_ColorBLUE)
        bitmap.setPixel(1, 1, SK_ColorBLUE)
        return SkImage.Make(bitmap)
    }
}

private const val F16_COLOR_TYPE_FLAG: SaveLayerFlags = 1 shl 4

private class RecordingLayerDevice(
    private val delegate: SkBitmapDevice,
) : SkDevice {
    var lastRequestedLayerColorType: SkColorType? = null
        private set

    override val width: Int get() = delegate.width
    override val height: Int get() = delegate.height

    override fun deviceClipBounds(): SkIRect = delegate.deviceClipBounds()

    override fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) =
        delegate.drawRect(rect, clip, paint)

    override fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint) =
        delegate.drawPaint(ctm, clip, paint)

    override fun drawPath(path: SkPath, ctm: SkMatrix, clip: SkIRect, paint: SkPaint) =
        delegate.drawPath(path, ctm, clip, paint)

    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    ) = delegate.drawImageRect(image, src, devDst, sampling, paint, constraint, clip)

    override fun makeLayerDevice(width: Int, height: Int, colorType: SkColorType?): SkDevice {
        lastRequestedLayerColorType = colorType
        return delegate.makeLayerDevice(width, height, colorType)
    }

    override fun compositeFrom(src: SkDevice, originX: Int, originY: Int, clip: SkIRect, paint: SkPaint?) =
        delegate.compositeFrom(src, originX, originY, clip, paint)

    override fun setActiveClipShape(shape: SkClipShape?) = delegate.setActiveClipShape(shape)

    override fun seedBackdropFrom(
        parent: SkDevice,
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
        backdrop: SkImageFilter?,
    ): Boolean = delegate.seedBackdropFrom(parent, originX, originY, width, height, backdrop)
}
