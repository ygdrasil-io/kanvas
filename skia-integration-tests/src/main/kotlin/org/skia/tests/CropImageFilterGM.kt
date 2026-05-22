package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's
 * [`gm/crop_imagefilter.cpp`](https://github.com/google/skia/blob/main/gm/crop_imagefilter.cpp)
 * — `CropImageFilterGM` (16 cpp variants). Each variant covers a
 * `(SkTileMode_in, SkTileMode_out)` pair via two layered
 * [SkImageFilters.Crop] calls (input crop -> Blur(4, 4) -> output
 * crop) ; the GM walks a 4×5 grid where rows hold input-relation ×
 * hint-content combinations and columns hold output-relation values.
 *
 * Each of the 16 cells layers an input crop / blur / output crop
 * filter on top of a stripe-pattern image, reusing the same
 * [makeCroppedImage]-based "cropped image" helper (built on
 * [SkImage.makeSubset]) to also tile the source image at quarter
 * alpha behind the filtered draw — that's the call-site that
 * exercises the [SkImage.makeSubset] API on `:kanvas-skia`.
 *
 * ## Port status
 *
 * Body is fully ported against the live `:kanvas-skia`
 * [SkImageFilters], [SkImage.makeShader] / [SkImage.makeSubset],
 * [SkDashPathEffect] and [SkSurfaces.Raster] surfaces. The matching
 * [CropImageFilterGMTest] is **active** (no `@Disabled`) and ratchets
 * 4 of the 16 cpp variants — the `(decal, decal)` /  `(clamp, clamp)`
 * / `(repeat, repeat)` / `(mirror, mirror)` diagonal. Only
 * `(decal, decal)` currently lands above 80 % similarity vs the
 * upstream PNG reference ; the three non-decal diagonals score
 * ~7 % — tracked as a known divergence in
 * `:cpu-raster`'s saveLayer + non-decal `Crop` tile-mode interaction
 * (see [skia-integration-tests/test-similarity-report.md] entries),
 * not a body-port gap.
 *
 * Classification per the GM port methodology : **LAZY_PORT** (body
 * already a faithful translation of the cpp at the time of authoring
 * in `Iter 4 — Extract Skia-mirror GMs into :skia-integration-tests`
 * — #469).
 */
public class CropImageFilterGM(
    private val inputMode: SkTileMode,
    private val outputMode: SkTileMode,
) : GM() {

    override fun getName(): String {
        val sb = StringBuilder("crop_imagefilter_")
        sb.append(tileModeSuffix(inputMode))
        sb.append("-in_")
        sb.append(tileModeSuffix(outputMode))
        sb.append("-out")
        return sb.toString()
    }

    override fun getISize(): SkISize = SkISize.Make(
        (kNumCols * (kExampleBoundsRight + 1) - 1).toInt(),
        (kNumRows * (kExampleBoundsBottom + 1) - 1).toInt(),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawExampleGrid(c, inputMode, outputMode)
    }

    // -----------------------------------------------------------------
    // Bounds helpers
    // -----------------------------------------------------------------

    private enum class CropRelation {
        kCropOverlapsRect,
        kCropContainsRect,
        kRectContainsCrop,
        kCropRectDisjoint,
    }

    private fun makeOverlap(r: SkRect, ax: Float, ay: Float): SkRect =
        r.makeOffset(r.width() * ax, r.height() * ay)

    private fun makeInset(r: SkRect, ax: Float, ay: Float): SkRect =
        r.makeInset(r.width() * ax, r.height() * ay)

    private fun makeOutset(r: SkRect, ax: Float, ay: Float): SkRect =
        r.makeOutset(r.width() * ax, r.height() * ay)

    private fun makeDisjoint(r: SkRect, ax: Float, ay: Float): SkRect {
        val ox = if (ax > 0f) r.width() + r.width() * ax
        else if (ax < 0f) -r.width() + r.width() * ax
        else 0f
        val oy = if (ay > 0f) r.height() + r.height() * ay
        else if (ay < 0f) -r.height() + r.height() * ay
        else 0f
        return r.makeOffset(ox, oy)
    }

    private data class Rects(val output: SkRect, val crop: SkRect, val content: SkRect)

    private fun getExampleRects(
        outputRelation: CropRelation, inputRelation: CropRelation, hintContent: Boolean,
    ): Rects {
        val outputBounds = kExampleBounds.makeInset(20f, 20f)
        var cropRect = when (outputRelation) {
            CropRelation.kCropOverlapsRect -> makeOverlap(outputBounds, -0.15f, 0.15f)
            CropRelation.kCropContainsRect -> makeOutset(outputBounds, 0.15f, 0.15f)
            CropRelation.kRectContainsCrop -> makeInset(outputBounds, 0.15f, 0.15f)
            CropRelation.kCropRectDisjoint -> makeDisjoint(outputBounds, 0.15f, 0f)
        }
        // Clamp crop to example bounds.
        val cropMut = SkRect.MakeLTRB(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
        cropMut.intersect(kExampleBounds)
        cropRect = cropMut

        val contentBounds = if (hintContent) {
            val cb = when (inputRelation) {
                CropRelation.kCropOverlapsRect -> makeOverlap(cropRect, 0.075f, -0.75f)
                CropRelation.kCropContainsRect -> makeInset(cropRect, 0.075f, 0.075f)
                CropRelation.kRectContainsCrop -> makeOutset(cropRect, 0.1f, 0.1f)
                CropRelation.kCropRectDisjoint -> makeDisjoint(cropRect, 0f, 0.075f)
            }
            val cbMut = SkRect.MakeLTRB(cb.left, cb.top, cb.right, cb.bottom)
            cbMut.intersect(kExampleBounds)
            cbMut
        } else {
            kExampleBounds
        }
        return Rects(outputBounds, cropRect, contentBounds)
    }

    // -----------------------------------------------------------------
    // Image factories
    // -----------------------------------------------------------------

    private fun makeImage(contentBounds: SkRect?): SkImage {
        val w = kExampleBounds.width()
        val h = kExampleBounds.height()
        val info = SkImageInfo.MakeN32Premul(
            kotlin.math.ceil(w.toDouble()).toInt(),
            kotlin.math.ceil(h.toDouble()).toInt(),
        )
        val surf = SkSurfaces.Raster(info)!!
        val sc = surf.canvas
        sc.clear(SK_ColorDKGRAY)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = h / 16f
        }
        // Horizontal lines
        paint.color = SK_ColorRED
        sc.drawLine(0f, h / 4f, w, h / 4f, paint)
        paint.color = 0xFF71EEB8.toInt() // sea foam
        sc.drawLine(0f, 3f * h / 8f, w, 3f * h / 8f, paint)
        paint.color = SK_ColorYELLOW
        sc.drawLine(0f, 5f * h / 8f, w, 5f * h / 8f, paint)
        paint.color = SK_ColorCYAN
        sc.drawLine(0f, 3f * h / 4f, w, 3f * h / 4f, paint)
        // Vertical lines
        paint.strokeWidth = w / 16f
        paint.color = 0xFFFFA500.toInt() // orange
        sc.drawLine(w / 4f, 0f, h / 4f, h, paint)
        paint.color = SK_ColorBLUE
        sc.drawLine(3f * w / 8f, 0f, 3f * h / 8f, h, paint)
        paint.color = SK_ColorMAGENTA
        sc.drawLine(5f * w / 8f, 0f, 5f * h / 8f, h, paint)
        paint.color = SK_ColorGREEN
        sc.drawLine(3f * w / 4f, 0f, 3f * h / 4f, h, paint)

        if (contentBounds != null) {
            val buffer = contentBounds.makeOutset(1f, 1f)
            sc.clipRect(buffer, org.skia.foundation.SkClipOp.kDifference)
            sc.clear(SK_ColorRED)
        }
        return surf.makeImageSnapshot()
    }

    /**
     * Mirrors C++ `make_cropped_image` — extracts the subset in
     * [contentBounds] from [image] (using [contentTile] to choose
     * roundOut / roundIn), tiles it through [cropRect] back into a new
     * raster surface. Exercises [SkImage.makeSubset].
     */
    private fun makeCroppedImage(
        image: SkImage, contentBounds: SkRect, contentTile: SkTileMode, cropRect: SkRect,
    ): SkImage? {
        val info = SkImageInfo.MakeN32Premul(
            kotlin.math.ceil(cropRect.width().toDouble()).toInt(),
            kotlin.math.ceil(cropRect.height().toDouble()).toInt(),
        )
        val surface = SkSurfaces.Raster(info) ?: return null
        val ir = if (contentTile == SkTileMode.kDecal) contentBounds.roundOut() else contentBounds.roundIn()
        val content = image.makeSubset(ir) ?: return null
        val lm = org.graphiks.math.SkMatrix.MakeTrans(contentBounds.left, contentBounds.top)
        val tiledShader = content.makeShader(contentTile, contentTile, SkSamplingOptions(SkFilterMode.kNearest), lm)
        val tiledPaint = SkPaint().apply { shader = tiledShader }
        val sc = surface.canvas
        sc.translate(-cropRect.left, -cropRect.top)
        sc.drawPaint(tiledPaint)
        return surface.makeImageSnapshot()
    }

    // -----------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------

    private fun drawExampleTile(
        canvas: SkCanvas,
        inputMode: SkTileMode,
        inputRelation: CropRelation,
        hintContent: Boolean,
        outputMode: SkTileMode,
        outputRelation: CropRelation,
    ) {
        val r = getExampleRects(outputRelation, inputRelation, hintContent)
        val outputBounds = r.output
        val cropRect = r.crop
        val contentBounds = r.content

        val image = makeImage(if (hintContent) contentBounds else null)

        canvas.save()
        // Visualise the image tiled on the content bounds and then on the crop
        // rect, semi-transparent.
        run {
            val cropImage = makeCroppedImage(image, contentBounds, inputMode, cropRect)
            if (cropImage != null) {
                val lm = org.graphiks.math.SkMatrix.MakeTrans(cropRect.left, cropRect.top)
                val tiledPaint = SkPaint().apply {
                    shader = cropImage.makeShader(outputMode, outputMode,
                        SkSamplingOptions(SkFilterMode.kNearest), lm)
                    alphaf = 0.25f
                }
                canvas.save()
                canvas.clipRect(kExampleBounds)
                canvas.drawPaint(tiledPaint)
                canvas.restore()
            }
        }

        // Build filter, clip, save layer, draw, restore.
        run {
            var filter: SkImageFilter = SkImageFilters.Crop(contentBounds, inputMode, null)
            filter = SkImageFilters.Blur(4f, 4f, filter) ?: filter
            filter = SkImageFilters.Crop(cropRect, outputMode, filter)
            val layerPaint = SkPaint().apply { imageFilter = filter }
            canvas.save()
            canvas.clipRect(outputBounds)
            canvas.saveLayer(if (hintContent) contentBounds else null, layerPaint)
            canvas.drawImageRect(image, contentBounds, contentBounds,
                SkSamplingOptions(SkFilterMode.kNearest), null,
                org.skia.core.SrcRectConstraint.kStrict)
            canvas.restore()
            canvas.restore()
        }

        // Visualise bounds.
        run {
            val border = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
            border.color = kOutputBoundsColor
            canvas.drawRect(outputBounds, border)
            border.color = kCropRectColor
            canvas.drawRect(cropRect, border)
            if (hintContent) {
                border.color = kContentBoundsColor
                canvas.drawRect(contentBounds, border)
            }
        }

        canvas.restore()
    }

    private fun drawExampleColumn(
        canvas: SkCanvas, inputMode: SkTileMode, outputMode: SkTileMode, outputRelation: CropRelation,
    ) {
        val inputRelations = listOf(
            CropRelation.kCropOverlapsRect to false,
            CropRelation.kCropOverlapsRect to true,
            CropRelation.kCropContainsRect to true,
            CropRelation.kRectContainsCrop to true,
            CropRelation.kCropRectDisjoint to true,
        )
        canvas.save()
        for ((inputRelation, hintContent) in inputRelations) {
            drawExampleTile(canvas, inputMode, inputRelation, hintContent, outputMode, outputRelation)
            canvas.translate(0f, kExampleBoundsBottom + 1f)
        }
        canvas.restore()
    }

    private fun drawExampleGrid(canvas: SkCanvas, inputMode: SkTileMode, outputMode: SkTileMode) {
        canvas.save()
        for (outputRelation in listOf(
            CropRelation.kCropOverlapsRect, CropRelation.kCropContainsRect,
            CropRelation.kRectContainsCrop, CropRelation.kCropRectDisjoint,
        )) {
            drawExampleColumn(canvas, inputMode, outputMode, outputRelation)
            canvas.translate(kExampleBoundsRight + 1f, 0f)
        }
        canvas.restore()

        // Dashed separators
        val dashed = SkPaint().apply {
            color = SK_ColorGRAY
            style = SkPaint.Style.kStroke_Style
            strokeCap = SkPaint.Cap.kSquare_Cap
            pathEffect = SkDashPathEffect.Make(floatArrayOf(5f, 15f), 0f)
        }
        for (y in 1 until kNumRows) {
            canvas.drawLine(0.5f, y * (kExampleBoundsBottom + 1f) - 0.5f,
                kGridWidth - 0.5f, y * (kExampleBoundsBottom + 1f) - 0.5f, dashed)
        }
        for (x in 1 until kNumCols) {
            canvas.drawLine(x * (kExampleBoundsRight + 1f) - 0.5f, 0.5f,
                x * (kExampleBoundsRight + 1f) - 0.5f, kGridHeight - 0.5f, dashed)
        }
    }

    private companion object {
        val kExampleBounds: SkRect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        const val kExampleBoundsRight: Float = 100f
        const val kExampleBoundsBottom: Float = 100f
        const val kNumRows = 5
        const val kNumCols = 4
        const val kGridWidth = kNumCols * (kExampleBoundsRight + 1f) - 1f
        const val kGridHeight = kNumRows * (kExampleBoundsBottom + 1f) - 1f
        const val kOutputBoundsColor = SK_ColorRED
        const val kCropRectColor = SK_ColorGREEN
        const val kContentBoundsColor = SK_ColorBLUE

        fun tileModeSuffix(m: SkTileMode): String = when (m) {
            SkTileMode.kDecal -> "decal"
            SkTileMode.kClamp -> "clamp"
            SkTileMode.kRepeat -> "repeat"
            SkTileMode.kMirror -> "mirror"
        }
    }
}
