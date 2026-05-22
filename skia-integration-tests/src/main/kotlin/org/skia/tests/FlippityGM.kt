package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTextEncoding
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/flippity.cpp::FlippityGM`](https://github.com/google/skia/blob/main/gm/flippity.cpp)
 * (768 × 640 — see [kGMWidth] / [kGMHeight]).
 *
 * Exercises `drawImage` / `drawImageRect` with a battery of corner-flip /
 * 90°-rotation / scale matrices. Each cell of the 6 × 4 grid renders the
 * same labelled reference image (LL/LR/UL/UR markers at the four corners)
 * through one of the six "UV" matrices, optionally on a subset, optionally
 * scaled. The corner labels are drawn separately *outside* the image
 * bounds so the matrix-induced orientation is always readable :
 *
 *  - row 1 : top-left-origin reference, full image, no scale.
 *  - row 2 : bottom-left-origin reference (irrelevant on raster — see
 *    *kanvas-skia adaptation* below), full image, no scale.
 *  - row 3 : bottom-left-origin reference, drawn from a subset rect into
 *    the same subset (translates the visible window).
 *  - row 4 : bottom-left-origin reference, drawn from a subset rect into
 *    the full image rect (scales the subset back up to fill the cell).
 *
 * **Kanvas-skia adaptation** :
 *
 *  - Upstream's `make_reference_image` has a GPU branch that wraps the
 *    bitmap in an `SkImage_Ganesh` whose texture is uploaded with either
 *    [`kBottomLeft_GrSurfaceOrigin`](https://github.com/google/skia/blob/main/include/gpu/ganesh/GrTypes.h)
 *    or `kTopLeft_GrSurfaceOrigin`. That origin flip is what makes rows
 *    1 / 2 differ on the GPU — a bottom-left-origin GPU texture is
 *    sampled with `v` axis-flipped at the texture stage, so the upstream
 *    reference image draws the *same* labelled corners in mirrored
 *    positions. The CPU path falls back to `SkImages::RasterFromBitmap`
 *    with no orientation twist, so the two `fReferenceImages[i]` are
 *    bit-identical on raster. We mirror that behaviour : both rows
 *    render identically on `:kanvas-skia` (matching the upstream raster
 *    sink, which is what produced `original-888/flippity.png`).
 *
 *  - [SkRect.MakeXYWH] in our math layer uses `width / height` (Skia's
 *    convention) — same as upstream's `SkRect::MakeXYWH(x, y, w, h)`.
 *
 *  - The original C++ uses `SkMatrix::RectToRectOrIdentity` to build the
 *    text-bounds-to-cell matrix when measuring labels. Our [SkMatrix]
 *    exposes [`MakeRectToRect`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L559)
 *    which returns `null` for empty source rects ; we fall back to
 *    [SkMatrix.Identity] in that case to preserve the upstream
 *    "OrIdentity" semantic.
 *
 * @see ToolUtils.DefaultPortableFont for the label rasterisation.
 */
public class FlippityGM : GM() {

    init {
        setBGColor(0xFFCCCCCC.toInt())
    }

    override fun getName(): String = "flippity"

    override fun getISize(): SkISize = SkISize.Make(kGMWidth, kGMHeight)

    // Lazy state — populated on first draw, mirrors upstream's
    // `onGpuSetup` / `onDraw` split.
    private val fLabels: MutableList<SkImage> = mutableListOf()
    private val fReferenceImages: Array<SkImage?> = arrayOf(null, null)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Lazy setup — upstream defers this to `onGpuSetup` ; we collapse
        // onto `onDraw` because the kanvas-skia GM lifecycle has no
        // pre-draw hook on the raster sink (raster GMs run setup
        // inline).
        ensureSetup()
        val refTopLeft = fReferenceImages[0] ?: return
        val refBottomLeft = fReferenceImages[1] ?: return

        c.save()

        // Top row : top-left-origin reference, no subset / scale.
        drawRow(c, refTopLeft, drawSubset = false, drawScaled = false)
        c.translate(0f, kCellSize.toFloat())

        // Bottom row : bottom-left-origin reference (== top-left on raster),
        // no subset / scale.
        drawRow(c, refBottomLeft, drawSubset = false, drawScaled = false)
        c.translate(0f, kCellSize.toFloat())

        // Third row : subset, no scale.
        drawRow(c, refBottomLeft, drawSubset = true, drawScaled = false)
        c.translate(0f, kCellSize.toFloat())

        // Fourth row : subset, scaled back to full image size.
        drawRow(c, refBottomLeft, drawSubset = true, drawScaled = true)

        c.restore()

        // Separator grid (drawn after the cells so the lines aren't
        // covered by image rectangles).
        val gridPaint = SkPaint()
        for (i in 0..3) {
            c.drawLine(0f, (i * kCellSize).toFloat(), kGMWidth.toFloat(), (i * kCellSize).toFloat(), gridPaint)
        }
        for (i in 0 until kNumMatrices) {
            c.drawLine((i * kCellSize).toFloat(), 0f, (i * kCellSize).toFloat(), kGMHeight.toFloat(), gridPaint)
        }
    }

    /**
     * Build the four corner labels and the two reference images. On
     * first call this populates [fLabels] and both [fReferenceImages]
     * slots ; subsequent calls are no-ops.
     */
    private fun ensureSetup() {
        makeLabels()
        if (fReferenceImages[0] == null) fReferenceImages[0] = makeReferenceImage(bottomLeftOrigin = false)
        if (fReferenceImages[1] == null) fReferenceImages[1] = makeReferenceImage(bottomLeftOrigin = true)
    }

    /** Mirrors upstream's `FlippityGM::makeLabels()`. */
    private fun makeLabels() {
        if (fLabels.isNotEmpty()) return
        for (i in 0 until kNumLabels) {
            fLabels.add(makeTextImage(kLabelText[i], kLabelColors[i]))
        }
    }

    /**
     * Mirrors upstream's free function `make_reference_image` (raster
     * branch — see *kanvas-skia adaptation* in the class KDoc for the
     * GPU branch we intentionally drop). [bottomLeftOrigin] is accepted
     * for signature parity but ignored : the raster path has no
     * orientation flag.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun makeReferenceImage(bottomLeftOrigin: Boolean): SkImage {
        // Upstream uses kRGBA_8888 / kOpaque ; we mirror that exactly so
        // the label pixels round-trip without an unpremul step.
        val info = SkImageInfo.Make(
            kImageSize, kImageSize,
            SkColorType.kRGBA_8888,
            org.skia.foundation.SkAlphaType.kOpaque,
        )
        val bitmap = SkBitmap(kImageSize, kImageSize, info.colorSpace, info.colorType)
        val canvas = SkCanvas(bitmap)
        canvas.clear(SK_ColorWHITE)
        for (i in 0 until kNumLabels) {
            // Inset the labels from each corner by `kInset` ; upstream's
            // formula compresses to "if x != 0 then x - kLabelSize -
            // kInset else kInset" (same for y).
            val x = if (kPoints[i].fX != 0f) kPoints[i].fX - kLabelSize - kInset else kInset.toFloat()
            val y = if (kPoints[i].fY != 0f) kPoints[i].fY - kLabelSize - kInset else kInset.toFloat()
            canvas.drawImage(fLabels[i], x, y)
        }
        return SkImage.Make(bitmap)
    }

    /**
     * Mirrors upstream's `FlippityGM::drawRow`. Walks the 6 matrices and
     * draws the reference image (optionally subsetted / scaled) plus the
     * four corner labels in the matrix's coordinate space.
     */
    private fun drawRow(canvas: SkCanvas, image: SkImage, drawSubset: Boolean, drawScaled: Boolean) {
        canvas.save()
        canvas.translate(kLabelSize.toFloat(), kLabelSize.toFloat())
        for (i in 0 until kNumMatrices) {
            drawImageWithMatrixAndLabels(canvas, image, i, drawSubset, drawScaled)
            canvas.translate(kCellSize.toFloat(), 0f)
        }
        canvas.restore()
    }

    /**
     * Mirrors upstream's `FlippityGM::drawImageWithMatrixAndLabels`. The
     * "geometry" matrix is derived from the per-matrix UV matrix via
     * [uvMatToGeomMatForImage] — this is the matrix that ends up on the
     * CTM. The labels are drawn *outside* the image's `[0, kImageSize]`
     * bounds so the corner the matrix exposes is always readable
     * regardless of any flips / rotations.
     */
    private fun drawImageWithMatrixAndLabels(
        canvas: SkCanvas,
        image: SkImage,
        matIndex: Int,
        drawSubset: Boolean,
        drawScaled: Boolean,
    ) {
        val imageGeomMat = uvMatToGeomMatForImage(kUVMatrices[matIndex])
            ?: error("UV matrix at index $matIndex is non-invertible — upstream invariant violated")

        canvas.save()

        canvas.concat(imageGeomMat)
        if (drawSubset) {
            val src = kSubsets[matIndex]
            val dst = if (drawScaled) SkRect.MakeWH(kImageSize.toFloat(), kImageSize.toFloat()) else src
            canvas.drawImageRect(
                image,
                src,
                dst,
                SkSamplingOptions.Default,
                null,
                SrcRectConstraint.kFast,
            )
        } else {
            canvas.drawImage(image, 0f, 0f)
        }

        // Labels are drawn outside the image bounds, on the corner the
        // matrix exposes. Mirrors the upstream "if x == 0 then -kLabelSize
        // else x" formula.
        for (i in 0 until kNumLabels) {
            val x = if (kPoints[i].fX == 0f) (-kLabelSize).toFloat() else kPoints[i].fX
            val y = if (kPoints[i].fY == 0f) (-kLabelSize).toFloat() else kPoints[i].fY
            canvas.drawImage(fLabels[i], x, y)
        }
        canvas.restore()
    }

    /**
     * Build a fixed-size [kLabelSize] × [kLabelSize] [SkImage] showing
     * the given [text] in [color] against a white background. Mirrors
     * upstream's `make_text_image` free function — the font's
     * tight-fit bounds are mapped into the label cell via a `RectToRect`
     * matrix so the glyph fills the cell regardless of font metrics.
     */
    private fun makeTextImage(text: String, color: SkColor): SkImage {
        val paint = SkPaint().apply {
            isAntiAlias = true
            this.color = color
        }
        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kAntiAlias
            size = 32f
        }
        val bounds = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        font.measureText(text, text.length, SkTextEncoding.kUTF8, bounds)
        // The measureText call above sets bounds in-place on the Skia
        // side ; our SkRect is a data class (immutable), so re-measure
        // through the `bounds` out-param won't propagate. The font API
        // accepts a non-null SkRect and returns the advance width, but
        // doesn't mutate the rect (it's a value object). We approximate
        // the upstream behaviour by re-measuring : ToolUtils' default
        // portable font carries fixed-width metrics that we can build
        // a tight cell bound from via getMetrics.
        // Build a [labelSize × labelSize] cell — the upstream
        // RectToRectOrIdentity scales the (variable-height) text bounds
        // into the cell. Without an actual tight rect we approximate by
        // drawing the text directly into the cell using a baseline
        // anchored near the cell bottom, with horizontal centring on
        // the advance width.
        val advance = font.measureText(text, text.length, SkTextEncoding.kUTF8, null)
        // Try to use the measured bounds if non-empty ; otherwise fall
        // back to a baseline at 80% of the cell height.
        val cell = SkRect.MakeWH(kLabelSize.toFloat(), kLabelSize.toFloat())
        val mat: SkMatrix = if (advance > 0f) {
            // Approximate tight bounds : width = advance, height = ascent
            // + descent (cap height + descender slack). The exact glyph
            // bounds aren't accessible without reading per-glyph paths,
            // so we centre by advance ; the cell's RectToRect mapping
            // gives a glyph that fills the cell.
            val metrics = org.skia.foundation.SkFontMetrics()
            font.getMetrics(metrics)
            val ascent = -metrics.fAscent  // upstream metrics are negative for ascent
            val descent = metrics.fDescent
            val tight = SkRect.MakeLTRB(0f, -ascent, advance, descent)
            SkMatrix.MakeRectToRect(tight, cell, SkMatrix.ScaleToFit.kFill_ScaleToFit)
                ?: SkMatrix.Identity
        } else {
            SkMatrix.Identity
        }
        val info = SkImageInfo.MakeN32Premul(kLabelSize, kLabelSize)
        val surf = SkSurfaces.Raster(info) ?: error("Failed to allocate label surface")
        val canvas = surf.canvas
        canvas.clear(SK_ColorWHITE)
        canvas.concat(mat)
        canvas.drawSimpleText(text, text.length, SkTextEncoding.kUTF8, 0f, 0f, font, paint)
        return surf.makeImageSnapshot()
    }

    public companion object {
        // Layout constants — mirror upstream's `static const int` block.
        private const val kNumMatrices: Int = 6
        private const val kImageSize: Int = 128
        private const val kLabelSize: Int = 32
        private const val kNumLabels: Int = 4
        private const val kInset: Int = 16
        private const val kCellSize: Int = kImageSize + 2 * kLabelSize
        private const val kGMWidth: Int = kNumMatrices * kCellSize
        private const val kGMHeight: Int = 4 * kCellSize

        // Anchor positions for the corner labels — LL, LR, UL, UR.
        // Mirrors upstream's `kPoints`.
        private val kPoints: Array<SkPoint> = arrayOf(
            SkPoint(0f, kImageSize.toFloat()),                 // LL
            SkPoint(kImageSize.toFloat(), kImageSize.toFloat()), // LR
            SkPoint(0f, 0f),                                   // UL
            SkPoint(kImageSize.toFloat(), 0f),                 // UR
        )

        // The six "UV" matrices that drive the per-cell orientation.
        // Mirrors upstream's `kUVMatrices`.
        private val kUVMatrices: Array<SkMatrix> = arrayOf(
            // 90° rotation : (u, v) → (1 - v, 1 - u)
            SkMatrix.MakeAll(
                0f, -1f, 1f,
                -1f, 0f, 1f,
            ),
            // flip y : (u, v) → (u, 1 - v)
            SkMatrix.MakeAll(
                1f, 0f, 0f,
                0f, -1f, 1f,
            ),
            // flip x : (u, v) → (1 - u, v)
            SkMatrix.MakeAll(
                -1f, 0f, 1f,
                0f, 1f, 0f,
            ),
            // -90° rotation : (u, v) → (v, 1 - u)
            SkMatrix.MakeAll(
                0f, 1f, 0f,
                -1f, 0f, 1f,
            ),
            // 180° rotation (flip both) : (u, v) → (1 - u, 1 - v)
            SkMatrix.MakeAll(
                -1f, 0f, 1f,
                0f, -1f, 1f,
            ),
            // identity
            SkMatrix.MakeAll(
                1f, 0f, 0f,
                0f, 1f, 0f,
            ),
        )

        // Per-matrix subset rects used by row 3 / 4. Mirrors upstream's
        // `kSubsets` (declared `static const` inside
        // `drawImageWithMatrixAndLabels` but hoisted out for Kotlin
        // readability).
        private val kSubsets: Array<SkRect> = arrayOf(
            SkRect.MakeXYWH(kInset.toFloat(), 0f, (kImageSize - kInset).toFloat(), kImageSize.toFloat()),
            SkRect.MakeXYWH(0f, kInset.toFloat(), kImageSize.toFloat(), (kImageSize - kInset).toFloat()),
            SkRect.MakeXYWH(0f, 0f, (kImageSize - kInset).toFloat(), kImageSize.toFloat()),
            SkRect.MakeXYWH(0f, 0f, kImageSize.toFloat(), (kImageSize - kInset).toFloat()),
            SkRect.MakeXYWH(
                (kInset / 2).toFloat(), (kInset / 2).toFloat(),
                (kImageSize - kInset).toFloat(), (kImageSize - kInset).toFloat(),
            ),
            SkRect.MakeXYWH(
                kInset.toFloat(), kInset.toFloat(),
                (kImageSize - 2 * kInset).toFloat(), (kImageSize - 2 * kInset).toFloat(),
            ),
        )

        private val kLabelText: Array<String> = arrayOf("LL", "LR", "UL", "UR")
        private val kLabelColors: IntArray = intArrayOf(
            SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN,
        )

        /**
         * Mirrors upstream's free function `UVMatToGeomMatForImage`.
         * Converts a `[0..1]² → [0..1]²` UV matrix into the inverse
         * geometry matrix in image-pixel space, with a y-flip applied
         * on both sides so the matrix matches Skia's screen-space
         * (y-down) convention. Returns `null` if the resulting matrix
         * is non-invertible — the upstream `SkAssertResult` invariant
         * would have fired in that case.
         */
        private fun uvMatToGeomMatForImage(uvMat: SkMatrix): SkMatrix? {
            val yFlip = SkMatrix.MakeAll(
                1f, 0f, 0f,
                0f, -1f, 1f,
            )
            var tmp = uvMat
            tmp = tmp.preConcat(yFlip)
            tmp = tmp.preScale(1f / kImageSize, 1f / kImageSize)
            tmp = tmp.postConcat(yFlip)
            tmp = tmp.postScale(kImageSize.toFloat(), kImageSize.toFloat())
            return tmp.invert()
        }
    }
}
