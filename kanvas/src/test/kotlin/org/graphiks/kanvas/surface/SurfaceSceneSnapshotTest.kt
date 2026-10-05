@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.ColorFilterNode
import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.render.ir.ImageResourceSnapshot
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.MaskFilterNode
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SurfaceSceneSnapshotTest {
    @Test
    fun `snapshotScene captures recorded operations at surface extent without rendering`() {
        val surface = Surface(
            width = 37,
            height = 19,
            config = RenderConfig(maxPathVertices = UInt.MAX_VALUE),
        )
        surface.canvas { drawRect(RectF32.ofLTRB(1f, 2f, 8f, 9f), Paint.fill(ColorARGB.Red)) }

        val captured = assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene

        assertEquals(37, captured.extent.width)
        assertEquals(19, captured.extent.height)
        assertEquals(ColorSpace.SRGB, captured.colorSpace)
        assertEquals(1, captured.commandCount)
        assertInstanceOf(SceneCommand.Draw::class.java, captured.commandAt(0))
    }

    @Test
    fun `snapshotScene is detached from later canvas mutations`() {
        val surface = Surface(16, 12)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint.fill(ColorARGB.Blue)) }

        val captured = assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene
        surface.canvas { drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint.fill(ColorARGB.Green)) }

        assertEquals(1, captured.commandCount)
        assertEquals(2, assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene.commandCount)
    }

    @Test
    fun `snapshotScene accepts an explicit capture budget for larger recordings`() {
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint.fill(ColorARGB.Blue))
            drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint.fill(ColorARGB.Green))
        }

        assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene(SceneCaptureLimits(maxNodes = 1)))
        val captured = assertInstanceOf(
            SceneCaptureResult.Captured::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxNodes = 2)),
        ).scene

        assertEquals(2, captured.commandCount)
    }

    @Test
    fun `snapshotScene retains image pixels present when drawImage was recorded`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val surface = Surface(16, 12)
        surface.canvas {
            drawImage(Image.fromPixels(1, 1, pixels, sourceId = "mutable-image"), RectF32.ofLTRB(0f, 0f, 1f, 1f))
        }
        pixels[0] = 9

        val draw = surface.singleDraw()
        val resource = assertInstanceOf(ImageResourceSnapshot.Pixels::class.java, draw.node.resource)

        assertEquals(listOf<Byte>(1, 2, 3, 4), resource.copyPixels().toList())
    }

    @Test
    fun `snapshotScene captures mutable image state separately for each draw`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val image = Image.fromPixels(1, 1, pixels, sourceId = "mutable-image-each-draw")
        val surface = Surface(16, 12)
        surface.canvas().drawImage(image, RectF32.ofLTRB(0f, 0f, 1f, 1f))
        pixels[0] = 9
        surface.canvas().drawImage(image, RectF32.ofLTRB(1f, 0f, 2f, 1f))

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene
        val first = assertInstanceOf(ImageResourceSnapshot.Pixels::class.java, assertInstanceOf(SceneCommand.Draw::class.java, scene.commandAt(0)).node.resource)
        val second = assertInstanceOf(ImageResourceSnapshot.Pixels::class.java, assertInstanceOf(SceneCommand.Draw::class.java, scene.commandAt(1)).node.resource)

        assertEquals(listOf<Byte>(1, 2, 3, 4), first.copyPixels().toList())
        assertEquals(listOf<Byte>(9, 2, 3, 4), second.copyPixels().toList())
    }

    @Test
    fun `snapshotScene retains image pixels present when image shader was recorded`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val image = Image.fromPixels(1, 1, pixels, sourceId = "mutable-shader-image")
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(shader = image.makeShader()))
        }
        pixels[0] = 9

        val shader = assertInstanceOf(MaterialNode.ImageSample::class.java, surface.singleDraw().node.paint!!.shader)
        val resource = assertInstanceOf(ImageResourceSnapshot.Pixels::class.java, shader.image)

        assertEquals(listOf<Byte>(1, 2, 3, 4), resource.copyPixels().toList())
    }

    @Test
    fun `snapshotScene retains color-filter table present when draw was recorded`() {
        val table = ubyteArrayOf(1u, 2u, 3u)
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(colorFilter = ColorFilter.Table(table)))
        }
        table[0] = 9u

        val filter = assertInstanceOf(ColorFilterNode.Table::class.java, surface.singleDraw().node.paint!!.colorFilter)

        assertEquals(listOf<UByte>(1u, 2u, 3u), filter.table.copyToUByteArray().toList())
    }

    @Test
    fun `snapshotScene retains HSLA matrix present when draw was recorded`() {
        val values = floatArrayOf(1f, 2f, 3f)
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(colorFilter = ColorFilter.HSLAMatrix(values)))
        }
        values[0] = 9f

        val filter = assertInstanceOf(ColorFilterNode.HSLAMatrix::class.java, surface.singleDraw().node.paint!!.colorFilter)

        assertEquals(listOf(1f, 2f, 3f), filter.values.copyToFloatArray().toList())
    }

    @Test
    fun `snapshotScene retains color matrix present when draw was recorded`() {
        val matrix = ColorMatrixF32.ofIdentity()
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(colorFilter = ColorFilter.Matrix(matrix)))
        }
        matrix.setScale(9f, 9f, 9f)

        val filter = assertInstanceOf(ColorFilterNode.Matrix::class.java, surface.singleDraw().node.paint!!.colorFilter)

        assertEquals(1f, filter.values.copyToFloatArray()[0])
    }

    @Test
    fun `snapshotScene retains mask-filter table present when draw was recorded`() {
        val table = ubyteArrayOf(1u, 2u, 3u)
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(maskFilter = MaskFilter.Table(table)))
        }
        table[0] = 9u

        val filter = assertInstanceOf(MaskFilterNode.Table::class.java, surface.singleDraw().node.paint!!.maskFilter)

        assertEquals(listOf<UByte>(1u, 2u, 3u), filter.table.copyToUByteArray().toList())
    }

    @Test
    fun `snapshotScene reports cyclic merge recorded through Canvas without stack overflow`() {
        val inputs = mutableListOf<ImageFilter>()
        val cyclic = ImageFilter.Merge(inputs)
        inputs += cyclic
        val surface = Surface(16, 12)

        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(imageFilter = cyclic))
        }

        val invalid = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
        assertEquals("cyclic-effect-graph", invalid.diagnostics.single().code.value)
    }

    @Test
    fun `snapshotScene defers acyclic effect depth limits until capture`() {
        var filter: ImageFilter = ImageFilter.Blur(1f, 1f)
        repeat(65) { filter = ImageFilter.Blur(1f, 1f, input = filter) }
        val surface = Surface(16, 12)

        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(imageFilter = filter))
        }

        assertInstanceOf(
            SceneCaptureResult.Captured::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxDepth = 128, graphLimits = GraphLimits(maxDepth = 128, maxNodes = 128))),
        )
        val invalid = assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene())
        assertEquals("graph-depth-limit", invalid.diagnostics.single().code.value)
    }

    private fun Surface.singleDraw(): SceneCommand.Draw = assertInstanceOf(
        SceneCommand.Draw::class.java,
        assertInstanceOf(SceneCaptureResult.Captured::class.java, snapshotScene()).scene.commandAt(0),
    )
}
