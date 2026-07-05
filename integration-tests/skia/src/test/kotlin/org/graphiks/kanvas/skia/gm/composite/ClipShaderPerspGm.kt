package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `DEF_SIMPLE_GM(clip_shader_persp, canvas, 1370, 1030)` in
 * `gm/complexclip.cpp`.
 * Draws a 3x2 grid of cells testing perspective + nested clip-shader interaction.
 * @see https://github.com/google/skia/blob/main/gm/complexclip.cpp
 */
class ClipShaderPerspGm : SkiaGm {
    override val name = "clip_shader_persp"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 1370
    override val height = 1030

    companion object {
        private fun perspTransformPoint(matrix: Matrix33, p: Point): Point {
            val w = matrix.persp0 * p.x + matrix.persp1 * p.y + matrix.persp2
            if (w == 0f) return Point(0f, 0f)
            val x = (matrix.scaleX * p.x + matrix.skewX * p.y + matrix.transX) / w
            val y = (matrix.skewY * p.x + matrix.scaleY * p.y + matrix.transY) / w
            return Point(x, y)
        }

        private fun computePerspectiveMatrix(w: Float, h: Float): Matrix33 {
            val dx0 = 0f; val dy0 = 80f
            val dx1 = w + 28f; val dy1 = -100f
            val dx2 = w - 28f; val dy2 = h + 100f
            val dx3 = 0f; val dy3 = h - 80f

            val sx = floatArrayOf(0f, w, w, 0f)
            val sy = floatArrayOf(0f, 0f, h, h)
            val dx = floatArrayOf(dx0, dx1, dx2, dx3)
            val dy = floatArrayOf(dy0, dy1, dy2, dy3)

            val A = Array(8) { FloatArray(9) }
            for (i in 0 until 4) {
                val r = 2 * i
                A[r][0] = sx[i]; A[r][1] = sy[i]; A[r][2] = 1f
                A[r][3] = 0f; A[r][4] = 0f; A[r][5] = 0f
                A[r][6] = -sx[i] * dx[i]; A[r][7] = -sy[i] * dx[i]; A[r][8] = dx[i]

                A[r + 1][0] = 0f; A[r + 1][1] = 0f; A[r + 1][2] = 0f
                A[r + 1][3] = sx[i]; A[r + 1][4] = sy[i]; A[r + 1][5] = 1f
                A[r + 1][6] = -sx[i] * dy[i]; A[r + 1][7] = -sy[i] * dy[i]; A[r + 1][8] = dy[i]
            }

            for (col in 0 until 8) {
                var maxVal = kotlin.math.abs(A[col][col])
                var maxRow = col
                for (row in col + 1 until 8) {
                    val v = kotlin.math.abs(A[row][col])
                    if (v > maxVal) { maxVal = v; maxRow = row }
                }
                if (maxRow != col) {
                    val tmp = A[col]; A[col] = A[maxRow]; A[maxRow] = tmp
                }
                val pivot = A[col][col]
                if (pivot == 0f) continue
                for (j in col until 9) A[col][j] /= pivot
                for (row in 0 until 8) {
                    if (row != col) {
                        val factor = A[row][col]
                        for (j in col until 9) A[row][j] -= factor * A[col][j]
                    }
                }
            }

            val hValues = FloatArray(8) { i -> A[i][8] }
            return Matrix33.makeAll(
                hValues[0], hValues[1], hValues[2],
                hValues[3], hValues[4], hValues[5],
                hValues[6], hValues[7], 1f,
            )
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/yellow_rose.png")?.readBytes() ?: return
        val img = Image.decode(bytes)
        val imgRect = Rect(0f, 0f, img.width.toFloat(), img.height.toFloat())

        val persp = computePerspectiveMatrix(img.width.toFloat(), img.height.toFloat())
        val scale = Matrix33.scale(0.25f, 0.25f)
        val perspScale = persp * scale

        val mappedBounds = computeBoundingBox(persp, imgRect)
        val gridRect = Rect(mappedBounds.left - 20f, mappedBounds.top, mappedBounds.right, mappedBounds.bottom)

        val matches = arrayOf(
            arrayOf(
                Config(ConcatPerspective.BEFORE_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.BOTH_WITH_LOCAL_MATRIX),
            ),
            arrayOf(
                Config(ConcatPerspective.BETWEEN_CLIPS, ClipOrder.GRADIENT_FIRST, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.IMAGE_WITH_LOCAL_MATRIX),
            ),
            arrayOf(
                Config(ConcatPerspective.BETWEEN_CLIPS, ClipOrder.IMAGE_FIRST, LocalMatrix.NO_LOCAL_MATRIX),
                Config(ConcatPerspective.AFTER_CLIPS, ClipOrder.DOES_NOT_MATTER, LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX),
            ),
        )

        canvas.translate(10f, 10f)
        for (pair in matches) {
            canvas.save()
            canvas.translate(-gridRect.left, -gridRect.top)
            drawConfig(canvas, img, imgRect, scale, persp, perspScale, pair[0])
            canvas.translate(0f, gridRect.height)
            drawConfig(canvas, img, imgRect, scale, persp, perspScale, pair[1])
            canvas.restore()
            canvas.translate(gridRect.width, 0f)
        }
    }

    private fun drawConfig(
        canvas: GmCanvas,
        img: Image,
        imgRect: Rect,
        scale: Matrix33,
        persp: Matrix33,
        perspScale: Matrix33,
        config: Config,
    ) {
        canvas.save()
        drawBanner(canvas, config)

        val gradLM = config.localMatrix == LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX ||
            config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX
        val gradCenter = Point(0.5f * img.width, 0.5f * img.height)
        val gradColors = listOf(
            GradientStop(0f, Color.BLACK),
            GradientStop(1f, Color.fromRGBA(0.5f, 0.5f, 0.5f, 0.5f)),
        )
        val gradShader = if (gradLM) {
            Shader.WithLocalMatrix(
                Shader.RadialGradient(gradCenter, 0.1f * img.width, gradColors, TileMode.REPEAT),
                persp,
            )
        } else {
            Shader.RadialGradient(gradCenter, 0.1f * img.width, gradColors, TileMode.REPEAT)
        }

        val imageLM = config.localMatrix == LocalMatrix.IMAGE_WITH_LOCAL_MATRIX ||
            config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX
        val imgShader = if (imageLM) {
            Shader.WithLocalMatrix(
                Shader.Image(img, TileMode.REPEAT, TileMode.REPEAT, SamplingOptions.LINEAR),
                perspScale,
            )
        } else {
            Shader.WithLocalMatrix(
                Shader.Image(img, TileMode.REPEAT, TileMode.REPEAT, SamplingOptions.LINEAR),
                scale,
            )
        }

        val first = if (config.clipOrder == ClipOrder.IMAGE_FIRST) imgShader else gradShader
        val second = if (config.clipOrder == ClipOrder.IMAGE_FIRST) gradShader else imgShader

        if (config.concatPerspective == ConcatPerspective.BEFORE_CLIPS) {
            canvas.concat(persp)
        }

        canvas.drawRect(imgRect, Paint(shader = first))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))

        if (config.concatPerspective == ConcatPerspective.BETWEEN_CLIPS) {
            canvas.concat(persp)
        }

        canvas.drawRect(imgRect, Paint(shader = second))
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))

        if (config.concatPerspective == ConcatPerspective.AFTER_CLIPS) {
            canvas.concat(persp)
        }

        canvas.clipRect(imgRect)
        canvas.drawRect(imgRect, Paint(color = Color.BLACK))
        canvas.drawImage(img, imgRect)

        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    private fun drawBanner(canvas: GmCanvas, config: Config) {
        val perspectiveTarget = when {
            config.concatPerspective == ConcatPerspective.BEFORE_CLIPS ||
                config.localMatrix == LocalMatrix.BOTH_WITH_LOCAL_MATRIX -> "Both Clips"
            (config.concatPerspective == ConcatPerspective.BETWEEN_CLIPS &&
                config.clipOrder == ClipOrder.IMAGE_FIRST) ||
                config.localMatrix == LocalMatrix.GRADIENT_WITH_LOCAL_MATRIX -> "Gradient"
            else -> "Image"
        }
        val suffix = if (config.localMatrix == LocalMatrix.NO_LOCAL_MATRIX) "" else " (w/ LM, should equal top row)"
        val font = Font(typeface, size = 12f)
        canvas.drawString("Persp: $perspectiveTarget$suffix", 20f, -30f, font, Paint())
    }

    private fun computeBoundingBox(m: Matrix33, r: Rect): Rect {
        val corners = listOf(
            perspTransformPoint(m, Point(r.left, r.top)),
            perspTransformPoint(m, Point(r.right, r.top)),
            perspTransformPoint(m, Point(r.right, r.bottom)),
            perspTransformPoint(m, Point(r.left, r.bottom)),
        )
        val xs = corners.map { it.x }
        val ys = corners.map { it.y }
        return Rect(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private enum class ConcatPerspective { BEFORE_CLIPS, AFTER_CLIPS, BETWEEN_CLIPS }
    private enum class ClipOrder { IMAGE_FIRST, GRADIENT_FIRST, DOES_NOT_MATTER }
    private enum class LocalMatrix {
        NO_LOCAL_MATRIX,
        IMAGE_WITH_LOCAL_MATRIX,
        GRADIENT_WITH_LOCAL_MATRIX,
        BOTH_WITH_LOCAL_MATRIX,
    }
    private data class Config(
        val concatPerspective: ConcatPerspective,
        val clipOrder: ClipOrder,
        val localMatrix: LocalMatrix,
    )
}
