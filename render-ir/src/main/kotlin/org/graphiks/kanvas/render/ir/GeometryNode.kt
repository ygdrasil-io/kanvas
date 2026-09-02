package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Backend-neutral geometry. Mutable math values are copied at this boundary. */
public sealed interface GeometryNode : CanonicalValue {
    public class Rect private constructor(bounds: RectF32) : GeometryNode {
        private val storedBounds: RectF32 = bounds.copy()
        public fun copyBounds(): RectF32 = storedBounds.copy()
        override val canonicalId: CanonicalId = rectId("geometry-rect-v1", storedBounds)
        public companion object { public fun of(bounds: RectF32): Rect = Rect(bounds) }
    }

    public class RRect private constructor(shape: RRectF32) : GeometryNode {
        private val storedShape: RRectF32 = copyRRect(shape)
        public fun copyShape(): RRectF32 = copyRRect(storedShape)
        override val canonicalId: CanonicalId = rrectId("geometry-rrect-v1", storedShape)
        public companion object { public fun of(shape: RRectF32): RRect = RRect(shape) }
    }

    public class DoubleRRect private constructor(outer: RRectF32, inner: RRectF32) : GeometryNode {
        private val storedOuter: RRectF32 = copyRRect(outer)
        private val storedInner: RRectF32 = copyRRect(inner)
        public fun copyOuter(): RRectF32 = copyRRect(storedOuter)
        public fun copyInner(): RRectF32 = copyRRect(storedInner)
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

    public class Points private constructor(
        public val mode: PointMode,
        points: Collection<Point2F32>,
    ) : GeometryNode, Iterable<Point2F32> {
        private val values: List<Point2F32> = immutableList(points)
        public val pointCount: Int get() = values.size
        public fun pointAt(index: Int): Point2F32 = values[index]
        override fun iterator(): Iterator<Point2F32> = values.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-points-v2",
            mode.name,
            pointSequenceId("points", values).value,
        )
        public companion object {
            public fun of(mode: PointMode, points: Collection<Point2F32>): Points = Points(mode, points)
        }
    }

    public class IndexedMesh private constructor(
        public val primitiveMode: MeshPrimitiveMode,
        vertices: Collection<Point2F32>,
        texCoords: Collection<Point2F32>?,
        colors: Collection<ColorARGB>?,
        indices: IntArray?,
        bounds: RectF32?,
        public val program: ResourceReference?,
        public val meshProgram: MeshProgramNode?,
    ) : GeometryNode {
        private val storedVertices: List<Point2F32> = immutableList(vertices)
        private val storedTexCoords: List<Point2F32>? = texCoords?.let(::immutableList)
        private val storedColors: List<ColorARGB>? = colors?.let(::immutableList)
        private val storedIndices: IntArray? = indices?.copyOf()
        private val storedBounds: RectF32? = bounds?.copy()

        public val vertexCount: Int get() = storedVertices.size
        public val texCoordCount: Int get() = storedTexCoords?.size ?: 0
        public val colorCount: Int get() = storedColors?.size ?: 0
        public val indexCount: Int get() = storedIndices?.size ?: 0
        public fun vertexAt(index: Int): Point2F32 = storedVertices[index]
        public fun texCoordAt(index: Int): Point2F32? = storedTexCoords?.get(index)
        public fun colorAt(index: Int): ColorARGB? = storedColors?.get(index)
        /** Null is distinct from a present-but-empty texture-coordinate stream. */
        public fun copyTexCoords(): List<Point2F32>? = storedTexCoords?.toList()
        /** Null is distinct from a present-but-empty color stream. */
        public fun copyColors(): List<ColorARGB>? = storedColors?.toList()
        /**
         * Returns null for a direct (non-indexed) mesh; a present empty array
         * remains a distinct indexed representation.
         */
        public fun copyIndices(): IntArray? = storedIndices?.copyOf()
        public fun copyBounds(): RectF32? = storedBounds?.copy()

        override val canonicalId: CanonicalId = canonicalId(
            "geometry-indexed-mesh-v3",
            primitiveMode.name,
            pointSequenceId("vertices", storedVertices).value,
            canonicalOptionalId("tex-coords", storedTexCoords?.let { pointSequenceId("values", it) }).value,
            canonicalOptionalId("colors", storedColors?.let { colorSequenceId("values", it) }).value,
            canonicalOptionalId(
                "indices",
                storedIndices?.let { canonicalSequenceId("values", it.map(Int::toString)) },
            ).value,
            canonicalOptionalId("bounds", storedBounds?.let { rectId("value", it) }).value,
            canonicalOptionalId("program", program?.canonicalId).value,
            canonicalOptionalId("mesh-program", meshProgram?.canonicalId).value,
        )

        public companion object {
            public fun of(
                primitiveMode: MeshPrimitiveMode,
                vertices: Collection<Point2F32>,
                texCoords: Collection<Point2F32>? = null,
                colors: Collection<ColorARGB>? = null,
                indices: IntArray? = null,
                bounds: RectF32? = null,
                program: ResourceReference? = null,
                meshProgram: MeshProgramNode? = null,
            ): IndexedMesh = IndexedMesh(primitiveMode, vertices, texCoords, colors, indices, bounds, program, meshProgram)
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
            "geometry-image-patch-v1",
            image.canonicalId.value,
            rectId("source", storedSource).value,
            rectId("destination", storedDestination).value,
        )
        public companion object {
            public fun of(image: ResourceReference, source: RectF32, destination: RectF32): ImagePatch =
                ImagePatch(image, source, destination)
        }
    }

    public class ImageLattice private constructor(
        public val image: ResourceReference,
        xDivs: IntArray,
        yDivs: IntArray,
        cellRects: Collection<RectF32>?,
        colors: Collection<ColorARGB>?,
        flags: Collection<LatticeCellFlag>?,
        destination: RectF32,
        public val sampling: ImageSampling,
    ) : GeometryNode {
        private val storedXDivs: IntArray = xDivs.copyOf()
        private val storedYDivs: IntArray = yDivs.copyOf()
        private val storedCellRects: List<RectF32>? = cellRects?.map(RectF32::copy)?.let(::immutableList)
        private val storedColors: List<ColorARGB>? = colors?.let(::immutableList)
        private val storedFlags: List<LatticeCellFlag>? = flags?.let(::immutableList)
        private val storedDestination: RectF32 = destination.copy()

        public fun copyXDivs(): IntArray = storedXDivs.copyOf()
        public fun copyYDivs(): IntArray = storedYDivs.copyOf()
        public fun copyCellRects(): List<RectF32>? = storedCellRects?.map(RectF32::copy)?.let(::immutableList)
        public fun colorAt(index: Int): ColorARGB? = storedColors?.get(index)
        public fun flagAt(index: Int): LatticeCellFlag? = storedFlags?.get(index)
        /** Null is distinct from a present-but-empty lattice color table. */
        public fun copyColors(): List<ColorARGB>? = storedColors?.toList()
        /** Null is distinct from a present-but-empty lattice flag table. */
        public fun copyFlags(): List<LatticeCellFlag>? = storedFlags?.toList()
        public fun copyDestination(): RectF32 = storedDestination.copy()

        override val canonicalId: CanonicalId = canonicalId(
            "geometry-image-lattice-v2",
            image.canonicalId.value,
            canonicalSequenceId("x-divs", storedXDivs.map(Int::toString)).value,
            canonicalSequenceId("y-divs", storedYDivs.map(Int::toString)).value,
            canonicalOptionalId("cell-rects", storedCellRects?.let { rectSequenceId("values", it) }).value,
            canonicalOptionalId("colors", storedColors?.let { colorSequenceId("values", it) }).value,
            canonicalOptionalId("flags", storedFlags?.let { canonicalSequenceId("values", it.map(LatticeCellFlag::name)) }).value,
            rectId("destination", storedDestination).value,
            sampling.canonicalId.value,
        )

        public companion object {
            public fun of(
                image: ResourceReference,
                xDivs: IntArray,
                yDivs: IntArray,
                cellRects: Collection<RectF32>? = null,
                colors: Collection<ColorARGB>? = null,
                flags: Collection<LatticeCellFlag>? = null,
                destination: RectF32,
                sampling: ImageSampling = ImageSampling.Linear,
            ): ImageLattice = ImageLattice(image, xDivs, yDivs, cellRects, colors, flags, destination, sampling)
        }
    }

    public class Atlas private constructor(public val image: ResourceReference, entries: Collection<AtlasEntry>) : GeometryNode,
        Iterable<AtlasEntry> {
        private val values: List<AtlasEntry> = immutableList(entries)
        public val entryCount: Int get() = values.size
        public fun entryAt(index: Int): AtlasEntry = values[index]
        override fun iterator(): Iterator<AtlasEntry> = values.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-atlas-v2",
            image.canonicalId.value,
            canonicalSequenceId("entries", values.map { it.canonicalId.value }).value,
        )
        public companion object { public fun of(image: ResourceReference, entries: Collection<AtlasEntry>): Atlas = Atlas(image, entries) }
    }

    public class AtlasEntry private constructor(
        public val transform: Matrix3x3F32,
        source: RectF32,
        public val color: ColorARGB?,
    ) : CanonicalValue {
        private val storedSource: RectF32 = source.copy()
        public fun copySource(): RectF32 = storedSource.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "atlas-entry-v2",
            matrixId("transform", transform).value,
            rectId("source", storedSource).value,
            canonicalOptionalId("color", color?.let { colorId("value", it) }).value,
        )
        public companion object {
            public fun of(transform: Matrix3x3F32, source: RectF32, color: ColorARGB? = null): AtlasEntry =
                AtlasEntry(transform, source, color)
        }
    }

    public class GlyphRun private constructor(
        glyphIds: IntArray,
        positions: Collection<Point2F32>,
        public val fontSize: Float,
        variations: Map<String, Float>,
        public val typeface: TypefaceReference?,
    ) : GeometryNode {
        private val storedGlyphIds: IntArray = glyphIds.copyOf()
        private val storedPositions: List<Point2F32> = immutableList(positions)
        private val storedVariations: Map<String, Float> = immutableSortedMap(variations)

        init { require(storedGlyphIds.size == storedPositions.size) { "GlyphRun glyph IDs and positions must have the same size" } }

        public val glyphCount: Int get() = storedGlyphIds.size
        public fun copyGlyphIds(): IntArray = storedGlyphIds.copyOf()
        public fun positionAt(index: Int): Point2F32 = storedPositions[index]
        public fun variations(): Map<String, Float> = storedVariations
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-glyph-run-v2",
            canonicalSequenceId("glyph-ids", storedGlyphIds.map(Int::toString)).value,
            pointSequenceId("positions", storedPositions).value,
            fontSize.canonicalBits(),
            canonicalMapId("variations", storedVariations).value,
            canonicalOptionalId("typeface", typeface?.canonicalId).value,
        )

        public companion object {
            public fun of(
                glyphIds: IntArray,
                positions: Collection<Point2F32>,
                fontSize: Float = 12f,
                variations: Map<String, Float> = emptyMap(),
                typeface: TypefaceReference? = null,
            ): GlyphRun = GlyphRun(glyphIds, positions, fontSize, variations, typeface)
        }
    }

    /** A resolved text blob retains all runs and its draw origin without importing font APIs. */
    public class TextBlob private constructor(
        runs: Collection<GlyphRun>,
        public val x: Float,
        public val y: Float,
        public val typeface: TypefaceReference?,
        public val fontSize: Float,
        variationCoordinates: Map<String, Float>,
    ) : GeometryNode, Iterable<GlyphRun> {
        private val values: List<GlyphRun> = immutableList(runs)
        private val storedVariationCoordinates: Map<String, Float> = immutableSortedMap(variationCoordinates)
        public val runCount: Int get() = values.size
        public fun runAt(index: Int): GlyphRun = values[index]
        /** Immutable, canonicalized OpenType design coordinates resolved for this blob. */
        public fun variationCoordinates(): Map<String, Float> = storedVariationCoordinates
        override fun iterator(): Iterator<GlyphRun> = values.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-text-blob-v2",
            canonicalSequenceId("runs", values.map { it.canonicalId.value }).value,
            x.canonicalBits(),
            y.canonicalBits(),
            canonicalOptionalId("typeface", typeface?.canonicalId).value,
            fontSize.canonicalBits(),
            canonicalMapId("variations", storedVariationCoordinates).value,
        )
        public companion object {
            public fun of(
                runs: Collection<GlyphRun>,
                x: Float,
                y: Float,
                typeface: TypefaceReference? = null,
                fontSize: Float = 12f,
                variationCoordinates: Map<String, Float> = emptyMap(),
            ): TextBlob = TextBlob(runs, x, y, typeface, fontSize, variationCoordinates)
        }
    }

    /** A bounded nested picture is a typed subscene, never a string or renderer handle. */
    public class Picture private constructor(public val scene: SceneSnapshot, cullRect: RectF32) : GeometryNode {
        private val storedCullRect: RectF32 = cullRect.copy()
        public fun copyCullRect(): RectF32 = storedCullRect.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "geometry-picture-v1", scene.canonicalId.value, rectId("cull", storedCullRect).value,
        )
        override fun equals(other: Any?): Boolean = other is Picture && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object { public fun of(scene: SceneSnapshot, cullRect: RectF32): Picture = Picture(scene, cullRect) }
    }
}

/** Typed MeshProgram runtime contract, independent from any compiled shader or GPU child object. */
public class MeshProgramNode private constructor(
    public val descriptor: RuntimeEffectDescriptor,
    uniforms: Map<String, RuntimeUniformValue>,
    children: Collection<MeshProgramChild>,
) : CanonicalValue, Iterable<MeshProgramChild> {
    private val storedUniforms: Map<String, RuntimeUniformValue> = immutableUniformMap(uniforms)
    private val storedChildren: List<MeshProgramChild> = immutableList(children)
    init {
        require(descriptor.abi == RuntimeEffectAbi.SHADER) { "Mesh Program must use SHADER runtime ABI" }
        RuntimeBindingValidator.validate(descriptor, storedUniforms, storedChildren.map { it.binding }).requireValid()
    }
    public fun uniforms(): Map<String, RuntimeUniformValue> = storedUniforms
    public val childCount: Int get() = storedChildren.size
    public fun childAt(index: Int): MeshProgramChild = storedChildren[index]
    override fun iterator(): Iterator<MeshProgramChild> = storedChildren.iterator()
    override val canonicalId: CanonicalId = canonicalId(
        "mesh-program-node-v1", descriptor.canonicalId.value, uniformMapId(storedUniforms).value,
        canonicalSequenceId("children", storedChildren.map { it.canonicalId.value }).value,
    )
    public companion object {
        public fun of(descriptor: RuntimeEffectDescriptor, uniforms: Map<String, RuntimeUniformValue>, children: Collection<MeshProgramChild>): MeshProgramNode =
            MeshProgramNode(descriptor, uniforms, children)
    }
}

/** Tagged, ordered MeshProgram children. */
public sealed interface MeshProgramChild : CanonicalValue {
    public val name: String
    public val binding: RuntimeChildBinding
    public data class Shader(override val name: String, public val material: MaterialNode) : MeshProgramChild {
        override val binding: RuntimeChildBinding = RuntimeChildBinding(name, RuntimeChildType.SHADER)
        override val canonicalId: CanonicalId = canonicalId("mesh-program-child-shader-v1", name, material.canonicalId.value)
    }
    public data class ColorFilter(override val name: String, public val filter: ColorFilterNode) : MeshProgramChild {
        override val binding: RuntimeChildBinding = RuntimeChildBinding(name, RuntimeChildType.COLOR_FILTER)
        override val canonicalId: CanonicalId = canonicalId("mesh-program-child-color-filter-v1", name, filter.canonicalId.value)
    }
    public data class Blender(override val name: String, public val blender: BlenderNode) : MeshProgramChild {
        override val binding: RuntimeChildBinding = RuntimeChildBinding(name, RuntimeChildType.BLENDER)
        override val canonicalId: CanonicalId = canonicalId("mesh-program-child-blender-v1", name, blender.canonicalId.value)
    }
}

/** Semantic point grouping; no canvas dependency is retained. */
public enum class PointMode { POINTS, LINES, POLYGON }

/** Semantic indexed-mesh topology; no backend primitive topology is selected here. */
public enum class MeshPrimitiveMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

/** Per-cell behavior for an image lattice. */
public enum class LatticeCellFlag { DEFAULT, TRANSPARENT, FIXED_COLOR }

/** Neutral image sampling request that a backend may later lower. */
public sealed interface ImageSampling : CanonicalValue {
    public data object Nearest : ImageSampling {
        override val canonicalId: CanonicalId = canonicalId("image-sampling-nearest-v1")
    }

    public data object Linear : ImageSampling {
        override val canonicalId: CanonicalId = canonicalId("image-sampling-linear-v1")
    }

    public data class Cubic(public val b: Float, public val c: Float) : ImageSampling {
        override val canonicalId: CanonicalId = canonicalId("image-sampling-cubic-v1", b.canonicalBits(), c.canonicalBits())
    }
}

/** Stable reference to a resolved typeface without importing a font implementation. */
@JvmInline
public value class TypefaceId(public val value: String) {
    init { require(value.isNotBlank()) { "TypefaceId.value must not be blank" } }
}

/** Backend-neutral typeface identity attached to resolved glyph data. */
public data class TypefaceReference(public val id: TypefaceId) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId("typeface-reference-v1", id.value)
}

private fun copyRRect(value: RRectF32): RRectF32 = value.copy(rect = value.rect.copy())

private fun rectId(tag: String, rect: RectF32): CanonicalId = canonicalId(
    tag,
    rect.left.canonicalBits(), rect.top.canonicalBits(), rect.right.canonicalBits(), rect.bottom.canonicalBits(),
)

private fun rrectId(tag: String, rrect: RRectF32): CanonicalId = canonicalId(
    tag,
    rectId("rect", rrect.rect).value,
    rrect.topLeft.x.canonicalBits(), rrect.topLeft.y.canonicalBits(),
    rrect.topRight.x.canonicalBits(), rrect.topRight.y.canonicalBits(),
    rrect.bottomRight.x.canonicalBits(), rrect.bottomRight.y.canonicalBits(),
    rrect.bottomLeft.x.canonicalBits(), rrect.bottomLeft.y.canonicalBits(),
)

private fun pointSequenceId(tag: String, points: Collection<Point2F32>): CanonicalId = canonicalSequenceId(
    tag,
    points.map { point -> canonicalId("point", point.x.canonicalBits(), point.y.canonicalBits()).value },
)

private fun rectSequenceId(tag: String, rects: Collection<RectF32>): CanonicalId = canonicalSequenceId(
    tag,
    rects.map { rect -> rectId("rect", rect).value },
)

private fun colorSequenceId(tag: String, colors: Collection<ColorARGB>): CanonicalId = canonicalSequenceId(
    tag,
    colors.map { color -> colorId("color", color).value },
)

private fun colorId(tag: String, color: ColorARGB): CanonicalId = canonicalId(tag, color.value.toString())

private fun matrixId(tag: String, matrix: Matrix3x3F32): CanonicalId = canonicalId(
    tag,
    matrix.sx.canonicalBits(), matrix.kx.canonicalBits(), matrix.tx.canonicalBits(),
    matrix.ky.canonicalBits(), matrix.sy.canonicalBits(), matrix.ty.canonicalBits(),
    matrix.persp0.canonicalBits(), matrix.persp1.canonicalBits(), matrix.persp2.canonicalBits(),
)

private fun canonicalMapId(tag: String, values: Map<String, Float>): CanonicalId = canonicalSequenceId(
    tag,
    values.map { (name, value) -> canonicalId("entry", name, value.canonicalBits()).value },
)

private fun pathId(path: PathF32): CanonicalId = canonicalId(
    "geometry-path-v2",
    path.fillRule.name,
    canonicalSequenceId("segments", path.map { segment -> pathSegmentId(segment).value }).value,
)

private fun pathSegmentId(segment: PathSegmentF32): CanonicalId = when (segment) {
    is PathSegmentF32.MoveTo -> canonicalId("move", segment.point.x.canonicalBits(), segment.point.y.canonicalBits())
    is PathSegmentF32.LineTo -> canonicalId("line", segment.point.x.canonicalBits(), segment.point.y.canonicalBits())
    is PathSegmentF32.QuadTo -> canonicalId(
        "quad", segment.control.x.canonicalBits(), segment.control.y.canonicalBits(),
        segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
    )
    is PathSegmentF32.CubicTo -> canonicalId(
        "cubic", segment.control1.x.canonicalBits(), segment.control1.y.canonicalBits(),
        segment.control2.x.canonicalBits(), segment.control2.y.canonicalBits(),
        segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
    )
    is PathSegmentF32.ArcTo -> canonicalId(
        "arc", segment.radius.x.canonicalBits(), segment.radius.y.canonicalBits(),
        segment.xAxisRotation.canonicalBits(), segment.largeArc.toString(), segment.sweep.toString(),
        segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
    )
    PathSegmentF32.Close -> canonicalId("close")
}
