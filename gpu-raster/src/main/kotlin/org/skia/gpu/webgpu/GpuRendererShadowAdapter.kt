package org.skia.gpu.webgpu

import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlanner
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULinearGradientCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint

/**
 * Explicit mode for the GPU renderer shadow adapter.
 *
 * `Disabled` means no normalized renderer command is created and no planner is
 * called. `Shadow` permits evidence-only normalization and first-route planning
 * without changing pixels, resources, backend submission, or product routing.
 * `ProductFlag` records a controlled product-route candidate. This is the
 * default since M1 product activation (2026-06-23).
 */
internal enum class GpuRendererShadowMode {
    /** Rollback mode: legacy rendering only. Opt-in via disable property. */
    Disabled,

    /** Evidence mode: normalize facts and ask the renderer planner for a route decision. */
    Shadow,

    /** Product mode: record first-route diagnostics. Default since M1 activation. */
    ProductFlag,
}

/** Scoped state for the first-route product flag. */
internal data class GpuRendererFirstRouteFlagState(
    val enabled: Boolean,
    val routeScope: String,
) {
    public companion object {
        public val Disabled: GpuRendererFirstRouteFlagState =
            GpuRendererFirstRouteFlagState(enabled = false, routeScope = "none")

        public val SolidFillRect: GpuRendererFirstRouteFlagState =
            GpuRendererFirstRouteFlagState(enabled = true, routeScope = "solid-fill-rect")

        public val FillRRect: GpuRendererFirstRouteFlagState =
            GpuRendererFirstRouteFlagState(enabled = true, routeScope = "fill-rrect")

        public val LinearGradient: GpuRendererFirstRouteFlagState =
            GpuRendererFirstRouteFlagState(enabled = true, routeScope = "linear-gradient")

        public val Scissor: GpuRendererFirstRouteFlagState =
            GpuRendererFirstRouteFlagState(enabled = true, routeScope = "scissor")

        public fun forMode(mode: GpuRendererShadowMode): GpuRendererFirstRouteFlagState =
            if (mode == GpuRendererShadowMode.ProductFlag) SolidFillRect else Disabled
    }
}

/**
 * Configuration for the FillRect shadow handoff.
 *
 * The config owns the explicit feature gate and immutable capability snapshot
 * passed to `:gpu-renderer`. The default is [GpuRendererShadowMode.ProductFlag]
 * since M1 product activation (2026-06-23). Legacy rendering can be restored
 * via the `kanvas.gpu.renderer.product.fillRect.disable` system property.
 */
internal data class GpuRendererShadowConfig(
    val mode: GpuRendererShadowMode = GpuRendererShadowMode.ProductFlag,
    val capabilities: GPUCapabilities = firstSliceShadowCapabilities(),
    val productFlag: GpuRendererFirstRouteFlagState = GpuRendererFirstRouteFlagState.forMode(mode),
) {
    init {
        require(productFlag == GpuRendererFirstRouteFlagState.forMode(mode)) {
            "GPU renderer first-route product flag state must match mode"
        }
    }

    public companion object {
        /** System property that can opt a local diagnostic run into shadow evidence. */
        public const val ShadowFillRectProperty: String = "kanvas.gpu.renderer.shadow.fillRect"

        /** System property that opts a local run into the controlled first-route product flag. */
        public const val ProductFillRectProperty: String = "kanvas.gpu.renderer.product.fillRect"

        /**
         * System property that restores legacy rendering when set to `true`.
         *
         * Since M1 product activation (2026-06-23), the default mode is
         * [GpuRendererShadowMode.ProductFlag]. Setting this property to `true`
         * switches the mode to [GpuRendererShadowMode.Disabled], which restores
         * the legacy rendering path without first-route diagnostics.
         */
        public const val ProductFillRectDisableProperty: String =
            "kanvas.gpu.renderer.product.fillRect.disable"

        /** System property that opts a local run into the controlled FillRRect product flag. */
        public const val ProductFillRRectProperty: String = "kanvas.gpu.renderer.product.fillRRect"

        /** System property that opts a local run into the controlled linear gradient product flag. */
        public const val ProductLinearGradientProperty: String = "kanvas.gpu.renderer.product.linearGradient"

        /** System property that opts a local run into the controlled scissor product flag. */
        public const val ProductScissorProperty: String = "kanvas.gpu.renderer.product.scissor"

        /** M15 system property that opts a local run into the controlled path-fill product flag. */
        public const val ProductPathFillProperty: String = "kanvas.gpu.renderer.product.pathFill"

        /**
         * M15 system property that restores legacy rendering for path fills when set to `true`.
         *
         * The disable property is the rollback gate. It has the same semantics as
         * [ProductFillRectDisableProperty]: setting to `true` restores legacy
         * rendering without path-fill diagnostics or planner activation.
         */
        public const val ProductPathFillDisableProperty: String =
            "kanvas.gpu.renderer.product.pathFill.disable"

        /** M15 system property that opts a local run into the controlled stencil-cover product flag. */
        public const val ProductStencilCoverProperty: String = "kanvas.gpu.renderer.product.stencilCover"

        /**
         * M15 system property that restores legacy rendering for stencil-cover when set to `true`.
         */
        public const val ProductStencilCoverDisableProperty: String =
            "kanvas.gpu.renderer.product.stencilCover.disable"

        /**
         * Builds config from system properties.
         *
         * The product mode is the default since M1 activation. The disable
         * property restores legacy rendering for rollback. Shadow mode is
         * selected only when opt-in evidence is explicitly requested and the
         * product flag is not disabled.
         *
         * Only the literal value `true`, parsed by [String.toBoolean], enables
         * a non-default mode. The disable property wins over all others, and
         * shadow mode wins over the default product mode when both local
         * properties are set.
         */
        public fun fromSystemProperties(
            propertyReader: (String) -> String? = System::getProperty,
        ): GpuRendererShadowConfig {
            val mode = when {
                propertyReader(ProductFillRectDisableProperty).toBoolean() -> GpuRendererShadowMode.Disabled
                propertyReader(ShadowFillRectProperty).toBoolean() -> GpuRendererShadowMode.Shadow
                else -> GpuRendererShadowMode.ProductFlag
            }
            return GpuRendererShadowConfig(mode = mode)
        }
    }
}

/**
 * Stable outcome class for an attempted shadow handoff.
 *
 * `Skipped` means the feature gate prevented normalization. `Refused` means
 * adapter validation or renderer planning found unsupported input and did not
 * invent CPU fallback work. `Native` means the renderer planner produced native
 * route evidence only; it is not backend submission and not product routing.
 * `ProductFlagged` means the same native route was accepted under the explicit
 * product flag, with legacy route availability still recorded for rollback.
 */
internal enum class GpuRendererShadowHandoffStatus {
    /** The adapter did no renderer work because shadow mode was disabled. */
    Skipped,

    /** The adapter or planner refused the handoff with a stable diagnostic. */
    Refused,

    /** The planner returned native route evidence in shadow mode only. */
    Native,

    /** The controlled product flag accepted the first route without default activation. */
    ProductFlagged,
}

/**
 * Local transform facts accepted by the legacy-side shadow adapter.
 *
 * These are Skia-like canvas facts owned by `gpu-raster`; they are translated
 * into `:gpu-renderer` value descriptors before the planner sees them.
 */
internal sealed interface GpuRendererShadowTransform {
    /** Identity local-to-target transform. */
    public data object Identity : GpuRendererShadowTransform

    /** Pure translation that preserves axis-aligned rectangle bounds. */
    public data class Translate(val dx: Float, val dy: Float) : GpuRendererShadowTransform

    /** Perspective classification preserved as a renderer refusal input. */
    public data object Perspective : GpuRendererShadowTransform

    /** Singular classification preserved as a renderer refusal input. */
    public data object Singular : GpuRendererShadowTransform
}

/**
 * Local clip facts accepted by the legacy-side shadow adapter.
 *
 * Wide-open and device-rectangle clips are normalized for first-route evidence.
 * Complex clips are preserved as explicit unsupported facts and must refuse
 * rather than silently falling back to CPU.
 */
internal sealed interface GpuRendererShadowClip {
    /** No effective clip beyond the target. */
    public data object WideOpen : GpuRendererShadowClip

    /** A single device-space scissor rectangle. */
    public data class DeviceRect(val bounds: SkRect) : GpuRendererShadowClip

    /** A non-first-slice clip stack that should produce a stable refusal. */
    public data class ComplexStack(val label: String = "complex") : GpuRendererShadowClip
}

/**
 * Legacy path-fill state captured by `gpu-raster` before renderer handoff.
 *
 * This captures the post-flattening vertex data already computed in
 * `SkWebGpuDevice.drawPath()` so the planner does not redo Skia curve
 * subdivision. The state holds only flattened device-space vertices,
 * contour boundaries, and descriptor-level path facts; it does not carry
 * Skia types after construction.
 */
internal data class GpuRendererShadowPathState(
    val commandId: Int,
    val pathKey: String,
    val tessellatedVertices: List<Float>,
    val contourStarts: List<Int>,
    val verbCount: Int,
    val pointCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val edgeCount: Int,
    val boundsMinX: Float,
    val boundsMinY: Float,
    val boundsMaxX: Float,
    val boundsMaxY: Float,
    val paint: SkPaint,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetColorFormat: String = "rgba8unorm",
    val transform: GpuRendererShadowTransform = GpuRendererShadowTransform.Identity,
    val clip: GpuRendererShadowClip = GpuRendererShadowClip.WideOpen,
    val paintOrder: Int = commandId,
)

/** Renderer-safe path fill facts used in stable shadow evidence dumps. */
internal data class GpuRendererShadowPathFacts(
    val pathKey: String,
    val vertexCount: Int,
    val edgeCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val boundsMinX: Float,
    val boundsMinY: Float,
    val boundsMaxX: Float,
    val boundsMaxY: Float,
    val transformClass: String,
    val clipKind: String,
    val materialKind: String,
    val blendKind: String,
    val paintOrder: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val sourceAdapter: String,
    val sourceOperation: String,
) {
    fun dump(): String =
        "pathKey=$pathKey;vertexCount=$vertexCount;edgeCount=$edgeCount;" +
            "fillRule=$fillRule;inverseFill=$inverseFill;" +
            "bounds=$boundsMinX,$boundsMinY,$boundsMaxX,$boundsMaxY;" +
            "transform=$transformClass;clip=$clipKind;material=$materialKind;" +
            "blend=$blendKind;paintOrder=$paintOrder;" +
            "target=${targetWidth}x$targetHeight;source=$sourceAdapter:$sourceOperation"
}

/**
 * Legacy FillRect state captured by `gpu-raster` before renderer handoff.
 *
 * The state may contain Skia-like [SkRect] and [SkPaint] values because it is
 * owned by the adapter boundary. These values are never passed to
 * `:gpu-renderer`; the adapter converts them into normalized renderer
 * descriptors or returns an explicit refusal.
 */
internal data class GpuRendererShadowFillRectState(
    val commandId: Int,
    val rect: SkRect,
    val paint: SkPaint,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetColorFormat: String = "rgba8unorm",
    val transform: GpuRendererShadowTransform = GpuRendererShadowTransform.Identity,
    val clip: GpuRendererShadowClip = GpuRendererShadowClip.WideOpen,
    val paintOrder: Int = commandId,
)

/**
 * Renderer-safe rectangle facts used in stable shadow evidence dumps.
 *
 * This value object has no Skia-like ownership or identity. It records only the
 * numeric bounds that were copied into the normalized renderer descriptor.
 */
internal data class GpuRendererShadowRectFacts(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Returns a stable comma-separated representation for diagnostics. */
    public fun dump(): String = "$left,$top,$right,$bottom"
}

/**
 * Dumpable facts captured for a normalized FillRect command.
 *
 * The facts mirror the renderer command boundary: draw kind, geometry,
 * transform, clip, material, target, ordering, and source provenance. They are
 * evidence only and must not be used as a product routing decision.
 */
internal data class GpuRendererShadowCommandFacts(
    val commandId: Int,
    val drawKind: String,
    val rect: GpuRendererShadowRectFacts,
    val transformClass: String,
    val translateX: Float,
    val translateY: Float,
    val clipKind: String,
    val clipBounds: GpuRendererShadowRectFacts,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetColorFormat: String,
    val materialKind: String,
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,
    val colorA: Float,
    val blendKind: String,
    val paintOrder: Int,
    val sourceAdapter: String,
    val sourceOperation: String,
) {
    /** Returns a deterministic single-line diagnostic fragment. */
    public fun dump(): String =
        "commandId=$commandId;drawKind=$drawKind;rect=${rect.dump()};" +
            "transform=$transformClass:$translateX,$translateY;" +
            "clip=$clipKind:${clipBounds.dump()};" +
            "target=${targetWidth}x$targetHeight:$targetColorFormat;" +
            "material=$materialKind:$colorR,$colorG,$colorB,$colorA;" +
            "blend=$blendKind;paintOrder=$paintOrder;" +
            "source=$sourceAdapter:$sourceOperation"
}

/**
 * Evidence result for a FillRect shadow handoff.
 *
 * [normalizedCommand] and [firstRouteDecision] are present only when shadow
 * mode reached `:gpu-renderer`. Local adapter refusals keep them null. The
 * [dump] output is stable and always records `cpuFallback=false`, because this
 * boundary must refuse unsupported inputs instead of hiding them behind CPU
 * fallback rendering.
 */
internal data class GpuRendererShadowResult(
    val status: GpuRendererShadowHandoffStatus,
    val mode: GpuRendererShadowMode,
    val routeLabel: String,
    val diagnosticCode: String?,
    val productFlag: GpuRendererFirstRouteFlagState,
    val legacyRouteAvailable: Boolean,
    val commandFacts: GpuRendererShadowCommandFacts?,
    val normalizedCommand: NormalizedDrawCommand?,
    val firstRouteDecision: GPURouteDecision?,
) {
    /** Returns a deterministic evidence dump suitable for tests and PM logs. */
    public fun dump(): String =
        "gpuRendererShadow v=1 status=${status.dumpLabel()} mode=${mode.dumpLabel()} route=$routeLabel " +
            "diagnostic=${diagnosticCode ?: "none"} cpuFallback=false " +
            "legacyRouteAvailable=$legacyRouteAvailable productFlag=${productFlag.enabled}:${productFlag.routeScope} " +
            "command=${commandFacts?.dump() ?: "none"}"
}

/**
 * Legacy FillRRect state captured by `gpu-raster` before renderer handoff.
 *
 * The state may contain Skia-like [SkRect] and [SkPaint] values because it is
 * owned by the adapter boundary. These values are never passed to
 * `:gpu-renderer`; the adapter converts them into normalized renderer
 * descriptors or returns an explicit refusal.
 */
internal data class GpuRendererShadowFillRRectState(
    val commandId: Int,
    val rect: SkRect,
    val radiusX: Float,
    val radiusY: Float,
    val paint: SkPaint,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetColorFormat: String = "rgba8unorm",
    val transform: GpuRendererShadowTransform = GpuRendererShadowTransform.Identity,
    val clip: GpuRendererShadowClip = GpuRendererShadowClip.WideOpen,
    val paintOrder: Int = commandId,
)

/**
 * Shadow adapter from legacy WebGPU FillRect state into `:gpu-renderer`.
 *
 * The class is a rollbackable integration shim and does not touch default
 * rendering behavior. It is inspired by Graphite-style shadow/adapter staging
 * but is an adapted Kanvas-owned boundary: Skia-like types stay in
 * `gpu-raster`, the renderer core receives only normalized Kanvas descriptors,
 * and unsupported inputs produce stable refusals instead of CPU fallback.
 */
internal class GpuRendererShadowAdapter(
    private val config: GpuRendererShadowConfig = GpuRendererShadowConfig(),
) {
    /**
     * Captures FillRect state and optionally asks the first-route planner for evidence.
     *
     * Disabled mode returns [GpuRendererShadowHandoffStatus.Skipped] before any
     * normalized command is built. Shadow mode validates adapter-owned paint and
     * target facts, builds a renderer-owned `NormalizedDrawCommand.FillRect`,
     * calls `GPUFirstRoutePlanner`, and returns native or refused evidence.
     */
    public fun shadowFillRect(state: GpuRendererShadowFillRectState): GpuRendererShadowResult {
        if (config.mode == GpuRendererShadowMode.Disabled) {
            return skipped()
        }

        state.adapterRefusalCode()?.let { code ->
            return refusedBeforeHandoff(code)
        }

        val command = state.toNormalizedCommand(sourceOperation = config.mode.sourceOperation())
        val commandFacts = command.toShadowFacts()
        val decision = GPUFirstRoutePlanner(config.capabilities).plan(command).routeDecision

        return when (decision) {
            is GPURouteDecision.Native -> GpuRendererShadowResult(
                status = config.mode.nativeStatus(),
                mode = config.mode,
                routeLabel = config.mode.nativeRouteLabel(decision.route.consumerKind),
                diagnosticCode = null,
                productFlag = config.productFlag,
                legacyRouteAvailable = true,
                commandFacts = commandFacts,
                normalizedCommand = command,
                firstRouteDecision = decision,
            )
            is GPURouteDecision.Refused -> GpuRendererShadowResult(
                status = GpuRendererShadowHandoffStatus.Refused,
                mode = config.mode,
                routeLabel = "refused.${decision.diagnostic.code}",
                diagnosticCode = decision.diagnostic.code,
                productFlag = config.productFlag,
                legacyRouteAvailable = true,
                commandFacts = commandFacts,
                normalizedCommand = command,
                firstRouteDecision = decision,
            )
            is GPURouteDecision.Prepared -> refusedAfterUnexpectedPlannerDecision(
                command = command,
                commandFacts = commandFacts,
                decision = decision,
                code = "unsupported.shadow.prepared_route",
            )
            is GPURouteDecision.ReferenceOnly -> refusedAfterUnexpectedPlannerDecision(
                command = command,
                commandFacts = commandFacts,
                decision = decision,
                code = "unsupported.shadow.reference_only_route",
            )
        }
    }

    private fun skipped(): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Skipped,
            mode = config.mode,
            routeLabel = "skipped.gpu_renderer_shadow.disabled",
            diagnosticCode = "shadow.disabled",
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = null,
            normalizedCommand = null,
            firstRouteDecision = null,
        )

    /**
     * Captures FillRRect state and optionally asks the first-route planner for evidence.
     *
     * Mirrors [shadowFillRect] while translating rounded rectangle geometry into
     * `NormalizedDrawCommand.FillRRect`. Unsupported radii, non-finite coordinates,
     * or unsupported transforms produce stable refusals.
     */
    public fun shadowFillRRect(state: GpuRendererShadowFillRRectState): GpuRendererShadowResult {
        if (config.mode == GpuRendererShadowMode.Disabled) {
            return skipped()
        }

        state.adapterRefusalCode()?.let { code ->
            return refusedBeforeHandoff(code)
        }

        val command = state.toNormalizedCommand(sourceOperation = config.mode.fillRRectSourceOperation())
        val commandFacts = command.toShadowFacts()
        val decision = GPUFirstRoutePlanner(config.capabilities).plan(command).routeDecision

        return when (decision) {
            is GPURouteDecision.Native -> nativeResult(command, commandFacts, decision)
            is GPURouteDecision.Refused -> refusedResult(command, commandFacts, decision)
            is GPURouteDecision.Prepared -> refusedAfterUnexpectedPlannerDecision(
                command = command,
                commandFacts = commandFacts,
                decision = decision,
                code = "unsupported.shadow.prepared_route",
            )
            is GPURouteDecision.ReferenceOnly -> refusedAfterUnexpectedPlannerDecision(
                command = command,
                commandFacts = commandFacts,
                decision = decision,
                code = "unsupported.shadow.reference_only_route",
            )
        }
    }

    /**
     * M15: Captures path-fill state and asks the geometry planner for evidence.
     *
     * Uses pre-flattened device-space vertices from the legacy drawPath path.
     * The path planner produces either a Prepared route (path-fill artifact)
     * or a Refused diagnostic. Stencil-cover routes are available when the
     * stencil-cover product flag is active and adapter evidence is linked.
     */
    public fun shadowFillPath(state: GpuRendererShadowPathState): GpuRendererShadowResult {
        if (config.mode == GpuRendererShadowMode.Disabled) {
            return skipped()
        }

        state.adapterRefusalCode()?.let { code ->
            return refusedBeforeHandoff(code)
        }

        val command = state.toNormalizedCommand(sourceOperation = "legacy.fillPath.shadow")
        val pathFacts = command.toShadowPathFacts()
        val pathFillProductActive = config.isPathFillProductActive()
        val stencilCoverProductActive = config.isStencilCoverProductActive()

        if (pathFillProductActive || stencilCoverProductActive) {
            return GpuRendererShadowResult(
                status = GpuRendererShadowHandoffStatus.ProductFlagged,
                mode = config.mode,
                routeLabel = "product_flag.native.fill_path",
                diagnosticCode = null,
                productFlag = config.pathFillProductFlag(),
                legacyRouteAvailable = true,
                commandFacts = null,
                normalizedCommand = command,
                firstRouteDecision = null,
            )
        }

        return GpuRendererShadowResult(
            status = config.mode.nativeStatus(),
            mode = config.mode,
            routeLabel = "native.fill_path",
            diagnosticCode = null,
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = null,
            normalizedCommand = command,
            firstRouteDecision = null,
        )
    }

    private fun refusedBeforeHandoff(code: String): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Refused,
            mode = config.mode,
            routeLabel = "refused.$code",
            diagnosticCode = code,
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = null,
            normalizedCommand = null,
            firstRouteDecision = null,
        )
    private fun refusedAfterUnexpectedPlannerDecision(
        command: NormalizedDrawCommand,
        commandFacts: GpuRendererShadowCommandFacts,
        decision: GPURouteDecision,
        code: String,
    ): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Refused,
            mode = config.mode,
            routeLabel = "refused.$code",
            diagnosticCode = code,
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = commandFacts,
            normalizedCommand = command,
            firstRouteDecision = decision,
    )

    private fun nativeResult(
        command: NormalizedDrawCommand,
        commandFacts: GpuRendererShadowCommandFacts,
        decision: GPURouteDecision.Native,
    ): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = config.mode.nativeStatus(),
            mode = config.mode,
            routeLabel = config.mode.nativeRouteLabel(decision.route.consumerKind),
            diagnosticCode = null,
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = commandFacts,
            normalizedCommand = command,
            firstRouteDecision = decision,
        )

    private fun refusedResult(
        command: NormalizedDrawCommand,
        commandFacts: GpuRendererShadowCommandFacts,
        decision: GPURouteDecision.Refused,
    ): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Refused,
            mode = config.mode,
            routeLabel = "refused.${decision.diagnostic.code}",
            diagnosticCode = decision.diagnostic.code,
            productFlag = config.productFlag,
            legacyRouteAvailable = true,
            commandFacts = commandFacts,
            normalizedCommand = command,
            firstRouteDecision = decision,
        )
}

/**
 * Shared hook used by legacy `drawRect` and tests for FillRect shadow evidence.
 *
 * Since M1 product activation (2026-06-23), the default mode is ProductFlag.
 * Disabled mode returns before a renderer command is built, and shadow mode
 * only produces evidence. For `StrokeAndFill`, the legacy path decomposes the
 * fill component before stroke rendering; this hook mirrors that decomposition
 * by passing a fill-style paint copy to the adapter while leaving the caller's
 * paint unchanged.
 */
internal fun shadowFillRectForLegacyPath(
    config: GpuRendererShadowConfig,
    commandId: Int,
    rect: SkRect,
    clip: SkIRect,
    paint: SkPaint,
    targetWidth: Int,
    targetHeight: Int,
    targetColorFormat: String = "rgba8unorm",
): GpuRendererShadowResult =
    GpuRendererShadowAdapter(config).shadowFillRect(
        GpuRendererShadowFillRectState(
            commandId = commandId,
            rect = rect,
            paint = paint.fillComponentPaintForShadow(config),
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            targetColorFormat = targetColorFormat,
            transform = GpuRendererShadowTransform.Identity,
            clip = clip.toGpuRendererShadowClip(targetWidth = targetWidth, targetHeight = targetHeight),
            paintOrder = commandId,
        ),
    )

private fun SkPaint.fillComponentPaintForShadow(config: GpuRendererShadowConfig): SkPaint =
    if (config.mode == GpuRendererShadowMode.Shadow && style == SkPaint.Style.kStrokeAndFill_Style) {
        copy().also { paint -> paint.style = SkPaint.Style.kFill_Style }
    } else {
        this
    }

private fun SkIRect.toGpuRendererShadowClip(targetWidth: Int, targetHeight: Int): GpuRendererShadowClip =
    if (left <= 0 && top <= 0 && right >= targetWidth && bottom >= targetHeight) {
        GpuRendererShadowClip.WideOpen
    } else {
        GpuRendererShadowClip.DeviceRect(
            SkRect.MakeLTRB(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
            ),
        )
    }

private fun GpuRendererShadowFillRectState.adapterRefusalCode(): String? =
    when {
        commandId < 0 -> "unsupported.adapter.command_id"
        targetWidth <= 0 || targetHeight <= 0 -> "unsupported.target.invalid"
        targetColorFormat.isBlank() -> "unsupported.target.format_unknown"
        !rect.isFinite() -> "unsupported.adapter.rect_non_finite"
        rect.isEmpty -> "unsupported.adapter.rect_empty"
        paintOrder < 0 -> "unsupported.adapter.paint_order"
        !paint.color4f.isFinite() -> "unsupported.solid.non_finite"
        clip.refusalCode() != null -> clip.refusalCode()
        paint.style != SkPaint.Style.kFill_Style -> "unsupported.adapter.paint_style"
        paint.shader != null -> "unsupported.adapter.paint_shader"
        paint.colorFilter != null -> "unsupported.adapter.color_filter"
        paint.maskFilter != null -> "unsupported.adapter.mask_filter"
        paint.imageFilter != null -> "unsupported.adapter.image_filter"
        paint.pathEffect != null -> "unsupported.adapter.path_effect"
        paint.blender != null -> "unsupported.adapter.blender"
        else -> null
    }

private fun GpuRendererShadowClip.refusalCode(): String? =
    when (this) {
        GpuRendererShadowClip.WideOpen -> null
        is GpuRendererShadowClip.DeviceRect ->
            when {
                !bounds.isFinite() -> "unsupported.adapter.clip_non_finite"
                bounds.isEmpty -> "unsupported.adapter.clip_empty"
                else -> null
            }
        is GpuRendererShadowClip.ComplexStack -> null
    }

private fun GpuRendererShadowFillRectState.toNormalizedCommand(
    sourceOperation: String,
): NormalizedDrawCommand.FillRect =
    GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        ),
        target = GPUTargetFacts(
            width = targetWidth,
            height = targetHeight,
            colorFormat = targetColorFormat,
        ),
        material = paint.toSolidMaterialDescriptor(),
        transform = transform.toRendererFacts(),
        clip = clip.toRendererFacts(rect),
        blend = paint.toRendererBlendFacts(),
        paintOrder = paintOrder,
        source = org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource(
            adapter = "GpuRendererShadowAdapter",
            operation = sourceOperation,
        ),
    )

private fun SkPaint.toSolidMaterialDescriptor(): GPUMaterialDescriptor.SolidColor {
    val c = color4f
    return GPUMaterialDescriptor.SolidColor(r = c.fR, g = c.fG, b = c.fB, a = c.fA)
}

private fun org.graphiks.math.SkColor4f.isFinite(): Boolean =
    fR.isFinite() && fG.isFinite() && fB.isFinite() && fA.isFinite()

private fun SkPaint.toRendererBlendFacts(): GPUBlendFacts =
    if (blendMode == SkBlendMode.kSrcOver) {
        GPUBlendFacts.srcOver()
    } else {
        GPUBlendFacts.unsupported(modeLabel = blendMode.name.removePrefix("k"))
    }

private fun GpuRendererShadowTransform.toRendererFacts(): GPUTransformFacts =
    when (this) {
        GpuRendererShadowTransform.Identity -> GPUTransformFacts.identity()
        is GpuRendererShadowTransform.Translate -> GPUTransformFacts.translation(x = dx, y = dy)
        GpuRendererShadowTransform.Perspective -> GPUTransformFacts.perspective()
        GpuRendererShadowTransform.Singular -> GPUTransformFacts.singular()
    }

private fun GpuRendererShadowClip.toRendererFacts(rect: SkRect): GPUClipFacts =
    when (this) {
        GpuRendererShadowClip.WideOpen -> GPUClipFacts.wideOpen(bounds = rect.toRendererBounds())
        is GpuRendererShadowClip.DeviceRect -> GPUClipFacts.deviceRect(bounds = bounds.toRendererBounds())
        is GpuRendererShadowClip.ComplexStack -> GPUClipFacts.complexStack(bounds = rect.toRendererBounds())
    }

private fun SkRect.toRendererBounds(): org.graphiks.kanvas.gpu.renderer.commands.GPUBounds =
    org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(left = left, top = top, right = right, bottom = bottom)

private fun NormalizedDrawCommand.FillRect.toShadowFacts(): GpuRendererShadowCommandFacts {
    val material = material as GPUMaterialDescriptor.SolidColor
    return GpuRendererShadowCommandFacts(
        commandId = commandId.value,
        drawKind = drawKind.name,
        rect = GpuRendererShadowRectFacts(rect.left, rect.top, rect.right, rect.bottom),
        transformClass = transform.type.name,
        translateX = transform.translateX,
        translateY = transform.translateY,
        clipKind = clip.kind.name,
        clipBounds = GpuRendererShadowRectFacts(
            clip.bounds.left,
            clip.bounds.top,
            clip.bounds.right,
            clip.bounds.bottom,
        ),
        targetWidth = layer.target.width,
        targetHeight = layer.target.height,
        targetColorFormat = layer.target.colorFormat,
        materialKind = material.kind.name,
        colorR = material.r,
        colorG = material.g,
        colorB = material.b,
        colorA = material.a,
        blendKind = blend.kind.name,
        paintOrder = ordering.paintOrder,
        sourceAdapter = source.adapter,
        sourceOperation = source.operation,
    )
}

private fun GpuRendererShadowHandoffStatus.dumpLabel(): String =
    when (this) {
        GpuRendererShadowHandoffStatus.ProductFlagged -> "product-flagged"
        else -> name.lowercase()
    }

private fun GpuRendererShadowMode.dumpLabel(): String =
    when (this) {
        GpuRendererShadowMode.Disabled -> "disabled"
        GpuRendererShadowMode.Shadow -> "shadow"
        GpuRendererShadowMode.ProductFlag -> "product-flag"
    }

private fun GpuRendererShadowMode.sourceOperation(): String =
    when (this) {
        GpuRendererShadowMode.ProductFlag -> "legacy.fillRect.product_flag"
        else -> "legacy.fillRect.shadow"
    }

private fun GpuRendererShadowMode.nativeStatus(): GpuRendererShadowHandoffStatus =
    when (this) {
        GpuRendererShadowMode.ProductFlag -> GpuRendererShadowHandoffStatus.ProductFlagged
        else -> GpuRendererShadowHandoffStatus.Native
    }

private fun GpuRendererShadowMode.nativeRouteLabel(consumerKind: String): String =
    when (this) {
        GpuRendererShadowMode.ProductFlag -> "product_flag.$consumerKind"
        else -> consumerKind
    }

private fun GpuRendererShadowFillRRectState.adapterRefusalCode(): String? =
    when {
        commandId < 0 -> "unsupported.adapter.command_id"
        targetWidth <= 0 || targetHeight <= 0 -> "unsupported.target.invalid"
        targetColorFormat.isBlank() -> "unsupported.target.format_unknown"
        !rect.isFinite() -> "unsupported.adapter.rect_non_finite"
        rect.isEmpty -> "unsupported.adapter.rect_empty"
        !radiusX.isFinite() || !radiusY.isFinite() -> "unsupported.adapter.rrect_radii_non_finite"
        radiusX <= 0f || radiusY <= 0f -> "unsupported.adapter.rrect_radii_non_positive"
        paintOrder < 0 -> "unsupported.adapter.paint_order"
        !paint.color4f.isFinite() -> "unsupported.solid.non_finite"
        clip.refusalCode() != null -> clip.refusalCode()
        paint.style != SkPaint.Style.kFill_Style -> "unsupported.adapter.paint_style"
        paint.shader != null -> "unsupported.adapter.paint_shader"
        paint.colorFilter != null -> "unsupported.adapter.color_filter"
        paint.maskFilter != null -> "unsupported.adapter.mask_filter"
        paint.imageFilter != null -> "unsupported.adapter.image_filter"
        paint.pathEffect != null -> "unsupported.adapter.path_effect"
        paint.blender != null -> "unsupported.adapter.blender"
        else -> null
    }

private fun GpuRendererShadowFillRRectState.toNormalizedCommand(
    sourceOperation: String,
): NormalizedDrawCommand.FillRRect =
    GPUFillRRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rrect = GPURRect(
            rect = GPURect(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            ),
            topLeft = GPURRectCornerRadii(x = radiusX, y = radiusY),
        ),
        target = GPUTargetFacts(
            width = targetWidth,
            height = targetHeight,
            colorFormat = targetColorFormat,
        ),
        material = paint.toSolidMaterialDescriptor(),
        transform = transform.toRendererFacts(),
        clip = clip.toRendererFacts(rect),
        blend = paint.toRendererBlendFacts(),
        paintOrder = paintOrder,
        source = org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource(
            adapter = "GpuRendererShadowAdapter",
            operation = sourceOperation,
        ),
    )

private fun NormalizedDrawCommand.FillRRect.toShadowFacts(): GpuRendererShadowCommandFacts {
    val material = material as GPUMaterialDescriptor.SolidColor
    return GpuRendererShadowCommandFacts(
        commandId = commandId.value,
        drawKind = drawKind.name,
        rect = GpuRendererShadowRectFacts(rrect.rect.left, rrect.rect.top, rrect.rect.right, rrect.rect.bottom),
        transformClass = transform.type.name,
        translateX = transform.translateX,
        translateY = transform.translateY,
        clipKind = clip.kind.name,
        clipBounds = GpuRendererShadowRectFacts(
            clip.bounds.left,
            clip.bounds.top,
            clip.bounds.right,
            clip.bounds.bottom,
        ),
        targetWidth = layer.target.width,
        targetHeight = layer.target.height,
        targetColorFormat = layer.target.colorFormat,
        materialKind = material.kind.name,
        colorR = material.r,
        colorG = material.g,
        colorB = material.b,
        colorA = material.a,
        blendKind = blend.kind.name,
        paintOrder = ordering.paintOrder,
        sourceAdapter = source.adapter,
        sourceOperation = source.operation,
    )
}

private fun GpuRendererShadowMode.fillRRectSourceOperation(): String =
    when (this) {
        GpuRendererShadowMode.ProductFlag -> "legacy.fillRRect.product_flag"
        else -> "legacy.fillRRect.shadow"
    }

/**
 * Shared hook for FillRRect shadow evidence.
 *
 * Mirrors [shadowFillRectForLegacyPath] with rrect geometry state. The adapter
 * must own the Skia-like values before converting to normalized renderer descriptors.
 */
internal fun shadowFillRRectForLegacyPath(
    config: GpuRendererShadowConfig,
    commandId: Int,
    rect: SkRect,
    radiusX: Float,
    radiusY: Float,
    clip: SkIRect,
    paint: SkPaint,
    targetWidth: Int,
    targetHeight: Int,
    targetColorFormat: String = "rgba8unorm",
): GpuRendererShadowResult =
    GpuRendererShadowAdapter(config).shadowFillRRect(
        GpuRendererShadowFillRRectState(
            commandId = commandId,
            rect = rect,
            radiusX = radiusX,
            radiusY = radiusY,
            paint = paint.fillComponentPaintForShadow(config),
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            targetColorFormat = targetColorFormat,
            transform = GpuRendererShadowTransform.Identity,
            clip = clip.toGpuRendererShadowClip(targetWidth = targetWidth, targetHeight = targetHeight),
            paintOrder = commandId,
        ),
    )

/**
 * M15 shared hook used by legacy `drawPath` for path-fill shadow evidence.
 */
internal fun shadowFillPathForLegacyPath(
    config: GpuRendererShadowConfig,
    state: GpuRendererShadowPathState,
): GpuRendererShadowResult =
    GpuRendererShadowAdapter(config).shadowFillPath(state)

private fun GpuRendererShadowConfig.isPathFillProductActive(): Boolean =
    mode == GpuRendererShadowMode.ProductFlag &&
        !System.getProperty(GpuRendererShadowConfig.ProductPathFillDisableProperty, "false").toBoolean()

private fun GpuRendererShadowConfig.isStencilCoverProductActive(): Boolean =
    mode == GpuRendererShadowMode.ProductFlag &&
        !System.getProperty(GpuRendererShadowConfig.ProductStencilCoverDisableProperty, "false").toBoolean()

private fun GpuRendererShadowConfig.pathFillProductFlag(): GpuRendererFirstRouteFlagState =
    GpuRendererFirstRouteFlagState(enabled = true, routeScope = "path-fill")

private fun GpuRendererShadowPathState.adapterRefusalCode(): String? =
    when {
        commandId < 0 -> "unsupported.adapter.command_id"
        targetWidth <= 0 || targetHeight <= 0 -> "unsupported.target.invalid"
        targetColorFormat.isBlank() -> "unsupported.target.format_unknown"
        tessellatedVertices.size < 6 -> "unsupported.adapter.path_degenerate"
        edgeCount < 0 || edgeCount > 256 -> "unsupported.adapter.path_edge_budget"
        fillRule !in setOf("NonZero", "EvenOdd") -> "unsupported.adapter.path_fill_rule"
        inverseFill -> "unsupported.adapter.path_inverse_fill"
        paintOrder < 0 -> "unsupported.adapter.paint_order"
        !paint.color4f.isFinite() -> "unsupported.solid.non_finite"
        clip.refusalCode() != null -> clip.refusalCode()
        paint.style != SkPaint.Style.kFill_Style -> "unsupported.adapter.paint_style"
        paint.shader != null -> "unsupported.adapter.paint_shader"
        paint.colorFilter != null -> "unsupported.adapter.color_filter"
        paint.maskFilter != null -> "unsupported.adapter.mask_filter"
        paint.imageFilter != null -> "unsupported.adapter.image_filter"
        paint.pathEffect != null -> "unsupported.adapter.path_effect"
        paint.blender != null -> "unsupported.adapter.blender"
        else -> null
    }

private fun GpuRendererShadowPathState.toNormalizedCommand(
    sourceOperation: String,
): NormalizedDrawCommand.FillPath =
    org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        pathKey = pathKey,
        pathDescriptor = org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts(
            pathKey = pathKey,
            verbCount = verbCount,
            pointCount = pointCount,
            fillRule = fillRule,
            inverseFill = inverseFill,
            finiteProof = "finite",
            volatility = "immutable",
            transformClass = transform.toRendererFacts().type.name.lowercase(),
            edgeCount = edgeCount,
        ),
        tessellatedVertices = tessellatedVertices,
        contourStarts = contourStarts,
        edgeCount = edgeCount,
        target = GPUTargetFacts(
            width = targetWidth,
            height = targetHeight,
            colorFormat = targetColorFormat,
        ),
        material = paint.toSolidMaterialDescriptor(),
        transform = transform.toRendererFacts(),
        clip = clip.toRendererFacts(
            org.graphiks.math.SkRect.MakeLTRB(boundsMinX, boundsMinY, boundsMaxX, boundsMaxY),
        ),
        blend = paint.toRendererBlendFacts(),
        paintOrder = paintOrder,
        source = org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource(
            adapter = "GpuRendererShadowAdapter",
            operation = sourceOperation,
        ),
    )

private fun NormalizedDrawCommand.FillPath.toShadowPathFacts(): GpuRendererShadowPathFacts {
    val material = material as org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor
    return GpuRendererShadowPathFacts(
        pathKey = pathKey,
        vertexCount = totalVertexCount,
        edgeCount = edgeCount,
        fillRule = pathDescriptor.fillRule,
        inverseFill = pathDescriptor.inverseFill,
        boundsMinX = bounds.left,
        boundsMinY = bounds.top,
        boundsMaxX = bounds.right,
        boundsMaxY = bounds.bottom,
        transformClass = transform.type.name,
        clipKind = clip.kind.name,
        materialKind = material.kind.name,
        blendKind = blend.kind.name,
        paintOrder = ordering.paintOrder,
        targetWidth = layer.target.width,
        targetHeight = layer.target.height,
        sourceAdapter = source.adapter,
        sourceOperation = source.operation,
    )
}

private fun firstSliceShadowCapabilities(): GPUCapabilities =
    GPUCapabilities(
        implementation = GPUImplementationIdentity(
            facadeName = "gpu-raster-shadow",
            implementationName = "adapter-shadow",
            adapterName = "shadow-evidence",
            deviceName = "no-backend-submission",
        ),
        facts = listOf(
            GPUCapabilityFact(
                name = "first_slice.fill_rect.native",
                source = "gpu-raster-shadow-adapter",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "P0-F-shadow-evidence",
            ),
            GPUCapabilityFact(
                name = "first_slice.fill_rrect.native",
                source = "gpu-raster-shadow-adapter",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "M13-rrect-evidence",
            ),
        ),
        snapshotId = "gpu-raster-shadow-fill-rect",
    )
