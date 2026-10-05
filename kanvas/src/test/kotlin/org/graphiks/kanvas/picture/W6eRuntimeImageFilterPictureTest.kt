@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi
import org.graphiks.kanvas.surface.SceneRecordingScope
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public Picture custody and terminal-refusal witnesses for W6e IMAGE_FILTER execution. */
class W6eRuntimeImageFilterPictureTest {
    @Test
    fun runtimeImageFilterReplayAndRefusalRemainPublic() {
        val source = opacitySourcePixels()
        // This exact public IMAGE_FILTER pixel is defined before recording or replay starts.
        val expected = rgba(60, 30, 15, 128)
        val recorded = recordRuntimeImageOpacity(source)
        val decoded = assertNotNull(Picture.fromByteArray(recorded.toByteArray()))

        listOf(recorded, decoded).forEach { picture -> assertPicturePixels(expected, picture) }
        assertTerminalWithoutReadbackMutation(unregisteredRuntimeSurface(source), "w6d.runtime_effect.not_registered:")
        assertTerminalWithoutReadbackMutation(wrongAbiRuntimeSurface(source), "w6d.runtime_effect.abi_unsupported:")
    }

    private fun recordRuntimeImageOpacity(source: UByteArray): Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(unit).drawRect(unit, Paint(
            shader = sourceShader(source), imageFilter = imageOpacity(alpha = .5f), antiAlias = false,
        ))
    }.finishRecordingAsPicture()

    private fun assertPicturePixels(expected: UByteArray, picture: Picture) {
        val result = Surface(1, 1).also { surface -> surface.canvas { drawPicture(picture) } }.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Suppress("DEPRECATION")
    private fun unregisteredRuntimeSurface(source: UByteArray): Surface {
        val unregistered = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }
        return Surface(1, 1).also { surface ->
            surface.canvas {
                drawRect(unit, Paint(shader = sourceShader(source),
                    imageFilter = ImageFilter.RuntimeEffect(unregistered, UniformBlock.EMPTY), antiAlias = false))
            }
        }
    }

    private fun wrongAbiRuntimeSurface(source: UByteArray): Surface = Surface(1, 1).also { surface ->
        surface.canvas {
            drawRect(unit, Paint(shader = sourceShader(source), imageFilter = ImageFilter.RuntimeEffect(
                requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
                UniformBlock { float1("alpha", .5f) }, childShaderName = "child",
            ), antiAlias = false))
        }
    }

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, diagnosticPrefix: String) {
        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(unit, sentinel) }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun imageOpacity(alpha: Float): ImageFilter.RuntimeEffect {
        val effect = requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1))
        val descriptor = requireNotNull(effect.descriptor)
        require(descriptor.id.value == "kanvas.runtime.image-opacity")
        require(descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER)
        return ImageFilter.RuntimeEffect(effect, UniformBlock { float1("alpha", alpha) })
    }

    private fun sourceShader(source: UByteArray): Shader = Shader.Image(
        Image.fromPixels(1, 1, source.toByteArray(), alphaType = AlphaType.PREMUL),
    )

    private fun opacitySourcePixels(): UByteArray = ubyteArrayOf(85u, 45u, 24u, 255u)

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte(),
    )

    private companion object {
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }
}
