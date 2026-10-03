package org.graphiks.math.matrix

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64

/** Independent endpoint witnesses for the W7 reciprocal-scale diagonal outline. */
class DiagonalStrokeGeometryF64F32Test {
    @Test
    fun tinyReciprocalDiagonalPreservesButtOutline() {
        val scales = listOf(.00005f, .000045f, .0000035f, .000003f, .000002f)
        val corners = listOf(
            68.23223f to 21.76777f,
            71.76777f to 18.23223f,
            151.76777f to 98.23223f,
            148.23223f to 101.76777f,
        )

        for (scale in scales) {
            val sourcePath = PathBuilder()
                .moveTo(20f * scale, 20f * scale)
                .lineTo(100f * scale, 100f * scale)
                .build()
            val matrix = Matrix3x3F32(
                sx = 1f / scale,
                sy = 1f / scale,
                tx = 50f,
                ty = 0f,
            )
            val prepared = assertIs<PathStrokePreparationResult.Ready>(
                matrix.preparePathStrokeGeometryF32(
                    path = sourcePath,
                    styleF64 = buttMiterStyle(5.0 * scale.toDouble()),
                    mode = PathStrokeDrawMode.Stroke,
                ),
                "source scale=$scale",
            ).geometryF32
            assertOutlineCorners(prepared.copyFillGeometryF32().copyStencilEdgeFanF32OrNull()
                ?: error("source scale=$scale did not publish stencil-edge geometry"), corners, "source scale=$scale")

            val devicePath = PathBuilder().moveTo(70f, 20f).lineTo(150f, 100f).build()
            val device = assertIs<PathStrokePreparationResult.Ready>(
                Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
                    path = devicePath,
                    styleF64 = buttMiterStyle(5.0),
                    mode = PathStrokeDrawMode.Stroke,
                ),
                "device control scale=$scale",
            ).geometryF32
            assertOutlineCorners(device.copyFillGeometryF32().copyStencilEdgeFanF32OrNull()
                ?: error("device control did not publish stencil-edge geometry"), corners, "device control scale=$scale")
        }
    }

    private fun buttMiterStyle(widthF64: Double) = PathStrokeStyleF64(
        widthF64 = PathStrokeWidthF64.Finite(widthF64),
        cap = PathStrokeCap.Butt,
        join = PathStrokeJoin.Miter,
        miterLimitF64 = 4.0,
    )

    private fun assertOutlineCorners(
        fan: org.graphiks.math.geometry.PathStencilEdgeFanF32,
        corners: List<Pair<Float, Float>>,
        label: String,
    ) {
        val vertices = fan.copyVerticesF32()
        val endpoints = buildList {
            for (offset in vertices.indices step 6) {
                add(vertices[offset + 2] to vertices[offset + 3])
                add(vertices[offset + 4] to vertices[offset + 5])
            }
        }
        assertTrue(endpoints.isNotEmpty(), "$label: outline has no edge endpoints")
        for ((x, y) in endpoints) {
            val nearest = corners.minOf { (cornerX, cornerY) ->
                hypot((x - cornerX).toDouble(), (y - cornerY).toDouble())
            }
            assertTrue(nearest <= 3e-5f, "$label: endpoint ($x,$y) is $nearest from the literal outline")
        }
        for ((cornerX, cornerY) in corners) {
            assertTrue(
                endpoints.any { (x, y) ->
                    hypot((x - cornerX).toDouble(), (y - cornerY).toDouble()) <= 3e-5
                },
                "$label: missing literal corner ($cornerX,$cornerY)",
            )
        }
    }
}
