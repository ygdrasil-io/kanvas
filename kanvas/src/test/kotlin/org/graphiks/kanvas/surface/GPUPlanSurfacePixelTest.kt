package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.surface.gpu.GPUPlanSurfaceTerminalException
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPlanSurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `W4dGeneral rotated hard fill reaches Surface with exact fully-covered pixels`() {
        val path = Path().apply {
            moveTo(1f, 2f)
            lineTo(4f, 2f)
            lineTo(4f, 4f)
            lineTo(1f, 4f)
            close()
        }
        val paint = Paint.fill(ColorARGB.of(255, 201, 71, 43)).copy(antiAlias = false)
        val transform = Matrix3x3F32.rotation(33f, pivotX = 4f, pivotY = 4f)
        val surface = Surface(8, 8)
        surface.canvas {
            rotate(33f, px = 4f, py = 4f)
            drawPath(path, paint)
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4dGeneralPathCpuOracle.render(
                widthI32 = 8,
                heightI32 = 8,
                draws = listOf(
                    W4dGeneralPathCpuOracle.Draw(
                        path = path.toPathF32(),
                        paint = paint,
                        transform = transform,
                        scissorI32 = RectI32(0, 0, 8, 8),
                    ),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4e hard ordered rect RRect and path clips match the independent public Surface oracle`() {
        val outer = RectF32.ofLTRB(1f, 1f, 9f, 9f)
        val rounded = RRectF32.of(RectF32.ofLTRB(2f, 2f, 8f, 8f), radius = 2f)
        val notch = Path().apply {
            moveTo(4f, 2f)
            lineTo(8f, 2f)
            lineTo(8f, 6f)
            close()
        }
        val foreground = Path().apply {
            moveTo(1f, 8f)
            lineTo(8f, 3f)
            lineTo(8f, 8f)
            close()
        }
        val background = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)) }
        val clips = listOf(
            W4eClipCpuOracle.Clip(
                W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 9.0, 9.0),
                W4eClipCpuOracle.ClipOperation.Intersect,
                antiAlias = W4eClipCpuOracle.AA.Hard,
            ),
            W4eClipCpuOracle.Clip(
                W4eClipCpuOracle.Shape.RRect(W4eClipCpuOracle.Shape.Rect(2.0, 2.0, 8.0, 8.0), 2.0, 2.0),
                W4eClipCpuOracle.ClipOperation.Intersect,
                antiAlias = W4eClipCpuOracle.AA.Hard,
            ),
            W4eClipCpuOracle.Clip(
                W4eClipCpuOracle.Shape.Polygon(
                    listOf(W4eClipCpuOracle.Point(4.0, 2.0), W4eClipCpuOracle.Point(8.0, 2.0), W4eClipCpuOracle.Point(8.0, 6.0)),
                ),
                W4eClipCpuOracle.ClipOperation.Difference,
                antiAlias = W4eClipCpuOracle.AA.Hard,
            ),
        )
        val expected = W4eClipCpuOracle.render(
            width = 10,
            height = 10,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 10.0, 10.0),
                    W4eClipCpuOracle.Rgba8(255, 43, 71, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = clips,
                ),
                W4eClipCpuOracle.Draw(
                    W4eClipCpuOracle.Shape.Polygon(
                        listOf(W4eClipCpuOracle.Point(1.0, 8.0), W4eClipCpuOracle.Point(8.0, 3.0), W4eClipCpuOracle.Point(8.0, 8.0)),
                    ),
                    W4eClipCpuOracle.Rgba8(38, 161, 99, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = clips,
                ),
            ),
        )
        val surface = Surface(10, 10)
        surface.canvas {
            clipRect(outer, antiAlias = false)
            clipRRect(rounded, antiAlias = false)
            clipPath(notch, ClipOp.DIFFERENCE, antiAlias = false)
            drawPath(background, Paint.fill(ColorARGB.of(255, 255, 43, 71)).copy(antiAlias = false))
            drawPath(foreground, Paint.fill(ColorARGB.of(255, 38, 161, 99)).copy(antiAlias = false))
        }

        val result = surface.render()
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public hard inverse empty and non-empty draws match the independent 1x oracle`() {
        val triangle = Path {
            moveTo(3f, 3f)
            lineTo(13f, 3f)
            lineTo(4f, 13f)
            close()
        }.apply { fillType = FillType.INVERSE_WINDING }
        val empty = Path().apply { fillType = FillType.INVERSE_WINDING }
        val clip = Path()
        val cases = listOf(
            triangle to W4eClipCpuOracle.Shape.Inverse(
                W4eClipCpuOracle.Shape.Polygon(listOf(
                    W4eClipCpuOracle.Point(3.0, 3.0),
                    W4eClipCpuOracle.Point(13.0, 3.0),
                    W4eClipCpuOracle.Point(4.0, 13.0),
                )),
            ),
            empty to W4eClipCpuOracle.Shape.Inverse(W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 0.0, 0.0)),
        )
        val expectedByCase = cases.map { (_, shape) ->
            W4eClipCpuOracle.render(
                16,
                16,
                listOf(
                    W4eClipCpuOracle.Draw(
                        shape = shape,
                        color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                        antiAlias = W4eClipCpuOracle.AA.Hard,
                        clips = listOf(
                            W4eClipCpuOracle.Clip(
                                shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 0.0, 0.0),
                                operation = W4eClipCpuOracle.ClipOperation.Difference,
                                antiAlias = W4eClipCpuOracle.AA.Hard,
                            ),
                        ),
                    ),
                ),
            )
        }
        assertFalse(
            expectedByCase[0].contentEquals(expectedByCase[1]),
            "The inverse non-empty and inverse empty draw fixtures must exercise distinct coverage.",
        )
        cases.zip(expectedByCase).forEach { (case, expected) ->
            val (path, _) = case
            val surface = Surface(16, 16)
            surface.canvas {
                clipPath(clip, ClipOp.DIFFERENCE, antiAlias = false)
                drawPath(path, Paint.fill(ColorARGB.Red).copy(antiAlias = false))
            }
            val result = surface.render()
            GPUBackendRuntimeFactory.dispose()
            assertPreparedRouteEvidence(result)
            assertPixelsEqual(expected, result.pixels)
        }
    }

    @Test
    fun `W4e public hard inverse path consumers keep distinct D24S8 domains in one frame`() {
        val width = 16
        val height = 12
        val firstDomainPath = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 7f, 11f)) }
        val secondDomainPath = Path().apply { addRect(RectF32.ofLTRB(9f, 1f, 15f, 11f)) }
        val firstInversePath = Path().apply {
            moveTo(2f, 3f)
            lineTo(6f, 3f)
            lineTo(5f, 8f)
            lineTo(3f, 9f)
            close()
            fillType = FillType.INVERSE_WINDING
        }
        val secondInversePath = Path().apply {
            moveTo(10f, 3f)
            lineTo(14f, 3f)
            lineTo(13f, 8f)
            lineTo(11f, 9f)
            close()
            fillType = FillType.INVERSE_WINDING
        }
        val firstDomain = W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 7.0, 11.0)
        val secondDomain = W4eClipCpuOracle.Shape.Rect(9.0, 1.0, 15.0, 11.0)
        val firstClip = W4eClipCpuOracle.Clip(
            firstDomain,
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.Hard,
        )
        val secondClip = W4eClipCpuOracle.Clip(
            secondDomain,
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.Hard,
        )
        val first = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Inverse(
                W4eClipCpuOracle.Shape.Polygon(
                    listOf(
                        W4eClipCpuOracle.Point(2.0, 3.0),
                        W4eClipCpuOracle.Point(6.0, 3.0),
                        W4eClipCpuOracle.Point(5.0, 8.0),
                        W4eClipCpuOracle.Point(3.0, 9.0),
                    ),
                ),
            ),
            color = W4eClipCpuOracle.Rgba8(213, 57, 41, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(firstClip),
        )
        val second = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Inverse(
                W4eClipCpuOracle.Shape.Polygon(
                    listOf(
                        W4eClipCpuOracle.Point(10.0, 3.0),
                        W4eClipCpuOracle.Point(14.0, 3.0),
                        W4eClipCpuOracle.Point(13.0, 8.0),
                        W4eClipCpuOracle.Point(11.0, 9.0),
                    ),
                ),
            ),
            color = W4eClipCpuOracle.Rgba8(38, 122, 221, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(secondClip),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(first, second))
        val withoutFirst = W4eClipCpuOracle.render(width, height, listOf(second))
        val withoutSecond = W4eClipCpuOracle.render(width, height, listOf(first))
        val firstDomainForBoth = W4eClipCpuOracle.render(
            width,
            height,
            listOf(first, second.copy(clips = listOf(firstClip))),
        )
        val secondDomainForBoth = W4eClipCpuOracle.render(
            width,
            height,
            listOf(first.copy(clips = listOf(secondClip)), second),
        )
        val singleConsumer = Surface(width, height)
        singleConsumer.canvas {
            clipPath(firstDomainPath, ClipOp.INTERSECT, antiAlias = false)
            drawPath(firstInversePath, Paint.fill(ColorARGB.of(255, 213, 57, 41)).copy(antiAlias = false))
        }
        val singleResult = singleConsumer.render()
        val surface = Surface(width, height)
        surface.canvas {
            save()
            clipPath(firstDomainPath, ClipOp.INTERSECT, antiAlias = false)
            drawPath(firstInversePath, Paint.fill(ColorARGB.of(255, 213, 57, 41)).copy(antiAlias = false))
            restore()
            save()
            clipPath(secondDomainPath, ClipOp.INTERSECT, antiAlias = false)
            drawPath(secondInversePath, Paint.fill(ColorARGB.of(255, 38, 122, 221)).copy(antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertFalse(expected.contentEquals(withoutFirst), "the first inverse consumer must contribute")
        assertFalse(expected.contentEquals(withoutSecond), "the second inverse consumer must contribute")
        assertFalse(expected.contentEquals(firstDomainForBoth), "the consumers must not share the first D24S8 domain")
        assertFalse(expected.contentEquals(secondDomainForBoth), "the consumers must not share the second D24S8 domain")
        assertPixelsEqual(withoutSecond, singleResult.pixels)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public mixed hard and Path AA4 inverse consumers keep distinct D24S8 domains`() {
        val width = 16
        val height = 12
        // Integer-aligned path and domain boundaries only observe fully covered or fully empty pixels;
        // no assertion depends on uncontracted native MSAA sample locations.
        val firstDomainPath = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 7f, 11f)) }
        val secondDomainPath = Path().apply { addRect(RectF32.ofLTRB(9f, 1f, 15f, 11f)) }
        val firstInversePath = Path().apply {
            addRect(RectF32.ofLTRB(2f, 3f, 6f, 9f))
            fillType = FillType.INVERSE_WINDING
        }
        val secondInversePath = Path().apply {
            addRect(RectF32.ofLTRB(10f, 3f, 14f, 9f))
            fillType = FillType.INVERSE_WINDING
        }
        val firstDomain = W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 7.0, 11.0)
        val secondDomain = W4eClipCpuOracle.Shape.Rect(9.0, 1.0, 15.0, 11.0)
        val firstClip = W4eClipCpuOracle.Clip(
            firstDomain,
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.Hard,
        )
        val secondClip = W4eClipCpuOracle.Clip(
            secondDomain,
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.PathMsaa4,
        )
        val first = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Inverse(W4eClipCpuOracle.Shape.Rect(2.0, 3.0, 6.0, 9.0)),
            color = W4eClipCpuOracle.Rgba8(213, 57, 41, 255),
            antiAlias = W4eClipCpuOracle.AA.PathMsaa4,
            clips = listOf(firstClip),
        )
        val second = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Inverse(W4eClipCpuOracle.Shape.Rect(10.0, 3.0, 14.0, 9.0)),
            color = W4eClipCpuOracle.Rgba8(38, 122, 221, 255),
            antiAlias = W4eClipCpuOracle.AA.PathMsaa4,
            clips = listOf(secondClip),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(first, second))
        val withoutFirst = W4eClipCpuOracle.render(width, height, listOf(second))
        val withoutSecond = W4eClipCpuOracle.render(width, height, listOf(first))
        val firstDomainForBoth = W4eClipCpuOracle.render(
            width,
            height,
            listOf(first, second.copy(clips = listOf(firstClip))),
        )
        val secondDomainForBoth = W4eClipCpuOracle.render(
            width,
            height,
            listOf(first.copy(clips = listOf(secondClip)), second),
        )
        val surface = Surface(width, height)
        surface.canvas {
            save()
            clipPath(firstDomainPath, ClipOp.INTERSECT, antiAlias = false)
            drawPath(firstInversePath, Paint.fill(ColorARGB.of(255, 213, 57, 41)).copy(antiAlias = true))
            restore()
            save()
            clipPath(secondDomainPath, ClipOp.INTERSECT, antiAlias = true)
            drawPath(secondInversePath, Paint.fill(ColorARGB.of(255, 38, 122, 221)).copy(antiAlias = true))
            restore()
        }

        val result = surface.renderPathAa4OrSkip()

        assertFalse(expected.contentEquals(withoutFirst), "the hard-domain inverse consumer must contribute")
        assertFalse(expected.contentEquals(withoutSecond), "the Path AA4-domain inverse consumer must contribute")
        assertFalse(expected.contentEquals(firstDomainForBoth), "the mixed consumers must not share the hard D24S8 domain")
        assertFalse(expected.contentEquals(secondDomainForBoth), "the mixed consumers must not share the Path AA4 D24S8 domain")
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public hard restrictive scissor preserves translucent SrcOver in RGBA and BGRA`() {
        val width = 7
        val height = 5
        val scissor = RectI32(2, 1, 6, 4)
        val first = ColorARGB.of(128, 14, 157, 83)
        val second = ColorARGB.of(128, 227, 62, 174)
        val firstPath = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 7f, 5f)) }
        val secondPath = Path().apply {
            moveTo(1f, 1f)
            lineTo(7f, 1f)
            lineTo(7f, 5f)
            lineTo(1f, 5f)
            close()
        }
        val draws = listOf(
            W4eClipCpuOracle.Draw(
                shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 7.0, 5.0),
                color = W4eClipCpuOracle.Rgba8(14, 157, 83, 128),
                antiAlias = W4eClipCpuOracle.AA.Hard,
                clips = emptyList(),
                scissorI32 = W4eClipCpuOracle.ScissorI32(2, 1, 6, 4),
            ),
            W4eClipCpuOracle.Draw(
                shape = W4eClipCpuOracle.Shape.Polygon(
                    listOf(
                        W4eClipCpuOracle.Point(1.0, 1.0),
                        W4eClipCpuOracle.Point(7.0, 1.0),
                        W4eClipCpuOracle.Point(7.0, 5.0),
                        W4eClipCpuOracle.Point(1.0, 5.0),
                    ),
                ),
                color = W4eClipCpuOracle.Rgba8(227, 62, 174, 128),
                antiAlias = W4eClipCpuOracle.AA.Hard,
                clips = emptyList(),
                scissorI32 = W4eClipCpuOracle.ScissorI32(2, 1, 6, 4),
            ),
        )

        listOf(PixelFormat.RGBA8, PixelFormat.BGRA8).forEach { format ->
            val surface = Surface(width, height, format)
            surface.canvas {
                clipRect(
                    RectF32.ofLTRB(
                        scissor.left.toFloat(),
                        scissor.top.toFloat(),
                        scissor.right.toFloat(),
                        scissor.bottom.toFloat(),
                    ),
                    antiAlias = false,
                )
                drawPath(firstPath, Paint.fill(first).copy(antiAlias = false, blendMode = BlendMode.SRC_OVER))
                drawPath(secondPath, Paint.fill(second).copy(antiAlias = false, blendMode = BlendMode.SRC_OVER))
            }

            val result = surface.render()
            val channelOrder = when (format) {
                PixelFormat.RGBA8 -> W4eClipCpuOracle.ChannelOrder.RGBA
                PixelFormat.BGRA8 -> W4eClipCpuOracle.ChannelOrder.BGRA
            }
            val expected = W4eClipCpuOracle.render(width, height, draws, channelOrder)
            val unscissored = W4eClipCpuOracle.render(
                width,
                height,
                draws.map { draw -> draw.copy(scissorI32 = W4eClipCpuOracle.ScissorI32.Unbounded) },
                channelOrder,
            )

            assertEquals(192u, expected[((1 * width + 2) * 4 + 3)].toUInt(), "SrcOver alpha must round to nearest UNORM")
            assertFalse(expected.contentEquals(unscissored), format.toString())
            assertPreparedRouteEvidence(result)
            assertPixelsEqual(expected, result.pixels)
            assertTransparentOutside(result.pixels, width, height, scissor)
        }
    }

    @Test
    fun `W4e public hard clip geometry operation matrix and reversed command order change pixels`() {
        val width = 12
        val height = 12
        val fill = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 12f, 12f)) }
        val color = ColorARGB.of(255, 43, 71, 211)
        val paint = Paint.fill(color).copy(antiAlias = false)
        val fullDraw = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 12.0, 12.0),
            color = W4eClipCpuOracle.Rgba8(43, 71, 211, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = emptyList(),
        )
        val rect = RectF32.ofLTRB(1f, 1f, 11f, 11f)
        val insetRect = RectF32.ofLTRB(4f, 4f, 8f, 8f)
        val rounded = RRectF32.of(RectF32.ofLTRB(1f, 1f, 11f, 11f), radius = 2f)
        val insetRounded = RRectF32.of(RectF32.ofLTRB(4f, 4f, 8f, 8f), radius = 1f)
        val path = Path().apply {
            moveTo(1f, 1f)
            lineTo(11f, 1f)
            lineTo(11f, 11f)
            lineTo(1f, 11f)
            close()
        }
        val notch = Path().apply {
            moveTo(4f, 3f)
            lineTo(9f, 6f)
            lineTo(4f, 9f)
            close()
        }
        data class ClipCase(
            val label: String,
            val clip: W4eClipCpuOracle.Clip,
            val record: Canvas.() -> Unit,
        )
        val cases = listOf(
            ClipCase(
                "Rect intersect",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 11.0, 11.0),
                    W4eClipCpuOracle.ClipOperation.Intersect,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipRect(rect, ClipOp.INTERSECT, antiAlias = false) },
            ClipCase(
                "Rect difference",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.Rect(4.0, 4.0, 8.0, 8.0),
                    W4eClipCpuOracle.ClipOperation.Difference,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipRect(insetRect, ClipOp.DIFFERENCE, antiAlias = false) },
            ClipCase(
                "RRect intersect",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.RRect(W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 11.0, 11.0), 2.0, 2.0),
                    W4eClipCpuOracle.ClipOperation.Intersect,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipRRect(rounded, ClipOp.INTERSECT, antiAlias = false) },
            ClipCase(
                "RRect difference",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.RRect(W4eClipCpuOracle.Shape.Rect(4.0, 4.0, 8.0, 8.0), 1.0, 1.0),
                    W4eClipCpuOracle.ClipOperation.Difference,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipRRect(insetRounded, ClipOp.DIFFERENCE, antiAlias = false) },
            ClipCase(
                "Path intersect",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.Polygon(
                        listOf(
                            W4eClipCpuOracle.Point(1.0, 1.0),
                            W4eClipCpuOracle.Point(11.0, 1.0),
                            W4eClipCpuOracle.Point(11.0, 11.0),
                            W4eClipCpuOracle.Point(1.0, 11.0),
                        ),
                    ),
                    W4eClipCpuOracle.ClipOperation.Intersect,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipPath(path, ClipOp.INTERSECT, antiAlias = false) },
            ClipCase(
                "Path difference",
                W4eClipCpuOracle.Clip(
                    W4eClipCpuOracle.Shape.Polygon(
                        listOf(
                            W4eClipCpuOracle.Point(4.0, 3.0),
                            W4eClipCpuOracle.Point(9.0, 6.0),
                            W4eClipCpuOracle.Point(4.0, 9.0),
                        ),
                    ),
                    W4eClipCpuOracle.ClipOperation.Difference,
                    W4eClipCpuOracle.AA.Hard,
                ),
            ) { clipPath(notch, ClipOp.DIFFERENCE, antiAlias = false) },
        )

        cases.forEach { case ->
            val expected = W4eClipCpuOracle.render(width, height, listOf(fullDraw.copy(clips = listOf(case.clip))))
            val unclippped = W4eClipCpuOracle.render(width, height, listOf(fullDraw))
            val surface = Surface(width, height)
            surface.canvas {
                case.record(this)
                drawPath(fill, paint)
            }

            val result = surface.render()
            GPUBackendRuntimeFactory.dispose()

            assertFalse(expected.contentEquals(unclippped), case.label)
            assertTrue(expected.indices.step(4).any { expected[it + 3] != 0.toUByte() }, case.label)
            assertPreparedRouteEvidence(result)
            assertPixelsEqual(expected, result.pixels)
        }

        val left = RectF32.ofLTRB(1f, 1f, 9f, 11f)
        val right = RRectF32.of(RectF32.ofLTRB(3f, 2f, 11f, 10f), radius = 2f)
        val leftShape = W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 9.0, 11.0)
        val rightShape = W4eClipCpuOracle.Shape.RRect(W4eClipCpuOracle.Shape.Rect(3.0, 2.0, 11.0, 10.0), 2.0, 2.0)
        val forwardClips = listOf(
            W4eClipCpuOracle.Clip(leftShape, W4eClipCpuOracle.ClipOperation.Intersect, W4eClipCpuOracle.AA.Hard),
            W4eClipCpuOracle.Clip(rightShape, W4eClipCpuOracle.ClipOperation.Difference, W4eClipCpuOracle.AA.Hard),
        )
        val reversedClips = listOf(
            W4eClipCpuOracle.Clip(rightShape, W4eClipCpuOracle.ClipOperation.Intersect, W4eClipCpuOracle.AA.Hard),
            W4eClipCpuOracle.Clip(leftShape, W4eClipCpuOracle.ClipOperation.Difference, W4eClipCpuOracle.AA.Hard),
        )
        val expectedForward = W4eClipCpuOracle.render(width, height, listOf(fullDraw.copy(clips = forwardClips)))
        val expectedReversed = W4eClipCpuOracle.render(width, height, listOf(fullDraw.copy(clips = reversedClips)))
        val forward = Surface(width, height).also { surface ->
            surface.canvas {
                clipRect(left, ClipOp.INTERSECT, antiAlias = false)
                clipRRect(right, ClipOp.DIFFERENCE, antiAlias = false)
                drawPath(fill, paint)
            }
        }.render()
        GPUBackendRuntimeFactory.dispose()
        val reversed = Surface(width, height).also { surface ->
            surface.canvas {
                clipRRect(right, ClipOp.INTERSECT, antiAlias = false)
                clipRect(left, ClipOp.DIFFERENCE, antiAlias = false)
                drawPath(fill, paint)
            }
        }.render()

        assertFalse(expectedForward.contentEquals(expectedReversed))
        assertPreparedRouteEvidence(forward)
        assertPreparedRouteEvidence(reversed)
        assertPixelsEqual(expectedForward, forward.pixels)
        assertPixelsEqual(expectedReversed, reversed.pixels)
    }

    @Test
    fun `W4e public hard inverse path clip has nontrivial exact coverage`() {
        val width = 12
        val height = 12
        val inverseTriangle = Path().apply {
            moveTo(3f, 2f)
            lineTo(10f, 4f)
            lineTo(4f, 10f)
            close()
            fillType = FillType.INVERSE_WINDING
        }
        val full = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())) }
        val triangle = W4eClipCpuOracle.Shape.Polygon(
            listOf(
                W4eClipCpuOracle.Point(3.0, 2.0),
                W4eClipCpuOracle.Point(10.0, 4.0),
                W4eClipCpuOracle.Point(4.0, 10.0),
            ),
        )
        val inverseClip = W4eClipCpuOracle.Clip(
            shape = W4eClipCpuOracle.Shape.Inverse(triangle),
            operation = W4eClipCpuOracle.ClipOperation.Intersect,
            antiAlias = W4eClipCpuOracle.AA.Hard,
        )
        val ordinaryClip = inverseClip.copy(shape = triangle)
        val draw = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
            color = W4eClipCpuOracle.Rgba8(196, 73, 31, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(inverseClip),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(draw))
        val ordinaryExpected = W4eClipCpuOracle.render(width, height, listOf(draw.copy(clips = listOf(ordinaryClip))))
        val surface = Surface(width, height)
        surface.canvas {
            clipPath(inverseTriangle, ClipOp.INTERSECT, antiAlias = false)
            drawPath(full, Paint.fill(ColorARGB.of(255, 196, 73, 31)).copy(antiAlias = false))
        }

        val result = surface.render()

        assertFalse(expected.contentEquals(ordinaryExpected), "inverse clip must not collapse to its interior")
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public hard affine clip and perspective draw are inverse-mapped exactly`() {
        val width = 12
        val height = 10
        val localClip = RectF32.ofLTRB(0f, 1f, 7f, 8f)
        val localDraw = Path().apply {
            moveTo(1f, 1f)
            lineTo(9f, 2f)
            lineTo(3f, 8f)
            close()
        }
        val affineClipTransform = Matrix3x3F32(kx = 0.25f, tx = 1f)
        val perspectiveDrawTransform = Matrix3x3F32(persp0 = 0.0625f)
        val affineClip = W4eClipCpuOracle.Clip(
            shape = W4eClipCpuOracle.Shape.Rect(0.0, 1.0, 7.0, 8.0),
            operation = W4eClipCpuOracle.ClipOperation.Intersect,
            antiAlias = W4eClipCpuOracle.AA.Hard,
            transformF64 = W4eClipCpuOracle.HomographyF64(
                m00F64 = 1.0, m01F64 = 0.25, m02F64 = 1.0,
                m10F64 = 0.0, m11F64 = 1.0, m12F64 = 0.0,
                m20F64 = 0.0, m21F64 = 0.0, m22F64 = 1.0,
            ),
        )
        val draw = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Polygon(
                listOf(
                    W4eClipCpuOracle.Point(1.0, 1.0),
                    W4eClipCpuOracle.Point(9.0, 2.0),
                    W4eClipCpuOracle.Point(3.0, 8.0),
                ),
            ),
            color = W4eClipCpuOracle.Rgba8(47, 132, 219, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(affineClip),
            transformF64 = W4eClipCpuOracle.HomographyF64(
                m00F64 = 1.0, m01F64 = 0.0, m02F64 = 0.0,
                m10F64 = 0.0, m11F64 = 1.0, m12F64 = 0.0,
                m20F64 = 0.0625, m21F64 = 0.0, m22F64 = 1.0,
            ),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(draw))
        val withoutAffineClip = W4eClipCpuOracle.render(
            width,
            height,
            listOf(draw.copy(clips = listOf(affineClip.copy(transformF64 = W4eClipCpuOracle.HomographyF64.Identity)))),
        )
        val withoutPerspectiveDraw = W4eClipCpuOracle.render(
            width,
            height,
            listOf(draw.copy(transformF64 = W4eClipCpuOracle.HomographyF64.Identity)),
        )
        val surface = Surface(width, height)
        surface.canvas {
            concat(affineClipTransform)
            clipRect(localClip, ClipOp.INTERSECT, antiAlias = false)
            resetMatrix()
            concat(perspectiveDrawTransform)
            drawPath(localDraw, Paint.fill(ColorARGB.of(255, 47, 132, 219)).copy(antiAlias = false))
        }

        val result = surface.render()

        assertFalse(expected.contentEquals(withoutAffineClip), "affine clip transform must change pixels")
        assertFalse(expected.contentEquals(withoutPerspectiveDraw), "perspective draw transform must change pixels")
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public hard shared clip is exact across draws`() {
        val width = 12
        val height = 12
        val rounded = RRectF32.of(RectF32.ofLTRB(1f, 1f, 11f, 11f), radius = 2f)
        val firstPath = Path().apply {
            moveTo(0f, 2f)
            lineTo(10f, 3f)
            lineTo(3f, 11f)
            close()
        }
        val secondPath = Path().apply {
            moveTo(2f, 11f)
            lineTo(11f, 4f)
            lineTo(11f, 11f)
            close()
        }
        val clip = W4eClipCpuOracle.Clip(
            shape = W4eClipCpuOracle.Shape.RRect(W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 11.0, 11.0), 2.0, 2.0),
            operation = W4eClipCpuOracle.ClipOperation.Intersect,
            antiAlias = W4eClipCpuOracle.AA.Hard,
        )
        val first = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Polygon(
                listOf(
                    W4eClipCpuOracle.Point(0.0, 2.0),
                    W4eClipCpuOracle.Point(10.0, 3.0),
                    W4eClipCpuOracle.Point(3.0, 11.0),
                ),
            ),
            color = W4eClipCpuOracle.Rgba8(210, 63, 44, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(clip),
        )
        val second = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Polygon(
                listOf(
                    W4eClipCpuOracle.Point(2.0, 11.0),
                    W4eClipCpuOracle.Point(11.0, 4.0),
                    W4eClipCpuOracle.Point(11.0, 11.0),
                ),
            ),
            color = W4eClipCpuOracle.Rgba8(39, 154, 107, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(clip),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(first, second))
        val firstOnly = W4eClipCpuOracle.render(width, height, listOf(first))
        val unclipped = W4eClipCpuOracle.render(
            width,
            height,
            listOf(first.copy(clips = emptyList()), second.copy(clips = emptyList())),
        )
        val surface = Surface(width, height)
        surface.canvas {
            clipRRect(rounded, ClipOp.INTERSECT, antiAlias = false)
            drawPath(firstPath, Paint.fill(ColorARGB.of(255, 210, 63, 44)).copy(antiAlias = false))
            drawPath(secondPath, Paint.fill(ColorARGB.of(255, 39, 154, 107)).copy(antiAlias = false))
        }

        val result = surface.render()

        assertFalse(expected.contentEquals(firstOnly), "both public draws must contribute")
        assertFalse(expected.contentEquals(unclipped), "the shared clip must restrict both draws")
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public hard and analytic Rect clips keep the exact 2x2 coverage distinction`() {
        val width = 4
        val height = 4
        val fractionalClip = RectF32.ofLTRB(0.2f, 0f, 0.7f, height.toFloat())
        val full = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())) }
        val paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false)
        val fullClip = W4eClipCpuOracle.Clip(
            W4eClipCpuOracle.Shape.Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.Hard,
        )
        val fractionalOracleClip = W4eClipCpuOracle.Clip(
            W4eClipCpuOracle.Shape.Rect(0.2, 0.0, 0.7, height.toDouble()),
            W4eClipCpuOracle.ClipOperation.Intersect,
            W4eClipCpuOracle.AA.Hard,
        )
        val hardDraw = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
            color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(fullClip, fractionalOracleClip),
        )
        val analyticDraw = hardDraw.copy(
            clips = listOf(fullClip, fractionalOracleClip.copy(antiAlias = W4eClipCpuOracle.AA.Analytic2x2)),
        )
        val expectedHard = W4eClipCpuOracle.render(width, height, listOf(hardDraw))
        val expectedAnalytic = W4eClipCpuOracle.render(width, height, listOf(analyticDraw))
        val hard = Surface(width, height).also { surface ->
            surface.canvas {
                clipRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat()), ClipOp.INTERSECT, antiAlias = false)
                clipRect(fractionalClip, ClipOp.INTERSECT, antiAlias = false)
                drawPath(full, paint)
            }
        }.render()
        GPUBackendRuntimeFactory.dispose()
        val analytic = Surface(width, height).also { surface ->
            surface.canvas {
                clipRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat()), ClipOp.INTERSECT, antiAlias = false)
                clipRect(fractionalClip, ClipOp.INTERSECT, antiAlias = true)
                drawPath(full, paint)
            }
        }.render()

        assertFalse(expectedHard.contentEquals(expectedAnalytic), "hard and analytic clip coverage must differ")
        assertPreparedRouteEvidence(hard)
        assertPreparedRouteEvidence(analytic)
        assertPixelsEqual(expectedHard, hard.pixels)
        assertPixelsEqual(expectedAnalytic, analytic.pixels)
    }

    @Test
    fun `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary`() {
        val width = 6
        val height = 6
        // Integer-aligned boundaries make every tested pixel fully covered or fully empty;
        // this asserts no uncontracted hardware MSAA sample location.
        val clipPath = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 5f, 5f)) }
        val full = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())) }
        val clip = W4eClipCpuOracle.Clip(
            shape = W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 5.0, 5.0),
            operation = W4eClipCpuOracle.ClipOperation.Intersect,
            antiAlias = W4eClipCpuOracle.AA.PathMsaa4,
        )
        val draw = W4eClipCpuOracle.Draw(
            shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
            color = W4eClipCpuOracle.Rgba8(122, 49, 216, 255),
            antiAlias = W4eClipCpuOracle.AA.Hard,
            clips = listOf(clip),
        )
        val expected = W4eClipCpuOracle.render(width, height, listOf(draw))
        val unclipped = W4eClipCpuOracle.render(width, height, listOf(draw.copy(clips = emptyList())))
        val surface = Surface(width, height)
        surface.canvas {
            clipPath(clipPath, ClipOp.INTERSECT, antiAlias = true)
            drawPath(full, Paint.fill(ColorARGB.of(255, 122, 49, 216)).copy(antiAlias = false))
        }

        val result = surface.renderPathAa4OrSkip()

        assertFalse(expected.contentEquals(unclipped), "the AA Path clip must remain observable on binary pixels")
        assertPreparedRouteEvidence(result)
        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e public budget error leaves the public Surface route usable for a later frame`() {
        val width = 8
        val height = 8
        val rounded = RRectF32.of(RectF32.ofLTRB(1f, 1f, 7f, 7f), radius = 2f)
        val full = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())) }
        val paint = Paint.fill(ColorARGB.of(255, 82, 141, 219)).copy(antiAlias = false)
        val expected = W4eClipCpuOracle.render(
            width,
            height,
            listOf(
                W4eClipCpuOracle.Draw(
                    shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
                    color = W4eClipCpuOracle.Rgba8(82, 141, 219, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = listOf(
                        W4eClipCpuOracle.Clip(
                            shape = W4eClipCpuOracle.Shape.RRect(
                                W4eClipCpuOracle.Shape.Rect(1.0, 1.0, 7.0, 7.0),
                                2.0,
                                2.0,
                            ),
                            operation = W4eClipCpuOracle.ClipOperation.Intersect,
                            antiAlias = W4eClipCpuOracle.AA.Hard,
                        ),
                    ),
                ),
            ),
        )
        val rejected = Surface(
            width,
            height,
            config = RenderConfig(frameLocalBudgetBytes = 1L),
        )
        rejected.canvas {
            clipRRect(rounded, ClipOp.INTERSECT, antiAlias = false)
            drawPath(full, paint)
        }

        val error = assertFailsWith<IllegalStateException> { rejected.render() }

        assertTrue(
            error.message.orEmpty().contains("w4e.clip.budget.frame-local-exceeded"),
            "Expected the public W4e budget error, got ${error.message}",
        )

        val recovered = Surface(width, height)
        recovered.canvas {
            clipRRect(rounded, ClipOp.INTERSECT, antiAlias = false)
            drawPath(full, paint)
        }

        val result = recovered.render()

        assertPixelsEqual(expected, result.pixels)
    }

    @Test
    fun `W4e oracle inverse-maps affine clip geometry before ordered folds`() {
        val pixels = W4eClipCpuOracle.render(
            width = 2,
            height = 1,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 2.0, 1.0),
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = listOf(
                        W4eClipCpuOracle.Clip(
                            shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 1.0, 1.0),
                            operation = W4eClipCpuOracle.ClipOperation.Intersect,
                            antiAlias = W4eClipCpuOracle.AA.Hard,
                            transformF64 = W4eClipCpuOracle.HomographyF64.translation(1.0, 0.0),
                        ),
                    ),
                ),
            ),
        )

        assertPixelsEqual(
            ubyteArrayOf(
                0u, 0u, 0u, 0u,
                255u, 0u, 0u, 255u,
            ),
            pixels,
        )
    }

    @Test
    fun `W4e oracle inverse-maps projective draw geometry instead of dropping perspective`() {
        val pixels = W4eClipCpuOracle.render(
            width = 2,
            height = 1,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = W4eClipCpuOracle.Shape.Rect(0.0, 0.0, 1.0, 1.0),
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    transformF64 = W4eClipCpuOracle.HomographyF64(
                        m00F64 = 1.0, m01F64 = 0.0, m02F64 = 0.0,
                        m10F64 = 0.0, m11F64 = 1.0, m12F64 = 0.0,
                        m20F64 = -0.5, m21F64 = 0.0, m22F64 = 1.0,
                    ),
                    clips = emptyList(),
                ),
            ),
        )

        assertPixelsEqual(
            ubyteArrayOf(
                255u, 0u, 0u, 255u,
                255u, 0u, 0u, 255u,
            ),
            pixels,
        )
    }

    @Test
    fun `W4e oracle restricts coverage to its local half-open I32 scissor`() {
        val pixels = W4eClipCpuOracle.render(
            width = 3,
            height = 2,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = W4eClipCpuOracle.Shape.Rect(-1.0, -1.0, 4.0, 3.0),
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = emptyList(),
                    scissorI32 = W4eClipCpuOracle.ScissorI32(1, 0, 2, 2),
                ),
            ),
        )

        assertPixelsEqual(
            ubyteArrayOf(
                0u, 0u, 0u, 0u,
                255u, 0u, 0u, 255u,
                0u, 0u, 0u, 0u,
                0u, 0u, 0u, 0u,
                255u, 0u, 0u, 255u,
                0u, 0u, 0u, 0u,
            ),
            pixels,
        )
    }

    @Test
    fun `W4e oracle analytic 2x2 coverage uses producer positions without inventing PathMsaa4 positions`() {
        val fractionalEdge = W4eClipCpuOracle.Shape.Rect(0.2, 0.0, 0.3, 1.0)
        val hard = W4eClipCpuOracle.render(
            width = 1,
            height = 1,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = fractionalEdge,
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.Hard,
                    clips = emptyList(),
                ),
            ),
        )
        val analytic = W4eClipCpuOracle.render(
            width = 1,
            height = 1,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = fractionalEdge,
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.Analytic2x2,
                    clips = emptyList(),
                ),
            ),
        )
        val pathMsaa4Binary = W4eClipCpuOracle.render(
            width = 1,
            height = 1,
            draws = listOf(
                W4eClipCpuOracle.Draw(
                    shape = W4eClipCpuOracle.Shape.Rect(-1.0, -1.0, 2.0, 2.0),
                    color = W4eClipCpuOracle.Rgba8(255, 0, 0, 255),
                    antiAlias = W4eClipCpuOracle.AA.PathMsaa4,
                    clips = emptyList(),
                ),
            ),
        )

        assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 0u), hard)
        assertPixelsEqual(ubyteArrayOf(188u, 0u, 0u, 128u), analytic)
        assertPixelsEqual(ubyteArrayOf(255u, 0u, 0u, 255u), pathMsaa4Binary)
        assertFalse(hard.contentEquals(analytic))
    }

    @Test
    fun `W4dGeneral hard fill stroke and hairline preserve skew reflection and bounded perspective pixels`() {
        val fill = Path().apply {
            moveTo(1.25f, 1.25f)
            lineTo(3.75f, 1.25f)
            lineTo(3.75f, 3.75f)
            lineTo(1.25f, 3.75f)
            close()
        }
        val finiteStroke = Path().apply {
            moveTo(2.25f, 3.5f)
            lineTo(5.75f, 3.5f)
        }
        val hairline = Path().apply {
            moveTo(1.25f, 3.5f)
            lineTo(5.75f, 3.5f)
        }
        val cases = listOf(
            W4dGeneralPathCpuOracle.Draw(
                path = fill.toPathF32(),
                paint = Paint.fill(ColorARGB.of(255, 38, 161, 99)).copy(antiAlias = false),
                transform = Matrix3x3F32.skewing(0.25f, 0f),
                scissorI32 = RectI32(0, 0, 9, 9),
            ) to fill,
            W4dGeneralPathCpuOracle.Draw(
                path = fill.toPathF32(),
                paint = Paint.fill(ColorARGB.of(255, 224, 91, 37)).copy(antiAlias = false),
                transform = Matrix3x3F32(sx = -1f, kx = 0.25f, tx = 8f),
                scissorI32 = RectI32(0, 0, 9, 9),
            ) to fill,
            W4dGeneralPathCpuOracle.Draw(
                path = fill.toPathF32(),
                paint = Paint.fill(ColorARGB.of(255, 62, 103, 231)).copy(antiAlias = false),
                transform = Matrix3x3F32(persp0 = 0.05f),
                scissorI32 = RectI32(0, 0, 9, 9),
            ) to fill,
            W4dGeneralPathCpuOracle.Draw(
                path = finiteStroke.toPathF32(),
                paint = Paint.stroke(ColorARGB.of(255, 203, 41, 167), 1f).copy(
                    strokeCap = StrokeCap.BUTT,
                    antiAlias = false,
                ),
                transform = Matrix3x3F32.rotation(33f, pivotX = 4f, pivotY = 4f),
                scissorI32 = RectI32(0, 0, 9, 9),
            ) to finiteStroke,
            W4dGeneralPathCpuOracle.Draw(
                path = hairline.toPathF32(),
                paint = Paint.stroke(ColorARGB.of(255, 147, 73, 220), 0f).copy(antiAlias = false),
                transform = Matrix3x3F32.skewing(0.25f, 0f),
                scissorI32 = RectI32(0, 0, 9, 9),
            ) to hairline,
        )

        cases.forEach { (draw, path) ->
            val surface = Surface(9, 9)
            surface.canvas {
                concat(draw.transform)
                drawPath(path, draw.paint)
            }

            val result = surface.render()

            assertPreparedRouteEvidence(result)
            assertPixelsEqual(
                W4dGeneralPathCpuOracle.render(9, 9, listOf(draw)),
                result.pixels,
            )
        }
    }

    @Test
    fun `W4dGeneral hard path SrcOver order and public channel order are exact`() {
        val backPath = Path().apply {
            moveTo(1.25f, 1.25f)
            lineTo(5.75f, 1.25f)
            lineTo(5.75f, 5.75f)
            lineTo(1.25f, 5.75f)
            close()
        }
        val frontPath = Path().apply {
            moveTo(2.25f, 2.25f)
            lineTo(6.75f, 2.25f)
            lineTo(6.75f, 6.75f)
            lineTo(2.25f, 6.75f)
            close()
        }
        val backPaint = Paint.fill(ColorARGB.of(128, 218, 57, 41)).copy(antiAlias = false)
        val frontPaint = Paint.fill(ColorARGB.of(128, 31, 122, 228)).copy(antiAlias = false)
        val skew = Matrix3x3F32.skewing(0.25f, 0f)
        val draws = listOf(
            W4dGeneralPathCpuOracle.Draw(backPath.toPathF32(), backPaint, skew, RectI32(0, 0, 9, 9)),
            W4dGeneralPathCpuOracle.Draw(frontPath.toPathF32(), frontPaint, skew, RectI32(0, 0, 9, 9)),
        )

        listOf(PixelFormat.RGBA8, PixelFormat.BGRA8).forEach { format ->
            val surface = Surface(9, 9, format)
            surface.canvas {
                concat(skew)
                drawPath(backPath, backPaint)
                drawPath(frontPath, frontPaint)
            }

            val result = surface.render()

            assertPreparedRouteEvidence(result)
            assertPixelsEqual(W4dGeneralPathCpuOracle.render(9, 9, draws, format), result.pixels)
        }
    }

    @Test
    fun `W4dGeneral mixed hard and AA paths expose the four-sample capability terminal`() {
        val aaPath = Path().apply {
            // Inverse-skewed device rect [1, 1]..[5, 5]: every legal AA sample is
            // either fully covered or fully uncovered, independently of its position.
            moveTo(0.75f, 1f)
            lineTo(4.75f, 1f)
            lineTo(3.75f, 5f)
            lineTo(-0.25f, 5f)
            close()
        }
        val hardPath = Path().apply {
            // Inverse-skewed device rect [3, 3]..[5, 5].
            moveTo(2.25f, 3f)
            lineTo(4.25f, 3f)
            lineTo(3.75f, 5f)
            lineTo(1.75f, 5f)
            close()
        }
        val aaPaint = Paint.fill(ColorARGB.of(128, 245, 77, 48)).copy(antiAlias = true)
        val hardPaint = Paint.fill(ColorARGB.of(255, 24, 119, 242)).copy(antiAlias = false)
        val transform = Matrix3x3F32.skewing(0.25f, 0f)
        val surface = Surface(9, 9)
        surface.canvas {
            concat(transform)
            drawPath(aaPath, aaPaint)
            drawPath(hardPath, hardPaint)
        }

        val failure = assertFailsWith<GPUPlanSurfaceTerminalException> { surface.render() }

        assertEquals("w4d.general.texture-sample-support-unavailable", failure.code)
    }

    @Test
    fun `W4dGeneral AA terminal leaves the next public W4d render usable`() {
        val rejectedPath = Path().apply {
            moveTo(1.25f, 1.25f)
            lineTo(5.25f, 1.25f)
            lineTo(5.25f, 5.25f)
            lineTo(1.25f, 5.25f)
            close()
        }
        val rejected = Surface(9, 9)
        rejected.canvas {
            concat(Matrix3x3F32.skewing(0.25f, 0f))
            drawPath(rejectedPath, Paint.fill(ColorARGB.Red).copy(antiAlias = true))
        }

        val failure = assertFailsWith<GPUPlanSurfaceTerminalException> { rejected.render() }
        assertEquals("w4d.general.texture-sample-support-unavailable", failure.code)

        val path = Path().apply {
            moveTo(2.25f, 3.25f)
            lineTo(5.75f, 3.25f)
        }
        val paint = Paint.stroke(ColorARGB.of(255, 64, 165, 231), 1.5f).copy(antiAlias = false)
        val accepted = Surface(8, 6)
        accepted.canvas { drawPath(path, paint) }

        val result = accepted.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(w4dOracle(8, 6, path, paint), result.pixels)
    }

    @Test
    fun `W4d Butt Round and Square caps match the independent public-path oracle`() {
        val path = Path().apply {
            moveTo(3.25f, 3.25f)
            lineTo(6.75f, 3.25f)
        }
        StrokeCap.entries.forEach { cap ->
            val paint = Paint.stroke(ColorARGB.of(255, 219, 47, 91), width = 2f).copy(
                strokeCap = cap,
                antiAlias = false,
            )
            val expected = w4dOracle(10, 7, path, paint)
            val surface = Surface(10, 7)
            surface.canvas { drawPath(path, paint) }

            val result = surface.render()

            assertPreparedRouteEvidence(result)
            assertPixelsEqual(expected, result.pixels)
        }
    }

    @Test
    fun `W4d Miter Round Bevel and miter-limit fallback match the independent oracle`() {
        val path = Path().apply {
            moveTo(1.125f, 9.8125f)
            lineTo(5.125f, 2.3125f)
            lineTo(9.125f, 9.8125f)
        }
        val cases = listOf(
            Triple("high miter", StrokeJoin.MITER, 8f),
            Triple("round", StrokeJoin.ROUND, 8f),
            Triple("bevel", StrokeJoin.BEVEL, 8f),
            Triple("low miter", StrokeJoin.MITER, 1f),
        )
        val expectedByCase = cases.associate { (label, join, miter) ->
            val paint = Paint.stroke(ColorARGB.of(255, 36, 143, 227), width = 2.5f).copy(
                strokeJoin = join,
                strokeMiter = miter,
                antiAlias = false,
            )
            label to w4dOracle(12, 12, path, paint)
        }
        assertFalse(expectedByCase.getValue("high miter").contentEquals(expectedByCase.getValue("round")))
        assertFalse(expectedByCase.getValue("high miter").contentEquals(expectedByCase.getValue("bevel")))
        assertFalse(expectedByCase.getValue("round").contentEquals(expectedByCase.getValue("bevel")))
        assertContentEquals(expectedByCase.getValue("bevel"), expectedByCase.getValue("low miter"))

        cases.forEach { (label, join, miter) ->
            val paint = Paint.stroke(ColorARGB.of(255, 36, 143, 227), width = 2.5f).copy(
                strokeJoin = join,
                strokeMiter = miter,
                antiAlias = false,
            )
            val surface = Surface(12, 12)
            surface.canvas { drawPath(path, paint) }

            val result = surface.render()

            assertPreparedRouteEvidence(result)
            assertContentEquals(expectedByCase.getValue(label), result.pixels, label)
        }
    }

    @Test
    fun `W4d negative dash phase matches an independent source-arclength oracle`() {
        val path = Path().apply {
            moveTo(1f, 2.125f)
            lineTo(11f, 2.125f)
        }
        val paint = Paint.stroke(ColorARGB.of(255, 41, 207, 126), width = 1.5f).copy(
            strokeCap = StrokeCap.BUTT,
            pathEffect = PathEffect.Dash(floatArrayOf(2f, 2f), phase = -1f),
            antiAlias = false,
        )
        val surface = Surface(12, 5)
        surface.canvas { drawPath(path, paint) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(w4dOracle(12, 5, path, paint), result.pixels)
    }

    @Test
    fun `W4d hairline remains one device pixel under four-times scale`() {
        val path = Path().apply {
            moveTo(0.5f, 1.125f)
            lineTo(2.5f, 1.125f)
        }
        val paint = Paint.stroke(ColorARGB.White, width = 0f).copy(antiAlias = false)
        val transform = Matrix3x3F32(sx = 4f, sy = 4f)
        val draw = W4dPathStrokeCpuOracle.Draw(
            path = path.toPathF32(),
            paint = paint,
            transform = transform,
            scissorI32 = RectI32(0, 0, 12, 8),
        )
        val surface = Surface(12, 8)
        surface.canvas {
            scale(4f, 4f)
            drawPath(path, paint)
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4dPathStrokeCpuOracle.render(12, 8, listOf(draw)), result.pixels)
        val opaquePixelCountI32 = result.pixels.indices.step(4).count { result.pixels[it + 3] == 255.toUByte() }
        assertEquals(8, opaquePixelCountI32)
    }

    @Test
    fun `W4d stroke-and-fill donut applies translucent SrcOver once over its union`() {
        val donut = Path().apply {
            addRect(RectF32.ofLTRB(1f, 1f, 7f, 7f))
            moveTo(3f, 3f)
            lineTo(3f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 3f)
            close()
        }
        val color = ColorARGB.of(128, 225, 74, 35)
        val paint = Paint(
            color = color,
            style = PaintStyle.STROKE_AND_FILL,
            strokeWidth = 1.5f,
            strokeJoin = StrokeJoin.BEVEL,
            antiAlias = false,
        )
        val draw = W4dPathStrokeCpuOracle.Draw(
            donut.toPathF32(),
            paint,
            scissorI32 = RectI32(0, 0, 8, 8),
        )
        val surface = Surface(8, 8)
        surface.canvas { drawPath(donut, paint) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4dPathStrokeCpuOracle.render(8, 8, listOf(draw)), result.pixels)
        assertEquals(128.toUByte(), result.pixels[((1 * 8 + 1) * 4) + 3])
    }

    @Test
    fun `W4d mixed fill-stroke paint order and integral scissor are byte exact`() {
        val fillPath = Path().addRect(RectF32.ofLTRB(0f, 0f, 6f, 5f))
        val strokePath = Path().apply {
            moveTo(0.25f, 2.125f)
            lineTo(6.75f, 2.125f)
        }
        val fillPaint = Paint.fill(ColorARGB.of(173, 42, 93, 221)).copy(antiAlias = false)
        val strokePaint = Paint.stroke(ColorARGB.of(139, 238, 167, 31), 1.5f).copy(
            strokeCap = StrokeCap.SQUARE,
            antiAlias = false,
        )
        val scissor = RectI32(1, 1, 6, 4)
        val draws = listOf(
            W4dPathStrokeCpuOracle.Draw(fillPath.toPathF32(), fillPaint, scissorI32 = scissor),
            W4dPathStrokeCpuOracle.Draw(strokePath.toPathF32(), strokePaint, scissorI32 = scissor),
        )
        val surface = Surface(7, 5)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 6f, 4f), antiAlias = false)
            drawPath(fillPath, fillPaint)
            drawPath(strokePath, strokePaint)
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4dPathStrokeCpuOracle.render(7, 5, draws), result.pixels)
        assertTransparentOutside(result.pixels, 7, 5, scissor)
    }

    @Test
    fun `W4d RGBA8 and BGRA8 readback preserve public channel interpretation`() {
        val path = Path().apply {
            moveTo(1f, 2.125f)
            lineTo(6f, 2.125f)
        }
        val paint = Paint.stroke(ColorARGB.of(255, 231, 37, 19), 1.5f).copy(antiAlias = false)
        val draw = W4dPathStrokeCpuOracle.Draw(path.toPathF32(), paint, scissorI32 = RectI32(0, 0, 7, 5))
        val rgba = Surface(7, 5, PixelFormat.RGBA8).also { it.canvas { drawPath(path, paint) } }.render()
        val bgra = Surface(7, 5, PixelFormat.BGRA8).also { it.canvas { drawPath(path, paint) } }.render()

        assertPreparedRouteEvidence(rgba)
        assertPreparedRouteEvidence(bgra)
        assertPixelsEqual(W4dPathStrokeCpuOracle.render(7, 5, listOf(draw), PixelFormat.RGBA8), rgba.pixels)
        assertPixelsEqual(W4dPathStrokeCpuOracle.render(7, 5, listOf(draw), PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `W4c hard edge triangle matches the independent line oracle through Surface`() {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 0f)
            lineTo(0f, 4f)
            close()
        }
        val draw = W4cPathFillCpuOracle.Draw(
            path = triangle.toPathF32(),
            transform = Matrix3x3F32.Identity,
            color = ColorARGB.Red,
            scissorI32 = RectI32(0, 0, 4, 4),
        )
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(triangle, Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(4, 4, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c concave hard edge path matches the independent line oracle`() {
        val concave = Path().apply {
            moveTo(0f, 0f)
            lineTo(5f, 0f)
            lineTo(5f, 1f)
            lineTo(2f, 1f)
            lineTo(2f, 5f)
            lineTo(0f, 5f)
            close()
        }
        val draw = w4cDraw(concave, ColorARGB.Blue, 5, 5)
        val surface = Surface(5, 5)
        surface.canvas { drawPath(concave, Paint.fill(ColorARGB.Blue).copy(antiAlias = false)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(5, 5, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c Winding contour with an opposite winding hole matches the independent line oracle`() {
        val windingHole = Path().apply {
            moveTo(0f, 0f)
            lineTo(6f, 0f)
            lineTo(6f, 6f)
            lineTo(0f, 6f)
            close()
            moveTo(1f, 1f)
            lineTo(1f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 1f)
            close()
        }
        val draw = w4cDraw(windingHole, ColorARGB.of(255, 37, 151, 88), 6, 6)
        val surface = Surface(6, 6)
        surface.canvas { drawPath(windingHole, Paint.fill(draw.color).copy(antiAlias = false)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(6, 6, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c EvenOdd contour with a same winding hole matches the independent line oracle`() {
        val evenOddHole = Path().apply {
            moveTo(0f, 0f)
            lineTo(6f, 0f)
            lineTo(6f, 6f)
            lineTo(0f, 6f)
            close()
            moveTo(1f, 1f)
            lineTo(5f, 1f)
            lineTo(5f, 5f)
            lineTo(1f, 5f)
            close()
            fillType = FillType.EVEN_ODD
        }
        val draw = w4cDraw(evenOddHole, ColorARGB.of(255, 167, 66, 245), 6, 6)
        val surface = Surface(6, 6)
        surface.canvas { drawPath(evenOddHole, Paint.fill(draw.color).copy(antiAlias = false)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(6, 6, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c preserves a retraced self-intersection through the stencil path`() {
        val retracedBowTie = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 4f)
            lineTo(0f, 4f)
            lineTo(4f, 0f)
            lineTo(0f, 0f)
            lineTo(4f, 4f)
            close()
        }
        val draw = w4cDraw(retracedBowTie, ColorARGB.of(255, 238, 136, 42), 4, 4)
        val surface = Surface(4, 4)
        surface.canvas { drawPath(retracedBowTie, Paint.fill(draw.color).copy(antiAlias = false)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(4, 4, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c scissor limits a path while retaining exact independent line pixels`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(5f, 0f)
            lineTo(0f, 5f)
            close()
        }
        val scissor = RectI32(1, 1, 4, 4)
        val draw = w4cDraw(path, ColorARGB.of(255, 68, 203, 157), 5, 5, scissorI32 = scissor)
        val surface = Surface(5, 5)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 4f, 4f), antiAlias = false)
            drawPath(path, Paint.fill(draw.color).copy(antiAlias = false))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(5, 5, listOf(draw)), result.pixels)
        assertTransparentOutside(result.pixels, 5, 5, scissor)
    }

    @Test
    fun `W4c negative axis scale maps a path in device space before exact comparison`() {
        val localTriangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 0f)
            lineTo(0f, 4f)
            close()
        }
        val transform = Matrix3x3F32(sx = -1f, sy = 1f, tx = 4f)
        val draw = w4cDraw(localTriangle, ColorARGB.of(255, 227, 71, 49), 4, 4, transform = transform)
        val surface = Surface(4, 4)
        surface.canvas {
            translate(4f, 0f)
            scale(-1f, 1f)
            drawPath(localTriangle, Paint.fill(draw.color).copy(antiAlias = false))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4cPathFillCpuOracle.render(4, 4, listOf(draw)), result.pixels)
    }

    @Test
    fun `W4c quantizes sRGB attachment contents between translucent path draws`() {
        val first = ColorARGB.of(182, 145, 133, 48)
        val second = ColorARGB.of(23, 221, 204, 240)
        val fullPixel = Path().apply {
            moveTo(0f, 0f)
            lineTo(1f, 0f)
            lineTo(1f, 1f)
            lineTo(0f, 1f)
            close()
        }
        val draws = listOf(
            w4cDraw(fullPixel, first, 1, 1),
            w4cDraw(fullPixel, second, 1, 1),
        )
        val surface = Surface(1, 1)
        surface.canvas {
            drawPath(fullPixel, Paint.fill(first).copy(antiAlias = false))
            drawPath(fullPixel, Paint.fill(second).copy(antiAlias = false))
        }

        val result = surface.render()
        val attachmentQuantized = W4cPathFillCpuOracle.render(1, 1, draws)
        val frameEndQuantized = W4cPathFillCpuOracle.renderWithFrameEndQuantization(1, 1, draws)

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(attachmentQuantized, result.pixels)
        assertFalse(attachmentQuantized.contentEquals(frameEndQuantized))
    }

    @Test
    fun `W4c path bytes use the requested RGBA and BGRA channel order`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(3f, 0f)
            lineTo(0f, 3f)
            close()
        }
        val color = ColorARGB.of(255, 231, 37, 19)
        val draw = w4cDraw(path, color, 3, 3)
        val rgba = Surface(3, 3, PixelFormat.RGBA8).also { surface ->
            surface.canvas { drawPath(path, Paint.fill(color).copy(antiAlias = false)) }
        }.render()
        val bgra = Surface(3, 3, PixelFormat.BGRA8).also { surface ->
            surface.canvas { drawPath(path, Paint.fill(color).copy(antiAlias = false)) }
        }.render()

        assertPreparedRouteEvidence(rgba)
        assertPreparedRouteEvidence(bgra)
        assertPixelsEqual(W4cPathFillCpuOracle.render(3, 3, listOf(draw)), rgba.pixels)
        assertPixelsEqual(W4cPathFillCpuOracle.render(3, 3, listOf(draw), PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `W4c certified quad cubic and SVG arc fixtures differ from line-only chord counterfactuals and match exact pixels`() {
        data class CurveFixture(
            val name: String,
            val curve: Path,
            val chord: Path,
            val widthI32: Int,
            val heightI32: Int,
            val scissorI32: RectI32,
        )

        fun chordPath(): Path = Path().apply {
            moveTo(2f, 2f)
            lineTo(6f, 3f)
            lineTo(6f, 7f)
            lineTo(2f, 7f)
            close()
        }

        val fullScissor = RectI32(0, 0, 8, 8)
        val cases = listOf(
            CurveFixture(
                name = "quad",
                curve = Path().apply {
                    moveTo(2f, 2f)
                    quadTo(-2f, 3.5f, 6f, 3f)
                    lineTo(6f, 7f)
                    lineTo(2f, 7f)
                    close()
                },
                chord = chordPath(),
                widthI32 = 8,
                heightI32 = 8,
                scissorI32 = fullScissor,
            ),
            CurveFixture(
                name = "cubic",
                curve = Path().apply {
                    moveTo(2f, 2f)
                    cubicTo(5.6805553f, 2.3333333f, -0.3472222f, 2.6666667f, 6f, 3f)
                    lineTo(6f, 7f)
                    lineTo(2f, 7f)
                    close()
                },
                chord = chordPath(),
                widthI32 = 8,
                heightI32 = 8,
                scissorI32 = fullScissor,
            ),
            CurveFixture(
                name = "arc",
                curve = Path().apply {
                    moveTo(2f, 2f)
                    arcTo(2.5f, 2.5f, 0f, largeArc = false, sweep = false, x = 6f, y = 3f)
                    lineTo(6f, 7f)
                    lineTo(2f, 7f)
                    close()
                },
                chord = chordPath(),
                widthI32 = 8,
                heightI32 = 8,
                scissorI32 = RectI32(3, 2, 4, 3),
            ),
        )
        val color = ColorARGB.of(255, 78, 126, 219)

        cases.forEach { fixture ->
            val draw = w4cDraw(
                fixture.curve,
                color,
                fixture.widthI32,
                fixture.heightI32,
                scissorI32 = fixture.scissorI32,
            )
            val certificate = W4cPathFillCpuOracle.certifyCurveFixture(
                fixture.widthI32,
                fixture.heightI32,
                draw,
            )
            assertIs<W4cPathFillCpuOracle.CurveFixtureCertificate.Certified>(certificate, certificate.toString())
            val expectedPixels = W4cPathFillCpuOracle.render(fixture.widthI32, fixture.heightI32, listOf(draw))
            val chordPixels = W4cPathFillCpuOracle.render(
                fixture.widthI32,
                fixture.heightI32,
                listOf(
                    w4cDraw(
                        fixture.chord,
                        color,
                        fixture.widthI32,
                        fixture.heightI32,
                        scissorI32 = fixture.scissorI32,
                    ),
                ),
            )
            assertFalse(expectedPixels.contentEquals(chordPixels), fixture.name)

            val surface = Surface(fixture.widthI32, fixture.heightI32)
            surface.canvas {
                if (fixture.scissorI32 != fullScissor) {
                    clipRect(
                        RectF32.ofLTRB(
                            fixture.scissorI32.left.toFloat(),
                            fixture.scissorI32.top.toFloat(),
                            fixture.scissorI32.right.toFloat(),
                            fixture.scissorI32.bottom.toFloat(),
                        ),
                        antiAlias = false,
                    )
                }
                drawPath(fixture.curve, Paint.fill(color).copy(antiAlias = false))
            }

            val result = surface.render()

            assertPreparedRouteEvidence(result)
            assertPixelsEqual(expectedPixels, result.pixels)
        }
    }

    @Test
    fun `W4c curve certificate rejects a far X endpoint tied to the pixel ray`() {
        val path = Path().apply {
            moveTo(64f, -1f)
            quadTo(96f, 0f, 128f, 0.5f)
        }
        val certificate = W4cPathFillCpuOracle.certifyCurveFixture(
            widthI32 = 1,
            heightI32 = 1,
            draw = w4cDraw(path, ColorARGB.Red, 1, 1),
        )

        assertIs<W4cPathFillCpuOracle.CurveFixtureCertificate.Uncertified>(certificate, certificate.toString())
    }

    @Test
    fun `W4c curve certificate retains a monotonic curve across an internal subdivision ray`() {
        val path = Path().apply {
            moveTo(2f, 2f)
            quadTo(-2f, 3.5f, 6f, 3f)
        }
        val certificate = W4cPathFillCpuOracle.certifyCurveFixture(
            widthI32 = 8,
            heightI32 = 8,
            draw = w4cDraw(path, ColorARGB.Red, 8, 8),
        )

        assertIs<W4cPathFillCpuOracle.CurveFixtureCertificate.Certified>(certificate, certificate.toString())
    }

    @Test
    fun `W4b fractional zero-radius rrect uses literal exact rectangular overlap`() {
        val shape = RRectF32.of(RectF32(0.25f, 0.5f, 1.75f, 1.5f))
        val draws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(ColorARGB.White, shape, RectI32(0, 0, 2, 2)),
        )
        val surface = Surface(2, 2)
        surface.canvas {
            drawRRect(shape, Paint.fill(ColorARGB.White).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            ubyteArrayOf(
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
            ),
            result.pixels,
        )
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(2, 2, draws), result.pixels)
    }

    @Test
    fun `W4b red radius-one rrect matches all nine SDF pixels in RGBA and BGRA`() {
        val red = ColorARGB.Red
        val shape = RRectF32.of(
            RectF32(0f, 0f, 3f, 3f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(1f, 1f),
        )
        val draws = listOf(W4bAnalyticRRectCpuOracle.Draw(red, shape, RectI32(0, 0, 3, 3)))
        val rgba = Surface(3, 3, PixelFormat.RGBA8).also { surface ->
            surface.canvas { drawRRect(shape, Paint.fill(red).copy(antiAlias = true)) }
        }.render()
        val bgra = Surface(3, 3, PixelFormat.BGRA8).also { surface ->
            surface.canvas { drawRRect(shape, Paint.fill(red).copy(antiAlias = true)) }
        }.render()

        assertPreparedRouteEvidence(rgba)
        assertPreparedRouteEvidence(bgra)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(3, 3, draws), rgba.pixels)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(3, 3, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `W4b partial translucent Rect and RRect preserve inter-draw quantized SrcOver paint order`() {
        val first = ColorARGB.of(137, 14, 157, 83)
        val second = ColorARGB.of(191, 227, 62, 174)
        val firstRect = RectF32(0.2f, 0.2f, 0.8f, 0.8f)
        val firstShape = RRectF32.of(firstRect)
        val secondShape = RRectF32.of(RectF32(0.1f, 0.1f, 0.9f, 0.9f), radius = 0.2f)
        val scissor = RectI32(0, 0, 1, 1)
        val forwardDraws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(first, firstShape, scissor),
            W4bAnalyticRRectCpuOracle.Draw(second, secondShape, scissor),
        )
        val reverseDraws = listOf(
            W4bAnalyticRRectCpuOracle.Draw(second, secondShape, scissor),
            W4bAnalyticRRectCpuOracle.Draw(first, firstShape, scissor),
        )
        val forward = Surface(1, 1).also { surface ->
            surface.canvas {
                drawRect(firstRect, Paint.fill(first).copy(antiAlias = true))
                drawRRect(secondShape, Paint.fill(second).copy(antiAlias = true))
            }
        }.render()
        val reverse = Surface(1, 1).also { surface ->
            surface.canvas {
                drawRRect(secondShape, Paint.fill(second).copy(antiAlias = true))
                drawRect(firstRect, Paint.fill(first).copy(antiAlias = true))
            }
        }.render()

        assertPreparedRouteEvidence(forward)
        assertPreparedRouteEvidence(reverse)
        assertPixelsEqual(ubyteArrayOf(181u, 66u, 140u, 172u), forward.pixels)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(1, 1, forwardDraws), forward.pixels)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(1, 1, reverseDraws), reverse.pixels)
        assertFalse(forward.pixels.contentEquals(reverse.pixels))
        assertPixelsEqual(
            ubyteArrayOf(181u, 66u, 140u, 173u),
            W4bAnalyticRRectCpuOracle.renderWithFrameEndQuantization(1, 1, forwardDraws),
        )
        assertFalse(
            forward.pixels.contentEquals(
                W4bAnalyticRRectCpuOracle.renderWithFrameEndQuantization(1, 1, forwardDraws),
            ),
        )
    }

    @Test
    fun `W4b asymmetric rrect anchors each independently visible corner`() {
        val color = ColorARGB.Black
        val shape = RRectF32.of(
            RectF32(0f, 0f, 8f, 8f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(2.5f, 1f),
            CornerRadiiF32.of(1.5f, 2.5f),
            CornerRadiiF32.of(0.75f, 1.5f),
        )
        val surface = Surface(8, 8)
        surface.canvas { drawRRect(shape, Paint.fill(color).copy(antiAlias = true)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                8,
                8,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, shape, RectI32(0, 0, 8, 8))),
            ),
            result.pixels,
        )
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 166u), result.pixels.copyOfRange(0, 4))
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 151u), result.pixels.copyOfRange(28, 32))
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 108u), result.pixels.copyOfRange(252, 256))
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 218u), result.pixels.copyOfRange(224, 228))
    }

    @Test
    fun `W4b supports a positive non-unit scale with exact device rrect pixels`() {
        val color = ColorARGB.Black
        val localShape = RRectF32.of(
            RectF32(0f, 0f, 2f, 2f),
            CornerRadiiF32.of(0.5f, 1f),
            CornerRadiiF32.of(1f, 0.5f),
            CornerRadiiF32.of(0.25f, 0.75f),
            CornerRadiiF32.of(0.75f, 0.25f),
        )
        val deviceShape = RRectF32.of(
            RectF32(0f, 0f, 3f, 1.5f),
            CornerRadiiF32.of(0.75f, 0.75f),
            CornerRadiiF32.of(1.5f, 0.375f),
            CornerRadiiF32.of(0.375f, 0.5625f),
            CornerRadiiF32.of(1.125f, 0.1875f),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            scale(1.5f, 0.75f)
            drawRRect(localShape, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                3,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, deviceShape, RectI32(0, 0, 4, 3))),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4b supports Y reflection with exact corner permutation`() {
        val color = ColorARGB.Black
        val localShape = asymmetricShape()
        val deviceShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(0.5f, 1f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 1f),
        )
        val surface = Surface(4, 4)
        surface.canvas {
            translate(0f, 4f)
            scale(1f, -1f)
            drawRRect(localShape, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                4,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, deviceShape, RectI32(0, 0, 4, 4))),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4b supports XY reflection with exact corner permutation`() {
        val color = ColorARGB.Black
        val localShape = asymmetricShape()
        val deviceShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(0.5f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(2f, 1f),
        )
        val surface = Surface(4, 4)
        surface.canvas {
            translate(4f, 4f)
            scale(-1f, -1f)
            drawRRect(localShape, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                4,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, deviceShape, RectI32(0, 0, 4, 4))),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4b normalizes excessive radii to the box before exact pixel comparison`() {
        val color = ColorARGB.of(197, 146, 61, 213)
        val excessive = RRectF32.of(RectF32(0f, 0f, 4f, 4f), radius = 3f)
        val normalized = RRectF32.of(RectF32(0f, 0f, 4f, 4f), radius = 2f)
        val surface = Surface(4, 4)
        surface.canvas { drawRRect(excessive, Paint.fill(color).copy(antiAlias = true)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                4,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, normalized, RectI32(0, 0, 4, 4))),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4b subpixel rrect exercises a scalar SDF scale strictly between zero and one`() {
        val color = ColorARGB.of(223, 46, 133, 202)
        val shape = RRectF32.of(
            RectF32(0.2f, 0.15f, 0.8f, 0.75f),
            CornerRadiiF32.of(0.15f, 0.15f),
            CornerRadiiF32.of(0.15f, 0.15f),
            CornerRadiiF32.of(0.15f, 0.15f),
            CornerRadiiF32.of(0.15f, 0.15f),
        )
        val surface = Surface(1, 1)
        surface.canvas { drawRRect(shape, Paint.fill(color).copy(antiAlias = true)) }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                1,
                1,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, shape, RectI32(0, 0, 1, 1))),
            ),
            result.pixels,
        )
    }

    @Test
    fun `W4b asymmetric reflected rrect clips exactly at the integral scissor`() {
        val color = ColorARGB.of(191, 37, 155, 229)
        val localShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 2f),
            CornerRadiiF32.of(0.5f, 1f),
        )
        val deviceShape = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(2f, 1f),
            CornerRadiiF32.of(1f, 1f),
            CornerRadiiF32.of(0.5f, 1f),
            CornerRadiiF32.of(1f, 2f),
        )
        val scissor = RectI32(1, 0, 4, 4)
        val surface = Surface(4, 4)
        surface.canvas {
            clipRect(RectF32(1f, 0f, 4f, 4f), antiAlias = false)
            translate(4f, 0f)
            scale(-1f, 1f)
            drawRRect(localShape, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(
            W4bAnalyticRRectCpuOracle.render(
                4,
                4,
                listOf(W4bAnalyticRRectCpuOracle.Draw(color, deviceShape, scissor)),
            ),
            result.pixels,
        )
        assertTransparentOutside(result.pixels, 4, 4, scissor)
    }

    @Test
    fun `W4b renders 512 mixed draws through the public Surface boundary`() {
        val blue = ColorARGB.Blue
        val translucentRed = ColorARGB.of(128, 255, 0, 0)
        val rect = RectF32(0f, 0f, 1f, 1f)
        val rrect = RRectF32.of(
            rect,
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
            CornerRadiiF32.of(0.25f, 0.25f),
        )
        val scissor = RectI32(0, 0, 1, 1)
        val draws = List(511) {
            W4bAnalyticRRectCpuOracle.Draw(blue, RRectF32.of(rect), scissor)
        } + W4bAnalyticRRectCpuOracle.Draw(translucentRed, rrect, scissor)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4B_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(511) { drawRect(rect, Paint.fill(blue).copy(antiAlias = true)) }
            drawRRect(rrect, Paint.fill(translucentRed).copy(antiAlias = true))
        }

        val result = surface.render()

        assertPreparedRouteEvidence(result)
        assertPixelsEqual(W4bAnalyticRRectCpuOracle.render(1, 1, draws), result.pixels)
    }

    @Test
    fun `W4a oracle calculates the hand checked fractional coverage and BGRA swizzle`() {
        val fractional = W4aAnalyticRectCpuOracle.render(
            width = 2,
            height = 2,
            draws = listOf(
                W4aAnalyticRectCpuOracle.Draw(
                    ColorARGB.White,
                    RectF32(0.25f, 0.5f, 1.75f, 1.5f),
                    RectI32(0, 0, 2, 2),
                ),
            ),
        )
        val bgra = W4aAnalyticRectCpuOracle.render(
            width = 1,
            height = 1,
            draws = listOf(
                W4aAnalyticRectCpuOracle.Draw(
                    ColorARGB.Red,
                    RectF32(0f, 0f, 1f, 1f),
                    RectI32(0, 0, 1, 1),
                ),
            ),
            format = PixelFormat.BGRA8,
        )

        // Each touched pixel has coverage (0.75 * 0.5) = 0.375 before blending.
        assertPixelsEqual(
            ubyteArrayOf(
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
                165u, 165u, 165u, 96u,
            ),
            fractional,
        )
        assertPixelsEqual(ubyteArrayOf(0u, 0u, 255u, 255u), bgra)
    }

    @Test
    fun `opaque fractional AA rectangle matches the independent W4a oracle`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(
                color,
                RectF32(0.25f, 0.5f, 3.75f, 2.25f),
                RectI32(0, 0, 4, 3),
            ),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws.single().bounds, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `overlapping translucent fractional AA rectangles quantize between draws`() {
        val first = ColorARGB.of(137, 14, 157, 83)
        val second = ColorARGB.of(191, 227, 62, 174)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(first, RectF32(0.25f, 0.25f, 2.75f, 2.5f), RectI32(0, 0, 4, 3)),
            W4aAnalyticRectCpuOracle.Draw(second, RectF32(1.5f, 0.5f, 3.75f, 2.75f), RectI32(0, 0, 4, 3)),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws[0].bounds, Paint.fill(first).copy(antiAlias = true))
            drawRect(draws[1].bounds, Paint.fill(second).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `integral scissor clips an AA fringe and leaves outside pixels transparent`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val scissor = RectI32(1, 0, 4, 2)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(color, RectF32(0.25f, 0.5f, 3.75f, 2.25f), scissor),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            clipRect(RectF32(1f, 0f, 4f, 2f), antiAlias = false)
            drawRect(draws.single().bounds, Paint.fill(color).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
        assertTransparentOutside(result.pixels, 4, 3, scissor)
    }

    @Test
    fun `fractional W4a rectangle supports RGBA and BGRA attachment ordering`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(color, RectF32(0.25f, 0.5f, 3.75f, 2.25f), RectI32(0, 0, 4, 3)),
        )

        val rgba = renderFractionalScene(PixelFormat.RGBA8, draws)
        val bgra = renderFractionalScene(PixelFormat.BGRA8, draws)

        assertW4aEvidence(rgba)
        assertW4aEvidence(bgra)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), rgba.pixels)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `integral and fractional AA rectangles share the W4a frame in paint order`() {
        val integral = ColorARGB.Blue
        val fractional = ColorARGB.Red
        val draws = listOf(
            W4aAnalyticRectCpuOracle.Draw(
                integral, RectF32(0f, 0f, 2f, 2f), RectI32(0, 0, 4, 3),
            ),
            W4aAnalyticRectCpuOracle.Draw(
                fractional, RectF32(1.25f, 0.5f, 3.75f, 2.25f), RectI32(0, 0, 4, 3),
            ),
        )
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(draws[0].bounds, Paint.fill(integral).copy(antiAlias = true))
            drawRect(draws[1].bounds, Paint.fill(fractional).copy(antiAlias = true))
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
    }

    @Test
    fun `512 fractional AA rectangles plus annotation render through W4a`() {
        val color = ColorARGB.Red
        val draw = W4aAnalyticRectCpuOracle.Draw(
            color,
            RectF32(0f, 0f, 0.5f, 1f),
            RectI32(0, 0, 1, 1),
        )
        val draws = List(512) { draw }
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4A_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(512) { drawRect(draw.bounds, Paint.fill(color).copy(antiAlias = true)) }
            drawAnnotation(RectF32.Empty, "evidence", "w4a-512")
        }

        val result = surface.render()

        assertW4aEvidence(result)
        assertPixelsEqual(W4aAnalyticRectCpuOracle.render(1, 1, draws), result.pixels)
    }

    @Test
    fun `513 fractional AA rectangles retain known legacy pixel under the W4a terminal budget`() {
        val color = ColorARGB.of(255, 40, 120, 210)
        val bounds = RectF32(0.25f, 0.25f, 0.75f, 0.75f)
        val surface = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = W4A_512_FRAME_BUDGET_BYTES))
        surface.canvas {
            repeat(513) { drawRect(bounds, Paint.fill(color).copy(antiAlias = true)) }
        }

        val result = surface.render()

        assertPixelsEqual(ubyteArrayOf(38u, 119u, 209u, 253u), result.pixels)
    }

    @Test
    fun `opaque overlapping rectangles match the independent CPU oracle`() {
        val red = ColorARGB.of(255, 231, 37, 19)
        val blue = ColorARGB.of(255, 17, 83, 219)
        val surface = Surface(4, 3)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(red).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(2f, 1f, 4f, 3f), Paint.fill(blue).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                4,
                3,
                listOf(
                    W3SolidRectCpuOracle.Draw(red, 0, 0, 3, 2),
                    W3SolidRectCpuOracle.Draw(blue, 2, 1, 4, 3),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `translucent rectangles use linear premultiplied SrcOver exactly`() {
        val first = ColorARGB.of(101, 23, 181, 71)
        val second = ColorARGB.of(173, 211, 47, 139)
        val surface = Surface(3, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(1f, 0f, 3f, 2f), Paint.fill(second).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                3,
                2,
                listOf(
                    W3SolidRectCpuOracle.Draw(first, 0, 0, 3, 2),
                    W3SolidRectCpuOracle.Draw(second, 1, 0, 3, 2),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `DeviceRect clip limits an otherwise larger solid rectangle`() {
        val green = ColorARGB.of(255, 29, 197, 113)
        val surface = Surface(5, 4)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 4f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 5f, 4f), Paint.fill(green).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                5,
                4,
                listOf(W3SolidRectCpuOracle.Draw(green, 0, 0, 5, 4, 1, 1, 4, 3)),
            ),
            result.pixels,
        )
    }

    @Test
    fun `DrawColor SrcOver matches the independent CPU oracle`() {
        val backdrop = ColorARGB.of(151, 44, 96, 203)
        val foreground = ColorARGB.of(119, 216, 71, 31)
        val surface = Surface(2, 2)
        surface.canvas {
            drawColor(backdrop)
            drawColor(foreground, BlendMode.SRC_OVER)
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                2,
                2,
                listOf(
                    W3SolidRectCpuOracle.Draw(backdrop, 0, 0, 2, 2),
                    W3SolidRectCpuOracle.Draw(foreground, 0, 0, 2, 2),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `RGBA and BGRA Surface results match the same scene after channel ordering`() {
        val first = ColorARGB.of(137, 14, 157, 83)
        val second = ColorARGB.of(191, 227, 62, 174)
        val draws = listOf(
            W3SolidRectCpuOracle.Draw(first, 0, 0, 3, 2),
            W3SolidRectCpuOracle.Draw(second, 1, 0, 4, 2),
        )

        val rgba = renderScene(PixelFormat.RGBA8, first, second)
        val bgra = renderScene(PixelFormat.BGRA8, first, second)

        assertW3Evidence(rgba)
        assertW3Evidence(bgra)
        assertPixelsEqual(W3SolidRectCpuOracle.render(4, 2, draws, PixelFormat.RGBA8), rgba.pixels)
        assertPixelsEqual(W3SolidRectCpuOracle.render(4, 2, draws, PixelFormat.BGRA8), bgra.pixels)
    }

    @Test
    fun `attachment quantization is visible to the next translucent SrcOver draw`() {
        val first = ColorARGB.of(182, 145, 133, 48)
        val second = ColorARGB.of(23, 221, 204, 240)
        val surface = Surface(1, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint.fill(second).copy(antiAlias = false))
        }

        val result = surface.render()
        assertW3Evidence(result)
        assertPixelsEqual(ubyteArrayOf(137u, 126u, 88u, 189u), result.pixels)
        assertPixelsEqual(
            W3SolidRectCpuOracle.render(
                1,
                1,
                listOf(
                    W3SolidRectCpuOracle.Draw(first, 0, 0, 1, 1),
                    W3SolidRectCpuOracle.Draw(second, 0, 0, 1, 1),
                ),
            ),
            result.pixels,
        )
    }

    @Test
    fun `unsupported SRC scene retains its known legacy pixels`() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawColor(ColorARGB.Blue)
            drawColor(ColorARGB.of(128, 255, 0, 0), BlendMode.SRC)
        }

        assertPixelsEqual(ubyteArrayOf(188u, 0u, 0u, 128u), surface.render().pixels)
    }

    private fun renderScene(format: PixelFormat, first: ColorARGB, second: ColorARGB): RenderResult {
        val surface = Surface(4, 2, format)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 2f), Paint.fill(first).copy(antiAlias = false))
            drawRect(RectF32.ofLTRB(1f, 0f, 4f, 2f), Paint.fill(second).copy(antiAlias = false))
        }
        return surface.render()
    }

    private fun Surface.renderPathAa4OrSkip(): RenderResult = try {
        render()
    } catch (error: IllegalStateException) {
        if (error.message == W4E_AA4_UNAVAILABLE_DIAGNOSTIC) {
            assumeTrue(false, error.message)
        }
        throw error
    }

    private fun w4cDraw(
        path: Path,
        color: ColorARGB,
        widthI32: Int,
        heightI32: Int,
        transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        scissorI32: RectI32 = RectI32(0, 0, widthI32, heightI32),
    ): W4cPathFillCpuOracle.Draw = W4cPathFillCpuOracle.Draw(
        path = path.toPathF32(),
        transform = transform,
        color = color,
        scissorI32 = scissorI32,
    )

    private fun w4dOracle(widthI32: Int, heightI32: Int, path: Path, paint: Paint): UByteArray =
        W4dPathStrokeCpuOracle.render(
            widthI32,
            heightI32,
            listOf(
                W4dPathStrokeCpuOracle.Draw(
                    path.toPathF32(),
                    paint,
                    scissorI32 = RectI32(0, 0, widthI32, heightI32),
                ),
            ),
        )

    private fun renderFractionalScene(
        format: PixelFormat,
        draws: List<W4aAnalyticRectCpuOracle.Draw>,
    ): RenderResult {
        val surface = Surface(4, 3, format)
        surface.canvas {
            draws.forEach { drawRect(it.bounds, Paint.fill(it.color).copy(antiAlias = true)) }
        }
        return surface.render()
    }

    private fun asymmetricShape(): RRectF32 = RRectF32.of(
        RectF32(0f, 0f, 4f, 4f),
        CornerRadiiF32.of(1f, 1f),
        CornerRadiiF32.of(2f, 1f),
        CornerRadiiF32.of(1f, 2f),
        CornerRadiiF32.of(0.5f, 1f),
    )

    private fun assertW3Evidence(result: RenderResult) {
        val expectedScopeKinds = setOf("Render", "Readback")
        assertTrue(
            result.nativeEvidenceScopeKinds.containsAll(expectedScopeKinds),
            result.nativeEvidenceScopeKinds.toString(),
        )
        assertEquals(expectedScopeKinds, result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertW4aEvidence(result: RenderResult) {
        assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertPreparedRouteEvidence(result: RenderResult) {
        assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    }

    private fun assertTransparentOutside(pixels: UByteArray, width: Int, height: Int, scissor: RectI32) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x !in scissor.left until scissor.right || y !in scissor.top until scissor.bottom) {
                    val offset = (y * width + x) * 4
                    assertPixelsEqual(ubyteArrayOf(0u, 0u, 0u, 0u), pixels.copyOfRange(offset, offset + 4))
                }
            }
        }
    }

    private fun assertPixelsEqual(expected: UByteArray, actual: UByteArray) {
        assertContentEquals(expected, actual, "expected=${expected.toList()} actual=${actual.toList()}")
    }

    private companion object {
        const val W4E_AA4_UNAVAILABLE_DIAGNOSTIC =
            "w4e.clip.sample-count-unavailable: W4e AA clip producer support is unavailable"
        const val W4A_512_FRAME_BUDGET_BYTES = 164_100L
        const val W4B_512_FRAME_BUDGET_BYTES = 164_100L
    }
}
