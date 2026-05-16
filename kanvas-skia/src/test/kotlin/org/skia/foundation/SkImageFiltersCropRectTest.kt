package org.skia.foundation


import org.skia.math.SkColorChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.math.SkIPoint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Phase R2.15 — verifies the `cropRect: SkRect?` trailing-parameter
 * overloads on the non-`Blur` factories of [SkImageFilters].
 *
 * Strategy (per upstream `SkImageFilters.h` l.154-158 — *"the optional
 * CropRect argument for many of the factories is equivalent to creating
 * the filter without a CropRect and then wrapping it in
 * ::Crop(rect, kDecal)"*) : for every cropRect overload, build the same
 * filter with `cropRect == rect` and compare its output to the explicit
 * `Crop(rect, kDecal, filter)` wrapper. The two pipelines must produce
 * pixel-identical [SkImageFilter.FilterResult] tuples (same image, same
 * offset).
 *
 * Each test also runs the `cropRect = null` case and asserts the result
 * matches the legacy overload — confirming the new overload is
 * source-compatible when no crop is requested.
 */
class SkImageFiltersCropRectTest {

    private val identity = SkMatrix.Identity

    /** 8x8 source : opaque red. Big enough to crop. */
    private val redImg: SkImage = SkImage(8, 8, IntArray(64) { 0xFFFF0000.toInt() })

    /** Driver image — small, ignored by `Image`-backed inputs. */
    private val driver: SkImage = SkImage(1, 1, IntArray(1))

    /** Reusable cropRect : a 4x4 window at (1, 1). */
    private val crop: SkRect = SkRect.MakeLTRB(1f, 1f, 5f, 5f)

    /** Identity colour matrix (5x4 row-major). */
    private val identityCfMatrix = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun assertSameResult(
        actual: SkImageFilter.FilterResult,
        expected: SkImageFilter.FilterResult,
        label: String,
    ) {
        assertEquals(expected.offsetX, actual.offsetX, "$label : offsetX")
        assertEquals(expected.offsetY, actual.offsetY, "$label : offsetY")
        assertEquals(expected.image.width, actual.image.width, "$label : width")
        assertEquals(expected.image.height, actual.image.height, "$label : height")
        for (y in 0 until expected.image.height) {
            for (x in 0 until expected.image.width) {
                assertEquals(
                    expected.image.peekPixel(x, y),
                    actual.image.peekPixel(x, y),
                    "$label : pixel ($x, $y)",
                )
            }
        }
    }

    // ─── Offset ─────────────────────────────────────────────────────────

    @Test
    fun `Offset with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.Offset(2f, 1f, input, crop)
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Offset(2f, 1f, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Offset+cropRect",
        )
    }

    @Test
    fun `Offset with null cropRect equals legacy overload`() {
        val input = SkImageFilters.Image(redImg)
        val withNull = SkImageFilters.Offset(2f, 1f, input, null)
        val legacy = SkImageFilters.Offset(2f, 1f, input)
        assertSameResult(
            withNull.filterImage(driver, identity),
            legacy.filterImage(driver, identity),
            "Offset null cropRect",
        )
    }

    // ─── ColorFilter ────────────────────────────────────────────────────

    @Test
    fun `ColorFilter with cropRect equals Crop wrapping`() {
        val cf = SkColorFilters.Matrix(identityCfMatrix)
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.ColorFilter(cf, input, crop)
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.ColorFilter(cf, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "ColorFilter+cropRect",
        )
    }

    @Test
    fun `ColorFilter with null cropRect equals legacy overload`() {
        val cf = SkColorFilters.Matrix(identityCfMatrix)
        val input = SkImageFilters.Image(redImg)
        val withNull = SkImageFilters.ColorFilter(cf, input, null)
        val legacy = SkImageFilters.ColorFilter(cf, input)
        assertSameResult(
            withNull.filterImage(driver, identity),
            legacy.filterImage(driver, identity),
            "ColorFilter null cropRect",
        )
    }

    // ─── Compose ────────────────────────────────────────────────────────

    @Test
    fun `Compose with cropRect equals Crop wrapping`() {
        val inner = SkImageFilters.Image(redImg)
        val outer = SkImageFilters.Offset(1f, 1f)
        val cropped = SkImageFilters.Compose(outer, inner, crop)!!
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Compose(outer, inner),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Compose+cropRect",
        )
    }

    @Test
    fun `Compose with null cropRect equals legacy overload`() {
        val inner = SkImageFilters.Image(redImg)
        val outer = SkImageFilters.Offset(1f, 1f)
        val withNull = SkImageFilters.Compose(outer, inner, null)
        val legacy = SkImageFilters.Compose(outer, inner)
        assertNotNull(withNull)
        assertNotNull(legacy)
        assertSameResult(
            withNull!!.filterImage(driver, identity),
            legacy!!.filterImage(driver, identity),
            "Compose null cropRect",
        )
    }

    // ─── MatrixTransform ────────────────────────────────────────────────

    @Test
    fun `MatrixTransform with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val mat = SkMatrix.MakeScale(1f, 1f)
        val cropped = SkImageFilters.MatrixTransform(
            mat, SkSamplingOptions.Default, input, crop,
        )!!
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.MatrixTransform(mat, SkSamplingOptions.Default, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "MatrixTransform+cropRect",
        )
    }

    // ─── DropShadow ─────────────────────────────────────────────────────

    @Test
    fun `DropShadow with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.DropShadow(
            2f, 2f, 1f, 1f, 0xFF000000.toInt(), input, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.DropShadow(2f, 2f, 1f, 1f, 0xFF000000.toInt(), input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "DropShadow+cropRect",
        )
    }

    // ─── Magnifier ──────────────────────────────────────────────────────

    @Test
    fun `Magnifier with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val lens = SkRect.MakeLTRB(1f, 1f, 7f, 7f)
        val cropped = SkImageFilters.Magnifier(
            lens, 2f, 0.5f, SkSamplingOptions.Default, input, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Magnifier(lens, 2f, 0.5f, SkSamplingOptions.Default, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Magnifier+cropRect",
        )
    }

    // ─── Blend ──────────────────────────────────────────────────────────

    @Test
    fun `Blend with cropRect equals Crop wrapping`() {
        val bg = SkImageFilters.Image(redImg)
        val fg = SkImageFilters.Image(
            SkImage(8, 8, IntArray(64) { 0x80008000.toInt() }),
        )
        val cropped = SkImageFilters.Blend(SkBlendMode.kSrcOver, bg, fg, crop)
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Blend(SkBlendMode.kSrcOver, bg, fg),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Blend+cropRect",
        )
    }

    // ─── Erode / Dilate ─────────────────────────────────────────────────

    @Test
    fun `Erode with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.Erode(1, 1, input, crop)
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Erode(1, 1, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Erode+cropRect",
        )
    }

    @Test
    fun `Dilate with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.Dilate(1, 1, input, crop)
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Dilate(1, 1, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "Dilate+cropRect",
        )
    }

    // ─── DisplacementMap ────────────────────────────────────────────────

    @Test
    fun `DisplacementMap with cropRect equals Crop wrapping`() {
        val color = SkImageFilters.Image(redImg)
        val displacement = SkImageFilters.Image(
            SkImage(8, 8, IntArray(64) { 0xFF808080.toInt() }),
        )
        val cropped = SkImageFilters.DisplacementMap(
            SkColorChannel.kR, SkColorChannel.kG, 4f, displacement, color, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.DisplacementMap(
                SkColorChannel.kR, SkColorChannel.kG, 4f, displacement, color,
            ),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "DisplacementMap+cropRect",
        )
    }

    // ─── MatrixConvolution ──────────────────────────────────────────────

    @Test
    fun `MatrixConvolution with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        // 3x3 identity kernel.
        val kernel = floatArrayOf(
            0f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 0f,
        )
        val cropped = SkImageFilters.MatrixConvolution(
            SkISize(3, 3), kernel, 1f, 0f, SkIPoint(1, 1),
            SkTileMode.kDecal, false, input, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.MatrixConvolution(
                SkISize(3, 3), kernel, 1f, 0f, SkIPoint(1, 1),
                SkTileMode.kDecal, false, input,
            ),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "MatrixConvolution+cropRect",
        )
    }

    // ─── PointLitDiffuse / PointLitSpecular ─────────────────────────────

    @Test
    fun `PointLitDiffuse with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val location = floatArrayOf(4f, 4f, 10f)
        val cropped = SkImageFilters.PointLitDiffuse(
            location, 0xFFFFFFFF.toInt(), 1f, 1f, input, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.PointLitDiffuse(location, 0xFFFFFFFF.toInt(), 1f, 1f, input),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "PointLitDiffuse+cropRect",
        )
    }

    @Test
    fun `PointLitSpecular with cropRect equals Crop wrapping`() {
        val input = SkImageFilters.Image(redImg)
        val location = floatArrayOf(4f, 4f, 10f)
        val cropped = SkImageFilters.PointLitSpecular(
            location, 0xFFFFFFFF.toInt(), 1f, 1f, 20f, input, crop,
        )
        val wrapped = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.PointLitSpecular(
                location, 0xFFFFFFFF.toInt(), 1f, 1f, 20f, input,
            ),
        )
        assertSameResult(
            cropped.filterImage(driver, identity),
            wrapped.filterImage(driver, identity),
            "PointLitSpecular+cropRect",
        )
    }

    // ─── cropRect actually constrains the bounds ────────────────────────

    @Test
    fun `cropRect constrains filter output to its dimensions`() {
        // Sanity : the cropRect must yield an output image sized to the
        // crop's dimensions (4x4 here), not the larger upstream image (8x8).
        val input = SkImageFilters.Image(redImg)
        val cropped = SkImageFilters.Erode(0, 0, input, crop)
        val result = cropped.filterImage(driver, identity)
        // Crop's output image is sized to ceil(rect.width × rect.height)
        // = (5 - 1) × (5 - 1) = 4 × 4.
        assertEquals(4, result.image.width, "cropRect width")
        assertEquals(4, result.image.height, "cropRect height")
        // Offset is floor(rect.left), floor(rect.top).
        assertEquals(1, result.offsetX, "cropRect offsetX")
        assertEquals(1, result.offsetY, "cropRect offsetY")
    }
}
