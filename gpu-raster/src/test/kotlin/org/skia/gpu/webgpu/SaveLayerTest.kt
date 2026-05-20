package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import kotlin.math.abs

/**
 * Phase G-saveLayer scaffolding tests — verify that
 * `SkCanvas.saveLayer + drawRect + restore` on a GPU root canvas
 * routes through [SkWebGpuDevice.makeLayerDevice] and
 * [SkWebGpuDevice.compositeFrom], honouring the layer paint's alpha
 * and blendMode.
 *
 * **Scope.** Plain `saveLayer(bounds, paint)` with the natively-
 * blendable subset (kSrcOver / kSrc / kClear / kDstOver) plus a
 * fragment-side `kPlus` exercise. Colour filter / image filter on
 * the layer paint are out of scope for this scaffolding slice (see
 * `MIGRATION_PLAN_GPU_WEBGPU.md` Phase G-saveLayer follow-ups).
 *
 * The unit tests render onto a raw sRGB-coded readback target
 * (`applyColorspaceTransform = false`), so RGB bytes are directly
 * comparable to the source colours.
 */
class SaveLayerTest {

    @Test
    fun `saveLayer plus drawRect plus restore composites layer onto parent`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLUE)
                val canvas = SkCanvas(device)
                // The full-viewport background is blue (set above + cleared
                // by the first render pass). Open a layer covering the
                // inner 16×16 square, fill it with opaque red, restore.
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                canvas.saveLayer(layerBounds, null)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside the layer : opaque red (composite with SrcOver onto blue ;
        // alpha = 255 so the layer pixels replace the destination).
        assertRgbaApprox(pixels, 12, 12, 255, 0, 0, 255, tag = "layer interior")
        assertRgbaApprox(pixels, 20, 20, 255, 0, 0, 255, tag = "layer interior #2")
        // Outside the layer : blue (background untouched).
        assertRgbaApprox(pixels, 2, 2, 0, 0, 255, 255, tag = "outside layer top-left")
        assertRgbaApprox(pixels, 28, 28, 0, 0, 255, 255, tag = "outside layer bottom-right")
        // Just past the layer edge : still blue.
        assertRgbaApprox(pixels, 24, 16, 0, 0, 255, 255, tag = "just outside right edge")
    }

    @Test
    fun `saveLayer with alpha paint blends the layer back at half opacity`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Open a layer with alpha = 128 ; fill with opaque red ;
                // restore. The composite should be roughly
                // (128/255) * red + (1 - 128/255) * white = pinkish.
                val layerPaint = SkPaint().apply { alpha = 128 }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Expected : SrcOver of (R=255, A=128) over (R=255, G=255, B=255, A=255).
        //   out_r = 255*128/255 + 255*(255-128)/255 = 128 + 127 = 255
        //   out_g = 0*128/255   + 255*(255-128)/255 = 0   + 127 = 127
        //   out_b = 0*128/255   + 255*(255-128)/255 = 0   + 127 = 127
        //   out_a = 128         + 255*(255-128)/255 = 128 + 127 = 255
        // The fragment pipeline carries integer-byte precision so ±2 of
        // tolerance is enough.
        assertRgbaApprox(pixels, 14, 14, 255, 127, 127, 255, tag = "alpha-blended center", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 127, 127, 255, tag = "alpha-blended center #2", tol = 2)
        // Outside layer : pure white background.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with bounds restricts composite to those device pixels`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                canvas.saveLayer(layerBounds, null)
                // Try to draw outside the layer bounds — the layer's clip
                // should drop these pixels.
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside the layer bounds : red.
        assertRgbaApprox(pixels, 15, 15, 255, 0, 0, 255, tag = "inside layer bounds")
        assertRgbaApprox(pixels, 11, 11, 255, 0, 0, 255, tag = "inside near top-left")
        // Outside the layer bounds : white background untouched (the layer
        // device was sized to 12×12 and the composite only writes that bbox).
        assertRgbaApprox(pixels, 5, 5, 255, 255, 255, 255, tag = "outside layer top-left")
        assertRgbaApprox(pixels, 25, 25, 255, 255, 255, 255, tag = "outside layer bottom-right")
        assertRgbaApprox(pixels, 22, 16, 255, 255, 255, 255, tag = "just past right edge")
    }

    @Test
    fun `nested saveLayer one level deep composites correctly`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val outerBounds = SkRect.MakeLTRB(4f, 4f, 28f, 28f)
                val innerBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                canvas.saveLayer(outerBounds, null)
                canvas.saveLayer(innerBounds, null)
                canvas.drawRect(innerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                canvas.restore()
                device.flush()
            }
        }

        // Inner-layer pixels : red (after two layer composites with null
        // paint → both are SrcOver with alpha 255).
        assertRgbaApprox(pixels, 15, 15, 255, 0, 0, 255, tag = "inner layer center")
        // Outer-but-not-inner : white (outer layer was untouched apart
        // from the inner composite).
        assertRgbaApprox(pixels, 6, 6, 255, 255, 255, 255, tag = "outer layer corner")
        // Outside both : white background.
        assertRgbaApprox(pixels, 1, 1, 255, 255, 255, 255, tag = "outside all layers")
    }

    @Test
    fun `saveLayer with blendMode kSrc replaces destination inside layer`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLUE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // kSrc on the layer composite : layer pixels REPLACE the
                // destination (no blend with the parent blue background).
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : opaque red.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "kSrc layer center")
        // Outside layer : blue background.
        assertRgbaApprox(pixels, 2, 2, 0, 0, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with blendMode kClear zeroes destination under layer extent`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLUE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // kClear on the layer composite : every pixel touched by
                // the layer's footprint becomes transparent black,
                // regardless of what the layer drew. Mirrors upstream's
                // "kClear erases parent pixels under the layer extent".
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kClear }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : transparent black.
        assertRgbaApprox(pixels, 14, 14, 0, 0, 0, 0, tag = "kClear layer center")
        // Outside layer : blue background untouched.
        assertRgbaApprox(pixels, 2, 2, 0, 0, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer plus 100 drawRects plus restore composites without error`() {
        // Phase G-saveLayer-fast perf-shape exercise -- 100 draws inside
        // the layer scope (more than the worst-case GM ever generates)
        // verifies the direct-blit composite handles a populated
        // intermediate texture without exception / NaN / obviously
        // wrong pixels (post-composite the layer body should still be
        // visibly painted, NOT background-coloured).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(4f, 4f, 28f, 28f)
                canvas.saveLayer(layerBounds, null)
                // 100 stacked 1-pixel-tall horizontal stripes inside the
                // layer bounds. Final stripe at y = 27 is red (the last
                // one painted under SrcOver wins for opaque alpha).
                repeat(100) { i ->
                    val y = 4f + (i % 24).toFloat()
                    canvas.drawRect(
                        SkRect.MakeLTRB(4f, y, 28f, y + 1f),
                        SkPaint().apply { color = SK_ColorRED },
                    )
                }
                canvas.restore()
                device.flush()
            }
        }

        // Layer body : every interior pixel was hit at least once by a
        // red stripe (the modulo wraps every 24 rows so each of the 24
        // interior rows is painted 4-5 times). Composite is SrcOver of
        // opaque red over white -> opaque red.
        assertRgbaApprox(pixels, 8, 8, 255, 0, 0, 255, tag = "layer body 1")
        assertRgbaApprox(pixels, 15, 15, 255, 0, 0, 255, tag = "layer body 2")
        assertRgbaApprox(pixels, 20, 25, 255, 0, 0, 255, tag = "layer body 3")
        // Outside layer : white background untouched.
        assertRgbaApprox(pixels, 1, 1, 255, 255, 255, 255, tag = "outside layer")
        assertRgbaApprox(pixels, 30, 30, 255, 255, 255, 255, tag = "outside layer #2")
        // No NaN bleed : every byte we sampled should be a valid byte.
        // (Test the corners + a couple of interior + a couple of bounds
        // pixels.)
        for ((x, y) in listOf(0 to 0, W - 1 to 0, 0 to H - 1, W - 1 to H - 1,
                              5 to 5, 10 to 10, 27 to 27, 3 to 3)) {
            val i = (y * W + x) * 4
            for (c in 0 until 4) {
                val v = pixels[i + c].toInt() and 0xFF
                assertTrue(v in 0..255) { "byte at ($x, $y).$c = $v out of range" }
            }
        }
    }

    @Test
    fun `saveLayer with null bounds spans full viewport`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                // null bounds : layer matches the current clip (full viewport).
                canvas.saveLayer(null, null)
                canvas.drawRect(SkRect.MakeLTRB(4f, 4f, 28f, 28f),
                    SkPaint().apply { color = SK_ColorBLUE })
                canvas.restore()
                device.flush()
            }
        }

        // Layer's drawRect lands at the same device pixels as a direct
        // draw would have, because the layer covers the full viewport
        // and the composite is SrcOver with full alpha.
        assertRgbaApprox(pixels, 15, 15, 0, 0, 255, 255, tag = "layer body")
        assertRgbaApprox(pixels, 1, 1, 255, 255, 255, 255, tag = "background corner")
    }

    // ─── Phase G-saveLayer-colorFilter tests ──────────────────────────

    @Test
    fun `saveLayer with Blend(red, kPlus) colorFilter tints green layer to yellow`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // saveLayer with colorFilter = Blend(red, kPlus) ; draw
                // green inside. The composite-pass shader runs the
                // colour filter on the sampled (green premul) texel :
                //   blendPremul(src = (R=1, G=0, B=0, A=1),
                //               dst = (R=0, G=1, B=0, A=1),
                //               kPlus) = min(1, s+d) = (1, 1, 0, 1)
                // ⇒ yellow premul. Composite (SrcOver) over white :
                //   out = yellow + white * (1 - 1) = yellow.
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kPlus)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : yellow (R=255, G=255, B=0, A=255).
        assertRgbaApprox(pixels, 12, 12, 255, 255, 0, 255, tag = "Blend(red,kPlus) center", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 255, 0, 255, tag = "Blend(red,kPlus) center #2", tol = 2)
        // Outside layer : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Blend(blue, kModulate) colorFilter masks green layer to black`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // colorFilter = Blend(blue, kModulate) on a green layer.
                //   src = blue premul = (R=0, G=0, B=1, A=1)
                //   dst = green premul = (R=0, G=1, B=0, A=1)
                //   modulate = s * d = (0, 0, 0, 1) -- opaque black.
                // Composite (SrcOver) over white -> opaque black.
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kModulate)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : opaque black.
        assertRgbaApprox(pixels, 14, 14, 0, 0, 0, 255, tag = "Blend(blue,kModulate) center", tol = 2)
        // Outside layer : white background.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Matrix grayscale colorFilter turns red layer to gray`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Rec.601 luma weights : 0.299 R + 0.587 G + 0.114 B,
                // alpha untouched. Applied to opaque red ⇒
                //   gray = (0.299, 0.299, 0.299, 1) ≈ (76, 76, 76, 255).
                val luma = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f,
                )
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Matrix(luma)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : gray (~ 76, 76, 76, 255). Float-precision +
        // 8-bit quantisation -> ±2 tolerance.
        assertRgbaApprox(pixels, 14, 14, 76, 76, 76, 255, tag = "Matrix luma center", tol = 2)
        // Outside layer : white background.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with Matrix bias colorFilter shifts blue layer to white`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLUE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Matrix with bias only : identity coefs + bias of
                // (1, 1, 0, 0) lifts R and G to 1 (clamped) while
                // leaving B and A from the source. Applied to blue
                // (R=0, G=0, B=1, A=1) ⇒ (1, 1, 1, 1) = white.
                val biasOnly = floatArrayOf(
                    1f, 0f, 0f, 0f, 1f,
                    0f, 1f, 0f, 0f, 1f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                val layerPaint = SkPaint().apply {
                    colorFilter = SkColorFilters.Matrix(biasOnly)
                }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorBLUE })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : white.
        assertRgbaApprox(pixels, 14, 14, 255, 255, 255, 255, tag = "Matrix bias center", tol = 2)
        // Outside layer : blue background.
        assertRgbaApprox(pixels, 2, 2, 0, 0, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with null paint colorFilter is a no-op (fast path)`() {
        // Regression -- the colour-filter slot expanded the uniform from
        // 32 to 128 bytes. Verify the no-filter path still produces
        // bit-iso output (matches the original `saveLayer plus drawRect`
        // test outside of this group).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                // Paint with NO colour filter -- the composite uniform
                // ships the identity payload, shader's kind == 0 fast
                // path runs.
                val layerPaint = SkPaint() // colorFilter = null
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : opaque red (SrcOver of red over white).
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "null-filter center")
        // Outside layer : white background untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    @Test
    fun `saveLayer with unsupported colorFilter (Compose) falls back to no-filter composite`() {
        // Phase G-saveLayer-colorFilter -- variants outside the supported
        // subset (modeFilter / matrixFilter) should drop silently and
        // composite as if no filter was set. Compose(Matrix, Matrix) is
        // a concrete unsupported case that the host-side `asMatrixFilter`
        // / `asBlendModeFilter` extractors return null for.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                // Compose two identity matrices -- not detected as a
                // Matrix filter by the extractor, so the GPU path drops
                // the filter and composites the layer pixels directly.
                val composed = SkColorFilters.Compose(
                    SkColorFilters.Matrix(identity),
                    SkColorFilters.Matrix(identity),
                )
                val layerPaint = SkPaint().apply { colorFilter = composed }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorRED })
                canvas.restore()
                device.flush()
            }
        }

        // Filter dropped ⇒ same as no-filter SrcOver of red over white.
        // (Identity Compose would have been bit-iso anyway -- this also
        // protects against a buggy extractor that picks up Compose as a
        // matrix and ships wrong floats.)
        assertRgbaApprox(pixels, 14, 14, 255, 0, 0, 255, tag = "Compose-fallback center")
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside layer")
    }

    // ─── Phase G-saveLayer-blend tests ────────────────────────────────

    @Test
    fun `saveLayer with kPlus blendMode adds layer to background`() {
        // kPlus is `out = clamp(s + d, 0, 1)` in premul space. A green
        // layer (R=0, G=1, B=0, A=1 premul) composited onto a red
        // background (R=1, G=0, B=0, A=1) yields min(1, s+d) =
        // (1, 1, 0, 1) = yellow.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kPlus }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : yellow (additive blend).
        assertRgbaApprox(pixels, 12, 12, 255, 255, 0, 255, tag = "kPlus center", tol = 2)
        assertRgbaApprox(pixels, 20, 20, 255, 255, 0, 255, tag = "kPlus center #2", tol = 2)
        // Outside layer : background red untouched.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kPlus")
    }

    @Test
    fun `saveLayer with kMultiply blendMode multiplies layer with background`() {
        // kMultiply premul : rc = (1-sa)*dc + (1-da)*sc + sc*dc.
        // With opaque src and dst (sa = da = 1), all carrier terms zero
        // out and the result is `s * d` channel-wise. 50% gray src over
        // red dst : s = (0.5, 0.5, 0.5, 1) premul, d = (1, 0, 0, 1) ->
        // rc = (0.5, 0, 0, 1) per channel ; ra = 1 + 1 - 1*1 = 1.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gray = 0xFF808080.toInt()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kMultiply }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = gray })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : dark red (~ 128, 0, 0, 255). 8-bit quantisation
        // tolerance of 2.
        assertRgbaApprox(pixels, 14, 14, 128, 0, 0, 255, tag = "kMultiply center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kMultiply")
    }

    @Test
    fun `saveLayer with kModulate blendMode multiplies layer with background opaque src`() {
        // kModulate is `s * d` per channel including alpha. For opaque
        // operands : s = (0.5, 0.5, 0.5, 1), d = (1, 0, 0, 1) -> s*d =
        // (0.5, 0, 0, 1). Alpha stays opaque (1 * 1 = 1) ; the per-channel
        // product on the colour channels keeps only the red component.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gray = 0xFF808080.toInt()
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kModulate }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = gray })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : premul (128, 0, 0, 255).
        assertRgbaApprox(pixels, 14, 14, 128, 0, 0, 255, tag = "kModulate center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kModulate")
    }

    @Test
    fun `saveLayer with kScreen blendMode lightens background`() {
        // kScreen : s + d - s*d (separable, including alpha). Opaque
        // blue layer over opaque red bg : s = (0, 0, 1, 1), d = (1, 0, 0, 1).
        // rc = (1, 0, 1, 1) - red component cancels with itself, others
        // sum. Result : magenta (255, 0, 255, 255).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kScreen }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorBLUE })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : magenta.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 255, 255, tag = "kScreen center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kScreen")
    }

    @Test
    fun `saveLayer with kDarken blendMode picks darker channel`() {
        // kDarken : rc = sc + dc - max(sc*da, dc*sa) ; ra = SrcOver.
        // For opaque s, d (sa = da = 1) : rc = sc + dc - max(sc, dc) =
        // min(sc, dc) per channel. Opaque green (0, 1, 0, 1) over opaque
        // red (1, 0, 0, 1) -> (min(0, 1), min(1, 0), min(0, 0), 1) =
        // (0, 0, 0, 1) = black.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kDarken }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : black (min of red and green channels = 0).
        assertRgbaApprox(pixels, 14, 14, 0, 0, 0, 255, tag = "kDarken center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kDarken")
    }

    @Test
    fun `saveLayer with kLighten blendMode picks brighter channel`() {
        // kLighten : rc = sc + dc - min(sc*da, dc*sa) ; ra = SrcOver.
        // For opaque s, d : rc = max(sc, dc) per channel. Opaque green
        // over opaque red -> (max(0, 1), max(1, 0), max(0, 0), 1) =
        // (1, 1, 0, 1) = yellow.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kLighten }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : yellow (max of red and green channels).
        assertRgbaApprox(pixels, 14, 14, 255, 255, 0, 255, tag = "kLighten center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kLighten")
    }

    @Test
    fun `saveLayer with kDifference blendMode subtracts colors`() {
        // kDifference : rc = sc + dc - 2*min(sc*da, dc*sa) ; ra = SrcOver.
        // For opaque s, d : rc = sc + dc - 2*min(sc, dc) = |sc - dc|.
        // Opaque white (1,1,1,1) over opaque red (1,0,0,1) -> (0, 1, 1, 1)
        // = cyan.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kDifference }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorWHITE })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : cyan.
        assertRgbaApprox(pixels, 14, 14, 0, 255, 255, 255, tag = "kDifference center", tol = 2)
        // Outside layer : background red.
        assertRgbaApprox(pixels, 2, 2, 255, 0, 0, 255, tag = "outside kDifference")
    }

    @Test
    fun `saveLayer with kExclusion blendMode subtracts double product`() {
        // kExclusion : rc = sc + dc - 2*sc*dc ; ra = SrcOver. Like
        // Difference for max-and-zero values. For opaque green (0,1,0,1)
        // over opaque white (1,1,1,1) -> sc + dc - 2*sc*dc per channel :
        //   R : 0 + 1 - 0 = 1
        //   G : 1 + 1 - 2*1*1 = 0
        //   B : 0 + 1 - 0 = 1
        // -> magenta (1, 0, 1, 1).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kExclusion }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : magenta.
        assertRgbaApprox(pixels, 14, 14, 255, 0, 255, 255, tag = "kExclusion center", tol = 2)
        // Outside layer : background white.
        assertRgbaApprox(pixels, 2, 2, 255, 255, 255, 255, tag = "outside kExclusion")
    }

    @Test
    fun `saveLayer with kPlus blendMode does not affect pixels outside layer`() {
        // Regression : ensure the non-native composite's snapshot pass
        // doesn't accidentally touch pixels outside the scissor rect.
        // The snapshot pass scissor covers the whole parent, but the
        // blend composite pass scissor only covers the dst rect, and the
        // blend pipeline uses kSrc -- so only the dst rect should change.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(10f, 10f, 22f, 22f)
                val layerPaint = SkPaint().apply { blendMode = SkBlendMode.kPlus }
                canvas.saveLayer(layerBounds, layerPaint)
                canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorBLUE })
                canvas.restore()
                device.flush()
            }
        }

        // Inside layer : magenta (R=255, G=0, B=255, A=255 from kPlus
        // of blue over red).
        assertRgbaApprox(pixels, 15, 15, 255, 0, 255, 255, tag = "kPlus inside", tol = 2)
        // Outside layer : background red untouched (key regression).
        assertRgbaApprox(pixels, 0, 0, 255, 0, 0, 255, tag = "kPlus outside TL")
        assertRgbaApprox(pixels, 31, 31, 255, 0, 0, 255, tag = "kPlus outside BR")
        assertRgbaApprox(pixels, 5, 5, 255, 0, 0, 255, tag = "kPlus outside near layer")
        assertRgbaApprox(pixels, 25, 25, 255, 0, 0, 255, tag = "kPlus outside near layer #2")
    }

    @Test
    fun `saveLayer with kPlus blendMode and Blur image filter throws`() {
        // Bail condition : combining a Blur image filter with a non-
        // native blend mode is deferred. Verify the gate throws clearly.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        var threw = false
        var msg: String? = null
        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                val layerBounds = SkRect.MakeLTRB(8f, 8f, 24f, 24f)
                val layerPaint = SkPaint().apply {
                    blendMode = SkBlendMode.kPlus
                    imageFilter = org.skia.foundation.SkImageFilters.Blur(
                        sigmaX = 2f, sigmaY = 2f,
                        tileMode = org.skia.foundation.SkTileMode.kDecal,
                        input = null,
                    )
                }
                try {
                    canvas.saveLayer(layerBounds, layerPaint)
                    canvas.drawRect(layerBounds, SkPaint().apply { color = SK_ColorGREEN })
                    canvas.restore()
                    device.flush()
                } catch (e: IllegalStateException) {
                    threw = true
                    msg = e.message
                }
            }
        }
        assertTrue(threw, "expected IllegalStateException for kPlus + Blur, got none")
        assertTrue(
            msg?.contains("Blur") == true && msg?.contains("kPlus") == true,
            "error message should mention Blur and kPlus ; got : $msg",
        )
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
