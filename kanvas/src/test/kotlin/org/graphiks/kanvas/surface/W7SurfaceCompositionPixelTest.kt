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
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.render.ir.ImagePremultiplicationV1
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.types.Lattice
import org.graphiks.math.color.ColorARGB
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
            val white = W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
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
        // Removing either the child-target store, the target domain, or the single restore
        // opacity multiplication changes this public native pixel.
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
                    layerStages(domain, restoreAlpha, secondChild, firstChild).trace)
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
            val root = W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(background, domain), domain)
            val child = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(foreground, domain), Array(4) { WgslFloatEnvelopeV1Oracle.Interval.ZERO },
            ), domain)
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

            val imageChild = W7CompositionCpuOracle.store(
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
            val transparent = W7CompositionCpuOracle.store(
                W7CompositionCpuOracle.solid(ColorARGB.Transparent, CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            )
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
            val result = clipped.render()
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
            assertEquals(0, result.stats.opsRefused)
            val clippedColor = W7CompositionCpuOracle.store(
                W7CompositionCpuOracle.solid(ColorARGB.of(255, 37, 101, 219), CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            )
            val clippedExpected = listOf(transparent, clippedColor, transparent,
                transparent, clippedColor, transparent)
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
            val coloredPixel = W7CompositionCpuOracle.store(
                W7CompositionCpuOracle.solid(boundedColor, CompositionDomain.SRGB_ENCODED),
                CompositionDomain.SRGB_ENCODED,
            )
            val expected = listOf(transparent, coloredPixel, coloredPixel,
                transparent, coloredPixel, coloredPixel)
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
        val compositeExpected = W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(
            W7CompositionCpuOracle.storedSample(W7CompositionCpuOracle.store(
                W7CompositionCpuOracle.solid(child, CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED,
            ), CompositionDomain.SRGB_ENCODED),
            W7CompositionCpuOracle.storedSample(W7CompositionCpuOracle.store(
                W7CompositionCpuOracle.solid(root, CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED,
            ), CompositionDomain.SRGB_ENCODED),
        ), CompositionDomain.SRGB_ENCODED)
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
        val expected = W7CompositionCpuOracle.store(
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

            val expected = W7CompositionCpuOracle.store(
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
        val expected = W7CompositionCpuOracle.store(
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
            source.canvas { colors.forEachIndexed { index, color ->
                val x = (index % 3).toFloat()
                val y = (index / 3).toFloat()
                drawRect(RectF32.ofLTRB(x, y, x + 1f, y + 1f), Paint(color, antiAlias = false))
            } }
            val sourceResult = source.render()
            assertSnapshotPixels(sourceResult, colors, format, CompositionDomain.SRGB_ENCODED)
            val sourceBytes = sourceResult.pixels
            val subsetBytes = listOf(1, 2, 4, 5).flatMap { index ->
                sourceBytes.copyOfRange(index * 4, index * 4 + 4).asList()
            }.toUByteArray()
            for ((snapshot, expectedColors, expectedBytes) in listOf(
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
                    val replay = Surface(snapshot.width, snapshot.height, format,
                        RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
                    replay.canvas { picture.playback(this) }
                    val first = replay.render()
                    assertSnapshotPixels(first, expectedColors, format, CompositionDomain.SRGB_ENCODED)
                    val second = replay.render()
                    assertSnapshotPixels(second, expectedColors, format, CompositionDomain.SRGB_ENCODED)
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
            source.canvas { drawRect(pixel, Paint(color, antiAlias = false)) }
            val snapshot = source.makeImageSnapshot()
            assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, snapshot.premultiplication)
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
        val expected = W7CompositionCpuOracle.store(
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
        source.canvas { drawRect(pixel, Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false)) }
        val image = source.makeImageSnapshot()
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
        val root = W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(ColorARGB.White, domain), domain)
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
        val expected = W7CompositionCpuOracle.store(
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
        val storedBackground = W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(background, domain), domain)
        val result = W7CompositionCpuOracle.store(
            W7CompositionCpuOracle.srcOver(
                W7CompositionCpuOracle.solid(foreground, domain),
                W7CompositionCpuOracle.storedSample(storedBackground, domain),
            ),
            domain,
        )
        return W7CompositionCpuOracle.trace(storedBackground, result)
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

    private fun assertSnapshotPixels(
        result: RenderResult,
        colors: List<ColorARGB>,
        format: PixelFormat,
        domain: CompositionDomain,
    ) {
        val expected = colors.map { color ->
            W7CompositionCpuOracle.swizzle(
                W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(color, domain), domain),
                format,
            )
        }
        assertEquals(expected.size * 4, result.pixels.size)
        expected.forEachIndexed { index, pixel ->
            W7CompositionCpuOracle.assertAdmits(pixel, result.pixels.copyOfRange(index * 4, index * 4 + 4))
        }
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            "domain=$domain format=$format scopes=${result.nativeEvidenceScopeKinds}")
        assertEquals(ImagePremultiplicationV1.SOURCE_SPACE, result.premultiplication)
    }

    private fun expectedReplay(
        image: Image,
        domain: CompositionDomain,
        targetFormat: PixelFormat,
        background: ColorARGB,
    ): W7CompositionCpuOracle.CompositionEnvelope {
        val source = W7CompositionCpuOracle.sourceSpacePremul(
            requireNotNull(image.pixels), image.colorType, image.alphaType, domain,
        )
        val destination = W7CompositionCpuOracle.solid(background, domain)
        return W7CompositionCpuOracle.swizzle(
            W7CompositionCpuOracle.store(W7CompositionCpuOracle.srcOver(source, destination), domain),
            targetFormat,
        )
    }
}
