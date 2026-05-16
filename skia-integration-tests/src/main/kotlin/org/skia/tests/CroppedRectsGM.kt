package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of upstream Skia's `gm/croppedrects.cpp` :
 * `DEF_GM(return new CroppedRectsGM();)`.
 *
 * Exercises three drawing code-paths that pre-crop filled rects
 * by the active clip : `drawPaint` (clip the shader), `drawImageRect`
 * (crop src/dst), `drawPath` with a stroked line that clips out
 * the wide stroke.
 *
 * Source image : 500×500 red background with a green 200×200 square
 * inside (clipped region). The expected output renders only the
 * green portions — no visible red bleed.
 */
public class CroppedRectsGM : GM() {

    override fun getName(): String = "croppedrects"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    private var fSrcImage: SkImage? = null
    private var fSrcShader: SkShader? = null

    private fun createImage(): SkImage {
        val srcSurface = SkSurface.MakeRasterN32Premul(500, 500)
        val srcCanvas = srcSurface.canvas
        srcCanvas.clear(SK_ColorRED)
        val paint = SkPaint().apply { color = SkColorSetARGB(0xFF, 0, 0xFF, 0) }   // 0xFF00FF00
        srcCanvas.drawRect(K_SRC_IMAGE_CLIP, paint)
        val strokeWidth = 10f
        val stroke = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            this.strokeWidth = strokeWidth
            color = SkColorSetARGB(0xFF, 0, 0x88, 0)         // 0xFF008800
        }
        srcCanvas.drawRect(
            K_SRC_IMAGE_CLIP.makeInset(strokeWidth / 2f, strokeWidth / 2f),
            stroke,
        )
        return srcSurface.makeImageSnapshot()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (fSrcImage == null) {
            fSrcImage = createImage()
            fSrcShader = fSrcImage!!.makeShader()
        }
        c.clear(SK_ColorWHITE)

        val img = fSrcImage!!
        val shader = fSrcShader!!

        // (1) drawPaint with clip.
        val saveCount1 = c.save()
        c.clipRect(K_SRC_IMAGE_CLIP)
        c.drawPaint(SkPaint().apply { this.shader = shader })
        c.restoreToCount(saveCount1)

        // (2) drawImageRect with src/dst cropping.
        val saveCount2 = c.save()
        val drawRect = SkRect.MakeXYWH(350f, 100f, 100f, 300f)
        c.clipRect(drawRect)
        c.drawImageRect(
            img,
            K_SRC_IMAGE_CLIP.makeOutset(0.5f * K_SRC_IMAGE_CLIP.width(), K_SRC_IMAGE_CLIP.height()),
            drawRect.makeOutset(0.5f * drawRect.width(), drawRect.height()),
            SkSamplingOptions.Default,
            null,
            SrcRectConstraint.kStrict,
        )
        c.restoreToCount(saveCount2)

        // (3) drawPath with stroked-line shader.
        val saveCount3 = c.save()
        val path = SkPath.Line(
            (K_SRC_IMAGE_CLIP.left - K_SRC_IMAGE_CLIP.width()) to K_SRC_IMAGE_CLIP.centerY(),
            (K_SRC_IMAGE_CLIP.right + 3f * K_SRC_IMAGE_CLIP.width()) to K_SRC_IMAGE_CLIP.centerY(),
        )
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f * K_SRC_IMAGE_CLIP.height()
            this.shader = shader
        }
        c.translate(23f, 301f)
        c.scale(300f / K_SRC_IMAGE_CLIP.width(), 100f / K_SRC_IMAGE_CLIP.height())
        c.translate(-K_SRC_IMAGE_CLIP.left, -K_SRC_IMAGE_CLIP.top)
        c.clipRect(K_SRC_IMAGE_CLIP)
        c.drawPath(path, paint)
        c.restoreToCount(saveCount3)
    }

    private companion object {
        private val K_SRC_IMAGE_CLIP: SkRect = SkRect.MakeLTRB(75f, 75f, 275f, 275f)
    }
}
