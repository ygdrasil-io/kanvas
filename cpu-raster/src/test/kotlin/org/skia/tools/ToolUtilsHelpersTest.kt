package org.skia.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.opentype.OpenTypeTypeface
import org.graphiks.math.SkRect

/**
 * S7-B sprint — covers the four ToolUtils helpers promoted from inline
 * GM duplicates :
 *
 *  - [ToolUtils.create_checkerboard_shader]
 *  - [ToolUtils.rotated_checkerboard_shader]
 *  - [ToolUtils.create_checkerboard_image]
 *  - [ToolUtils.CreateStringImage]
 *
 * Each test renders the helper through a small bitmap and asserts on the
 * produced pixel pattern (checkerboard quadrants for the shader/image
 * helpers ; non-blank inked region for the string-image helper).
 */
class ToolUtilsHelpersTest {
    @Test
    fun `portable font helpers use OpenType typefaces`() {
        val defaultTypeface = ToolUtils.DefaultPortableTypeface()
        val sans = ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal())
        val serif = ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Italic())
        val mono = ToolUtils.CreatePortableTypeface("monospace", SkFontStyle.Bold())

        assertTrue(defaultTypeface is OpenTypeTypeface)
        assertTrue(sans is OpenTypeTypeface)
        assertTrue(serif is OpenTypeTypeface)
        assertTrue(mono is OpenTypeTypeface)
        assertEquals("Liberation Sans", defaultTypeface.getFamilyName())
    }

    @Test
    fun `resource typeface helper loads TTF through OpenType`() {
        val typeface = ToolUtils.CreateTypefaceFromResource("fonts/ReallyBigA.ttf")

        assertNotNull(typeface)
        assertTrue(typeface is OpenTypeTypeface)
    }

    @Test
    fun `TestFontMgr exposes portable Liberation families`() {
        val fontMgr = ToolUtils.TestFontMgr()

        assertEquals(3, fontMgr.countFamilies())
        assertNotNull(fontMgr.legacyMakeTypeface("sans-serif", SkFontStyle.Normal()))
        assertNotNull(fontMgr.legacyMakeTypeface("serif", SkFontStyle.BoldItalic()))
        assertNotNull(fontMgr.legacyMakeTypeface("monospace", SkFontStyle.Italic()))
    }


    @Test
    fun `create_checkerboard_shader paints alternating quadrants`() {
        val size = 4
        val shader = ToolUtils.create_checkerboard_shader(SK_ColorWHITE, SK_ColorBLACK, size)
        // Render the shader onto a 2*size x 2*size bitmap — should match
        // the underlying tile exactly with no aliasing (kRepeat tiling
        // at scale 1).
        val bm = SkBitmap(2 * size, 2 * size)
        bm.eraseColor(0xFFFF00FF.toInt()) // magenta sentinel
        val canvas = SkCanvas(bm)
        val paint = SkPaint().apply { this.shader = shader }
        canvas.drawRect(SkRect.MakeWH((2 * size).toFloat(), (2 * size).toFloat()), paint)

        // Recipe : erase to c1, then overlay c2 on the (0,0,size,size)
        // and (size,size,2*size,2*size) quadrants. So top-left and
        // bottom-right are c2 ; top-right and bottom-left stay c1.
        assertEquals(SK_ColorBLACK, bm.getPixel(0, 0), "top-left = c2 (black)")
        assertEquals(SK_ColorWHITE, bm.getPixel(size, 0), "top-right = c1 (white)")
        assertEquals(SK_ColorWHITE, bm.getPixel(0, size), "bottom-left = c1 (white)")
        assertEquals(SK_ColorBLACK, bm.getPixel(size, size), "bottom-right = c2 (black)")
    }

    @Test
    fun `create_checkerboard_shader rejects non-positive size`() {
        try {
            ToolUtils.create_checkerboard_shader(SK_ColorWHITE, SK_ColorBLACK, 0)
            error("expected IllegalArgumentException for size=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rotated_checkerboard_shader differs from straight checkerboard at small scale`() {
        val size = 4
        val plain = ToolUtils.create_checkerboard_shader(SK_ColorWHITE, SK_ColorBLACK, size)
        val rotated = ToolUtils.rotated_checkerboard_shader(
            SK_ColorWHITE, SK_ColorBLACK, size, angleDegrees = 30f, scale = 0.75f,
        )
        assertNotNull(rotated)

        fun renderToBitmap(s: org.skia.foundation.SkShader): SkBitmap {
            val bm = SkBitmap(2 * size, 2 * size)
            bm.eraseColor(0xFFFF00FF.toInt())
            val canvas = SkCanvas(bm)
            val paint = SkPaint().apply { this.shader = s }
            canvas.drawRect(SkRect.MakeWH((2 * size).toFloat(), (2 * size).toFloat()), paint)
            return bm
        }
        val plainBm = renderToBitmap(plain)
        val rotatedBm = renderToBitmap(rotated)
        // Both shouldn't be identical — the rotation introduces sampling
        // differences in at least one pixel.
        var anyDiff = false
        for (i in plainBm.pixels.indices) {
            if (plainBm.pixels[i] != rotatedBm.pixels[i]) { anyDiff = true; break }
        }
        assertTrue(anyDiff, "rotated shader must produce a different pixel pattern")
    }

    @Test
    fun `create_checkerboard_image alternates quadrants per checkSize`() {
        // Verify the same quadrant rule across an image (drawn through
        // draw_checkerboard internally). Use checkSize=4 so a 2*4=8-pixel
        // tile naturally fits the 8x8 bitmap.
        val img = ToolUtils.create_checkerboard_image(8, 8, SK_ColorWHITE, SK_ColorBLACK, 4)
        assertEquals(8, img.width)
        assertEquals(8, img.height)

        // Render image to a bitmap to inspect pixels.
        val bm = SkBitmap(8, 8)
        bm.eraseColor(0xFFFF00FF.toInt())
        val canvas = SkCanvas(bm)
        canvas.drawImage(img, 0f, 0f)

        // Same quadrant rule as create_checkerboard_shader (which
        // create_checkerboard_image goes through internally).
        assertEquals(SK_ColorBLACK, bm.getPixel(0, 0), "image: top-left = c2 (black)")
        assertEquals(SK_ColorWHITE, bm.getPixel(4, 0), "image: top-right = c1 (white)")
        assertEquals(SK_ColorWHITE, bm.getPixel(0, 4), "image: bottom-left = c1 (white)")
        assertEquals(SK_ColorBLACK, bm.getPixel(4, 4), "image: bottom-right = c2 (black)")
    }

    @Test
    fun `create_checkerboard_image rejects non-positive dimensions`() {
        try {
            ToolUtils.create_checkerboard_image(0, 8, SK_ColorWHITE, SK_ColorBLACK, 4)
            error("expected IllegalArgumentException for w=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            ToolUtils.create_checkerboard_image(8, 0, SK_ColorWHITE, SK_ColorBLACK, 4)
            error("expected IllegalArgumentException for h=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `CreateStringImage produces a non-blank image with text pixels`() {
        // 60x40 image, draw black "A" at baseline (5, 30), 24pt.
        val img = ToolUtils.CreateStringImage(60, 40, SK_ColorBLACK, 5, 30, 24, "A")
        assertEquals(60, img.width)
        assertEquals(40, img.height)

        val bm = SkBitmap(60, 40)
        bm.eraseColor(0xFFFFFFFF.toInt()) // white BG
        val canvas = SkCanvas(bm)
        canvas.drawImage(img, 0f, 0f)
        // The image is transparent BG + black glyph ink ; so blitting it
        // onto the white bitmap should leave at least one non-white pixel.
        val anyNonWhite = bm.pixels.any { it != 0xFFFFFFFF.toInt() }
        assertTrue(anyNonWhite, "CreateStringImage must paint at least one non-white pixel")
    }

    @Test
    fun `CreateStringImage with empty string produces fully transparent image`() {
        val img = ToolUtils.CreateStringImage(20, 20, SK_ColorBLACK, 0, 15, 16, "")
        // Render onto opaque-white BG ; should remain all white because
        // the source image is fully transparent (drawString no-op for "").
        val bm = SkBitmap(20, 20)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)
        canvas.drawImage(img, 0f, 0f)
        for (p in bm.pixels) {
            assertEquals(0xFFFFFFFF.toInt(), p, "empty-string image must be transparent")
        }
    }

    @Test
    fun `CreateStringImage honours the color parameter`() {
        // Pure red text on a transparent BG ; the drawn ink must include
        // at least one pixel whose RGB is dominated by red.
        val img = ToolUtils.CreateStringImage(60, 40, 0xFFFF0000.toInt(), 5, 30, 24, "X")
        val bm = SkBitmap(60, 40)
        bm.eraseColor(0xFFFFFFFF.toInt())
        val canvas = SkCanvas(bm)
        canvas.drawImage(img, 0f, 0f)
        // Look for at least one pixel with R > G && R > B (a red-ish hue).
        var found = false
        for (p in bm.pixels) {
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            if (a == 0xFF && r > g && r > b && r > 100 && g < 240 && b < 240) {
                found = true ; break
            }
        }
        assertTrue(found, "CreateStringImage must paint red glyph pixels")
        assertNotEquals(0, img.width * img.height)
    }
}
