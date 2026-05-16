package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import kotlin.math.abs

/**
 * R1-B unit tests for [SkShaders.Color] and [SkShaders.CoordClamp].
 *
 * Both factories run through a minimal `setupForDraw` → `shadeRow` cycle :
 * the actual rasterisation path the device follows. The xform pipeline is
 * sRGB → sRGB (identity), which keeps the colour bytes round-trip-stable
 * so an equality check against the source colour is meaningful.
 */
class SkShadersTest {

    private val identityXform: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = SkColorSpace.makeSRGB(), dstAT = SkAlphaType.kUnpremul,
    )

    // -- SkShaders.Color -----------------------------------------------------

    @Test
    fun `Color shader returns the constant colour for every covered pixel`() {
        val colour = SkColorSetARGB(0xFF, 0x80, 0x40, 0xC0)
        val shader = SkShaders.Color(colour)
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(5)
        shader.shadeRow(7, 11, 5, out)
        for (i in 0 until 5) {
            assertEquals(colour, out[i], "row pixel $i")
        }
    }

    @Test
    fun `Color shader preserves transparent alpha`() {
        val colour = SkColorSetARGB(0x00, 0xFF, 0x00, 0x00)
        val shader = SkShaders.Color(colour)
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(2)
        shader.shadeRow(0, 0, 2, out)
        assertEquals(colour, out[0])
        assertEquals(colour, out[1])
    }

    @Test
    fun `Color shader F16 path emits premul floats`() {
        // Opaque half-grey: premul should equal the unpremul value (alpha=1).
        val shader = SkShaders.Color(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80))
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = FloatArray(4 * 3)
        shader.shadeRowF16(0, 0, 3, out)
        for (i in 0 until 3) {
            val o = i * 4
            assertTrue(abs(out[o] - 0x80 / 255f) < 0.005f, "R[$i]=${out[o]}")
            assertTrue(abs(out[o + 1] - 0x80 / 255f) < 0.005f, "G[$i]=${out[o + 1]}")
            assertTrue(abs(out[o + 2] - 0x80 / 255f) < 0.005f, "B[$i]=${out[o + 2]}")
            assertEquals(1f, out[o + 3], 1e-6f, "A[$i]")
        }
    }

    @Test
    fun `Color shader F16 path premultiplies semi-transparent colours`() {
        // alpha = 0.5, RGB = white ⇒ premul = (0.5, 0.5, 0.5, 0.5).
        val shader = SkShaders.Color(SkColorSetARGB(0x80, 0xFF, 0xFF, 0xFF))
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = FloatArray(4)
        shader.shadeRowF16(0, 0, 1, out)
        val a = 0x80 / 255f
        assertTrue(abs(out[0] - 1f * a) < 0.005f, "R premul")
        assertTrue(abs(out[1] - 1f * a) < 0.005f, "G premul")
        assertTrue(abs(out[2] - 1f * a) < 0.005f, "B premul")
        assertTrue(abs(out[3] - a) < 0.005f, "A")
    }

    @Test
    fun `Color shader sampleAtLocal returns the constant colour`() {
        val colour = SkColorSetARGB(0xFF, 0x12, 0x34, 0x56)
        val shader = SkShaders.Color(colour)
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        assertEquals(colour, shader.sampleAtLocal(0f, 0f))
        assertEquals(colour, shader.sampleAtLocal(-100f, 100f))
        assertEquals(colour, shader.sampleAtLocal(7.5f, 9.25f))
    }

    // -- SkShaders.CoordClamp ------------------------------------------------

    /**
     * Small 2×2 bitmap shader, used as the child for CoordClamp tests.
     * Colours are picked so each clamped corner is unambiguously identifiable.
     */
    private fun checkerShader(): SkShader {
        val bm = SkBitmap(2, 2)
        bm.setPixel(0, 0, SkColorSetARGB(0xFF, 0xFF, 0, 0))     // red
        bm.setPixel(1, 0, SkColorSetARGB(0xFF, 0, 0xFF, 0))     // green
        bm.setPixel(0, 1, SkColorSetARGB(0xFF, 0, 0, 0xFF))     // blue
        bm.setPixel(1, 1, SkColorSetARGB(0xFF, 0xFF, 0xFF, 0))  // yellow
        return bm.makeShader(SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default)
    }

    @Test
    fun `CoordClamp returns a non-null shader`() {
        val s = SkShaders.CoordClamp(checkerShader(), SkRect(0f, 0f, 2f, 2f))
        assertNotNull(s)
    }

    @Test
    fun `CoordClamp rejects empty rects`() {
        val child = checkerShader()
        assertThrows(IllegalArgumentException::class.java) {
            SkShaders.CoordClamp(child, SkRect(0f, 0f, 0f, 5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkShaders.CoordClamp(child, SkRect(5f, 5f, 5f, 5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            // Reversed rect (negative width).
            SkShaders.CoordClamp(child, SkRect(5f, 0f, 1f, 5f))
        }
    }

    @Test
    fun `CoordClamp clamps device coords to the rect, sampling the nearest child pixel`() {
        // Child = 2×2 bitmap occupying local coords [0, 2)×[0, 2).
        // Clamp to (0, 0, 0.99, 0.99) — the strict top-left quadrant. Every
        // sample maps to red (the (0, 0) pixel of the bitmap, which the
        // kClamp child returns for any (x, y) inside [0, 1)×[0, 1)).
        // The 0.99 right/bottom keeps the clamped local-X strictly < 1, so
        // nearest-neighbour bitmap sampling lands in column 0 even at the
        // boundary.
        val clamped = SkShaders.CoordClamp(checkerShader(), SkRect(0f, 0f, 0.99f, 0.99f))
        clamped.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(4)
        // Walk across x = 0..3 at y = 0. Without clamp we'd hit columns
        // 0,1,1,1 (kClamp child); with clamp we hit only column 0.
        clamped.shadeRow(0, 0, 4, out)
        for (i in 0 until 4) {
            assertEquals(SkColorSetARGB(0xFF, 0xFF, 0, 0), out[i], "pixel $i")
        }
        // y = 1 should also stay clamped to row 0 because top-left rect
        // excludes y >= 1.
        clamped.shadeRow(0, 1, 4, out)
        for (i in 0 until 4) {
            assertEquals(SkColorSetARGB(0xFF, 0xFF, 0, 0), out[i], "pixel $i row 1")
        }
    }

    @Test
    fun `CoordClamp inside the rect behaves like the child`() {
        // Clamp to the full bitmap extent — should match the child exactly
        // for samples inside [0, 2)×[0, 2).
        val child = checkerShader()
        val clamped = SkShaders.CoordClamp(child, SkRect(0f, 0f, 2f, 2f))
        child.setupForDraw(SkMatrix.Identity, identityXform)
        clamped.setupForDraw(SkMatrix.Identity, identityXform)

        val direct = IntArray(2)
        child.shadeRow(0, 0, 2, direct)

        // CoordClamp emits one sample per pixel via the child's
        // sampleAtLocal hook — which returns the same colour as a direct
        // bitmap sample, modulo a 0.5-px centre offset that doesn't
        // matter at (0,0)/(1,0) where the centres still fall inside the
        // correct cell.
        val viaClamp = IntArray(2)
        clamped.shadeRow(0, 0, 2, viaClamp)
        assertEquals(direct[0], viaClamp[0], "inside rect, x=0 matches child")
        assertEquals(direct[1], viaClamp[1], "inside rect, x=1 matches child")
    }

    @Test
    fun `CoordClamp sampleAtLocal pins local coords to the rect`() {
        val child = checkerShader()
        val clamped = SkShaders.CoordClamp(child, SkRect(0f, 0f, 0.5f, 0.5f))
        clamped.setupForDraw(SkMatrix.Identity, identityXform)
        child.setupForDraw(SkMatrix.Identity, identityXform)
        // Sampling outside the clamp rect snaps to the rect boundary
        // (0.5, 0.5) — well inside the bitmap's column 0 / row 0 cell, so
        // the child returns red (the (0, 0) pixel).
        val outOfRectSample = clamped.sampleAtLocal(5f, 5f)
        val directBoundarySample = child.sampleAtLocal(0.5f, 0.5f)
        assertEquals(directBoundarySample, outOfRectSample,
            "clamp(5,5) into [0,0.5]² should equal child.sample(0.5, 0.5)")
        // And the boundary sample must equal red (col 0, row 0).
        assertEquals(SkColorSetARGB(0xFF, 0xFF, 0, 0), outOfRectSample,
            "clamped sample lands in the red cell")
    }
}
