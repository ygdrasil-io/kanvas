@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.GradientAlphaMode
import org.graphiks.kanvas.paint.GradientStop
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
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Native public witnesses for the hard, integer Rect hairline contract. */
class W7RectHairlineSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `integerRectHairlineCoversEveryExpectedPixel`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        // This literal oracle is defined before Surface creation and does not reuse renderer geometry.
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRRRBB", "BBRBBRBB",
            "BBRBBRBB", "BBRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )

        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }

        val first = surface.render()
        assertPixelTable(expected, blue, red, first)
        val second = surface.render()
        assertPixelTable(expected, blue, red, second)
        assertContentEquals(first.pixels, second.pixels)
    }

    @Test
    fun `encodedHairlineUsesTheSameGeometryAndDifferentBlendDomain`() {
        // Removing encoded hairline admission, using LINEAR blending for the encoded target,
        // or emitting a corner twice respectively makes this independently closed witness fail.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val halfRed = ColorARGB.of(128, 255, 0, 0)
        val domains = listOf(CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR)
        val background = domains.associateWith { domain ->
            W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(blue, domain), domain)
        }
        val border = domains.associateWith { domain ->
            W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(halfRed, domain),
                W7CompositionCpuOracle.storedSample(background.getValue(domain), domain),
            ), domain)
        }
        val doubledCorner = domains.associateWith { domain ->
            W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(halfRed, domain),
                W7CompositionCpuOracle.storedSample(border.getValue(domain), domain),
            ), domain)
        }
        assertDisjointHairlineExpectations(
            border.getValue(CompositionDomain.LINEAR),
            border.getValue(CompositionDomain.SRGB_ENCODED),
            "wrong-domain border",
        )
        for (domain in domains) {
            assertDisjointHairlineExpectations(
                border.getValue(domain), doubledCorner.getValue(domain), "double-corner domain=$domain",
            )
            for (format in PixelFormat.entries) {
                val surface = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
                surface.canvas {
                    drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
                    drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                        Paint(halfRed, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
                }
                val expectedBorder = W7CompositionCpuOracle.swizzle(border.getValue(domain), format)
                val expectedBackground = W7CompositionCpuOracle.swizzle(background.getValue(domain), format)
                val first = surface.render()
                for (y in 0 until 8) for (x in 0 until 8) {
                    val isBorder = x in 2..5 && y in 2..5 && (x == 2 || x == 5 || y == 2 || y == 5)
                    W7CompositionCpuOracle.assertAdmits(
                        if (isBorder) expectedBorder else expectedBackground,
                        first.pixelAt(x, y),
                    )
                }
                assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
                val second = surface.render()
                assertContentEquals(first.pixels, second.pixels, "domain=$domain format=$format")
            }
        }
    }

    @Test
    fun `encodedHairlineMixesWithAdmittedSourcesInBothOrders`() {
        val domain = CompositionDomain.SRGB_ENCODED
        val hairline = ColorARGB.of(128, 255, 0, 0)
        val solid = ColorARGB.of(128, 31, 191, 73)
        val gradientLeft = ColorARGB.of(128, 0, 255, 0)
        val gradientRight = ColorARGB.of(64, 0, 0, 255)
        val drawColor = ColorARGB.of(160, 23, 91, 173)
        val image = Image.fromPixels(1, 1, byteArrayOf(37, 101, -37, -1), alphaType = AlphaType.PREMUL)
        val sourceRect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val hairlineRect = RectF32.ofLTRB(3f, 3f, 6f, 6f)
        data class Source(
            val name: String,
            val colorAtSource: () -> Array<WgslFloatEnvelopeV1Oracle.Interval>,
            val record: Canvas.() -> Unit,
            val coversHairline: Boolean = false,
        )
        val sources = listOf(
            Source("solid", { W7CompositionCpuOracle.solid(solid, domain) }, {
                drawRect(sourceRect, Paint(solid, antiAlias = false))
            }),
            Source("gradient-straight", { W7CompositionCpuOracle.gradient(
                gradientLeft, gradientRight, .5f, GradientAlphaMode.STRAIGHT, domain,
            ) }, {
                drawRect(sourceRect, Paint(shader = Shader.LinearGradient(
                    Point2F32(0f, 0f), Point2F32(1f, 0f),
                    listOf(GradientStop(0f, gradientLeft), GradientStop(1f, gradientRight)),
                    alphaMode = GradientAlphaMode.STRAIGHT,
                ), antiAlias = false))
            }),
            Source("gradient-premultiplied", { W7CompositionCpuOracle.gradient(
                gradientLeft, gradientRight, .5f, GradientAlphaMode.PREMULTIPLIED, domain,
            ) }, {
                drawRect(sourceRect, Paint(shader = Shader.LinearGradient(
                    Point2F32(0f, 0f), Point2F32(1f, 0f),
                    listOf(GradientStop(0f, gradientLeft), GradientStop(1f, gradientRight)),
                    alphaMode = GradientAlphaMode.PREMULTIPLIED,
                ), antiAlias = false))
            }),
            Source("image", { W7CompositionCpuOracle.sourceSpacePremul(
                requireNotNull(image.pixels), image.colorType, image.alphaType, domain,
            ) }, {
                drawImage(image, sourceRect, SamplingOptions.NEAREST, Paint(antiAlias = false))
            }),
            Source("draw-color", { W7CompositionCpuOracle.solid(drawColor, domain) }, {
                save()
                clipRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), antiAlias = false)
                drawColor(drawColor)
                restore()
            }, coversHairline = true),
        )
        val hairlineSource = W7CompositionCpuOracle.solid(hairline, domain)
        val hairlineOnClear = W7CompositionCpuOracle.drawOnClear(hairlineSource, domain)
        for (format in PixelFormat.entries) for (source in sources) for (sourceFirst in listOf(true, false)) {
            val sourceOnClear = W7CompositionCpuOracle.drawOnClear(source.colorAtSource(), domain)
            val hairlineExpected = if (source.coversHairline) {
                val first = if (sourceFirst) sourceOnClear else hairlineOnClear
                val second = if (sourceFirst) hairlineSource else source.colorAtSource()
                W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                    second, W7CompositionCpuOracle.storedSample(first, domain),
                ), domain)
            } else hairlineOnClear
            if (source.coversHairline) {
                val oppositeFirst = if (sourceFirst) hairlineOnClear else sourceOnClear
                val oppositeSecond = if (sourceFirst) source.colorAtSource() else hairlineSource
                val oppositeOrder = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                    oppositeSecond, W7CompositionCpuOracle.storedSample(oppositeFirst, domain),
                ), domain)
                assertDisjointHairlineExpectations(
                    hairlineExpected,
                    oppositeOrder,
                    "source order source=${source.name} format=$format first=$sourceFirst",
                )
            }
            val surface = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
            surface.canvas {
                if (sourceFirst) source.record(this)
                drawRect(hairlineRect, Paint(hairline, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
                if (!sourceFirst) source.record(this)
            }
            val first = surface.render()
            W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(sourceOnClear, format), first.pixelAt(0, 0),
            )
            W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(hairlineExpected, format), first.pixelAt(3, 3),
            )
            W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(hairlineOnClear, format), first.pixelAt(6, 6),
            )
            if (!source.coversHairline) W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.clear(), format), first.pixelAt(7, 7),
            )
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), source.name)
            val second = surface.render()
            assertContentEquals(first.pixels, second.pixels, "source=${source.name} first=$sourceFirst format=$format")
        }
    }

    @Test
    fun `plainLayerHairlinePreservesLocalizationAndRestoreOpacity`() {
        val domain = CompositionDomain.SRGB_ENCODED
        val background = ColorARGB.of(255, 17, 61, 211)
        val hairline = ColorARGB.of(128, 255, 0, 0)
        val deviceHairline = RectF32.ofLTRB(3f, 3f, 6f, 6f)
        val root = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(background, domain), domain)
        val child = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(hairline, domain), domain)
        for (restoreAlpha in listOf(255, 128)) for (format in PixelFormat.entries) {
            // The child store and restore store are fixed before Surface/GPU creation.
            val restored = W7CompositionCpuOracle.opacity(W7CompositionCpuOracle.storedSample(child, domain), restoreAlpha)
            val border = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                restored, W7CompositionCpuOracle.storedSample(root, domain),
            ), domain)
            if (restoreAlpha == 128) {
                val withoutOpacity = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                    W7CompositionCpuOracle.storedSample(child, domain), W7CompositionCpuOracle.storedSample(root, domain),
                ), domain)
                assertDisjointHairlineExpectations(border, withoutOpacity, "layer restore opacity")
            }
            val surface = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(background, antiAlias = false))
                saveLayer(RectF32.ofLTRB(2f, 2f, 7f, 7f), Paint(ColorARGB.of(restoreAlpha, 0, 0, 0), antiAlias = false))
                translate(1f, 0f)
                clipRect(RectF32.ofLTRB(3f, 3f, 6f, 7f), antiAlias = false)
                drawRect(RectF32.ofLTRB(2f, 3f, 5f, 6f),
                    Paint(hairline, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
                restore()
            }
            val expectedRoot = W7CompositionCpuOracle.swizzle(root, format)
            val expectedBorder = W7CompositionCpuOracle.swizzle(border, format)
            val first = surface.render()
            for (y in 0 until 8) for (x in 0 until 8) {
                val isBorder = x in 4..6 && y in 3..6 && (x == 6 || y == 3 || y == 6)
                W7CompositionCpuOracle.assertAdmits(if (isBorder) expectedBorder else expectedRoot, first.pixelAt(x, y))
            }
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
            val second = surface.render()
            assertContentEquals(first.pixels, second.pixels, "restore=$restoreAlpha format=$format")
        }
    }

    @Test
    fun `hairlinePictureAndSnapshotsPreserveCapturedRepresentation`() {
        val capturedRect = RectF32.ofLTRB(2f, 2f, 5f, 5f)
        val capturedPaint = Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f)
        val snapshotRect = RectF32.ofLTRB(2f, 2f, 5f, 5f)
        val subset = RectF32.ofLTRB(2f, 2f, 6f, 6f)
        val pictureBackground = ColorARGB.of(255, 17, 61, 211)
        val pictureDomains = listOf(
            CompositionDomain.SRGB_ENCODED,
            CompositionDomain.LINEAR,
            CompositionDomain.SRGB_ENCODED,
        )
        // These independent tables are frozen before recording and before any GPU work.
        // `capturedRect` is intentionally mutated only after the Picture has captured it.
        val pictureBackgroundExpected = pictureDomains.distinct().associateWith { domain ->
            W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(pictureBackground, domain), domain)
        }
        val pictureBorderExpected = pictureDomains.distinct().associateWith { domain ->
            W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(capturedPaint.color, domain),
                W7CompositionCpuOracle.storedSample(pictureBackgroundExpected.getValue(domain), domain),
            ), domain)
        }
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).drawRect(capturedRect, capturedPaint)
        val hairlinePicture = recorder.finishRecordingAsPicture()
        capturedRect.setLTRB(0f, 0f, 1f, 1f)
        val archivedHairlinePicture = assertNotNull(Picture.fromByteArray(hairlinePicture.toByteArray()))
        for (format in PixelFormat.entries) {
            for (domain in pictureDomains) {
                for (picture in listOf(hairlinePicture, archivedHairlinePicture)) {
                    val replay = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
                    replay.canvas {
                        drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(pictureBackground, antiAlias = false))
                        picture.playback(this)
                    }
                    val firstPictureRender = replay.render()
                    for (y in 0 until 8) for (x in 0 until 8) {
                        val border = x in 2..5 && y in 2..5 && (x == 2 || x == 5 || y == 2 || y == 5)
                        W7CompositionCpuOracle.assertAdmits(
                            W7CompositionCpuOracle.swizzle(
                                if (border) pictureBorderExpected.getValue(domain) else pictureBackgroundExpected.getValue(domain),
                                format,
                            ),
                            firstPictureRender.pixelAt(x, y),
                        )
                    }
                    assertContentEquals(firstPictureRender.pixels, replay.render().pixels)
                }
            }
            val source = Surface(8, 8, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            source.canvas { drawRect(snapshotRect, capturedPaint) }
            val first = source.render()
            val second = source.render()
            assertContentEquals(first.pixels, second.pixels)
            val fullSnapshot = source.makeImageSnapshot()
            val subsetSnapshot = requireNotNull(source.makeImageSnapshot(subset))
            val subsetBytes = buildList<UByte> {
                for (y in 2 until 6) for (x in 2 until 6) addAll(first.pixelAt(x, y).asList())
            }.toUByteArray()
            for ((snapshot, expected) in listOf(fullSnapshot to first.pixels, subsetSnapshot to subsetBytes)) {
                assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
                assertContentEquals(expected, requireNotNull(snapshot.pixels).toUByteArray())
                val destination = RectF32.ofLTRB(0f, 0f, snapshot.width.toFloat(), snapshot.height.toFloat())
                val recorder = PictureRecorder()
                recorder.beginRecording(destination).drawImage(snapshot, destination, SamplingOptions.NEAREST, Paint(antiAlias = false))
                val memory = recorder.finishRecordingAsPicture()
                val archive = assertNotNull(Picture.fromByteArray(memory.toByteArray()))
                for (domain in listOf(CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED))
                    for (picture in listOf(memory, archive)) {
                        val replayExpected = (0 until snapshot.width * snapshot.height).map { pixelIndex ->
                            val offset = pixelIndex * 4
                            val storedSource = requireNotNull(snapshot.pixels).copyOfRange(offset, offset + 4)
                            W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.drawOnClear(
                                W7CompositionCpuOracle.sourceSpacePremul(
                                    storedSource,
                                    snapshot.colorType,
                                    snapshot.alphaType,
                                    domain,
                                ),
                                domain,
                            ), format)
                        }
                        val replay = Surface(snapshot.width, snapshot.height, format, RenderConfig(compositionDomain = domain))
                        replay.canvas { picture.playback(this) }
                        val replayFirst = replay.render()
                        for (y in 0 until snapshot.height) for (x in 0 until snapshot.width) {
                            W7CompositionCpuOracle.assertAdmits(
                                replayExpected[y * snapshot.width + x],
                                replayFirst.pixelAt(x, y),
                            )
                        }
                        val replaySecond = replay.render()
                        assertContentEquals(replayFirst.pixels, replaySecond.pixels)
                    }
            }
        }
    }

    @Test
    fun `encodedHairlineExclusionsAreTransactional`() {
        val full = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val hairline = RectF32.ofLTRB(2f, 2f, 5f, 5f)
        val acceptedPaint = Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f)
        val excluded: List<Triple<String, String, Canvas.() -> Unit>> = listOf(
            Triple("aa", "geometry") { drawRect(hairline, acceptedPaint.copy(antiAlias = true)) },
            Triple("finite-width", "geometry") { drawRect(hairline, acceptedPaint.copy(strokeWidth = 1f)) },
            Triple("path", "geometry") { drawPath(Path().apply { addRect(hairline) }, acceptedPaint) },
            Triple("translation", "geometry") { save(); translate(.5f, 0f); drawRect(hairline, acceptedPaint); restore() },
            Triple("shader", "source") { drawRect(hairline, acceptedPaint.copy(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(1f, 0f), listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            ))) },
            Triple("image-filter", "source") { drawRect(hairline, acceptedPaint.copy(imageFilter = ImageFilter.Offset(1f, 0f))) },
            Triple("color-filter", "source") { drawRect(hairline, acceptedPaint.copy(colorFilter = ColorFilter.Luma)) },
            Triple("cap", "geometry") { drawRect(hairline, acceptedPaint.copy(strokeCap = StrokeCap.ROUND)) },
            Triple("join", "geometry") { drawRect(hairline, acceptedPaint.copy(strokeJoin = StrokeJoin.BEVEL)) },
            Triple("miter", "geometry") { drawRect(hairline, acceptedPaint.copy(strokeMiter = 1f)) },
            Triple("src", "blend") { drawRect(hairline, acceptedPaint.copy(blendMode = BlendMode.SRC)) },
            Triple("nested-layer", "layer") { saveLayer(); saveLayer(); restore(); restore() },
        )
        for ((name, suffix, appendExcluded) in excluded) {
            val surface = Surface(8, 8, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas { drawRect(hairline, acceptedPaint); appendExcluded() }
            val sentinel = UByteArray(8 * 8 * 4) { 0x5au }
            val failure = assertFailsWith<IllegalStateException> { surface.readPixels(full, sentinel) }
            assertTrue(failure.message.orEmpty().startsWith("unsupported.surface.composition.$suffix:"), "$name: ${failure.message}")
            assertContentEquals(UByteArray(8 * 8 * 4) { 0x5au }, sentinel)
            surface.discardRecordedOperations()
            surface.canvas { drawRect(hairline, acceptedPaint) }
            val recovered = surface.render()
            assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), name)
            assertHairlineOnClear(recovered, PixelFormat.RGBA8, acceptedPaint.color)
            assertContentEquals(recovered.pixels, surface.render().pixels, name)
        }
        val contradictory = Surface(8, 8, config = RenderConfig(
            compositionDomain = CompositionDomain.SRGB_ENCODED,
            gpuColorFormat = GPUColorFormat.RGBA8_UNORM_SRGB,
        ))
        contradictory.canvas { drawRect(hairline, acceptedPaint) }
        val sentinel = UByteArray(8 * 8 * 4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> { contradictory.readPixels(full, sentinel) }
        assertTrue(failure.message.orEmpty().startsWith("unsupported.surface.composition.target-format:"), failure.message.orEmpty())
        assertContentEquals(UByteArray(8 * 8 * 4) { 0x5au }, sentinel)
        val valid = Surface(8, 8, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        valid.canvas { drawRect(hairline, acceptedPaint) }
        val recovered = valid.render()
        assertHairlineOnClear(recovered, PixelFormat.RGBA8, acceptedPaint.color)
        assertContentEquals(recovered.pixels, valid.render().pixels)
    }

    @Test
    fun `clippedEncodedHairlineRendersAndSnapshotsTransparent`() {
        val hairline = RectF32.ofLTRB(2f, 2f, 5f, 5f)
        val paint = Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f)
        val clear = W7CompositionCpuOracle.clear()
        for (format in PixelFormat.entries) {
            val surface = Surface(8, 8, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas {
                save()
                clipRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), antiAlias = false)
                drawRect(hairline, paint)
                restore()
            }
            val first = surface.render()
            val expected = W7CompositionCpuOracle.swizzle(clear, format)
            for (y in 0 until 8) for (x in 0 until 8) W7CompositionCpuOracle.assertAdmits(expected, first.pixelAt(x, y))
            val second = surface.render()
            assertContentEquals(first.pixels, second.pixels)
            for (snapshot in listOf(surface.makeImageSnapshot(), requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(0f, 0f, 1f, 1f))))) {
                assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
                assertContentEquals(UByteArray(snapshot.width * snapshot.height * 4), requireNotNull(snapshot.pixels).toUByteArray())
            }
            surface.discardRecordedOperations()
            surface.canvas { drawRect(hairline, paint) }
            val recovered = surface.render()
            val recoveredBorder = W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.solid(paint.color, CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            ), format)
            for (y in 0 until 8) for (x in 0 until 8) {
                val border = x in 2..5 && y in 2..5 && (x == 2 || x == 5 || y == 2 || y == 5)
                W7CompositionCpuOracle.assertAdmits(if (border) recoveredBorder else expected, recovered.pixelAt(x, y))
            }
            assertContentEquals(recovered.pixels, surface.render().pixels)
        }
    }

    @Test
    fun `fullyClippedEncodedHairlinePreservesMixedRootAndLayerDestinations`() {
        val domain = CompositionDomain.SRGB_ENCODED
        val hairline = RectF32.ofLTRB(3f, 3f, 6f, 6f)
        val excludedClip = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        val source = ColorARGB.of(128, 31, 191, 73)
        val background = ColorARGB.of(255, 17, 61, 211)
        val hairlinePaint = Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false,
            style = PaintStyle.STROKE, strokeWidth = 0f)
        val sourceOnClear = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(source, domain), domain)
        val backgroundOnClear = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(background, domain), domain)
        for (format in PixelFormat.entries) {
            val mixedRoot = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
            mixedRoot.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(source, antiAlias = false))
                save()
                clipRect(excludedClip, antiAlias = false)
                drawRect(hairline, hairlinePaint)
                restore()
            }
            val rootFirst = mixedRoot.render()
            val clear = W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.clear(), format)
            val expectedSource = W7CompositionCpuOracle.swizzle(sourceOnClear, format)
            for (y in 0 until 8) for (x in 0 until 8) W7CompositionCpuOracle.assertAdmits(
                if (x == 0 && y == 0) expectedSource else clear, rootFirst.pixelAt(x, y),
            )
            assertContentEquals(rootFirst.pixels, mixedRoot.render().pixels, "mixed-root format=$format")

            val layer = Surface(8, 8, format, RenderConfig(compositionDomain = domain))
            layer.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(background, antiAlias = false))
                saveLayer()
                clipRect(excludedClip, antiAlias = false)
                drawRect(hairline, hairlinePaint)
                restore()
            }
            val layerFirst = layer.render()
            val expectedBackground = W7CompositionCpuOracle.swizzle(backgroundOnClear, format)
            for (y in 0 until 8) for (x in 0 until 8) W7CompositionCpuOracle.assertAdmits(
                expectedBackground, layerFirst.pixelAt(x, y),
            )
            assertContentEquals(layerFirst.pixels, layer.render().pixels, "layer format=$format")
        }
    }

    @Test
    fun `encodedHairlineBudgetBoundaryIsTransactional`() {
        // Static preflight is archived in task-2-report.md before this GPU witness:
        // B = 64 + 64 + 1024 + 16 + 32 + 16384 + 4096 + 4096 + 64 = 25840.
        val budgetB = 25_840L
        val domain = CompositionDomain.SRGB_ENCODED
        val rootColor = ColorARGB.of(255, 17, 61, 211)
        val hairlineColor = ColorARGB.of(128, 255, 0, 0)
        val full = RectF32.ofLTRB(0f, 0f, 4f, 4f)
        val hairline = RectF32.ofLTRB(0f, 0f, 3f, 3f)
        val root = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(rootColor, domain), domain)
        val child = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(hairlineColor, domain), domain)
        val border = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.storedSample(child, domain),
            W7CompositionCpuOracle.storedSample(root, domain),
        ), domain)
        fun record(surface: Surface) = surface.canvas {
            drawRect(full, Paint(rootColor, antiAlias = false))
            saveLayer(full)
            drawRect(hairline, Paint(hairlineColor, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
            restore()
        }
        fun assertExpected(result: RenderResult) {
            for (y in 0 until 4) for (x in 0 until 4) {
                val isBorder = x == 0 || x == 3 || y == 0 || y == 3
                W7CompositionCpuOracle.assertAdmits(
                    W7CompositionCpuOracle.swizzle(if (isBorder) border else root, PixelFormat.RGBA8),
                    result.pixelAt(x, y),
                )
            }
        }

        val accepted = Surface(4, 4, config = RenderConfig(
            compositionDomain = domain, frameLocalBudgetBytes = budgetB,
        ))
        record(accepted)
        val acceptedFirst = accepted.render()
        assertExpected(acceptedFirst)
        val acceptedSecond = accepted.render()
        assertExpected(acceptedSecond)
        assertContentEquals(acceptedFirst.pixels, acceptedSecond.pixels)

        val refused = Surface(4, 4, config = RenderConfig(
            compositionDomain = domain, frameLocalBudgetBytes = budgetB - 1L,
        ))
        record(refused)
        val sentinel = UByteArray(4 * 4 * 4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(full, sentinel) }
        assertTrue(failure.message.orEmpty().startsWith("w6a.layer.frame_budget_exceeded:"), failure.message.orEmpty())
        assertContentEquals(UByteArray(4 * 4 * 4) { 0x5au }, sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(full, Paint(rootColor, antiAlias = false)) }
        val recoveredFirst = refused.render()
        for (y in 0 until 4) for (x in 0 until 4) W7CompositionCpuOracle.assertAdmits(
            W7CompositionCpuOracle.swizzle(root, PixelFormat.RGBA8), recoveredFirst.pixelAt(x, y),
        )
        val recoveredSecond = refused.render()
        assertContentEquals(recoveredFirst.pixels, recoveredSecond.pixels)
    }

    @Test
    fun `translucentCornersAreCompositedOnce`() {
        val black = ColorARGB.Black
        val halfRed = ColorARGB.of(128, 255, 0, 0)
        val once = ColorARGB.of(255, 188, 0, 0)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRRRBB", "BBRBBRBB",
            "BBRBBRBB", "BBRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(black, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(halfRed, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, black, once)
    }

    @Test
    fun `clippingDoesNotMoveHairlineEdges`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "RRRRRRBB", "BBBBBRBB",
            "BBBBBRBB", "RRRRRRBB", "BBBBBBBB", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(-1f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, blue, red)

        val interiorClip = Surface(8, 8)
        interiorClip.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            clipRect(RectF32.ofLTRB(2f, 0f, 8f, 8f), antiAlias = false)
            drawRect(RectF32.ofLTRB(1f, 2f, 6f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(
            interiorClip,
            listOf(
                "BBBBBBBB", "BBBBBBBB", "BBRRRRRB", "BBBBBBRB",
                "BBBBBBRB", "BBRRRRRB", "BBBBBBBB", "BBBBBBBB",
            ),
            blue,
            red,
        )
    }

    @Test
    fun `integerScaledHairlineStaysOneDevicePixel`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val expected = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBRRRR",
            "BBBRBBR", "BBBRBBR", "BBBRRRR", "BBBBBBBB",
        )
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            scale(3f, 3f)
            drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(surface, expected, blue, red)
    }

    @Test
    fun `thinAndFullyClippedHairlinesPreserveDestination`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val red = ColorARGB.of(255, 239, 51, 73)
        val thin = listOf(
            "BBBBBBBB", "BBBBBBBB", "BBRRBBBB", "BBRRBBBB",
            "BBRRBBBB", "BBRRBBBB", "BBBBBBBB", "BBBBBBBB",
        )
        val thinSurface = Surface(8, 8)
        thinSurface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(thinSurface, thin, blue, red)

        val fullyClipped = Surface(8, 8)
        fullyClipped.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(blue, antiAlias = false))
            clipRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(2f, 2f, 5f, 5f),
                Paint(red, antiAlias = false, style = PaintStyle.STROKE, strokeWidth = 0f))
        }
        assertRepeatedPixelTable(fullyClipped, List(8) { "BBBBBBBB" }, blue, red)
    }
}

private fun RenderResult.pixelAt(x: Int, y: Int): UByteArray {
    val offset = (y * width + x) * 4
    return pixels.copyOfRange(offset, offset + 4)
}

private fun assertHairlineOnClear(actual: RenderResult, format: PixelFormat, color: ColorARGB) {
    val border = W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.drawOnClear(
        W7CompositionCpuOracle.solid(color, CompositionDomain.SRGB_ENCODED),
        CompositionDomain.SRGB_ENCODED,
    ), format)
    val clear = W7CompositionCpuOracle.swizzle(W7CompositionCpuOracle.clear(), format)
    for (y in 0 until 8) for (x in 0 until 8) {
        val isBorder = x in 2..5 && y in 2..5 && (x == 2 || x == 5 || y == 2 || y == 5)
        W7CompositionCpuOracle.assertAdmits(if (isBorder) border else clear, actual.pixelAt(x, y))
    }
}

private fun assertDisjointHairlineExpectations(
    left: W7CompositionCpuOracle.CompositionEnvelope,
    right: W7CompositionCpuOracle.CompositionEnvelope,
    label: String,
) {
    assertFalse(left.channels.zip(right.channels).all { (a, b) -> a.intersect(b).isNotEmpty() }, label)
}

private fun assertRepeatedPixelTable(surface: Surface, expected: List<String>, blue: ColorARGB, red: ColorARGB) {
    val first = surface.render()
    assertPixelTable(expected, blue, red, first)
    val second = surface.render()
    assertPixelTable(expected, blue, red, second)
    assertContentEquals(first.pixels, second.pixels)
}

private fun assertPixelTable(expected: List<String>, blue: ColorARGB, red: ColorARGB, actual: RenderResult) {
    expected.forEachIndexed { y, row -> row.forEachIndexed { x, marker ->
        val color = if (marker == 'R') red else blue
        val offset = (y * 8 + x) * 4
        assertContentEquals(
            ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte()),
            actual.pixels.copyOfRange(offset, offset + 4),
            "pixel ($x,$y)",
        )
    } }
    assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), actual.nativeEvidenceScopeKinds.toString())
}
