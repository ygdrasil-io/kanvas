package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.reflect.KClass
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

internal sealed interface GPUWgpu4kPreparedFramePayloadRoute {
    data object DestinationCopySolidRect : GPUWgpu4kPreparedFramePayloadRoute
    data object SolidRect : GPUWgpu4kPreparedFramePayloadRoute
    data object CorePrimitive : GPUWgpu4kPreparedFramePayloadRoute
    data object ColorGlyph : GPUWgpu4kPreparedFramePayloadRoute
    data object RegisteredUniformRect : GPUWgpu4kPreparedFramePayloadRoute
    data object SeparableBlurRect : GPUWgpu4kPreparedFramePayloadRoute
    data class Refused(val code: String, val message: String) : GPUWgpu4kPreparedFramePayloadRoute
}

internal fun refusedWgpu4kPreRegistrationMaterialization(
    code: String,
    message: String,
    ledger: GPUPreRegistrationNativeHandleLedger,
    retainedCloseOwner: AutoCloseable? = null,
) = GPUPreparedNativeFramePayloadMaterialization.Refused(
    code,
    message,
    retainedPreRegistrationLedger = ledger.takeIf { it.pendingHandleCount > 0 },
    retainedCloseOwner = retainedCloseOwner,
)

internal fun interface GPUAcquiredSurfaceNativeTargetResolver {
    fun resolve(output: GPUAcquiredSurfaceOutput): GPUPreparedNativeTextureViewOperand?

    companion object {
        val Unavailable = GPUAcquiredSurfaceNativeTargetResolver { null }
    }
}

internal fun decorateWgpu4kSurfaceBlitDraft(
    fullEncoderPlan: GPUCommandEncoderPlan,
    reusableDraft: GPUPreparedNativeFrameDraft,
    surfaceBlit: GPUPreparedNativeScopeOperand.SurfaceBlit,
): GPUPreparedNativeFrameDraft {
    val reusable = reusableDraft.payload
    require(reusable.identity.contextIdentity == fullEncoderPlan.contextIdentity)
    require(reusable.identity.encoderPlanId == fullEncoderPlan.planId)
    require(reusable.identity.deviceGeneration == fullEncoderPlan.deviceGeneration)
    require(reusable.identity.targetGeneration == fullEncoderPlan.targetGeneration)
    val reusableByStep = reusable.scopeOperands.associateBy { it.sourceStepIndex }
    val fullOperands = fullEncoderPlan.scopes.map { scope ->
        if (scope.operationKind == GPUEncoderOperationKind.SurfaceBlit) {
            require(scope.sourceStepIndex == surfaceBlit.sourceStepIndex)
            surfaceBlit
        } else {
            requireNotNull(reusableByStep[scope.sourceStepIndex]) {
                "Reusable native payload omitted encoder step ${scope.sourceStepIndex}"
            }
        }
    }
    val decorated = GPUPreparedNativeFrameDraft(
        GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = reusable.identity.frameId,
                contextIdentity = fullEncoderPlan.contextIdentity,
                encoderPlanId = fullEncoderPlan.planId,
                deviceGeneration = fullEncoderPlan.deviceGeneration,
                targetGeneration = fullEncoderPlan.targetGeneration,
                scopes = fullEncoderPlan.scopes.map { scope ->
                    GPUPreparedNativeScopeKey(
                        scope.sourceStepIndex,
                        scope.operationKind,
                        scope.resourceGenerationLabels,
                        scope.nativeOperandKeys,
                    )
                },
            ),
            scopeOperands = fullOperands,
            scopeOperandKeys = fullEncoderPlan.scopes.map { it.nativeOperandKeys },
            auxiliaryOwnedHandles = reusable.auxiliaryOwnedHandles,
            leaseLifecycle = reusable.leaseLifecycle,
        ),
    )
    check(reusableDraft.transferOwnershipToDraft(decorated)) {
        "Reusable native draft ownership could not move to its surface-decorated replacement"
    }
    return decorated
}

internal fun bindWgpu4kLateSurface(
    draft: GPUPreparedNativeFrameDraft,
    acquiredSurface: GPUAcquiredSurfaceOutput?,
    resolver: GPUAcquiredSurfaceNativeTargetResolver,
): GPUPreparedNativeFrameLateSurfaceBinding {
    val surface = draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.SurfaceBlit>()
        .singleOrNull() ?: return GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    val acquired = acquiredSurface ?: return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
        "unsupported.native-frame-payload.surface-output-missing",
        "The native surface blit requires one acquired output.",
    )
    if (acquired.output != surface.output) {
        return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "stale.native-frame-payload.surface-output-mismatch",
            "The acquired native surface output does not match the prepared blit output.",
        )
    }
    val target = resolver.resolve(acquired) ?: return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
        "unsupported.native-frame-payload.surface-target-unavailable",
        "The acquired output did not expose its session-owned opaque native target.",
    )
    if (target.deviceGeneration != acquired.deviceGeneration ||
        target.ownership != GPUPreparedNativeOperandOwnership.Borrowed
    ) {
        return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "stale.native-frame-payload.surface-target-generation",
            "The opaque native surface target does not match the acquired output generation.",
        )
    }
    return GPUPreparedNativeFrameLateSurfaceBinding.Bound(acquired.output, target)
}

internal fun materializeWgpu4kSurfaceRoute(
    format: GPUTextureFormat,
    acquireSurfaceBlit: (GPUTextureFormat) -> GPUWgpu4kSurfaceBlitCacheLease,
    materializeWithSurfaceBlit: (
        GPUWgpu4kSurfaceBlitCacheLease,
    ) -> GPUPreparedNativeFramePayloadMaterialization,
    decorateMaterializedDraft: (
        GPUWgpu4kSurfaceBlitCacheLease,
        GPUPreparedNativeFrameDraft,
    ) -> GPUPreparedNativeFrameDraft = { _, draft -> draft },
): GPUPreparedNativeFramePayloadMaterialization {
    val cached = try {
        acquireSurfaceBlit(format)
    } catch (failure: Throwable) {
        return refusedWgpu4kSurfaceBlitMaterialization(failure)
    }
    val materialized = materializeWithSurfaceBlit(cached)
    if (materialized !is GPUPreparedNativeFramePayloadMaterialization.Materialized) {
        return materialized
    }
    return try {
        GPUPreparedNativeFramePayloadMaterialization.Materialized(
            decorateMaterializedDraft(cached, materialized.draft),
        )
    } catch (failure: Throwable) {
        val retainedDraft = materialized.draft.takeUnless {
            it.disposeBeforeRegistration()
        }
        refusedWgpu4kSurfaceBlitMaterialization(failure, retainedDraft)
    }
}

private fun refusedWgpu4kSurfaceBlitMaterialization(
    failure: Throwable,
    retainedDraft: GPUPreparedNativeFrameDraft? = null,
) = GPUPreparedNativeFramePayloadMaterialization.Refused(
    "failed.native-frame-payload.surface-blit-materialization",
    "The typed surface blit payload could not be materialized: ${failure::class.simpleName.orEmpty()}",
    retainedDraft,
)

internal fun selectWgpu4kPreparedFramePayloadRoute(
    semanticClasses: List<KClass<out GPUDrawSemanticPayload>>,
    hasDestinationCopy: Boolean = false,
): GPUWgpu4kPreparedFramePayloadRoute {
    val distinct = semanticClasses.distinct()
    return when {
        hasDestinationCopy && distinct == listOf(GPUDrawSemanticPayload.SolidRect::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.DestinationCopySolidRect
        hasDestinationCopy -> GPUWgpu4kPreparedFramePayloadRoute.Refused(
            "unsupported.native-frame-payload.destination-copy-semantic-shape",
            "A prepared destination-copy frame requires the supported solid-rectangle semantic shape.",
        )
        distinct == listOf(GPUDrawSemanticPayload.SolidRect::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.SolidRect
        distinct == listOf(GPUDrawSemanticPayload.CorePrimitive::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.CorePrimitive
        distinct == listOf(GPUDrawSemanticPayload.ColorGlyph::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.ColorGlyph
        distinct == listOf(GPUDrawSemanticPayload.RegisteredUniformRect::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.RegisteredUniformRect
        distinct == listOf(GPUDrawSemanticPayload.SeparableBlurRect::class) ->
            GPUWgpu4kPreparedFramePayloadRoute.SeparableBlurRect
        distinct.size > 1 -> GPUWgpu4kPreparedFramePayloadRoute.Refused(
            "unsupported.native-frame-payload.mixed-semantic-shape",
            "One prepared native frame may not mix typed semantic payload shapes.",
        )
        else -> GPUWgpu4kPreparedFramePayloadRoute.Refused(
            "unsupported.native-frame-payload.semantic-shape",
            "The prepared frame does not contain one supported typed semantic payload shape.",
        )
    }
}

/** Selects one closed public-wgpu4k materializer without probing labels or allocating native handles. */
internal class GPUWgpu4kFramePayloadMaterializerDispatcher(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val solidRectCache: GPUWgpu4kSolidRectSessionCache,
    private val corePrimitiveCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val colorGlyphCache: GPUWgpu4kColorGlyphSessionCache,
    private val registeredUniformRectCache: GPUWgpu4kRegisteredUniformRectSessionCache,
    private val separableBlurRectCache: GPUWgpu4kSeparableBlurRectSessionCache,
    private val destinationCopyCache: GPUWgpu4kDestinationCopySessionCache,
    private val surfaceBlitCache: GPUWgpu4kSurfaceBlitSessionCache,
    private val surfaceTargetResolver: GPUAcquiredSurfaceNativeTargetResolver =
        GPUAcquiredSurfaceNativeTargetResolver.Unavailable,
    private val corePrimitiveLimits: GPULimits? = null,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private var delegate: GPUPreparedNativeFramePayloadMaterializer? = null
    private var closed = false

    @Synchronized
    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        if (closed || delegate != null) {
            return GPUPreparedNativeFramePayloadMaterialization.Refused(
                "unsupported.native-frame-payload.dispatcher-state",
                "The prepared frame payload dispatcher is one-shot and already consumed.",
            )
        }
        val surfaceRoute = when (val split = splitWgpu4kSurfaceRoute(framePlan, encoderPlan)) {
            Wgpu4kSurfaceRouteSplit.NoSurface -> null
            is Wgpu4kSurfaceRouteSplit.Routed -> split
            is Wgpu4kSurfaceRouteSplit.Refused -> return GPUPreparedNativeFramePayloadMaterialization.Refused(
                split.code,
                split.message,
            )
        }
        val reusableFramePlan = surfaceRoute?.reusableFramePlan ?: framePlan
        val reusableEncoderPlan = surfaceRoute?.reusableEncoderPlan ?: encoderPlan
        val semantics = reusableFramePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { step -> step.drawPackets.mapNotNull { it.semanticPayload } }
        val hasDestinationCopy = reusableFramePlan.steps.any { it is GPUFrameStep.CopyDestinationStep }
        return when (
            val route = selectWgpu4kPreparedFramePayloadRoute(
                semantics.map { it::class },
                hasDestinationCopy,
            )
        ) {
            GPUWgpu4kPreparedFramePayloadRoute.DestinationCopySolidRect -> dispatch(
                GPUWgpu4kDestinationCopyFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    destinationCopyCache,
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            GPUWgpu4kPreparedFramePayloadRoute.SolidRect -> dispatch(
                GPUWgpu4kSolidRectFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    solidRectCache,
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            GPUWgpu4kPreparedFramePayloadRoute.CorePrimitive -> dispatch(
                GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    corePrimitiveCache,
                    corePrimitiveLimits ?: return GPUPreparedNativeFramePayloadMaterialization.Refused(
                        "unsupported.native-core-primitive.limits-unavailable",
                        "The direct CorePrimitive route requires observed backend limits.",
                    ),
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            GPUWgpu4kPreparedFramePayloadRoute.ColorGlyph -> dispatch(
                GPUWgpu4kColorGlyphFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    colorGlyphCache,
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            GPUWgpu4kPreparedFramePayloadRoute.RegisteredUniformRect -> dispatch(
                GPUWgpu4kRegisteredUniformRectFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    registeredUniformRectCache,
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            GPUWgpu4kPreparedFramePayloadRoute.SeparableBlurRect -> dispatch(
                GPUWgpu4kSeparableBlurRectFramePayloadMaterializer(
                    device,
                    queue,
                    preparedSceneTarget,
                    separableBlurRectCache,
                ),
                reusableFramePlan,
                reusableEncoderPlan,
                encoderPlan,
                surfaceRoute,
                resources,
                generationSeal,
            )
            is GPUWgpu4kPreparedFramePayloadRoute.Refused ->
                GPUPreparedNativeFramePayloadMaterialization.Refused(route.code, route.message)
        }
    }

    private fun dispatch(
        selected: GPUPreparedNativeFramePayloadMaterializer,
        reusableFramePlan: GPUFramePlan,
        reusableEncoderPlan: GPUCommandEncoderPlan,
        fullEncoderPlan: GPUCommandEncoderPlan,
        surfaceRoute: Wgpu4kSurfaceRouteSplit.Routed?,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        delegate = selected
        if (surfaceRoute == null) {
            return selected.materializeReusable(
                reusableFramePlan,
                reusableEncoderPlan,
                resources,
                generationSeal,
            )
        }
        return materializeWgpu4kSurfaceRoute(
            format = surfaceRoute.format,
            acquireSurfaceBlit = surfaceBlitCache::acquire,
            materializeWithSurfaceBlit = {
                selected.materializeReusable(
                    reusableFramePlan,
                    reusableEncoderPlan,
                    resources,
                    generationSeal,
                )
            },
            decorateMaterializedDraft = { cached, reusableDraft ->
                val surfaceBlit = GPUPreparedNativeScopeOperand.SurfaceBlit(
                    sourceStepIndex = surfaceRoute.surfaceScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureViewOperand(
                        cached.sourceView,
                        generationSeal.deviceGeneration,
                    ),
                    output = surfaceRoute.output,
                    pipeline = GPUPreparedNativeRenderPipelineOperand(
                        cached.pipeline,
                        generationSeal.deviceGeneration,
                    ),
                    bindGroup = GPUPreparedNativeBindGroupOperand(
                        cached.bindGroup,
                        generationSeal.deviceGeneration,
                    ),
                )
                decorateWgpu4kSurfaceBlitDraft(
                    fullEncoderPlan,
                    reusableDraft,
                    surfaceBlit,
                )
            },
        )
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = synchronized(this) {
        if (draft.payload.scopeOperands.any { it.operationKind == GPUEncoderOperationKind.SurfaceBlit }) {
            bindWgpu4kLateSurface(draft, acquiredSurface, surfaceTargetResolver)
        } else {
            delegate?.bindLateSurface(draft, acquiredSurface)
        } ?: GPUPreparedNativeFrameLateSurfaceBinding.Refused(
                "unsupported.native-frame-payload.dispatcher-state",
                "No prepared native payload route was selected.",
            )
    }

    @Synchronized
    override fun close() {
        closed = true
        (delegate as? AutoCloseable)?.close()
    }
}

private sealed interface Wgpu4kSurfaceRouteSplit {
    data object NoSurface : Wgpu4kSurfaceRouteSplit

    data class Routed(
        val reusableFramePlan: GPUFramePlan,
        val reusableEncoderPlan: GPUCommandEncoderPlan,
        val surfaceScope: GPUCommandEncoderScopePlan,
        val output: org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef,
        val format: GPUTextureFormat,
    ) : Wgpu4kSurfaceRouteSplit

    data class Refused(val code: String, val message: String) : Wgpu4kSurfaceRouteSplit
}

internal fun wgpu4kReusableEncoderPlanWithoutSurface(
    encoderPlan: GPUCommandEncoderPlan,
): GPUCommandEncoderPlan {
    require(encoderPlan.scopes.lastOrNull()?.operationKind == GPUEncoderOperationKind.SurfaceBlit) {
        "A reusable window encoder plan requires one final SurfaceBlit scope"
    }
    return GPUCommandEncoderPlan.ordered(
        planId = encoderPlan.planId,
        contextIdentity = encoderPlan.contextIdentity,
        deviceGeneration = encoderPlan.deviceGeneration,
        targetGeneration = encoderPlan.targetGeneration,
        scopes = encoderPlan.scopes.dropLast(1),
    )
}

private fun splitWgpu4kSurfaceRoute(
    framePlan: GPUFramePlan,
    encoderPlan: GPUCommandEncoderPlan,
): Wgpu4kSurfaceRouteSplit {
    val surfaceSteps = framePlan.steps.filter {
        it is GPUFrameStep.AcquireSurfaceOutput ||
            it is GPUFrameStep.SurfaceBlitRenderPassStep ||
            it is GPUFrameStep.PostSubmitPresentAction
    }
    val surfaceScopes = encoderPlan.scopes.filter { it.operationKind == GPUEncoderOperationKind.SurfaceBlit }
    if (surfaceSteps.isEmpty() && surfaceScopes.isEmpty()) return Wgpu4kSurfaceRouteSplit.NoSurface
    val suffix = framePlan.steps.takeLast(3)
    val acquire = suffix.getOrNull(0) as? GPUFrameStep.AcquireSurfaceOutput
    val blit = suffix.getOrNull(1) as? GPUFrameStep.SurfaceBlitRenderPassStep
    val present = suffix.getOrNull(2) as? GPUFrameStep.PostSubmitPresentAction
    val scope = surfaceScopes.singleOrNull()
    if (surfaceSteps.size != 3 || acquire == null || blit == null || present == null ||
        scope == null || encoderPlan.scopes.lastOrNull() !== scope ||
        scope.sourceStepIndex != framePlan.steps.lastIndex - 1 ||
        acquire.descriptor.output != blit.output || blit.output != present.output
    ) {
        return Wgpu4kSurfaceRouteSplit.Refused(
            "unsupported.native-frame-payload.surface-chain-shape",
            "A window frame requires one final AcquireSurfaceOutput/SurfaceBlit/PostSubmitPresent chain.",
        )
    }
    val format = acquire.descriptor.format.value.toWgpu4kSurfaceFormat()
        ?: return Wgpu4kSurfaceRouteSplit.Refused(
            "unsupported.native-frame-payload.surface-format",
            "The window surface format is not supported by the typed surface blit pipeline.",
        )
    return Wgpu4kSurfaceRouteSplit.Routed(
        reusableFramePlan = GPUFramePlan(
            frameId = framePlan.frameId,
            capabilitySeal = framePlan.capabilitySeal,
            recordingSeals = framePlan.recordingSeals,
            steps = framePlan.steps.dropLast(3),
            memoryBudget = framePlan.memoryBudget,
            diagnostics = framePlan.diagnostics,
            dependencies = framePlan.dependencies,
            phaseOrder = framePlan.phaseOrder,
            elidedNoOpDraws = framePlan.elidedNoOpDraws,
            atomicallyRefused = framePlan.atomicallyRefused,
        ),
        reusableEncoderPlan = wgpu4kReusableEncoderPlanWithoutSurface(encoderPlan),
        surfaceScope = scope,
        output = blit.output,
        format = format,
    )
}

private fun String.toWgpu4kSurfaceFormat(): GPUTextureFormat? = when (lowercase()) {
    "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
    "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
    "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
    "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
    else -> null
}
