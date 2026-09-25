@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorChannel
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/**
 * Public W6d atomic-visibility witnesses.  B is derived before a [Surface] exists:
 * one RGBA8 root texel (4), the explicit layer target (4), the captured draw source
 * and layer source (2 × 4), and one frozen MatrixConvolution FilterTarget texel (4),
 * a 16-byte W6 draw record plus a 16-byte frozen matrix-operation uniform record, and
 * one aligned RGBA8 readback row (256). Program and sampler selection is frozen metadata
 * for this registered operation and does not allocate a separate resource.
 */
class W6dAdvancedRecoverySurfacePixelTest {
    @Test
    fun f16RequestsRefuseWithExactCapabilityBeforeReadback() {
        val surface = Surface(
            1,
            1,
            config = RenderConfig(gpuColorFormat = GPUColorFormat.RGBA16_FLOAT),
        )
        surface.canvas {
            drawRect(unit, Paint(
                ColorARGB.of(255, 43, 181, 93),
                imageFilter = identityMatrixConvolution(),
                antiAlias = false,
            ))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6d.layer.unsupported_target_format:")
    }

    @Test
    fun `exact budget accepts and one byte less refuses without readback publication then recovers`() {
        val expected = rgba(43, 181, 93)

        val accepted = advancedWitnessSurface(W6D_BUDGET_BYTES)
        val acceptedResult = accepted.render()
        assertContentEquals(expected, acceptedResult.pixels)
        assertTrue(acceptedResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))

        val refused = advancedWitnessSurface(W6D_BUDGET_BYTES - 1L)
        assertTerminalWithoutReadbackMutation(refused, "w6d.layer.frame_budget_exceeded:")
        refused.discardRecordedOperations()
        refused.canvas { drawRect(unit, Paint(ColorARGB.of(255, 43, 181, 93), antiAlias = false)) }
        val recovered = refused.render()
        assertContentEquals(expected, recovered.pixels)
        assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Test
    fun `warm replay remains pessimistically charged at the frozen B budget`() {
        val expected = rgba(43, 181, 93)
        val surface = advancedWitnessSurface(W6D_BUDGET_BYTES)

        val cold = surface.render()
        assertContentEquals(expected, cold.pixels)
        assertTrue(cold.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        surface.discardRecordedOperations()
        surface.canvas { recordAdvancedWitness(this) }
        val warm = surface.render()
        assertContentEquals(expected, warm.pixels)
        assertTrue(warm.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))

        assertTerminalWithoutReadbackMutation(
            advancedWitnessSurface(W6D_BUDGET_BYTES - 1L),
            "w6d.layer.frame_budget_exceeded:",
        )
    }

    @Test
    fun `late advanced sibling refusal publishes neither sibling and same surface recovers`() {
        val expected = rgba(43, 181, 93)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W6D_BUDGET_BYTES))
        surface.canvas {
            recordAdvancedWitness(this)
            recordAdvancedWitness(this)
        }

        assertTerminalWithoutReadbackMutation(surface, "w6d.layer.frame_budget_exceeded:")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(unit, Paint(ColorARGB.of(255, 43, 181, 93), antiAlias = false)) }
        val recovered = surface.render()
        assertContentEquals(expected, recovered.pixels)
        assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    @Test
    fun `one frozen graph covers all eleven W6d filter families`() {
        val expected = rgba(0, 0, 0)
        val surface = Surface(1, 1)
        val allFamilies = allElevenFamilyGraph()
        surface.canvas {
            drawRect(unit, Paint(ColorARGB.White, imageFilter = allFamilies, antiAlias = false))
        }

        val result = surface.render()
        assertContentEquals(expected, result.pixels)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
    }

    private fun advancedWitnessSurface(frameLocalBudgetBytes: Long): Surface =
        Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = frameLocalBudgetBytes)).also { surface ->
            surface.canvas { recordAdvancedWitness(this) }
        }

    private fun recordAdvancedWitness(canvas: Canvas) {
        canvas.saveLayer(SaveLayerRec(paint = Paint(imageFilter = identityMatrixConvolution(), antiAlias = false)))
        canvas.drawRect(unit, Paint(ColorARGB.of(255, 43, 181, 93), antiAlias = false))
        canvas.restore()
    }

    private fun allElevenFamilyGraph(): ImageFilter {
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(unit).drawRect(unit, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        return ImageFilter.ColorFilter(
            ColorFilter.Blend(ColorARGB.Black, BlendMode.SRC),
            ImageFilter.Merge(listOf(
                identityMatrixConvolution(),
                ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 0f, ImageFilter.Offset(0f, 0f)),
                ImageFilter.Magnifier(unit, zoom = 1f, inset = 0f),
                ImageFilter.Picture(picture),
                ImageFilter.RuntimeEffect(
                    requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
                    UniformBlock { float1("alpha", 1f) },
                ),
                ImageFilter.DistantLitDiffuse(Vector3F32(0f, 0f, 1f), ColorARGB.White, 1f, 1f),
                ImageFilter.PointLitDiffuse(Point3F32(0f, 0f, 1f), ColorARGB.White, 1f, 1f),
                ImageFilter.SpotLitDiffuse(
                    Point3F32(0f, 0f, 1f), Point3F32(0f, 0f, 0f), 1f, 90f, ColorARGB.White, 1f, 1f,
                ),
                ImageFilter.DistantLitSpecular(Vector3F32(0f, 0f, 1f), ColorARGB.White, 1f, 1f, 1f),
                ImageFilter.PointLitSpecular(Point3F32(0f, 0f, 1f), ColorARGB.White, 1f, 1f, 1f),
                ImageFilter.SpotLitSpecular(
                    Point3F32(0f, 0f, 1f), Point3F32(0f, 0f, 0f), 1f, 90f, ColorARGB.White, 1f, 1f, 1f,
                ),
            )),
        )
    }

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, diagnosticPrefix: String) {
        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(unit, sentinel) }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun rgba(red: Int, green: Int, blue: Int): UByteArray = ubyteArrayOf(
        red.toUByte(), green.toUByte(), blue.toUByte(), 255u,
    )

    private fun identityMatrixConvolution(): ImageFilter.MatrixConvolution = ImageFilter.MatrixConvolution(
        SizeF32.of(1f, 1f), floatArrayOf(1f), 1f, 0f, Vector2F32(0f, 0f), TileMode.CLAMP, true,
    )

    private companion object {
        const val W6D_BUDGET_BYTES: Long = 4L + 4L + 4L + 4L + 4L + 16L + 16L + 256L
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }
}
