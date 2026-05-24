package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of `DEF_SIMPLE_GM(clip_shader_persp, canvas, 1370, 1030)` in
 * `gm/complexclip.cpp`.
 *
 * Draws a 3×2 grid of cells, each applying a perspective matrix and two
 * nested clip-shaders (one from an image, one from a radial gradient) in
 * varying orders, with or without local-matrix wrappers, to prove that the
 * clip-shader pipeline respects the perspective transform regardless of
 * when `concat(persp)` is called relative to the two `clipShader` calls.
 *
 * Reference image: `clip_shader_persp.png`, 1370 × 1030.
 */
public class ClipShaderPerspGM : GM() {

    override fun getName(): String = "clip_shader_persp"
    override fun getISize(): SkISize = SkISize.Make(1370, 1030)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val img = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        val scale = SkMatrix.MakeScale(0.25f, 0.25f)
        val imgRect = SkRect.MakeIWH(img.width, img.height)

        // Build the perspective matrix that maps the image quad to a trapezoid.
        // Upstream: SkMatrix persp; SkAssertResult(persp.setPolyToPoly(src, dst));
        val src = arrayOf(
            SkPoint.Make(0f, 0f),
            SkPoint.Make(img.width.toFloat(), 0f),
            SkPoint.Make(img.width.toFloat(), img.height.toFloat()),
            SkPoint.Make(0f, img.height.toFloat()),
        )
        val dst = arrayOf(
            SkPoint.Make(0f, 80f),
            SkPoint.Make(img.width + 28f, -100f),
            SkPoint.Make(img.width - 28f, img.height + 100f),
            SkPoint.Make(0f, img.height - 80f),
        )
        val persp = SkMatrix.setPolyToPoly(src, dst) ?: return
        val perspScale = SkMatrix.concat(persp, scale)
        val grid = persp.mapRect(imgRect).roundOut().apply {
            left -= 20
        }

        val matches = arrayOf(
            arrayOf(
                Config(ConcatPerspective.BEFORE_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.BOTH_WITH_LOCAL_MATRIX),
            ),
            arrayOf(
                Config(ConcatPerspective.BETWEEN_CLIPS, ClipOrder.GRADIENT_FIRST, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.IMAGE_WITH_LOCAL_MATRIX),
            ),
            arrayOf(
                Config(ConcatPerspective.BETWEEN_CLIPS, ClipOrder.IMAGE_FIRST, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX),
            ),
        )

        c.translate(10f, 10f)
        for (pair in matches) {
            c.save()
            c.translate(-grid.left.toFloat(), -grid.top.toFloat())
            drawConfig(c, img, imgRect, scale, persp, perspScale, pair[0])
            c.translate(0f, grid.height().toFloat())
            drawConfig(c, img, imgRect, scale, persp, perspScale, pair[1])
            c.restore()

            c.translate(grid.width().toFloat(), 0f)
        }
    }

    private fun drawConfig(
        canvas: SkCanvas,
        img: SkImage,
        imgRect: SkRect,
        scale: SkMatrix,
        persp: SkMatrix,
        perspScale: SkMatrix,
        config: Config,
    ) {
        canvas.save()

        drawBanner(canvas, config)

        val gradLM = config.localMatrix == LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX ||
            config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX
        val gradCenter = SkPoint(0.5f * img.width, 0.5f * img.height)
        val gradColors = intArrayOf(SK_ColorBLACK, SkColorSetARGB(128, 128, 128, 128))
        val gradShader = if (gradLM) {
            SkRadialGradient.Make(
                gradCenter,
                0.1f * img.width,
                gradColors,
                null,
                SkTileMode.kRepeat,
                persp,
            )
        } else {
            SkRadialGradient.Make(
                gradCenter,
                0.1f * img.width,
                gradColors,
                null,
                SkTileMode.kRepeat,
            )
        }

        val imageLM = config.localMatrix == LocalMatrix.IMAGE_WITH_LOCAL_MATRIX ||
            config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX
        val imgShader = img.makeShader(
            SkTileMode.kRepeat,
            SkTileMode.kRepeat,
            SkSamplingOptions(),
            if (imageLM) perspScale else scale,
        )

        if (config.concatPerspective == ConcatPerspective.BEFORE_CLIPS) {
            canvas.concat(persp)
        }

        canvas.clipShader(firstShader(config, imgShader, gradShader))

        if (config.concatPerspective == ConcatPerspective.BETWEEN_CLIPS) {
            canvas.concat(persp)
        }

        canvas.clipShader(secondShader(config, imgShader, gradShader))

        if (config.concatPerspective == ConcatPerspective.AFTER_CLIPS) {
            canvas.concat(persp)
        }

        canvas.clipRect(imgRect)
        canvas.clear(SK_ColorBLACK)
        canvas.drawImage(img, 0f, 0f)

        canvas.restore()
    }

    private fun firstShader(config: Config, imgShader: SkShader, gradShader: SkShader): SkShader =
        if (config.clipOrder == ClipOrder.IMAGE_FIRST) imgShader else gradShader

    private fun secondShader(config: Config, imgShader: SkShader, gradShader: SkShader): SkShader =
        if (config.clipOrder == ClipOrder.IMAGE_FIRST) gradShader else imgShader

    private fun drawBanner(canvas: SkCanvas, config: Config) {
        val perspectiveTarget =
            if (config.concatPerspective == ConcatPerspective.BEFORE_CLIPS ||
                config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX
            ) {
                "Both Clips"
            } else if (
                (config.concatPerspective == ConcatPerspective.BETWEEN_CLIPS &&
                    config.clipOrder == ClipOrder.IMAGE_FIRST) ||
                config.localMatrix == LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX
            ) {
                "Gradient"
            } else {
                "Image"
            }
        val suffix = if (config.localMatrix == LocalMatrix.NO_LOCAL_MATRIX) {
            ""
        } else {
            " (w/ LM, should equal top row)"
        }
        canvas.drawString(
            "Persp: $perspectiveTarget$suffix",
            20f,
            -30f,
            SkFont(ToolUtils.DefaultPortableTypeface(), 12f),
            SkPaint(),
        )
    }

    private enum class ConcatPerspective { BEFORE_CLIPS, AFTER_CLIPS, BETWEEN_CLIPS }

    private enum class ClipOrder { IMAGE_FIRST, GRADIENT_FIRST, DOES_NOT_MATTER }

    private enum class LocalMatrix {
        NO_LOCAL_MATRIX,
        IMAGE_WITH_LOCAL_MATRIX,
        GRADIENT_WITH_LOCAL_MATRIX,
        BOTH_WITH_LOCAL_MATRIX,
    }

    private data class Config(
        val concatPerspective: ConcatPerspective,
        val clipOrder: ClipOrder,
        val localMatrix: LocalMatrix,
    )
}
