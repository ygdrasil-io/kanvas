package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
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
            val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                Surface(width = 8, height = 8).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                        saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                        drawRect(Rect(2f, 2f, 6f, 6f), Paint(color = translucentBlue.toColor(), antiAlias = false))
                        restore()
                    }
                    render()
                }
            }
            assertEquals("unsupported.layer.bounds_unbounded", failure.diagnostic.code.value)
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
                OuterClip("alpha-mask", Rect(12.5f, 12.5f, 23.5f, 23.5f), antiAlias = true, edge = Point2F32(12f, 16f)),
            ).forEach { outerClip ->
                val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                    Surface(width = 32, height = 32).run {
                        canvas {
                            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                            clipRect(outerClip.rect, ClipOp.INTERSECT, outerClip.antiAlias)
                            saveLayer(paint = Paint(color = translucentRed.toColor(), blendMode = mode))
                            drawRect(Rect(6f, 6f, 26f, 26f), Paint.fill(Color.RED).copy(antiAlias = false))
                            restore()
                        }
                        render()
                    }
                }

                // Unbounded saveLayers are a documented prepared-route refusal: the
                // isolated-target planner cannot materialize a layer without device bounds.
                assertEquals("unsupported.layer.bounds_unbounded", failure.diagnostic.code.value)
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
                OuterClip("alpha-mask", Rect(12.5f, 12.5f, 23.5f, 23.5f), antiAlias = true, edge = Point2F32(12f, 16f)),
            ).forEach { outerClip ->
                val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                    Surface(width = 32, height = 32).run {
                        canvas {
                            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                            clipRect(outerClip.rect, ClipOp.INTERSECT, outerClip.antiAlias)
                            drawPicture(picture)
                        }
                        render()
                    }
                }

                // The fixture carries two documented prepared-route refusals (an unbounded
                // saveLayer and a clip at the layer boundary); either may fire first.
                assertTrue(
                    failure.diagnostic.code.value == "unsupported.layer.bounds_unbounded" ||
                        failure.diagnostic.code.value == "unsupported.composite.clip",
                    failure.diagnostic.toString(),
                )
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
            val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                Surface(width = 32, height = 32).run {
                    canvas {
                        drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                        clipRect(Rect(12.5f, 12.5f, 23.5f, 23.5f), ClipOp.INTERSECT, antiAlias = true)
                        picture.playback(this)
                    }
                    render()
                }
            }

            // Clips inside a layer scope are a documented prepared-route refusal.
            assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
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
                val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                    Surface(width = 32, height = 32).run {
                        canvas {
                            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                            clipRect(fixture.hostClip, ClipOp.INTERSECT, fixture.hostClipAntiAlias)
                            picture.playback(this)
                        }
                        render()
                    }
                }

                // Clips inside a layer scope are a documented prepared-route refusal.
                assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
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
                val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
                    Surface(width = 32, height = 32).run {
                        canvas {
                            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE).copy(antiAlias = false))
                            drawPicture(picture)
                        }
                        render()
                    }
                }

                // Clips inside a layer scope are a documented prepared-route refusal.
                assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
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

        // Unbounded saveLayers are a documented prepared-route refusal: the isolated-target
        // planner cannot materialize a layer without device bounds.
        assertFatalCode({ surface.render() }, "unsupported.layer.bounds_unbounded")
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

        // Nested saveLayers are a documented prepared-route refusal
        // (unsupported.prepared-surface.layer-nesting) until nesting materialization lands.
        assertFatalCode({ surface.render() }, "unsupported.prepared-surface.layer-nesting")
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

        // DrawColor children are a documented prepared-route refusal: the composite capture
        // admits only core geometry operations inside layer scopes.
        assertFatalCode({ surface.render() }, "unsupported.composite.operation")
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

        // DrawColor children are a documented prepared-route refusal: the composite capture
        // admits only core geometry operations inside layer scopes.
        assertFatalCode({ surface.render() }, "unsupported.composite.operation")
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

        // Unbounded saveLayers are a documented prepared-route refusal: the isolated-target
        // planner cannot materialize a layer without device bounds.
        assertFatalCode({ surface.render() }, "unsupported.layer.bounds_unbounded")
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

        // Unbounded saveLayers are a documented prepared-route refusal: the isolated-target
        // planner cannot materialize a layer without device bounds.
        assertFatalCode({ surface.render() }, "unsupported.layer.bounds_unbounded")
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

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 8, height = 8).run {
            canvas {
                drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                translate(2f, 1f)
                drawPicture(picture)
            }
            render()
        }
        }

        assertEquals(
            "unsupported.surface.prepared.mixed-composite-topology",
            failure.diagnostic.code.value,
        )
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

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            Surface(width = 8, height = 8).run {
            canvas {
                drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
                save()
                clipRect(Rect(1f, 1f, 7f, 7f), ClipOp.INTERSECT, antiAlias = true)
                drawPicture(picture)
                restore()
            }
            render()
        }
        }

        // Picture frames the composite route cannot cover are a documented prepared-route
        // refusal: the flat mapper cannot replay a DrawPicture.
        assertEquals("unsupported.surface.prepared.mixed-composite-topology", failure.diagnostic.code.value)
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

        // Unbounded saveLayers are a documented prepared-route refusal: the isolated-target
        // planner cannot materialize a layer without device bounds.
        assertFatalCode({ surface.render() }, "unsupported.layer.bounds_unbounded")
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
            concat(Matrix3x3F32.of(1f, 0f, Float.POSITIVE_INFINITY, 0f, 1f, 0f))
            saveLayer(Rect(0f, 0f, 4f, 4f))
            drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
            restore()
        }

        // The non-finite transform is refused by the composite capture at the operation
        // boundary (unsupported.composite.operation) before any encoding.
        assertFatalCode({ surface.render() }, "unsupported.composite.operation")
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

        // Nested saveLayers are a documented prepared-route refusal
        // (unsupported.prepared-surface.layer-nesting) until nesting materialization lands.
        assertFatalCode({ surface.render() }, "unsupported.prepared-surface.layer-nesting")
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

    private fun assertFatalCode(render: () -> Unit, code: String) {
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> { render() }
        assertEquals(code, failure.diagnostic.code.value, failure.diagnostic.toString())
    }

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
        val edge: Point2F32?,
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
