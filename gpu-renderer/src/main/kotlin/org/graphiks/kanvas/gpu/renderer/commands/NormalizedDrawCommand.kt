package org.graphiks.kanvas.gpu.renderer.commands

import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextArtifactRef

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
enum class GPUDrawCommandFamily {
    /** Rectangle draw family. */
    Rect,
    /** Rounded rectangle draw family. */
    RRect,
    /** Path draw family. */
    Path,
    /** Text draw family. */
    Text,
    /** Image draw family. */
    Image,
    /** Vertices draw family. */
    Vertices,
}

/** Stable adapter/source provenance for a normalized draw command. */
data class GPUDrawCommandProvenance(
    val adapter: String,
    val operation: String,
    val sourceLabel: String,
)

/** Paint-order and dependency token for normalized command ordering. */
@JvmInline
value class GPUDrawOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDrawOrderingToken.value must not be blank" }
    }
}

/** Captured conservative command bounds before route analysis. */
data class GPUCommandBounds(
    val bounds: GPUBounds,
    val coordinateSpace: String,
    val conservative: Boolean,
)

/** Immutable capture record for normalized command input state. */
data class GPUCommandCapture(
    val commandId: GPUDrawCommandID,
    val family: GPUDrawCommandFamily,
    val provenance: GPUDrawCommandProvenance,
    val bounds: GPUCommandBounds,
    val stateHash: String,
)

/** First-slice draw kinds accepted by the normalized command surface. */
enum class GPUDrawKind {
    /** Filled rectangle command family. */
    FillRect,
    /** Filled rounded rectangle command family. */
    FillRRect,
    /** Filled path command family with tessellated vertex buffers. */
    FillPath,
    /** Text run command family with prepared text stack artifacts. */
    DrawTextRun,
}

/** Transform class captured by the command adapter before route analysis. */
enum class GPUTransformType {
    /** Identity transform with no coordinate remapping. */
    Identity,
    /** Pure translation transform that keeps rectangles axis-aligned. */
    Translate,
    /** Axis-aligned scale transform that needs route-specific coverage proof. */
    Scale,
    /** Non-perspective affine transform that needs route-specific coverage proof. */
    Affine,
    /** Perspective transform outside the first native route. */
    Perspective,
    /** Singular transform outside the first native route. */
    Singular,
}

/** Clip class captured by the command adapter before route analysis. */
enum class GPUClipKind {
    /** No effective clipping beyond the target. */
    WideOpen,
    /** A single device-space rectangle scissor clip. */
    DeviceRect,
    /** A clip stack that needs later stencil, mask, or analytic clip work. */
    ComplexStack,
}

/** Coarse material classification captured before material lowering. */
enum class GPUMaterialKind {
    /** Solid source color material. */
    SolidColor,
    /** Linear gradient source material. */
    LinearGradient,
    /** Radial gradient source material. */
    RadialGradient,
    /** Sweep gradient source material. */
    SweepGradient,
}

/** Rectangle geometry in local command coordinates. */
data class GPURect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** X/Y radii for one rounded-rectangle corner. */
data class GPURRectCornerRadii(
    val x: Float,
    val y: Float,
)

/** Rounded rectangle geometry in local command coordinates. */
data class GPURRect(
    val rect: GPURect,
    val topLeft: GPURRectCornerRadii,
    val topRight: GPURRectCornerRadii = topLeft,
    val bottomRight: GPURRectCornerRadii = topRight,
    val bottomLeft: GPURRectCornerRadii = topLeft,
) {
    /** Convenience constructor for uniform rrect radii used by first-slice fixtures. */
    constructor(
        rect: GPURect,
        radiusX: Float,
        radiusY: Float,
    ) : this(
        rect = rect,
        topLeft = GPURRectCornerRadii(x = radiusX, y = radiusY),
        topRight = GPURRectCornerRadii(x = radiusX, y = radiusY),
        bottomRight = GPURRectCornerRadii(x = radiusX, y = radiusY),
        bottomLeft = GPURRectCornerRadii(x = radiusX, y = radiusY),
    )
}

/** Conservative command bounds in the coordinate space selected by the caller. */
data class GPUBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** M15 path-fill facts captured from the legacy path fill before tessellation. */
data class GPUPathFacts(
    val pathKey: String,
    val verbCount: Int,
    val pointCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val finiteProof: String,
    val volatility: String,
    val transformClass: String,
    val edgeCount: Int,
)

/** Captured transform facts owned by commands and consumed by analysis without replaying Canvas state. */
data class GPUTransformFacts(
    val type: GPUTransformType,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
) {
    /** Creates identity transform facts for first-slice fixtures. */
    companion object {
        /** Returns a transform fact record with identity classification. */
        fun identity(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Identity)

        /** Returns a transform fact record with translate-like classification. */
        fun translation(x: Float, y: Float): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Translate,
                translateX = x,
                translateY = y,
            )

        /** Returns a scale transform fact record that routes must explicitly accept or refuse. */
        fun scale(x: Float, y: Float): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Scale,
                scaleX = x,
                scaleY = y,
            )

        /** Returns a non-perspective affine transform fact record for route-specific validation. */
        fun affine(
            scaleX: Float,
            skewX: Float,
            skewY: Float,
            scaleY: Float,
            translateX: Float = 0f,
            translateY: Float = 0f,
        ): GPUTransformFacts =
            GPUTransformFacts(
                type = GPUTransformType.Affine,
                translateX = translateX,
                translateY = translateY,
                scaleX = scaleX,
                scaleY = scaleY,
                skewX = skewX,
                skewY = skewY,
            )

        /** Returns a transform fact record with perspective classification. */
        fun perspective(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Perspective)

        /** Returns a transform fact record with singular classification. */
        fun singular(): GPUTransformFacts = GPUTransformFacts(GPUTransformType.Singular)
    }
}

/** Captured clip facts owned by commands; complex stacks remain explicit refusal inputs for this slice. */
data class GPUClipFacts(
    val kind: GPUClipKind,
    val bounds: GPUBounds,
) {
    /** Constructors for common clip fact records. */
    companion object {
        /** Returns a wide-open clip bounded by the provided conservative area. */
        fun wideOpen(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.WideOpen, bounds = bounds)

        /** Returns a single device-rectangle clip for first-route scissor fixtures. */
        fun deviceRect(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.DeviceRect, bounds = bounds)

        /** Returns a complex clip stack fact record that must refuse in the first route. */
        fun complexStack(bounds: GPUBounds): GPUClipFacts =
            GPUClipFacts(kind = GPUClipKind.ComplexStack, bounds = bounds)
    }
}

/** Captured render-target facts needed for first-route validation without exposing backend texture handles. */
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

/** Layer scope classification captured before layer planning and offscreen materialization. */
enum class GPULayerScopeKind {
    /** Root target scope with no saveLayer isolation. */
    Root,
    /** saveLayer or equivalent offscreen scope requiring later proof. */
    SaveLayer,
}

/** Captured layer facts that keep layer/filter/destination-read requirements visible to analysis. */
data class GPULayerFacts(
    val target: GPUTargetFacts,
    val scopeKind: GPULayerScopeKind = GPULayerScopeKind.Root,
    val requiresFilter: Boolean = false,
    val requiresDestinationRead: Boolean = false,
) {
    /** Constructors for layer fact records. */
    companion object {
        /** Returns a root-layer fact record for the provided target. */
        fun root(target: GPUTargetFacts): GPULayerFacts = GPULayerFacts(target)

        /** Returns a saveLayer fact record that remains refused by the first route. */
        fun saveLayer(target: GPUTargetFacts): GPULayerFacts =
            GPULayerFacts(target = target, scopeKind = GPULayerScopeKind.SaveLayer)
    }
}

/** Blend classification captured before fixed-function blend planning and destination-read strategy selection. */
enum class GPUBlendKind {
    /** Source-over fixed-function blend accepted by the first route. */
    SrcOver,
    /** Unsupported blend mode that must refuse deterministically. */
    Unsupported,
}

/** Captured blend facts; unsupported or destination-reading blends are refused before pass construction. */
data class GPUBlendFacts(
    val kind: GPUBlendKind,
    val modeLabel: String,
    val requiresDestinationRead: Boolean,
) {
    /** Constructors for first-route blend fact records. */
    companion object {
        /** Returns accepted source-over fixed-function blend facts. */
        fun srcOver(): GPUBlendFacts =
            GPUBlendFacts(kind = GPUBlendKind.SrcOver, modeLabel = "src_over", requiresDestinationRead = false)

        /** Returns an unsupported blend mode fact record. */
        fun unsupported(modeLabel: String): GPUBlendFacts =
            GPUBlendFacts(kind = GPUBlendKind.Unsupported, modeLabel = modeLabel, requiresDestinationRead = false)

        /** Returns a blend fact record that requires destination-read planning. */
        fun destinationReadRequired(): GPUBlendFacts =
            GPUBlendFacts(kind = GPUBlendKind.SrcOver, modeLabel = "dst_read", requiresDestinationRead = true)
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

    /** Linear gradient descriptor with start/end colors and tile mode for the first GPU expansion slice. */
    data class LinearGradient(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String = "clamp",
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.LinearGradient
    }

    /** Radial gradient descriptor with center, radius, and tile mode for M14. */
    data class RadialGradient(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String = "clamp",
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.RadialGradient
    }

    /** Sweep gradient descriptor with center, start/end angles, and tile mode for M14. */
    data class SweepGradient(
        val centerX: Float,
        val centerY: Float,
        val startAngle: Float,
        val endAngle: Float,
        val startR: Float,
        val startG: Float,
        val startB: Float,
        val startA: Float,
        val endR: Float,
        val endG: Float,
        val endB: Float,
        val endA: Float,
        val tileMode: String = "clamp",
    ) : GPUMaterialDescriptor {
        override val kind: GPUMaterialKind = GPUMaterialKind.SweepGradient
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

/** Builds Kanvas-owned first-route FillRect commands from already-normalized facts. */
object GPUFillRectCommandBuilder {
    /**
     * Builds an immutable FillRect command from facts already captured by the caller.
     *
     * Ownership stays with the command package: this builder records geometry,
     * transform, clip, layer, material, blend, ordering, and source provenance
     * without lowering materials, allocating resources, or choosing a backend.
     * Defaults are deliberately narrow: root layer, source-over blend, identity
     * transform, and a wide-open clip derived from the rectangle bounds. Invalid
     * or unsupported facts are preserved so analysis can return stable terminal
     * diagnostics instead of hiding failures during command construction.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rect: GPURect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillRect"),
    ): NormalizedDrawCommand.FillRect {
        val bounds = rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRect(
            commandId = commandId,
            rect = rect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }
}

/** Builds Kanvas-owned first-expansion FillRRect commands from already-normalized facts. */
object GPUFillRRectCommandBuilder {
    /**
     * Builds an immutable FillRRect command from facts already captured by the caller.
     *
     * This mirrors [GPUFillRectCommandBuilder] while keeping rrect radii as
     * command-owned geometry facts. Radius normalization and route acceptance
     * remain analysis responsibilities so unsupported rounded rectangles can
     * refuse with stable diagnostics instead of being approximated.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rrect: GPURRect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillRRect"),
    ): NormalizedDrawCommand.FillRRect {
        val bounds = rrect.rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRRect(
            commandId = commandId,
            rrect = rrect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }
}

/** Builds Kanvas-owned first-expansion LinearGradient rect commands from already-normalized facts. */
object GPULinearGradientCommandBuilder {
    /**
     * Builds an immutable FillRect command with a linear gradient material descriptor.
     *
     * The builder reuses the FillRect command family with a
     * [GPUMaterialDescriptor.LinearGradient] material so the planner can accept
     * gradient rects without a new command family. Gradient-specific validation
     * (non-degenerate endpoints, finite colors, valid tile mode) is deferred to
     * analysis-time refusal checks.
     */
    fun build(
        commandId: GPUDrawCommandID,
        rect: GPURect,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor.LinearGradient,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "linearGradientRect"),
    ): NormalizedDrawCommand.FillRect {
        val bounds = rect.toBounds()
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = bounds)
        return NormalizedDrawCommand.FillRect(
            commandId = commandId,
            rect = rect,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = bounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }
}

/** Builds Kanvas-owned M15 path-fill commands from tessellated vertex buffers. */
object GPUFillPathCommandBuilder {
    /** Builds a FillPath normalized command with tessellation facts bound to the target and material. */
    fun build(
        commandId: GPUDrawCommandID,
        pathKey: String,
        pathDescriptor: GPUPathFacts,
        tessellatedVertices: List<Float>,
        contourStarts: List<Int>,
        edgeCount: Int,
        target: GPUTargetFacts,
        material: GPUMaterialDescriptor,
        transform: GPUTransformFacts = GPUTransformFacts.identity(),
        clip: GPUClipFacts? = null,
        layer: GPULayerFacts? = null,
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        paintOrder: Int = 0,
        source: GPUCommandSource = GPUCommandSource(adapter = "gpu-renderer", operation = "fillPath.shadow"),
    ): NormalizedDrawCommand.FillPath {
        val vertexCount = tessellatedVertices.size / 2
        val minBounds = if (vertexCount > 0) {
            var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
            var i = 0
            while (i < tessellatedVertices.size) {
                val x = tessellatedVertices[i]; val y = tessellatedVertices[i + 1]
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                i += 2
            }
            GPUBounds(minX, minY, maxX, maxY)
        } else {
            GPUBounds(0f, 0f, 0f, 0f)
        }
        val resolvedClip = clip ?: GPUClipFacts.wideOpen(bounds = minBounds)
        return NormalizedDrawCommand.FillPath(
            commandId = commandId,
            pathKey = pathKey,
            pathDescriptor = pathDescriptor,
            tessellatedVertices = tessellatedVertices,
            contourStarts = contourStarts,
            totalVertexCount = vertexCount,
            edgeCount = edgeCount,
            transform = transform,
            clip = resolvedClip,
            layer = layer ?: GPULayerFacts.root(target = target),
            material = material,
            blend = blend,
            bounds = minBounds,
            ordering = GPUOrderingFacts(
                paintOrder = paintOrder,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
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
    /** Captured blend facts. */
    val blend: GPUBlendFacts
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
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillRect
    }

    /** First-expansion filled rounded rectangle command with captured state. */
    data class FillRRect(
        override val commandId: GPUDrawCommandID,
        val rrect: GPURRect,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillRRect
    }

    /** M15 path-fill command with tessellated vertex buffers from the shadow adapter. */
    data class FillPath(
        override val commandId: GPUDrawCommandID,
        val pathKey: String,
        val pathDescriptor: GPUPathFacts,
        val tessellatedVertices: List<Float>,
        val contourStarts: List<Int>,
        val totalVertexCount: Int,
        val edgeCount: Int,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.FillPath
    }

    /**
     * Text run command with only dumpable text-stack artifact references.
     *
     * Blend facts are retained to satisfy the shared normalized-command contract,
     * but recording still refuses text runs until a text GPU route is promoted.
     */
    data class DrawTextRun(
        override val commandId: GPUDrawCommandID,
        val textLayoutResultId: String?,
        val glyphRunId: String?,
        val glyphRunDescriptorRefs: List<String>,
        val glyphRunDescriptor: GlyphRunDescriptor? = null,
        val artifactRefs: List<GPUTextArtifactRef>,
        val artifactKeyHashes: List<String>,
        val atlasGenerationTokens: List<String>,
        val uploadDependencyFacts: List<String>,
        val routeDiagnostics: List<GPUTextDiagnostic>,
        override val transform: GPUTransformFacts,
        override val clip: GPUClipFacts,
        override val layer: GPULayerFacts,
        override val material: GPUMaterialDescriptor,
        override val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        override val bounds: GPUBounds,
        override val ordering: GPUOrderingFacts,
        override val source: GPUCommandSource,
    ) : NormalizedDrawCommand {
        override val drawKind: GPUDrawKind = GPUDrawKind.DrawTextRun
    }
}

/** Converts rectangle geometry to conservative command bounds. */
private fun GPURect.toBounds(): GPUBounds =
    GPUBounds(left = left, top = top, right = right, bottom = bottom)
