package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RtifUnsharpWgsl
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeimagefilter.cpp::rtif_unsharp` (512 x 256).
 *
 * Left: raw mandrill image. Right: unsharp mask via runtime-shader
 * ImageFilter with a blurred child DAG node.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeimagefilter.cpp
 */
class RtifUnsharpGm : SkiaGm {
    override val name = "rtif_unsharp"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.00152587890625
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = syntheticMandrill()
        val effect = RuntimeEffect.compile(RtifUnsharpWgsl).getOrThrow()
        val filter = ImageFilter.RuntimeEffect(
            effect, UniformBlock {},
            childImageFilters = mapOf(
                "content" to null,
                "blurred" to ImageFilter.Blur(1f, 1f, input = null),
            ),
        )

        // Left: raw
        canvas.drawImage(img, Rect(0f, 0f, 256f, 256f))

        // Right: sharpened via saveLayer + ImageFilter.RuntimeEffect
        canvas.save()
        canvas.translate(256f, 0f)
        canvas.saveLayer(Rect(0f, 0f, 256f, 256f), Paint(imageFilter = filter))
        canvas.drawImage(img, Rect(0f, 0f, 256f, 256f))
        canvas.restore()
        canvas.restore()
    }

    private fun syntheticMandrill(): Image {
        val pixels = IntArray(256 * 256)
        for (y in 0 until 256) for (x in 0 until 256) {
            pixels[y * 256 + x] = (0xFF shl 24) or ((x and 0xFF) shl 16) or ((y and 0xFF) shl 8) or (((x + y) / 2) and 0xFF)
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
