package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedSurfaceProductNativeSmokeTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `public Surface uniformly scaled triangle uses device cover and fills exactly 1128 pixels`() {
        val background = ColorARGB.of(alpha = 255, red = 13, green = 20, blue = 33)
        val fill = ColorARGB.of(alpha = 255, red = 31, green = 115, blue = 209)
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            drawColor(background)
            scale(1.5f, 1.5f)
            drawPath(
                Path().apply {
                    moveTo(8f, 8f)
                    lineTo(40f, 8f)
                    lineTo(8f, 40f)
                    close()
                },
                Paint.fill(fill).copy(antiAlias = false),
            )
        }
        val result = surface.render()
        val pixels = result.pixels.toByteArray()
        var opaqueFillPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            val offset = (y * 64 + x) * 4
            val actual = (0..3).map { pixels[offset + it].toInt() and 0xff }
            if (actual == listOf(31, 115, 209, 255)) opaqueFillPixels++
        }
        assertEquals(listOf(31, 115, 209, 255), pixelAt(pixels, 64, 20, 20))
        assertEquals(listOf(13, 20, 33, 255), pixelAt(pixels, 64, 10, 10))
        assertEquals(listOf(31, 115, 209, 255), pixelAt(pixels, 64, 50, 20))
        assertEquals(1128, opaqueFillPixels)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test
    fun `public Surface vertical round cap stroke executes the pixel exact native route`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(16f, 6f)
                    lineTo(16f, 26f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(92, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 4))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 26))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 13, 6))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface reverse vertical round cap stroke preserves direction invariant coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(16f, 26f)
                    lineTo(16f, 6f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(92, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 4))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 26))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 13, 6))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface reverse horizontal round cap stroke preserves direction invariant coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(26f, 16f)
                    lineTo(6f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(92, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 4, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 26, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 16, 13))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated reverse horizontal round cap stroke preserves exact coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(3f, 2f)
            drawPath(
                Path().apply {
                    moveTo(26f, 16f)
                    lineTo(6f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(92, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 7, 17))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 30, 17))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 19, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored reverse horizontal round cap stroke preserves exact clipped coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(24f, 15f, 27f, 18f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(26f, 16f)
                    lineTo(6f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(9, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 24, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 26, 17))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 27, 16))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated scissored reverse horizontal round cap stroke preserves exact clipped coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(28f, 17f, 30f, 20f), antiAlias = false)
            translate(3f, 2f)
            drawPath(
                Path().apply {
                    moveTo(26f, 16f)
                    lineTo(6f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(6, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 28, 17))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 29, 19))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 27, 18))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored reverse vertical round cap stroke preserves exact clipped coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(16f, 5f, 18f, 8f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(16f, 26f)
                    lineTo(16f, 6f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(6, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 5))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 17, 7))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 6))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated reverse vertical round cap stroke preserves exact coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(2f, 3f)
            drawPath(
                Path().apply {
                    moveTo(16f, 26f)
                    lineTo(16f, 6f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(92, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 17, 7))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 17, 30))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 19))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated scissored reverse vertical round cap stroke preserves exact clipped coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(17f, 28f, 20f, 30f), antiAlias = false)
            translate(2f, 3f)
            drawPath(
                Path().apply {
                    moveTo(16f, 26f)
                    lineTo(16f, 6f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(6, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 17, 28))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 19, 29))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 16, 29))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled round cap stroke preserves radius four coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 16f)
                    lineTo(24f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 16, 32))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 13, 32))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 11, 32))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated round cap stroke preserves translated coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(4f, 6f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 16f)
                    lineTo(24f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 20, 38))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 17, 38))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 15, 38))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled vertical round cap stroke preserves radius four coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(16f, 8f)
                    lineTo(16f, 24f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 32, 16))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 32, 13))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 32, 11))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated vertical round cap stroke preserves translated coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(4f, 6f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(16f, 8f)
                    lineTo(16f, 24f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 36, 22))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 36, 19))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 36, 17))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled reverse round cap stroke preserves direction invariant coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(24f, 16f)
                    lineTo(8f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 48, 32))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 45, 32))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 53, 32))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated reverse round cap stroke preserves translated coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(4f, 6f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(24f, 16f)
                    lineTo(8f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 52, 38))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 49, 38))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 57, 38))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled reverse vertical round cap stroke preserves direction invariant coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(16f, 24f)
                    lineTo(16f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 32, 48))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 32, 45))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 32, 53))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated reverse vertical round cap stroke preserves translated coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(4f, 6f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(16f, 24f)
                    lineTo(16f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(308, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 36, 54))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 36, 51))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 36, 59))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled scissored round cap stroke preserves clipped coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(12f, 30f, 20f, 38f), antiAlias = false)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 16f)
                    lineTo(24f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(45, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 12, 30))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 19, 33))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 12, 34))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated scissored round cap stroke preserves clipped coverage`() {
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(16f, 36f, 24f, 44f), antiAlias = false)
            translate(4f, 6f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 16f)
                    lineTo(24f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            if (pixelAt(pixels, 64, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(45, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 16, 36))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 64, 23, 39))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 64, 16, 40))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface quarter turn round cap stroke executes the pixel exact native route`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(20f, 4f)
            rotate(90f)
            drawPath(
                Path().apply {
                    moveTo(4f, 8f)
                    lineTo(16f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(60, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 12, 6))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 12, 20))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 9, 8))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface half turn round cap stroke executes the pixel exact native route`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(28f, 24f)
            rotate(180f)
            drawPath(
                Path().apply {
                    moveTo(8f, 4f)
                    lineTo(8f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(60, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 20, 6))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 20, 20))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 17, 12))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface negative quarter turn round cap stroke executes the pixel exact native route`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(12f, 24f)
            rotate(-90f)
            drawPath(
                Path().apply {
                    moveTo(4f, 8f)
                    lineTo(16f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(60, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 20, 6))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 20, 20))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 17, 12))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored vertical round cap stroke preserves exact clipped coverage`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(14f, 5f, 18f, 22f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(16f, 6f)
                    lineTo(16f, 26f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(68, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 14, 5))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 17, 21))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 13, 10))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface horizontal dashed butt stroke executes the bounded native dash route`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(4f, 16f)
                    lineTo(28f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 4, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 12, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored horizontal dashed butt stroke preserves dash gaps`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(8f, 14f, 20f, 19f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(4f, 16f)
                    lineTo(28f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(32, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 8, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 12, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 20, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated horizontal dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(3f, 2f)
            drawPath(
                Path().apply {
                    moveTo(4f, 16f)
                    lineTo(28f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 7, 17))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 17))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 19, 17))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface phase shifted dashed butt stroke preserves admitted phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(4f, 16f)
                    lineTo(28f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 4, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 8, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 12, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 20, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 24, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface vertical dashed butt stroke preserves dash gaps`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(16f, 4f)
                    lineTo(16f, 28f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 4))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 12))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 16))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 24))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface reverse horizontal dashed butt stroke preserves source phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(28f, 16f)
                    lineTo(4f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 27, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 19, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 7, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface reverse vertical dashed butt stroke preserves source phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(16f, 28f)
                    lineTo(16f, 4f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 27))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 19))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 7))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface phase shifted vertical dashed butt stroke preserves admitted phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(16f, 4f)
                    lineTo(16f, 28f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
                ),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 4))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 8))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 12))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 16))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 20))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 24))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored vertical dashed butt stroke preserves dash gaps`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(14f, 8f, 19f, 20f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(16f, 4f)
                    lineTo(16f, 28f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(32, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 8))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 12))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 16))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 15, 19))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 15, 20))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface translated vertical dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(3f, 2f)
            drawPath(
                Path().apply {
                    moveTo(16f, 4f)
                    lineTo(16f, 28f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 18, 6))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 18, 14))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 18, 18))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 18, 26))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled horizontal dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(4f, 8f)
                    lineTo(14f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(128, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 8, 12))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 20, 16))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 24, 16))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled vertical dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 4f)
                    lineTo(8f, 14f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(128, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 12, 8))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 23))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 16, 24))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated horizontal dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(2f, 4f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(4f, 4f)
                    lineTo(14f, 4f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(128, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 10, 8))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 25, 12))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 26, 12))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface uniformly scaled translated vertical dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(2f, 4f)
            scale(2f, 2f)
            drawPath(
                Path().apply {
                    moveTo(8f, 3f)
                    lineTo(8f, 13f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(128, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 18, 10))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 18, 25))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 18, 26))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface quarter turn horizontal dashed butt stroke preserves device phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(20f, 4f)
            rotate(90f)
            drawPath(
                Path().apply {
                    moveTo(4f, 8f)
                    lineTo(16f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(32, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 11, 8))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 11, 16))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface half turn vertical dashed butt stroke preserves source phase`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            translate(32f, 32f)
            rotate(180f)
            drawPath(
                Path().apply {
                    moveTo(8f, 4f)
                    lineTo(8f, 28f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(64, redPixels)
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 23, 27))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 23, 19))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 23, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 23, 7))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface scissored phase shifted dashed butt stroke preserves gaps`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(8f, 14f, 20f, 19f), antiAlias = false)
            drawPath(
                Path().apply {
                    moveTo(4f, 16f)
                    lineTo(28f, 16f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
                ),
            )
            restore()
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var redPixels = 0
        for (y in 0 until 32) for (x in 0 until 32) {
            if (pixelAt(pixels, 32, x, y) == listOf(255, 0, 0, 255)) redPixels++
        }
        assertEquals(32, redPixels)
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 8, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 12, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 16, 15))
        assertEquals(listOf(255, 0, 0, 255), pixelAt(pixels, 32, 19, 15))
        assertEquals(listOf(0, 0, 0, 0), pixelAt(pixels, 32, 20, 15))
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    private fun pixelAt(bytes: ByteArray, width: Int, x: Int, y: Int): List<Int> {
        val offset = (y * width + x) * 4
        return (0..3).map { bytes[offset + it].toInt() and 0xff }
    }

    @Test
    fun `public Surface identity solid drrect renders exact analytic hole pixels natively`() {
        val background = ColorARGB.of(alpha = 255, red = 13, green = 20, blue = 33)
        val fill = ColorARGB.of(alpha = 255, red = 31, green = 115, blue = 209)
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            drawColor(background)
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
                RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
                Paint.fill(fill).copy(antiAlias = false),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var bluePixels = 0
        var mismatches = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            val expectedFill = rrectContains(x + 0.5, y + 0.5, 8.0, 8.0, 56.0, 56.0, 8.0, 8.0) &&
                !rrectContains(x + 0.5, y + 0.5, 20.0, 20.0, 44.0, 44.0, 4.0, 4.0)
            val expected = if (expectedFill) listOf(31, 115, 209, 255) else listOf(13, 20, 33, 255)
            val offset = (y * 64 + x) * 4
            val actual = (0..3).map { pixels[offset + it].toInt() and 0xff }
            if (actual == listOf(31, 115, 209, 255)) bluePixels++
            if (actual != expected) mismatches++
        }

        assertEquals(1692, bluePixels)
        assertEquals(0, mismatches)
        assertEquals(2, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed > 0L)
        assertTrue(evidence.pipelineBinds > 0L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface analytic rrect and drrect retain isolated native slabs in one ordered frame`() {
        val background = ColorARGB.of(alpha = 255, red = 13, green = 20, blue = 33)
        val rrectFill = ColorARGB.of(alpha = 255, red = 211, green = 73, blue = 52)
        val drrectFill = ColorARGB.of(alpha = 255, red = 31, green = 115, blue = 209)
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            drawColor(background)
            drawRRect(
                RRectF32.of(RectF32.ofLTRB(4f, 4f, 60f, 60f), radius = 6f),
                Paint.fill(rrectFill).copy(antiAlias = false),
            )
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
                RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
                Paint.fill(drrectFill).copy(antiAlias = false),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single()).evidence
        val pixels = result.pixels.toByteArray()
        var mismatches = 0
        for (y in 0 until 64) for (x in 0 until 64) {
            val rrectContains = rrectContains(x + 0.5, y + 0.5, 4.0, 4.0, 60.0, 60.0, 6.0, 6.0)
            val drrectContains = rrectContains(x + 0.5, y + 0.5, 8.0, 8.0, 56.0, 56.0, 8.0, 8.0) &&
                !rrectContains(x + 0.5, y + 0.5, 20.0, 20.0, 44.0, 44.0, 4.0, 4.0)
            val expected = when {
                drrectContains -> listOf(31, 115, 209, 255)
                rrectContains -> listOf(211, 73, 52, 255)
                else -> listOf(13, 20, 33, 255)
            }
            val offset = (y * 64 + x) * 4
            if ((0..3).map { pixels[offset + it].toInt() and 0xff } != expected) mismatches++
        }

        assertEquals(0, mismatches)
        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertTrue(evidence.draws + evidence.drawIndexed >= 2L)
        assertTrue(evidence.pipelineBinds >= 2L)
        assertTrue(evidence.submits > 0L)
        assertTrue(evidence.readbackCopies > 0L)
    }

    @Test
    fun `public Surface linear gradient submits one real native frame`() {
        val start = ColorARGB.of(alpha = 160, red = 40, green = 120, blue = 208)
        val end = ColorARGB.of(alpha = 96, red = 224, green = 72, blue = 48)
        val surface = Surface(width = 64, height = 64, format = PixelFormat.RGBA8)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 64f, 64f),
                Paint(
                    shader = Shader.LinearGradient(
                        start = Point2F32(0f, 0f),
                        end = Point2F32(64f, 0f),
                        stops = listOf(GradientStop(0f, start), GradientStop(1f, end)),
                    ),
                ).copy(antiAlias = false),
            )
        }
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceNativeBackendPortFactory,
            ),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        val expected = linearGradientPremulSrgbOracle(width = 64, height = 64, start = start, end = end)
        val actualPixels = result.pixels.toByteArray()
        val maxChannelDelta = GPUPreparedImagePixelOracle.maxChannelDelta(actualPixels, expected)
        assertTrue(
            GPUPreparedImagePixelOracle.matchesWithinOneLsb(actualPixels, expected),
            "linear gradient 64x64 maxChannelDelta=$maxChannelDelta",
        )
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(1, decisions.size)
        val runtime = requireNotNull(GPUBackendRuntimeFactory.createOrNull())
        println(
            "task5.linear-native adapter=${runtime.adapterInfo} submits=${evidence.submits} " +
                "readbacks=${evidence.readbackCopies} pixels=${actualPixels.size / 4} " +
                "maxChannelDelta=$maxChannelDelta telemetry=${runtime.runtimeTelemetry}",
        )
    }

    @Test
    fun `internal prepared Surface route wraps the post first cycle pixel natively`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 32f, 32f),
                Paint(
                    shader = Shader.LinearGradient(
                        start = Point2F32(8.5f, 16.5f),
                        end = Point2F32(16.5f, 16.5f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                        tileMode = TileMode.REPEAT,
                    ),
                ).copy(antiAlias = false),
            )
        }

        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
        val result = GPUPreparedSurfaceProductEntry.render(
            operations = surface.snapshotOps(),
            width = surface.width,
            height = surface.height,
            format = surface.format,
            config = surface.config,
            executionPort = GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceNativeBackendPortFactory,
            ),
            trace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertPixel(result.pixels.toByteArray(), 32, 16, 16, listOf(255, 0, 0, 255))
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
    }

    @Test
    fun `Surface render wraps the post first cycle repeat gradient pixel`() {
        val surface = Surface(width = 32, height = 32, format = PixelFormat.RGBA8)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 32f, 32f),
                Paint(
                    shader = Shader.LinearGradient(
                        start = Point2F32(8.5f, 16.5f),
                        end = Point2F32(16.5f, 16.5f),
                        stops = listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                        tileMode = TileMode.REPEAT,
                    ),
                ).copy(antiAlias = false),
            )
        }

        val result = surface.render()

        // The centre of pixel x=16 is 16.5, so t_raw=1: repeat is red and clamp would be blue.
        assertPixel(result.pixels.toByteArray(), 32, 16, 16, listOf(255, 0, 0, 255))
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test
    fun `hardware sRGB store preserves semi transparent premultiplied readback`() {
        val color = ColorARGB.of(alpha = 160, red = 40, green = 120, blue = 208)
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(rect(RectF32.ofLTRB(0f, 0f, 4f, 4f), color)),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        )
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(31, 96, 169, 160))
    }

    @Test
    fun `mixed direct path direct frame uses the prepared product route with exact native evidence`() {
        // Prime the shared executor's cached session deterministically, independent of test
        // order: render one frame through the shared port, then dispose the runtime. The dispose
        // forces the next frame to open a fresh runtime with a new device generation, which the
        // executor observes as a generation boundary: it invalidates the primed session and
        // prepares a new one. The asserted frame below therefore always sees (targetCreations 1,
        // targetCloses 0), whether or not a previous test in the class already rendered.
        renderViaGpu(
            buffer = StaticDisplayListBuffer(listOf(rect(RectF32.ofLTRB(0f, 0f, 4f, 4f), ColorARGB.Red))),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
        )
        GPUBackendRuntimeFactory.dispose()

        val operations = listOf(
            rect(RectF32.ofLTRB(1f, 1f, 7f, 7f), ColorARGB.Red),
            DisplayOp.DrawPath(
                triangle(),
                Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            rect(RectF32.ofLTRB(22f, 18f, 30f, 26f), ColorARGB.Blue),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(operations),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertEquals(1, decisions.size)
        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertPixel(result.pixels.toByteArray(), 32, 3, 3, listOf(255, 0, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 14, 5, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 25, 21, listOf(0, 0, 255, 255))
        assertPixel(result.pixels.toByteArray(), 32, 31, 31, listOf(0, 0, 0, 0))

        assertEquals(1L, evidence.targetCreations)
        // The primed session from above is already closed by dispose at the device-generation
        // boundary, so this frame's evidence is invalidation+recreate, not a second close.
        assertEquals(0L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(1L, evidence.renderPasses)
        assertEquals(0L, evidence.draws)
        assertEquals(4L, evidence.drawIndexed)
        assertEquals(4L, evidence.pipelineBinds)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)

        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(Math.toIntExact(evidence.draws + evidence.drawIndexed), result.stats.drawCallCount)
        assertEquals(Math.toIntExact(evidence.pipelineBinds), result.stats.pipelineCount)
        assertEquals(false, result.stats.coverageMeasured)
    }

    @Test
    fun `image only product frame executes prepared without a synthetic core draw`() {
        val source = image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "product-image-only",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(
                    drawImage(
                        source,
                        RectF32.ofLTRB(1f, 1f, 3f, 3f),
                        SamplingOptions.NEAREST,
                    ),
                ),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val prepared = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        )
        assertEquals(
            GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
            prepared.evidence.routeMarker,
        )
        assertEquals(1L, prepared.evidence.submits)
        assertEquals(0, prepared.evidence.activeNativePayloads)
        assertEquals(1, result.stats.opsDispatched)
        assertPixel(result.pixels.toByteArray(), 4, 0, 0, listOf(0, 0, 0, 0))
        assertPixel(result.pixels.toByteArray(), 4, 1, 1, listOf(188, 0, 0, 128))
        assertPixel(result.pixels.toByteArray(), 4, 2, 1, listOf(0, 188, 0, 128))
        assertPixel(result.pixels.toByteArray(), 4, 1, 2, listOf(0, 0, 188, 128))
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(188, 188, 188, 128))
    }

    @Test
    fun `direct prepared image frame preserves native pixels ordering and ownership`() {
        val rgba = image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "native-rgba",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val bgra = image(
            width = GPUPreparedImageTestFixtures.bgraOpaque2x2Width,
            height = GPUPreparedImageTestFixtures.bgraOpaque2x2Height,
            colorType = GPUPreparedImageTestFixtures.bgraOpaque2x2ColorType,
            sourceId = "native-bgra",
            pixels = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes,
        )
        val alpha = image(
            width = GPUPreparedImageTestFixtures.a8_3x1Width,
            height = GPUPreparedImageTestFixtures.a8_3x1Height,
            colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
            sourceId = "native-a8",
            pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val linear = image(
            width = 2,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "native-linear",
            pixels = byteArrayOf(
                0, 0, 0, 255.toByte(),
                255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            ),
        )
        val operations = listOf(
            rect(RectF32.ofLTRB(0f, 0f, 2f, 2f), ColorARGB.Red),
            drawImage(rgba, RectF32.ofLTRB(3f, 0f, 5f, 2f), SamplingOptions.NEAREST),
            drawImage(bgra, RectF32.ofLTRB(6f, 0f, 8f, 2f), SamplingOptions.NEAREST),
            drawImage(
                alpha,
                RectF32.ofLTRB(9f, 0f, 12f, 1f),
                SamplingOptions.NEAREST,
                Paint.fill(ColorARGB.Red),
            ),
            drawImage(linear, RectF32.ofLTRB(13f, 0f, 14f, 1f), SamplingOptions.LINEAR),
            rect(RectF32.ofLTRB(15f, 0f, 17f, 2f), ColorARGB.Blue),
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 18,
                height = 4,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(execution, execution.toString())

        assertPixel(result.rgba, 18, 0, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 3, 0, listOf(188, 0, 0, 128))
        assertPixel(result.rgba, 18, 6, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 10, 0, listOf(188, 0, 0, 128))
        assertTrue(
            GPUPreparedImagePixelOracle.matchesWithinOneLsb(
                result.rgba.copyOfRange((13 * 4), (13 * 4) + 4),
                byteArrayOf(188.toByte(), 188.toByte(), 188.toByte(), 255.toByte()),
            ),
        )
        assertPixel(result.rgba, 18, 15, 0, listOf(0, 0, 255, 255))

        val evidence = result.evidence
        assertEquals(1L, evidence.targetCreations)
        assertEquals(0L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(3L, evidence.renderPasses)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)
        assertEquals(operations.size, result.visualOperationCount)
    }

    @Test
    fun `direct prepared nine and mixed lattice preserve pixels affine placement and route order`() {
        val gridImage = image(
            width = GPUPreparedImageTestFixtures.imageNine6x6Width,
            height = GPUPreparedImageTestFixtures.imageNine6x6Height,
            colorType = GPUPreparedImageTestFixtures.imageNine6x6ColorType,
            sourceId = "native-grid",
            pixels = GPUPreparedImageTestFixtures.imageNine6x6Bytes,
        )
        val operations = listOf(
            DisplayOp.DrawImageNine(
                image = gridImage,
                center = RectF32.ofLTRB(2f, 2f, 4f, 4f),
                dst = RectF32.ofLTRB(0f, 0f, 18f, 18f),
                paint = null,
                transform = Matrix3x3F32.translation(1f, 1f),
                clip = ClipStack.WideOpen,
            ),
            DisplayOp.DrawImageLattice(
                image = gridImage,
                lattice = Lattice(
                    xDivs = listOf(2, 4),
                    yDivs = emptyList(),
                    rects = listOf(
                        RectF32.ofLTRB(20f, 0f, 28f, 6f),
                        RectF32.ofLTRB(24f, 0f, 32f, 6f),
                        RectF32.ofLTRB(32f, 0f, 38f, 6f),
                    ),
                    colors = listOf(ColorARGB.Transparent, ColorARGB.Green, ColorARGB.Transparent),
                    flags = listOf(
                        LatticeFlags.DEFAULT,
                        LatticeFlags.FIXED_COLOR,
                        LatticeFlags.TRANSPARENT,
                    ),
                ),
                dst = RectF32.ofLTRB(20f, 0f, 38f, 6f),
                paint = Paint.fill(ColorARGB.White).copy(antiAlias = false),
                transform = Matrix3x3F32.translation(0f, 10f),
                clip = ClipStack.WideOpen,
                sampling = SamplingOptions.NEAREST,
            ),
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val execution = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 40,
                height = 20,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )

        assertPixel(result.rgba, 40, 2, 2, listOf(255, 255, 255, 255))
        assertPixel(result.rgba, 40, 10, 2, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 40, 2, 10, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 40, 10, 10, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 40, 18, 18, listOf(255, 255, 255, 255))
        assertPixel(result.rgba, 40, 21, 11, listOf(255, 255, 255, 255))
        // Both emitted lattice cells cover this pixel; green proves fixed cell 1 ran after sampled cell 0.
        assertPixel(
            result.rgba,
            40,
            25,
            11,
            listOf(0, 255, 0, 255),
        )
        assertPixel(result.rgba, 40, 30, 11, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 40, 37, 11, listOf(0, 0, 0, 0))
        assertEquals(11, result.visualOperationCount)
        assertEquals(1L, result.evidence.targetCreations)
        assertEquals(0L, result.evidence.targetCloses)
        assertEquals(1L, result.evidence.submits)
        assertEquals(1L, result.evidence.readbackCopies)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct affine atlas executes exact integral scissor through the product gate`() {
        val atlas = image(
            width = GPUPreparedImageTestFixtures.atlas4x4Width,
            height = GPUPreparedImageTestFixtures.atlas4x4Height,
            colorType = GPUPreparedImageTestFixtures.atlas4x4ColorType,
            sourceId = "native-atlas",
            pixels = GPUPreparedImageTestFixtures.atlas4x4Bytes,
        )
        val operations = listOf(
            rect(RectF32.ofLTRB(12f, 12f, 16f, 16f), ColorARGB.Green),
            DisplayOp.DrawAtlas(
                atlas = atlas,
                transforms = listOf(
                    Matrix3x3F32.translation(2f, 2f),
                    Matrix3x3F32.translation(8f, 2f),
                ),
                texRects = listOf(
                    RectF32.ofLTRB(0f, 0f, 2f, 2f),
                    RectF32.ofLTRB(2f, 0f, 4f, 2f),
                ),
                colors = listOf(ColorARGB.Blue, ColorARGB.Red),
                blendMode = BlendMode.SRC,
                paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.DeviceRect(
                    rect = RectF32.ofLTRB(3f, 2f, 11f, 4f),
                    antiAlias = false,
                ),
            ),
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = operations,
            target = GPUTargetFacts(16, 16, "rgba8unorm-srgb"),
            config = RenderConfig.DEFAULT,
            capabilities = GPUProductFlagConfig().buildCapabilities(),
        )
        assertEquals(null, inventory.preparedRefusal)
        assertTrue(inventory.visualCommands.any { it.normalized.source.operation == "drawAtlas" })
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
        )
        assertEquals(operations, candidate.operations)

        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceNativeBackendPortFactory,
        ).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 16,
                height = 16,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )

        assertPixel(result.rgba, 16, 2, 3, listOf(0, 0, 0, 0))
        assertPixel(result.rgba, 16, 3, 3, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 16, 10, 3, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 16, 11, 3, listOf(0, 0, 0, 0))
        assertPixel(result.rgba, 16, 13, 13, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct A8 atlas applies nontrivial paint tint and alpha once in all source modes`() {
        val atlas = image(
            width = GPUPreparedImageTestFixtures.a8_3x1Width,
            height = GPUPreparedImageTestFixtures.a8_3x1Height,
            colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
            sourceId = "native-a8-atlas-modes",
            pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val modes = listOf(
            BlendMode.SRC,
            BlendMode.DST,
            BlendMode.SRC_OVER,
            BlendMode.PLUS,
            BlendMode.MODULATE,
        )
        val operations = buildList {
            add(rect(RectF32.ofLTRB(6f, 10f, 8f, 12f), ColorARGB.Green))
            modes.forEachIndexed { index, mode ->
                add(
                    DisplayOp.DrawAtlas(
                        atlas = atlas,
                        transforms = listOf(Matrix3x3F32.translation(0f, (index * 2).toFloat())),
                        texRects = listOf(RectF32.ofLTRB(0f, 0f, 3f, 1f)),
                        colors = listOf(ColorARGB.of(160, 192, 96, 32)),
                        blendMode = mode,
                        paint = Paint.fill(ColorARGB.of(192, 128, 64, 160)),
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                )
            }
        }
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val execution = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceNativeBackendPortFactory,
        ).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 8,
                height = 12,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )
        val halfCoverage = listOf(
            listOf(84, 41, 37, 60),
            listOf(121, 87, 134, 96),
            listOf(111, 68, 92, 96),
            listOf(121, 87, 134, 96),
            listOf(84, 41, 37, 60),
        )
        val fullCoverage = listOf(
            listOf(117, 60, 54, 120),
            listOf(165, 120, 183, 192),
            listOf(153, 95, 127, 192),
            listOf(165, 120, 183, 192),
            listOf(117, 60, 54, 120),
        )

        modes.indices.forEach { index ->
            val y = index * 2
            assertPixel(result.rgba, 8, 0, y, listOf(0, 0, 0, 0))
            assertPixelWithinOne(result.rgba, 8, 1, y, halfCoverage[index])
            assertPixelWithinOne(result.rgba, 8, 2, y, fullCoverage[index])
        }
        assertPixel(result.rgba, 8, 7, 11, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct RGBA atlas ignores paint RGB and applies paint alpha once in all source modes`() {
        val atlas = image(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "native-rgba-atlas-modes",
            pixels = byteArrayOf(96, 48, 24, 160.toByte()),
        )
        val modes = listOf(
            BlendMode.SRC,
            BlendMode.DST,
            BlendMode.SRC_OVER,
            BlendMode.PLUS,
            BlendMode.MODULATE,
        )
        val paints = listOf(
            Paint.fill(ColorARGB.of(192, 128, 64, 160)),
            Paint.fill(ColorARGB.of(192, 32, 224, 96)),
        )
        val operations = buildList {
            add(rect(RectF32.ofLTRB(2f, 10f, 4f, 12f), ColorARGB.Green))
            modes.forEachIndexed { index, mode ->
                paints.forEachIndexed { paintIndex, paint ->
                    add(
                        DisplayOp.DrawAtlas(
                            atlas = atlas,
                            transforms = listOf(
                                Matrix3x3F32.translation(
                                    paintIndex.toFloat(),
                                    (index * 2).toFloat(),
                                ),
                            ),
                            texRects = listOf(RectF32.ofLTRB(0f, 0f, 1f, 1f)),
                            colors = listOf(ColorARGB.of(176, 80, 160, 48)),
                            blendMode = mode,
                            paint = paint,
                            transform = Matrix3x3F32.Identity,
                            clip = ClipStack.WideOpen,
                        ),
                    )
                }
            }
        }
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceNativeBackendPortFactory,
            ).execute(
                GPUPreparedSurfaceExecutionRequest(
                    candidate = GPUPreparedSurfaceEligibility.Candidate(
                        operations = operations,
                        config = RenderConfig.DEFAULT,
                        color = color,
                    ),
                    width = 4,
                    height = 12,
                ),
            ),
        )
        val expected = listOf(
            listOf(112, 155, 88, 133),
            listOf(108, 52, 24, 120),
            listOf(126, 157, 89, 170),
            listOf(152, 162, 92, 192),
            listOf(51, 33, 4, 83),
        )

        modes.indices.forEach { index ->
            assertPixelWithinOne(result.rgba, 4, 0, index * 2, expected[index])
            assertPixelWithinOne(result.rgba, 4, 1, index * 2, expected[index])
        }
        assertPixel(result.rgba, 4, 3, 11, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `heterogeneous Core Image Text Vertices Core frame uses one prepared submission and one readback`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 14 heterogeneous COLRv0",
        )
        val image = Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = ColorType.RGBA_8888,
            sourceId = "task14-heterogeneous-image",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            rect(RectF32.ofLTRB(0f, 0f, 4f, 4f), ColorARGB.Red),
            drawImage(
                image,
                RectF32.ofLTRB(48f, 0f, 50f, 2f),
                SamplingOptions.NEAREST,
            ),
            text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 12, 58, ColorARGB.White),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(
                        Point2F32(0f, 0f),
                        Point2F32(4f, 0f),
                        Point2F32(0f, 4f),
                    ),
                ),
                paint = Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
            rect(RectF32.ofLTRB(6f, 0f, 10f, 4f), ColorARGB.Blue),
        )
        var captured: GPUPreparedSurfaceFrameBuildResult.Ready? = null
        val executor = GPUPreparedSurfaceFrameExecutor(
            backendFactory = GPUPreparedSurfaceNativeBackendPortFactory,
            frameBuilder = { request ->
                GPUPreparedSurfaceFrameBuilder.build(request).also { result ->
                    if (result is GPUPreparedSurfaceFrameBuildResult.Ready) captured = result
                }
            },
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = executor.execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 160,
                height = 96,
                output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )
        val semanticOrder = checkNotNull(captured).taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull(GPUDrawPacket::semanticPayload)
            .map(GPUDrawSemanticPayload::canonicalType)
        assertEquals(
            listOf("CorePrimitive", "SampledImage", "TextA8", "Vertices", "CorePrimitive"),
            semanticOrder,
        )

        // Pixel order: the vertices triangle overlaps the first core rect and is painted
        // after it, so the overlap pixel must be the vertices green.
        assertPixel(result.rgba, 160, 1, 1, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 160, 2, 3, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 160, 7, 0, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 160, 48, 0, listOf(188, 0, 0, 128))
        val textDelta = deltaWithinOneLsb(
            result.rgba,
            160,
            16,
            58,
            GPUPreparedTextPixelOracle.a8SourceOver(
                GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
                paintAlpha = 1f,
                coverage = 255,
            ).bytes(),
        )
        assertTrue(textDelta <= 1, "heterogeneous text pixel delta=$textDelta")

        // The vertices overlap region must match the Task 13 CPU oracle (red rect under a
        // green triangle) within one LSB of the declared UNORM quantization.
        val expectedVertices = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixture.create(
                    positions = floatArrayOf(0f, 0f, 4f, 0f, 0f, 4f, 4f, 0f, 4f, 4f, 0f, 4f),
                    colorsRgba8 = byteArrayOf(
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                    ),
                    indices = intArrayOf(0, 1, 2, 1, 4, 2, 2, 4, 5, 0, 2, 5),
                    topology = GPUPreparedVerticesTopology.TRIANGLES,
                    pixelWidth = 4,
                    pixelHeight = 4,
                ),
                GPUPreparedVerticesTestFixture.create(
                    positions = floatArrayOf(0f, 0f, 4f, 0f, 0f, 4f),
                    colorsRgba8 = byteArrayOf(
                        0, 255.toByte(), 0, 255.toByte(),
                        0, 255.toByte(), 0, 255.toByte(),
                        0, 255.toByte(), 0, 255.toByte(),
                    ),
                    topology = GPUPreparedVerticesTopology.TRIANGLES,
                    pixelWidth = 4,
                    pixelHeight = 4,
                ),
            ),
        )
        val actualRegion = ByteArray(4 * 4 * 4)
        for (regionY in 0 until 4) {
            for (regionX in 0 until 4) {
                val source = (regionY * 160 + regionX) * 4
                val target = (regionY * 4 + regionX) * 4
                (0 until 4).forEach { channel ->
                    actualRegion[target + channel] = result.rgba[source + channel]
                }
            }
        }
        val verticesDelta = GPUPreparedVerticesCpuOracle.comparePixels(
            actualRegion,
            expectedVertices,
        )
        assertTrue(
            verticesDelta.matchesWithinOneLsb,
            "heterogeneous vertices region maxChannelDelta=${verticesDelta.maxChannelDelta} " +
                "differing=${verticesDelta.differingChannels}/${verticesDelta.comparedChannels}",
        )

        assertEquals(4, result.visualOperationCount)
        assertEquals(1L, result.evidence.encoders)
        assertEquals(1L, result.evidence.commandBuffers)
        assertEquals(1L, result.evidence.submits)
        assertEquals(1L, result.evidence.readbackCopies)
        assertEquals(0, result.evidence.activeNativePayloads)
        assertEquals(0, result.evidence.outputOwnedNativePayloads)
        assertEquals(0, result.evidence.quarantinedNativePayloads)
        assertEquals(result.evidence.retentionRegistrations, result.evidence.retentionCompletions)
        assertEquals(0L, result.evidence.retentionQuarantines)
        assertEquals(1, result.evidence.distinctRetentionTickets)
        assertEquals(5L, result.evidence.renderPasses)
        assertTrue(result.evidence.draws + result.evidence.drawIndexed >= 2L)
        assertTrue(
            captured.taskList.tasks
                .filterIsInstance<GPUTask.Upload>()
                .any { upload -> upload.destination.value.contains("prepared-vertices") },
            "vertices upload must be planned before its consuming render",
        )
        println(
            "task14.native heterogeneous prepared=true skipped=0 submits=" +
                "${result.evidence.submits} readbacks=${result.evidence.readbackCopies} " +
                "renderPasses=${result.evidence.renderPasses} " +
                "verticesDelta=${verticesDelta.maxChannelDelta} textDelta=$textDelta",
        )
    }

    @Test
    fun `vertices frame routes through the product gate as prepared with exact native pixels`() {
        val operations = listOf(
            rect(RectF32.ofLTRB(0f, 0f, 4f, 4f), ColorARGB.Red),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f)),
                ),
                paint = Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(operations),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        // The green triangle covers x+y<=4; pixels beyond it keep the red rect. Interior
        // pixels avoid the exact x+y=4 rasterization tie.
        assertPixel(result.pixels.toByteArray(), 4, 1, 1, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 2, 0, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 3, 2, listOf(255, 0, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 1, 3, listOf(255, 0, 0, 255))
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test
    fun `bgra8 surface renders prepared with native BGRA byte order and exact format`() {
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(rect(RectF32.ofLTRB(0f, 0f, 2f, 1f), ColorARGB.Red)),
            ),
            width = 2,
            height = 1,
            format = PixelFormat.BGRA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertEquals(1L, evidence.submits)
        assertEquals(PixelFormat.BGRA8, result.format)
        assertContentEquals(
            byteArrayOf(0, 0, 255.toByte(), 255.toByte(), 0, 0, 255.toByte(), 255.toByte()),
            result.pixels.toByteArray(),
        )
    }

    @Test
    fun `unregistered mesh program is terminal through the product gate`() {
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f)),
        )

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawMesh(
                        mesh = org.graphiks.kanvas.types.Mesh(
                            vertices = triangle,
                            program = org.graphiks.kanvas.paint.MeshProgram(
                                effect = org.graphiks.kanvas.pipeline.RuntimeEffect(
                                    id = "not.registered",
                                    module = org.graphiks.kanvas.pipeline.ShaderModule.fromSource(
                                        "fixture",
                                    ),
                                    uniformLayout = org.graphiks.kanvas.pipeline.UniformLayout(
                                        emptyList(),
                                    ),
                                    children = emptyList(),
                                ),
                                uniforms = org.graphiks.kanvas.pipeline.UniformBlock {},
                            ),
                            bounds = RectF32.ofLTRB(0f, 0f, 4f, 4f),
                        ),
                        paint = Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                        blendMode = null,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
                .MeshProgramUnregistered,
            failure.diagnostic.code.value,
        )
    }

    private fun rect(bounds: RectF32, color: ColorARGB) = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun text(
        typeface: FontTypeface,
        glyphId: Int,
        x: Int,
        baselineY: Int,
        color: ColorARGB,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(glyphId.toUShort()),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 48f,
                ),
            ),
            typeface = typeface,
            fontSize = 48f,
        ),
        x = x.toFloat(),
        y = baselineY.toFloat(),
        paint = Paint.fill(color),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun deltaWithinOneLsb(
        rgba: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: ByteArray,
    ): Int {
        val offset = (y * width + x) * 4
        return GPUPreparedImagePixelOracle.maxChannelDelta(
            rgba.copyOfRange(offset, offset + 4),
            expected,
        )
    }

    private fun drawImage(
        image: Image,
        dst: RectF32,
        sampling: SamplingOptions,
        paint: Paint = Paint.fill(ColorARGB.White),
    ) = DisplayOp.DrawImage(
        image = image,
        src = RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = paint.copy(shader = Shader.Image(image, sampling = sampling)),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun image(
        width: Int,
        height: Int,
        colorType: ColorType,
        sourceId: String,
        pixels: ByteArray,
    ) = Image(
        width = width,
        height = height,
        colorType = colorType,
        sourceId = sourceId,
        pixels = pixels,
        alphaType = AlphaType.PREMUL,
    )

    private fun triangle(): Path = Path().apply {
        moveTo(10f, 2f)
        lineTo(19f, 2f)
        lineTo(14f, 11f)
        close()
    }

    private fun assertPixel(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        assertEquals(expected, (0..3).map { bytes[offset + it].toInt() and 0xff }, "pixel ($x,$y)")
    }

    private fun assertPixelWithinOne(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        val actual = bytes.copyOfRange(offset, offset + 4)
        val expectedBytes = expected.map(Int::toByte).toByteArray()
        assertTrue(
            GPUPreparedImagePixelOracle.maxChannelDelta(actual, expectedBytes) <= 1,
            "pixel ($x,$y) expected=$expected actual=${actual.map { it.toInt() and 0xff }}",
        )
    }

    private fun rrectContains(
        x: Double,
        y: Double,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        radiusX: Double,
        radiusY: Double,
    ): Boolean {
        if (x < left || x >= right || y < top || y >= bottom) return false
        val cornerX = when {
            x < left + radiusX -> left + radiusX
            x >= right - radiusX -> right - radiusX
            else -> return true
        }
        val cornerY = when {
            y < top + radiusY -> top + radiusY
            y >= bottom - radiusY -> bottom - radiusY
            else -> return true
        }
        val dx = (x - cornerX) / radiusX
        val dy = (y - cornerY) / radiusY
        return dx * dx + dy * dy <= 1.0
    }

    private fun linearGradientPremulSrgbOracle(
        width: Int,
        height: Int,
        start: ColorARGB,
        end: ColorARGB,
    ): ByteArray = ByteArray(width * height * 4).also { bytes ->
        val startPremul = srgbPremulLinear(start)
        val endPremul = srgbPremulLinear(end)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val t = (x + 0.5).toDouble() / width.toDouble()
                val offset = (y * width + x) * 4
                bytes[offset] = quantize8(encodeSrgb(interpolate(startPremul.red, endPremul.red, t))).toByte()
                bytes[offset + 1] = quantize8(encodeSrgb(interpolate(startPremul.green, endPremul.green, t))).toByte()
                bytes[offset + 2] = quantize8(encodeSrgb(interpolate(startPremul.blue, endPremul.blue, t))).toByte()
                bytes[offset + 3] = quantize8(interpolate(startPremul.alpha, endPremul.alpha, t)).toByte()
            }
        }
    }

    private fun srgbPremulLinear(color: ColorARGB): PremulLinearRgba {
        val alpha = color.alpha.toDouble() / 255.0
        return PremulLinearRgba(
            red = decodeSrgb(color.red.toDouble() / 255.0) * alpha,
            green = decodeSrgb(color.green.toDouble() / 255.0) * alpha,
            blue = decodeSrgb(color.blue.toDouble() / 255.0) * alpha,
            alpha = alpha,
        )
    }

    private fun decodeSrgb(encoded: Double): Double =
        if (encoded <= 0.04045) {
            encoded / 12.92
        } else {
            Math.pow((encoded + 0.055) / 1.055, 2.4)
        }

    private fun encodeSrgb(linear: Double): Double =
        if (linear <= 0.0031308) linear * 12.92 else 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055

    private fun interpolate(start: Double, end: Double, t: Double): Double = start + (end - start) * t

    private fun quantize8(value: Double): Int = (value.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()

    private data class PremulLinearRgba(
        val red: Double,
        val green: Double,
        val blue: Double,
        val alpha: Double,
    )

    private class StaticDisplayListBuffer(
        private val operations: List<DisplayOp>,
    ) : DisplayListBuffer {
        override fun append(op: DisplayOp) = error("static buffer")
        override fun ops(): List<DisplayOp> = operations
    }
}
