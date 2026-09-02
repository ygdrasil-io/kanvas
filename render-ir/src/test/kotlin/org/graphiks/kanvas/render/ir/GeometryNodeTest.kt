package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GeometryNodeTest {
    private val image = ResourceReference(ResourceId("image-1"))

    @Test
    fun `points retain their mode and iterator cannot mutate the node`() {
        val points = GeometryNode.Points.of(PointMode.LINES, listOf(Point2F32(1f, 2f)))
        val identity = points.canonicalId

        assertFailsWith<UnsupportedOperationException> {
            (points.iterator() as MutableIterator<Point2F32>).remove()
        }

        assertEquals(PointMode.LINES, points.mode)
        assertEquals(1, points.pointCount)
        assertEquals(identity, points.canonicalId)
        assertNotEquals(
            points.canonicalId,
            GeometryNode.Points.of(PointMode.POLYGON, listOf(Point2F32(1f, 2f))).canonicalId,
        )
    }

    @Test
    fun `indexed mesh snapshots every source and distinguishes structural list boundaries`() {
        val vertices = mutableListOf(Point2F32(1f, 2f))
        val texCoords = mutableListOf(Point2F32(3f, 4f))
        val colors = mutableListOf(ColorARGB.Red)
        val indices = intArrayOf(0)
        val bounds = RectF32(1f, 2f, 3f, 4f)
        val mesh = GeometryNode.IndexedMesh.of(
            primitiveMode = MeshPrimitiveMode.TRIANGLES,
            vertices = vertices,
            texCoords = texCoords,
            colors = colors,
            indices = indices,
            bounds = bounds,
            program = ResourceReference(ResourceId("program-1")),
        )
        val identity = mesh.canonicalId

        vertices.clear()
        texCoords.clear()
        colors.clear()
        indices[0] = 9
        bounds.setEmpty()
        mesh.copyIndices()!![0] = 8
        mesh.copyBounds()!!.setEmpty()

        assertEquals(1, mesh.vertexCount)
        assertEquals(Point2F32(3f, 4f), mesh.texCoordAt(0))
        assertEquals(ColorARGB.Red, mesh.colorAt(0))
        assertContentEquals(intArrayOf(0), mesh.copyIndices()!!)
        assertEquals(RectF32(1f, 2f, 3f, 4f), mesh.copyBounds())
        assertEquals(identity, mesh.canonicalId)
        assertNotEquals(identity, indexedMesh(primitiveMode = MeshPrimitiveMode.TRIANGLE_STRIP).canonicalId)
        assertNotEquals(identity, indexedMesh(texCoords = listOf(Point2F32(3f, 5f))).canonicalId)
        assertNotEquals(identity, indexedMesh(colors = listOf(ColorARGB.Blue)).canonicalId)
        assertNotEquals(identity, indexedMesh(indices = intArrayOf(1)).canonicalId)
        assertNotEquals(identity, indexedMesh(bounds = RectF32(1f, 2f, 4f, 4f)).canonicalId)
        assertNotEquals(identity, indexedMesh(program = ResourceReference(ResourceId("program-2"))).canonicalId)

        val vertexBits = GeometryNode.IndexedMesh.of(
            MeshPrimitiveMode.TRIANGLES,
            listOf(Point2F32(Float.fromBits(0), Float.fromBits(1))),
            indices = intArrayOf(),
        )
        val indexBits = GeometryNode.IndexedMesh.of(
            MeshPrimitiveMode.TRIANGLES,
            emptyList(),
            indices = intArrayOf(0, 1),
        )
        assertNotEquals(vertexBits.canonicalId, indexBits.canonicalId)
    }

    @Test
    fun `indexed mesh distinguishes absent indices from present empty indices`() {
        val direct = GeometryNode.IndexedMesh.of(
            primitiveMode = MeshPrimitiveMode.TRIANGLES,
            vertices = listOf(Point2F32(1f, 2f)),
            indices = null,
        )
        val empty = GeometryNode.IndexedMesh.of(
            primitiveMode = MeshPrimitiveMode.TRIANGLES,
            vertices = listOf(Point2F32(1f, 2f)),
            indices = intArrayOf(),
        )
        val supplied = intArrayOf(3)
        val indexed = GeometryNode.IndexedMesh.of(
            primitiveMode = MeshPrimitiveMode.TRIANGLES,
            vertices = listOf(Point2F32(1f, 2f)),
            indices = supplied,
        )

        supplied[0] = 7
        indexed.copyIndices()!![0] = 8

        assertEquals(null, direct.copyIndices())
        assertContentEquals(intArrayOf(), empty.copyIndices()!!)
        assertContentEquals(intArrayOf(3), indexed.copyIndices()!!)
        assertNotEquals(direct.canonicalId, empty.canonicalId)
    }

    @Test
    fun `image patch lattice and atlas preserve their full neutral semantics`() {
        val source = RectF32(1f, 2f, 3f, 4f)
        val destination = RectF32(5f, 6f, 7f, 8f)
        val patch = GeometryNode.ImagePatch.of(image, source, destination)
        val xDivs = intArrayOf(1)
        val yDivs = intArrayOf(2)
        val cellRects = mutableListOf(RectF32(0f, 0f, 2f, 2f))
        val lattice = GeometryNode.ImageLattice.of(
            image = image,
            xDivs = xDivs,
            yDivs = yDivs,
            cellRects = cellRects,
            colors = listOf(ColorARGB.Blue),
            flags = listOf(LatticeCellFlag.FIXED_COLOR),
            destination = destination,
            sampling = ImageSampling.Cubic(1f / 3f, 1f / 3f),
        )
        val atlasSource = RectF32(2f, 3f, 4f, 5f)
        val atlas = GeometryNode.Atlas.of(
            image,
            listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32(tx = 2f, ty = 3f), atlasSource, ColorARGB.Green)),
        )
        val latticeIdentity = lattice.canonicalId

        source.setEmpty()
        destination.setEmpty()
        xDivs[0] = 9
        yDivs[0] = 9
        cellRects.single().setEmpty()
        atlasSource.setEmpty()
        lattice.copyCellRects()!!.single().setEmpty()
        atlas.entryAt(0).copySource().setEmpty()

        assertEquals(RectF32(1f, 2f, 3f, 4f), patch.copySource())
        assertContentEquals(intArrayOf(1), lattice.copyXDivs())
        assertContentEquals(intArrayOf(2), lattice.copyYDivs())
        assertEquals(RectF32(0f, 0f, 2f, 2f), lattice.copyCellRects()!!.single())
        assertEquals(ColorARGB.Blue, lattice.colorAt(0))
        assertEquals(LatticeCellFlag.FIXED_COLOR, lattice.flagAt(0))
        assertEquals(ImageSampling.Cubic(1f / 3f, 1f / 3f), lattice.sampling)
        assertEquals(latticeIdentity, lattice.canonicalId)
        assertEquals(RectF32(2f, 3f, 4f, 5f), atlas.entryAt(0).copySource())
        assertEquals(ColorARGB.Green, atlas.entryAt(0).color)
        assertFailsWith<UnsupportedOperationException> {
            (atlas.iterator() as MutableIterator<GeometryNode.AtlasEntry>).remove()
        }
        assertFailsWith<UnsupportedOperationException> {
            (lattice.copyCellRects() as MutableList<RectF32>).clear()
        }
        assertNotEquals(
            lattice.canonicalId,
            GeometryNode.ImageLattice.of(image, intArrayOf(), intArrayOf(1), destination = RectF32(5f, 6f, 7f, 8f)).canonicalId,
        )
        assertNotEquals(
            lattice.canonicalId,
            GeometryNode.ImageLattice.of(
                image, intArrayOf(1), intArrayOf(2), cellRects = listOf(RectF32(0f, 0f, 3f, 2f)),
                colors = listOf(ColorARGB.Blue), flags = listOf(LatticeCellFlag.FIXED_COLOR),
                destination = RectF32(5f, 6f, 7f, 8f), sampling = ImageSampling.Cubic(1f / 3f, 1f / 3f),
            ).canonicalId,
        )
        assertNotEquals(
            lattice.canonicalId,
            GeometryNode.ImageLattice.of(
                image, intArrayOf(1), intArrayOf(2), cellRects = listOf(RectF32(0f, 0f, 2f, 2f)),
                colors = listOf(ColorARGB.Red), flags = listOf(LatticeCellFlag.FIXED_COLOR),
                destination = RectF32(5f, 6f, 7f, 8f), sampling = ImageSampling.Cubic(1f / 3f, 1f / 3f),
            ).canonicalId,
        )
        assertNotEquals(
            atlas.canonicalId,
            GeometryNode.Atlas.of(
                image,
                listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32(tx = 3f, ty = 2f), RectF32(2f, 3f, 4f, 5f), ColorARGB.Green)),
            ).canonicalId,
        )
        assertNotEquals(
            atlas.canonicalId,
            GeometryNode.Atlas.of(
                image,
                listOf(GeometryNode.AtlasEntry.of(Matrix3x3F32(tx = 2f, ty = 3f), RectF32(2f, 3f, 4f, 5f), ColorARGB.Blue)),
            ).canonicalId,
        )
    }

    @Test
    fun `glyph run retains size variations and typeface identity without font types`() {
        val glyphIds = intArrayOf(10)
        val variations = linkedMapOf("wght" to 400f)
        val run = GeometryNode.GlyphRun.of(
            glyphIds = glyphIds,
            positions = listOf(Point2F32(3f, 4f)),
            fontSize = 14f,
            variations = variations,
            typeface = TypefaceReference(TypefaceId("face-1")),
        )
        val identity = run.canonicalId

        glyphIds[0] = 99
        variations["wght"] = 700f
        assertFailsWith<UnsupportedOperationException> {
            (run.variations() as MutableMap<String, Float>).clear()
        }

        assertContentEquals(intArrayOf(10), run.copyGlyphIds())
        assertEquals(14f, run.fontSize)
        assertEquals(mapOf("wght" to 400f), run.variations())
        assertEquals(TypefaceReference(TypefaceId("face-1")), run.typeface)
        assertEquals(identity, run.canonicalId)
        assertNotEquals(
            run.canonicalId,
            GeometryNode.GlyphRun.of(
                glyphIds = intArrayOf(10),
                positions = listOf(Point2F32(3f, 4f)),
                fontSize = 15f,
                variations = mapOf("wght" to 400f),
                typeface = TypefaceReference(TypefaceId("face-1")),
            ).canonicalId,
        )
        assertNotEquals(
            run.canonicalId,
            GeometryNode.GlyphRun.of(
                glyphIds = intArrayOf(10),
                positions = listOf(Point2F32(4f, 3f)),
                fontSize = 14f,
                variations = mapOf("wght" to 400f),
                typeface = TypefaceReference(TypefaceId("face-1")),
            ).canonicalId,
        )
        assertNotEquals(
            run.canonicalId,
            GeometryNode.GlyphRun.of(
                glyphIds = intArrayOf(10),
                positions = listOf(Point2F32(3f, 4f)),
                fontSize = 14f,
                variations = mapOf("wght" to 700f),
                typeface = TypefaceReference(TypefaceId("face-2")),
            ).canonicalId,
        )
    }

    @Test
    fun `canonical identity is sensitive to each foundational geometry family`() {
        val values = listOf<GeometryNode>(
            GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)),
            GeometryNode.RRect.of(org.graphiks.math.geometry.RRectF32.of(RectF32(0f, 0f, 1f, 1f), 0.5f)),
            GeometryNode.DoubleRRect.of(
                org.graphiks.math.geometry.RRectF32.of(RectF32(0f, 0f, 2f, 2f), 0.5f),
                org.graphiks.math.geometry.RRectF32.of(RectF32(0.5f, 0.5f, 1.5f, 1.5f), 0.25f),
            ),
            GeometryNode.Path(org.graphiks.math.geometry.PathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).build()),
            GeometryNode.Points.of(PointMode.POINTS, listOf(Point2F32(0f, 0f))),
            GeometryNode.IndexedMesh.of(MeshPrimitiveMode.TRIANGLES, emptyList(), indices = intArrayOf()),
            GeometryNode.ImagePatch.of(image, RectF32(0f, 0f, 1f, 1f), RectF32(0f, 0f, 1f, 1f)),
            GeometryNode.ImageLattice.of(image, intArrayOf(), intArrayOf(), destination = RectF32(0f, 0f, 1f, 1f)),
            GeometryNode.Atlas.of(image, emptyList()),
            GeometryNode.GlyphRun.of(intArrayOf(), emptyList()),
        )

        assertEquals(values.size, values.map(GeometryNode::canonicalId).distinct().size)
    }

    private fun indexedMesh(
        primitiveMode: MeshPrimitiveMode = MeshPrimitiveMode.TRIANGLES,
        texCoords: Collection<Point2F32> = listOf(Point2F32(3f, 4f)),
        colors: Collection<ColorARGB> = listOf(ColorARGB.Red),
        indices: IntArray = intArrayOf(0),
        bounds: RectF32 = RectF32(1f, 2f, 3f, 4f),
        program: ResourceReference = ResourceReference(ResourceId("program-1")),
    ): GeometryNode.IndexedMesh = GeometryNode.IndexedMesh.of(
        primitiveMode = primitiveMode,
        vertices = listOf(Point2F32(1f, 2f)),
        texCoords = texCoords,
        colors = colors,
        indices = indices,
        bounds = bounds,
        program = program,
    )
}
