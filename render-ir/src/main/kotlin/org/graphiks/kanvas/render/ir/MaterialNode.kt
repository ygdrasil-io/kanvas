package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32

/** Tile behavior for material sampling and image effects. */
public enum class TileMode { CLAMP, REPEAT, MIRROR, DECAL }

/** Interpolation color space requested by a material without selecting a backend implementation. */
public enum class ColorInterpolation { SRGB, LINEAR, OKLAB, HSL, OKLCH }

/** Semantic blend modes, preserving the public paint surface without importing it. */
public enum class BlendMode {
    CLEAR, SRC, DST, SRC_OVER, DST_OVER,
    SRC_IN, DST_IN, SRC_OUT, DST_OUT,
    SRC_ATOP, DST_ATOP, XOR, PLUS, MODULATE,
    MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN,
    COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT,
    DIFFERENCE, EXCLUSION,
    HUE, SATURATION, COLOR, LUMINOSITY,
}

/** A position/color pair copied into gradient resources. */
public data class GradientStop(public val position: Float, public val color: ColorARGB) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId("gradient-stop-v1", position.canonicalBits(), colorId(color).value)
}

/** Root of the backend-neutral material graph. */
public sealed interface MaterialNode : CanonicalValue {
    /** A neutral material that contributes no source colour. */
    public data object Transparent : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-transparent-v1")
    }

    public data class Solid(public val color: ColorARGB) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-solid-v1", colorId(color).value)
    }

    public class LinearGradient private constructor(
        public val start: Point2F32,
        public val end: Point2F32,
        stops: Collection<GradientStop>,
        public val tileMode: TileMode,
        public val interpolation: ColorInterpolation,
    ) : MaterialNode {
        private val values: List<GradientStop> = immutableList(stops)
        public fun stops(): List<GradientStop> = values
        override val canonicalId: CanonicalId = canonicalId(
            "material-linear-gradient-v1", pointId(start).value, pointId(end).value,
            canonicalSequenceId("stops", values.map { it.canonicalId.value }).value, tileMode.name, interpolation.name,
        )
        public companion object {
            public fun of(
                start: Point2F32,
                end: Point2F32,
                stops: Collection<GradientStop>,
                tileMode: TileMode = TileMode.CLAMP,
                interpolation: ColorInterpolation = ColorInterpolation.SRGB,
            ): LinearGradient = LinearGradient(start, end, stops, tileMode, interpolation)
        }
    }

    public class RadialGradient private constructor(
        public val center: Point2F32,
        public val radius: Float,
        stops: Collection<GradientStop>,
        public val tileMode: TileMode,
        public val interpolation: ColorInterpolation,
    ) : MaterialNode {
        private val values: List<GradientStop> = immutableList(stops)
        public fun stops(): List<GradientStop> = values
        override val canonicalId: CanonicalId = canonicalId(
            "material-radial-gradient-v1", pointId(center).value, radius.canonicalBits(),
            canonicalSequenceId("stops", values.map { it.canonicalId.value }).value, tileMode.name, interpolation.name,
        )
        public companion object {
            public fun of(
                center: Point2F32,
                radius: Float,
                stops: Collection<GradientStop>,
                tileMode: TileMode = TileMode.CLAMP,
                interpolation: ColorInterpolation = ColorInterpolation.SRGB,
            ): RadialGradient = RadialGradient(center, radius, stops, tileMode, interpolation)
        }
    }

    public class SweepGradient private constructor(
        public val center: Point2F32,
        public val startAngle: Float,
        public val endAngle: Float,
        stops: Collection<GradientStop>,
        public val tileMode: TileMode,
        public val interpolation: ColorInterpolation,
    ) : MaterialNode {
        private val values: List<GradientStop> = immutableList(stops)
        public fun stops(): List<GradientStop> = values
        override val canonicalId: CanonicalId = canonicalId(
            "material-sweep-gradient-v1", pointId(center).value, startAngle.canonicalBits(), endAngle.canonicalBits(),
            canonicalSequenceId("stops", values.map { it.canonicalId.value }).value, tileMode.name, interpolation.name,
        )
        public companion object {
            public fun of(
                center: Point2F32,
                startAngle: Float = 0f,
                endAngle: Float = 360f,
                stops: Collection<GradientStop>,
                tileMode: TileMode = TileMode.CLAMP,
                interpolation: ColorInterpolation = ColorInterpolation.SRGB,
            ): SweepGradient = SweepGradient(center, startAngle, endAngle, stops, tileMode, interpolation)
        }
    }

    public class ConicalGradient private constructor(
        public val start: Point2F32,
        public val startRadius: Float,
        public val end: Point2F32,
        public val endRadius: Float,
        stops: Collection<GradientStop>,
        public val tileMode: TileMode,
        public val interpolation: ColorInterpolation,
    ) : MaterialNode {
        private val values: List<GradientStop> = immutableList(stops)
        public fun stops(): List<GradientStop> = values
        override val canonicalId: CanonicalId = canonicalId(
            "material-conical-gradient-v1", pointId(start).value, startRadius.canonicalBits(), pointId(end).value,
            endRadius.canonicalBits(), canonicalSequenceId("stops", values.map { it.canonicalId.value }).value,
            tileMode.name, interpolation.name,
        )
        public companion object {
            public fun of(
                start: Point2F32,
                startRadius: Float,
                end: Point2F32,
                endRadius: Float,
                stops: Collection<GradientStop>,
                tileMode: TileMode = TileMode.CLAMP,
                interpolation: ColorInterpolation = ColorInterpolation.SRGB,
            ): ConicalGradient = ConicalGradient(start, startRadius, end, endRadius, stops, tileMode, interpolation)
        }
    }

    public data class ImageSample(
        public val image: ImageResourceSnapshot,
        public val tileModeX: TileMode = TileMode.CLAMP,
        public val tileModeY: TileMode = TileMode.CLAMP,
        public val sampling: ImageSampling = ImageSampling.Nearest,
    ) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId(
            "material-image-sample-v1", image.canonicalId.value, tileModeX.name, tileModeY.name, sampling.canonicalId.value,
        )
    }

    public data class Blend(public val mode: BlendMode, public val dst: MaterialNode, public val src: MaterialNode) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-blend-v1", mode.name, dst.canonicalId.value, src.canonicalId.value)
    }

    public class RuntimeEffect private constructor(
        public val descriptor: RuntimeEffectDescriptor,
        uniforms: Map<String, RuntimeUniformValue>,
        children: Collection<RuntimeMaterialChild>,
    ) : MaterialNode, Iterable<RuntimeMaterialChild> {
        private val storedUniforms: Map<String, RuntimeUniformValue> = immutableUniformMap(uniforms)
        private val storedChildren: List<RuntimeMaterialChild> = immutableList(children)
        init {
            require(descriptor.abi == RuntimeEffectAbi.SHADER) { "Runtime material must use SHADER ABI" }
            require(storedChildren.map(RuntimeMaterialChild::name).distinct().size == storedChildren.size) {
                "Runtime material child names must be unique"
            }
        }
        public fun uniforms(): Map<String, RuntimeUniformValue> = storedUniforms
        public val childCount: Int get() = storedChildren.size
        public fun childAt(index: Int): RuntimeMaterialChild = storedChildren[index]
        override fun iterator(): Iterator<RuntimeMaterialChild> = storedChildren.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "material-runtime-effect-v1", descriptor.canonicalId.value, uniformMapId(storedUniforms).value,
            canonicalSequenceId("children", storedChildren.map { it.canonicalId.value }).value,
        )
        public companion object {
            public fun of(
                descriptor: RuntimeEffectDescriptor,
                uniforms: Map<String, RuntimeUniformValue>,
                children: Collection<RuntimeMaterialChild>,
            ): RuntimeEffect = RuntimeEffect(descriptor, uniforms, children)
        }
    }

    public data class WithLocalMatrix(public val material: MaterialNode, public val matrix: Matrix3x3F32) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-local-matrix-v1", material.canonicalId.value, matrixCanonicalId("matrix", matrix).value)
    }

    public data class WithColorFilter(public val material: MaterialNode, public val filter: ColorFilterNode) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-color-filter-v1", material.canonicalId.value, filter.canonicalId.value)
    }

    public data class Opacity(public val material: MaterialNode, public val alpha: Float) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-opacity-v1", material.canonicalId.value, alpha.canonicalBits())
    }

    public data class PerlinNoise(
        public val baseX: Float,
        public val baseY: Float,
        public val numOctaves: Int,
        public val seed: Int,
        public val tileSize: SizeF32?,
    ) : MaterialNode {
        override val canonicalId: CanonicalId = noiseId("material-perlin-noise-v1", baseX, baseY, numOctaves, seed, tileSize)
    }

    public data class FractalNoise(
        public val baseX: Float,
        public val baseY: Float,
        public val numOctaves: Int,
        public val seed: Int,
        public val tileSize: SizeF32?,
    ) : MaterialNode {
        override val canonicalId: CanonicalId = noiseId("material-fractal-noise-v1", baseX, baseY, numOctaves, seed, tileSize)
    }

    public data class WithWorkingColorSpace(public val material: MaterialNode, public val interpolation: ColorInterpolation) : MaterialNode {
        override val canonicalId: CanonicalId = canonicalId("material-working-color-space-v1", material.canonicalId.value, interpolation.name)
    }

    public class CoordClamp private constructor(public val material: MaterialNode, subset: RectF32) : MaterialNode {
        private val storedSubset: RectF32 = subset.copy()
        public fun copySubset(): RectF32 = storedSubset.copy()
        override val canonicalId: CanonicalId = canonicalId("material-coord-clamp-v1", material.canonicalId.value, rectId(storedSubset).value)
        public companion object { public operator fun invoke(material: MaterialNode, subset: RectF32): CoordClamp = CoordClamp(material, subset) }
    }
}

/** Semantic coverage request, deliberately separate from a GPU coverage strategy. */
public enum class CoverageRequest : CanonicalValue {
    DEFAULT,
    ANTIALIASED,
    HARD_EDGE;

    override val canonicalId: CanonicalId get() = canonicalId("coverage-request-v1", name)
}

/** Semantic blend axis. */
public sealed interface BlendNode : CanonicalValue {
    public data object SrcOver : BlendNode {
        override val canonicalId: CanonicalId = canonicalId("blend-src-over-v1")
    }

    public data class Mode(public val mode: BlendMode) : BlendNode {
        override val canonicalId: CanonicalId = canonicalId("blend-mode-v1", mode.name)
    }

    public data class Custom(public val blender: BlenderNode) : BlendNode {
        override val canonicalId: CanonicalId = canonicalId("blend-custom-v1", blender.canonicalId.value)
    }
}

/** Backend-neutral clip operation kind. */
public enum class ClipOperation { INTERSECT, DIFFERENCE }

/** Immutable clip entry preserving captured geometry and canvas semantics. */
public data class ClipEntry(
    public val geometry: GeometryNode,
    public val operation: ClipOperation,
    public val antiAlias: Boolean = true,
    public val perspectiveCaptureRefusal: Boolean = false,
    public val transformClass: String = "identity",
) : CanonicalValue {
    init { require(transformClass.isNotBlank()) { "ClipEntry.transformClass must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId(
        "clip-entry-v1", geometry.canonicalId.value, operation.name, antiAlias.toString(),
        perspectiveCaptureRefusal.toString(), transformClass,
    )
}

/** Neutral ordered clip axis. */
public sealed interface ClipStackNode : CanonicalValue {
    public data object Empty : ClipStackNode {
        override val canonicalId: CanonicalId = canonicalId("clip-stack-empty-v1")
    }

    public class Operations private constructor(entries: Collection<ClipEntry>) : ClipStackNode, Iterable<ClipEntry> {
        private val values: List<ClipEntry> = immutableList(entries)
        public val entryCount: Int get() = values.size
        public fun entryAt(index: Int): ClipEntry = values[index]
        override fun iterator(): Iterator<ClipEntry> = values.iterator()
        override val canonicalId: CanonicalId = canonicalSequenceId("clip-stack-operations-v1", values.map { it.canonicalId.value })
        public companion object { public fun of(entries: Collection<ClipEntry>): ClipStackNode = if (entries.isEmpty()) Empty else Operations(entries) }
    }
}

private fun colorId(color: ColorARGB): CanonicalId = canonicalId("color", color.value.toString())

private fun pointId(point: Point2F32): CanonicalId = canonicalId("point", point.x.canonicalBits(), point.y.canonicalBits())

private fun rectId(rect: RectF32): CanonicalId = canonicalId(
    "rect", rect.left.canonicalBits(), rect.top.canonicalBits(), rect.right.canonicalBits(), rect.bottom.canonicalBits(),
)

private fun noiseId(tag: String, baseX: Float, baseY: Float, octaves: Int, seed: Int, tileSize: SizeF32?): CanonicalId = canonicalId(
    tag,
    baseX.canonicalBits(),
    baseY.canonicalBits(),
    octaves.toString(),
    seed.toString(),
    if (tileSize == null) "absent" else "present",
    tileSize?.width?.canonicalBits().orEmpty(),
    tileSize?.height?.canonicalBits().orEmpty(),
)
