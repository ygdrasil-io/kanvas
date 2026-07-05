package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedBackdropBlur`.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedBackdropBlurGm : SkiaGm {
    override val name = "animated-backdrop-blur"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 20f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val txts = arrayOf(
            "Lorem ipsum dolor sit amet,",
            "consectetur adipiscing elit,",
            "sed do eiusmod tempor incididunt",
            "ut labore et dolore magna aliqua.",
            "",
            "",
            "Ut enim ad minim veniam,",
            "quis nostrud exercitation ullamco laboris",
            "nisi ut aliquip ex ea commodo consequat.",
            "",
            "",
            "Duis aute irure dolor in reprehenderit",
            "in voluptate velit esse cillum dolore",
            "eu fugiat nulla pariatur.",
        )

        var curOffset = 0f
        for (txt in txts) {
            if (txt.isNotEmpty()) {
                canvas.drawString(txt, 0f, curOffset, font, Paint())
            }
            curOffset += 20f
        }

        canvas.saveLayer(
            bounds = Rect.fromLTRB(0f, 100f, 512f, 400f),
            paint = null,
        )
        canvas.restore()
    }
}
