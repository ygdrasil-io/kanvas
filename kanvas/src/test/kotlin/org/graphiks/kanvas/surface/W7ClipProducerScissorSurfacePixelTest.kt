@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** Public native witness that a visible path clip owns its conservative producer scissor. */
class W7ClipProducerScissorSurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun negativeTriangleScissorRenders() {
        val triangle = Path().apply {
            moveTo(-4f, -4f)
            lineTo(12f, -4f)
            lineTo(-4f, 12f)
            close()
        }
        val surface = Surface(8, 8).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
            clipPath(triangle, antiAlias = false)
            drawRect(
                RectF32.ofLTRB(0f, 0f, 8f, 8f),
                Paint(ColorARGB.of(255, 0, 0, 255), antiAlias = false),
            )
        } }

        val first = surface.render()

        assertNativeResult(first)
        assertPixel(first.pixels, 1, 1, 0, 0, 255, 255)
        assertPixel(first.pixels, 6, 6, 255, 0, 0, 255)

        val second = surface.render()

        assertNativeResult(second)
        assertContentEquals(first.pixels, second.pixels, "second native frame must be byte-identical")
    }

    @Test
    fun positiveOverflowTriangleScissorRenders() {
        val triangle = Path().apply {
            moveTo(5f, 6f); lineTo(16f, 6f); lineTo(5f, 15f); close()
        }
        val result = clippedFullTarget(triangle).renderAndRepeat()
        assertPixel(result.pixels, 5, 6, 0, 0, 255, 255)
        assertPixel(result.pixels, 4, 6, 255, 0, 0, 255)
    }

    @Test
    fun initialOutsideCoverage() {
        listOf(false, true).forEach { rectangular -> listOf(false, true).forEach { inverse ->
            listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation ->
                val outside = Path().apply {
                    if (rectangular) addRect(RectF32.ofLTRB(10f, 10f, 14f, 14f))
                    else { moveTo(10f, 10f); lineTo(14f, 10f); lineTo(10f, 14f); close() }
                    if (inverse) fillType = FillType.INVERSE_WINDING
                }
                val result = clippedFullTarget(outside, operation).renderAndRepeat()
                val blue = (operation == ClipOp.DIFFERENCE) != inverse
                for (y in 0 until 8) for (x in 0 until 8) {
                    assertPixel(result.pixels, x, y, if (blue) 0 else 255, 0, if (blue) 255 else 0, 255)
                }
            }
        } }
    }

    @Test
    fun stencilScissorRenders() {
        fun donut(inverse: Boolean) = Path().apply {
            addRect(RectF32.ofLTRB(-2f, -2f, 10f, 10f))
            addRect(RectF32.ofLTRB(2f, 2f, 6f, 6f))
            fillType = if (inverse) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
        }
        listOf(false, true).forEach { inverse ->
            val result = clippedFullTarget(donut(inverse)).renderAndRepeat()
            assertPixel(result.pixels, 1, 1, if (inverse) 255 else 0, 0, if (inverse) 0 else 255, 255)
            assertPixel(result.pixels, 3, 3, if (inverse) 0 else 255, 0, if (inverse) 255 else 0, 255)
        }
        fun concave(inverse: Boolean) = Path().apply {
            moveTo(-2f, -2f); lineTo(10f, -2f); lineTo(10f, 2f); lineTo(2f, 2f)
            lineTo(2f, 10f); lineTo(-2f, 10f); close()
            if (inverse) fillType = FillType.INVERSE_WINDING
        }
        listOf(false, true).forEach { inverse ->
            val result = clippedFullTarget(concave(inverse)).renderAndRepeat()
            assertPixel(result.pixels, 1, 6, if (inverse) 255 else 0, 0, if (inverse) 0 else 255, 255)
            assertPixel(result.pixels, 6, 6, if (inverse) 0 else 255, 0, if (inverse) 255 else 0, 255)
        }
    }

    @Test
    fun lateLayerOutsideCoverage() {
        listOf(false, true).forEach { inverse -> listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation ->
            val surface = Surface(16, 16).also { target -> target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
                save()
                clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
                saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
                clipPath(Path().apply {
                    moveTo(0f, 0f); lineTo(2f, 0f); lineTo(0f, 2f); close()
                    if (inverse) fillType = FillType.INVERSE_WINDING
                }, operation, antiAlias = false)
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
                restore(); restore()
            } }
            val result = surface.renderAndRepeat()
            val blue = (operation == ClipOp.DIFFERENCE) != inverse
            for (y in 0 until 16) for (x in 0 until 16) {
                val inside = x in 4 until 12 && y in 3 until 11
                assertPixel(result.pixels, 16, x, y, if (blue && inside) 0 else 255, 0, if (blue && inside) 255 else 0, 255)
            }
        } }
    }

    @Test
    fun translatedLayerScissor() {
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            clipPath(Path().apply { moveTo(0f, -1f); lineTo(16f, -1f); lineTo(0f, 15f); close() }, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
            restore(); restore()
        } }
        val result = surface.renderAndRepeat()
        assertPixel(result.pixels, 16, 5, 4, 0, 0, 255, 255)
        assertPixel(result.pixels, 16, 10, 9, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 3, 4, 255, 0, 0, 255)
    }

    @Test
    fun affinePictureScissor() {
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).apply {
            clipPath(Path().apply { moveTo(-4f, -4f); lineTo(12f, -4f); lineTo(-4f, 12f); close() }, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        }
        val picture = recorder.finishRecordingAsPicture()
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); translate(4f, 3f); drawPicture(picture); restore()
        } }
        val result = surface.renderAndRepeat()
        assertPixel(result.pixels, 16, 5, 4, 0, 0, 255, 255)
        assertPixel(result.pixels, 16, 10, 9, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 3, 4, 255, 0, 0, 255)
    }

    @Test
    fun orderedOutsideCoverage() {
        listOf(false, true).forEach { inverse -> listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation ->
            listOf(false, true).forEach { outsideFirst ->
                val surface = Surface(8, 8).also { target -> target.canvas {
                    drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
                    val main = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 7f, 7f)) }
                    val outside = Path().apply {
                        moveTo(10f, 10f); lineTo(14f, 10f); lineTo(10f, 14f); close()
                        if (inverse) fillType = FillType.INVERSE_WINDING
                    }
                    if (outsideFirst) { clipPath(outside, operation, antiAlias = false); clipPath(main, antiAlias = false) }
                    else { clipPath(main, antiAlias = false); clipPath(outside, operation, antiAlias = false) }
                    drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
                } }
                val result = surface.renderAndRepeat()
                val blue = (operation == ClipOp.DIFFERENCE) != inverse
                for (y in 0 until 8) for (x in 0 until 8) {
                    val expectedBlue = blue && x in 1 until 7 && y in 1 until 7
                    assertPixel(result.pixels, x, y, if (expectedBlue) 0 else 255, 0, if (expectedBlue) 255 else 0, 255)
                }
            }
        } }
    }

    @Test
    fun orderedOutsideCoverageInLayer() {
        listOf(false, true).forEach { inverse -> listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation -> listOf(false, true).forEach { outsideFirst ->
            val surface = Surface(16, 16).also { target -> target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
                save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
                saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
                val main = Path().apply { addRect(RectF32.ofLTRB(5f, 4f, 11f, 10f)) }
                val outside = Path().apply {
                    moveTo(0f, 0f); lineTo(2f, 0f); lineTo(0f, 2f); close()
                    if (inverse) fillType = FillType.INVERSE_WINDING
                }
                if (outsideFirst) { clipPath(outside, operation, antiAlias = false); clipPath(main, antiAlias = false) }
                else { clipPath(main, antiAlias = false); clipPath(outside, operation, antiAlias = false) }
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
                restore(); restore()
            } }
            val result = surface.renderAndRepeat()
            val blue = (operation == ClipOp.DIFFERENCE) != inverse
            assertPixel(result.pixels, 16, 6, 5, if (blue) 0 else 255, 0, if (blue) 255 else 0, 255)
            assertPixel(result.pixels, 16, 4, 3, 255, 0, 0, 255)
            assertPixel(result.pixels, 16, 3, 4, 255, 0, 0, 255)
        } } }
    }

    @Test
    fun analyticHardRectScissorInLayer() {
        val rect = Surface(16, 16).also { target -> target.canvas {
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            drawRect(RectF32.ofLTRB(11f, 10f, 12f, 11f), Paint(ColorARGB.Blue, antiAlias = false))
            clipPath(Path().apply { addRect(RectF32.ofLTRB(4f, 3f, 12f, 11f)) }, antiAlias = false)
            clipRect(RectF32.ofLTRB(3.75f, 3f, 4.5f, 11f), antiAlias = false)
            drawRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), Paint(ColorARGB.Black, antiAlias = false))
            restore(); restore()
        } }.renderAndRepeat()
        assertPixel(rect.pixels, 16, 4, 6, 0, 0, 0, 0)
        assertPixel(rect.pixels, 16, 5, 6, 0, 0, 0, 0)
        assertPixel(rect.pixels, 16, 11, 10, 0, 0, 255, 255)
    }

    @Test
    fun analyticAaRectScissorInLayer() {
        val rect = Surface(16, 16).also { target -> target.canvas {
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            clipPath(Path().apply { addRect(RectF32.ofLTRB(4f, 3f, 12f, 11f)) }, antiAlias = false)
            clipRect(RectF32.ofLTRB(3.75f, 3f, 4.5f, 11f), antiAlias = true)
            drawRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), Paint(ColorARGB.Black, antiAlias = false))
            restore(); restore()
        } }.renderAndRepeat()
        assertPixel(rect.pixels, 16, 4, 6, 0, 0, 0, 128)
        assertPixel(rect.pixels, 16, 5, 6, 0, 0, 0, 0)
    }

    @Test
    fun analyticHardRRectScissorInLayer() {
        val rrect = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            clipPath(Path().apply { addRect(RectF32.ofLTRB(4f, 3f, 12f, 11f)) }, antiAlias = false)
            clipRRect(RRectF32.of(RectF32.ofLTRB(2f, 1f, 10f, 9f), radius = 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), Paint(ColorARGB.Blue, antiAlias = false))
            restore(); restore()
        } }.renderAndRepeat()
        assertPixel(rrect.pixels, 16, 4, 6, 0, 0, 255, 255)
        assertPixel(rrect.pixels, 16, 11, 6, 255, 0, 0, 255)
        assertPixel(rrect.pixels, 16, 11, 10, 255, 0, 0, 255)
    }

    @Test
    fun analyticAaRRectScissorInLayerIsControlledCapabilityRefusal() {
        val rrect = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            clipPath(Path().apply { addRect(RectF32.ofLTRB(4f, 3f, 12f, 11f)) }, antiAlias = false)
            clipRRect(RRectF32.of(RectF32.ofLTRB(2f, 1f, 10f, 9f), radius = 1f), antiAlias = true)
            drawRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), Paint(ColorARGB.Blue, antiAlias = false))
            restore(); restore()
        } }
        // Future positive literal oracle after layer-AA promotion: blue(4,6), red(11,6), red(11,10).
        assertLayerAaCapabilityRefusalAndRecovery(rrect)
    }

    @Test
    fun analyticHardRectScissor() {
        val rect = Surface(8, 8).also { target -> target.canvas {
            save(); clipPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 8f, 8f)) }, antiAlias = false)
            clipRect(RectF32.ofLTRB(-.25f, 0f, .5f, 8f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Black, antiAlias = false)); restore()
            // Must be after restore: the hard thin clip is intentionally empty, but the frame stays observable.
            drawRect(RectF32.ofLTRB(7f, 7f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        } }.renderAndRepeat()
        assertPixel(rect.pixels, 0, 3, 0, 0, 0, 0)
        assertPixel(rect.pixels, 1, 3, 0, 0, 0, 0)
        assertPixel(rect.pixels, 7, 7, 0, 0, 255, 255)
    }

    @Test
    fun analyticAaRectScissor() {
        val rect = Surface(8, 8).also { target -> target.canvas {
            clipPath(Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 8f, 8f)) }, antiAlias = false)
            clipRect(RectF32.ofLTRB(-.25f, 0f, .5f, 8f), antiAlias = true)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Black, antiAlias = false))
        } }.renderAndRepeat()
        assertPixel(rect.pixels, 0, 3, 0, 0, 0, 128)
        assertPixel(rect.pixels, 1, 3, 0, 0, 0, 0)
    }

    @Test
    fun analyticHardRRectScissor() {
        val rrect = Surface(8, 8).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
            clipRRect(RRectF32.of(RectF32.ofLTRB(-2f, -2f, 6f, 6f), radius = 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        } }.renderAndRepeat()
        assertPixel(rrect.pixels, 0, 3, 0, 0, 255, 255)
        assertPixel(rrect.pixels, 7, 3, 255, 0, 0, 255)
        assertPixel(rrect.pixels, 7, 7, 255, 0, 0, 255)
    }

    @Test
    fun analyticAaRRectScissor() {
        val rrect = Surface(8, 8).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
            clipRRect(RRectF32.of(RectF32.ofLTRB(-2f, -2f, 6f, 6f), radius = 1f), antiAlias = true)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        } }.renderAndRepeat()
        assertPixel(rrect.pixels, 0, 3, 0, 0, 255, 255)
        assertPixel(rrect.pixels, 7, 3, 255, 0, 0, 255)
        assertPixel(rrect.pixels, 7, 7, 255, 0, 0, 255)
    }

    @Test
    fun aa4BinaryPathScissor() {
        fun donut(inverse: Boolean) = Path().apply {
            addRect(RectF32.ofLTRB(-2f, -2f, 10f, 10f)); addRect(RectF32.ofLTRB(2f, 2f, 6f, 6f))
            fillType = if (inverse) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
        }
        listOf(false, true).forEach { inverse ->
            val result = clippedFullTarget(donut(inverse), antiAlias = true).renderAndRepeat()
            assertPixel(result.pixels, 1, 1, if (inverse) 255 else 0, 0, if (inverse) 0 else 255, 255)
            assertPixel(result.pixels, 3, 3, if (inverse) 0 else 255, 0, if (inverse) 255 else 0, 255)
        }
        listOf(false, true).forEach { inverse -> listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation ->
            val outside = Path().apply {
                addRect(RectF32.ofLTRB(10f, 10f, 14f, 14f)); if (inverse) fillType = FillType.INVERSE_WINDING
            }
            val result = clippedFullTarget(outside, operation, antiAlias = true).renderAndRepeat()
            val blue = (operation == ClipOp.DIFFERENCE) != inverse
            for (y in 0 until 8) for (x in 0 until 8)
                assertPixel(result.pixels, x, y, if (blue) 0 else 255, 0, if (blue) 255 else 0, 255)
        } }
    }

    @Test
    fun aa4BinaryPathScissorInLayerIsControlledCapabilityRefusal() {
        listOf(false, true).forEach { inverse -> listOf(ClipOp.INTERSECT, ClipOp.DIFFERENCE).forEach { operation ->
            val surface = Surface(16, 16).also { target -> target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
                save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false); saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
                val outside = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)); if (inverse) fillType = FillType.INVERSE_WINDING }
                clipPath(outside, operation, antiAlias = true)
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false)); restore(); restore()
            } }
            // Future positive literal oracle: blue(6,5) iff Difference xor inverse; red(3,4).
            assertLayerAaCapabilityRefusalAndRecovery(surface)
        } }
    }

    @Test
    fun aa4BinaryPathDonutInLayerIsControlledCapabilityRefusal() {
        listOf(false, true).forEach { inverse ->
            val donut = Path().apply {
                addRect(RectF32.ofLTRB(2f, 1f, 14f, 13f))
                addRect(RectF32.ofLTRB(6f, 5f, 10f, 9f))
                fillType = if (inverse) FillType.INVERSE_EVEN_ODD else FillType.EVEN_ODD
            }
            val surface = Surface(16, 16).also { target -> target.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
                save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
                saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
                clipPath(donut, antiAlias = true)
                drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false))
                restore(); restore()
            } }
            // Future positive literal oracle: blue(5,4)/red(7,6) swap with inverse; red(3,4).
            assertLayerAaCapabilityRefusalAndRecovery(surface)
        }
    }

    @Test
    fun stencilAndScissorReset() {
        val donut = Path().apply {
            addRect(RectF32.ofLTRB(-2f, -2f, 6f, 6f)); addRect(RectF32.ofLTRB(1f, 1f, 4f, 4f))
            fillType = FillType.EVEN_ODD
        }
        val concave = Path().apply {
            moveTo(4f, 0f); lineTo(8f, 0f); lineTo(8f, 2f); lineTo(6f, 2f)
            lineTo(6f, 6f); lineTo(4f, 6f); close()
        }
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); clipPath(donut, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false)); restore()
            save(); clipPath(concave, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.of(255, 0, 255, 0), antiAlias = false)); restore()
            drawRect(RectF32.ofLTRB(12f, 12f, 14f, 14f), Paint(ColorARGB.of(255, 0, 255, 0), antiAlias = false))
        } }
        val result = surface.renderAndRepeat()
        assertPixel(result.pixels, 16, 2, 2, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 0, 4, 0, 0, 255, 255)
        assertPixel(result.pixels, 16, 5, 4, 0, 255, 0, 255)
        assertPixel(result.pixels, 16, 7, 4, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 12, 12, 0, 255, 0, 255)
    }

    @Test
    fun stencilAndScissorResetInLayer() {
        val donut = Path().apply {
            addRect(RectF32.ofLTRB(2f, 1f, 10f, 9f)); addRect(RectF32.ofLTRB(5f, 4f, 8f, 7f))
            fillType = FillType.EVEN_ODD
        }
        val concave = Path().apply {
            moveTo(8f, 3f); lineTo(12f, 3f); lineTo(12f, 5f); lineTo(10f, 5f)
            lineTo(10f, 9f); lineTo(8f, 9f); close()
        }
        val surface = Surface(16, 16).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Red, antiAlias = false))
            save(); clipRect(RectF32.ofLTRB(4f, 3f, 12f, 11f), antiAlias = false)
            saveLayer(RectF32.ofLTRB(4f, 3f, 12f, 11f))
            save(); clipPath(donut, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.Blue, antiAlias = false)); restore()
            save(); clipPath(concave, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint(ColorARGB.of(255, 0, 255, 0), antiAlias = false)); restore()
            restore(); restore()
            drawRect(RectF32.ofLTRB(12f, 12f, 14f, 14f), Paint(ColorARGB.of(255, 0, 255, 0), antiAlias = false))
        } }
        val result = surface.renderAndRepeat()
        assertPixel(result.pixels, 16, 6, 5, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 4, 7, 0, 0, 255, 255)
        assertPixel(result.pixels, 16, 9, 7, 0, 255, 0, 255)
        assertPixel(result.pixels, 16, 11, 7, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 12, 12, 0, 255, 0, 255)
    }

    @Test
    fun budgetRefusalPreservesSentinelAndRecovers() {
        val sentinel = Surface(8, 8).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
        } }
        val before = sentinel.render().also(::assertNativeResult).pixels.copyOf()
        val rejected = Surface(8, 8, config = RenderConfig(frameLocalBudgetBytes = 1L)).also { target -> target.canvas {
            clipPath(Path().apply { moveTo(-4f, -4f); lineTo(12f, -4f); lineTo(-4f, 12f); close() }, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Blue, antiAlias = false))
        } }
        val failure = assertFailsWith<IllegalStateException> { rejected.render() }
        assertTrue(
            failure.message?.startsWith("w4e.clip.budget.frame-local-exceeded:") == true,
            failure.message ?: "missing W4e frame-local budget diagnostic",
        )
        val after = sentinel.render().also(::assertNativeResult)
        assertContentEquals(before, after.pixels, "fresh sentinel render must survive B=1 refusal")
        val recovered = clippedFullTarget(Path().apply {
            moveTo(-4f, -4f); lineTo(12f, -4f); lineTo(-4f, 12f); close()
        }).renderAndRepeat()
        assertPixel(recovered.pixels, 1, 1, 0, 0, 255, 255)
        assertPixel(recovered.pixels, 6, 6, 255, 0, 0, 255)
    }

    private fun clippedFullTarget(clip: Path, operation: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = false): Surface = Surface(8, 8).also { target -> target.canvas {
        drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
        clipPath(clip, operation, antiAlias = antiAlias)
        drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.of(255, 0, 0, 255), antiAlias = false))
    } }

    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render()
        assertNativeResult(first)
        val second = render()
        assertNativeResult(second)
        assertContentEquals(first.pixels, second.pixels, "second native frame must be byte-identical")
        return first
    }

    private fun assertLayerAaCapabilityRefusalAndRecovery(rejected: Surface) {
        val sentinel = Surface(8, 8).also { target -> target.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), Paint(ColorARGB.Red, antiAlias = false))
            drawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint(ColorARGB.Blue, antiAlias = false))
        } }
        val before = sentinel.render().also(::assertNativeResult).pixels.copyOf()
        assertPixel(before, 1, 1, 0, 0, 255, 255)
        assertPixel(before, 6, 6, 255, 0, 0, 255)

        val failure = assertFailsWith<IllegalStateException> { rejected.render() }
        val message = failure.message.orEmpty()
        val code = "w4e.clip.capability-unavailable"
        val detail = "W5b final blending requires the admitted single-sample W4e topology"
        assertTrue(message == "$code: $detail", message)

        val after = sentinel.render().also(::assertNativeResult)
        assertContentEquals(before, after.pixels, "fresh sentinel render must survive layer-AA capability refusal")
        assertPixel(after.pixels, 1, 1, 0, 0, 255, 255)
        assertPixel(after.pixels, 6, 6, 255, 0, 0, 255)
    }

    private fun assertNativeResult(result: RenderResult) {
        val marker = if (result.width > 7 && result.height > 7) {
            val offset = (7 * result.width + 7) * 4
            result.pixels.copyOfRange(offset, offset + 4).joinToString(prefix = "rgba(7,7)=", separator = ",") { it.toString() }
        } else "rgba(7,7)=out-of-range"
        val trace = "diagnostics=${result.diagnostics.summary()} dispatch=${result.stats.opsDispatched} " +
            "steps=${result.structuralSteps} evidence=${result.nativeEvidenceScopeKinds} " +
            "counters=${result.nativeEvidenceCounters} pixels=${result.pixels.contentHashCode()} $marker"
        assertTrue(result.isClean, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), trace)
        assertTrue(result.stats.opsDispatched > 0, "native clipped frame must dispatch: $trace")
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        assertPixel(pixels, 8, x, y, r, g, b, a)
    }

    private fun assertPixel(pixels: UByteArray, width: Int, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(
            ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4),
            "pixel $x,$y",
        )
    }
}
