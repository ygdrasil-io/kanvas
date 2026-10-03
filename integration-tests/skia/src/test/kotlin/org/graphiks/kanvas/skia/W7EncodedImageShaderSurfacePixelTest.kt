@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Native literal-pixel witnesses for simple encoded-domain image shader leaves. */
class W7EncodedImageShaderSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun disposeW7EncodedImageShaderSurfacePixelTestGpu() = GPUBackendRuntimeFactory.dispose()

        val DOMAINS = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)
        val DOMAIN_SEQUENCE = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED,
            CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)
        val SOURCE_FORMATS = listOf(ColorType.RGBA_8888, ColorType.BGRA_8888)
        val OUTPUT_FORMATS = listOf(PixelFormat.RGBA8, PixelFormat.BGRA8)
        val GRAY_221 = ColorARGB.of(0xFF, 221, 221, 221)
        val BYTE_ALPHA_WHITE = ColorARGB.of(128, 255, 255, 255)
        val GRAY_RGBA = intArrayOf(221, 221, 221, 255)
        val RECOVERY_PIXEL = intArrayOf(230, 165, 165, 255)
        val SOURCE_2X2 = byteArrayOf(128.toByte(), 0, 0, 128.toByte(), 0, 64, 0, 64,
            0, 0, 32, 32, 64, 32, 16, 128.toByte())
        val SOURCE_NON_SATURATED_AND_ZERO_ALPHA = byteArrayOf(64, 32, 16, 128.toByte(), 63, 17, 9, 0)
        val LINEAR_2X2 = listOf(intArrayOf(230, 194, 194, 255), intArrayOf(208, 226, 208, 255),
            intArrayOf(215, 215, 223, 255), intArrayOf(203, 196, 195, 255))
        val ENCODED_2X2 = listOf(intArrayOf(230, 165, 165, 255), intArrayOf(193, 225, 193, 255),
            intArrayOf(207, 207, 223, 255), intArrayOf(197, 181, 173, 255))
        val LINEAR_NON_SATURATED = intArrayOf(203, 196, 195, 255)
        val ENCODED_NON_SATURATED = intArrayOf(197, 181, 173, 255)
        val REPEAT_X = intArrayOf(0, 1, 0, 1, 0, 1, 0, 1)
        val MIRROR_Y = intArrayOf(0, 1, 1, 0, 0, 1, 1, 0)
    }

    @Test
    fun nonUniformImageShaderAlternatesDomainsAndLayouts() {
        for (sourceFormat in SOURCE_FORMATS) {
            val image = sourceImage(sourceFormat, SOURCE_2X2, 2, 2, "alternating-${sourceFormat.name}")
            for (outputFormat in OUTPUT_FORMATS) {
                for (domain in DOMAIN_SEQUENCE) {
                    val surface = Surface(8, 8, format = outputFormat,
                        config = RenderConfig.DEFAULT.copy(compositionDomain = domain))
                    val canvas = surface.canvas()
                    canvas.drawRect(RectF32(0f, 0f, 8f, 8f), Paint(color = GRAY_221, antiAlias = false))
                    canvas.drawRect(RectF32(0f, 0f, 8f, 8f), imagePaint(image, TileMode.REPEAT, TileMode.MIRROR))
                    val operationCount = surface.snapshotOps().size
                    val first = captureSurface("alternate-${sourceFormat.name}-${outputFormat.name}-${domain.name}-first", surface, operationCount)
                    val replay = captureSurface("alternate-${sourceFormat.name}-${outputFormat.name}-${domain.name}-replay", surface, operationCount)
                    assertEquals(2, operationCount, "background and image-shader rectangle")
                    assertCleanDispatch(first.result, 2, "first ${domain.name} render")
                    assertCleanDispatch(replay.result, 2, "replay ${domain.name} render")
                    assertArrayEquals(first.rgba, replay.rgba, "same-surface full RGBA replay")
                    assertPixels(first.rgba, 8, 8, expected2x2Tiled(domain), "${sourceFormat.name}/${outputFormat.name}/${domain.name} first")
                    assertPixels(replay.rgba, 8, 8, expected2x2Tiled(domain), "${sourceFormat.name}/${outputFormat.name}/${domain.name} replay")
                }
            }
        }
    }

    @Test
    fun translatedClampedAndDecalImageShaderKeepsBounds() {
        for (sourceFormat in SOURCE_FORMATS) {
            val image = sourceImage(sourceFormat, SOURCE_2X2, 2, 2, "translated-${sourceFormat.name}")
            for (outputFormat in OUTPUT_FORMATS) for (domain in DOMAINS) {
                for ((tileMode, label) in listOf(TileMode.CLAMP to "clamp", TileMode.DECAL to "decal")) {
                    val surface = Surface(6, 4, format = outputFormat,
                        config = RenderConfig.DEFAULT.copy(compositionDomain = domain))
                    val canvas = surface.canvas()
                    canvas.drawRect(RectF32(0f, 0f, 6f, 4f), Paint(color = GRAY_221, antiAlias = false))
                    canvas.clipRect(RectF32(0f, 0f, 5f, 4f), antiAlias = false)
                    canvas.translate(1f, 0f)
                    canvas.drawRect(RectF32(-1f, 0f, 5f, 4f), imagePaint(image, tileMode, tileMode))
                    val operationCount = surface.snapshotOps().size
                    val first = captureSurface("translated-$label-${sourceFormat.name}-${outputFormat.name}-${domain.name}-first", surface, operationCount)
                    val replay = captureSurface("translated-$label-${sourceFormat.name}-${outputFormat.name}-${domain.name}-replay", surface, operationCount)
                    assertCleanDispatch(first.result, 2, "$label first ${domain.name} render")
                    assertCleanDispatch(replay.result, 2, "$label replay ${domain.name} render")
                    assertArrayEquals(first.rgba, replay.rgba, "$label same-surface full RGBA replay")
                    val expected = expectedTranslated(domain, tileMode)
                    assertPixels(first.rgba, 6, 4, expected, "$label ${sourceFormat.name}/${outputFormat.name}/${domain.name} first")
                    assertPixels(replay.rgba, 6, 4, expected, "$label ${sourceFormat.name}/${outputFormat.name}/${domain.name} replay")
                }
            }
        }
    }

    @Test
    fun nonsaturatedAndZeroAlphaImageTexelsKeepLiteralPixels() {
        for (sourceFormat in SOURCE_FORMATS) {
            val image = sourceImage(sourceFormat, SOURCE_NON_SATURATED_AND_ZERO_ALPHA, 2, 1, "zero-alpha-${sourceFormat.name}")
            for (outputFormat in OUTPUT_FORMATS) for (domain in DOMAINS) {
                val surface = Surface(6, 3, format = outputFormat,
                    config = RenderConfig.DEFAULT.copy(compositionDomain = domain))
                val canvas = surface.canvas()
                canvas.drawRect(RectF32(0f, 0f, 6f, 3f), Paint(color = GRAY_221, antiAlias = false))
                canvas.drawRect(RectF32(0f, 0f, 6f, 3f), imagePaint(image, TileMode.REPEAT, TileMode.MIRROR))
                val operationCount = surface.snapshotOps().size
                val first = captureSurface("zero-alpha-${sourceFormat.name}-${outputFormat.name}-${domain.name}-first", surface, operationCount)
                val replay = captureSurface("zero-alpha-${sourceFormat.name}-${outputFormat.name}-${domain.name}-replay", surface, operationCount)
                assertEquals(2, operationCount, "background and image-shader rectangle")
                assertCleanDispatch(first.result, 2, "first ${domain.name} render")
                assertCleanDispatch(replay.result, 2, "replay ${domain.name} render")
                assertArrayEquals(first.rgba, replay.rgba, "same-surface full RGBA replay")
                val expected = expectedZeroAlphaPattern(domain)
                assertPixels(first.rgba, 6, 3, expected, "${sourceFormat.name}/${outputFormat.name}/${domain.name} first")
                assertPixels(replay.rgba, 6, 3, expected, "${sourceFormat.name}/${outputFormat.name}/${domain.name} replay")
            }
        }
    }

    @Test
    fun encodedImageShaderExclusionsRecoverTransactionally() {
        val full = RectF32(0f, 0f, 2f, 2f)
        val ordinaryImage = sourceImage(ColorType.RGBA_8888, SOURCE_2X2, 2, 2, "exclusion-image")
        val recoveryImage = sourceImage(ColorType.RGBA_8888,
            byteArrayOf(128.toByte(), 0, 0, 128.toByte()), 1, 1, "exclusion-recovery-image")
        val excludedRequests: List<Triple<String, String, Paint.() -> Paint>> = listOf(
            Triple("aa", "geometry") { copy(antiAlias = true) },
            Triple("linear-sampling", "geometry") {
                copy(shader = Shader.Image(ordinaryImage, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.LINEAR))
            },
            Triple("unpremul", "image") {
                copy(shader = Shader.Image(sourceImage(ColorType.RGBA_8888, SOURCE_2X2, 2, 2,
                    "unpremul-image", alphaType = AlphaType.UNPREMUL), TileMode.REPEAT, TileMode.MIRROR))
            },
            Triple("linear-srgb", "image") {
                copy(shader = Shader.Image(sourceImage(ColorType.RGBA_8888, SOURCE_2X2, 2, 2,
                    "linear-srgb-image", colorSpace = ColorSpace.LINEAR_SRGB), TileMode.REPEAT, TileMode.MIRROR))
            },
            Triple("local-matrix", "source") {
                copy(shader = Shader.WithLocalMatrix(
                    Shader.Image(ordinaryImage, TileMode.REPEAT, TileMode.MIRROR),
                    Matrix3x3F32.translation(1f, 0f),
                ))
            },
            Triple("paint-color-filter", "source") { copy(colorFilter = ColorFilter.Luma) },
            Triple("non-src-over", "blend") { copy(blendMode = BlendMode.MULTIPLY) },
            Triple("fractional-rect", "geometry") { this },
        )

        for ((name, suffix, customize) in excludedRequests) {
            val surface = Surface(2, 2, config = RenderConfig.DEFAULT.copy(
                compositionDomain = CompositionDomain.SRGB_ENCODED,
            ))
            val canvas = surface.canvas()
            canvas.drawRect(full, Paint(color = GRAY_221, antiAlias = false))
            val ordinaryPaint = imagePaint(ordinaryImage, TileMode.REPEAT, TileMode.MIRROR)
            val excludedPaint = customize(ordinaryPaint)
            val rect = if (name == "fractional-rect") RectF32(0.25f, 0.25f, 2f, 2f) else full
            canvas.drawRect(rect, excludedPaint)
            val failure = try {
                surface.render()
                null
            } catch (caught: IllegalStateException) {
                println("W7_ENCODED_IMAGE_EXCLUSION label=$name exception=${caught.message}")
                caught
            }
            assertEquals("unsupported.surface.composition.$suffix",
                failure?.message.orEmpty().substringBefore(':'), name)

            surface.discardRecordedOperations()
            canvas.drawRect(full, Paint(color = GRAY_221, antiAlias = false))
            canvas.drawRect(full, imagePaint(recoveryImage, TileMode.REPEAT, TileMode.MIRROR))
            val operationCount = surface.snapshotOps().size
            val first = captureSurface("exclusion-recovery-$name-first", surface, operationCount)
            val replay = captureSurface("exclusion-recovery-$name-replay", surface, operationCount)
            assertEquals(2, operationCount, "$name recovery background and image rectangle")
            assertCleanDispatch(first.result, 2, "$name recovery first dispatch")
            assertCleanDispatch(replay.result, 2, "$name recovery replay dispatch")
            assertArrayEquals(first.rgba, replay.rgba, "$name recovery same-surface replay")
            val expected = ByteArray(2 * 2 * 4) { offset -> RECOVERY_PIXEL[offset % 4].toByte() }
            assertPixels(first.rgba, 2, 2, expected, "$name recovery full literal RGBA")
            assertPixels(replay.rgba, 2, 2, expected, "$name recovery replay full literal RGBA")
        }
    }

    private fun imagePaint(image: Image, x: TileMode, y: TileMode) = Paint(
        color = BYTE_ALPHA_WHITE, shader = Shader.Image(image, x, y), antiAlias = false)

    private fun sourceImage(colorType: ColorType, rgbaBytes: ByteArray, width: Int, height: Int,
        sourceId: String, colorSpace: ColorSpace = ColorSpace.SRGB,
        alphaType: AlphaType = AlphaType.PREMUL): Image = Image.fromPixels(
        width = width, height = height,
        pixels = if (colorType == ColorType.BGRA_8888) swizzlePixels(rgbaBytes) else rgbaBytes.copyOf(),
        colorType = colorType, sourceId = sourceId, alphaType = alphaType, colorSpace = colorSpace)

    private fun expected2x2Tiled(domain: CompositionDomain): ByteArray {
        val texels = if (domain == CompositionDomain.LINEAR) LINEAR_2X2 else ENCODED_2X2
        val result = ByteArray(8 * 8 * 4)
        for (y in 0 until 8) for (x in 0 until 8)
            putPixel(result, (y * 8 + x) * 4, texels[MIRROR_Y[y] * 2 + REPEAT_X[x]])
        return result
    }

    private fun expectedTranslated(domain: CompositionDomain, tileMode: TileMode): ByteArray {
        val texels = if (domain == CompositionDomain.LINEAR) LINEAR_2X2 else ENCODED_2X2
        val result = ByteArray(6 * 4 * 4)
        for (y in 0 until 4) for (x in 0 until 6) {
            var rgba = GRAY_RGBA
            if (x < 5) {
                val localX = x - 1
                val sourceX = when {
                    tileMode == TileMode.DECAL && localX !in 0..1 -> null
                    tileMode == TileMode.DECAL -> localX
                    else -> localX.coerceIn(0, 1)
                }
                val sourceY = when {
                    tileMode == TileMode.DECAL && y !in 0..1 -> null
                    tileMode == TileMode.DECAL -> y
                    else -> y.coerceIn(0, 1)
                }
                if (sourceX != null && sourceY != null) rgba = texels[sourceY * 2 + sourceX]
            }
            putPixel(result, (y * 6 + x) * 4, rgba)
        }
        return result
    }

    private fun expectedZeroAlphaPattern(domain: CompositionDomain): ByteArray {
        val opaque = if (domain == CompositionDomain.LINEAR) LINEAR_NON_SATURATED else ENCODED_NON_SATURATED
        val result = ByteArray(6 * 3 * 4)
        for (y in 0 until 3) for (x in 0 until 6)
            putPixel(result, (y * 6 + x) * 4, if (x % 2 == 0) opaque else GRAY_RGBA)
        return result
    }

    private fun captureSurface(label: String, surface: Surface, operationCount: Int): SurfaceCapture {
        val result = surface.render()
        val rgba = toRgba(result)
        logSurface(label, operationCount, result, rgba)
        return SurfaceCapture(result, rgba)
    }

    private fun toRgba(result: RenderResult): ByteArray {
        val bytes = result.pixels.map { it.toByte() }.toByteArray()
        return if (result.format == PixelFormat.BGRA8) swizzlePixels(bytes) else bytes
    }

    private fun swizzlePixels(bytes: ByteArray): ByteArray {
        require(bytes.size % 4 == 0)
        val output = bytes.copyOf()
        for (offset in bytes.indices step 4) {
            output[offset] = bytes[offset + 2]
            output[offset + 2] = bytes[offset]
        }
        return output
    }

    private fun assertCleanDispatch(result: RenderResult, expectedDispatches: Int, label: String) {
        assertEquals(expectedDispatches, result.stats.opsDispatched, "$label native dispatch count")
        assertEquals(0, result.stats.opsRefused, "$label refusal count")
        assertEquals(0, result.diagnostics.fatalCount, "$label fatal diagnostics")
        assertTrue(result.diagnostics.isEmpty, "$label ${result.diagnostics.summary()}")
        assertTrue(result.stats.drawCallCount > 0, "$label issues a native draw call")
        assertTrue(result.stats.pipelineCount > 0, "$label binds a native pipeline")
    }

    private fun assertPixels(actual: ByteArray, width: Int, height: Int, expected: ByteArray, label: String) {
        assertEquals(width * height * 4, actual.size, "$label full RGBA buffer size")
        assertEquals(width * height * 4, expected.size, "$label independent literal RGBA oracle size")
        assertArrayEquals(expected, actual, "$label complete literal RGBA pixels")
    }

    private fun putPixel(target: ByteArray, offset: Int, rgba: IntArray) {
        for (channel in 0..3) target[offset + channel] = rgba[channel].toByte()
    }

    private fun logSurface(label: String, operations: Int, result: RenderResult, rgba: ByteArray) {
        println("W7_ENCODED_IMAGE_NATIVE label=$label operations=$operations stats=${result.stats} " +
            "diagnostics=${result.diagnostics.summary()} nativeCounters=${result.nativeEvidenceCounters} " +
            "nativeScopes=${result.nativeEvidenceScopeKinds} sha256=${sha256(rgba)}")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class SurfaceCapture(val result: RenderResult, val rgba: ByteArray)

}
