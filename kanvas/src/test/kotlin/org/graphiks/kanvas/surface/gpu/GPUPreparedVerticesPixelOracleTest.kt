package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hand-computable CPU-oracle cases for [GPUPreparedVerticesCpuOracle].
 *
 * Expected bytes come from the declared WebGPU `rgba8unorm-srgb`
 * LinearPremul quantization rule: double-precision barycentric over the
 * STORED premultiplied sRGB-encoded values (exactly what the WebGPU vertex
 * stage interpolates — RAW, no vertex-stage decode), f32 width, RGB sRGB
 * DECODE to linear before the material multiply and blend, blending in
 * LINEAR space, and final store: f32 width, RGB ENCODE linear→sRGB (alpha
 * stores linear, never encoded), 8-bit UNORM round-half-up.
 *
 * | stored value | byte |
 * |--------------|------|
 * | 0            |   0  |
 * | 1/8          |  32  |
 * | 1/4          |  64  |
 * | 3/8          |  96  |
 * | 1/2          | 128  |
 * | 5/8          | 159  |
 * | 3/4          | 191  |
 * | 7/8          | 223  |
 * | 1            | 255  |
 *
 * Declared sRGB ENCODE (linear→sRGB) for dyadic linear values:
 *
 * | linear      | byte |
 * |-------------|------|
 * | 1/8         |  99  |
 * | 1/4         | 137  |
 * | 1/2         | 188  |
 * | 3/4         | 225  |
 *
 * Vertex colors and image texels enter as premultiplied sRGB-encoded bytes;
 * the oracle interpolates/samples them RAW, decodes the interpolated RGB
 * sRGB→linear (before image filtering for sampled texels), applies the
 * material result (opaque white paint in the fixture scope → paintAlpha) by
 * multiplication, blends in linear premultiplied space, and stores with the
 * RGB linear→sRGB encode above (alpha stored linear).
 */
class GPUPreparedVerticesPixelOracleTest {

    private fun pixel(output: ByteArray, width: Int, x: Int, y: Int): IntArray {
        val base = (y * width + x) * 4
        return intArrayOf(
            output[base].toInt() and 0xff,
            output[base + 1].toInt() and 0xff,
            output[base + 2].toInt() and 0xff,
            output[base + 3].toInt() and 0xff,
        )
    }

    private fun assertPixel(
        output: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ) {
        val expected = intArrayOf(r, g, b, a)
        val actual = pixel(output, width, x, y)
        assertEquals(expected.toList(), actual.toList(), "pixel($x,$y) expected=$r,$g,$b,$a")
    }

    private fun assertAllAlpha(output: ByteArray, width: Int, height: Int, alpha: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(alpha, pixel(output, width, x, y)[3], "pixel($x,$y) alpha")
            }
        }
    }

    // ---- edge inclusion -------------------------------------------------------

    @Test
    fun `edge inclusion covers the interior and excludes the descending hypotenuse`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.edgeInclusionTriangle(),
        )
        assertPixel(out, 3, 0, 0, 255, 255, 255, 255)
        // (0,1) and (1,0) sample exactly on the x+y=2 hypotenuse, which is
        // traversed downward and therefore excluded by the left/top rule.
        assertPixel(out, 3, 0, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 2, 0, 0, 0, 0)
    }

    // ---- barycentric color ----------------------------------------------------

    @Test
    fun `barycentric color interpolation mixes premultiplied raw vertex colors`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.barycentricColorTriangle(),
        )
        // lambda = (1/2, 1/4, 1/4) over red/green/blue, interpolated RAW.
        assertPixel(out, 2, 0, 0, 128, 64, 64, 255)
        // (1,0) lies on the descending hypotenuse, excluded by the left/top
        // rule; (1,1) is outside.
        assertPixel(out, 2, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 2, 1, 1, 0, 0, 0, 0)
    }

    // ---- UV interpolation + sampling ------------------------------------------

    @Test
    fun `nearest sampling selects the texel under the interpolated uv`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.texturedTriangle(GPUPreparedVerticesFilterMode.NEAREST),
        )
        // uv = (1/4, 1/4) at pixel (0,0) → texel (1,1) which is green.
        assertPixel(out, 2, 0, 0, 0, 255, 0, 255)
    }

    @Test
    fun `linear sampling averages the four texel centres around the interpolated uv`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.texturedTriangle(GPUPreparedVerticesFilterMode.LINEAR),
        )
        // uv = (1/4, 1/4) → fx = fy = 1/2 → average of the four DECODED texel
        // centres (red/white/white/green, linear) re-encoded on store.
        assertPixel(out, 2, 0, 0, 225, 225, 188, 255)
    }

    // ---- vertex-color primitive blend ------------------------------------------

    @Test
    fun `vertex color primitive blend modulates the sampled texel`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.primitiveBlendTriangle(),
        )
        // Gray (188 sRGB ≈ 1/2 linear) vertex color over an opaque red texel.
        assertPixel(out, 2, 0, 0, 188, 0, 0, 255)
    }

    // ---- paint alpha applied once ---------------------------------------------

    @Test
    fun `paint alpha is applied exactly once`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.halfAlphaRedTriangle(),
        )
        // Red (1,0,0,1) scaled by paintAlpha 1/2: premultiplied (1/2,0,0,1/2)
        // in linear; the red channel is re-encoded linear→sRGB on store
        // (encode(1/2)=188) while alpha stays linear (128).
        assertPixel(out, 2, 0, 0, 188, 0, 0, 128)
    }

    // ---- strip winding canonicalization ---------------------------------------

    @Test
    fun `triangle strip canonicalization covers the whole quad with alternating winding`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.stripQuad(),
        )
        assertAllAlpha(out, 2, 2, 255)
        // Triangle 0 = (v0,v1,v2) = red/green/blue.
        assertPixel(out, 2, 0, 0, 128, 64, 64, 255)
        // Triangle 1 = (v2,v1,v3) = blue/green/white, lambda=(1/4,1/4,1/2).
        assertPixel(out, 2, 1, 1, 128, 191, 191, 255)
    }

    @Test
    fun `shared diagonal pixels are claimed by exactly one triangle`() {
        // The strip quad's diagonal (2,0)-(0,2) passes through the centres of
        // pixels (1,0) and (0,1). Fixed-function rasterizers claim an
        // on-edge pixel for exactly one triangle; the left/top rule claims it
        // for triangle 1 (whose diagonal edge is traversed upward). The
        // translucent strip makes a double claim observable: src-over twice
        // would raise alpha to 128 + 128·(1-128/255) = 192 instead of 128.
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.translucentStripQuad(),
        )
        // (1,0): triangle 1 = blue/green/white with lambda (1/4,3/4,0).
        assertPixel(out, 2, 1, 0, 0, 96, 32, 128)
        // (0,1): triangle 1 with lambda (3/4,1/4,0).
        assertPixel(out, 2, 0, 1, 0, 32, 96, 128)
    }

    // ---- fan canonicalization --------------------------------------------------

    @Test
    fun `triangle fan canonicalization covers the whole quad with two triangles`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.fanQuad(),
        )
        assertAllAlpha(out, 3, 2, 255)
        // Triangle 0 = (v0,v1,v2) = red/green/blue.
        assertPixel(out, 3, 1, 0, 128, 64, 64, 255)
        // Triangle 1 = (v0,v2,v3) = red/blue/white, lambda=(1/4,1/2,1/4).
        assertPixel(out, 3, 1, 1, 128, 64, 191, 255)
    }

    // ---- rect clip -------------------------------------------------------------

    @Test
    fun `rect clip rejects sample points outside the clip bounds`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.clippedTriangle(),
        )
        assertPixel(out, 2, 0, 0, 255, 255, 255, 255)
        assertPixel(out, 2, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 2, 0, 1, 0, 0, 0, 0)
        assertPixel(out, 2, 1, 1, 0, 0, 0, 0)
    }

    @Test
    fun `horizontal edge pixels follow the left top horizontal clause`() {
        // The top horizontal edge y=0.5 passes through the sample centres of
        // pixels (0,0) and (1,0); a rightward-traversed horizontal edge is
        // excluded, so only the interior pixel (0,1) is covered.
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.horizontalEdgeTriangle(),
        )
        assertPixel(out, 3, 0, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 0, 1, 255, 255, 255, 255)
        assertPixel(out, 3, 1, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 0, 2, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 2, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 2, 0, 0, 0, 0)
    }

    // ---- affine transform -------------------------------------------------------

    @Test
    fun `affine transform maps local vertices to device space before rasterization`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.translatedTriangle(),
        )
        assertPixel(out, 5, 2, 2, 255, 255, 255, 255)
        assertPixel(out, 5, 1, 1, 0, 0, 0, 0)
        assertPixel(out, 5, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun `affine scale shrinks the triangle footprint`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.scaledTriangle(),
        )
        assertPixel(out, 2, 0, 0, 255, 255, 255, 255)
        assertPixel(out, 2, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 2, 0, 1, 0, 0, 0, 0)
    }

    @Test
    fun `explicit indices select which triangle renders`() {
        val first = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.indexedTriangleSelection(intArrayOf(0, 1, 2)),
        )
        // Triangle A only: (0,0) at lambda (1/2,1/4,1/4), (4,0) empty.
        assertPixel(first, 6, 0, 0, 128, 64, 64, 255)
        assertPixel(first, 6, 4, 0, 0, 0, 0, 0)

        val second = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.indexedTriangleSelection(intArrayOf(3, 4, 5)),
        )
        // Triangle B only: (4,0) at lambda (1/2,1/4,1/4) of magenta/cyan/yellow,
        // (0,0) empty.
        assertPixel(second, 6, 4, 0, 191, 128, 191, 255)
        assertPixel(second, 6, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun `skew transform shears the triangle footprint`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.skewedTriangle(),
        )
        // The skew maps the unit triangle to device (0,0),(2,2),(0,2) with
        // interior x <= y and its x=y edge traversed downward (excluded by
        // the left/top rule), so only device pixel (0,1) is covered — a
        // pixel the unskewed triangle (hypotenuse x+y=2) would not cover.
        // Pixel (0,0) sits exactly on the excluded x=y edge.
        assertPixel(out, 3, 0, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 0, 1, 255, 255, 255, 255)
        assertPixel(out, 3, 1, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 0, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 1, 0, 0, 0, 0)
        assertPixel(out, 3, 0, 2, 0, 0, 0, 0)
        assertPixel(out, 3, 1, 2, 0, 0, 0, 0)
        assertPixel(out, 3, 2, 2, 0, 0, 0, 0)
    }

    // ---- final blend modes ------------------------------------------------------

    @Test
    fun `src over composites a translucent source over an opaque destination`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(0, 0, 255, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC_OVER,
                    paintAlpha = 1f,
                ),
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(255, 0, 0, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC_OVER,
                    paintAlpha = 0.5f,
                ),
            ),
        )
        // dst blue (0,0,1,1), src red (1/2,0,0,1/2) → (1/2,0,1/2,1) linear,
        // RGB re-encoded on store (encode(1/2)=188), alpha 255.
        assertPixel(out, 2, 0, 0, 188, 0, 188, 255)
    }

    @Test
    fun `plus blend adds premultiplied channels`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(255, 0, 0, 255),
                    blendMode = GPUPreparedVerticesBlendMode.PLUS,
                    paintAlpha = 1f,
                ),
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(0, 0, 255, 255),
                    blendMode = GPUPreparedVerticesBlendMode.PLUS,
                    paintAlpha = 1f,
                ),
            ),
        )
        assertPixel(out, 2, 0, 0, 255, 0, 255, 255)
    }

    @Test
    fun `src blend replaces the destination`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(0, 0, 255, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC,
                    paintAlpha = 1f,
                ),
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(255, 0, 0, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC,
                    paintAlpha = 1f,
                ),
            ),
        )
        assertPixel(out, 2, 0, 0, 255, 0, 0, 255)
    }

    @Test
    fun `src in blend multiplies the source by the destination alpha`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(0, 0, 255, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC_OVER,
                    paintAlpha = 1f,
                ),
                GPUPreparedVerticesTestFixtures.solidColorTriangle(
                    color = intArrayOf(255, 0, 0, 255),
                    blendMode = GPUPreparedVerticesBlendMode.SRC_IN,
                    paintAlpha = 0.5f,
                ),
            ),
        )
        // Opaque destination alpha 1 → src (1/2,0,0,1/2) linear * 1, RGB
        // re-encoded on store (encode(1/2)=188), alpha stays linear 128.
        assertPixel(out, 2, 0, 0, 188, 0, 0, 128)
    }

    // ---- delta comparator -------------------------------------------------------

    @Test
    fun `delta comparator reports identical buffers as zero delta`() {
        val a = byteArrayOf(0, 1, 2, 3)
        val delta = GPUPreparedVerticesCpuOracle.comparePixels(a, a.copyOf())
        assertEquals(0, delta.maxChannelDelta)
        assertEquals(0, delta.differingChannels)
        assertEquals(4, delta.comparedChannels)
        assertTrue(delta.matchesWithinOneLsb)
    }

    @Test
    fun `delta comparator counts one-lsb differences as matching`() {
        val a = byteArrayOf(128.toByte(), 0, 0, 255.toByte())
        val b = byteArrayOf(127.toByte(), 0, 0, 255.toByte())
        val delta = GPUPreparedVerticesCpuOracle.comparePixels(a, b)
        assertEquals(1, delta.maxChannelDelta)
        assertEquals(1, delta.differingChannels)
        assertEquals(4, delta.comparedChannels)
        assertTrue(delta.matchesWithinOneLsb)
    }

    @Test
    fun `delta comparator flags deltas beyond one lsb`() {
        val a = byteArrayOf(128.toByte(), 0, 0, 255.toByte())
        val b = byteArrayOf(123.toByte(), 9, 0, 255.toByte())
        val delta = GPUPreparedVerticesCpuOracle.comparePixels(a, b)
        assertEquals(9, delta.maxChannelDelta)
        assertEquals(2, delta.differingChannels)
        assertFalse(delta.matchesWithinOneLsb)
    }

    @Test
    fun `delta comparator compares every channel in both buffers`() {
        val a = ByteArray(8) { it.toByte() }
        val b = ByteArray(8) { (it + 1).toByte() }
        val delta = GPUPreparedVerticesCpuOracle.comparePixels(a, b)
        assertEquals(1, delta.maxChannelDelta)
        assertEquals(8, delta.differingChannels)
        assertEquals(8, delta.comparedChannels)
    }

    @Test
    fun `render output has the declared pixel size`() {
        val out = GPUPreparedVerticesCpuOracle.renderVertices(
            GPUPreparedVerticesTestFixtures.barycentricColorTriangle(),
        )
        assertEquals(2 * 2 * 4, out.size)
    }
}
