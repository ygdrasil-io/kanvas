@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.SceneRecordingValidationException
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W5fConvergenceSurfacePixelTest {
    @ParameterizedTest(name = "default image matching explicit hard-edge Paint: {0}")
    @ValueSource(booleans = [false, true])
    fun defaultImagePaintRetainsPublicPixels(explicitPaint: Boolean) {
        val image = Image.fromPixels(1,1,byteArrayOf(1,2,3,4))
        val wanted = W5fColorCpuOracle.expectedImagePixel(image, SamplingOptions.NEAREST,
            Point2F32(.5f,.5f), Paint(), finalBlend = BlendMode.SRC_OVER)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val surface = Surface(2,2)
        surface.canvas { drawImage(image, RectF32.ofLTRB(0f,0f,2f,2f), SamplingOptions.NEAREST,
            if (explicitPaint) Paint(antiAlias = false) else null) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), List(4) { wanted }) }
    }

    @ParameterizedTest(name = "Surface and Picture retain image and {0} payload")
    @ValueSource(strings = ["Matrix", "Table", "HSLAMatrix"])
    fun callerPayloadMutationDoesNotChangeRetainedImage(name: String) {
        val pixels = byteArrayOf(0, -1, 0, -128)
        val image = Image.fromPixels(1, 1, pixels, alphaType = AlphaType.UNPREMUL)
        val values = floatArrayOf(1f,0f,0f,0f,.125f, 0f,.5f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,.5f,.25f)
        val matrix = ColorMatrixF32.of(values)
        val table = UByteArray(256) { (255 - it).toUByte() }
        val filter = when (name) {
            "Matrix" -> ColorFilter.Matrix(matrix)
            "Table" -> ColorFilter.Table(table)
            else -> ColorFilter.HSLAMatrix(values)
        }
        val paint = Paint(color = ColorARGB.of(127,255,255,255), colorFilter = filter,
            blendMode = BlendMode.SRC, antiAlias = false)
        fun expected(source: Image, sourcePaint: Paint) = W5fColorCpuOracle.expectedImagePixel(
            source, SamplingOptions.NEAREST, Point2F32(.5f,.5f), sourcePaint, finalBlend = BlendMode.SRC)
        val wanted = expected(image, paint)
        val changedFilter = when (name) {
            "Matrix" -> ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20)))
            "Table" -> ColorFilter.Table(UByteArray(256))
            else -> ColorFilter.HSLAMatrix(FloatArray(20))
        }
        disjoint(wanted, expected(image, paint.copy(colorFilter = changedFilter)))
        disjoint(wanted, expected(Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1),
            alphaType = AlphaType.UNPREMUL), paint))
        val surface = Surface(1,1)
        surface.canvas { drawImage(image, rect(), SamplingOptions.NEAREST, paint) }
        val recorder = PictureRecorder()
        recorder.beginRecording(rect()).drawImage(image, rect(), SamplingOptions.NEAREST, paint)
        val picture = recorder.finishRecordingAsPicture()
        matrix.setRowMajor(FloatArray(20)); values.fill(0f); table.fill(0u); pixels.fill(0)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(wanted)) }
        val restored = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        for (replay in listOf(picture, restored)) {
            val target = Surface(1,1)
            target.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(), listOf(wanted)) }
        }
    }

    @Test fun invalidFilterCapturesLeaveTheSameSurfaceOperational() {
        val identity = ColorFilter.Matrix(ColorMatrixF32.ofIdentity())
        val wanted = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Red, identity, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val invalid = mutableListOf<Pair<ColorFilter, String>>()
        for (t in listOf(-.25f, 1.25f, Float.NaN, Float.POSITIVE_INFINITY)) {
            invalid += ColorFilter.Lerp(t, identity, identity) to
                if (t.isFinite()) "invalid.material.filter.lerp" else "non-finite-value"
        }
        for (size in listOf(255, 257)) invalid += ColorFilter.Table(UByteArray(size)) to "invalid.material.filter.table"
        for (size in listOf(19, 21)) invalid += ColorFilter.HSLAMatrix(FloatArray(size)) to "invalid.material.filter.hsla"
        for (value in listOf(Float.NaN, Float.NEGATIVE_INFINITY)) {
            invalid += ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply {
                setRowMajor(FloatArray(20).apply { this[4] = value })
            }) to "non-finite-value"
            invalid += ColorFilter.HSLAMatrix(FloatArray(20).apply { this[4] = value }) to "non-finite-value"
        }
        var deep: ColorFilter = identity
        repeat(65) { deep = ColorFilter.Compose(identity, deep) }
        invalid += deep to "graph-depth-limit"
        fun tree(depth: Int): ColorFilter = if (depth == 0) ColorFilter.Matrix(ColorMatrixF32.ofIdentity())
            else ColorFilter.Compose(tree(depth - 1), tree(depth - 1))
        invalid += tree(12) to "graph-node-limit"
        val surface = Surface(1,1)
        for ((filter, code) in invalid) {
            val failure = assertFailsWith<SceneRecordingValidationException> {
                surface.canvas { drawRect(rect(), Paint(colorFilter = filter, antiAlias = false)) }
            }
            assertEquals(code, failure.diagnostic.code.value)
            surface.canvas { drawRect(rect(), Paint(color = ColorARGB.Red, colorFilter = identity,
                blendMode = BlendMode.SRC, antiAlias = false)) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(wanted)) }
        }
    }

    @Test fun dynamicFilterAllocationRefusesCausallyAndSameRuntimeRecovers() {
        fun constant(redF32: Float) = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,redF32, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        fun filter(indexI32: Int, expanded: Boolean): ColorFilter {
            var value: ColorFilter = constant(2f + indexI32 / 128f)
            if (expanded) repeat(3) { value = ColorFilter.Compose(constant(2f), value) }
            return value
        }
        val wanted = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black, filter(63,true), finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        // The established causal window retains the same geometry and distinct
        // source count: only the three additional 80-byte Matrix records differ.
        fun frame(budgetI64: Long, expanded: Boolean) =
            Surface(29,1,config = RenderConfig(frameLocalBudgetBytes = budgetI64)).also { surface ->
                surface.canvas { repeat(64) { indexI32 ->
                    val paint = Paint(color = ColorARGB.Black, colorFilter = filter(indexI32,expanded),
                        blendMode = BlendMode.SRC, antiAlias = false)
                    if (indexI32 % 2 == 0) drawRect(rect(29f),paint)
                    else drawPath(Path().apply { moveTo(-10f,-10f); lineTo(80f,-10f); lineTo(-10f,80f); close() },paint)
                } }
            }
        val healthy = frame(1L shl 23,true)
        fun check(surface: Surface) = W5fSurfacePixelFixtures.assertNativePixels(surface.render(), List(29) { wanted })
        check(frame(1_585_000L,false))
        check(healthy)
        val rejected = frame(1_585_000L,true)
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { rejected.render() }
            assertEquals("budget.w5f.filter-uniform", failure.message.orEmpty().substringBefore(':'))
            check(healthy)
        }
    }

    private fun rect(widthF32: Float = 1f) = RectF32.ofLTRB(0f,0f,widthF32,1f)
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult, b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() })
    }
}
