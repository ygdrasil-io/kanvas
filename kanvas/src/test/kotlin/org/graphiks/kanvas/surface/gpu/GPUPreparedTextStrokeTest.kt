package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

class GPUPreparedTextStrokeTest {
    @Test
    fun `text stroke resolves every admitted style through prepared fill paths`() {
        val styles = buildList {
            StrokeCap.entries.forEach { cap ->
                StrokeJoin.entries.forEach { join ->
                    add(StrokeStyle(width = 3f, cap = cap, join = join, miter = 4f))
                }
            }
            add(StrokeStyle(width = 0f, cap = StrokeCap.BUTT, join = StrokeJoin.MITER, miter = 1f))
            add(StrokeStyle(width = 5f, cap = StrokeCap.SQUARE, join = StrokeJoin.MITER, miter = 8f))
        }

        styles.forEachIndexed { index, style ->
            val ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
                prepare(
                    listOf(
                        text(
                            Paint.stroke(ColorARGB.Red, style.width).copy(
                                strokeCap = style.cap,
                                strokeJoin = style.join,
                                strokeMiter = style.miter,
                            ),
                        ),
                    ),
                ),
                "style[$index]=$style",
            )
            val visuals = ready.mapping.visualCommands

            assertTrue(visuals.isNotEmpty(), "style[$index]=$style")
            visuals.forEach { visual ->
                val path = assertIs<NormalizedDrawCommand.FillPath>(visual.normalized)
                assertEquals("drawText.stroke-path", path.source.operation)
                assertEquals(false, path.stroke)
                assertEquals(style.width, path.strokeWidth)
                assertEquals(style.cap.name.lowercase(), path.strokeCap)
                assertEquals(style.join.name.lowercase(), path.strokeJoin)
                assertEquals(style.miter, path.strokeMiterLimit)
                assertNull(visual.preparedText)
            }
            assertTrue(ready.inventory.pages.isEmpty(), "style[$index]=$style")
            assertEquals(0, ready.inventory.metrics.uniqueMaskCount)
        }
    }

    @Test
    fun `supported text dash is expanded once by the common stroke authority`() {
        val solid = preparedPaths(Paint.stroke(ColorARGB.Blue, 3f))
        val dashed = preparedPaths(
            Paint.stroke(ColorARGB.Blue, 3f).copy(
                strokeCap = StrokeCap.ROUND,
                pathEffect = PathEffect.Dash(floatArrayOf(4f, 2f), phase = 1f),
            ),
        )

        assertTrue(dashed.isNotEmpty())
        assertTrue(dashed.all { command -> command.dashIntervals?.contentEquals(floatArrayOf(4f, 2f)) == true })
        assertTrue(dashed.all { command -> command.dashPhase == 1f })
        assertNotEquals(
            solid.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
            dashed.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
        )
    }

    @Test
    fun `miter limit and join remain semantic inputs to common stroke expansion`() {
        val bevel = preparedPaths(
            Paint.stroke(ColorARGB.Green, 5f).copy(strokeJoin = StrokeJoin.BEVEL),
        )
        val shortMiter = preparedPaths(
            Paint.stroke(ColorARGB.Green, 5f).copy(
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 1f,
            ),
        )
        val longMiter = preparedPaths(
            Paint.stroke(ColorARGB.Green, 5f).copy(
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 8f,
            ),
        )

        assertNotEquals(
            bevel.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
            longMiter.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
        )
        assertNotEquals(
            shortMiter.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
            longMiter.flatMap(NormalizedDrawCommand.FillPath::tessellatedVertices),
        )
    }

    @Test
    fun `common stroke uses bevel fallback below the open and closed miter threshold`() {
        val openCorner = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
        )
        val closedSquare = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f,
            0f, 0f,
        )

        listOf(openCorner, closedSquare).forEach { contour ->
            val bevel = commonStroke(contour, StrokeJoin.BEVEL, miterLimit = 4f)
            val belowThreshold = commonStroke(contour, StrokeJoin.MITER, miterLimit = 1f)
            val aboveThreshold = commonStroke(contour, StrokeJoin.MITER, miterLimit = 2f)

            assertContentEquals(bevel.vertices, belowThreshold.vertices)
            assertNotEquals(bevel.vertices, aboveThreshold.vertices)
        }
    }

    @Test
    fun `unsupported text path effect returns the existing terminal text code`() {
        val refused = assertIs<GPUPreparedTextFramePreparation.Refused>(
            prepare(
                listOf(
                    text(
                        Paint.stroke(ColorARGB.Red, 2f).copy(
                            pathEffect = PathEffect.Corner(radius = 3f),
                        ),
                    ),
                ),
            ),
        ).refusal

        assertEquals(GPUTextRefusalCodes.PATH_EFFECT_UNSUPPORTED, refused.code)
    }

    @Test
    fun `later stroke refusal publishes no earlier prepared visual`() {
        val result = prepare(
            listOf(
                text(Paint.stroke(ColorARGB.Red, 2f)),
                text(
                    Paint.stroke(ColorARGB.Blue, 2f).copy(
                        pathEffect = PathEffect.Corner(radius = 3f),
                    ),
                ),
            ),
        )

        val refused = assertIs<GPUPreparedTextFramePreparation.Refused>(result)
        assertEquals(1, refused.refusal.operationIndex)
        assertEquals(GPUTextRefusalCodes.PATH_EFFECT_UNSUPPORTED, refused.refusal.code)
    }

    @Test
    fun `caller dash mutation cannot alter the prepared stroke snapshot`() {
        val intervals = floatArrayOf(3f, 1f)
        val ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
            prepare(
                listOf(
                    text(
                        Paint.stroke(ColorARGB.Red, 2f).copy(
                            pathEffect = PathEffect.Dash(intervals, phase = 0.5f),
                        ),
                    ),
                ),
            ),
        )

        intervals.fill(99f)
        ready.mapping.visualCommands.forEach { visual ->
            val path = assertIs<NormalizedDrawCommand.FillPath>(visual.normalized)
            assertContentEquals(floatArrayOf(3f, 1f), path.dashIntervals)
        }
    }

    @Test
    fun `prepared stroke path key seals exact geometry and verb count seals every contour`() {
        val style = Paint.stroke(ColorARGB.Red, 2f).copy(
            strokeCap = StrokeCap.SQUARE,
            strokeJoin = StrokeJoin.BEVEL,
            pathEffect = PathEffect.Dash(floatArrayOf(4f, 2f), phase = 1f),
        )
        val first = preparedPaths(style)
        val repeated = preparedPaths(style)
        val wider = preparedPaths(style.copy(strokeWidth = 3f))
        val scaled = preparedPaths(style, transform = Matrix3x3F32.scaling(2f, 2f))

        assertEquals(first.map { path -> path.pathKey }, repeated.map { path -> path.pathKey })
        assertNotEquals(first.map { path -> path.pathKey }, wider.map { path -> path.pathKey })
        first.zip(scaled).forEach { (local, device) ->
            assertEquals(
                local.tessellatedVertices.map { coordinate -> coordinate * 2f },
                device.tessellatedVertices,
            )
            assertEquals(GPUTransformType.Identity, device.transform.type)
            assertEquals("identity", device.pathDescriptor.transformClass)
            assertEquals(computeBounds(device.tessellatedVertices), device.bounds)
        }
        first.forEach { path ->
            val contourEnds = path.contourStarts.drop(1) + path.totalVertexCount
            val contourSizes = path.contourStarts.zip(contourEnds) { start, end -> end - start }

            assertTrue(contourSizes.any { size -> size != 3 })
            assertEquals(
                path.totalVertexCount + path.contourStarts.size,
                path.pathDescriptor.verbCount,
            )
        }
    }

    @Test
    fun `stroke inventory identity seals exact transform clip material blend and mask filter`() {
        fun inventoryHash(
            paint: Paint = Paint.stroke(ColorARGB.Red, 2f),
            transform: Matrix3x3F32 = Matrix3x3F32.translation(1f, 2f),
            clip: ClipStack = ClipStack.DeviceRect(
                RectF32.ofLTRB(1f, 2f, 40f, 50f),
                antiAlias = false,
            ),
        ): String = assertIs<GPUPreparedTextFramePreparation.Ready>(
            prepare(listOf(text(paint = paint, transform = transform, clip = clip))),
        ).inventory.contentSha256

        val transformOne = inventoryHash(transform = Matrix3x3F32.translation(1f, 2f))
        val transformTwo = inventoryHash(transform = Matrix3x3F32.translation(1f, 3f))
        val clipOne = inventoryHash()
        val clipTwo = inventoryHash(
            clip = ClipStack.DeviceRect(
                RectF32.ofLTRB(1f, 2f, 41f, 50f),
                antiAlias = false,
            ),
        )
        val red = inventoryHash(paint = Paint.stroke(ColorARGB.Red, 2f))
        val blue = inventoryHash(paint = Paint.stroke(ColorARGB.Blue, 2f))
        val srcOver = inventoryHash(
            paint = Paint.stroke(ColorARGB.Red, 2f).copy(blendMode = BlendMode.SRC_OVER),
        )
        val plus = inventoryHash(
            paint = Paint.stroke(ColorARGB.Red, 2f).copy(blendMode = BlendMode.PLUS),
        )
        val sigmaOne = inventoryHash(
            paint = Paint.stroke(ColorARGB.Red, 2f).copy(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 1f),
            ),
        )
        val sigmaTwo = inventoryHash(
            paint = Paint.stroke(ColorARGB.Red, 2f).copy(
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 2f),
            ),
        )

        assertNotEquals(transformOne, transformTwo, "exact transform")
        assertNotEquals(clipOne, clipTwo, "exact clip")
        assertNotEquals(red, blue, "material")
        assertNotEquals(srcOver, plus, "blend")
        assertNotEquals(sigmaOne, sigmaTwo, "mask filter")
    }

    private fun preparedPaths(
        paint: Paint,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    ): List<NormalizedDrawCommand.FillPath> {
        val ready = assertIs<GPUPreparedTextFramePreparation.Ready>(
            prepare(listOf(text(paint, transform = transform))),
        )
        return ready.mapping.visualCommands.map { visual ->
            assertIs<NormalizedDrawCommand.FillPath>(visual.normalized)
        }
    }

    private fun commonStroke(
        contour: List<Float>,
        join: StrokeJoin,
        miterLimit: Float,
    ): StrokeGeometry = strokeToFillGeometry(
        contourVertices = contour,
        contourStarts = listOf(0),
        strokeWidth = 2f,
        joinStyle = join,
        miterLimit = miterLimit,
    )

    private fun prepare(operations: List<DisplayOp>): GPUPreparedTextFramePreparation =
        GPUPreparedTextFramePreparer.prepare(
            operations = operations,
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities(),
            generation = GPUTextArtifactGeneration(12),
        )

    private fun text(
        paint: Paint,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        clip: ClipStack = ClipStack.WideOpen,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(36u),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 24f,
                ),
            ),
            typeface = liberationTypeface(),
            fontSize = 24f,
        ),
        x = 20f,
        y = 40f,
        paint = paint,
        transform = transform,
        clip = clip,
    )

    private data class StrokeStyle(
        val width: Float,
        val cap: StrokeCap,
        val join: StrokeJoin,
        val miter: Float,
    )
}
