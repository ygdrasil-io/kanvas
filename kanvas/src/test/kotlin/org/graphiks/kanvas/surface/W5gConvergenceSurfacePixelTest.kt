@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W5gConvergenceSurfacePixelTest {
    @ParameterizedTest(name = "one captured image across ordinary and composed sources: reversed={0}")
    @CsvSource("false", "true")
    fun sameImageOwnerSharesOneMixedFrameReservationWhileEqualIndependentOwnersDoNot(reversed: Boolean) {
        val pixels = ByteArray(256 * 256 * 4) { indexI32 -> if (indexI32 % 4 >= 2) -1 else 0 }
        val image = Image.fromPixels(256, 256, pixels, sourceId = "mixed-shared-owner", alphaType = AlphaType.OPAQUE)
        // Distinct sourceId preserves a distinct captured owner despite equal bytes.
        val independent = Image.fromPixels(256, 256, pixels.copyOf(),
            sourceId = "mixed-independent-owner", alphaType = AlphaType.OPAQUE)
        val ordinary = Shader.Image(image)
        fun composed(value: Image) = Shader.Blend(BlendMode.SRC_OVER,
            Shader.Image(value), Shader.SolidColor(ColorARGB.Red.withAlpha(128)))
        val sharedShader = composed(image)
        val independentShader = composed(independent)
        val expected = listOf(
            W5fColorCpuOracle.expectedShaderTree(ordinary, devicePointF32 = Point2F32(.5f, .5f)),
            W5fColorCpuOracle.expectedShaderTree(sharedShader, devicePointF32 = Point2F32(1.5f, .5f)))
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        val independentExpected = W5fColorCpuOracle.expectedShaderTree(independentShader,
            devicePointF32 = Point2F32(1.5f, .5f))
        W5fSurfacePixelFixtures.requireBounded(independentExpected)
        assertEquals(channels(expected[1]), channels(independentExpected))
        disjoint(expected[0], expected[1])
        // Complete inventory derived before execution: common25144 bytes;
        // each owner adds262144 texture +262144 staging. One549432; two1073720.
        // 800000 is between those inventories;1200000 admits either control.
        fun frame(shader: Shader, budgetI64: Long) = Surface(2, 1,
            config = RenderConfig(frameLocalBudgetBytes = budgetI64)).also { surface ->
            surface.canvas {
                val shaders = listOf(ordinary, shader)
                val order = if (reversed) shaders.indices.reversed() else shaders.indices
                for (indexI32 in order) drawRect(RectF32.ofLTRB(indexI32.toFloat(), 0f, indexI32 + 1f, 1f),
                    Paint(shader = shaders[indexI32], blendMode = BlendMode.SRC, antiAlias = false))
            }
        }
        val wideShared = frame(sharedShader, 1_200_000)
        val wideIndependent = frame(independentShader, 1_200_000)
        val shared = frame(sharedShader, 800_000)
        val refused = frame(independentShader, 800_000)
        W5fSurfacePixelFixtures.assertNativePixels(wideShared.render(), expected)
        W5fSurfacePixelFixtures.assertNativePixels(wideIndependent.render(), expected)
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { refused.render() }
            assertEquals("resource-limit.w5g.composed-binding", failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(wideShared.render(), expected)
            W5fSurfacePixelFixtures.assertNativePixels(shared.render(), expected)
        }
    }

    @ParameterizedTest(name = "separate unpromoted boundary: {0}")
    @CsvSource("stroke,unsupported.material.composed.slice",
        "aa4,w4d.general.texture-sample-support-unavailable",
        "spatial,unsupported.image.native_binding",
        "runtime,unsupported.material.composed.slice")
    fun unpromotedBoundariesRemainSeparateFromPositiveCells(kind: String, code: String) {
        val shader = Shader.Blend(BlendMode.SRC_OVER,
            Shader.SolidColor(ColorARGB.Red.withAlpha(128)), Shader.SolidColor(ColorARGB.Blue.withAlpha(128)))
        val expected = W5fColorCpuOracle.expectedShaderTree(shader)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val healthy = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false))
        } }
        // Public handle-free recording installs compilation; no registry injection
        // or runtime admission. The actual control still ends at Surface.render().
        val refusedShader = if (kind == "runtime") Shader.Blend(BlendMode.SRC_OVER, shader,
            SceneRecordingScope.recordingOnly {
                RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0, 0.0, 0.0, 1.0); }")
                    .getOrThrow().makeShader(UniformBlock.EMPTY)
            }) else shader
        val refused = Surface(1, 1).also { surface -> surface.canvas {
            val paint = Paint(shader = refusedShader, blendMode = BlendMode.SRC, antiAlias = false)
            when (kind) {
                "stroke" -> drawPath(directPath(), paint.copy(style = PaintStyle.STROKE, strokeWidth = 2f))
                "aa4" -> {
                    concat(Matrix3x3F32.skewing(.25f, 0f))
                    drawPath(directPath(), paint.copy(antiAlias = true, blendMode = BlendMode.DIFFERENCE))
                }
                "spatial" -> {
                    drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint)
                    drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                        shader = Shader.Image(Image.fromPixels(1, 1, byteArrayOf(0, -1, 0, -1))),
                        maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
                }
                else -> drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint)
            }
        } }
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { refused.render() }
            assertEquals(code, failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(), listOf(expected))
        }
    }

    @ParameterizedTest(name = "whole-frame Noise work: route={0}, reversed={1}")
    @CsvSource("0,false", "0,true", "1,false", "1,true", "2,false", "2,true")
    fun childPreparationCannotResetEarlierOrLaterNoiseWorkAndRecoveryRepeats(
        routeI32: Int, reversed: Boolean,
    ) {
        val first = Shader.PerlinNoise(.125f, .25f, 2, 7, null)
        val image = Shader.Image(Image.fromPixels(1, 1, byteArrayOf(0, -1, 0, -1)))
        val gradient = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            List(17) { GradientStop(it / 16f,
                if (it < 8) ColorARGB.Red.withAlpha(128) else ColorARGB.Blue.withAlpha(128)) })
        val images = Shader.Blend(BlendMode.SRC_OVER,
            Shader.Image(Image.fromPixels(1, 1, byteArrayOf(-1, 0, 0, -128))),
            Shader.Image(Image.fromPixels(1, 1, byteArrayOf(0, 0, -1, -128))))
        val last = Shader.WithLocalMatrix(Shader.Blend(BlendMode.SRC_OVER,
            Shader.Blend(BlendMode.SRC_OVER, gradient, images),
            Shader.FractalNoise(.125f, .25f, 2, 7, null)), Matrix3x3F32(tx = 2f))
        val shaders = listOf(first, image, last)
        val expected = shaders.mapIndexed { indexI32, shader ->
            W5fColorCpuOracle.expectedShaderTree(shader,
                devicePointF32 = Point2F32(indexI32 + .5f, .5f))
                .also(W5fSurfacePixelFixtures::requireBounded)
        }
        // Each Noise draw has target-clipped bounds1x1 and one two-octave leaf:
        // 1*2*4 + 0(image) + 1*2*4 =16, in either submission order. The late
        // source also prepares17 stops and two images but cannot reset that sum.
        fun frame(limitI64: Long) = Surface(3, 1,
            config = RenderConfig(maxNoiseOctaveEvaluationsI64 = limitI64)).also { surface ->
            surface.canvas {
                val order = if (reversed) shaders.indices.reversed() else shaders.indices
                for (indexI32 in order) {
                    val paint = Paint(shader = shaders[indexI32], blendMode = BlendMode.SRC, antiAlias = false)
                    if (indexI32 == 1 || routeI32 == 0)
                        drawRect(RectF32.ofLTRB(indexI32.toFloat(), 0f, indexI32 + 1f, 1f), paint)
                    else drawPath(cellPath(indexI32, routeI32 == 2), paint)
                }
            }
        }
        val healthy = frame(16)
        val refused = frame(15)
        repeat(2) {
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(), expected)
            val failure = assertFailsWith<IllegalStateException> { refused.render() }
            assertEquals("budget.material.noise.octave-evaluations", failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(), expected)
        }
    }

    @ParameterizedTest(name = "unfiltered legacy image plus V5: family={0}, route={1}, imageFirst={2}")
    @CsvSource("0,0,false", "0,0,true", "0,1,false", "0,1,true", "0,2,false", "0,2,true",
        "1,0,false", "1,0,true", "1,1,false", "1,1,true", "1,2,false", "1,2,true",
        "2,0,false", "2,0,true", "2,1,false", "2,1,true", "2,2,false", "2,2,true")
    fun unfilteredImageAndComposedSourcesShareTheDeferredFrameInBothOrders(
        familyI32: Int, routeI32: Int, imageFirst: Boolean,
    ) {
        val leaf = when (familyI32) {
            0 -> Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Red.withAlpha(128)),
                Shader.SolidColor(ColorARGB.Blue.withAlpha(128)))
            1 -> Shader.PerlinNoise(.125f, .25f, 2, 7, null)
            else -> Shader.FractalNoise(.125f, .25f, 2, 7, null)
        }
        val shader = Shader.WithLocalMatrix(leaf, Matrix3x3F32(tx = 1f))
        val image = Shader.Image(Image.fromPixels(1, 1, byteArrayOf(0, -1, 0, -1)))
        val expected = listOf(W5fColorCpuOracle.expectedShaderTree(image),
            W5fColorCpuOracle.expectedShaderTree(shader, devicePointF32 = Point2F32(1.5f, .5f)))
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        val surface = Surface(2, 1)
        surface.canvas {
            fun drawImageCell() = drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(shader = image, blendMode = BlendMode.SRC, antiAlias = false))
            fun drawComposedCell() {
                val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
                if (routeI32 == 0) drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), paint)
                else drawPath(cellPath(1, routeI32 == 2), paint)
            }
            if (imageFirst) { drawImageCell(); drawComposedCell() }
            else { drawComposedCell(); drawImageCell() }
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), expected) }
    }

    @Test fun mixedOldSourcesAndAllComposedResourcesRetainOrderAndCapturedPayloadsThroughPictures() {
        val stops = MutableList(17) { indexI32 -> GradientStop(indexI32 / 16f,
            if (indexI32 < 8) ColorARGB.Red.withAlpha(128) else ColorARGB.Blue.withAlpha(128)) }
        val firstPixels = byteArrayOf(-1, 0, 0, -128)
        val secondPixels = byteArrayOf(0, 0, -1, -128)
        val matrix = ColorMatrixF32.ofIdentity().apply { setScale(.75f, 1f, 1f, 1f) }
        val filter = ColorFilter.Matrix(matrix)
        fun tree(values: List<GradientStop>, first: ByteArray, second: ByteArray, reverse: Boolean = false): Shader {
            val gradient = Shader.WithLocalMatrix(Shader.LinearGradient(Point2F32(0f, 0f),
                Point2F32(1f, 0f), values), Matrix3x3F32(tx = .25f))
            val images = Shader.Blend(BlendMode.SRC_OVER,
                Shader.Opacity(Shader.Image(Image.fromPixels(1, 1, first)), .5f),
                Shader.Opacity(Shader.Image(Image.fromPixels(1, 1, second)), .25f))
            val noise = Shader.Blend(BlendMode.SRC_OVER,
                Shader.Opacity(Shader.PerlinNoise(.125f, .25f, 2, 7, null), .5f),
                Shader.Opacity(Shader.FractalNoise(.125f, .25f, 2, 7, null), .5f))
            val resources = Shader.Blend(BlendMode.SRC_OVER, gradient, images)
            return if (reverse) Shader.Blend(BlendMode.SRC_OVER, noise, resources)
                else Shader.Blend(BlendMode.SRC_OVER, resources, noise)
        }
        val shader = tree(stops, firstPixels, secondPixels)
        val blue = ColorARGB.Blue
        val oldGradient = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(8f, 0f),
            listOf(GradientStop(0f, blue), GradientStop(1f, blue)))
        val oldSources = listOf(
            Shader.SolidColor(blue), Shader.Opacity(Shader.SolidColor(ColorARGB.Blue), 1f),
            oldGradient, Shader.WithLocalMatrix(oldGradient, Matrix3x3F32(tx = .25f)),
            Shader.Image(Image.fromPixels(1, 1, byteArrayOf(0, 0, -1, -1))),
            Shader.WithColorFilter(Shader.SolidColor(blue), ColorFilter.Matrix(ColorMatrixF32.ofIdentity())))
        val oldExpected = oldSources.mapIndexed { indexI32, old ->
            W5fColorCpuOracle.expectedShaderTree(old, devicePointF32 = Point2F32(indexI32 + .5f, .5f))
                .also(W5fSurfacePixelFixtures::requireBounded)
        }
        val blueExpected = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(blue))
        W5fSurfacePixelFixtures.requireBounded(blueExpected)
        // SRC_IN observes only destination alpha, whose independently verified
        // stored set is identical for all old lanes. RGB envelopes may differ.
        oldExpected.forEach { assertEquals(channels(blueExpected)[3], channels(it)[3]) }
        fun expected(value: Shader, colorFilter: ColorFilter = filter, destination: ColorARGB = blue) =
            W5fColorCpuOracle.expectedShaderTree(value, 127f / 255f, colorFilter,
                destination, BlendMode.SRC_IN).also(W5fSurfacePixelFixtures::requireBounded)
        val wanted = expected(shader)
        val changedStops = List(17) { GradientStop(it / 16f, ColorARGB.Green) }
        val changedPixels = byteArrayOf(0, -1, 0, -1)
        disjoint(wanted, expected(tree(changedStops, firstPixels, secondPixels)))
        disjoint(wanted, expected(tree(stops, changedPixels, secondPixels)))
        disjoint(wanted, expected(tree(stops, firstPixels, changedPixels)))
        disjoint(wanted, expected(tree(stops, firstPixels, secondPixels, reverse = true)))
        disjoint(wanted, expected(shader, ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply {
            setScale(.125f, 1f, 1f, 1f)
        })))
        disjoint(wanted, expected(shader, destination = ColorARGB.Transparent))
        val paints = List(6) { indexI32 -> Paint(
            shader = Shader.WithLocalMatrix(shader, Matrix3x3F32(tx = indexI32.toFloat())),
            color = ColorARGB.of(127, 255, 255, 255), colorFilter = filter,
            blendMode = BlendMode.SRC_IN, antiAlias = false) }
        val expectedPixels = paints.mapIndexed { indexI32, paint ->
            W5fColorCpuOracle.expectedShaderTree(requireNotNull(paint.shader), 127f / 255f,
                filter, blue, BlendMode.SRC_IN, Point2F32(indexI32 + .5f, .5f))
                .also(W5fSurfacePixelFixtures::requireBounded)
        }
        // Whole-frame oracle: clear; old draw i writes blue to cell i; the
        // following composed draw i reads its stored alpha. Other cells keep
        // their previous attachment; all six final centers are asserted.
        fun record(canvas: Canvas) { oldSources.forEachIndexed { indexI32, old ->
            canvas.drawRect(RectF32.ofLTRB(indexI32.toFloat(), 0f, indexI32 + 1f, 1f),
                Paint(shader = old, blendMode = BlendMode.SRC, antiAlias = false))
            val paint = paints[indexI32]
            if (indexI32 % 3 == 0) canvas.drawRect(RectF32.ofLTRB(indexI32.toFloat(), 0f, indexI32 + 1f, 1f), paint)
            else canvas.drawPath(cellPath(indexI32, indexI32 % 3 == 2), paint)
        } }
        val surface = Surface(6, 1, config = RenderConfig(maxNoiseOctaveEvaluationsI64 = 96))
        surface.canvas { record(this) }
        val recorder = PictureRecorder()
        record(recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 6f, 1f)))
        val picture = recorder.finishRecordingAsPicture()
        stops.indices.forEach { stops[it] = changedStops[it] }
        changedPixels.copyInto(firstPixels); changedPixels.copyInto(secondPixels)
        matrix.setScale(.125f, 1f, 1f, 1f)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), expectedPixels) }
        val decoded = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        for (replay in listOf(picture, decoded)) {
            val target = Surface(6, 1, config = RenderConfig(maxNoiseOctaveEvaluationsI64 = 96))
            target.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(), expectedPixels) }
        }
    }

    @Test fun sharedSeedStorageFitsWhileDistinctTablesRefuseAndNativeRecoveryRepeats() {
        fun paint(indexI32: Int, distinct: Boolean) = Paint(
            shader = Shader.FractalNoise(.001953125f, .25f, 2, if (distinct) indexI32 + 1 else 7, null),
            // Equal 80-byte external-filter topology in both controls. Every source
            // stays distinct even when its Noise seed is shared; clamp makes red exact.
            colorFilter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
                0f, 0f, 0f, 0f, 2f + indexI32 / 128f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 1f))),
            blendMode = BlendMode.SRC, antiAlias = false)
        val controls = listOf(false, true).map { distinct -> List(64) { paint(it, distinct) } }
        // Every input and every device center is independently bounded before capture.
        val expected = controls.map { paints -> paints.map { paint -> List(29) { xI32 ->
            W5gNoiseCpuOracle.expected(requireNotNull(paint.shader), Point2F32(xI32 + .5f, .5f),
                external = paint.colorFilter).also(W5fSurfacePixelFixtures::requireBounded)
        } } }
        // Audited before execution: shared total1,590,900; distinct nonuniform
        // prefix1,851,764, total1,865,076. Seed1..64 are already normalized,
        // so their only physical difference is63*4352=274176 table bytes.
        fun frame(paints: List<Paint>) = Surface(29, 1,
            config = RenderConfig(frameLocalBudgetBytes = 1_700_000,
                maxNoiseOctaveEvaluationsI64 = 64L * 29L * 2L * 4L)).also { surface ->
            surface.canvas { paints.forEachIndexed { indexI32, paint ->
                if (indexI32 % 2 == 0) drawRect(RectF32.ofLTRB(0f, 0f, 29f, 1f), paint)
                else drawPath(directPath(), paint)
            } }
        }
        val shared = frame(controls[0])
        val distinct = frame(controls[1])
        repeat(2) {
            W5fSurfacePixelFixtures.assertNativePixels(shared.render(), expected[0].last())
            val failure = assertFailsWith<IllegalStateException> { distinct.render() }
            assertEquals("budget.material.noise.storage", failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(shared.render(), expected[0].last())
        }
    }

    @Test fun workBudgetChargesBothGeometryConsumersAndRecovers() {
        val shader = Shader.FractalNoise(.125f, .25f, 2, 7, null)
        val expected = W5gNoiseCpuOracle.expected(shader, Point2F32(.5f, .5f))
        W5fSurfacePixelFixtures.requireBounded(expected)
        // Each target-clipped draw costs 1 pixel * 2 requested octaves * 4 channels.
        // Shared seed/table bytes must not erase the second geometry consumer's work.
        fun frame(limitI64: Long) = Surface(1, 1,
            config = RenderConfig(maxNoiseOctaveEvaluationsI64 = limitI64)).also { surface ->
            surface.canvas {
                val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint)
                drawPath(directPath(), paint)
            }
        }
        val healthy = frame(16)
        val refused = frame(15)
        W5fSurfacePixelFixtures.assertNativePixels(healthy.render(), listOf(expected))
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { refused.render() }
            assertEquals("budget.material.noise.octave-evaluations", failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(), listOf(expected))
        }
    }

    @Test fun invalidNoiseCaptureThenValidMixedAppendKeepsTheSameRecordingOwnerUsable() {
        val valid = Shader.PerlinNoise(.125f, .25f, 2, 7, null)
        val expected = W5gNoiseCpuOracle.expected(valid, Point2F32(.5f, .5f))
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1, 1, config = RenderConfig(maxNoiseOctaveEvaluationsI64 = 16))
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f),
                Paint(shader = valid, blendMode = BlendMode.SRC, antiAlias = false))
        }
        val failure = assertFailsWith<IllegalArgumentException> {
            surface.canvas {
                drawPath(directPath(), Paint(shader = Shader.Blend(BlendMode.SRC_OVER,
                    valid, Shader.FractalNoise(.125f, .25f, 256, 7, null)),
                    blendMode = BlendMode.SRC, antiAlias = false))
            }
        }
        assertEquals("invalid.material.noise.parameters", failure.message.orEmpty().substringBefore(':'))
        surface.canvas {
            drawPath(directPath(), Paint(shader = valid, blendMode = BlendMode.SRC, antiAlias = false))
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected)) }
    }

    private fun directPath() = Path().apply {
        moveTo(-10f, -10f); lineTo(40f, -10f); lineTo(-10f, 40f); close()
    }

    private fun cellPath(indexI32: Int, stencil: Boolean) = Path().apply {
        val xF32 = indexI32.toFloat()
        moveTo(xF32, 0f); lineTo(xF32 + 1f, 0f)
        if (stencil) {
            lineTo(xF32 + 1f, 1f); lineTo(xF32 + .5f, .75f); lineTo(xF32, 1f)
        } else lineTo(xF32 + .5f, 1f)
        close()
    }

    private fun channels(value: WgslFloatEnvelopeV1Oracle.DrawResult) =
        (value as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels

    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult, b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        assertTrue(channels(a).indices.any { channels(a)[it].intersect(channels(b)[it]).isEmpty() })
    }
}
