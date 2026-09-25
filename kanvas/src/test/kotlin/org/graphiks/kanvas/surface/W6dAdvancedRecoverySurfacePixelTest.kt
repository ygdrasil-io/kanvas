@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ColorChannel
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
 * a 16-byte W6 draw record plus a 16-byte frozen matrix-operation uniform record, one
 * 4096-byte frozen W6d program logical lease, and one aligned RGBA8 readback row (256).
 * The program lease is an immutable owner/program/binding record held through frame
 * completion, charged before native publication even on a warm cache hit; it is not a
 * claim about opaque driver pipeline allocation.
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
    fun `independent frozen graphs cover all eleven W6d filter families with branch-sensitive output`() {
        // This independent composite oracle is complete before either public recording object is
        // created. Each family owns a separate public 3x3 surface, so no later allocation can
        // mask a sparse source result from a previous family.
        val expected = allElevenFamilyExpectedPixels()
        val redPicture = allFamilyPicture(ColorARGB.Red)
        val baseline = renderAllElevenFamilies(redPicture)
        assertAllElevenFamilyBands(expected, baseline)

        familyNames.indices.forEach { familyIndex ->
            val mutationPicture = if (familyIndex == pictureFamilyIndex) allFamilyPicture(ColorARGB.Blue) else redPicture
            val mutated = renderAllElevenFamilies(mutationPicture, mutedFamilyIndex = familyIndex)
            assertBandChanged(baseline, mutated, familyIndex, familyNames[familyIndex])
        }
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

    private fun renderAllElevenFamilies(picture: org.graphiks.kanvas.picture.Picture, mutedFamilyIndex: Int? = null): UByteArray {
        val combined = UByteArray(familyWidthI32 * familyHeightI32 * familyNames.size * 4)
        familyNames.indices.forEach { familyIndex ->
            val surface = Surface(familyWidthI32, familyHeightI32)
            surface.canvas {
                val filter = allFamilyFilter(familyIndex, picture, mutedFamilyIndex == familyIndex)
                when (familyIndex) {
                    pictureFamilyIndex -> {
                        // Picture is a filter-owned aggregate and therefore keeps its captured carrier draw.
                        drawRect(unit, Paint(ColorARGB.Blue, imageFilter = filter, antiAlias = false))
                    }
                    matrixFamilyIndex -> {
                        // Fill the Matrix source band before freezing it. A sparse source did
                        // not make this family observable without a later allocation reuse.
                        saveLayer(SaveLayerRec(bounds = familyRect, paint = Paint(imageFilter = filter, antiAlias = false)))
                        drawRect(familyRect, Paint(ColorARGB.Red, antiAlias = false))
                        restore()
                    }
                    else -> {
                        saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
                        drawAllFamilySource(this, familyIndex)
                        restore()
                    }
                }
            }
            val rendered = surface.render()
            assertTrue(rendered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
            rendered.pixels.copyInto(combined, familyIndex * familyWidthI32 * familyHeightI32 * 4)
        }
        return combined
    }

    private fun allFamilyFilter(familyIndex: Int, picture: org.graphiks.kanvas.picture.Picture, muted: Boolean): ImageFilter = when (familyIndex) {
        matrixFamilyIndex -> ImageFilter.MatrixConvolution(
            SizeF32.of(1f, 1f), floatArrayOf(if (muted) 0f else 1f), 1f, 0f,
            Vector2F32(0f, 0f), TileMode.CLAMP, true,
        )
        displacementFamilyIndex -> ImageFilter.DisplacementMap(
            ColorChannel.R, ColorChannel.G, if (muted) 100f else 0f, ImageFilter.Offset(0f, 0f),
        )
        magnifierFamilyIndex -> ImageFilter.Magnifier(
            RectF32.ofLTRB(-1f, 0f, 3f, 1f), zoom = if (muted) 2f else 1f, inset = .5f,
        )
        pictureFamilyIndex -> ImageFilter.Picture(picture)
        runtimeFamilyIndex -> ImageFilter.RuntimeEffect(
            requireNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1)),
            UniformBlock { float1("alpha", if (muted) 0f else 1f) },
        )
        distantDiffuseFamilyIndex -> ImageFilter.DistantLitDiffuse(
            if (muted) Vector3F32(0f, 0f, 0f) else Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f,
        )
        pointDiffuseFamilyIndex -> ImageFilter.PointLitDiffuse(
            Point3F32(1f, 0f, if (muted) 100f else 1f), ColorARGB.White, 1f, 1f,
        )
        spotDiffuseFamilyIndex -> ImageFilter.SpotLitDiffuse(
            Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, if (muted) 2f else 0f), 1f, 90f,
            ColorARGB.White, 1f, 1f,
        )
        distantSpecularFamilyIndex -> ImageFilter.DistantLitSpecular(
            if (muted) Vector3F32(0f, 0f, -1f) else Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f,
        )
        pointSpecularFamilyIndex -> ImageFilter.PointLitSpecular(
            Point3F32(1f, 0f, if (muted) 100f else 1f), ColorARGB.White, 1f, 1f, 2f,
        )
        spotSpecularFamilyIndex -> ImageFilter.SpotLitSpecular(
            Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, if (muted) 2f else 0f), 1f, 90f,
            ColorARGB.White, 1f, 1f, 2f,
        )
        else -> error("Unknown W6d family index $familyIndex")
    }

    private fun drawAllFamilySource(canvas: Canvas, familyIndex: Int) {
        when (familyIndex) {
            matrixFamilyIndex -> (0 until familyHeightI32).forEach { y -> (0 until familyWidthI32).forEach { x ->
                canvas.drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.Red, antiAlias = false))
            } }
            displacementFamilyIndex,
            magnifierFamilyIndex,
            -> (0 until familyHeightI32).forEach { y -> listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue).forEachIndexed { x, color ->
                canvas.drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(color, antiAlias = false))
            }
            }
            distantDiffuseFamilyIndex,
            pointDiffuseFamilyIndex,
            spotDiffuseFamilyIndex,
            distantSpecularFamilyIndex,
            pointSpecularFamilyIndex,
            spotSpecularFamilyIndex,
            -> lightingFixtureCoordinates.forEach { (x, y) ->
                canvas.drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            else -> canvas.drawRect(unit, Paint(ColorARGB.Red, antiAlias = false))
        }
    }

    private fun allElevenFamilyExpectedPixels(): UByteArray = UByteArray(familyWidthI32 * familyHeightI32 * familyNames.size * 4).also { expected ->
        fun putBand(familyIndex: Int, pixels: UByteArray) = pixels.copyInto(expected, familyIndex * familyWidthI32 * familyHeightI32 * 4)
        val redPixel = rgba(255, 0, 0)
        putBand(matrixFamilyIndex, UByteArray(familyWidthI32 * familyHeightI32 * 4).also { band ->
            (0 until familyWidthI32 * familyHeightI32).forEach { redPixel.copyInto(band, it * 4) }
        })
        putBand(displacementFamilyIndex, samplingBand(
            W6dAdvancedSamplingCpuOracle.displacementRedNearestClamp(samplingSourcePixels, scale = 0f),
        ))
        putBand(magnifierFamilyIndex, samplingBand(samplingSourcePixels))
        putBand(pictureFamilyIndex, bandWithTopRow(listOf(redPixel)))
        putBand(runtimeFamilyIndex, bandWithTopRow(listOf(redPixel)))
        putBand(distantDiffuseFamilyIndex, W6dLightingCpuOracle.distantDiffuseRgba8(
            3, 3, lightingAlphaFixture, 0, 0, 3, 3, 1f, 0f, 1f, 1f, 1f,
        ))
        putBand(pointDiffuseFamilyIndex, remainingLightingExpected(W6dLightingCpuOracle.Family.POINT_DIFFUSE))
        putBand(spotDiffuseFamilyIndex, remainingLightingExpected(W6dLightingCpuOracle.Family.SPOT_DIFFUSE))
        putBand(distantSpecularFamilyIndex, remainingLightingExpected(W6dLightingCpuOracle.Family.DISTANT_SPECULAR))
        putBand(pointSpecularFamilyIndex, remainingLightingExpected(W6dLightingCpuOracle.Family.POINT_SPECULAR))
        putBand(spotSpecularFamilyIndex, remainingLightingExpected(W6dLightingCpuOracle.Family.SPOT_SPECULAR))
    }

    private fun remainingLightingExpected(family: W6dLightingCpuOracle.Family): UByteArray =
        W6dLightingCpuOracle.remainingFamilyRgba8(
            family, 3, 3, lightingAlphaFixture,
            locationX = 1f, locationY = 0f, locationZ = 1f,
            targetX = 1f, targetY = 0f, targetZ = 0f,
            surfaceDepth = 1f, coefficient = 1f, shininess = 2f, specularExponent = 1f, cutoffDegrees = 90f,
        )

    private fun bandWithTopRow(pixels: List<UByteArray>): UByteArray = UByteArray(familyWidthI32 * familyHeightI32 * 4).also { band ->
        pixels.forEachIndexed { x, pixel -> pixel.copyInto(band, x * 4) }
    }

    private fun samplingBand(row: UByteArray): UByteArray = UByteArray(familyWidthI32 * familyHeightI32 * 4).also { band ->
        (0 until familyHeightI32).forEach { y -> row.copyInto(band, y * familyWidthI32 * 4) }
    }

    private fun allFamilyPicture(color: ColorARGB): org.graphiks.kanvas.picture.Picture = PictureRecorder().also { recorder ->
        recorder.beginRecording(unit).drawRect(unit, Paint(color, antiAlias = false))
    }.finishRecordingAsPicture()

    private fun assertPixelsNear(expected: UByteArray, actual: UByteArray, maxChannelDelta: Int) {
        assertTrue(expected.size == actual.size)
        expected.indices.forEach { index ->
            assertTrue(abs(expected[index].toInt() - actual[index].toInt()) <= maxChannelDelta,
                "pixel ${index / 4} channel ${index % 4} expected=${expected[index]} actual=${actual[index]} " +
                    "expectedRgba=${expected.copyOfRange(index / 4 * 4, index / 4 * 4 + 4).contentToString()} " +
                    "actualRgba=${actual.copyOfRange(index / 4 * 4, index / 4 * 4 + 4).contentToString()}")
        }
    }

    /** Exact-copy families stay byte-exact; only numerical sampling/lighting keep their local tolerance. */
    private fun assertAllElevenFamilyBands(expected: UByteArray, actual: UByteArray) {
        assertTrue(expected.size == actual.size)
        familyNames.indices.forEach { familyIndex ->
            val start = familyIndex * familyWidthI32 * familyHeightI32 * 4
            val end = start + familyWidthI32 * familyHeightI32 * 4
            val tolerance = when (familyIndex) {
                pictureFamilyIndex, runtimeFamilyIndex -> 0
                matrixFamilyIndex, displacementFamilyIndex, magnifierFamilyIndex -> 1
                else -> 2
            }
            assertPixelsNear(expected.copyOfRange(start, end), actual.copyOfRange(start, end), tolerance)
        }
    }

    private fun assertBandChanged(before: UByteArray, after: UByteArray, familyIndex: Int, familyName: String) {
        val start = familyIndex * familyWidthI32 * familyHeightI32 * 4
        val end = start + familyWidthI32 * familyHeightI32 * 4
        assertTrue((start until end).any { abs(before[it].toInt() - after[it].toInt()) > 2 },
            "$familyName mutation did not change its public output band")
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
        const val W6D_BUDGET_BYTES: Long = 4L + 4L + 4L + 4L + 4L + 16L + 16L + 4096L + 256L
        val unit: RectF32 = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        const val familyWidthI32: Int = 3
        const val familyHeightI32: Int = 3
        val familyRect: RectF32 = RectF32.ofLTRB(0f, 0f, familyWidthI32.toFloat(), familyHeightI32.toFloat())
        const val matrixFamilyIndex: Int = 0
        const val displacementFamilyIndex: Int = 1
        const val magnifierFamilyIndex: Int = 2
        const val pictureFamilyIndex: Int = 3
        const val runtimeFamilyIndex: Int = 4
        const val distantDiffuseFamilyIndex: Int = 5
        const val pointDiffuseFamilyIndex: Int = 6
        const val spotDiffuseFamilyIndex: Int = 7
        const val distantSpecularFamilyIndex: Int = 8
        const val pointSpecularFamilyIndex: Int = 9
        const val spotSpecularFamilyIndex: Int = 10
        val familyNames: List<String> = listOf(
            "MatrixConvolution", "DisplacementMap", "Magnifier", "Picture", "RuntimeEffect",
            "DistantLitDiffuse", "PointLitDiffuse", "SpotLitDiffuse", "DistantLitSpecular",
            "PointLitSpecular", "SpotLitSpecular",
        )
        val lightingAlphaFixture: FloatArray = floatArrayOf(
            0f, 1f, 0f,
            1f, 1f, 0f,
            0f, 1f, 0f,
        )
        val samplingSourcePixels: UByteArray = ubyteArrayOf(
            255u, 0u, 0u, 255u,
            0u, 255u, 0u, 255u,
            0u, 0u, 255u, 255u,
        )
        val lightingFixtureCoordinates: List<Pair<Int, Int>> = listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2)
    }
}
