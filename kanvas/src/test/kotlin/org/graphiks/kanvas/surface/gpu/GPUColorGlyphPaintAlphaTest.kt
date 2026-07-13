package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

@OptIn(ExperimentalUnsignedTypes::class)
class GPUColorGlyphPaintAlphaTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `CPAL layer alpha is multiplied by partial paint alpha`() {
        val cpalLayer = Color.fromArgb(192, 32, 96, 224)
        val paint = Color.fromArgb(128, 255, 255, 255)

        val modulated = modulateCpalLayerAlpha(cpalLayer, paint)

        assertEquals(32, modulated.redByte)
        assertEquals(96, modulated.greenByte)
        assertEquals(224, modulated.blueByte)
        assertEquals(96, modulated.alphaByte)
    }

    @Test
    fun `color glyph geometry coverage is white and opaque`() {
        val cpalLayer = Color.fromArgb(96, 32, 96, 224)

        assertEquals(
            Color.WHITE,
            colorGlyphSourceColor(cpalLayer, geometryCoverage = true),
        )
        assertEquals(
            cpalLayer,
            colorGlyphSourceColor(cpalLayer, geometryCoverage = false),
        )
    }

    @Test
    fun `WebGPU COLRv0 CPAL glyph keeps paint alpha in S and geometry in G`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        val telemetryBefore = session!!.runtimeTelemetry
        val fixture = loadSkiaColrV0Fixture()
        val opaque = renderFixtureGlyph(fixture, paintAlpha = 255)
        val partial = renderFixtureGlyph(fixture, paintAlpha = 128)
        val telemetryAfter = GPUBackendRuntimeFactory.createOrNull()!!.runtimeTelemetry

        listOf(opaque, partial).forEach { result ->
            assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
            assertEquals(0, result.stats.opsRefused, result.diagnostics.entries.toString())
            assertTrue(
                result.diagnostics.entries.any { entry ->
                    entry.code.startsWith("route:destination-read:DrawText") &&
                        entry.reason == "gpu-copy-then-formula"
                },
                result.diagnostics.entries.toString(),
            )
        }
        assertEquals(
            telemetryBefore.destinationReadbackSnapshots,
            telemetryAfter.destinationReadbackSnapshots,
        )

        val interior = findOpaquePalettePixel(opaque.pixels, fixture.cpalColor)
        assertPixelNear(opaque.pixels, interior, fixture.cpalColor, tolerance = 2)
        // This pixel belongs to the opaque-red CPAL layer. Its coverage is one, so a partial
        // DrawText paint must halve S alpha only. A CPAL alpha leak into G would quarter it.
        assertPixelNear(
            partial.pixels,
            interior,
            premultipliedSrgb(
                modulateCpalLayerAlpha(fixture.cpalColor, Color.fromArgb(128, 255, 255, 255)),
            ),
            tolerance = 2,
        )
    }

    private data class FixtureGlyph(
        val typeface: FontTypeface,
        val glyphId: Int,
        val cpalColor: Color,
    )

    private fun loadSkiaColrV0Fixture(): FixtureGlyph {
        val typeface = FontTypeface(
            javaClass.classLoader.getResourceAsStream("fonts/skia/colr.ttf")!!.readBytes(),
            fontName = "Skia COLRv0 test font",
        )
        val scaler = GlyphScaler.fromBytes(typeface.fontBytes)
        assertTrue(scaler.hasAnyColorTable, "Skia COLRv0 fixture must expose a color table")
        val representation = scaler.getGlyphRepresentation(2, FIXTURE_FONT_SIZE)
            as? GlyphRepresentation.ColorLayers
        assertNotNull(representation, "Skia COLRv0 fixture glyph 2 must expose color layers")
        val colorLayers = requireNotNull(representation)
        val redLayer =
            colorLayers.layers.firstOrNull { layer ->
                layer.paletteColorArgb == 0xFFFF2A2A.toInt()
            }
        assertNotNull(redLayer, "Skia COLRv0 fixture glyph 2 must carry its opaque-red CPAL layer")
        assertEquals(7, requireNotNull(redLayer).glyphId)
        return FixtureGlyph(
            typeface = typeface,
            glyphId = 2,
            cpalColor = Color.fromArgb(255, 255, 42, 42),
        )
    }

    private fun renderFixtureGlyph(fixture: FixtureGlyph, paintAlpha: Int) =
        Surface(FIXTURE_DIMENSION, FIXTURE_DIMENSION).run {
            canvas {
                save()
                // A fractional AA device clip deliberately selects the alpha-mask S/G route.
                clipRect(Rect(0.5f, 0.5f, 191.5f, 191.5f), ClipOp.INTERSECT, antiAlias = true)
                drawText(
                    TextBlob(
                        glyphRuns = listOf(
                            KanvasGlyphRun(
                                glyphs = listOf(fixture.glyphId.toUShort()),
                                positions = listOf(Point(48f, 144f)),
                                fontSize = FIXTURE_FONT_SIZE,
                            ),
                        ),
                        typeface = fixture.typeface,
                        fontSize = FIXTURE_FONT_SIZE,
                    ),
                    0f,
                    0f,
                    Paint.fill(Color.fromArgb(paintAlpha, 255, 255, 255)).copy(
                        blendMode = BlendMode.COLOR_DODGE,
                    ),
                )
                restore()
            }
            render()
        }

    private fun findOpaquePalettePixel(pixels: UByteArray, expected: Color): Int =
        (0 until FIXTURE_DIMENSION * FIXTURE_DIMENSION).firstOrNull { index ->
            val offset = index * 4
            (pixels[offset].toInt() and 0xFF) == expected.redByte &&
                (pixels[offset + 1].toInt() and 0xFF) == expected.greenByte &&
                (pixels[offset + 2].toInt() and 0xFF) == expected.blueByte &&
                (pixels[offset + 3].toInt() and 0xFF) == expected.alphaByte
        } ?: error("fixture glyph produced no opaque CPAL pixel for $expected")

    private fun assertPixelNear(pixels: UByteArray, index: Int, expected: Color, tolerance: Int) {
        val offset = index * 4
        val actual = List(4) { channel -> pixels[offset + channel].toInt() and 0xFF }
        val wanted = listOf(expected.redByte, expected.greenByte, expected.blueByte, expected.alphaByte)
        actual.zip(wanted).forEachIndexed { channel, (value, target) ->
            assertTrue(
                kotlin.math.abs(value - target) <= tolerance,
                "pixel=$index channel=$channel expected=$target actual=$value tolerance=$tolerance",
            )
        }
    }

    /** RGBA8 sRGB readback stores premultiplied linear color, then transfers it to sRGB. */
    private fun premultipliedSrgb(color: Color): Color {
        fun srgbToLinear(component: Int): Float {
            val normalized = component / 255f
            return if (normalized <= 0.04045f) normalized / 12.92f
            else ((normalized + 0.055f) / 1.055f).pow(2.4f)
        }
        fun linearToSrgb(component: Float): Int {
            val normalized = component.coerceIn(0f, 1f)
            val srgb = if (normalized <= 0.0031308f) normalized * 12.92f
            else 1.055f * normalized.pow(1f / 2.4f) - 0.055f
            return (srgb * 255f).toInt().coerceIn(0, 255)
        }
        return Color.fromArgb(
            color.alphaByte,
            linearToSrgb(srgbToLinear(color.redByte) * color.a),
            linearToSrgb(srgbToLinear(color.greenByte) * color.a),
            linearToSrgb(srgbToLinear(color.blueByte) * color.a),
        )
    }

    private companion object {
        const val FIXTURE_DIMENSION = 192
        const val FIXTURE_FONT_SIZE = 96f
    }
}
