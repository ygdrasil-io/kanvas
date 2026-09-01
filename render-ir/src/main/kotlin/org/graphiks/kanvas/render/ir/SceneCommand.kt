package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorF32
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
    )
}

/** A serializable layer boundary with no renderer allocation or handle. */
public class LayerDescriptor private constructor(
    public val label: String?,
    bounds: RectF32?,
    public val material: MaterialNode?,
    public val blend: BlendNode,
    public val clip: ClipStackNode,
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
        blend.canonicalId.value,
        clip.canonicalId.value,
        backdrop.canonicalId.value,
        effects.canonicalId.value,
        matrixId("transform", transform).value,
    )

    public companion object {
        public fun of(
            label: String? = null,
            bounds: RectF32? = null,
            material: MaterialNode? = null,
            blend: BlendNode = BlendNode.SrcOver,
            clip: ClipStackNode = ClipStackNode.Empty,
            backdrop: EffectStack = EffectStack.Empty,
            effects: EffectStack = EffectStack.Empty,
            transform: Matrix3x3F32 = Matrix3x3F32.Identity,
        ): LayerDescriptor = LayerDescriptor(label, bounds, material, blend, clip, backdrop, effects, transform)
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
