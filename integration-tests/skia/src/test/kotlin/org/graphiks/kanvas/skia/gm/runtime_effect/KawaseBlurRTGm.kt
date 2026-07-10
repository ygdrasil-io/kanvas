package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.KawaseBlurWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.KawaseMixWgsl
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/kawaseblur.cpp` (512 x 512).
 *
 * Dual-pass kawase blur: first pass applies the blur shader,
 * second pass cross-fades blurred + original via mix shader.
 *
 * @see https://github.com/google/skia/blob/main/gm/kawaseblur.cpp
 */
class KawaseBlurRTGm : SkiaGm {
    override val name = "kawase_blur_rt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 76.66666666666667
    override val width = 1280
    override val height = 768

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val original = syntheticCheckerboard(256, 256)
        val blurEffect = RuntimeEffect.compile(KawaseBlurWgsl).getOrThrow()
        val mixEffect = RuntimeEffect.compile(KawaseMixWgsl).getOrThrow()

        // Pass 1: render original through blur shader
        val step = 1f / 256f
        val origShader = Shader.Image(original, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)
        val blurUniforms = UniformBlock {
            float1("in_inverseScale", 1f)
            float2("in_blurOffset", step * 2f, step * 2f)
        }
        val blurShader = blurEffect.makeShader(blurUniforms, mapOf("src" to origShader))
        canvas.saveLayer(null, Paint(shader = blurShader))
        canvas.drawImage(original, Rect(0f, 0f, 256f, 256f))
        canvas.restore()

        // Pass 2: cross-fade blurred result + original
        val blurredImg = canvas.makeImageSnapshot()
        val blurredShader = Shader.Image(blurredImg, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR)
        val mixUniforms = UniformBlock {
            float1("in_inverseScale", 1f)
            float1("in_mix", 0.5f)
        }
        val mixShader = mixEffect.makeShader(
            mixUniforms,
            mapOf("in_blur" to blurredShader, "in_original" to origShader),
        )
        canvas.saveLayer(null, Paint(shader = mixShader))
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))
        canvas.restore()
    }

    private fun syntheticCheckerboard(w: Int, h: Int): Image {
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val on = ((x / 32) + (y / 32)) % 2 == 0
            val v = if (on) 0xFF else 0x00
            pixels[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Image.fromPixels(w, h, intsToRGBA(pixels))
    }

    private fun intsToRGBA(pixels: IntArray): ByteArray {
        val out = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            out[i * 4] = ((p shr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
            out[i * 4 + 2] = (p and 0xFF).toByte()
            out[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
        }
        return out
    }
}
