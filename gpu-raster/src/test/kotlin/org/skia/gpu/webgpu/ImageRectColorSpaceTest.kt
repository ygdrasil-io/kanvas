package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkCanvas
import org.skia.core.SkColorSpaceXformSteps
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.math.abs

/**
 * G5.3 -- texture color management acceptance tests.
 *
 * Scope :
 *  - sRGB-tagged source : identity / no-op fast path (csMode = 0).
 *    Validated indirectly by [ImageRectTest] (all 7 tests are sRGB) ;
 *    here we add a regression guard that the same image rendered with
 *    an explicit sRGB tag matches the implicit-default rendering.
 *  - Display P3-tagged source : non-trivial primaries matrix path
 *    (csMode = 1, sRGB transfer function preserved). The shader runs
 *    sRGB EOTF -> P3-to-sRGB matrix -> sRGB OETF on the unpremul
 *    sample before the premul + paintColor steps.
 *
 * Out of scope (deferred ; documented in MIGRATION_PLAN_GPU_WEBGPU.md) :
 *  - Rec.2020 (linear or PQ TF), Adobe RGB, ProPhoto -- need their own
 *    TF coefs in the uniform.
 *  - HDR / PQ / HLG luminance scaling.
 *  - Color management on the paint side (paint.colorFilter, paint.color).
 *
 * The host computes the (mode, matrix) tuple via
 * `SkColorSpaceXformSteps(image.colorSpace, kUnpremul, sRGB, kUnpremul)`
 * and passes them through the bitmap-shader uniform. The shader uses
 * the matrix as the gamut transform between sRGB-linear endpoints.
 *
 * Expected reference values are computed by running the same
 * `SkColorSpaceXformSteps.apply` pipeline on the host : if the GPU
 * shader implements the same chain it must agree to within a small
 * 8-bit quantisation tolerance (`pow` precision + `RGBA8Unorm`
 * intermediate -> 8-bit readback round-trip).
 */
class ImageRectColorSpaceTest {

    @Test
    fun `sRGB-tagged image renders identically to the default-tagged baseline`() {
        // Regression guard for the fast path : explicitly tagging an
        // sRGB image must take the csMode = 0 branch and produce the
        // same readback as the default (untagged) sRGB image. We use
        // two contexts because [WebGpuContext.use] closes the underlying
        // GLFW + wgpu device on exit -- a second render against the
        // same closed device aborts the JVM.
        val ctx1 = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(ctx1 != null, "No WebGPU adapter")
        val ctx2 = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(ctx2 != null, "No WebGPU adapter (second context)")

        val color = SkColorSetARGB(0xFF, 200, 100, 50)
        val defaultTag = solidImage(color, colorSpace = null)
        val explicitSrgb = solidImage(color, colorSpace = SkColorSpace.makeSRGB())

        val defaultPixels = render(ctx1!!, defaultTag)
        val explicitPixels = render(ctx2!!, explicitSrgb)

        // The two renders must agree byte-for-byte in the rendered rect.
        val (px, py) = SAMPLE_POINT
        assertEquals(
            defaultPixels.rgbaAt(px, py),
            explicitPixels.rgbaAt(px, py),
            "sRGB-tagged image must take the identity fast path",
        )
    }

    @Test
    fun `Display P3-tagged image applies the P3-to-sRGB primaries matrix`() {
        // End-to-end : a P3-tagged image whose unpremul texel bytes are
        // P3-encoded (255, 0, 0) must, after GPU rendering into the
        // sRGB-coded intermediate target, match the same texel passed
        // through `SkColorSpaceXformSteps(P3, kUnpremul, sRGB, kUnpremul)`
        // on the CPU side. A texel whose linear-P3 RGB lies outside the
        // sRGB gamut (e.g. pure P3 red) saturates after sRGB OETF -- so
        // we pick mid-range non-saturated colors where the transform is
        // visible without clipping.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // P3-encoded mid-tones : the linear-P3 values stay well inside
        // the sRGB gamut after the primaries matrix, so the readback is
        // not biased by the shader's `max(v, 0.0)` clamp before the OETF.
        val texelColor = SkColorSetARGB(0xFF, 64, 128, 32)
        val p3 = displayP3()
        val image = solidImage(texelColor, colorSpace = p3)

        val expected = cpuTransform(texelColor, p3, SkColorSpace.makeSRGB())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        val actual = rendered.rgbaAt(px, py)
        assertCloseEnough(
            expected, actual,
            tolerance = 3,
            label = "P3-tagged texel -> sRGB-coded output",
        )
    }

    @Test
    fun `Display P3 pure white round-trips through the gamut transform`() {
        // White is the gamut invariant : P3 (255, 255, 255) must render
        // as sRGB (255, 255, 255) -- the matrix maps the D65 white point
        // to itself, alpha = 1. Catches obvious matrix-orientation bugs
        // (transposed / inverted matrix would shift white).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = solidImage(SK_ColorWHITE, colorSpace = displayP3())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        assertCloseEnough(
            listOf(255, 255, 255, 255), rendered.rgbaAt(px, py),
            tolerance = 2,
            label = "P3 white must stay white through gamut transform",
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private fun displayP3(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)!!

    /**
     * Build a small solid-color image, optionally tagged with a
     * non-default colorspace. The stored 8888 texel bytes are literally
     * [color] -- they are *not* re-encoded through any xform pipeline.
     * This matches the convention the GPU upload + shader rely on
     * (texture stores source-encoded unpremul bytes ; shader applies
     * the colorspace transform per fragment).
     */
    private fun solidImage(color: Int, colorSpace: SkColorSpace?): SkImage {
        val cs = colorSpace ?: SkColorSpace.makeSRGB()
        val bitmap = SkBitmap(SIDE, SIDE, cs, SkColorType.kRGBA_8888)
        bitmap.pixels.fill(color)
        return SkImage.Make(bitmap)
    }

    /**
     * Render [image] over a white background and return the device
     * pixel buffer. The dst rect is 1:1 with the source so a fragment
     * over [SAMPLE_POINT] reads exactly one source texel.
     */
    private fun render(context: WebGpuContext, image: SkImage): ByteArray =
        context.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.drawImageRect(
                    image,
                    SkRect.MakeWH(SIDE.toFloat(), SIDE.toFloat()),
                    SkRect.MakeXYWH(10f, 10f, SIDE.toFloat(), SIDE.toFloat()),
                    SkSamplingOptions.linear(),
                )
                device.flush()
            }
        }

    /**
     * Apply [SkColorSpaceXformSteps] on the host to compute the
     * expected on-screen byte values for an unpremul sample of [texel]
     * tagged with [src], rendered into the [dst]-coded intermediate
     * target. The shader's pipeline mirrors this exactly (sample ->
     * linearize -> matrix -> encode -> premul-by-alpha), so the
     * outputs must agree within `pow` precision.
     */
    private fun cpuTransform(texel: Int, src: SkColorSpace, dst: SkColorSpace): List<Int> {
        val r = SkColorGetR(texel) / 255f
        val g = SkColorGetG(texel) / 255f
        val b = SkColorGetB(texel) / 255f
        val a = SkColorGetA(texel) / 255f
        val rgba = floatArrayOf(r, g, b, a)
        val steps = SkColorSpaceXformSteps(src, SkAlphaType.kUnpremul, dst, SkAlphaType.kUnpremul)
        steps.apply(rgba)
        // The shader premultiplies after the colorspace transform.
        val pr = (rgba[0].coerceIn(0f, 1f) * rgba[3]).coerceIn(0f, 1f)
        val pg = (rgba[1].coerceIn(0f, 1f) * rgba[3]).coerceIn(0f, 1f)
        val pb = (rgba[2].coerceIn(0f, 1f) * rgba[3]).coerceIn(0f, 1f)
        val pa = rgba[3].coerceIn(0f, 1f)
        return listOf(
            (pr * 255f + 0.5f).toInt().coerceIn(0, 255),
            (pg * 255f + 0.5f).toInt().coerceIn(0, 255),
            (pb * 255f + 0.5f).toInt().coerceIn(0, 255),
            (pa * 255f + 0.5f).toInt().coerceIn(0, 255),
        )
    }

    private fun assertCloseEnough(expected: List<Int>, actual: List<Int>, tolerance: Int, label: String) {
        val ok = expected.zip(actual).all { (e, a) -> abs(e - a) <= tolerance }
        assertTrue(ok, "$label : expected ~$expected got $actual (tol=$tolerance)")
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
        const val SIDE: Int = 4
        // A pixel safely inside the dst rect (10, 10, SIDE, SIDE) =
        // [10, 14] x [10, 14]. (12, 12) is the centre.
        val SAMPLE_POINT: Pair<Int, Int> = 12 to 12
    }
}
