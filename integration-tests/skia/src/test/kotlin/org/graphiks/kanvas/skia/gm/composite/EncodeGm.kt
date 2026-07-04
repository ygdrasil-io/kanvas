package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class EncodeGm : SkiaGm {
    override val name = "encode"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 600

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sourceBytes = javaClass.classLoader?.getResourceAsStream("images/mandrill_512_q075.jpg")
            ?.readAllBytes() ?: return
        val pngImage = Image.decode(sourceBytes, "image/jpeg")
        if (pngImage.width == 0) return

        canvas.drawImage(pngImage, Rect.fromXYWH(0f, 0f, 512f, 512f))
        canvas.drawImage(pngImage, Rect.fromXYWH(512f, 0f, 512f, 512f))

        val font = Font(typeface, 12f)
        canvas.drawString("Images should look identical.", 450f, 550f, font, Paint())
    }
}
