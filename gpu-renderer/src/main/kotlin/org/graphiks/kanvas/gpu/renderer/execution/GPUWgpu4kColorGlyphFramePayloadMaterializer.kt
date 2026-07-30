package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan

/**
 * Compatibility-only adapter for callers that still instantiate the retired isolated
 * one-packet ColorGlyph materializer.
 *
 * Prepared ColorGlyph frames now execute through the common prepared-surface run materializer so
 * they can share generic frame-local R8 uploads with TextA8 and preserve global render ordering.
 */
internal class GPUWgpu4kColorGlyphFramePayloadMaterializer(
    @Suppress("UNUSED_PARAMETER") device: GPUDevice,
    @Suppress("UNUSED_PARAMETER") queue: GPUQueue,
    @Suppress("UNUSED_PARAMETER") preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    @Suppress("UNUSED_PARAMETER") sessionCache: GPUWgpu4kColorGlyphSessionCache,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private var consumed = false

    @Synchronized
    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        if (consumed) {
            return GPUPreparedNativeFramePayloadMaterialization.Refused(
                "unsupported.native-color-glyph.materializer-state",
                "The ColorGlyph compatibility adapter is one-shot and already consumed.",
            )
        }
        consumed = true
        return GPUPreparedNativeFramePayloadMaterialization.Refused(
            "unsupported.native-color-glyph.isolated-adapter-retired",
            "Prepared ColorGlyph requires the common sealed prepared-surface materializer.",
        )
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding =
        GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "unsupported.native-color-glyph.isolated-adapter-retired",
            "Prepared ColorGlyph requires the common sealed prepared-surface materializer.",
        )

    @Synchronized
    override fun close() {
        consumed = true
    }
}
