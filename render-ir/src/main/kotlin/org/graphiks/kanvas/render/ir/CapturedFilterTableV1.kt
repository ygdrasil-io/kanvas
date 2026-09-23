package org.graphiks.kanvas.render.ir

import java.util.ArrayDeque
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32

/** Stable, table-local identity for an immutable captured image-filter node. */
public data class CapturedFilterNodeIdI32(public val valueI32: Int) : CanonicalValue {
    init { require(valueI32 >= 0) { "Captured filter node IDs must be non-negative" } }
    override val canonicalId: CanonicalId = canonicalId("captured-filter-node-id-v1", valueI32.toString())
}

/** Reserved typed identities for W6d picture and backdrop filter inputs. */
public data class CapturedPictureIdI32(public val valueI32: Int) : CanonicalValue {
    init { require(valueI32 >= 0) { "Captured picture IDs must be non-negative" } }
    override val canonicalId: CanonicalId = canonicalId("captured-picture-id-v1", valueI32.toString())
}

public data class CapturedBackdropIdI32(public val valueI32: Int) : CanonicalValue {
    init { require(valueI32 >= 0) { "Captured backdrop IDs must be non-negative" } }
    override val canonicalId: CanonicalId = canonicalId("captured-backdrop-id-v1", valueI32.toString())
}

/** Explicit filter source binding; null public inputs always become [ImplicitSource]. */
public sealed interface CapturedFilterInputV1 : CanonicalValue {
    public data object ImplicitSource : CapturedFilterInputV1 {
        override val canonicalId: CanonicalId = canonicalId("captured-filter-input-v1", "implicit-source")
    }
    public data object TransparentBlack : CapturedFilterInputV1 {
        override val canonicalId: CanonicalId = canonicalId("captured-filter-input-v1", "transparent-black")
    }
    public data class Node(public val id: CapturedFilterNodeIdI32) : CapturedFilterInputV1 {
        override val canonicalId: CanonicalId = canonicalId("captured-filter-input-v1", "node", id.valueI32.toString())
    }
    public data class Picture(public val id: CapturedPictureIdI32) : CapturedFilterInputV1 {
        override val canonicalId: CanonicalId = canonicalId("captured-filter-input-v1", "picture", id.valueI32.toString())
    }
    public data class Backdrop(public val id: CapturedBackdropIdI32) : CapturedFilterInputV1 {
        override val canonicalId: CanonicalId = canonicalId("captured-filter-input-v1", "backdrop", id.valueI32.toString())
    }
}

/** Typed scene root; recursive image-filter payloads are never stored at a paint/effect boundary. */
public data class CapturedFilterRootV1(public val id: CapturedFilterNodeIdI32) : EffectNode {
    override val canonicalId: CanonicalId = canonicalId("captured-filter-root-v1", id.valueI32.toString())
}

/**
 * Fully captured image-filter payload. Inputs are table references instead of
 * recursive public/API objects, which keeps sharing explicit and wire-safe.
 */
public sealed interface CapturedFilterNodeV1 : CanonicalValue {
    override val canonicalId: CanonicalId
        get() = capturedFilterNodeId(this)
    public class Crop(crop: RectF32, public val tileMode: TileMode, public val input: CapturedFilterInputV1) : CapturedFilterNodeV1 {
        private val cropSnapshot = crop.copy()
        /** A copy prevents callers from mutating the table through [nodeAt]. */
        public val crop: RectF32 get() = cropSnapshot.copy()
        public fun copyCrop(): RectF32 = cropSnapshot.copy()
        override fun equals(other: Any?): Boolean = other is Crop && cropSnapshot == other.cropSnapshot &&
            tileMode == other.tileMode && input == other.input
        override fun hashCode(): Int = 31 * (31 * cropSnapshot.hashCode() + tileMode.hashCode()) + input.hashCode()
    }
    public data class Blur(val sigmaX: Float, val sigmaY: Float, val tileMode: TileMode, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class DropShadow(val dx: Float, val dy: Float, val sigmaX: Float, val sigmaY: Float, val color: ColorARGB, val input: CapturedFilterInputV1, val mode: CapturedDropShadowModeV1) : CapturedFilterNodeV1
    public data class ColorFilter(val filter: ColorFilterNode, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class Compose(val outer: CapturedFilterInputV1, val inner: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class Blend(val mode: BlendMode, val background: CapturedFilterInputV1, val foreground: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class Dilate(val radiusX: Float, val radiusY: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class Erode(val radiusX: Float, val radiusY: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class DistantLitDiffuse(val direction: Vector3F32, val lightColor: ColorARGB, val surfaceScale: Float, val kd: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class PointLitDiffuse(val location: Point3F32, val lightColor: ColorARGB, val surfaceScale: Float, val kd: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class SpotLitDiffuse(val location: Point3F32, val target: Point3F32, val specularExponent: Float, val cutoffAngle: Float, val lightColor: ColorARGB, val surfaceScale: Float, val kd: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class DistantLitSpecular(val direction: Vector3F32, val lightColor: ColorARGB, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class PointLitSpecular(val location: Point3F32, val lightColor: ColorARGB, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class SpotLitSpecular(val location: Point3F32, val target: Point3F32, val specularExponent: Float, val cutoffAngle: Float, val lightColor: ColorARGB, val surfaceScale: Float, val ks: Float, val shininess: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public data class Offset(val dx: Float, val dy: Float, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public class Tile(src: RectF32, dst: RectF32, public val input: CapturedFilterInputV1) : CapturedFilterNodeV1 {
        private val sourceSnapshot = src.copy()
        private val destinationSnapshot = dst.copy()
        public val src: RectF32 get() = sourceSnapshot.copy()
        public val dst: RectF32 get() = destinationSnapshot.copy()
        public fun copySource(): RectF32 = sourceSnapshot.copy()
        public fun copyDestination(): RectF32 = destinationSnapshot.copy()
        override fun equals(other: Any?): Boolean = other is Tile && sourceSnapshot == other.sourceSnapshot &&
            destinationSnapshot == other.destinationSnapshot && input == other.input
        override fun hashCode(): Int = 31 * (31 * sourceSnapshot.hashCode() + destinationSnapshot.hashCode()) + input.hashCode()
    }
    public class Merge(inputs: Collection<CapturedFilterInputV1>) : CapturedFilterNodeV1, Iterable<CapturedFilterInputV1> {
        init {
            require(inputs.size <= GraphLimits().maxNodes) {
                "Captured filter table fan-out exceeds node limit"
            }
        }
        private val values = immutableList(inputs)
        public val inputCount: Int get() = values.size
        public fun inputAt(index: Int): CapturedFilterInputV1 = values[index]
        override fun iterator(): Iterator<CapturedFilterInputV1> = values.iterator()
        override val canonicalId: CanonicalId = canonicalSequenceId("captured-filter-merge-v1", values.map { it.canonicalId.value })
    }
    public data class DisplacementMap(val xChannelSelector: ColorChannel, val yChannelSelector: ColorChannel, val scale: Float, val displacement: CapturedFilterInputV1, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public class Picture(public val scene: SceneSnapshot, cullRect: RectF32, src: RectF32?) : CapturedFilterNodeV1 {
        private val cullSnapshot = cullRect.copy()
        private val sourceSnapshot = src?.copy()
        public val cullRect: RectF32 get() = cullSnapshot.copy()
        public val src: RectF32? get() = sourceSnapshot?.copy()
        public fun copyCullRect(): RectF32 = cullSnapshot.copy()
        public fun copySource(): RectF32? = sourceSnapshot?.copy()
        override fun equals(other: Any?): Boolean = other is Picture && scene == other.scene &&
            cullSnapshot == other.cullSnapshot && sourceSnapshot == other.sourceSnapshot
        override fun hashCode(): Int = 31 * (31 * scene.hashCode() + cullSnapshot.hashCode()) + (sourceSnapshot?.hashCode() ?: 0)
    }
    public class Magnifier(src: RectF32, public val zoom: Float, public val inset: Float, public val input: CapturedFilterInputV1) : CapturedFilterNodeV1 {
        private val sourceSnapshot = src.copy()
        public val src: RectF32 get() = sourceSnapshot.copy()
        public fun copySource(): RectF32 = sourceSnapshot.copy()
        override fun equals(other: Any?): Boolean = other is Magnifier && sourceSnapshot == other.sourceSnapshot &&
            zoom == other.zoom && inset == other.inset && input == other.input
        override fun hashCode(): Int = 31 * (31 * (31 * sourceSnapshot.hashCode() + zoom.hashCode()) + inset.hashCode()) + input.hashCode()
    }
    public data class MatrixConvolution(val kernelSize: SizeF32, val kernel: ImmutableFloats, val gain: Float, val bias: Float, val kernelOffset: Vector2F32, val tileMode: TileMode, val convolveAlpha: Boolean, val input: CapturedFilterInputV1) : CapturedFilterNodeV1
    public class RuntimeEffect(
        public val descriptor: RuntimeEffectDescriptor,
        uniforms: Map<String, RuntimeUniformValue>,
        public val childShaderName: String?,
        children: Collection<CapturedRuntimeImageFilterChildV1>,
    ) : CapturedFilterNodeV1, Iterable<CapturedRuntimeImageFilterChildV1> {
        private val storedUniforms = immutableUniformMap(uniforms)
        private val storedChildren = immutableList(children)
        init {
            require(descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER) { "Runtime image filter must use IMAGE_FILTER ABI" }
            require(childShaderName == null || childShaderName.isNotBlank()) { "Runtime child shader name must not be blank" }
            require(storedChildren.map(CapturedRuntimeImageFilterChildV1::name).distinct().size == storedChildren.size) { "Runtime image-filter child names must be unique" }
            require(childShaderName == null || storedChildren.none { it.name == childShaderName }) { "Runtime image-filter child names must be unique" }
            RuntimeBindingValidator.validate(
                descriptor,
                storedUniforms,
                buildList {
                    childShaderName?.let { add(RuntimeChildBinding(it, RuntimeChildType.SHADER)) }
                    storedChildren.forEach { add(RuntimeChildBinding(it.name, RuntimeChildType.IMAGE_FILTER)) }
                },
            ).requireValid()
        }
        public fun uniforms(): Map<String, RuntimeUniformValue> = storedUniforms
        public val childCount: Int get() = storedChildren.size
        public fun childAt(index: Int): CapturedRuntimeImageFilterChildV1 = storedChildren[index]
        override fun iterator(): Iterator<CapturedRuntimeImageFilterChildV1> = storedChildren.iterator()
        override val canonicalId: CanonicalId = canonicalId("captured-filter-runtime-v1", descriptor.canonicalId.value, uniformMapId(storedUniforms).value, childShaderName.orEmpty(), canonicalSequenceId("children", storedChildren.map { it.canonicalId.value }).value)
    }
}

public data class CapturedRuntimeImageFilterChildV1(public val name: String, public val input: CapturedFilterInputV1) : CanonicalValue {
    init { require(name.isNotBlank()) { "Runtime image-filter child name must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("captured-runtime-image-filter-child-v1", name, input.canonicalId.value)
}

/** Immutable table shared by every command in one [SceneSnapshot]. */
public class CapturedFilterTableV1 private constructor(
    /** Owned by [of] or one of the bounded internal construction paths. */
    private val values: List<CapturedFilterNodeV1>,
) : CanonicalValue {
    public val nodeCount: Int get() = values.size
    public fun nodeAt(id: CapturedFilterNodeIdI32): CapturedFilterNodeV1 = values.getOrElse(id.valueI32) {
        throw IllegalArgumentException("Unknown captured filter node ${id.valueI32}")
    }
    override val canonicalId: CanonicalId = canonicalSequenceId("captured-filter-table-v1", values.map { it.canonicalId.value })

    public companion object {
        public val Empty: CapturedFilterTableV1 = CapturedFilterTableV1(emptyList())

        public fun of(nodes: Collection<CapturedFilterNodeV1>, limits: GraphLimits = GraphLimits()): CapturedFilterTableV1 {
            require(nodes.size <= limits.maxNodes) { "Captured filter table exceeds node limit" }
            // Check before copying: archive input and capture callers must never duplicate an
            // attacker-controlled graph that is already outside the W6b budget.
            return fromOwnedNodes(ArrayList(nodes), limits)
        }

        /**
         * Retains a fresh, private, already-bounded list without a second copy.
         *
         * This is deliberately internal: every public caller continues through [of], which
         * takes its defensive snapshot before a table can become immutable.
         */
        internal fun fromOwnedNodes(
            nodes: ArrayList<CapturedFilterNodeV1>,
            limits: GraphLimits = GraphLimits(),
        ): CapturedFilterTableV1 {
            require(nodes.size <= limits.maxNodes) { "Captured filter table exceeds node limit" }
            return CapturedFilterTableV1(nodes).also { it.validate(limits) }
        }
    }

    public fun validate(limits: GraphLimits = GraphLimits()) {
        require(nodeCount <= limits.maxNodes) { "Captured filter table exceeds node limit" }
        val state = IntArray(nodeCount)
        var visited = 0
        data class Visit(val id: Int, val leaving: Boolean, val depth: Int)
        for (start in values.indices) {
            if (state[start] != 0) continue
            val pending = ArrayDeque<Visit>()
            pending.addLast(Visit(start, false, 1))
            while (pending.isNotEmpty()) {
                val visit = pending.removeLast()
                if (visit.leaving) { state[visit.id] = 2; continue }
                if (visit.depth > limits.maxDepth) throw IllegalArgumentException("Captured filter table exceeds depth limit")
                when (state[visit.id]) {
                    1 -> throw IllegalArgumentException("Captured filter table contains a cycle")
                    2 -> continue
                }
                state[visit.id] = 1
                visited += 1
                if (visited > limits.maxNodes) throw IllegalArgumentException("Captured filter table exceeds node limit")
                val inputs = values[visit.id].inputs()
                if (inputs.size > limits.maxNodes) throw IllegalArgumentException("Captured filter table fan-out exceeds node limit")
                pending.addLast(Visit(visit.id, true, visit.depth))
                inputs.asReversed().forEach { input ->
                    when (input) {
                        is CapturedFilterInputV1.Node -> {
                            require(input.id.valueI32 in values.indices) { "Captured filter table references an unknown node" }
                            pending.addLast(Visit(input.id.valueI32, false, visit.depth + 1))
                        }
                        CapturedFilterInputV1.ImplicitSource,
                        CapturedFilterInputV1.TransparentBlack,
                        -> Unit
                        is CapturedFilterInputV1.Picture,
                        is CapturedFilterInputV1.Backdrop,
                        -> throw IllegalArgumentException("Captured picture and backdrop filter inputs are reserved until W6d")
                    }
                }
            }
        }
    }
}

/** Mutable capture-only builder. It never escapes a completed [SceneSnapshot]. */
public class CapturedFilterTableBuilderV1(private val limits: GraphLimits = GraphLimits()) {
    private val values = mutableListOf<CapturedFilterNodeV1?>()
    public fun reserve(): CapturedFilterNodeIdI32 {
        require(values.size < limits.maxNodes) { "Captured filter table exceeds node limit" }
        return CapturedFilterNodeIdI32(values.size).also { values += null }
    }
    public fun put(id: CapturedFilterNodeIdI32, node: CapturedFilterNodeV1) {
        require(id.valueI32 in values.indices && values[id.valueI32] == null) { "Captured filter table node ID is not reservable" }
        values[id.valueI32] = node
    }
    public fun build(limits: GraphLimits = this.limits): CapturedFilterTableV1 {
        require(values.size <= limits.maxNodes) { "Captured filter table exceeds node limit" }
        val completed = ArrayList<CapturedFilterNodeV1>(values.size)
        values.forEach { completed += requireNotNull(it) { "Captured filter table contains an unfinished node" } }
        return CapturedFilterTableV1.fromOwnedNodes(completed, limits)
    }
}

/** Converts one historical recursive wire occurrence without adding value-based aliases. */
internal fun CapturedFilterTableBuilderV1.appendLegacyOccurrence(value: ImageFilterNode): CapturedFilterRootV1 {
    val id = reserve()
    fun input(child: ImageFilterNode?): CapturedFilterInputV1 = child
        ?.let(::appendLegacyOccurrence)
        ?.let { CapturedFilterInputV1.Node(it.id) }
        ?: CapturedFilterInputV1.ImplicitSource
    val node = when (value) {
        is ImageFilterNode.Crop -> CapturedFilterNodeV1.Crop(value.copyCrop(), value.tileMode, input(value.input))
        is ImageFilterNode.Blur -> CapturedFilterNodeV1.Blur(value.sigmaX, value.sigmaY, value.tileMode, input(value.input))
        is ImageFilterNode.DropShadow -> CapturedFilterNodeV1.DropShadow(value.dx, value.dy, value.sigmaX, value.sigmaY, value.color, input(value.input), value.mode)
        is ImageFilterNode.ColorFilter -> CapturedFilterNodeV1.ColorFilter(value.filter, input(value.input))
        is ImageFilterNode.Compose -> CapturedFilterNodeV1.Compose(input(value.outer), input(value.inner))
        is ImageFilterNode.Blend -> CapturedFilterNodeV1.Blend(value.mode, input(value.background), input(value.foreground))
        is ImageFilterNode.Dilate -> CapturedFilterNodeV1.Dilate(value.radiusX, value.radiusY, input(value.input))
        is ImageFilterNode.Erode -> CapturedFilterNodeV1.Erode(value.radiusX, value.radiusY, input(value.input))
        is ImageFilterNode.DistantLitDiffuse -> CapturedFilterNodeV1.DistantLitDiffuse(value.direction, value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is ImageFilterNode.PointLitDiffuse -> CapturedFilterNodeV1.PointLitDiffuse(value.location, value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is ImageFilterNode.SpotLitDiffuse -> CapturedFilterNodeV1.SpotLitDiffuse(value.location, value.target, value.specularExponent, value.cutoffAngle, value.lightColor, value.surfaceScale, value.kd, input(value.input))
        is ImageFilterNode.DistantLitSpecular -> CapturedFilterNodeV1.DistantLitSpecular(value.direction, value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is ImageFilterNode.PointLitSpecular -> CapturedFilterNodeV1.PointLitSpecular(value.location, value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is ImageFilterNode.SpotLitSpecular -> CapturedFilterNodeV1.SpotLitSpecular(value.location, value.target, value.specularExponent, value.cutoffAngle, value.lightColor, value.surfaceScale, value.ks, value.shininess, input(value.input))
        is ImageFilterNode.Offset -> CapturedFilterNodeV1.Offset(value.dx, value.dy, input(value.input))
        is ImageFilterNode.Tile -> CapturedFilterNodeV1.Tile(value.copySource(), value.copyDestination(), input(value.input))
        is ImageFilterNode.Merge -> CapturedFilterNodeV1.Merge(value.map(::input))
        is ImageFilterNode.DisplacementMap -> CapturedFilterNodeV1.DisplacementMap(value.xChannelSelector, value.yChannelSelector, value.scale, input(value.displacement), input(value.input))
        is ImageFilterNode.Picture -> CapturedFilterNodeV1.Picture(value.scene, value.copyCullRect(), value.copySource())
        is ImageFilterNode.Magnifier -> CapturedFilterNodeV1.Magnifier(value.copySource(), value.zoom, value.inset, input(value.input))
        is ImageFilterNode.MatrixConvolution -> CapturedFilterNodeV1.MatrixConvolution(value.kernelSize, value.kernel, value.gain, value.bias, value.kernelOffset, value.tileMode, value.convolveAlpha, input(value.input))
        is ImageFilterNode.RuntimeEffect -> CapturedFilterNodeV1.RuntimeEffect(value.descriptor, value.uniforms(), value.childShaderName, value.map { CapturedRuntimeImageFilterChildV1(it.name, input(it.filter)) })
    }
    put(id, node)
    return CapturedFilterRootV1(id)
}

private fun CapturedFilterNodeV1.inputs(): List<CapturedFilterInputV1> = when (this) {
    is CapturedFilterNodeV1.Crop -> listOf(input)
    is CapturedFilterNodeV1.Blur -> listOf(input)
    is CapturedFilterNodeV1.DropShadow -> listOf(input)
    is CapturedFilterNodeV1.ColorFilter -> listOf(input)
    is CapturedFilterNodeV1.Compose -> listOf(outer, inner)
    is CapturedFilterNodeV1.Blend -> listOf(background, foreground)
    is CapturedFilterNodeV1.Dilate -> listOf(input)
    is CapturedFilterNodeV1.Erode -> listOf(input)
    is CapturedFilterNodeV1.DistantLitDiffuse -> listOf(input)
    is CapturedFilterNodeV1.PointLitDiffuse -> listOf(input)
    is CapturedFilterNodeV1.SpotLitDiffuse -> listOf(input)
    is CapturedFilterNodeV1.DistantLitSpecular -> listOf(input)
    is CapturedFilterNodeV1.PointLitSpecular -> listOf(input)
    is CapturedFilterNodeV1.SpotLitSpecular -> listOf(input)
    is CapturedFilterNodeV1.Offset -> listOf(input)
    is CapturedFilterNodeV1.Tile -> listOf(input)
    is CapturedFilterNodeV1.Merge -> toList()
    is CapturedFilterNodeV1.DisplacementMap -> listOf(displacement, input)
    is CapturedFilterNodeV1.Picture -> emptyList()
    is CapturedFilterNodeV1.Magnifier -> listOf(input)
    is CapturedFilterNodeV1.MatrixConvolution -> listOf(input)
    is CapturedFilterNodeV1.RuntimeEffect -> map(CapturedRuntimeImageFilterChildV1::input)
}

private fun capturedFilterNodeId(value: CapturedFilterNodeV1): CanonicalId = when (value) {
    is CapturedFilterNodeV1.Crop -> canonicalId("captured-filter-crop-v1", capturedRectId(value.crop).value, value.tileMode.name, value.input.canonicalId.value)
    is CapturedFilterNodeV1.Blur -> canonicalId("captured-filter-blur-v1", value.sigmaX.canonicalBits(), value.sigmaY.canonicalBits(), value.tileMode.name, value.input.canonicalId.value)
    is CapturedFilterNodeV1.DropShadow -> canonicalId("captured-filter-drop-shadow-v1", value.dx.canonicalBits(), value.dy.canonicalBits(), value.sigmaX.canonicalBits(), value.sigmaY.canonicalBits(), capturedColorId(value.color).value, value.input.canonicalId.value, value.mode.name)
    is CapturedFilterNodeV1.ColorFilter -> canonicalId("captured-filter-color-filter-v1", value.filter.canonicalId.value, value.input.canonicalId.value)
    is CapturedFilterNodeV1.Compose -> canonicalId("captured-filter-compose-v1", value.outer.canonicalId.value, value.inner.canonicalId.value)
    is CapturedFilterNodeV1.Blend -> canonicalId("captured-filter-blend-v1", value.mode.name, value.background.canonicalId.value, value.foreground.canonicalId.value)
    is CapturedFilterNodeV1.Dilate -> canonicalId("captured-filter-dilate-v1", value.radiusX.canonicalBits(), value.radiusY.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.Erode -> canonicalId("captured-filter-erode-v1", value.radiusX.canonicalBits(), value.radiusY.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.DistantLitDiffuse -> canonicalId("captured-filter-distant-lit-diffuse-v2", capturedVectorId(value.direction).value, capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.kd.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.PointLitDiffuse -> canonicalId("captured-filter-point-lit-diffuse-v2", capturedPointId(value.location).value, capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.kd.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.SpotLitDiffuse -> canonicalId("captured-filter-spot-lit-diffuse-v2", capturedPointId(value.location).value, capturedPointId(value.target).value, value.specularExponent.canonicalBits(), value.cutoffAngle.canonicalBits(), capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.kd.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.DistantLitSpecular -> canonicalId("captured-filter-distant-lit-specular-v2", capturedVectorId(value.direction).value, capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.ks.canonicalBits(), value.shininess.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.PointLitSpecular -> canonicalId("captured-filter-point-lit-specular-v2", capturedPointId(value.location).value, capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.ks.canonicalBits(), value.shininess.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.SpotLitSpecular -> canonicalId("captured-filter-spot-lit-specular-v2", capturedPointId(value.location).value, capturedPointId(value.target).value, value.specularExponent.canonicalBits(), value.cutoffAngle.canonicalBits(), capturedColorId(value.lightColor).value, value.surfaceScale.canonicalBits(), value.ks.canonicalBits(), value.shininess.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.Offset -> canonicalId("captured-filter-offset-v1", value.dx.canonicalBits(), value.dy.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.Tile -> canonicalId("captured-filter-tile-v1", capturedRectId(value.src).value, capturedRectId(value.dst).value, value.input.canonicalId.value)
    is CapturedFilterNodeV1.Merge -> canonicalSequenceId("captured-filter-merge-v1", value.map { it.canonicalId.value })
    is CapturedFilterNodeV1.DisplacementMap -> canonicalId("captured-filter-displacement-map-v1", value.xChannelSelector.name, value.yChannelSelector.name, value.scale.canonicalBits(), value.displacement.canonicalId.value, value.input.canonicalId.value)
    is CapturedFilterNodeV1.Picture -> canonicalId("captured-filter-picture-v1", value.scene.canonicalId.value, capturedRectId(value.cullRect).value, canonicalOptionalId("source", value.src?.let(::capturedRectId)).value)
    is CapturedFilterNodeV1.Magnifier -> canonicalId("captured-filter-magnifier-v1", capturedRectId(value.src).value, value.zoom.canonicalBits(), value.inset.canonicalBits(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.MatrixConvolution -> canonicalId("captured-filter-matrix-convolution-v1", value.kernelSize.width.canonicalBits(), value.kernelSize.height.canonicalBits(), value.kernel.canonicalId.value, value.gain.canonicalBits(), value.bias.canonicalBits(), capturedVectorId(value.kernelOffset).value, value.tileMode.name, value.convolveAlpha.toString(), value.input.canonicalId.value)
    is CapturedFilterNodeV1.RuntimeEffect -> canonicalId("captured-filter-runtime-effect-v1", value.descriptor.canonicalId.value, uniformMapId(value.uniforms()).value, if (value.childShaderName == null) "absent" else "present", value.childShaderName.orEmpty(), canonicalSequenceId("children", value.map { it.canonicalId.value }).value)
}

private fun capturedColorId(value: ColorARGB): CanonicalId = canonicalId("color", value.value.toString())
private fun capturedPointId(value: Point3F32): CanonicalId = canonicalId("point3", value.x.canonicalBits(), value.y.canonicalBits(), value.z.canonicalBits())
private fun capturedVectorId(value: Vector2F32): CanonicalId = canonicalId("vector", value.x.canonicalBits(), value.y.canonicalBits())
private fun capturedVectorId(value: Vector3F32): CanonicalId = canonicalId("vector3", value.x.canonicalBits(), value.y.canonicalBits(), value.z.canonicalBits())
private fun capturedRectId(value: RectF32): CanonicalId = canonicalId("rect", value.left.canonicalBits(), value.top.canonicalBits(), value.right.canonicalBits(), value.bottom.canonicalBits())
