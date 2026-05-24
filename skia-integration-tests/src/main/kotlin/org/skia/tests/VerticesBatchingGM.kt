package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.skia.tools.SkRandom

/**
 * Port of upstream Skia's `gm/vertices.cpp::vertices_batching`
 * (`DEF_SIMPLE_GM(vertices_batching, canvas, 100, 500)`).
 *
 * The upstream GM uses `SkVertices::Builder` to fill the arrays, then
 * converts the 9-vertex triangle fan into plain indexed triangles so GPU
 * backends can exercise their batching path. This Kotlin port builds the
 * same immutable [SkVertices] data directly via [SkVertices.MakeCopy], which
 * keeps the public API slice small while exercising the existing raster
 * `drawVertices` implementation.
 */
public class VerticesBatchingGM : GM() {

    override fun getName(): String = "vertices_batching"
    override fun getISize(): SkISize = SkISize.Make(100, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawBatching(c)
        c.translate(50f, 0f)
        drawBatching(c)
    }

    private fun drawBatching(canvas: SkCanvas) {
        val mesh = makeMesh(shaderScale = 1f)
        val indices = fanToTriangleIndices()
        val matrices = arrayOf(
            SkMatrix.Identity,
            SkMatrix.MakeTrans(0f, 40f),
            SkMatrix.MakeRotate(45f, K_MESH_SIZE / 2f, K_MESH_SIZE / 2f)
                .postScale(1.2f, 0.8f, K_MESH_SIZE / 2f, K_MESH_SIZE / 2f)
                .postTranslate(0f, 80f),
        )
        val shader = makeShader1(shaderScale = 1f)

        canvas.save()
        canvas.translate(10f, 10f)
        for (useShader in booleanArrayOf(false, true)) {
            for (useTex in booleanArrayOf(false, true)) {
                for (matrix in matrices) {
                    canvas.save()
                    canvas.concat(matrix)
                    val paint = SkPaint(SK_ColorWHITE).apply {
                        this.shader = if (useShader) shader else null
                    }
                    val vertices = SkVertices.MakeCopy(
                        mode = SkVertices.VertexMode.kTriangles,
                        positions = mesh.positions,
                        texCoords = if (useTex) mesh.texCoords else null,
                        colors = mesh.colors,
                        indices = indices,
                    )
                    canvas.drawVertices(vertices, SkBlendMode.kModulate, paint)
                    canvas.restore()
                }
                canvas.translate(0f, 120f)
            }
        }
        canvas.restore()
    }

    private fun makeMesh(shaderScale: Float): MeshData {
        val positions = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(K_MESH_SIZE / 2f, 3f),
            SkPoint(K_MESH_SIZE, 0f),
            SkPoint(3f, K_MESH_SIZE / 2f),
            SkPoint(K_MESH_SIZE / 2f, K_MESH_SIZE / 2f),
            SkPoint(K_MESH_SIZE - 3f, K_MESH_SIZE / 2f),
            SkPoint(0f, K_MESH_SIZE),
            SkPoint(K_MESH_SIZE / 2f, K_MESH_SIZE - 3f),
            SkPoint(K_MESH_SIZE, K_MESH_SIZE),
        )

        val shaderSize = K_SHADER_SIZE * shaderScale
        val texCoords = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(shaderSize / 2f, 0f),
            SkPoint(shaderSize, 0f),
            SkPoint(0f, shaderSize / 2f),
            SkPoint(shaderSize / 2f, shaderSize / 2f),
            SkPoint(shaderSize, shaderSize / 2f),
            SkPoint(0f, shaderSize),
            SkPoint(shaderSize / 2f, shaderSize),
            SkPoint(shaderSize, shaderSize),
        )

        val random = SkRandom()
        val colors = IntArray(K_MESH_VERTEX_COUNT) { random.nextU() or 0xFF000000.toInt() }
        return MeshData(positions, texCoords, colors)
    }

    private fun fanToTriangleIndices(): ShortArray {
        val numTris = K_MESH_FAN.size - 2
        val out = ShortArray(numTris * 3)
        for (i in 0 until numTris) {
            out[3 * i] = K_MESH_FAN[0].toShort()
            out[3 * i + 1] = K_MESH_FAN[i + 1].toShort()
            out[3 * i + 2] = K_MESH_FAN[i + 2].toShort()
        }
        return out
    }

    private fun makeShader1(shaderScale: Float): SkShader {
        val colors = intArrayOf(
            SK_ColorRED,
            SK_ColorCYAN,
            SK_ColorGREEN,
            SK_ColorWHITE,
            SK_ColorMAGENTA,
            SK_ColorBLUE,
            SK_ColorYELLOW,
        )
        val localMatrix = SkMatrix.MakeScale(shaderScale, shaderScale)
        val gradient = SkLinearGradient.Make(
            SkPoint(K_SHADER_SIZE / 4f, 0f),
            SkPoint(3f * K_SHADER_SIZE / 4f, K_SHADER_SIZE),
            colors,
            null,
            SkTileMode.kMirror,
            localMatrix,
        )
        return if (shaderScale == 1f) {
            gradient
        } else {
            gradient
                .makeWithLocalMatrix(SkMatrix.MakeTrans(-10f, 0f))
                .makeWithLocalMatrix(SkMatrix.MakeTrans(10f, 0f))
        }
    }

    private data class MeshData(
        val positions: Array<SkPoint>,
        val texCoords: Array<SkPoint>,
        val colors: IntArray,
    )

    private companion object {
        const val K_SHADER_SIZE: Float = 40f
        const val K_MESH_SIZE: Float = 30f
        const val K_MESH_VERTEX_COUNT: Int = 9
        val K_MESH_FAN: IntArray = intArrayOf(4, 0, 1, 2, 5, 8, 7, 6, 3, 0)
    }
}
