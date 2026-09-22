@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.pipeline.ClipOp
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

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `unfiltered picture sibling remains admitted before filtered picture terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val unfilteredRecorder = PictureRecorder()
        unfilteredRecorder.beginRecording(bounds).drawRect(
            bounds,
            Paint(ColorARGB.White, antiAlias = false),
        )
        val filteredRecorder = PictureRecorder()
        filteredRecorder.beginRecording(bounds).drawRect(
            bounds,
            Paint(imageFilter = ImageFilter.Blur(1f, 1f)),
        )
        val surface = Surface(2, 2)
        surface.canvas {
            drawPicture(unfilteredRecorder.finishRecordingAsPicture())
            drawPicture(filteredRecorder.finishRecordingAsPicture())
        }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `one Picture keeps ordered unfiltered and nested filtered children before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val filtered = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }.finishRecordingAsPicture()
        val nested = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
                drawPicture(filtered)
            }
        }.finishRecordingAsPicture()
        val outer = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.of(255, 11, 22, 33), antiAlias = false))
                drawPicture(nested)
                drawRect(bounds, Paint(ColorARGB.of(255, 44, 55, 66), antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(outer) }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered Picture closes its recorded init-with-previous layer through one W6a scope`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.of(255, 11, 22, 33), antiAlias = false))
                saveLayer(SaveLayerRec(initWithPrevious = true))
                drawRect(bounds, Paint(ColorARGB.of(255, 44, 55, 66), antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f))) }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `inline repeated Picture destination blends remain planned before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val erase = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds,
                Paint(ColorARGB.of(128, 255, 0, 0), blendMode = BlendMode.DST_OUT, antiAlias = false))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
                drawPicture(erase)
                translate(1f, 0f)
                drawPicture(erase)
                drawPicture(erase, Paint(blendMode = BlendMode.MULTIPLY))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(parent, Paint(imageFilter = ImageFilter.Blur(1f, 1f))) }
        assertImageBlurMaterializes(surface)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `nested Picture noncommuting transforms retain explicit clip before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clipRect(RectF32.ofLTRB(0f, 0f, 1f, 2f), ClipOp.INTERSECT, false)
                drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                translate(1f, 0f)
                clipRect(bounds, ClipOp.INTERSECT, false)
                drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            scale(2f, 1f)
            translate(-1f, 0f)
            drawPicture(parent)
        }
        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `Picture overlap and transparent holes feed parent mask alpha before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                drawRect(bounds, Paint(ColorARGB.of(128, 255, 0, 0), antiAlias = false))
                drawRect(RectF32.ofLTRB(0f, 0f, 1f, 2f), Paint(ColorARGB.of(128, 0, 255, 0), antiAlias = false))
                drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint(blendMode = BlendMode.CLEAR, antiAlias = false))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture, Paint(ColorARGB.of(128, 255, 255, 255),
            imageFilter = ImageFilter.Blur(1f, 1f), maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f))) }
        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered Picture nested layer initializes from its immediate layer parent and recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                saveLayer()
                drawRect(bounds, Paint(ColorARGB.of(255, 11, 22, 33), antiAlias = false))
                saveLayer(SaveLayerRec(initWithPrevious = true))
                drawRect(bounds, Paint(ColorARGB.of(128, 44, 55, 66), antiAlias = false))
                restore()
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f))) }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered Picture closes its recorded mask layer through one W6a scope`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                saveLayer(paint = Paint(maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White))))
                drawRect(bounds, Paint(ColorARGB.of(255, 44, 55, 66), antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(1f, 1f))) }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `nested isolated Pictures retain each transform prefix once before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.of(255, 44, 55, 66), antiAlias = false))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                translate(0.25f, 0f)
                drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            translate(-0.25f, 0f)
            drawPicture(parent, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `nested Picture mask shader retains outer transform clip paint before terminal recovery`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val filtered = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(
                maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White)),
            ))
        }.finishRecordingAsPicture()
        val outer = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                save()
                translate(0.25f, 0f)
                clipRect(RectF32.ofLTRB(0f, 0f, 1.75f, 2f), ClipOp.INTERSECT, antiAlias = false)
                drawPicture(filtered, Paint(ColorARGB.of(255, 190, 200, 210), antiAlias = false))
                restore()
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(outer) }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `filtered Picture parent keeps its filtered child in the sealed command stream`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val filteredChild = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }.finishRecordingAsPicture()
        val parent = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawPicture(filteredChild, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(parent) }

        assertImageBlurMaterializes(surface)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `image root followed by mask occurrence remains one terminal filtered source`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(
                bounds,
                Paint(
                    imageFilter = ImageFilter.Blur(1f, 1f),
                    maskFilter = MaskFilter.Blur(BlurStyle.OUTER, 1f),
                ),
            )
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `layer mask shader reaches w6b terminal admission and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(paint = Paint(maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White))))
            drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

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

    /** Image-only W6b now returns one regular readback, even when the result is transparent. */
    private fun assertImageBlurMaterializes(surface: Surface) {
        assertEquals(16, surface.render().pixels.size)
    }

    private fun recoveryBlue2x2(): UByteArray = ubyteArrayOf(
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
        17u, 61u, 211u, 255u,
    )
}
