@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SceneRecordingLimitException
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class W5cGradientSurfacePixelTest {
    @Test
    fun linearBiaxialHardStopConsumesRoundedPreflightLength() {
        val startF32 = Point2F32(0f, .5f)
        val endF32 = Point2F32(.5453823208808899f, 1.242221713066101f)
        // W5 §7.2 rounds every operation before the next. Fusing either
        // square into the sum instead produces 0.8483349680900574f.
        val linearDxF32 = endF32.x - startF32.x
        val linearDyF32 = endF32.y - startF32.y
        val linearX2F32 = linearDxF32 * linearDxF32
        val linearY2F32 = linearDyF32 * linearDyF32
        val linearLen2F32 = linearX2F32 + linearY2F32
        val linearLengthF32 = kotlin.math.sqrt(linearLen2F32)
        val linearDegenerate = linearLengthF32 <= .000030517578125f
        val localPointF32 = Point2F32(.5f, .5f)
        val pxF32 = localPointF32.x - startF32.x
        val pyF32 = localPointF32.y - startF32.y
        val dotXF32 = pxF32 * linearDxF32
        val dotYF32 = pyF32 * linearDyF32
        val dotF32 = dotXF32 + dotYF32
        val tF32 = if (linearDegenerate) 1f else (dotF32 / linearLen2F32).coerceIn(0f, 1f)
        val hardStopF32 = .3214428126811981f
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(hardStopF32, ColorARGB.Red),
            GradientStop(hardStopF32, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))
        // Independent upper_bound selection. The selected constant-color span
        // needs no interpolation; opaque SRC_OVER on transparent preserves it.
        val selected = stops.indexOfLast { it.position <= tF32 }
        val expectedColor = stops[selected].color
        val expected = ubyteArrayOf(expectedColor.red.toUByte(), expectedColor.green.toUByte(),
            expectedColor.blue.toUByte(), expectedColor.alpha.toUByte())
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(
                shader = Shader.LinearGradient(startF32, endF32, stops), antiAlias = false))
        }
        assertContentEquals(expected, surface.render().pixels,
            "The biaxial hard stop must use the immediately rounded preflight length squared")
    }

    @Test
    fun mixedGradientFramePreservesOrderRangesOpacityAndBlend() {
        val reused = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))
        val distinct = List(17) { indexI32 -> GradientStop(
            if (indexI32 in 8..9) .5f else indexI32 / 16f,
            if (indexI32 <= 8) ColorARGB.Green else ColorARGB.White) }
        val shaders = listOf(
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(10f, 0f), reused),
            Shader.Opacity(Shader.RadialGradient(Point2F32(0f, 3f), 10f, distinct), .5f),
            Shader.SweepGradient(Point2F32(5f, 3f), 0f, 360f, reused),
            Shader.ConicalGradient(Point2F32(0f, 3f), 4f, Point2F32(16f, 3f), 4f, distinct),
        )
        fun frame(reversed: Boolean): UByteArray {
            val surface = Surface(13, 19)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 13f, 19f), Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
                for (laneI32 in if (reversed) (3 downTo 0) else (0..3)) {
                    save()
                    translate(0f, laneI32 * 4f)
                    val paint = Paint(shader = shaders[laneI32], antiAlias = false,
                        blendMode = if (laneI32 == 1) BlendMode.DIFFERENCE else BlendMode.SRC_OVER)
                    when (laneI32) {
                        0 -> drawRect(RectF32.ofLTRB(0f, 0f, 10f, 6f), paint)
                        1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 6f), CornerRadiiF32.of(.5f)),
                            paint.copy(antiAlias = true))
                        2 -> drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 10f, 6f)) }, paint)
                        3 -> drawPath(Path().apply { moveTo(0f, 3f); lineTo(10f, 3f) },
                            paint.copy(style = PaintStyle.STROKE, strokeWidth = 6f))
                    }
                    restore()
                }
                drawRect(RectF32.ofLTRB(11f, 18f, 13f, 19f), Paint(shader = Shader.SolidColor(ColorARGB.Black), antiAlias = false))
            }
            return surface.render().pixels
        }
        val forward = frame(false)
        val reversed = frame(true)
        val background = W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER)
        val uniqueSources = listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Green, .5f, BlendMode.DIFFERENCE),
            W5bBlendCpuOracle.Draw(ColorARGB.Blue, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Green, 1f, BlendMode.SRC_OVER),
        )
        fun sample(pixels: UByteArray, pixelYI32: Int): UByteArray {
            val offsetI32 = (pixelYI32 * 13 + 2) * 4
            return pixels.copyOfRange(offsetI32, offsetI32 + 4)
        }
        // At x=2.5, y=4*lane+2.5 each middle draw has an uncovered witness.
        // All parameters are strictly inside constant-color stop segments.
        uniqueSources.forEachIndexed { laneI32, source ->
            val expected = W5bBlendCpuOracle.mixedPixel(listOf(background to 1f, source to 1f))
            for (pixels in listOf(forward, reversed)) {
                val actual = sample(pixels, laneI32 * 4 + 2)
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, actual)
                assertTrue(!actual.contentEquals(ubyteArrayOf(255u, 255u, 255u, 255u)), "Omitted lane $laneI32")
            }
        }
        // Every adjacent pair overlaps; reversing order changes every witness.
        // Sweep is red below its center and blue above it at this x.
        for (laneI32 in 1..3) {
            val preceding = if (laneI32 == 3) uniqueSources[2].copy(color = ColorARGB.Red) else uniqueSources[laneI32 - 1]
            val current = uniqueSources[laneI32]
            val expected = W5bBlendCpuOracle.mixedPixel(listOf(background to 1f, preceding to 1f, current to 1f))
            val counterfactual = W5bBlendCpuOracle.mixedPixel(listOf(background to 1f, current to 1f, preceding to 1f))
            val actual = sample(forward, laneI32 * 4)
            val reordered = sample(reversed, laneI32 * 4)
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, actual)
            WgslFloatEnvelopeV1Oracle.assertAdmits(counterfactual, reordered)
            assertTrue(!actual.contentEquals(reordered), "Order must remain observable at lane $laneI32")
        }
        for (pixels in listOf(forward, reversed)) {
            assertContentEquals(ubyteArrayOf(255u, 255u, 255u, 255u), pixels.copyOfRange(48, 52))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 255u), pixels.copyOfRange((13 * 19 - 1) * 4, 13 * 19 * 4))
        }
    }

    @Test
    fun gradientFrameBudgetRefusesThenRuntimeRecovers() {
        val stops = List(257) { indexI32 -> GradientStop(indexI32 / 256f, ColorARGB.Blue) }
        fun frame(budgetI64: Long) = Surface(13, 1, config = RenderConfig(frameLocalBudgetBytes = budgetI64)).also { surface ->
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 13f, 1f), Paint(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(13f, 0f), stops), antiAlias = false)) }
        }
        val healthy = frame(1L shl 20)
        val expected = UByteArray(13 * 4) { indexI32 -> if (indexI32 % 4 >= 2) 255u else 0u }
        assertContentEquals(expected, healthy.render().pixels)
        val failure = assertThrows<IllegalStateException> { frame(4096L).render() }
        assertEquals("resource.material.gradient.stop-budget", failure.message.orEmpty().substringBefore(':'))
        // Surface keeps its append-only recording and immutable RenderConfig. The
        // valid Surface therefore differs, but the runtime/backend is never reset.
        assertContentEquals(expected, healthy.render().pixels)
    }

    @Test
    fun authenticStorageCapabilityEitherRendersOrRefusesTyped() {
        val surface = Surface(7, 1, config = RenderConfig(frameLocalBudgetBytes = 1L shl 20))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 7f, 1f), Paint(shader = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(7f, 0f), List(17) { indexI32 ->
                GradientStop(indexI32 / 16f, ColorARGB.Blue)
            }), antiAlias = false)) }
        // Only the public render queries the production adapter. No test capability
        // snapshot is supplied, and unrelated/native failures cannot satisfy this gate.
        val result = try { surface.render() } catch (failure: IllegalStateException) {
            // This tiny frame has ample software budget. The second code can
            // therefore only express a real physical buffer/binding-size limit.
            assertTrue(failure.message.orEmpty().substringBefore(':') in setOf(
                "unsupported.material.gradient.storage-capability",
                "resource.material.gradient.stop-budget",
            ), failure.message)
            println("W5c authentic storage capability: typed refusal (${failure.message})")
            return
        }
        assertContentEquals(UByteArray(7 * 4) { indexI32 -> if (indexI32 % 4 >= 2) 255u else 0u }, result.pixels)
        println("W5c authentic storage capability: exact pixels rendered")
    }

    @Test
    fun conicalGradientCoversFourLanesAndSelectsLargestValidRoot() {
        val stops = List(17) { indexI32 -> GradientStop(
            if (indexI32 in 8..9) .5f else indexI32 / 16f,
            if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue) }
        // Equal radii give two positive-radius roots (x-3)/8 and (x+1)/8.
        // At x=5 the smaller root is red and the required larger root is blue.
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 2f,
            Point2F32(9f, 1f), 2f, stops), listOf(2f to ColorARGB.Red, 5f to ColorARGB.Blue, 7f to ColorARGB.Blue))
        // Negative A reverses the +/- formula ordering; only the positive-radius root survives.
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 0f,
            Point2F32(3f, 1f), 4f, stops), listOf(2f to ColorARGB.Red, 7f to ColorARGB.Blue))
        // Off-axis sqrt(3) and interpolation pass through the common opacity/blend tail.
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 2f), 2f,
            Point2F32(9f, 2f), 2f, listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White))),
            listOf(2f to null), opacityF32 = 32f / 255f, blend = BlendMode.DIFFERENCE)
    }

    @Test
    fun conicalGradientMasksFragmentsWithoutValidRoot() {
        val singleton = listOf(GradientStop(.7f, ColorARGB.Green))
        // Outside the strip |x-4|<=2 the discriminant is negative. One stop
        // must keep this mask; reducing the source to Solid would fill white pixels.
        assertConicalLanes(Shader.ConicalGradient(Point2F32(4f, 1f), 2f,
            Point2F32(4f, 9f), 2f, singleton),
            listOf(1f to ColorARGB.White, 4f to ColorARGB.Green, 7f to ColorARGB.White))
        // Behind an expanding cone both finite roots have a negative radius.
        assertConicalLanes(Shader.ConicalGradient(Point2F32(4f, 1f), 0f,
            Point2F32(12f, 1f), 2f, singleton),
            listOf(1f to ColorARGB.White, 7f to ColorARGB.Green))
    }

    @Test
    fun conicalGradientCoversAllDegeneracyBranches() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 1f,
            Point2F32(5f, 1f), 5f, stops), listOf(2f to ColorARGB.Red, 7f to ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 0f,
            Point2F32(5f, 1f), 4f, stops), listOf(1f to ColorARGB.White, 7f to ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 2f,
            Point2F32(1f, 1f), 6f, stops), listOf(2f to ColorARGB.Red, 7f to ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 6f,
            Point2F32(1f, 1f), 2f, stops), listOf(2f to ColorARGB.Blue, 7f to ColorARGB.Red))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 0f,
            Point2F32(1f, 1f), 4f, stops), listOf(1f to ColorARGB.White, 7f to ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 2f,
            Point2F32(1f, 1f), 2f, stops),
            listOf(2f to ColorARGB.Red, 3f to ColorARGB.Blue, 7f to ColorARGB.Blue))
        assertConicalLanes(Shader.ConicalGradient(Point2F32(1f, 1f), 0f,
            Point2F32(1f, 1f), 0f, stops), listOf(1f to ColorARGB.Blue, 7f to ColorARGB.Blue))
        for ((startRadiusF32, endRadiusF32) in listOf(-1f to 2f, 2f to -1f,
            Float.NaN to 2f, 2f to Float.POSITIVE_INFINITY)) {
            val invalid = Surface(39, 43)
            invalid.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 39f, 43f), Paint(shader = Shader.ConicalGradient(
                Point2F32(1f, 1f), startRadiusF32, Point2F32(9f, 1f), endRadiusF32, stops), antiAlias = false)) }
            val failure = assertThrows<IllegalStateException> { invalid.render() }
            assertEquals(if (startRadiusF32 < 0f || endRadiusF32 < 0f) "unsupported.material.gradient.negative_radius"
                else "non-finite-value", failure.message.orEmpty().substringBefore(':'))
        }
        val outsideDomain = Surface(41, 1)
        outsideDomain.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 41f, 1f), Paint(shader = Shader.ConicalGradient(
            Point2F32(0f, 0f), 1e20f, Point2F32(1f, 0f), 1e20f, stops), antiAlias = false)) }
        assertInstanceOf(SceneCaptureResult.Captured::class.java, outsideDomain.snapshotScene())
        val failure = assertThrows<IllegalStateException> { outsideDomain.render() }
        assertEquals("unsupported.material.gradient.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
    }

    private fun assertConicalLanes(shader: Shader.ConicalGradient, samples: List<Pair<Float, ColorARGB?>>,
        opacityF32: Float = 1f, blend: BlendMode = BlendMode.SRC_OVER) {
        val widthI32 = if (blend == BlendMode.SRC_OVER) 38 else 40
        val surface = Surface(widthI32, 44)
        surface.canvas {
            // Distinct preceding range exercises the common frame slab and rebase.
            drawRect(RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), 44f), Paint(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(widthI32.toFloat(), 0f), listOf(GradientStop(0f, ColorARGB.White),
                    GradientStop(1f, ColorARGB.White))), antiAlias = false))
            repeat(4) { laneI32 ->
                save()
                concat(Matrix3x3F32.translation(.5f, laneI32 * 10f + .5f) * Matrix3x3F32.scaling(2f, 2f))
                val paint = Paint(shader = Shader.Opacity(shader, opacityF32), blendMode = blend, antiAlias = false)
                when (laneI32) {
                    0 -> drawRect(RectF32.ofLTRB(-.25f, -.25f, 10.25f, 3.25f), paint)
                    1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 3f), CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
                    2 -> drawPath(Path().apply { moveTo(-1f, -1f); lineTo(14f, -1f); lineTo(-1f, 7f); close() }, paint)
                    3 -> drawPath(Path().apply { moveTo(-1f, 1f); lineTo(11f, 1f) }, paint.copy(style = PaintStyle.STROKE, strokeWidth = 1f))
                }
                restore()
            }
        }
        val pixels = surface.render().pixels
        repeat(4) { laneI32 -> samples.forEach { (localXF32, color) ->
            val offsetI32 = ((laneI32 * 10 + 2) * widthI32 + (localXF32 * 2).toInt()) * 4
            if (color != null) assertContentEquals(ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), 255u),
                pixels.copyOfRange(offsetI32, offsetI32 + 4), "Conical lane=$laneI32 x=$localXF32 shader=$shader")
            val expected = W5cGradientCpuOracle.conicalClampSrgb(Point2F32(localXF32, 1f), shader.start, shader.startRadius,
                shader.end, shader.endRadius, shader.stops).thenBlend(
                W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER), blend, opacityF32)
            require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "Conical lane=$laneI32 x=$localXF32: $expected" }
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
        } }
        assertContentEquals(ubyteArrayOf(255u, 255u, 255u, 255u), pixels.copyOfRange((44 * widthI32 - 1) * 4, 44 * widthI32 * 4))
    }

    @Test
    fun sweepGradientUsesClockwiseScreenAnglesOnFourLanes() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red),
            GradientStop(.25f, ColorARGB.Red), GradientStop(.25f, ColorARGB.Green),
            GradientStop(.5f, ColorARGB.Green), GradientStop(.5f, ColorARGB.Blue),
            GradientStop(.75f, ColorARGB.Blue), GradientStop(.75f, ColorARGB.White), GradientStop(1f, ColorARGB.White))
        assertSweepLanes(0f, 360f, stops, listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.White), generic = true)
        val hardStops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))
        assertSweepLanes(90f, 270f, hardStops, listOf(ColorARGB.Red, ColorARGB.Red, ColorARGB.Blue, ColorARGB.Blue), generic = true)
        // Coverage extending beyond a revolution still maps through its declared span.
        assertSweepLanes(-90f, 450f, hardStops, listOf(ColorARGB.Red, ColorARGB.Red, ColorARGB.Blue, ColorARGB.Blue), generic = true)
        assertSweepLanes(0f, 360f, listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White)),
            null, opacityF32 = 32f / 255f, blend = BlendMode.DIFFERENCE,
            samplePointsI32 = listOf(1 to 4, 7 to 6))
    }

    @Test
    fun sweepGradientHandlesSpanBoundariesAndDegeneracy() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        assertSweepLanes(180f, 180f, stops, listOf(ColorARGB.Red, ColorARGB.Red, ColorARGB.Blue, ColorARGB.Blue))
        val epsilonF32 = 0.000030517578125f
        assertSweepLanes(90f - epsilonF32, 90f, stops,
            listOf(ColorARGB.Red, ColorARGB.Blue, ColorARGB.Blue, ColorARGB.Blue))
        for (spanF32 in listOf(0f, Math.nextDown(epsilonF32), epsilonF32))
            assertSweepLanes(0f, spanF32, stops, List(4) { ColorARGB.Blue })
        // A constant first segment proves the epsilon branch without making the
        // near-zero interpolation/transfer envelope cross several output codes.
        val epsilonStops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))
        assertSweepLanes(0f, Math.nextUp(epsilonF32), epsilonStops,
            listOf(ColorARGB.Red, ColorARGB.Blue, ColorARGB.Blue, ColorARGB.Blue))
        for ((startF32, endF32) in listOf(180f to 90f, Float.NaN to 360f, 0f to Float.POSITIVE_INFINITY)) {
            repeat(4) { laneI32 ->
                val invalid = Surface(35, 77)
                invalid.canvas {
                    concat(Matrix3x3F32.translation(.5f, .5f) * Matrix3x3F32.scaling(2f, 2f))
                    drawSweepLane(laneI32, Paint(shader = Shader.SweepGradient(
                        Point2F32(4f, 4f), startF32, endF32, stops), antiAlias = false))
                }
                val failure = assertThrows<IllegalStateException> { invalid.render() }
                assertEquals(if (startF32.isFinite() && endF32.isFinite()) "unsupported.material.gradient.sweep_ordering"
                    else "non-finite-value", failure.message.orEmpty().substringBefore(':'))
            }
        }
    }

    private fun assertSweepLanes(startF32: Float, endF32: Float, stops: List<GradientStop>, colors: List<ColorARGB>?,
        generic: Boolean = false, opacityF32: Float = 1f, blend: BlendMode = BlendMode.SRC_OVER,
        samplePointsI32: List<Pair<Int, Int>>? = null) {
        val surface = Surface(34, 76)
        surface.canvas {
            // A distinct Linear range makes Sweep rebase/reseal into the common frame slab.
            drawRect(RectF32.ofLTRB(0f, 0f, 34f, 76f), Paint(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(34f, 0f), listOf(GradientStop(0f, ColorARGB.White),
                    GradientStop(1f, ColorARGB.White))), antiAlias = false))
            repeat(4) { laneI32 ->
                save()
                concat(Matrix3x3F32.translation(.5f, laneI32 * 19f + .5f) * Matrix3x3F32.scaling(2f, 2f))
                val paint = Paint(shader = Shader.Opacity(Shader.SweepGradient(Point2F32(4f, 4f), startF32, endF32, stops),
                    opacityF32), blendMode = blend, antiAlias = false)
                drawSweepLane(laneI32, paint)
                restore()
            }
        }
        val pixels = surface.render().pixels
        repeat(4) { laneI32 ->
            val samples = samplePointsI32 ?: (listOf(7 to 4, 4 to 7, 1 to 4, 4 to 1) +
                if (generic) listOf(7 to 5, 3 to 7, 1 to 3, 5 to 1) else emptyList())
            samples.forEachIndexed { angleI32, (xI32, yI32) ->
                val color = colors?.get(angleI32 % 4)
                val offsetI32 = ((laneI32 * 19 + yI32 * 2) * 34 + xI32 * 2) * 4
                if (color != null) assertContentEquals(ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), 255u),
                    pixels.copyOfRange(offsetI32, offsetI32 + 4), "lane=$laneI32 angle=${angleI32 * 90} span=$startF32..$endF32")
                val expected = W5cGradientCpuOracle.sweepClampSrgb(Point2F32(xI32.toFloat(), yI32.toFloat()),
                    Point2F32(4f, 4f), startF32, endF32, stops).thenBlend(
                    W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER), blend, opacityF32)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) {
                    "lane=$laneI32 sample=$xI32,$yI32 span=$startF32..$endF32 opacity=$opacityF32: $expected" }
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
            }
        }
    }

    private fun Canvas.drawSweepLane(laneI32: Int, paint: Paint) {
        when (laneI32) {
            0 -> drawRect(RectF32.ofLTRB(-.25f, -.25f, 8.25f, 8.25f), paint)
            1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 8f, 8f), CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
            2 -> drawPath(Path().apply { moveTo(-1f, -1f); lineTo(18f, -1f); lineTo(-1f, 18f); close() }, paint)
            3 -> drawPath(Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 7f, 7f)) },
                paint.copy(style = PaintStyle.STROKE, strokeWidth = 1f))
        }
    }

    @Test
    fun radialGradientCoversFourGeometryLanes() {
        val stops = List(17) { indexI32 -> GradientStop(
            if (indexI32 in 8..9) .5f else indexI32 / 16f,
            if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue) }
        val surface = Surface(22, 40)
        surface.canvas {
            // A different Linear sequence precedes the Radial ranges in the same slab.
            drawRect(RectF32.ofLTRB(0f, 0f, 22f, 40f), Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f),
                Point2F32(22f, 0f), listOf(GradientStop(0f, ColorARGB.Green), GradientStop(1f, ColorARGB.Green))), antiAlias = false))
            repeat(4) { laneI32 ->
                save()
                concat(Matrix3x3F32.translation(.5f, laneI32 * 10f + .5f) * Matrix3x3F32.scaling(2f, 2f))
                val paint = Paint(shader = Shader.RadialGradient(Point2F32(1f, 1f), 8f, stops), antiAlias = false)
                when (laneI32) {
                    0 -> drawRect(RectF32.ofLTRB(-.25f, -.25f, 10.25f, 3.25f), paint)
                    1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 3f), CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
                    2 -> drawPath(Path().apply { moveTo(-1f, -1f); lineTo(12f, -1f); lineTo(-1f, 5f); close() }, paint)
                    3 -> drawPath(Path().apply { moveTo(-1f, 1f); lineTo(11f, 1f) }, paint.copy(style = PaintStyle.STROKE, strokeWidth = 1f))
                }
                restore()
            }
        }
        val pixels = surface.render().pixels
        assertContentEquals(ubyteArrayOf(0u, 255u, 0u, 255u), pixels.copyOfRange((39 * 22 + 21) * 4, (39 * 22 + 22) * 4))
        repeat(4) { laneI32 ->
            for ((localXF32, color) in listOf(4.5f to ubyteArrayOf(255u, 0u, 0u, 255u),
                5f to ubyteArrayOf(0u, 0u, 255u, 255u), 5.5f to ubyteArrayOf(0u, 0u, 255u, 255u))) {
                val offsetI32 = ((laneI32 * 10 + 2) * 22 + (localXF32 * 2).toInt()) * 4
                assertContentEquals(color, pixels.copyOfRange(offsetI32, offsetI32 + 4), "lane=$laneI32 x=$localXF32")
                val expected = W5cGradientCpuOracle.radialClampSrgb(Point2F32(localXF32, 1f), Point2F32(1f, 1f), 8f, stops)
                    .thenBlend(W5bBlendCpuOracle.Draw(ColorARGB.Transparent, 1f, BlendMode.SRC_OVER), BlendMode.SRC_OVER, 1f)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
            }
        }
        // General distance and vertical-axis distance, through the shared opacity/blend tail.
        val interpolatedStops = listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White))
        // 32/255 keeps the complete distance/transfer/attachment envelope within
        // two adjacent codes at these samples; 16/255 and 64/255 straddle a code.
        val opacityF32 = 32f / 255f
        val interpolation = Surface(29, 40)
        interpolation.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 29f, 40f), Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
            repeat(4) { laneI32 ->
                save()
                concat(Matrix3x3F32.translation(.5f, laneI32 * 10f + .5f) * Matrix3x3F32.scaling(2f, 2f))
                val paint = Paint(shader = Shader.Opacity(Shader.RadialGradient(Point2F32(1f, 1f), 8f, interpolatedStops), opacityF32),
                    blendMode = BlendMode.DIFFERENCE, antiAlias = false)
                when (laneI32) {
                    0 -> drawRect(RectF32.ofLTRB(-.25f, -.25f, 10.25f, 4.25f), paint)
                    1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 10f, 4f), CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
                    2 -> drawPath(Path().apply { moveTo(-1f, -1f); lineTo(12f, -1f); lineTo(-1f, 5f); close() }, paint)
                    3 -> drawPath(Path().apply { moveTo(-1f, 2f); lineTo(11f, 2f) }, paint.copy(style = PaintStyle.STROKE, strokeWidth = 1f))
                }
                restore()
            }
        }
        val interpolatedPixels = interpolation.render().pixels
        repeat(4) { laneI32 ->
            for (localXF32 in listOf(1f, 5f)) {
                val expected = W5cGradientCpuOracle.radialClampSrgb(Point2F32(localXF32, 2f), Point2F32(1f, 1f), 8f, interpolatedStops)
                    .thenBlend(W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER), BlendMode.DIFFERENCE, opacityF32)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "lane=$laneI32 x=$localXF32: $expected" }
                val offsetI32 = ((laneI32 * 10 + 4) * 29 + (localXF32 * 2).toInt()) * 4
                try { WgslFloatEnvelopeV1Oracle.assertAdmits(expected, interpolatedPixels.copyOfRange(offsetI32, offsetI32 + 4)) }
                catch (failure: IllegalArgumentException) { throw AssertionError("lane=$laneI32 x=$localXF32", failure) }
            }
        }
    }

    @Test
    fun radialGradientHandlesDegenerateAndSingletonStops() {
        val epsilonF32 = 0.000030517578125f
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        for ((indexI32, radiusF32) in listOf(0f, Math.nextDown(epsilonF32), epsilonF32, Math.nextUp(epsilonF32)).withIndex()) {
            val surface = Surface(23 + indexI32, 1)
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, (23 + indexI32).toFloat(), 1f),
                Paint(shader = Shader.RadialGradient(Point2F32(.5f, .5f), radiusF32, stops), antiAlias = false)) }
            val expected = if (radiusF32 <= epsilonF32) ubyteArrayOf(0u, 0u, 255u, 255u) else ubyteArrayOf(255u, 0u, 0u, 255u)
            assertContentEquals(expected, surface.render().pixels.copyOfRange(0, 4))
        }
        val singleton = Surface(27, 1)
        singleton.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 27f, 1f), Paint(shader = Shader.RadialGradient(
            Point2F32(0f, 0f), 8f, listOf(GradientStop(.7f, ColorARGB.Green))), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 255u, 0u, 255u), singleton.render().pixels.copyOfRange(0, 4))
        for (radiusF32 in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val invalid = Surface(28, 1)
            invalid.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 28f, 1f), Paint(shader = Shader.RadialGradient(
                Point2F32(0f, 0f), radiusF32, stops), antiAlias = false)) }
            val failure = assertThrows<IllegalStateException> { invalid.render() }
            assertEquals(if (radiusF32 < 0f) "unsupported.material.gradient.negative_radius" else "non-finite-value",
                failure.message.orEmpty().substringBefore(':'))
        }
        val outsideDomain = Surface(30, 1)
        outsideDomain.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 30f, 1f), Paint(shader = Shader.RadialGradient(
            Point2F32(0f, 0f), 1e20f, stops), antiAlias = false)) }
        assertInstanceOf(SceneCaptureResult.Captured::class.java, outsideDomain.snapshotScene())
        val domainFailure = assertThrows<IllegalStateException> { outsideDomain.render() }
        assertEquals("unsupported.material.gradient.numeric-domain-unbounded", domainFailure.message.orEmpty().substringBefore(':'))
    }

    @Test
    fun linearGradientCoversRRectPathFillAndStroke() {
        for (countI32 in listOf(1, 2, 16, 17)) {
            val stops = when (countI32) {
                1 -> listOf(GradientStop(.7f, ColorARGB.Blue))
                2 -> listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White))
                else -> List(countI32) { indexI32 -> GradientStop(
                    when { indexI32 < 8 -> indexI32 / 16f; indexI32 <= 9 -> .5f; else -> indexI32.toFloat() / (countI32 - 1) },
                    if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue) }
            }
            val samplesF32 = if (countI32 == 2) listOf(4f) else listOf(1f, 4f, 7f)
            for (blend in listOf(BlendMode.SRC_OVER, BlendMode.DIFFERENCE)) {
                val opacityF32 = if (blend == BlendMode.SRC_OVER) 1f else if (countI32 == 2) 64f / 255f else .5f
                // Keep the fixed-function fixture bounded; the two-stop interpolation is
                // checked through DIFFERENCE, independently of attachment factor precision.
                val drawStops = if (countI32 == 2 && blend == BlendMode.SRC_OVER)
                    listOf(GradientStop(0f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue)) else stops
                val surface = Surface(18, if (countI32 == 1 && blend == BlendMode.SRC_OVER) 49 else 48)
                surface.canvas { drawLinearLaneMatrix(drawStops, opacityF32 = opacityF32, blend = blend) }
                val pixels = try { surface.render().pixels } catch (failure: Exception) {
                    throw AssertionError("$countI32 stops, $blend", failure)
                }
                assertLinearLaneMatrix(pixels, drawStops, samplesF32, opacityF32, blend)
            }
        }
    }

    @Test
    fun linearGradientPreservesHardStopsAndImplicitEndpoints() {
        val stops = listOf(
            GradientStop(.25f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Green),
            GradientStop(.5f, ColorARGB.Blue),
            GradientStop(.75f, ColorARGB.Blue),
        )
        val surface = Surface(18, 48)
        surface.canvas { drawLinearLaneMatrix(stops) }
        // The inverse CTM gives exact local samples at 1, 3.5, 4 and 7: the equality
        // sample must select Blue, while 1 and 7 exercise the implicit endpoints.
        assertLinearLaneMatrix(surface.render().pixels, stops, listOf(1f, 3.5f, 4f, 7f))
        // A general affine Path and its clipped successor retain local, not device, x.
        for (clipped in listOf(false, true)) for (blend in listOf(BlendMode.SRC_OVER, BlendMode.DIFFERENCE)) {
            val opacityF32 = if (blend == BlendMode.SRC_OVER) 1f else .5f
            val transformed = Surface(4, 4)
            transformed.canvas {
                val bounds = Path().apply { addRect(RectF32.ofLTRB(-1f, -1f, 5f, 5f)) }
                drawPath(bounds, Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
                if (clipped) clipPath(Path().apply {
                    moveTo(0f, 0f); lineTo(4f, 0f); lineTo(4f, 2.5f)
                    lineTo(2.5f, 4f); lineTo(0f, 4f); close()
                }, antiAlias = false)
                concat(Matrix3x3F32.skewing(.25f, 0f))
                drawPath(bounds, Paint(shader = Shader.Opacity(Shader.LinearGradient(Point2F32(0f, 0f),
                    Point2F32(4f, 0f), stops), opacityF32), blendMode = blend, antiAlias = false))
            }
            val pixels = try { transformed.render().pixels } catch (failure: Exception) {
                throw AssertionError("clipped=$clipped, $blend", failure)
            }
            for ((pixelXI32, pixelYI32) in listOf(2 to 3, 3 to 1)) {
                val expected = W5cGradientCpuOracle.linearClampSrgb(
                    Point2F32(pixelXI32 + .5f - .25f * (pixelYI32 + .5f), pixelYI32 + .5f),
                    Point2F32(0f, 0f), Point2F32(4f, 0f), stops).thenBlend(
                    W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER), blend, opacityF32)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
                val offsetI32 = (pixelYI32 * 4 + pixelXI32) * 4
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
            }
            if (clipped) assertContentEquals(ubyteArrayOf(255u, 255u, 255u, 255u), pixels.copyOfRange(60, 64))
        }
    }

    @Test
    fun linearGradientPictureSnapshotIsMutationSafe() {
        val stops = MutableList(17) { indexI32 ->
            GradientStop(indexI32 / 16f, if (indexI32 < 8) ColorARGB.Red else ColorARGB.Blue)
        }
        val originalStops = stops.toList()
        val capturedPaths = mutableListOf<Path>()
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 18f, 48f))
        canvas.drawLinearLaneMatrix(stops, capturedPaths)
        val picture = recorder.finishRecordingAsPicture()
        stops.clear()
        stops += GradientStop(0f, ColorARGB.Green)
        capturedPaths.forEach { it.addRect(RectF32.ofLTRB(-100f, -100f, 100f, 100f)) }
        canvas.translate(100f, 100f)
        val restored = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        val surface = Surface(18, 48)
        surface.canvas { restored.playback(this); translate(100f, 100f) }
        assertLinearLaneMatrix(surface.render().pixels, originalStops, listOf(1f, 4f, 7f))
    }

    private fun Canvas.drawLinearLaneMatrix(stops: List<GradientStop>, capturedPaths: MutableList<Path> = mutableListOf(),
        opacityF32: Float = .5f, blend: BlendMode = BlendMode.DIFFERENCE) {
        drawRect(RectF32.ofLTRB(0f, 0f, 18f, 48f), Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
        repeat(6) { laneI32 ->
            save()
            concat(Matrix3x3F32.translation(.5f, laneI32 * 8f + .5f) * Matrix3x3F32.scaling(2f, 2f))
            val paint = Paint(shader = Shader.Opacity(Shader.LinearGradient(Point2F32(0f, 0f),
                Point2F32(8f, 0f), stops), opacityF32), blendMode = blend, antiAlias = false)
            when (laneI32) {
                // These local bounds become an integral device Rect under the captured CTM.
                0 -> drawRect(RectF32.ofLTRB(-.25f, -.25f, 8.25f, 3.25f), paint)
                1 -> drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 8f, 3f), CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
                else -> {
                    val path = Path().apply {
                        when (laneI32) {
                            2 -> { moveTo(-1f, -1f); lineTo(10f, -1f); lineTo(-1f, 10f); close() }
                            3 -> { moveTo(-1f, -1f); lineTo(9f, -1f); lineTo(9f, 4f)
                                lineTo(4f, 2f); lineTo(-1f, 4f); close() }
                            else -> { moveTo(-1f, 1f); lineTo(9f, 1f) }
                        }
                    }
                    capturedPaths += path
                    drawPath(path, if (laneI32 >= 4) paint.copy(style = PaintStyle.STROKE,
                        strokeWidth = if (laneI32 == 4) 1f else 0f) else paint)
                }
            }
            restore()
        }
        // Later opaque content never covers the independently checked middle draws.
        setMatrix(Matrix3x3F32.Identity)
        drawRect(RectF32.ofLTRB(16f, 46f, 18f, 48f), Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = false))
    }

    private fun assertLinearLaneMatrix(pixels: UByteArray, stops: List<GradientStop>, localXsF32: List<Float>,
        opacityF32: Float = .5f, blend: BlendMode = BlendMode.DIFFERENCE) {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER)
        repeat(6) { laneI32 ->
            localXsF32.forEach { localXF32 ->
                val expected = W5cGradientCpuOracle.linearClampSrgb(Point2F32(localXF32, 1f),
                    Point2F32(0f, 0f), Point2F32(8f, 0f), stops).thenBlend(destination, blend, opacityF32)
                require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) {
                    "${stops.size} stops, lane $laneI32, local x $localXF32: $expected"
                }
                val offsetI32 = ((laneI32 * 8 + 2) * 18 + (localXF32 * 2f).toInt()) * 4
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(offsetI32, offsetI32 + 4))
            }
        }
        val finalOffsetI32 = (47 * 18 + 17) * 4
        assertContentEquals(ubyteArrayOf(0u, 255u, 0u, 255u), pixels.copyOfRange(finalOffsetI32, finalOffsetI32 + 4))
    }

    @Test
    fun linearRectNormalizesPositionsAndHardStopRuns() {
        val red = ubyteArrayOf(255u, 0u, 0u, 255u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val fixtures = listOf(
            // Clamp both ends, then monotonize .25 into the hard stop at .5.
            listOf(GradientStop(-1f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
                GradientStop(.25f, ColorARGB.Blue), GradientStop(2f, ColorARGB.Blue)) to listOf(red, red, blue, blue, blue),
            // Both implicit endpoints must repeat the nearest input color.
            listOf(GradientStop(.25f, ColorARGB.Red), GradientStop(.25f, ColorARGB.Blue),
                GradientStop(.75f, ColorARGB.Blue), GradientStop(.75f, ColorARGB.Red)) to listOf(red, blue, blue, red, red),
            // Interior colors in a run longer than two never replace its first or last.
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
                GradientStop(.5f, ColorARGB.Green), GradientStop(.5f, ColorARGB.Black),
                GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue)) to listOf(red, red, blue, blue, blue),
        )
        for ((stops, expected) in fixtures) {
            val bounds = RectF32.ofLTRB(0f, 0f, 5f, 1f)
            val recorder = PictureRecorder()
            recorder.beginRecording(bounds).drawRect(bounds, Paint(shader = Shader.LinearGradient(
                Point2F32(.5f, 0f), Point2F32(4.5f, 0f), stops), antiAlias = false))
            val picture = recorder.finishRecordingAsPicture()
            val surface = Surface(5, 1)
            surface.canvas { picture.playback(this) }
            val pixels = surface.render().pixels
            expected.forEachIndexed { indexI32, color ->
                assertContentEquals(color, pixels.copyOfRange(indexI32 * 4, indexI32 * 4 + 4))
            }
        }
    }

    @Test
    fun linearRectDegenerateAxisUsesLastStop() {
        for (endF32 in listOf(Point2F32(0f, 0f), Point2F32(0.0000152587890625f, 0f))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), endF32,
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Green),
                    GradientStop(1f, ColorARGB.Blue))), antiAlias = false)) }
            assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), surface.render().pixels)
        }
    }

    @Test
    fun linearRectInvalidInputsRefusePubliclyAndAllowRecovery() {
        for (shader in listOf(linearStops(2).copy(start = Point2F32(Float.NaN, 0f)),
            linearStops(2).copy(stops = listOf(GradientStop(Float.POSITIVE_INFINITY, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
            val captured = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
            assertEquals("non-finite-value", captured.diagnostics.single().code.value)
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("non-finite-value", failure.message.orEmpty().substringBefore(':'))
        }
        // Finite public inputs whose per-fragment numeric domain cannot be proven must never execute.
        for (shader in listOf(linearStops(2).copy(end = Point2F32(1e20f, 0f)),
            linearStops(2).copy(start = Point2F32(1_000_000f, 0f), end = Point2F32(1_000_001f, 0f)))) {
            val surface = Surface(1, 1)
            surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
            assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene())
            val failure = assertThrows<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.gradient.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
        }
        val recovery = Surface(1, 1)
        recovery.canvas { drawRect(rect, Paint(shader = linearStops(2), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), recovery.render().pixels)
    }

    @Test
    fun linearRectDeepOpacityReportsDepthLimitWithoutOverflow() {
        var shader: Shader = linearStops(2)
        repeat(50_000) { shader = Shader.Opacity(shader, 1f) }
        val surface = Surface(1, 1)
        surface.canvas { drawRect(rect, Paint(shader = shader, antiAlias = false)) }
        val captured = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
        assertEquals("graph-depth-limit", captured.diagnostics.single().code.value)
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("graph-depth-limit", failure.message.orEmpty().substringBefore(':'))
    }

    @Test
    fun linearRectUsesLocalCoordinatesAndMoreThanSixteenStops() {
        val capturedStops = MutableList(17) { indexI32 ->
            GradientStop(
                position = when (indexI32) { 8, 9 -> 0.5f; else -> indexI32 / 16f },
                color = if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue,
            )
        }
        val originalStops = capturedStops.toList()
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.White, 1f, BlendMode.SRC_OVER)
        val blend = BlendMode.DIFFERENCE
        val paintAlphaF32 = 64f / 255f
        var ctmF32 = Matrix3x3F32.translation(-8190.5f, -8.5f) * Matrix3x3F32.scaling(1024f, 2f)
        val surface = Surface(4098, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4098f, 1f), Paint(
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4098f, 0f),
                    listOf(GradientStop(0f, destination.color), GradientStop(1f, destination.color))), antiAlias = true,
            ))
            save()
            concat(ctmF32)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 8f), Paint(
                color = ColorARGB.of(64, 1, 2, 3),
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(16f, 0f), capturedStops),
                blendMode = blend, antiAlias = true,
            ))
            restore()
            translate(71f, 93f)
            scale(3f, 7f)
        }
        capturedStops.clear()
        capturedStops += GradientStop(0f, ColorARGB.Green)
        ctmF32 = Matrix3x3F32.scaling(7f, 11f)
        surface.canvas { concat(ctmF32) }

        val expectedSamples = listOf(0 to 7.9990234375f, 1 to 8f, 2 to 8.0009765625f, 4097 to 12f).map { (pixelXI32, localXF32) ->
            val expected = W5cGradientCpuOracle.linearClampSrgb(
                Point2F32(localXF32, 4.5f), Point2F32(0f, 0f), Point2F32(16f, 0f), originalStops,
            ).thenBlend(destination, blend, paintAlphaF32)
            require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
            pixelXI32 to expected
        }
        val result = surface.render()
        expectedSamples.forEach { (pixelXI32, expected) ->
            WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
        }

        // The same Linear program must interpolate straight sRGB on the integral Rect lane.
        val interpolationStops = listOf(GradientStop(0f, ColorARGB.Black), GradientStop(1f, ColorARGB.White))
        val integral = Surface(9, 1)
        integral.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 9f, 1f), Paint(color = ColorARGB.of(64, 0, 0, 0), blendMode = blend,
                shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(9f, 0f), interpolationStops), antiAlias = false))
        }
        val midpoint = W5cGradientCpuOracle.linearClampSrgb(Point2F32(4.5f, .5f), Point2F32(0f, 0f),
            Point2F32(9f, 0f), interpolationStops).thenBlend(
            destination, blend, paintAlphaF32)
        require(midpoint is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { midpoint.toString() }
        WgslFloatEnvelopeV1Oracle.assertAdmits(midpoint, integral.render().pixels.copyOfRange(16, 20))

        val direct = Surface(1, 1)
        direct.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(0f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue))), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), direct.render().pixels)

        val singleStop = Surface(1, 1)
        singleStop.canvas { drawRect(rect, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(.7f, ColorARGB.Blue))), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), singleStop.render().pixels)
    }

    @Test
    fun recordingStopLimitRefusesTransactionallyBeforeCopy() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(17))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(16, failure.limitI32)
        assertEquals(17L, failure.requestedI64)
        surface.canvas { drawRect(rect, Paint(shader = Shader.SolidColor(ColorARGB.Red), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(0xFFu, 0x00u, 0x00u, 0xFFu), surface.render().pixels)
    }

    @Test
    fun recordingStopLimitCountsSiblingGradientChildren() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas {
                drawRect(rect, Paint(shader = Shader.Blend(BlendMode.SRC_OVER, linearStops(9), linearStops(9))))
            }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(18L, failure.requestedI64)
        // A refused sibling must release the first child's pending reservation.
        surface.canvas { drawRect(rect, Paint(shader = linearStops(16))) }
        assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene())
    }

    @Test
    fun recordingStopLimitCountsPriorAppends() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))
        surface.canvas { drawRect(rect, Paint(shader = linearStops(9))) }

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(8))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(17L, failure.requestedI64)
    }

    @Test
    fun captureStopLimitReturnsTypedDiagnostic() {
        val surface = Surface(1, 1)
        surface.canvas { drawRect(rect, Paint(shader = linearStops(17))) }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )

        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    @Test
    fun recordingStopLimitCannotBeBypassedByReusingRefusedShader() {
        val surface = Surface(1, 1, captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))
        val reused = linearStops(9)
        assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = Shader.Blend(BlendMode.SRC_OVER, reused, linearStops(9)))) }
        }
        surface.canvas { drawRect(rect, Paint(shader = reused)) }

        val failure = assertThrows<SceneRecordingLimitException> {
            surface.canvas { drawRect(rect, Paint(shader = linearStops(8))) }
        }

        assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
        assertEquals(17L, failure.requestedI64)
    }

    @Test
    fun captureStopLimitPreflightsAllOperationsBeforeStopValidation() {
        val surface = Surface(1, 1)
        val invalidFirst = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })
        surface.canvas {
            drawRect(rect, Paint(shader = invalidFirst))
            drawRect(rect, Paint(shader = linearStops(8)))
        }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )

        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    @Test
    fun captureStopLimitPreflightsNestedPicturesBeforeStopValidation() {
        val recorder = PictureRecorder()
        recorder.beginRecording(rect).drawRect(rect, Paint(shader = linearStops(8)))
        val picture = recorder.finishRecordingAsPicture()
        for (asImageFilter in listOf(false, true)) {
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(rect, Paint(shader = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })))
                if (asImageFilter) drawRect(rect, Paint(imageFilter = ImageFilter.Picture(picture)))
                else drawPicture(picture)
            }

            val failure = assertInstanceOf(
                SceneCaptureResult.Invalid::class.java,
                surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
            )
            assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
        }
    }

    @Test
    fun captureStopLimitPreflightsMaskShadersBeforeStopValidation() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(rect, Paint(shader = linearStops(9).copy(stops = List(9) { GradientStop(Float.NaN, ColorARGB.Red) })))
            drawRect(rect, Paint(maskFilter = MaskFilter.Shader(linearStops(8))))
        }

        val failure = assertInstanceOf(
            SceneCaptureResult.Invalid::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16)),
        )
        assertEquals("scene-capture-gradient-stops-exceeded", failure.diagnostics.single().code.value)
    }

    private fun linearStops(countI32: Int): Shader.LinearGradient = Shader.LinearGradient(
        start = Point2F32(0f, 0f),
        end = Point2F32(1f, 0f),
        stops = List(countI32) { indexI32 -> GradientStop(indexI32.toFloat() / (countI32 - 1), ColorARGB.Red) },
    )

    private val rect = RectF32.ofLTRB(0f, 0f, 1f, 1f)
}
