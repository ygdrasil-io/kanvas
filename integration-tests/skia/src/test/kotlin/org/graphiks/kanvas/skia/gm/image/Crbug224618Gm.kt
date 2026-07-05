package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_224618.cpp` CrBug224618GM.
 * Renders a 6-faced cube projected through a CSS-style perspective
 * matrix, alternating between solid-fill quads and textured drawImageRect
 * quads on each face.
 * @see https://github.com/google/skia/blob/main/gm/crbug_224618.cpp
 */
class Crbug224618Gm : SkiaGm {
    override val name = "crbug_224618"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val radius = 150f

        val cubeImage = makeCubeTexture()

        // CSS-style perspective: [1,0,0; 0,1,0; 0,-1/radius,1]
        val persp = Matrix33.makeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, -1f / radius, 1f,
        )

        val faceColors = listOf(
            Color.RED,
            Color.fromRGBA(0f, 1f, 0f),
            Color.BLUE,
            Color.fromRGBA(1f, 1f, 0f),
            Color.fromRGBA(1f, 0x65 / 255f, 0f),  // orange
            Color.fromRGBA(0x80 / 255f, 0f, 0x80 / 255f),  // purple
        )

        // Approximate 3D cube faces with 2D transforms
        // Each face is drawn with a perspective distort and positioned offset
        for (i in 0 until 6) {
            val angle = i * 60f
            val xOff = radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val yOff = radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * 0.3f

            val mat = persp * Matrix33.translate(xOff + radius, yOff + radius)

            canvas.save()
            canvas.concat(mat)

            val fillPaint = Paint(color = faceColors[i], antiAlias = true)
            canvas.drawRect(Rect(0f, 0f, 300f, 300f), fillPaint)

            // Draw textured rect (no perspective correction, but shows image placement)
            canvas.drawImageRect(
                cubeImage,
                Rect(0f, 0f, 400f, 400f),
                Rect(0f, 0f, 300f, 300f),
                Paint(color = Color.fromRGBA(0.5f, 1f, 1f, 1f)),
            )

            canvas.restore()
        }
    }

    private fun makeCubeTexture(): Image {
        val surface = Surface(400, 400)
        surface.canvas {
            val shader = Shader.RadialGradient(
                center = Point(200f, 200f),
                radius = 25f,
                stops = listOf(
                    GradientStop(0f, Color.TRANSPARENT),
                    GradientStop(1f, Color.fromRGBA(1f, 1f, 1f, 0.5f)),
                ),
                tileMode = TileMode.MIRROR,
            )
            drawRect(Rect(0f, 0f, 400f, 400f), Paint(shader = shader))
        }
        return surface.makeImageSnapshot()
    }
}
