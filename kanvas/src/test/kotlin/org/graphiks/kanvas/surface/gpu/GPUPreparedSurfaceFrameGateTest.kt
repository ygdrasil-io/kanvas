package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat as CanonicalGPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedSurfaceFrameGateTest {
    @Test
    fun `DrawText enters prepared candidate`() {
        assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(
                listOf(DisplayOp.DrawText(TextBlob(emptyList()), 0f, 0f, PAINT, MATRIX, CLIP)),
                RenderConfig.DEFAULT,
            ),
        )
    }

    @Test
    fun `DrawVertices and DrawMesh enter the prepared candidate`() {
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
        )
        listOf(
            DisplayOp.DrawVertices(vertices, PAINT, MATRIX, CLIP),
            DisplayOp.DrawMesh(Mesh(vertices, bounds = RECT), PAINT, null, MATRIX, CLIP),
        ).forEach { operation ->
            val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
                GPUPreparedSurfaceFrameGate.classify(listOf(operation), RenderConfig.DEFAULT),
                operation::class.simpleName,
            )
            assertEquals(listOf(operation), candidate.operations)
        }
    }

    @Test
    fun `all display op variants have one exact whole frame classification`() {
        val fixtures = displayOpFixtures()

        assertEquals(22, fixtures.size)
        assertEquals(22, fixtures.map { it.operation::class.simpleName }.distinct().size)
        fixtures.forEach { fixture ->
            when (fixture.expected) {
                Expected.Candidate -> {
                    val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
                        GPUPreparedSurfaceFrameGate.classify(listOf(fixture.operation), RenderConfig.DEFAULT),
                    )
                    assertEquals(listOf(fixture.operation), candidate.operations)
                    assertEquals(RenderConfig.DEFAULT, candidate.config)
                    assertEquals(
                        RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
                        candidate.color,
                    )
                }
                is Expected.Legacy -> {
                    val legacy = assertIs<GPUPreparedSurfaceEligibility.Legacy>(
                        GPUPreparedSurfaceFrameGate.classify(listOf(fixture.operation), RenderConfig.DEFAULT),
                    )
                    assertEquals(fixture.expected.code, legacy.code)
                    assertEquals(fixture.expected.operationIndex, legacy.operationIndex)
                }
            }
        }
    }

    @Test
    fun `empty and state only frames use the stable empty frame diagnostic`() {
        val stateOnly = listOf(
            DisplayOp.SetTransform(Matrix33.translate(1f, 2f)),
            DisplayOp.SetClip(ClipStack.WideOpen),
            DisplayOp.Annotation(RECT, "key", "value"),
        )

        listOf(emptyList(), stateOnly).forEach { operations ->
            val legacy = assertIs<GPUPreparedSurfaceEligibility.Legacy>(
                GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
            )
            assertEquals("legacy.surface.prepared.empty-frame", legacy.code)
            assertEquals(null, legacy.operationIndex)
        }
    }

    @Test
    fun `first refused operation wins with its exact index family and code`() {
        val visual = visualRect()
        val image = displayOpFixtures().single { it.operation is DisplayOp.DrawImage }.operation
        val text = displayOpFixtures().single { it.operation is DisplayOp.DrawText }.operation
        val flush = DisplayOp.FlushAndSnapshot(RECT)
        val cases = listOf(
            listOf(DisplayOp.SetTransform(Matrix33.identity()), visual, image, flush, text) to
                Expected.Legacy("legacy.surface.prepared.flush-snapshot", 3),
            listOf(visual, flush, image) to
                Expected.Legacy("legacy.surface.prepared.flush-snapshot", 1),
        )

        cases.forEach { (operations, expected) ->
            val legacy = assertIs<GPUPreparedSurfaceEligibility.Legacy>(
                GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
            )
            assertEquals(expected.code, legacy.code)
            assertEquals(expected.operationIndex, legacy.operationIndex)
        }
    }

    @Test
    fun `bgra8 unorm enters the candidate while rgba8 unorm stays refused`() {
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(
                listOf(visualRect()),
                RenderConfig.DEFAULT.copy(gpuColorFormat = GPUColorFormat.BGRA8_UNORM),
            ),
        )
        assertEquals(CanonicalGPUColorFormat.BGRA8Unorm, candidate.color.physicalFormat)
        assertEquals(GPUColorInterpretation.EncodedPremulSrgb, candidate.color.interpretation)

        val legacy = assertIs<GPUPreparedSurfaceEligibility.Legacy>(
            GPUPreparedSurfaceFrameGate.classify(
                listOf(visualRect()),
                RenderConfig.DEFAULT.copy(gpuColorFormat = GPUColorFormat.RGBA8_UNORM),
            ),
        )
        assertEquals("unsupported.surface.gpu-color-format.rgba8-unorm", legacy.code)
        assertEquals(null, legacy.operationIndex)
    }

    @Test
    fun `candidate owns an unmodifiable defensive operation snapshot`() {
        val source = mutableListOf<DisplayOp>(visualRect())
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(source, RenderConfig.DEFAULT),
        )

        source += DisplayOp.Clear(Color.BLUE)

        assertEquals(1, candidate.operations.size)
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (candidate.operations as MutableList<DisplayOp>).add(DisplayOp.Clear(Color.GREEN))
        }
    }

    private data class Fixture(val operation: DisplayOp, val expected: Expected)

    private sealed interface Expected {
        data object Candidate : Expected
        data class Legacy(
            val code: String,
            val operationIndex: Int?,
        ) : Expected
    }

    private fun displayOpFixtures(): List<Fixture> {
        val image = Image.placeholder(2, 2)
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
        )
        val path = Path().addRect(RECT)
        val visual = Expected.Candidate
        fun legacy(code: String) = Expected.Legacy(code, 0)

        return listOf(
            Fixture(visualRect(), visual),
            Fixture(DisplayOp.DrawRRect(RRect(RECT, radius = 1f), PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawPath(path, PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawImage(image, RECT, RECT, null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawText(TextBlob(emptyList()), 0f, 0f, PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.SetTransform(MATRIX), Expected.Legacy(
                "legacy.surface.prepared.empty-frame", null,
            )),
            Fixture(DisplayOp.SetClip(CLIP), Expected.Legacy(
                "legacy.surface.prepared.empty-frame", null,
            )),
            Fixture(DisplayOp.BeginLayer(null, null), visual),
            Fixture(DisplayOp.EndLayer, visual),
            Fixture(DisplayOp.DrawColor(Color.RED, BlendMode.SRC_OVER, MATRIX, CLIP), visual),
            Fixture(DisplayOp.Clear(Color.RED), visual),
            Fixture(DisplayOp.DrawPoint(1f, 1f, PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawPoints(PointMode.POINTS, listOf(Point(1f, 1f)), PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawDRRect(RRect(RECT, radius = 1f), RRect(INNER_RECT, radius = 1f), PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawImageNine(image, INNER_RECT, RECT, null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawImageLattice(image, Lattice(emptyList(), emptyList()), RECT, null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawPicture(Picture(RECT, emptyList()), null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawVertices(vertices, PAINT, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawMesh(Mesh(vertices, bounds = RECT), PAINT, null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.DrawAtlas(image, emptyList(), emptyList(), null, BlendMode.SRC_OVER, null, MATRIX, CLIP), visual),
            Fixture(DisplayOp.Annotation(RECT, "key", "value"), Expected.Legacy(
                "legacy.surface.prepared.empty-frame", null,
            )),
            Fixture(DisplayOp.FlushAndSnapshot(RECT), legacy("legacy.surface.prepared.flush-snapshot")),
        )
    }

    private fun visualRect(): DisplayOp.DrawRect = DisplayOp.DrawRect(RECT, PAINT, MATRIX, CLIP)

    private companion object {
        val RECT = Rect.fromLTRB(0f, 0f, 8f, 8f)
        val INNER_RECT = Rect.fromLTRB(2f, 2f, 6f, 6f)
        val PAINT = Paint.fill(Color.RED)
        val MATRIX = Matrix33.identity()
        val CLIP = ClipStack.WideOpen
    }
}
