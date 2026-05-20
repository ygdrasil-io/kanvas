package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkRect
import kotlin.math.abs

/**
 * Phase MaskFilter-blur acceptance tests -- `paint.maskFilter =
 * SkBlurMaskFilter(style, sigma)` on the GPU backend, for all four
 * [SkBlurStyle] variants : kNormal / kSolid / kOuter / kInner.
 *
 * The GPU path :
 *  1. Allocates a child layer device sized to the path bounds + 3*sigma
 *     padding.
 *  2. Renders the path white-tinted onto the child's intermediate.
 *  3. Drains the child via [SkWebGpuDevice.flushDrawsOnly].
 *  4. Enqueues a [SkWebGpuDevice.BlurredPathDraw] that the parent's
 *     flush replays as two render passes (H Gaussian blur on a scratch
 *     texture, then V Gaussian blur + style-driven composition + paint-
 *     colour modulation onto the parent's intermediate).
 *
 * The V pass binds both the H-pass scratch (the convolution source)
 * and the original shape mask (the sharp M(p)) -- the four styles
 * differ only in how M and B are combined per the formulas :
 *  - kNormal : B
 *  - kSolid  : min(M + B, 1)
 *  - kOuter  : max(B - M, 0)
 *  - kInner  : B * M
 *
 * Coverage in this suite :
 *  - `drawRect` + kNormal : soft edges, opaque centre, monotone decay.
 *  - `drawPath(circle)` + kNormal : edge-softness on a curved boundary.
 *  - paint.alpha modulation : halving alpha halves output alpha.
 *  - background bleed-through : far outside the path, untouched.
 *  - kSolid : interior stays fully solid, halo extends outward.
 *  - kOuter : interior transparent (B - M = 0), only outer halo.
 *  - kInner : blur clipped to interior, exterior fully transparent.
 *  - paint without maskFilter : sharp fill, dispatch gate doesn't fire.
 */
class MaskFilterBlurTest {

    @Test
    fun `drawRect with kNormal blur produces soft edges and opaque interior`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Rect [20, 20, 44, 44] (axis-aligned, 24x24) blurred with
        // sigma = 4 ; the blur radius is 12 pixels.
        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : still opaque black (the blurred mass at
        // the centre approaches the kernel sum on a saturated 24x24
        // interior, which is >>1 px from any edge for sigma = 4).
        val centre = pixels.rgbaAt(32, 32)
        // The output is premul SrcOver-on-white : (R, G, B, 255) where
        // R = G = B come from `1*(1-cov) + 0*cov = 1 - cov`. For a
        // fully-covered pixel cov = 1 -> bytes (0, 0, 0, 255).
        assertTrue(centre[0] < 20, "rect centre R should be near 0, got ${centre[0]}")
        assertTrue(centre[1] < 20, "rect centre G should be near 0, got ${centre[1]}")
        assertTrue(centre[2] < 20, "rect centre B should be near 0, got ${centre[2]}")

        // Far outside the blur extent : background white, untouched.
        // Sample (5, 5) -- 15 px outside the rect's left edge, well
        // past the 12-pixel blur radius.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5),
            "outside the blur extent : background")

        // Sample a row crossing the right edge at y = 32 (centre row).
        // The blur radius is 12 ; rect's right edge is at x = 44. So
        // x = 32 (12 px inside the edge) should be (nearly) opaque,
        // x = 44 (the edge) should be midway (greyish), x = 56 should
        // be (nearly) the background. The R/G/B luminance should
        // decrease monotonically along x as the coverage falls off.
        val luminanceProfile = IntArray(31) { dx ->
            val x = 30 + dx  // x = 30..60 (covers 14 px inside -> 16 px outside)
            pixels.rgbaAt(x, 32)[0]
        }
        // First sample (x = 30 ; 14 px inside the right edge) should be
        // near-opaque black (R close to 0) ; the last sample (x = 60 ;
        // 16 px outside the rect, > 1 radius beyond the edge) should be
        // very close to white.
        assertTrue(luminanceProfile[0] < 30,
            "x = 30 (inside rect) R should be near 0, got ${luminanceProfile[0]}")
        assertTrue(luminanceProfile[30] >= 250,
            "x = 60 (well past blur extent) R should be near 255, got ${luminanceProfile[30]}")

        // Monotonic non-decrease along the row from x = 30 to x = 60 :
        // the alpha gradient must be smooth. Allow up to 1 unit of
        // 8-bit quantisation noise.
        for (dx in 1 until luminanceProfile.size) {
            assertTrue(
                luminanceProfile[dx] >= luminanceProfile[dx - 1] - 1,
                "row profile must be (nearly) monotone : " +
                    "luminance[${dx - 1}] = ${luminanceProfile[dx - 1]}, " +
                    "luminance[$dx] = ${luminanceProfile[dx]}",
            )
        }

        // The edge transition has visible "soft" pixels in the
        // (rect right edge .. rect right edge + radius) band -- i.e.
        // x in [44, 56]. At least one pixel in that band must have a
        // luminance strictly between the interior (~0) and the bg (255).
        // Otherwise the blur isn't producing partial coverage.
        val edgeBand = (44..56).map { x -> pixels.rgbaAt(x, 32)[0] }
        val partials = edgeBand.count { it in 20..240 }
        assertTrue(partials >= 3,
            "expected at least 3 partially-covered pixels in the right-edge " +
                "blur band, got ${partials} (band luminances : $edgeBand)")
    }

    @Test
    fun `drawPath with circle and kNormal blur softens the boundary`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Circle of radius 16 centred at (32, 32), blurred with sigma 4.
        val sigma = 4f
        val cx = 32f; val cy = 32f; val r = 16f
        val circle = SkPath.Circle(cx, cy, r)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(circle, paint)
                device.flush()
            }
        }

        // Circle centre : near-opaque black.
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] < 30,
            "circle centre R should be near 0, got ${centre[0]}")

        // Far outside (top-left corner) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(2, 2),
            "outside the blur extent")

        // Radial profile along the +x axis from the centre. Boundary
        // sits at x = 48 (cx + r). Sample x = 32, 36, 40, 44, 48, 52,
        // 56, 60 -- the luminance must increase monotonically and
        // cross the bg threshold past the blur extent.
        val radialProfile = listOf(32, 36, 40, 44, 48, 52, 56, 60).map { x ->
            pixels.rgbaAt(x, cy.toInt())[0]
        }
        // Monotonic non-decrease.
        for (i in 1 until radialProfile.size) {
            assertTrue(
                radialProfile[i] >= radialProfile[i - 1] - 1,
                "radial profile must be (nearly) monotone : $radialProfile " +
                    "(violation at index $i)",
            )
        }
        // First sample (x = cx = 32) near-opaque, last (x = 60, 12 px
        // beyond the boundary at full sigma) near-background.
        assertTrue(radialProfile.first() < 30,
            "radial profile centre should be near 0, got ${radialProfile.first()}")
        assertTrue(radialProfile.last() >= 245,
            "radial profile far should be near 255, got ${radialProfile.last()}")
    }

    @Test
    fun `paint alpha modulates the blurred output proportionally`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)

        // Render the same rect twice with paint alpha = 255 (opaque)
        // and alpha = 128 (half) ; the blurred output's alpha should
        // halve, and the resulting SrcOver-on-white luminance should
        // move from ~0 (rect centre) to ~128 (half the background
        // bleeds through).
        val opaquePixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = SK_ColorBLACK
                    maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
                }
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
        // Use a fresh context for the second pass (avoid context reuse
        // semantics and keep the test self-contained).
        val context2 = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context2 != null, "No WebGPU adapter for second pass")
        val halfPixels = context2!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val paint = SkPaint().apply {
                    color = (0x80 shl 24) or (SK_ColorBLACK and 0x00FFFFFF)
                    maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
                }
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre pixel of the rect, opaque pass : near-black.
        val opaqueCentre = opaquePixels.rgbaAt(32, 32)
        // Half-alpha pass : centre luminance shifts toward the
        // background (white). Expect ~128 +/- some quantisation.
        val halfCentre = halfPixels.rgbaAt(32, 32)
        assertTrue(opaqueCentre[0] < halfCentre[0] - 50,
            "half-alpha centre ($halfCentre) must be much brighter than " +
                "opaque centre ($opaqueCentre)")
        // Sanity : the half-alpha centre is in the 100-180 range
        // (the centre is near-saturated coverage, so halving alpha
        // gives roughly bg/2 = 127). Bounds are generous to absorb
        // F16 precision and any blur tail loss.
        assertTrue(
            halfCentre[0] in 100..180,
            "half-alpha centre R should be ~128, got ${halfCentre[0]}",
        )
    }

    @Test
    fun `drawRect with kSolid blur keeps interior fully solid and halos outward`() {
        // kSolid : output = min(M + B, 1). The sharp shape stays
        // fully opaque (M = 1 inside the rect), and a soft outer halo
        // (B > 0 outside) extends past the original edges. Inside the
        // rect there's NO partial-coverage band (kNormal would have
        // one near the inner edge as the kernel mass leaks out) -- M
        // re-saturates every interior pixel.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : fully opaque black (coverage = 1).
        val centre = pixels.rgbaAt(32, 32)
        assertEquals(listOf(0, 0, 0, 255), centre,
            "kSolid centre must be fully opaque")

        // Just inside the right edge (x = 43, the last rect column) :
        // still fully opaque under kSolid since M = 1. Under kNormal,
        // this column would already be partially transparent because
        // the blurred kernel is leaking the mass outward.
        val justInside = pixels.rgbaAt(43, 32)
        assertEquals(listOf(0, 0, 0, 255), justInside,
            "kSolid interior must stay fully solid up to the edge")

        // Just outside the right edge (x = 44) : partial coverage --
        // the halo starts. Should be partially transparent (luminance
        // between 0 and 255).
        val justOutside = pixels.rgbaAt(44, 32)
        assertTrue(justOutside[0] in 20..240,
            "kSolid halo at x = 44 must show partial coverage, got $justOutside")

        // Far outside (x = 60, 16 px past the edge) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kSolid far-outside must be background")
    }

    @Test
    fun `drawRect with kOuter blur shows halo only outside the shape`() {
        // kOuter : output = max(B - M, 0). Inside the shape M = 1
        // and B is also high (kernel is fully covered) so B - M = 0
        // -> the interior is transparent (background bleeds through).
        // Outside the shape M = 0 and B > 0 -> only the halo remains.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kOuter, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : transparent (B - M = 0). Background
        // bleeds through -> white.
        val centre = pixels.rgbaAt(32, 32)
        assertEquals(listOf(255, 255, 255, 255), centre,
            "kOuter centre must be background (halo subtracted by M)")

        // Just outside the right edge : halo present.
        val haloBand = (45..56).map { x -> pixels.rgbaAt(x, 32)[0] }
        val partials = haloBand.count { it in 20..240 }
        assertTrue(partials >= 3,
            "kOuter must have partial halo pixels outside the rect, " +
                "got band luminances $haloBand")

        // Far outside : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kOuter far-outside must be background")
    }

    @Test
    fun `drawRect with kInner blur shows blur clipped inside the shape`() {
        // kInner : output = B * M. Outside the shape (M = 0) the
        // output is 0 -> background. Inside the shape the blurred
        // coverage B is < 1 near the inner edges (the kernel partly
        // leaks past the boundary) -> a darker centre, brighter near
        // the inner edges (B falls off going inward toward 0 at the
        // edge of M).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kInner, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : near-opaque (M = 1, B saturated since
        // the 24x24 interior is wider than 2*radius = 24 for sigma 4,
        // borderline -- the centre tap mass is essentially full).
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] < 30,
            "kInner centre R should be near 0 (full B * 1), got ${centre[0]}")

        // Just outside the right edge (x = 44) : background. M = 0
        // there, so no contribution regardless of B.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(44, 32),
            "kInner just-outside must be background (clipped by M = 0)")

        // Far outside : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kInner far-outside must be background")

        // Inside the rect, near the right edge (x = 43, the last
        // interior column) : the blur kernel mostly straddles the
        // edge so B is roughly half. Output should be lighter than
        // the centre -- the inner falloff.
        val justInside = pixels.rgbaAt(43, 32)
        assertTrue(justInside[0] > centre[0] + 20,
            "kInner must fade toward the interior edge : justInside R " +
                "(${justInside[0]}) should exceed centre R (${centre[0]}) " +
                "by at least 20 luminance units")
    }

    @Test
    fun `drawRect with large sigma 20 routes through cascade and spreads beyond single-stage cap`() {
        // Phase MaskFilter-blur-cascade -- sigma = 20 is roughly 2x
        // the single-stage cap (~10) so it routes through the
        // downsample-blur-upsample cascade. We pick the rect large
        // enough (120x120) that the centre sees ~full kernel mass,
        // so coverage at the centre is near opaque. The 3-sigma blur
        // reach is 60 px ; we sample well past the single-stage's
        // 32-pixel kernel reach to verify the cascade carries the
        // blur further than the historical clamp would.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val w = 256
        val h = 256
        val sigma = 20f
        // Centre = (128, 128), half-size = 60 so the rect is 120x120
        // and the kernel (60 px reach) fits comfortably inside the
        // saturated interior at the centre.
        val rect = SkRect.MakeLTRB(68f, 68f, 188f, 188f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, w, h).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        fun lumAt(x: Int, y: Int): Int {
            val i = (y * w + x) * 4
            return pixels[i].toInt() and 0xFF
        }

        // Centre of the rect : the 120x120 rect is large enough for
        // the kernel (60 px reach per side) to find full coverage at
        // the centre. Expect near-opaque black.
        val centreLum = lumAt(128, 128)
        assertTrue(centreLum < 60,
            "rect centre should be near-opaque black (R < 60), got $centreLum")

        // 20 px past the rect's right edge (= 1 sigma past) :
        // expected coverage ~ 0.5 * erfc(1 / sqrt(2)) ~ 0.16, so
        // luminance ~ (1 - 0.16) * 255 ~ 214. The single-stage 32-tap
        // clamp would give some coverage here too (1 sigma is well
        // inside the 32-px reach) but with the cascade we expect the
        // full Gaussian shape, no truncation.
        val lum1Sigma = lumAt(208, 128)
        assertTrue(lum1Sigma in 180..240,
            "1 sigma past edge (sigma 20) should be a midtone-ish " +
                "(R in [180, 240]), got $lum1Sigma")

        // 32 px past the rect's right edge (x = 188 + 32 = 220) :
        // about 1.6 sigma. Expected coverage ~ 0.5 * erfc(1.6/sqrt(2))
        // ~ 0.055, luminance ~ 241. Loose threshold -- the key
        // contract is that the cascade carries SOME blur past the
        // single-stage 32-px kernel reach (the single-stage clamp
        // would be ~255 here).
        val lumPast32 = lumAt(220, 128)
        assertTrue(lumPast32 < 250,
            "with cascade (sigma 20), pixel 32 px past the edge should " +
                "still carry some blur falloff (R < 250), got $lumPast32 -- " +
                "the single-stage clamp would yield ~255 here")

        // 60 px past the edge (3 sigma -- the kernel's tail) :
        // approaches background but still shows a tiny falloff.
        val lumFar = lumAt(248, 128)
        assertTrue(lumFar >= 240,
            "60 px past the edge should be near-background (R >= 240), " +
                "got $lumFar")

        // Profile from x = 188 (right edge) to x = 248 (3 sigma past)
        // must be monotonically non-decreasing.
        val profile = (188..248 step 4).map { x -> lumAt(x, 128) }
        for (i in 1 until profile.size) {
            assertTrue(profile[i] >= profile[i - 1] - 2,
                "cascade profile must be monotone : profile = $profile, " +
                    "violation at index $i")
        }

        // Far corner (background) : white.
        assertEquals(255, lumAt(2, 2),
            "far from the blur extent : background")
    }

    @Test
    fun `drawRect with sigma 64 spreads gradually over 3 sigma 192 px`() {
        // Phase MaskFilter-blur-cascade -- sigma = 64 is ~6x the
        // single-stage cap, exercising a 3+ stage cascade. Render a
        // 320x320 rect on a 768x768 canvas (the rect is big enough
        // that the 3-sigma kernel reach of 192 px finds full coverage
        // at the centre).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val w = 768
        val h = 768
        val sigma = 64f
        // Centre = (384, 384), rect spans 320x320.
        val rect = SkRect.MakeLTRB(224f, 224f, 544f, 544f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, w, h).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        fun lumAt(x: Int, y: Int): Int {
            val i = (y * w + x) * 4
            return pixels[i].toInt() and 0xFF
        }

        // Centre of the rect : 320x320 is large enough for the kernel
        // to find full coverage. Expect near-opaque black.
        val centreLum = lumAt(384, 384)
        assertTrue(centreLum < 60,
            "rect centre should be near-opaque black (R < 60), got $centreLum")

        // 64 px past the right edge (= 1 sigma) : expected coverage
        // ~ 0.16, luminance ~ 214. We test the cascade carries the
        // Gaussian past where the single-stage 32-tap clamp would
        // have yielded ~255.
        val lum1Sigma = lumAt(608, 384)
        assertTrue(lum1Sigma in 180..240,
            "1 sigma (64 px) past edge should be a midtone " +
                "(R in [180, 240]), got $lum1Sigma -- single-stage clamp " +
                "would yield ~255")

        // 96 px past the edge (= 1.5 sigma) : visible blur tail,
        // expected coverage ~ 0.067, luminance ~ 238.
        val lum96 = lumAt(640, 384)
        assertTrue(lum96 < 250,
            "96 px past edge should still carry blur (R < 250), got $lum96")

        // 192 px past the edge (~3 sigma) : approaches background.
        val lum192 = lumAt(736, 384)
        assertTrue(lum192 >= 240,
            "192 px past the edge should be near-background (R >= 240), " +
                "got $lum192")

        // Monotone non-decrease from rect right edge outward.
        val profile = (544..736 step 12).map { x -> lumAt(x, 384) }
        for (i in 1 until profile.size) {
            assertTrue(profile[i] >= profile[i - 1] - 3,
                "cascade profile must be (nearly) monotone : " +
                    "profile = $profile, violation at index $i")
        }

        // Far corner : untouched background.
        assertEquals(255, lumAt(2, 2),
            "far from the blur extent : background")
    }

    @Test
    fun `drawRect cascade preserves kSolid blur style`() {
        // Phase MaskFilter-blur-cascade -- the style combine
        // (kSolid / kOuter / kInner) runs at the FINAL composite
        // pass, sampling the original sharp shape mask. Verify the
        // cascade preserves the kSolid contract (interior stays
        // fully opaque, halo extends outward).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val w = 256
        val h = 256
        val sigma = 20f
        // Use a 120x120 rect so the interior carries enough kernel
        // mass for the halo to be visibly distinct from the bg.
        val rect = SkRect.MakeLTRB(68f, 68f, 188f, 188f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, w, h).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
        fun lumAt(x: Int, y: Int): Int {
            val i = (y * w + x) * 4
            return pixels[i].toInt() and 0xFF
        }

        // kSolid : interior is fully opaque (sharp mask M = 1, so
        // min(M + B, 1) = 1 for every pixel inside the shape).
        assertEquals(0, lumAt(128, 128), "kSolid centre must be fully opaque")
        // The interior right edge (just inside the shape) should also
        // be solid : in pixel-edge coords the rect covers [68, 188) ;
        // x = 187 is still inside.
        assertEquals(0, lumAt(187, 128),
            "kSolid : interior right edge still fully opaque")
        // The halo (outside the shape) still spreads via the cascade.
        // 16 px past the edge (close enough to the boundary that the
        // sigma=20 Gaussian still carries significant mass) has
        // visible darkening.
        val lumNear = lumAt(204, 128)
        assertTrue(lumNear in 1..240,
            "kSolid halo at 16 px past edge should be a midtone, got $lumNear")
        // Far corner : white.
        assertEquals(255, lumAt(2, 2),
            "kSolid far from blur extent : background")
    }

    @Test
    fun `blur draw with no maskFilter falls through to ordinary fill`() {
        // Sanity : the blur dispatch gate must NOT intercept a paint
        // without maskFilter. Regression guard for the dispatch's
        // early-return logic. Render a plain rect ; verify the result
        // matches the existing solid-fill behaviour (full alpha
        // everywhere inside, untouched background outside).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply { color = SK_ColorBLACK }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
        assertEquals(listOf(0, 0, 0, 255), pixels.rgbaAt(32, 32),
            "no maskFilter : centre should be opaque black")
        // Edges sharp (no blur) : (44, 32) is just past the rect's
        // right edge ; non-AA rect doesn't visit it -> background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(44, 32),
            "no maskFilter : just past edge is unblurred background")
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
    }
}
