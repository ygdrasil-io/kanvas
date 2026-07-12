package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipCoverageSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `complex difference clip masks rect rrect path and image without changing exterior`() {
        requireWebGpu()
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipRect(Rect(2f, 2f, 30f, 30f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(Rect(12f, 12f, 20f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawRect(Rect(2f, 2f, 12f, 12f), Paint.fill(Color.RED))
            drawRRect(RRect(Rect(20f, 2f, 30f, 12f), radius = 2f), Paint.fill(Color.RED))
            drawPath(Path { moveTo(2f, 22f); lineTo(12f, 22f); lineTo(7f, 30f); close() }, Paint.fill(Color.RED))
            drawRect(Rect(14f, 22f, 26f, 29f), Paint.stroke(Color.RED, 1f))
            drawImage(bluePixel(), Rect(24f, 14f, 30f, 20f), Paint())
            restore()
        }

        val result = surface.render()
        assertPixel(result.pixels, 4, 4, Color.RED)
        assertTransparent(result.pixels, 16, 16)
        assertPixel(result.pixels, 28, 16, Color.BLUE)
        assertPixel(result.pixels, 14, 22, Color.RED)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `AA device rect uses alpha mask rather than integer scissor`() {
        requireWebGpu()
        val surface = Surface(16, 16)
        surface.canvas {
            save()
            clipRect(Rect(3.5f, 2f, 12.5f, 14f), ClipOp.INTERSECT, antiAlias = true)
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED))
            restore()
        }

        val result = surface.render()
        assertTrue(alphaAt(result.pixels, 3, 8) in 1..254)
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
        )
    }

    @Test
    fun `mask refuses dst in before it emits a source`() {
        requireWebGpu()

        val result = renderMaskedRect(BlendMode.DST_IN)

        assertEquals(0, result.stats.opsDispatched)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.clip.mask.blend_mode:dst_in",
            result.diagnostics.entries.single().reason,
        )
    }

    @Test
    fun `mask refuses destination read blend before it emits a source`() {
        requireWebGpu()

        val result = renderMaskedRect(BlendMode.DARKEN)

        assertEquals(0, result.stats.opsDispatched)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.clip.mask.blend_mode:darken",
            result.diagnostics.entries.single().reason,
        )
    }

    @Test
    fun `no clip destination read remains delegated to task eight without a source`() {
        requireWebGpu()
        val surface = Surface(16, 16)
        surface.canvas {
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = BlendMode.DARKEN))
        }

        val result = surface.render()

        assertEquals(0, result.stats.opsDispatched)
        assertEquals(1, result.diagnostics.fatalCount)
        assertEquals(
            "unsupported.clip.destination_read.pending_task8:darken",
            result.diagnostics.entries.single().reason,
        )
    }

    @Test
    fun `unsupported clear and color dodge never default to src over`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.COLOR_DODGE).forEach { blendMode ->
            val surface = Surface(16, 16)
            surface.canvas {
                drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = blendMode))
            }
            val result = surface.render()

            assertEquals(0, result.stats.opsDispatched, blendMode.name)
            assertEquals(1, result.diagnostics.fatalCount, blendMode.name)
            assertEquals(
                "unsupported.clip.blend_unsupported:${blendMode.name.lowercase()}",
                result.diagnostics.entries.single().reason,
            )
        }
    }

    @Test
    fun `core complex clip routes use source then composite without a direct bypass`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val ops = listOf(
            DisplayOp.DrawRect(Rect(2f, 2f, 8f, 8f), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawRRect(RRect(Rect(20f, 2f, 28f, 10f), radius = 2f), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawPath(
                Path { moveTo(2f, 22f); lineTo(10f, 22f); lineTo(6f, 30f); close() },
                Paint.fill(Color.RED),
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawImage(
                bluePixel(),
                Rect(0f, 0f, 1f, 1f),
                Rect(22f, 22f, 30f, 30f),
                Paint(),
                Matrix33.identity(),
                clip,
            ),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(ops),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            routeTrace = trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(4, trace.logicalDrawCount)
        assertEquals(4, trace.sourceThenCompositeCount)
        assertEquals(0, trace.directComplexClipDispatches)
    }

    @Test
    fun `text atlas and textured vertices each use one complex clip source composite`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val image = bgraBluePixel()
        val ops = listOf(
            DisplayOp.DrawText(
                blob = Font(typeface, 12f).toTextBlob("A", 4f, 16f),
                x = 0f,
                y = 0f,
                paint = Paint.fill(Color.RED),
                transform = Matrix33.identity(),
                clip = clip,
            ),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(Point(0f, 0f), Point(8f, 0f), Point(0f, 8f)),
                    texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
                ),
                paint = Paint.fill(Color.WHITE).copy(shader = Shader.Image(image)),
                transform = Matrix33.translate(20f, 20f),
                clip = clip,
            ),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(ops),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            routeTrace = trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(2, trace.logicalDrawCount)
        assertEquals(2, trace.sourceThenCompositeCount)
        assertEquals(0, trace.directComplexClipDispatches)
        assertPixel(result.pixels, 21, 21, Color.BLUE)
    }

    @Test
    fun `outlined multi glyph text preserves every source glyph under a complex clip`() {
        requireWebGpu()
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        Font(typeface, 16f).toTextBlob("AA", 2f, 18f),
                        0f,
                        0f,
                        Paint.stroke(Color.RED, 1f),
                        Matrix33.identity(),
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleIn(result.pixels, 32, 2..10, 4..19)
        assertVisibleIn(result.pixels, 32, 14..24, 4..19)
    }

    @Test
    fun `complex clip source retains every point subpass`() {
        requireWebGpu()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawPoints(
                        PointMode.POINTS,
                        listOf(Point(4f, 4f), Point(20f, 4f)),
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleAt(result.pixels, 32, 4, 4)
        assertVisibleAt(result.pixels, 32, 20, 4)
    }

    @Test
    fun `complex clip source retains every image cell subpass`() {
        requireWebGpu()
        val image = opaqueImage(size = 3)

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawImageNine(
                        image,
                        Rect(1f, 1f, 2f, 2f),
                        Rect(2f, 2f, 14f, 14f),
                        null,
                        Matrix33.identity(),
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleAt(result.pixels, 32, 3, 3)
        assertVisibleAt(result.pixels, 32, 12, 12)
    }

    @Test
    fun `complex clip source retains every atlas sprite subpass`() {
        requireWebGpu()
        val image = opaqueImage(size = 3)

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawAtlas(
                        atlas = image,
                        transforms = listOf(Matrix33.translate(2f, 20f), Matrix33.translate(18f, 20f)),
                        texRects = listOf(Rect(0f, 0f, 3f, 3f), Rect(0f, 0f, 3f, 3f)),
                        colors = null,
                        blendMode = BlendMode.SRC_OVER,
                        paint = null,
                        transform = Matrix33.identity(),
                        clip = complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleAt(result.pixels, 32, 3, 21)
        assertVisibleAt(result.pixels, 32, 19, 21)
    }

    @Test
    fun `mesh program is refused rather than rendered as plain vertices`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val effect = simpleRuntimeEffect()
        val mesh = Mesh(
            vertices = Vertices(
                VertexMode.TRIANGLES,
                listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
            ),
            program = MeshProgram(effect),
            bounds = Rect(2f, 2f, 8f, 8f),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(DisplayOp.DrawMesh(mesh, Paint.fill(Color.RED), null, Matrix33.identity(), clip)),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            trace,
        )

        assertEquals(1, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.entries.any { it.reason == "unsupported.mesh.program" })
        assertEquals(0, trace.logicalDrawCount)
        assertTrue(result.diagnostics.entries.none { it.reason == "clip_mask_acquire" })
    }

    @Test
    fun `nested picture paint and clip are refused before masked source acquisition`() {
        requireWebGpu()
        val child = Picture(
            Rect(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawRect(Rect(1f, 1f, 7f, 7f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)),
        )
        val outerClip = complexFullClip()
        val invalidPictures = listOf(
            Picture(
                Rect(0f, 0f, 8f, 8f),
                listOf(DisplayOp.DrawPicture(child, Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)),
            ) to "unsupported.picture.nested_paint",
            Picture(
                Rect(0f, 0f, 8f, 8f),
                listOf(DisplayOp.DrawPicture(child, null, Matrix33.identity(), outerClip)),
            ) to "unsupported.picture.nested_clip",
        )

        invalidPictures.forEach { (picture, expectedReason) ->
            val trace = GPUClipRouteTrace()
            val result = renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(DisplayOp.DrawPicture(picture, null, Matrix33.identity(), outerClip)),
                ),
                32,
                32,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                trace,
            )

            assertTrue(result.diagnostics.entries.any { it.reason == expectedReason }, result.diagnostics.entries.toString())
            assertEquals(0, trace.logicalDrawCount)
            assertTrue(result.diagnostics.entries.none { it.reason == "clip_mask_acquire" })
        }
    }

    @Test
    fun `vertices diagnostics retain their logical operation name`() {
        requireWebGpu()
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
            texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
        )
        val expectedOperations = listOf(
            DisplayOp.DrawVertices(vertices, Paint.fill(Color.WHITE), Matrix33.identity(), complexFullClip()) to "drawVertices",
            DisplayOp.DrawMesh(
                Mesh(vertices, bounds = Rect(2f, 2f, 8f, 8f)),
                Paint.fill(Color.WHITE),
                null,
                Matrix33.identity(),
                complexFullClip(),
            ) to "drawMesh",
        )

        expectedOperations.forEach { (op, expectedOperation) ->
            val result = renderViaGpu(
                StaticDisplayListBuffer(listOf(op)),
                32,
                32,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )

            assertTrue(
                result.diagnostics.entries.any {
                    it.operation == expectedOperation && it.reason == "gpu_textured_vertices_no_image_shader"
                },
                result.diagnostics.entries.toString(),
            )
        }
    }

    @Test
    fun `textured vertices refuse perspective and non triangle list source encoders`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val triangle = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f))
        val uvs = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f))
        val paint = Paint.fill(Color.WHITE).copy(shader = Shader.Image(bgraBluePixel()))

        val perspective = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawVertices(
                        Vertices(VertexMode.TRIANGLES, triangle, texCoords = uvs),
                        paint,
                        Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f),
                        clip,
                    ),
                ),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )
        assertTrue(perspective.diagnostics.entries.any { it.reason == "unsupported.vertices.perspective_transform" })

        val strip = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawVertices(
                        Vertices(VertexMode.TRIANGLE_STRIP, triangle, texCoords = uvs),
                        paint,
                        Matrix33.identity(),
                        clip,
                    ),
                ),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )
        assertTrue(strip.diagnostics.entries.any { it.reason == "unsupported.vertices.textured_mode" })
    }

    @Test
    fun `non textured vertices with colors or indices are refused instead of flattened`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f)),
            colors = listOf(Color.RED, Color.GREEN, Color.BLUE),
            indices = listOf(0, 1, 2),
        )

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(DisplayOp.DrawVertices(vertices, Paint.fill(Color.WHITE), Matrix33.identity(), clip)),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertTrue(result.diagnostics.entries.any { it.reason == "unsupported.vertices.colors_or_indices" })
    }

    @Test
    fun `textured vertices with invalid indices are refused before the backend buffer`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f)),
            texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
            indices = listOf(0, 1, 3),
        )

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawVertices(
                        vertices,
                        Paint.fill(Color.WHITE).copy(shader = Shader.Image(bgraBluePixel())),
                        Matrix33.identity(),
                        clip,
                    ),
                ),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertTrue(result.diagnostics.entries.any { it.reason == "unsupported.vertices.indices" })
    }

    @Test
    fun `picture paint and captured child clips are explicitly refused`() {
        requireWebGpu()
        val outerClip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(Rect(0f, 0f, 8f, 8f)).drawRect(Rect(1f, 1f, 7f, 7f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        val paintResult = renderPictureWithClip(picture, Paint.fill(Color.RED), outerClip)
        assertTrue(paintResult.diagnostics.entries.any { it.reason == "unsupported.picture.paint" })

        val childClipResult = renderPictureWithClip(picture, null, outerClip)
        assertTrue(childClipResult.diagnostics.entries.any { it.reason == "unsupported.picture.nested_clip" })
    }

    @Test
    fun `outline text without a typeface reports a stable degradation without a source`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        TextBlob(emptyList()),
                        0f,
                        0f,
                        Paint.stroke(Color.RED, 1f),
                        Matrix33.identity(),
                        clip,
                    ),
                ),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.entries.any { it.reason == "unsupported.text.outline.no_typeface" })
        assertEquals(0, trace.logicalDrawCount)
    }

    @Test
    fun `empty text does not produce a complex clip source composite`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true),
            ),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        TextBlob(emptyList()),
                        0f,
                        0f,
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        clip,
                    ),
                ),
            ),
            width = 16,
            height = 16,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            routeTrace = trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(0, trace.logicalDrawCount)
        assertEquals(0, trace.sourceThenCompositeCount)
    }

    @Test
    fun `remaining high level GPU routes use one source composite or a stable refusal`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val image = opaqueImage(size = 3)
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
        )
        val picture = Picture(
            Rect(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect(2f, 2f, 8f, 8f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)),
        )
        val ops = listOf(
            DisplayOp.DrawPoints(PointMode.POINTS, listOf(Point(3f, 3f), Point(6f, 6f)), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawDRRect(
                RRect(Rect(2f, 20f, 10f, 28f), radius = 1f),
                RRect(Rect(4f, 22f, 8f, 26f), radius = 1f),
                Paint.fill(Color.RED),
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawImageNine(image, Rect(1f, 1f, 2f, 2f), Rect(12f, 2f, 22f, 12f), null, Matrix33.identity(), clip),
            DisplayOp.DrawImageLattice(
                image,
                Lattice(xDivs = listOf(1, 2), yDivs = listOf(1, 2)),
                Rect(12f, 14f, 22f, 24f),
                null,
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawAtlas(
                atlas = image,
                transforms = listOf(Matrix33.identity(), Matrix33.identity()),
                texRects = listOf(Rect(0f, 0f, 3f, 3f), Rect(0f, 0f, 3f, 3f)),
                colors = null,
                blendMode = BlendMode.SRC_OVER,
                paint = null,
                transform = Matrix33.identity(),
                clip = clip,
            ),
            DisplayOp.DrawPicture(picture, null, Matrix33.identity(), clip),
            DisplayOp.DrawMesh(Mesh(triangle, bounds = Rect(2f, 12f, 8f, 18f)), Paint.fill(Color.RED), null, Matrix33.identity(), clip),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(ops),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            routeTrace = trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(7, trace.logicalDrawCount, result.diagnostics.entries.toString())
        assertEquals(7, trace.sourceThenCompositeCount, result.diagnostics.entries.toString())
        assertEquals(0, trace.directComplexClipDispatches)
        assertTrue(
            result.diagnostics.entries.none { it.reason.startsWith("unsupported.gpu.route.unclassified") },
            result.diagnostics.entries.toString(),
        )
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, expected: Color) {
        val offset = (y * 32 + x) * 4
        val actual = listOf(
            pixels[offset].toInt() and 0xff,
            pixels[offset + 1].toInt() and 0xff,
            pixels[offset + 2].toInt() and 0xff,
            pixels[offset + 3].toInt() and 0xff,
        )
        assertTrue((pixels[offset].toInt() and 0xff) >= expected.redByte * 200 / 255)
        assertTrue((pixels[offset + 1].toInt() and 0xff) >= expected.greenByte * 200 / 255)
        assertTrue((pixels[offset + 2].toInt() and 0xff) >= expected.blueByte * 200 / 255, "actual=$actual")
        assertTrue((pixels[offset + 3].toInt() and 0xff) >= expected.alphaByte * 200 / 255)
    }

    private fun assertTransparent(pixels: UByteArray, x: Int, y: Int) {
        val offset = (y * 32 + x) * 4
        assertEquals(0, pixels[offset + 3].toInt() and 0xff)
    }

    private fun assertVisibleAt(pixels: UByteArray, width: Int, x: Int, y: Int) {
        val alpha = pixels[(y * width + x) * 4 + 3].toInt() and 0xff
        assertTrue(alpha >= 200, "expected visible pixel at ($x, $y)")
    }

    private fun assertVisibleIn(pixels: UByteArray, width: Int, xs: IntRange, ys: IntRange) {
        assertTrue(
            ys.any { y -> xs.any { x -> (pixels[(y * width + x) * 4 + 3].toInt() and 0xff) >= 200 } },
            "expected a visible pixel in x=$xs, y=$ys",
        )
    }

    private fun alphaAt(pixels: UByteArray, x: Int, y: Int): Int =
        pixels[(y * 16 + x) * 4 + 3].toInt() and 0xff

    private fun renderMaskedRect(blendMode: BlendMode) = Surface(16, 16).run {
        canvas {
            save()
            clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = blendMode))
            restore()
        }
        render()
    }

    private fun complexFullClip(): ClipStack = ClipStack.Complex(
        listOf(ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)),
    )

    private fun bluePixel(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()),
        colorType = ColorType.RGBA_8888,
        sourceId = "clip-blue-pixel",
    )

    private fun bgraBluePixel(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0xff.toByte(), 0, 0, 0xff.toByte()),
        colorType = ColorType.BGRA_8888,
        sourceId = "clip-bgra-blue-pixel",
    )

    private fun opaqueImage(size: Int): Image = Image.fromPixels(
        width = size,
        height = size,
        pixels = ByteArray(size * size * 4) { index -> if (index % 4 == 3) 0xff.toByte() else 0x7f },
        colorType = ColorType.RGBA_8888,
        sourceId = "clip-opaque-$size",
    )

    private fun simpleRuntimeEffect(): RuntimeEffect {
        RuntimeEffectWgsl4kWiring.install()
        return RuntimeEffect.compile(
            """
                @fragment
                fn main() -> @location(0) vec4f {
                    return vec4f(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent(),
        ).getOrThrow()
    }

    private fun renderPictureWithClip(picture: Picture, paint: Paint?, clip: ClipStack) = renderViaGpu(
        StaticDisplayListBuffer(
            listOf(DisplayOp.DrawPicture(picture, paint, Matrix33.identity(), clip)),
        ),
        16,
        16,
        PixelFormat.RGBA8,
        RenderConfig.DEFAULT,
    )

    private class StaticDisplayListBuffer(
        private val operations: List<DisplayOp>,
    ) : DisplayListBuffer {
        override fun append(op: DisplayOp): Nothing = error("Static buffer is immutable")

        override fun ops(): List<DisplayOp> = operations
    }
}
