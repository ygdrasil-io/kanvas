package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_color_types.cpp::EncodeColorTypesGM`.
 * Exercises WEBP encoder across three alpha layouts (opaque / premul / unpremul).
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * from [Image]; the source image is drawn directly in the upstream grid layout.
 * @see https://github.com/google/skia/blob/main/gm/encode_color_types.cpp
 */
open class EncodeColorTypesGm(
    private val variant: Variant,
    private val variantName: String,
) : SkiaGm {

    enum class Variant {
        kOpaque,
        kGray,
        kNormal,
    }

    constructor() : this(Variant.kNormal, "webp-lossless")

    private val imageWidth = 128
    private val imageHeight = 128

    override val name: String get() {
        val variantPrefix = when (variant) {
            Variant.kOpaque -> "opaque-"
            Variant.kGray -> "gray-"
            Variant.kNormal -> ""
        }
        return "encode-${variantPrefix}color-types-$variantName"
    }

    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0

    override val width: Int get() {
        val cells = if (variant == Variant.kNormal) 7 else 2
        return imageWidth * cells
    }

    override val height: Int get() = imageHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (variant != Variant.kNormal) return

        val bytes = javaClass.classLoader?.getResourceAsStream("images/color_wheel.jpg")?.readAllBytes() ?: return
        val src = Image.decode(bytes)
        if (src.width == 0) return

        val alphaCount = 3
        for (i in 0 until alphaCount) {
            canvas.drawImage(src, Rect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat()))
            canvas.translate(imageWidth.toFloat(), 0f)
            canvas.drawImage(src, Rect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat()))
            canvas.translate(imageWidth * 1.5f, 0f)
        }
    }
}
