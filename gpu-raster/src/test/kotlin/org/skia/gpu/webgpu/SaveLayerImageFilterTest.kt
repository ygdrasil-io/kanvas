package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.foundation.asBlurImageFilter
import org.skia.foundation.asColorFilterImageFilter
import org.skia.foundation.asComposeImageFilter
import org.skia.foundation.asCropImageFilter
import org.skia.foundation.asDropShadowImageFilter
import org.skia.foundation.asMagnifierImageFilter
import org.skia.foundation.asMatrixTransformImageFilter
import org.skia.foundation.asOffsetImageFilter
import org.skia.foundation.asTileImageFilter
import kotlin.math.abs

/**
 * Phase G-saveLayer-imageFilter scaffolding tests -- verify that
 * `SkCanvas.saveLayer(paint with imageFilter) + drawRect + restore`
 * either routes through the existing colour-filter pipeline (for the
 * supported `SkImageFilters.ColorFilter(cf, input = null)` wrap) or
 * raises a clear error for any other variant.
 *
 * **Scope (scaffolding slice).**
 *  - Routed : `SkImageFilters.ColorFilter(cf, input = null)` with `cf`
 *    one of the supported `SkColorFilters.Blend` / `SkColorFilters.
 *    Matrix` variants. The composite shader runs `cf` per pixel after
 *    the paintColor scale, mirroring `paint.colorFilter`.
 *  - Thrown : `SkImageFilters.Blur`, `SkImageFilters.Offset`,
 *    `SkImageFilters.DropShadow`, ColorFilter wrap with non-null child,
 *    ColorFilter wrap **and** `paint.colorFilter` set together. The
 *    error message names the variant + points at the follow-up slice.
 *
 * The unit tests render onto a raw sRGB-coded readback target
 * (`applyColorspaceTransform = false`), so RGB bytes are directly
 * comparable to the source colours -- same conventions as
 * [SaveLayerTest].
 */
class SaveLayerImageFilterTest {

    @Test
    fun `saveLayer with ColorFilter Matrix grayscale imageFilter turns red layer to gray`() {
        // Phase G-saveLayer-imageFilter -- the routed path. Wrap a
        // grayscale matrix colour filter into an SkImageFilter via
        // SkImageFilters.ColorFilter(cf, input = null) ; the GPU
        // backend unwraps and dispatches it through the same uniform
        // packing as paint.colorFilter. Pixel result is identical to
        // the SaveLayerTest's `Matrix grayscale` case.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val cf = SkColorFilters.Matrix(luma)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(cf, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : gray (~ 76, 76, 76, 255) -- bit-iso with the
        // direct paint.colorFilter Matrix grayscale test.
        assertRgbaApprox(
            pixels, 14, 14,
            76, 76, 76, 255,
            tag = "ImageFilter ColorFilter Matrix grayscale", tol = 2,
        )
        // Outside layer : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with ColorFilter Blend red kPlus imageFilter tints green layer to yellow`() {
        // Phase G-saveLayer-imageFilter -- routed path with the second
        // supported SkColorFilter variant (Blend(colour, mode)). Mirrors
        // SaveLayerTest's `Blend(red, kPlus) colorFilter` case but with
        // the filter expressed as an SkImageFilter wrap.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val cf = SkColorFilters.Blend(SK_ColorRED, org.skia.foundation.SkBlendMode.kPlus)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(cf, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Draw an opaque green rect : (0, 255, 0, 255).
                canvas.drawRect(layerBounds, SkPaint().apply {
                    color = (255 shl 24) or (0 shl 16) or (255 shl 8) or 0
                })
                canvas.restore()
                device.flush()
            }
        }

        // Filter math : red (255, 0, 0, 255) premul as src kPlus green
        // (0, 255, 0, 255) as dst -> (255, 255, 0, 255) yellow.
        // SrcOver composite onto white background : src.a == 255 ->
        // dst replaced by src -> (255, 255, 0, 255).
        assertRgbaApprox(
            pixels, 14, 14,
            255, 255, 0, 255,
            tag = "ImageFilter ColorFilter Blend red kPlus", tol = 2,
        )
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with null imageFilter and null colorFilter is the fast path`() {
        // Regression : the imageFilter detection must not break the
        // existing no-filter composite path. Both slots null ->
        // identity payload, same as SaveLayerTest's `null paint
        // colorFilter is a no-op` case.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint() // both filter slots null.
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Plain SrcOver of opaque red over white -> red inside layer.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "no-filter center")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Blur imageFilter sigma 0 collapses to identity composite`() {
        // Phase G-saveLayer-imageFilter-blur -- sigma == 0 on both axes
        // is the identity case. The dispatch routes through the plain
        // [LayerCompositeDraw] path (no blur scratch textures, no
        // multi-pass) ; the pixel result must be bit-iso with the
        // no-imageFilter saveLayer.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 0f, sigmaY = 0f,
                        tileMode = SkTileMode.kClamp, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Plain SrcOver of opaque red over white -> red inside layer.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "Blur sigma=0 center")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Blur imageFilter kClamp softens layer edges`() {
        // Phase G-saveLayer-imageFilter-blur -- the load-bearing test :
        // a small red rect inset inside a larger layer, blurred with
        // sigma > 0. Properties to verify :
        //   - the rect's interior pixels stay solid red (no kernel mass
        //     loss in the centre),
        //   - the rect's edges fade to white (= blurred red mixed with
        //     transparent surround), forming a soft falloff,
        //   - the falloff width matches the kernel radius (3 * sigma),
        //   - the layer's surround inside the layer bounds stays white
        //     where the kernel hasn't reached.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // 32x32 device. The layer covers the whole device so
                // the blur extends out into the layer's transparent
                // surround. Inside the layer, draw a 12x12 red square
                // inset by 10 px from each side : pixels (10..21).
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 2f, sigmaY = 2f,
                        tileMode = SkTileMode.kClamp, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the red rect -- well inside the kernel's full
        // support, so the convolved sum collapses back to the source
        // colour (kernel sums to 1, all taps land on red).
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "blur centre red", tol = 2)

        // Far outside the rect, but inside the layer -- kernel taps
        // all land on transparent, the SrcOver composite onto the
        // white background leaves white untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "blur far surround white")

        // Just outside the rect edge (1 px past the right edge) --
        // halfway into the kernel's spread, expect a soft pink. With
        // sigma = 2 the centre tap weight is ~0.20 ; a pixel 1 px
        // outside the edge has about half its kernel mass landing on
        // red so the alpha should be ~50% red blended over white.
        val i = (16 * W + 22) * 4
        val r = pixels[i].toInt() and 0xFF
        val g = pixels[i + 1].toInt() and 0xFF
        val b = pixels[i + 2].toInt() and 0xFF
        assertTrue(r > 220 && g > 140 && g < 220 && b > 140 && b < 220) {
            "Just-outside-edge pixel should be a soft pink (high R, mid G/B) ; " +
                "got ($r, $g, $b)"
        }

        // Inside the rect 1 px from the edge -- kernel mostly red but
        // a sliver of transparent taps from outside the rect ; the
        // final colour after SrcOver onto white is a slightly washed-
        // out red.
        val j = (16 * W + 20) * 4
        val r2 = pixels[j].toInt() and 0xFF
        val g2 = pixels[j + 1].toInt() and 0xFF
        val b2 = pixels[j + 2].toInt() and 0xFF
        assertTrue(r2 > 230 && g2 < 120 && b2 < 120) {
            "Inside-edge pixel should be near-red with a faint white wash ; " +
                "got ($r2, $g2, $b2)"
        }
    }

    @Test
    fun `saveLayer with Blur imageFilter input nonNull throws clear error`() {
        // The non-null child case is deferred to a follow-up slice that
        // handles structural-transform pre-passes.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 4f, sigmaY = 4f,
                        tileMode = SkTileMode.kClamp, input = inner,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Blur with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("Blur") && msg.contains("input == null")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with Blur imageFilter kRepeat keeps full-layer edges solid`() {
        // N9 -- the kRepeat path : the layer is fully filled with red, so
        // every kernel tap (including those past the layer extent) wraps
        // around to a red sample. The blurred result is therefore solid
        // red across the entire layer (no edge falloff, unlike kDecal).
        // This is the same property the CPU raster's `SkBlurImageFilter`
        // exhibits under `positiveModInternal` -- the GPU shader mirrors
        // it with the matching positive-mod arithmetic in
        // `blur_image_filter.wgsl::tile_load`.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 4f, sigmaY = 4f,
                        tileMode = SkTileMode.kRepeat, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre is well inside the layer -- every kernel tap is red,
        // even before any wrap kicks in.
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "kRepeat centre", tol = 2)
        // Corner pixel (0, 0) -- with kClamp / kRepeat / kMirror, all
        // kernel taps land on red (clamped at the edge / wrapped to the
        // opposite side / mirrored back into the layer). The output
        // stays solid red with no SrcOver-onto-white wash. Compared
        // against kDecal (which would fade at this corner), this is
        // the load-bearing assertion : the H/V passes consult the
        // tile mode and the corner sees full kernel mass.
        assertRgbaApprox(pixels, 0, 0, 255, 0, 0, 255, tag = "kRepeat corner solid", tol = 2)
        // Mid-edge pixel (16, 0) -- same property.
        assertRgbaApprox(pixels, 16, 0, 255, 0, 0, 255, tag = "kRepeat top edge", tol = 2)
        // Far corner.
        assertRgbaApprox(pixels, W - 1, H - 1, 255, 0, 0, 255, tag = "kRepeat far corner", tol = 2)
    }

    @Test
    fun `saveLayer with Blur imageFilter kMirror keeps full-layer edges solid`() {
        // N9 -- the kMirror path mirrors kRepeat for a full-layer fill :
        // out-of-bound taps reflect back into the layer (which is solid
        // red), so the convolved sum stays solid red on every pixel.
        // Mirrors the CPU raster's `mirrorModInternal` semantics.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Blur(
                        sigmaX = 4f, sigmaY = 4f,
                        tileMode = SkTileMode.kMirror, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "kMirror centre", tol = 2)
        assertRgbaApprox(pixels, 0, 0, 255, 0, 0, 255, tag = "kMirror corner solid", tol = 2)
        assertRgbaApprox(pixels, 16, 0, 255, 0, 0, 255, tag = "kMirror top edge", tol = 2)
        assertRgbaApprox(pixels, W - 1, H - 1, 255, 0, 0, 255, tag = "kMirror far corner", tol = 2)
    }

    @Test
    fun `saveLayer with Compose grayscale Blur kRepeat keeps full-layer edges solid gray`() {
        // N9 -- exercise the Compose(outer=ColorFilter, inner=Blur(kRepeat))
        // path : the Blur leaf is reached through the resolver's walker,
        // so the kRepeat ordinal flows through the same gate that used to
        // throw on non-kClamp/non-kDecal. Layer is fully red, blur edges
        // wrap to red, grayscale CF converts red (0.299 * 255 = ~76) to
        // a uniform gray. The whole layer should be ~(76, 76, 76, 255).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val cf = SkColorFilters.Matrix(luma)
                val blur = SkImageFilters.Blur(
                    sigmaX = 3f, sigmaY = 3f,
                    tileMode = SkTileMode.kRepeat, input = null,
                )
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(cf, input = null),
                        inner = blur,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // 0.299 * 255 ~= 76. Centre and corner should be ~uniform gray
        // since the blur preserves the colour everywhere under kRepeat.
        assertRgbaApprox(pixels, 16, 16, 76, 76, 76, 255,
            tag = "Compose(gray, Blur(kRepeat)) centre", tol = 3,
        )
        assertRgbaApprox(pixels, 0, 0, 76, 76, 76, 255,
            tag = "Compose(gray, Blur(kRepeat)) corner", tol = 3,
        )
        assertRgbaApprox(pixels, W - 1, H - 1, 76, 76, 76, 255,
            tag = "Compose(gray, Blur(kRepeat)) far corner", tol = 3,
        )
    }

    @Test
    fun `saveLayer with Offset imageFilter shifts layer by dx dy`() {
        // Phase G-saveLayer-imageFilter-offset -- the routed path. The
        // composite shader's existing `dstOriginSize.xy` slot already
        // drives the parent-pixel mapping ; we just shift the dst
        // origin by the integer-rounded (dx, dy) and let the scissor
        // cull the off-by-N edge. No new pipeline or shader.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // The layer covers the whole device. Inside it we draw
                // a 4x4 red rect at (10, 10)..(14, 14). With the
                // Offset filter set to (8, 8) the composite places the
                // layer pixels at (originX + 8, originY + 8) so the
                // red rect lands at (18, 18)..(22, 22).
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 14f, 14f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Red rect at the shifted position (18, 18)..(22, 22).
        assertRgbaApprox(pixels, 19, 19, 255, 0, 0, 255, tag = "Offset shifted rect centre")
        assertRgbaApprox(pixels, 21, 21, 255, 0, 0, 255, tag = "Offset shifted rect inside")
        // Where the rect *would* have been drawn at (10, 10)..(14, 14)
        // it now shows the white background (the layer is transparent
        // there before the Offset shift).
        assertRgbaApprox(pixels, 11, 11, 255, 255, 255, 255, tag = "Offset original pos cleared")
        // The far corner is untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside Offset reach")
    }

    @Test
    fun `saveLayer with Offset imageFilter input nonNull throws clear error`() {
        // Phase G-saveLayer-imageFilter-offset -- the non-null child
        // case is deferred to a follow-up slice that handles
        // structural-transform pre-passes.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Offset(dx = 4f, dy = 4f, input = inner)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Offset with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("Offset") && msg.contains("input == null")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with MatrixTransform translate equivalent to Offset filter`() {
        // Phase G-saveLayer-imageFilter-matrixTransform -- a pure
        // translation MatrixTransform should produce the same composite
        // as SkImageFilters.Offset(dx, dy). Renders both side-by-side
        // (different canvases) and asserts the two outputs match
        // pixel-for-pixel inside the shifted rect.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val (pixelsOffset, pixelsMatrix) = context!!.use { ctx ->
            val viaOffset = SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 14f, 14f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
            val viaMatrix = SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 14f, 14f)
                // MakeAll : (sx, kx, tx, ky, sy, ty). Pure translate (8, 8).
                val translate = SkMatrix.MakeAll(1f, 0f, 8f, 0f, 1f, 8f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.MatrixTransform(
                        matrix = translate,
                        sampling = SkSamplingOptions(SkFilterMode.kNearest),
                        input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
            viaOffset to viaMatrix
        }

        // Same shifted-rect anchor as the Offset test : (18, 18).
        assertRgbaApprox(pixelsMatrix, 19, 19, 255, 0, 0, 255, tag = "MT translate centre")
        assertRgbaApprox(pixelsMatrix, 11, 11, 255, 255, 255, 255, tag = "MT translate cleared origin")
        // Strict pixel-for-pixel equivalence with the Offset path.
        for (y in 0 until H) for (x in 0 until W) {
            val i = (y * W + x) * 4
            for (c in 0..3) {
                assertEquals(
                    pixelsOffset[i + c], pixelsMatrix[i + c],
                    "Offset vs MatrixTransform(translate) at ($x, $y) channel $c",
                )
            }
        }
    }

    @Test
    fun `saveLayer with MatrixTransform scale stretches layer pixels`() {
        // Phase G-saveLayer-imageFilter-matrixTransform -- a non-uniform
        // scale ((2, 0.5)) should place a 4-px wide source rect into an
        // 8-px wide x 2-px tall mapped rect. We assert the mapped rect's
        // interior is red and the unmapped corners stay white.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Source rect : (4, 8)..(8, 12) -- 4x4 red.
                val rectBounds = SkRect.MakeLTRB(4f, 8f, 8f, 12f)
                // Scale (2, 0.5) -- mapped rect : (8, 4)..(16, 6).
                val scale = SkMatrix.MakeAll(2f, 0f, 0f, 0f, 0.5f, 0f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.MatrixTransform(
                        matrix = scale,
                        sampling = SkSamplingOptions(SkFilterMode.kLinear),
                        input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the mapped rect : (12, 5) -- expect saturated red.
        assertRgbaApprox(pixels, 12, 5, 255, 0, 0, 255, tag = "MT scale centre", tol = 2)
        // Strict interior away from the scaled-rect edges (kLinear
        // mixes a half-pixel along the edges) -- pick (11, 5) which is
        // safely 2+ texels inside the mapped (8..16, 4..6) rect.
        assertRgbaApprox(pixels, 11, 5, 255, 0, 0, 255, tag = "MT scale interior", tol = 2)
        // Original (4..8, 8..12) source rect's position is empty (the
        // matrix moved its content elsewhere) -- white background.
        assertRgbaApprox(pixels, 5, 9, 255, 255, 255, 255, tag = "MT scale source pos cleared")
        // Far corner untouched.
        assertRgbaApprox(pixels, 28, 28, 255, 255, 255, 255, tag = "MT scale far white")
    }

    @Test
    fun `saveLayer with MatrixTransform rotate 90 reorients layer`() {
        // Phase G-saveLayer-imageFilter-matrixTransform -- a 90-degree
        // rotation around the origin sends `(x, y) -> (-y, x)`. A red
        // rect at `(2, 8)..(6, 10)` (4 wide, 2 tall, centred on y = 9)
        // maps to `(-10, 2)..(-8, 6)` -- the bbox lands at x in [-10, -8]
        // which is OFF-canvas, so we shift by translating after.
        // Concretely : matrix = `translate(16, 0) * rotate(90deg)`.
        // The rotated rect lands at `(16 - 10, 2)..(16 - 8, 6)`
        // = `(6, 2)..(8, 6)` -- 2 wide, 4 tall.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(2f, 8f, 6f, 10f)
                // MakeAll(sx, kx, tx, ky, sy, ty) -- 90 deg CCW would be
                // (0, -1, tx, 1, 0, ty). To land the rotated bbox at
                // x in [6, 8], y in [2, 6] we set tx = 16, ty = 0.
                val rot = SkMatrix.MakeAll(0f, -1f, 16f, 1f, 0f, 0f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.MatrixTransform(
                        matrix = rot,
                        sampling = SkSamplingOptions(SkFilterMode.kNearest),
                        input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside the rotated rect : (7, 4) -- centre of (6..8, 2..6).
        assertRgbaApprox(pixels, 7, 4, 255, 0, 0, 255, tag = "MT rotate centre", tol = 2)
        // Original source rect position now empty -- white.
        assertRgbaApprox(pixels, 4, 9, 255, 255, 255, 255, tag = "MT rotate src pos cleared")
        // Outside the rotated rect, still white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "MT rotate far white")
    }

    @Test
    fun `saveLayer with MatrixTransform input nonNull throws clear error`() {
        // Phase G-saveLayer-imageFilter-matrixTransform -- non-null
        // child is deferred to a follow-up slice that handles
        // render-to-texture pre-passes.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val translate = SkMatrix.MakeAll(1f, 0f, 4f, 0f, 1f, 4f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.MatrixTransform(
                        matrix = translate,
                        sampling = SkSamplingOptions(SkFilterMode.kNearest),
                        input = inner,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for MatrixTransform with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("MatrixTransform") && msg.contains("input == null")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with MatrixTransform perspective throws clear error`() {
        // Phase G-saveLayer-imageFilter-matrixTransform -- the first
        // slice handles 2x3 affine only ; perspective rows (non-trivial
        // bottom row, persp0 / persp1 != 0) need a homogeneous divide
        // in the shader that is deferred.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Set persp0 = 0.01 to push the matrix into perspective
                // territory ; `hasPerspective()` returns true.
                val perspective = SkMatrix.MakeAll(
                    1f, 0f, 0f,
                    0f, 1f, 0f,
                    0.01f, 0f, 1f,
                )
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.MatrixTransform(
                        matrix = perspective,
                        sampling = SkSamplingOptions(SkFilterMode.kNearest),
                        input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for MatrixTransform perspective ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("perspective")) {
            "Error message should call out perspective ; got : $msg"
        }
    }

    @Test
    fun `asMatrixTransformImageFilter extracts matrix and sampling and input`() {
        val translate = SkMatrix.MakeAll(2f, 0f, 3f, 0f, 0.5f, 4f)
        val mt = SkImageFilters.MatrixTransform(
            matrix = translate,
            sampling = SkSamplingOptions(SkFilterMode.kLinear),
            input = null,
        )
        val params = mt!!.asMatrixTransformImageFilter()
        assertTrue(params != null) { "MatrixTransform must extract" }
        assertEquals(2f, params!!.matrix.sx, "sx")
        assertEquals(0f, params.matrix.kx, "kx")
        assertEquals(3f, params.matrix.tx, "tx")
        assertEquals(0f, params.matrix.ky, "ky")
        assertEquals(0.5f, params.matrix.sy, "sy")
        assertEquals(4f, params.matrix.ty, "ty")
        assertEquals(SkFilterMode.kLinear, params.sampling.filter, "sampling")
        assertEquals(null, params.input, "input")
        // Other filter variants must not extract.
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )
        assertEquals(null, blur?.asMatrixTransformImageFilter()) {
            "Blur must not extract as MatrixTransform"
        }
    }

    @Test
    fun `saveLayer with DropShadow imageFilter draws shadow under layer`() {
        // Phase G-saveLayer-imageFilter-dropshadow -- the load-bearing
        // test. A green rect drawn inside a layer with a black drop
        // shadow set to (5, 7) and small sigma. Properties to verify :
        //   - the green rect's interior pixels stay green (the original
        //     layer content lands ON TOP of the shadow),
        //   - a few px right + down of the rect, the white background
        //     is darkened by the shadow,
        //   - far from the rect + shadow extent, white is untouched.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 16f, 16f)
                // Opaque green : ARGB (255, 0, 255, 0).
                val green = (255 shl 24) or (0 shl 16) or (255 shl 8) or 0
                // Opaque black : ARGB (255, 0, 0, 0).
                val black = (255 shl 24)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.DropShadow(
                        dx = 5f, dy = 7f,
                        sigmaX = 1.5f, sigmaY = 1.5f,
                        color = black, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = green })
                canvas.restore()
                device.flush()
            }
        }

        // The green rect is at (8..16, 8..16). Its centre should stay
        // pure green -- the shadow underneath is fully covered.
        assertRgbaApprox(pixels, 12, 12, 0, 255, 0, 255,
            tag = "DropShadow green centre", tol = 2)

        // A pixel in the shadow's strongest region : the rect's
        // centre + (5, 7) = (17, 19) lands inside the shadow's
        // densest area, just outside the rect. With small sigma the
        // shadow alpha there is close to the rect's silhouette
        // alpha (= 1) -> nearly black.
        val i = (19 * W + 17) * 4
        val r = pixels[i].toInt() and 0xFF
        val g = pixels[i + 1].toInt() and 0xFF
        val b = pixels[i + 2].toInt() and 0xFF
        assertTrue(r < 80 && g < 80 && b < 80) {
            "Shadow centre pixel should be near-black ; got ($r, $g, $b)"
        }

        // Far corner -- outside both the rect and the shadow's
        // kernel reach. White background untouched.
        assertRgbaApprox(pixels, 28, 2, 255, 255, 255, 255, tag = "DropShadow far white")
    }

    @Test
    fun `saveLayer with DropShadow imageFilter zero sigma is a colorize-offset copy`() {
        // Phase G-saveLayer-imageFilter-dropshadow -- sigma == 0 on
        // both axes : the blur kernel collapses to the centre tap (1.0
        // weight). The shadow is a colorized, offset copy of the layer
        // silhouette (alpha mask tinted to the shadow colour). For a
        // fully-opaque red rect with a fully-opaque blue shadow the
        // result is :
        //   - the red rect at its original position,
        //   - a blue rectangle at (originalRect + (dx, dy)) under it.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 14f, 14f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.DropShadow(
                        dx = 6f, dy = 6f,
                        sigmaX = 0f, sigmaY = 0f,
                        color = SK_ColorBLUE, input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Original red rect at (8..14, 8..14) -- pure red because
        // the layer's content lands on top of the shadow with srcOver
        // and the rect has alpha = 255.
        assertRgbaApprox(pixels, 10, 10, 255, 0, 0, 255, tag = "DropShadow original red")
        // Shadow at (8+6 .. 14+6, 8+6 .. 14+6) = (14..20, 14..20) --
        // pure blue. The shadow comes BEFORE the original layer
        // content, so the original rect doesn't overlap this region.
        assertRgbaApprox(pixels, 16, 16, 0, 0, 255, 255, tag = "DropShadow shadow blue")
        // Far untouched background.
        assertRgbaApprox(pixels, 28, 28, 255, 255, 255, 255, tag = "DropShadow far white")
    }

    @Test
    fun `saveLayer with DropShadow imageFilter input nonNull throws clear error`() {
        // Phase G-saveLayer-imageFilter-dropshadow -- non-null child
        // is deferred to a follow-up slice.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.DropShadow(
                        dx = 2f, dy = 2f,
                        sigmaX = 1f, sigmaY = 1f,
                        color = (255 shl 24), input = inner,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for DropShadow with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("DropShadow") && msg.contains("input == null")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `asOffsetImageFilter extracts dx dy and input from Offset`() {
        val off = SkImageFilters.Offset(dx = 3.5f, dy = -2.25f, input = null)
        val params = off.asOffsetImageFilter()
        assertTrue(params != null) { "Offset must extract" }
        assertEquals(3.5f, params!!.dx, "dx")
        assertEquals(-2.25f, params.dy, "dy")
        assertEquals(null, params.input, "input")
    }

    @Test
    fun `asOffsetImageFilter returns null for non-Offset filter`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )
        assertEquals(null, blur?.asOffsetImageFilter()) {
            "Blur must not extract as Offset"
        }
    }

    @Test
    fun `asDropShadowImageFilter extracts params from DropShadow`() {
        val shadowColor = (200 shl 24) or (10 shl 16) or (20 shl 8) or 30
        val ds = SkImageFilters.DropShadow(
            dx = 4f, dy = 6f,
            sigmaX = 2f, sigmaY = 3f,
            color = shadowColor, input = null,
        )
        val params = ds.asDropShadowImageFilter()
        assertTrue(params != null) { "DropShadow must extract" }
        assertEquals(4f, params!!.dx, "dx")
        assertEquals(6f, params.dy, "dy")
        assertEquals(2f, params.sigmaX, "sigmaX")
        assertEquals(3f, params.sigmaY, "sigmaY")
        assertEquals(shadowColor, params.color, "color")
        assertEquals(null, params.input, "input")
    }

    @Test
    fun `asDropShadowImageFilter returns null for non-DropShadow filter`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )
        assertEquals(null, blur?.asDropShadowImageFilter()) {
            "Blur must not extract as DropShadow"
        }
        val offset = SkImageFilters.Offset(dx = 1f, dy = 0f, input = null)
        assertEquals(null, offset.asDropShadowImageFilter()) {
            "Offset must not extract as DropShadow"
        }
    }

    @Test
    fun `saveLayer with ColorFilter wrap and paint colorFilter both set throws clear error`() {
        // Phase G-saveLayer-imageFilter -- the composite shader's
        // colour-filter uniform is single-occupancy. Setting both
        // paint.colorFilter and paint.imageFilter = ColorFilter wrap
        // would need a Compose extractor that the scaffolding doesn't
        // ship -- surface the conflict early.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Matrix(identity)
                    imageFilter = SkImageFilters.ColorFilter(
                        SkColorFilters.Matrix(identity), input = null,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for double colour filter ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("single-occupancy") || msg.contains("both")) {
            "Error message should explain the conflict ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with ColorFilter wrap input nonNull throws clear error`() {
        // Phase G-saveLayer-imageFilter -- the ColorFilter wrap's
        // `input` child means a structural transform runs upstream of
        // the per-pixel colour transform. The scaffolding pipeline
        // can't render-to-texture pre-pass yet -- surface the conflict.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val inner = SkImageFilters.Offset(dx = 0f, dy = 0f, input = null)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.ColorFilter(
                        SkColorFilters.Matrix(identity),
                        input = inner,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }

        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for ColorFilter wrap with non-null child ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("input == null") || msg.contains("non-null child")) {
            "Error message should call out the non-null child input ; got : $msg"
        }
    }

    @Test
    fun `asColorFilterImageFilter returns null for non-ColorFilter wrap`() {
        // Unit test of the extractor : Blur and Offset filters must
        // return null, not silently misinterpret as a ColorFilter wrap.
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )
        assertEquals(null, blur?.asColorFilterImageFilter()) {
            "Blur must not extract as ColorFilter wrap"
        }
        val offset = SkImageFilters.Offset(dx = 1f, dy = 0f, input = null)
        assertEquals(null, offset.asColorFilterImageFilter()) {
            "Offset must not extract as ColorFilter wrap"
        }
    }

    @Test
    fun `asBlurImageFilter returns null for non-Blur wrap`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(identity)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        assertEquals(null, wrap.asBlurImageFilter()) {
            "ColorFilter wrap must not extract as Blur"
        }
        val offset = SkImageFilters.Offset(dx = 1f, dy = 0f, input = null)
        assertEquals(null, offset.asBlurImageFilter()) {
            "Offset must not extract as Blur"
        }
    }

    @Test
    fun `asBlurImageFilter extracts sigmas and tileMode from Blur`() {
        val blur = SkImageFilters.Blur(
            sigmaX = 2.5f, sigmaY = 1.25f, tileMode = SkTileMode.kClamp, input = null,
        )!!
        val params = blur.asBlurImageFilter()
        assertTrue(params != null) { "Blur must extract" }
        assertEquals(2.5f, params!!.sigmaX, "sigmaX")
        assertEquals(1.25f, params.sigmaY, "sigmaY")
        assertEquals(SkTileMode.kClamp, params.tileMode, "tileMode")
        assertEquals(null, params.input, "input")
    }

    @Test
    fun `asColorFilterImageFilter extracts colorFilter and input from ColorFilter wrap`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(identity)
        val wrap = SkImageFilters.ColorFilter(cf, input = null)
        val params = wrap.asColorFilterImageFilter()
        assertTrue(params != null) { "ColorFilter wrap must extract" }
        assertEquals(cf, params!!.colorFilter, "inner colorFilter identity")
        assertEquals(null, params.input, "input must be null")
    }

    // ─── Phase G-saveLayer-imageFilter-compose ─────────────────────────

    @Test
    fun `saveLayer with Compose(ColorFilter grayscale, Blur) blurs first then grays`() {
        // Phase G-saveLayer-imageFilter-compose -- the headline case :
        // Compose(outer = ColorFilter(grayscale), inner = Blur(sigma)).
        // SkImageFilters.Compose(outer, inner) means "apply inner first,
        // then outer", so the layer texture is blurred first (still red,
        // softened at the edges) and then the colour filter desaturates
        // to grayscale. The composite shader already runs the colour
        // filter on the already-blurred pixels (the existing blur
        // pipeline's pass 3), so this case folds onto the existing 3-
        // pass blur path with the outer ColorFilter routed as the
        // composite-pass colour filter slot.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                val blur = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(grayscale, input = null),
                        inner = blur,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the rect : still gray (luma of red = 0.299 * 255 = 76).
        // The blur centre tap collapses on the source colour, then the
        // grayscale matrix converts that red to luma (76, 76, 76, 255).
        assertRgbaApprox(
            pixels, 16, 16,
            76, 76, 76, 255,
            tag = "Compose(grayscale, blur) centre", tol = 2,
        )
        // Far surround : untouched white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "Compose far surround")
        // Just-inside-edge : a softened gray-ish (blur partially fades
        // the edge before the grayscale converts). The R/G/B channels
        // are all equal after grayscale, so we check they match within
        // tolerance.
        val j = (16 * W + 20) * 4
        val r2 = pixels[j].toInt() and 0xFF
        val g2 = pixels[j + 1].toInt() and 0xFF
        val b2 = pixels[j + 2].toInt() and 0xFF
        assertTrue(abs(r2 - g2) <= 1 && abs(g2 - b2) <= 1) {
            "After grayscale, R/G/B must match : got ($r2, $g2, $b2)"
        }
    }

    @Test
    fun `saveLayer with Compose(Blur, ColorFilter grayscale) grays first then blurs`() {
        // Phase G-saveLayer-imageFilter-compose -- the pre-blur CF case.
        // Compose(outer = Blur, inner = ColorFilter(grayscale)) : the
        // colour filter runs first (red layer -> gray layer), then the
        // blur softens the gray. The dispatch encodes a 4-pass pipeline
        // (preCF, H, V, composite) with the ColorFilter packed into the
        // pre-blur scratch's uniform.
        //
        // Order-comparison with the previous test : the centre pixel
        // must be identical (gray of (76, 76, 76, 255)) because the
        // blur centre tap and grayscale matrix commute when the kernel
        // fully lands on the source colour. Edge pixels differ
        // numerically -- here grayscale-then-blur fades pre-grayscale
        // gray with transparent surround ; the previous case faded
        // pre-blur red and then desaturated -- but in both cases the
        // R/G/B channels stay equal so the gray property holds.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                val blur = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = blur,
                        inner = SkImageFilters.ColorFilter(grayscale, input = null),
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre : still (76, 76, 76, 255). Both filter orderings give
        // the same centre because the kernel collapses to the source
        // colour and the grayscale matrix is linear.
        assertRgbaApprox(
            pixels, 16, 16,
            76, 76, 76, 255,
            tag = "Compose(blur, grayscale) centre", tol = 2,
        )
        // Far surround : white.
        assertRgbaApprox(
            pixels, 2, 2, 255, 255, 255, 255,
            tag = "Compose far surround",
        )
        // R/G/B equality holds for all pixels (post-grayscale property
        // preserved by linear blur).
        for ((x, y) in listOf(20 to 16, 22 to 16, 16 to 20, 16 to 22)) {
            val j = (y * W + x) * 4
            val rr = pixels[j].toInt() and 0xFF
            val gg = pixels[j + 1].toInt() and 0xFF
            val bb = pixels[j + 2].toInt() and 0xFF
            assertTrue(abs(rr - gg) <= 1 && abs(gg - bb) <= 1) {
                "Grayscale preserved through linear blur at ($x, $y) : " +
                    "($rr, $gg, $bb)"
            }
        }
    }

    @Test
    fun `saveLayer with Compose(ColorFilter, ColorFilter) folds chain when only one CF`() {
        // Phase G-saveLayer-imageFilter-compose -- pure colour-filter
        // Compose with no Blur. Two CFs in the same stage are rejected
        // by the single-occupancy rule (the composite shader's
        // colorFilter uniform holds one CF only). This test exercises
        // the throw branch so the call site gets a clear error rather
        // than a silently dropped filter.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val cf1 = SkColorFilters.Matrix(identity)
                val cf2 = SkColorFilters.Matrix(identity)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(cf1, input = null),
                        inner = SkImageFilters.ColorFilter(cf2, input = null),
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }
        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Compose(CF, CF) ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("single-occupancy") || msg.contains("ColorFilter")) {
            "Error message should explain the conflict ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with Compose(Compose, X) left-associative chain walks correctly`() {
        // Phase G-saveLayer-imageFilter-compose -- nested Compose. The
        // tree :
        //
        //   Compose(outer = Compose(outer = grayscale, inner = Blur),
        //           inner = identity-matrix)
        //
        // resolves as :
        //   1. identity-matrix on the source (no-op)
        //   2. Blur (the inner of the outer Compose)
        //   3. grayscale (the outer of the outer Compose)
        //
        // i.e. identical end result to the non-nested
        // Compose(grayscale, Blur) case. The walk's job is to flatten
        // the tree correctly.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                val identityCF = SkColorFilters.Matrix(identity)
                val blur = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val grayPlusBlur = SkImageFilters.Compose(
                    outer = SkImageFilters.ColorFilter(grayscale, input = null),
                    inner = blur,
                )
                val nested = SkImageFilters.Compose(
                    outer = grayPlusBlur,
                    inner = SkImageFilters.ColorFilter(identityCF, input = null),
                )
                val layerPaint = SkPaint().apply { imageFilter = nested }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre : gray (76, 76, 76, 255). Same as the non-nested
        // Compose(grayscale, Blur) case.
        assertRgbaApprox(
            pixels, 16, 16,
            76, 76, 76, 255,
            tag = "nested Compose centre", tol = 2,
        )
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "nested Compose far surround")
    }

    @Test
    fun `saveLayer with Compose(Offset, Blur) shifts the blurred result`() {
        // K1 GPU follow-up to PR #605 -- Offset leaves inside a Compose
        // chain collapse onto a dst-origin shift on the final composite.
        // Both Blur and ColorFilter are translation-invariant, so
        // Compose(Offset(dx, dy), Blur) is bit-iso with Blur shifted by
        // (dx, dy). We render a red rect at (10..22, 10..22) with
        // sigma = 0 (degenerate identity Gaussian, kept here for the
        // dispatch path) and dx = dy = 8 ; the composite must land at
        // (18..30, 18..30) with the rest of the layer transparent.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val blur = SkImageFilters.Blur(
                    sigmaX = 1f, sigmaY = 1f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null),
                        inner = blur,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Original rect lives at (10..22, 10..22) ; after Offset(8, 8)
        // it must land at (18..30, 18..30). With sigma = 1 the centre
        // tap is essentially red away from edges.
        assertRgbaApprox(
            pixels, 22, 22,
            255, 0, 0, 255,
            tag = "Compose(Offset, Blur) shifted centre", tol = 3,
        )
        // Old rect position (12, 12) must now show the background --
        // the layer's contribution shifted away by 8 pixels.
        assertRgbaApprox(
            pixels, 5, 5,
            255, 255, 255, 255,
            tag = "Compose(Offset, Blur) pre-shift position is now background", tol = 1,
        )
    }

    @Test
    fun `saveLayer with Compose(Blur, Offset) shifts the blurred result`() {
        // K1 GPU follow-up to PR #605 -- mirror of the previous test
        // with the Offset on the inner side. The Blur kernel is
        // translation-invariant, so Compose(Blur, Offset) and
        // Compose(Offset, Blur) must produce the same shifted output.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val blur = SkImageFilters.Blur(
                    sigmaX = 1f, sigmaY = 1f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = blur,
                        inner = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null),
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        assertRgbaApprox(
            pixels, 22, 22,
            255, 0, 0, 255,
            tag = "Compose(Blur, Offset) shifted centre", tol = 3,
        )
    }

    @Test
    fun `saveLayer with Compose(Offset, ColorFilter) shifts the color-filtered result`() {
        // K1 GPU follow-up to PR #605 -- the no-blur path with a
        // ColorFilter on the inner side and an Offset on the outer.
        // ColorFilter is point-wise (no neighbour samples) so this
        // collapses to the plain LayerCompositeDraw path with the
        // dst origin shifted by (dx, dy) and the colour filter
        // packed into the composite uniform.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.Offset(dx = 8f, dy = 8f, input = null),
                        inner = SkImageFilters.ColorFilter(grayscale, input = null),
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // After grayscale, red -> luma (76, 76, 76). Position shifted
        // by (8, 8) so the gray rect now centred at (20, 20).
        assertRgbaApprox(
            pixels, 20, 20,
            76, 76, 76, 255,
            tag = "Compose(Offset, ColorFilter) shifted centre", tol = 2,
        )
        assertRgbaApprox(
            pixels, 12, 12,
            255, 255, 255, 255,
            tag = "Compose(Offset, ColorFilter) pre-shift is background", tol = 1,
        )
    }

    @Test
    fun `saveLayer with Compose(Blur, DropShadow) materialises DropShadow into scratch then blurs`() {
        // L1a -- DropShadow as the innermost leaf inside a Compose tree.
        // Walker order : inner first -> DropShadow encountered first ->
        // materialise (shadow + original) into a layer-sized scratch as a
        // pre-pass ; then the outer Blur runs against the scratch as if
        // it were the raw layer source.
        //
        // Expected pixels :
        //  - The blurred DropShadow result (red rect + offset black shadow,
        //    softened by the outer 1.5-sigma blur).
        //  - Centre of the rect : near-red (the blur softens edges but
        //    the centre is unaffected when the rect is bigger than the
        //    kernel reach).
        //  - Pixel offset by (dx, dy) outside the rect : a soft-edged
        //    shadow tinted black.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 16f, 16f)
                val red = (255 shl 24) or (255 shl 16)
                val black = 255 shl 24
                val blur = SkImageFilters.Blur(
                    sigmaX = 1.5f, sigmaY = 1.5f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val ds = SkImageFilters.DropShadow(
                    dx = 5f, dy = 7f,
                    sigmaX = 1f, sigmaY = 1f,
                    color = black, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = blur, inner = ds)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = red })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the rect (12, 12) : red rectangle from the original
        // pass, softened slightly by the outer 1.5-sigma blur. The rect
        // is 8x8 so the centre stays close to red but tolerates blur
        // attenuation from the kClamp neighbour samples.
        val i = (12 * W + 12) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 150 && gC < 100 && bC < 100) {
            "Centre of rect should be predominantly red ; got ($rC, $gC, $bC)"
        }
        // Pixel inside the shadow's offset footprint, OUTSIDE the rect :
        // (17, 19) lands at the rect centre + (5, 7). With small sigma
        // the shadow alpha there is high, so the pixel should be darker
        // than the white background (and not red, since the original
        // doesn't reach this position).
        val j = (19 * W + 17) * 4
        val rS = pixels[j].toInt() and 0xFF
        val gS = pixels[j + 1].toInt() and 0xFF
        val bS = pixels[j + 2].toInt() and 0xFF
        assertTrue(rS < 200 && gS < 200 && bS < 200) {
            "Shadow region pixel should be darkened ; got ($rS, $gS, $bS)"
        }
        // Far corner -- outside both rect and shadow extent. White
        // background untouched (small tolerance for the blur kernel's
        // negligible tail).
        assertRgbaApprox(pixels, 28, 2, 255, 255, 255, 255,
            tag = "Compose(Blur, DropShadow) far white", tol = 2)
    }

    @Test
    fun `saveLayer with Compose(ColorFilter, DropShadow) grayscales the materialised shadow`() {
        // L1a -- DropShadow as innermost leaf, ColorFilter as outer.
        // Walker order : inner first -> DropShadow materialises into
        // scratch ; then the outer ColorFilter (grayscale matrix) runs on
        // the scratch via the standard LayerCompositeDraw path.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 14f, 14f)
                val red = (255 shl 24) or (255 shl 16)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                val ds = SkImageFilters.DropShadow(
                    dx = 6f, dy = 6f,
                    sigmaX = 0f, sigmaY = 0f,
                    color = (255 shl 24) or 255, // opaque blue
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(grayscale, input = null),
                        inner = ds,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = red })
                canvas.restore()
                device.flush()
            }
        }

        // Original red rect at (8..14, 8..14) -> grayscale luma of red =
        // 76. Tol of 5 to account for the materialised sub-passes and the
        // 6-vec colour-filter pack rounding.
        assertRgbaApprox(pixels, 10, 10, 76, 76, 76, 255,
            tag = "Compose(CF, DropShadow) grayscale red", tol = 5)
        // Shadow at (14..20, 14..20) was blue (0, 0, 255). Grayscale luma
        // of blue = 29. Tol of 5.
        assertRgbaApprox(pixels, 16, 16, 29, 29, 29, 255,
            tag = "Compose(CF, DropShadow) grayscale blue shadow", tol = 5)
        // Far untouched background -- grayscale of white is still white.
        assertRgbaApprox(pixels, 28, 28, 255, 255, 255, 255,
            tag = "Compose(CF, DropShadow) far white", tol = 1)
    }

    @Test
    fun `saveLayer with Compose(DropShadow, Blur) chains BlurCF materialise then DS materialise`() {
        // N1 -- chain-of-passes : the inverse Compose layout (DropShadow
        // on the OUTER side, Blur on the INNER side) walks Blur first.
        // Pipeline :
        //  1. Stage 1 : Blur becomes the prefix of the DS pre-stage.
        //     -> materialise BlurCF (blur over raw layer) into scratch1
        //     -> materialise DS (shadow + original over scratch1) into scratch2
        //  2. Final composite reads scratch2 and blends onto parent.
        // Visual check : we should see a blurred red rect with a soft
        // shadow offset by (dx, dy).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 16f, 16f)
                val blur = SkImageFilters.Blur(
                    sigmaX = 1f, sigmaY = 1f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val ds = SkImageFilters.DropShadow(
                    dx = 6f, dy = 6f,
                    sigmaX = 1f, sigmaY = 1f,
                    color = SK_ColorBLACK,
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = ds, inner = blur)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the rect : (12, 12) -- the original (blurred) red
        // sits there ; the shadow is offset by (+6, +6) and lands BEHIND
        // the original (so the original colour dominates at the centre).
        // The blur slightly softens the edge ; the centre is still
        // dominantly red.
        val i = (12 * W + 12) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 180 && gC < 100 && bC < 100) {
            "Compose(DS, Blur) centre should be dominantly red ; got ($rC, $gC, $bC)"
        }
        // Shadow region : a pixel at (20, 20) -- inside the shadow's
        // offset rect (8+6=14..16+6=22 -- so (20, 20) is in the shadow
        // band, OUTSIDE the original rect 8..16). The shadow is black
        // tinted with some blur softening ; this position should NOT be
        // pure white (the shadow contributes there).
        val j = (20 * W + 20) * 4
        val rS = pixels[j].toInt() and 0xFF
        val gS = pixels[j + 1].toInt() and 0xFF
        val bS = pixels[j + 2].toInt() and 0xFF
        assertTrue(rS < 240 && gS < 240 && bS < 240) {
            "Compose(DS, Blur) shadow band at (20, 20) should be darker than white ; got ($rS, $gS, $bS)"
        }
        // Far corner stays at the white background (well beyond the
        // shadow + blur kernel reach).
        assertRgbaApprox(pixels, 0, 0, 255, 255, 255, 255,
            tag = "Compose(DS, Blur) far white", tol = 2)
    }

    @Test
    fun `saveLayer with Compose(Blur, MatrixTransform) materialises MT into scratch then blurs`() {
        // L1b -- MatrixTransform as the innermost leaf inside a Compose
        // tree. Walker order : inner first -> MatrixTransform encountered
        // first -> materialise the matrix-transformed image into a layer-
        // sized scratch as a pre-pass ; then the outer Blur runs against
        // the scratch as if it were the raw layer source.
        //
        // We use a pure translation MatrixTransform (dx = 8, dy = 8) :
        // that's structurally equivalent in semantic to Compose(Blur,
        // Offset) -- the kernel reads a shifted source. We assert the
        // qualitative output : centre of the shifted rect is dominantly
        // red, pre-shift position is background, soft edges along the
        // shifted rect.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 14f, 14f)
                val blur = SkImageFilters.Blur(
                    sigmaX = 1.5f, sigmaY = 1.5f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val translate = SkMatrix.MakeAll(1f, 0f, 8f, 0f, 1f, 8f)
                val mt = SkImageFilters.MatrixTransform(
                    matrix = translate,
                    sampling = SkSamplingOptions(SkFilterMode.kNearest),
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = blur, inner = mt)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the shifted-blurred rect (mapped centre = (11+8, 11+8)
        // = (19, 19)) : dominantly red, with the green / blue channels
        // small but possibly non-zero from kClamp blur neighbour samples.
        val i = (19 * W + 19) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 200 && gC < 120 && bC < 120) {
            "Compose(Blur, MT-translate) shifted centre should be dominantly red ; got ($rC, $gC, $bC)"
        }
        // The pre-shift position (where the source rect would have been
        // before the MatrixTransform) is now empty background (white).
        // The blur kernel may extend slightly into this region from the
        // shifted rect's left edge, so allow a tolerance.
        assertRgbaApprox(pixels, 9, 9, 255, 255, 255, 255,
            tag = "Compose(Blur, MT-translate) pre-shift cleared", tol = 5)
        // Far corner stays at the white background (the kernel tail at
        // 1.5 sigma is negligible 10+ px away from the shifted rect).
        assertRgbaApprox(pixels, 28, 2, 255, 255, 255, 255,
            tag = "Compose(Blur, MT-translate) far white", tol = 2)
        // Soft edge : a pixel a couple of px OUTSIDE the shifted rect
        // should be partially blurred -- not pure white. The shifted
        // rect lives at (16..22, 16..22) ; pick (24, 19) which is 2 px
        // right of the right edge in y = 19's row. The 1.5-sigma kernel
        // tail reaches there with non-trivial weight, so the pixel
        // should carry some red contribution (green / blue drop below
        // 255 from the partial coverage).
        val j = (19 * W + 24) * 4
        val rE = pixels[j].toInt() and 0xFF
        val gE = pixels[j + 1].toInt() and 0xFF
        assertTrue(rE >= 200 && gE < 250) {
            "Compose(Blur, MT-translate) soft edge should be partially red ; got ($rE, $gE)"
        }
    }

    @Test
    fun `saveLayer with Compose(ColorFilter, MatrixTransform) grayscales the transformed pixels`() {
        // L1b -- MatrixTransform as innermost leaf, ColorFilter as outer.
        // Walker order : inner first -> MatrixTransform materialises into
        // scratch ; then the outer ColorFilter (luma grayscale matrix)
        // runs on the scratch via the standard LayerCompositeDraw path.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Source red rect at (4, 8)..(8, 12) -- 4x4 wide.
                val rectBounds = SkRect.MakeLTRB(4f, 8f, 8f, 12f)
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val grayscale = SkColorFilters.Matrix(luma)
                // Pure translation matrix : shift (8, 4) -- mapped rect
                // lands at (12, 12)..(16, 16).
                val translate = SkMatrix.MakeAll(1f, 0f, 8f, 0f, 1f, 4f)
                val mt = SkImageFilters.MatrixTransform(
                    matrix = translate,
                    sampling = SkSamplingOptions(SkFilterMode.kNearest),
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(grayscale, input = null),
                        inner = mt,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Centre of the shifted rect : (14, 14). Source is red ; after
        // luma grayscale R contribution = 76. Tol of 5 to account for the
        // materialised sub-pass + colour-filter pack rounding.
        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255,
            tag = "Compose(CF, MT-translate) grayscale red", tol = 5)
        // Pre-shift position now empty.
        assertRgbaApprox(pixels, 5, 9, 255, 255, 255, 255,
            tag = "Compose(CF, MT-translate) pre-shift cleared", tol = 1)
        // Far corner -- grayscale of white is still white.
        assertRgbaApprox(pixels, 28, 28, 255, 255, 255, 255,
            tag = "Compose(CF, MT-translate) far white", tol = 1)
    }

    @Test
    fun `saveLayer with Compose(MatrixTransform, Blur) chains BlurCF materialise then MT materialise`() {
        // N1 -- chain-of-passes : the inverse Compose layout
        // (MatrixTransform on the OUTER side, Blur on the INNER side)
        // walks Blur first. Pipeline :
        //  1. Stage 1 : Blur becomes the prefix of the MT pre-stage.
        //     -> materialise BlurCF (blur over raw layer) into scratch1
        //     -> materialise MT (translate) over scratch1 into scratch2
        //  2. Final composite reads scratch2 and blends onto parent.
        // Visual semantics : the source is blurred FIRST, then the
        // blurred result is translated.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(4f, 4f, 10f, 10f)
                val blur = SkImageFilters.Blur(
                    sigmaX = 1f, sigmaY = 1f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val translate = SkMatrix.MakeAll(1f, 0f, 12f, 0f, 1f, 12f)
                val mt = SkImageFilters.MatrixTransform(
                    matrix = translate,
                    sampling = SkSamplingOptions(SkFilterMode.kNearest),
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = mt, inner = blur)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Source rect lives at (4..10, 4..10). After Blur, it occupies a
        // slightly larger soft region. After MT-translate(+12, +12), the
        // blurred rect lands at roughly (16..22, 16..22). Centre = (19, 19).
        val i = (19 * W + 19) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 180 && gC < 130 && bC < 130) {
            "Compose(MT, Blur) translated centre should be dominantly red ; got ($rC, $gC, $bC)"
        }
        // The pre-translation position should be empty (white) -- the
        // blurred rect has been moved to the new position.
        assertRgbaApprox(pixels, 5, 5, 255, 255, 255, 255,
            tag = "Compose(MT, Blur) pre-translate cleared", tol = 5)
        // Far corner stays at the white background.
        assertRgbaApprox(pixels, 0, 28, 255, 255, 255, 255,
            tag = "Compose(MT, Blur) far white", tol = 2)
    }

    @Test
    fun `saveLayer with Compose(MatrixTransform, DropShadow) chains DS materialise then MT materialise`() {
        // N1 -- chain-of-passes : a DropShadow on the INNER side and a
        // MatrixTransform on the OUTER side. Walker order :
        //  - inner DS first -> first materialise stage (no BlurCF prefix,
        //    DS reads the raw layer).
        //  - outer MT -> second materialise stage (no BlurCF prefix,
        //    MT reads the DS scratch).
        // Final composite reads the MT scratch and blends onto parent.
        // Visual semantics : the shadow + original are computed first,
        // then the whole (shadow + original) composite is translated.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Source red rect at (4, 4)..(10, 10).
                val rectBounds = SkRect.MakeLTRB(4f, 4f, 10f, 10f)
                val ds = SkImageFilters.DropShadow(
                    dx = 3f, dy = 3f,
                    sigmaX = 0.5f, sigmaY = 0.5f,
                    color = SK_ColorBLACK, input = null,
                )!!
                // Pure translation (+12, +12).
                val translate = SkMatrix.MakeAll(1f, 0f, 12f, 0f, 1f, 12f)
                val mt = SkImageFilters.MatrixTransform(
                    matrix = translate,
                    sampling = SkSamplingOptions(SkFilterMode.kNearest),
                    input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = mt, inner = ds)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // After DS : red rect at (4..10, 4..10) + shadow at (7..13, 7..13).
        // After MT-translate(+12, +12) : red lands at (16..22, 16..22),
        // shadow at (19..25, 19..25). Centre of translated red = (19, 19).
        val i = (19 * W + 19) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 180 && gC < 100 && bC < 100) {
            "Compose(MT, DS) translated centre should be dominantly red ; got ($rC, $gC, $bC)"
        }
        // Translated shadow band : pixel at (24, 24) -- inside the
        // shadow's translated rect (19..25) but OUTSIDE the translated
        // red rect (16..22). Should be darker than white (shadow tint).
        val j = (24 * W + 24) * 4
        val rS = pixels[j].toInt() and 0xFF
        val gS = pixels[j + 1].toInt() and 0xFF
        val bS = pixels[j + 2].toInt() and 0xFF
        assertTrue(rS < 240 && gS < 240 && bS < 240) {
            "Compose(MT, DS) translated shadow at (24, 24) should be darker ; got ($rS, $gS, $bS)"
        }
        // Pre-translation original position should be empty.
        assertRgbaApprox(pixels, 5, 5, 255, 255, 255, 255,
            tag = "Compose(MT, DS) pre-translate cleared", tol = 5)
    }

    @Test
    fun `saveLayer with Compose(DropShadow, DropShadow) chains two DS materialise stages`() {
        // N1 -- chain-of-passes : two DropShadows in the same Compose
        // chain. Walker order :
        //  - inner DS first -> first materialise stage (reads raw layer).
        //  - outer DS -> second materialise stage (reads first DS's
        //    scratch).
        // Final composite reads the second DS's scratch.
        // Visual semantics : the inner DS adds a shadow to the source ;
        // the outer DS adds ANOTHER shadow to the (source + first
        // shadow) composite.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(8f, 8f, 14f, 14f)
                val ds1 = SkImageFilters.DropShadow(
                    dx = 3f, dy = 0f,
                    sigmaX = 0.5f, sigmaY = 0.5f,
                    color = SK_ColorBLACK, input = null,
                )!!
                val ds2 = SkImageFilters.DropShadow(
                    dx = 0f, dy = 3f,
                    sigmaX = 0.5f, sigmaY = 0.5f,
                    color = SK_ColorBLACK, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = ds2, inner = ds1)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Original red rect at (8..14, 8..14) -- centre (10, 10) stays
        // red after both DS passes (the shadows land BEHIND the original).
        val i = (10 * W + 10) * 4
        val rC = pixels[i].toInt() and 0xFF
        val gC = pixels[i + 1].toInt() and 0xFF
        val bC = pixels[i + 2].toInt() and 0xFF
        assertTrue(rC > 200 && gC < 50 && bC < 50) {
            "Compose(DS, DS) centre should stay red ; got ($rC, $gC, $bC)"
        }
        // First DS shadow at (+3, 0) lands at (11..17, 8..14). The
        // pixel at (16, 10) is in that shadow region but OUTSIDE the
        // original rect -- it should be darker than white.
        val j = (10 * W + 16) * 4
        val rS = pixels[j].toInt() and 0xFF
        val gS = pixels[j + 1].toInt() and 0xFF
        val bS = pixels[j + 2].toInt() and 0xFF
        assertTrue(rS < 240 && gS < 240 && bS < 240) {
            "Compose(DS, DS) first shadow at (16, 10) should be darker ; got ($rS, $gS, $bS)"
        }
        // Second DS shadow at (0, +3) -- applied to the (original + first
        // shadow) composite, so it shadows BOTH. At (10, 16) (below
        // original) it should also be darker.
        val k = (16 * W + 10) * 4
        val rB = pixels[k].toInt() and 0xFF
        val gB = pixels[k + 1].toInt() and 0xFF
        val bB = pixels[k + 2].toInt() and 0xFF
        assertTrue(rB < 240 && gB < 240 && bB < 240) {
            "Compose(DS, DS) second shadow at (10, 16) should be darker ; got ($rB, $gB, $bB)"
        }
        // Far corner stays at the white background.
        assertRgbaApprox(pixels, 0, 0, 255, 255, 255, 255,
            tag = "Compose(DS, DS) far white", tol = 2)
    }

    @Test
    fun `saveLayer with Compose two Blurs throws clear error`() {
        // Phase G-saveLayer-imageFilter-compose -- two Blurs in a
        // chain. Folding into a single Gaussian with sigma = sqrt(s1^2
        // + s2^2) needs a follow-up slice that proves the kernel-mass
        // renormalization on kDecal ; the first cut throws.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val err = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val blur1 = SkImageFilters.Blur(
                    sigmaX = 1f, sigmaY = 1f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val blur2 = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(outer = blur1, inner = blur2)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                runCatching { canvas.restore() }.exceptionOrNull()
            }
        }
        assertTrue(err is IllegalStateException) {
            "Expected IllegalStateException for Compose(Blur, Blur) ; got $err"
        }
        val msg = err?.message ?: ""
        assertTrue(msg.contains("more than one Blur") || msg.contains("Blur")) {
            "Error message should call out the duplicate Blur ; got : $msg"
        }
    }

    @Test
    fun `saveLayer with Compose(ColorFilter, Blur) is bit-iso with the existing Blur+CF case`() {
        // Phase G-saveLayer-imageFilter-compose -- regression guard.
        // The existing path "Blur imageFilter + paint.colorFilter" runs
        // the colour filter at the composite pass after the blur ; the
        // new path "Compose(ColorFilter, Blur) imageFilter" must
        // produce identical pixels because the inner Blur runs first
        // and the outer ColorFilter routes to the same composite-pass
        // slot.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val luma = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )

        val pixelsCompose = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val gray = SkColorFilters.Matrix(luma)
                val blur = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Compose(
                        outer = SkImageFilters.ColorFilter(gray, input = null),
                        inner = blur,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }
        // Compare with : same blur + paint.colorFilter set directly.
        val pixelsRef = WebGpuContext.createOrNull()!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val rectBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val gray = SkColorFilters.Matrix(luma)
                val blur = SkImageFilters.Blur(
                    sigmaX = 2f, sigmaY = 2f,
                    tileMode = SkTileMode.kClamp, input = null,
                )!!
                val layerPaint = SkPaint().apply {
                    imageFilter = blur
                    colorFilter = gray
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(rectBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }
        // Bit-iso check at a few spot pixels.
        for ((x, y) in listOf(2 to 2, 16 to 16, 20 to 16, 22 to 22)) {
            val i = (y * W + x) * 4
            for (c in 0..3) {
                assertEquals(
                    pixelsRef[i + c], pixelsCompose[i + c],
                    "Compose(CF, Blur) vs Blur+CF at ($x, $y) channel $c",
                )
            }
        }
    }

    @Test
    fun `asComposeImageFilter extracts outer and inner from Compose`() {
        // Unit test of the extractor : Compose nodes must surface the
        // two children, leaf filters must return null (no
        // misinterpretation as a Compose).
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )!!
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cfWrap = SkImageFilters.ColorFilter(
            SkColorFilters.Matrix(identity), input = null,
        )
        val composed = SkImageFilters.Compose(outer = cfWrap, inner = blur)
        val params = composed!!.asComposeImageFilter()
        assertTrue(params != null) { "Compose must extract" }
        assertEquals(cfWrap, params!!.outer, "outer identity")
        assertEquals(blur, params.inner, "inner identity")
        // Leaves must not extract as Compose.
        assertEquals(null, blur.asComposeImageFilter()) {
            "Blur must not extract as Compose"
        }
        assertEquals(null, cfWrap.asComposeImageFilter()) {
            "ColorFilter wrap must not extract as Compose"
        }
    }

    // ─── Phase G-saveLayer-imageFilter-crop ─────────────────────────

    @Test
    fun `saveLayer with Crop kDecal imageFilter clears outside rect`() {
        // Phase G-saveLayer-imageFilter-crop -- top-level Crop with the
        // strict-crop semantic (kDecal) on a full-canvas layer. A red
        // rect drawn outside the crop rect should not contribute to
        // the final composite -- those pixels stay at the white
        // background. Inside the crop rect : the layer pixels pass
        // through (1:1).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Crop rect : (8, 8) .. (24, 24) -- a 16x16 window.
                val cropRect = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Crop(cropRect, SkTileMode.kDecal)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Draw red across the entire layer.
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }
        // Inside the crop rect : red (passthrough).
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "Crop kDecal inside")
        // Outside the crop rect : white (background untouched -- the
        // layer composite contributed transparent).
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "Crop kDecal outside top-left")
        assertRgbaApprox(pixels, 28, 28, 255, 255, 255, 255, tag = "Crop kDecal outside bot-right")
        // Just inside the crop edge : still red.
        assertRgbaApprox(pixels, 9, 9, 255, 0, 0, 255, tag = "Crop kDecal edge inside")
        // Just outside the crop edge : still white.
        assertRgbaApprox(pixels, 7, 7, 255, 255, 255, 255, tag = "Crop kDecal edge outside")
    }

    @Test
    fun `saveLayer with Crop kClamp imageFilter replicates edge color outside`() {
        // Phase G-saveLayer-imageFilter-crop -- kClamp tile mode :
        // outside the crop rect, the sampler clamps to the nearest
        // in-rect texel. We paint the inside of the crop rect with
        // red ; outside the rect we should still see red (because the
        // edge texel is red), modulated through the kSrcOver onto the
        // background. The whole layer was drawn red, but only the
        // crop rect's interior holds red premul ; the kClamp branch
        // re-samples that red border for every out-of-rect pixel.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                val cropRect = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Crop(cropRect, SkTileMode.kClamp)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Draw a 16x16 red rect that fills the crop rect.
                canvas.drawRect(cropRect, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }
        // Inside : red.
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "Crop kClamp inside")
        // Outside, top-left corner : the closest in-rect pixel is at
        // (8, 8) which is red -- expect red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "Crop kClamp clamp top-left")
        // Outside, bottom-right corner : closest in-rect pixel at (23,
        // 23) -- expect red.
        assertRgbaApprox(pixels, 28, 28, 255, 0, 0, 255, tag = "Crop kClamp clamp bot-right")
    }

    // ─── Phase G-saveLayer-imageFilter-tile ─────────────────────────

    @Test
    fun `saveLayer with Tile imageFilter replicates source rect across dst rect`() {
        // Phase G-saveLayer-imageFilter-tile -- Tile(src, dst) repeats
        // a sub-region (`src`) of the layer texture across the dst
        // rect. We paint a small red square inside src ; expect the
        // tile to replicate that square across the dst rect's grid.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // src = top-left 8x8 of the layer ; dst = full layer
                // (32x32) -> tile 4x4.
                val srcRect = SkRect.MakeLTRB(0f, 0f, 8f, 8f)
                val dstRect = SkRect.MakeLTRB(0f, 0f, 32f, 32f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Tile(srcRect, dstRect)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Inside src : a 4x4 red square at (2..6, 2..6) ;
                // remaining pixels of src stay transparent (the layer
                // device starts cleared transparent).
                canvas.drawRect(
                    SkRect.MakeLTRB(2f, 2f, 6f, 6f),
                    SkPaint().apply { color = SK_ColorRED },
                )
                canvas.restore()
                device.flush()
            }
        }
        // Tile (0, 0) of the dst : original red square at (2, 2) ..
        // (6, 6). Test centre pixel (4, 4) -> red.
        assertRgbaApprox(pixels, 4, 4, 255, 0, 0, 255, tag = "Tile (0,0) red")
        // Tile (1, 0) of the dst : the same square replicated at
        // (10, 2) .. (14, 6). Test (12, 4) -> red.
        assertRgbaApprox(pixels, 12, 4, 255, 0, 0, 255, tag = "Tile (1,0) red")
        // Tile (2, 2) of the dst : square at (18, 18) .. (22, 22).
        // Test (20, 20) -> red.
        assertRgbaApprox(pixels, 20, 20, 255, 0, 0, 255, tag = "Tile (2,2) red")
        // Tile (3, 3) : (26, 26) .. (30, 30). Test (28, 28) -> red.
        assertRgbaApprox(pixels, 28, 28, 255, 0, 0, 255, tag = "Tile (3,3) red")
        // Between tiles : (0, 0) is in src but outside the red
        // square -> transparent in the layer -> background white
        // shows through.
        assertRgbaApprox(pixels, 0, 0, 255, 255, 255, 255, tag = "Tile in-src background")
        // Tile (1, 0) at the same in-tile location (8, 0) -> also
        // background.
        assertRgbaApprox(pixels, 8, 0, 255, 255, 255, 255, tag = "Tile (1,0) background")
    }

    // ─── Phase G-saveLayer-imageFilter-magnifier ────────────────────

    @Test
    fun `saveLayer with Magnifier outside lens is identity passthrough`() {
        // Phase G-saveLayer-imageFilter-magnifier -- the outside-the-
        // lens path is a strict identity. We paint a red rect that
        // extends beyond the lens and check the outside-lens pixels
        // match the unfiltered composite.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Lens in the middle of the layer.
                val lens = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val layerPaint = SkPaint().apply {
                    imageFilter = SkImageFilters.Magnifier(
                        lensBounds = lens, zoomAmount = 2f, inset = 4f,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Paint a red rect that extends well outside the lens
                // ; the outside-lens portion must be untouched.
                canvas.drawRect(
                    SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    SkPaint().apply { color = SK_ColorRED },
                )
                canvas.restore()
                device.flush()
            }
        }
        // Outside lens : red passthrough.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "Magnifier outside lens top-left")
        assertRgbaApprox(pixels, 28, 28, 255, 0, 0, 255, tag = "Magnifier outside lens bot-right")
        // Inside lens centre : red (the layer was painted uniform red,
        // any sample inside the layer extent is red).
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "Magnifier inside lens centre")
    }

    @Test
    fun `saveLayer with Magnifier zoom magnifies inside lens`() {
        // Phase G-saveLayer-imageFilter-magnifier -- non-trivial zoom.
        // We paint a horizontal red bar at the lens-centre Y line
        // (devY = 16). Inside the lens, with zoom = 2, the sample
        // coord halves the distance from the lens centre, so a fragment
        // at devY = 12 (centre = 16) samples devY = 14 ; that's still
        // within the bar (Y in 15..16 in our test), so we need a thick
        // bar. Make the bar at Y = 14..18 (4 px) and place lens with
        // a small inset to maximise the magnified region.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                // Lens : centred at (16, 16), extending 8 px in each
                // direction so the lens spans (8, 8) .. (24, 24).
                val lens = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    // inset = 1 -> the lens has a 1-px transition zone
                    // and the rest is fully magnified.
                    imageFilter = SkImageFilters.Magnifier(
                        lensBounds = lens, zoomAmount = 2f, inset = 1f,
                    )
                }
                canvas.saveLayer(layerBounds, layerPaint)
                // Horizontal red bar at Y = 14..18 across the whole
                // layer.
                canvas.drawRect(
                    SkRect.MakeLTRB(0f, 14f, 32f, 18f),
                    SkPaint().apply { color = SK_ColorRED },
                )
                canvas.restore()
                device.flush()
            }
        }
        // Outside lens : (4, 4) is outside both the lens and the bar
        // -> white.
        assertRgbaApprox(pixels, 4, 4, 255, 255, 255, 255, tag = "Magnifier outside lens, off-bar")
        // Outside lens but on the bar : (4, 16) -> red.
        assertRgbaApprox(pixels, 4, 16, 255, 0, 0, 255, tag = "Magnifier outside lens, on bar")
        // Inside lens at centre Y line : (16, 16) -> red (the bar
        // extends across, magnification of a uniform line is the line
        // itself).
        assertRgbaApprox(pixels, 16, 16, 255, 0, 0, 255, tag = "Magnifier centre on bar")
        // Inside lens at a row 4 px above centre : devY = 12, lens
        // centre Y = 16. With zoom = 2 and t = clamp(min_edge / inset)
        // = 1 (we are 4 px from the top edge, inset = 1, so fully
        // magnified), sampleY = 16 + (12 - 16) / 2 = 14. y = 14 is
        // INSIDE the bar (14..18) -> red.
        assertRgbaApprox(pixels, 16, 12, 255, 0, 0, 255, tag = "Magnifier above centre samples bar")
        // Inside lens at a row 7 px above centre : devY = 9, near
        // edge. min_edge = 1 -> t = 1 (saturated). sampleY = 16 +
        // (9 - 16) / 2 = 12.5 -> rounds to 13. y = 13 is OUTSIDE the
        // bar (the bar starts at 14) -> white.
        assertRgbaApprox(pixels, 16, 9, 255, 255, 255, 255, tag = "Magnifier near top edge samples off-bar")
    }

    // ─── Extractor unit tests ────────────────────────────────────────

    @Test
    fun `asCropImageFilter extracts rect tileMode and input from Crop`() {
        // Unit test of the extractor : Crop nodes surface (rect,
        // tileMode, input) ; leaf filters return null.
        val rect = SkRect.MakeLTRB(2f, 4f, 8f, 16f)
        val crop = SkImageFilters.Crop(rect, SkTileMode.kRepeat)
        val params = crop.asCropImageFilter()
        assertTrue(params != null) { "Crop must extract" }
        assertEquals(rect.left, params!!.rect.left)
        assertEquals(rect.top, params.rect.top)
        assertEquals(rect.right, params.rect.right)
        assertEquals(rect.bottom, params.rect.bottom)
        assertEquals(SkTileMode.kRepeat, params.tileMode)
        assertEquals(null, params.input, "Crop input must be null")
        // Other variants must not extract as Crop.
        val blur = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )!!
        assertEquals(null, blur.asCropImageFilter()) {
            "Blur must not extract as Crop"
        }
    }

    @Test
    fun `asTileImageFilter extracts src and dst rects from Tile`() {
        val src = SkRect.MakeLTRB(0f, 0f, 16f, 16f)
        val dst = SkRect.MakeLTRB(0f, 0f, 64f, 64f)
        val tile = SkImageFilters.Tile(src, dst)
        val params = tile.asTileImageFilter()
        assertTrue(params != null) { "Tile must extract" }
        assertEquals(src.right, params!!.src.right, "src right")
        assertEquals(dst.right, params.dst.right, "dst right")
        assertEquals(null, params.input, "Tile input must be null")
        // Other variants must not extract as Tile.
        val crop = SkImageFilters.Crop(src, SkTileMode.kDecal)
        assertEquals(null, crop.asTileImageFilter()) {
            "Crop must not extract as Tile"
        }
    }

    @Test
    fun `asMagnifierImageFilter extracts lensBounds zoom inset from Magnifier`() {
        val lens = SkRect.MakeLTRB(4f, 4f, 28f, 28f)
        val mag = SkImageFilters.Magnifier(
            lensBounds = lens, zoomAmount = 3.5f, inset = 2f,
        )
        val params = mag.asMagnifierImageFilter()
        assertTrue(params != null) { "Magnifier must extract" }
        assertEquals(lens.left, params!!.lensBounds.left)
        assertEquals(lens.right, params.lensBounds.right)
        assertEquals(3.5f, params.zoomAmount, "zoomAmount")
        assertEquals(2f, params.inset, "inset")
        assertEquals(null, params.input, "Magnifier input must be null")
        // Other variants must not extract as Magnifier.
        val tile = SkImageFilters.Tile(lens, lens)
        assertEquals(null, tile.asMagnifierImageFilter()) {
            "Tile must not extract as Magnifier"
        }
    }

    // ─── Negative / error path tests ─────────────────────────────────

    @Test
    fun `saveLayer with Crop and paint colorFilter both set throws clear error`() {
        // The first Crop slice keeps the UV-remap and colour-filter
        // branches orthogonal (the composite shader runs them in
        // series but the host gate keeps the wiring simple).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val luma = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f,
        )
        val ex = kotlin.runCatching {
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                    val layerPaint = SkPaint().apply {
                        imageFilter = SkImageFilters.Crop(
                            SkRect.MakeLTRB(8f, 8f, 24f, 24f),
                            SkTileMode.kDecal,
                        )
                        colorFilter = SkColorFilters.Matrix(luma)
                    }
                    canvas.saveLayer(layerBounds, layerPaint)
                    canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                    canvas.restore()
                    device.flush()
                }
            }
        }.exceptionOrNull()
        assertTrue(ex != null) { "must throw" }
        assertTrue(ex!!.message?.contains("Crop") == true) {
            "error must mention Crop, got: ${ex.message}"
        }
        assertTrue(ex.message?.contains("colorFilter") == true) {
            "error must mention colorFilter, got: ${ex.message}"
        }
    }

    @Test
    fun `saveLayer with Tile imageFilter input nonNull throws clear error`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val inner = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )!!
        val ex = kotlin.runCatching {
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                    val layerPaint = SkPaint().apply {
                        imageFilter = SkImageFilters.Tile(
                            srcRect = SkRect.MakeLTRB(0f, 0f, 8f, 8f),
                            dstRect = SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                            input = inner,
                        )
                    }
                    canvas.saveLayer(layerBounds, layerPaint)
                    canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                    canvas.restore()
                    device.flush()
                }
            }
        }.exceptionOrNull()
        assertTrue(ex != null) { "must throw" }
        assertTrue(ex!!.message?.contains("Tile") == true) {
            "error must mention Tile, got: ${ex.message}"
        }
    }

    @Test
    fun `saveLayer with Magnifier imageFilter input nonNull throws clear error`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val inner = SkImageFilters.Blur(
            sigmaX = 1f, sigmaY = 1f, tileMode = SkTileMode.kDecal, input = null,
        )!!
        val ex = kotlin.runCatching {
            context!!.use { ctx ->
                SkWebGpuDevice(ctx, W, H).use { device ->
                    device.setBackground(SK_ColorWHITE)
                    val canvas = SkCanvas(device)
                    val layerBounds = SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat())
                    val layerPaint = SkPaint().apply {
                        imageFilter = SkImageFilters.Magnifier(
                            lensBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f),
                            zoomAmount = 2f,
                            inset = 4f,
                            input = inner,
                        )
                    }
                    canvas.saveLayer(layerBounds, layerPaint)
                    canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                    canvas.restore()
                    device.flush()
                }
            }
        }.exceptionOrNull()
        assertTrue(ex != null) { "must throw" }
        assertTrue(ex!!.message?.contains("Magnifier") == true) {
            "error must mention Magnifier, got: ${ex.message}"
        }
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
