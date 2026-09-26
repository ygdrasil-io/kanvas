@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.W6eBlurShadowCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public Picture custody for the five spatial-core W6e filter families. */
class W6eEffectsConvergencePictureTest {
    @Test
    fun coreShardMemoryAndWireReplayAreStable() {
        // These expected pixels are complete before recording and before any Surface exists.
        val cropExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val offsetExpected = rgbaRow(listOf(transparent, blue, transparent, transparent))
        val tileExpected = rgbaRow(listOf(blue, transparent, blue, transparent))
        // Edge placement makes the captured DECAL addressing observable before either Picture
        // recorder or replay Surface is created.
        val blurExpected = W6eBlurShadowCpuOracle.blurOpaqueWhiteImpulse(7, 7, 0, 3)
        val shadowExpected = W6eBlurShadowCpuOracle.dropShadow(
            width = 7, height = 7, sourceAlpha = impulseAlpha(7, 7, 2, 3),
            sourceColor = ColorARGB.White, dx = 2f, dy = 0f, sigma = 1f,
            shadowColor = ColorARGB.Blue, mode = DropShadowMode.COMPOSITE,
        )

        val cropRect = RectF32.ofLTRB(1f, 0f, 2f, 1f)
        val tileSource = RectF32.ofLTRB(0f, 0f, 2f, 1f)
        val tileDestination = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val fixtures = listOf(
            exactFixture("Crop", 4, 1, cropExpected, ImageFilter.Crop(cropRect, TileMode.DECAL),
                RectF32.ofLTRB(1f, 0f, 2f, 1f), ColorARGB.Blue,
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.Red),
            exactFixture("Offset", 4, 1, offsetExpected, ImageFilter.Offset(1f, 0f),
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.Blue),
            exactFixture("Tile", 4, 1, tileExpected, ImageFilter.Tile(tileSource, tileDestination),
                RectF32.ofLTRB(0f, 0f, 1f, 1f), ColorARGB.Blue),
            oracleFixture("Blur", blurExpected, ImageFilter.Blur(1f, 1f, TileMode.DECAL),
                RectF32.ofLTRB(0f, 3f, 1f, 4f), ColorARGB.White),
            oracleFixture("DropShadow", shadowExpected, ImageFilter.DropShadow(
                2f, 0f, 1f, 1f, ColorARGB.Blue, mode = DropShadowMode.COMPOSITE,
            ), RectF32.ofLTRB(2f, 3f, 3f, 4f), ColorARGB.White),
        )
        val captured = fixtures.map { fixture -> fixture to fixture.record() }

        // Every public caller-owned input is changed after capture. Recorded Crop/Tile geometry,
        // and every recorded draw rectangle, must retain the immutable captured values above.
        cropRect.setLTRB(0f, 0f, 1f, 1f)
        tileSource.setLTRB(1f, 0f, 2f, 1f)
        tileDestination.setLTRB(1f, 0f, 2f, 1f)
        fixtures.forEach {
            it.sourceRect.setLTRB(0f, 0f, 0f, 0f)
            it.extraSourceRect?.setLTRB(0f, 0f, 0f, 0f)
        }

        captured.forEach { (fixture, picture) ->
            val bytes = picture.toByteArray()
            assertEquals(15, ByteBuffer.wrap(bytes).getInt(4), fixture.name)
            assertEquals(9, ByteBuffer.wrap(bytes).getInt(28), fixture.name)
            val decoded = assertNotNull(Picture.fromByteArray(bytes), fixture.name)
            assertContentEquals(bytes, decoded.toByteArray(), fixture.name)
            listOf(picture, decoded).forEach { replay -> fixture.assertPixels(render(fixture, replay)) }
        }
    }

    private fun exactFixture(
        name: String,
        width: Int,
        height: Int,
        expected: UByteArray,
        filter: ImageFilter,
        sourceRect: RectF32,
        sourceColor: ColorARGB,
        extraSourceRect: RectF32? = null,
        extraSourceColor: ColorARGB? = null,
    ): Fixture = Fixture(name, width, height, expected, maxDelta = null, filter, sourceRect, sourceColor,
        extraSourceRect, extraSourceColor)

    private fun oracleFixture(
        name: String,
        expected: UByteArray,
        filter: ImageFilter,
        sourceRect: RectF32,
        sourceColor: ColorARGB,
    ): Fixture = Fixture(name, 7, 7, expected, maxDelta = 12, filter, sourceRect, sourceColor)

    private fun render(fixture: Fixture, picture: Picture): RenderResult = Surface(fixture.width, fixture.height).also { surface ->
        surface.canvas { drawPicture(picture) }
    }.render()

    private class Fixture(
        val name: String,
        val width: Int,
        val height: Int,
        private val expected: UByteArray,
        private val maxDelta: Int?,
        private val filter: ImageFilter,
        val sourceRect: RectF32,
        private val sourceColor: ColorARGB,
        val extraSourceRect: RectF32? = null,
        private val extraSourceColor: ColorARGB? = null,
    ) {
        fun record(): Picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())).apply {
                extraSourceRect?.let { extra ->
                    drawRect(extra, Paint(requireNotNull(extraSourceColor), imageFilter = filter, antiAlias = false))
                }
                drawRect(sourceRect, Paint(sourceColor, imageFilter = filter, antiAlias = false))
            }
        }.finishRecordingAsPicture()

        fun assertPixels(result: RenderResult) {
            if (maxDelta == null) assertContentEquals(expected, result.pixels, name)
            else W6eBlurShadowCpuOracle.assertNear(expected, result.pixels, maxDelta)
            assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "$name: ${result.nativeEvidenceScopeKinds}")
        }
    }

    private fun impulseAlpha(width: Int, height: Int, x: Int, y: Int): UByteArray =
        UByteArray(width * height).also { it[x + y * width] = 255u }

    private fun rgbaRow(pixels: List<UByteArray>): UByteArray = pixels.flatMap { it.asList() }.toUByteArray()

    private companion object {
        val transparent = ubyteArrayOf(0u, 0u, 0u, 0u)
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
    }
}
