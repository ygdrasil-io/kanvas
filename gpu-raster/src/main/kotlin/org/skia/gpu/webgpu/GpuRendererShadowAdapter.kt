package org.skia.gpu.webgpu

import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlanner
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
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
 */
internal enum class GpuRendererShadowMode {
    /** Default mode: legacy rendering remains the only behavior. */
    Disabled,

    /** Evidence mode: normalize facts and ask the renderer planner for a route decision. */
    Shadow,
}

/**
 * Configuration for the FillRect shadow handoff.
 *
 * The config owns the explicit feature gate and immutable capability snapshot
 * passed to `:gpu-renderer`. The default is disabled, so constructing an adapter
 * cannot activate a renderer route unless callers opt into [GpuRendererShadowMode.Shadow]
 * or use [fromSystemProperties] with the documented feature flag.
 */
internal data class GpuRendererShadowConfig(
    val mode: GpuRendererShadowMode = GpuRendererShadowMode.Disabled,
    val capabilities: GPUCapabilities = firstSliceShadowCapabilities(),
) {
    public companion object {
        /** System property that can opt a local diagnostic run into shadow evidence. */
        public const val ShadowFillRectProperty: String = "kanvas.gpu.renderer.shadow.fillRect"

        /**
         * Builds config from system properties without changing the default.
         *
         * Only the literal value `true`, parsed by [String.toBoolean], enables
         * shadow mode. Missing or false values keep the handoff skipped.
         */
        public fun fromSystemProperties(
            propertyReader: (String) -> String? = System::getProperty,
        ): GpuRendererShadowConfig =
            GpuRendererShadowConfig(
                mode = if (propertyReader(ShadowFillRectProperty).toBoolean()) {
                    GpuRendererShadowMode.Shadow
                } else {
                    GpuRendererShadowMode.Disabled
                },
            )
    }
}

/**
 * Stable outcome class for an attempted shadow handoff.
 *
 * `Skipped` means the feature gate prevented normalization. `Refused` means
 * adapter validation or renderer planning found unsupported input and did not
 * invent CPU fallback work. `Native` means the renderer planner produced native
 * route evidence only; it is not backend submission and not product routing.
 */
internal enum class GpuRendererShadowHandoffStatus {
    /** The adapter did no renderer work because shadow mode was disabled. */
    Skipped,

    /** The adapter or planner refused the handoff with a stable diagnostic. */
    Refused,

    /** The planner returned native route evidence in shadow mode only. */
    Native,
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
    val routeLabel: String,
    val diagnosticCode: String?,
    val commandFacts: GpuRendererShadowCommandFacts?,
    val normalizedCommand: NormalizedDrawCommand.FillRect?,
    val firstRouteDecision: GPURouteDecision?,
) {
    /** Returns a deterministic evidence dump suitable for tests and PM logs. */
    public fun dump(): String =
        "gpuRendererShadow v=1 status=${status.dumpLabel()} route=$routeLabel " +
            "diagnostic=${diagnosticCode ?: "none"} cpuFallback=false " +
            "command=${commandFacts?.dump() ?: "none"}"
}

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

        val command = state.toNormalizedCommand()
        val commandFacts = command.toShadowFacts()
        val decision = GPUFirstRoutePlanner(config.capabilities).plan(command).routeDecision

        return when (decision) {
            is GPURouteDecision.Native -> GpuRendererShadowResult(
                status = GpuRendererShadowHandoffStatus.Native,
                routeLabel = decision.route.consumerKind,
                diagnosticCode = null,
                commandFacts = commandFacts,
                normalizedCommand = command,
                firstRouteDecision = decision,
            )
            is GPURouteDecision.Refused -> GpuRendererShadowResult(
                status = GpuRendererShadowHandoffStatus.Refused,
                routeLabel = "refused.${decision.diagnostic.code}",
                diagnosticCode = decision.diagnostic.code,
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
            routeLabel = "skipped.gpu_renderer_shadow.disabled",
            diagnosticCode = "shadow.disabled",
            commandFacts = null,
            normalizedCommand = null,
            firstRouteDecision = null,
        )

    private fun refusedBeforeHandoff(code: String): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Refused,
            routeLabel = "refused.$code",
            diagnosticCode = code,
            commandFacts = null,
            normalizedCommand = null,
            firstRouteDecision = null,
        )

    private fun refusedAfterUnexpectedPlannerDecision(
        command: NormalizedDrawCommand.FillRect,
        commandFacts: GpuRendererShadowCommandFacts,
        decision: GPURouteDecision,
        code: String,
    ): GpuRendererShadowResult =
        GpuRendererShadowResult(
            status = GpuRendererShadowHandoffStatus.Refused,
            routeLabel = "refused.$code",
            diagnosticCode = code,
            commandFacts = commandFacts,
            normalizedCommand = command,
            firstRouteDecision = decision,
    )
}

/**
 * Shared hook used by legacy `drawRect` and tests for FillRect shadow evidence.
 *
 * The hook keeps default rendering unchanged: disabled mode returns before a
 * renderer command is built, and shadow mode only produces evidence. For
 * `StrokeAndFill`, the legacy path decomposes the fill component before stroke
 * rendering; this hook mirrors that decomposition by passing a fill-style paint
 * copy to the adapter while leaving the caller's paint unchanged.
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

private fun GpuRendererShadowFillRectState.toNormalizedCommand(): NormalizedDrawCommand.FillRect =
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
            operation = "legacy.fillRect.shadow",
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
    name.lowercase()

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
        ),
        snapshotId = "gpu-raster-shadow-fill-rect",
    )
