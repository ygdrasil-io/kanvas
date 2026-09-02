package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/** The independent axes that make up a normalized draw. */
public data class DrawNode(
    public val geometry: GeometryNode,
    public val material: MaterialNode,
    public val coverage: CoverageRequest,
    public val clip: ClipStackNode,
    public val blend: BlendNode,
    public val effects: EffectStack,
    public val transform: Matrix3x3F32,
    /** The public operation which supplied this normalized draw. */
    public val origin: DrawOrigin = DrawOrigin.RECT,
    /**
     * Complete public paint state. Null is deliberate for image-family operations
     * recorded without a paint.
     */
    public val paint: PaintNode? = null,
    /** Image resource used by image, lattice, atlas, and picture-independent draw forms. */
    public val resource: ImageResourceSnapshot? = null,
    /** Operation-level blend mode (mesh/atlas), distinct from PaintNode.blendMode. */
    public val operationBlendMode: BlendMode? = null,
) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId(
        "draw-node-v1",
        geometry.canonicalId.value,
        material.canonicalId.value,
        coverage.canonicalId.value,
        clip.canonicalId.value,
        blend.canonicalId.value,
        effects.canonicalId.value,
        matrixId("transform", transform).value,
        origin.name,
        canonicalOptionalId("paint", paint?.canonicalId).value,
        canonicalOptionalId("resource", resource?.canonicalId).value,
        canonicalOptionalId("operation-blend-mode", operationBlendMode?.let { canonicalId("mode", it.name) }).value,
    )
}

/** Public display-operation provenance retained where normalized geometry alone is ambiguous. */
public enum class DrawOrigin {
    RECT,
    RRECT,
    DOUBLE_RRECT,
    PATH,
    TEXT_EXPANDED_PATH,
    POINT,
    POINTS,
    IMAGE,
    IMAGE_NINE,
    IMAGE_LATTICE,
    PICTURE,
    TEXT,
    VERTICES,
    MESH,
    ATLAS,
}

/** Backend-neutral paint style, deliberately independent from the public Canvas enum. */
public enum class PaintStyleNode { FILL, STROKE, STROKE_AND_FILL }
public enum class StrokeCapNode { BUTT, ROUND, SQUARE }
public enum class StrokeJoinNode { MITER, ROUND, BEVEL }

/**
 * Complete public paint value for lossless capture.  The shader, blend mode and
 * custom blender are deliberately independent: Canvas allows their values to
 * coexist, so neither may overwrite the other during normalization.
 */
public data class PaintNode(
    public val color: ColorARGB,
    public val shader: MaterialNode?,
    public val blendMode: BlendMode,
    public val blender: BlenderNode?,
    public val colorFilter: ColorFilterNode?,
    public val maskFilter: MaskFilterNode?,
    public val pathEffect: PathEffectNode?,
    public val imageFilter: ImageFilterNode?,
    public val style: PaintStyleNode,
    public val strokeWidth: Float,
    public val strokeCap: StrokeCapNode,
    public val strokeJoin: StrokeJoinNode,
    public val strokeMiter: Float,
    public val antiAlias: Boolean,
) : CanonicalValue {
    override val canonicalId: CanonicalId = canonicalId(
        "paint-node-v1",
        color.value.toString(),
        canonicalOptionalId("shader", shader?.canonicalId).value,
        blendMode.name,
        canonicalOptionalId("blender", blender?.canonicalId).value,
        canonicalOptionalId("color-filter", colorFilter?.canonicalId).value,
        canonicalOptionalId("mask-filter", maskFilter?.canonicalId).value,
        canonicalOptionalId("path-effect", pathEffect?.canonicalId).value,
        canonicalOptionalId("image-filter", imageFilter?.canonicalId).value,
        style.name,
        strokeWidth.canonicalBits(),
        strokeCap.name,
        strokeJoin.name,
        strokeMiter.canonicalBits(),
        antiAlias.toString(),
    )
}

/** A serializable layer boundary with no renderer allocation or handle. */
public class LayerDescriptor private constructor(
    public val label: String?,
    bounds: RectF32?,
    public val material: MaterialNode?,
    /** Complete public paint source retained alongside normalized material. */
    public val paint: PaintNode?,
    public val blend: BlendNode,
    public val clip: ClipStackNode,
    /** Clip reapplied while compositing this layer, distinct from child clip state. */
    public val compositeClip: ClipStackNode?,
    public val backdrop: EffectStack,
    public val effects: EffectStack,
    public val transform: Matrix3x3F32,
) : CanonicalValue {
    private val storedBounds: RectF32? = bounds?.copy()

    init { require(label == null || label.isNotBlank()) { "LayerDescriptor.label must not be blank" } }

    public fun copyBounds(): RectF32? = storedBounds?.copy()

    override val canonicalId: CanonicalId = canonicalId(
        "layer-descriptor-v3",
        label.orEmpty(),
        canonicalOptionalId("bounds", storedBounds?.let { rectId("value", it) }).value,
        canonicalOptionalId("material", material?.canonicalId).value,
        canonicalOptionalId("paint", paint?.canonicalId).value,
        blend.canonicalId.value,
        clip.canonicalId.value,
        canonicalOptionalId("composite-clip", compositeClip?.canonicalId).value,
        backdrop.canonicalId.value,
        effects.canonicalId.value,
        matrixId("transform", transform).value,
    )

    public companion object {
        public fun of(
            label: String? = null,
            bounds: RectF32? = null,
            material: MaterialNode? = null,
            paint: PaintNode? = null,
            blend: BlendNode = BlendNode.SrcOver,
            clip: ClipStackNode = ClipStackNode.Empty,
            compositeClip: ClipStackNode? = null,
            backdrop: EffectStack = EffectStack.Empty,
            effects: EffectStack = EffectStack.Empty,
            transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        ): LayerDescriptor = LayerDescriptor(label, bounds, material, paint, blend, clip, compositeClip, backdrop, effects, transform)
    }
}

/** A serializable readback request with an application-owned stable name and bounds. */
public class ReadbackRequest private constructor(public val label: String, bounds: RectF32) : CanonicalValue {
    private val storedBounds: RectF32 = bounds.copy()

    init { require(label.isNotBlank()) { "ReadbackRequest.label must not be blank" } }

    public fun copyBounds(): RectF32 = storedBounds.copy()
    override val canonicalId: CanonicalId = canonicalId("readback-request-v2", label, rectId("bounds", storedBounds).value)

    public companion object {
        public fun of(label: String, bounds: RectF32): ReadbackRequest = ReadbackRequest(label, bounds)
    }
}

/** Ordered commands in a [SceneSnapshot]. */
public sealed interface SceneCommand : CanonicalValue {
    public data class Draw(public val node: DrawNode) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId("scene-command-draw-v1", node.canonicalId.value)
    }

    public data class Clear(public val color: ColorF32) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-clear-v1",
            color.red.canonicalBits(), color.green.canonicalBits(), color.blue.canonicalBits(), color.alpha.canonicalBits(),
        )
    }

    /** Fill the canvas using the public blend mode; this is not a clear. */
    public data class DrawColor(
        public val color: ColorARGB,
        public val mode: BlendMode,
        public val transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        public val clip: ClipStackNode = ClipStackNode.Empty,
    ) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-draw-color-v2", color.value.toString(), mode.name,
            matrixId("transform", transform).value, clip.canonicalId.value,
        )
    }

    /** Typed recorded state marker; it is not an opaque State string payload. */
    public data class SetTransform(public val matrix: Matrix3x3F32) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-set-transform-v1", matrixId("matrix", matrix).value,
        )
    }

    /** Typed recorded state marker; it preserves the clip kind and ordered entries. */
    public data class SetClip(public val clip: ClipStackNode) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-set-clip-v1", clip.canonicalId.value,
        )
    }

    public data class BeginLayer(public val descriptor: LayerDescriptor) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId("scene-command-begin-layer-v1", descriptor.canonicalId.value)
    }

    public data object EndLayer : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId("scene-command-end-layer-v1")
    }

    /** Explicit serializable state for extensions that do not need a backend object. */
    public class State private constructor(
        public val name: String,
        entries: Map<String, String>,
    ) : SceneCommand {
        private val values: Map<String, String> = immutableSortedMap(entries)

        init { require(name.isNotBlank()) { "SceneCommand.State.name must not be blank" } }

        public fun entries(): Map<String, String> = values
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-state-v2",
            name,
            canonicalSequenceId(
                "entries",
                values.map { (key, value) -> canonicalId("entry", key, value).value },
            ).value,
        )

        public companion object {
            public fun of(name: String, entries: Map<String, String>): State = State(name, entries)
        }
    }

    /** Metadata annotation with the recorded canvas region. */
    public class Annotation private constructor(bounds: RectF32, public val key: String, public val value: String) : SceneCommand {
        private val storedBounds: RectF32 = bounds.copy()

        init {
            require(key.isNotBlank()) { "SceneCommand.Annotation.key must not be blank" }
            require(value.isNotBlank()) { "SceneCommand.Annotation.value must not be blank" }
        }

        public fun copyBounds(): RectF32 = storedBounds.copy()
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-annotation-v2",
            rectId("bounds", storedBounds).value,
            key,
            value,
        )

        public companion object {
            public fun of(bounds: RectF32, key: String, value: String): Annotation = Annotation(bounds, key, value)
        }
    }

    public data class Readback(public val request: ReadbackRequest) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId("scene-command-readback-v1", request.canonicalId.value)
    }
}

private fun rectId(tag: String, rect: RectF32): CanonicalId = canonicalId(
    tag,
    rect.left.canonicalBits(), rect.top.canonicalBits(), rect.right.canonicalBits(), rect.bottom.canonicalBits(),
)

private fun matrixId(tag: String, matrix: Matrix3x3F32): CanonicalId = canonicalId(
    tag,
    matrix.sx.canonicalBits(), matrix.kx.canonicalBits(), matrix.tx.canonicalBits(),
    matrix.ky.canonicalBits(), matrix.sy.canonicalBits(), matrix.ty.canonicalBits(),
    matrix.persp0.canonicalBits(), matrix.persp1.canonicalBits(), matrix.persp2.canonicalBits(),
)
