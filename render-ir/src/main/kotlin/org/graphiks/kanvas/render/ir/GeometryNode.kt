package org.graphiks.kanvas.render.ir

import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/** Backend-neutral geometry. Mutable math values are copied at this boundary. */
public sealed interface GeometryNode : CanonicalValue {
    public class Rect private constructor(bounds: RectF32) : GeometryNode {
        private val storedBounds: RectF32 = bounds.copy()
        public fun copyBounds(): RectF32 = storedBounds.copy()
        override val canonicalId: CanonicalId = rectId("geometry-rect-v1", storedBounds)
        public companion object { public fun of(bounds: RectF32): Rect = Rect(bounds) }
    }

    public class RRect private constructor(shape: RRectF32) : GeometryNode {
        private val storedShape: RRectF32 = shape.copy(rect = shape.rect.copy())
        public fun copyShape(): RRectF32 = storedShape.copy(rect = storedShape.rect.copy())
        override val canonicalId: CanonicalId = rrectId("geometry-rrect-v1", storedShape)
        public companion object { public fun of(shape: RRectF32): RRect = RRect(shape) }
    }

    public class DoubleRRect private constructor(outer: RRectF32, inner: RRectF32) : GeometryNode {
        private val storedOuter: RRectF32 = outer.copy(rect = outer.rect.copy())
        private val storedInner: RRectF32 = inner.copy(rect = inner.rect.copy())
        public fun copyOuter(): RRectF32 = storedOuter.copy(rect = storedOuter.rect.copy())
        public fun copyInner(): RRectF32 = storedInner.copy(rect = storedInner.rect.copy())
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-double-rrect-v1",
            rrectId("outer", storedOuter).value,
            rrectId("inner", storedInner).value,
        )
        public companion object {
            public fun of(outer: RRectF32, inner: RRectF32): DoubleRRect = DoubleRRect(outer, inner)
        }
    }

    public data class Path(public val path: PathF32) : GeometryNode {
        override val canonicalId: CanonicalId = pathId(path)
    }

    public class Points private constructor(points: Collection<Point2F32>) : GeometryNode, Iterable<Point2F32> {
        private val values: List<Point2F32> = points.toList()
        public val pointCount: Int get() = values.size
        public fun pointAt(index: Int): Point2F32 = values[index]
        override fun iterator(): Iterator<Point2F32> = values.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-points-v1",
            *values.flatMap { listOf(it.x.canonicalBits(), it.y.canonicalBits()) }.toTypedArray(),
        )
        public companion object { public fun of(points: Collection<Point2F32>): Points = Points(points) }
    }

    public class IndexedMesh private constructor(vertices: Collection<Point2F32>, indices: IntArray) : GeometryNode {
        private val storedVertices: List<Point2F32> = vertices.toList()
        private val storedIndices: IntArray = indices.copyOf()
        public val vertexCount: Int get() = storedVertices.size
        public val indexCount: Int get() = storedIndices.size
        public fun vertexAt(index: Int): Point2F32 = storedVertices[index]
        public fun copyIndices(): IntArray = storedIndices.copyOf()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-indexed-mesh-v1",
            *storedVertices.flatMap { listOf(it.x.canonicalBits(), it.y.canonicalBits()) }.toTypedArray(),
            *storedIndices.map(Int::toString).toTypedArray(),
        )
        public companion object {
            public fun of(vertices: Collection<Point2F32>, indices: IntArray): IndexedMesh = IndexedMesh(vertices, indices)
        }
    }

    public class ImagePatch private constructor(
        public val image: ResourceReference,
        source: RectF32,
        destination: RectF32,
    ) : GeometryNode {
        private val storedSource: RectF32 = source.copy()
        private val storedDestination: RectF32 = destination.copy()
        public fun copySource(): RectF32 = storedSource.copy()
        public fun copyDestination(): RectF32 = storedDestination.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-image-patch-v1", image.canonicalId.value,
            rectId("source", storedSource).value, rectId("destination", storedDestination).value,
        )
        public companion object {
            public fun of(image: ResourceReference, source: RectF32, destination: RectF32): ImagePatch =
                ImagePatch(image, source, destination)
        }
    }

    public class ImageLattice private constructor(
        public val image: ResourceReference,
        xDivs: Collection<Float>,
        yDivs: Collection<Float>,
        destination: RectF32,
    ) : GeometryNode {
        private val storedXDivs: List<Float> = xDivs.toList()
        private val storedYDivs: List<Float> = yDivs.toList()
        private val storedDestination: RectF32 = destination.copy()
        public fun xDivs(): List<Float> = storedXDivs.toList()
        public fun yDivs(): List<Float> = storedYDivs.toList()
        public fun copyDestination(): RectF32 = storedDestination.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-image-lattice-v1", image.canonicalId.value,
            *storedXDivs.map(Float::canonicalBits).toTypedArray(),
            *storedYDivs.map(Float::canonicalBits).toTypedArray(), rectId("destination", storedDestination).value,
        )
        public companion object {
            public fun of(
                image: ResourceReference, xDivs: Collection<Float>, yDivs: Collection<Float>, destination: RectF32,
            ): ImageLattice = ImageLattice(image, xDivs, yDivs, destination)
        }
    }

    public class Atlas private constructor(public val image: ResourceReference, entries: Collection<AtlasEntry>) : GeometryNode,
        Iterable<AtlasEntry> {
        private val values: List<AtlasEntry> = entries.map { it.copy(source = it.source.copy()) }
        public val entryCount: Int get() = values.size
        public fun entryAt(index: Int): AtlasEntry = values[index].copy(source = values[index].source.copy())
        override fun iterator(): Iterator<AtlasEntry> = values.map { it.copy(source = it.source.copy()) }.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-atlas-v1", image.canonicalId.value,
            *values.map { entry -> canonicalId("atlas-entry", rectId("source", entry.source).value, entry.position.x.canonicalBits(), entry.position.y.canonicalBits()).value }.toTypedArray(),
        )
        public companion object { public fun of(image: ResourceReference, entries: Collection<AtlasEntry>): Atlas = Atlas(image, entries) }
    }

    public data class AtlasEntry(public val source: RectF32, public val position: Point2F32)

    public class GlyphRun private constructor(glyphIds: IntArray, positions: Collection<Point2F32>) : GeometryNode {
        private val storedGlyphIds: IntArray = glyphIds.copyOf()
        private val storedPositions: List<Point2F32> = positions.toList()
        init { require(storedGlyphIds.size == storedPositions.size) { "GlyphRun glyph IDs and positions must have the same size" } }
        public val glyphCount: Int get() = storedGlyphIds.size
        public fun copyGlyphIds(): IntArray = storedGlyphIds.copyOf()
        public fun positionAt(index: Int): Point2F32 = storedPositions[index]
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-glyph-run-v1", *storedGlyphIds.map(Int::toString).toTypedArray(),
            *storedPositions.flatMap { listOf(it.x.canonicalBits(), it.y.canonicalBits()) }.toTypedArray(),
        )
        public companion object { public fun of(glyphIds: IntArray, positions: Collection<Point2F32>): GlyphRun = GlyphRun(glyphIds, positions) }
    }
}

private fun rectId(tag: String, rect: RectF32): CanonicalId = canonicalId(
    tag, rect.left.canonicalBits(), rect.top.canonicalBits(), rect.right.canonicalBits(), rect.bottom.canonicalBits(),
)

private fun rrectId(tag: String, rrect: RRectF32): CanonicalId = canonicalId(
    tag, rectId("rect", rrect.rect).value,
    rrect.topLeft.x.canonicalBits(), rrect.topLeft.y.canonicalBits(),
    rrect.topRight.x.canonicalBits(), rrect.topRight.y.canonicalBits(),
    rrect.bottomRight.x.canonicalBits(), rrect.bottomRight.y.canonicalBits(),
    rrect.bottomLeft.x.canonicalBits(), rrect.bottomLeft.y.canonicalBits(),
)

private fun pathId(path: PathF32): CanonicalId = canonicalId(
    "geometry-path-v1",
    path.fillRule.name,
    *path.flatMap { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> listOf("move", segment.point.x.canonicalBits(), segment.point.y.canonicalBits())
            is PathSegmentF32.LineTo -> listOf("line", segment.point.x.canonicalBits(), segment.point.y.canonicalBits())
            is PathSegmentF32.QuadTo -> listOf(
                "quad", segment.control.x.canonicalBits(), segment.control.y.canonicalBits(),
                segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
            )
            is PathSegmentF32.CubicTo -> listOf(
                "cubic", segment.control1.x.canonicalBits(), segment.control1.y.canonicalBits(),
                segment.control2.x.canonicalBits(), segment.control2.y.canonicalBits(),
                segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
            )
            is PathSegmentF32.ArcTo -> listOf(
                "arc", segment.radius.x.canonicalBits(), segment.radius.y.canonicalBits(),
                segment.xAxisRotation.canonicalBits(), segment.largeArc.toString(), segment.sweep.toString(),
                segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
            )
            PathSegmentF32.Close -> listOf("close")
        }
    }.toTypedArray(),
)
