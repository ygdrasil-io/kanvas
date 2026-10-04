@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public pixel contract for Matrix filters evaluated in their destination domain. */
class W7ColorFilterDestinationDomainSurfacePixelTest {
    private companion object {
        const val SIZE = 32

        @AfterAll
        @JvmStatic
        fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun identityAndLumaRespectBothDestinationDomains() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
        )
        val luma = floatArrayOf(
            .2126f, .7152f, .0722f, 0f, 0f,
            .2126f, .7152f, .0722f, 0f, 0f,
            .2126f, .7152f, .0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val sample = Pixel(128, 64, 192, 255)
        val identityExpected = Domains.entries.associateWith { domain ->
            layerBuffer(domain, sample, identity)
        }
        val redLumaExpected = Domains.entries.associateWith { domain ->
            layerBuffer(domain, Pixel(255, 0, 0, 255), luma)
        }
        assertEquals(ubyteArrayOf(128u, 64u, 192u, 255u).toList(),
            identityExpected.getValue(Domains.ENCODED).copyOfRange(0, 4).toList())
        assertEquals(ubyteArrayOf(127u, 127u, 127u, 255u).toList(),
            redLumaExpected.getValue(Domains.LINEAR).copyOfRange(0, 4).toList())
        assertEquals(ubyteArrayOf(54u, 54u, 54u, 255u).toList(),
            redLumaExpected.getValue(Domains.ENCODED).copyOfRange(0, 4).toList())

        for (domain in Domains.entries) {
            val surface = Surface(SIZE, SIZE, config = config(domain))
            recordLayer(surface, sample, identity)
            assertBuffer(surface.render(), identityExpected.getValue(domain), "identity $domain")
            assertBuffer(surface.render(), identityExpected.getValue(domain), "identity replay $domain")

            val lumaSurface = Surface(SIZE, SIZE, config = config(domain))
            recordLayer(lumaSurface, Pixel(255, 0, 0, 255), luma)
            assertBuffer(lumaSurface.render(), redLumaExpected.getValue(domain), "red luma $domain")
        }
    }

    @Test
    fun partialAlphaMatrixUsesIndependentStageQuantization() {
        val matrix = floatArrayOf(
            .5f, .25f, 0f, 0f, .0625f,
            0f, .5f, .25f, 0f, .125f,
            .25f, 0f, .5f, 0f, .125f,
            0f, 0f, 0f, .5f, .25f,
        )
        val source = Pixel(64, 128, 192, 128)
        val expected = Domains.entries.associateWith { domain -> layerBuffer(domain, source, matrix) }
        assertEquals(1024 * 4, expected.getValue(Domains.LINEAR).size)
        assertTrue(!expected.getValue(Domains.LINEAR).contentEquals(expected.getValue(Domains.ENCODED)))
        for (domain in Domains.entries) {
            val surface = Surface(SIZE, SIZE, config = config(domain))
            recordLayer(surface, source, matrix)
            assertBuffer(surface.render(), expected.getValue(domain), "partial alpha $domain")
        }
    }

    @Test
    fun alphaBiasClampAndTransparentBlackAreDomainCorrect() {
        val clampAndAlpha = floatArrayOf(
            2f, 0f, 0f, 0f, -.25f,
            0f, 1.5f, 0f, 0f, 0f,
            0f, 0f, 1.5f, 0f, 0f,
            0f, 0f, 0f, 2f, -.25f,
        )
        val offsets = constantBiasMatrix()
        val inputs = listOf(Pixel(192, 64, 160, 128), Pixel(255, 180, 90, 0))
        val expected = Domains.entries.associateWith { domain ->
            inputs.map { source -> layerBuffer(domain, source, clampAndAlpha) }
        }
        val transparentExpected = Domains.entries.associateWith { domain ->
            layerBuffer(domain, Pixel(0, 0, 0, 0), offsets)
        }
        assertEquals(ubyteArrayOf(137u, 188u, 225u, 255u).toList(),
            transparentExpected.getValue(Domains.LINEAR).copyOfRange(0, 4).toList())
        assertEquals(ubyteArrayOf(64u, 128u, 191u, 255u).toList(),
            transparentExpected.getValue(Domains.ENCODED).copyOfRange(0, 4).toList())

        for (domain in Domains.entries) {
            inputs.forEachIndexed { index, source ->
                val surface = Surface(SIZE, SIZE, config = config(domain))
                recordLayer(surface, source, clampAndAlpha)
                assertBuffer(surface.render(), expected.getValue(domain)[index], "clamp/alpha $domain/$index")
            }
            val transparent = Surface(SIZE, SIZE, config = config(domain))
            recordLayer(transparent, Pixel(0, 0, 0, 0), offsets)
            assertBuffer(transparent.render(), transparentExpected.getValue(domain), "zero-alpha bias $domain")
        }
    }

    @Test
    fun transparentBlackBiasLayerMatchesPerStageOracle() {
        val matrix = constantBiasMatrix()
        val expected = Domains.entries.associateWith { domain ->
            layerBuffer(domain, Pixel(0, 0, 0, 0), matrix, sourceRect = RectI32.ofLTRB(4, 4, 8, 8))
        }
        assertEquals(ubyteArrayOf(137u, 188u, 225u, 255u).toList(), expected.getValue(Domains.LINEAR).copyOfRange(0, 4).toList())
        assertEquals(ubyteArrayOf(64u, 128u, 191u, 255u).toList(), expected.getValue(Domains.ENCODED).copyOfRange(0, 4).toList())
        for (domain in Domains.entries) {
            val surface = Surface(SIZE, SIZE, config = config(domain))
            recordLayer(surface, Pixel(0, 0, 0, 0), matrix, sourceRect = RectI32.ofLTRB(4, 4, 8, 8))
            val actual = surface.render()
            retainActualPixels(actual, "small-hint-layer-bias", domain)
            assertBuffer(actual, expected.getValue(domain), "transparent layer bias $domain")
        }
    }

    @Test
    fun restoreAlphaBackgroundsAndOrderMatchOracle() {
        val identity = identityMatrix()
        val child = Pixel(192, 64, 32, 255)
        val after = Pixel(15, 200, 90, 128)
        val backgrounds = listOf(null, Pixel(32, 96, 160, 255))
        val expected = Domains.entries.associateWith { domain -> backgrounds.map { background ->
            orderedLayerBuffer(domain, background, child, identity, 128, after, afterBeforeLayer = false)
        } }
        val movedAfter = Domains.entries.associateWith { domain -> backgrounds.map { background ->
            orderedLayerBuffer(domain, background, child, identity, 128, after, afterBeforeLayer = true)
        } }
        for (domain in Domains.entries) backgrounds.forEachIndexed { index, background ->
            assertTrue(!expected.getValue(domain)[index].contentEquals(movedAfter.getValue(domain)[index]),
                "draw-after must be order-sensitive domain=$domain background=$background")
            val surface = Surface(SIZE, SIZE, config = config(domain))
            surface.canvas {
                if (background != null) drawRect(FULL, Paint(background.toColor(), antiAlias = false))
                val before = if (background == null) Pixel(0, 0, 0, 0) else Pixel(20, 40, 80, 255)
                drawRect(RectF32.ofLTRB(0f, 0f, SIZE / 2f, SIZE.toFloat()),
                    Paint(before.toColor(), antiAlias = false))
                saveLayer(SaveLayerRec(paint = Paint(
                    color = ColorARGB.of(128, 0, 0, 0),
                    imageFilter = imageFilter(identity),
                    blendMode = BlendMode.SRC_OVER,
                    antiAlias = false,
                )))
                drawRect(FULL, Paint(child.toColor(), antiAlias = false))
                restore()
                drawRect(FULL, Paint(after.toColor(), antiAlias = false))
            }
            assertBuffer(surface.render(), expected.getValue(domain)[index], "restore alpha order $domain/$index")
        }
    }

    @Test
    fun destinationDomainsDoNotContaminateIndependentSurfaces() {
        val source = Pixel(64, 128, 192, 128)
        val matrix = floatArrayOf(
            .5f, .25f, 0f, 0f, .0625f,
            0f, .5f, .25f, 0f, .125f,
            .25f, 0f, .5f, 0f, .125f,
            0f, 0f, 0f, .5f, .25f,
        )
        val expected = Domains.entries.associateWith { domain -> layerBuffer(domain, source, matrix) }
        val recorder = PictureRecorder()
        recorder.beginRecording(FULL).apply {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = imageFilter(matrix), antiAlias = false)))
            drawRect(FULL, Paint(source.toColor(), antiAlias = false))
            restore()
        }
        val picture = recorder.finishRecordingAsPicture()
        val archived = assertNotNull(Picture.fromByteArray(picture.toByteArray()))

        val linearFirst = Surface(SIZE, SIZE, config = config(Domains.LINEAR))
        linearFirst.canvas { picture.playback(this) }
        assertBuffer(linearFirst.render(), expected.getValue(Domains.LINEAR), "linear before encoded")
        assertBuffer(linearFirst.render(), expected.getValue(Domains.LINEAR), "linear repeated")

        val encoded = Surface(SIZE, SIZE, config = config(Domains.ENCODED))
        encoded.canvas { archived.playback(this) }
        assertBuffer(encoded.render(), expected.getValue(Domains.ENCODED), "encoded between linear")
        assertBuffer(encoded.render(), expected.getValue(Domains.ENCODED), "encoded repeated")

        val linearLast = Surface(SIZE, SIZE, config = config(Domains.LINEAR))
        linearLast.canvas { picture.playback(this) }
        assertBuffer(linearLast.render(), expected.getValue(Domains.LINEAR), "linear after encoded")
        assertBuffer(linearLast.render(), expected.getValue(Domains.LINEAR), "linear final repeated")
    }

    @Test
    fun foreignEncodedLayerFamiliesRefuseAndRecover() {
        val matrix = ColorFilter.Matrix(ColorMatrixF32.of(identityMatrix()))
        val composed = ColorFilter.Compose(matrix, ColorFilter.Luma)
        val foreignImage = Image.fromPixels(1, 1, byteArrayOf(0, 0, 0, -1), alphaType = AlphaType.PREMUL)
        val cases: List<Pair<String, Canvas.() -> Unit>> = listOf(
            "nested saveLayer" to {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = imageFilter(identityMatrix()), antiAlias = false)))
                saveLayer()
                restore()
                restore()
            },
            "initWithPrevious" to {
                saveLayer(SaveLayerRec(initWithPrevious = true,
                    paint = Paint(imageFilter = imageFilter(identityMatrix()), antiAlias = false)))
                restore()
            },
            "backdrop" to {
                saveLayer(SaveLayerRec(backdrop = ImageFilter.ColorFilter(ColorFilter.Luma),
                    paint = Paint(imageFilter = imageFilter(identityMatrix()), antiAlias = false)))
                restore()
            },
            "foreign Luma" to {
                saveLayer(SaveLayerRec(paint = Paint(
                    imageFilter = ImageFilter.ColorFilter(ColorFilter.Luma), antiAlias = false)))
                restore()
            },
            "composed Matrix" to {
                saveLayer(SaveLayerRec(paint = Paint(
                    imageFilter = ImageFilter.ColorFilter(composed, null), antiAlias = false)))
                restore()
            },
            "non SrcOver blend" to {
                saveLayer(SaveLayerRec(paint = Paint(
                    imageFilter = imageFilter(identityMatrix()), blendMode = BlendMode.MULTIPLY, antiAlias = false)))
                restore()
            },
            "shader input" to {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = imageFilter(identityMatrix()), antiAlias = false)))
                drawRect(FULL, Paint(shader = Shader.SolidColor(ColorARGB.Red), antiAlias = false))
                restore()
            },
            "image input" to {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = imageFilter(identityMatrix()), antiAlias = false)))
                drawImage(foreignImage, FULL, SamplingOptions.NEAREST, Paint(antiAlias = false))
                restore()
            },
            "direct paint colorFilter" to {
                drawRect(FULL, Paint(colorFilter = matrix, antiAlias = false))
            },
        )
        val recoveryExpected = layerBuffer(Domains.ENCODED, Pixel(17, 61, 211, 255), identityMatrix())
        for ((name, append) in cases) {
            val surface = Surface(SIZE, SIZE, config = config(Domains.ENCODED))
            surface.canvas { append() }
            val sentinel = UByteArray(SIZE * SIZE * 4) { 0x5au }
            val before = sentinel.copyOf()
            assertFailsWith<IllegalStateException>(name) { surface.readPixels(FULL, sentinel) }
            assertContentEquals(before, sentinel, "$name refusal must be transactional")
            surface.discardRecordedOperations()
            recordLayer(surface, Pixel(17, 61, 211, 255), identityMatrix())
            assertBuffer(surface.render(), recoveryExpected, "$name recovery")
        }
    }

    @Test
    fun directLinearMatrixBiasRecordsOutsideExtentFalsifier() {
        val bias = constantBiasMatrix()
        val rect = RectI32.ofLTRB(4, 4, 8, 8)
        val black = Pixel(0, 0, 0, 255)
        val identityExpected = directRectBuffer(Domains.LINEAR, black, identityMatrix(), rect)
        val biasDemandedOutputExpected = directDemandBuffer(Domains.LINEAR, black, bias, rect, FULL_I)

        // Bounded identity control: the ordinary direct ImageFilter path produces the source
        // rectangle and no color outside that command's geometry.
        val boundedControl = Surface(SIZE, SIZE, config = config(Domains.LINEAR))
        boundedControl.canvas {
            drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint(
                imageFilter = imageFilter(identityMatrix()), antiAlias = false,
            ))
        }
        assertBuffer(boundedControl.render(), identityExpected, "direct LINEAR bounded identity control")

        // W6a's direct construction retains the hard rect as sourceDomain and the unclipped root
        // as effectiveDesired; W6b ColorFilter demands that full output because this matrix
        // affects transparent black. Thus the independent oracle expects bias over the complete
        // destination, including transparent-black input beyond the compact source. RenderResult
        // still cannot establish a native sample coordinate beyond that source texture.
        val falsifier = Surface(SIZE, SIZE, config = config(Domains.LINEAR))
        falsifier.canvas {
            drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint(
                imageFilter = imageFilter(bias), antiAlias = false,
            ))
        }
        val actual = falsifier.render()
        retainActualPixels(actual, "direct-full-demand-bias", Domains.LINEAR)
        assertBuffer(actual, biasDemandedOutputExpected, "direct LINEAR Matrix bias full demanded output")
    }

    private enum class Domains(val composition: CompositionDomain, val format: GPUColorFormat) {
        LINEAR(CompositionDomain.LINEAR, GPUColorFormat.RGBA8_UNORM_SRGB),
        ENCODED(CompositionDomain.SRGB_ENCODED, GPUColorFormat.RGBA8_UNORM),
    }

    private data class Pixel(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
        fun toColor() = ColorARGB.of(alpha, red, green, blue)
    }

    private data class Premul(val r: Double, val g: Double, val b: Double, val a: Double) {
        operator fun times(scale: Double) = Premul(r * scale, g * scale, b * scale, a * scale)
        operator fun plus(other: Premul) = Premul(r + other.r, g + other.g, b + other.b, a + other.a)
    }

    private val FULL = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
    private val FULL_I = RectI32.ofLTRB(0, 0, SIZE, SIZE)

    private fun config(domain: Domains) = RenderConfig(
        compositionDomain = domain.composition,
        gpuColorFormat = domain.format,
    )

    private fun identityMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
    )

    private fun constantBiasMatrix() = floatArrayOf(
        0f, 0f, 0f, 0f, .25f,
        0f, 0f, 0f, 0f, .5f,
        0f, 0f, 0f, 0f, .75f,
        0f, 0f, 0f, 0f, 1f,
    )

    private fun imageFilter(values: FloatArray) = ImageFilter.ColorFilter(
        ColorFilter.Matrix(ColorMatrixF32.of(values)), null,
    )

    private fun recordLayer(
        surface: Surface,
        source: Pixel,
        matrix: FloatArray,
        sourceRect: RectI32 = FULL_I,
    ) = surface.canvas {
        saveLayer(SaveLayerRec(
            bounds = RectF32.ofLTRB(sourceRect.left.toFloat(), sourceRect.top.toFloat(),
                sourceRect.right.toFloat(), sourceRect.bottom.toFloat()),
            paint = Paint(imageFilter = imageFilter(matrix), antiAlias = false),
        ))
        drawRect(RectF32.ofLTRB(sourceRect.left.toFloat(), sourceRect.top.toFloat(),
            sourceRect.right.toFloat(), sourceRect.bottom.toFloat()), Paint(source.toColor(), antiAlias = false))
        restore()
    }

    /** Expected pixels model source, filtered, and restore attachment writes separately. */
    private fun layerBuffer(
        domain: Domains,
        source: Pixel,
        matrix: FloatArray,
        groupAlpha: Int = 255,
        sourceRect: RectI32 = FULL_I,
        background: Pixel? = null,
    ): UByteArray {
        val destination = background?.let { quantize(sourcePixel(it, domain), domain) } ?: transparent()
        return UByteArray(SIZE * SIZE * 4).also { result ->
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val inSource = sourceRect.contains(x, y)
                val sourceStage = if (inSource) quantize(sourcePixel(source, domain), domain) else transparent()
                val filtered = quantize(applyMatrix(sourceStage, matrix), domain)
                result.putPixel(x, y, quantize(over(filtered * (groupAlpha / 255.0), destination), domain), domain)
            }
        }
    }

    private fun orderedLayerBuffer(
        domain: Domains,
        background: Pixel?,
        child: Pixel,
        matrix: FloatArray,
        groupAlpha: Int,
        after: Pixel,
        afterBeforeLayer: Boolean,
    ): UByteArray {
        val before = if (background == null) Pixel(0, 0, 0, 0) else Pixel(20, 40, 80, 255)
        val beforePixel = sourcePixel(before, domain)
        val backgroundPixel = background?.let { quantize(sourcePixel(it, domain), domain) } ?: transparent()
        val afterPixel = sourcePixel(after, domain)
        val groupSource = quantize(sourcePixel(child, domain), domain)
        val filtered = quantize(applyMatrix(groupSource, matrix), domain)
        return UByteArray(SIZE * SIZE * 4).also { out ->
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                var parent = backgroundPixel
                if (x < SIZE / 2) parent = quantize(over(beforePixel, parent), domain)
                if (afterBeforeLayer) parent = quantize(over(afterPixel, parent), domain)
                parent = quantize(over(filtered * (groupAlpha / 255.0), parent), domain)
                if (!afterBeforeLayer) parent = quantize(over(afterPixel, parent), domain)
                out.putPixel(x, y, parent, domain)
            }
        }
    }

    private fun directRectBuffer(domain: Domains, source: Pixel, matrix: FloatArray, rect: RectI32): UByteArray {
        val zero = transparent()
        val color = quantize(sourcePixel(source, domain), domain)
        val filtered = quantize(applyMatrix(color, matrix), domain)
        return UByteArray(SIZE * SIZE * 4).also { out ->
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                out.putPixel(x, y, if (rect.contains(x, y)) filtered else zero, domain)
            }
        }
    }

    private fun directDemandBuffer(
        domain: Domains,
        source: Pixel,
        matrix: FloatArray,
        sourceRect: RectI32,
        demandedOutput: RectI32,
    ): UByteArray {
        val inputInsideSource = quantize(sourcePixel(source, domain), domain)
        val transparentInput = transparent()
        val result = UByteArray(SIZE * SIZE * 4)
        for (y in demandedOutput.top until demandedOutput.bottom) for (x in demandedOutput.left until demandedOutput.right) {
            val input = if (sourceRect.contains(x, y)) inputInsideSource else transparentInput
            result.putPixel(x, y, quantize(applyMatrix(input, matrix), domain), domain)
        }
        return result
    }

    private fun sourcePixel(pixel: Pixel, domain: Domains): Premul {
        val alpha = pixel.alpha / 255.0
        fun channel(byte: Int): Double {
            val value = byte / 255.0
            return if (domain == Domains.ENCODED) value else eotf(value)
        }
        return Premul(channel(pixel.red) * alpha, channel(pixel.green) * alpha,
            channel(pixel.blue) * alpha, alpha)
    }

    private fun applyMatrix(source: Premul, values: FloatArray): Premul {
        val straight = if (source.a == 0.0) doubleArrayOf(0.0, 0.0, 0.0, 0.0) else
            doubleArrayOf(source.r / source.a, source.g / source.a, source.b / source.a, source.a)
        val output = DoubleArray(4) { row ->
            (0..3).sumOf { column -> values[row * 5 + column].toDouble() * straight[column] } +
                values[row * 5 + 4].toDouble()
        }.map { it.coerceIn(0.0, 1.0) }
        return Premul(output[0] * output[3], output[1] * output[3], output[2] * output[3], output[3])
    }

    private fun over(source: Premul, destination: Premul): Premul = source + destination * (1.0 - source.a)

    private fun quantize(pixel: Premul, domain: Domains): Premul {
        fun channel(value: Double): Double {
            val bounded = value.coerceIn(0.0, 1.0)
            val byte = if (domain == Domains.LINEAR) (oetf(bounded) * 255.0).roundToInt()
                else (bounded * 255.0).roundToInt()
            val normalized = byte.coerceIn(0, 255) / 255.0
            return if (domain == Domains.LINEAR) eotf(normalized) else normalized
        }
        return Premul(channel(pixel.r), channel(pixel.g), channel(pixel.b),
            (pixel.a.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255) / 255.0)
    }

    private fun transparent() = Premul(0.0, 0.0, 0.0, 0.0)

    private fun eotf(value: Double): Double = if (value <= .04045) value / 12.92
        else ((value + .055) / 1.055).pow(2.4)

    private fun oetf(value: Double): Double = if (value <= .0031308) value * 12.92
        else 1.055 * value.pow(1.0 / 2.4) - .055

    private fun UByteArray.putPixel(x: Int, y: Int, pixel: Premul, domain: Domains) {
        val offset = (y * SIZE + x) * 4
        fun output(value: Double) = if (domain == Domains.LINEAR) oetf(value.coerceIn(0.0, 1.0)) else value.coerceIn(0.0, 1.0)
        this[offset] = (output(pixel.r) * 255.0).roundToInt().coerceIn(0, 255).toUByte()
        this[offset + 1] = (output(pixel.g) * 255.0).roundToInt().coerceIn(0, 255).toUByte()
        this[offset + 2] = (output(pixel.b) * 255.0).roundToInt().coerceIn(0, 255).toUByte()
        this[offset + 3] = (pixel.a.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255).toUByte()
    }

    private fun assertBuffer(actual: RenderResult, expected: UByteArray, label: String) {
        assertNative(actual, label)
        assertEquals(SIZE, actual.width, label)
        assertEquals(SIZE, actual.height, label)
        assertEquals(PixelFormat.RGBA8, actual.format, label)
        assertEquals(SIZE * SIZE * 4, actual.pixels.size, label)
        for (offset in expected.indices) {
            val delta = abs(actual.pixels[offset].toInt() - expected[offset].toInt())
            if (offset % 4 == 3) assertEquals(0, delta, "$label alpha pixel=${offset / 4}")
            else assertTrue(delta <= 2, "$label channel=$offset expected=${expected[offset]} actual=${actual.pixels[offset]}")
        }
    }

    private fun assertNative(result: RenderResult, label: String) {
        val evidence = "$label scopes=${result.nativeEvidenceScopeKinds} stats=${result.stats} " +
            "steps=${result.structuralSteps} counters=${result.nativeEvidenceCounters} " +
            "diagnostics=${result.diagnostics.summary()} bytes=${result.pixels.size}"
        println("w7.destination-domain.native $evidence")
        assertTrue(result.isClean, evidence)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), evidence)
        val submittedIndex = result.structuralSteps.indexOf("QueueSubmitted")
        val completedIndex = result.structuralSteps.indexOf("CompletionSucceeded")
        assertTrue(submittedIndex >= 0, evidence)
        assertTrue(completedIndex > submittedIndex, evidence)
        assertTrue(result.stats.opsDispatched > 0, evidence)
        assertEquals(0, result.stats.opsRefused, evidence)
        assertEquals(1L, result.nativeEvidenceCounters["submits"], evidence)
        assertEquals(1L, result.nativeEvidenceCounters["readbackCopies"], evidence)
        assertTrue(result.nativeEvidenceCounters.containsKey("draws"), evidence)
        assertTrue(result.nativeEvidenceCounters.containsKey("drawIndexed"), evidence)
        assertTrue(
            (result.nativeEvidenceCounters.getValue("draws") +
                result.nativeEvidenceCounters.getValue("drawIndexed")) > 0L,
            evidence,
        )
        assertTrue((result.nativeEvidenceCounters["pipelineBinds"] ?: 0L) > 0L, evidence)
    }

    private fun retainActualPixels(result: RenderResult, label: String, domain: Domains) {
        val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir") ?: return
        val directory = File(evidenceRoot)
        check(directory.isDirectory) { "W7 evidence directory does not exist: $directory" }
        val file = File(directory, "w7-destination-domain-$label-${domain.composition.name.lowercase()}.rgba")
        check(!file.exists()) { "Refusing to overwrite W7 pixel evidence: $file" }
        val bytes = ByteArray(result.pixels.size) { index -> result.pixels[index].toByte() }
        file.outputStream().use { output -> output.write(bytes) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        println("w7.destination-domain.actual-buffer path=${file.absolutePath} byteCount=${bytes.size} sha256=$sha256")
    }

}
