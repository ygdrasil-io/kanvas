@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval as I

class W5eImageFamiliesSurfacePixelTest {
    @Test fun latticeCellsMatchPublicSemantics() {
        // FIXED_COLOR must replace blue texels with red, while TRANSPARENT
        // preserves green even under SRC. Divs retain fixed outer bands.
        val blue = Image.fromPixels(3, 3, ByteArray(36) { if (it % 4 >= 2) -1 else 0 },
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val flags = List(9) { when (it) { 1 -> LatticeFlags.TRANSPARENT; 4 -> LatticeFlags.FIXED_COLOR; else -> LatticeFlags.DEFAULT } }
        val lattice = Lattice(listOf(1, 2), listOf(1, 2), colors = List(9) { ColorARGB.Red }, flags = flags)
        for (sampling in listOf(SamplingOptions.LINEAR, SamplingOptions.Cubic(0f, .5f))) for (flipped in listOf(false, true)) {
            val expected = List(49) { index ->
                val x = if (flipped) 6 - index % 7 else index % 7
                val y = index / 7
                expectedLinear(when {
                    index % 7 == 6 -> listOf(0f, 1f, 0f, 1f)
                    x in 1..5 && y == 0 -> listOf(0f, 1f, 0f, 1f)
                    x in 1..5 && y in 1..5 -> listOf(1f, 0f, 0f, 1f)
                    else -> listOf(0f, 0f, 1f, 1f)
                })
            }
            val surface = Surface(7, 7)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), paint().copy(color = ColorARGB.Green))
                clipRect(RectF32.ofLTRB(0f, 0f, 6f, 7f), antiAlias = false)
                drawImageLattice(blue, lattice, RectF32.ofLTRB(if (flipped) 7f else 0f, 0f, if (flipped) 0f else 7f, 7f), paint(), sampling)
            }
            val result = surface.render()
            assertPixels(expected, result.pixels)
            assertEquals(2, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun latticeExplicitRectanglesSupplyDestinations() {
        // Supplied rectangles are destination cells: exchange opposite corners.
        val indices = listOf(8, 1, 2, 3, 4, 5, 6, 7, 0)
        val expected = expectations(indices)
        val rects = indices.map { RectF32.ofLTRB((it % 3).toFloat(), (it / 3).toFloat(), (it % 3 + 1).toFloat(), (it / 3 + 1).toFloat()) }
        val moved = Surface(3, 3)
        moved.canvas { drawImageLattice(image(), Lattice(listOf(1, 2), listOf(1, 2), rects = rects),
            RectF32.ofLTRB(0f, 0f, 3f, 3f), paint(), SamplingOptions.NEAREST) }
        assertPixels(expected, moved.render().pixels)
    }

    @Test fun atlasEntriesKeepTransformsColorsAndBlend() {
        // Independent 2x2 sprites, with a quarter turn for the second sprite;
        // their overlapping column must observe entry order and paint alpha once.
        val source = Image.fromPixels(2, 1, byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1),
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val alphaPaint = paint().copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f), blendMode = BlendMode.SRC_OVER)
        val alpha = I.input(alphaPaint.color.alphaNormalized)
        val inverse = WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, alpha)
        val redUnderBlue = WgslFloatEnvelopeV1Oracle.gradientMultiply(alpha, inverse)
        val combinedAlpha = WgslFloatEnvelopeV1Oracle.gradientAdd(alpha, redUnderBlue)
        val expected = List(8) { index -> when (index % 4) {
            0 -> bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(arrayOf(alpha, I.ZERO, I.ZERO, alpha)))
            1 -> bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(arrayOf(redUnderBlue, I.ZERO, alpha, combinedAlpha)))
            else -> expectedLinear(listOf(0f, 0f, 0f, 0f))
        } }
        val surface = Surface(4, 2)
        surface.canvas {
            clipRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), antiAlias = false)
            drawAtlas(source, listOf(Matrix3x3F32(sx = 2f, sy = 2f),
                Matrix3x3F32(sx = 0f, kx = -2f, tx = 3f, ky = 2f, sy = 0f)),
                listOf(RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(1f, 0f, 2f, 1f)), paint = alphaPaint)
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)

        // Entry color is the blend source, image is destination; SRC produces
        // green over red/blue, whereas MODULATE produces black.
        for (mode in listOf(BlendMode.SRC, BlendMode.MODULATE)) {
            val coloredExpected = List(2) { expectedLinear(if (mode == BlendMode.SRC)
                listOf(0f, 1f, 0f, 1f) else listOf(0f, 0f, 0f, 1f)) }
            val colored = Surface(2, 1)
            colored.canvas { drawAtlas(source, listOf(Matrix3x3F32()), listOf(RectF32.ofLTRB(0f, 0f, 2f, 1f)),
                listOf(ColorARGB.Green), mode, paint()) }
            assertPixels(coloredExpected, colored.render().pixels)
        }
    }

    @Test fun imageFamiliesKeepMixedCommandOrder() {
        val expected = listOf(0, 4, 2, 6).map { expectations(listOf(it)).single() }
        val surface = Surface(4, 1)
        surface.canvas {
            drawImageRect(image(), RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(0f, 0f, 4f, 1f), paint())
            drawImageLattice(image(), Lattice(emptyList(), emptyList(), colors = listOf(ColorARGB.Magenta),
                flags = listOf(LatticeFlags.FIXED_COLOR)), RectF32.ofLTRB(1f, 0f, 4f, 1f), paint(), SamplingOptions.NEAREST)
            drawAtlas(image(), listOf(Matrix3x3F32(sx = 2f, tx = 2f)), listOf(RectF32.ofLTRB(2f, 0f, 3f, 1f)), paint = paint())
            drawRect(RectF32.ofLTRB(3f, 0f, 4f, 1f), paint())
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(4, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun atlasPublicBlendModesKeepBoundedSourceEquations() {
        // Literal opaque black/white equations distinguish Porter-Duff factors,
        // source/destination orientation and separable branch selection.
        // Zero/zero is the exact bounded domain for nonseparable helper guards.
        val whiteBlack = setOf(BlendMode.SRC, BlendMode.SRC_OVER, BlendMode.SRC_IN, BlendMode.SRC_ATOP, BlendMode.HARD_LIGHT)
        val blackWhite = setOf(BlendMode.DST, BlendMode.DST_OVER, BlendMode.DST_IN, BlendMode.DST_ATOP,
            BlendMode.OVERLAY, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.SOFT_LIGHT)
        val whiteWhite = setOf(BlendMode.PLUS, BlendMode.SCREEN, BlendMode.LIGHTEN, BlendMode.DIFFERENCE, BlendMode.EXCLUSION)
        val transparent = setOf(BlendMode.CLEAR, BlendMode.SRC_OUT, BlendMode.DST_OUT, BlendMode.XOR)
        val nonseparable = setOf(BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY)
        val modes = BlendMode.entries
        val expected = modes.flatMap { mode -> (0..1).map { column ->
            val value = when { mode in whiteBlack -> if (column == 0) 1f else 0f
                mode in blackWhite -> if (column == 0) 0f else 1f
                mode in whiteWhite -> 1f
                else -> 0f }
            expectedLinear(listOf(value, value, value, if (mode in transparent) 0f else 1f))
        } }
        val source = Image.fromPixels(2, 1, byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1),
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val blackSource = Image.fromPixels(1, 1, byteArrayOf(0, 0, 0, -1), alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        for ((row, mode) in modes.withIndex()) {
            val surface = Surface(2, 1)
            surface.canvas { drawAtlas(if (mode in nonseparable) blackSource else source,
                listOf(Matrix3x3F32(), Matrix3x3F32(tx = 1f)),
                listOf(RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(if (mode in nonseparable) 0f else 1f, 0f, if (mode in nonseparable) 1f else 2f, 1f)),
                listOf(if (mode in nonseparable) ColorARGB.Black else ColorARGB.White, ColorARGB.Black), mode, paint())
            }
            val result = try { surface.render() } catch (failure: IllegalStateException) { throw AssertionError("Atlas mode=$mode", failure) }
            assertPixels(expected.subList(row * 2, row * 2 + 2), result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun atlasEntryColorsPrecedePaintAlphaAndOrderedFinalBlendOnA8() {
        // SRC entry color survives a zero A8 mask. Per-entry SRC_OVER then
        // accumulates two paint-alpha contributions, rather than one folded source.
        val alphaPaint = paint().copy(color = ColorARGB.fromRGBA(0f, 0f, 1f, .5f), blendMode = BlendMode.SRC_OVER)
        val a = I.input(alphaPaint.color.alphaNormalized)
        val retained = WgslFloatEnvelopeV1Oracle.gradientMultiply(a, WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, a))
        val expected = listOf(bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(arrayOf(a, retained, I.ZERO,
            WgslFloatEnvelopeV1Oracle.gradientAdd(a, retained)))))
        val source = Image.fromPixels(1, 1, byteArrayOf(0), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        val surface = Surface(1, 1)
        surface.canvas { drawAtlas(source, List(2) { Matrix3x3F32() }, List(2) { RectF32.ofLTRB(0f, 0f, 1f, 1f) },
            listOf(ColorARGB.Green, ColorARGB.Red), BlendMode.SRC, alphaPaint) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    @Test fun atlasSoftLightSignedP3RefusesAndRuntimeRecovers() {
        // P3 red maps to negative green/blue in linear sRGB. SoftLight's eager
        // sqrt(destinationColor) is non-finite even when select chooses its polynomial.
        val healthyExpected = listOf(expectedLinear(listOf(0f, 0f, 1f, 1f)))
        val signed = Image.fromPixels(1, 1, byteArrayOf(-1, 0, 0, -1), alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.DISPLAY_P3)
        val failing = Surface(1, 1)
        failing.canvas { drawAtlas(signed, listOf(Matrix3x3F32()), listOf(RectF32.ofLTRB(0f, 0f, 1f, 1f)),
            listOf(ColorARGB.White), BlendMode.SOFT_LIGHT, paint()) }
        val failure = assertThrows<IllegalStateException> { failing.render() }
        assertEquals("unsupported.material.image.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        val healthy = Surface(1, 1)
        healthy.canvas { drawAtlas(image(), listOf(Matrix3x3F32()), listOf(RectF32.ofLTRB(2f, 0f, 3f, 1f)), paint = paint()) }
        assertPixels(healthyExpected, healthy.render().pixels)
    }

    @Test fun atlasSoftLightNonendpointSrgbColorMatchesBoundedEquation() {
        // Intermediate entry sRGB exercises pow(log2/exp2). Positive sampled
        // destination channels all exceed 1/4, so SoftLight selects sqrt(d),
        // while the red entry channel selects low and green/blue select high.
        val entry = ColorARGB.of(160, 192, 224)
        val destinationCodes = listOf(64, 128, 192)
        fun add(a: I, b: I) = WgslFloatEnvelopeV1Oracle.gradientAdd(a, b)
        fun sub(a: I, b: I) = WgslFloatEnvelopeV1Oracle.gradientSubtract(a, b)
        fun mul(a: I, b: I) = WgslFloatEnvelopeV1Oracle.gradientMultiply(a, b)
        val source = listOf(entry.redNormalized, entry.greenNormalized, entry.blueNormalized)
            .map { WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(I.input(it)) }
        val resultSource = Array(4) { channel -> if (channel == 3) I.ONE else {
            val d = WgslFloatEnvelopeV1Oracle.imageUnorm8(destinationCodes[channel])
            val s = source[channel]
            val twiceSource = mul(I.input(2f), s)
            val root = WgslFloatEnvelopeV1Oracle.gradientSqrt(d)
            val low = sub(d, mul(mul(sub(I.ONE, twiceSource), d), sub(I.ONE, d)))
            val high = add(d, mul(sub(twiceSource, I.ONE), sub(root, d)))
            if (channel == 0) low else high
        } }
        val expected = listOf(bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(resultSource)))
        val image = Image.fromPixels(1, 1, byteArrayOf(64, 128.toByte(), 192.toByte(), -1),
            alphaType = AlphaType.UNPREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val surface = Surface(1, 1)
        surface.canvas { drawAtlas(image, listOf(Matrix3x3F32()), listOf(RectF32.ofLTRB(0f, 0f, 1f, 1f)),
            listOf(entry), BlendMode.SOFT_LIGHT, paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun latticeAndAtlasA8GradientChildrenRetainLocalCoordinates() {
        val expected = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Red, ColorARGB.Green).map {
            expectedLinear(listOf(it.redNormalized, it.greenNormalized, it.blueNormalized, 1f)) }
        val mask = Image.fromPixels(2, 1, byteArrayOf(-1, -1), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        val shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(2f, 0f), listOf(
            GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Green), GradientStop(1f, ColorARGB.Green)))
        val sourcePaint = paint().copy(color = ColorARGB.White, shader = shader)
        val surface = Surface(4, 1)
        surface.canvas {
            drawImageLattice(mask, Lattice(listOf(1), emptyList()), RectF32.ofLTRB(0f, 0f, 2f, 1f), sourcePaint, SamplingOptions.NEAREST)
            drawAtlas(mask, listOf(Matrix3x3F32(tx = 2f)), listOf(RectF32.ofLTRB(0f, 0f, 2f, 1f)), paint = sourcePaint)
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(2, result.stats.opsDispatched)
    }

    @Test fun latticeInvalidTablesRefuseWithTypedDiagnostics() {
        for (invalid in listOf(
            Lattice(listOf(2, 1), listOf(1, 2)),
            Lattice(listOf(1, 1), listOf(1, 2)),
            Lattice(listOf(-1, 2), listOf(1, 2)),
            Lattice(listOf(1, 4), listOf(1, 2)),
            Lattice(listOf(1, 2), listOf(1, 2), rects = emptyList()),
            Lattice(listOf(1, 2), listOf(1, 2), flags = emptyList()),
            Lattice(listOf(1, 2), listOf(1, 2), colors = emptyList()),
            Lattice(emptyList(), emptyList(), flags = listOf(LatticeFlags.FIXED_COLOR)),
        )) {
            val surface = Surface(3, 3)
            surface.canvas { drawImageLattice(image(), invalid, RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("invalid.material.image.lattice", failure.message.orEmpty().substringBefore(':'))
        }
    }

    @Test fun latticeAdjacentCellsKeepFullInteriorCoverage() {
        // At shared x=1.25, separate .25/.75 SRC_OVER contributions would
        // produce alpha .8125. The original lattice interior has coverage one.
        val expected = listOf(.75f, 1f, 1f, .25f).map { expectedLinear(listOf(0f, 0f, it, it)) }
        val blue = Image.fromPixels(3, 1, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1),
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val surface = Surface(4, 1)
        surface.canvas { drawImageLattice(blue, Lattice(listOf(1, 2), emptyList()),
            RectF32.ofLTRB(.25f, 0f, 3.25f, 1f), paint().copy(antiAlias = true, blendMode = BlendMode.SRC_OVER), SamplingOptions.NEAREST) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    @Test fun latticeSolidPaintAlphaAppliesOnceAndMoreThanNineCellsRemainDistinct() {
        val alphaPaint = paint().copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        val a = alphaPaint.color.alphaNormalized
        val expected = listOf(expectedLinear(listOf(0f, 0f, a, a)), expectedLinear(listOf(a, 0f, 0f, a)),
            expectedLinear(listOf(0f, 1f, 0f, 1f)))
        val blue = Image.fromPixels(3, 1, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1),
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val surface = Surface(3, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 1f), paint().copy(color = ColorARGB.Green))
            drawImageLattice(blue, Lattice(listOf(1, 2), emptyList(), colors = List(3) { ColorARGB.Red },
                flags = listOf(LatticeFlags.DEFAULT, LatticeFlags.FIXED_COLOR, LatticeFlags.TRANSPARENT)),
                RectF32.ofLTRB(0f, 0f, 3f, 1f), alphaPaint, SamplingOptions.NEAREST)
        }
        assertPixels(expected, surface.render().pixels)

        val solidExpected = listOf(expectedLinear(listOf(a, 0f, 0f, a)))
        val solid = Surface(1, 1)
        solid.canvas { drawImageLattice(Image.fromPixels(1, 1, byteArrayOf(0), ColorType.ALPHA_8, alphaType = AlphaType.PREMUL),
            Lattice(emptyList(), emptyList(), colors = listOf(ColorARGB.Red), flags = listOf(LatticeFlags.FIXED_COLOR)),
            RectF32.ofLTRB(0f, 0f, 1f, 1f), alphaPaint, SamplingOptions.NEAREST) }
        assertPixels(solidExpected, solid.render().pixels)
    }

    @Test fun latticeMoreThanNineExplicitCellsRemainDistinct() {
        val rowColors = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.Yellow, ColorARGB.Magenta)
        val wideExpected = List(15) { index -> expectedLinear(rowColors[index % 5].let {
            listOf(it.redNormalized, it.greenNormalized, it.blueNormalized, 1f) }) }
        val wide = Surface(5, 3)
        wide.canvas { drawImageLattice(image(), Lattice(listOf(0, 1, 2, 3), listOf(0, 1),
            rects = List(15) { RectF32.ofLTRB((it % 5).toFloat(), (it / 5).toFloat(), (it % 5 + 1).toFloat(), (it / 5 + 1).toFloat()) },
            colors = List(15) { rowColors[it % 5] }, flags = List(15) { LatticeFlags.FIXED_COLOR }),
            RectF32.ofLTRB(0f, 0f, 5f, 3f), paint(), SamplingOptions.NEAREST) }
        assertPixels(wideExpected, wide.render().pixels)
    }

    @Test fun latticeDeclaredSamplingFiltersAcrossSourceCellEdges() {
        val sourceBytes = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1)
        for (sampling in listOf(SamplingOptions.LINEAR, SamplingOptions.Cubic(0f, .5f))) {
            val expected = listOf(.5f, 1.25f, 1.75f, 2.5f).map { x -> bounded(W5eDecodedImageCpuOracle.sampledColorPixel(
                3, 1, sourceBytes, x, .5f, sampling == SamplingOptions.LINEAR,
                org.graphiks.kanvas.paint.TileMode.CLAMP, org.graphiks.kanvas.paint.TileMode.CLAMP,
                cubic = sampling as? SamplingOptions.Cubic)) }
            val source = Image.fromPixels(3, 1, sourceBytes, alphaType = AlphaType.PREMUL)
            val surface = Surface(4, 1)
            surface.canvas { drawImageLattice(source, Lattice(listOf(1, 2), emptyList()),
                RectF32.ofLTRB(0f, 0f, 4f, 1f), paint(), sampling) }
            assertPixels(expected, surface.render().pixels)
        }
    }

    @Test fun latticeRegularSelectorKeepsMoreThanNineCells() {
        val palette = listOf(listOf(1f, 0f, 0f, 1f), listOf(0f, 1f, 0f, 1f), listOf(0f, 0f, 1f, 1f), listOf(1f, 1f, 1f, 1f))
        val indices = listOf(0, 1, 2, 3, 1, 2, 3, 0, 2, 3, 0, 1, 3, 0, 1, 2)
        val expected = indices.map { expectedLinear(palette[it]) }
        val source = Image.fromPixels(4, 4, indices.flatMap { palette[it].map { value -> (value * 255f).toInt().toByte() } }.toByteArray(),
            alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val surface = Surface(4, 4)
        surface.canvas { drawImageLattice(source, Lattice(listOf(1, 2, 3), listOf(1, 2, 3)),
            RectF32.ofLTRB(0f, 0f, 4f, 4f), paint(), SamplingOptions.NEAREST) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    @Test fun imageNineSharedBoundarySelectionDoesNotDiscardAfterCancellation() {
        // Independent constant-source expectation isolates an artificial transparent
        // hole from legitimate nearest-coordinate rounding at this extreme scale.
        val expected = List(6) { expectedLinear(listOf(1f, 0f, 0f, 1f)) }
        val opaque = Image.fromPixels(3, 3, ByteArray(36) { index ->
            if (index % 4 == 0 || index % 4 == 3) -1 else 0
        }, alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
        val surface = Surface(2, 3)
        surface.canvas { drawImageNine(opaque, center(), RectF32.ofLTRB(-16777216f, 0f, 2f, 3f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNinePreservesCornersAndStretchesEdgesAndCenter() {
        // A single stretched center patch loses eight distinct source colors.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = expectations(axis.flatMap { row -> axis.map { column -> row * 3 + column } })
        val surface = Surface(7, 7, config = RenderConfig(preparedImageRoute = PreparedImageRoute.BOUNDED_NEAREST_1_TO_1))
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineHandlesSmallAndFlippedDestinations() {
        // A destination smaller than its two borders compresses both borders equally;
        // the center disappears. Signed destination extents reverse their order.
        val cases = listOf(
            RectF32.ofLTRB(0f, 0f, 1.75f, 1.75f) to listOf(0, 2, 6, 8),
            RectF32.ofLTRB(1.75f, 0f, 0f, 1.75f) to listOf(2, 0, 8, 6),
            RectF32.ofLTRB(1.75f, 1.75f, 0f, 0f) to listOf(8, 6, 2, 0),
        )
        for ((destination, indices) in cases) {
            val expected = expectations(indices)
            val surface = Surface(2, 2)
            surface.canvas { drawImageNine(image(), center(), destination, paint()) }
            val result = surface.render()
            assertPixels(expected, result.pixels)
            assertEquals(1, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun imageNinePaintAlphaAndBlendApplyOnce() {
        // Per-cell public draws, duplicate opacity, or tinting RGBA by paint RGB all
        // change these independently derived source-over / source replacement pixels.
        val sourcePaint = paint().copy(color = ColorARGB.fromRGBA(0f, 1f, 0f, .5f))
        val axis = listOf(0, 1, 1, 2)
        for (mode in listOf(BlendMode.SRC, BlendMode.SRC_OVER, BlendMode.DIFFERENCE)) {
            val expected = List(16) { index ->
                if (index == 5) expectedLinear(listOf(0f, 1f, 0f, 1f)) else {
                    val sourceIndex = axis[index / 4] * 3 + axis[index % 4]
                    val alpha = I.input(sourcePaint.color.alphaNormalized)
                    val source = List(3) { channel -> WgslFloatEnvelopeV1Oracle.gradientMultiply(
                        WgslFloatEnvelopeV1Oracle.imageUnorm8(bytes[sourceIndex * 4 + channel].toInt() and 255), alpha) }
                    val components = Array(4) { channel ->
                        val value = if (channel == 3) alpha else source[channel]
                        if (mode == BlendMode.DIFFERENCE && channel == 3) I.ONE
                        else if (mode == BlendMode.DIFFERENCE && channel == 2) WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, value)
                        else if (mode == BlendMode.SRC || channel !in listOf(2, 3)) value else
                            WgslFloatEnvelopeV1Oracle.gradientAdd(value, WgslFloatEnvelopeV1Oracle.gradientSubtract(I.ONE, alpha))
                    }
                    bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(components))
                }
            }
            val surface = Surface(4, 4)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint().copy(color = ColorARGB.Blue))
                drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 4f, 4f), sourcePaint.copy(blendMode = mode))
                drawRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), paint().copy(color = ColorARGB.Green))
            }
            val result = surface.render()
            assertPixels(expected, result.pixels)
            assertEquals(3, result.stats.opsDispatched)
            assertEquals(0, result.stats.opsRefused)
        }
    }

    @Test fun imageNineTransformAndClipMatchPublicSemantics() {
        // A quarter turn swaps source axes; a device clip removes the final row.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = List(81) { index ->
            val x = index % 9
            val y = index / 9
            if (x in 1..7 && y in 1..6) expectations(listOf(axis[7 - x] * 3 + axis[y - 1])).single()
            else expectedLinear(listOf(0f, 0f, 0f, 0f))
        }
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 9f, 9f))
        canvas.concat(Matrix3x3F32(sx = 0f, kx = -1f, tx = 8f, ky = 1f, sy = 0f, ty = 1f))
        canvas.drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint())
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(9, 9)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 8f, 7f), antiAlias = false)
            picture.playback(this)
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)

        // The left/top fragment centers are outside the destination yet have 1/4
        // analytic outer coverage. Internal bands must not acquire their own AA.
        val probes = listOf(
            Triple(0, 3, listOf(.25f, .25f, 0f, .25f)),
            Triple(3, 0, listOf(0f, .25f, 0f, .25f)),
            Triple(6, 3, listOf(0f, .75f, .75f, .75f)),
            Triple(1, 3, listOf(1f, 1f, 0f, 1f)),
            Triple(2, 3, listOf(1f, 0f, 1f, 1f)),
        ).map { (x, y, rgba) -> (y * 8 + x) to expectedLinear(rgba) }
        val fractional = Surface(8, 8)
        fractional.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(.75f, .75f, 6.75f, 6.75f),
            paint().copy(antiAlias = true)) }
        val fractionalResult = fractional.render()
        for ((index, value) in probes) WgslFloatEnvelopeV1Oracle.assertAdmits(value,
            fractionalResult.pixels.copyOfRange(index * 4, index * 4 + 4))
        assertEquals(1, fractionalResult.stats.opsDispatched)
    }

    @Test fun imageNineEmptyDestinationAndDstRetainOriginalOperationCounts() {
        val expected = List(4) { expectedLinear(listOf(0f, 0f, 1f, 1f)) }
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), paint().copy(color = ColorARGB.Blue))
            drawImageNine(image(), center(), RectF32.ofLTRB(1f, 0f, 1f, 2f), paint())
            drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 2f, 2f), paint().copy(blendMode = BlendMode.DST))
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineOnlyEmptyDestinationRetainsOriginalOperation() {
        val expected = List(4) { expectedLinear(listOf(0f, 0f, 0f, 0f)) }
        val surface = Surface(2, 2)
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(1f, 0f, 1f, 2f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(0, result.stats.drawCallCount)
    }

    @Test fun imageNineClampsCenterAndOmitsEmptySourceCells() {
        val stretchedAxis = listOf(0, 0, 1, 1, 1, 2, 2)
        val clampedExpected = expectations(stretchedAxis.flatMap { row -> stretchedAxis.map { row * 3 + it } })
        val clamped = Surface(7, 7)
        clamped.canvas { drawImageNine(image(), RectF32.ofLTRB(-2f, -3f, 5f, 6f), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint()) }
        assertPixels(clampedExpected, clamped.render().pixels)
        // Empty source center columns leave destination untouched, even for SRC.
        val axis = listOf(0, 1, 1, 1, 1, 1, 2)
        val expected = List(49) { index ->
            val sourceX = when (index % 7) { 0 -> 0; 5 -> 1; 6 -> 2; else -> null }
            if (sourceX == null) expectedLinear(listOf(0f, 0f, 1f, 1f))
            else expectations(listOf(axis[index / 7] * 3 + sourceX)).single()
        }
        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), paint().copy(color = ColorARGB.Blue))
            drawImageNine(image(), RectF32.ofLTRB(1f, 1f, 1f, 2f), RectF32.ofLTRB(0f, 0f, 7f, 7f), paint())
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(2, result.stats.opsDispatched)
    }

    @Test fun imageNineA8PaintChildAndOrdinaryGradientKeepIndependentCoordinates() {
        val maskBytes = byteArrayOf(-1, -1, -1, -1, 127, -1, -1, -1, -1)
        val mask = Image.fromPixels(3, 3, maskBytes, ColorType.ALPHA_8, alphaType = AlphaType.PREMUL)
        fun gradient(start: Float, left: ColorARGB, right: ColorARGB) = Shader.LinearGradient(
            Point2F32(start, 0f), Point2F32(start + 4f, 0f), listOf(GradientStop(0f, left), GradientStop(.5f, left),
                GradientStop(.5f, right), GradientStop(1f, right)))
        val alphaPaint = ColorARGB.fromRGBA(0f, 1f, 0f, .5f)
        val axis = listOf(0, 1, 1, 2)
        val expected = List(48) { index ->
            val x = index % 12
            val y = index / 12
            if (x in 4..7) expectedLinear(if (x < 6) listOf(0f, 0f, 1f, 1f) else listOf(1f, 1f, 0f, 1f))
            else {
                val localX = x % 4
                val color = if (x >= 8) alphaPaint else if (localX < 2) ColorARGB.fromRGBA(1f, 0f, 0f, .5f)
                    else ColorARGB.fromRGBA(0f, 1f, 0f, .5f)
                bounded(W5eDecodedImageCpuOracle.colorPixel(ColorType.ALPHA_8, AlphaType.PREMUL, ColorSpace.SRGB,
                    byteArrayOf(maskBytes[axis[y] * 3 + axis[localX]]), paintColor = color))
            }
        }
        val surface = Surface(12, 4)
        surface.canvas {
            drawImageNine(mask, center(), RectF32.ofLTRB(0f, 0f, 4f, 4f), paint().copy(color = alphaPaint,
                shader = gradient(0f, ColorARGB.Red, ColorARGB.Green)))
            drawRect(RectF32.ofLTRB(4f, 0f, 8f, 4f), paint().copy(shader = gradient(4f, ColorARGB.Blue, ColorARGB.Yellow)))
            drawImageNine(mask, center(), RectF32.ofLTRB(8f, 0f, 12f, 4f), paint().copy(color = alphaPaint))
        }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test fun imageNineInvalidCentersRefuseAndRecoverOnSameRuntime() {
        for ((invalid, diagnostic) in listOf(
            RectF32.ofLTRB(2f, 1f, 1f, 2f) to "invalid.material.image.nine-center",
            RectF32.ofLTRB(Float.NaN, 1f, 2f, 2f) to "non-finite-value",
            RectF32.ofLTRB(1f, 1f, Float.POSITIVE_INFINITY, 2f) to "non-finite-value")) for (empty in listOf(false, true)) {
            val surface = Surface(3, 3)
            surface.canvas { drawImageNine(image(), invalid, RectF32.ofLTRB(0f, 0f, if (empty) 0f else 3f, 3f), paint()) }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals(diagnostic, failure.message.orEmpty().substringBefore(':'))
            val expected = expectations((0..8).toList())
            val healthy = Surface(3, 3)
            healthy.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
            assertPixels(expected, healthy.render().pixels)
        }
    }

    @Test fun imageNineNumericAndBudgetRefusalsRecoverOnSameRuntime() {
        for (numeric in listOf(false, true)) {
            val surface = Surface(3, 3, config = if (numeric) RenderConfig() else RenderConfig(frameLocalBudgetBytes = 1L))
            surface.canvas {
                if (numeric) concat(Matrix3x3F32(sx = 1.5f, tx = 1f, persp0 = 1f, persp2 = 0f))
                drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint())
            }
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals(if (numeric) "unsupported.material.image.numeric-domain-unbounded" else "resource.material.image.frame-budget",
                failure.message.orEmpty().substringBefore(':'))
            val expected = expectations((0..8).toList())
            val healthy = Surface(3, 3)
            healthy.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
            assertPixels(expected, healthy.render().pixels)
        }
    }

    @Test fun imageNineCaptureRefusalRecoversOnTheSameSurface() {
        val surface = Surface(3, 3, captureLimits = SceneCaptureLimits(maxImageBytesI64 = 36L))
        val oversized = Image.fromPixels(4, 3, ByteArray(48), alphaType = AlphaType.PREMUL)
        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawImageNine(oversized, center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
        }
        assertEquals("scene-recording-image-bytes-exceeded", failure.diagnostic.code.value)
        val expected = expectations((0..8).toList())
        surface.canvas { drawImageNine(image(), center(), RectF32.ofLTRB(0f, 0f, 3f, 3f), paint()) }
        val result = surface.render()
        assertPixels(expected, result.pixels)
        assertEquals(1, result.stats.opsDispatched)
    }

    private fun expectedLinear(values: List<Float>) = bounded(WgslFloatEnvelopeV1Oracle.imageSourceAttachment(
        values.map(I::input).toTypedArray()))
    private fun bounded(value: WgslFloatEnvelopeV1Oracle.DrawResult): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded {
        require(value is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { value.toString() }
        return value
    }

    private fun expectations(indices: List<Int>) = indices.map { index ->
        W5eDecodedImageCpuOracle.colorPixel(ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.LINEAR_SRGB,
            bytes.copyOfRange(index * 4, index * 4 + 4)).also {
            require(it is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { it.toString() }
        } as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
    }

    private fun assertPixels(expected: List<WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>, pixels: UByteArray) {
        assertEquals(expected.size * 4, pixels.size)
        expected.forEachIndexed { index, value ->
            try { WgslFloatEnvelopeV1Oracle.assertAdmits(value, pixels.copyOfRange(index * 4, index * 4 + 4)) }
            catch (failure: IllegalArgumentException) { throw IllegalArgumentException("pixel=$index: ${failure.message}", failure) }
        }
    }
    private fun image() = Image.fromPixels(3, 3, bytes, alphaType = AlphaType.PREMUL, colorSpace = ColorSpace.LINEAR_SRGB)
    private fun center() = RectF32.ofLTRB(1f, 1f, 2f, 2f)
    private fun paint() = Paint(color = ColorARGB.Black, antiAlias = false, blendMode = BlendMode.SRC)
    private val bytes = byteArrayOf(
        -1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1,
        -1, -1, 0, -1, -1, 0, -1, -1, 0, -1, -1, -1,
        0, 0, 0, -1, -1, -1, -1, -1, 127, 127, 127, -1,
    )
}
