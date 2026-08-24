package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RtifDistortWgsl
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32
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
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 750

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(RtifDistortWgsl).getOrThrow()
        val filter = ImageFilter.RuntimeEffect(effect, UniformBlock {}, childShaderName = "child")
        val filterPaint = Paint(imageFilter = filter)
        val clip = RectF32(0f, 0f, 250f, 250f)
        val colPositions = listOf(0f, 250f)
        val rowPositions = listOf(0f, 250f, 500f)
        val transforms = listOf(
            Matrix3x3F32.Identity,
            Matrix3x3F32.scaling(0.5f, 0.5f),
            Matrix3x3F32.rotation(45f),
            Matrix3x3F32.scaling(0.5f, 0.5f) * Matrix3x3F32.rotation(45f),
            Matrix3x3F32.skewing(-0.5f, 0f),
            Matrix3x3F32.of(1f, 0f, 0.0015f, 0f, 1f, -0.0015f, 0f, 0f, 1f),
        )
        var idx = 0
        for (row in rowPositions) for (col in colPositions) {
            if (idx >= transforms.size) break
            drawLayer(canvas, col, row, transforms[idx], clip, filterPaint)
            idx++
        }
    }

    private fun drawLayer(
        canvas: GmCanvas, tx: Float, ty: Float, m: Matrix3x3F32,
        clip: RectF32, filterPaint: Paint,
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
            canvas.drawString(str, x, y, font, Paint(color = ColorARGB.fromRGBA(r / 255f, g / 255f, b / 255f)))
        }
        canvas.restore()
        canvas.restore()
    }
}
