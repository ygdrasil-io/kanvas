package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ColorCubeRTWgsl
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
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp::ColorCubeRT` (512 x 512).
 *
 * Applies a 3D LUT color transform via a runtime shader with two
 * child shaders: one for the source image, one for the LUT texture.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class ColorCubeRTGm : SkiaGm {
    override val name = "color_cube_rt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = synthetic256()
        val lutImg = syntheticLUT(32, 32)

        val effect = RuntimeEffect.compile(ColorCubeRTWgsl).getOrThrow()
        val childShader = Shader.Image(img, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
        val lutShader = Shader.WithLocalMatrix(
            Shader.Image(lutImg, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST),
            Matrix33.identity(),
        )

        val uniforms = UniformBlock {
            float1("rg_scale", 0.5f)
            float1("rg_bias", 0.25f)
            float1("b_scale", 0.5f)
            float1("inv_size", 1f / 32f)
        }
        val shader = effect.makeShader(
            uniforms,
            mapOf("child" to childShader, "color_cube" to lutShader),
        )
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }

    private fun synthetic256(): Image {
        val pixels = IntArray(256 * 256)
        for (y in 0 until 256) for (x in 0 until 256) {
            pixels[y * 256 + x] = (0xFF shl 24) or (x shl 16) or (y shl 8) or ((x + y) / 2)
        }
        return Image.fromPixels(256, 256, intsToRGBA(pixels))
    }

    private fun syntheticLUT(w: Int, h: Int): Image {
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val r = (x * 255 / (w - 1)).coerceIn(0, 255)
            val g = (y * 255 / (h - 1)).coerceIn(0, 255)
            val b = ((x + y) * 255 / (w + h - 2)).coerceIn(0, 255)
            pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
