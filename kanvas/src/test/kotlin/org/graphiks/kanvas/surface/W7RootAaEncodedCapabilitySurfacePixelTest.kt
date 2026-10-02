@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.paint.GradientStop
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class W7RootAaEncodedCapabilitySurfacePixelTest {
    companion object {
        private const val WIDTH = 12
        private const val HEIGHT = 12
        private val vertical = W7MsaaCompositionCpuOracle.Stroke(4f, 2f, 4f, 10f, 5f)
        private val mixed = ColorARGB.of(128, 128, 64, 32)
        private val black = ColorARGB.Black
        private val white = ColorARGB.White
        private val outside = listOf(Pixel(0, 5), Pixel(7, 5))
        private val edgePoints = listOf(Pixel(1, 5), Pixel(6, 5))
        private val inside = Pixel(3, 5)

        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    private data class Pixel(val x: Int, val y: Int)

    @Test
    fun encodedRootAlphaByteLayoutAndNativeFormatRemainDistinct() {
        val encoded = CompositionDomain.SRGB_ENCODED
        val linear = CompositionDomain.LINEAR
        val points = edgePoints + inside + outside
        val expectedByDomain = listOf(encoded, linear).associateWith { domain ->
            points.associateWith { point ->
                val mask = strokeMask(point)
                W7MsaaCompositionCpuOracle.pixel(
                    W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), domain, null, listOf(mask to mixed),
                )
            }
        }
        assertMixedMasks()
        val duplicateAlpha = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.opacity(W7CompositionCpuOracle.solid(mixed, encoded), 128), encoded,
        )
        val fullInterior = expectedByDomain.getValue(encoded).getValue(inside)
        assertTrue((0..2).any { fullInterior.channels[it].intersect(duplicateAlpha.channels[it]).isEmpty() },
            "interior must distinguish a second application of source alpha")
        for (point in edgePoints) {
            val target = expectedByDomain.getValue(encoded).getValue(point)
            assertTrue(target.channels[3].intersect(expectedByDomain.getValue(linear).getValue(point).channels[3]).isNotEmpty(),
                "alpha alone does not distinguish domains at $point")
            val wrongOrder = W7CompositionCpuOracle.swapRedBlue(target)
            assertTrue((0..2).any { target.channels[it].intersect(wrongOrder.channels[it]).isEmpty() },
                "wrong red/blue order must be disjoint at $point")
        }

        for (point in edgePoints) {
            val encodedExpected = expectedByDomain.getValue(encoded).getValue(point)
            val linearExpected = expectedByDomain.getValue(linear).getValue(point)
            assertTrue((0..2).any { encodedExpected.channels[it].intersect(linearExpected.channels[it]).isEmpty() },
                "encoded and LINEAR RGB witnesses must be disjoint at $point")
        }

        data class RenderCase(
            val domain: CompositionDomain,
            val pixelFormat: PixelFormat,
            val physicalFormat: GPUColorFormat,
            val expected: Map<Pixel, W7CompositionCpuOracle.CompositionEnvelope>,
        )
        val renderCases = buildList {
            for (pixelFormat in PixelFormat.entries) for ((domain, physicalFormat) in listOf(
                encoded to GPUColorFormat.AUTO,
                encoded to GPUColorFormat.RGBA8_UNORM,
                linear to GPUColorFormat.RGBA8_UNORM_SRGB,
            )) add(RenderCase(domain, pixelFormat, physicalFormat,
                expectedByDomain.getValue(domain).mapValues { (_, value) -> swizzle(value, pixelFormat) }))
        }
        for (case in renderCases) {
            val surface = Surface(WIDTH, HEIGHT, case.pixelFormat, RenderConfig(
                compositionDomain = case.domain, gpuColorFormat = case.physicalFormat,
            ))
            recordMixedStroke(surface)
            val first = surface.render()
            assertNative(first, "domain=${case.domain} format=${case.pixelFormat} physical=${case.physicalFormat}")
            points.forEach { point -> assertPixel(first, point, case.expected.getValue(point)) }
            val second = surface.render()
            assertNative(second, "repeat domain=${case.domain} format=${case.pixelFormat} physical=${case.physicalFormat}")
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun rootDomainsAlternateWithoutSampleCacheReuse() {
        assertMixedMasks()
        val domains = listOf(
            CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR,
            CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR,
        )
        val formats = listOf(PixelFormat.RGBA8, PixelFormat.BGRA8)
        val points = edgePoints + inside + outside
        val expected = domains.associateWith { domain -> points.associateWith { point ->
            W7MsaaCompositionCpuOracle.pixel(
                W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), domain, null,
                listOf(strokeMask(point) to mixed),
            )
        } }
        edgePoints.forEach { point ->
            assertTrue((0..2).any {
                expected.getValue(CompositionDomain.SRGB_ENCODED).getValue(point).channels[it]
                    .intersect(expected.getValue(CompositionDomain.LINEAR).getValue(point).channels[it]).isEmpty()
            }, "same-alpha domain witness must separate RGB at $point")
            assertTrue(expected.getValue(CompositionDomain.SRGB_ENCODED).getValue(point).channels[3]
                .intersect(expected.getValue(CompositionDomain.LINEAR).getValue(point).channels[3]).isNotEmpty(),
                "alpha alone does not separate domains at $point")
        }
        val expectedByTarget = formats.flatMap { pixelFormat -> domains.map { domain ->
            (domain to pixelFormat) to expected.getValue(domain).mapValues { (_, value) -> swizzle(value, pixelFormat) }
        } }.toMap()
        for (pixelFormat in formats) for (domain in domains) {
            val surface = Surface(WIDTH, HEIGHT, pixelFormat, RenderConfig(compositionDomain = domain))
            recordMixedStroke(surface)
            val first = surface.render()
            assertNative(first, "alternate domain=$domain format=$pixelFormat")
            points.forEach { point -> assertPixel(first, point, expectedByTarget.getValue(domain to pixelFormat).getValue(point)) }
            val second = surface.render()
            assertNative(second, "alternate repeat domain=$domain format=$pixelFormat")
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun encodedRootPictureAndSnapshotPreserveRepresentation() {
        assertMixedMasks()
        val path = verticalPath()
        val bounds = RectF32.ofLTRB(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
        val recorder = PictureRecorder()
        recorder.beginRecording(bounds).drawPath(path, mixedStrokePaint())
        val memory = recorder.finishRecordingAsPicture()
        val archived = requireNotNull(Picture.fromByteArray(memory.toByteArray()))
        val points = edgePoints + inside + outside
        val direct = Surface(WIDTH, HEIGHT, PixelFormat.RGBA8,
            RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        recordMixedStroke(direct)
        val allPoints = (0 until HEIGHT).flatMap { y -> (0 until WIDTH).map { x -> Pixel(x, y) } }
        val directExpected = allPoints.associateWith { point ->
            W7MsaaCompositionCpuOracle.pixel(
                W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), CompositionDomain.SRGB_ENCODED, null,
                listOf(strokeMask(point) to mixed),
            )
        }
        val producer = direct.render()
        assertNative(producer, "validated snapshot producer")
        allPoints.forEach { point -> assertPixel(producer, point, directExpected.getValue(point)) }
        val producerRepeat = direct.render()
        assertNative(producerRepeat, "validated snapshot producer repeat")
        assertContentEquals(producer.pixels, producerRepeat.pixels)

        val subset = RectF32.ofLTRB(1f, 4f, 7f, 6f)
        val snapshots = listOf(direct.makeImageSnapshot(), requireNotNull(direct.makeImageSnapshot(subset)))
        val expectedByteSets = listOf(producer.pixels, slice(producer.pixels, WIDTH, 1, 4, 6, 2))
        snapshots.forEachIndexed { index, image ->
            assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, image.premultiplication)
            val bytes = requireNotNull(image.pixels).toUByteArray()
            assertContentEquals(expectedByteSets[index], bytes, "snapshot bytes must match validated producer pixels")
            val expectedPixels = bytes.asList().chunked(4).map { pixelBytes ->
                val sourceBytes = pixelBytes.map { it.toByte() }.toByteArray()
                val source = W7CompositionCpuOracle.sourceSpacePremul(
                    sourceBytes, image.colorType, image.alphaType, CompositionDomain.SRGB_ENCODED,
                )
                W7CompositionCpuOracle.drawOnClear(source, CompositionDomain.SRGB_ENCODED)
            }
            val expectedByFormat = PixelFormat.entries.associateWith { format ->
                expectedPixels.map { swizzle(it, format) }
            }
            val destination = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
            val imageRecorder = PictureRecorder()
            imageRecorder.beginRecording(destination).drawImage(image, destination, SamplingOptions.NEAREST, Paint(antiAlias = false))
            val imageMemory = imageRecorder.finishRecordingAsPicture()
            val imageArchive = requireNotNull(Picture.fromByteArray(imageMemory.toByteArray()))
            for (format in PixelFormat.entries) for (picture in listOf(imageMemory, imageArchive)) {
                val replay = Surface(image.width, image.height, format,
                    RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
                replay.canvas { picture.playback(this) }
                val first = replay.render()
                assertNative(first, "snapshot=$index format=$format archived=${picture === imageArchive}")
                assertEveryPixel(first, expectedByFormat.getValue(format))
                val second = replay.render()
                assertNative(second, "snapshot=$index repeat format=$format")
                assertContentEquals(first.pixels, second.pixels)
            }
        }
        for (picture in listOf(memory, archived)) {
            val replay = Surface(WIDTH, HEIGHT, PixelFormat.RGBA8,
                RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            replay.canvas { picture.playback(this) }
            val result = replay.render()
            assertNative(result, "root Picture replay")
            points.forEach { point -> assertPixel(result, point, directExpected.getValue(point)) }
            val repeated = replay.render()
            assertNative(repeated, "root Picture replay repeat")
            assertContentEquals(result.pixels, repeated.pixels)
        }
    }

    @Test
    fun encodedRootIntegerTranslationAndHardClipKeepDeviceMasks() {
        val surface = Surface(16, 16, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        surface.canvas {
            clipRect(RectF32.ofLTRB(3f, 5f, 9f, 12f), antiAlias = false)
            translate(2f, 3f)
            drawPath(verticalPath(4f, 2f, 4f, 10f), blackStrokePaint(5f))
        }
        val points = listOf(Pixel(3, 8), Pixel(8, 8), Pixel(5, 8), Pixel(2, 8), Pixel(9, 8), Pixel(5, 4), Pixel(5, 12))
        val masks = listOf(0b1010, 0b0101, 0b1111, 0, 0, 0, 0)
        val expected = points.zip(masks).associate { (point, bits) -> point to encodedBlack(bits) }
        val first = surface.render()
        assertNative(first, "translated hard device clip")
        points.forEach { point -> assertPixel(first, point, expected.getValue(point)) }
        val second = surface.render()
        assertNative(second, "translated hard device clip repeat")
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun encodedRootTinyReciprocalCtmKeepsSourceStroke() {
        val s = .00005f
        val surface = Surface(180, 180, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        surface.canvas {
            translate(50f, 50f)
            scale(1f / s, 1f / s)
            drawPath(verticalPath(20f * s, 20f * s, 20f * s, 100f * s), blackStrokePaint(5f * s))
        }
        val points = listOf(Pixel(67, 100), Pixel(72, 100), Pixel(70, 100), Pixel(66, 100), Pixel(73, 100))
        val expected = listOf(0b1010, 0b0101, 0b1111, 0, 0).map(::encodedBlack)
        val first = surface.render()
        assertNative(first, "tiny reciprocal source stroke")
        points.zip(expected).forEach { (point, pixel) -> assertPixel(first, point, pixel) }
        val second = surface.render()
        assertNative(second, "tiny reciprocal source stroke repeat")
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun encodedRootAnisotropicCtmUsesSourceNormals() {
        data class Case(
            val name: String,
            val path: Path,
            val points: List<Pixel>,
            val expected: List<W7CompositionCpuOracle.CompositionEnvelope>,
        )
        val cases = listOf(
            Case("horizontal", Path().apply { moveTo(2f, 4.25f); lineTo(6f, 4.25f) },
                listOf(Pixel(20, 14), Pixel(20, 18), Pixel(20, 16), Pixel(20, 13), Pixel(20, 19)),
                listOf(0b1100, 0b0011, 0b1111, 0, 0).map(::encodedBlack)),
            Case("vertical", Path().apply { moveTo(4.125f, 2f); lineTo(4.125f, 8f) },
                listOf(Pixel(20, 20), Pixel(28, 20), Pixel(24, 20), Pixel(19, 20), Pixel(29, 20)),
                listOf(0b1010, 0b0101, 0b1111, 0, 0).map(::encodedBlack)),
        )
        for (case in cases) {
            val surface = Surface(40, 32, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas { translate(8f, 8f); scale(4f, 2f); drawPath(case.path, blackStrokePaint(2f)) }
            val first = surface.render()
            assertNative(first, "anisotropic ${case.name}")
            case.points.zip(case.expected).forEach { (point, expected) -> assertPixel(first, point, expected) }
            val second = surface.render()
            assertNative(second, "anisotropic ${case.name} repeat")
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun encodedRootExclusionMatrixIsTransactional() {
        val full = RectF32.ofLTRB(0f, 0f, 12f, 12f)
        val line = verticalPath()
        val image = Image.fromPixels(1, 1, byteArrayOf(20, 40, 60, -1), alphaType = AlphaType.PREMUL)
        val shader = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(12f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        )
        val exclusions: List<Triple<String, String, Canvas.() -> Unit>> = listOf(
            Triple("layer", "layer") { saveLayer(); restore() },
            Triple("aa-clip", "geometry") { clipRect(full, antiAlias = true) },
            Triple("inverse-path", "geometry") { drawPath(
                Path().apply { fillType = FillType.INVERSE_WINDING; addRect(full) }, Paint(black, antiAlias = true),
            ) },
            Triple("perspective", "geometry") { concat(Matrix3x3F32(persp0 = .01f)) },
            Triple("skew", "geometry") { concat(Matrix3x3F32(kx = .25f)) },
            Triple("zero-width", "geometry") { drawPath(line, blackStrokePaint(0f)) },
            Triple("round-cap", "geometry") { drawPath(line, blackStrokePaint(5f, cap = StrokeCap.ROUND)) },
            Triple("round-join", "geometry") { drawPath(Path().apply {
                moveTo(2f, 2f); lineTo(6f, 2f); lineTo(6f, 6f)
            }, blackStrokePaint(2f, join = StrokeJoin.ROUND)) },
            Triple("hard-draw-rect", "geometry") { drawRect(full, Paint(black, antiAlias = false)) },
            Triple("nearest-image", "geometry") { drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST, Paint(antiAlias = false)) },
            Triple("rect-gradient", "geometry") { drawRect(full, Paint(shader = shader, antiAlias = false)) },
            Triple("path-gradient", "source") { drawPath(line, Paint(shader = shader, antiAlias = true)) },
            Triple("path-color-filter", "source") { drawPath(line, Paint(colorFilter = ColorFilter.Luma, antiAlias = true)) },
            Triple("path-image-filter", "source") { drawPath(line, Paint(imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = true)) },
            Triple("path-src", "blend") { drawPath(line, Paint(black, blendMode = BlendMode.SRC,
                style = PaintStyle.STROKE, strokeWidth = 5f, antiAlias = true)) },
            Triple("mixed-old-family", "geometry") {
                drawPath(line, blackStrokePaint(5f)); drawImage(image,
                    RectF32.ofLTRB(0f, 0f, 1f, 1f), SamplingOptions.NEAREST, Paint(antiAlias = false))
            },
        )
        val recoveryExpected = (edgePoints + inside + outside).associateWith { point ->
            W7MsaaCompositionCpuOracle.pixel(
                W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), CompositionDomain.SRGB_ENCODED, null,
                listOf(strokeMask(point) to black),
            )
        }
        for ((name, code, append) in exclusions) {
            val surface = Surface(12, 12, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas { drawPath(line, blackStrokePaint(5f)); save(); append(); restore() }
            val sentinel = UByteArray(12 * 12 * 4) { 0x5au }
            val failure = assertFailsWith<IllegalStateException> { surface.readPixels(full, sentinel) }
            assertEquals("unsupported.surface.composition.$code", failure.message.orEmpty().substringBefore(':'), name)
            assertContentEquals(UByteArray(12 * 12 * 4) { 0x5au }, sentinel, name)
            surface.discardRecordedOperations()
            surface.canvas { resetMatrix(); drawPath(line, blackStrokePaint(5f)) }
            repeat(2) {
                val recovered = surface.render()
                assertNative(recovered, "$name recovery")
                recoveryExpected.forEach { (point, value) -> assertPixel(recovered, point, value) }
            }
        }
    }

    @Test
    fun rootIncompatibleTargetCannotBeRecoveredByDiscard() {
        val contradictory = listOf(
            CompositionDomain.LINEAR to GPUColorFormat.RGBA8_UNORM,
            CompositionDomain.LINEAR to GPUColorFormat.BGRA8_UNORM,
            CompositionDomain.LINEAR to GPUColorFormat.RGBA16_FLOAT,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.RGBA8_UNORM_SRGB,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.BGRA8_UNORM,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.RGBA16_FLOAT,
        )
        val validExpectedByDomain = contradictory.map { it.first }.distinct().associateWith { domain ->
            (edgePoints + inside + outside).associateWith { point -> mixedExpected(domain, point) }
        }
        for ((domain, format) in contradictory) {
            val surface = Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain, gpuColorFormat = format))
            recordMixedStroke(surface)
            val sentinel = UByteArray(WIDTH * HEIGHT * 4) { 0x5au }
            repeat(2) { attempt ->
                val failure = assertFailsWith<IllegalStateException> {
                    surface.readPixels(RectF32.ofLTRB(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), sentinel)
                }
                assertEquals("unsupported.surface.composition.target-format", failure.message.orEmpty().substringBefore(':'),
                    "domain=$domain format=$format attempt=$attempt")
                assertContentEquals(UByteArray(WIDTH * HEIGHT * 4) { 0x5au }, sentinel)
                if (attempt == 0) surface.discardRecordedOperations()
            }
            val valid = Surface(WIDTH, HEIGHT, config = RenderConfig(compositionDomain = domain))
            recordMixedStroke(valid)
            val expected = validExpectedByDomain.getValue(domain)
            val first = valid.render()
            assertNative(first, "new compatible target domain=$domain")
            expected.forEach { (point, value) -> assertPixel(first, point, value) }
            val second = valid.render()
            assertNative(second, "new compatible target repeat domain=$domain")
            expected.forEach { (point, value) -> assertPixel(second, point, value) }
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun encodedRootBudgetIsExactAndOneByteLessIsTransactional() {
        val budget = maxOf(
            256L + 2_048L + 24_576L,
            256L + 24_576L + 1_024L + 1_024L,
        )
        assertEquals(26_880L, budget)
        val points = listOf(Pixel(1, 4), Pixel(6, 4), Pixel(3, 4), Pixel(0, 4), Pixel(7, 4))
        val masks = listOf(0b1010, 0b0101, 0b1111, 0, 0)
        val expected = points.zip(masks).map { (_, bits) -> encodedBlack(bits) }
        val clearExpected = W7CompositionCpuOracle.clear()
        val clearExpectedRgba = swizzle(clearExpected, PixelFormat.RGBA8)
        fun fixture(limit: Long) = Surface(8, 8, config = RenderConfig(
            compositionDomain = CompositionDomain.SRGB_ENCODED, frameLocalBudgetBytes = limit,
        )).also { surface ->
            surface.canvas { drawPath(verticalPath(4f, 2f, 4f, 6f), blackStrokePaint(5f)) }
        }
        val admitted = fixture(budget)
        repeat(2) {
            val result = admitted.render()
            assertNative(result, "budget=$budget")
            points.zip(expected).forEach { (point, pixel) -> assertPixel(result, point, pixel) }
        }

        val refused = fixture(budget - 1)
        val sentinel = UByteArray(8 * 8 * 4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> {
            refused.readPixels(RectF32.ofLTRB(0f, 0f, 8f, 8f), sentinel)
        }
        assertEquals("w4d.general.budget.frame-local-exceeded", failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(UByteArray(8 * 8 * 4) { 0x5au }, sentinel)
        refused.discardRecordedOperations()
        repeat(2) {
            val result = refused.render()
            assertTrue(result.isClean, result.diagnostics.summary())
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
            assertEquals(0, result.stats.opsRefused)
            assertEveryPixel(result, List(64) { clearExpectedRgba })
        }
    }

    private fun recordMixedStroke(surface: Surface) {
        surface.canvas {
            drawPath(verticalPath(), mixedStrokePaint())
        }
    }

    private fun verticalPath(x1: Float = 4f, y1: Float = 2f, x2: Float = 4f, y2: Float = 10f) =
        Path().apply { moveTo(x1, y1); lineTo(x2, y2) }

    private fun mixedStrokePaint() = Paint(
        mixed, style = PaintStyle.STROKE, strokeWidth = 5f, strokeCap = StrokeCap.BUTT,
        strokeJoin = StrokeJoin.MITER, strokeMiter = 4f, antiAlias = true,
    )

    private fun blackStrokePaint(
        width: Float,
        cap: StrokeCap = StrokeCap.BUTT,
        join: StrokeJoin = StrokeJoin.MITER,
    ) = Paint(black, style = PaintStyle.STROKE, strokeWidth = width,
        strokeCap = cap, strokeJoin = join, strokeMiter = 4f, antiAlias = true)

    private fun strokeMask(point: Pixel): W7MsaaCompositionCpuOracle.Mask =
        W7MsaaCompositionCpuOracle.strokeMask(vertical, W7MsaaCompositionCpuOracle.Pixel(point.x, point.y))

    private fun mixedExpected(
        domain: CompositionDomain,
        point: Pixel,
    ): W7CompositionCpuOracle.CompositionEnvelope = W7MsaaCompositionCpuOracle.pixel(
        W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), domain, null,
        listOf(strokeMask(point) to mixed),
    )

    private fun assertMixedMasks() {
        assertEquals(0b1010, strokeMask(edgePoints[0]).bits)
        assertEquals(0b0101, strokeMask(edgePoints[1]).bits)
        assertEquals(0b1111, strokeMask(inside).bits)
        outside.forEach { assertEquals(0, strokeMask(it).bits) }
    }

    private fun encodedBlack(bits: Int): W7CompositionCpuOracle.CompositionEnvelope =
        W7MsaaCompositionCpuOracle.pixel(
            W7MsaaCompositionCpuOracle.Pixel(0, 0), CompositionDomain.SRGB_ENCODED, null,
            listOf(W7MsaaCompositionCpuOracle.Mask(bits) to black),
        )

    private fun swizzle(
        value: W7CompositionCpuOracle.CompositionEnvelope,
        format: PixelFormat,
    ) = W7CompositionCpuOracle.swizzle(value, format)

    private fun assertNative(result: RenderResult, label: String) {
        val evidence = "$label diagnostics=${result.diagnostics.summary()} scopes=${result.nativeEvidenceScopeKinds} " +
            "dispatched=${result.stats.opsDispatched} refused=${result.stats.opsRefused}"
        assertTrue(result.isClean, evidence)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), evidence)
        assertTrue(result.stats.opsDispatched > 0, evidence)
        assertEquals(0, result.stats.opsRefused, evidence)
    }

    private fun assertPixel(
        result: RenderResult,
        point: Pixel,
        expected: W7CompositionCpuOracle.CompositionEnvelope,
    ) {
        val offset = (point.y * result.width + point.x) * 4
        W7CompositionCpuOracle.assertAdmits(expected, result.pixels.copyOfRange(offset, offset + 4))
    }

    private fun assertEveryPixel(
        result: RenderResult,
        expected: List<W7CompositionCpuOracle.CompositionEnvelope>,
    ) {
        assertEquals(expected.size * 4, result.pixels.size)
        expected.forEachIndexed { index, value ->
            W7CompositionCpuOracle.assertAdmits(value, result.pixels.copyOfRange(index * 4, index * 4 + 4))
        }
    }

    private fun slice(pixels: UByteArray, width: Int, x: Int, y: Int, w: Int, h: Int): UByteArray {
        val out = UByteArray(w * h * 4)
        for (row in 0 until h) pixels.copyInto(out, row * w * 4,
            ((y + row) * width + x) * 4, ((y + row) * width + x + w) * 4)
        return out
    }
}
