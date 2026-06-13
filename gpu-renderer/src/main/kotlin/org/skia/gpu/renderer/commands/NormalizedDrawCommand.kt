package org.skia.gpu.renderer.commands

@JvmInline
value class GPUCommandId(val value: Int) {
    init {
        require(value >= 0) { "GPUCommandId must be non-negative" }
    }
}

enum class GPUDrawKind {
    FillRect,
}

enum class GPUTransformType {
    Identity,
}

enum class GPUClipKind {
    WideOpen,
}

enum class GPUMaterialKind {
    SolidColor,
}

data class GPURect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class GPUBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class GPUTransformFacts(
    val type: GPUTransformType,
) {
    companion object {
        fun identity(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Identity)
    }
}

data class GPUClipFacts(
    val kind: GPUClipKind,
    val bounds: GPUBounds,
) {
    companion object {
        fun wideOpen(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.WideOpen, bounds = bounds)
    }
}

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

data class GPULayerFacts(
    val target: GPUTargetFacts,
) {
    companion object {
        fun root(target: GPUTargetFacts): GPULayerFacts = GPULayerFacts(target)
    }
}

sealed interface GPUMaterialDescriptor {
    val kind: GPUMaterialKind

    data class SolidColor(
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.SolidColor
    }
}

data class GPUOrderingFacts(
    val paintOrder: Int,
    val dependsOnDestination: Boolean,
    val requiresBarrier: Boolean,
) {
    init {
        require(paintOrder >= 0) { "GPUOrderingFacts.paintOrder must be non-negative" }
    }
}

data class GPUCommandSource(
    val adapter: String,
    val operation: String,
) {
    init {
        require(adapter.isNotBlank()) { "GPUCommandSource.adapter must not be blank" }
        require(operation.isNotBlank()) { "GPUCommandSource.operation must not be blank" }
    }
}

sealed interface NormalizedDrawCommand {
    val commandId: GPUCommandId
    val drawKind: GPUDrawKind
    val transform: GPUTransformFacts
    val clip: GPUClipFacts
    val layer: GPULayerFacts
    val material: GPUMaterialDescriptor
    val bounds: GPUBounds
    val ordering: GPUOrderingFacts
    val source: GPUCommandSource

    val diagnosticName: String
        get() = "${source.adapter}:${source.operation}#${commandId.value}"

    data class FillRect(
        override val commandId: GPUCommandId,
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
