package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.UnsharpRTWgsl
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp::UnsharpRT` (512 x 256).
 *
 * Left half: the original mandrill image drawn unmodified.
 * Right half: the same image sharpened via a 5-tap unsharp-mask
 * runtime shader.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class UnsharpRTGm : SkiaGm {
    override val name = "unsharp_rt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 1.0223388671875
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = syntheticMandrill256()

        // Left: unmodified image
        canvas.drawImage(img, Rect(0f, 0f, 256f, 256f))

        // Right: unsharp-masked
        val effect = RuntimeEffect.compile(UnsharpRTWgsl).getOrThrow()
        val childShader = Shader.Image(img, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
        val shader = effect.makeShader(UniformBlock {}, mapOf("child" to childShader))
        canvas.save()
        canvas.translate(256f, 0f)
        canvas.drawRect(Rect(0f, 0f, 256f, 256f), Paint(shader = shader))
        canvas.restore()
    }

    private fun syntheticMandrill256(): Image {
        val pixels = IntArray(256 * 256)
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                val r = x and 0xFF
                val g = y and 0xFF
                val b = ((x + y) / 2) and 0xFF
                pixels[y * 256 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Image.fromPixels(256, 256, intsToRGBA(pixels))
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
