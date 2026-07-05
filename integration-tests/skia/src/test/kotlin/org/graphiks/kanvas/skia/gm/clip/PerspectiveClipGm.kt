package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import kotlin.random.Random

/**
 * Port of Skia's `gm/perspshaders.cpp::DEF_SIMPLE_GM(perspective_clip, …)`
 * (name `perspective_clip`, 800 × 800).
 *
 * Draws a random path filled with grey, then again under a perspective
 * matrix with an image shader. Exercises half-plane clipping where part
 * of the geometry is "behind" the viewer.
 * @see https://github.com/google/skia/blob/main/gm/perspshaders.cpp
 */
class PerspectiveClipGm : SkiaGm {
    override val name = "perspective_clip"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = makePath()

        val greyPaint = Paint(color = Color.fromRGBA(0.75f, 0.75f, 0.75f))
        canvas.drawPath(path, greyPaint)

        // Crazy perspective matrix derived from halfplanes3
        val mx = Matrix33.makeAll(
            -1.7866f, 1.3357f, 273.0295f,
            -1.0820f, 1.3186f, 135.5196f,
            -0.0047f, -0.0015f, 2.1485f,
        )

        val shaderPaint = Paint(color = Color.fromRGBA(0.5f, 0.7f, 0.9f))
        canvas.save()
        canvas.concat(mx)
        canvas.drawPath(path, shaderPaint)
        canvas.restore()
    }

    private fun makePath(): Path {
        val rand = Random(0)
        fun randPt(): Pair<Float, Float> {
            return Pair(rand.nextFloat() * 400f, rand.nextFloat() * 400f)
        }

        val path = Path { }
        for (i in 0 until 4) {
            val pts = Array(6) { randPt() }
            path.moveTo(pts[0].first, pts[0].second)
            path.quadTo(pts[1].first, pts[1].second, pts[2].first, pts[2].second)
            path.quadTo(pts[3].first, pts[3].second, pts[4].first, pts[4].second)
            path.lineTo(pts[5].first, pts[5].second)
        }
        return path
    }
}
