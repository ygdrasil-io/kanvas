package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMask
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendCoverageMaskRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.DiagnosticLevel
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.math.pow

@OptIn(ExperimentalUnsignedTypes::class)
class GPUSaveLayerCompositeRegressionTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun layerRestoreAcceptsEveryBlendMode() {
        requireWebGpu()

        BlendMode.entries.forEach { mode ->
            val result = Surface(width = 8, height = 8).run {
                canvas {
                    drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                    saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                    drawRect(Rect(2f, 2f, 6f, 6f), Paint(color = translucentBlue.toColor(), antiAlias = false))
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$mode ${result.diagnostics.entries}")
            if (mode.toGpuBlendFacts().needsDestinationTexture()) {
                assertTrue(
                    result.diagnostics.entries.any { entry ->
                        entry.code.startsWith("route:destination-read:saveLayer:") &&
                            entry.reason == "gpu-copy-then-formula"
                    },
                    "$mode saveLayer restore did not report its GPU destination-read formula route: " +
                        result.diagnostics.entries,
                )
            }
        }
    }

    /**
     * An outer Canvas clip constrains the group restore, not every child draw in the temporary
     * layer. Otherwise transparent layer pixels outside the clip corrupt the parent for SRC and
     * DST_IN, and an AA clip's F is applied twice.
     */
    @Test
    fun `public saveLayer defers outer scissor and AA clips to one group restore`() {
        requireWebGpu()

        listOf(BlendMode.SRC, BlendMode.DST_IN, BlendMode.MULTIPLY).forEach { mode ->
            listOf(
                OuterClip("scissor", Rect(12f, 12f, 24f, 24f), antiAlias = false, edge = null),
                OuterClip("alpha-mask", Rect(12.5f, 12.5f, 23.5f, 23.5f), antiAlias = true, edge = Point(12f, 16f)),
            ).forEach { outerClip ->
                val result = Surface(width = 32, height = 32).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                        clipRect(outerClip.rect, ClipOp.INTERSECT, outerClip.antiAlias)
                        saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                        drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
                        restore()
                    }
                    render()
                }

                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 16,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = 1f),
                    tolerance = 2,
                )
                // (10,16) is inside the child rect but outside the outer clip. It must leave D intact.
                assertPixelNearAt(result.pixels, width = 32, x = 10, y = 16, expected = white, tolerance = 2)
                outerClip.edge?.let { edge ->
                    assertPixelNearAt(
                        result.pixels,
                        width = 32,
                        x = edge.x.toInt(),
                        y = edge.y.toInt(),
                        expected = publicLayerExpected(mode, coverage = .5f),
                        tolerance = 2,
                    )
                }
                if (mode == BlendMode.MULTIPLY) {
                    assertTrue(
                        result.diagnostics.entries.any { entry ->
                            entry.code.startsWith("route:destination-read:saveLayer:") &&
                                entry.reason == "gpu-copy-then-formula"
                        },
                        "$mode/${outerClip.name} ${result.diagnostics.entries}",
                    )
                }
            }
        }
    }

    @Test
    fun `picture saveLayer intersects an outer scissor or AA clip at its restore`() {
        requireWebGpu()

        listOf(BlendMode.SRC, BlendMode.DST_IN, BlendMode.MULTIPLY).forEach { mode ->
            val recorder = PictureRecorder()
            recorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
                saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
                restore()
            }
            val picture = recorder.finishRecordingAsPicture()

            listOf(
                OuterClip("scissor", Rect(12f, 12f, 24f, 24f), antiAlias = false, edge = null),
                OuterClip("alpha-mask", Rect(12.5f, 12.5f, 23.5f, 23.5f), antiAlias = true, edge = Point(12f, 16f)),
            ).forEach { outerClip ->
                val result = Surface(width = 32, height = 32).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                        clipRect(outerClip.rect, ClipOp.INTERSECT, outerClip.antiAlias)
                        drawPicture(picture)
                    }
                    render()
                }

                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 16,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = 1f),
                    tolerance = 2,
                )
                assertPixelNearAt(result.pixels, width = 32, x = 10, y = 16, expected = white, tolerance = 2)
                outerClip.edge?.let { edge ->
                    assertPixelNearAt(
                        result.pixels,
                        width = 32,
                        x = edge.x.toInt(),
                        y = edge.y.toInt(),
                        expected = publicLayerExpected(mode, coverage = .5f),
                        tolerance = 2,
                    )
                }
                if (mode == BlendMode.MULTIPLY) {
                    assertTrue(
                        result.diagnostics.entries.any { entry ->
                            entry.code.startsWith("route:destination-read:saveLayer:") &&
                                entry.reason == "gpu-copy-then-formula"
                        },
                        "$mode/${outerClip.name} ${result.diagnostics.entries}",
                    )
                }
            }
        }
    }

    @Test
    fun `picture playback keeps its layer clip and applies the host AA clip once`() {
        requireWebGpu()

        listOf(BlendMode.SRC, BlendMode.DST_IN).forEach { mode ->
            val recorder = PictureRecorder()
            recorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
                clipRect(Rect(8f, 8f, 24f, 24f), ClipOp.INTERSECT, antiAlias = false)
                saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
                restore()
            }
            val picture = recorder.finishRecordingAsPicture()
            val result = Surface(width = 32, height = 32).run {
                canvas {
                    drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                    clipRect(Rect(12.5f, 12.5f, 23.5f, 23.5f), ClipOp.INTERSECT, antiAlias = true)
                    picture.playback(this)
                }
                render()
            }

            assertPixelNearAt(
                result.pixels,
                width = 32,
                x = 16,
                y = 16,
                expected = publicLayerExpected(mode, coverage = 1f),
                tolerance = 2,
            )
            assertPixelNearAt(result.pixels, width = 32, x = 10, y = 16, expected = white, tolerance = 2)
            assertPixelNearAt(
                result.pixels,
                width = 32,
                x = 12,
                y = 16,
                expected = publicLayerExpected(mode, coverage = .5f),
                tolerance = 2,
            )
        }
    }

    @Test
    fun `picture deferred layer preserves mixed AA and hard clip edges`() {
        requireWebGpu()

        listOf(
            MixedClipFixture(
                name = "outer-AA-inner-hard",
                pictureClip = Rect(8f, 8f, 23.5f, 24f),
                pictureClipAntiAlias = false,
                hostClip = Rect(8.5f, 8f, 24f, 24f),
                hostClipAntiAlias = true,
            ),
            MixedClipFixture(
                name = "outer-hard-inner-AA",
                pictureClip = Rect(8.5f, 8f, 24f, 24f),
                pictureClipAntiAlias = true,
                hostClip = Rect(8f, 8f, 23.5f, 24f),
                hostClipAntiAlias = false,
            ),
        ).forEach { fixture ->
            listOf(BlendMode.SRC, BlendMode.DST_IN, BlendMode.MULTIPLY).forEach { mode ->
                val picture = deferredLayerPicture(fixture, mode)
                val result = Surface(width = 32, height = 32).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                        clipRect(fixture.hostClip, ClipOp.INTERSECT, fixture.hostClipAntiAlias)
                        picture.playback(this)
                    }
                    render()
                }

                // The AA edge is retained at x=8, while the hard edge at x=23.5 excludes x=23.
                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 8,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = .5f),
                    tolerance = 2,
                )
                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 22,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = 1f),
                    tolerance = 2,
                )
                assertPixelNearAt(result.pixels, width = 32, x = 23, y = 16, expected = white, tolerance = 2)
            }
        }
    }

    @Test
    fun `drawPicture preserves mixed AA and hard deferred layer clip edges`() {
        requireWebGpu()

        listOf(
            RecordedClipFixture(
                name = "outer-AA-inner-hard",
                clips = listOf(
                    RecordedClip(Rect(8.5f, 8f, 24f, 24f), antiAlias = true),
                    RecordedClip(Rect(8f, 8f, 23.5f, 24f), antiAlias = false),
                ),
            ),
            RecordedClipFixture(
                name = "outer-hard-inner-AA",
                clips = listOf(
                    RecordedClip(Rect(8f, 8f, 23.5f, 24f), antiAlias = false),
                    RecordedClip(Rect(8.5f, 8f, 24f, 24f), antiAlias = true),
                ),
            ),
        ).forEach { fixture ->
            listOf(BlendMode.SRC, BlendMode.DST_IN, BlendMode.MULTIPLY).forEach { mode ->
                val recorder = PictureRecorder()
                recorder.beginRecording(Rect(0f, 0f, 32f, 32f)).apply {
                    fixture.clips.forEach { clip ->
                        clipRect(clip.rect, ClipOp.INTERSECT, clip.antiAlias)
                    }
                    saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                    drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
                    restore()
                }
                val picture = recorder.finishRecordingAsPicture()
                val result = Surface(width = 32, height = 32).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                        drawPicture(picture)
                    }
                    render()
                }

                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 8,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = .5f),
                    tolerance = 2,
                )
                assertPixelNearAt(
                    result.pixels,
                    width = 32,
                    x = 22,
                    y = 16,
                    expected = publicLayerExpected(mode, coverage = 1f),
                    tolerance = 2,
                )
                assertPixelNearAt(result.pixels, width = 32, x = 23, y = 16, expected = white, tolerance = 2)
            }
        }
    }

    @Test
    fun `ordinary saveLayer composites SRC content over its opaque checkerboard parent`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer()
            drawRect(
                Rect(1f, 1f, 7f, 3f),
                Paint(
                    color = translucentRed.toColor(),
                    antiAlias = false,
                    blendMode = BlendMode.SRC,
                ),
            )
            restore()
        }

        val pixels = surface.render().pixels

        assertPixelNear(pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 6, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 2, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(pixels, x = 5, y = 2, expected = sourceOverSrgb(translucentRed, checkerGray), tolerance = 2)
    }

    @Test
    fun `nested ordinary saveLayers preserve parent isolation before final composition`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(color = green.toColor(), antiAlias = false),
            )
            saveLayer()
            drawRect(
                Rect(0f, 0f, 8f, 8f),
                Paint(
                    color = translucentRed.toColor(),
                    antiAlias = false,
                ),
            )
            saveLayer()
            drawRect(
                Rect(2f, 2f, 6f, 6f),
                Paint(
                    color = translucentBlue.toColor(),
                    antiAlias = false,
                    blendMode = BlendMode.DST_OUT,
                ),
            )
            restore()
            restore()
        }

        val pixels = surface.render().pixels
        val expectedOuterLayer = sourceOverSrgb(translucentRed, green)

        assertPixelNear(pixels, x = 1, y = 1, expected = expectedOuterLayer, tolerance = 2)
        assertPixelNear(pixels, x = 3, y = 3, expected = expectedOuterLayer, tolerance = 2)
    }

    @Test
    fun `ordinary saveLayer composes clipped DrawColor SRC before restore`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = checkerGray.toColor(), antiAlias = false))
            saveLayer()
            save()
            clipRect(Rect(1f, 1f, 7f, 7f))
            drawColor(translucentBackground.toColor(), BlendMode.SRC)
            restore()
            restore()
        }

        val result = surface.render()

        assertPixelNear(
            result.pixels,
            x = 2,
            y = 2,
            expected = sourceOverSrgb(translucentBackground, checkerGray),
            tolerance = 2,
        )
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
    }

    @Test
    fun `advanced blend composes after a preceding clipped DrawColor SRC`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = checkerGray.toColor(), antiAlias = false))
            saveLayer()
            save()
            clipRect(Rect(1f, 1f, 7f, 7f))
            drawColor(translucentBackground.toColor(), BlendMode.SRC)
            restore()
            drawRect(
                Rect(5f, 5f, 7f, 7f),
                Paint(color = translucentRed.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            restore()
        }

        val result = surface.render()

        assertPixelNear(
            result.pixels,
            x = 2,
            y = 2,
            expected = sourceOverSrgb(translucentBackground, checkerGray),
            tolerance = 2,
        )
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
    }

    @Test
    fun `advanced blend snapshot does not retain root content behind an active layer`() {
        requireWebGpu()

        val baseline = Surface(width = 8, height = 8)
        baseline.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentBlue.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            saveLayer()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
            restore()
        }

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentBlue.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            saveLayer()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
            drawRect(
                Rect(6f, 6f, 7f, 7f),
                Paint(color = translucentRed.toColor(), antiAlias = false, blendMode = BlendMode.SCREEN),
            )
            restore()
        }

        assertPixelNearPixels(surface.render().pixels, baseline.render().pixels, x = 2, y = 2, tolerance = 2)
    }

    @Test
    fun `DrawPicture containing saveLayer restores the nested layer`() {
        requireWebGpu()

        val recorder = PictureRecorder()
        val pictureCanvas = recorder.beginRecording(Rect(0f, 0f, 8f, 8f))
        pictureCanvas.drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
        pictureCanvas.saveLayer()
        pictureCanvas.drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentBlue.toColor(), antiAlias = false))
        pictureCanvas.restore()
        val picture = recorder.finishRecordingAsPicture()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            drawPicture(picture)
        }

        val result = surface.render()

        assertPixelNear(
            result.pixels,
            x = 2,
            y = 2,
            expected = sourceOverSrgb(translucentBlue, sourceOverSrgb(translucentRed, white)),
            tolerance = 2,
        )
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
    }

    @Test
    fun `translated DrawPicture with captured clip and bounded saveLayer refuses before encoding`() {
        requireWebGpu()

        val recorder = PictureRecorder()
        recorder.beginRecording(Rect(0f, 0f, 8f, 8f)).apply {
            saveLayer(Rect(0f, 0f, 4f, 4f))
            save()
            clipRect(Rect(1f, 1f, 4f, 4f), ClipOp.INTERSECT, antiAlias = false)
            drawRect(Rect(0f, 0f, 4f, 4f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
            restore()
        }
        val picture = recorder.finishRecordingAsPicture()

        val result = Surface(width = 8, height = 8).run {
            canvas {
                drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                translate(2f, 1f)
                drawPicture(picture)
            }
            render()
        }

        assertEquals(1, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { it.reason == "unsupported.picture.transformed_layer" },
            result.diagnostics.entries.toString(),
        )
        assertPixelNear(result.pixels, x = 2, y = 2, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 4, y = 3, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 6, y = 3, expected = white, tolerance = 0)
    }

    @Test
    fun `clipped DrawPicture composes a nested multiply through the source formula`() {
        requireWebGpu()

        val recorder = PictureRecorder()
        val pictureCanvas = recorder.beginRecording(Rect(0f, 0f, 8f, 8f))
        pictureCanvas.drawRect(
            Rect(0f, 0f, 8f, 8f),
            Paint(color = translucentRed.toColor(), antiAlias = false, blendMode = BlendMode.MULTIPLY),
        )
        val picture = recorder.finishRecordingAsPicture()

        val result = Surface(width = 8, height = 8).run {
            canvas {
                drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                save()
                clipRect(Rect(1f, 1f, 7f, 7f), ClipOp.INTERSECT, antiAlias = true)
                drawPicture(picture)
                restore()
            }
            render()
        }

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.entries.any { it.reason == "gpu-copy-then-formula" })
    }

    @Test
    fun `empty ordinary saveLayer leaves its parent untouched`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer()
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 2, y = 6, expected = checkerGray, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `bounded saveLayer clips child and composite to device bounds`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            saveLayer(Rect(2f, 2f, 6f, 6f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 1, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 3, y = 3, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `bounded saveLayer intersects every child raw draw scissor before encoding`() {
        val childDraw = GPUBackendRawUniformDraw(
            uniformBytes = ByteArray(16),
            scissorX = 1,
            scissorY = 1,
            scissorWidth = 6,
            scissorHeight = 6,
        )

        val clipped = childDraw.intersectLayerScissor(
            layerX = 2,
            layerY = 3,
            layerWidth = 3,
            layerHeight = 2,
        )

        requireNotNull(clipped)
        assertEquals(2, clipped.scissorX)
        assertEquals(3, clipped.scissorY)
        assertEquals(3, clipped.scissorWidth)
        assertEquals(2, clipped.scissorHeight)
    }

    @Test
    fun `bounded child target forwards the intersected scissor to its backend recorder`() {
        val recordedDraws = mutableListOf<GPUBackendRawUniformDraw>()
        val childTarget = LayerScissorOffscreenTarget(
            delegate = SpyOffscreenTarget(recordedDraws),
            sceneLayerBounds = { label -> if (label == "bounded-child") LayerBounds(2, 3, 3, 2) else null },
        )

        childTarget.encodeOffscreenTexture("bounded-child", clearColor = null) {
            drawFullscreenRawUniformPass(
                wgsl = "test",
                colorFormat = "rgba8unorm",
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = ByteArray(16),
                        scissorX = 1,
                        scissorY = 1,
                        scissorWidth = 6,
                        scissorHeight = 6,
                    ),
                ),
            )
        }

        val forwarded = recordedDraws.single()
        assertEquals(2, forwarded.scissorX)
        assertEquals(3, forwarded.scissorY)
        assertEquals(3, forwarded.scissorWidth)
        assertEquals(2, forwarded.scissorHeight)
    }

    @Test
    fun `bounded child target skips a fully out of bounds stencil test pass`() {
        val stencilPassCalls = mutableListOf<RecordedStencilPass>()
        val childTarget = LayerScissorOffscreenTarget(
            delegate = SpyOffscreenTarget(mutableListOf(), stencilPassCalls),
            sceneLayerBounds = { label -> if (label == "bounded-child") LayerBounds(2, 3, 3, 2) else null },
        )

        childTarget.encodeOffscreenTexture("bounded-child", clearColor = null) {
            drawFullscreenStencilPass(
                wgsl = "test",
                colorFormat = "rgba8unorm",
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
                draws = listOf(
                    GPUBackendRawUniformDraw(
                        uniformBytes = ByteArray(16),
                        scissorX = 0,
                        scissorY = 0,
                        scissorWidth = 1,
                        scissorHeight = 1,
                    ),
                ),
            )
        }

        assertTrue(stencilPassCalls.isEmpty())
    }

    @Test
    fun `bounded child target forwards filtered stencil test draws`() {
        val stencilPasses = mutableListOf<RecordedStencilPass>()
        val childTarget = LayerScissorOffscreenTarget(
            delegate = SpyOffscreenTarget(mutableListOf(), stencilPasses),
            sceneLayerBounds = { label -> if (label == "bounded-child") LayerBounds(2, 3, 3, 2) else null },
        )

        childTarget.encodeOffscreenTexture("bounded-child", clearColor = null) {
            drawFullscreenStencilPass(
                wgsl = "test",
                colorFormat = "rgba8unorm",
                stencilMode = GPUBackendStencilMode.Test,
                triangleData = null,
                draws = listOf(
                    GPUBackendRawUniformDraw(ByteArray(16), 1, 1, 6, 6),
                    GPUBackendRawUniformDraw(ByteArray(16), 0, 0, 1, 1),
                ),
            )
        }

        val forwarded = stencilPasses.single()
        assertEquals(GPUBackendStencilMode.Test, forwarded.mode)
        val draw = forwarded.draws.single()
        assertEquals(2, draw.scissorX)
        assertEquals(3, draw.scissorY)
        assertEquals(3, draw.scissorWidth)
        assertEquals(2, draw.scissorHeight)
    }

    @Test
    fun `bounded child target forwards empty stencil write pass`() {
        val stencilPasses = mutableListOf<RecordedStencilPass>()
        val childTarget = LayerScissorOffscreenTarget(
            delegate = SpyOffscreenTarget(mutableListOf(), stencilPasses),
            sceneLayerBounds = { label -> if (label == "bounded-child") LayerBounds(2, 3, 3, 2) else null },
        )

        childTarget.encodeOffscreenTexture("bounded-child", clearColor = null) {
            drawFullscreenStencilPass(
                wgsl = "test",
                colorFormat = "rgba8unorm",
                stencilMode = GPUBackendStencilMode.Write,
                triangleData = null,
                draws = listOf(GPUBackendRawUniformDraw(ByteArray(16), 0, 0, 1, 1)),
            )
        }

        val forwarded = stencilPasses.single()
        assertEquals(GPUBackendStencilMode.Write, forwarded.mode)
        assertTrue(forwarded.draws.isEmpty())
    }

    @Test
    fun `empty bounded saveLayer leaves parent untouched`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            saveLayer(Rect(20f, 20f, 21f, 21f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertCheckerboard(result.pixels)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `translated bounded saveLayer maps local bounds to device space`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(2f, 1f)
            saveLayer(Rect(0f, 0f, 4f, 4f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 2, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 4, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `scaled bounded saveLayer maps local bounds to device space`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            scale(2f, 2f)
            saveLayer(Rect(1f, 1f, 3f, 3f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 1, y = 3, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 3, y = 3, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 3, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `partially offscreen bounded saveLayer clips at the device edge`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(-2f, 1f)
            saveLayer(Rect(0f, 0f, 6f, 4f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 3, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 4, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    @Test
    fun `non finite mapped saveLayer bounds leave parent and report exact refusal`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawCheckerboardRoot()
            concat(Matrix33.makeAll(1f, 0f, Float.POSITIVE_INFINITY, 0f, 1f, 0f))
            saveLayer(Rect(0f, 0f, 4f, 4f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        val result = surface.render()

        assertCheckerboard(result.pixels)
        assertFatalReason(result, "unsupported.layer.bounds.non_finite")
    }

    @Test
    fun `nested bounded saveLayers intersect their transformed device bounds`() {
        requireWebGpu()

        val surface = Surface(width = 8, height = 8)
        surface.canvas {
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
            translate(1f, 0f)
            saveLayer(Rect(0f, 0f, 5f, 6f))
            translate(2f, 0f)
            saveLayer(Rect(0f, 0f, 5f, 6f))
            resetMatrix()
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
            restore()
        }

        val result = surface.render()

        assertPixelNear(result.pixels, x = 2, y = 2, expected = white, tolerance = 0)
        assertPixelNear(result.pixels, x = 5, y = 2, expected = sourceOverSrgb(translucentRed, white), tolerance = 2)
        assertPixelNear(result.pixels, x = 6, y = 2, expected = white, tolerance = 0)
        assertEquals(0, result.diagnostics.fatalCount)
    }

    private fun org.graphiks.kanvas.canvas.Canvas.drawCheckerboardRoot() {
        drawRect(Rect(0f, 0f, 4f, 4f), Paint(color = white.toColor(), antiAlias = false))
        drawRect(
            Rect(4f, 0f, 8f, 4f),
            Paint(color = checkerGray.toColor(), antiAlias = false),
        )
        drawRect(
            Rect(0f, 4f, 4f, 8f),
            Paint(color = checkerGray.toColor(), antiAlias = false),
        )
        drawRect(Rect(4f, 4f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
    }

    private fun assertCheckerboard(pixels: UByteArray) {
        assertPixelNear(pixels, x = 0, y = 0, expected = white, tolerance = 0)
        assertPixelNear(pixels, x = 6, y = 0, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 0, y = 6, expected = checkerGray, tolerance = 0)
        assertPixelNear(pixels, x = 6, y = 6, expected = white, tolerance = 0)
    }

    private fun assertFatalReason(result: org.graphiks.kanvas.surface.RenderResult, reason: String) {
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(reason, result.diagnostics.entries.single { it.level == DiagnosticLevel.FATAL }.reason)
    }

    private data class RecordedStencilPass(
        val mode: GPUBackendStencilMode,
        val draws: List<GPUBackendRawUniformDraw>,
    )

    private inner class SpyOffscreenTarget(
        private val recordedDraws: MutableList<GPUBackendRawUniformDraw>,
        private val stencilPassCalls: MutableList<RecordedStencilPass>? = null,
    ) : GPUBackendOffscreenTarget {
        override val target: GPUSurfaceTarget
            get() = error("target is not used by this spy")

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit): Nothing =
            error("primary target encoding is not expected")

        override fun readRgba(): Nothing = error("readback is not expected")

        override fun createOffscreenTexture(texture: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture): Nothing =
            error("texture allocation is not expected")

        override fun snapshotTargetToOffscreenTexture(textureLabel: String): Nothing =
            error("snapshot is not expected")

        override fun copyTargetToOffscreenTexture(destinationTextureLabel: String): Nothing =
            error("target copy is not expected")

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            block(rawDrawRecorder(recordedDraws, stencilPassCalls))
        }

        override fun createCoverageMask(request: GPUBackendCoverageMaskRequest): Nothing =
            error("coverage mask allocation is not expected")

        override fun encodeCoverageMask(
            mask: GPUBackendCoverageMask,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ): Nothing = error("coverage mask encoding is not expected")

        override fun releaseCoverageMask(mask: GPUBackendCoverageMask): Nothing =
            error("coverage mask release is not expected")

        override fun copyOffscreenTexture(sourceTextureLabel: String, destinationTextureLabel: String): Nothing =
            error("offscreen texture copy is not expected")

        override fun close() = Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun rawDrawRecorder(
        recordedDraws: MutableList<GPUBackendRawUniformDraw>,
        stencilPassCalls: MutableList<RecordedStencilPass>? = null,
    ): GPUBackendRenderRecorder =
        Proxy.newProxyInstance(
            GPUBackendRenderRecorder::class.java.classLoader,
            arrayOf(GPUBackendRenderRecorder::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMaxTextureDimension2D" -> Int.MAX_VALUE
                "drawFullscreenRawUniformPass" -> {
                    recordedDraws += args!![2] as List<GPUBackendRawUniformDraw>
                    null
                }
                "drawFullscreenStencilPass" -> {
                    stencilPassCalls?.add(
                        RecordedStencilPass(
                            mode = args!![2] as GPUBackendStencilMode,
                            draws = args[4] as List<GPUBackendRawUniformDraw>,
                        ),
                    )
                    null
                }
                else -> error("unexpected recorder call: ${method.name}")
            }
        } as GPUBackendRenderRecorder

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPixelNear(
        pixels: UByteArray,
        x: Int,
        y: Int,
        expected: Rgba,
        tolerance: Int,
    ) {
        val offset = (y * 8 + x) * 4
        val actual = IntArray(4) { channel -> pixels[offset + channel].toInt() and 0xff }
        actual.zip(expected.toIntArray()).forEachIndexed { channel, (actualByte, expectedByte) ->
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    private fun assertPixelNearAt(
        pixels: UByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: Rgba,
        tolerance: Int,
    ) {
        val offset = (y * width + x) * 4
        val actual = IntArray(4) { channel -> pixels[offset + channel].toInt() and 0xff }
        actual.zip(expected.toIntArray()).forEachIndexed { channel, (actualByte, expectedByte) ->
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    private fun publicLayerExpected(mode: BlendMode, coverage: Float): Rgba = when (mode) {
        BlendMode.SRC -> when (coverage) {
            1f -> Rgba(red = 188, green = 0, blue = 0, alpha = 128)
            .5f -> Rgba(red = 225, green = 188, blue = 188, alpha = 191)
            else -> error("unsupported coverage $coverage")
        }
        BlendMode.DST_IN -> when (coverage) {
            1f -> Rgba(red = 188, green = 188, blue = 188, alpha = 128)
            .5f -> Rgba(red = 225, green = 225, blue = 225, alpha = 191)
            else -> error("unsupported coverage $coverage")
        }
        BlendMode.MULTIPLY -> when (coverage) {
            1f -> Rgba(red = 255, green = 188, blue = 188, alpha = 255)
            .5f -> Rgba(red = 255, green = 225, blue = 225, alpha = 255)
            else -> error("unsupported coverage $coverage")
        }
        else -> error("fixture only defines SRC, DST_IN, and MULTIPLY")
    }

    private fun assertPixelNearPixels(
        actual: UByteArray,
        expected: UByteArray,
        x: Int,
        y: Int,
        tolerance: Int,
    ) {
        val offset = (y * 8 + x) * 4
        (0 until 4).forEach { channel ->
            val actualByte = actual[offset + channel].toInt() and 0xff
            val expectedByte = expected[offset + channel].toInt() and 0xff
            assertTrue(
                kotlin.math.abs(actualByte - expectedByte) <= tolerance,
                "channel=$channel at ($x,$y): expected=$expectedByte +/- $tolerance, actual=$actualByte",
            )
        }
    }

    /** Models one RGBA8_UNORM_SRGB source-over pass, including texture quantisation. */
    private fun sourceOverSrgb(source: Rgba, destination: Rgba): Rgba {
        val sourceAlpha = source.alpha / 255f
        val destinationAlpha = destination.alpha / 255f
        val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)

        fun composite(sourceChannel: Int, destinationChannel: Int): Int {
            if (outputAlpha == 0f) return 0
            val outputLinearPremul = srgbToLinear(sourceChannel) * sourceAlpha +
                srgbToLinear(destinationChannel) * destinationAlpha * (1f - sourceAlpha)
            return linearToSrgb(outputLinearPremul / outputAlpha)
        }

        return Rgba(
            red = composite(source.red, destination.red),
            green = composite(source.green, destination.green),
            blue = composite(source.blue, destination.blue),
            alpha = (outputAlpha * 255f + 0.5f).toInt(),
        )
    }

    private fun srgbToLinear(channel: Int): Float {
        val srgb = channel / 255f
        return if (srgb <= 0.04045f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(linear: Float): Int {
        val srgb = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (srgb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    private data class Rgba(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        fun toColor(): Color = Color.fromRGBA(red / 255f, green / 255f, blue / 255f, alpha / 255f)

        fun toIntArray(): IntArray = intArrayOf(red, green, blue, alpha)
    }

    private data class OuterClip(
        val name: String,
        val rect: Rect,
        val antiAlias: Boolean,
        val edge: Point?,
    )

    private data class MixedClipFixture(
        val name: String,
        val pictureClip: Rect,
        val pictureClipAntiAlias: Boolean,
        val hostClip: Rect,
        val hostClipAntiAlias: Boolean,
    )

    private data class RecordedClipFixture(
        val name: String,
        val clips: List<RecordedClip>,
    )

    private data class RecordedClip(
        val rect: Rect,
        val antiAlias: Boolean,
    )

    private fun deferredLayerPicture(fixture: MixedClipFixture, mode: BlendMode): Picture {
        val buffer = DeferredPictureBuffer()
        Canvas(buffer).apply {
            clipRect(fixture.pictureClip, ClipOp.INTERSECT, fixture.pictureClipAntiAlias)
            saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
            drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
            restore()
        }
        return Picture(Rect(0f, 0f, 32f, 32f), buffer.ops())
    }

    private class DeferredPictureBuffer : DisplayListBuffer {
        private val recordedOps = mutableListOf<DisplayOp>()

        override fun append(op: DisplayOp) {
            recordedOps += op
        }

        override fun ops(): List<DisplayOp> = recordedOps.toList()
    }

    private companion object {
        val white = Rgba(red = 255, green = 255, blue = 255, alpha = 255)
        val checkerGray = Rgba(red = 191, green = 191, blue = 191, alpha = 255)
        val green = Rgba(red = 0, green = 255, blue = 0, alpha = 255)
        val translucentRed = Rgba(red = 255, green = 0, blue = 0, alpha = 128)
        val translucentBlue = Rgba(red = 0, green = 0, blue = 255, alpha = 128)
        val translucentBackground = Rgba(red = 210, green = 184, blue = 135, alpha = 200)
    }
}
