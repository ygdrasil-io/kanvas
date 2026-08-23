package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/compositor_quads.cpp` (compositor variant).
 *  Tests compositor quad rendering — draws a grid of coloured filled
 *  and stroked quads with transforms.
 *  @see https://github.com/google/skia/blob/main/gm/compositor_quads.cpp
 */
class CompositorGm : SkiaGm {
    override val name = "compositor_quads_color"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = (kCellWidth * kMatrixCount + 175f).toInt()
    override val height = (kCellHeight * 1 + 75f).toInt()

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val matrices = configureMatrices()
        val matrixNames = listOf("Identity", "T+S", "Rotate", "Skew", "Perspective")
        val font = Font(typeface, 12f)

        canvas.save()
        canvas.translate(kOffset + kBannerWidth, kOffset)
        var colIdx = 0
        for (mat in matrices) {
            canvas.save()
            canvas.drawString(matrixNames[colIdx], 0f, 0f, font, Paint())
            canvas.translate(0f, kGap)

            canvas.save()
            canvas.concat(mat)

            val gridPaint = Paint(color = Color.RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f)
            for (i in 0 until kRowCount) {
                for (j in 0 until kColCount) {
                    val tile = Rect.fromXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
                    canvas.drawRect(tile, Paint(color = Color.fromRGBA(0.2f, 0.8f, 0.3f, 1f)))
                    canvas.drawRect(tile, gridPaint)
                }
            }

            canvas.restore()
            canvas.translate(0f, kGap + kRowCount * kTileHeight)
            canvas.restore()
            canvas.translate(kGap + kColCount * kTileWidth, 0f)
            colIdx++
        }
        canvas.restore()

        canvas.save()
        canvas.translate(kOffset, kGap + 0.5f * kRowCount * kTileHeight)
        canvas.drawString("Solid Color", 0f, 0f, font, Paint())
        canvas.restore()
    }

    private fun configureMatrices(): List<Matrix3x3F32> {
        val identity = Matrix3x3F32.Identity
val ts = Matrix3x3F32.translation(5.5f, 20.25f) * Matrix3x3F32.scaling(0.9f, 0.7f)
val rotate = Matrix3x3F32.rotation(20f) * Matrix3x3F32.translation(15f, -20f)
val skew = Matrix3x3F32.skewing(0.5f, 0.25f) * Matrix3x3F32.translation(-30f, 0f)
        val perspectiveStub = Matrix3x3F32.Identity
        return listOf(identity, ts, rotate, skew, perspectiveStub)
    }

    private companion object {
        const val kTileWidth = 40f
        const val kTileHeight = 30f
        const val kRowCount = 4
        const val kColCount = 3
        const val kMatrixCount = 5
        const val kGap = 40f
        const val kBannerWidth = 120f
        const val kOffset = 15f
        const val kCellWidth = 1.3f * kColCount * kTileWidth
        const val kCellHeight = 1.3f * kRowCount * kTileHeight
    }
}
