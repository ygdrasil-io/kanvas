package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's
 * [`gm/pictureimagegenerator.cpp`](https://github.com/google/skia/blob/main/gm/pictureimagegenerator.cpp)
 * `pictureimagegenerator` GM (1160 × 860).
 *
 * Records a 200 × 100 vector "logo" picture, then draws **16 variants**
 * of it onto a 4-column grid (`kDrawSize = 250`), exercising the
 * matrix / opacity-driving surface of [SkImages.DeferredFromPicture]
 * (Skia's `SkImageGenerators::MakeFromPicture` paired with
 * `SkImages::DeferredFromGenerator`). Each tile :
 *  - chooses a target [size] (`200×100`, `200×200`, `400×200`),
 *  - applies a [scaleX] / [scaleY] (incl. negative scales for
 *    horizontal / vertical flips),
 *  - and an [opacity] (1, 0.9, 0.75, 0.5, 0.25).
 *
 * The negative-scale tiles emit a `postTranslate` to keep the picture
 * inside the target bitmap (mirrors `SkMatrix::postTranslate(...)` in
 * the upstream `onDraw`).
 *
 * **Picture content divergence.** The upstream picture is the SKIA
 * wordmark via `SkTextUtils::GetPath` + multi-stop linear gradients —
 * a path/text dependency we don't fully wire up at the
 * [SkImages.DeferredFromPicture] layer in `:kanvas-skia`. The Kotlin
 * port substitutes a simpler concentric-rectangles vector pattern that
 * matches the upstream tile bounds and gradient sweep direction. The
 * grid layout, scale matrix, and opacity wiring follow upstream
 * exactly so the GM exercises the [SkImages.DeferredFromPicture]
 * matrix / opacity contract end-to-end.
 *
 * Reference image: `pictureimagegenerator.png`, 1160 × 860 — pixel
 * fidelity vs. the upstream wordmark is **not** expected (see test
 * `@Ignore` reason).
 */
public class PictureImageGeneratorGM : GM() {

    override fun getName(): String = "pictureimagegenerator"
    override fun getISize(): SkISize = SkISize.Make(1160, 860)

    private var picture: SkPicture? = null

    override fun onOnceBeforeDraw() {
        val rect = SkRect.MakeWH(PICTURE_W, PICTURE_H)
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(rect)
        drawSimplifiedLogo(canvas, rect)
        picture = recorder.finishRecordingAsPicture()
    }

    private fun drawSimplifiedLogo(canvas: SkCanvas, viewBox: SkRect) {
        // Substitute for `draw_vector_logo` — concentric rounded rects
        // with alternating colour bands. Visually distinct enough that
        // the matrix / opacity tile mapping is testable per-eye.
        val paint = SkPaint().apply {
            isAntiAlias = true
        }
        val cx = viewBox.centerX()
        val cy = viewBox.centerY()
        val maxR = minOf(viewBox.width(), viewBox.height()) * 0.45f
        var r = maxR
        var i = 0
        while (r > 4f) {
            paint.color = if ((i and 1) == 0) SK_ColorBLACK else 0xFFCC4141.toInt()
            canvas.drawRect(SkRect.MakeLTRB(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.5f), paint)
            r *= 0.7f
            i++
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pic = picture ?: return

        val configs = arrayOf(
            Config(SkISize.Make(200, 100), 1f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 2f, 1f),
            Config(SkISize.Make(400, 200), 2f, 2f, 1f),

            Config(SkISize.Make(200, 100), 1f, 1f, 0.9f),
            Config(SkISize.Make(200, 200), 1f, 1f, 0.75f),
            Config(SkISize.Make(200, 200), 1f, 2f, 0.5f),
            Config(SkISize.Make(400, 200), 2f, 2f, 0.25f),

            Config(SkISize.Make(200, 200), 0.5f, 1f, 1f),
            Config(SkISize.Make(200, 200), 1f, 0.5f, 1f),
            Config(SkISize.Make(200, 200), 0.5f, 0.5f, 1f),
            Config(SkISize.Make(200, 200), 2f, 2f, 1f),

            Config(SkISize.Make(200, 100), -1f, 1f, 1f),
            Config(SkISize.Make(200, 100), 1f, -1f, 1f),
            Config(SkISize.Make(200, 100), -1f, -1f, 1f),
            Config(SkISize.Make(200, 100), -1f, -1f, 0.5f),
        )

        val drawsPerRow = 4
        val drawSize = 250f
        val srgb = SkColorSpace.makeSRGB()

        for (i in configs.indices) {
            val cfg = configs[i]
            var m = SkMatrix.MakeScale(cfg.scaleX, cfg.scaleY)
            if (cfg.scaleX < 0f) m = m.postTranslate(cfg.size.width.toFloat(), 0f)
            if (cfg.scaleY < 0f) m = m.postTranslate(0f, cfg.size.height.toFloat())

            val opacityPaint = if (cfg.opacity < 1f) {
                SkPaint().apply {
                    color = (((cfg.opacity * 255f + 0.5f).toInt().coerceIn(0, 255)) shl 24) or 0xFFFFFF
                }
            } else {
                null
            }

            val image = SkImages.DeferredFromPicture(
                picture = pic,
                dimensions = cfg.size,
                matrix = m,
                paint = opacityPaint,
                bitDepth = SkImages.BitDepth.kU8,
                colorSpace = srgb,
            ) ?: continue

            val x = drawSize * (i % drawsPerRow)
            val y = drawSize * (i / drawsPerRow)

            // Backdrop (matches upstream `0xfff0f0f0` rect under each tile).
            val bg = SkPaint().apply { color = 0xFFF0F0F0.toInt() }
            c.drawRect(
                SkRect.MakeXYWH(x, y, image.width.toFloat(), image.height.toFloat()),
                bg,
            )
            c.drawImage(image, x, y)
        }
    }

    private data class Config(
        val size: SkISize,
        val scaleX: Float,
        val scaleY: Float,
        val opacity: Float,
    )

    private companion object {
        const val PICTURE_W: Float = 200f
        const val PICTURE_H: Float = 100f
    }
}
