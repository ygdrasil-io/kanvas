@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public execution and terminal-refusal witnesses for W6d's registered IMAGE_FILTER built-in. */
class W6dRuntimeImageOpacitySurfacePixelTest {
    @Test
    fun `image opacity multiplies premultiplied rgba without spatial sampling`() {
        val expected = rgba(60, 30, 15, 128)
        val surface = Surface(1, 1)
        surface.canvas { drawRect(unit, Paint(shader = sourceShader, imageFilter = imageOpacity(.5f), antiAlias = false)) }

        val result = surface.render()

        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Test
    fun explicitInputAndAbsentInputBindTheSameContext() {
        val expected = rgba(60, 30, 15, 128)
        val explicit = Surface(1, 1).also { surface ->
            surface.canvas {
                drawRect(
                    unit,
                    Paint(shader = sourceShader, imageFilter = imageOpacity(.5f, ImageFilter.Crop(unit)), antiAlias = false),
                )
            }
        }.render()
        val absent = Surface(1, 1).also { surface ->
            surface.canvas { drawRect(unit, Paint(shader = sourceShader, imageFilter = imageOpacity(.5f), antiAlias = false)) }
        }.render()

        assertContentEquals(expected, explicit.pixels)
        assertContentEquals(expected, absent.pixels)
        assertTrue(explicit.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        assertTrue(absent.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Suppress("DEPRECATION")
    @Test
    fun unknownOrWrongAbiRefusesWithoutReadbackMutation() {
        val unknown = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(unit, Paint(shader = sourceShader, imageFilter = ImageFilter.RuntimeEffect(unknown, UniformBlock.EMPTY), antiAlias = false))
        }

        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(unit, sentinel) }

        assertTrue(failure.message?.startsWith("w6d.runtime_effect.not_registered:") == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)

        val wrongAbiSurface = Surface(1, 1)
        wrongAbiSurface.canvas {
            drawRect(
                unit,
                Paint(
                    shader = sourceShader,
                    imageFilter = ImageFilter.RuntimeEffect(
                        requireNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
                        UniformBlock { float1("alpha", .5f) },
                        childShaderName = "child",
                    ),
                    antiAlias = false,
                ),
            )
        }
        val wrongAbiSentinel = UByteArray(4) { 0x5au }
        val wrongAbiBefore = wrongAbiSentinel.copyOf()
        val wrongAbiFailure = assertFailsWith<IllegalStateException> {
            wrongAbiSurface.readPixels(unit, wrongAbiSentinel)
        }

        assertTrue(
            wrongAbiFailure.message?.startsWith("w6d.runtime_effect.abi_unsupported:") == true,
            wrongAbiFailure.message ?: "missing diagnostic",
        )
        assertContentEquals(wrongAbiBefore, wrongAbiSentinel)
    }

    private fun imageOpacity(alpha: Float, input: ImageFilter? = null): ImageFilter.RuntimeEffect = ImageFilter.RuntimeEffect(
        requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
        UniformBlock { float1("alpha", alpha) },
        childImageFilters = input?.let { mapOf("input" to it) }.orEmpty(),
    )

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte(),
    )

    private companion object {
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        // The W6 filter targets are linear-premultiplied sRGB textures.  This encoded fixture
        // has the exact .5f opacity readback required by the public RGBA8 contract below.
        val sourceShader: Shader = Shader.Image(
            Image.fromPixels(1, 1, byteArrayOf(85, 45, 24, -1), alphaType = AlphaType.PREMUL),
        )
    }
}
