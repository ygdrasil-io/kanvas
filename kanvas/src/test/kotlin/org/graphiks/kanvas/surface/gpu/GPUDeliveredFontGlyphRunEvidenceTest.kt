package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.PreparedTextOutline
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Task 10 evidence for the shipped Liberation Sans font only.  This is not a
 * general text or font-support claim: each row is a bounded Latin glyph run
 * derived from one of the blocked GM families.
 */
class GPUDeliveredFontGlyphRunEvidenceTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `shipped Liberation Sans glyph runs render through CPU outline and headless WebGPU routes`() {
        val typeface = shippedLiberationTypeface()
        val rows = listOf(
            EvidenceRow(
                id = "gradtext.glyph-run.linear-clamp.v1",
                text = "Skia",
                size = 24f,
                paint = Paint.fill(ColorARGB.White).copy(
                    shader = Shader.LinearGradient(
                        start = Point2F32(0f, 0f),
                        end = Point2F32(64f, 0f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                        tileMode = TileMode.CLAMP,
                    ),
                ),
                transform = Matrix3x3F32.Identity,
                requiresOpaqueCpuOracle = false,
            ),
            EvidenceRow(
                id = "text-scale-skew.glyph-run.affine.v1",
                text = "Skia",
                size = 24f,
                paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32(sx = 1.15f, kx = 0.18f, tx = 0f, ky = 0f, sy = 1f, ty = 0f),
                requiresOpaqueCpuOracle = true,
            ),
            EvidenceRow(
                id = "fontscaler.glyph-run.size-18.v1",
                text = "Aa",
                size = 18f,
                paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32.Identity,
                requiresOpaqueCpuOracle = true,
            ),
        )

        rows.forEach { row ->
            val blob = Font(typeface, size = row.size).toTextBlob(row.text, 0f, 0f)
            val glyphRun = blob.glyphRuns.single()
            assertEquals(row.text.codePointCount(0, row.text.length), glyphRun.glyphs.size, row.id)
            assertTrue(glyphRun.glyphs.all { glyph -> glyph.toInt() != 0 }, "${row.id}: missing glyph mapping")
            assertTrue(
                glyphRun.glyphs.all { glyph -> typeface.getGlyphPath(glyph.toInt(), row.size) != null },
                "${row.id}: CPU getGlyphPath oracle is unavailable",
            )
            assertTrue(
                glyphRun.glyphs.all { glyph ->
                    typeface.preparedTextOutline(glyph.toInt(), row.size) is PreparedTextOutline.ProvenNonEmpty
                },
                "${row.id}: preparedTextOutline cannot supply the GPU TextA8 route",
            )

            val result = execute(
                operations = listOf(
                    DisplayOp.DrawText(
                        blob = blob,
                        x = 8f,
                        y = 28f,
                        paint = row.paint,
                        transform = row.transform,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                width = 96,
                height = 48,
            )

            assertEquals(0, result.evidence.textCounters.pathStrokeDraws, row.id)
            assertEquals(glyphRun.glyphs.size, result.evidence.textCounters.a8Instances, row.id)
            assertEquals(1L, result.evidence.submits, row.id)
            assertEquals(1L, result.evidence.readbackCopies, row.id)
            assertTrue(result.rgba.any { byte -> byte.toInt() != 0 }, "${row.id}: blank GPU readback")
            if (row.requiresOpaqueCpuOracle) {
                val expectedOpaque = GPUPreparedTextPixelOracle.a8SourceOver(
                    material = GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
                    paintAlpha = 1f,
                    coverage = 255,
                ).bytes()
                assertTrue(
                    result.rgba.asList().windowed(expectedOpaque.size, expectedOpaque.size)
                        .any { pixel -> pixel.toByteArray().contentEquals(expectedOpaque) },
                    "${row.id}: GPU output contains no CPU-oracle opaque A8 texel",
                )
            }
            println(
                "task10.text id=${row.id} glyphs=${glyphRun.glyphs.size} " +
                    "a8Instances=${result.evidence.textCounters.a8Instances} " +
                    "submits=${result.evidence.submits} readbacks=${result.evidence.readbackCopies}",
            )
        }
    }

    private fun execute(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
    ): GPUPreparedSurfaceExecutionResult.Succeeded {
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        return assertIs(
            GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
                GPUPreparedSurfaceExecutionRequest(
                    candidate = GPUPreparedSurfaceEligibility.Candidate(
                        operations = operations,
                        config = RenderConfig.DEFAULT,
                        color = color,
                    ),
                    width = width,
                    height = height,
                    output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
                ),
            ),
        )
    }

    private fun shippedLiberationTypeface(): FontTypeface = FontTypeface(
        requireNotNull(
            javaClass.classLoader.getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf"),
        ).use { stream -> stream.readBytes() },
        "LiberationSans-Regular.ttf",
    )

    private data class EvidenceRow(
        val id: String,
        val text: String,
        val size: Float,
        val paint: Paint,
        val transform: Matrix3x3F32,
        val requiresOpaqueCpuOracle: Boolean,
    )
}
