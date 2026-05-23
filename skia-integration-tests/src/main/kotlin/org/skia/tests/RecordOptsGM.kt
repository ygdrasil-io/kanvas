package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions

/**
 * Port of Skia's `gm/recordopts.cpp::DEF_SIMPLE_GM(recordopts, …)`
 * (`recordopts`, (kTestRectSize+1)*2 × (kTestRectSize+1)*15 = 102 × 765).
 *
 * Tests that the SkPicture recording optimisation (folding the alpha of
 * an outer saveLayer onto inner draws) produces results identical to the
 * unoptimised path.  Three drawing sequences are exercised:
 *  1. `saveLayer` → `drawRect` → `restore`  (uniform draw — can be folded).
 *  2. `saveLayer` → `drawImage` → `restore`  (non-uniform bitmap).
 *  3. SVG-opacity style: outer `saveLayer` → inner `saveLayer`(with picture
 *     image-filter) → `restore` × 2.
 *
 * For each sequence the GM draws two columns side by side: the left column
 * draws directly onto the canvas, the right column records into an
 * [SkPictureRecorder] and then replays the picture (the optimization path).
 * Both columns must produce identical pixels.
 *
 * The detector colour filters verify that the optimisation does not
 * silently alter the green-channel value that each pixel pipeline stage
 * "sees" : `make_detector_color_filter()` maps input green == 50 to full
 * green (255) and everything else to 0.  If the optimisation mis-folds an
 * alpha value the detector turns the misfolded pixel invisible, making the
 * visual divergence detectable on a diff.
 */
public class RecordOptsGM : GM() {

    override fun getName(): String = "recordopts"
    override fun getISize(): SkISize = SkISize.Make(
        (kTestRectSize + 1) * 2,
        (kTestRectSize + 1) * 15,
    )

    // ------------------------------------------------------------------
    // Helpers mirroring the C++ static helpers
    // ------------------------------------------------------------------

    private fun makeDetectorColorFilter(): SkColorFilter {
        // All-zero tables by default; only the entries below are non-zero.
        val tableA = ByteArray(256) { 0 }
        val tableR = ByteArray(256) { 0 }
        val tableG = ByteArray(256) { 0 }
        val tableB = ByteArray(256) { 0 }
        tableA[255] = (-1).toByte()                        // 255u as signed byte
        tableG[kDetectorGreenValue] = (-1).toByte()        // 255u as signed byte
        return SkColorFilters.TableARGB(tableA, tableR, tableG, tableB)
    }

    private fun installDetectorColorFilter(paint: SkPaint) {
        paint.colorFilter = makeDetectorColorFilter()
    }

    private fun installDetectorImageFilter(paint: SkPaint) {
        // SkImageFilters::ColorFilter(make_detector_color_filter(),
        //                             drawPaint->refImageFilter())
        // drawPaint has no image filter at this point, so input == null.
        paint.imageFilter = SkImageFilters.ColorFilter(
            cf = makeDetectorColorFilter(),
            input = paint.imageFilter,
        )
    }

    // ------------------------------------------------------------------
    // Three drawing sequences
    // ------------------------------------------------------------------

    private fun drawSaveLayerDrawRectRestoreSequence(
        canvas: SkCanvas,
        shapeColor: Int,
        installDetector: (SkPaint) -> Unit,
    ) {
        val targetRect = SkRect.MakeWH(kTestRectSize.toFloat(), kTestRectSize.toFloat())
        val layerPaint = SkPaint().apply {
            color = SkColorSetARGB(128, 0, 0, 0)
        }
        canvas.saveLayer(targetRect, layerPaint)
        val drawPaint = SkPaint().apply { color = shapeColor }
        installDetector(drawPaint)
        canvas.drawRect(targetRect, drawPaint)
        canvas.restore()
    }

    private fun drawSaveLayerDrawBitmapRestoreSequence(
        canvas: SkCanvas,
        shapeColor: Int,
        installDetector: (SkPaint) -> Unit,
    ) {
        val bitmap = SkBitmap(kTestRectSize, kTestRectSize)
        bitmap.eraseColor(shapeColor)
        // Make the bitmap non-uniform: draw a small white rect in the corner
        // so it cannot be optimised as a uniform drawRect.
        val bitmapCanvas = SkCanvas(bitmap)
        val whitePaint = SkPaint().apply { color = 0xFFFFFFFF.toInt() }
        bitmapCanvas.drawRect(SkRect.MakeWH(7f, 7f), whitePaint)

        val targetRect = SkRect.MakeWH(kTestRectSize.toFloat(), kTestRectSize.toFloat())
        val layerPaint = SkPaint().apply {
            color = SkColorSetARGB(129, 0, 0, 0)
        }
        canvas.saveLayer(targetRect, layerPaint)
        val drawPaint = SkPaint()
        installDetector(drawPaint)
        canvas.drawImage(bitmap.asImage(), 0f, 0f, SkSamplingOptions.Default, drawPaint)
        canvas.restore()
    }

    private fun drawSvgOpacityAndFilterLayerSequence(
        canvas: SkCanvas,
        shapeColor: Int,
        installDetector: (SkPaint) -> Unit,
    ) {
        val targetRect = SkRect.MakeWH(kTestRectSize.toFloat(), kTestRectSize.toFloat())

        // Record a small picture with a coloured rect.
        val rec = SkPictureRecorder()
        val recordCanvas = rec.beginRecording(
            (kTestRectSize + 2).toFloat(),
            (kTestRectSize + 2).toFloat(),
        )
        val shapePaint = SkPaint().apply { color = shapeColor }
        recordCanvas.drawRect(targetRect, shapePaint)
        val shape = rec.finishRecordingAsPicture()

        val layerPaint = SkPaint().apply {
            color = SkColorSetARGB(130, 0, 0, 0)
        }
        canvas.saveLayer(targetRect, layerPaint)
        canvas.save()
        canvas.clipRect(targetRect)
        val drawPaint = SkPaint().apply {
            imageFilter = SkImageFilters.Picture(shape)
        }
        installDetector(drawPaint)
        canvas.saveLayer(targetRect, drawPaint)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    // ------------------------------------------------------------------
    // onDraw
    // ------------------------------------------------------------------

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorTRANSPARENT)

        val funcs: List<(SkCanvas, Int, (SkPaint) -> Unit) -> Unit> = listOf(
            ::drawSaveLayerDrawRectRestoreSequence,
            ::drawSaveLayerDrawBitmapRestoreSequence,
            ::drawSvgOpacityAndFilterLayerSequence,
        )

        // --- Pass 1: no detector (direct + picture recording side-by-side) ---
        val shapeColorGreen = SkColorSetARGB(255, 0, 255, 0)
        for (drawSeq in funcs) {
            c.save()

            drawSeq(c, shapeColorGreen) { /* no-op detector */ }

            c.translate((kTestRectSize + 1).toFloat(), 0f)

            run {
                val recorder = SkPictureRecorder()
                drawSeq(
                    recorder.beginRecording(kTestRectSize.toFloat(), kTestRectSize.toFloat()),
                    shapeColorGreen,
                ) { /* no-op detector */ }
                recorder.finishRecordingAsPicture().playback(c)
            }

            c.restore()
            c.translate(0f, (kTestRectSize + 1).toFloat())
        }

        // --- Pass 2: detector variants ---
        val shapeColors = intArrayOf(
            SkColorSetARGB(255, 0, kDetectorGreenValue, 0),
            SkColorSetARGB(255, 0, kDetectorGreenValue + 1, 0), // tests that detectors work
        )
        val detectorFuncs: List<(SkPaint) -> Unit> = listOf(
            ::installDetectorImageFilter,
            ::installDetectorColorFilter,
        )

        for (shapeColor in shapeColors) {
            for (installDetector in detectorFuncs) {
                for (drawSeq in funcs) {
                    c.save()

                    drawSeq(c, shapeColor, installDetector)

                    c.translate((kTestRectSize + 1).toFloat(), 0f)

                    run {
                        val recorder = SkPictureRecorder()
                        drawSeq(
                            recorder.beginRecording(kTestRectSize.toFloat(), kTestRectSize.toFloat()),
                            shapeColor,
                            installDetector,
                        )
                        recorder.finishRecordingAsPicture().playback(c)
                    }

                    c.restore()
                    c.translate(0f, (kTestRectSize + 1).toFloat())
                }
            }
        }
    }

    private companion object {
        const val kTestRectSize = 50
        const val kDetectorGreenValue = 50
    }
}
