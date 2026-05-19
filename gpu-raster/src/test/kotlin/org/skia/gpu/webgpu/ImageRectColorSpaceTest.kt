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
 * G5.3 / G5.3.x -- texture color management acceptance tests.
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
 *  - Rec.2020-linear-TF-tagged source (G5.3.x ; csMode = 2 with
 *    `(g, a, b, c, d, e, f) = (1, 1, 0, 0, 0, 0, 0)`) : tests the
 *    parametric-TF path with a no-op linearise stage. Only the
 *    primaries matrix does real work.
 *  - Adobe RGB / k2Dot2-tagged source (G5.3.x ; csMode = 2 with
 *    `(2.2, 1, 0, 0, 0, 0, 0)`) : tests the parametric-TF path with
 *    a non-trivial source TF (pure 2.2 power law).
 *
 * Out of scope (deferred ; documented in MIGRATION_PLAN_GPU_WEBGPU.md) :
 *  - Rec.2020 PQ TF, HDR / PQ / HLG luminance scaling (need OOTF
 *    handling).
 *  - Color management on the paint side (paint.colorFilter, paint.color).
 *
 * The host computes the (mode, matrix, tfParams) triple via
 * `SkColorSpaceXformSteps(image.colorSpace, kUnpremul, sRGB, kUnpremul)`
 * and passes them through the bitmap-shader uniform. The shader uses
 * the matrix as the gamut transform between source-linear and
 * sRGB-linear endpoints, and (for csMode = 2) the parametric TF coefs
 * to linearise the source-encoded sample.
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
    fun `Rec_2020 linear-TF tagged image runs the parametric-TF path`() {
        // G5.3.x : Rec.2020 with the kLinear transfer function exercises
        // csMode = 2 with `(g, a, b, c, d, e, f) = (1, 1, 0, 0, 0, 0, 0)`
        // (the linear identity TF). The linearise stage is a no-op
        // (`x^1 = x`) ; the primaries matrix Rec.2020 -> sRGB does all
        // the work. The output is then re-encoded through the sRGB OETF
        // before the premul step.
        //
        // We pick a mid-tone source `(64, 128, 32, 255)` whose Rec.2020-
        // linear value stays inside the sRGB linear gamut after the
        // matrix transform (some negative-coefficient cells in the
        // Rec.2020 -> sRGB matrix would otherwise push extreme reds /
        // greens out of gamut and clamp). The same CPU
        // `SkColorSpaceXformSteps.apply` pipeline defines the
        // ground-truth bytes.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val texelColor = SkColorSetARGB(0xFF, 64, 128, 32)
        val rec2020Linear = rec2020Linear()
        val image = solidImage(texelColor, colorSpace = rec2020Linear)

        val expected = cpuTransform(texelColor, rec2020Linear, SkColorSpace.makeSRGB())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        val actual = rendered.rgbaAt(px, py)
        assertCloseEnough(
            expected, actual,
            tolerance = 3,
            label = "Rec.2020-linear texel -> sRGB-coded output",
        )
    }

    @Test
    fun `Rec_2020 linear-TF pure white round-trips through the gamut transform`() {
        // Gamut invariant : Rec.2020 white (D65) maps to sRGB white
        // (D65) since both colorspaces share the D65 white point. Catches
        // matrix-orientation bugs (transposed / inverted matrix would
        // shift white). Same shape as the P3-white test but exercises
        // the csMode = 2 parametric-TF branch instead of csMode = 1.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = solidImage(SK_ColorWHITE, colorSpace = rec2020Linear())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        assertCloseEnough(
            listOf(255, 255, 255, 255), rendered.rgbaAt(px, py),
            tolerance = 2,
            label = "Rec.2020-linear white must stay white through gamut transform",
        )
    }

    @Test
    fun `Adobe RGB tagged image applies the gamma-2_2 TF + AdobeRGB-to-sRGB matrix`() {
        // G5.3.x : Adobe RGB carries a 2.2-power TF (k2Dot2) and a
        // wider-than-sRGB gamut. The shader's csMode = 2 branch runs
        // `x^2.2` on each channel (linearise), then the AdobeRGB-to-sRGB
        // primaries matrix, then the sRGB OETF on the way out.
        //
        // The mid-tone `(64, 128, 32, 255)` source is far enough from
        // the saturated reds / greens that the AdobeRGB-linear -> sRGB-
        // linear cells (which include negative off-diagonal entries)
        // do not push any channel into the clipped band before the OETF.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val texelColor = SkColorSetARGB(0xFF, 64, 128, 32)
        val adobeRgb = adobeRgb()
        val image = solidImage(texelColor, colorSpace = adobeRgb)

        val expected = cpuTransform(texelColor, adobeRgb, SkColorSpace.makeSRGB())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        val actual = rendered.rgbaAt(px, py)
        assertCloseEnough(
            expected, actual,
            tolerance = 3,
            label = "Adobe RGB texel -> sRGB-coded output",
        )
    }

    @Test
    fun `Adobe RGB pure white round-trips through the gamut transform`() {
        // Gamut invariant : Adobe RGB white (D65) -> sRGB white (D65)
        // regardless of the TF. Catches matrix-orientation bugs even
        // when the TF stage is non-trivial.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val image = solidImage(SK_ColorWHITE, colorSpace = adobeRgb())
        val rendered = render(context!!, image)

        val (px, py) = SAMPLE_POINT
        assertCloseEnough(
            listOf(255, 255, 255, 255), rendered.rgbaAt(px, py),
            tolerance = 2,
            label = "Adobe RGB white must stay white through gamut transform",
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
     * G5.3.x -- Rec.2020 gamut with the linear (`g=1`) transfer
     * function. Exercises the parametric-TF path (csMode = 2) with a
     * no-op linearise stage : only the primaries matrix does real
     * work, but the shader still routes through the `parametric_tf`
     * eval (which must return `x` for the identity TF).
     */
    private fun rec2020Linear(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!

    /**
     * G5.3.x -- Adobe RGB / k2Dot2 colorspace. The k2Dot2 TF is `x^2.2`
     * (pure power law, no linear branch). Exercises the parametric-TF
     * path (csMode = 2) with a non-trivial linearise stage *and* a
     * non-sRGB primaries matrix.
     */
    private fun adobeRgb(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.k2Dot2, SkNamedGamut.kAdobeRGB)!!

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
