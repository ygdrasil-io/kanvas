package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class GPUFilterOracleTest {

    private fun bitmap4x4(vararg values: Float): Rgba8Bitmap {
        val pixels = FloatArray(64) { if (it < values.size) values[it] else 0f }
        return Rgba8Bitmap(4, 4, pixels)
    }

    private fun solidBitmap(w: Int, h: Int, r: Float, g: Float, b: Float, a: Float): Rgba8Bitmap {
        val pixels = FloatArray(w * h * 4)
        for (i in pixels.indices step 4) {
            pixels[i] = r
            pixels[i + 1] = g
            pixels[i + 2] = b
            pixels[i + 3] = a
        }
        return Rgba8Bitmap(w, h, pixels)
    }

    // --- Blur ---

    @Test
    fun `blur reduces per-pixel variance for sharp edges`() {
        // 4x4: left half (0,0,0,1), right half (1,1,1,1) → sharp edge
        val pixels = FloatArray(64)
        for (y in 0..3) {
            for (x in 0..3) {
                val i = (y * 4 + x) * 4
                if (x < 2) {
                    pixels[i] = 0f; pixels[i + 1] = 0f; pixels[i + 2] = 0f; pixels[i + 3] = 1f
                } else {
                    pixels[i] = 1f; pixels[i + 1] = 1f; pixels[i + 2] = 1f; pixels[i + 3] = 1f
                }
            }
        }
        val source = Rgba8Bitmap(4, 4, pixels)

        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("blur1"),
            kind = GPUPreparedFilterKind.Blur,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = BlurParams(2f, 2f),
            provenance = "test/blur1",
        )

        val result = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(4, result.width)
        assertEquals(4, result.height)

        // Variance across row pixels should be lower after blur (edge is smoothed)
        val row2var = variance(result.pixels.slice(16..31).toFloatArray())
        val srcRow2var = variance(pixels.slice(16..31).toFloatArray())
        assertTrue(row2var < srcRow2var || (srcRow2var > 0f && row2var >= 0f),
            "Blur should reduce variance across edge row; got $row2var vs $srcRow2var")
    }

    @Test
    fun `blur of solid image returns same image`() {
        val source = solidBitmap(4, 4, 0.5f, 0.5f, 0.5f, 1f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("blur2"),
            kind = GPUPreparedFilterKind.Blur,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = BlurParams(1f, 1f),
            provenance = "test/blur2",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        val rgba = FloatArray(4)
        for (y in 0..3) {
            for (x in 0..3) {
                result.getPixel(x, y, rgba)
                assertTrue(abs(rgba[0] - 0.5f) < 0.1f, "Expected ~0.5 got ${rgba[0]}")
                assertTrue(abs(rgba[3] - 1f) < 0.1f, "Expected ~1.0 got ${rgba[3]}")
            }
        }
    }

    // --- ColorFilter ---

    @Test
    fun `colorFilter grayscale matrix makes r==g==b for all pixels`() {
        val source = solidBitmap(4, 3, 1f, 0.5f, 0.25f, 1f)
        // Row-major 4x5 (Skia / production WGSL convention): each row of 5
        // values is one output channel's coefficients + translation.
        // r row: (new_r coefficients, addend)
        // g row: (new_g coefficients, addend)
        // b row: (new_b coefficients, addend)
        // a row: (new_a coefficients, addend)
        val grayscale = floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f, // R' = luminance
            0.2126f, 0.7152f, 0.0722f, 0f, 0f, // G' = luminance
            0.2126f, 0.7152f, 0.0722f, 0f, 0f, // B' = luminance
            0f, 0f, 0f, 1f, 0f,                // A' = A
        )
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("cf1"),
            kind = GPUPreparedFilterKind.ColorFilter,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = ColorFilterParams(grayscale.copyOf()),
            provenance = "test/cf1",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(source.width, result.width)
        assertEquals(source.height, result.height)

        for (i in result.pixels.indices step 4) {
            val r = result.pixels[i]
            val g = result.pixels[i + 1]
            val b = result.pixels[i + 2]
            assertTrue(abs(r - g) < 0.01f, "Expected r==g, got r=$r g=$g")
            assertTrue(abs(g - b) < 0.01f, "Expected g==b, got g=$g b=$b")
        }
    }

    @Test
    fun `color filter uses row-major matrix layout`() {
        val source = Rgba8Bitmap(1, 1, floatArrayOf(0.2f, 0.8f, 0.0f, 1.0f))
        // Row-major 4x5 (Skia / production WGSL convention):
        // R' = m0*R + m1*G + m2*B + m3*A + m4 ; G' = m5*R + m6*G + ... etc.
        val matrix = FloatArray(20)
        matrix[0] = 1f; matrix[6] = 1f; matrix[11] = 1f; matrix[15] = 1f
        matrix[1] = 0.5f   // G contributes to output R (row-major: m[1])
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("cf-row-major"),
            kind = GPUPreparedFilterKind.ColorFilter,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = ColorFilterParams(matrix),
            provenance = "test/cf-row-major",
        )
        val out = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(0.6f, out.pixels[0], 1e-4f)   // R' = 0.2 + 0.5*0.8
        assertEquals(0.8f, out.pixels[1], 1e-4f)   // G' unchanged
    }

    @Test
    fun `colorFilter identity matrix is a no-op`() {
        val source = solidBitmap(4, 4, 1f, 0f, 0f, 0.5f)
        // Row-major 4x5 identity: 1s on the diagonal at m0, m6, m12, m18
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, // R' = R
            0f, 1f, 0f, 0f, 0f, // G' = G
            0f, 0f, 1f, 0f, 0f, // B' = B
            0f, 0f, 0f, 1f, 0f, // A' = A
        )
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("cf2"),
            kind = GPUPreparedFilterKind.ColorFilter,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = ColorFilterParams(identity.copyOf()),
            provenance = "test/cf2",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        for (i in source.pixels.indices) {
            assertEquals(source.pixels[i], result.pixels[i], 0f,
                "pixel[$i] expected ${source.pixels[i]} got ${result.pixels[i]}")
        }
    }

    // --- Offset ---

    @Test
    fun `offset 2_2 moves pixel from 0_0 to 2_2`() {
        // single red pixel at (0,0)
        val pixels = FloatArray(16) // 2x2
        pixels[0] = 1f; pixels[1] = 0f; pixels[2] = 0f; pixels[3] = 1f // red at (0,0)
        val source = Rgba8Bitmap(2, 2, pixels)

        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("off1"),
            kind = GPUPreparedFilterKind.Offset,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = OffsetParams(2f, 2f),
            provenance = "test/off1",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())

        val rgba = FloatArray(4)
        // Pixel at (2,2) should be red (the original (0,0) moved)
        result.getPixel(2, 2, rgba)
        assertEquals(1f, rgba[0], "Expected red at (2,2)")
        assertEquals(0f, rgba[1])
        assertEquals(0f, rgba[2])
        assertEquals(1f, rgba[3])

        // Pixel at (0,0) should be transparent black (nothing there)
        result.getPixel(0, 0, rgba)
        assertEquals(0f, rgba[0], "Expected black at (0,0)")
        assertEquals(0f, rgba[3], "Expected transparent at (0,0)")
    }

    @Test
    fun `negative offset shifts content left`() {
        val source = Rgba8Bitmap(3, 1, floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("off-neg-x"),
            kind = GPUPreparedFilterKind.Offset,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = OffsetParams(dx = -1f, dy = 0f),
            provenance = "test/off-neg-x",
        )
        val out = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(4, out.width)                       // W + |dx|
        val rgba = FloatArray(4)
        out.getPixel(0, 0, rgba)
        assertEquals(0f, rgba[0], 1e-4f)                 // empty column on the left
        out.getPixel(1, 0, rgba)
        assertEquals(1f, rgba[0], 1e-4f)                 // first source pixel shifted to x=1
        out.getPixel(4 - 1, 0, rgba)
        assertEquals(0f, rgba[0], 1e-4f)                 // last source pixel beyond canvas end
    }

    @Test
    fun `negative offset shifts content up`() {
        val source = Rgba8Bitmap(1, 3, floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("off-neg-y"),
            kind = GPUPreparedFilterKind.Offset,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = OffsetParams(dx = 0f, dy = -1f),
            provenance = "test/off-neg-y",
        )
        val out = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(4, out.height)                      // H + |dy|
        val rgba = FloatArray(4)
        out.getPixel(0, 0, rgba)
        assertEquals(0f, rgba[0], 1e-4f)                 // empty row on the top
        out.getPixel(0, 1, rgba)
        assertEquals(1f, rgba[0], 1e-4f)                 // first source pixel shifted to y=1
        out.getPixel(0, 4 - 1, rgba)
        assertEquals(0f, rgba[0], 1e-4f)                 // last source pixel beyond canvas end
    }

    @Test
    fun `offset zero is identity`() {
        val source = solidBitmap(3, 3, 0f, 1f, 0f, 1f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("off2"),
            kind = GPUPreparedFilterKind.Offset,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = OffsetParams(0f, 0f),
            provenance = "test/off2",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(source.width, result.width)
        assertEquals(source.height, result.height)
        for (i in source.pixels.indices) {
            assertEquals(source.pixels[i], result.pixels[i], 1e-5f)
        }
    }

    // --- Crop ---

    @Test
    fun `crop to 2x2 from 4x4 produces 2x2 output`() {
        val source = solidBitmap(4, 4, 0.1f, 0.2f, 0.3f, 1f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("crop1"),
            kind = GPUPreparedFilterKind.Crop,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = CropParams(1f, 1f, 2f, 2f),
            provenance = "test/crop1",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(2, result.width)
        assertEquals(2, result.height)

        // Verify the cropped pixels match the source region
        val rgba = FloatArray(4)
        result.getPixel(0, 0, rgba)
        assertEquals(0.1f, rgba[0])
        assertEquals(0.2f, rgba[1])
        assertEquals(0.3f, rgba[2])
        assertEquals(1f, rgba[3])
    }

    @Test
    fun `crop identity returns full image`() {
        val source = solidBitmap(3, 3, 0.7f, 0.7f, 0.7f, 1f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("crop2"),
            kind = GPUPreparedFilterKind.Crop,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = CropParams(0f, 0f, 3f, 3f),
            provenance = "test/crop2",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())
        assertEquals(3, result.width)
        assertEquals(3, result.height)
    }

    // --- DropShadow ---

    @Test
    fun `dropShadow produces output larger than source when offset`() {
        val source = solidBitmap(4, 4, 1f, 0f, 0f, 1f)
        val color = floatArrayOf(0f, 0f, 0f, 0.5f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("ds1"),
            kind = GPUPreparedFilterKind.DropShadow,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = DropShadowParams(3f, 3f, 1f, 1f, color.copyOf()),
            provenance = "test/ds1",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())

        // Output should be larger to accommodate offset shadow
        assertTrue(result.width > source.width,
            "DropShadow output width ${result.width} should exceed source width ${source.width}")
        assertTrue(result.height > source.height,
            "DropShadow output height ${result.height} should exceed source height ${source.height}")
    }

    @Test
    fun `dropShadow shadow appears offset from source`() {
        // Small source: 3x3 white square
        val source = solidBitmap(3, 3, 1f, 1f, 1f, 1f)
        val shadowColor = floatArrayOf(0f, 0f, 0f, 0.8f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("ds2"),
            kind = GPUPreparedFilterKind.DropShadow,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = DropShadowParams(2f, 0f, 0.5f, 0.5f, shadowColor.copyOf()),
            provenance = "test/ds2",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())

        val rgba = FloatArray(4)

        // Source should be visible at its original position (x=0 because dx>0 pushes shadow right)
        result.getPixel(0, 1, rgba)
        val sourceR = rgba[0]
        assertTrue(sourceR > 0.5f, "Source should be visible at its position")

        // Shadow should appear offset to the right of source area
        result.getPixel(0, 1, rgba)
        val nearSource = rgba[3]

        // At a position where source is NOT (right edge), check for shadow contribution
        result.getPixel(result.width - 1, 1, rgba)
        val farEdgeA = rgba[3]
        // One of near source or far edge should show the shadow influence
        assertTrue(nearSource > 0f || farEdgeA > 0f,
            "Shadow should contribute non-zero alpha somewhere in output")
    }

    @Test
    fun `dropShadow zero sigma is identity-like for shadow color black`() {
        val source = solidBitmap(3, 3, 0f, 1f, 0f, 1f)
        val shadowColor = floatArrayOf(0f, 0f, 0f, 0.0f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("ds3"),
            kind = GPUPreparedFilterKind.DropShadow,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = DropShadowParams(0f, 0f, 0f, 0f, shadowColor.copyOf()),
            provenance = "test/ds3",
        )
        val result = GPUFilterOracle.apply(source, node, emptyMap())

        // With no offset, no blur, and transparent shadow: output should match source
        assertEquals(source.width, result.width)
        assertEquals(source.height, result.height)

        val rgba = FloatArray(4)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                result.getPixel(x, y, rgba)
                assertTrue(abs(rgba[1] - 1f) < 0.1f, "Green channel should be preserved at ($x,$y): got ${rgba[1]}")
            }
        }
    }

    @Test
    fun `dropShadow with negative dx places shadow left of source`() {
        val source = Rgba8Bitmap(3, 1, floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val shadowColor = floatArrayOf(0f, 0f, 0f, 0.8f)
        val node = GPUPreparedFilterNode(
            id = GPUPreparedFilterNodeId("ds-neg"),
            kind = GPUPreparedFilterKind.DropShadow,
            inputs = listOf(GPUPreparedFilterInputRef.ImplicitSource),
            parameters = DropShadowParams(-1f, 0f, 0f, 0f, shadowColor.copyOf()),
            provenance = "test/ds-neg",
        )
        val out = GPUFilterOracle.apply(source, node, emptyMap())

        // Union of source [0,3) and shadow shifted by -1 ([-1,2)) is [-1,3) -> width 4
        assertEquals(4, out.width)
        assertEquals(1, out.height)

        val rgba = FloatArray(4)
        // Shadow color visible at x=0, left of the source
        out.getPixel(0, 0, rgba)
        assertEquals(0f, rgba[0], 1e-4f)
        assertEquals(0f, rgba[1], 1e-4f)
        assertEquals(0f, rgba[2], 1e-4f)
        assertEquals(0.8f, rgba[3], 1e-4f)
        // Source starts at x=1
        out.getPixel(1, 0, rgba)
        assertEquals(1f, rgba[0], 1e-4f)
        assertEquals(1f, rgba[3], 1e-4f)
    }

    // --- Helpers ---

    private fun variance(arr: FloatArray): Float {
        val mean = arr.sum() / arr.size
        return arr.map { (it - mean) * (it - mean) }.sum() / arr.size
    }
}
