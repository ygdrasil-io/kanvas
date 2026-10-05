@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.surface.gpu.GPUPreparedTextTestFixtures
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.PointMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Public composition witnesses; the exhaustive family products remain in their owning H classes. */
class W5hConvergenceSurfacePixelTest {
    @Test fun sameCapturedRuntimeOwnerAcrossEveryLane() {
        val wanted = laneExpectations()
        val children = linkedMapOf("child" to Shader.SolidColor(SOURCE) as Shader)
        val shader = runtime(children)
        val surface = Surface(WIDTH, 1)
        surface.canvas { everyLane(shader) }
        children["child"] = Shader.SolidColor(ColorARGB.Green)
        repeat(2) { assertLanePixels(surface.render(), wanted) }

        // Equal public work, with only a non-Core consumer moved between the two
        // Core groups. Neither physical run may charge the other group's uniforms.
        val splitChild = Shader.LinearGradient(Point2F32(.5f, 0f), Point2F32(2.5f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Green),
                GradientStop(1f, ColorARGB.Blue)))
        val splitShader = Shader.RuntimeEffect(
            assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
            UniformBlock { float1("alpha", 1f) }, mapOf("child" to splitChild))
        val splitPaint = Paint(shader = splitShader, antiAlias = false)
        val splitWanted = (0..2).map { index ->
            W5fColorCpuOracle.expectedShaderTree(splitChild,
                devicePointF32 = Point2F32(index + .5f, .5f))
        }
        splitWanted.forEach(W5fSurfacePixelFixtures::requireBounded)
        fun recordSplit(target: Surface, interleaved: Boolean, count: Int = 256) = target.canvas {
            fun core(left: Float) = repeat(count) {
                drawRRect(RRectF32.of(RectF32.ofLTRB(left, -1f, left + 2f, 2f),
                    CornerRadiiF32.of(.5f)), splitPaint)
            }
            fun middle() = drawVertices(Vertices(VertexMode.TRIANGLES,
                listOf(Point2F32(1f, 0f), Point2F32(2f, 0f), Point2F32(2f, 1f), Point2F32(1f, 1f)),
                indices = listOf(0, 1, 2, 0, 2, 3)), splitPaint)
            core(-1f)
            if (interleaved) middle()
            core(2f)
            if (!interleaved) middle()
        }
        fun assertSplit(target: Surface) = repeat(2) {
            val pixels = target.render().pixels
            splitWanted.forEachIndexed { index, expected ->
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(index * 4, index * 4 + 4))
            }
        }
        val bounded = Surface(3, 1, config = RenderConfig(frameLocalBudgetBytes = 400_000))
        recordSplit(bounded, interleaved = false)
        assertSplit(bounded)
        bounded.discardRecordedOperations()
        recordSplit(bounded, interleaved = true)
        assertSplit(bounded)
        bounded.discardRecordedOperations()
        recordSplit(bounded, interleaved = true, count = 512)
        val budgetFailure = assertFailsWith<IllegalStateException> { bounded.render() }
        assertEquals("invalid.surface.prepared.frame-build-contract",
            budgetFailure.message.orEmpty().substringBefore(':'))
        bounded.discardRecordedOperations()
        recordSplit(bounded, interleaved = true)
        assertSplit(bounded)
    }

    @Test fun independentEqualRuntimeOwnersKeepTheirImageBudgets() {
        val bytes = ByteArray(256 * 256 * 4) { byteArrayOf(-1, 0, 0, -1)[it % 4] }
        fun image(id: String) = Image.fromPixels(256, 256, bytes.copyOf(), sourceId = id,
            alphaType = AlphaType.UNPREMUL)
        val shared = image("w5h-convergence-shared")
        val independent = image("w5h-convergence-independent")
        val wanted = W5fColorCpuOracle.expectedShaderTree(Shader.Opacity(Shader.SolidColor(ColorARGB.Red), .5f),
            finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        fun record(surface: Surface, second: Image) = surface.canvas {
            drawRect(unit(0f), Paint(shader = runtime(mapOf("child" to Shader.Image(shared))),
                blendMode = BlendMode.SRC, antiAlias = false))
            drawPath(Path().apply { addRect(unit(1f)) },
                Paint(shader = runtime(mapOf("child" to Shader.Image(second))),
                    blendMode = BlendMode.SRC, antiAlias = false))
        }
        val surface = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = 800_000))
        record(surface, shared)
        repeat(2) { assertTwoPixels(surface.render(), wanted) }
        val wide = Surface(2, 1, config = RenderConfig(frameLocalBudgetBytes = 1_200_000))
        record(wide, independent)
        repeat(2) { assertTwoPixels(wide.render(), wanted) }
        surface.discardRecordedOperations()
        record(surface, independent)
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals("resource-limit.w5g.composed-binding", failure.message.orEmpty().substringBefore(':'))
        }
        surface.discardRecordedOperations()
        record(surface, shared)
        repeat(2) { assertTwoPixels(surface.render(), wanted) }
    }

    @Test fun mixedPriorFamiliesStayUnderChildOpacity() {
        val stops = mutableListOf(GradientStop(0f, SOURCE), GradientStop(1f, SECOND))
        val bytes = byteArrayOf(224.toByte(), 64, 128.toByte(), 191.toByte())
        val matrix = ColorMatrixF32.ofIdentity().apply { setScale(.5f, .75f, .25f, .5f) }
        val families = listOf(
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), stops),
            Shader.Image(Image.fromPixels(1, 1, bytes, alphaType = AlphaType.UNPREMUL)),
            Shader.WithColorFilter(Shader.SolidColor(SOURCE), ColorFilter.Matrix(matrix)),
            Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(SOURCE), Shader.SolidColor(SECOND)),
            Shader.FractalNoise(.125f, .25f, 2, 7, null))
        val wanted = families.mapIndexed { index, child ->
            W5fColorCpuOracle.expectedShaderTree(Shader.Opacity(child, .5f), PAINT_ALPHA,
                devicePointF32 = Point2F32(index + .5f, .5f), finalBlend = BlendMode.SRC)
        }
        wanted.forEach(W5fSurfacePixelFixtures::requireBounded)
        val children = families.map { linkedMapOf("child" to it) }
        val shaders = children.map(::runtime)
        val surface = Surface(5, 1)
        surface.canvas {
            shaders.forEachIndexed { index, shader ->
                val paint = paint(shader).copy(blendMode = BlendMode.SRC)
                if (index % 2 == 0) drawRect(unit(index.toFloat()), paint)
                else drawPath(Path().apply { addRect(unit(index.toFloat())) }, paint)
            }
        }
        stops[0] = GradientStop(0f, ColorARGB.Green)
        bytes.fill(0)
        matrix.setScale(0f, 0f, 0f, 0f)
        children.forEach { it["child"] = Shader.SolidColor(ColorARGB.Green) }
        repeat(2) {
            val pixels = surface.render().pixels
            wanted.forEachIndexed { index, expected ->
                WgslFloatEnvelopeV1Oracle.assertAdmits(expected, pixels.copyOfRange(index * 4, index * 4 + 4))
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test fun lateSiblingRefusalDoesNotPublishAndSameSurfaceRecovers() {
        val recovered = W5hRuntimeEffectCpuOracle.attachment(
            W5hRuntimeEffectCpuOracle.opacity(W5hRuntimeEffectCpuOracle.linearPremul(SOURCE), .5))
        val shader = runtime(mapOf("child" to Shader.SolidColor(SOURCE)))
        val unregistered = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }.makeShader(UniformBlock.EMPTY)
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(unit(0f), Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false))
            drawPath(Path().apply { addRect(unit(1f)) },
                Paint(shader = unregistered, blendMode = BlendMode.SRC, antiAlias = false))
        }
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.runtime_effect.unregistered_semantics",
                failure.message.orEmpty().substringBefore(':'))
        }
        surface.discardRecordedOperations()
        surface.canvas {
            drawPath(Path().apply { addRect(unit(1f)) },
                Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false))
        }
        repeat(2) {
            val pixels = surface.render().pixels
            assertEquals(listOf(0, 0, 0, 0), pixels.take(4).map { it.toInt() })
            recovered.forEachIndexed { channel, codes -> assertTrue(pixels[4 + channel].toInt() in codes) }
        }
    }

    @Test fun pictureReplaysMixedPromotedLanesAfterCaptureMutation() {
        val wanted = laneExpectations()
        val children = linkedMapOf("child" to Shader.SolidColor(SOURCE) as Shader)
        val shader = runtime(children)
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, WIDTH.toFloat(), 1f)).everyLane(shader)
        val picture = recorder.finishRecordingAsPicture()
        children["child"] = Shader.SolidColor(ColorARGB.Green)
        // The established synthetic Text fixture supports in-memory replay. Its font
        // wire decoder may return null; no font/codec capability is added by this test.
        for (replay in listOfNotNull(picture, Picture.fromByteArray(picture.toByteArray()))) {
            val surface = Surface(WIDTH, 1)
            surface.canvas { replay.playback(this) }
            repeat(2) { assertLanePixels(surface.render(), wanted) }
        }
    }

    private fun Canvas.everyLane(shader: Shader) {
        val p = paint(shader)
        drawRect(unit(0f), p)
        drawRRect(RRectF32.of(RectF32.ofLTRB(38f, -2f, 43f, 3f), CornerRadiiF32.of(1f)), p)
        drawPath(Path().apply { addRect(unit(80f)) }, p)
        drawPath(line(120f), p.copy(style = PaintStyle.STROKE, strokeWidth = 2f))
        drawPath(line(160f), p.copy(style = PaintStyle.STROKE, strokeWidth = 0f))
        drawPoint(200.5f, .5f, p.copy(strokeWidth = 0f))
        drawPoints(PointMode.POINTS, listOf(Point2F32(240.5f, .5f)), p.copy(strokeWidth = 0f))
        drawText(TEXT, 274f, 18f, p)
        drawVertices(Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(319f, -1f), Point2F32(325f, -1f), Point2F32(319f, 5f)), indices = listOf(0, 1, 2)), p)
        drawImage(Image.fromPixels(1, 1, byteArrayOf(127), ColorType.ALPHA_8,
            alphaType = AlphaType.UNPREMUL), unit(360f), SamplingOptions.NEAREST, p)
        drawImage(Image.fromPixels(1, 1, byteArrayOf(224.toByte(), 64, 128.toByte(), 191.toByte()),
            alphaType = AlphaType.UNPREMUL), unit(400f), SamplingOptions.NEAREST, p)
    }

    private fun laneExpectations(): List<List<IntRange>> = (0..10).map { lane ->
        val factor = PAINT_ALPHA.toDouble() * when (lane) { 9 -> .5 * 127.0 / 255; 10 -> 1.0; else -> .5 }
        W5hRuntimeEffectCpuOracle.attachment(W5hRuntimeEffectCpuOracle.opacity(
            W5hRuntimeEffectCpuOracle.linearPremul(SOURCE), factor))
    }

    private fun assertLanePixels(result: RenderResult, wanted: List<List<IntRange>>) {
        wanted.forEachIndexed { lane, channels -> channels.forEachIndexed { channel, codes ->
            val observed = result.pixels[lane * 40 * 4 + channel].toInt()
            assertTrue(observed in codes, "lane=$lane channel=$channel observed=$observed expected=$codes")
        } }
    }

    private fun assertTwoPixels(result: RenderResult, wanted: WgslFloatEnvelopeV1Oracle.DrawResult) {
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted, result.pixels.copyOfRange(it * 4, it * 4 + 4)) }
    }

    private fun runtime(children: Map<String, Shader>): Shader = Shader.RuntimeEffect(
        assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
        UniformBlock { float1("alpha", .5f) }, children)
    private fun paint(shader: Shader) = Paint(color = ColorARGB.of(149, 255, 255, 255),
        shader = shader, antiAlias = false, blendMode = BlendMode.SRC_OVER)
    private fun unit(x: Float) = RectF32.ofLTRB(x, 0f, x + 1f, 1f)
    private fun line(x: Float) = Path().apply { moveTo(x - 1f, .5f); lineTo(x + 2f, .5f) }

    companion object {
        private const val WIDTH = 401
        private const val PAINT_ALPHA = 149f / 255f
        private val SOURCE = ColorARGB.of(191, 224, 64, 128)
        private val SECOND = ColorARGB.of(149, 32, 160, 224)
        private val TEXT = TextBlob(listOf(KanvasGlyphRun(
            listOf(GPUPreparedTextTestFixtures.A8_GLYPH_ID.toUShort()), listOf(Point2F32(0f, 0f)), fontSize = 48f)),
            FontTypeface(GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(), "W5h convergence coverage"), 48f)
    }
}
