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
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/vertices.cpp::VerticesGM` (registered as `vertices`
 * and `vertices_scaled_shader`, 975 x 1175).
 *
 * Upstream draws the same indexed triangle fan across every blend mode,
 * paint alpha, optional color filter, two shaders, and three vertex
 * attribute combinations. This raster slice intentionally mirrors that
 * matrix now that the local raster stack has color shaders, color filters,
 * all public blend modes, and per-vertex color/UV sampling.
 */
public class VerticesGM(
    private val shaderScale: Float = 1f,
) : GM() {

    override fun getName(): String =
        "vertices" + if (shaderScale != 1f) "_scaled_shader" else ""

    override fun getISize(): SkISize = SkISize.Make(975, 1175)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val mesh = makeMesh(shaderScale)
        val shader1 = makeShader1(shaderScale)
        val shader2 = SkShaders.Color(SK_ColorBLUE)
        val colorFilter = SkColorFilters.Blend(0xFFAABBCC.toInt(), SkBlendMode.kDarken)

        c.translate(4f, 4f)
        for (mode in MODES) {
            c.save()
            for (alpha in floatArrayOf(1f, 0.5f)) {
                for (cf in arrayOf(null, colorFilter)) {
                    for (shader in arrayOf(shader1, shader2)) {
                        for (attrs in ATTRS) {
                            val paint = SkPaint().apply {
                                this.shader = shader
                                this.colorFilter = cf
                                this.alphaf = alpha
                            }
                            val vertices = SkVertices.MakeCopy(
                                mode = SkVertices.VertexMode.kTriangleFan,
                                positions = mesh.positions,
                                texCoords = if (attrs.hasTexs) mesh.texCoords else null,
                                colors = if (attrs.hasColors) mesh.colors else null,
                                indices = K_MESH_FAN,
                            )
                            c.drawVertices(vertices, mode, paint)
                            c.translate(40f, 0f)
                        }
                    }
                }
            }
            c.restore()
            c.translate(0f, 40f)
        }
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
        val gradient = SkLinearGradient.Make(
            SkPoint(K_SHADER_SIZE / 4f, 0f),
            SkPoint(3f * K_SHADER_SIZE / 4f, K_SHADER_SIZE),
            colors,
            null,
            SkTileMode.kMirror,
            SkMatrix.MakeScale(shaderScale, shaderScale),
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

    private data class Attrs(
        val hasColors: Boolean,
        val hasTexs: Boolean,
    )

    private companion object {
        const val K_SHADER_SIZE: Float = 40f
        const val K_MESH_SIZE: Float = 30f
        const val K_MESH_VERTEX_COUNT: Int = 9
        val K_MESH_FAN: ShortArray = shortArrayOf(4, 0, 1, 2, 5, 8, 7, 6, 3, 0)
        val ATTRS: Array<Attrs> = arrayOf(
            Attrs(hasColors = true, hasTexs = false),
            Attrs(hasColors = false, hasTexs = true),
            Attrs(hasColors = true, hasTexs = true),
        )
        val MODES: Array<SkBlendMode> = arrayOf(
            SkBlendMode.kClear,
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
            SkBlendMode.kScreen,
            SkBlendMode.kOverlay,
            SkBlendMode.kDarken,
            SkBlendMode.kLighten,
            SkBlendMode.kColorDodge,
            SkBlendMode.kColorBurn,
            SkBlendMode.kHardLight,
            SkBlendMode.kSoftLight,
            SkBlendMode.kDifference,
            SkBlendMode.kExclusion,
            SkBlendMode.kMultiply,
            SkBlendMode.kHue,
            SkBlendMode.kSaturation,
            SkBlendMode.kColor,
            SkBlendMode.kLuminosity,
        )
    }
}
