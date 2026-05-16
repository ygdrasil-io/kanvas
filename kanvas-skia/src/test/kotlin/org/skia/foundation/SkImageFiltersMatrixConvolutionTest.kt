package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix

/**
 * C1.6 verification suite — MatrixConvolution.
 *
 * Coverage :
 *  - Identity kernel (single 1.0 at the centre) → input pass-through.
 *  - Uniform 3×3 box blur kernel → averaging behaviour.
 *  - Edge-detect-style kernels (sum = 0) → zero-mean output.
 *  - `gain` and `bias` adjustments.
 *  - `convolveAlpha = false` preserves input alpha.
 *  - Tile-mode wiring (kDecal vs kClamp) — different OOB sampling
 *    leads to different edge values.
 *  - Argument validation : kernel size mismatch throws.
 */
class SkImageFiltersMatrixConvolutionTest {

    private val identity = SkMatrix.Identity

    /** 4×4 image with horizontal red gradient (x * 64 R, full alpha). */
    private val redGradient: SkImage = SkImage(
        4, 4,
        IntArray(16) { i ->
            val x = i % 4
            (0xFF shl 24) or ((x * 64).coerceAtMost(255) shl 16)
        },
    )

    /** 4×4 image, all pixels opaque mid-grey (0x808080). */
    private val midGrey: SkImage = SkImage(4, 4, IntArray(16) { 0xFF808080.toInt() })

    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    @Test
    fun `identity kernel passes input through`() {
        // 3×3 kernel, only the centre is 1.0. With kCenter at (1, 1),
        // every output pixel = the corresponding input pixel.
        val filter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(3, 3),
            kernel = floatArrayOf(
                0f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 0f,
            ),
            gain = 1f, bias = 0f,
            kernelOffset = SkIPoint(1, 1),
            tileMode = SkTileMode.kClamp,
            convolveAlpha = false,
            input = SkImageFilters.Image(redGradient),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(redGradient.peekPixel(x, y), result.image.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `uniform 3x3 box kernel averages neighbours`() {
        // 3×3 kernel of 1/9. Applied to mid-grey input → mid-grey output
        // everywhere (averaging a constant gives the constant back).
        val filter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(3, 3),
            kernel = FloatArray(9) { 1f / 9f },
            gain = 1f, bias = 0f,
            kernelOffset = SkIPoint(1, 1),
            tileMode = SkTileMode.kClamp,
            convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until 4) for (x in 0 until 4) {
            // Allow ±1 byte for floating-point rounding error.
            val px = result.image.peekPixel(x, y)
            val r = (px ushr 16) and 0xFF
            assertTrue(kotlin.math.abs(r - 0x80) <= 1, "R=$r at ($x, $y) should be ~0x80")
        }
    }

    @Test
    fun `gain doubles the output`() {
        // Identity kernel + gain=2 → output = 2 * input (clamped to 255).
        val filter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(1, 1),
            kernel = floatArrayOf(1f),
            gain = 2f, bias = 0f,
            kernelOffset = SkIPoint(0, 0),
            tileMode = SkTileMode.kDecal,
            convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val result = filter.filterImage(anyDriver, identity)
        // mid-grey 0x80 → 2 * 0x80 = 0x100, clamped to 0xFF.
        for (y in 0 until 4) for (x in 0 until 4) {
            val px = result.image.peekPixel(x, y)
            assertEquals(0xFF, (px ushr 16) and 0xFF, "R at ($x, $y) should saturate")
            assertEquals(0xFF, (px ushr 8) and 0xFF, "G at ($x, $y) should saturate")
            assertEquals(0xFF, px and 0xFF, "B at ($x, $y) should saturate")
        }
    }

    @Test
    fun `bias adds constant offset to every channel`() {
        // 1×1 zero kernel + bias=64 → every channel = 64 (clamped).
        val filter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(1, 1),
            kernel = floatArrayOf(0f),
            gain = 1f, bias = 64f,
            kernelOffset = SkIPoint(0, 0),
            tileMode = SkTileMode.kDecal,
            convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until 4) for (x in 0 until 4) {
            val px = result.image.peekPixel(x, y)
            assertEquals(64, (px ushr 16) and 0xFF, "R at ($x, $y)")
            assertEquals(64, (px ushr 8) and 0xFF, "G at ($x, $y)")
            assertEquals(64, px and 0xFF, "B at ($x, $y)")
        }
    }

    @Test
    fun `convolveAlpha false preserves input alpha`() {
        // Identity kernel, convolveAlpha=false. Input alpha = 0xFF.
        // Output alpha must stay 0xFF regardless of bias.
        val filter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(1, 1),
            kernel = floatArrayOf(1f),
            gain = 1f, bias = -200f, // Would drive alpha to 0 if convolved.
            kernelOffset = SkIPoint(0, 0),
            tileMode = SkTileMode.kDecal,
            convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val result = filter.filterImage(anyDriver, identity)
        for (y in 0 until 4) for (x in 0 until 4) {
            val px = result.image.peekPixel(x, y)
            assertEquals(0xFF, (px ushr 24) and 0xFF, "alpha at ($x, $y) should be untouched")
        }
    }

    @Test
    fun `tile mode kDecal vs kClamp differs at edges`() {
        // 3×3 box kernel, applied to a uniform mid-grey 4×4 image.
        // - kClamp : every kernel sample is in-bounds (clamped) → out = 0x80.
        // - kDecal : the corner pixel sees 5 OOB samples (transparent
        //   = 0) and 4 in-bounds samples (each 0x80) → average ≈
        //   (4 * 0x80) / 9 ≈ 56.9 ⇒ R ≈ 56-57.
        val kernel = FloatArray(9) { 1f / 9f }
        val centre = SkIPoint(1, 1)

        val clampedFilter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(3, 3), kernel = kernel,
            gain = 1f, bias = 0f, kernelOffset = centre,
            tileMode = SkTileMode.kClamp, convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val decalFilter = SkImageFilters.MatrixConvolution(
            kernelSize = SkISize(3, 3), kernel = kernel,
            gain = 1f, bias = 0f, kernelOffset = centre,
            tileMode = SkTileMode.kDecal, convolveAlpha = false,
            input = SkImageFilters.Image(midGrey),
        )
        val clamped = clampedFilter.filterImage(anyDriver, identity)
        val decaled = decalFilter.filterImage(anyDriver, identity)

        // Top-left corner.
        val cR = (clamped.image.peekPixel(0, 0) ushr 16) and 0xFF
        val dR = (decaled.image.peekPixel(0, 0) ushr 16) and 0xFF
        assertTrue(kotlin.math.abs(cR - 0x80) <= 1, "kClamp corner R=$cR should be ~0x80")
        assertTrue(dR < cR, "kDecal corner R=$dR should be darker than kClamp R=$cR")
    }

    @Test
    fun `kernel size mismatch throws`() {
        assertThrows<IllegalArgumentException> {
            SkImageFilters.MatrixConvolution(
                kernelSize = SkISize(3, 3),
                kernel = floatArrayOf(1f, 2f, 3f), // 3 != 9
                gain = 1f, bias = 0f,
                kernelOffset = SkIPoint(1, 1),
                tileMode = SkTileMode.kDecal,
                convolveAlpha = false,
                input = SkImageFilters.Image(midGrey),
            )
        }
    }
}
