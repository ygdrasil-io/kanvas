package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextColdFrameSamples
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point

class GPUPreparedTextPixelTest {
    @Test
    fun `A8 literal coverage applies premultiplication coverage and sRGB encoding once`() {
        val expected = listOf(
            byteArrayOf(0, 0, 0, 0),
            byteArrayOf(13, 0, 0, 1),
            byteArrayOf(188.toByte(), 0, 0, 128.toByte()),
            byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
        )

        GPUPreparedTextTestFixtures.a8CoverageLevels().forEachIndexed { index, encodedCoverage ->
            val actual = GPUPreparedTextPixelOracle.a8SourceOver(
                material = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
                paintAlpha = 1f,
                coverage = encodedCoverage.toInt() and 0xff,
            ).bytes()

            assertContentEquals(expected[index], actual, "coverage=${encodedCoverage.toInt() and 0xff}")
        }
    }

    @Test
    fun `material alpha and paint alpha are each applied once before coverage`() {
        val actual = GPUPreparedTextPixelOracle.a8SourceOver(
            material = GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255, 128),
            paintAlpha = 0.5f,
            coverage = 255,
        ).bytes()

        assertContentEquals(
            byteArrayOf(137.toByte(), 137.toByte(), 137.toByte(), 64),
            actual,
        )
    }

    @Test
    fun `paint alpha zero half and one have literal premultiplied outputs`() {
        val actual = listOf(0f, 0.5f, 1f).map { paintAlpha ->
            GPUPreparedTextPixelOracle.a8SourceOver(
                material = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
                paintAlpha = paintAlpha,
                coverage = 255,
            ).bytes()
        }

        assertContentEquals(byteArrayOf(0, 0, 0, 0), actual[0])
        assertContentEquals(byteArrayOf(188.toByte(), 0, 0, 128.toByte()), actual[1])
        assertContentEquals(byteArrayOf(255.toByte(), 0, 0, 255.toByte()), actual[2])
    }

    @Test
    fun `common prepared materials enter the same literal coverage pipeline`() {
        val evaluatedMaterialByCase = listOf(
            "solid" to GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
            "linear-gradient" to GPUPreparedTextPixelOracle.StraightSrgb(0, 255, 0),
            "radial-gradient" to GPUPreparedTextPixelOracle.StraightSrgb(0, 0, 255),
            "sweep-gradient" to GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
            "conical-gradient" to GPUPreparedTextPixelOracle.StraightSrgb(0, 0, 0),
            "registered-runtime-effect" to GPUPreparedTextPixelOracle.StraightSrgb(128, 64, 191),
            "supported-image-shader" to GPUPreparedTextPixelOracle.StraightSrgb(42, 170, 85),
        )
        val literalExpected = listOf(
            byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
            byteArrayOf(0, 255.toByte(), 0, 255.toByte()),
            byteArrayOf(0, 0, 255.toByte(), 255.toByte()),
            byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()),
            byteArrayOf(0, 0, 0, 255.toByte()),
            byteArrayOf(128.toByte(), 64, 191.toByte(), 255.toByte()),
            byteArrayOf(42, 170.toByte(), 85, 255.toByte()),
        )

        assertEquals(
            listOf(
                "solid",
                "linear-gradient",
                "radial-gradient",
                "sweep-gradient",
                "conical-gradient",
                "registered-runtime-effect",
                "supported-image-shader",
            ),
            evaluatedMaterialByCase.map { it.first },
        )
        evaluatedMaterialByCase.forEachIndexed { index, (_, material) ->
            assertContentEquals(
                literalExpected[index],
                GPUPreparedTextPixelOracle.a8SourceOver(
                    material = material,
                    paintAlpha = 1f,
                    coverage = 255,
                ).bytes(),
            )
        }
    }

    @Test
    fun `stroke cases and blur styles consume hand derived mask coverage`() {
        val strokeExpected = byteArrayOf(188.toByte(), 0, 0, 128.toByte())
        listOf("butt-miter-dash", "round-round-dash", "square-bevel").forEach { stroke ->
            assertContentEquals(
                strokeExpected,
                GPUPreparedTextPixelOracle.a8SourceOver(
                    material = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
                    paintAlpha = 1f,
                    coverage = 128,
                ).bytes(),
                stroke,
            )
        }

        val literalBlurCoverage = listOf(64, 128, 192, 255)
        val literalBlurExpected = listOf(
            byteArrayOf(137.toByte(), 0, 0, 64),
            byteArrayOf(188.toByte(), 0, 0, 128.toByte()),
            byteArrayOf(225.toByte(), 0, 0, 192.toByte()),
            byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
        )
        BlurStyle.entries.forEachIndexed { index, style ->
            assertContentEquals(
                literalBlurExpected[index],
                GPUPreparedTextPixelOracle.a8SourceOver(
                    material = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
                    paintAlpha = 1f,
                    coverage = literalBlurCoverage[index],
                ).bytes(),
                "blurStyle=$style",
            )
        }
    }

    @Test
    fun `COLRv0 begins from resolved primitive layer and uses the common blend`() {
        val actual = GPUPreparedTextPixelOracle.colorGlyphSourceOver(
            resolvedPrimitiveLayer = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
            paintAlpha = 0.5f,
            coverage = 255,
            destination = GPUPreparedTextPixelOracle.EncodedPremulSrgb(0, 0, 255, 255),
        ).bytes()

        assertContentEquals(
            byteArrayOf(188.toByte(), 0, 188.toByte(), 255.toByte()),
            actual,
        )
    }

    @Test
    fun `max channel delta reports the literal worst channel`() {
        assertEquals(
            1,
            GPUPreparedTextPixelOracle.maxChannelDelta(
                actual = byteArrayOf(188.toByte(), 1, 0, 128.toByte()),
                expected = byteArrayOf(188.toByte(), 0, 1, 128.toByte()),
            ),
        )
    }

    @Test
    fun `sole oracle composes ordered source over rectangles into one RGBA buffer`() {
        val actual = GPUPreparedTextPixelOracle.renderLayers(
            width = 2,
            height = 1,
            layers = listOf(
                GPUPreparedTextPixelOracle.Layer(
                    bounds = GPUPreparedTextPixelOracle.IntRect(0, 0, 2, 1),
                    color = GPUPreparedTextPixelOracle.StraightSrgb(255, 0, 0),
                    paintAlpha = 0.5f,
                ),
                GPUPreparedTextPixelOracle.Layer(
                    bounds = GPUPreparedTextPixelOracle.IntRect(1, 0, 2, 1),
                    color = GPUPreparedTextPixelOracle.StraightSrgb(0, 0, 255),
                    paintAlpha = 0.5f,
                ),
            ),
        )

        assertContentEquals(
            byteArrayOf(
                188.toByte(), 0, 0, 128.toByte(),
                137.toByte(), 0, 188.toByte(), 191.toByte(),
            ),
            actual,
        )
    }

    @Test
    fun `every mutable byte fixture accessor returns a fresh snapshot`() {
        val coverageFirst = GPUPreparedTextTestFixtures.a8CoverageLevels()
        val coverageSecond = GPUPreparedTextTestFixtures.a8CoverageLevels()
        coverageFirst[0] = 99
        assertNotSame(coverageFirst, coverageSecond)
        assertContentEquals(byteArrayOf(0, 1, 128.toByte(), 255.toByte()), coverageSecond)

        val diagonalFirst = GPUPreparedTextTestFixtures.diagonalAntialiasedGlyph()
        val diagonalSecond = GPUPreparedTextTestFixtures.diagonalAntialiasedGlyph()
        diagonalFirst[0] = 0
        assertNotSame(diagonalFirst, diagonalSecond)
        assertEquals(255, diagonalSecond[0].toInt() and 0xff)

        assertEquals(2, GPUPreparedTextTestFixtures.colrPaletteAndForeground().paletteArgb.size)
    }

    @Test
    fun `thirty cold frames rebuild inventories and publish all phase samples`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 13 cold-frame COLRv0",
        )
        val operations = listOf(
            text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 4, 58, Color.WHITE),
            text(typeface, GPUPreparedTextTestFixtures.COLOR_BASE_GLYPH_ID, 36, 58, Color.BLUE),
        )
        val inventoryIdentities = java.util.IdentityHashMap<PreparedTextFrameInventory, Boolean>()
        val pageIdentities =
            java.util.IdentityHashMap<org.graphiks.kanvas.glyph.gpu.GPUTextA8AtlasPageArtifact, Boolean>()
        val lowering = ArrayList<Long>()
        val raster = ArrayList<Long>()
        val packing = ArrayList<Long>()
        val total = ArrayList<Long>()

        repeat(30) { sampleIndex ->
            val prepared = assertIs<GPUPreparedTextFramePreparation.Ready>(
                GPUPreparedTextFramePreparer.prepare(
                    operations = operations,
                    target = target(),
                    config = RenderConfig.DEFAULT,
                    capabilities = capabilities(),
                    generation = GPUTextArtifactGeneration(sampleIndex + 1),
                ),
            )
            assertEquals(null, inventoryIdentities.put(prepared.inventory, true))
            prepared.inventory.pages.forEach { page ->
                assertEquals(null, pageIdentities.put(page, true))
            }
            assertEquals(1, prepared.inventory.metrics.a8InstanceCount)
            assertEquals(2, prepared.inventory.metrics.colorGlyphInstanceCount)
            assertEquals(0, prepared.inventory.metrics.pathStrokeDrawCount)
            lowering += prepared.metrics.loweringNanoseconds
            raster += prepared.metrics.rasterNanoseconds
            packing += prepared.metrics.packingNanoseconds
            total += Math.addExact(
                prepared.metrics.loweringNanoseconds,
                Math.addExact(
                    prepared.metrics.rasterNanoseconds,
                    prepared.metrics.packingNanoseconds,
                ),
            )
        }

        assertEquals(30, inventoryIdentities.size)
        assertEquals(30, pageIdentities.size)
        assertTrue(lowering.all { it > 0L })
        assertTrue(raster.all { it > 0L })
        assertTrue(packing.all { it > 0L })
        val evidence = GPUPreparedTextColdFrameSamples.from(total)
        assertEquals(30, evidence.sampleCount)
        assertEquals(14, evidence.p50Index)
        assertEquals(27, evidence.p95Index)
        println(
            "task13.cold-frame samples=${evidence.sampleCount} " +
                "loweringRawNs=${lowering.sorted()} rasterRawNs=${raster.sorted()} " +
                "packingRawNs=${packing.sorted()} totalRawNs=${evidence.sortedNanoseconds} " +
                "p50Index=${evidence.p50Index} p50Ns=${evidence.p50Nanoseconds} " +
                "p95Index=${evidence.p95Index} p95Ns=${evidence.p95Nanoseconds}",
        )
    }

    private fun text(
        typeface: FontTypeface,
        glyphId: Int,
        x: Int,
        baselineY: Int,
        color: Color,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(glyphId.toUShort()),
                    positions = listOf(Point(0f, 0f)),
                    fontSize = 48f,
                ),
            ),
            typeface = typeface,
            fontSize = 48f,
        ),
        x = x.toFloat(),
        y = baselineY.toFloat(),
        paint = Paint.fill(color),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )
}
