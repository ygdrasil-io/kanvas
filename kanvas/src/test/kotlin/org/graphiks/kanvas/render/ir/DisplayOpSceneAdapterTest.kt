package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayOpSceneAdapterTest {
    @Test
    fun `capture rejects nonfinite graph text and picture values before reconstruction`() {
        val bounds = RectF32.ofLTRB(0f, 0f, 2f, 2f)
        val transform = Matrix3x3F32.Identity
        val operations = listOf(
            DisplayOp.DrawRect(
                bounds,
                Paint(shader = org.graphiks.kanvas.paint.Shader.LinearGradient(Point2F32(Float.NaN, 0f), Point2F32(1f, 1f), emptyList())),
                transform,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                bounds,
                Paint(imageFilter = ImageFilter.PointLitDiffuse(Point2F32(Float.POSITIVE_INFINITY, 0f), ColorARGB.White, 1f, 1f)),
                transform,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawText(
                TextBlob(listOf(KanvasGlyphRun(listOf(1u), listOf(Point2F32(Float.NaN, 0f)), 12f))),
                0f,
                0f,
                Paint.fill(ColorARGB.White),
                transform,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawPicture(Picture(RectF32.ofLTRB(0f, 0f, Float.NaN, 2f), emptyList()), null, transform, ClipStack.WideOpen),
        )

        operations.forEach { operation ->
            val result = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(8, 8), ColorSpace.SRGB)
            assertEquals("non-finite-value", assertInstanceOf(SceneCaptureResult.Invalid::class.java, result).diagnostics.single().code.value)
        }
    }

    @Test
    fun `capture returns Invalid before recursing through a cyclic image filter graph`() {
        val children = mutableListOf<ImageFilter>()
        val cyclic = ImageFilter.Merge(children)
        children += cyclic
        val operation = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            Paint(imageFilter = cyclic),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )

        val result = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(8, 8), ColorSpace.SRGB)

        assertEquals("cyclic-effect-graph", assertInstanceOf(SceneCaptureResult.Invalid::class.java, result).diagnostics.single().code.value)
    }

    @Test
    fun `capture returns Invalid for malformed Atlas cardinalities and nonfinite clip geometry`() {
        val image = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "atlas-invalid")
        val transform = Matrix3x3F32.Identity
        val malformedAtlas = DisplayOp.DrawAtlas(
            image,
            transforms = listOf(transform),
            texRects = emptyList(),
            colors = null,
            blendMode = BlendMode.SRC_OVER,
            paint = null,
            transform = transform,
            clip = ClipStack.WideOpen,
        )
        val nonfiniteClip = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            Paint.fill(ColorARGB.Red),
            transform,
            ClipStack.Complex(listOf(ClipStackOp.RectOp(RectF32.ofLTRB(Float.NaN, 0f, 1f, 1f), org.graphiks.kanvas.pipeline.ClipOp.INTERSECT))),
        )

        val atlasResult = DisplayOpSceneAdapter.capture(listOf(malformedAtlas), SceneExtent(8, 8), ColorSpace.SRGB)
        val clipResult = DisplayOpSceneAdapter.capture(listOf(nonfiniteClip), SceneExtent(8, 8), ColorSpace.SRGB)

        assertEquals("atlas-cardinality", assertInstanceOf(SceneCaptureResult.Invalid::class.java, atlasResult).diagnostics.single().code.value)
        assertEquals("non-finite-value", assertInstanceOf(SceneCaptureResult.Invalid::class.java, clipResult).diagnostics.single().code.value)
    }

    @Test
    fun `normalized image and layer axes retain source effects and custom blend independently`() {
        val image = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "axis-image")
        val paint = Paint(
            color = ColorARGB.Magenta,
            blendMode = BlendMode.SCREEN,
            blender = Blender.Arithmetic(1f, 2f, 3f, 4f),
            imageFilter = ImageFilter.Blur(1f, 2f, TileMode.DECAL),
        )
        val imageOperation = DisplayOp.DrawImage(
            image,
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            RectF32.ofLTRB(2f, 3f, 6f, 7f),
            paint,
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val layer = DisplayOp.BeginLayer(RectF32.ofLTRB(0f, 0f, 5f, 5f), paint)

        val scene = assertInstanceOf(
            SceneCaptureResult.Captured::class.java,
            DisplayOpSceneAdapter.capture(listOf(imageOperation, layer), SceneExtent(8, 8), ColorSpace.SRGB),
        ).scene
        val draw = assertInstanceOf(SceneCommand.Draw::class.java, scene.commandAt(0)).node
        val descriptor = assertInstanceOf(SceneCommand.BeginLayer::class.java, scene.commandAt(1)).descriptor

        assertInstanceOf(MaterialNode.ImageSample::class.java, draw.material)
        assertInstanceOf(EffectStack.Entries::class.java, draw.effects)
        assertInstanceOf(BlendNode.Paint::class.java, draw.blend)
        assertInstanceOf(EffectStack.Entries::class.java, descriptor.effects)
        assertInstanceOf(BlendNode.Paint::class.java, descriptor.blend)
    }

    @Test
    fun `capture aggregates graph limits across commands rather than resetting per paint`() {
        val operations = listOf(
            DisplayOp.DrawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(shader = org.graphiks.kanvas.paint.Shader.SolidColor(ColorARGB.Red)), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawRect(RectF32.ofLTRB(2f, 0f, 3f, 1f), Paint(shader = org.graphiks.kanvas.paint.Shader.SolidColor(ColorARGB.Blue)), Matrix3x3F32.Identity, ClipStack.WideOpen),
        )

        val result = DisplayOpSceneAdapter.capture(
            operations,
            SceneExtent(8, 8),
            ColorSpace.SRGB,
            SceneCaptureLimits(graphLimits = GraphLimits(maxNodes = 1)),
        )

        assertEquals("graph-node-limit", assertInstanceOf(SceneCaptureResult.Invalid::class.java, result).diagnostics.single().code.value)
    }

    @Test
    fun `capture reports typed invalid diagnostics for nonfinite and aggregate limits`() {
        val invalidPoint = DisplayOp.DrawPoint(Float.NaN, 1f, Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen)
        val nonfinite = DisplayOpSceneAdapter.capture(listOf(invalidPoint), SceneExtent(8, 8), ColorSpace.SRGB)
        val nonfiniteInvalid = assertInstanceOf(SceneCaptureResult.Invalid::class.java, nonfinite)
        assertEquals("non-finite-value", nonfiniteInvalid.diagnostics.single().code.value)

        val first = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "resource-one")
        val second = Image.fromPixels(1, 1, byteArrayOf(5, 6, 7, 8), sourceId = "resource-two")
        val images = listOf(
            DisplayOp.DrawImage(first, RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(0f, 0f, 1f, 1f), null, Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawImage(second, RectF32.ofLTRB(0f, 0f, 1f, 1f), RectF32.ofLTRB(0f, 0f, 1f, 1f), null, Matrix3x3F32.Identity, ClipStack.WideOpen),
        )
        val limited = DisplayOpSceneAdapter.capture(images, SceneExtent(8, 8), ColorSpace.SRGB, SceneCaptureLimits(maxResources = 1))
        assertTrue(assertInstanceOf(SceneCaptureResult.Invalid::class.java, limited).diagnostics.single().code.value == "scene-resource-limit")
    }

    @Test
    fun `capture preserves a rectangle public geometry paint transform and clip`() {
        val rectangle = RectF32.ofLTRB(1f, 2f, 30f, 40f)
        val paint = Paint.fill(ColorARGB.Red)
        val operation = DisplayOp.DrawRect(
            rectangle,
            paint,
            Matrix3x3F32.translation(3f, 4f),
            ClipStack.DeviceRect(RectF32.ofLTRB(0f, 0f, 20f, 20f), antiAlias = false),
        )

        val captured = DisplayOpSceneAdapter.capture(
            operations = listOf(operation),
            extent = SceneExtent(64, 48),
            colorSpace = ColorSpace.SRGB,
        )

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene
        val reconstructed = SceneDisplayOpAdapter.toDisplayOps(scene)

        assertEquals(listOf(operation), reconstructed)
    }

    @Test
    fun `capture and inverse preserve the ordered matrix of all DisplayOp variants`() {
        val bounds = RectF32.ofLTRB(1f, 2f, 12f, 14f)
        val transform = Matrix3x3F32.translation(3f, 4f)
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(bounds, org.graphiks.kanvas.pipeline.ClipOp.INTERSECT, antiAlias = false)),
        )
        val paint = Paint(
            color = ColorARGB.Magenta,
            blendMode = BlendMode.SCREEN,
            style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
            strokeWidth = 2f,
            antiAlias = false,
        )
        val image = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "matrix-image")
        val path = Path().addRect(bounds)
        val rrect = RRectF32.of(bounds, 2f)
        val vertices = Vertices(VertexMode.TRIANGLES, listOf(Point2F32(1f, 2f), Point2F32(3f, 4f), Point2F32(5f, 6f)))
        val picture = Picture(bounds, listOf(DisplayOp.Clear(ColorARGB.Transparent)))
        val operations = listOf(
            DisplayOp.DrawRect(bounds, paint, transform, clip),
            DisplayOp.DrawRRect(rrect, paint, transform, clip),
            DisplayOp.DrawPath.withSourceOperation(path, paint, transform, clip, DrawPathSourceOperation.TEXT_EXPANDED),
            DisplayOp.DrawImage(image, bounds, bounds, null, transform, clip),
            DisplayOp.DrawText(TextBlob(listOf(KanvasGlyphRun(listOf(7u), listOf(Point2F32(2f, 3f)), 14f))), 5f, 6f, paint, transform, clip),
            DisplayOp.SetTransform(transform),
            DisplayOp.SetClip(clip),
            DisplayOp.BeginLayer(bounds, paint),
            DisplayOp.EndLayer,
            DisplayOp.DrawColor(ColorARGB.Cyan, BlendMode.XOR, transform, clip),
            DisplayOp.Clear(ColorARGB.Black),
            DisplayOp.DrawPoint(1f, 2f, paint, transform, clip),
            DisplayOp.DrawPoints(PointMode.LINES, listOf(Point2F32(1f, 2f), Point2F32(3f, 4f)), paint, transform, clip),
            DisplayOp.DrawDRRect(rrect, RRectF32.of(RectF32.ofLTRB(3f, 4f, 10f, 12f), 1f), paint, transform, clip),
            DisplayOp.DrawImageNine(image, bounds, bounds, null, transform, clip),
            DisplayOp.DrawImageLattice(image, Lattice(listOf(1), listOf(1)), bounds, null, transform, clip, SamplingOptions.Cubic(0f, 0.5f)),
            DisplayOp.DrawPicture(picture, null, transform, clip),
            DisplayOp.DrawVertices(vertices, paint, transform, clip),
            DisplayOp.DrawMesh(Mesh(vertices, bounds = bounds), paint, BlendMode.OVERLAY, transform, clip),
            DisplayOp.DrawAtlas(image, listOf(transform), listOf(bounds), listOf(ColorARGB.Yellow), BlendMode.MODULATE, null, transform, clip),
            DisplayOp.Annotation(bounds, "matrix", "annotation"),
            DisplayOp.FlushAndSnapshot(bounds),
        )

        val captured = DisplayOpSceneAdapter.capture(operations, SceneExtent(32, 32), ColorSpace.SRGB)

        val scene = assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene
        val reconstructed = SceneDisplayOpAdapter.toDisplayOps(scene)
        assertEquals(operations.size, reconstructed.size)
        operations.indices.forEach { index ->
            val expected = operations[index]
            val actual = reconstructed[index]
            when (expected) {
                is DisplayOp.DrawPath -> {
                    val restored = assertInstanceOf(DisplayOp.DrawPath::class.java, actual)
                    assertEquals(expected.path.toPathF32(), restored.path.toPathF32())
                    assertEquals(expected.paint, restored.paint)
                    assertEquals(expected.transform, restored.transform)
                    assertEquals(expected.clip, restored.clip)
                    assertEquals(expected.sourceOperation, restored.sourceOperation)
                }
                is DisplayOp.DrawPicture -> {
                    val restored = assertInstanceOf(DisplayOp.DrawPicture::class.java, actual)
                    assertEquals(expected.picture.cullRect, restored.picture.cullRect)
                    assertEquals(expected.picture.ops.size, restored.picture.ops.size)
                    assertEquals(expected.paint, restored.paint)
                    assertEquals(expected.transform, restored.transform)
                    assertEquals(expected.clip, restored.clip)
                }
                else -> assertEquals(expected, actual)
            }
        }
    }
}
