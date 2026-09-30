@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.GradientAlphaMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.types.Lattice
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class W7SurfaceCompositionPixelTest {
    companion object {
        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
        private val pixel = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }

    @Test
    fun encodedSolidSrcOverDiffersFromLinearAndIgnoresByteLayout() {
        // A route that ignores compositionDomain, changes AUTO's target, or swaps public byte
        // layout must fail this public result. The oracle is closed before either render begins.
        val expected = CompositionDomain.entries.associateWith { domain ->
            val white = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
            val result = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(ColorARGB.of(128, 0, 0, 0), domain),
                W7CompositionCpuOracle.storedSample(white, domain),
            ), domain)
            W7CompositionCpuOracle.trace(white, result)
        }
        val encoded = expected.getValue(CompositionDomain.SRGB_ENCODED)
        val linear = expected.getValue(CompositionDomain.LINEAR)
        assertTrue(encoded.channels.take(3).zip(linear.channels.take(3)).all { (left, right) -> left.intersect(right).isEmpty() })

        for (format in PixelFormat.entries) for (domain in listOf(CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR)) {
            val surface = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
            surface.canvas {
                drawRect(pixel, Paint(ColorARGB.White, antiAlias = false))
                drawRect(pixel, Paint(ColorARGB.of(128, 0, 0, 0), antiAlias = false))
            }
            val first = surface.render()
            W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(expected.getValue(domain), format),
                first.pixels,
            )
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "domain=$domain format=$format scopes=${first.nativeEvidenceScopeKinds}")
            val second = surface.render()
            assertTrue(second.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "domain=$domain format=$format scopes=${second.nativeEvidenceScopeKinds}")
            W7CompositionCpuOracle.assertAdmits(W7CompositionCpuOracle.swizzle(expected.getValue(domain), format), second.pixels)
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun gradientAlphaModeIsIndependentFromCompositionDomain() {
        val whiteToTransparent = ColorARGB.White to ColorARGB.Transparent
        val colored = ColorARGB.of(128, 255, 0, 0) to ColorARGB.of(64, 0, 0, 255)
        val expectations = listOf(whiteToTransparent, colored).associateWith { (left, right) ->
            GradientAlphaMode.entries.flatMap { mode -> CompositionDomain.entries.map { domain ->
                mode to domain to gradientOverWhite(left, right, mode, domain)
            } }.toMap()
        }
        val white = expectations.getValue(whiteToTransparent)
        val coloredExpected = expectations.getValue(colored)
        for (domain in CompositionDomain.entries) {
            assertDisjoint("white alpha-mode domain=$domain",
                white.getValue(GradientAlphaMode.STRAIGHT to domain),
                white.getValue(GradientAlphaMode.PREMULTIPLIED to domain))
            assertDisjoint("colored alpha-mode domain=$domain",
                coloredExpected.getValue(GradientAlphaMode.STRAIGHT to domain),
                coloredExpected.getValue(GradientAlphaMode.PREMULTIPLIED to domain))
        }
        for (mode in GradientAlphaMode.entries) {
            assertDisjoint("colored domain mode=$mode",
                coloredExpected.getValue(mode to CompositionDomain.LINEAR),
                coloredExpected.getValue(mode to CompositionDomain.SRGB_ENCODED))
            val linear = white.getValue(mode to CompositionDomain.LINEAR)
            val encoded = white.getValue(mode to CompositionDomain.SRGB_ENCODED)
            if (mode == GradientAlphaMode.PREMULTIPLIED)
                assertOverlaps("white premultiplied domain mode=$mode", linear, encoded)
            else assertDisjoint("white straight domain mode=$mode", linear, encoded)
        }

        for ((stops, expectedByModeDomain) in expectations) for (mode in GradientAlphaMode.entries)
            for (domain in CompositionDomain.entries) for (format in PixelFormat.entries) {
                val surface = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
                surface.canvas {
                    drawRect(pixel, Paint(ColorARGB.White, antiAlias = false))
                    drawRect(pixel, Paint(shader = Shader.LinearGradient(
                        Point2F32(0f, 0f), Point2F32(1f, 0f),
                        listOf(GradientStop(0f, stops.first), GradientStop(1f, stops.second)),
                        alphaMode = mode,
                    ), antiAlias = false))
                }
                val expected = expectedByModeDomain.getValue(mode to domain)
                val first = surface.render()
                assertColoredResult(first, expected, format, domain)
                val second = surface.render()
                assertColoredResult(second, expected, format, domain)
                assertContentEquals(first.pixels, second.pixels, "stops=$stops mode=$mode domain=$domain format=$format")
            }
    }

    @Test
    fun gradientDomainSurvivesLayerPictureAndRepeatedTargets() {
        val left = ColorARGB.of(128, 255, 0, 0)
        val right = ColorARGB.of(64, 0, 0, 255)
        val originalStops = mutableListOf(
            GradientStop(0f, left),
            GradientStop(1f, right),
        )
        fun shader(mode: GradientAlphaMode, stops: List<GradientStop> = listOf(GradientStop(0f, left), GradientStop(1f, right))) = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(1f, 0f), stops, alphaMode = mode,
        )
        fun paint(mode: GradientAlphaMode) = Paint(
            color = ColorARGB.of(128, 0, 0, 0), shader = shader(mode), antiAlias = false,
        )
        fun directExpected(mode: GradientAlphaMode, domain: CompositionDomain) =
            W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.opacity(
                W7CompositionCpuOracle.gradient(left, right, .5f, mode, domain), 128,
            ), domain)
        fun layerExpected(mode: GradientAlphaMode, domain: CompositionDomain): W7CompositionCpuOracle.CompositionEnvelope {
            val root = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
            val child = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.opacity(
                W7CompositionCpuOracle.gradient(left, right, .5f, mode, domain), 128,
            ), domain)
            val restored = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.storedSample(child, domain), W7CompositionCpuOracle.storedSample(root, domain),
            ), domain)
            return W7CompositionCpuOracle.trace(root, child, restored)
        }

        for (mode in GradientAlphaMode.entries) {
            originalStops[0] = GradientStop(0f, left)
            originalStops[1] = GradientStop(1f, right)
            val recorder = PictureRecorder()
            recorder.beginRecording(pixel).drawRect(pixel, Paint(
                color = ColorARGB.of(128, 0, 0, 0), shader = shader(mode, originalStops), antiAlias = false,
            ))
            val memory = recorder.finishRecordingAsPicture()
            val wire = assertNotNull(Picture.fromByteArray(memory.toByteArray()))
            // Picture owns the recorded immutable stops, rather than the mutable caller list.
            originalStops[0] = GradientStop(0f, ColorARGB.Green)
            originalStops[1] = GradientStop(1f, ColorARGB.Green)
            for (domain in listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED,
                CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)) {
                val direct = Surface(1, 1, config = RenderConfig(compositionDomain = domain))
                direct.canvas { drawRect(pixel, paint(mode)) }
                val directExpected = directExpected(mode, domain)
                val directFirst = direct.render()
                assertColoredResult(directFirst, directExpected, PixelFormat.RGBA8, domain)
                assertContentEquals(directFirst.pixels, direct.render().pixels, "direct mode=$mode domain=$domain")

                val layered = Surface(1, 1, config = RenderConfig(compositionDomain = domain))
                layered.canvas {
                    drawRect(pixel, Paint(ColorARGB.White, antiAlias = false))
                    saveLayer()
                    drawRect(pixel, paint(mode))
                    restore()
                }
                val layeredExpected = layerExpected(mode, domain)
                val layeredFirst = layered.render()
                assertColoredResult(layeredFirst, layeredExpected, PixelFormat.RGBA8, domain)
                assertContentEquals(layeredFirst.pixels, layered.render().pixels, "layer mode=$mode domain=$domain")

                for (picture in listOf(memory, wire)) {
                    val replay = Surface(1, 1, config = RenderConfig(compositionDomain = domain))
                    replay.canvas { picture.playback(this) }
                    val first = replay.render()
                    assertColoredResult(first, directExpected, PixelFormat.RGBA8, domain)
                    assertContentEquals(first.pixels, replay.render().pixels, "picture=$picture mode=$mode domain=$domain")
                }
            }
        }
    }

    @Test
    fun encodedGradientHardStopsDegenerateAndZeroAlpha() {
        val encoded = CompositionDomain.SRGB_ENCODED
        val blue = ColorARGB.of(128, 0, 0, 255)
        val cases = listOf(
            "one-stop" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(.3f, blue))),
            "hard-stop" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), listOf(
                GradientStop(0f, ColorARGB.of(128, 255, 0, 0)), GradientStop(.5f, ColorARGB.of(64, 0, 255, 0)),
                GradientStop(.5f, blue), GradientStop(1f, blue),
            )),
            "degenerate-clamp-last-stop" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(0f, 0f), listOf(
                GradientStop(0f, ColorARGB.of(128, 255, 0, 0)), GradientStop(.5f, ColorARGB.Green),
                GradientStop(.5f, blue), GradientStop(1f, blue),
            )),
            "both-zero" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), listOf(
                GradientStop(0f, ColorARGB.Transparent), GradientStop(1f, ColorARGB.Transparent),
            )),
            "alpha-one" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), listOf(
                GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue),
            )),
        )
        for ((name, shader) in cases) {
            val expectedColor = when (name) {
                "both-zero" -> ColorARGB.Transparent
                "alpha-one" -> ColorARGB.of(255, 127, 0, 127)
                else -> blue
            }
            val expected = W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.solid(expectedColor, encoded), encoded,
            )
            val surface = Surface(1, 1, config = RenderConfig(compositionDomain = encoded))
            surface.canvas { drawRect(pixel, Paint(shader = shader, antiAlias = false)) }
            val first = surface.render()
            assertColoredResult(first, expected, PixelFormat.RGBA8, encoded)
            assertContentEquals(first.pixels, surface.render().pixels, name)
        }

        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val exclusions: List<Pair<String, Shader>> = listOf(
            "linear-interpolation" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), stops,
                interpolation = org.graphiks.kanvas.paint.ColorSpaceInterpolation.LINEAR),
            "repeat" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), stops, tileMode = TileMode.REPEAT),
            "mirror" to Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), stops, tileMode = TileMode.MIRROR),
            "radial" to Shader.RadialGradient(Point2F32(0f, 0f), 1f, stops),
            "sweep" to Shader.SweepGradient(Point2F32(0f, 0f), stops = stops),
            "composed" to Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Red),
                Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), stops)),
        )
        val full = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        exclusions.forEach { (name, shader) ->
            assertEncodedExclusion(name, "source", full) { drawRect(full, Paint(shader = shader, antiAlias = false)) }
        }
    }

    @Test
    fun compatibleExplicitFormatsMatchAutoForColoredPixels() {
        val background = ColorARGB.of(255, 23, 91, 173)
        val foreground = ColorARGB.of(128, 197, 41, 113)
        for (domain in CompositionDomain.entries) for (format in PixelFormat.entries) {
            val expected = expected(domain, background, foreground)
            val auto = renderColoredRectsRepeated(format, RenderConfig(compositionDomain = domain), background, foreground)
            val explicit = renderColoredRectsRepeated(
                format,
                RenderConfig(
                    gpuColorFormat = when (domain) {
                        CompositionDomain.LINEAR -> GPUColorFormat.RGBA8_UNORM_SRGB
                        CompositionDomain.SRGB_ENCODED -> GPUColorFormat.RGBA8_UNORM
                    },
                    compositionDomain = domain,
                ),
                background,
                foreground,
            )
            assertColoredResult(auto.first, expected, format, domain)
            assertColoredResult(auto.second, expected, format, domain)
            assertContentEquals(auto.first.pixels, auto.second.pixels, "auto domain=$domain format=$format")
            assertColoredResult(explicit.first, expected, format, domain)
            assertColoredResult(explicit.second, expected, format, domain)
            assertContentEquals(explicit.first.pixels, explicit.second.pixels, "explicit domain=$domain format=$format")
            assertContentEquals(auto.first.pixels, explicit.first.pixels, "domain=$domain format=$format")
        }
    }

    @Test
    fun drawColorPreservesDomainDirect() {
        val background = ColorARGB.of(255, 19, 143, 71)
        val foreground = ColorARGB.of(128, 211, 47, 129)
        for (domain in CompositionDomain.entries) for (format in PixelFormat.entries) {
            val expected = expected(domain, background, foreground)
            val surface = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
            surface.canvas {
                drawColor(background)
                drawColor(foreground)
            }
            val first = surface.render()
            assertColoredResult(first, expected, format, domain)
            val second = surface.render()
            assertColoredResult(second, expected, format, domain)
            assertContentEquals(first.pixels, second.pixels, "domain=$domain format=$format")
        }
    }

    @Test
    fun drawColorComposesWithEveryAdmittedEncodedSourceInBothOrdersAndPlainLayer() {
        // Removing W6's ordered root ownership makes every direct encoded mixed
        // frame below refuse; flattening the layer or losing a source store makes
        // the independently closed trace reject its result.
        val background = ColorARGB.of(160, 23, 91, 173)
        val solid = ColorARGB.of(128, 197, 41, 113)
        val gradientLeft = ColorARGB.of(128, 255, 0, 0)
        val gradientRight = ColorARGB.of(64, 0, 0, 255)
        val image = Image.fromPixels(1, 1, byteArrayOf(37, 101, -37, -1), alphaType = AlphaType.PREMUL)
        data class Source(
            val name: String,
            val source: (CompositionDomain) -> Array<WgslFloatEnvelopeV1Oracle.Interval>,
            val record: Canvas.() -> Unit,
        )
        val sources = listOf(
            Source("solid", { domain -> W7CompositionCpuOracle.solid(solid, domain) }) {
                drawRect(pixel, Paint(solid, antiAlias = false))
            },
            Source("gradient", { domain -> W7CompositionCpuOracle.gradient(
                gradientLeft, gradientRight, .5f, GradientAlphaMode.STRAIGHT, domain,
            ) }) {
                drawRect(pixel, Paint(shader = Shader.LinearGradient(
                    Point2F32(0f, 0f), Point2F32(1f, 0f),
                    listOf(GradientStop(0f, gradientLeft), GradientStop(1f, gradientRight)),
                ), antiAlias = false))
            },
            Source("image", { domain -> W7CompositionCpuOracle.sourceSpacePremul(
                requireNotNull(image.pixels), image.colorType, image.alphaType, domain,
            ) }) {
                drawImage(image, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
            },
        )
        // The historical LINEAR mixed route is not a native W7 owner; its
        // admitted controls remain in drawColorPreservesDomainDirect().
        for (domain in listOf(CompositionDomain.SRGB_ENCODED)) for (format in PixelFormat.entries)
            for (source in sources) for (colorFirst in listOf(true, false)) for (inLayer in listOf(false, true)) {
                val colorSource = W7CompositionCpuOracle.solid(background, domain)
                val firstSource = if (colorFirst) colorSource else source.source(domain)
                val secondSource = if (colorFirst) source.source(domain) else colorSource
                val first = W7CompositionCpuOracle.drawOnClear(firstSource, domain)
                val second = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                    secondSource, W7CompositionCpuOracle.storedSample(first, domain),
                ), domain)
                val expected = if (inLayer) {
                    val restored = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                        W7CompositionCpuOracle.storedSample(second, domain),
                        W7CompositionCpuOracle.storedSample(W7CompositionCpuOracle.clear(), domain),
                    ), domain)
                    W7CompositionCpuOracle.trace(first, second, restored)
                } else W7CompositionCpuOracle.trace(first, second)
                val surface = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
                surface.canvas {
                    if (inLayer) saveLayer()
                    if (colorFirst) drawColor(background) else source.record(this)
                    if (colorFirst) source.record(this) else drawColor(background)
                    if (inLayer) restore()
                }
                val firstRender = surface.render()
                assertColoredResult(firstRender, expected, format, domain)
                val secondRender = surface.render()
                assertColoredResult(secondRender, expected, format, domain)
                assertContentEquals(firstRender.pixels, secondRender.pixels,
                    "source=${source.name} colorFirst=$colorFirst layer=$inLayer domain=$domain format=$format")
            }
    }

    @Test
    fun surfaceDomainsAlternateWithoutCrossTargetCacheReuse() {
        val background = ColorARGB.of(255, 31, 79, 151)
        val foreground = ColorARGB.of(128, 223, 55, 107)
        listOf(
            CompositionDomain.LINEAR,
            CompositionDomain.SRGB_ENCODED,
            CompositionDomain.LINEAR,
            CompositionDomain.SRGB_ENCODED,
        ).forEach { domain ->
            val result = renderColoredRects(PixelFormat.RGBA8, RenderConfig(compositionDomain = domain), background, foreground)
            assertColoredResult(result, expected(domain, background, foreground), PixelFormat.RGBA8, domain)
        }
    }

    @Test
    fun plainLayerPreservesDomainAndAppliesRestoreOpacityOnce() {
        // Domain and one-time restore opacity are discriminated before GPU.  The
        // intermediate-store alternative is retained below as an honest overlap,
        // not misrepresented as a witness that can detect its omission.
        val background = ColorARGB.White
        val firstChild = ColorARGB.of(128, 255, 0, 0)
        val secondChild = ColorARGB.of(64, 0, 0, 255)
        for (restoreAlpha in listOf(255, 128)) for (domain in CompositionDomain.entries) for (format in PixelFormat.entries) {
            val stages = layerStages(domain, restoreAlpha, firstChild, secondChild)
            val tracedExpected = stages.trace
            assertEquals(4, tracedExpected.storeTrace.size, "Each root/child/restore store is traced before GPU")
            if (restoreAlpha == 128) {
                assertDisjoint("domain=$domain wrong-domain", tracedExpected,
                    layerStages(if (domain == CompositionDomain.LINEAR) CompositionDomain.SRGB_ENCODED else CompositionDomain.LINEAR,
                        restoreAlpha, firstChild, secondChild).trace)
                assertDisjoint("domain=$domain omitted-restore", tracedExpected,
                    layerStages(domain, 255, firstChild, secondChild).trace)
                assertDisjoint("domain=$domain doubled-restore", tracedExpected,
                    layerStages(domain, restoreAlpha, firstChild, secondChild, doubleRestore = true).trace)
                assertDisjoint("domain=$domain R/B-inverted", tracedExpected,
                    W7CompositionCpuOracle.swapRedBlue(tracedExpected))
                assertOverlaps("domain=$domain intermediate-store-omitted", tracedExpected,
                    layerStages(domain, restoreAlpha, firstChild, secondChild, omitIntermediateStore = true).trace)
            }

            val full = RectF32.ofLTRB(0f, 0f, 2f, 2f)
            val surface = Surface(2, 2, format, RenderConfig(compositionDomain = domain))
            surface.canvas {
                drawRect(full, Paint(background, antiAlias = false))
                saveLayer(paint = Paint(ColorARGB.of(restoreAlpha, 0, 0, 0),
                    blendMode = org.graphiks.kanvas.paint.BlendMode.SRC_OVER, antiAlias = false))
                drawRect(full, Paint(firstChild, antiAlias = false))
                drawRect(full, Paint(secondChild, antiAlias = false))
                restore()
            }
            val firstRender = surface.render()
            assertEveryPixelResult(firstRender, tracedExpected, format, domain)
            val secondRender = surface.render()
            assertEveryPixelResult(secondRender, tracedExpected, format, domain)
            assertContentEquals(firstRender.pixels, secondRender.pixels, "domain=$domain format=$format")
        }
    }

    @Test
    fun drawColorInsidePlainLayerAndOrdinaryImagePreserveEncodedComposition() {
        val background = ColorARGB.of(255, 19, 143, 71)
        val foreground = ColorARGB.of(128, 211, 47, 129)
        val image = Image.fromPixels(1, 1, byteArrayOf(37, 101, -37, -1), alphaType = AlphaType.PREMUL)
        for (domain in CompositionDomain.entries) for (format in PixelFormat.entries) {
            val root = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(background, domain), domain)
            val child = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(foreground, domain), domain)
            val drawColorResult = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.storedSample(child, domain), W7CompositionCpuOracle.storedSample(root, domain),
            ), domain)
            val drawColorExpected = W7CompositionCpuOracle.trace(root, child, drawColorResult)
            val drawColorLayer = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
            drawColorLayer.canvas {
                drawColor(background)
                saveLayer()
                drawColor(foreground)
                restore()
            }
            assertColoredResult(drawColorLayer.render(), drawColorExpected, format, domain)
            assertColoredResult(drawColorLayer.render(), drawColorExpected, format, domain)

            val imageChild = W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.sourceSpacePremul(
                    requireNotNull(image.pixels), image.colorType, image.alphaType, domain,
                ),
                domain,
            )
            val imageResult = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.storedSample(imageChild, domain), Array(4) { WgslFloatEnvelopeV1Oracle.Interval.ZERO },
            ), domain)
            val imageExpected = W7CompositionCpuOracle.trace(imageChild, imageResult)
            val imageLayer = Surface(1, 1, format, RenderConfig(compositionDomain = domain))
            imageLayer.canvas {
                saveLayer()
                drawImage(image, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
                restore()
            }
            assertColoredResult(imageLayer.render(), imageExpected, format, domain)
            assertColoredResult(imageLayer.render(), imageExpected, format, domain)
        }
    }

    @Test
    fun encodedLayerBoundsTranslationHardClipAndEmptyLayerAreTransactional() {
        for (format in PixelFormat.entries) {
            val transparent = W7CompositionCpuOracle.clear()
            val empty = Surface(2, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            empty.canvas { saveLayer(); restore() }
            assertEveryPixelResult(empty.render(), transparent, format, CompositionDomain.SRGB_ENCODED)
            assertEveryPixelResult(empty.render(), transparent, format, CompositionDomain.SRGB_ENCODED)

            val clipped = Surface(3, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            clipped.canvas {
                clipRect(RectF32.ofLTRB(1f, 0f, 3f, 2f), antiAlias = false)
                translate(1f, 0f)
                saveLayer(RectF32.ofLTRB(-1f, 0f, 1f, 2f))
                drawRect(RectF32.ofLTRB(-1f, 0f, 1f, 2f), Paint(ColorARGB.of(255, 37, 101, 219), antiAlias = false))
                restore()
            }
            val clippedChild = W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.solid(ColorARGB.of(255, 37, 101, 219), CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            )
            val clippedColor = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.storedSample(clippedChild, CompositionDomain.SRGB_ENCODED),
                W7CompositionCpuOracle.storedSample(transparent, CompositionDomain.SRGB_ENCODED),
            ), CompositionDomain.SRGB_ENCODED)
            val clippedTrace = W7CompositionCpuOracle.trace(clippedChild, clippedColor)
            assertEquals(2, clippedTrace.storeTrace.size, "Clip child and root restore stores are traced before GPU")
            val clippedExpected = listOf(transparent, clippedTrace, transparent,
                transparent, clippedTrace, transparent)
            val result = clipped.render()
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
            assertEquals(0, result.stats.opsRefused)
            assertPixelSequence(result, clippedExpected, format, CompositionDomain.SRGB_ENCODED)
            assertPixelSequence(clipped.render(), clippedExpected, format, CompositionDomain.SRGB_ENCODED)

            // The frozen DrawColor operand must retain the non-zero layer origin and hard
            // clip. Its own CTM remains identity, as required by encoded admission.
            val boundedColor = ColorARGB.of(255, 37, 101, 219)
            val bounded = Surface(3, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            bounded.canvas {
                clipRect(RectF32.ofLTRB(1f, 0f, 3f, 2f), antiAlias = false)
                saveLayer(RectF32.ofLTRB(1f, 0f, 3f, 2f))
                drawColor(boundedColor)
                restore()
            }
            val boundedChild = W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.solid(boundedColor, CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            )
            val coloredPixel = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.storedSample(boundedChild, CompositionDomain.SRGB_ENCODED),
                W7CompositionCpuOracle.storedSample(transparent, CompositionDomain.SRGB_ENCODED),
            ), CompositionDomain.SRGB_ENCODED)
            val boundedTrace = W7CompositionCpuOracle.trace(boundedChild, coloredPixel)
            assertEquals(2, boundedTrace.storeTrace.size, "Bounded child and root restore stores are traced before GPU")
            val expected = listOf(transparent, boundedTrace, boundedTrace,
                transparent, boundedTrace, boundedTrace)
            assertPixelSequence(bounded.render(), expected, format, CompositionDomain.SRGB_ENCODED)
            assertPixelSequence(bounded.render(), expected, format, CompositionDomain.SRGB_ENCODED)
        }
    }

    @Test
    fun encodedPlainLayerBudgetIsExactAndOneByteLessRecovers() {
        // The initial 2x2 attempt counted 16 + 16 + 512 + 16 + 16 = 576 and
        // omitted W6a's 16-byte geometry UniformData base, not a restore-opacity
        // uniform; native validation correctly said 592. The independent 3x3
        // preflight is anchored in W6aLayerGraphConstruction's LogicalTarget,
        // LayerTarget, ReadbackStaging and UniformData descriptors, plus
        // FrameSourceLayoutV4's two SourceUniformData rows (root and child):
        // B = 36 + 36 + 768 + 16 + 16 + 16 = 888 bytes.
        val budgetB = 36L + 36L + 768L + 16L + 16L + 16L
        val root = ColorARGB.of(255, 19, 143, 71)
        val child = ColorARGB.of(128, 211, 47, 129)
        fun record(surface: Surface) = surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(root, antiAlias = false))
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(child, antiAlias = false))
            restore()
        }
        val budgetRoot = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(root, CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED,
        )
        val budgetChild = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(child, CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED,
        )
        val budgetRestore = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.storedSample(budgetChild, CompositionDomain.SRGB_ENCODED),
            W7CompositionCpuOracle.storedSample(budgetRoot, CompositionDomain.SRGB_ENCODED),
        ), CompositionDomain.SRGB_ENCODED)
        val compositeExpected = W7CompositionCpuOracle.trace(budgetRoot, budgetChild, budgetRestore)
        assertEquals(3, compositeExpected.storeTrace.size, "Budget root, child and restore stores are traced before GPU")
        val accepted = Surface(3, 3, config = RenderConfig(
            compositionDomain = CompositionDomain.SRGB_ENCODED, frameLocalBudgetBytes = budgetB,
        ))
        record(accepted)
        val acceptedFirst = accepted.render()
        assertEveryPixelResult(acceptedFirst, compositeExpected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        val acceptedSecond = accepted.render()
        assertEveryPixelResult(acceptedSecond, compositeExpected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        assertContentEquals(acceptedFirst.pixels, acceptedSecond.pixels)

        val refused = Surface(3, 3, config = RenderConfig(
            compositionDomain = CompositionDomain.SRGB_ENCODED, frameLocalBudgetBytes = budgetB - 1L,
        ))
        record(refused)
        val sentinel = UByteArray(36) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> {
            refused.readPixels(RectF32.ofLTRB(0f, 0f, 3f, 3f), sentinel)
        }
        assertTrue(failure.message.orEmpty().startsWith("w6a.layer.frame_budget_exceeded:"), failure.message.orEmpty())
        assertContentEquals(UByteArray(36) { 0x5au }, sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(root, antiAlias = false)) }
        val expected = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(root, CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED,
        )
        assertEveryPixelResult(refused.render(), expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        assertEveryPixelResult(refused.render(), expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
    }

    @Test
    fun encodedCompositionExclusionMatrixIsTransactionalAfterAValidDraw() {
        // The table is declared before any Surface is rendered.  Each operation is
        // appended after a known admitted Rect so this validates whole-frame admission,
        // not merely that an isolated unsupported command has no renderer.
        val image = Image.fromPixels(1, 1, byteArrayOf(37, 101, -37, -1), alphaType = AlphaType.PREMUL)
        val full = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val matrix: List<Triple<String, String, Canvas.() -> Unit>> = listOf(
            Triple("aa", "geometry") { drawRect(pixel, Paint(ColorARGB.Red, antiAlias = true)) },
            Triple("stroke", "geometry") { drawRect(pixel, Paint(ColorARGB.Red, style = PaintStyle.STROKE, strokeWidth = 1f, antiAlias = false)) },
            Triple("path", "geometry") { drawPath(Path().apply { addRect(pixel) }, Paint(ColorARGB.Red, antiAlias = false)) },
            Triple("src", "blend") { drawColor(ColorARGB.Red, BlendMode.SRC) },
            Triple("noninteger-transform", "geometry") { translate(.5f, 0f); drawRect(pixel, Paint(ColorARGB.Red, antiAlias = false)) },
            Triple("translated-drawColor", "geometry") { translate(1f, 0f); drawColor(ColorARGB.Red) },
            Triple("translated-drawColor-src", "geometry") { translate(1f, 0f); drawColor(ColorARGB.Red, BlendMode.SRC) },
            Triple("image-filter", "source") { drawRect(pixel, Paint(ColorARGB.Red, imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false)) },
            Triple("color-filter", "source") { drawRect(pixel, Paint(ColorARGB.Red, colorFilter = ColorFilter.Luma, antiAlias = false)) },
            Triple("nested-layer", "layer") { saveLayer(); saveLayer(); restore(); restore() },
            Triple("sibling-layer", "layer") { saveLayer(); restore(); saveLayer(); restore() },
            Triple("scaled-image", "geometry") { drawImage(image, full, SamplingOptions.NEAREST, Paint(antiAlias = false)) },
            Triple("scaled-image-src", "geometry") { drawImage(image, full, SamplingOptions.NEAREST,
                Paint(blendMode = BlendMode.SRC, antiAlias = false)) },
            Triple("scaled-image-unpremul", "geometry") { drawImage(
                Image.fromPixels(1, 1, byteArrayOf(37, 101, -37, -1), alphaType = AlphaType.UNPREMUL),
                full, SamplingOptions.NEAREST, Paint(antiAlias = false),
            ) },
            Triple("nonnearest-image", "geometry") { drawImage(image, pixel, SamplingOptions.LINEAR, Paint(antiAlias = false)) },
            Triple("image-shader", "source") { drawRect(pixel, Paint(shader = Shader.Image(image), antiAlias = false)) },
            Triple("nine", "image") { drawImageNine(image, pixel, full, Paint(antiAlias = false)) },
            Triple("lattice", "image") { drawImageLattice(image, Lattice(emptyList(), emptyList()), full, Paint(antiAlias = false), SamplingOptions.NEAREST) },
            Triple("atlas", "image") { drawAtlas(image, listOf(Matrix3x3F32()), listOf(pixel), paint = Paint(antiAlias = false)) },
        )
        matrix.forEach { (name, suffix, appendExcluded) ->
            assertEncodedExclusion(name, suffix, full, appendExcluded)
        }
    }

    @Test
    fun encodedLayerDescriptorAndTopologyRefusalsAreTransactional() {
        val full = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        assertEncodedExclusion("init-with-previous", "layer", full) {
            saveLayer(SaveLayerRec(initWithPrevious = true)); restore()
        }
        assertEncodedExclusion("backdrop", "layer", full) {
            saveLayer(SaveLayerRec(backdrop = ImageFilter.Offset(1f, 0f))); restore()
        }
        // Topology is examined before command order: the AA draw cannot mask nested layers.
        assertEncodedExclusion("aa-before-nested", "layer", full) {
            drawRect(pixel, Paint(ColorARGB.Red, antiAlias = true))
            saveLayer(); saveLayer(); restore(); restore()
        }
    }

    @Test
    fun contradictoryNativeTargetsStayInvalidInBothDomainsAndUseSeparateRecovery() {
        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val contradictions = listOf(
            CompositionDomain.LINEAR to GPUColorFormat.RGBA8_UNORM,
            CompositionDomain.LINEAR to GPUColorFormat.BGRA8_UNORM,
            CompositionDomain.LINEAR to GPUColorFormat.RGBA16_FLOAT,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.RGBA8_UNORM_SRGB,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.BGRA8_UNORM,
            CompositionDomain.SRGB_ENCODED to GPUColorFormat.RGBA16_FLOAT,
        )
        for ((domain, invalidFormat) in contradictions) {
            val invalid = Surface(1, 1, config = RenderConfig(
                compositionDomain = domain,
                gpuColorFormat = invalidFormat,
            ))
            invalid.canvas { drawRect(pixel, Paint(ColorARGB.Red, antiAlias = false)) }
            val firstFailure = assertFailsWith<IllegalStateException> { invalid.readPixels(pixel, sentinel) }
            assertEquals("unsupported.surface.composition.target-format", firstFailure.message.orEmpty().substringBefore(':'))
            assertContentEquals(ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au), sentinel)
            invalid.discardRecordedOperations()
            invalid.canvas { drawRect(pixel, Paint(ColorARGB.Blue, antiAlias = false)) }
            val stillInvalid = assertFailsWith<IllegalStateException> { invalid.readPixels(pixel, sentinel) }
            assertEquals("unsupported.surface.composition.target-format", stillInvalid.message.orEmpty().substringBefore(':'))
            assertContentEquals(ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au), sentinel)

            val expected = W7CompositionCpuOracle.drawOnClear(
                W7CompositionCpuOracle.solid(ColorARGB.Blue, domain), domain,
            )
            val valid = Surface(1, 1, config = RenderConfig(compositionDomain = domain))
            valid.canvas { drawRect(pixel, Paint(ColorARGB.Blue, antiAlias = false)) }
            assertColoredResult(valid.render(), expected, PixelFormat.RGBA8, domain)
            assertColoredResult(valid.render(), expected, PixelFormat.RGBA8, domain)
        }
    }

    @Test
    fun ordinaryEncodedImageWithZeroAlphaCannotLeakRgb() {
        // These are ordinary SOURCE_SPACE/PREMUL/SRGB bytes, deliberately not a
        // normalized transparent snapshot: ZERO_ALPHA_GUARD must erase their RGB.
        val background = ColorARGB.of(255, 19, 143, 71)
        val expected = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(background, CompositionDomain.SRGB_ENCODED),
            CompositionDomain.SRGB_ENCODED,
        )
        val sources = listOf(
            Image.fromPixels(1, 1, byteArrayOf(-1, 71, 127, 0), alphaType = AlphaType.PREMUL),
            Image.fromPixels(1, 1, byteArrayOf(127, 71, -1, 0), colorType = ColorType.BGRA_8888,
                alphaType = AlphaType.PREMUL),
        )
        for (image in sources) for (format in PixelFormat.entries) {
            val surface = Surface(1, 1, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas {
                drawRect(pixel, Paint(background, antiAlias = false))
                drawImage(image, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
            }
            val first = surface.render()
            assertColoredResult(first, expected, format, CompositionDomain.SRGB_ENCODED)
            assertEquals(0, first.stats.opsRefused)
            val second = surface.render()
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun emptyEncodedSurfaceRendersSnapshotsAndReplaysTransparentAfterDiscard() {
        // Removing the encoded empty-frame owner makes the first render and the
        // snapshots refuse; changing SOURCE_SPACE or introducing a fake draw
        // makes these exact transparent bytes or replay assertions fail.
        val full = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val subset = pixel
        val clear = W7CompositionCpuOracle.clear()
        for (format in PixelFormat.entries) {
            val fresh = Surface(2, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            repeat(2) { assertEveryPixelResult(fresh.render(), clear, format, CompositionDomain.SRGB_ENCODED) }
            val discarded = Surface(2, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            discarded.canvas { drawRect(full, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
            assertTrue(discarded.render().pixels.any { it != 0.toUByte() })
            discarded.discardRecordedOperations()
            repeat(2) { assertEveryPixelResult(discarded.render(), clear, format, CompositionDomain.SRGB_ENCODED) }
            for (snapshot in listOf(
                fresh.makeImageSnapshot(), requireNotNull(fresh.makeImageSnapshot(subset)),
                discarded.makeImageSnapshot(), requireNotNull(discarded.makeImageSnapshot(subset)),
            )) {
                assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
                assertContentEquals(UByteArray(snapshot.width * snapshot.height * 4), requireNotNull(snapshot.pixels).toUByteArray())
                val replay = Surface(snapshot.width, snapshot.height, format,
                    RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
                replay.canvas { drawImage(snapshot, RectF32.ofLTRB(0f, 0f, snapshot.width.toFloat(), snapshot.height.toFloat()),
                    SamplingOptions.NEAREST, Paint(antiAlias = false)) }
                repeat(2) { assertEveryPixelResult(replay.render(), clear, format, CompositionDomain.SRGB_ENCODED) }
            }
        }
    }

    @Test
    fun encodedFullSubsetSnapshotsPreserveRepresentationThroughPicture() {
        // If subset snapshots retag an encoded target as attachment-linear premul,
        // replay either refuses or decodes the premultiplied bytes incorrectly.
        // The independently stored per-pixel oracle covers both channel orders.
        val red = ColorARGB.of(128, 255, 0, 0)
        val muted = ColorARGB.of(128, 128, 64, 32)
        val colors = listOf(red, muted, muted, red, red, muted)
        val subset = RectF32.ofLTRB(1f, 0f, 3f, 2f)
        val subsetColors = listOf(muted, muted, red, muted)
        for (format in PixelFormat.entries) {
            val source = Surface(3, 2, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            val sourceExpected = snapshotExpected(colors, CompositionDomain.SRGB_ENCODED, format)
            source.canvas { colors.forEachIndexed { index, color ->
                val x = (index % 3).toFloat()
                val y = (index / 3).toFloat()
                drawRect(RectF32.ofLTRB(x, y, x + 1f, y + 1f), Paint(color, antiAlias = false))
            } }
            val sourceResult = source.render()
            assertSnapshotPixels(sourceResult, sourceExpected)
            val sourceBytes = sourceResult.pixels
            val subsetBytes = listOf(1, 2, 4, 5).flatMap { index ->
                sourceBytes.copyOfRange(index * 4, index * 4 + 4).asList()
            }.toUByteArray()
            for ((snapshot, _, expectedBytes) in listOf(
                Triple(source.makeImageSnapshot(), colors, sourceBytes),
                Triple(requireNotNull(source.makeImageSnapshot(subset)), subsetColors, subsetBytes),
            )) {
                assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
                assertContentEquals(expectedBytes, requireNotNull(snapshot.pixels).toUByteArray())
                val copied = snapshot.copy()
                assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, copied.premultiplication)
                assertContentEquals(expectedBytes, requireNotNull(copied.pixels).toUByteArray())
                val destination = RectF32.ofLTRB(0f, 0f, snapshot.width.toFloat(), snapshot.height.toFloat())
                val recorder = PictureRecorder()
                recorder.beginRecording(destination).drawImage(copied, destination, SamplingOptions.NEAREST, Paint(antiAlias = false))
                val memory = recorder.finishRecordingAsPicture()
                val wire = assertNotNull(Picture.fromByteArray(memory.toByteArray()))
                for (picture in listOf(memory, wire)) {
                    val replayExpected = snapshotReplayExpected(copied, CompositionDomain.SRGB_ENCODED, format)
                    val replay = Surface(snapshot.width, snapshot.height, format,
                        RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
                    replay.canvas { picture.playback(this) }
                    val first = replay.render()
                    assertSnapshotPixels(first, replayExpected)
                    val second = replay.render()
                    assertSnapshotPixels(second, replayExpected)
                    assertContentEquals(first.pixels, second.pixels)
                }
            }
        }
    }

    @Test
    fun linearSnapshotsRetainHistoricalAttachmentRepresentation() {
        val source = Surface(3, 2, PixelFormat.RGBA8)
        source.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false)) }
        for (snapshot in listOf(source.makeImageSnapshot(), requireNotNull(source.makeImageSnapshot(RectF32.ofLTRB(1f, 0f, 3f, 2f))))) {
            assertEquals(ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL, snapshot.premultiplication)
        }
    }

    @Test
    fun encodedSnapshotReplaysInLinearWithoutRetagging() {
        // Replacing SOURCE_SPACE with attachment-linear bytes makes this red-over-blue
        // witness about (128, 0, 127, 255), rather than the independently decoded
        // linear result about (188, 0, 187, 255).
        val samples = listOf(
            ColorARGB.of(128, 255, 0, 0),
            ColorARGB.of(128, 128, 64, 32),
            ColorARGB.Transparent,
        )
        for (sourceFormat in PixelFormat.entries) for (color in samples) {
            val source = Surface(1, 1, sourceFormat, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            val sourceExpected = snapshotExpected(listOf(color), CompositionDomain.SRGB_ENCODED, sourceFormat)
            source.canvas { drawRect(pixel, Paint(color, antiAlias = false)) }
            val sourceResult = source.render()
            assertSnapshotPixels(sourceResult, sourceExpected)
            val snapshot = source.makeImageSnapshot()
            assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
            assertContentEquals(sourceResult.pixels, requireNotNull(snapshot.pixels).toUByteArray())
            val expected = expectedReplay(snapshot, CompositionDomain.LINEAR, PixelFormat.RGBA8, ColorARGB.Blue)
            val replay = Surface(1, 1, PixelFormat.RGBA8)
            replay.canvas {
                drawRect(pixel, Paint(ColorARGB.Blue, antiAlias = false))
                drawImage(snapshot, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
            }
            val first = replay.render()
            assertColoredResult(first, expected, PixelFormat.RGBA8, CompositionDomain.LINEAR)
            val second = replay.render()
            assertColoredResult(second, expected, PixelFormat.RGBA8, CompositionDomain.LINEAR)
            assertContentEquals(first.pixels, second.pixels)
            if (color == samples.first()) {
                assertTrue(first.pixels[0].toInt() in 187..189, "red=${first.pixels[0]}")
                assertTrue(first.pixels[2].toInt() in 186..188, "blue=${first.pixels[2]}")
            }
        }
    }

    @Test
    fun linearSnapshotToEncodedRefusesAfterAdmissibleDrawAndRecovers() {
        val linearSource = Surface(1, 1)
        linearSource.canvas { drawRect(pixel, Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false)) }
        val legacy = linearSource.makeImageSnapshot()
        assertEquals(ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL, legacy.premultiplication)
        val sentinel = ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au)
        val refused = Surface(1, 1, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        refused.canvas {
            drawRect(pixel, Paint(ColorARGB.Blue, antiAlias = false))
            drawImage(legacy, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
        }
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(pixel, sentinel) }
        assertEquals("unsupported.surface.composition.image", failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(ubyteArrayOf(0x5au, 0x5au, 0x5au, 0x5au), sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(pixel, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        val expected = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(ColorARGB.of(255, 17, 61, 211), CompositionDomain.SRGB_ENCODED),
            CompositionDomain.SRGB_ENCODED,
        )
        assertColoredResult(refused.render(), expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        assertColoredResult(refused.render(), expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
    }

    @Test
    fun recordingOnlyEncodedSnapshotsRefuseWithoutRenderingWhileSceneCaptureRemainsValid() {
        for (subset in listOf<RectF32?>(null, pixel)) {
            val surface = Surface(1, 1, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas { drawRect(pixel, Paint(ColorARGB.Red, antiAlias = false)) }
            val capture = SceneRecordingScope.recordingOnly { surface.snapshotScene() }
            assertTrue(capture is SceneCaptureResult.Captured)
            val failure = assertFailsWith<IllegalStateException> {
                SceneRecordingScope.recordingOnly {
                    if (subset == null) surface.makeImageSnapshot() else requireNotNull(surface.makeImageSnapshot(subset))
                }
            }
            assertEquals("unsupported.surface.composition.recording-snapshot", failure.message.orEmpty().substringBefore(':'))
        }
    }

    @Test
    fun oneEncodedImageResourceAlternatesAcrossCompositionDomains() {
        val source = Surface(1, 1, PixelFormat.BGRA8, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        val sourceExpected = snapshotExpected(listOf(ColorARGB.of(128, 255, 0, 0)), CompositionDomain.SRGB_ENCODED, PixelFormat.BGRA8)
        source.canvas { drawRect(pixel, Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false)) }
        val sourceResult = source.render()
        assertSnapshotPixels(sourceResult, sourceExpected)
        val image = source.makeImageSnapshot()
        assertContentEquals(sourceResult.pixels, requireNotNull(image.pixels).toUByteArray())
        for (domain in listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED,
            CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)) {
            val expected = expectedReplay(image, domain, PixelFormat.RGBA8, ColorARGB.Blue)
            val replay = Surface(1, 1, config = RenderConfig(compositionDomain = domain))
            replay.canvas {
                drawRect(pixel, Paint(ColorARGB.Blue, antiAlias = false))
                drawImage(image, pixel, SamplingOptions.NEAREST, Paint(antiAlias = false))
            }
            val first = replay.render()
            assertColoredResult(first, expected, PixelFormat.RGBA8, domain)
            val second = replay.render()
            assertColoredResult(second, expected, PixelFormat.RGBA8, domain)
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    private fun renderColoredRects(
        format: PixelFormat,
        config: RenderConfig,
        background: ColorARGB,
        foreground: ColorARGB,
    ): RenderResult = Surface(1, 1, format, config).also { surface ->
        surface.canvas {
            drawRect(pixel, Paint(background, antiAlias = false))
            drawRect(pixel, Paint(foreground, antiAlias = false))
        }
    }.render()

    private data class LayerStages(val trace: W7CompositionCpuOracle.CompositionEnvelope)

    private fun layerStages(
        domain: CompositionDomain,
        restoreAlpha: Int,
        firstChild: ColorARGB,
        secondChild: ColorARGB,
        doubleRestore: Boolean = false,
        omitIntermediateStore: Boolean = false,
    ): LayerStages {
        val root = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
        val firstSource = W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.solid(firstChild, domain),
            W7CompositionCpuOracle.solid(ColorARGB.Transparent, domain),
        )
        val first = W7CompositionCpuOracle.store(firstSource, domain)
        val childDestination = if (omitIntermediateStore) firstSource else W7CompositionCpuOracle.storedSample(first, domain)
        val second = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.solid(secondChild, domain), childDestination,
        ), domain)
        val child = W7CompositionCpuOracle.storedSample(second, domain)
        val restored = W7CompositionCpuOracle.opacity(child, restoreAlpha)
        val restore = if (doubleRestore) W7CompositionCpuOracle.opacity(restored, restoreAlpha) else restored
        val result = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            restore, W7CompositionCpuOracle.storedSample(root, domain),
        ), domain)
        return LayerStages(W7CompositionCpuOracle.trace(root, first, second, result))
    }

    private fun assertDisjoint(
        label: String,
        correct: W7CompositionCpuOracle.CompositionEnvelope,
        alternative: W7CompositionCpuOracle.CompositionEnvelope,
    ) = assertTrue(correct.channels.zip(alternative.channels).any { (left, right) -> left.intersect(right).isEmpty() },
        "$label correct=${correct.channels} alternative=${alternative.channels}")

    private fun assertOverlaps(
        label: String,
        correct: W7CompositionCpuOracle.CompositionEnvelope,
        alternative: W7CompositionCpuOracle.CompositionEnvelope,
    ) = assertTrue(correct.channels.zip(alternative.channels).all { (left, right) -> left.intersect(right).isNotEmpty() },
        "$label correct=${correct.channels} alternative=${alternative.channels}")

    private fun renderColoredRectsRepeated(
        format: PixelFormat,
        config: RenderConfig,
        background: ColorARGB,
        foreground: ColorARGB,
    ): Pair<RenderResult, RenderResult> {
        val surface = Surface(1, 1, format, config)
        surface.canvas {
            drawRect(pixel, Paint(background, antiAlias = false))
            drawRect(pixel, Paint(foreground, antiAlias = false))
        }
        return surface.render() to surface.render()
    }

    private fun assertEncodedExclusion(
        name: String,
        suffix: String,
        full: RectF32,
        appendExcluded: Canvas.() -> Unit,
    ) {
        val refused = Surface(2, 2, config = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
        refused.canvas {
            drawRect(full, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            appendExcluded()
        }
        val sentinel = UByteArray(16) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(full, sentinel) }
        assertEquals("unsupported.surface.composition.$suffix", failure.message.orEmpty().substringBefore(':'), name)
        assertContentEquals(UByteArray(16) { 0x5au }, sentinel, name)
        refused.discardRecordedOperations()
        refused.canvas {
            resetMatrix()
            drawRect(full, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        val expected = W7CompositionCpuOracle.drawOnClear(
            W7CompositionCpuOracle.solid(ColorARGB.of(255, 17, 61, 211), CompositionDomain.SRGB_ENCODED),
            CompositionDomain.SRGB_ENCODED,
        )
        val first = refused.render()
        assertEveryPixelResult(first, expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        val second = refused.render()
        assertEveryPixelResult(second, expected, PixelFormat.RGBA8, CompositionDomain.SRGB_ENCODED)
        assertContentEquals(first.pixels, second.pixels, name)
    }

    private fun expected(
        domain: CompositionDomain,
        background: ColorARGB,
        foreground: ColorARGB,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        val storedBackground = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(background, domain), domain)
        val result = W7CompositionCpuOracle.store(
            W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(foreground, domain),
                W7CompositionCpuOracle.storedSample(storedBackground, domain),
            ),
            domain,
        )
        return W7CompositionCpuOracle.trace(storedBackground, result)
    }

    private fun gradientOverWhite(
        left: ColorARGB,
        right: ColorARGB,
        alphaMode: GradientAlphaMode,
        domain: CompositionDomain,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        val white = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
        val result = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.gradient(left, right, .5f, alphaMode, domain),
            W7CompositionCpuOracle.storedSample(white, domain),
        ), domain)
        return W7CompositionCpuOracle.trace(white, result)
    }

    private fun assertColoredResult(
        result: RenderResult,
        expected: W7CompositionCpuOracle.CompositionEnvelope,
        format: PixelFormat,
        domain: CompositionDomain,
    ) {
        val publicExpected = W7CompositionCpuOracle.swizzle(expected, format)
        W7CompositionCpuOracle.assertAdmits(publicExpected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "domain=$domain format=$format scopes=${result.nativeEvidenceScopeKinds}")
        kotlin.test.assertEquals(
            if (domain == CompositionDomain.SRGB_ENCODED) ImagePremultiplicationV1.SOURCE_SPACE
            else ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL,
            result.premultiplication,
        )
    }

    private fun assertEveryPixelResult(
        result: RenderResult,
        expected: W7CompositionCpuOracle.CompositionEnvelope,
        format: PixelFormat,
        domain: CompositionDomain,
    ) {
        val pixelExpected = W7CompositionCpuOracle.swizzle(expected, format)
        require(result.pixels.size % 4 == 0)
        result.pixels.asList().chunked(4).forEach { actual ->
            W7CompositionCpuOracle.assertAdmits(pixelExpected, actual.toUByteArray())
        }
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "domain=$domain format=$format scopes=${result.nativeEvidenceScopeKinds}")
    }

    private fun assertPixelSequence(
        result: RenderResult,
        expected: List<W7CompositionCpuOracle.CompositionEnvelope>,
        format: PixelFormat,
        domain: CompositionDomain,
    ) {
        assertEquals(expected.size * 4, result.pixels.size)
        expected.forEachIndexed { index, pixel ->
            W7CompositionCpuOracle.assertAdmits(
                W7CompositionCpuOracle.swizzle(pixel, format),
                result.pixels.copyOfRange(index * 4, index * 4 + 4),
            )
        }
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "domain=$domain format=$format scopes=${result.nativeEvidenceScopeKinds}")
    }

    private fun snapshotExpected(
        colors: List<ColorARGB>,
        domain: CompositionDomain,
        format: PixelFormat,
    ): List<W7CompositionCpuOracle.CompositionEnvelope> = colors.map { color ->
            W7CompositionCpuOracle.swizzle(
                W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(color, domain), domain),
                format,
            )
        }

    private fun assertSnapshotPixels(
        result: RenderResult,
        expected: List<W7CompositionCpuOracle.CompositionEnvelope>,
    ) {
        assertEquals(expected.size * 4, result.pixels.size)
        expected.forEachIndexed { index, pixel ->
            W7CompositionCpuOracle.assertAdmits(pixel, result.pixels.copyOfRange(index * 4, index * 4 + 4))
        }
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "snapshot scopes=${result.nativeEvidenceScopeKinds}")
        assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, result.premultiplication)
    }

    private fun expectedReplay(
        image: Image,
        domain: CompositionDomain,
        targetFormat: PixelFormat,
        background: ColorARGB? = null,
    ): W7CompositionCpuOracle.CompositionEnvelope = expectedReplay(
        requireNotNull(image.pixels), image.colorType, image.alphaType, domain, targetFormat, background,
    )

    private fun snapshotReplayExpected(
        image: Image,
        domain: CompositionDomain,
        targetFormat: PixelFormat,
    ): List<W7CompositionCpuOracle.CompositionEnvelope> = requireNotNull(image.pixels).asList().chunked(4).map { bytes ->
        expectedReplay(bytes.toByteArray(), image.colorType, image.alphaType, domain, targetFormat)
    }

    private fun expectedReplay(
        bytes: ByteArray,
        colorType: ColorType,
        alphaType: AlphaType,
        domain: CompositionDomain,
        targetFormat: PixelFormat,
        background: ColorARGB? = null,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        val source = W7CompositionCpuOracle.sourceSpacePremul(
            bytes, colorType, alphaType, domain,
        )
        val root = background?.let { W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.solid(it, domain), domain) }
        val destination = root?.let { W7CompositionCpuOracle.storedSample(it, domain) }
            ?: W7CompositionCpuOracle.solid(ColorARGB.Transparent, domain)
        val result = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(source, destination), domain)
        return W7CompositionCpuOracle.swizzle(
            if (root == null) result else W7CompositionCpuOracle.trace(root, result),
            targetFormat,
        )
    }
}
