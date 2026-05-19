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
 * SkBlurMaskFilter(kNormal, sigma)` on the GPU backend.
 *
 * The GPU path :
 *  1. Allocates a child layer device sized to the path bounds + 3*sigma
 *     padding.
 *  2. Renders the path white-tinted onto the child's intermediate.
 *  3. Drains the child via [SkWebGpuDevice.flushDrawsOnly].
 *  4. Enqueues a [SkWebGpuDevice.BlurredPathDraw] that the parent's
 *     flush replays as two render passes (H Gaussian blur on a scratch
 *     texture, then V Gaussian blur + paint-colour modulation onto the
 *     parent's intermediate).
 *
 * Coverage in this suite :
 *  - `drawRect` + blur : the rect's edges become soft (gradient from
 *    full alpha to zero over ~3*sigma pixels) ; the rect's interior
 *    keeps the paint colour. Sample a row of pixels crossing the edge
 *    and verify monotonic decay.
 *  - `drawPath(circle)` + blur : same edge-softness check on a curved
 *    boundary.
 *  - paint.alpha modulation : the V-pass uniform pre-multiplies the
 *    paint colour, so halving the alpha halves the alpha of every
 *    blurred pixel.
 *  - background bleed-through : far outside the path, the parent's
 *    background pixels stay untouched (SrcOver with alpha = 0).
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
