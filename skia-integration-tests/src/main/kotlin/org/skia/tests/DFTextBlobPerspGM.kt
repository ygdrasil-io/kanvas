package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.awt.SkSdfGlyphCache
import org.graphiks.math.SkColor
import org.graphiks.math.SkScalar
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/dftext_blob_persp.cpp::DFTextBlobPerspGM`](https://github.com/google/skia/blob/main/gm/dftext_blob_persp.cpp).
 *
 * "Tests reusing the same text blobs with distance fields rendering using
 * various combinations of perspective and non-perspective matrices,
 * scissor clips, and different x,y params passed to the draw" (upstream
 * cpp KDoc, line 38-41).
 *
 * The upstream variant runs an offscreen [org.skia.core.SkSurface]
 * created with [SkSurfaceProps.kUseDeviceIndependentFonts_Flag] — that
 * flag drives the **distance-field text** code path on the GPU, where
 * glyphs are rasterised through a signed-distance-field shader instead
 * of the per-size glyph atlas (`gm/dftext.cpp` is the broader DF-text
 * showcase ; see [DFTextGM]).
 *
 * **`:kanvas-skia` raster behaviour** — the upstream Ganesh atlas/shader
 * path is replaced here by [SkSdfGlyphCache], a portable raster SDF glyph
 * cache that materialises A8 glyph images and samples them through the
 * existing image-shader path. This keeps the surface-props and perspective
 * plumbing live without porting Ganesh/Graphite.
 *
 * Upstream creates an offscreen Ganesh `SkSurface` when
 * `inputCanvas->recordingContext()` is non-null and falls back to
 * drawing on the input canvas otherwise — our raster pipeline has no
 * recording context, so the fallback branch is the live path
 * (matches upstream's `surface ? surface->getCanvas() : inputCanvas`
 * dispatch on a raster sink).
 */
public class DFTextBlobPerspGM : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    private val blobs: MutableList<SkTextBlob> = mutableListOf()

    override fun getName(): String = "dftext_blob_persp"

    override fun getISize(): SkISize = SkISize.Make(900, 350)

    override fun onOnceBeforeDraw() {
        // Three blobs — one per SkFont.Edging mode. Upstream sets
        // size = 32, subpixel = true on each. `kSubpixelAntiAlias`
        // collapses to `kAntiAlias` in our raster backend
        // (`SkFont.Edging` KDoc) — that's fine for this GM, the
        // edging axis is just a "three different blob instances"
        // generator.
        for (i in 0 until 3) {
            val font = ToolUtils.DefaultPortableFont()
            font.size = 32f
            font.edging = when (i) {
                0 -> SkFont.Edging.kAlias
                1 -> SkFont.Edging.kAntiAlias
                else -> SkFont.Edging.kSubpixelAntiAlias
            }
            font.isSubpixel = true
            val builder = SkTextBlobBuilder()
            ToolUtils.addToTextBlob(builder, "SkiaText", font, 0f, 0f)
            val blob = builder.make() ?: continue
            blobs.add(blob)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return

        // Upstream allocates an offscreen surface with
        // `kUseDeviceIndependentFonts_Flag` when a recording context
        // exists, then blits the result back. With our raster sink :
        //  - there is no recording context, so `surface` is `null`,
        //    and upstream's `canvas = surface ? … : inputCanvas`
        //    dispatch lands on `inputCanvas` directly ;
        //  - we still attempt the `makeSurface` call (with the
        //    upstream flag) so the surface-props plumbing rounds-
        //    trips. If it succeeds (raster path always does for a
        //    valid `SkImageInfo`), we draw into that surface and
        //    composite back via [org.skia.foundation.SkSurface.draw]
        //    to mirror the upstream "blit back" step.
        //
        // The size matches the GM's reported ISize : upstream queries
        // `inputCanvas->getBaseLayerSize()` to fall back to the input
        // canvas's pixel dimensions when non-empty. Our test harness
        // (`TestUtils.runGmTest`) wires the canvas to a surface of
        // exactly `getISize()` size, so the two values coincide.
        val size = getISize()
        val inputProps = canvas.surfaceProps()
        val props = SkSurfaceProps(
            flags = inputProps.flags or SkSurfaceProps.kUseDeviceIndependentFonts_Flag,
            pixelGeometry = inputProps.pixelGeometry,
        )
        val info = org.skia.foundation.SkImageInfo.MakeN32Premul(size.width, size.height)
        val surface = canvas.makeSurface(info, props)
        val drawCanvas: SkCanvas = surface?.canvas ?: canvas

        // Carry the input canvas's CTM onto the offscreen canvas — the
        // upstream call is `canvas->setMatrix(inputCanvas->getLocalToDeviceAs3x3())`.
        // Our equivalent is `getLocalToDeviceAsMatrix()` (Kotlin-nullable)
        // which collapses perspective to its 3×3 affine drop or returns
        // null if true 3D content is active. Default to identity in the
        // null case (matches the affine round-trip).
        if (surface != null) {
            val ctm = canvas.getLocalToDeviceAsMatrix() ?: SkMatrix.Identity
            drawCanvas.setMatrix(ctm)
        }

        var x: SkScalar = 0f
        var y: SkScalar = 0f
        var maxH: SkScalar = 0f
        for (twm in arrayOf(TranslateWithMatrix.kNo, TranslateWithMatrix.kYes)) {
            for (pm in arrayOf(
                PerspMode.kNone, PerspMode.kX, PerspMode.kY, PerspMode.kXY,
            )) {
                for (blob in blobs) {
                    val w = blob.bounds().width()
                    val h = blob.bounds().height()
                    for (clip in booleanArrayOf(false, true)) {
                        drawCanvas.withSave {
                            if (clip) {
                                val rect = SkRect.MakeXYWH(
                                    x + 5f, y + 5f, w * 3f / 4f, h * 3f / 4f,
                                )
                                drawCanvas.clipRect(rect, doAntiAlias = false)
                            }
                            drawBlob(drawCanvas, blob, SK_ColorBLACK, x, y + h, pm, twm)
                        }
                        x += w + 20f
                        if (h > maxH) maxH = h
                    }
                }
                x = 0f
                y += maxH + 20f
                maxH = 0f
            }
        }

        // Render the offscreen buffer back onto the input canvas. The
        // upstream code resets the input canvas matrix to identity
        // before the blit, because the offscreen canvas was seeded
        // with the original CTM already (so the blit lands at
        // device-coord origin).
        if (surface != null) {
            canvas.withSave {
                canvas.resetMatrix()
                canvas.drawImage(surface.makeImageSnapshot(), 0f, 0f)
            }
        }
    }

    /**
     * Mirrors upstream's `drawBlob` helper (cpp line ~124). Concats
     * a perspective-around-`(x, y)` matrix onto the canvas, optionally
     * folds the `(x, y)` offset into the CTM (when
     * `translateWithMatrix == kYes`), and draws the blob via
     * [SkCanvas.drawTextBlob].
     *
     * Perspective is constructed as
     * `T(x, y) · P · T(-x, -y)` so the perspective origin sits at the
     * blob's logical anchor point — same recipe as upstream.
     */
    private fun drawBlob(
        canvas: SkCanvas,
        blob: SkTextBlob,
        color: SkColor,
        xIn: SkScalar,
        yIn: SkScalar,
        perspMode: PerspMode,
        translateWithMatrix: TranslateWithMatrix,
    ) {
        var x = xIn
        var y = yIn
        canvas.withSave {
            // Base perspective matrix : identity for `kNone`, `setPerspX` /
            // `setPerspY` (or both) for the other modes. Upstream uses
            // `SkMatrix::setPerspX(v)` to set the bottom-row `kMPersp0`
            // entry ; our `SkMatrix` is a `data class` with `persp0` /
            // `persp1` constructor parameters, so we use `copy(...)`.
            val basePersp: SkMatrix = when (perspMode) {
                PerspMode.kNone -> SkMatrix.Identity
                PerspMode.kX -> SkMatrix.Identity.copy(persp0 = 0.005f)
                PerspMode.kY -> SkMatrix.Identity.copy(persp1 = 0.005f)
                PerspMode.kXY -> SkMatrix.Identity.copy(persp0 = -0.001f, persp1 = -0.0015f)
            }

            // `persp = T(x, y) · P · T(-x, -y)` — upstream writes this
            // out as two nested `SkMatrix::Concat` calls in source order
            // `(T(x, y), Concat(P, T(-x, -y)))`. We follow the same
            // build order.
            val tNeg = SkMatrix.MakeTrans(-x, -y)
            val tPos = SkMatrix.MakeTrans(x, y)
            val inner = SkMatrix.concat(basePersp, tNeg) // P · T(-x, -y)
            val persp = SkMatrix.concat(tPos, inner)     // T(x, y) · (P · T(-x, -y))
            canvas.concat(persp)

            if (translateWithMatrix == TranslateWithMatrix.kYes) {
                // Fold the per-blob offset into the CTM ; the
                // `drawTextBlob` call then anchors at the origin. This
                // tests that the rasteriser produces identical output
                // when the offset rides in the CTM vs. when it rides
                // in the draw arguments.
                canvas.translate(x, y)
                x = 0f
                y = 0f
            }

            val paint = SkPaint().apply { this.color = color }
            SkSdfGlyphCache.drawTextBlob(canvas, blob, x, y, paint)
        }
    }

    /** Mirrors upstream's `enum class PerspMode` (cpp line ~120). */
    private enum class PerspMode { kNone, kX, kY, kXY }

    /** Mirrors upstream's `enum class TranslateWithMatrix : bool`. */
    private enum class TranslateWithMatrix { kNo, kYes }
}
