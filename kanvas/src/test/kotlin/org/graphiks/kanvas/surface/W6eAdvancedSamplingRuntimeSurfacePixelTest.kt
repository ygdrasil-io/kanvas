@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for W6e's advanced sampling and IMAGE_FILTER families. */
class W6eAdvancedSamplingRuntimeSurfacePixelTest {
    @Test
    fun advancedSamplingAndRuntimeImageFilterConverge() {
        val samplingSource = samplingSourcePixels()
        val convolutionExpected = W6eAdvancedSamplingCpuOracle.convolution3x1Clamp(
            samplingSource, floatArrayOf(1f, 0f, 0f), kernelOffsetX = 1,
        )
        val displacementExpected = W6eAdvancedSamplingCpuOracle.displacementRedNearestClamp(samplingSource, scale = 1f)
        val magnifierExpected = W6eAdvancedSamplingCpuOracle.magnifierNearestClamp(
            samplingSource, lensLeft = -1f, lensRight = 3f, zoom = 2f, inset = .5f,
        )
        val opacitySource = opacitySourcePixels()
        // Public IMAGE_FILTER contract: the encoded premul fixture below at alpha .5 reads back
        // this exact RGBA8 pixel after its linear-premultiplied filter target round trip.
        val opacityExpected = rgba(60, 30, 15, 128)

        assertFamilyNear(convolutionExpected, maxChannelDelta = 1) {
            renderSampling(ImageFilter.MatrixConvolution(
                SizeF32.of(3f, 1f), floatArrayOf(1f, 0f, 0f), 1f, 0f,
                Vector2F32(1f, 0f), TileMode.CLAMP, true,
            ))
        }
        assertFamilyNear(displacementExpected, maxChannelDelta = 1) {
            renderSampling(ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 1f, ImageFilter.Offset(0f, 0f)))
        }
        assertFamilyNear(magnifierExpected, maxChannelDelta = 1) {
            renderSampling(ImageFilter.Magnifier(RectF32.ofLTRB(-1f, 0f, 3f, 1f), zoom = 2f, inset = .5f))
        }
        assertRenderedExactly(opacityExpected) {
            renderOpacity(opacitySource, imageOpacity(alpha = .5f))
        }
    }

    private fun renderSampling(filter: ImageFilter): RenderResult = Surface(3, 1).also { surface ->
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            drawSamplingSource(this)
            restore()
        }
    }.render()

    private fun renderOpacity(source: UByteArray, filter: ImageFilter): RenderResult = Surface(1, 1).also { surface ->
        surface.canvas {
            drawRect(unit, Paint(
                shader = Shader.Image(Image.fromPixels(1, 1, source.toByteArray(), alphaType = AlphaType.PREMUL)),
                imageFilter = filter,
                antiAlias = false,
            ))
        }
    }.render()

    private fun imageOpacity(alpha: Float): ImageFilter.RuntimeEffect {
        val effect = requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1))
        val descriptor = requireNotNull(effect.descriptor)
        require(descriptor.id.value == "kanvas.runtime.image-opacity")
        require(descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER)
        return ImageFilter.RuntimeEffect(effect, UniformBlock { float1("alpha", alpha) })
    }

    private fun drawSamplingSource(canvas: Canvas) {
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(ColorARGB.Green, antiAlias = false))
        canvas.drawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(ColorARGB.Blue, antiAlias = false))
    }

    private fun assertRenderedExactly(expected: UByteArray, render: () -> RenderResult) {
        val actual = render()
        assertContentEquals(expected, actual.pixels)
        assertNativeRenderAndReadback(actual)
    }

    private fun assertFamilyNear(expected: UByteArray, maxChannelDelta: Int, render: () -> RenderResult) {
        val actual = render()
        assertTrue(expected.size == actual.pixels.size)
        expected.indices.forEach { channel ->
            assertTrue(abs(expected[channel].toInt() - actual.pixels[channel].toInt()) <= maxChannelDelta,
                "channel $channel expected=${expected[channel]} actual=${actual.pixels[channel]}")
        }
        assertNativeRenderAndReadback(actual)
    }

    private fun assertNativeRenderAndReadback(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    private fun samplingSourcePixels(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u,
        0u, 255u, 0u, 255u,
        0u, 0u, 255u, 255u,
    )

    private fun opacitySourcePixels(): UByteArray = ubyteArrayOf(85u, 45u, 24u, 255u)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte(),
    )

    private companion object {
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }
}
