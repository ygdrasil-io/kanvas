package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class FillTypesGm : SkiaGm {
    override val name = "filltypes"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 835
    override val height = 840

    private val path: Path = Path().apply {
        addCircle(50f, 50f, 45f)
        addCircle(100f, 100f, 45f)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint().apply { r = 223f / 255f; g = 223f / 255f; b = 223f / 255f; a = 1f },
        )

        canvas.translate(20f, 20f)

        val paint = Paint().apply { antiAlias = false }
        showFour(canvas, 1f, paint)
        canvas.translate(450f, 0f)
        showFour(canvas, 5f / 4f, paint)

        paint.antiAlias = true

        canvas.translate(-450f, 450f)
        showFour(canvas, 1f, paint)
        canvas.translate(450f, 0f)
        showFour(canvas, 5f / 4f, paint)
    }

    private fun showFour(canvas: GmCanvas, scale: Float, paint: Paint) {
        showPath(canvas, 0, 0, KanvasFillType.WINDING, scale, paint)
        showPath(canvas, 200, 0, KanvasFillType.EVEN_ODD, scale, paint)
        showPath(canvas, 0, 200, KanvasFillType.INVERSE_WINDING, scale, paint)
        showPath(canvas, 200, 200, KanvasFillType.INVERSE_EVEN_ODD, scale, paint)
    }

    private fun showPath(
        canvas: GmCanvas,
        x: Int, y: Int,
        ft: KanvasFillType,
        scale: Float,
        paint: Paint,
    ) {
        val rect = Rect(0f, 0f, 150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(rect)
        canvas.drawRect(rect, Paint().apply { r = 1f; g = 1f; b = 1f; a = 1f })
        val centerX = rect.left + rect.width / 2f
        val centerY = rect.top + rect.height / 2f
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        canvas.translate(-centerX, -centerY)
        val typed = Path().apply {
            addCircle(50f, 50f, 45f)
            addCircle(100f, 100f, 45f)
            fillType = ft
        }
        canvas.drawPath(typed, paint)
        canvas.restore()
    }
}
