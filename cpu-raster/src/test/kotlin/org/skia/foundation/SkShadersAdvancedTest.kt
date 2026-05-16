package org.skia.foundation


import org.skia.math.SkColor4f
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import kotlin.math.abs

/**
 * R2.8 unit tests for the advanced [SkShaders] factories — `Empty()`,
 * `Color(SkColor4f, SkColorSpace?)`, `Blend(...)`, `MakeFractalNoise(...)`,
 * `MakeTurbulence(...)`.
 *
 * Each factory is exercised through a minimal `setupForDraw` → `shadeRow`
 * cycle (the same path the rasterizer follows), so a row-equality
 * assertion against an expected colour or a hand-computed blend
 * captures regressions in any of (1) the factory function, (2) the
 * shader subclass it builds, (3) the per-row sampling pipeline.
 */
class SkShadersAdvancedTest {

    private val identityXform: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = SkColorSpace.makeSRGB(), dstAT = SkAlphaType.kUnpremul,
    )

    // -- SkShaders.Empty -----------------------------------------------------

    @Test
    fun `Empty produces transparent black for every pixel`() {
        val shader = SkShaders.Empty()
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(6)
        // Fill with sentinel to detect "not written".
        for (i in out.indices) out[i] = 0x11223344
        shader.shadeRow(0, 0, 6, out)
        for (i in 0 until 6) {
            assertEquals(0, out[i], "Empty row pixel $i should be 0x00000000")
        }
    }

    @Test
    fun `Empty F16 path emits zero premul`() {
        val shader = SkShaders.Empty()
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = FloatArray(4 * 3) { 0.5f }
        shader.shadeRowF16(0, 0, 3, out)
        for (i in 0 until 12) {
            assertEquals(0f, out[i], 0f, "Empty F16 component $i")
        }
    }

    @Test
    fun `Empty sampleAtLocal returns 0`() {
        val shader = SkShaders.Empty()
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        assertEquals(0, shader.sampleAtLocal(0f, 0f))
        assertEquals(0, shader.sampleAtLocal(-100f, 100f))
    }

    // -- SkShaders.Color(SkColor4f, SkColorSpace?) ---------------------------

    @Test
    fun `Color4f with null cs encodes via SkColor4f toSkColor`() {
        // (0.5, 0.25, 0.75, 1.0) sRGB-encoded should produce the byte
        // (0xFF, 0x80, 0x40, 0xC0) per SkColor4f.toSkColor's quantize.
        val c4 = SkColor4f(0.5f, 0.25f, 0.75f, 1f)
        val shader = SkShaders.Color(c4, cs = null)
        shader.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(2)
        shader.shadeRow(0, 0, 2, out)
        val expected = c4.toSkColor()
        assertEquals(expected, out[0])
        assertEquals(expected, out[1])
    }

    @Test
    fun `Color4f with sRGB cs matches null cs`() {
        val c4 = SkColor4f(0.1f, 0.9f, 0.4f, 0.7f)
        val viaNull = SkShaders.Color(c4, cs = null)
        val viaSrgb = SkShaders.Color(c4, cs = SkColorSpace.makeSRGB())
        viaNull.setupForDraw(SkMatrix.Identity, identityXform)
        viaSrgb.setupForDraw(SkMatrix.Identity, identityXform)
        val a = IntArray(1); val b = IntArray(1)
        viaNull.shadeRow(0, 0, 1, a)
        viaSrgb.shadeRow(0, 0, 1, b)
        assertEquals(a[0], b[0])
    }

    @Test
    fun `Color4f with linear-sRGB cs differs from sRGB on a mid-grey`() {
        // Linear-sRGB encoded 0.5 → sRGB-encoded ~0.735 → byte ~0xBC.
        // Versus naive sRGB-encoded 0.5 → byte 0x80. The two paths must
        // produce *different* bytes (exact value depends on the TF
        // pipeline, but the key invariant is "different").
        val c4 = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        val linearCs = SkColorSpace.makeSRGBLinear()
        val viaLinear = SkShaders.Color(c4, cs = linearCs)
        val viaSrgb = SkShaders.Color(c4, cs = null)
        viaLinear.setupForDraw(SkMatrix.Identity, identityXform)
        viaSrgb.setupForDraw(SkMatrix.Identity, identityXform)
        val a = IntArray(1); val b = IntArray(1)
        viaLinear.shadeRow(0, 0, 1, a)
        viaSrgb.shadeRow(0, 0, 1, b)
        assertNotEquals(a[0], b[0], "linear-sRGB and sRGB Color4f shaders must encode differently")
        // The linear path's byte should be brighter (> 0x80) since sRGB
        // gamma compresses dark values.
        assertTrue(
            SkColorGetR(a[0]) > SkColorGetR(b[0]),
            "linear→sRGB byte ${SkColorGetR(a[0])} should exceed sRGB-byte ${SkColorGetR(b[0])}",
        )
    }

    // -- SkShaders.Blend -----------------------------------------------------

    @Test
    fun `Blend with kSrcOver matches hand-computed formula`() {
        // dst = opaque red, src = half-alpha green.
        // SrcOver: r = s + (1-sa)·d.
        //   sa = 0x80/255 ≈ 0.5019608
        //   s (premul) = (0, 0.5019608·1, 0, 0.5019608)
        //   d (premul) = (1, 0, 0, 1)
        //   out_premul = s + (1-sa)*d
        //              = (0.4980392, 0.5019608, 0, 1)
        //   out_unpremul = (0.4980392, 0.5019608, 0, 1) — full opaque
        //   bytes = (0xFF, 0x7F, 0x80, 0)  (rounding may vary by 1 ULP)
        val dstShader = SkShaders.Color(SkColorSetARGB(0xFF, 0xFF, 0, 0))
        val srcShader = SkShaders.Color(SkColorSetARGB(0x80, 0, 0xFF, 0))
        val blended = SkShaders.Blend(SkBlendMode.kSrcOver, dstShader, srcShader)
        blended.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(2)
        blended.shadeRow(0, 0, 2, out)
        val outA = SkColorGetA(out[0])
        val outR = SkColorGetR(out[0])
        val outG = SkColorGetG(out[0])
        val outB = SkColorGetB(out[0])
        assertEquals(0xFF, outA, "alpha")
        assertEquals(0, outB, "blue")
        // Expect roughly half-half red/green, with mild byte rounding.
        assertTrue(abs(outR - 0x7F) <= 1, "R=$outR expected ≈0x7F")
        assertTrue(abs(outG - 0x80) <= 1, "G=$outG expected ≈0x80")
        // Row uniformity.
        assertEquals(out[0], out[1])
    }

    @Test
    fun `Blend with kClear always emits transparent`() {
        val dstShader = SkShaders.Color(SkColorSetARGB(0xFF, 0x12, 0x34, 0x56))
        val srcShader = SkShaders.Color(SkColorSetARGB(0xFF, 0x78, 0x9A, 0xBC))
        val blended = SkShaders.Blend(SkBlendMode.kClear, dstShader, srcShader)
        blended.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(3)
        blended.shadeRow(0, 0, 3, out)
        for (i in 0 until 3) assertEquals(0, out[i], "pixel $i")
    }

    @Test
    fun `Blend with kSrc equals the src shader regardless of dst`() {
        val dstShader = SkShaders.Color(SkColorSetARGB(0xFF, 0xFF, 0x00, 0x00))
        val srcColour = SkColorSetARGB(0xFF, 0x33, 0x66, 0x99)
        val srcShader = SkShaders.Color(srcColour)
        val blended = SkShaders.Blend(SkBlendMode.kSrc, dstShader, srcShader)
        blended.setupForDraw(SkMatrix.Identity, identityXform)
        val out = IntArray(1)
        blended.shadeRow(5, 7, 1, out)
        assertEquals(srcColour, out[0])
    }

    // -- SkShaders.MakeFractalNoise / MakeTurbulence -------------------------

    @Test
    fun `MakeFractalNoise produces deterministic output for the same seed`() {
        val a = SkShaders.MakeFractalNoise(
            baseFreqX = 0.05f, baseFreqY = 0.05f,
            numOctaves = 3, seed = 7f, tileSize = null,
        )
        val b = SkShaders.MakeFractalNoise(
            baseFreqX = 0.05f, baseFreqY = 0.05f,
            numOctaves = 3, seed = 7f, tileSize = null,
        )
        a.setupForDraw(SkMatrix.Identity, identityXform)
        b.setupForDraw(SkMatrix.Identity, identityXform)
        val out1 = IntArray(16); val out2 = IntArray(16)
        a.shadeRow(0, 0, 16, out1)
        b.shadeRow(0, 0, 16, out2)
        assertTrue(out1.contentEquals(out2), "same seed must produce identical pixel rows")
    }

    @Test
    fun `MakeFractalNoise differs from MakeTurbulence`() {
        val fractal = SkShaders.MakeFractalNoise(
            baseFreqX = 0.1f, baseFreqY = 0.1f,
            numOctaves = 4, seed = 3f, tileSize = null,
        )
        val turbulence = SkShaders.MakeTurbulence(
            baseFreqX = 0.1f, baseFreqY = 0.1f,
            numOctaves = 4, seed = 3f, tileSize = null,
        )
        fractal.setupForDraw(SkMatrix.Identity, identityXform)
        turbulence.setupForDraw(SkMatrix.Identity, identityXform)
        val outF = IntArray(32); val outT = IntArray(32)
        fractal.shadeRow(0, 0, 32, outF)
        turbulence.shadeRow(0, 0, 32, outT)
        // The two flavours use different post-processing (fractal: n·.5
        // + .5; turbulence: |n|), so the row pixel-streams must differ.
        assertFalse(
            outF.contentEquals(outT),
            "FractalNoise and Turbulence with the same parameters must produce different output",
        )
    }

    @Test
    fun `MakeFractalNoise honours tileSize parameter`() {
        // With a 32×32 tile, the noise is stitched at that period — same
        // seed plus tile should still be deterministic.
        val a = SkShaders.MakeFractalNoise(
            baseFreqX = 0.1f, baseFreqY = 0.1f,
            numOctaves = 2, seed = 5f, tileSize = SkISize.Make(32, 32),
        )
        val b = SkShaders.MakeFractalNoise(
            baseFreqX = 0.1f, baseFreqY = 0.1f,
            numOctaves = 2, seed = 5f, tileSize = SkISize.Make(32, 32),
        )
        a.setupForDraw(SkMatrix.Identity, identityXform)
        b.setupForDraw(SkMatrix.Identity, identityXform)
        val out1 = IntArray(8); val out2 = IntArray(8)
        a.shadeRow(0, 0, 8, out1)
        b.shadeRow(0, 0, 8, out2)
        assertTrue(out1.contentEquals(out2), "tile-stitched noise must still be deterministic")
    }

    @Test
    fun `MakeFractalNoise returns a non-null shader instance`() {
        // Smoke test the upstream zero-octaves short-circuit (upstream
        // collapses to a [.5, .5, .5, .5] solid colour; we likewise
        // accept zero octaves without throwing).
        val s = SkShaders.MakeFractalNoise(0.1f, 0.1f, 0, 0f, null)
        assertNotNull(s)
    }
}
