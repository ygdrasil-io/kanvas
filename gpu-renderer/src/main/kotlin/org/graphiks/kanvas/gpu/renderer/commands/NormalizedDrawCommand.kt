package org.graphiks.kanvas.gpu.renderer.commands

/** Canonical command identifier name used by the package layout target. */
@JvmInline
value class GPUDrawCommandID(val value: Int) {
    init {
        require(value >= 0) { "GPUDrawCommandID must be non-negative" }
    }
}

/** Compatibility alias for the earlier command identifier name. */
typealias GPUCommandId = GPUDrawCommandID

/** Draw command family marker used by analysis and route diagnostics. */
class GPUDrawCommandFamily

/** Stable adapter/source provenance for a normalized draw command. */
class GPUDrawCommandProvenance

/** Paint-order and dependency token for normalized command ordering. */
class GPUDrawOrderingToken

/** Captured conservative command bounds before route analysis. */
class GPUCommandBounds

/** Immutable capture record for normalized command input state. */
class GPUCommandCapture

/** First-slice draw kinds accepted by the normalized command surface. */
enum class GPUDrawKind {
    /** Filled rectangle command family. */
    FillRect,
}

/** Coarse transform classification captured before analysis. */
enum class GPUTransformType {
    /** Identity transform with no coordinate remapping. */
    Identity,
}

/** Coarse clip classification captured before analysis. */
enum class GPUClipKind {
    /** No effective clipping beyond the target. */
    WideOpen,
}

/** Coarse material classification captured before material lowering. */
enum class GPUMaterialKind {
    /** Solid source color material. */
    SolidColor,
}

/** Rectangle geometry in local command coordinates. */
data class GPURect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Conservative command bounds in the coordinate space selected by the caller. */
data class GPUBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Captured transform facts consumed by later coordinate planning. */
data class GPUTransformFacts(
    val type: GPUTransformType,
) {
    /** Creates identity transform facts for first-slice fixtures. */
    companion object {
        /** Returns a transform fact record with identity classification. */
        fun identity(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Identity)
    }
}

/** Captured clip facts consumed by later clip planning. */
data class GPUClipFacts(
    val kind: GPUClipKind,
    val bounds: GPUBounds,
) {
    /** Constructors for common clip fact records. */
    companion object {
        /** Returns a wide-open clip bounded by the provided conservative area. */
        fun wideOpen(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.WideOpen, bounds = bounds)
    }
}

/** Captured render-target facts attached to the command layer. */
data class GPUTargetFacts(
    val width: Int,
    val height: Int,
    val colorFormat: String,
) {
    init {
        require(width > 0) { "GPUTargetFacts.width must be positive" }
        require(height > 0) { "GPUTargetFacts.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUTargetFacts.colorFormat must not be blank" }
    }
}

/** Captured layer facts for normalized commands. */
data class GPULayerFacts(
    val target: GPUTargetFacts,
) {
    /** Constructors for layer fact records. */
    companion object {
        /** Returns a root-layer fact record for the provided target. */
        fun root(target: GPUTargetFacts): GPULayerFacts = GPULayerFacts(target)
    }
}

/** Material descriptor captured before material-source lowering. */
sealed interface GPUMaterialDescriptor {
    /** Coarse material family used by tests and diagnostics. */
    val kind: GPUMaterialKind

    /** Solid color descriptor for the first GPU renderer slice. */
    data class SolidColor(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.SolidColor
    }
}

/** Captured ordering facts for normalized draw commands. */
data class GPUOrderingFacts(
    val paintOrder: Int,
    val dependsOnDestination: Boolean,
    val requiresBarrier: Boolean,
) {
    init {
        require(paintOrder >= 0) { "GPUOrderingFacts.paintOrder must be non-negative" }
    }
}

/** Source adapter information used by diagnostics and dumps. */
data class GPUCommandSource(
    val adapter: String,
    val operation: String,
) {
    init {
        require(adapter.isNotBlank()) { "GPUCommandSource.adapter must not be blank" }
        require(operation.isNotBlank()) { "GPUCommandSource.operation must not be blank" }
    }
}

/** High-level draw command after legacy state has been captured and normalized. */
sealed interface NormalizedDrawCommand {
    /** Recording-local command identifier. */
    val commandId: GPUDrawCommandID
    /** Coarse draw command family. */
    val drawKind: GPUDrawKind
    /** Captured transform facts. */
    val transform: GPUTransformFacts
    /** Captured clip facts. */
    val clip: GPUClipFacts
    /** Captured layer facts. */
    val layer: GPULayerFacts
    /** Captured material descriptor. */
    val material: GPUMaterialDescriptor
    /** Conservative command bounds. */
    val bounds: GPUBounds
    /** Captured ordering facts. */
    val ordering: GPUOrderingFacts
    /** Source adapter provenance. */
    val source: GPUCommandSource

    /** Stable diagnostic label for route and analysis reports. */
    val diagnosticName: String
        get() = "${source.adapter}:${source.operation}#${commandId.value}"

    /** First-slice filled rectangle command with captured state. */
    data class FillRect(
        override val commandId: GPUDrawCommandID,
        val rect: GPURect,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillRect
    }
}
