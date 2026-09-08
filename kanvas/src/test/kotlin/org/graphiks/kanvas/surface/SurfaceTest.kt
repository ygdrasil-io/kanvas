package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import java.util.Base64
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SurfaceTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test fun `Surface dimensions`() { val s = Surface(320, 240); assertEquals(320, s.width); assertEquals(240, s.height); assertEquals(PixelFormat.RGBA8, s.format) }
    @Test fun `Surface BGRA8`() { assertEquals(PixelFormat.BGRA8, Surface(100, 100, PixelFormat.BGRA8).format) }
    @Test
    fun `BGRA render and snapshots preserve exact channel order and color type`() {
        val surface = Surface(2, 1, PixelFormat.BGRA8)
        surface.canvas { drawColor(ColorARGB.Red) }

        val result = surface.render()
        assertEquals(PixelFormat.BGRA8, result.format)
        assertArrayEquals(
            byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1),
            result.pixels.toByteArray(),
        )

        val whole = result.toImage()
        assertEquals(ColorType.BGRA_8888, whole.colorType)
        assertEquals(AlphaType.PREMUL, whole.alphaType)
        assertArrayEquals(byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1), whole.pixels)

        val subset = surface.makeImageSnapshot(RectF32.ofLTRB(1f, 0f, 2f, 1f))
        assertNotNull(subset)
        assertEquals(ColorType.BGRA_8888, subset!!.colorType)
        assertEquals(AlphaType.PREMUL, subset.alphaType)
        assertArrayEquals(byteArrayOf(0, 0, -1, -1), subset.pixels)
    }

    @Test
    fun `raw GPU runtime disposal refreshes the W3 context before the next frame`() {
        val first = Surface(1, 1).also { surface ->
            surface.canvas { drawColor(ColorARGB.Red) }
        }.render()
        assertArrayEquals(byteArrayOf(-1, 0, 0, -1), first.pixels.toByteArray())

        GPUBackendRuntimeFactory.dispose()

        val second = Surface(1, 1).also { surface ->
            surface.canvas { drawColor(ColorARGB.Blue) }
        }.render()
        assertArrayEquals(byteArrayOf(0, 0, -1, -1), second.pixels.toByteArray())
    }

    @Test
    fun `W3 frame local budget exhaustion is terminal`() {
        val surface = Surface(
            width = 1,
            height = 1,
            config = RenderConfig(frameLocalBudgetBytes = 1L),
        )
        surface.canvas { drawColor(ColorARGB.Red) }

        val failure = assertThrows(IllegalStateException::class.java) { surface.render() }

        assertTrue(failure.message.orEmpty().startsWith("w3.budget.frame_local_exceeded:"))
    }

    @Test
    fun `W3 capability failure is terminal without returning the legacy pixel sentinel`() {
        val legacySentinel = Surface(
            width = 1,
            height = 1,
            config = RenderConfig(gpuColorFormat = GPUColorFormat.BGRA8_UNORM),
        ).also { surface ->
            surface.canvas { drawColor(ColorARGB.Red) }
        }.render()
        assertArrayEquals(byteArrayOf(0, 0, -1, -1), legacySentinel.pixels.toByteArray())

        val surface = Surface(width = 16_777_217, height = 1)
        surface.canvas { drawColor(ColorARGB.Red) }
        val before = surface.snapshotOps()

        val failure = assertThrows(IllegalStateException::class.java) { surface.render() }

        assertTrue(failure.message.orEmpty().startsWith("w3.capability.texture_dimension:"))
        assertEquals(before, surface.snapshotOps())
    }

    @Test
    fun `annotation is pixel inert and snapshots distinguish valid empty and out of bounds subsets`() {
        val surface = Surface(4, 3)
        surface.canvas {
            clear(ColorARGB.Transparent)
            drawColor(ColorARGB.fromRGBA(1f, 0f, 0f, .5f))
            drawAnnotation(RectF32.Empty, "evidence", "basic-primitives")
        }

        val render = surface.render()
        val whole = surface.makeImageSnapshot()
        val valid = surface.makeImageSnapshot(RectF32.ofLTRB(1f, 1f, 3f, 3f))

        assertArrayEquals(render.pixels.toByteArray(), whole.pixels)
        assertNotNull(valid)
        assertEquals(2, valid!!.width)
        assertEquals(2, valid.height)
        assertEquals(null, surface.makeImageSnapshot(RectF32.ofLTRB(1f, 1f, 1f, 2f)))
        assertEquals(null, surface.makeImageSnapshot(RectF32.ofLTRB(8f, 1f, 9f, 2f)))
        assertTrue(surface.snapshotOps().any { it is org.graphiks.kanvas.canvas.DisplayOp.Annotation })
    }
    @Test fun `Surface canvas DSL`() { val s = Surface(320, 240); s.canvas { drawRect(RectF32.ofLTRB(0f,0f,100f,80f), Paint.fill(ColorARGB.Red)) }; val r = s.render(); assertEquals(1, r.stats.opsDispatched) }

    @Test
    fun `scaled RRect clip remains fixed at its capture CTM after later Canvas CTM changes`() {
        val rrectSurface = Surface(32, 64)
        rrectSurface.canvas {
            translate(3f, 5f)
            scale(2f, 3f)
            clipRRect(RRectF32.of(RectF32.ofLTRB(4f, 6f, 12f, 16f), radius = 2f), antiAlias = false)
            resetMatrix()
            translate(100f, 200f)
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 64f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }
        rrectSurface.render()
        assertArrayEquals(
            byteArrayOf(-1, 0, 0, -1),
            requireNotNull(rrectSurface.makeImageSnapshot(RectF32.ofLTRB(15f, 30f, 16f, 31f))).pixels,
        )
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 0),
            requireNotNull(rrectSurface.makeImageSnapshot(RectF32.ofLTRB(5f, 5f, 6f, 6f))).pixels,
        )
    }

    @Test
    fun `rotated rect clip stays frozen through save restore after a CTM mutation`() {
        val surface = Surface(16, 16)
        surface.canvas {
            rotate(180f, px = 8f, py = 8f)
            clipRect(RectF32.ofLTRB(1f, 1f, 5f, 5f), antiAlias = false)
            save()
            resetMatrix()
            translate(100f, 200f)
            restore()
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }
        surface.render()
        assertArrayEquals(
            byteArrayOf(-1, 0, 0, -1),
            requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(12f, 12f, 13f, 13f))).pixels,
        )
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 0),
            requireNotNull(surface.makeImageSnapshot(RectF32.ofLTRB(1f, 1f, 2f, 2f))).pixels,
        )
    }

    @Test
    fun `affine rect clip remains at its capture CTM after a later Canvas CTM reset`() {
        val surface = Surface(32, 16)
        surface.canvas {
            setMatrix(Matrix3x3F32(sx = .75f, kx = .25f, tx = 1f, sy = .5f))
            clipRect(RectF32.ofLTRB(4f, 4f, 28f, 28f), antiAlias = false)
            resetMatrix()
            translate(100f, 200f)
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 16f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }
        val failure = assertThrows(IllegalStateException::class.java) { surface.render() }
        assertTrue(failure.message.orEmpty().startsWith("unsupported.clip.path_transform"), failure.message)
    }

    @Test
    fun `hard path clips render through the public Surface`() {
        val pathSurface = Surface(16, 16)
        pathSurface.canvas {
            clipPath(
                Path().apply {
                    moveTo(2f, 2f)
                    lineTo(14f, 2f)
                    lineTo(2f, 14f)
                    close()
                },
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Blue).copy(antiAlias = false))
        }
        pathSurface.render()
        assertArrayEquals(
            byteArrayOf(0, 0, -1, -1),
            requireNotNull(pathSurface.makeImageSnapshot(RectF32.ofLTRB(4f, 4f, 5f, 5f))).pixels,
        )
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 0),
            requireNotNull(pathSurface.makeImageSnapshot(RectF32.ofLTRB(13f, 13f, 14f, 14f))).pixels,
        )
    }

    @Test
    fun `typed perspective nonfinite and overflow clip captures fail closed after reset`() {
        val cases = listOf(
            "Perspective" to Matrix3x3F32(persp0 = .1f),
            "NonFinite" to Matrix3x3F32(sx = Float.NaN),
            "Singular" to Matrix3x3F32(sx = 0f),
            "NonFiniteProjection" to Matrix3x3F32(sx = Float.MAX_VALUE),
        )
        cases.forEach { (label, matrix) ->
            val surface = Surface(16, 16)
            surface.canvas {
                clear(ColorARGB.Transparent)
                setMatrix(matrix)
                clipRect(RectF32.ofLTRB(1f, 1f, 2f, 2f), antiAlias = false)
                resetMatrix()
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
            }

            val failure = assertThrows(IllegalStateException::class.java) { surface.render() }
            val expectedCode = if (label == "Perspective") {
                "unsupported_transform:Perspective"
            } else {
                "unsupported_clip_transform:$label"
            }
            assertTrue(failure.message.orEmpty().startsWith(expectedCode), "$label: ${failure.message}")
        }
    }

    @Test
    fun `picture replay retains singular and overflow rect clips for a typed terminal refusal`() {
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).apply {
            clipRect(RectF32.ofLTRB(1f, 1f, 7f, 7f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
        }
        val picture = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))

        listOf(
            "Singular" to Matrix3x3F32(sx = 0f),
            "NonFiniteProjection" to Matrix3x3F32(sx = Float.MAX_VALUE),
        ).forEach { (label, matrix) ->
            val surface = Surface(8, 8)
            surface.canvas {
                setMatrix(matrix)
                drawPicture(picture)
            }

            val failure = assertThrows(IllegalStateException::class.java) { surface.render() }
            assertTrue(
                failure.message.orEmpty().startsWith("unsupported_clip_transform:$label"),
                "$label: ${failure.message}",
            )
        }
    }

    @Test
    fun `historical schema v1 perspective clip cannot become replay authority under an outer transform`() {
        // Fixed KPIC v8 / SceneArchive schema-v1 fixture: one hard-edge red rect with a
        // perspective legacy clip. It was laid out with the pre-v2 boolean-plus-string
        // transform record and is consumed only through Picture's public decoder.
        val picture = requireNotNull(
            Picture.fromByteArray(
                Base64.getDecoder().decode(
                    "S1BJQwAAAAgAAAAAAAAAAEEAAABBAAAArRa6rgAAAAEAAAAIAAAACAAAAARzUkdCAAAABFNSR0IAAAAEU1JHQgAAAAEAAAABAAAAAQAAAAAAAAAAQQAAAEEAAAAAAAAC//8AAAAAAAlIQVJEX0VER0UAAAADAAAAAQAAAAE/gAAAP4AAAEDgAABA4AAAAAAACUlOVEVSU0VDVAABAAAAC3BlcnNwZWN0aXZlAAAAAQAAAAE/gAAAAAAAAAAAAAAAAAAAP4AAAAAAAAAAAAAAAAAAAD+AAAAAAAAEUkVDVAH//wAAAAAAAAhTUkNfT1ZFUgAAAAAAAAAABEZJTEw/gAAAAAAABEJVVFQAAAAFTUlURVJAgAAAAAAA",
                ),
            ),
        )
        val surface = Surface(8, 8)
        surface.canvas {
            concat(Matrix3x3F32.translation(1f, 0f))
            drawPicture(picture)
        }

        val failure = assertThrows(IllegalStateException::class.java) { surface.render() }
        assertTrue(failure.message.orEmpty().startsWith("unsupported_transform:Perspective"), failure.message)
    }

    @Test
    fun `W3 renders multiple solid rectangles in draw order`() {
        val surface = Surface(2, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint.fill(ColorARGB.Red))
            drawRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), Paint.fill(ColorARGB.Blue))
        }

        val result = surface.render()

        assertArrayEquals(
            byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1),
            result.pixels.toByteArray(),
        )
        assertEquals(2, result.stats.opsDispatched)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")))
        assertTrue(result.nativeEvidenceCounters.isNotEmpty())
    }

    @Test
    fun `W3 planner gap keeps the product legacy pixels`() {
        val surface = Surface(1, 1)
        surface.canvas {
            drawColor(ColorARGB.Blue)
            drawColor(ColorARGB.of(128, 255, 0, 0), BlendMode.SRC)
        }

        assertArrayEquals(byteArrayOf(-68, 0, 0, -128), surface.render().pixels.toByteArray())
    }
    @Test
    fun `readPixels copies correct region`() {
        val surface = Surface(100, 100)
        surface.canvas { drawColor(ColorARGB.Red) }
        val buffer = UByteArray(10 * 10 * 4)
        val ok = surface.readPixels(RectF32.ofLTRB(0f, 0f, 10f, 10f), buffer)
        assertTrue(ok)
        // Verify first pixel is red (RGBA = 255,0,0,255)
        assertEquals(255.toByte(), buffer[0].toByte()) // R
        assertEquals(0.toByte(), buffer[1].toByte())   // G
        assertEquals(0.toByte(), buffer[2].toByte())   // B
        assertEquals(255.toByte(), buffer[3].toByte()) // A
    }
    @Test
    fun `Image decode detects PNG magic bytes`() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val img = Image.decode(pngHeader)
        assertTrue(img.sourceId.contains("png"))
    }
    @Test
    fun `Image decode detects JPEG magic bytes`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val img = Image.decode(jpegHeader)
        assertTrue(img.sourceId.contains("jpeg"))
    }
    @Test
    fun `Image decode detects WebP magic bytes with WEBP fourCC`() {
        val webpHeader = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        val img = Image.decode(webpHeader)
        assertTrue(img.sourceId.contains("webp"))
    }
    @Test
    fun `Image decode rejects RIFF without WEBP fourCC`() {
        val riffHeader = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20) // AVI
        val img = Image.decode(riffHeader)
        assertTrue(img.sourceId.contains("unknown"), "RIFF without WEBP should not be detected as webp")
    }
    @Test
    fun `drawImage produces non-blank pixels`() {
        val pixels = ByteArray(10 * 10 * 4) { 255.toByte() }
        val img = Image.fromPixels(
            10,
            10,
            pixels,
            ColorType.RGBA_8888,
            "test-white",
            AlphaType.PREMUL,
        )
        val surface = Surface(100, 100)
        surface.canvas {
            drawImage(img, RectF32.ofLTRB(0f, 0f, 10f, 10f))
        }
        val result = surface.render()
        val nonZero = (0 until result.pixels.size step 4).any { idx ->
            result.pixels[idx].toInt() and 0xFF > 0
        }
        assertTrue(nonZero, "drawImage should produce visible pixels")
    }

}
