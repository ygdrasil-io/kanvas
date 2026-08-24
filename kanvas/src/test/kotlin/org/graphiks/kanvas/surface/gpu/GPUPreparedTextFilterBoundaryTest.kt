package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPreparedTextFilterBoundaryTest {
    @Test
    fun `text image filter is terminal until FP-07`() {
        val operation = text(
            Paint.fill(ColorARGB.Red).copy(
                imageFilter = ImageFilter.Blur(sigmaX = 2f, sigmaY = 3f),
            ),
        )

        val refused = assertIs<GPUPreparedTextLowering.Refused>(
            GPUPreparedTextLowerer.lower(operation, 0, target(), capabilities()),
        )
        assertEquals(GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE, refused.code)

        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(operation),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
        )
        assertEquals(GPUTextRefusalCodes.IMAGE_FILTER_REQUIRES_COMPOSITE, inventory.preparedRefusal?.code)
        assertEquals(emptyList(), inventory.visualCommands)
    }

    @Test
    fun `unsupported text mask filters use the common terminal code without legacy`() {
        val filters = listOf<MaskFilter>(
            MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)),
            MaskFilter.Table(UByteArray(256) { it.toUByte() }),
        )

        filters.forEach { filter ->
            val operation = text(Paint.fill(ColorARGB.Red).copy(maskFilter = filter))
            val result = GPUPreparedTextFramePreparer.prepare(
                operations = listOf(operation),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
                generation = GPUTextArtifactGeneration(12),
            )
            val refused = assertIs<GPUPreparedTextFramePreparation.Refused>(result)

            assertEquals(GPUTextRefusalCodes.MASK_FILTER_UNSUPPORTED, refused.refusal.code)
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(operation),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities(),
            )
            assertEquals(emptyList(), inventory.visualCommands)
        }
    }

    private fun text(paint: Paint): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(36u),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 16f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 16f,
        ),
        x = 8f,
        y = 24f,
        paint = paint,
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )
}
