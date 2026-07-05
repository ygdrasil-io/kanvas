package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ConvexPolyClipGm : SkiaGm {
    override val name = "convex_poly_clip"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 870
    override val height = 540

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!
    private val clips = mutableListOf<Clip>()

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        setupClips()
        val image = makeImg(100, 100)
        val bgPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.08f))
        canvas.drawImageRect(image, Rect.fromXYWH(0f, 0f, 100f, 100f), Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), bgPaint)
        val font = Font(typeface, 23f)
        val txtPaint = Paint(color = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f))
        val kMargin = 10f
        var startX = 0f
        for (doLayer in 0..1) {
            var y = 0f
            for (clip in clips) {
                var x = startX
                for (aa in 0..1) {
                    if (doLayer == 1) canvas.saveLayer() else canvas.save()
                    canvas.translate(x, y)
                    clip.setOnCanvas(canvas, ClipOp.INTERSECT, aa == 1)
                    canvas.drawImage(image, Rect.fromXYWH(0f, 0f, 100f, 100f))
                    canvas.restore()
                    x += 100 + kMargin
                }
                for (aa in 0..1) {
                    val outlinePaint = Paint(color = Color.fromRGBA(0.314f, 0.314f, 0.314f, 0.314f), antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 0f)
                    if (doLayer == 1) canvas.saveLayer() else canvas.save()
                    canvas.translate(x, y)
                    canvas.drawPath(clip.asClosedPath(), outlinePaint)
                    clip.setOnCanvas(canvas, ClipOp.INTERSECT, aa == 1)
                    canvas.scale(1f, 1.8f)
                    canvas.drawString("Clip Me!", 0f, 1.5f * 23f, font, txtPaint)
                    canvas.restore()
                    x += 180 + 2 * kMargin
                }
                y += 100 + kMargin
            }
            startX += (2 * 100 + 360 + 6 * kMargin)
        }
    }

    private fun setupClips() {
        if (clips.isNotEmpty()) return
        clips.add(Clip().apply { setPath(Path {
            moveTo(5f, 5f); lineTo(100f, 20f); lineTo(15f, 100f); close()
        }) })
        val kRadius = 45f
        val hexagon = Path {
            for (i in 0 until 6) {
                val angle = (2 * PI.toFloat() * i / 6)
                val px = cos(angle.toDouble()).toFloat() * kRadius
                val py = sin(angle.toDouble()).toFloat() * kRadius
                if (i == 0) moveTo(45f + px, 45f + py) else lineTo(45f + px, 45f + py)
            }
            close()
        }
        clips.add(Clip().apply { setPath(hexagon) })
        clips.add(Clip().apply {
            setPath(Path {
                moveTo(45f + 0.737f * 0f, 45f + 0.267f * 0f)
                lineTo(45f + 0.737f * 39f, 45f + 0.267f * 22.5f)
                lineTo(45f + 0.737f * 39f, 45f + 0.267f * (-22.5f))
                lineTo(45f + 0.737f * 0f, 45f + 0.267f * (-45f))
                lineTo(45f + 0.737f * (-39f), 45f + 0.267f * (-22.5f))
                lineTo(45f + 0.737f * (-39f), 45f + 0.267f * 22.5f)
                close()
            })
        })
        clips.add(Clip().apply { setRect(Rect.fromXYWH(8.3f, 11.6f, 78.2f, 72.6f)) })
        val cos23 = cos(Math.toRadians(23.0)).toFloat()
        val sin23 = sin(Math.toRadians(23.0)).toFloat()
        clips.add(Clip().apply {
            setPath(Path {
                val pts = listOf(Point(10f, 12f), Point(80f, 12f), Point(80f, 86f), Point(10f, 86f))
                val cx = 45f; val cy = 49f
                for ((i, pt) in pts.withIndex()) {
                    val dx = pt.x - cx; val dy = pt.y - cy
                    val rx = cx + cos23 * dx - sin23 * dy
                    val ry = cy + sin23 * dx + cos23 * dy
                    if (i == 0) moveTo(rx, ry) else lineTo(rx, ry)
                }
                close()
            })
        })
    }

    private fun makeImg(w: Int, h: Int): org.graphiks.kanvas.image.Image {
        val surface = Surface(w, h)
        surface.canvas {
            val wF = w.toFloat(); val hF = h.toFloat()
            val pt = Point(wF / 2f, hF / 2f)
            val radius = 3f * maxOf(wF, hF)
            val colors = listOf(Color.fromRGBA(0.5f, 0.5f, 0.5f), Color.fromRGBA(0.133f, 0.133f, 0.333f),
                Color.fromRGBA(0.2f, 0.075f, 0.2f), Color.fromRGBA(0.533f, 0.267f, 0.133f),
                Color.fromRGBA(0f, 0f, 0.133f), Color.WHITE, Color.fromRGBA(0.667f, 0.733f, 0.8f))
            val pos = floatArrayOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)
            val stops = colors.mapIndexed { i, c -> GradientStop(pos[i], c) }
            var rect = Rect.fromXYWH(0f, 0f, wF, hF)
            var mat = Matrix33.identity()
            for (i in 0 until 4) {
                drawRect(rect, Paint(shader = Shader.WithLocalMatrix(Shader.RadialGradient(pt, radius, stops, TileMode.REPEAT), mat)))
                val inset = wF / 8f
                rect = Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                mat = Matrix33.translate(6f * wF, 6f * hF) * Matrix33.scale(1f / 3f, 1f / 3f) * mat
            }
        }
        return surface.makeImageSnapshot()
    }

    private class Clip {
        enum class Type { NONE, PATH, RECT }
        var type: Type = Type.NONE
        var clipPath: Path = Path { }
        var clipRect: Rect = Rect.fromXYWH(0f, 0f, 0f, 0f)
        fun setPath(p: Path) { type = Type.PATH; clipPath = p }
        fun setRect(r: Rect) { type = Type.RECT; clipRect = r }
        fun setOnCanvas(canvas: GmCanvas, op: ClipOp, aa: Boolean) {
            when (type) {
                Type.PATH -> canvas.clipPath(clipPath, op, aa)
                Type.RECT -> canvas.clipRect(clipRect)
                Type.NONE -> Unit
            }
        }
        fun asClosedPath(): Path = when (type) {
            Type.PATH -> Path { }.apply { addPath(clipPath); close() }
            Type.RECT -> Path { }.apply { addRect(clipRect) }
            Type.NONE -> Path { }
        }
    }
}
