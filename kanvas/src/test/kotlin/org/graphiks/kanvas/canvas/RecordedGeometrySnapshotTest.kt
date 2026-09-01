package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Path1DStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class RecordedGeometrySnapshotTest {
    @Test
    fun `mutating a terminal perspective clip source cannot change a later draw`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        val canvas = surface.canvas()
        canvas.setMatrix(Matrix3x3F32(persp0 = 0.01f))
        canvas.clipPath(source)

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))
        canvas.drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red))

        val recorded = assertIs<DisplayOp.DrawRect>(surface.snapshotOps().last())
        val clip = assertIs<ClipStack.Complex>(recorded.clip)
        val pathClip = assertIs<ClipStackOp.PathOp>(clip.ops.single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), pathClip.path.computeBounds())
    }

    @Test
    fun `working color space shader snapshots coord clamp geometry at append and ops`() {
        val surface = Surface(32, 32)
        val subset = RectF32.ofLTRB(1f, 2f, 9f, 10f)
        val paint = Paint(
            shader = Shader.WithWorkingColorSpace(
                Shader.CoordClamp(Shader.SolidColor(ColorARGB.Red), subset),
                ColorSpaceInterpolation.LINEAR,
            ),
        )
        surface.canvas().drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), paint)

        subset.setLTRB(20f, 20f, 30f, 30f)

        val first = assertIs<DisplayOp.DrawRect>(surface.snapshotOps().single())
        val firstShader = assertIs<Shader.WithWorkingColorSpace>(first.paint.shader)
        val firstClamp = assertIs<Shader.CoordClamp>(firstShader.shader)
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), firstClamp.subset)

        firstClamp.subset.setLTRB(40f, 40f, 50f, 50f)

        val later = assertIs<DisplayOp.DrawRect>(surface.snapshotOps().single())
        val laterShader = assertIs<Shader.WithWorkingColorSpace>(later.paint.shader)
        val laterClamp = assertIs<Shader.CoordClamp>(laterShader.shader)
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), laterClamp.subset)
    }

    @Test
    fun `text blob collections are frozen at append`() {
        val surface = Surface(32, 32)
        val glyphs = mutableListOf(1.toUShort())
        val positions = mutableListOf(Point2F32(3f, 4f))
        val glyphRuns = mutableListOf(KanvasGlyphRun(glyphs, positions, fontSize = 12f))
        val variationCoordinates = mutableMapOf("wght" to 400f)
        val blob = TextBlob(glyphRuns, variationCoordinates = variationCoordinates)
        surface.canvas().drawText(blob, 0f, 0f, Paint.fill(ColorARGB.Red))

        glyphs[0] = 2.toUShort()
        positions[0] = Point2F32(30f, 40f)
        glyphRuns += KanvasGlyphRun(listOf(3.toUShort()), listOf(Point2F32(5f, 6f)))
        variationCoordinates["wght"] = 700f

        val recorded = assertIs<DisplayOp.DrawText>(surface.snapshotOps().single()).blob
        assertEquals(1, recorded.glyphRuns.size)
        assertEquals(listOf(1.toUShort()), recorded.glyphRuns.first().glyphs)
        assertEquals(listOf(Point2F32(3f, 4f)), recorded.glyphRuns.first().positions)
        assertEquals(mapOf("wght" to 400f), recorded.variationCoordinates)
    }

    @Test
    fun `text blob snapshots share one internal copy while isolating the mutable source`() {
        val surface = Surface(32, 32)
        val glyphs = mutableListOf(1.toUShort())
        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphs, listOf(Point2F32(3f, 4f)), fontSize = 12f)),
            variationCoordinates = mutableMapOf("wght" to 400f),
        )
        surface.canvas().drawText(blob, 0f, 0f, Paint.fill(ColorARGB.Red))
        surface.canvas().drawText(blob, 0f, 10f, Paint.fill(ColorARGB.Red))

        glyphs[0] = 2.toUShort()

        val snapshot = surface.snapshotOps()
        val first = assertIs<DisplayOp.DrawText>(snapshot[0]).blob
        val second = assertIs<DisplayOp.DrawText>(snapshot[1]).blob
        assertSame(first, second)
        assertEquals(listOf(1.toUShort()), first.glyphRuns.single().glyphs)
    }

    @Test
    fun `picture forEachOp exposes copies rather than recorded geometry`() {
        val childRecorder = PictureRecorder()
        childRecorder.beginRecording(RectF32.ofLTRB(0f, 0f, 16f, 16f)).drawPath(
            Path().addRect(RectF32.ofLTRB(2f, 3f, 8f, 9f)),
            Paint.fill(ColorARGB.Blue),
        )
        val child = childRecorder.finishRecordingAsPicture()
        val recorder = PictureRecorder()
        val canvas = recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 16f, 16f))
        canvas.drawRect(RectF32.ofLTRB(1f, 2f, 9f, 10f), Paint.fill(ColorARGB.Red))
        canvas.drawPicture(child)
        val picture = recorder.finishRecordingAsPicture()

        picture.forEachOp(nested = true) { op ->
            when (op) {
                is DisplayOp.DrawRect -> op.rect.setLTRB(20f, 20f, 30f, 30f)
                is DisplayOp.DrawPath -> op.path.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))
                else -> Unit
            }
        }

        val reportedBounds = mutableListOf<RectF32>()
        picture.forEachOp(nested = true) { op ->
            when (op) {
                is DisplayOp.DrawRect -> reportedBounds += op.rect
                is DisplayOp.DrawPath -> reportedBounds += requireNotNull(op.path.computeBounds())
                else -> Unit
            }
        }
        assertEquals(
            listOf(
                RectF32.ofLTRB(1f, 2f, 9f, 10f),
                RectF32.ofLTRB(2f, 3f, 8f, 9f),
            ),
            reportedBounds,
        )
    }

    @Test
    fun `mutating picture cull bounds after recording begins cannot change the picture`() {
        val cullBounds = RectF32.ofLTRB(1f, 2f, 9f, 10f)
        val recorder = PictureRecorder()
        recorder.beginRecording(cullBounds)

        cullBounds.setLTRB(20f, 20f, 30f, 30f)

        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), recorder.finishRecordingAsPicture().cullRect)
    }

    @Test
    fun `mutating a source path after draw cannot change recorded operations`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        surface.canvas().drawPath(source, Paint.fill(ColorARGB.Red))

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), recorded.path.computeBounds())
    }

    @Test
    fun `mutating a clip path returned from an earlier snapshot cannot change later snapshots`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        surface.canvas().clipPath(source)

        val firstSnapshot = assertIs<DisplayOp.SetClip>(surface.snapshotOps().single())
        val firstClip = assertIs<ClipStack.Complex>(firstSnapshot.clip)
        val firstPath = assertIs<ClipStackOp.PathOp>(firstClip.ops.single()).path
        firstPath.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.SetClip>(surface.snapshotOps().single())
        val complexClip = assertIs<ClipStack.Complex>(recorded.clip)
        val pathClip = assertIs<ClipStackOp.PathOp>(complexClip.ops.single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), pathClip.path.computeBounds())
    }

    @Test
    fun `mutating a path effect path cannot change recorded operations`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        val paint = Paint(
            color = ColorARGB.Red,
            pathEffect = PathEffect.Path1D(source, advance = 4f, phase = 0f, style = Path1DStyle.ROTATE),
        )
        surface.canvas().drawRect(RectF32.ofLTRB(0f, 0f, 16f, 16f), paint)

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val recorded = assertIs<DisplayOp.DrawRect>(surface.snapshotOps().single())
        val effect = assertIs<PathEffect.Path1D>(recorded.paint.pathEffect)
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), effect.path.computeBounds())
    }

    @Test
    fun `mutating a path returned from an earlier snapshot cannot change later snapshots`() {
        val surface = Surface(32, 32)
        surface.canvas().drawPath(
            Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f)),
            Paint.fill(ColorARGB.Red),
        )

        val firstSnapshot = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        firstSnapshot.path.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))

        val laterSnapshot = assertIs<DisplayOp.DrawPath>(surface.snapshotOps().single())
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), laterSnapshot.path.computeBounds())
    }

    @Test
    fun `rectangles and rounded rectangles are frozen at append`() {
        val surface = Surface(32, 32)
        val rect = RectF32.ofLTRB(1f, 2f, 9f, 10f)
        val rrectBounds = RectF32.ofLTRB(2f, 3f, 8f, 9f)
        val rrect = RRectF32.of(rrectBounds, radius = 2f)
        surface.canvas().drawRect(rect, Paint.fill(ColorARGB.Red))
        surface.canvas().drawRRect(rrect, Paint.fill(ColorARGB.Blue))

        rect.setLTRB(20f, 20f, 30f, 30f)
        rrectBounds.setLTRB(20f, 20f, 30f, 30f)

        val snapshot = surface.snapshotOps()
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), assertIs<DisplayOp.DrawRect>(snapshot[0]).rect)
        assertEquals(RectF32.ofLTRB(2f, 3f, 8f, 9f), assertIs<DisplayOp.DrawRRect>(snapshot[1]).rrect.rect)
    }

    @Test
    fun `point and vertex collections are frozen at append`() {
        val surface = Surface(32, 32)
        val points = mutableListOf(Point2F32(1f, 2f))
        val positions = mutableListOf(Point2F32(3f, 4f), Point2F32(5f, 6f), Point2F32(7f, 8f))
        val vertices = Vertices(VertexMode.TRIANGLES, positions)
        surface.canvas().drawPoints(PointMode.POINTS, points, Paint.fill(ColorARGB.Red))
        surface.canvas().drawVertices(vertices, Paint.fill(ColorARGB.Blue))
        surface.canvas().drawMesh(
            Mesh(vertices, bounds = RectF32.ofLTRB(3f, 4f, 7f, 8f)),
            Paint.fill(ColorARGB.Green),
        )

        points[0] = Point2F32(20f, 20f)
        positions[0] = Point2F32(30f, 30f)

        val draws = surface.snapshotOps()
        assertEquals(listOf(Point2F32(1f, 2f)), assertIs<DisplayOp.DrawPoints>(draws[0]).points)
        assertEquals(listOf(Point2F32(3f, 4f), Point2F32(5f, 6f), Point2F32(7f, 8f)), assertIs<DisplayOp.DrawVertices>(draws[1]).vertices.positions)
        assertEquals(listOf(Point2F32(3f, 4f), Point2F32(5f, 6f), Point2F32(7f, 8f)), assertIs<DisplayOp.DrawVertices>(draws[2]).vertices.positions)
    }

    @Test
    fun `lattice and atlas rectangles are frozen at append`() {
        val surface = Surface(32, 32)
        val image = Image.placeholder(1, 1)
        val latticeRect = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        val lattice = Lattice(xDivs = listOf(1), yDivs = listOf(1), rects = mutableListOf(latticeRect))
        val latticeDst = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val atlasRects = mutableListOf(RectF32.ofLTRB(2f, 3f, 4f, 5f))
        surface.canvas().drawImageLattice(image, lattice, latticeDst, Paint.fill(ColorARGB.Red))
        surface.canvas().drawAtlas(image, mutableListOf(Matrix3x3F32.Identity), atlasRects)

        latticeRect.setLTRB(20f, 20f, 30f, 30f)
        latticeDst.setLTRB(20f, 20f, 30f, 30f)
        atlasRects[0].setLTRB(20f, 20f, 30f, 30f)

        val snapshot = surface.snapshotOps()
        val latticeOp = assertIs<DisplayOp.DrawImageLattice>(snapshot[0])
        assertEquals(RectF32.ofLTRB(1f, 2f, 3f, 4f), latticeOp.lattice.rects!!.single())
        assertEquals(RectF32.ofLTRB(0f, 0f, 8f, 8f), latticeOp.dst)
        assertEquals(RectF32.ofLTRB(2f, 3f, 4f, 5f), assertIs<DisplayOp.DrawAtlas>(snapshot[1]).texRects.single())
    }

    @Test
    fun `path effect path2d and layer bounds are frozen at append`() {
        val surface = Surface(32, 32)
        val source = Path().addRect(RectF32.ofLTRB(1f, 2f, 9f, 10f))
        val layerBounds = RectF32.ofLTRB(2f, 3f, 8f, 9f)
        surface.canvas().drawRect(
            RectF32.ofLTRB(0f, 0f, 16f, 16f),
            Paint(pathEffect = PathEffect.Path2D(Matrix3x3F32.Identity, source)),
        )
        surface.canvas().saveLayer(layerBounds)

        source.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))
        layerBounds.setLTRB(20f, 20f, 30f, 30f)

        val snapshot = surface.snapshotOps()
        val pathEffect = assertIs<PathEffect.Path2D>(assertIs<DisplayOp.DrawRect>(snapshot[0]).paint.pathEffect)
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), pathEffect.path.computeBounds())
        assertEquals(RectF32.ofLTRB(2f, 3f, 8f, 9f), assertIs<DisplayOp.BeginLayer>(snapshot[1]).bounds)
    }

    @Test
    fun `ops snapshots retain rect rrect path effect and layer geometry after a prior snapshot mutates them`() {
        val surface = Surface(32, 32)
        val pathEffectPath = Path().addRect(RectF32.ofLTRB(3f, 4f, 7f, 8f))
        surface.canvas().drawRect(RectF32.ofLTRB(1f, 2f, 9f, 10f), Paint.fill(ColorARGB.Red))
        surface.canvas().drawRRect(
            RRectF32.of(RectF32.ofLTRB(2f, 3f, 8f, 9f), radius = 2f),
            Paint.fill(ColorARGB.Blue),
        )
        surface.canvas().drawRect(
            RectF32.ofLTRB(0f, 0f, 16f, 16f),
            Paint(pathEffect = PathEffect.Path2D(Matrix3x3F32.Identity, pathEffectPath)),
        )
        surface.canvas().saveLayer(RectF32.ofLTRB(4f, 5f, 12f, 13f))

        val first = surface.snapshotOps()
        assertIs<DisplayOp.DrawRect>(first[0]).rect.setLTRB(20f, 20f, 30f, 30f)
        assertIs<DisplayOp.DrawRRect>(first[1]).rrect.rect.setLTRB(20f, 20f, 30f, 30f)
        val firstEffect = assertIs<PathEffect.Path2D>(assertIs<DisplayOp.DrawRect>(first[2]).paint.pathEffect)
        firstEffect.path.addRect(RectF32.ofLTRB(20f, 20f, 30f, 30f))
        requireNotNull(assertIs<DisplayOp.BeginLayer>(first[3]).bounds).setLTRB(20f, 20f, 30f, 30f)

        val later = surface.snapshotOps()
        assertEquals(RectF32.ofLTRB(1f, 2f, 9f, 10f), assertIs<DisplayOp.DrawRect>(later[0]).rect)
        assertEquals(RectF32.ofLTRB(2f, 3f, 8f, 9f), assertIs<DisplayOp.DrawRRect>(later[1]).rrect.rect)
        val laterEffect = assertIs<PathEffect.Path2D>(assertIs<DisplayOp.DrawRect>(later[2]).paint.pathEffect)
        assertEquals(RectF32.ofLTRB(3f, 4f, 7f, 8f), laterEffect.path.computeBounds())
        assertEquals(RectF32.ofLTRB(4f, 5f, 12f, 13f), assertIs<DisplayOp.BeginLayer>(later[3]).bounds)
    }

    @Test
    fun `ops snapshots retain points vertices and a programmed mesh after a prior snapshot mutates them`() {
        val surface = Surface(32, 32)
        val points = listOf(Point2F32(1f, 2f), Point2F32(2f, 3f))
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            listOf(Point2F32(3f, 4f), Point2F32(5f, 6f), Point2F32(7f, 8f)),
        )
        val mesh = Mesh(
            vertices = vertices,
            program = MeshProgram(compiledMeshEffect()),
            bounds = RectF32.ofLTRB(3f, 4f, 7f, 8f),
        )
        surface.canvas().drawPoints(PointMode.POINTS, points, Paint.fill(ColorARGB.Red))
        surface.canvas().drawVertices(vertices, Paint.fill(ColorARGB.Blue))
        surface.canvas().drawMesh(mesh, Paint.fill(ColorARGB.Green))

        val first = surface.snapshotOps()
        @Suppress("UNCHECKED_CAST")
        (assertIs<DisplayOp.DrawPoints>(first[0]).points as MutableList<Point2F32>)[0] = Point2F32(20f, 20f)
        @Suppress("UNCHECKED_CAST")
        (assertIs<DisplayOp.DrawVertices>(first[1]).vertices.positions as MutableList<Point2F32>)[0] = Point2F32(20f, 20f)
        val firstMesh = assertIs<DisplayOp.DrawMesh>(first[2])
        @Suppress("UNCHECKED_CAST")
        (firstMesh.mesh.vertices.positions as MutableList<Point2F32>)[0] = Point2F32(20f, 20f)
        firstMesh.mesh.bounds.setLTRB(20f, 20f, 30f, 30f)

        val later = surface.snapshotOps()
        assertEquals(listOf(Point2F32(1f, 2f), Point2F32(2f, 3f)), assertIs<DisplayOp.DrawPoints>(later[0]).points)
        val expectedVertices = listOf(Point2F32(3f, 4f), Point2F32(5f, 6f), Point2F32(7f, 8f))
        assertEquals(expectedVertices, assertIs<DisplayOp.DrawVertices>(later[1]).vertices.positions)
        val laterMesh = assertIs<DisplayOp.DrawMesh>(later[2])
        assertEquals(expectedVertices, laterMesh.mesh.vertices.positions)
        assertEquals(RectF32.ofLTRB(3f, 4f, 7f, 8f), laterMesh.mesh.bounds)
    }

    @Test
    fun `ops snapshots retain lattice and atlas geometry after a prior snapshot mutates them`() {
        val surface = Surface(32, 32)
        val image = Image.placeholder(1, 1)
        surface.canvas().drawImageLattice(
            image,
            Lattice(xDivs = listOf(1), yDivs = listOf(1), rects = listOf(RectF32.ofLTRB(1f, 2f, 3f, 4f))),
            RectF32.ofLTRB(0f, 0f, 8f, 8f),
        )
        surface.canvas().drawAtlas(
            image,
            transforms = listOf(Matrix3x3F32(tx = 2f, ty = 3f), Matrix3x3F32(tx = 4f, ty = 5f)),
            texRects = listOf(RectF32.ofLTRB(2f, 3f, 4f, 5f), RectF32.ofLTRB(4f, 5f, 6f, 7f)),
        )

        val first = surface.snapshotOps()
        val firstLattice = assertIs<DisplayOp.DrawImageLattice>(first[0])
        @Suppress("UNCHECKED_CAST")
        (requireNotNull(firstLattice.lattice.rects) as MutableList<RectF32>)[0] = RectF32.ofLTRB(20f, 20f, 30f, 30f)
        firstLattice.dst.setLTRB(20f, 20f, 30f, 30f)
        val firstAtlas = assertIs<DisplayOp.DrawAtlas>(first[1])
        @Suppress("UNCHECKED_CAST")
        (firstAtlas.transforms as MutableList<Matrix3x3F32>)[0] = Matrix3x3F32(tx = 20f, ty = 30f)
        @Suppress("UNCHECKED_CAST")
        (firstAtlas.texRects as MutableList<RectF32>)[0] = RectF32.ofLTRB(20f, 20f, 30f, 30f)

        val later = surface.snapshotOps()
        val laterLattice = assertIs<DisplayOp.DrawImageLattice>(later[0])
        assertEquals(RectF32.ofLTRB(1f, 2f, 3f, 4f), laterLattice.lattice.rects!!.single())
        assertEquals(RectF32.ofLTRB(0f, 0f, 8f, 8f), laterLattice.dst)
        val laterAtlas = assertIs<DisplayOp.DrawAtlas>(later[1])
        assertEquals(
            listOf(Matrix3x3F32(tx = 2f, ty = 3f), Matrix3x3F32(tx = 4f, ty = 5f)),
            laterAtlas.transforms,
        )
        assertEquals(
            listOf(RectF32.ofLTRB(2f, 3f, 4f, 5f), RectF32.ofLTRB(4f, 5f, 6f, 7f)),
            laterAtlas.texRects,
        )
    }

    @Test
    fun `nested pictures retain geometry through a bounded recording chain`() {
        val leafRecorder = PictureRecorder()
        leafRecorder.beginRecording(RectF32.ofLTRB(0f, 0f, 16f, 16f)).drawRect(
            RectF32.ofLTRB(1f, 2f, 9f, 10f),
            Paint.fill(ColorARGB.Red),
        )
        var picture = leafRecorder.finishRecordingAsPicture()
        repeat(12) {
            val recorder = PictureRecorder()
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 16f, 16f)).drawPicture(picture)
            picture = recorder.finishRecordingAsPicture()
        }

        val recordedBounds = mutableListOf<RectF32>()
        picture.forEachOp(nested = true) { op ->
            if (op is DisplayOp.DrawRect) recordedBounds += op.rect
        }
        assertEquals(listOf(RectF32.ofLTRB(1f, 2f, 9f, 10f)), recordedBounds)
    }

    private fun compiledMeshEffect(): RuntimeEffect {
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
}
