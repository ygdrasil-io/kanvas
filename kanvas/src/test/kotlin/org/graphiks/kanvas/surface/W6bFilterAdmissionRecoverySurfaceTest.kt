@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test

/** Public W6b ownership, terminal-admission, and same-surface recovery contract. */
class W6bFilterAdmissionRecoverySurfaceTest {
    @Test
    fun `w6c filter refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(bounds, Paint(imageFilter = ImageFilter.Offset(1f, 0f)))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.unsupported_family:")

        surface.discardRecordedOperations()
        surface.canvas {
            drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
        }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `direct no-filter w6a layer control remains admitted`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer()
            drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false))
            restore()
        }

        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered previous refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(SaveLayerRec(
                paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
                initWithPrevious = true,
            ))
            drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.filtered_previous:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `w6b non-rgba target refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val recoveryColor = ColorARGB.of(255, 17, 61, 211)
        val expectedRecovery = Surface(
            2,
            2,
            config = RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM),
        ).also { reference ->
            reference.canvas { drawRect(bounds, Paint(recoveryColor, antiAlias = false)) }
        }.render().pixels
        val surface = Surface(
            2,
            2,
            config = RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM),
        )
        surface.canvas {
            drawRect(bounds, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.unsupported_target_format:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(recoveryColor, antiAlias = false)) }
        assertContentEquals(expectedRecovery, surface.render().pixels)
    }

    @Test
    fun `nested picture filter refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val recorder = PictureRecorder()
        recorder.beginRecording(bounds).drawRect(
            bounds,
            Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
        )
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture) }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered capture limit refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val chainedBlur = ImageFilter.Blur(1f, 1f, input = ImageFilter.Blur(1f, 1f))
        val recorder = PictureRecorder()
        recorder.beginRecording(bounds).drawRect(bounds, Paint(imageFilter = chainedBlur))
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(
            2,
            2,
            captureLimits = SceneCaptureLimits(graphLimits = GraphLimits(maxNodes = 1)),
        )
        surface.canvas { drawPicture(picture) }

        assertTerminalWithoutReadbackMutation(surface, "graph-node-limit:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    private fun assertTerminalWithoutReadbackMutation(surface: Surface, diagnosticPrefix: String) {
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 2f), sentinel)
        }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
    }

    private fun recoveryBlue2x2(): UByteArray = ubyteArrayOf(
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
    )
}
