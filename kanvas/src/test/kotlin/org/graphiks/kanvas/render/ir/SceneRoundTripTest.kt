package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SceneRoundTripTest {
    @Test
    fun `round trip preserves a point as DrawPoint rather than a one element DrawPoints`() {
        val operation = DisplayOp.DrawPoint(
            x = 7f,
            y = 9f,
            paint = Paint.fill(ColorARGB.Blue),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )

        val captured = DisplayOpSceneAdapter.capture(
            operations = listOf(operation),
            extent = SceneExtent(16, 16),
            colorSpace = ColorSpace.SRGB,
        )

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene
        val reconstructed = SceneDisplayOpAdapter.toDisplayOps(scene)

        assertEquals(listOf(operation), reconstructed)
        assertInstanceOf(DisplayOp.DrawPoint::class.java, reconstructed.single())
    }

    @Test
    fun `round trip keeps DrawColor transform and clip unlike Clear`() {
        val drawColor = DisplayOp.DrawColor(
            color = ColorARGB.Green,
            mode = org.graphiks.kanvas.paint.BlendMode.XOR,
            transform = Matrix3x3F32.translation(5f, 6f),
            clip = ClipStack.DeviceRect(RectF32.ofLTRB(1f, 2f, 12f, 13f), antiAlias = false),
        )
        val clear = DisplayOp.Clear(ColorARGB.Blue)

        val captured = DisplayOpSceneAdapter.capture(
            operations = listOf(drawColor, clear),
            extent = SceneExtent(16, 16),
            colorSpace = ColorSpace.SRGB,
        )

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene
        assertEquals(listOf(drawColor, clear), SceneDisplayOpAdapter.toDisplayOps(scene))
    }

    @Test
    fun `round trip keeps complete layer record and transform`() {
        val layer = DisplayOp.BeginLayer(
            rec = SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 2f, 30f, 40f),
                paint = Paint(
                    color = ColorARGB.Red,
                    blendMode = org.graphiks.kanvas.paint.BlendMode.SCREEN,
                    style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
                    strokeWidth = 3f,
                    antiAlias = false,
                ),
            ),
            transform = Matrix3x3F32.translation(8f, 9f),
        )

        val captured = DisplayOpSceneAdapter.capture(
            operations = listOf(layer, DisplayOp.EndLayer),
            extent = SceneExtent(48, 48),
            colorSpace = ColorSpace.SRGB,
        )

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene
        assertEquals(listOf(layer, DisplayOp.EndLayer), SceneDisplayOpAdapter.toDisplayOps(scene))
    }

    @Test
    fun `round trip keeps layer backdrop and composite clip independently`() {
        val composite = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32.ofLTRB(3f, 4f, 21f, 22f), ClipOp.DIFFERENCE, antiAlias = false)),
        )
        val layer = DisplayOp.BeginLayer(
            SaveLayerRec(
                bounds = RectF32.ofLTRB(1f, 2f, 30f, 40f),
                paint = Paint.fill(ColorARGB.Yellow),
                backdrop = ImageFilter.Blur(1.5f, 2.5f, TileMode.DECAL),
                compositeClip = composite,
            ),
            Matrix3x3F32.translation(8f, 9f),
        )

        val result = DisplayOpSceneAdapter.capture(listOf(layer, DisplayOp.EndLayer), SceneExtent(48, 48), ColorSpace.SRGB)

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, result).scene
        assertEquals(listOf(layer, DisplayOp.EndLayer), SceneDisplayOpAdapter.toDisplayOps(scene))
    }

    @Test
    fun `paint effects and owned arrays survive capture mutation and inverse`() {
        val pixels = byteArrayOf(1, 2, 3, 4)
        val colorTable = ubyteArrayOf(1u, 2u, 3u)
        val dash = floatArrayOf(2f, 3f)
        val kernel = floatArrayOf(0.5f)
        val image = Image.fromPixels(1, 1, pixels, sourceId = "paint-image")
        val paint = Paint(
            color = ColorARGB.Cyan,
            shader = Shader.Image(image, sampling = SamplingOptions.Cubic(0f, 0.5f)),
            colorFilter = ColorFilter.Table(colorTable),
            maskFilter = MaskFilter.Table(ubyteArrayOf(9u)),
            pathEffect = PathEffect.Dash(dash, 1f),
            imageFilter = ImageFilter.MatrixConvolution(
                SizeF32(1f, 1f), kernel, 1f, 0f, Vector2F32(0f, 0f), TileMode.CLAMP, false,
            ),
            blender = Blender.Arithmetic(1f, 2f, 3f, 4f),
            blendMode = org.graphiks.kanvas.paint.BlendMode.SCREEN,
        )
        val operation = DisplayOp.DrawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint, Matrix3x3F32.Identity, ClipStack.WideOpen)

        val captured = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(8, 8), ColorSpace.SRGB)
        pixels[0] = 99
        colorTable[0] = 99u
        dash[0] = 99f
        kernel[0] = 99f

        val restored = assertInstanceOf(
            DisplayOp.DrawRect::class.java,
            SceneDisplayOpAdapter.toDisplayOps(assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene).single(),
        )
        val restoredShader = assertInstanceOf(Shader.Image::class.java, restored.paint.shader)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), requireNotNull(restoredShader.image.pixels))
        assertTrue(ubyteArrayOf(1u, 2u, 3u).contentEquals(assertInstanceOf(ColorFilter.Table::class.java, restored.paint.colorFilter).table))
        assertArrayEquals(floatArrayOf(2f, 3f), assertInstanceOf(PathEffect.Dash::class.java, restored.paint.pathEffect).intervals)
        assertArrayEquals(floatArrayOf(0.5f), assertInstanceOf(ImageFilter.MatrixConvolution::class.java, restored.paint.imageFilter).kernel)
        assertEquals(Blender.Arithmetic(1f, 2f, 3f, 4f), restored.paint.blender)
    }
}
