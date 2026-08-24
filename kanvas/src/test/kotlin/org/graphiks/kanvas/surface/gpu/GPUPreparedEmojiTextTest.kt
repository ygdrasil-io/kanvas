package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32

class GPUPreparedEmojiTextTest {
    @Test
    fun `already shaped monochrome and COLRv0 glyphs are admitted by representation`() {
        val monochrome = assertIs<GPUPreparedTextLowering.Ready>(
            lower(listOf(41), GPUPreparedTextSourceRepresentation.OUTLINE),
        )
        val color = assertIs<GPUPreparedTextLowering.Ready>(
            lower(listOf(42), GPUPreparedTextSourceRepresentation.COLRV0),
        )

        assertEquals(
            listOf(GPUPreparedTextRepresentation.A8_MASK),
            monochrome.draw.representationPolicy.representations,
        )
        assertEquals(
            listOf(GPUPreparedTextRepresentation.COLRV0),
            color.draw.representationPolicy.representations,
        )
    }

    @Test
    fun `ZWJ sequence already reduced to one glyph id is treated as one shaped glyph`() {
        val ready = assertIs<GPUPreparedTextLowering.Ready>(
            lower(listOf(43), GPUPreparedTextSourceRepresentation.COLRV0),
        )

        assertEquals(listOf(43), ready.draw.glyphs.map { glyph -> glyph.glyphId })
        assertEquals(1, ready.draw.representationPolicy.representations.size)
    }

    @Test
    fun `unpositioned sequence input and missing font do not trigger implicit shaping or fallback`() {
        val implicitShaping = assertIs<GPUPreparedTextLowering.Refused>(
            lower(
                glyphIds = listOf(44, 45),
                representation = GPUPreparedTextSourceRepresentation.OUTLINE,
                positions = listOf(Point2F32(0f, 0f)),
            ),
        )
        val fallback = assertIs<GPUPreparedTextLowering.Refused>(
            lower(
                glyphIds = listOf(46),
                representation = GPUPreparedTextSourceRepresentation.OUTLINE,
                typefacePresent = false,
            ),
        )

        assertEquals(GPUTextRefusalCodes.POSITION_COUNT_MISMATCH, implicitShaping.code)
        assertEquals(GPUTextRefusalCodes.TYPEFACE_MISSING, fallback.code)
    }

    @Test
    fun `unproved color representations keep exact terminal refusal codes`() {
        val expected = listOf(
            GPUPreparedTextSourceRepresentation.COLRV1 to
                GPUTextRefusalCodes.COLRV1_UNPROVED,
            GPUPreparedTextSourceRepresentation.CBDT_CBLC to
                GPUTextRefusalCodes.BITMAP_CBDT_CBLC_UNSUPPORTED,
            GPUPreparedTextSourceRepresentation.SBIX to
                GPUTextRefusalCodes.BITMAP_SBIX_UNSUPPORTED,
            GPUPreparedTextSourceRepresentation.SVG to
                GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED,
        )

        expected.forEach { (representation, code) ->
            val refused = assertIs<GPUPreparedTextLowering.Refused>(
                lower(listOf(47), representation),
            )
            assertEquals(code, refused.code, representation.name)
        }
    }

    private fun lower(
        glyphIds: List<Int>,
        representation: GPUPreparedTextSourceRepresentation,
        positions: List<Point2F32> = glyphIds.indices.map { index ->
            Point2F32(index * 8f, 0f)
        },
        typefacePresent: Boolean = true,
    ): GPUPreparedTextLowering {
        val typeface = liberationTypeface()
        val default = assertIs<GPUPreparedTextFontResolution.Ready>(
            GPUPreparedFontTypefaceResolver.resolve(typeface),
        )
        val resolver = GPUPreparedTextFontResolver {
            GPUPreparedTextFontResolution.ready(
                face = default.face,
                glyphCount = default.glyphCount,
                representationResolver =
                    GPUPreparedTextGlyphRepresentationResolver { _, _, _ ->
                        representation
                    },
            )
        }
        val operation = DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = glyphIds.map(Int::toUShort),
                        positions = positions,
                        fontSize = 16f,
                    ),
                ),
                typeface = typeface.takeIf { typefacePresent },
                fontSize = 16f,
            ),
            x = 0f,
            y = 0f,
            paint = Paint.fill(ColorARGB.White),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        return GPUPreparedTextLowerer.lower(
            operation = operation,
            operationIndex = 0,
            target = target(),
            capabilities = capabilities(),
            fontResolver = resolver,
        )
    }
}
