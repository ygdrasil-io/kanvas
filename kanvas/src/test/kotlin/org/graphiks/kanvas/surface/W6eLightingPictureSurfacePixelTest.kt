@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for all six lighting families and Picture filtering. */
class W6eLightingPictureSurfacePixelTest {
    @Test fun distantDiffuseMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.distantDiffuse(),
        ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
        maxChannelDelta = 2,
    )

    @Test fun pointDiffuseMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.pointDiffuse(),
        ImageFilter.PointLitDiffuse(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
        maxChannelDelta = 2,
    )

    @Test fun spotDiffuseMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.spotDiffuse(),
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f,
            ColorARGB.White, 1f, 1f),
        maxChannelDelta = 2,
    )

    @Test fun distantSpecularMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.distantSpecular(),
        ImageFilter.DistantLitSpecular(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f),
        maxChannelDelta = 2,
    )

    @Test fun pointSpecularMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.pointSpecular(),
        ImageFilter.PointLitSpecular(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f),
        maxChannelDelta = 2,
    )

    @Test fun spotSpecularMatchesItsIndependentOracle() = assertFamily(
        W6eLightingCpuOracle.spotSpecular(),
        ImageFilter.SpotLitSpecular(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f,
            ColorARGB.White, 1f, 1f, 2f),
        maxChannelDelta = 2,
    )

    @Test
    fun pictureFilterUsesItsFrozenPictureInsteadOfCarrierPixels() {
        val expected = ubyteArrayOf(255u, 0u, 0u, 255u)
        val unit = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val source = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
        }.finishRecordingAsPicture()
        val result = Surface(1, 1).also { surface ->
            surface.canvas {
                drawRect(unit, Paint(ColorARGB.Blue, imageFilter = ImageFilter.Picture(source), antiAlias = false))
            }
        }.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }

    private fun assertFamily(expected: UByteArray, filter: ImageFilter, maxChannelDelta: Int) {
        val result = Surface(3, 3).also { surface ->
            surface.canvas {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
                listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                    drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f),
                        Paint(ColorARGB.White, antiAlias = false))
                }
                restore()
            }
        }.render()

        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
        assertTrue(expected.size == result.pixels.size)
        expected.indices.forEach { channel ->
            assertTrue(abs(expected[channel].toInt() - result.pixels[channel].toInt()) <= maxChannelDelta,
                "channel $channel expected=${expected[channel]} actual=${result.pixels[channel]}")
        }
    }
}
