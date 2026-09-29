@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.geometry.Path
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public W7 contract for an AA colour source that occurs at the W6 root target. */
class W7RootAaSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `root aa preserves sibling chronology`() {
        val red = ColorARGB.of(255, 239, 51, 73)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val green = ColorARGB.of(255, 45, 179, 97)
        val triangle = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        val layer = RectF32.ofLTRB(2f, 2f, 3f, 3f)

        fun record(rootFirst: Boolean): Surface = Surface(7, 7).also { surface ->
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(red, antiAlias = false))
                if (rootFirst) drawPath(triangle, Paint(blue, antiAlias = true))
                saveLayer()
                drawRect(layer, Paint(green, antiAlias = false))
                restore()
                if (!rootFirst) drawPath(triangle, Paint(blue, antiAlias = true))
            }
        }

        val rootThenLayerSurface = record(rootFirst = true)
        val rootThenLayer = rootThenLayerSurface.render()
        assertPixel(rootThenLayer.pixels, 7, 2, 2, 45, 179, 97, 255)
        assertPixel(rootThenLayer.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(rootThenLayer.pixels, 7, 0, 0, 239, 51, 73, 255)
        assertPixel(rootThenLayer.pixels, 7, 5, 5, 239, 51, 73, 255)
        val secondRenderSurface = rootThenLayerSurface.render()
        assertContentEquals(rootThenLayer.pixels, secondRenderSurface.pixels)

        val layerThenRootSurface = record(rootFirst = false)
        val layerThenRoot = layerThenRootSurface.render()
        assertPixel(layerThenRoot.pixels, 7, 2, 2, 17, 61, 211, 255)
        assertContentEquals(layerThenRoot.pixels, layerThenRootSurface.render().pixels)
        assertNative(rootThenLayer)
        assertNative(layerThenRoot)
    }

    @Test
    fun `root aa alpha is composed once`() {
        val black = ColorARGB.Black
        val halfWhite = ColorARGB.of(128, 255, 255, 255)
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val path = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f)) }
        fun render(pathDraw: org.graphiks.kanvas.canvas.Canvas.() -> Unit) = Surface(7, 7).also { surface ->
            surface.canvas {
                drawRect(bounds, Paint(black, antiAlias = false))
                saveLayer(); restore()
                pathDraw()
            }
        }.render()

        val direct = render { drawPath(path, Paint(halfWhite, antiAlias = true)) }
        assertPixel(direct.pixels, 7, 3, 3, 188, 188, 188, 255)
        assertPixel(direct.pixels, 7, 0, 0, 0, 0, 0, 255)

        assertNative(direct)
    }

    @Test
    fun `Picture playback inside layer remains unsupported transactionally`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val halfWhite = ColorARGB.of(128, 255, 255, 255)
        val path = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f)) }
        val picture = PictureRecorder().also { recorder ->
            // No explicit clip is recorded through the public API.
            recorder.beginRecording(bounds).drawPath(path, Paint(halfWhite, antiAlias = true))
        }.finishRecordingAsPicture()
        val surface = Surface(7, 7).also { target ->
            target.canvas {
                drawRect(bounds, Paint(ColorARGB.Black, antiAlias = false))
                saveLayer()
                drawPicture(picture)
                restore()
            }
        }
        assertTerminalAndRecovers(surface, bounds, "w6a.layer.unsupported_child")
    }

    @Test
    fun `root aa transform clip and layer origin remain distinct`() {
        val blue = ColorARGB.of(255, 17, 61, 211)
        val green = ColorARGB.of(255, 45, 179, 97)
        val surface = Surface(9, 8)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(3f, 2f, 5f, 4f), antiAlias = false)
            translate(2f, 1f)
            drawPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)) }, Paint(blue, antiAlias = true))
            restore()
            saveLayer(RectF32.ofLTRB(5f, 4f, 9f, 8f))
            drawPath(triangle(5f, 4f, 9f, 4f, 5f, 8f), Paint(green, antiAlias = true))
            restore()
        }

        val result = surface.render()
        assertPixel(result.pixels, 9, 3, 2, 17, 61, 211, 255)
        assertPixel(result.pixels, 9, 2, 2, 0, 0, 0, 0)
        assertPixel(result.pixels, 9, 5, 2, 0, 0, 0, 0)
        assertPixel(result.pixels, 9, 5, 4, 45, 179, 97, 255)
        assertNative(result)
    }

    @Test
    fun `root aa stencil sources stay isolated`() {
        val green = ColorARGB.of(255, 45, 179, 97)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val yellow = ColorARGB.of(255, 255, 235, 59)
        val first = lPath(1f)
        val second = lPath(4f)
        val surface = Surface(7, 7)
        surface.canvas {
            drawPath(first, Paint(green, antiAlias = true))
            saveLayer()
            drawRect(RectF32.ofLTRB(0f, 6f, 1f, 7f), Paint(yellow, antiAlias = false))
            restore()
            drawPath(second, Paint(blue, antiAlias = true))
        }

        val firstRender = surface.render()
        assertPixel(firstRender.pixels, 7, 1, 1, 45, 179, 97, 255)
        assertPixel(firstRender.pixels, 7, 4, 1, 17, 61, 211, 255)
        assertPixel(firstRender.pixels, 7, 0, 6, 255, 235, 59, 255)
        assertPixel(firstRender.pixels, 7, 2, 4, 0, 0, 0, 0)
        assertPixel(firstRender.pixels, 7, 5, 4, 0, 0, 0, 0)
        assertPixel(firstRender.pixels, 7, 6, 6, 0, 0, 0, 0)
        assertContentEquals(firstRender.pixels, surface.render().pixels)
        assertNative(firstRender)
    }

    @Test
    fun `root aa exact budget refuses before publication and recovers`() {
        // root RGBA8 (196), full-root empty layer (196), AA4 colour (784), sampled resolve
        // (196), seven aligned readback rows (1792), W4d V/I/U floors (16384+4096+4096),
        // plus the W4d and solid material uniforms (16+16): B = 27772.
        val budgetB = listOf(196L, 196L, 784L, 196L, 1792L, 16_384L, 4_096L, 4_096L, 16L, 16L)
            .fold(0L, Math::addExact)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val rootTriangle = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        fun record(surface: Surface) = surface.canvas {
            drawPath(rootTriangle, Paint(blue, antiAlias = true))
            saveLayer(); restore()
        }

        val admitted = Surface(7, 7, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        record(admitted)
        val accepted = admitted.render()
        assertPixel(accepted.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(accepted.pixels, 7, 6, 6, 0, 0, 0, 0)
        assertNative(accepted)

        val refused = Surface(7, 7, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L))
        record(refused)
        assertTerminalAndRecovers(refused, bounds, "w6a.layer.frame_budget_exceeded")
    }

    @Test
    fun `unsupported root aa siblings refuse transactionally`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val root = triangle(1f, 1f, 5f, 1f, 1f, 5f)
        val fixtures = listOf(
            Paint(blue, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = true) to "w6a.layer.unsupported_spatial_filter",
            Paint(blue, imageFilter = ImageFilter.Blur(1f, 1f), antiAlias = true) to "w6a.layer.unsupported_spatial_filter",
            Paint(blue, blendMode = BlendMode.PLUS, antiAlias = true) to "w6a.layer.unsupported_child",
        )
        fixtures.forEach { (sibling, prefix) ->
            val surface = Surface(7, 7)
            surface.canvas {
                drawPath(root, Paint(blue, antiAlias = true))
                saveLayer()
                drawPath(root, sibling)
                restore()
            }
            assertTerminalAndRecovers(surface, bounds, prefix)
        }

        val w6bSurface = Surface(7, 7)
        w6bSurface.canvas {
            drawPath(root, Paint(blue, antiAlias = true))
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.Blur(1f, 1f), antiAlias = false)))
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(blue, antiAlias = false))
            restore()
        }
        assertTerminalAndRecovers(w6bSurface, bounds, "w6a.layer.unsupported_child")

        val hardControl = Surface(7, 7)
        hardControl.canvas {
            drawPath(root, Paint(blue, antiAlias = false))
            saveLayer()
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.of(255, 45, 179, 97), antiAlias = false))
            restore()
        }
        val hardControlResult = hardControl.render()
        assertPixel(hardControlResult.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(hardControlResult.pixels, 7, 2, 2, 45, 179, 97, 255)
        assertNative(hardControlResult)
    }

    private fun assertTerminalAndRecovers(surface: Surface, bounds: RectF32, prefix: String) {
        val sentinel = UByteArray(7 * 7 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("$prefix:") == true, failure.message)
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(bounds, Paint(ColorARGB.of(255, 17, 61, 211), antiAlias = false)) }
        val recovered = surface.render()
        assertPixel(recovered.pixels, 7, 3, 3, 17, 61, 211, 255)
        assertNative(recovered)
    }

    private fun triangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Path = Path().apply {
        moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); close()
    }

    private fun lPath(left: Float): Path = Path().apply {
        moveTo(left, 1f); lineTo(left + 2f, 1f); lineTo(left + 2f, 3f)
        lineTo(left + 1f, 3f); lineTo(left + 1f, 5f); lineTo(left, 5f); close()
    }

    private fun assertNative(result: RenderResult) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(ubyteArrayOf(red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte()),
            pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }
}
