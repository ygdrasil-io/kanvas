/**
 * Port of Skia's `gm/pictureshader.cpp`.
 * Exercises [Picture.asShader] with tile modes, local matrices, and alpha.
 * Three variants: pictureshader (default), pictureshader_localwrapper
 * (via [Shader.WithLocalMatrix]), pictureshader_alpha (alpha=0.25).
 * @see https://github.com/google/skia/blob/main/gm/pictureshader.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

abstract class PictureShaderBaseGm(
    protected val tileSize: Float,
    protected val sceneSize: Float,
    protected val useLocalMatrixWrapper: Boolean,
    protected val alpha: Float,
) : SkiaGm {
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1400
    override val height = 1450

    protected var picture: Picture? = null

    private fun lazyInit() {
        if (picture == null) {
            val recorder = PictureRecorder()
            val pictureCanvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, tileSize, tileSize))
            drawTile(pictureCanvas)
            picture = recorder.finishRecordingAsPicture()
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        lazyInit()

        drawSceneColumn(canvas, 0f, 0f, 1f, 1f, 0)
        drawSceneColumn(canvas, 0f, sceneSize * 6.4f, 1f, 2f, 0)
        drawSceneColumn(canvas, sceneSize * 2.4f, 0f, 1f, 1f, 1)
        drawSceneColumn(canvas, sceneSize * 2.4f, sceneSize * 6.4f, 1f, 1f, 2)
        drawSceneColumn(canvas, sceneSize * 4.8f, 0f, 2f, 1f, 0)
        drawSceneColumn(canvas, sceneSize * 9.6f, 0f, 2f, 2f, 0)

        var ctm = Matrix33.scale(-1f, -1f) * Matrix33.translate(sceneSize * 2.1f, sceneSize * 13.8f)
        var localMatrix = Matrix33.scale(2f, 2f)
        drawScene(canvas, ctm, localMatrix, 0)

        ctm = Matrix33.translate(sceneSize * 2.4f, sceneSize * 12.8f)
        localMatrix = Matrix33.scale(-1f, -1f)
        drawScene(canvas, ctm, localMatrix, 0)

        ctm = Matrix33.scale(2f, 2f) * Matrix33.translate(sceneSize * 4.8f, sceneSize * 12.3f)
        drawScene(canvas, ctm, localMatrix, 0)

        ctm = Matrix33.scale(-2f, -2f) * Matrix33.translate(sceneSize * 13.8f, sceneSize * 14.3f)
        localMatrix = Matrix33.scale(-2f, -2f) * Matrix33.rotate(45f) * Matrix33.translate(tileSize / 4f, tileSize / 4f)
        drawScene(canvas, ctm, localMatrix, 0)
    }

    private fun drawSceneColumn(
        canvas: GmCanvas,
        x: Float,
        y: Float,
        scale: Float,
        localScale: Float,
        tileMode: Int,
    ) {
        val rows: Array<Pair<Float, () -> Matrix33>> = arrayOf(
            0f to { Matrix33.scale(localScale, localScale) },
            1.2f to {
                Matrix33.scale(localScale, localScale) * Matrix33.translate(tileSize / 4f, tileSize / 4f)
            },
            2.4f to { Matrix33.scale(localScale, localScale) * Matrix33.rotate(45f) },
            3.6f to { Matrix33.scale(localScale, localScale) * Matrix33.skew(1f, 0f) },
            4.8f to {
                Matrix33.scale(localScale, localScale) * Matrix33.rotate(45f) * Matrix33.translate(tileSize / 4f, tileSize / 4f)
            },
        )
        for ((yMul, lmBuilder) in rows) {
            val ctm = Matrix33.scale(scale, scale) * Matrix33.translate(x, y + sceneSize * yMul * scale)
            drawScene(canvas, ctm, lmBuilder(), tileMode)
        }
    }

    private fun drawTile(canvas: Canvas) {
        var paint = Paint(color = Color.GREEN, antiAlias = true)
        val circle = Path { }.apply { addCircle(tileSize / 4f, tileSize / 4f, tileSize / 4f) }
        canvas.drawPath(circle, paint)
        canvas.drawRect(
            Rect.fromXYWH(tileSize / 2f, tileSize / 2f, tileSize / 2f, tileSize / 2f),
            paint,
        )
        paint = paint.copy(color = Color.RED)
        canvas.drawPath(Path { moveTo(tileSize / 2f, tileSize * 1f / 3f); lineTo(tileSize / 2f, tileSize * 2f / 3f) }, paint)
        canvas.drawPath(Path { moveTo(tileSize * 1f / 3f, tileSize / 2f); lineTo(tileSize * 2f / 3f, tileSize / 2f) }, paint)
    }

    private fun drawScene(canvas: GmCanvas, matrix: Matrix33, localMatrix: Matrix33, tileMode: Int) {
        val tile = kTileConfigs[tileMode]
        val ltGray = Color.fromRGBA(0.827f, 0.827f, 0.827f)
        val fillPaint = Paint(color = ltGray)

        canvas.save()
        canvas.concat(matrix)
        val sceneRect = Rect.fromLTRB(0f, 0f, sceneSize, sceneSize)
        val rightSceneRect = Rect.fromXYWH(sceneSize * 1.1f, 0f, sceneSize, sceneSize)
        canvas.drawRect(sceneRect, fillPaint)
        canvas.drawRect(rightSceneRect, fillPaint)

        val tinted = if (alpha < 1f) {
            fillPaint.copy(color = Color.fromRGBA(0.827f, 0.827f, 0.827f, alpha))
        } else {
            fillPaint
        }

        // Picture shader half (left column).
        val baseLm: Matrix33? = if (useLocalMatrixWrapper) null else localMatrix
        val pictureBaseShader: Shader = picture!!.asShader(
            tileX = tile.tmx, tileY = tile.tmy,
            sampling = SamplingOptions.NEAREST,
            matrix = baseLm,
        )
        val pictureShader: Shader = if (useLocalMatrixWrapper) {
            Shader.WithLocalMatrix(pictureBaseShader, localMatrix)
        } else {
            pictureBaseShader
        }
        canvas.drawRect(sceneRect, tinted.copy(shader = pictureShader))

        // Bitmap shader half (right column) omitted — Kanvas has no SkBitmap/SkCanvas raster equivalent.
        // The original GM drew the same tile into a SkBitmap via SkCanvas(SkBitmap) and used
        // SkBitmap.makeShader for visual cross-check.

        canvas.restore()
    }

    private data class TileConfig(val tmx: TileMode, val tmy: TileMode)

    companion object {
        private val kTileConfigs: Array<TileConfig> = arrayOf(
            TileConfig(TileMode.REPEAT, TileMode.REPEAT),
            TileConfig(TileMode.REPEAT, TileMode.MIRROR),
            TileConfig(TileMode.MIRROR, TileMode.REPEAT),
        )
    }
}

class PictureShaderGm : PictureShaderBaseGm(50f, 100f, false, 1f) {
    override val renderCost = RenderCost.FAST
    override val name = "pictureshader"
}

class PictureShaderLocalWrapperGm : PictureShaderBaseGm(50f, 100f, true, 1f) {
    override val renderCost = RenderCost.FAST
    override val name = "pictureshader_localwrapper"
}

class PictureShaderAlphaGm : PictureShaderBaseGm(50f, 100f, false, 0.25f) {
    override val renderCost = RenderCost.MEDIUM
    override val name = "pictureshader_alpha"
}
