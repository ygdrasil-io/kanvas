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
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.ChildSlot
import org.graphiks.kanvas.pipeline.ChildType
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.pipeline.UniformSlot
import org.graphiks.kanvas.pipeline.UniformType
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SceneRoundTripTest {
    @Test
    fun `round trip restores registered runtime shader color filter and image filter descriptors`() {
        fun runtime(id: String): RuntimeEffect = RuntimeEffect(
            id = id,
            module = ShaderModule.fromSource("@fragment fn main() -> @location(0) vec4f { return vec4f(); }"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
        )
        val shaderRuntime = runtime("task11-runtime-shader")
        val colorRuntime = runtime("task11-runtime-color-filter")
        val imageRuntime = runtime("task11-runtime-image-filter")
        val operation = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 4f, 4f),
            Paint(
                shader = Shader.RuntimeEffect(shaderRuntime, UniformBlock.EMPTY),
                colorFilter = ColorFilter.RuntimeEffect(colorRuntime, UniformBlock.EMPTY),
                imageFilter = ImageFilter.RuntimeEffect(imageRuntime, UniformBlock.EMPTY),
            ),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )

        val captured = DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(8, 8), ColorSpace.SRGB)
        val restored = assertInstanceOf(
            DisplayOp.DrawRect::class.java,
            SceneDisplayOpAdapter.toDisplayOps(assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene).single(),
        )

        assertEquals(shaderRuntime, assertInstanceOf(Shader.RuntimeEffect::class.java, restored.paint.shader).effect)
        assertEquals(colorRuntime, assertInstanceOf(ColorFilter.RuntimeEffect::class.java, restored.paint.colorFilter).effect)
        assertEquals(imageRuntime, assertInstanceOf(ImageFilter.RuntimeEffect::class.java, restored.paint.imageFilter).effect)
    }

    @Test
    fun `runtime registry rejects a conflicting descriptor for the same public id`() {
        RuntimeEffect(
            id = "task11-runtime-collision",
            module = ShaderModule.fromSource("@fragment fn main() -> @location(0) vec4f { return vec4f(); }"),
            uniformLayout = UniformLayout(emptyList()),
            children = emptyList(),
        )

        val collision = assertThrows(IllegalArgumentException::class.java) {
            RuntimeEffect(
                id = "task11-runtime-collision",
                module = ShaderModule.fromSource("@fragment fn main() -> @location(0) vec4f { return vec4f(1); }"),
                uniformLayout = UniformLayout(emptyList()),
                children = emptyList(),
            )
        }

        assertTrue(collision.message.orEmpty().contains("incompatible descriptor"))
    }

    @Test
    fun `round trip preserves an explicitly empty complex clip instead of wide open`() {
        val operation = DisplayOp.DrawRect(
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            ClipStack.Complex(emptyList()),
        )

        val scene = assertInstanceOf(
            SceneCaptureResult.Captured::class.java,
            DisplayOpSceneAdapter.capture(listOf(operation), SceneExtent(8, 8), ColorSpace.SRGB),
        ).scene
        val restored = assertInstanceOf(DisplayOp.DrawRect::class.java, SceneDisplayOpAdapter.toDisplayOps(scene).single())

        assertEquals(ClipStack.Complex(emptyList()), restored.clip)
    }

    @Test
    fun `round trip restores a registered mesh runtime program and immutable mutable inputs`() {
        val positions = mutableListOf(Point2F32(1f, 2f), Point2F32(3f, 4f), Point2F32(5f, 6f))
        val vertices = org.graphiks.kanvas.types.Vertices(org.graphiks.kanvas.types.VertexMode.TRIANGLES, positions)
        val latticeX = mutableListOf(1)
        val latticeY = mutableListOf(1)
        val lattice = org.graphiks.kanvas.types.Lattice(latticeX, latticeY)
        val uniformValues = FloatArray(16) { index -> index.toFloat() }
        val effect = RuntimeEffect(
            id = "task11-mesh-runtime",
            module = ShaderModule.fromSource("@vertex fn main() -> @builtin(position) vec4f { return vec4f(); }"),
            uniformLayout = UniformLayout(listOf(UniformSlot("matrix", 0, UniformType.MAT4X4, 16))),
            children = listOf(ChildSlot("source", ChildType.SHADER)),
        )
        RuntimeEffect.register(effect)
        val program = MeshProgram(
            effect = effect,
            uniforms = UniformBlock { mat4x4("matrix", uniformValues) },
            children = MeshChildren.of("source" to ShaderChild(Shader.SolidColor(ColorARGB.Green))),
        )
        val image = Image.fromPixels(1, 1, byteArrayOf(1, 2, 3, 4), sourceId = "mutable-lattice")
        val operations = listOf(
            DisplayOp.DrawMesh(
                org.graphiks.kanvas.types.Mesh(vertices, program, RectF32.ofLTRB(0f, 0f, 8f, 8f)),
                Paint.fill(ColorARGB.White),
                null,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawImageLattice(image, lattice, RectF32.ofLTRB(0f, 0f, 8f, 8f), null, Matrix3x3F32.Identity, ClipStack.WideOpen),
        )

        val captured = DisplayOpSceneAdapter.capture(operations, SceneExtent(8, 8), ColorSpace.SRGB)
        positions[0] = Point2F32(99f, 99f)
        latticeX[0] = 99
        latticeY[0] = 99
        uniformValues[0] = 99f

        val restored = SceneDisplayOpAdapter.toDisplayOps(assertInstanceOf(SceneCaptureResult.Captured::class.java, captured).scene)
        val mesh = assertInstanceOf(DisplayOp.DrawMesh::class.java, restored[0]).mesh
        val restoredLattice = assertInstanceOf(DisplayOp.DrawImageLattice::class.java, restored[1]).lattice
        val matrix = assertInstanceOf(org.graphiks.kanvas.pipeline.UniformValue.M4::class.java, requireNotNull(mesh.program).uniforms.entries["matrix"])

        assertEquals(Point2F32(1f, 2f), mesh.vertices.positions.first())
        assertEquals(listOf(1), restoredLattice.xDivs)
        assertEquals(listOf(1), restoredLattice.yDivs)
        assertEquals(0f, matrix.values[0])
        assertEquals(effect, mesh.program.effect)
        assertEquals(Shader.SolidColor(ColorARGB.Green), mesh.program.children.getShader("source"))
    }

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
