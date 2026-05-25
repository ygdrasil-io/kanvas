package org.skia.core


import org.graphiks.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.graphiks.math.SkPoint
import org.graphiks.math.SkMatrix

/**
 * Phase I5.3.a — `SkCanvas.drawVertices` solid-color path semantics.
 *
 * Per-vertex colour / UV interpolation is deferred to I5.3.b ; this
 * suite covers triangle iteration under all three [SkVertices.VertexMode]s
 * and the indices-indirection path.
 */
class DrawVerticesTest {

    private fun newWhiteCanvas(w: Int = 30, h: Int = 30): Pair<SkBitmap, SkCanvas> {
        val bm = SkBitmap(w, h)
        bm.eraseColor(0xFFFFFFFF.toInt())
        return Pair(bm, SkCanvas(bm))
    }

    @Test
    fun `triangleCount on a kTriangles vertex array`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(
                SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f),
                SkPoint(10f, 0f), SkPoint(10f, 10f), SkPoint(0f, 10f),
            ),
        )
        assertEquals(2, v.triangleCount())
    }

    @Test
    fun `triangleCount on kTriangleStrip is N-2`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleStrip,
            arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f), SkPoint(10f, 10f)),
        )
        assertEquals(2, v.triangleCount())
    }

    @Test
    fun `triangleCount on kTriangleFan is N-2`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(10f, 10f), SkPoint(0f, 10f)),
        )
        assertEquals(3, v.triangleCount())
    }

    @Test
    fun `triangleAt returns vertex indices honouring fan mode`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(10f, 10f)),
        )
        // Fan : (0, 1, 2), (0, 2, 3).
        assertTrue(v.triangleAt(0).contentEquals(intArrayOf(0, 1, 2)))
        assertTrue(v.triangleAt(1).contentEquals(intArrayOf(0, 2, 3)))
    }

    @Test
    fun `triangleAt with indices indirects through the indirection table`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f), SkPoint(10f, 10f)),
            indices = shortArrayOf(0, 1, 2, 1, 3, 2),
        )
        assertEquals(2, v.triangleCount())
        assertTrue(v.triangleAt(0).contentEquals(intArrayOf(0, 1, 2)))
        assertTrue(v.triangleAt(1).contentEquals(intArrayOf(1, 3, 2)))
    }

    @Test
    fun `MakeCopy rejects mismatched colors size`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkVertices.MakeCopy(
                SkVertices.VertexMode.kTriangles,
                arrayOf(SkPoint(0f, 0f), SkPoint(1f, 0f), SkPoint(0f, 1f)),
                colors = intArrayOf(0xFFFF0000.toInt()),  // wrong : should be 3
            )
        }
    }

    @Test
    fun `drawVertices on empty triangle count is a no-op`() {
        val (bm, canvas) = newWhiteCanvas()
        val before = bm.pixels.copyOf()
        // Single vertex → no triangles in any mode.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f)),
        )
        canvas.drawVertices(v, SkBlendMode.kDst, SkPaint(0xFF000000.toInt()))
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `drawVertices fills a single triangle with paint color`() {
        val (bm, canvas) = newWhiteCanvas()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        // (10, 10) is inside the right-triangle (5,5)-(25,5)-(5,25). Expect blue.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(10, 10))
        // (29, 29) is outside. White.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
        // (4, 4) is outside (left of triangle). White.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(4, 4))
    }

    @Test
    fun `kTriangleFan tessellates a quad into two triangles`() {
        val (bm, canvas) = newWhiteCanvas()
        // Quad 5..25 × 5..25 as a fan : (5,5)-(25,5)-(25,25)-(5,25).
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(25f, 25f), SkPoint(5f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        // Quad interior is filled.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(20, 10))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(10, 20))
        // Outside the quad stays white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(2, 2))
    }

    // ─── Phase I5.3.b — per-vertex colour interpolation ────────────

    @Test
    fun `per-vertex colors with same color everywhere paints solid`() {
        val (bm, canvas) = newWhiteCanvas()
        // Triangle (5,5)-(25,5)-(5,25), all 3 vertices red.
        val red = 0xFFFF0000.toInt()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f)),
            colors = intArrayOf(red, red, red),
        )
        canvas.drawVertices(v, SkBlendMode.kDst, SkPaint(0xFF000000.toInt()))
        // Interior pixel is red.
        assertEquals(red, bm.getPixel(10, 10))
        // Outside stays white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
    }

    @Test
    fun `per-vertex colors interpolate linearly between vertices`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        // Triangle (5,5)-red, (35,5)-green, (5,35)-blue.
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(35f, 5f), SkPoint(5f, 35f)),
            colors = intArrayOf(red, green, blue),
        )
        canvas.drawVertices(v, SkBlendMode.kDst, SkPaint(0xFF000000.toInt()))
        // Near red vertex (5, 5) → strong red.
        val nearRed = bm.getPixel(7, 7)
        val rR = (nearRed shr 16) and 0xFF
        val gR = (nearRed shr 8) and 0xFF
        assertTrue(rR > gR) { "near red vertex : R=$rR should dominate G=$gR" }
        // Near green vertex (35, 5) — pick pixel well inside the
        // triangle's right corner. Hypotenuse is x+y=40 ; (28, 6) is
        // safely inside (28+6=34 < 40).
        val nearGreen = bm.getPixel(28, 6)
        val rG = (nearGreen shr 16) and 0xFF
        val gG = (nearGreen shr 8) and 0xFF
        assertTrue(gG > rG) { "near green vertex : G=$gG should dominate R=$rG" }
        // Near blue vertex (5, 35) → pick (6, 28) on the other interior side.
        val nearBlue = bm.getPixel(6, 28)
        val gB = (nearBlue shr 8) and 0xFF
        val bB = nearBlue and 0xFF
        assertTrue(bB > gB) { "near blue vertex : B=$bB should dominate G=$gB" }
    }

    @Test
    fun `per-vertex colors with paint alpha 0 produces transparent draw`() {
        val (bm, canvas) = newWhiteCanvas()
        val before = bm.pixels.copyOf()
        val red = 0xFFFF0000.toInt()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f)),
            colors = intArrayOf(red, red, red),
        )
        // Paint alpha = 0: vertex colors get modulated to transparent.
        canvas.drawVertices(v, SkBlendMode.kDst, SkPaint(0x00000000))
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `per-vertex colors ignore opaque paint color when no shader is present`() {
        val (bm, canvas) = newWhiteCanvas()
        val red = 0xFFFF0000.toInt()
        val bluePaint = SkPaint(0xFF0000FF.toInt()).apply {
            blendMode = SkBlendMode.kSrc
        }
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f)),
            colors = intArrayOf(red, red, red),
        )
        canvas.drawVertices(v, SkBlendMode.kSrc, bluePaint)
        assertEquals(red, bm.getPixel(10, 10))
    }

    // ─── Phase I5.3.c — texture sampling via shader ────────────────

    /** 16×16 4-quadrant atlas : red / green / blue / white (same layout as DrawAtlasTest). */
    private fun makeColorAtlas(): SkBitmap {
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
    fun `texCoords with shader samples atlas pixels at interpolated UV`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        val atlas = makeColorAtlas().asImage()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = atlas.makeShader()
            isAntiAlias = false
        }
        // Quad at canvas (0..16, 0..16) mapped to full atlas via two triangles.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(
                SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(0f, 16f),
                SkPoint(16f, 0f), SkPoint(16f, 16f), SkPoint(0f, 16f),
            ),
            texCoords = arrayOf(
                SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(0f, 16f),
                SkPoint(16f, 0f), SkPoint(16f, 16f), SkPoint(0f, 16f),
            ),
        )
        canvas.drawVertices(v, SkBlendMode.kModulate, paint)
        // (4, 4) → atlas (4, 4) — red.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(4, 4))
        // (12, 4) → atlas (12, 4) — green.
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(12, 4))
        // (4, 12) → atlas (4, 12) — blue.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(4, 12))
        // (12, 12) → atlas (12, 12) — white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(12, 12))
        // Outside the quad stays white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(20, 20))
    }

    @Test
    fun `texCoords with scaled mapping stretches the atlas across the dst quad`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        val atlas = makeColorAtlas().asImage()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = atlas.makeShader()
            isAntiAlias = false
        }
        // Dst quad 0..32 × 0..32, mapped to atlas (0..16, 0..16) — 2× upscale.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(0f, 0f), SkPoint(32f, 0f), SkPoint(32f, 32f), SkPoint(0f, 32f)),
            texCoords = arrayOf(SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(16f, 16f), SkPoint(0f, 16f)),
        )
        canvas.drawVertices(v, SkBlendMode.kModulate, paint)
        // (5, 5) in dst ↔ atlas (2.5, 2.5) — red.
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(5, 5))
        // (25, 5) in dst ↔ atlas (12.5, 2.5) — green.
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(25, 5))
        // (25, 25) in dst ↔ atlas (12.5, 12.5) — white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(25, 25))
    }

    @Test
    fun `texCoords with linear gradient shader sample gradient local coordinates`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f),
                SkPoint(16f, 0f),
                intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
                null,
                SkTileMode.kClamp,
            )
            blendMode = SkBlendMode.kSrc
            isAntiAlias = false
        }
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(0f, 0f), SkPoint(32f, 0f), SkPoint(0f, 32f)),
            texCoords = arrayOf(SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(0f, 16f)),
        )
        canvas.drawVertices(v, SkBlendMode.kModulate, paint)

        val left = bm.getPixel(4, 4)
        val right = bm.getPixel(24, 4)
        assertTrue(((left shr 16) and 0xFF) > (left and 0xFF)) {
            "left sample should be closer to red: ${Integer.toHexString(left)}"
        }
        assertTrue((right and 0xFF) > ((right shr 16) and 0xFF)) {
            "right sample should be closer to blue: ${Integer.toHexString(right)}"
        }
    }

    @Test
    fun `texCoords honor gradient localMatrix scaling`() {
        val (bm, canvas) = newWhiteCanvas(48, 32)
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f),
                SkPoint(16f, 0f),
                intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
                null,
                SkTileMode.kClamp,
                localMatrix = SkMatrix.MakeScale(2f, 1f),
            )
            blendMode = SkBlendMode.kSrc
            isAntiAlias = false
        }
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(0f, 0f), SkPoint(32f, 0f), SkPoint(0f, 32f)),
            texCoords = arrayOf(SkPoint(0f, 0f), SkPoint(32f, 0f), SkPoint(0f, 32f)),
        )
        canvas.drawVertices(v, SkBlendMode.kModulate, paint)

        val mid = bm.getPixel(12, 4)
        assertTrue(((mid shr 16) and 0xFF) > (mid and 0xFF)) {
            "mid sample should still be red-dominant with 2x localMatrix scale: ${Integer.toHexString(mid)}"
        }
    }

    @Test
    fun `texCoords + per-vertex colors modulate the texture`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        val atlas = makeColorAtlas().asImage()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = atlas.makeShader()
            isAntiAlias = false
        }
        // Same quad as above but with all-half-grey vertex colors.
        // kModulate : tex × vertex / 255. Half-grey (128) × red (255) = 128.
        val grey = 0xFF808080.toInt()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(16f, 16f), SkPoint(0f, 16f)),
            texCoords = arrayOf(SkPoint(0f, 0f), SkPoint(16f, 0f), SkPoint(16f, 16f), SkPoint(0f, 16f)),
            colors = intArrayOf(grey, grey, grey, grey),
        )
        canvas.drawVertices(v, SkBlendMode.kModulate, paint)
        // Atlas red × grey 128 = (128 * 255 + 127) / 255 = 128. So (4, 4) is half-red.
        val px = bm.getPixel(4, 4)
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        // R should be near 128 (modulated red) ; G and B should be near 0.
        assertTrue(r in 100..150) { "expected R≈128, got R=$r" }
        assertTrue(g in 0..30) { "expected G≈0, got G=$g" }
        assertTrue(b in 0..30) { "expected B≈0, got B=$b" }
    }

    @Test
    fun `texCoords + per-vertex colors honor SrcOver vertex blend`() {
        assertTexturedVertexBlend(
            SkBlendMode.kSrcOver,
            vertexColor = 0x80FF0000.toInt(),
            textureColor = 0xFF0000FF.toInt(),
        )
    }

    @Test
    fun `texCoords + per-vertex colors honor Screen vertex blend`() {
        assertTexturedVertexBlend(
            SkBlendMode.kScreen,
            vertexColor = 0xFF00AA44.toInt(),
            textureColor = 0xFF882200.toInt(),
        )
    }

    @Test
    fun `texCoords + per-vertex colors honor Overlay vertex blend`() {
        assertTexturedVertexBlend(
            SkBlendMode.kOverlay,
            vertexColor = 0xFFCC3344.toInt(),
            textureColor = 0xFF4488CC.toInt(),
        )
    }

    @Test
    fun `texCoords + per-vertex colors honor Hue vertex blend`() {
        assertTexturedVertexBlend(
            SkBlendMode.kHue,
            vertexColor = 0xFFFF0000.toInt(),
            textureColor = 0xFF3366CC.toInt(),
        )
    }

    @Test
    fun `per-vertex colors without texCoords still blend with paint shader`() {
        assertTexturedVertexBlend(
            SkBlendMode.kOverlay,
            vertexColor = 0xFFCC3344.toInt(),
            textureColor = 0xFF4488CC.toInt(),
            includeTexCoords = false,
        )
    }

    @Test
    fun `per-vertex colors apply paint color filter`() {
        val (bm, canvas) = newWhiteCanvas(20, 20)
        val red = 0xFFFF0000.toInt()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(2f, 2f), SkPoint(18f, 2f), SkPoint(2f, 18f)),
            colors = intArrayOf(red, red, red),
        )
        val paint = SkPaint(0xFF000000.toInt()).apply {
            blendMode = SkBlendMode.kSrc
            colorFilter = SkColorFilters.Blend(0xFF808080.toInt(), SkBlendMode.kDarken)
        }
        canvas.drawVertices(v, SkBlendMode.kDst, paint)
        assertEquals(0xFF800000.toInt(), bm.getPixel(6, 6))
    }

    @Test
    fun `kTriangleStrip tessellates a strip into a quad`() {
        val (bm, canvas) = newWhiteCanvas()
        // Strip : (5,5)-(25,5)-(5,25)-(25,25). Two triangles cover quad.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleStrip,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f), SkPoint(25f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(2, 2))
    }

    private fun assertTexturedVertexBlend(
        mode: SkBlendMode,
        vertexColor: Int,
        textureColor: Int,
        includeTexCoords: Boolean = true,
    ) {
        val (bm, canvas) = newWhiteCanvas(20, 20)
        val atlas = SkBitmap(2, 2).apply { eraseColor(textureColor) }.asImage()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = atlas.makeShader()
            blendMode = SkBlendMode.kSrc
            isAntiAlias = false
        }
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(2f, 2f), SkPoint(18f, 2f), SkPoint(2f, 18f)),
            texCoords = if (includeTexCoords) {
                arrayOf(SkPoint(0f, 0f), SkPoint(0f, 0f), SkPoint(0f, 0f))
            } else {
                null
            },
            colors = intArrayOf(vertexColor, vertexColor, vertexColor),
        )
        canvas.drawVertices(v, mode, paint)
        val expected = SkBitmapDevice(SkBitmap(1, 1)).blendPixel(textureColor, vertexColor, mode)
        assertEquals(expected, bm.getPixel(6, 6))
    }
}
