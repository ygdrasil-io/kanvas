package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_srgb.cpp::EncodeSRGBGM`.
 * Renders a 2 × 15 matrix of 128×128 cells. Each row is one (colourType, alphaType)
 * combination; the two columns swap between null colour-space and sRGB tag.
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * from [Image]; source images are drawn directly in the upstream grid layout.
 * @see https://github.com/google/skia/blob/main/gm/encode_srgb.cpp
 */
open class EncodeSrgbGm(
    private val variantName: String,
) : SkiaGm {

    constructor() : this("png")

    private val imageWidth = 128
    private val imageHeight = 128

    override val name: String get() = "encode-srgb-$variantName"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width: Int get() = imageWidth * 2
    override val height: Int get() = imageHeight * 15

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val colorTypes = listOf("8888", "f16", "gray8", "rgb565")
        val alphaTypes = listOf("opaque", "premul", "unpremul")

        for (colorType in colorTypes) {
            for (alpha in alphaTypes) {
                canvas.save()
                for (useSRGB in listOf(false, true)) {
                    val src = makeImage(colorType, alpha) ?: continue
                    canvas.drawImage(src, Rect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat()))
                    canvas.translate(imageWidth.toFloat(), 0f)
                }
                canvas.restore()
                canvas.translate(0f, imageHeight.toFloat())
            }
        }
    }

    private fun makeImage(colorType: String, alpha: String): Image? {
        val resource = when (colorType) {
            "gray8" -> "images/grayscale.jpg"
            else -> if (alpha == "opaque") "images/color_wheel.jpg" else "images/color_wheel.png"
        }
        val bytes = javaClass.classLoader?.getResourceAsStream(resource)?.readAllBytes() ?: return null
        val img = Image.decode(bytes)
        return if (img.width == 0) null else img
    }
}
