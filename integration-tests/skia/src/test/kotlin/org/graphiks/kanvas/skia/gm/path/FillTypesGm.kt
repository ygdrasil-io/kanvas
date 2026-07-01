package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class FillTypesGm : SkiaGm {
    override val name = "filltypes"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 20.3
    override val width = 835
    override val height = 840

    private val path: Path = Path { }.also {
        it.addCircle(50f, 50f, 45f)
        it.addCircle(100f, 100f, 45f)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(223f / 255f, 223f / 255f, 223f / 255f, 1f)),
        )

        canvas.translate(20f, 20f)

        var paint = Paint(antiAlias = false)
        showFour(canvas, 1f, paint)
        canvas.translate(450f, 0f)
        showFour(canvas, 5f / 4f, paint)

        paint = paint.copy(antiAlias = true)

        canvas.translate(-450f, 450f)
        showFour(canvas, 1f, paint)
        canvas.translate(450f, 0f)
        showFour(canvas, 5f / 4f, paint)
    }

    private fun showFour(canvas: GmCanvas, scale: Float, paint: Paint) {
        showPath(canvas, 0, 0, FillType.WINDING, scale, paint)
        showPath(canvas, 200, 0, FillType.EVEN_ODD, scale, paint)
        showPath(canvas, 0, 200, FillType.INVERSE_WINDING, scale, paint)
        showPath(canvas, 200, 200, FillType.INVERSE_EVEN_ODD, scale, paint)
    }

    private fun showPath(
        canvas: GmCanvas,
        x: Int, y: Int,
        ft: FillType,
        scale: Float,
        paint: Paint,
    ) {
        val rect = Rect(0f, 0f, 150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(rect)
        canvas.drawRect(rect, Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f)))
        val centerX = rect.left + rect.width / 2f
        val centerY = rect.top + rect.height / 2f
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        canvas.translate(-centerX, -centerY)
        val typed = Path { }.also {
            it.addCircle(50f, 50f, 45f)
            it.addCircle(100f, 100f, 45f)
            it.fillType = ft
        }
        canvas.drawPath(typed, paint)
        canvas.restore()
    }
}
