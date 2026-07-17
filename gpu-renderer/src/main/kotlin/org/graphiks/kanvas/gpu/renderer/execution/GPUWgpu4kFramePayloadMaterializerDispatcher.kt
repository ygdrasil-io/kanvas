package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import kotlin.reflect.KClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

internal sealed interface GPUWgpu4kPreparedFramePayloadRoute {
    data object DestinationCopySolidRect : GPUWgpu4kPreparedFramePayloadRoute
    data object SolidRect : GPUWgpu4kPreparedFramePayloadRoute
    data object ColorGlyph : GPUWgpu4kPreparedFramePayloadRoute
    data object RegisteredUniformRect : GPUWgpu4kPreparedFramePayloadRoute
    data object SeparableBlurRect : GPUWgpu4kPreparedFramePayloadRoute
    data class Refused(val code: String, val message: String) : GPUWgpu4kPreparedFramePayloadRoute
}

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
    private val colorGlyphCache: GPUWgpu4kColorGlyphSessionCache,
    private val registeredUniformRectCache: GPUWgpu4kRegisteredUniformRectSessionCache,
    private val separableBlurRectCache: GPUWgpu4kSeparableBlurRectSessionCache,
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
        val semantics = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { step -> step.drawPackets.mapNotNull { it.semanticPayload } }
        val hasDestinationCopy = framePlan.steps.any { it is GPUFrameStep.CopyDestinationStep }
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
                ),
                framePlan,
                encoderPlan,
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
                framePlan,
                encoderPlan,
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
                framePlan,
                encoderPlan,
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
                framePlan,
                encoderPlan,
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
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
            )
            is GPUWgpu4kPreparedFramePayloadRoute.Refused ->
                GPUPreparedNativeFramePayloadMaterialization.Refused(route.code, route.message)
        }
    }

    private fun dispatch(
        selected: GPUPreparedNativeFramePayloadMaterializer,
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        delegate = selected
        return selected.materializeReusable(framePlan, encoderPlan, resources, generationSeal)
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = synchronized(this) {
        delegate?.bindLateSurface(draft, acquiredSurface)
            ?: GPUPreparedNativeFrameLateSurfaceBinding.Refused(
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
