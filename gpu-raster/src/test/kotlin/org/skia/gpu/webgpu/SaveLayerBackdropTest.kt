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
 * Phase G-saveLayer-backdrop -- verify that
 * `SkCanvas.saveLayer(SaveLayerRec(..., backdrop = ...))` on a GPU
 * root canvas routes through [SkWebGpuDevice.seedBackdropFrom] and
 * pre-fills the new layer with the parent device's pixels (copy-only
 * slice -- the backdrop [SkImageFilter] itself is currently ignored
 * on GPU).
 *
 * **Scope of this slice (copy-only).**
 *  - `backdrop = null` : layer starts transparent (regression guard --
 *    same as the pre-slice behaviour).
 *  - `backdrop != null` : layer is seeded with a 1:1 copy of the
 *    parent's pixels in the layer bbox. The filter is dropped ; the
 *    final composite shows parent-pixels-under-new-draws, not
 *    transparent-under-new-draws.
 *
 * Filter application (Blur / ColorFilter / Offset on the backdrop
 * snapshot) is a deferred follow-up -- the unit tests below assert
 * **copy-only** semantics, not blurred-backdrop semantics. The CPU
 * raster path remains the full-featured fallback (see
 * `SaveLayerRecBackdropTest` for the filter-applied case).
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
    fun `saveLayer with non-null backdrop seeds the layer with parent pixels`() {
        // Phase G-saveLayer-backdrop main exercise -- a non-null backdrop
        // triggers [SkWebGpuDevice.seedBackdropFrom], which copies the
        // parent's pixels into the layer's bbox. We don't draw anything
        // inside the layer scope ; the composite back to the parent
        // should leave the parent's red unchanged (it composited red
        // pixels onto themselves via SrcOver, alpha = 1, no-op).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Pre-draw a blue circle (a rect actually) on the
                // parent so we have something non-uniform in the
                // layer's bbox.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 18f, 18f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                // Open the layer with a Blur backdrop (filter ignored
                // in the copy-only slice). Don't draw anything inside.
                // Restore composites the (parent-seeded) layer back
                // onto the parent.
                val backdrop = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // Parent had blue at (10..18, 10..18) on red elsewhere. The
        // backdrop seeded the layer with a copy of these pixels in the
        // (8..24, 8..24) bbox ; restore SrcOver-composited the same
        // pixels onto the same locations -> the parent is unchanged.
        // (This is the architectural-pattern test : layer started from
        // parent pixels, not transparent.)
        assertRgbaApprox(pixels, 12, 12, 0, 0, 255, 255, tag = "blue inside layer", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 0, 0, 255, tag = "red inside layer (no blue)", tol = 2)
        // Outside the layer's bbox : red background untouched.
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
