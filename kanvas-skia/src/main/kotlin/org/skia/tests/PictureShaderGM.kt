package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.core.withRestore
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorLTGRAY
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Port of Skia's
 * [`gm/pictureshader.cpp::PictureShaderGM`](https://github.com/google/skia/blob/main/gm/pictureshader.cpp).
 *
 * Built as `PictureShaderGM(50, 100, useLocalMatrixWrapper)` — the
 * upstream constructor parameters. Three GM variants are registered :
 *  - `pictureshader` — `(50, 100)`,
 *  - `pictureshader_localwrapper` — `(50, 100, true)`,  ← R-final.2
 *    consumer ; the `useLocalMatrixWrapper` flag turns on the
 *    `pictureShader.makeWithLocalMatrix(localMatrix)` call site.
 *  - `pictureshader_alpha` — `(50, 100, false, 0.25f)`.
 *
 * **Class shape** : a 50×50 picture (`drawTile`) tiled into 100×100
 * scenes through 6 columns × 5 rows of CTM/localMatrix variants, plus
 * a custom row of negative-scaling stress tests at the bottom. The
 * `useLocalMatrixWrapper` branch routes the local matrix through
 * `shader.makeWithLocalMatrix(lm)` instead of passing it to
 * `picture.makeShader(localMatrix=lm)` — both code paths produce the
 * same effective shader by R-final.2's folding contract, but the
 * wrapper path goes through [SkLocalMatrixShader] internally.
 *
 * Output canvas is fixed at 1400 × 1450 (upstream's
 * `SkISize::Make(1400, 1450)`).
 */
public class PictureShaderGM(
    private val tileSize: SkScalar,
    private val sceneSize: SkScalar,
    private val useLocalMatrixWrapper: Boolean = false,
    private val alpha: Float = 1f,
) : GM() {

    private var fPicture: SkPicture? = null
    private var fBitmap: SkBitmap = SkBitmap(1, 1)

    override fun onOnceBeforeDraw() {
        // Build the picture.
        val recorder = SkPictureRecorder()
        val pictureCanvas = recorder.beginRecording(tileSize, tileSize)
        drawTile(pictureCanvas)
        fPicture = recorder.finishRecordingAsPicture()

        // Build a reference bitmap (kept side-by-side with picture so
        // the right-hand column of each scene draws the bitmap-shader
        // variant for visual cross-check).
        val w = kotlin.math.ceil(tileSize.toDouble()).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(tileSize.toDouble()).toInt().coerceAtLeast(1)
        fBitmap = SkBitmap(w, h)
        val bitmapCanvas = SkCanvas(fBitmap)
        drawTile(bitmapCanvas)
    }

    override fun getName(): String {
        val sfx1 = if (useLocalMatrixWrapper) "_localwrapper" else ""
        val sfx2 = if (alpha < 1f) "_alpha" else ""
        return "pictureshader$sfx1$sfx2"
    }

    override fun getISize(): SkISize = SkISize.Make(1400, 1450)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        drawSceneColumn(c, SkPoint.Make(0f, 0f), 1f, 1f, 0)
        drawSceneColumn(c, SkPoint.Make(0f, sceneSize * 6.4f), 1f, 2f, 0)
        drawSceneColumn(c, SkPoint.Make(sceneSize * 2.4f, 0f), 1f, 1f, 1)
        drawSceneColumn(c, SkPoint.Make(sceneSize * 2.4f, sceneSize * 6.4f), 1f, 1f, 2)
        drawSceneColumn(c, SkPoint.Make(sceneSize * 4.8f, 0f), 2f, 1f, 0)
        drawSceneColumn(c, SkPoint.Make(sceneSize * 9.6f, 0f), 2f, 2f, 0)

        // One last custom row to exercise negative scaling.
        var ctm = SkMatrix.MakeTrans(sceneSize * 2.1f, sceneSize * 13.8f).preScale(-1f, -1f)
        var localMatrix = SkMatrix.MakeScale(2f, 2f)
        drawScene(c, ctm, localMatrix, 0)

        ctm = SkMatrix.MakeTrans(sceneSize * 2.4f, sceneSize * 12.8f)
        localMatrix = SkMatrix.MakeScale(-1f, -1f)
        drawScene(c, ctm, localMatrix, 0)

        ctm = SkMatrix.MakeTrans(sceneSize * 4.8f, sceneSize * 12.3f).preScale(2f, 2f)
        drawScene(c, ctm, localMatrix, 0)

        ctm = SkMatrix.MakeTrans(sceneSize * 13.8f, sceneSize * 14.3f).preScale(-2f, -2f)
        localMatrix = SkMatrix.MakeTrans(tileSize / 4f, tileSize / 4f)
            .preRotate(45f).preScale(-2f, -2f)
        drawScene(c, ctm, localMatrix, 0)
    }

    private fun drawSceneColumn(
        canvas: SkCanvas, pos: SkPoint, scale: SkScalar, localScale: SkScalar, tileMode: Int,
    ) {
        val rows = arrayOf(
            // (rowYMul, localMatrix builder)
            0f to { SkMatrix.MakeScale(localScale, localScale) },
            1.2f to {
                SkMatrix.MakeTrans(tileSize / 4f, tileSize / 4f).preScale(localScale, localScale)
            },
            2.4f to { SkMatrix.MakeRotate(45f).preScale(localScale, localScale) },
            3.6f to { SkMatrix.MakeSkew(1f, 0f).preScale(localScale, localScale) },
            4.8f to {
                SkMatrix.MakeTrans(tileSize / 4f, tileSize / 4f)
                    .preRotate(45f).preScale(localScale, localScale)
            },
        )
        for ((yMul, lmBuilder) in rows) {
            val ctm = SkMatrix.MakeTrans(pos.fX, pos.fY + sceneSize * yMul * scale)
                .preScale(scale, scale)
            drawScene(canvas, ctm, lmBuilder(), tileMode)
        }
    }

    private fun drawTile(canvas: SkCanvas) {
        val paint = SkPaint().apply {
            color = SK_ColorGREEN
            style = SkPaint.Style.kFill_Style
            isAntiAlias = true
        }
        canvas.drawCircle(tileSize / 4f, tileSize / 4f, tileSize / 4f, paint)
        canvas.drawRect(
            SkRect.MakeXYWH(tileSize / 2f, tileSize / 2f, tileSize / 2f, tileSize / 2f),
            paint,
        )
        paint.color = SK_ColorRED
        canvas.drawLine(tileSize / 2f, tileSize * 1f / 3f, tileSize / 2f, tileSize * 2f / 3f, paint)
        canvas.drawLine(tileSize * 1f / 3f, tileSize / 2f, tileSize * 2f / 3f, tileSize / 2f, paint)
    }

    private fun drawScene(canvas: SkCanvas, matrix: SkMatrix, localMatrix: SkMatrix, tileMode: Int) {
        val tile = kTileConfigs[tileMode]
        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            color = SK_ColorLTGRAY
        }

        canvas.withRestore {
            concat(matrix)
            drawRect(SkRect.MakeWH(sceneSize, sceneSize), paint)
            drawRect(SkRect.MakeXYWH(sceneSize * 1.1f, 0f, sceneSize, sceneSize), paint)

            paint.alphaf = alpha

            // Picture shader half.
            val baseLm = if (useLocalMatrixWrapper) null else localMatrix
            val pictureBaseShader: SkShader = fPicture!!.makeShader(
                tileX = tile.tmx, tileY = tile.tmy,
                filter = SkFilterMode.kNearest,
                localMatrix = baseLm,
            )
            paint.shader = if (useLocalMatrixWrapper) {
                pictureBaseShader.makeWithLocalMatrix(localMatrix)
            } else {
                pictureBaseShader
            }
            drawRect(SkRect.MakeWH(sceneSize, sceneSize), paint)

            translate(sceneSize * 1.1f, 0f)

            // Bitmap shader half.
            val bitmapBaseShader: SkShader = fBitmap.makeShader(
                tileX = tile.tmx, tileY = tile.tmy,
                sampling = SkSamplingOptions.Default,
                localMatrix = baseLm ?: SkMatrix.Identity,
            )
            paint.shader = if (useLocalMatrixWrapper) {
                bitmapBaseShader.makeWithLocalMatrix(localMatrix)
            } else {
                bitmapBaseShader
            }
            drawRect(SkRect.MakeWH(sceneSize, sceneSize), paint)
        }
    }

    private data class TileConfig(val tmx: SkTileMode, val tmy: SkTileMode)

    private companion object {
        val kTileConfigs: Array<TileConfig> = arrayOf(
            TileConfig(SkTileMode.kRepeat, SkTileMode.kRepeat),
            TileConfig(SkTileMode.kRepeat, SkTileMode.kMirror),
            TileConfig(SkTileMode.kMirror, SkTileMode.kRepeat),
        )
    }
}
