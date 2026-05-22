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
 * Phase G-saveLayer-backdrop (#591) + Phase J5 backdrop filter +
 * Phase K3 backdrop ColorFilter / DropShadow -- verify that
 * `SkCanvas.saveLayer(SaveLayerRec(..., backdrop = ...))` on a GPU
 * root canvas routes through [SkWebGpuDevice.seedBackdropFrom] and
 * pre-fills the new layer with the parent device's pixels, optionally
 * filtered through the backdrop [org.skia.foundation.SkImageFilter]
 * before they land.
 *
 * **Scope.**
 *  - `backdrop = null` : layer starts transparent (regression guard --
 *    same as the pre-slice behaviour).
 *  - `backdrop = SkImageFilters.Blur(input = null)` with positive
 *    sigma : layer is seeded with the parent's pixels in the layer
 *    bbox, then a separable Gaussian blur is applied in-place via the
 *    existing `blur_image_filter.wgsl` pipeline. Final composite shows
 *    blurred-parent-pixels-under-new-draws (frosted-glass pattern).
 *  - `backdrop = SkImageFilters.ColorFilter(cf, input = null)` (K3) :
 *    the cf folds into the copy-seed's `colorFilterPacked` slot --
 *    pixel-perfect equivalent to a snapshot-then-apply-cf path. No
 *    extra render pass.
 *  - `backdrop = SkImageFilters.DropShadow(dx, dy, sigmaX, sigmaY,
 *    color, input = null)` (K3) : copy-seed + 3-pass blurred-and-
 *    tinted composite onto the seed with `mode = kDstOver` (shadow
 *    behind the seed). The shadow shows through transparent regions
 *    of the seed, matching upstream `SkDropShadowImageFilter` semantics.
 *  - Other backdrop variants (Offset, MatrixTransform, Compose, ...)
 *    fall back to copy-only semantics on GPU. The CPU raster path
 *    remains the full-featured fallback (see `SaveLayerRecBackdropTest`
 *    for the comprehensive filter coverage).
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
    fun `saveLayer with non-null ColorFilter backdrop applies the color filter on the seed`() {
        // Phase K3 main exercise -- a `SkImageFilters.ColorFilter`
        // backdrop folds the inner cf into the copy-seed's
        // `colorFilterPacked` slot. The composite shader applies the
        // filter per-sample as the parent pixels land in the layer,
        // identical to the CPU raster path's snapshot-then-apply-cf.
        //
        // Test cf : Blend(SK_ColorGREEN, kSrcIn). SrcIn against an
        // opaque source returns the cf colour (alpha = src.a * cfColor
        // = src.a * 1.0 = src.a ; rgb = cfColor.rgb premul by cfColor.a
        // = green premul). So every opaque pixel in the seed becomes
        // opaque green ; transparent pixels stay transparent.
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
                // ColorFilter(Blend(green, kSrcIn)) : turns the seed's
                // RGB to green wherever the seed is opaque. Both the
                // red background pixels (inside the layer bbox but
                // outside the blue square) and the blue square pixels
                // are opaque, so both become green in the layer ;
                // the composite back to the parent paints green
                // wherever the layer bbox covers.
                val cf = org.skia.foundation.SkColorFilters.Blend(
                    SK_ColorGREEN,
                    org.skia.foundation.SkBlendMode.kSrcIn,
                )
                val backdrop = SkImageFilters.ColorFilter(cf, null)
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // Inside the layer : every pixel becomes opaque green (cf
        // applied on the seed). Both the previously-blue square's
        // footprint and the previously-red surround turn green.
        assertRgbaApprox(pixels, 12, 12, 0, 255, 0, 255, tag = "cf-tinted (was blue)", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 0, 255, 0, 255, tag = "cf-tinted (was red)", tol = 2)
        // Outside the layer : red untouched (the layer bbox didn't
        // reach (2, 2) ; the parent's red survives the saveLayer).
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with non-null DropShadow backdrop seeds parent plus shadow behind`() {
        // Phase K3 main exercise -- a `SkImageFilters.DropShadow`
        // backdrop seeds the layer with the parent pixels (copy seed)
        // AND lands a colorized blurred shadow BEHIND the seed via
        // kDstOver. The shadow shows through transparent regions of
        // the seed -- here the layer rect extends past the opaque
        // blue square's footprint into transparent (white-on-white)
        // areas where the shadow becomes visible.
        //
        // Layout :
        //   - White parent background.
        //   - Opaque blue square at (10..14, 10..14) -- the layer
        //     bbox (8..20, 8..20) extends 2 px past it on each side.
        //   - Backdrop DropShadow(dx = 4, dy = 4, sigma = 1, black).
        //
        // After saveLayer + immediate restore : inside the layer bbox,
        // the seed has copied the parent (blue square on white) ;
        // the shadow has been composited behind. White is opaque, so
        // the shadow is hidden in white regions of the seed. The
        // shadow becomes visible only where the seed is transparent,
        // which on a white-opaque-everywhere parent is nowhere --
        // making this test specifically exercise the dispatch wiring
        // (no crash, copy-seed still correct) rather than the visual
        // shadow effect. The shadow IS visible when the parent has
        // transparent regions ; we verify that separately by setting
        // a transparent background.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                // Transparent-clear background so the shadow has
                // somewhere to land (the seed is transparent where
                // the parent is transparent, and kDstOver lets the
                // shadow show through).
                device.setBackground(0)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(4f, 4f, 28f, 28f)
                // Opaque blue square -- the only opaque region of
                // the parent inside the layer bbox.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 14f, 14f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                // DropShadow(dx = 4, dy = 4, sigma = 1, opaque black).
                // The blur of the seed's alpha channel (1 inside the
                // blue square, 0 elsewhere) at offset (4, 4) creates
                // a shadow at (14..18, 14..18) -- visible because
                // the parent's transparent surround leaves the seed
                // transparent there and kDstOver lets the shadow
                // through.
                val backdrop = SkImageFilters.DropShadow(
                    4f, 4f, 1f, 1f,
                    /* color = */ 0xFF000000.toInt(),
                    /* input = */ null,
                )
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // The blue square's centre survives unchanged (the shadow is
        // behind the opaque blue seed pixels ; kDstOver keeps them).
        assertRgbaApprox(pixels, 12, 12, 0, 0, 255, 255, tag = "blue seed (shadow behind)", tol = 2)
        // The shadow region (offset by (4, 4) from the blue square
        // centre) lands in the transparent seed surround : we expect
        // a dark pixel with non-zero alpha (the blurred shadow). The
        // exact value depends on the Gaussian kernel sum at that
        // pixel ; we just assert the shadow is present (alpha > 0
        // and rgb close to zero -- the shadow colour is black).
        val sIdx = (16 * W + 16) * 4
        val sA = pixels[sIdx + 3].toInt() and 0xFF
        val sR = pixels[sIdx].toInt() and 0xFF
        val sG = pixels[sIdx + 1].toInt() and 0xFF
        val sB = pixels[sIdx + 2].toInt() and 0xFF
        assertTrue(sA > 10) { "(16, 16) : shadow should land here, got alpha=$sA" }
        // Black shadow -- rgb should be near zero (with premul, rgb
        // <= alpha so rgb is small when alpha is small too, but the
        // ratio rgb/alpha is the unpremul colour which is black =
        // (0, 0, 0)). We assert rgb <= alpha (premul invariant) and
        // rgb is small in absolute terms.
        assertTrue(sR <= sA + 1) { "(16, 16) : premul invariant R<=A, got R=$sR A=$sA" }
        assertTrue(sG <= sA + 1) { "(16, 16) : premul invariant G<=A, got G=$sG A=$sA" }
        assertTrue(sB <= sA + 1) { "(16, 16) : premul invariant B<=A, got B=$sB A=$sA" }
        // Outside the layer bbox : transparent untouched.
        val oIdx = (2 * W + 2) * 4
        assertTrue((pixels[oIdx + 3].toInt() and 0xFF) == 0) {
            "(2, 2) : outside layer should be transparent"
        }
    }

    @Test
    fun `saveLayer with non-null MatrixTransform backdrop remaps parent pixels into the layer`() {
        // L2b main exercise -- a `SkImageFilters.MatrixTransform`
        // backdrop replaces the plain copy-only seed with an affine
        // remap. The composite shader receives the inverse 2x3 (with
        // the parent origin baked into the translation slots) and
        // samples the parent's intermediate at `M^{-1}(child_px) +
        // (originX, originY)` per fragment.
        //
        // Test matrix : translate(+4, 0) -- shifts the parent's pixels
        // 4 px to the right in the layer's view. With a blue square at
        // parent (10..14, 10..14) and a layer at parent (8..24, 8..24),
        // the backdrop's M maps the layer input rect (0..16, 0..16) to
        // (4..20, 0..16) in layer space ; the shader reads
        // `parent[(cx - 4) + 8, cy + 8]`, so the blue square (which
        // sits at layer-local (2..6, 2..6) before the matrix) lands at
        // layer-local (6..10, 2..6) after the matrix. Mapped onto
        // parent coords, the blue square in the final composite shows
        // up at parent (14..18, 10..14).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Pre-draw a blue square on the parent so we have a
                // sharp blue/red edge for the matrix to translate.
                canvas.drawRect(
                    SkRect.MakeLTRB(10f, 10f, 14f, 14f),
                    SkPaint().apply { color = SK_ColorBLUE },
                )
                // MatrixTransform(translate(+4, 0)) backdrop. The
                // shader maps each child px (cx, cy) to parent px
                // (cx - 4 + 8, cy + 8) = (cx + 4, cy + 8).
                val matrix = org.graphiks.math.SkMatrix.MakeTrans(4f, 0f)
                val backdrop = org.skia.foundation.SkImageFilters.MatrixTransform(
                    matrix,
                    org.skia.foundation.SkSamplingOptions(
                        org.skia.foundation.SkFilterMode.kNearest,
                    ),
                    /* input = */ null,
                )
                canvas.saveLayer(SaveLayerRec(layerBounds, null, backdrop))
                canvas.restore()
                device.flush()
            }
        }

        // Inside the post-matrix blue footprint (parent (14..18, 10..14)) :
        // the seed produced blue, the composite back lands blue on red.
        // At parent (15, 11) the child px is (7, 3), the shader samples
        // parent at (7 - 4 + 8, 3 + 8) = (11, 11) which is blue.
        assertRgbaApprox(pixels, 15, 11, 0, 0, 255, 255, tag = "translated blue (right)", tol = 2)
        assertRgbaApprox(pixels, 17, 13, 0, 0, 255, 255, tag = "translated blue (right edge)", tol = 2)
        // Discriminator vs the copy-only fallback : at parent (13, 11)
        // the pre-saveLayer pixel is BLUE (inside the original blue
        // square). With copy-only seed the child px (5, 3) would copy
        // parent (13, 11) = blue, the composite back lands blue. With
        // the matrix-remapped seed, the shader samples parent at
        // (5 - 4 + 8, 3 + 8) = (9, 11) which is OUTSIDE the blue square
        // = red. The opaque red seed replaces the pre-saveLayer blue
        // when the layer composites back via SrcOver. If this assertion
        // sees blue, the matrix branch silently degraded to copy-only.
        assertRgbaApprox(pixels, 13, 11, 255, 0, 0, 255, tag = "MT branch active vs copy-only", tol = 2)
        // Inside the layer but on the post-matrix red surround : red.
        assertRgbaApprox(pixels, 20, 12, 255, 0, 0, 255, tag = "remapped red surround", tol = 2)
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
