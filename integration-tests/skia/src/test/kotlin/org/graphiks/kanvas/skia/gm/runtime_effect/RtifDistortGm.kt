package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RtifDistortWgsl
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/runtimeimagefilter.cpp::rtif_distort` (500 x 750).
 *
 * Six 250x250 panels, each rendering 25 random strings through a
 * saveLayer with a runtime-shader image filter that warps x by sin(y/3)*4.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeimagefilter.cpp
 */
class RtifDistortGm : SkiaGm {
    override val name = "rtif_distort"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 750

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(RtifDistortWgsl).getOrThrow()
        val filter = ImageFilter.RuntimeEffect(effect, UniformBlock {}, childShaderName = "child")
        val filterPaint = Paint(imageFilter = filter)
        val clip = Rect(0f, 0f, 250f, 250f)
        val colPositions = listOf(0f, 250f)
        val rowPositions = listOf(0f, 250f, 500f)
        val transforms = listOf(
            Matrix33.identity(),
            Matrix33.scale(0.5f, 0.5f),
            Matrix33.rotate(45f),
            Matrix33.scale(0.5f, 0.5f) * Matrix33.rotate(45f),
            Matrix33.skew(-0.5f, 0f),
            Matrix33.makeAll(1f, 0f, 0.0015f, 0f, 1f, -0.0015f, 0f, 0f, 1f),
        )
        var idx = 0
        for (row in rowPositions) for (col in colPositions) {
            if (idx >= transforms.size) break
            drawLayer(canvas, col, row, transforms[idx], clip, filterPaint)
            idx++
        }
    }

    private fun drawLayer(
        canvas: GmCanvas, tx: Float, ty: Float, m: Matrix33,
        clip: Rect, filterPaint: Paint,
    ) {
        canvas.save()
        canvas.translate(tx, ty)
        canvas.clipRect(clip)
        canvas.concat(m)
        canvas.saveLayer(null, filterPaint)
        val str = "The quick brown fox jumped over the lazy dog."
        val rand = Random(0)
        repeat(25) {
            val x = rand.nextInt(450).toFloat()
            val y = rand.nextInt(450).toFloat()
            val fontSize = rand.nextInt(300).toFloat() + 1f
            val r = rand.nextInt(256)
            val g = rand.nextInt(256)
            val b = rand.nextInt(256)
            val font = Font(typeface, size = fontSize)
            canvas.drawString(str, x, y, font, Paint(color = Color.fromRGBA(r / 255f, g / 255f, b / 255f)))
        }
        canvas.restore()
        canvas.restore()
    }
}
