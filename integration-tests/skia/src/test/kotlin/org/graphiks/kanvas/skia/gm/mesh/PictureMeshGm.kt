package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::PictureMesh`.
 * Exercises mesh draws across indexed/non-indexed and
 * triangle/triangle-strip modes, comparing direct draw
 * against picture record-and-playback.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class PictureMeshGm : SkiaGm {
    override val name = "picture_mesh"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 99.0
    override val width = 390
    override val height = 90

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rectW = RECT_W
        val rectH = RECT_H
        val shader = Shader.SweepGradient(
            center = Point(rectW / 2f, rectH / 2f),
            startAngle = 0f,
            endAngle = 360f,
            stops = listOf(
                GradientStop(0f, Color(0xFFE91E63u)),
                GradientStop(0.33f, Color(0xFF00BCD4u)),
                GradientStop(0.66f, Color(0xFFFFEB3Bu)),
                GradientStop(1f, Color(0xFFE91E63u)),
            ),
        )

        for (usePicture in listOf(false, true)) {
            canvas.save()
            for (mode in 0 until 4) {
                val verts = buildVerts(mode)
                val paint = Paint(
                    shader = shader,
                    blendMode = BlendMode.DIFFERENCE,
                )
                if (usePicture) {
                    val recorder = PictureRecorder()
                    val recCanvas = recorder.beginRecording(
                        Rect(0f, 0f, rectW, rectH),
                    )
                    recCanvas.drawVertices(verts, paint)
                    val picture = recorder.finishRecordingAsPicture()
                    canvas.drawPicture(picture)
                } else {
                    canvas.drawVertices(verts, paint)
                }
                canvas.translate(rectW + 10f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, rectH + 10f)
        }
    }

    private companion object {
        private const val RECT_W = 40f
        private const val RECT_H = 40f
        private val allPositions = listOf(
            Point(1000f, 1000f), // sentinel, skipped via offset
            Point(0f, 0f),
            Point(RECT_W, 0f),
            Point(0f, RECT_H),
            Point(RECT_W, RECT_H),
            Point(0f, RECT_H),
            Point(RECT_W, 0f),
        )
        private val indices = listOf(1, 2, 3, 4, 5, 6)

        fun buildVerts(mode: Int): Vertices {
            return when (mode) {
                0 -> Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = allPositions.subList(1, 7), // skip sentinel
                )
                1 -> Vertices(
                    mode = VertexMode.TRIANGLE_STRIP,
                    positions = allPositions.subList(1, 5),
                )
                else -> Vertices(
                    mode = if (mode == 2) VertexMode.TRIANGLES else VertexMode.TRIANGLE_STRIP,
                    positions = allPositions.drop(1),
                    indices = indices,
                )
            }
        }
    }
}
