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
    fun `finite huge blur refuses with stable bounds diagnostic and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            drawPicture(picture, Paint(imageFilter = ImageFilter.Blur(Float.MAX_VALUE, Float.MAX_VALUE)))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.invalid_bounds:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `finite huge shadow offset refuses with stable bounds diagnostic and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            drawPicture(picture, Paint(imageFilter = ImageFilter.DropShadow(
                dx = Float.MAX_VALUE,
                dy = 0f,
                sigmaX = 0f,
                sigmaY = 0f,
                color = ColorARGB.Black,
            )))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.invalid_bounds:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
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
    fun `Picture overlap and transparent holes feed parent mask alpha and surface recovers`() {
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
        val pixels = surface.render().pixels
        // The cleared upper-right source texel is still reached by both frozen blurs, but
        // retains less alpha than its opaque neighbours rather than becoming a white fill.
        assertTrue(pixels[7] < pixels[3] && pixels[7] < pixels[11])
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
    fun `filtered Picture materializes its recorded mask layer through one W6a scope`() {
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

        assertContentEquals(UByteArray(16), surface.render().pixels)

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
    fun `image root followed by mask occurrence materializes one frozen filtered source`() {
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

        val pixels = surface.render().pixels
        // OUTER coverage is transparent black with an alpha fringe at the clipped source edge.
        assertTrue(pixels.indices.filter { it.rem(4) != 3 }.all { pixels[it] == 0.toUByte() })
        assertTrue((3 until pixels.size step 4).all { pixels[it] > 0.toUByte() })

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `layer mask shader materializes and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val surface = Surface(2, 2)
        surface.canvas {
            saveLayer(paint = Paint(maskFilter = MaskFilter.Shader(Shader.SolidColor(ColorARGB.White))))
            drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

        assertContentEquals(opaqueWhite2x2(), surface.render().pixels)

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

    @Test
    fun `non axis aligned deferred Picture clip refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                rotate(30f)
                clipRect(bounds, ClipOp.INTERSECT, antiAlias = false)
                drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture) }

        assertTerminalWithoutReadbackMutation(surface, "w6a.layer.unsupported_child:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `antialiased deferred Picture clip refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clipRect(bounds, ClipOp.INTERSECT, antiAlias = true)
                drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture) }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `empty deferred Picture clip is a terminal no op`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).apply {
                clipRect(RectF32.ofLTRB(1f, 1f, 1f, 1f), ClipOp.INTERSECT, antiAlias = false)
                drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            }
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas { drawPicture(picture) }

        assertContentEquals(UByteArray(16), surface.render().pixels)
    }

    @Test
    fun `empty deferred Picture clip remains a terminal no op under a finite transform`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            rotate(30f)
            clipRect(RectF32.ofLTRB(1f, 1f, 1f, 1f), ClipOp.INTERSECT, antiAlias = false)
            drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }

        assertContentEquals(UByteArray(16), surface.render().pixels)
    }

    @Test
    fun `empty deferred Picture clip elides a filtered child under a finite singular transform and recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(
                ColorARGB.of(255, 221, 33, 17),
                imageFilter = ImageFilter.Blur(1f, 1f),
                antiAlias = false,
            ))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            save()
            scale(0f, 1f)
            clipRect(RectF32.ofLTRB(1f, 1f, 1f, 1f), ClipOp.INTERSECT, antiAlias = false)
            drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
            restore()
        }

        val readback = UByteArray(16) { 0x5au }
        surface.readPixels(bounds, readback)
        assertContentEquals(UByteArray(16), readback)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        assertContentEquals(recoveryBlue2x2(), surface.render().pixels)
    }

    @Test
    fun `fractional hard edge deferred Picture clip refuses terminally and same surface recovers`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val child = PictureRecorder().also { recorder ->
            recorder.beginRecording(bounds).drawRect(bounds, Paint(ColorARGB.White, antiAlias = false))
        }.finishRecordingAsPicture()
        val surface = Surface(2, 2)
        surface.canvas {
            clipRect(RectF32.ofLTRB(0.5f, 0f, 1.5f, 2f), ClipOp.INTERSECT, antiAlias = false)
            drawPicture(child, Paint(imageFilter = ImageFilter.Blur(1f, 1f)))
        }

        assertTerminalWithoutReadbackMutation(surface, "w6b.filter.native_execution_unimplemented:")

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

    private fun opaqueWhite2x2(): UByteArray = UByteArray(16) { 255u }
}
