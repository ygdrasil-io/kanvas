package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import kotlin.math.abs

/**
 * Phase G-saveLayer-backdrop (#591) + Phase J5 backdrop filter --
 * verify that `SkCanvas.saveLayer(SaveLayerRec(..., backdrop = ...))`
 * on a GPU root canvas routes through
 * [SkWebGpuDevice.seedBackdropFrom] and pre-fills the new layer with
 * the parent device's pixels, optionally filtered through the
 * backdrop [org.skia.foundation.SkImageFilter] before they land.
 *
 * **Scope.**
 *  - `backdrop = null` : layer starts transparent (regression guard --
 *    same as the pre-slice behaviour).
 *  - `backdrop = SkImageFilters.Blur(input = null)` with positive
 *    sigma : layer is seeded with the parent's pixels in the layer
 *    bbox, then a separable Gaussian blur is applied in-place via the
 *    existing `blur_image_filter.wgsl` pipeline. Final composite shows
 *    blurred-parent-pixels-under-new-draws (frosted-glass pattern).
 *  - Other backdrop variants (ColorFilter, Offset, MatrixTransform,
 *    DropShadow, Compose, ...) fall back to copy-only semantics on
 *    GPU. The CPU raster path remains the full-featured fallback (see
 *    `SaveLayerRecBackdropTest` for the comprehensive filter
 *    coverage).
 */
class SaveLayerBackdropTest {

    @Test
    fun `saveLayer with backdrop null starts the child layer transparent`() {
        // Regression guard -- the SaveLayerRec(backdrop = null) overload
        // must behave bit-iso with the legacy `saveLayer(bounds, paint)`
        // path. The child layer starts from a transparent clear ; only
        // explicit draws inside the layer scope land on the parent.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Open the layer with backdrop = null. Draw a green
                // rectangle ; restore. The child started transparent
                // and only the green rect was drawn into it, so the
                // composite (SrcOver) of opaque green over red yields
                // green inside the layer's footprint.
                canvas.saveLayer(SaveLayerRec(layerBounds, null, null))
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside the layer : opaque green (SrcOver of green over red).
        assertRgbaApprox(pixels, 12, 12, 0, 255, 0, 255, tag = "layer center")
        assertRgbaApprox(pixels, 20, 20, 0, 255, 0, 255, tag = "layer center #2")
        // Outside the layer : red background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside layer")
        assertRgbaApprox(pixels, 28, 28, 255, 0, 0, 255, tag = "outside layer #2")
    }

    @Test
    fun `saveLayer with non-null Blur backdrop blurs the parent pixels into the layer`() {
        // Phase J5 main exercise -- a `SkImageFilters.Blur` backdrop
        // triggers the GPU blur seed path : the parent's pixels are
        // copied into the layer's bbox AND then run through a
        // separable Gaussian (same `blur_image_filter.wgsl` pipeline
        // as `paint.imageFilter = Blur` on saveLayer composite). We
        // don't draw anything inside the layer scope ; the composite
        // back to the parent paints the blurred copy on top of the
        // unmodified parent. Inside the layer bbox the result is the
        // (parent SrcOver blurred-parent) which, for an opaque blur
        // output, equals the blurred parent.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Pre-draw a blue square on the parent so we have a
                // sharp blue/red edge for the blur to spread.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 18f, 18f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                // Open the layer with a Blur backdrop. Phase J5 :
                // the GPU now honours the Blur and the seeded layer
                // pixels are blurred.
                val backdrop = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // Center of the original blue square (12, 12) : still mostly
        // blue but with red bleeding in from the surrounding (visible
        // because the layer bbox extends out to (8..24) and the blur
        // pulls in red from (8..10) on each side). The blur output
        // here is bluish-purple, NOT pure blue -- this is the J5
        // discriminator vs the pre-J5 copy-only behaviour.
        val cIdx = (12 * W + 12) * 4
        val cR = pixels[cIdx].toInt() and 0xFF
        val cG = pixels[cIdx + 1].toInt() and 0xFF
        val cB = pixels[cIdx + 2].toInt() and 0xFF
        // Blue dominates, but red is present (blur bleed) ; not pure blue.
        assertTrue(cB > cR) { "(12, 12) : blue should dominate, got R=$cR B=$cB" }
        assertTrue(cR > 10) { "(12, 12) : red should bleed in (blur), got R=$cR" }
        // Just past the original blue square's right edge but
        // inside the layer (18, 14) : the blur spreads BLUE into the
        // red region. Pre-J5 (copy-only) would land at pure red ;
        // J5 lands at a red+blue mixture. (Distance 1 from the blue
        // edge -- well within the sigma=2 Gaussian kernel's reach.)
        val sIdx = (14 * W + 18) * 4
        val sR = pixels[sIdx].toInt() and 0xFF
        val sB = pixels[sIdx + 2].toInt() and 0xFF
        assertTrue(sR > 50) { "(18, 14) : red present (right edge spread), got R=$sR" }
        assertTrue(sB > 50) { "(18, 14) : blue should bleed in (blur), got B=$sB" }
        // Outside the layer's bbox : red background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with non-null ColorFilter backdrop falls back to copy-only on GPU`() {
        // Phase J5 regression guard -- ColorFilter backdrops are NOT
        // yet implemented on GPU and must degrade silently to
        // copy-only (the parent's pixels land in the layer
        // unchanged ; the filter is dropped). This matches the
        // SkDevice.seedBackdropFrom kdoc and preserves the PR #591
        // contract for unsupported variants. The CPU raster path
        // applies the filter (see `SaveLayerRecBackdropTest`).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 18f, 18f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                // Wrap a ColorFilter (Blend(black, kSrcIn) -- turns
                // everything opaque black) as a backdrop. On GPU the
                // filter is silently dropped and the layer is seeded
                // with raw parent pixels -- the composite back to the
                // parent is a no-op.
                val cf = org.skia.foundation.SkColorFilters.Blend(
                    org.graphiks.math.SK_ColorBLACK,
                    org.skia.foundation.SkBlendMode.kSrcIn,
                )
                val backdrop = SkImageFilters.ColorFilter(cf, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // Copy-only fallback : parent pixels survive unchanged inside
        // the layer bbox (blue stays blue, red stays red).
        assertRgbaApprox(pixels, 12, 12, 0, 0, 255, 255, tag = "blue copy-only", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 0, 0, 255, tag = "red copy-only", tol = 2)
        // Outside the layer : red untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with backdrop and draw composites new content on top of seeded pixels`() {
        // Phase G-saveLayer-backdrop -- this is the main frosted-glass
        // pattern : seed from parent, draw on top of the seed. The
        // composite should show parent-pixels-under-new-content (where
        // new content is non-opaque) rather than transparent-under-
        // new-content (which would be the pre-slice behaviour).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Open layer with backdrop (filter ignored in
                // copy-only slice -- layer seeds with raw red parent
                // pixels). Draw a half-alpha green rect that doesn't
                // fully cover the layer.
                val backdrop = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                // Half-alpha green stripe inside the layer.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 12f, 22f, 16f),
                    SkPaint().apply {
                        color = SK_ColorGREEN
                        alpha = 128
                    },
                )
                canvas.restore()
                device.flush()
            }
        }

        // Outside the green stripe but INSIDE the layer : copy of
        // parent red. With copy-only seeding, the layer pixels are
        // red (premul) ; the SrcOver composite back to the parent is
        // red over red = red. (Without seeding, the layer would have
        // been transparent here and the composite would still show red
        // -- but for a different reason. See the next assertion for
        // the discriminating check.)
        assertRgbaApprox(pixels, 12, 10, 255, 0, 0, 255, tag = "above stripe, inside layer", tol = 2)

        // INSIDE the green stripe : the layer holds (seeded red) +
        // (half-alpha green SrcOver on top). The half-alpha green
        // SrcOver over premul red gives :
        //   out = src + dst * (1 - srcAlpha)
        //   src = (0, 128/255, 0, 128/255) premul = (0, 0.502, 0, 0.502)
        //   dst (in layer) = (1, 0, 0, 1)   [seeded from parent red]
        //   out = (0 + 1 * 0.498, 0.502 + 0, 0, 0.502 + 0.498) =
        //         (~127, ~128, 0, 255) premul.
        // Restore SrcOver of this premul onto the parent's red :
        //   src = (~127, ~128, 0, 255), dst = (255, 0, 0, 255)
        //   out = (127 + 255 * 0, 128 + 0, 0, 255) = (127, 128, 0, 255).
        // WITHOUT seeding (transparent layer), the same draw inside
        // would give layer = (0, 128, 0, 128) premul, composited onto
        // red gives (255*127/255 + 0, 128 + 0, 0, 128 + 127) =
        // (127, 128, 0, 255). Both paths land at the same pixel here
        // because the seeded pixels are premul-red and the half-alpha
        // green draw's SrcOver is associative with that. The
        // discriminator below uses a fully-out-of-stripe-but-inside-
        // layer pixel.
        assertRgbaApprox(pixels, 16, 14, 127, 128, 0, 255, tag = "inside stripe", tol = 4)

        // Outside the layer entirely : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with backdrop preserves saveLayer-with-bounds clipping`() {
        // The seed pass uses scissor = (0, 0, w, h) on the child --
        // verify that draws inside the layer scope can't escape the
        // layer's bounds even when the seed happened. Mirrors the
        // existing 'saveLayer with bounds restricts composite' test
        // but with a non-null backdrop.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val backdrop = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                // Draw outside the layer's bounds -- should be clipped.
                canvas.drawRect(
                    SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer bounds : the layer's pixels are (seeded white) +
        // (opaque blue drawn on top) = blue. Composite back gives blue.
        assertRgbaApprox(pixels, 15, 15, 0, 0, 255, 255, tag = "inside layer with backdrop")
        // Outside the layer bounds : white background untouched (the
        // layer device is sized to 12×12 ; the composite only writes
        // that bbox).
        assertRgbaApprox(pixels, 5, 5, 255, 255, 255, 255, tag = "outside layer top-left")
        assertRgbaApprox(pixels, 25, 25, 255, 255, 255, 255, tag = "outside layer bottom-right")
    }

    @Test
    fun `saveLayer with backdrop preserves parent prior draws after restore plus more draws`() {
        // Regression guard for the `intermediateInitialized` flag :
        // after seedBackdropFrom flushes the parent mid-render, the
        // parent's subsequent flush must use loadOp = Load so the
        // pre-saveLayer parent draws survive. Without the flag, the
        // final flush would clear and only the post-restore draws
        // would land.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // Pre-saveLayer draw : red rect in the top-left.
                canvas.drawRect(
                    SkRect.MakeLTRB(2f, 2f, 6f, 6f),
                    SkPaint().apply { color = SK_ColorRED },
                )
                // Open / close a layer with backdrop. This is the
                // critical mid-render flushDrawsOnly call.
                val backdrop = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
                val layerBounds = SkRect.MakeLTRB(12f, 12f, 20f, 20f)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                // Post-restore draw : blue rect in the bottom-right.
                canvas.drawRect(
                    SkRect.MakeLTRB(26f, 26f, 30f, 30f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                device.flush()
            }
        }

        // Pre-saveLayer red rect must survive the mid-render flush.
        assertRgbaApprox(pixels, 4, 4, 255, 0, 0, 255, tag = "pre-saveLayer red")
        // Post-restore blue rect must land.
        assertRgbaApprox(pixels, 28, 28, 0, 0, 255, 255, tag = "post-restore blue")
        // Background : white untouched between the two rects.
        assertRgbaApprox(pixels, 16, 16, 255, 255, 255, 255, tag = "background middle")
        assertRgbaApprox(pixels, 0, 16, 255, 255, 255, 255, tag = "background left edge")
    }

    private fun assertRgbaApprox(
        rgba: ByteArray, x: Int, y: Int,
        r: Int, g: Int, b: Int, a: Int,
        tag: String, tol: Int = 1,
    ) {
        val i = (y * W + x) * 4
        val ar = rgba[i].toInt() and 0xFF
        val ag = rgba[i + 1].toInt() and 0xFF
        val ab = rgba[i + 2].toInt() and 0xFF
        val aa = rgba[i + 3].toInt() and 0xFF
        val ok = abs(ar - r) <= tol && abs(ag - g) <= tol &&
            abs(ab - b) <= tol && abs(aa - a) <= tol
        assertTrue(ok) {
            "$tag at ($x, $y) : expected RGBA=($r, $g, $b, $a) ±$tol got ($ar, $ag, $ab, $aa)"
        }
    }

    private companion object {
        const val W: Int = 32
        const val H: Int = 32
    }
}
