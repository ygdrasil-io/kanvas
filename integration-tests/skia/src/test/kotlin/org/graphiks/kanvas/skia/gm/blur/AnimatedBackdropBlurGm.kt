package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedBackdropBlur`.
 *
 * Port contract: draw the scrolling text and `color_wheel.png`, then apply a
 * backdrop `SaveLayerRec` filter chain of Crop(Decal) -> Blur(30) ->
 * Crop(Mirror) over `(0, 100, 512, 400)`. GmCanvas does not yet expose a
 * backdrop filter, so this GM remains explicitly unsupported.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedBackdropBlurGm : SkiaGm {
    override val name = "animated-backdrop-blur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 20f,
    )
    private var colorWheel: Image? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = javaClass.classLoader?.getResourceAsStream("images/color_wheel.png")?.readBytes()
        colorWheel = bytes?.let(Image::decode)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = arrayOf(
            "Lorem ipsum dolor sit amet,",
            "consectetur adipiscing elit,",
            "sed do eiusmod tempor incididunt",
            "ut labore et dolore magna aliqua.",
            "", "",
            "Ut enim ad minim veniam,",
            "quis nostrud exercitation ullamco laboris",
            "nisi ut aliquip ex ea commodo consequat.",
            "", "",
            "Duis aute irure dolor in reprehenderit",
            "in voluptate velit esse cillum dolore",
            "eu fugiat nulla pariatur.",
        )
        var verticalOffset = 0f
        for (line in text) {
            canvas.drawString(line, 0f, verticalOffset, font, Paint())
            verticalOffset += font.size
        }

        colorWheel?.let { image ->
            val destinationHeight = image.height * 128f / image.width
            canvas.drawImage(image, Rect.fromXYWH(16f, 0f, 128f, destinationHeight))
        }

        val crop = Rect.fromLTRB(0f, 100f, 512f, 400f)
        canvas.saveLayer(
            SaveLayerRec(
                backdrop = ImageFilter.Crop(
                    crop = crop,
                    tileMode = TileMode.DECAL,
                    input = ImageFilter.Blur(
                        sigmaX = 30f,
                        sigmaY = 30f,
                        input = ImageFilter.Crop(crop, TileMode.MIRROR),
                    ),
                ),
            ),
        )
        canvas.restore()
    }
}
