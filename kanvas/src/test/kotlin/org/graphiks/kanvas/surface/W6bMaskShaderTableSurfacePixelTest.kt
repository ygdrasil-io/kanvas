@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public pixels for W6b's already-frozen shader and table mask coverage arms. */
class W6bMaskShaderTableSurfacePixelTest {
    @Test
    fun `solid mask shader consumes the frozen W5 material mapping`() {
        val actual = Surface(3, 1).also { surface ->
            surface.canvas {
                drawRect(bounds3x1, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(ubyteArrayOf(
            255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u,
        ), actual)
    }

    @Test
    fun `gradient mask shader multiplies frozen coverage before source blend`() {
        val actual = Surface(3, 1).also { surface ->
            surface.canvas {
                drawRect(bounds3x1, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Shader(alphaGradient()),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(gradientMaskedRedPixels, actual)
    }

    @Test
    fun `image-backed mask shader consumes its frozen W5 resource`() {
        val alphaResource = Image.fromPixels(1, 1, byteArrayOf(-1, -1, -1, -1), alphaType = AlphaType.PREMUL)
        val actual = Surface(1, 1).also { surface ->
            surface.canvas {
                drawRect(bounds1x1, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Shader(Shader.Image(alphaResource)),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(red1x1, actual)
    }

    @Test
    fun `image-backed mask shader on saveLayer consumes its frozen W5 resource`() {
        val alphaResource = Image.fromPixels(1, 1, byteArrayOf(-1, -1, -1, -1), alphaType = AlphaType.PREMUL)
        val actual = Surface(1, 1).also { surface ->
            surface.canvas {
                saveLayer(paint = Paint(maskFilter = MaskFilter.Shader(Shader.Image(alphaResource))))
                drawRect(bounds1x1, Paint(ColorARGB.Red, antiAlias = false))
                restore()
            }
        }.render().pixels

        assertContentEquals(red1x1, actual)
    }

    @Test
    fun `shader mask applies DST_OUT only at the final composite`() {
        val actual = destinationColoredMaskSurface(
            MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)),
        )

        assertContentEquals(UByteArray(4), actual)
    }

    @Test
    fun `table mask applies DST_OUT only at the final composite`() {
        val actual = destinationColoredMaskSurface(
            MaskFilter.Table(UByteArray(256) { indexI32 -> indexI32.toUByte() }),
        )

        assertContentEquals(UByteArray(4), actual)
    }

    @Test
    fun `repeated identical image mask shaders preserve both draws`() {
        val alphaResource = Image.fromPixels(1, 1, byteArrayOf(-1, -1, -1, -1), alphaType = AlphaType.PREMUL)
        val shader = Shader.Image(alphaResource)
        val actual = Surface(2, 1).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                    ColorARGB.Red, maskFilter = MaskFilter.Shader(shader), antiAlias = false,
                ))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(
                    ColorARGB.Red, maskFilter = MaskFilter.Shader(shader), antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 255u, 0u, 0u, 255u), actual)
    }

    @Test
    fun `invert-like 256 entry table transforms full coverage to transparent`() {
        val inverse = UByteArray(256) { indexI32 -> (255 - indexI32).toUByte() }
        val actual = Surface(3, 1).also { surface ->
            surface.canvas {
                drawRect(bounds3x1, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Table(inverse),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(UByteArray(12), actual)
    }

    @Test
    fun `identity 256 entry table preserves full coverage before source blend`() {
        val identity = UByteArray(256) { indexI32 -> indexI32.toUByte() }
        val actual = Surface(3, 1).also { surface ->
            surface.canvas {
                drawRect(bounds3x1, Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Table(identity),
                    antiAlias = false,
                ))
            }
        }.render().pixels

        assertContentEquals(ubyteArrayOf(
            255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u,
        ), actual)
    }

    @Test
    fun `identity 256 entry table preserves fractional anti aliased coverage`() {
        // A half-covered opaque-red texel encodes as linear 0.5 premultiplied red in an
        // sRGB RGBA8 attachment.  The table is a coverage substitution, so an identity
        // LUT must leave those hand-derived bytes unchanged rather than square coverage.
        val expected = ubyteArrayOf(
            188u, 0u, 0u, 128u,
            188u, 0u, 0u, 128u,
        )
        val identity = UByteArray(256) { indexI32 -> indexI32.toUByte() }
        val actual = Surface(2, 1).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(.5f, 0f, 1.5f, 1f), Paint(
                    ColorARGB.Red,
                    maskFilter = MaskFilter.Table(identity),
                    antiAlias = true,
                ))
            }
        }.render().pixels

        assertContentEquals(expected, actual)
    }

    @Test
    fun `Picture parent shader and descendant table masks consume the sealed aggregate source`() {
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds3x1).drawRect(bounds3x1, Paint(
                ColorARGB.Red,
                maskFilter = MaskFilter.Table(UByteArray(256) { indexI32 -> indexI32.toUByte() }),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds3x1).drawPicture(child, Paint(
                maskFilter = MaskFilter.Shader(alphaGradient()),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()

        val actual = Surface(3, 1).also { surface ->
            surface.canvas { drawPicture(parent) }
        }.render().pixels

        assertContentEquals(gradientMaskedRedPixels, actual)
    }

    @Test
    fun `mask table remains immutable after the draw is recorded`() {
        val capturedTable = UByteArray(256)
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(bounds2x2, Paint(
                ColorARGB.Red,
                maskFilter = MaskFilter.Table(capturedTable),
                antiAlias = false,
            ))
        }
        capturedTable.fill(255u)

        assertContentEquals(UByteArray(16), surface.render().pixels)
    }

    @Test
    fun `historical invalid table refuses atomically and same surface recovers`() {
        val surface = Surface(2, 2)
        val picture = assertNotNull(Picture.fromByteArray(fixture("format-13-invalid-mask-table-length.base64")))
        surface.canvas { picture.playback(this) }

        assertTerminalWithoutReadbackMutation(surface, "invalid.mask_filter.table_length:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds2x2, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2, surface.render().pixels)
    }

    private fun alphaGradient(): Shader.LinearGradient = Shader.LinearGradient(
        start = Point2F32(0f, 0f),
        end = Point2F32(3f, 0f),
        stops = listOf(
            org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.Transparent),
            org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.White),
        ),
    )

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, diagnosticPrefix: String) {
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(bounds2x2, sentinel)
        }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun destinationColoredMaskSurface(maskFilter: MaskFilter): UByteArray = Surface(1, 1).also { surface ->
        surface.canvas {
            drawRect(bounds1x1, Paint(ColorARGB.Blue, blendMode = BlendMode.SRC, antiAlias = false))
            drawRect(bounds1x1, Paint(
                ColorARGB.Red,
                blendMode = BlendMode.DST_OUT,
                maskFilter = maskFilter,
                antiAlias = false,
            ))
        }
    }.render().pixels

    private fun fixture(name: String): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResource("/picture/$name")).readText().trim(),
    )

    private companion object {
        val bounds3x1: RectF32 = RectF32.ofLTRB(0f, 0f, 3f, 1f)
        val bounds2x2: RectF32 = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val bounds1x1: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val red1x1: UByteArray = ubyteArrayOf(255u, 0u, 0u, 255u)
        val recoveryBlue2x2: UByteArray = ubyteArrayOf(
            17u, 61u, 211u, 255u,
            17u, 61u, 211u, 255u,
            17u, 61u, 211u, 255u,
            17u, 61u, 211u, 255u,
        )
        /** RGBA8 sRGB attachment encoding of opaque red premultiplied by the W5 gradient alpha. */
        val gradientMaskedRedPixels: UByteArray = ubyteArrayOf(
            114u, 0u, 0u, 43u,
            188u, 0u, 0u, 128u,
            235u, 0u, 0u, 212u,
        )
    }
}
