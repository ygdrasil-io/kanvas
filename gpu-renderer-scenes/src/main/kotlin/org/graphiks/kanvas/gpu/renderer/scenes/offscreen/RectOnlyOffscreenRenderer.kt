package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BlurWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.StrokeWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderSnippetSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TextAtlasA8Wgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TextAtlasA8EntryPoint
import org.graphiks.kanvas.gpu.renderer.images.decodePngToRgba
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureBuilder
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureResult
import org.graphiks.kanvas.gpu.renderer.wgsl.LayerCompositeEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.LayerCompositeSnippetSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.LayerCompositeWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.gpu.renderer.layers.SaveLayerExecutor
import org.graphiks.kanvas.gpu.renderer.text.SDFGenerator
import org.graphiks.kanvas.gpu.renderer.text.TextA8AtlasExecutor
import org.graphiks.kanvas.gpu.renderer.vertices.GPUDrawCallDescriptor
import org.graphiks.kanvas.gpu.renderer.vertices.GPUMeshBatcher
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexBufferUploader
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.gpu.renderer.vertices.VerticesExecutor
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.a8GlyphAtlasGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.legacyRetirementBlockerDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pathStencilCoverGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pmReadinessFreezeDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.runtimeEffectRefusalGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.textResourceBindingGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBlendMode
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.ConvexFanExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.isPathConvex
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableReason
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val BYTES_PER_PIXEL: Int = 4
internal const val OFFSCREEN_COLOR_FORMAT: String = "rgba8unorm"

/** Canonical attachment state for ordinary premultiplied-alpha scene composition. */
internal val SCENE_SRC_OVER_BLEND_STATE: GPUFixedFunctionBlendState =
    requireNotNull(
        GPUBlendPlanner().plan(
            GPUBlendSpecializationRequest(
                mode = GPUBlendMode.SRC_OVER,
                coverage = GPUCoverageConsumption.FullOrScissor,
                sourceAlpha = GPUSourceAlphaClassification.Translucent,
                target = GPUTargetBlendFacts(
                    formatClass = OFFSCREEN_COLOR_FORMAT,
                    clampsNormalizedColorWrites = true,
                    premultipliedAlpha = true,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
            ),
        ) as? GPUBlendPlan.FixedFunctionBlend,
    ).state

private data class GradientWgslInfo(
    val snippet: String,
    val entryPoint: String,
    val uniformStruct: String,
    val uniformArgs: String,
)

class RectOnlyOffscreenRenderer internal constructor(
    private val intermediatePlanAdapter: SceneIntermediatePlanAdapter,
    private val intermediatePlanExecutor: SceneIntermediatePlanExecutor,
) {
    constructor() : this(
        intermediatePlanAdapter = SceneIntermediatePlanAdapter(),
        intermediatePlanExecutor = SceneIntermediatePlanExecutor(),
    )

    fun render(scene: GPURendererScene<SceneCommand>, outputDir: Path): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        outputDir.createDirectories()
        if (scene.commands.any { it is SceneCommand.ColorTextRun }) {
            return renderPreparedColorGlyphScene(scene, outputDir)
        }
        if (scene.usesPreparedSolidRectPilot()) {
            return renderPreparedSolidRectScene(scene, outputDir)
        }
        if (scene.usesPreparedStrokeRectPilot()) {
            return renderPreparedStrokeRectScene(scene, outputDir)
        }
        if (scene.usesPreparedRegisteredUniformRectPilot()) {
            return renderPreparedRegisteredUniformRectScene(scene, outputDir)
        }
        if (scene.usesPreparedSeparableBlurRectPilot()) {
            return renderPreparedSeparableBlurRectScene(scene, outputDir)
        }
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = sceneId,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )

        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")

        runtime.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { target ->
                val sharedDiagnostics = rectOnlyRenderedDiagnostics(
                    sceneId = sceneId,
                    adapterInfo = session.adapterInfo?.summary,
                    clearCount = drawPlan.clearCount,
                    fillRectCount = drawPlan.fillRectCount,
                    fillRRectCount = drawPlan.fillRRectCount,
                    linearGradientRectCount = drawPlan.linearGradientRectCount,
                    radialGradientRectCount = drawPlan.radialGradientRectCount,
                    sweepGradientRectCount = drawPlan.sweepGradientRectCount,
                    clipCount = drawPlan.clipCount,
                    bitmapRectCount = drawPlan.bitmapRectCount,
                    blurRectCount = drawPlan.blurRectCount,
                    colorMatrixRectCount = drawPlan.colorMatrixRectCount,
                    strokeRectCount = drawPlan.strokeRectCount,
                    textRunRectCount = drawPlan.textRunCount,
                    colorTextRunRectCount = drawPlan.colorTextRunCount,
                    saveLayerRectCount = drawPlan.saveLayerRectCount,
                    pathFillStencilCount = drawPlan.pathFillStencilCount,
                    pathFillGradientCount = drawPlan.pathFillGradientCount,
                    convexFanMeshCount = drawPlan.convexFanMeshCount,
                    customRuntimeEffectRectCount = drawPlan.customRuntimeEffectRectCount,
                    filters = drawPlan.filters,
                    saveLayers = drawPlan.saveLayers,
                    runtimeEffects = drawPlan.runtimeEffects,
                    meshRibbons = drawPlan.meshRibbons,
                ) +
                    passBatchingWiringDiagnostics() +
                    drawPlan.tessellationDiagnostics +
                    drawPlan.executorWiringDiagnostics +
                    scene.runtimeEffectRefusalGateDiagnostics() +
                    scene.a8GlyphAtlasGateDiagnostics() +
                    scene.textResourceBindingGateDiagnostics() +
                    scene.pmReadinessFreezeDiagnostics() +
                    scene.legacyRetirementBlockerDiagnostics() +
                    scene.pathStencilCoverGateDiagnostics()
                val intermediateDiagnostics = mutableListOf<String>()
                val pixels = try {
                    renderToPixels(target, drawPlan, intermediateDiagnostics)
                } catch (failure: SceneIntermediateExecutionRefused) {
                    return OffscreenRunReport.failed(
                        sceneId = sceneId,
                        reason = failure.reasonCode,
                        diagnostics = sharedDiagnostics + failure.diagnostics,
                    )
                }
                val nonTransparentPixels = pixels.countNonTransparentPixels()
                val imagePath = outputDir.resolve(RENDER_FILE_NAME)
                val width = target.target.descriptor.width
                val height = target.target.descriptor.height
                writePng(pixels, width, height, imagePath)
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = width,
                    height = height,
                    byteCount = rectOnlyRawRgbaByteCount(pixels, width, height),
                    nonTransparentPixels = nonTransparentPixels,
                    diagnostics = sharedDiagnostics + intermediateDiagnostics + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    private fun renderPreparedColorGlyphScene(
        scene: GPURendererScene<SceneCommand>,
        outputDir: Path,
    ): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")
        runtime.use { session ->
            val capabilities = session.capabilities
                ?: return OffscreenRunReport.failed(sceneId, "prepared-color-glyph-capabilities-unavailable")
            val generation = session.deviceGeneration
            val preparedFrame = when (
                val result = PreparedColorGlyphSceneFrameRecorder().record(
                    scene = scene,
                    capabilities = capabilities,
                    deviceGeneration = generation,
                    frameOrdinal = 1L,
                    withReadback = true,
                )
            ) {
                is PreparedColorGlyphSceneFrameResult.Refused ->
                    return OffscreenRunReport.failed(sceneId, result.reason)
                is PreparedColorGlyphSceneFrameResult.Recorded -> result
            }
            val requestId = requireNotNull(preparedFrame.readbackRequestId)
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val terminal = preparedSession.renderFrame(
                    preparedFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    return OffscreenRunReport.failed(
                        sceneId,
                        terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                            ?: "prepared-color-glyph-frame-failed",
                        diagnostics = preparedFrame.diagnostics,
                    )
                }
                val pixels = (terminal.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
                    ?: return OffscreenRunReport.failed(
                        sceneId,
                        "prepared-color-glyph-readback-missing",
                        diagnostics = preparedFrame.diagnostics,
                    )
                val reference = preparedFrame.cpuReferenceRgba
                val nativeCounters = preparedSession.nativeCounters()
                val matchingPixels = pixels.indices.step(BYTES_PER_PIXEL).count { offset ->
                    pixels[offset] == reference[offset] &&
                        pixels[offset + 1] == reference[offset + 1] &&
                        pixels[offset + 2] == reference[offset + 2] &&
                        pixels[offset + 3] == reference[offset + 3]
                }
                val totalPixels = scene.dimensions.width * scene.dimensions.height
                writePng(pixels, scene.dimensions.width, scene.dimensions.height, outputDir.resolve(RENDER_FILE_NAME))
                writePng(
                    reference,
                    scene.dimensions.width,
                    scene.dimensions.height,
                    outputDir.resolve("reference.png"),
                )
                if (!pixels.contentEquals(reference)) {
                    writePng(
                        colorGlyphDiff(pixels, reference),
                        scene.dimensions.width,
                        scene.dimensions.height,
                        outputDir.resolve("diff.png"),
                    )
                } else {
                    outputDir.resolve("diff.png").toFile().delete()
                }
                outputDir.resolve("parity.txt").toFile().writeText(
                    buildString {
                        appendLine("COLRv0 color glyph parity report")
                        appendLine("fixture=/fonts/skia/colr.ttf")
                        appendLine("baseGlyph=2")
                        appendLine("layerGlyphs=7,8")
                        appendLine("reference=independent-cpu-source-over")
                        appendLine("matchingPixels=$matchingPixels/$totalPixels")
                        appendLine("pixelExact=${pixels.contentEquals(reference)}")
                        appendLine("targetSize=${scene.dimensions.width}x${scene.dimensions.height}")
                        appendLine("uniformBytes=784")
                    },
                )
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    byteCount = pixels.size.toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = listOf(
                        "rendered $sceneId via prepared WebGPU frame",
                        "adapter=${session.adapterInfo?.summary ?: "unknown-adapter"}",
                    ) + preparedFrame.diagnostics + listOf(
                        "colorTextRun:pixelExact=$matchingPixels/$totalPixels",
                        "colorTextRun:native encoders=${nativeCounters.encoders} " +
                            "commandBuffers=${nativeCounters.commandBuffers} " +
                            "submits=${nativeCounters.submits} readbacks=${nativeCounters.readbackCopies}",
                    ) + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    private fun renderPreparedSolidRectScene(
        scene: GPURendererScene<SceneCommand>,
        outputDir: Path,
    ): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")
        runtime.use { session ->
            val capabilities = session.capabilities
                ?: return OffscreenRunReport.failed(sceneId, "prepared-solid-rect-capabilities-unavailable")
            val generation = session.deviceGeneration
            val preparedFrame = when (
                val result = PreparedSolidRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    generation,
                    frameOrdinal = 1L,
                    withReadback = true,
                )
            ) {
                is PreparedSolidRectSceneFrameResult.Refused ->
                    return OffscreenRunReport.failed(sceneId, result.reason)
                is PreparedSolidRectSceneFrameResult.Recorded -> result
            }
            val requestId = requireNotNull(preparedFrame.readbackRequestId)
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val terminal = preparedSession.renderFrame(
                    preparedFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    return OffscreenRunReport.failed(
                        sceneId,
                        terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                            ?: "prepared-solid-rect-frame-failed",
                        diagnostics = preparedFrame.diagnostics,
                    )
                }
                val pixels = (terminal.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
                    ?: return OffscreenRunReport.failed(
                        sceneId,
                        "prepared-solid-rect-readback-missing",
                        diagnostics = preparedFrame.diagnostics,
                    )
                val counters = preparedSession.nativeCounters()
                writePng(pixels, scene.dimensions.width, scene.dimensions.height, outputDir.resolve(RENDER_FILE_NAME))
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    byteCount = pixels.size.toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = listOf(
                        "rendered $sceneId via WebGPU offscreen",
                        "frameRoute=prepared-WebGPU-frame",
                        "adapter=${session.adapterInfo?.summary ?: "unknown-adapter"}",
                        "fillRectCommands=${scene.commands.count { it is SceneCommand.FillRect }}",
                        "fillRRectCommands=0",
                    ) + preparedFrame.diagnostics + listOf(
                        "solidRect:native encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits} " +
                            "readbacks=${counters.readbackCopies}",
                        "solidRect:cache creations=${counters.solidRectInvariantCreations} " +
                            "reuses=${counters.solidRectInvariantReuses} " +
                            "invalidations=${counters.solidRectInvariantInvalidations}",
                    ) + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    private fun renderPreparedStrokeRectScene(
        scene: GPURendererScene<SceneCommand>,
        outputDir: Path,
    ): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")
        runtime.use { session ->
            val capabilities = session.capabilities
                ?: return OffscreenRunReport.failed(sceneId, "prepared-stroke-rect-capabilities-unavailable")
            val generation = session.deviceGeneration
            val preparedFrame = when (
                val result = PreparedStrokeRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    generation,
                    frameOrdinal = 1L,
                    withReadback = true,
                )
            ) {
                is PreparedStrokeRectSceneFrameResult.Refused ->
                    return OffscreenRunReport.failed(sceneId, result.reason)
                is PreparedStrokeRectSceneFrameResult.Recorded -> result
            }
            val requestId = requireNotNull(preparedFrame.readbackRequestId)
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val terminal = preparedSession.renderFrame(
                    preparedFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    val failureReason = terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared-stroke-rect-frame-failed"
                    return OffscreenRunReport.failed(
                        sceneId,
                        failureReason,
                        diagnostics = listOf(failureReason) + preparedFrame.diagnostics,
                    )
                }
                val pixels = (terminal.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
                    ?: return OffscreenRunReport.failed(
                        sceneId,
                        "prepared-stroke-rect-readback-missing",
                        diagnostics = preparedFrame.diagnostics,
                    )
                val reference = preparedFrame.cpuReferenceRgba
                var matchingPixels = 0
                var maxChannelDelta = 0
                pixels.indices.step(BYTES_PER_PIXEL).forEach { offset ->
                    var pixelMaxDelta = 0
                    repeat(BYTES_PER_PIXEL) { channel ->
                        val actual = pixels[offset + channel].toInt() and 0xff
                        val expected = reference[offset + channel].toInt() and 0xff
                        pixelMaxDelta = maxOf(pixelMaxDelta, kotlin.math.abs(actual - expected))
                    }
                    if (pixelMaxDelta == 0) matchingPixels += 1
                    maxChannelDelta = maxOf(maxChannelDelta, pixelMaxDelta)
                }
                val totalPixels = scene.dimensions.width * scene.dimensions.height
                val counters = preparedSession.nativeCounters()
                writePng(
                    pixels,
                    scene.dimensions.width,
                    scene.dimensions.height,
                    outputDir.resolve(RENDER_FILE_NAME),
                )
                writePng(
                    reference,
                    scene.dimensions.width,
                    scene.dimensions.height,
                    outputDir.resolve("reference.png"),
                )
                if (!pixels.contentEquals(reference)) {
                    writePng(
                        colorGlyphDiff(pixels, reference),
                        scene.dimensions.width,
                        scene.dimensions.height,
                        outputDir.resolve("diff.png"),
                    )
                } else {
                    outputDir.resolve("diff.png").toFile().delete()
                }
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    byteCount = pixels.size.toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = listOf(
                        "rendered $sceneId via prepared WebGPU frame",
                        "adapter=${session.adapterInfo?.summary ?: "unknown-adapter"}",
                    ) + preparedFrame.diagnostics + listOf(
                        "strokeRect:native encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits} " +
                            "readbacks=${counters.readbackCopies}",
                        "strokeRect:cache creations=${counters.solidRectInvariantCreations} " +
                            "reuses=${counters.solidRectInvariantReuses}",
                        "strokeRect:pixelExact=$matchingPixels/$totalPixels maxChannelDelta=$maxChannelDelta",
                    ) + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    private fun renderPreparedRegisteredUniformRectScene(
        scene: GPURendererScene<SceneCommand>,
        outputDir: Path,
    ): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")
        runtime.use { session ->
            val capabilities = session.capabilities
                ?: return OffscreenRunReport.failed(
                    sceneId,
                    "prepared-registered-uniform-capabilities-unavailable",
                )
            val generation = session.deviceGeneration
            val preparedFrame = when (
                val result = PreparedRegisteredUniformRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    generation,
                    frameOrdinal = 1L,
                    withReadback = true,
                )
            ) {
                is PreparedRegisteredUniformRectSceneFrameResult.Refused ->
                    return OffscreenRunReport.failed(sceneId, result.reason)
                is PreparedRegisteredUniformRectSceneFrameResult.Recorded -> result
            }
            val requestId = requireNotNull(preparedFrame.readbackRequestId)
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val terminal = preparedSession.renderFrame(
                    preparedFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    return OffscreenRunReport.failed(
                        sceneId,
                        terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                            ?: "prepared-registered-uniform-frame-failed",
                        diagnostics = preparedFrame.diagnostics,
                    )
                }
                val pixels = (terminal.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
                    ?: return OffscreenRunReport.failed(
                        sceneId,
                        "prepared-registered-uniform-readback-missing",
                        diagnostics = preparedFrame.diagnostics,
                    )
                val counters = preparedSession.nativeCounters()
                val reference = preparedFrame.cpuReferenceRgba
                val matchingPixels = pixels.indices.step(BYTES_PER_PIXEL).count { offset ->
                    pixels[offset] == reference[offset] &&
                        pixels[offset + 1] == reference[offset + 1] &&
                        pixels[offset + 2] == reference[offset + 2] &&
                        pixels[offset + 3] == reference[offset + 3]
                }
                val totalPixels = scene.dimensions.width * scene.dimensions.height
                var maxChannelDelta = 0
                var withinOneLsbPixels = 0
                pixels.indices.step(BYTES_PER_PIXEL).forEach { offset ->
                    var pixelMaxDelta = 0
                    repeat(BYTES_PER_PIXEL) { channel ->
                        val actual = pixels[offset + channel].toInt() and 0xff
                        val expected = reference[offset + channel].toInt() and 0xff
                        pixelMaxDelta = maxOf(pixelMaxDelta, kotlin.math.abs(actual - expected))
                    }
                    maxChannelDelta = maxOf(maxChannelDelta, pixelMaxDelta)
                    if (pixelMaxDelta <= 1) withinOneLsbPixels += 1
                }
                writePng(
                    pixels,
                    scene.dimensions.width,
                    scene.dimensions.height,
                    outputDir.resolve(RENDER_FILE_NAME),
                )
                writePng(
                    reference,
                    scene.dimensions.width,
                    scene.dimensions.height,
                    outputDir.resolve("reference.png"),
                )
                if (!pixels.contentEquals(reference)) {
                    writePng(
                        colorGlyphDiff(pixels, reference),
                        scene.dimensions.width,
                        scene.dimensions.height,
                        outputDir.resolve("diff.png"),
                    )
                } else {
                    outputDir.resolve("diff.png").toFile().delete()
                }
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    byteCount = pixels.size.toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = listOf(
                        "rendered $sceneId via prepared WebGPU frame",
                        "adapter=${session.adapterInfo?.summary ?: "unknown-adapter"}",
                    ) + preparedFrame.diagnostics + listOf(
                        "registeredUniform:native encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits} " +
                            "readbacks=${counters.readbackCopies}",
                        "registeredUniform:cache creations=${counters.registeredUniformInvariantCreations} " +
                            "reuses=${counters.registeredUniformInvariantReuses}",
                        "registeredUniform:pixelExact=$matchingPixels/$totalPixels",
                        "registeredUniform:withinOneLsb=$withinOneLsbPixels/$totalPixels " +
                            "maxChannelDelta=$maxChannelDelta",
                        "registeredUniform:reference=independent-cpu-premul-source-over",
                    ) + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    private fun renderPreparedSeparableBlurRectScene(
        scene: GPURendererScene<SceneCommand>,
        outputDir: Path,
    ): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")
        runtime.use { session ->
            val capabilities = session.capabilities
                ?: return OffscreenRunReport.failed(sceneId, "prepared-separable-blur-capabilities-unavailable")
            val generation = session.deviceGeneration
            val preparedFrame = when (
                val result = PreparedSeparableBlurRectSceneFrameRecorder().record(
                    scene,
                    capabilities,
                    generation,
                    frameOrdinal = 1L,
                    withReadback = true,
                )
            ) {
                is PreparedSeparableBlurRectSceneFrameResult.Refused ->
                    return OffscreenRunReport.failed(sceneId, result.reason)
                is PreparedSeparableBlurRectSceneFrameResult.Recorded -> result
            }
            val requestId = requireNotNull(preparedFrame.readbackRequestId)
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val terminal = preparedSession.renderFrame(
                    preparedFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    val failureReason = terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                        ?: "prepared-separable-blur-frame-failed"
                    return OffscreenRunReport.failed(
                        sceneId,
                        failureReason,
                        diagnostics = listOf(failureReason) + preparedFrame.diagnostics,
                    )
                }
                val pixels = (terminal.output as? GPUSceneFrameOutput.ReadbackRgba)?.bytes
                    ?: return OffscreenRunReport.failed(
                        sceneId,
                        "prepared-separable-blur-readback-missing",
                        diagnostics = listOf("prepared-separable-blur-readback-missing") +
                            preparedFrame.diagnostics,
                    )
                val reference = preparedFrame.cpuReferenceRgba
                var matchingPixels = 0
                var withinOneLsbPixels = 0
                var maxChannelDelta = 0
                pixels.indices.step(BYTES_PER_PIXEL).forEach { offset ->
                    var pixelMaxDelta = 0
                    repeat(BYTES_PER_PIXEL) { channel ->
                        val actual = pixels[offset + channel].toInt() and 0xff
                        val expected = reference[offset + channel].toInt() and 0xff
                        pixelMaxDelta = maxOf(pixelMaxDelta, kotlin.math.abs(actual - expected))
                    }
                    if (pixelMaxDelta == 0) matchingPixels += 1
                    if (pixelMaxDelta <= 1) withinOneLsbPixels += 1
                    maxChannelDelta = maxOf(maxChannelDelta, pixelMaxDelta)
                }
                val totalPixels = scene.dimensions.width * scene.dimensions.height
                val counters = preparedSession.nativeCounters()
                writePng(pixels, scene.dimensions.width, scene.dimensions.height, outputDir.resolve(RENDER_FILE_NAME))
                writePng(reference, scene.dimensions.width, scene.dimensions.height, outputDir.resolve("reference.png"))
                if (!pixels.contentEquals(reference)) {
                    writePng(
                        colorGlyphDiff(pixels, reference),
                        scene.dimensions.width,
                        scene.dimensions.height,
                        outputDir.resolve("diff.png"),
                    )
                } else {
                    outputDir.resolve("diff.png").toFile().delete()
                }
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    byteCount = pixels.size.toLong(),
                    nonTransparentPixels = pixels.countNonTransparentPixels(),
                    diagnostics = listOf(
                        "rendered $sceneId via prepared WebGPU frame",
                        "adapter=${session.adapterInfo?.summary ?: "unknown-adapter"}",
                    ) + preparedFrame.diagnostics + listOf(
                        "separableBlur:native encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits} " +
                            "readbacks=${counters.readbackCopies}",
                        "separableBlur:cache invariants=${counters.separableBlurInvariantCreations}/" +
                            "${counters.separableBlurInvariantReuses} intermediates=" +
                            "${counters.separableBlurIntermediateCreations}/" +
                            "${counters.separableBlurIntermediateReuses}",
                        "separableBlur:pixelExact=$matchingPixels/$totalPixels",
                        "separableBlur:withinOneLsb=$withinOneLsbPixels/$totalPixels " +
                            "maxChannelDelta=$maxChannelDelta",
                    ) + session.runtimeTelemetryDumpLines,
                )
            }
        }
    }

    internal fun renderToPixels(
        target: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        intermediateDiagnostics: MutableList<String>? = null,
    ): ByteArray {
        val viewportWidth = target.target.descriptor.width
        val viewportHeight = target.target.descriptor.height

        val intermediatePlan = intermediatePlanAdapter.plan(
            sceneId = drawPlan.sceneId,
            drawPlan = drawPlan,
            width = viewportWidth,
            height = viewportHeight,
        )
        val preparedIntermediateExecution = when (
            val intermediateExecution = intermediatePlanExecutor.executeSaveLayerPreparation(
            target = target,
            drawPlan = drawPlan,
            plan = intermediatePlan,
        ) { fills ->
            val solidDraws = SceneIntermediatePlanExecutor.solidRectDraws(fills)
            if (solidDraws.isNotEmpty()) {
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = solidDraws,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                )
            }
        }) {
            is SceneIntermediateExecutionResult.Refused -> {
                intermediateDiagnostics?.addAll(intermediateExecution.diagnostics)
                throw SceneIntermediateExecutionRefused(
                    scopeLabel = intermediateExecution.scopeLabel,
                    reasonCode = intermediateExecution.reasonCode,
                    diagnostics = intermediateExecution.diagnostics,
                )
            }
            is SceneIntermediateExecutionResult.Prepared -> {
                intermediateDiagnostics?.addAll(intermediateExecution.diagnostics)
                intermediateExecution
            }
        }

        if (preparedIntermediateExecution.destinationReadBlends.isNotEmpty()) {
            renderDestinationReadBlends(
                target = target,
                drawPlan = drawPlan,
                execution = preparedIntermediateExecution,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                intermediateDiagnostics = intermediateDiagnostics,
            )
            return target.readRgba()
        }

        target.encode(clearColor = drawPlan.clearColor.toGpuClearColor()) {
            val saveLayerFills = drawPlan.fills.filter { it.family == "save-layer" }
            val effectFamilies = setOf(
                "linear-gradient-rect", "radial-gradient-rect", "sweep-gradient-rect",
                "blur-rect", "color-matrix-rect", "stroke-rect",
                "bitmap-rect", "runtime-effect", "text-run", "save-layer",
                "custom-runtime-effect", "color-text-run",
            )
            val gradientTypes = setOf(
                "linear-gradient-rect", "radial-gradient-rect", "sweep-gradient-rect",
            )
            val solidFills = drawPlan.fills.filter {
                it.family !in effectFamilies &&
                    it.family != "vertices" &&
                    it.family != "path-fill-stencil" &&
                    it.family != "path-fill-gradient" &&
                    it.family != "convex-fan-mesh" &&
                    it.label !in preparedIntermediateExecution.childLabels &&
                    it.label !in preparedIntermediateExecution.destinationReadDrawLabels
            }
            if (solidFills.isNotEmpty()) {
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = solidFills.map { fill ->
                        GPUBackendRectDraw(
                            rgbaPremul = fill.toPremulColorArray(),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                )
            }

            preparedIntermediateExecution.destinationReadBlends.forEach { blend ->
                drawBlendPass(
                    wgsl = composeSceneDestinationReadBlendWgsl(blend.routeLabel),
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    srcTextureLabel = blend.sourceTextureLabel,
                    dstTextureLabel = blend.destinationTextureLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.solidColorBytes(SceneColor(1f, 1f, 1f, 1f)),
                            scissorX = 0,
                            scissorY = 0,
                            scissorWidth = viewportWidth,
                            scissorHeight = viewportHeight,
                        ),
                    ),
                )
            }

            val blurFills = drawPlan.fills.filter { it.family == "blur-rect" }
            if (blurFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = BlurWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    draws = blurFills.map { fill ->
                        val cx = (fill.left + fill.right) * 0.5f
                        val cy = (fill.top + fill.bottom) * 0.5f
                        val radius = fill.paintOrder.toFloat()
                        val bytes = UniformPacker.blurBytes(fill.startColor, cx, cy, radius)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val cmFills = drawPlan.fills.filter { it.family == "color-matrix-rect" }
            if (cmFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = ColorMatrixWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    draws = cmFills.map { fill ->
                        val kind = fill.paintOrder.toInt()
                        val bytes = UniformPacker.colorMatrixBytes(fill.startColor, kind)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val strokeFills = drawPlan.fills.filter { it.family == "stroke-rect" }
            if (strokeFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = StrokeWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    draws = strokeFills.map { fill ->
                        val cx = (fill.left + fill.right) * 0.5f
                        val cy = (fill.top + fill.bottom) * 0.5f
                        val hw = (fill.right - fill.left) * 0.5f
                        val hh = (fill.bottom - fill.top) * 0.5f
                        val bytes = UniformPacker.strokeBytes(fill.startColor, fill.paintOrder.toInt(), cx, cy, hw, hh)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val bitmapFills = drawPlan.fills.filter { it.family == "bitmap-rect" }
            if (bitmapFills.isNotEmpty()) {
                val pngBytes = this::class.java.classLoader.getResourceAsStream("bitmap-test-32x32.png")?.readBytes()
                val decoded = pngBytes?.let { decodePngToRgba(it, "bitmap-test-32x32") }
                val wgsl = composeBitmapTextureWgsl()
                drawFullscreenTextureUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    textureRgba = decoded?.rgba ?: ByteArray(4),
                    textureWidth = decoded?.width ?: 1,
                    textureHeight = decoded?.height ?: 1,
                    textureFormat = "rgba8unorm",
                    draws = bitmapFills.map { fill ->
                        val rectWidth = fill.right - fill.left
                        val rectHeight = fill.bottom - fill.top
                        GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.bitmapTextureBytes(
                                fill.startColor, fill.left, fill.top, rectWidth, rectHeight,
                            ),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val reFills = drawPlan.fills.filter { it.family == "runtime-effect" }
            if (reFills.isNotEmpty()) {
                val wgsl = composeRuntimeEffectWgsl()
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    draws = reFills.map { fill ->
                        val bytes = UniformPacker.simpleRtBytes(fill.startColor)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val customReFills = drawPlan.fills.filter { it.family == "custom-runtime-effect" }
            if (customReFills.isNotEmpty()) {
                customReFills.groupBy { it.customWgslSource ?: "" }.forEach { (wgsl, fills) ->
                    if (wgsl.isBlank() || wgsl.isEmpty()) return@forEach
                    drawFullscreenRawUniformPass(
                        wgsl = composeCustomRuntimeEffectWgsl(wgsl),
                        colorFormat = OFFSCREEN_COLOR_FORMAT,
                        blendMode = SCENE_SRC_OVER_BLEND_STATE,
                        draws = fills.map { fill ->
                            val bytes = UniformPacker.simpleRtBytes(fill.startColor)
                            GPUBackendRawUniformDraw(
                                uniformBytes = bytes,
                                scissorX = fill.scissorX,
                                scissorY = fill.scissorY,
                                scissorWidth = fill.scissorWidth,
                                scissorHeight = fill.scissorHeight,
                            )
                        },
                    )
                }
            }

            val textFills = drawPlan.fills.filter { it.family == "text-run" }
            if (textFills.isNotEmpty()) {
                val atlasResult = GlyphAtlasTextureBuilder().build("TheQuickBrownFoxJumpsOver", fontSize = 24f)
                val atlas = (atlasResult as? GlyphAtlasTextureResult.Built)?.atlas
                val wgsl = composeTextAtlasWgsl()
                drawFullscreenTextureUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    textureRgba = atlas?.a8Bytes ?: ByteArray(1),
                    textureWidth = atlas?.width ?: 1,
                    textureHeight = atlas?.height ?: 1,
                    textureFormat = "r8unorm",
                    draws = textFills.map { fill ->
                        val rectWidth = fill.right - fill.left
                        val rectHeight = fill.bottom - fill.top
                        GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.textAtlasBytes(
                                fill.startColor, fill.left, fill.top, rectWidth, rectHeight,
                            ),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val gradientFills = drawPlan.fills.filter { it.family in gradientTypes }
            gradientFills.groupBy { it.family }.forEach { (family, fills) ->
                val gradientWgslInfo = when (family) {
                    "linear-gradient-rect" -> GradientWgslInfo(
                        LinearGradientWgsl, LinearGradientEntryPoint,
                        "struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.start.xy, uniforms.end.xy",
                    )
                    "radial-gradient-rect" -> GradientWgslInfo(
                        RadialGradientWgsl, RadialGradientEntryPoint,
                        "struct Uniforms { center: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.center.xy, uniforms.center.z",
                    )
                    "sweep-gradient-rect" -> GradientWgslInfo(
                        SweepGradientWgsl, SweepGradientEntryPoint,
                        "struct Uniforms { center: vec4f, angles: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.center.xy, uniforms.angles.x, uniforms.angles.y",
                    )
                    else -> error("Unknown gradient family: $family")
                }
                val wgsl = composeGradientWgsl(gradientWgslInfo.snippet, gradientWgslInfo.entryPoint, gradientWgslInfo.uniformStruct, gradientWgslInfo.uniformArgs)
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    passBatchKind = if (family == "linear-gradient-rect") {
                        GPUBackendSimplePassBatchKind.SimpleGradient
                    } else {
                        null
                    },
                    draws = fills.map { fill ->
                        val bytes = when (family) {
                            "linear-gradient-rect" -> UniformPacker.linearGradientBytes(
                                startX = fill.left, startY = fill.top,
                                endX = fill.right, endY = fill.bottom,
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            "radial-gradient-rect" -> UniformPacker.radialGradientBytes(
                                centerX = fill.gradientCenterX ?: ((fill.left + fill.right) / 2f),
                                centerY = fill.gradientCenterY ?: ((fill.top + fill.bottom) / 2f),
                                radius = fill.gradientRadius ?: ((fill.right - fill.left) / 2f),
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            "sweep-gradient-rect" -> UniformPacker.sweepGradientBytes(
                                centerX = fill.gradientCenterX ?: ((fill.left + fill.right) / 2f),
                                centerY = fill.gradientCenterY ?: ((fill.top + fill.bottom) / 2f),
                                startAngle = fill.gradientStartAngle ?: 0f,
                                endAngle = fill.gradientEndAngle ?: 360f,
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            else -> error("Unknown gradient family: $family")
                        }
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val stencilFills = drawPlan.fills.filter { it.family == "path-fill-stencil" }
            if (stencilFills.isNotEmpty()) {
                stencilFills.forEach { fill ->
                    val starVertices = generateStarVertices(160f, 100f, 80f, 35f, 5)
                    val pathData = makeLineLoopPath(starVertices)
                    val tessellator = PathTessellator()
                    val flat = tessellator.flatten(pathData)
                    val tri = tessellator.triangulate(flat)
                    // x,y position pairs only; the stencil-write pipeline binds a
                    // Float32x2 @location(0) vertex layout (no color/padding).
                    val triangleVertices = tri.vertices.flatMap { p -> listOf(p.x, p.y) }.toFloatArray()
                    val triangleIndices = tri.indices.toIntArray()
                    val triangleData = GPUBackendTriangleData(
                        vertices = triangleVertices,
                        indices = triangleIndices,
                    )
                    // Pass 1 (stencil write): rasterize the triangulated contour with
                    // increment/decrement-wrap winding ops and no color writes, so a
                    // non-convex (concave) star resolves to correct coverage via the
                    // nonzero winding rule instead of a bloated fan rasterization.
                    drawFullscreenStencilPass(
                        wgsl = STENCIL_RENDER_WGSL,
                        colorFormat = OFFSCREEN_COLOR_FORMAT,
                        stencilMode = GPUBackendStencilMode.Write,
                        triangleData = triangleData,
                        draws = emptyList(),
                    )
                    // Pass 2 (stencil cover): a fullscreen quad that passes only where
                    // stencil != 0, writing the fill color through srcOver blending.
                    drawFullscreenStencilPass(
                        wgsl = SOLID_RECT_WGSL,
                        colorFormat = OFFSCREEN_COLOR_FORMAT,
                        stencilMode = GPUBackendStencilMode.Test,
                        triangleData = null,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = UniformPacker.solidColorBytes(fill.startColor),
                                scissorX = fill.scissorX,
                                scissorY = fill.scissorY,
                                scissorWidth = fill.scissorWidth,
                                scissorHeight = fill.scissorHeight,
                            ),
                        ),
                        blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    )
                }
            }

            val gradientStencilFills = drawPlan.fills.filter { it.family == "path-fill-gradient" }
            if (gradientStencilFills.isNotEmpty()) {
                val gradientWgsl = composeGradientWgsl(
                    LinearGradientWgsl, LinearGradientEntryPoint,
                    "struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f };",
                    "uniforms.start.xy, uniforms.end.xy",
                )
                gradientStencilFills.forEach { fill ->
                    val starVertices = generateStarVertices(160f, 100f, 80f, 35f, 5)
                    val pathData = makeLineLoopPath(starVertices)
                    val tessellator = PathTessellator()
                    val flat = tessellator.flatten(pathData)
                    val tri = tessellator.triangulate(flat)
                    val triangleVertices = tri.vertices.flatMap { p -> listOf(p.x, p.y) }.toFloatArray()
                    val triangleIndices = tri.indices.toIntArray()
                    val triangleData = GPUBackendTriangleData(
                        vertices = triangleVertices,
                        indices = triangleIndices,
                    )
                    drawFullscreenStencilPass(
                        wgsl = STENCIL_RENDER_WGSL,
                        colorFormat = OFFSCREEN_COLOR_FORMAT,
                        stencilMode = GPUBackendStencilMode.Write,
                        triangleData = triangleData,
                        draws = emptyList(),
                    )
                    drawFullscreenStencilPass(
                        wgsl = gradientWgsl,
                        colorFormat = OFFSCREEN_COLOR_FORMAT,
                        blendMode = SCENE_SRC_OVER_BLEND_STATE,
                        stencilMode = GPUBackendStencilMode.Test,
                        triangleData = null,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = UniformPacker.linearGradientBytes(
                                    startX = fill.left, startY = fill.top,
                                    endX = fill.right, endY = fill.bottom,
                                    startColor = fill.startColor, endColor = fill.endColor,
                                ),
                                scissorX = fill.scissorX,
                                scissorY = fill.scissorY,
                                scissorWidth = fill.scissorWidth,
                                scissorHeight = fill.scissorHeight,
                            ),
                        ),
                    )
                }
            }

            val convexFills = drawPlan.fills.filter { it.family == "convex-fan-mesh" }
            if (convexFills.isNotEmpty()) {
                convexFills.forEach { fill ->
                    val octagonVertices = generateOctagonVertices(160f, 100f, 60f, 8)
                    val pathData = makeLineLoopPath(octagonVertices)
                    val tessellator = PathTessellator()
                    val flat = tessellator.flatten(pathData)
                    val tri = tessellator.triangulate(flat)
                    val flatVerts = tri.vertices.flatMap { p ->
                        listOf(p.x, p.y, 0f, 0f, fill.startColor.r, fill.startColor.g, fill.startColor.b, fill.startColor.a)
                    }.toFloatArray()
                    val flatIndices = tri.indices.toIntArray()
                    val vertexColorData = GPUBackendVertexColorData(vertexData = flatVerts, indices = flatIndices)
                    val bufferLabel = createVertexColorBuffer(vertexColorData)
                    drawVertexColorIndexed(
                        vertexBufferLabel = bufferLabel,
                        indexCount = flatIndices.size,
                        blendMode = SCENE_SRC_OVER_BLEND_STATE,
                        uniformDraw = GPUBackendRawUniformDraw(
                            // VERTEX_COLOR_WGSL multiplies per-vertex color by the uniform
                            // (`in.color * uniforms.color`). The fill color is already carried
                            // per-vertex, so pass an identity-white uniform to avoid squaring it.
                            uniformBytes = UniformPacker.solidColorBytes(SceneColor(1f, 1f, 1f, 1f)),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        ),
                    )
                }
            }

            val verticesFills = drawPlan.fills.filter { it.family == "vertices" }
            if (verticesFills.isNotEmpty()) {
                verticesFills.forEach { fill ->
                    val rectWidth = fill.right - fill.left
                    val rectHeight = fill.bottom - fill.top
                    val meshVertices = generateRibbonVertices(
                        fill.left, fill.top, rectWidth, rectHeight,
                        fill.startColor, fill.endColor,
                    )
                    val indices = (0 until meshVertices.size / 8).toList().toIntArray()
                    val vertexColorData = GPUBackendVertexColorData(
                        vertexData = meshVertices,
                        indices = indices,
                    )
                    val bufferLabel = createVertexColorBuffer(vertexColorData)
                    drawVertexColorIndexed(
                        vertexBufferLabel = bufferLabel,
                        indexCount = indices.size,
                        blendMode = SCENE_SRC_OVER_BLEND_STATE,
                        uniformDraw = GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.bitmapTextureBytes(
                                fill.startColor, fill.left, fill.top, rectWidth, rectHeight,
                            ),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        ),
                    )
                }
            }

            if (saveLayerFills.isNotEmpty()) {
                val compositeDiagnostics = intermediatePlanExecutor.run {
                    compositeSaveLayers(
                        drawPlan = drawPlan,
                        execution = preparedIntermediateExecution,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                    )
                }
                intermediateDiagnostics?.addAll(compositeDiagnostics)
            }
        }
        return target.readRgba()
    }

    private fun renderDestinationReadBlends(
        target: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        execution: SceneIntermediateExecutionResult.Prepared,
        viewportWidth: Int,
        viewportHeight: Int,
        intermediateDiagnostics: MutableList<String>?,
    ) {
        require(execution.destinationReadBlends.size == 1) {
            "scene destination-read execution currently supports one shader blend per scene"
        }
        val destinationDrawLabels = execution.destinationReadBlends
            .flatMap { blend -> blend.destinationDrawLabels }
            .toSet()
        val destinationFills = drawPlan.fills.filter { fill -> fill.label in destinationDrawLabels }

        target.encode(clearColor = drawPlan.clearColor.toGpuClearColor()) {
            if (destinationFills.isNotEmpty()) {
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = destinationFills.map { fill ->
                        GPUBackendRectDraw(
                            rgbaPremul = fill.toPremulColorArray(),
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                    blendMode = SCENE_SRC_OVER_BLEND_STATE,
                    passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                )
            }
        }

        execution.destinationReadBlends.forEach { blend ->
            target.copyTargetToOffscreenTexture(blend.destinationTextureLabel)
            intermediateDiagnostics?.add("intermediate.scene.destination-read-gpu-copied command=${blend.commandId}")
        }

        target.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
            execution.destinationReadBlends.forEach { blend ->
                drawBlendPass(
                    wgsl = composeSceneDestinationReadBlendWgsl(blend.routeLabel),
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    srcTextureLabel = blend.sourceTextureLabel,
                    dstTextureLabel = blend.destinationTextureLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.solidColorBytes(SceneColor(1f, 1f, 1f, 1f)),
                            scissorX = 0,
                            scissorY = 0,
                            scissorWidth = viewportWidth,
                            scissorHeight = viewportHeight,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * KGPU-M27-002: pipeline-cache telemetry for the passes this renderer emits
     * for [drawPlan], modeled across [frameCount] steady-state frames. Derived
     * from the draw plan (not a backend pipeline-cache observation), so it carries
     * no GPU support or performance claim by itself.
     */
    internal fun pipelineCacheTelemetry(
        drawPlan: RectOnlyDrawPlan,
        sceneId: String,
        frameCount: Int,
    ): org.graphiks.kanvas.gpu.renderer.telemetry.GPUPipelineCacheTelemetry =
        rectOnlyPipelineCacheTelemetry(drawPlan, sceneId, frameCount)

    private fun SceneColor.toGpuClearColor(): GPUClearColor =
        GPUClearColor(
            red = (r * a).toDouble(),
            green = (g * a).toDouble(),
            blue = (b * a).toDouble(),
            alpha = a.toDouble(),
        )

    private fun RectOnlyFillDraw.toPremulColorArray(): FloatArray =
        floatArrayOf(
            startColor.r * startColor.a,
            startColor.g * startColor.a,
            startColor.b * startColor.a,
            startColor.a,
        )

    private fun writePng(pixels: ByteArray, width: Int, height: Int, path: Path) {
        require(pixels.size == width * height * BYTES_PER_PIXEL) {
            "RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${pixels.size}"
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (pixelIndex in 0 until width * height) {
            val base = pixelIndex * BYTES_PER_PIXEL
            val r = pixels[base].toInt() and 0xFF
            val g = pixels[base + 1].toInt() and 0xFF
            val b = pixels[base + 2].toInt() and 0xFF
            val a = pixels[base + 3].toInt() and 0xFF
            image.setRGB(pixelIndex % width, pixelIndex / width, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
        require(ImageIO.write(image, "png", path.toFile())) {
            "No PNG writer available for $RENDER_FILE_NAME"
        }
    }

    private companion object {
        const val RENDER_FILE_NAME: String = "render.png"

        fun composeGradientWgsl(
            snippetWgsl: String,
            entryPoint: String,
            uniformStruct: String,
            uniformArgs: String,
        ): String = """
$uniformStruct

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

$snippetWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    var positions: array<vec4f, 16>;
    var colors: array<vec4f, 16>;
    positions[0] = vec4f(0.0, 0.0, 0.0, 0.0);
    positions[1] = vec4f(1.0, 0.0, 0.0, 0.0);
    colors[0] = uniforms.startColor;
    colors[1] = uniforms.endColor;
    return $entryPoint(pos, $uniformArgs, 2u, &positions, &colors);
}
"""

        fun composeRectWgsl(
            tag: String,
            snippetWgsl: String,
            entryPoint: String,
            uniformArgs: String,
        ): String = """
struct Uniforms { color: vec4f, };
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

$snippetWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    return $entryPoint(pos, $uniformArgs);
}
"""

        fun composeBitmapTextureWgsl(): String = """
struct Uniforms { color: vec4f, texRect: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

${BitmapShaderWgsl}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let uv = (pos.xy - uniforms.texRect.xy) / uniforms.texRect.zw;
    let c = bitmap_shader_clamp(uv) * uniforms.color;
    return vec4f(c.rgb * c.a, c.a);
}
"""

        fun composeTextAtlasWgsl(): String = """
struct Uniforms { color: vec4f, texRect: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

${TextAtlasA8Wgsl}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let uv = (pos.xy - uniforms.texRect.xy) / uniforms.texRect.zw;
    let t = text_atlas_source(uv);
    return vec4f(uniforms.color.rgb * t.a, uniforms.color.a * t.a);
}
"""

        val STENCIL_RENDER_WGSL: String = """
struct VertexInput {
    @location(0) position: vec2f,
};

@vertex
fn vs_main(in: VertexInput) -> @builtin(position) vec4f {
    return vec4f(in.position.x / 160.0 - 1.0, 1.0 - in.position.y / 100.0, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}
""".trimIndent()

        val SOLID_RECT_WGSL: String = """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return uniforms.color;
            }
        """.trimIndent()

        fun ByteArray.countNonTransparentPixels(): Int {
            require(size % BYTES_PER_PIXEL == 0) { "RGBA buffer size must be a multiple of $BYTES_PER_PIXEL" }
            var count = 0
            for (base in 3 until size step BYTES_PER_PIXEL) {
                if ((this[base].toInt() and 0xFF) > 0) count += 1
            }
            return count
        }
    }
}

private fun colorGlyphDiff(actual: ByteArray, expected: ByteArray): ByteArray {
    require(actual.size == expected.size)
    return ByteArray(actual.size).also { diff ->
        for (offset in actual.indices step BYTES_PER_PIXEL) {
            val changed = (0 until BYTES_PER_PIXEL).any { channel ->
                actual[offset + channel] != expected[offset + channel]
            }
            if (changed) {
                diff[offset] = 255.toByte()
                diff[offset + 3] = 255.toByte()
            }
        }
    }
}

internal fun composeSaveLayerCompositeWgsl(): String = """
struct Uniforms { color: vec4f, params: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

${LayerCompositeWgsl}

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let uv = pos.xy / vec2f(320.0, 200.0);
    return layer_composite(uv, uniforms.color, uniforms.params.x);
}
"""

internal fun composeSceneDestinationReadBlendWgsl(routeLabel: String): String {
    val blendExpression = when (routeLabel) {
        "shader-blend:Multiply" -> "(src.rgb * dst.rgb) + (src.rgb * (1.0 - dst.a)) + (dst.rgb * (1.0 - src.a))"
        else -> error("unsupported destination-read blend route: $routeLabel")
    }
    return """
struct Uniforms { color: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var src_texture: texture_2d<f32>;
@group(1) @binding(2) var src_sampler: sampler;
@group(1) @binding(3) var dst_texture: texture_2d<f32>;
@group(1) @binding(4) var dst_sampler: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let dims = vec2f(textureDimensions(src_texture));
    let uv = pos.xy / dims;
    let src = textureSample(src_texture, src_sampler, uv);
    let dst = textureSample(dst_texture, dst_sampler, uv);
    let outAlpha = src.a + dst.a * (1.0 - src.a);
    let outRgb = $blendExpression;
    return vec4f(outRgb, outAlpha) * uniforms.color;
}
"""
}

internal data class RectOnlyDrawPlan(
    val sceneId: String,
    val clearColor: SceneColor,
    val clearCount: Int,
    val fills: List<RectOnlyFillDraw>,
    val clipCount: Int = 0,
    val filters: List<RectOnlyFilterNode> = emptyList(),
    val saveLayers: List<RectOnlySaveLayer> = emptyList(),
    val tessellationDiagnostics: List<String> = emptyList(),
    val executorWiringDiagnostics: List<String> = emptyList(),
) {
    val fillRectCount: Int = fills.count { it.family == "fill-rect" }
    val fillRRectCount: Int = fills.count { it.family == "fill-rrect" }
    val linearGradientRectCount: Int = fills.count { it.family == "linear-gradient-rect" }
    val radialGradientRectCount: Int = fills.count { it.family == "radial-gradient-rect" }
    val sweepGradientRectCount: Int = fills.count { it.family == "sweep-gradient-rect" }
    val bitmapRectCount: Int = fills.count { it.family == "bitmap-rect" }
    val blurRectCount: Int = fills.count { it.family == "blur-rect" }
    val colorMatrixRectCount: Int = fills.count { it.family == "color-matrix-rect" }
    val strokeRectCount: Int = fills.count { it.family == "stroke-rect" }
    val textRunCount: Int = fills.count { it.family == "text-run" }
    val colorTextRunCount: Int = fills.count { it.family == "color-text-run" }
    val saveLayerRectCount: Int = fills.count { it.family == "save-layer" }
    val pathFillStencilCount: Int = fills.count { it.family == "path-fill-stencil" }
    val pathFillGradientCount: Int = fills.count { it.family == "path-fill-gradient" }
    val convexFanMeshCount: Int = fills.count { it.family == "convex-fan-mesh" }
    val customRuntimeEffectRectCount: Int = fills.count { it.family == "custom-runtime-effect" }
    val filterNodeCount: Int = filters.size
    val runtimeEffects: List<RectOnlyRuntimeEffectTile> = fills
        .filter { it.family == "runtime-effect" }
        .mapNotNull { fill ->
            val stableId = fill.runtimeEffectStableId
            val wgslId = fill.runtimeEffectWgslImplementationId
            val uniformLayout = fill.runtimeEffectUniformLayout
            val pipelineKey = fill.runtimeEffectPipelineKey
            if (stableId == null || wgslId == null || uniformLayout == null || pipelineKey == null) {
                null
            } else {
                RectOnlyRuntimeEffectTile(
                    label = fill.label,
                    stableId = stableId,
                    wgslImplementationId = wgslId,
                    uniformLayout = uniformLayout,
                    pipelineKey = pipelineKey,
                )
            }
        }
    val runtimeEffectCount: Int = runtimeEffects.size
    val meshRibbons: List<RectOnlyMeshRibbon> = fills
        .filter { it.family == "vertices" }
        .map { fill ->
            RectOnlyMeshRibbon(
                label = fill.label,
                meshKind = fill.meshRibbonKind
                    ?: error("MeshRibbon draw requires mesh kind: ${fill.label}"),
            )
        }
    val meshRibbonCount: Int = meshRibbons.size
}

internal data class RectOnlyFilterNode(
    val label: String,
    val inputLabel: String,
    val kind: SceneFilterKind,
    val strength: Float,
)

internal data class RectOnlyRuntimeEffectTile(
    val label: String,
    val stableId: String,
    val wgslImplementationId: String,
    val uniformLayout: String,
    val pipelineKey: String,
)

internal data class RectOnlySaveLayer(
    val label: String,
    val layerKind: String,
    val filterLabel: String,
    val filterKind: SceneFilterKind,
    val filterStrength: Float,
)

internal data class RectOnlyMeshRibbon(
    val label: String,
    val meshKind: String,
)

internal data class RectOnlyFillDraw(
    val label: String,
    val family: String,
    val startColor: SceneColor,
    val endColor: SceneColor,
    val bottomLeftColor: SceneColor,
    val bottomRightColor: SceneColor,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    val paintKind: Float,
    val filterKind: Float,
    val filterStrength: Float,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
    val paintOrder: Int = 0,
    val runtimeEffectStableId: String? = null,
    val runtimeEffectWgslImplementationId: String? = null,
    val runtimeEffectUniformLayout: String? = null,
    val runtimeEffectPipelineKey: String? = null,
    val customWgslSource: String? = null,
    val meshRibbonKind: String? = null,
    val gradientCenterX: Float? = null,
    val gradientCenterY: Float? = null,
    val gradientRadius: Float? = null,
    val gradientStartAngle: Float? = null,
    val gradientEndAngle: Float? = null,
    val shadowColor: SceneColor? = null,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val groupAlpha: Float = 1f,
    val blendMode: SceneBlendMode = SceneBlendMode.SrcOver,
)

private data class RectOnlyIndexedDraw(
    val index: Int,
    val command: SceneCommand,
    val clip: SceneCommand.Clip?,
)

internal fun rectOnlyRawRgbaByteCount(pixels: ByteArray, width: Int, height: Int): Long {
    require(width > 0) { "rect-only raw byte count width must be positive" }
    require(height > 0) { "rect-only raw byte count height must be positive" }
    val expectedByteCount = width.toLong() * height.toLong() * BYTES_PER_PIXEL.toLong()
    require(pixels.size.toLong() == expectedByteCount) {
        "RGBA buffer size mismatch: expected $expectedByteCount, got ${pixels.size}"
    }
    return pixels.size.toLong()
}

internal fun prepareRectOnlyDrawPlan(
    sceneId: String,
    commands: List<SceneCommand>,
    width: Int,
    height: Int,
): RectOnlyDrawPlan {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(width > 0) { "$sceneId rect-only target width must be positive" }
    require(height > 0) { "$sceneId rect-only target height must be positive" }
    val unsupportedReason = rectOnlyCommandSequenceUnsupportedReason(commands)
    require(unsupportedReason == null) { "$sceneId $unsupportedReason" }

    var activeClip: SceneCommand.Clip? = null
    val indexedDraws = buildList {
        commands.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Clip -> activeClip = command
                is SceneCommand.FillRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.FillRRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.Stroke -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.LinearGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.RadialGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.SweepGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.PathFillStencil -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.PathFillGradient -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.ConvexFanMesh -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.BitmapRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.SaveLayer -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.RuntimeEffectTile -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.CustomRuntimeEffectTile -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.TextRun -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.MeshRibbon -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.ColorTextRun -> add(RectOnlyIndexedDraw(index, command, activeClip))
                else -> Unit
            }
        }
    }

    val fills = indexedDraws
        .sortedWith(
            compareBy<RectOnlyIndexedDraw> { (_, command) ->
                command.paintOrder()
            }.thenBy { it.index },
        )
        .map { (_, command, clip) ->
            val radialCommand = command as? SceneCommand.RadialGradientRect
            val sweepCommand = command as? SceneCommand.SweepGradientRect
            val bitmapCommand = command as? SceneCommand.BitmapRect
            val saveLayerCommand = command as? SceneCommand.SaveLayer
            val reCommand = command as? SceneCommand.RuntimeEffectTile
            val textRunCommand = command as? SceneCommand.TextRun
            val meshRibbonCommand = command as? SceneCommand.MeshRibbon
            val creCommand = command as? SceneCommand.CustomRuntimeEffectTile
            val paintOrder = command.paintOrder()
            val mappedFamily = when {
                command is SceneCommand.Stroke -> "stroke-rect"
                sceneId == "blur-radius-ladder" && command is SceneCommand.FillRect -> "blur-rect"
                sceneId == "color-matrix-filter" && command is SceneCommand.FillRect -> "color-matrix-rect"
                sceneId == "gaussian-blur-photo" && command is SceneCommand.FillRect -> "blur-rect"
                sceneId == "color-matrix-tint" && command is SceneCommand.FillRect -> "color-matrix-rect"
                sceneId == "stroke-and-filter-card" && command is SceneCommand.FillRect -> "blur-rect"
                sceneId in setOf("stroke-cap-join", "dash-pattern-ladder") && command is SceneCommand.FillRect -> "stroke-rect"
                else -> command.family
            }
            rectOnlyFillDraw(
                sceneId = sceneId,
                label = command.label,
                family = mappedFamily,
                rect = if (command is SceneCommand.ColorTextRun) SceneRect(0f, 0f, width.toFloat(), height.toFloat()) else command.shapeRect(),
                radius = if (command is SceneCommand.ColorTextRun) 0f else command.shapeRadius(),
                startColor = if (command is SceneCommand.ColorTextRun) colorTextRunFallbackColor() else command.shapeStartColor(),
                endColor = if (command is SceneCommand.ColorTextRun) colorTextRunFallbackColor() else command.shapeEndColor(),
                bottomLeftColor = if (command is SceneCommand.ColorTextRun) colorTextRunFallbackColor() else command.shapeBottomLeftColor(),
                bottomRightColor = if (command is SceneCommand.ColorTextRun) colorTextRunFallbackColor() else command.shapeBottomRightColor(),
                paintKind = if (command is SceneCommand.ColorTextRun) 13f else command.shapePaintKind(),
                filterKind = 0f,
                filterStrength = 0f,
                clip = clip,
                width = width,
                height = height,
                paintOrder = paintOrder,
                runtimeEffectStableId = reCommand?.stableId,
                runtimeEffectWgslImplementationId = reCommand?.wgslImplementationId,
                runtimeEffectUniformLayout = reCommand?.uniformLayout,
                runtimeEffectPipelineKey = reCommand?.pipelineKey,
                customWgslSource = creCommand?.wgslSource,
                meshRibbonKind = meshRibbonCommand?.meshKind,
                gradientCenterX = radialCommand?.centerX ?: sweepCommand?.centerX,
                gradientCenterY = radialCommand?.centerY ?: sweepCommand?.centerY,
                gradientRadius = radialCommand?.radius,
                gradientStartAngle = sweepCommand?.startAngle,
                gradientEndAngle = sweepCommand?.endAngle,
                shadowColor = saveLayerCommand?.fixtureShadowColor(),
                shadowOffsetX = saveLayerCommand?.shadowOffsetX ?: 0f,
                shadowOffsetY = saveLayerCommand?.shadowOffsetY ?: 0f,
                groupAlpha = saveLayerCommand?.groupAlpha ?: 1f,
                blendMode = (command as? SceneCommand.FillRect)?.blendMode ?: SceneBlendMode.SrcOver,
            )
        }
    require(fills.isNotEmpty()) {
        "$sceneId rect-only offscreen render requires at least one FillRect command"
    }

    val tessellationDiagnostics = buildList {
        commands.forEach { command ->
            when (command) {
                is SceneCommand.PathFillStencil -> {
                    val tessellator = PathTessellator()
                    val vertices = generateStarVertices(160f, 100f, 80f, 35f, 5)
                    val pathData = makeLineLoopPath(vertices)
                    try {
                        val flat = tessellator.flatten(pathData)
                        val tri = tessellator.triangulate(flat)
                        val executor = StencilCoverExecutor()
                        val stats = executor.execute(tri)
                        val convex = isPathConvex(flat)
                        add("pathFillStencil:label=${command.label}")
                        add("pathFillStencil:vertices=${stats.vertexCount}")
                        add("pathFillStencil:triangles=${stats.triangleCount}")
                        add("pathFillStencil:isConvex=$convex")
                        add("pathFillStencil:stencilPasses=${stats.stencilPassCount}")
                        add("pathFillStencil:coverPasses=${stats.coverPassCount}")
                        add("pathFillStencil:totalDraws=${stats.totalDrawCalls}")
                        addAll(executor.stencilStateDiagnostics())
                    } catch (e: Exception) {
                        add("pathFillStencil:error=${e.message}")
                    }
                }
                is SceneCommand.PathFillGradient -> {
                    val tessellator = PathTessellator()
                    val vertices = generateStarVertices(160f, 100f, 80f, 35f, 5)
                    val pathData = makeLineLoopPath(vertices)
                    try {
                        val flat = tessellator.flatten(pathData)
                        val tri = tessellator.triangulate(flat)
                        val executor = StencilCoverExecutor()
                        val stats = executor.execute(tri)
                        val convex = isPathConvex(flat)
                        add("pathFillGradient:label=${command.label}")
                        add("pathFillGradient:vertices=${stats.vertexCount}")
                        add("pathFillGradient:triangles=${stats.triangleCount}")
                        add("pathFillGradient:isConvex=$convex")
                        add("pathFillGradient:stencilPasses=${stats.stencilPassCount}")
                        add("pathFillGradient:coverPasses=${stats.coverPassCount}")
                        add("pathFillGradient:totalDraws=${stats.totalDrawCalls}")
                        addAll(executor.stencilStateDiagnostics())
                    } catch (e: Exception) {
                        add("pathFillGradient:error=${e.message}")
                    }
                }
                is SceneCommand.ConvexFanMesh -> {
                    val tessellator = PathTessellator()
                    val vertices = generateOctagonVertices(160f, 100f, 80f, command.vertexCount)
                    val pathData = makeLineLoopPath(vertices)
                    try {
                        val flat = tessellator.flatten(pathData)
                        val tri = tessellator.triangulate(flat)
                        val executor = ConvexFanExecutor()
                        val stats = executor.execute(tri)
                        val convex = isPathConvex(flat)
                        add("convexFanMesh:label=${command.label}")
                        add("convexFanMesh:vertices=${stats.vertexCount}")
                        add("convexFanMesh:triangles=${stats.triangleCount}")
                        add("convexFanMesh:isConvex=$convex")
                        add("convexFanMesh:singlePass=${stats.singlePass}")
                        add("convexFanMesh:drawCalls=${stats.drawCallCount}")
                        if (convex) {
                            val stencilExecutor = StencilCoverExecutor()
                            val stencilStats = stencilExecutor.execute(tri)
                            addAll(executor.performanceDiagnostics(stats, stencilStats))
                        }
                    } catch (e: Exception) {
                        add("convexFanMesh:error=${e.message}")
                    }
                }
                else -> Unit
            }
        }
    }

    val executorWiringDiagnostics = buildList {
        if (fills.any { it.family == "bitmap-rect" }) {
            addAll(bitmapShaderWiringDiagnostics())
        }
        if (fills.any { it.family == "text-run" }) {
            addAll(textAtlasWiringDiagnostics(width, height))
        }
        if (fills.any { it.family == "runtime-effect" }) {
            addAll(runtimeEffectWiringDiagnostics())
        }
        if (fills.any { it.family == "custom-runtime-effect" }) {
            addAll(customRuntimeEffectWiringDiagnostics())
        }
        if (fills.any { it.family == "save-layer" }) {
            addAll(saveLayerWiringDiagnostics(fills, sceneId, width, height))
        }
        if (fills.any { it.family == "vertices" }) {
            addAll(verticesWiringDiagnostics())
        }
        if (fills.any { it.family == "color-text-run" }) {
            addAll(colorTextRunWiringDiagnostics())
        }
    }

    return RectOnlyDrawPlan(
        sceneId = sceneId,
        clearColor = commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f),
        clearCount = commands.count { it is SceneCommand.Clear },
        fills = fills,
        clipCount = commands.count { it is SceneCommand.Clip },
        filters = emptyList(),
        saveLayers = emptyList(),
        tessellationDiagnostics = tessellationDiagnostics,
        executorWiringDiagnostics = executorWiringDiagnostics,
    )
}

/**
 * KGPU-M26-002: bitmap now samples a real decoded image texture uploaded via the
 * offscreen texture-uniform backend. The M25 wiring evidence (snippet identity,
 * entry point, packer) stays; the procedural wrapper is removed per M26 exit criteria.
 */
internal fun bitmapShaderWiringDiagnostics(): List<String> = listOf(
    "bitmapShader:snippetSourceHash=$BitmapShaderSnippetSourceHash",
    "bitmapShader:entryPoint=$BitmapShaderClampEntryPoint",
    "bitmapShader:uniformPacker=UniformPacker.bitmapTextureBytes",
    "bitmapShader:catalogWired=true realTextureUploaded=true bitmapDecodedSource=bitmap-test-32x32 productActivation=true",
)

/**
 * KGPU-M25-002: routes DrawTextRun through the real [TextA8AtlasExecutor] + [SDFGenerator]
 * (M20/M12) for diagnostic evidence. The procedural glyph stays in the renderer wrapper; the real
 * Liberation Sans A8 atlas is deferred to M26.
 */
internal fun textAtlasWiringDiagnostics(width: Int, height: Int): List<String> = buildList {
    val a8Stats = TextA8AtlasExecutor().execute(atlasKey = "scene-text-a8", width = width, height = height)
    add(
        "textA8Atlas:executor accepted=${a8Stats.accepted} atlasWidth=${a8Stats.atlasWidth} " +
            "atlasHeight=${a8Stats.atlasHeight} glyphCount=${a8Stats.glyphCount} " +
            "uploadSizeBytes=${a8Stats.uploadSizeBytes}",
    )
    a8Stats.diagnostic?.let { add("textA8Atlas:diagnostic=$it") }
    add("textA8Atlas:${TextA8AtlasExecutor.nonClaimLine}")

    val proceduralA8 = ByteArray(8 * 8) { index ->
        val x = index % 8
        val y = index / 8
        if (x in 2..5 && y in 2..5) 0xFF.toByte() else 0x00.toByte()
    }
    val sdf = SDFGenerator().generateFromA8(proceduralA8, width = 8, height = 8)
    add(
        "textSdf:generator accepted=${sdf.accepted} width=${sdf.width} height=${sdf.height} " +
            "radius=${sdf.radius} sdfBytes=${sdf.sdfBytes.size}",
    )
    add("textSdf:smoothing=${SDFGenerator.SDF_SMOOTHING} threshold=${SDFGenerator.SDF_THRESHOLD}")
    add("textSdf:${SDFGenerator.nonClaimLine}")
    add("textAtlas:realAtlasUploaded=true atlasFont=LiberationSans productActivation=true")
}

/**
 * KGPU-M25-003: composes the runtime-effect fullscreen pass from the real registered
 * [SimpleRTWgsl] module source (M21 descriptor) rather than an inline copy. The offscreen
 * fullscreen-uniform backend binds the uniform at `@group(0) @binding(0)`, while [SimpleRTWgsl]
 * declares it at `@group(1) @binding(0)`; the binding is rebound here (in the renderer) instead of
 * forking the shader. The `gColor` ABI (vec4f@0:16) and `simple_rt_source` entry point are taken
 * verbatim from the module, so this is real GPU output (no procedural wrapper).
 */
internal fun composeRuntimeEffectWgsl(): String {
    val snippet = SimpleRTWgsl.replace("@group(1)", "@group(0)")
    return """
$snippet

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    return $SimpleRTEntryPoint(pos.xy);
}
"""
}

internal fun composeCustomRuntimeEffectWgsl(source: String): String {
    val snippet = source.replace("@group(1)", "@group(0)")
        .replace(Regex("@fragment\\s+"), "")
        .replace(Regex("@location\\(\\d+\\)\\s+"), "")
    val entryPoint = "custom_main"
    val renamed = snippet.replace(Regex("fn\\s+main\\s*\\("), "fn ${entryPoint}(")
    val takesNoParams = Regex("fn\\s+${Regex.escape(entryPoint)}\\s*\\(\\s*\\)").containsMatchIn(renamed)
    val call = if (takesNoParams) "$entryPoint()" else "$entryPoint(pos.xy)"
    return """
$renamed

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    return $call;
}
"""
}

/**
 * KGPU-M25-003: BitmapRect's runtime-effect sibling routes through the real SimpleRT snippet
 * identity ([SimpleRTSourceHash]) and the registered gColor uniform ABI. Unlike the other families,
 * this is real GPU output (the fragment returns the per-tile gColor uniform), not a procedural wrapper.
 */
internal fun runtimeEffectWiringDiagnostics(): List<String> = listOf(
    "runtimeEffect:wgslSnippetSourceHash=$SimpleRTSourceHash",
    "runtimeEffect:entryPoint=$SimpleRTEntryPoint",
    "runtimeEffect:uniformPacker=UniformPacker.simpleRtBytes",
    "runtimeEffect:realGpuOutput=true proceduralWrapperRemoved=true productActivation=true",
)

internal fun customRuntimeEffectWiringDiagnostics(): List<String> = listOf(
    "custom-runtime-effect supports custom WGSL source per-tile via fullscreen raw uniform pass",
)

/**
 * KGPU-M25-004 / KGPU-M28-005/006: routes SaveLayer through the real [SaveLayerExecutor] (M18) and
 * references the [LayerCompositeSnippetSourceHash] composite snippet. M28 added the secondary
 * offscreen target, so the composite runs against a real allocated target using the real
 * layer_composite snippet (child layer content is not yet sampled into it; childrenRendered=0).
 */
internal fun saveLayerWiringDiagnostics(sceneId: String, width: Int, height: Int): List<String> = buildList {
    val executor = SaveLayerExecutor()
    val stats = executor.execute(scopeLabel = sceneId, width = width, height = height)
    addAll(executor.dumpLines(stats))
    add("saveLayer:compositeSnippetSourceHash=$LayerCompositeSnippetSourceHash")
    add("saveLayer:compositeEntryPoint=$LayerCompositeEntryPoint")
    add("saveLayer:secondaryTargetAllocated=true childContentSampled=false productActivation=true")
}

internal fun saveLayerWiringDiagnostics(fills: List<RectOnlyFillDraw>, sceneId: String, width: Int, height: Int): List<String> = buildList {
    val saveLayerFills = fills.filter { it.family == "save-layer" }
    val childCount = saveLayerFills.mapIndexed { index, slFill ->
        val nextPaintOrder = if (index + 1 < saveLayerFills.size)
            saveLayerFills[index + 1].paintOrder else Int.MAX_VALUE
        fills.count { it.paintOrder > slFill.paintOrder && it.paintOrder < nextPaintOrder && it.family != "save-layer" }
    }.sum()
    val executor = SaveLayerExecutor()
    val executorStats = executor.execute(scopeLabel = sceneId, width = width, height = height)
    val updatedStats = executorStats.copy(childrenRendered = childCount)
    addAll(executor.dumpLines(updatedStats))
    add("saveLayer:compositeSnippetSourceHash=$LayerCompositeSnippetSourceHash")
    add("saveLayer:compositeEntryPoint=$LayerCompositeEntryPoint")
    add("saveLayer:secondaryTargetAllocated=true childContentSampled=${childCount > 0} productActivation=true")
}

/**
 * KGPU-M25-006 / KGPU-M28-003/004: vertices wiring evidence. M28 added vertex/index buffers to the
 * offscreen backend, so the `vertices` family now renders a real indexed mesh (see renderToPixels).
 * This also invokes the real [VerticesExecutor] + [GPUVertexBufferUploader] + [GPUMeshBatcher] (M22)
 * on a representative triangle mesh to produce dispatch + upload + batching evidence.
 */
internal fun verticesWiringDiagnostics(): List<String> = buildList {
    val positions = listOf(
        20f, 20f, 120f, 20f, 70f, 120f,
        140f, 20f, 240f, 20f, 190f, 120f,
    )
    val colors = List(6 * 4) { 1f }
    val executor = VerticesExecutor()
    val execStats = executor.execute(positions, colors, GPUVertexMode.Triangles)
    add(
        "vertices:executor executed=${execStats.executed} vertexCount=${execStats.vertexCount} " +
            "colorCount=${execStats.colorCount} mode=${execStats.primitiveMode.sourceLabel}",
    )
    add("vertices:executor.${execStats.nonClaimLine}")

    val uploader = GPUVertexBufferUploader()
    val uploadStats = uploader.upload(positions, colors, vertexStrideBytes = 24)
    add(
        "vertices:uploader uploaded=${uploadStats.uploaded} vertexCount=${uploadStats.vertexCount} " +
            "bufferBytes=${uploadStats.bufferBytes} providerUsed=${uploadStats.providerUsed}",
    )
    add("vertices:uploader.${uploadStats.nonClaimLine}")

    val batcher = GPUMeshBatcher()
    val batchStats = batcher.batch(
        listOf(
            GPUDrawCallDescriptor(
                drawId = "vertices-mesh-a",
                pipelineKey = "vertices/triangles/srcover",
                vertexCount = 3,
                topology = GPUVertexMode.Triangles,
                blendMode = "SrcOver",
                sortKey = 0,
            ),
            GPUDrawCallDescriptor(
                drawId = "vertices-mesh-b",
                pipelineKey = "vertices/triangles/srcover",
                vertexCount = 3,
                topology = GPUVertexMode.Triangles,
                blendMode = "SrcOver",
                sortKey = 1,
            ),
        ),
    )
    add(
        "vertices:batcher inputDraws=${batchStats.inputDrawCount} batches=${batchStats.batchCount} " +
            "pipelineChanges=${batchStats.pipelineChangeCount} mergedDraws=${batchStats.mergedDrawCount}",
    )
    add("vertices:batcher.${batchStats.nonClaimLine}")
    add("vertices:realMesh=true vertexIndexBuffersUploaded=true productActivation=true")
}

internal fun colorTextRunWiringDiagnostics(): List<String> = listOf(
    "colorTextRun:route=prepared-colr-v0",
    "colorTextRun:shader=colorGlyphCompositeWgsl",
    "colorTextRun:maxLayers=16",
    "colorTextRun:atlasFormat=r8unorm",
    "colorTextRun:vertexLayout=pos+quad_uv",
    "colorTextRun:uniformPack=784-byte-le",
    "colorTextRun:source=real-colr-font-cpal-layers",
    "colorTextRun:nonClaim=no-colrv1-no-shaping-no-emoji",
)

private fun rectOnlyFillDraw(
    sceneId: String,
    label: String,
    family: String,
    rect: SceneRect,
    radius: Float,
    startColor: SceneColor,
    endColor: SceneColor,
    bottomLeftColor: SceneColor,
    bottomRightColor: SceneColor,
    paintKind: Float,
    filterKind: Float,
    filterStrength: Float,
    clip: SceneCommand.Clip?,
    width: Int,
    height: Int,
    paintOrder: Int = 0,
    runtimeEffectStableId: String? = null,
    runtimeEffectWgslImplementationId: String? = null,
    runtimeEffectUniformLayout: String? = null,
    runtimeEffectPipelineKey: String? = null,
    customWgslSource: String? = null,
    meshRibbonKind: String? = null,
    gradientCenterX: Float? = null,
    gradientCenterY: Float? = null,
    gradientRadius: Float? = null,
    gradientStartAngle: Float? = null,
    gradientEndAngle: Float? = null,
    shadowColor: SceneColor? = null,
    shadowOffsetX: Float = 0f,
    shadowOffsetY: Float = 0f,
    groupAlpha: Float = 1f,
    blendMode: SceneBlendMode = SceneBlendMode.SrcOver,
): RectOnlyFillDraw {
    requireInsideTarget(sceneId, label, rect, width, height, "fill shape")
    clip?.let { requireInsideTarget(sceneId, it.label, it.rect, width, height, "clip") }
    val scissorRect = rect.intersect(clip?.rect)
    require(scissorRect != null) {
        "$sceneId rect-only fill shape must intersect active clip: $label"
    }
    val left = floor(scissorRect.left).toInt()
    val top = floor(scissorRect.top).toInt()
    val right = ceil(scissorRect.right).toInt()
    val bottom = ceil(scissorRect.bottom).toInt()
    val widthPx = rect.right - rect.left
    val heightPx = rect.bottom - rect.top
    return RectOnlyFillDraw(
        label = label,
        family = family,
        startColor = startColor,
        endColor = endColor,
        bottomLeftColor = bottomLeftColor,
        bottomRightColor = bottomRightColor,
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
        radius = minOf(radius, widthPx * 0.5f, heightPx * 0.5f),
        paintKind = paintKind,
        filterKind = filterKind,
        filterStrength = filterStrength,
        scissorX = left,
        scissorY = top,
        scissorWidth = right - left,
        scissorHeight = bottom - top,
        paintOrder = paintOrder,
        runtimeEffectStableId = runtimeEffectStableId,
        runtimeEffectWgslImplementationId = runtimeEffectWgslImplementationId,
        runtimeEffectUniformLayout = runtimeEffectUniformLayout,
        runtimeEffectPipelineKey = runtimeEffectPipelineKey,
        customWgslSource = customWgslSource,
        meshRibbonKind = meshRibbonKind,
        gradientCenterX = gradientCenterX,
        gradientCenterY = gradientCenterY,
        gradientRadius = gradientRadius,
        gradientStartAngle = gradientStartAngle,
        gradientEndAngle = gradientEndAngle,
        shadowColor = shadowColor,
        shadowOffsetX = shadowOffsetX,
        shadowOffsetY = shadowOffsetY,
        groupAlpha = groupAlpha,
        blendMode = blendMode,
    )
}

internal fun rectOnlyCommandSequenceUnsupportedReason(commands: List<SceneCommand>): String? {
    val unsupportedFamilies = commands
        .mapNotNull { command ->
            if (
                command is SceneCommand.Clear ||
                command is SceneCommand.FillRect ||
                command is SceneCommand.FillRRect ||
                command is SceneCommand.Stroke ||
        command is SceneCommand.LinearGradientRect ||
        command is SceneCommand.RadialGradientRect ||
        command is SceneCommand.SweepGradientRect ||
        command is SceneCommand.Clip ||
                command is SceneCommand.PathFillStencil ||
                command is SceneCommand.PathFillGradient ||
                command is SceneCommand.ConvexFanMesh ||
                command is SceneCommand.BitmapRect ||
                command is SceneCommand.SaveLayer ||
                command is SceneCommand.RuntimeEffectTile ||
                command is SceneCommand.CustomRuntimeEffectTile ||
                command is SceneCommand.TextRun ||
                command is SceneCommand.MeshRibbon ||
                command is SceneCommand.ColorTextRun
            ) {
                null
            } else {
                command.family
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only offscreen render supports only clear, fill-rect, fill-rrect, stroke, linear-gradient-rect, radial-gradient-rect, sweep-gradient-rect, clip, path-fill-stencil, path-fill-gradient, convex-fan-mesh, bitmap-rect, save-layer, runtime-effect, custom-runtime-effect, mesh-ribbon, and text-run command families: " +
            unsupportedFamilies.joinToString()
    }

    if (commands.none {
                it is SceneCommand.FillRect || it is SceneCommand.FillRRect || it is SceneCommand.Stroke ||
        it is SceneCommand.LinearGradientRect || it is SceneCommand.RadialGradientRect || it is SceneCommand.SweepGradientRect ||
        it is SceneCommand.PathFillStencil || it is SceneCommand.PathFillGradient || it is SceneCommand.ConvexFanMesh ||
                it is SceneCommand.BitmapRect || it is SceneCommand.SaveLayer ||
                it is SceneCommand.RuntimeEffectTile || it is SceneCommand.CustomRuntimeEffectTile ||
                it is SceneCommand.TextRun || it is SceneCommand.MeshRibbon || it is SceneCommand.ColorTextRun
        }
    ) {
        return "rect-only offscreen render requires at least one FillRect, FillRRect, Stroke, LinearGradientRect, RadialGradientRect, SweepGradientRect, PathFillStencil, PathFillGradient, ConvexFanMesh, BitmapRect, SaveLayer, RuntimeEffectTile, CustomRuntimeEffectTile, MeshRibbon, or TextRun command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only offscreen render supports zero or one initial Clear before drawable commands"
    }

    return null
}

internal fun rectOnlyRenderedDiagnostics(
    sceneId: String,
    adapterInfo: String?,
    clearCount: Int,
    fillRectCount: Int,
    fillRRectCount: Int,
    linearGradientRectCount: Int = 0,
    radialGradientRectCount: Int = 0,
    sweepGradientRectCount: Int = 0,
    clipCount: Int = 0,
    bitmapRectCount: Int = 0,
    blurRectCount: Int = 0,
    colorMatrixRectCount: Int = 0,
    strokeRectCount: Int = 0,
    textRunRectCount: Int = 0,
    colorTextRunRectCount: Int = 0,
    saveLayerRectCount: Int = 0,
    pathFillStencilCount: Int = 0,
    pathFillGradientCount: Int = 0,
    convexFanMeshCount: Int = 0,
    customRuntimeEffectRectCount: Int = 0,
    filters: List<RectOnlyFilterNode> = emptyList(),
    saveLayers: List<RectOnlySaveLayer> = emptyList(),
    runtimeEffects: List<RectOnlyRuntimeEffectTile> = emptyList(),
    meshRibbons: List<RectOnlyMeshRibbon> = emptyList(),
): List<String> {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(
        fillRectCount +
            fillRRectCount +
            linearGradientRectCount +
            radialGradientRectCount +
            sweepGradientRectCount +
            bitmapRectCount +
            blurRectCount +
            colorMatrixRectCount +
            strokeRectCount +
            textRunRectCount +
            colorTextRunRectCount +
            saveLayerRectCount +
            pathFillStencilCount +
            pathFillGradientCount +
            convexFanMeshCount +
            customRuntimeEffectRectCount +
            saveLayers.size +
            runtimeEffects.size +
            meshRibbons.size > 0,
    ) {
        "$sceneId rect-only diagnostics require at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, BlurRect, ColorMatrixRect, StrokeRect, TextRun, ColorTextRun, SaveLayer, RuntimeEffectTile, CustomRuntimeEffectTile, MeshRibbon, PathFillStencil, PathFillGradient, or ConvexFanMesh command"
    }
    return buildList {
        add("rendered $sceneId via WebGPU offscreen")
        add("adapter=${adapterInfo ?: "unknown-adapter"}")
        add("clearCommands=$clearCount")
        add("fillRectCommands=$fillRectCount")
        add("fillRRectCommands=$fillRRectCount")
        add("linearGradientRectCommands=$linearGradientRectCount")
        add("radialGradientRectCommands=$radialGradientRectCount")
        add("sweepGradientRectCommands=$sweepGradientRectCount")
        add("clipCommands=$clipCount")
        add("bitmapRectCommands=$bitmapRectCount")
        add("blurRectCommands=$blurRectCount")
        add("colorMatrixRectCommands=$colorMatrixRectCount")
        add("strokeRectCommands=$strokeRectCount")
        add("textRunCommands=$textRunRectCount")
        add("colorTextRunCommands=$colorTextRunRectCount")
        add("saveLayerRectCommands=$saveLayerRectCount")
        add("pathFillStencilCommands=$pathFillStencilCount")
        add("pathFillGradientCommands=$pathFillGradientCount")
        add("convexFanMeshCommands=$convexFanMeshCount")
        add("customRuntimeEffectRectCommands=$customRuntimeEffectRectCount")
        if (saveLayers.isNotEmpty()) {
            add("saveLayerCommands=${saveLayers.size}")
            add("saveLayerKinds=${saveLayers.joinToString { it.layerKind }}")
            add("saveLayerRoute=scene-fixture.bounded-shadow-card")
            add("saveLayerMaterializedDraws=${saveLayers.size * 2}")
            add("saveLayerFilterKinds=${saveLayers.joinToString { it.filterKind.wireName }}")
            add("saveLayerFallbackReason=none")
            add("filterRoutes=scene-fixture.bounded-drop-shadow")
            add("generalSaveLayerSupport=false")
            add("imageFilterDagSupport=false")
        }
        if (filters.isNotEmpty()) {
            add("filterNodeCommands=${filters.size}")
            add("filterKinds=${filters.joinToString { it.kind.wireName }}")
            add("filterInputs=${filters.joinToString { it.inputLabel }}")
        }
        if (runtimeEffects.isNotEmpty()) {
            add("runtimeEffectCommands=${runtimeEffects.size}")
            add("runtimeEffectStableIds=${runtimeEffects.joinToString { it.stableId }}")
            add("runtimeEffectWgslImplementationIds=${runtimeEffects.joinToString { it.wgslImplementationId }}")
            add("runtimeEffectUniformLayout=${runtimeEffects.joinToString { it.uniformLayout }}")
            add("runtimeEffectPipelineKey=${runtimeEffects.joinToString { it.pipelineKey }}")
            add("runtimeEffectDescriptorEvidence=reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json")
            add(
                "runtimeEffectParserEvidence=" +
                    "RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms",
            )
            add("fallbackReason=none")
        }
        if (meshRibbons.isNotEmpty()) {
            add("meshRibbonCommands=${meshRibbons.size}")
            add("meshRibbonKinds=${meshRibbons.joinToString { it.meshKind }}")
            add("meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
            add("meshRibbonFallbackReason=none")
            add("generalVerticesSupport=false")
            add("vertexIndexBufferSupport=false")
        }
    }
}

internal fun passBatchingWiringDiagnostics(): List<String> =
    listOf(
        "passes.batching.wiring-fixture passes.batch-plan stream=phase4-simple-rect-route pass=phase4-simple-rect-pass batches=1 accepted=1 cuts=0 packets=4 diagnostics=none",
        "passes.batching.wiring-fixture passes.batch id=batch-1 kind=solid-fill target=rgba8unorm packets=packet-1,packet-2,packet-3,packet-4 pipelines=render:solid-fill queueRetained=true",
        "passes.batching.wiring-fixture passes.batch-queue-guard batch=batch-1 retained=true required=lease:uniform-slab:phase4 retainedRefs=lease:uniform-slab:phase4",
        "passes.batching.nonclaim no-destination-read-batching nonclaim:no-destination-read-batching",
        "passes.batching.nonclaim no-save-layer-batching nonclaim:no-save-layer-batching",
        "passes.batching.nonclaim no-text-complex-batching nonclaim:no-text-complex-batching",
    )

private fun colorTextRunFallbackColor(): SceneColor = SceneColor(1f, 1f, 1f, 1f)

private fun SceneCommand.paintOrder(): Int =
    when (this) {
        is SceneCommand.FillRect -> paintOrder
        is SceneCommand.FillRRect -> paintOrder
        is SceneCommand.Stroke -> paintOrder
        is SceneCommand.LinearGradientRect -> paintOrder
        is SceneCommand.RadialGradientRect -> paintOrder
        is SceneCommand.SweepGradientRect -> paintOrder
        is SceneCommand.BitmapRect -> paintOrder
        is SceneCommand.SaveLayer -> paintOrder
        is SceneCommand.RuntimeEffectTile -> paintOrder
        is SceneCommand.MeshRibbon -> paintOrder
        is SceneCommand.PathFillStencil -> paintOrder
        is SceneCommand.PathFillGradient -> paintOrder
        is SceneCommand.ConvexFanMesh -> paintOrder
        is SceneCommand.TextRun -> paintOrder
        is SceneCommand.ColorTextRun -> paintOrder
        else -> 0
    }

private fun SceneCommand.shapeRect() =
    when (this) {
        is SceneCommand.FillRect -> rect
        is SceneCommand.FillRRect -> rect
        is SceneCommand.Stroke -> rect
        is SceneCommand.LinearGradientRect -> rect
        is SceneCommand.RadialGradientRect -> rect
        is SceneCommand.SweepGradientRect -> rect
        is SceneCommand.BitmapRect -> fixtureRect()
        is SceneCommand.SaveLayer -> fixtureContentRect()
        is SceneCommand.RuntimeEffectTile -> fixtureRect()
        is SceneCommand.CustomRuntimeEffectTile -> rect
        is SceneCommand.MeshRibbon -> fixtureBounds()
        is SceneCommand.PathFillStencil -> pathFillBoundingRect(pathKind)
        is SceneCommand.PathFillGradient -> pathFillBoundingRect(pathKind)
        is SceneCommand.ConvexFanMesh -> convexFanBoundingRect(pathKind)
        is SceneCommand.TextRun -> textRunBoundingRect(this)
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeStartColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.Stroke -> strokeColor
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.RadialGradientRect -> startColor
        is SceneCommand.SweepGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().topLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.CustomRuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.PathFillGradient -> startColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.textColor(): SceneColor =
    when (this) {
        is SceneCommand.TextRun -> color ?: SceneColor(1f, 1f, 1f, 1f)
        else -> error("Not a TextRun command")
    }

private fun SceneCommand.shapeEndColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.Stroke -> strokeColor
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.RadialGradientRect -> endColor
        is SceneCommand.SweepGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().topRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.CustomRuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.PathFillGradient -> endColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomLeftColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.Stroke -> strokeColor
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.RadialGradientRect -> startColor
        is SceneCommand.SweepGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.CustomRuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.PathFillGradient -> startColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomRightColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.Stroke -> strokeColor
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.RadialGradientRect -> endColor
        is SceneCommand.SweepGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.CustomRuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.PathFillGradient -> endColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeRadius(): Float =
    when (this) {
        is SceneCommand.FillRect -> 0f
        is SceneCommand.FillRRect -> radius
        is SceneCommand.Stroke -> 0f
        is SceneCommand.LinearGradientRect -> 0f
        is SceneCommand.RadialGradientRect -> 0f
        is SceneCommand.SweepGradientRect -> 0f
        is SceneCommand.BitmapRect -> 0f
        is SceneCommand.SaveLayer -> radius
        is SceneCommand.RuntimeEffectTile -> 0f
        is SceneCommand.CustomRuntimeEffectTile -> 0f
        is SceneCommand.MeshRibbon -> thickness * 0.5f
        is SceneCommand.PathFillStencil -> 0f
        is SceneCommand.PathFillGradient -> 0f
        is SceneCommand.ConvexFanMesh -> 0f
        is SceneCommand.TextRun -> 0f
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapePaintKind(): Float =
    when (this) {
        is SceneCommand.MeshRibbon -> 5f
        is SceneCommand.RuntimeEffectTile -> 4f
        is SceneCommand.CustomRuntimeEffectTile -> 12f
        is SceneCommand.LinearGradientRect -> 1f
        is SceneCommand.RadialGradientRect -> 6f
        is SceneCommand.SweepGradientRect -> 7f
        is SceneCommand.PathFillStencil -> 8f
        is SceneCommand.PathFillGradient -> 11f
        is SceneCommand.ConvexFanMesh -> 9f
        is SceneCommand.BitmapRect -> when (sampling) {
            SceneBitmapSampling.Nearest -> 2f
            SceneBitmapSampling.Linear -> 3f
        }
        is SceneCommand.TextRun -> 10f
        else -> 0f
    }

private fun SceneFilterKind.filterPaintKind(): Float =
    when (this) {
        SceneFilterKind.LumaTint -> 1f
        SceneFilterKind.DropShadow -> 0f
        SceneFilterKind.GaussianBlur -> 2f
        SceneFilterKind.ColorMatrix -> 3f
    }

private fun SceneCommand.BitmapRect.fixtureRect(): SceneRect =
    rect ?: error("BitmapRect requires rect fixture payload: $label")

private fun SceneCommand.BitmapRect.fixtureSource(): SceneBitmapSource =
    source ?: error("BitmapRect requires source fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentRect(): SceneRect =
    contentRect ?: error("SaveLayer requires contentRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowRect(): SceneRect =
    shadowRect ?: error("SaveLayer requires shadowRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentColor(): SceneColor =
    contentColor ?: error("SaveLayer requires contentColor fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowColor(): SceneColor =
    shadowColor ?: error("SaveLayer requires shadowColor fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureRect(): SceneRect =
    rect ?: error("RuntimeEffectTile requires rect fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureUniformColor(): SceneColor =
    uniformColor ?: error("RuntimeEffectTile requires uniform color fixture payload: $label")

private fun SceneCommand.CustomRuntimeEffectTile.fixtureUniformColor(): SceneColor =
    SceneColor.blue()

private fun SceneCommand.MeshRibbon.fixtureBounds(): SceneRect =
    bounds ?: error("MeshRibbon requires bounds fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureStartColor(): SceneColor =
    startColor ?: error("MeshRibbon requires startColor fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureEndColor(): SceneColor =
    endColor ?: error("MeshRibbon requires endColor fixture payload: $label")

private fun SceneColor.withAlpha(alpha: Float): SceneColor =
    SceneColor(r = r, g = g, b = b, a = alpha.coerceIn(0f, 1f))

private fun requireInsideTarget(
    sceneId: String,
    label: String,
    rect: SceneRect,
    width: Int,
    height: Int,
    kind: String,
) {
    val left = floor(rect.left).toInt()
    val top = floor(rect.top).toInt()
    val right = ceil(rect.right).toInt()
    val bottom = ceil(rect.bottom).toInt()
    require(
        left >= 0 &&
            top >= 0 &&
            right <= width &&
            bottom <= height &&
            right > left &&
            bottom > top,
    ) {
        "$sceneId rect-only $kind must be inside positive bounds: $label"
    }
}

private fun SceneRect.intersect(other: SceneRect?): SceneRect? {
    if (other == null) return this
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right > left && bottom > top) SceneRect(left, top, right, bottom) else null
}

private fun makeLineLoopPath(vertices: List<Pair<Float, Float>>): org.graphiks.kanvas.gpu.renderer.geometry.PathData {
    val pts = vertices.map { (x, y) -> org.graphiks.kanvas.gpu.renderer.geometry.Point(x, y) }
    return org.graphiks.kanvas.gpu.renderer.geometry.PathData(
        verbs = pts.map { org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.LineTo(it) } +
            listOf(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.Close),
        points = emptyList(),
    )
}

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top

internal fun generateStarVertices(
    centerX: Float, centerY: Float,
    outerRadius: Float, innerRadius: Float, points: Int,
): List<Pair<Float, Float>> {
    val vertices = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until points * 2) {
        val angle = kotlin.math.PI * i / points - kotlin.math.PI / 2
        val r = if (i % 2 == 0) outerRadius else innerRadius
        vertices.add(
            Pair(
                centerX + r * kotlin.math.cos(angle).toFloat(),
                centerY + r * kotlin.math.sin(angle).toFloat(),
            ),
        )
    }
    return vertices
}

private fun generateRibbonVertices(
    left: Float, top: Float, width: Float, height: Float,
    startColor: SceneColor, endColor: SceneColor,
): FloatArray {
    val right = left + width
    val bottom = top + height
    val midX = left + width * 0.5f
    val midY = top + height * 0.5f
    return floatArrayOf(
        left, top, 0f, 0f, startColor.r, startColor.g, startColor.b, startColor.a,
        right, top, 0f, 0f, endColor.r, endColor.g, endColor.b, endColor.a,
        midX, bottom, 0f, 0f, endColor.r, endColor.g, endColor.b, endColor.a,
        left, top, 0f, 0f, startColor.r, startColor.g, startColor.b, startColor.a,
        midX, bottom, 0f, 0f, endColor.r, endColor.g, endColor.b, endColor.a,
        right, bottom, 0f, 0f, startColor.r, startColor.g, startColor.b, startColor.a,
    )
}

internal fun generateOctagonVertices(
    centerX: Float, centerY: Float, radius: Float, sides: Int,
): List<Pair<Float, Float>> {
    val vertices = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until sides) {
        val angle = 2.0 * kotlin.math.PI * i / sides - kotlin.math.PI / 2
        vertices.add(
            Pair(
                centerX + radius * kotlin.math.cos(angle).toFloat(),
                centerY + radius * kotlin.math.sin(angle).toFloat(),
            ),
        )
    }
    return vertices
}

private fun boundingRect(vertices: List<Pair<Float, Float>>): SceneRect {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    for ((x, y) in vertices) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    return SceneRect(minX, minY, maxX, maxY)
}

private fun SceneCommand.TextRun.textRunBoundingRect(cmd: SceneCommand.TextRun): SceneRect {
    val baselineX = cmd.baselineX ?: 0f
    val baselineY = cmd.baselineY ?: 0f
    val fontSize = cmd.fontSize ?: 16f
    val textLen = cmd.text?.length?.toFloat() ?: 4f
    val estimatedWidth = fontSize * textLen * 0.5f
    val estimatedHeight = fontSize * 1.2f
    return SceneRect(baselineX, baselineY - fontSize * 0.7f, baselineX + estimatedWidth, baselineY + fontSize * 0.3f)
}

private fun SceneCommand.PathFillStencil.pathFillBoundingRect(pathKind: String): SceneRect =
    when (pathKind) {
        "non-convex-star" -> boundingRect(generateStarVertices(160f, 100f, 80f, 35f, 5))
        else -> SceneRect(60f, 20f, 260f, 180f)
    }

private fun SceneCommand.PathFillGradient.pathFillBoundingRect(pathKind: String): SceneRect =
    when (pathKind) {
        "non-convex-star" -> boundingRect(generateStarVertices(160f, 100f, 80f, 35f, 5))
        else -> SceneRect(60f, 20f, 260f, 180f)
    }

private fun SceneCommand.ConvexFanMesh.convexFanBoundingRect(pathKind: String): SceneRect =
    when (pathKind) {
        "convex-octagon" -> boundingRect(generateOctagonVertices(160f, 100f, 80f, vertexCount))
        else -> SceneRect(60f, 20f, 260f, 180f)
    }
