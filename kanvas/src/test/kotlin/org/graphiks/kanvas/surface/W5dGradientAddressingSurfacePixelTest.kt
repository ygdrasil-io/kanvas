@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals

class W5dGradientAddressingSurfacePixelTest {
    @Test
    fun linearTileModesCoverSignedBoundariesOnEveryLane() = tileLanes()

    @Test
    fun linearHardStopsPreserveTileBoundaries() = tileLanes(destinationBlend = true)

    @Test
    fun nonClampDropsOnlyTheOuterEndpointDuplicate() = tileLanes(endpointDuplicates = true)

    private fun tileLanes(endpointDuplicates: Boolean = false, destinationBlend: Boolean = false) {
        // Wrong signed tiling, endpoint pruning, upper_bound selection or lane admission
        // changes these public pixels. All samples lie inside the existing W4 coverage.
        val failures = mutableListOf<String>()
        for (mode in TileMode.entries) for (laneI32 in 0..3) {
            try {
                val stops = buildList {
                    if (endpointDuplicates) add(GradientStop(0f, ColorARGB.Black))
                    add(GradientStop(0f, ColorARGB.Red)); add(GradientStop(.5f, ColorARGB.Red))
                    if (endpointDuplicates) add(GradientStop(.5f, ColorARGB.Green))
                    add(GradientStop(.5f, ColorARGB.Blue)); add(GradientStop(1f, ColorARGB.Blue))
                    if (endpointDuplicates) add(GradientStop(1f, ColorARGB.Green))
                }
                var shader: Shader = Shader.LinearGradient(Point2F32(18.5f, 0f), Point2F32(26.5f, 0f), stops, tileMode = mode)
                // CLAMP without coordinates intentionally stays W5c/V1. Force the
                // common V2 tile graph here, including both endpoint duplicates.
                if (mode == TileMode.CLAMP) shader = Shader.WithLocalMatrix(shader, Matrix3x3F32())
                if (destinationBlend) shader = Shader.Opacity(shader, .5f)
                val rectF32 = RectF32.ofLTRB(0f, 0f, 39f, 8f)
                val recorder = PictureRecorder()
                val canvas = recorder.beginRecording(rectF32)
                val paint = Paint(shader = shader, antiAlias = false,
                    blendMode = if (destinationBlend) BlendMode.DIFFERENCE else BlendMode.SRC_OVER)
                when (laneI32) {
                    0 -> canvas.drawRect(rectF32, paint)
                    1 -> canvas.drawRRect(RRectF32.of(rectF32, CornerRadiiF32.of(.5f)), paint.copy(antiAlias = true))
                    2 -> canvas.drawPath(Path().apply { addRect(rectF32) }, paint)
                    3 -> canvas.drawPath(Path().apply { moveTo(0f, 4f); lineTo(39f, 4f) },
                        paint.copy(style = PaintStyle.STROKE, strokeWidth = 8f))
                }
                val picture = recorder.finishRecordingAsPicture()
                val surface = Surface(39, 8)
                surface.canvas {
                    if (destinationBlend) drawRect(rectF32, Paint(shader = Shader.SolidColor(ColorARGB.White), antiAlias = false))
                    picture.playback(this)
                }
                val pixels = surface.render().pixels
                for (sample in W5dGradientAddressingCpuOracle.tileSamples(mode)) {
                    val pixelXI32 = (18f + sample.rawTF32 * 8f).toInt()
                    val offsetI32 = (4 * 39 + pixelXI32) * 4
                    try {
                        WgslFloatEnvelopeV1Oracle.assertAdmits(W5dGradientAddressingCpuOracle.tiledPixel(
                            sample, mode, endpointDuplicates, destinationBlend), pixels.copyOfRange(offsetI32, offsetI32 + 4))
                    } catch (failure: AssertionError) {
                        failures += "$mode lane=$laneI32 rawT=${sample.rawTF32}: ${failure.message}"
                    } catch (failure: IllegalArgumentException) {
                        failures += "$mode lane=$laneI32 rawT=${sample.rawTF32}: ${failure.message}"
                    }
                }
            } catch (failure: IllegalStateException) {
                failures += "$mode lane=$laneI32: ${failure.message}"
            }
        }
        kotlin.test.assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @org.junit.jupiter.api.BeforeEach
    fun establishPublicRuntime() {
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
    }

    private val bounds = RectF32.ofLTRB(0f, 0f, 13f, 1f)
    private fun linearGradient(): Shader.LinearGradient = Shader.LinearGradient(
        Point2F32(0f, 0f), Point2F32(8f, 0f),
        List(17) { indexI32 -> GradientStop(indexI32 / 16f, if (indexI32 < 8) ColorARGB.Red else ColorARGB.Blue) },
        tileMode = TileMode.CLAMP,
    )

    @Test
    fun linearLocalMatricesPreserveNonCommutativeOrder() {
        // Swapping the segment product changes inverse x from y + 8 to y.
        val linear17 = linearGradient()
        val rotationF32 = Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f)
        val translationF32 = Matrix3x3F32.translation(0f, -8f)
        val outerThenInner = Shader.WithLocalMatrix(
            Shader.WithLocalMatrix(linear17, rotationF32), translationF32)
        val reversed = Shader.WithLocalMatrix(
            Shader.WithLocalMatrix(linear17, translationF32), rotationF32)
        val first = renderPixel(outerThenInner)
        val second = renderPixel(reversed)
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), first)
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), second)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun linearDistinctLocalMatrixValuesSurvivePictureCapture() {
        // Reusing topology with another immutable value must not replace the first Picture's uniforms.
        val firstMatrixF32 = Matrix3x3F32.translation(0f, -8f)
        val secondMatrixF32 = firstMatrixF32.copy(ty = 0f)
        val rotationF32 = Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f)
        val pictures = listOf(firstMatrixF32, secondMatrixF32).map { matrixF32 ->
            val recorder = PictureRecorder()
            recorder.beginRecording(bounds).drawRect(bounds, Paint(shader = Shader.WithLocalMatrix(
                Shader.WithLocalMatrix(linearGradient(), rotationF32), matrixF32), antiAlias = false))
            recorder.finishRecordingAsPicture()
        }
        for (indexI32 in listOf(0, 1, 0)) {
            val surface = Surface(1, 1)
            surface.canvas { pictures[indexI32].playback(this) }
            assertContentEquals(if (indexI32 == 0) W5dGradientAddressingCpuOracle.bluePixel()
                else W5dGradientAddressingCpuOracle.redPixel(), surface.render().pixels)
        }
    }

    @Test
    fun nonFiniteAndSingularLocalMatricesRefusePrecisely() {
        // Capture must preserve invalid admitted matrices until the W5d planner classifies them.
        val cases = listOf(
            Matrix3x3F32(tx = Float.NaN) to "unsupported.material.gradient.local-matrix-non-finite",
            Matrix3x3F32(ky = Float.POSITIVE_INFINITY) to "unsupported.material.gradient.local-matrix-non-finite",
            Matrix3x3F32.scaling(0f, 1f) to "unsupported.material.gradient.local-matrix-singular",
        )
        for ((matrixF32, code) in cases) assertRefusesThenRecovers(matrixF32, code)
    }

    @Test
    fun unrepresentableInverseRefusesPrecisely() {
        // The F64 inverse exists, but its F32 coefficient exceeds Float.MAX_VALUE.
        assertRefusesThenRecovers(Matrix3x3F32.scaling(Float.MIN_VALUE, 1f),
            "unsupported.material.gradient.local-matrix-unrepresentable")
    }

    private fun assertRefusesThenRecovers(matrixF32: Matrix3x3F32, code: String) {
        // Establish the eligible public runtime before refusal; never dispose it between draws.
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
        val failure = assertThrows<IllegalStateException> {
            renderPixel(Shader.WithLocalMatrix(Shader.WithLocalMatrix(linearGradient(), matrixF32),
                Matrix3x3F32.translation(1f, 0f)))
        }
        assertEquals(code, failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
    }

    private fun renderPixel(shader: Shader): UByteArray {
        val surface = Surface(1, 1)
        surface.canvas { drawRect(bounds, Paint(shader = shader, antiAlias = false)) }
        return surface.render().pixels
    }

    @Test
    fun bareClampPreservesW5cBudgetDiagnostic() {
        val stops = List(257) { indexI32 -> GradientStop(indexI32 / 256f, ColorARGB.Blue) }
        fun frame(budgetI64: Long) = Surface(13, 1,
            config = RenderConfig(frameLocalBudgetBytes = budgetI64)).also { surface ->
            surface.canvas { drawRect(bounds, Paint(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(13f, 0f), stops), antiAlias = false)) }
        }
        val healthy = frame(1L shl 20)
        val expected = UByteArray(13 * 4) { indexI32 -> if (indexI32 % 4 >= 2) 255u else 0u }
        assertContentEquals(expected, healthy.render().pixels)
        val failure = assertThrows<IllegalStateException> { frame(4096L).render() }
        // Routing this bare CLAMP through V2 changes this public diagnostic.
        assertEquals("resource.material.gradient.stop-budget", failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(expected, healthy.render().pixels)
    }

    @Test
    fun excludedLinearPaintLanesPreservePreparedRefusals() {
        val failures = mutableListOf<String>()
        for (mode in TileMode.entries) for (laneI32 in 0..2) for (wrapped in listOf(false, true))
            for (endXF32 in listOf(8f, Float.NaN)) {
            // Excluded paints must keep prepared refusal precedence even when
            // gradient geometry is invalid; they do not belong to W5d capture.
            val leaf = linearGradient().copy(tileMode = mode, end = Point2F32(endXF32, 0f))
            val shader = if (wrapped) Shader.WithLocalMatrix(leaf, Matrix3x3F32()) else leaf
            val surface = Surface(13, 8)
            val rectF32 = RectF32.ofLTRB(1f, 1f, 12f, 7f)
            surface.canvas {
                val paint = Paint(shader = shader, antiAlias = laneI32 != 2,
                    style = if (laneI32 == 2) PaintStyle.FILL else PaintStyle.STROKE, strokeWidth = 2f)
                if (laneI32 == 0) drawRect(rectF32, paint)
                else drawRRect(RRectF32.of(rectF32, CornerRadiiF32.of(1f)), paint)
            }
            try {
                val failure = assertThrows<IllegalStateException> { surface.render() }
                assertEquals(if (laneI32 == 0) "unsupported.stroke.rect_anti_alias"
                    else "unsupported.material.mapping.linear_gradient_stop_count",
                    failure.message.orEmpty().substringBefore(':'))
            } catch (failure: AssertionError) {
                failures += "$mode lane=$laneI32 wrapped=$wrapped endX=$endXF32: ${failure.message}"
            }
        }
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
        kotlin.test.assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun localClampLocalPreservesExactOrder() {
        // Moving the clamp across the translation changes x from 1 to 4.25.
        val subsetF32 = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val inner = Shader.WithLocalMatrix(linearGradient(), Matrix3x3F32.scaling(2f, 1f))
        val ordered = Shader.WithLocalMatrix(Shader.CoordClamp(inner, subsetF32), Matrix3x3F32.translation(-8f, 0f))
        val moved = Shader.CoordClamp(Shader.WithLocalMatrix(inner, Matrix3x3F32.translation(-8f, 0f)), subsetF32)
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(ordered))
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), renderPixel(moved))
        assertNotEquals(renderPixel(ordered).toList(), renderPixel(moved).toList())
    }

    @Test
    fun adjacentDisjointClampsAreNotMerged() {
        // Disjoint clamps select the last clamp's near edge; intersection is not equivalent.
        val lowF32 = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val highF32 = RectF32.ofLTRB(6f, 0f, 8f, 1f)
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), renderPixel(
            Shader.CoordClamp(Shader.CoordClamp(linearGradient(), highF32), lowF32)))
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(
            Shader.CoordClamp(Shader.CoordClamp(linearGradient(), lowF32), highF32)))
        // Equal edges are a valid point clamp, not an empty-subset refusal.
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), renderPixel(
            Shader.CoordClamp(linearGradient(), RectF32.ofLTRB(6f, .5f, 6f, .5f))))
    }

    @Test
    fun projectiveLocalMatrixRendersBoundedPixelsAndMasksWZero() {
        val leaf = linearGradient().copy(end = Point2F32(2f, 0f))
        val subsetF32 = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        // Bounded, non-unit w is admitted without a following clamp; omitting /w gives red.
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), renderPixel(
            Shader.WithLocalMatrix(leaf, Matrix3x3F32(persp2 = 2f))))
        for ((scaleF64, numeratorF64) in listOf(1.0 to 2.0,
            Math.scalb(1.0, 126) to Math.scalb(1.0, 127), 1.0 to Math.scalb(1.0, -127))) {
            // Inverse rows are [0,0,2B], [0,B,0], [B,0,-1.5B].
            // At x=2.5 the large control divides 2^127 by 2^126, without overflow.
            // The tiny numerator also exercises surviving/input-FTZ and output-FTZ alternatives.
            val matrixF32 = Matrix3x3F32(sx = (1.5 / numeratorF64).toFloat(), kx = 0f,
                tx = (1.0 / scaleF64).toFloat(), ky = 0f, sy = (1.0 / scaleF64).toFloat(),
                ty = 0f, persp0 = (1.0 / numeratorF64).toFloat(), persp1 = 0f, persp2 = 0f)
            val shader = Shader.WithLocalMatrix(Shader.CoordClamp(leaf, subsetF32), matrixF32)
            val surface = Surface(3, 1)
            surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 3f, 1f), Paint(shader = shader, antiAlias = false)) }
            val pixels = surface.render().pixels
            for (pixelXI32 in 0..2) {
                val admitted = W5dGradientAddressingCpuOracle.projectivePixelCodes(pixelXI32, scaleF64, numeratorF64)
                kotlin.test.assertTrue(pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4).toList() in admitted)
            }
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixels.copyOfRange(4, 8))
            assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), pixels.copyOfRange(0, 4))
            assertContentEquals(if (numeratorF64 / scaleF64 >= 1.0) W5dGradientAddressingCpuOracle.bluePixel()
                else W5dGradientAddressingCpuOracle.redPixel(), pixels.copyOfRange(8, 12))
            // Cross-zero must be refused when no immediately following clamp closes it.
            val failure = assertThrows<IllegalStateException> { renderPixel(Shader.WithLocalMatrix(leaf, matrixF32)) }
            assertEquals("unsupported.material.gradient.numeric-domain-unbounded", failure.message.orEmpty().substringBefore(':'))
            assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
        }
        // A clamp cannot rescue an overflowing homogeneous multiply/add schedule.
        val overflow = assertThrows<IllegalStateException> { renderPixel(Shader.WithLocalMatrix(
            Shader.CoordClamp(leaf, subsetF32), Matrix3x3F32.scaling(java.lang.Float.MIN_NORMAL, 1f))) }
        assertEquals("unsupported.material.gradient.numeric-domain-unbounded", overflow.message.orEmpty().substringBefore(':'))
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
    }

    @Test
    fun coordClampRejectsNonFiniteAndUnsortedSubsets() {
        for ((subsetF32, suffix) in listOf(
            RectF32.ofLTRB(Float.NaN, 0f, 1f, 1f) to "non-finite",
            RectF32.ofLTRB(0f, 0f, Float.POSITIVE_INFINITY, 1f) to "non-finite",
            RectF32.ofLTRB(2f, 0f, 1f, 1f) to "unsorted",
            RectF32.ofLTRB(0f, 2f, 1f, 1f) to "unsorted")) {
            val failure = assertThrows<IllegalStateException> { renderPixel(Shader.CoordClamp(linearGradient(), subsetF32)) }
            assertEquals("unsupported.material.gradient.coord-clamp-$suffix", failure.message.orEmpty().substringBefore(':'))
            assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
        }
    }

    @Test
    fun coordinateGraphDepthUsesExistingCaptureLimit() {
        var shader: Shader = linearGradient()
        repeat(50_000) { shader = if (it % 2 == 0) Shader.WithLocalMatrix(shader, Matrix3x3F32())
            else Shader.CoordClamp(shader, bounds) }
        val failure = assertThrows<IllegalStateException> { renderPixel(shader) }
        assertEquals("graph-depth-limit", failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
    }

    @Test
    fun linearClampWithAffineLocalMatrixIsPlanOwned() {
        // Ignoring the local matrix or either opacity changes these public pixels.
        val leaf = linearGradient()
        val shader = Shader.Opacity(Shader.WithLocalMatrix(
            Shader.Opacity(leaf, .75f), Matrix3x3F32.translation(3f, 0f)), .5f)
        for (ctmScaleXF32 in listOf(1f, 2f)) for (antiAlias in listOf(false, true)) {
            val expected = listOf(0, 3, 5, 7, 10, 12).map { it * ctmScaleXF32.toInt() }
                .associateWith { W5dGradientAddressingCpuOracle.evaluate(it, leaf.stops, ctmScaleXF32) }
            val surface = Surface(13 * ctmScaleXF32.toInt(), 1)
            surface.canvas {
                scale(ctmScaleXF32, 1f)
                val rect = if (antiAlias) RectF32.ofLTRB(-.25f, -.25f, 13.25f, 1.25f) else bounds
                drawRect(rect, Paint(color = ColorARGB.of(191, 0, 0, 0), shader = shader, antiAlias = antiAlias))
            }
            val pixels = surface.render().pixels
            for ((pixelXI32, envelope) in expected) {
                WgslFloatEnvelopeV1Oracle.assertAdmits(envelope,
                    pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
            }
        }
    }

    @Test
    fun unsupportedWrapperRemainsPreAdmission() {
        val surface = Surface(13, 1)
        val leaf = linearGradient().copy(stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        surface.canvas { drawRect(bounds, Paint(shader = Shader.WithColorFilter(leaf, ColorFilter.HighContrast), antiAlias = false)) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.w5a.kind", failure.message.orEmpty().substringBefore(':'))
    }

    @Test
    fun singularLocalMatrixRefusesAndRecoversOnTheSameRuntime() {
        val surface = Surface(13, 1)
        val leaf = linearGradient().copy(stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        surface.canvas { drawRect(bounds, Paint(shader = Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(0f, 1f)), antiAlias = false)) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.gradient.local-matrix-singular", failure.message.orEmpty().substringBefore(':'))
        // No runtime disposal between refusal and a new public Surface.
        val recovery = Surface(1, 1)
        recovery.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(shader = linearGradient(), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), recovery.render().pixels)
    }
}
