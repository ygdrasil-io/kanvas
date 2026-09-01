package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorF32
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
        transform.sx.canonicalBits(), transform.kx.canonicalBits(), transform.tx.canonicalBits(),
        transform.ky.canonicalBits(), transform.sy.canonicalBits(), transform.ty.canonicalBits(),
        transform.persp0.canonicalBits(), transform.persp1.canonicalBits(), transform.persp2.canonicalBits(),
    )
}

/** A serializable layer boundary with no renderer allocation or handle. */
public data class LayerDescriptor(public val label: String? = null) : CanonicalValue {
    init { require(label == null || label.isNotBlank()) { "LayerDescriptor.label must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("layer-descriptor-v1", label.orEmpty())
}

/** A serializable readback request with an application-owned stable name. */
public data class ReadbackRequest(public val label: String) : CanonicalValue {
    init { require(label.isNotBlank()) { "ReadbackRequest.label must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId("readback-request-v1", label)
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
        private val values: Map<String, String> = entries.toSortedMap()
        init { require(name.isNotBlank()) { "SceneCommand.State.name must not be blank" } }
        public fun entries(): Map<String, String> = values.toMap()
        override val canonicalId: CanonicalId = canonicalId(
            "scene-command-state-v1", name,
            *values.flatMap { listOf(it.key, it.value) }.toTypedArray(),
        )
        public companion object {
            public fun of(name: String, entries: Map<String, String>): State = State(name, entries)
        }
    }

    public data class Annotation(public val key: String, public val value: String) : SceneCommand {
        init {
            require(key.isNotBlank()) { "SceneCommand.Annotation.key must not be blank" }
            require(value.isNotBlank()) { "SceneCommand.Annotation.value must not be blank" }
        }
        override val canonicalId: CanonicalId = canonicalId("scene-command-annotation-v1", key, value)
    }

    public data class Readback(public val request: ReadbackRequest) : SceneCommand {
        override val canonicalId: CanonicalId = canonicalId("scene-command-readback-v1", request.canonicalId.value)
    }
}
