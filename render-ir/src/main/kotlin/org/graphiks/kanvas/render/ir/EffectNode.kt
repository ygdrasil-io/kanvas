package org.graphiks.kanvas.render.ir

import java.util.ArrayDeque
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

/** Root of backend-neutral effects. */
public sealed interface EffectNode : CanonicalValue

/** Color-channel selection used by displacement-map effects. */
public enum class ColorChannel { RED, GREEN, BLUE, ALPHA }
public enum class MaskBlurStyle { NORMAL, SOLID, OUTER, INNER }
public enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }

/** Color-filter variants, represented without a paint or renderer dependency. */
public sealed interface ColorFilterNode : EffectNode {
    public data class Matrix(public val values: ImmutableFloats) : ColorFilterNode {
        init { require(values.copyToFloatArray().size == 20) { "Color matrix must contain exactly 20 values" } }
        override val canonicalId: CanonicalId = canonicalId("color-filter-matrix-v1", values.canonicalId.value)
    }
    public data class Blend(public val color: ColorARGB, public val mode: BlendMode) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-blend-v1", effectColorId(color).value, mode.name)
    }
    public data class Compose(public val outer: ColorFilterNode, public val inner: ColorFilterNode) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-compose-v1", outer.canonicalId.value, inner.canonicalId.value)
    }
    public data class Table(public val table: ImmutableUBytes) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-table-v1", table.canonicalId.value)
    }
    public data class Lighting(public val mul: ColorARGB, public val add: ColorARGB) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-lighting-v1", effectColorId(mul).value, effectColorId(add).value)
    }
    public data object SRGBToLinear : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-srgb-to-linear-v1")
    }
    public data object LinearToSRGB : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-linear-to-srgb-v1")
    }
    public data class HSLAMatrix(public val values: ImmutableFloats) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-hsla-matrix-v1", values.canonicalId.value)
    }
    public data class Lerp(public val t: Float, public val dst: ColorFilterNode, public val src: ColorFilterNode) : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-lerp-v1", t.canonicalBits(), dst.canonicalId.value, src.canonicalId.value)
    }
    public data object HighContrast : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-high-contrast-v1")
    }
    public data object Luma : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-luma-v1")
    }
    public data object Overdraw : ColorFilterNode {
        override val canonicalId: CanonicalId = canonicalId("color-filter-overdraw-v1")
    }
    public class RuntimeEffect private constructor(
        public val descriptor: RuntimeEffectDescriptor,
        uniforms: Map<String, RuntimeUniformValue>,
        children: Collection<RuntimeColorFilterChild>,
    ) : ColorFilterNode, Iterable<RuntimeColorFilterChild> {
        private val storedUniforms: Map<String, RuntimeUniformValue> = immutableUniformMap(uniforms)
        private val storedChildren: List<RuntimeColorFilterChild> = immutableList(children)
        init {
            require(descriptor.abi == RuntimeEffectAbi.COLOR_FILTER) { "Runtime color filter must use COLOR_FILTER ABI" }
            require(storedChildren.map(RuntimeColorFilterChild::name).distinct().size == storedChildren.size) {
                "Runtime color-filter child names must be unique"
            }
            RuntimeBindingValidator.validate(
                descriptor,
                storedUniforms,
                storedChildren.map { RuntimeChildBinding(it.name, RuntimeChildType.COLOR_FILTER) },
            ).requireValid()
        }
        public fun uniforms(): Map<String, RuntimeUniformValue> = storedUniforms
        public val childCount: Int get() = storedChildren.size
        public fun childAt(index: Int): RuntimeColorFilterChild = storedChildren[index]
        override fun iterator(): Iterator<RuntimeColorFilterChild> = storedChildren.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "color-filter-runtime-effect-v1", descriptor.canonicalId.value, uniformMapId(storedUniforms).value,
            canonicalSequenceId("children", storedChildren.map { it.canonicalId.value }).value,
        )
        override fun equals(other: Any?): Boolean = other is RuntimeEffect && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object {
            public fun of(
                descriptor: RuntimeEffectDescriptor,
                uniforms: Map<String, RuntimeUniformValue>,
                children: Collection<RuntimeColorFilterChild>,
            ): RuntimeEffect = RuntimeEffect(descriptor, uniforms, children)
        }
    }
}

/** Ordered color-filter child retained by a runtime color filter. */
public data class RuntimeColorFilterChild(public val name: String, public val filter: ColorFilterNode) : CanonicalValue {
    init { require(name.isNotBlank()) { "RuntimeColorFilterChild.name must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("runtime-color-filter-child-v1", name, filter.canonicalId.value)
}

/** Mask-filter variants. */
public sealed interface MaskFilterNode : EffectNode {
    public data class Blur(public val style: MaskBlurStyle, public val sigma: Float) : MaskFilterNode {
        override val canonicalId: CanonicalId = canonicalId("mask-filter-blur-v1", style.name, sigma.canonicalBits())
    }
    public data class Shader(public val material: MaterialNode) : MaskFilterNode {
        override val canonicalId: CanonicalId = canonicalId("mask-filter-shader-v1", material.canonicalId.value)
    }
    public data class Table(public val table: ImmutableUBytes) : MaskFilterNode {
        override val canonicalId: CanonicalId = canonicalId("mask-filter-table-v1", table.canonicalId.value)
    }
}

/** Path-effect variants. Their path values use the immutable math authority. */
public sealed interface PathEffectNode : EffectNode {
    public data class Dash(public val intervals: ImmutableFloats, public val phase: Float = 0f) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-dash-v1", intervals.canonicalId.value, phase.canonicalBits())
    }
    public data class Corner(public val radius: Float) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-corner-v1", radius.canonicalBits())
    }
    public data class Discrete(public val segmentLength: Float, public val deviation: Float) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-discrete-v1", segmentLength.canonicalBits(), deviation.canonicalBits())
    }
    public data class Path1D(public val path: PathF32, public val advance: Float, public val phase: Float, public val style: Path1DStyle) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-path-1d-v1", effectPathId(path).value, advance.canonicalBits(), phase.canonicalBits(), style.name)
    }
    public data class Path2D(public val matrix: Matrix3x3F32, public val path: PathF32) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-path-2d-v1", matrixCanonicalId("matrix", matrix).value, effectPathId(path).value)
    }
    public data class Trim(public val start: Float, public val stop: Float) : PathEffectNode {
        override val canonicalId: CanonicalId = canonicalId("path-effect-trim-v1", start.canonicalBits(), stop.canonicalBits())
    }
}

/** Image-filter variants. Optional inputs retain absent-versus-present semantics. */
public sealed interface ImageFilterNode : EffectNode {
    public class Crop private constructor(crop: RectF32, public val tileMode: TileMode, public val input: ImageFilterNode?) : ImageFilterNode {
        private val storedCrop: RectF32 = crop.copy()
        public fun copyCrop(): RectF32 = storedCrop.copy()
        override val canonicalId: CanonicalId = canonicalId("image-filter-crop-v1", effectRectId(storedCrop).value, tileMode.name, optionalEffectId(input).value)
        override fun equals(other: Any?): Boolean = other is Crop && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object { public fun of(crop: RectF32, tileMode: TileMode = TileMode.CLAMP, input: ImageFilterNode? = null): Crop = Crop(crop, tileMode, input) }
    }
    public data class Blur(public val sigmaX: Float, public val sigmaY: Float, public val tileMode: TileMode = TileMode.CLAMP, public val input: ImageFilterNode? = null) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-blur-v1", sigmaX.canonicalBits(), sigmaY.canonicalBits(), tileMode.name, optionalEffectId(input).value)
    }
    public data class DropShadow(
        public val dx: Float, public val dy: Float, public val sigmaX: Float, public val sigmaY: Float,
        public val color: ColorARGB, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-drop-shadow-v1", dx.canonicalBits(), dy.canonicalBits(), sigmaX.canonicalBits(), sigmaY.canonicalBits(), effectColorId(color).value, optionalEffectId(input).value)
    }
    public data class ColorFilter(public val filter: ColorFilterNode, public val input: ImageFilterNode? = null) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-color-filter-v1", filter.canonicalId.value, optionalEffectId(input).value)
    }
    public data class Compose(public val outer: ImageFilterNode, public val inner: ImageFilterNode) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-compose-v1", outer.canonicalId.value, inner.canonicalId.value)
    }
    public data class Blend(public val mode: BlendMode, public val background: ImageFilterNode, public val foreground: ImageFilterNode) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-blend-v1", mode.name, background.canonicalId.value, foreground.canonicalId.value)
    }
    public data class Dilate(public val radiusX: Float, public val radiusY: Float, public val input: ImageFilterNode? = null) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-dilate-v1", radiusX.canonicalBits(), radiusY.canonicalBits(), optionalEffectId(input).value)
    }
    public data class Erode(public val radiusX: Float, public val radiusY: Float, public val input: ImageFilterNode? = null) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-erode-v1", radiusX.canonicalBits(), radiusY.canonicalBits(), optionalEffectId(input).value)
    }
    public data class DistantLitDiffuse(
        public val directionX: Float, public val directionY: Float, public val lightColor: ColorARGB,
        public val surfaceScale: Float, public val kd: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = lightingId("image-filter-distant-lit-diffuse-v1", directionX, directionY, lightColor, surfaceScale, kd, input)
    }
    public data class PointLitDiffuse(
        public val location: Point2F32, public val lightColor: ColorARGB, public val surfaceScale: Float,
        public val kd: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-point-lit-diffuse-v1", effectPointId(location).value, effectColorId(lightColor).value, surfaceScale.canonicalBits(), kd.canonicalBits(), optionalEffectId(input).value)
    }
    public data class SpotLitDiffuse(
        public val location: Point2F32, public val target: Point2F32, public val specularExponent: Float,
        public val cutoffAngle: Float, public val lightColor: ColorARGB, public val surfaceScale: Float,
        public val kd: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = spotLightingId("image-filter-spot-lit-diffuse-v1", location, target, specularExponent, cutoffAngle, lightColor, surfaceScale, kd, null, input)
    }
    public data class DistantLitSpecular(
        public val directionX: Float, public val directionY: Float, public val lightColor: ColorARGB,
        public val surfaceScale: Float, public val ks: Float, public val shininess: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-distant-lit-specular-v1", directionX.canonicalBits(), directionY.canonicalBits(), effectColorId(lightColor).value, surfaceScale.canonicalBits(), ks.canonicalBits(), shininess.canonicalBits(), optionalEffectId(input).value)
    }
    public data class PointLitSpecular(
        public val location: Point2F32, public val lightColor: ColorARGB, public val surfaceScale: Float,
        public val ks: Float, public val shininess: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-point-lit-specular-v1", effectPointId(location).value, effectColorId(lightColor).value, surfaceScale.canonicalBits(), ks.canonicalBits(), shininess.canonicalBits(), optionalEffectId(input).value)
    }
    public data class SpotLitSpecular(
        public val location: Point2F32, public val target: Point2F32, public val specularExponent: Float,
        public val cutoffAngle: Float, public val lightColor: ColorARGB, public val surfaceScale: Float,
        public val ks: Float, public val shininess: Float, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = spotLightingId("image-filter-spot-lit-specular-v1", location, target, specularExponent, cutoffAngle, lightColor, surfaceScale, ks, shininess, input)
    }
    public data class Offset(public val dx: Float, public val dy: Float, public val input: ImageFilterNode? = null) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-offset-v1", dx.canonicalBits(), dy.canonicalBits(), optionalEffectId(input).value)
    }
    public class Tile private constructor(src: RectF32, dst: RectF32, public val input: ImageFilterNode?) : ImageFilterNode {
        private val storedSrc: RectF32 = src.copy()
        private val storedDst: RectF32 = dst.copy()
        public fun copySource(): RectF32 = storedSrc.copy()
        public fun copyDestination(): RectF32 = storedDst.copy()
        override val canonicalId: CanonicalId = canonicalId("image-filter-tile-v1", effectRectId(storedSrc).value, effectRectId(storedDst).value, optionalEffectId(input).value)
        override fun equals(other: Any?): Boolean = other is Tile && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object { public fun of(src: RectF32, dst: RectF32, input: ImageFilterNode? = null): Tile = Tile(src, dst, input) }
    }
    public class Merge private constructor(inputs: Collection<ImageFilterNode>) : ImageFilterNode, Iterable<ImageFilterNode> {
        private val values: List<ImageFilterNode> = immutableList(inputs)
        public val inputCount: Int get() = values.size
        public fun inputAt(index: Int): ImageFilterNode = values[index]
        override fun iterator(): Iterator<ImageFilterNode> = values.iterator()
        override val canonicalId: CanonicalId = canonicalSequenceId("image-filter-merge-v1", values.map { it.canonicalId.value })
        override fun equals(other: Any?): Boolean = other is Merge && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object { public fun of(inputs: Collection<ImageFilterNode>): Merge = Merge(inputs) }
    }
    public data class DisplacementMap(
        public val xChannelSelector: ColorChannel, public val yChannelSelector: ColorChannel, public val scale: Float,
        public val displacement: ImageFilterNode, public val input: ImageFilterNode? = null,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId("image-filter-displacement-map-v1", xChannelSelector.name, yChannelSelector.name, scale.canonicalBits(), displacement.canonicalId.value, optionalEffectId(input).value)
    }
    public class Picture private constructor(public val scene: SceneSnapshot, cullRect: RectF32, src: RectF32?) : ImageFilterNode {
        private val storedCullRect: RectF32 = cullRect.copy()
        private val storedSrc: RectF32? = src?.copy()
        public fun copyCullRect(): RectF32 = storedCullRect.copy()
        public fun copySource(): RectF32? = storedSrc?.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "image-filter-picture-v1",
            scene.canonicalId.value,
            effectRectId(storedCullRect).value,
            canonicalOptionalId("source", storedSrc?.let(::effectRectId)).value,
        )
        override fun equals(other: Any?): Boolean = other is Picture && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object {
            public fun of(scene: SceneSnapshot, cullRect: RectF32, src: RectF32? = null): Picture = Picture(scene, cullRect, src)
        }
    }
    public class Magnifier private constructor(src: RectF32, public val zoom: Float, public val inset: Float, public val input: ImageFilterNode?) : ImageFilterNode {
        private val storedSrc: RectF32 = src.copy()
        public fun copySource(): RectF32 = storedSrc.copy()
        override val canonicalId: CanonicalId = canonicalId("image-filter-magnifier-v1", effectRectId(storedSrc).value, zoom.canonicalBits(), inset.canonicalBits(), optionalEffectId(input).value)
        override fun equals(other: Any?): Boolean = other is Magnifier && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object { public fun of(src: RectF32, zoom: Float, inset: Float, input: ImageFilterNode? = null): Magnifier = Magnifier(src, zoom, inset, input) }
    }
    public class MatrixConvolution private constructor(
        public val kernelSize: SizeF32,
        public val kernel: ImmutableFloats,
        public val gain: Float,
        public val bias: Float,
        public val kernelOffset: Vector2F32,
        public val tileMode: TileMode,
        public val convolveAlpha: Boolean,
        public val input: ImageFilterNode?,
    ) : ImageFilterNode {
        override val canonicalId: CanonicalId = canonicalId(
            "image-filter-matrix-convolution-v1", kernelSize.width.canonicalBits(), kernelSize.height.canonicalBits(),
            kernel.canonicalId.value, gain.canonicalBits(), bias.canonicalBits(), effectVectorId(kernelOffset).value,
            tileMode.name, convolveAlpha.toString(), optionalEffectId(input).value,
        )
        override fun equals(other: Any?): Boolean = other is MatrixConvolution && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object {
            public fun of(
                kernelSize: SizeF32,
                kernel: ImmutableFloats,
                gain: Float,
                bias: Float,
                kernelOffset: Vector2F32,
                tileMode: TileMode,
                convolveAlpha: Boolean,
                input: ImageFilterNode? = null,
            ): MatrixConvolution = MatrixConvolution(kernelSize, kernel, gain, bias, kernelOffset, tileMode, convolveAlpha, input)
        }
    }
    public class RuntimeEffect private constructor(
        public val descriptor: RuntimeEffectDescriptor,
        uniforms: Map<String, RuntimeUniformValue>,
        public val childShaderName: String?,
        children: Collection<RuntimeImageFilterChild>,
    ) : ImageFilterNode, Iterable<RuntimeImageFilterChild> {
        private val storedUniforms: Map<String, RuntimeUniformValue> = immutableUniformMap(uniforms)
        private val storedChildren: List<RuntimeImageFilterChild> = immutableList(children)
        init {
            require(descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER) { "Runtime image filter must use IMAGE_FILTER ABI" }
            require(childShaderName == null || childShaderName.isNotBlank()) { "Runtime child shader name must not be blank" }
            require(storedChildren.map(RuntimeImageFilterChild::name).distinct().size == storedChildren.size) {
                "Runtime image-filter child names must be unique"
            }
            require(childShaderName == null || storedChildren.none { it.name == childShaderName }) {
                "Runtime image-filter child names must be unique"
            }
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
        public fun childAt(index: Int): RuntimeImageFilterChild = storedChildren[index]
        override fun iterator(): Iterator<RuntimeImageFilterChild> = storedChildren.iterator()
        override val canonicalId: CanonicalId = canonicalId(
            "image-filter-runtime-effect-v1", descriptor.canonicalId.value, uniformMapId(storedUniforms).value,
            if (childShaderName == null) "absent" else "present", childShaderName.orEmpty(),
            canonicalSequenceId("children", storedChildren.map { it.canonicalId.value }).value,
        )
        override fun equals(other: Any?): Boolean = other is RuntimeEffect && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
        public companion object {
            public fun of(
                descriptor: RuntimeEffectDescriptor,
                uniforms: Map<String, RuntimeUniformValue>,
                childShaderName: String?,
                children: Collection<RuntimeImageFilterChild>,
            ): RuntimeEffect = RuntimeEffect(descriptor, uniforms, childShaderName, children)
        }
    }
}

/** Ordered runtime image-filter child; null is semantically distinct from an absent entry. */
public data class RuntimeImageFilterChild(public val name: String, public val filter: ImageFilterNode?) : CanonicalValue {
    init { require(name.isNotBlank()) { "RuntimeImageFilterChild.name must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("runtime-image-filter-child-v1", name, optionalEffectId(filter).value)
}

/** Public blender variants, kept separate from backend blend state. */
public sealed interface BlenderNode : CanonicalValue {
    public data class Mode(public val mode: BlendMode) : BlenderNode {
        override val canonicalId: CanonicalId = canonicalId("blender-mode-v1", mode.name)
    }
    public data class Arithmetic(public val k1: Float, public val k2: Float, public val k3: Float, public val k4: Float) : BlenderNode {
        override val canonicalId: CanonicalId = canonicalId("blender-arithmetic-v1", k1.canonicalBits(), k2.canonicalBits(), k3.canonicalBits(), k4.canonicalBits())
    }
}

/** Ordered effect axis for a draw. */
public sealed interface EffectStack : CanonicalValue {
    public data object Empty : EffectStack {
        override val canonicalId: CanonicalId = canonicalId("effect-stack-empty-v1")
    }
    public class Entries internal constructor(effects: Collection<EffectNode>) : EffectStack, Iterable<EffectNode> {
        private val values: List<EffectNode> = immutableList(effects)
        public val effectCount: Int get() = values.size
        public fun effectAt(index: Int): EffectNode = values[index]
        override fun iterator(): Iterator<EffectNode> = values.iterator()
        override val canonicalId: CanonicalId = canonicalSequenceId("effect-stack-v1", values.map { it.canonicalId.value })
        override fun equals(other: Any?): Boolean = other is Entries && canonicalId == other.canonicalId
        override fun hashCode(): Int = canonicalId.hashCode()
    }
    public companion object {
        public fun of(effects: Collection<EffectNode>): EffectStack = if (effects.isEmpty()) Empty else Entries(effects)
    }
}

/** Explicit bounded validation settings for recursive material and effect graphs. */
public data class GraphLimits(public val maxDepth: Int = 64, public val maxNodes: Int = 4_096) {
    init {
        require(maxDepth > 0) { "GraphLimits.maxDepth must be positive" }
        require(maxNodes > 0) { "GraphLimits.maxNodes must be positive" }
    }
}

/** Public result of iterative graph validation, with no recursive traversal or backend allocation. */
public sealed interface GraphValidationResult {
    public data object Valid : GraphValidationResult
    public data class DepthLimitExceeded(public val maxDepth: Int, public val observedDepth: Int) : GraphValidationResult
    public data class NodeLimitExceeded(public val maxNodes: Int, public val observedNodes: Int) : GraphValidationResult
}

/** Validates or bounds a material graph before any backend is asked to plan it. */
public object MaterialGraph {
    public fun validate(root: MaterialNode, limits: GraphLimits = GraphLimits()): GraphValidationResult =
        validateGraph(listOf(GraphWork.Material(root, 1)), limits)
    public fun bounded(root: MaterialNode, limits: GraphLimits = GraphLimits()): MaterialGraphBuildResult =
        when (val validation = validate(root, limits)) {
            GraphValidationResult.Valid -> MaterialGraphBuildResult.Accepted(root)
            else -> MaterialGraphBuildResult.Rejected(validation)
        }
}

/** Validates or bounds an effect graph before any backend is asked to plan it. */
public object EffectGraph {
    public fun validate(root: EffectNode, limits: GraphLimits = GraphLimits()): GraphValidationResult =
        validateGraph(listOf(GraphWork.Effect(root, 1)), limits)
    public fun validate(stack: EffectStack, limits: GraphLimits = GraphLimits()): GraphValidationResult = when (stack) {
        EffectStack.Empty -> GraphValidationResult.Valid
        is EffectStack.Entries -> validateGraph(stack.map { GraphWork.Effect(it, 1) }, limits)
    }
}

/** Result of a bounded material construction request. */
public sealed interface MaterialGraphBuildResult {
    public data class Accepted(public val root: MaterialNode) : MaterialGraphBuildResult
    public data class Rejected(public val validation: GraphValidationResult) : MaterialGraphBuildResult
}

private sealed interface GraphWork {
    val depth: Int
    data class Material(val value: MaterialNode, override val depth: Int) : GraphWork
    data class Effect(val value: EffectNode, override val depth: Int) : GraphWork
    data class Scene(val value: SceneSnapshot, override val depth: Int) : GraphWork
}

private fun validateGraph(initial: Collection<GraphWork>, limits: GraphLimits): GraphValidationResult {
    val pending = ArrayDeque<GraphWork>()
    initial.toList().asReversed().forEach(pending::addLast)
    var nodes = 0
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        if (current.depth > limits.maxDepth) return GraphValidationResult.DepthLimitExceeded(limits.maxDepth, current.depth)
        nodes += 1
        if (nodes > limits.maxNodes) return GraphValidationResult.NodeLimitExceeded(limits.maxNodes, nodes)
        val children = when (current) {
            is GraphWork.Material -> materialChildren(current.value, current.depth + 1)
            is GraphWork.Effect -> effectChildren(current.value, current.depth + 1)
            is GraphWork.Scene -> sceneChildren(current.value, current.depth + 1)
        }
        children.asReversed().forEach(pending::addLast)
    }
    return GraphValidationResult.Valid
}

private fun materialChildren(value: MaterialNode, depth: Int): List<GraphWork> = when (value) {
    MaterialNode.Transparent,
    is MaterialNode.Solid,
    is MaterialNode.LinearGradient,
    is MaterialNode.RadialGradient,
    is MaterialNode.SweepGradient,
    is MaterialNode.ConicalGradient,
    is MaterialNode.ImageSample,
    is MaterialNode.PerlinNoise,
    is MaterialNode.FractalNoise,
    -> emptyList()
    is MaterialNode.Blend -> listOf(GraphWork.Material(value.dst, depth), GraphWork.Material(value.src, depth))
    is MaterialNode.RuntimeEffect -> value.map { GraphWork.Material(it.material, depth) }
    is MaterialNode.WithLocalMatrix -> listOf(GraphWork.Material(value.material, depth))
    is MaterialNode.WithColorFilter -> listOf(GraphWork.Material(value.material, depth), GraphWork.Effect(value.filter, depth))
    is MaterialNode.Opacity -> listOf(GraphWork.Material(value.material, depth))
    is MaterialNode.WithWorkingColorSpace -> listOf(GraphWork.Material(value.material, depth))
    is MaterialNode.CoordClamp -> listOf(GraphWork.Material(value.material, depth))
}

private fun effectChildren(value: EffectNode, depth: Int): List<GraphWork> = when (value) {
    is ColorFilterNode.Compose -> listOf(GraphWork.Effect(value.outer, depth), GraphWork.Effect(value.inner, depth))
    is ColorFilterNode.Lerp -> listOf(GraphWork.Effect(value.dst, depth), GraphWork.Effect(value.src, depth))
    is ColorFilterNode.RuntimeEffect -> value.map { GraphWork.Effect(it.filter, depth) }
    is MaskFilterNode.Shader -> listOf(GraphWork.Material(value.material, depth))
    is ImageFilterNode.Crop -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.Blur -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.DropShadow -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.ColorFilter -> listOfNotNull(GraphWork.Effect(value.filter, depth), value.input?.let { GraphWork.Effect(it, depth) })
    is ImageFilterNode.Compose -> listOf(GraphWork.Effect(value.outer, depth), GraphWork.Effect(value.inner, depth))
    is ImageFilterNode.Blend -> listOf(GraphWork.Effect(value.background, depth), GraphWork.Effect(value.foreground, depth))
    is ImageFilterNode.Dilate -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.Erode -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.DistantLitDiffuse -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.PointLitDiffuse -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.SpotLitDiffuse -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.DistantLitSpecular -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.PointLitSpecular -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.SpotLitSpecular -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.Offset -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.Tile -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.Merge -> value.map { GraphWork.Effect(it, depth) }
    is ImageFilterNode.DisplacementMap -> listOfNotNull(GraphWork.Effect(value.displacement, depth), value.input?.let { GraphWork.Effect(it, depth) })
    is ImageFilterNode.Picture -> listOf(GraphWork.Scene(value.scene, depth))
    is ImageFilterNode.Magnifier -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.MatrixConvolution -> value.input?.let { listOf(GraphWork.Effect(it, depth)) }.orEmpty()
    is ImageFilterNode.RuntimeEffect -> value.mapNotNull { it.filter?.let { filter -> GraphWork.Effect(filter, depth) } }
    is ColorFilterNode.Matrix,
    is ColorFilterNode.Blend,
    is ColorFilterNode.Table,
    is ColorFilterNode.Lighting,
    ColorFilterNode.SRGBToLinear,
    ColorFilterNode.LinearToSRGB,
    is ColorFilterNode.HSLAMatrix,
    ColorFilterNode.HighContrast,
    ColorFilterNode.Luma,
    ColorFilterNode.Overdraw,
    is MaskFilterNode.Blur,
    is MaskFilterNode.Table,
    is PathEffectNode.Dash,
    is PathEffectNode.Corner,
    is PathEffectNode.Discrete,
    is PathEffectNode.Path1D,
    is PathEffectNode.Path2D,
    is PathEffectNode.Trim,
    -> emptyList()
}

private fun sceneChildren(value: SceneSnapshot, depth: Int): List<GraphWork> = buildList {
    value.forEach { command ->
        when (command) {
            is SceneCommand.Draw -> {
                add(GraphWork.Material(command.node.material, depth))
                stackEffects(command.node.effects).forEach { add(GraphWork.Effect(it, depth)) }
            }
            is SceneCommand.BeginLayer -> {
                command.descriptor.material?.let { add(GraphWork.Material(it, depth)) }
                stackEffects(command.descriptor.backdrop).forEach { add(GraphWork.Effect(it, depth)) }
                stackEffects(command.descriptor.effects).forEach { add(GraphWork.Effect(it, depth)) }
            }
            is SceneCommand.Clear,
            SceneCommand.EndLayer,
            is SceneCommand.State,
            is SceneCommand.Annotation,
            is SceneCommand.Readback,
            -> Unit
        }
    }
}

private fun stackEffects(value: EffectStack): List<EffectNode> = when (value) {
    EffectStack.Empty -> emptyList()
    is EffectStack.Entries -> value.toList()
}

private fun effectColorId(color: ColorARGB): CanonicalId = canonicalId("color", color.value.toString())
private fun effectPointId(point: Point2F32): CanonicalId = canonicalId("point", point.x.canonicalBits(), point.y.canonicalBits())
private fun effectVectorId(vector: Vector2F32): CanonicalId = canonicalId("vector", vector.x.canonicalBits(), vector.y.canonicalBits())
private fun effectRectId(rect: RectF32): CanonicalId = canonicalId("rect", rect.left.canonicalBits(), rect.top.canonicalBits(), rect.right.canonicalBits(), rect.bottom.canonicalBits())
private fun optionalEffectId(value: EffectNode?): CanonicalId = canonicalOptionalId("effect", value?.canonicalId)
private fun effectPathId(path: PathF32): CanonicalId = canonicalId(
    "path-v1",
    path.fillRule.name,
    canonicalSequenceId("segments", path.map { effectPathSegmentId(it).value }).value,
)
private fun effectPathSegmentId(segment: PathSegmentF32): CanonicalId = when (segment) {
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
        "arc", segment.radius.x.canonicalBits(), segment.radius.y.canonicalBits(), segment.xAxisRotation.canonicalBits(),
        segment.largeArc.toString(), segment.sweep.toString(), segment.point.x.canonicalBits(), segment.point.y.canonicalBits(),
    )
    PathSegmentF32.Close -> canonicalId("close")
}
private fun lightingId(tag: String, x: Float, y: Float, color: ColorARGB, surface: Float, coefficient: Float, input: ImageFilterNode?): CanonicalId = canonicalId(
    tag, x.canonicalBits(), y.canonicalBits(), effectColorId(color).value, surface.canonicalBits(), coefficient.canonicalBits(), optionalEffectId(input).value,
)
private fun spotLightingId(
    tag: String,
    location: Point2F32,
    target: Point2F32,
    exponent: Float,
    cutoff: Float,
    color: ColorARGB,
    surface: Float,
    coefficient: Float,
    shininess: Float?,
    input: ImageFilterNode?,
): CanonicalId = canonicalId(
    tag, effectPointId(location).value, effectPointId(target).value, exponent.canonicalBits(), cutoff.canonicalBits(),
    effectColorId(color).value, surface.canonicalBits(), coefficient.canonicalBits(),
    if (shininess == null) "absent" else "present", shininess?.canonicalBits().orEmpty(), optionalEffectId(input).value,
)
