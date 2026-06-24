package org.skia.gpu.webgpu

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionPreflightRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUFirstRouteRenderSubmitRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawInvocation
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCacheKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCreationPlan
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheEventResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetryEvent
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteCommandFamily
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteCommandSubmissionOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteResourceMaterializationOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteRouteKind
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteTelemetryEvent
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteWGSLModuleValidationOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger
import org.graphiks.kanvas.gpu.renderer.validation.GPUPromotionGateCheck
import org.graphiks.kanvas.gpu.renderer.validation.GPUContractDump
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationArtifactBundleWriteResult
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationFixture
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationReport
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationStatus
import org.graphiks.kanvas.gpu.renderer.validation.artifactBundle
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLBindingLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserState
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformFieldLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformLayout
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SkRect
import org.graphiks.wgsl.ast.Attribute
import org.graphiks.wgsl.ast.IntLiteral
import org.graphiks.wgsl.ast.NamedType
import org.graphiks.wgsl.ast.ScalarKind
import org.graphiks.wgsl.ast.ScalarType
import org.graphiks.wgsl.ast.StructDecl
import org.graphiks.wgsl.ast.TypeDecl
import org.graphiks.wgsl.ast.VariableDecl
import org.graphiks.wgsl.ast.VectorType
import org.graphiks.wgsl.parser.parseWgslResult
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl
import java.io.File
import java.security.MessageDigest

private const val DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME = "diagnostic-webgpu-first-route-pm-evidence"

/**
 * Writes the diagnostic WebGPU executed first-route PM evidence bundle.
 *
 * This helper is intentionally owned by `:gpu-raster` because it uses a concrete WebGPU context.
 * It materializes evidence for PM review only: callers must opt in with an already-created
 * [WebGpuContext], and no product route, default renderer support, Kadre window, or CPU fallback is
 * activated. The resulting validation bundle can pass the first-route promotion gate because it is
 * backed by a real `queue.submit` and completed readback, but that pass remains diagnostic evidence.
 */
internal fun writeGpuRendererFirstRouteExecutedPMEvidenceBundle(
    context: WebGpuContext,
    outputDirectory: File,
    replaceExisting: Boolean = false,
): GPUValidationArtifactBundleWriteResult =
    GpuRendererFirstRouteExecutedPMEvidenceScenario(context)
        .write(outputDirectory = outputDirectory, replaceExisting = replaceExisting)

/**
 * Writes a diagnostic executed first-route report produced by [produceReport].
 *
 * This overload exists so setup failures can still materialize PM-readable failure artifacts before
 * the exporter exits non-zero. It writes whatever report was produced, then fails if the report or
 * promotion gate did not pass; if [produceReport] throws, a minimal `Failed` report is written first.
 */
internal fun writeGpuRendererFirstRouteExecutedPMEvidenceBundle(
    outputDirectory: File,
    replaceExisting: Boolean = false,
    produceReport: () -> GPUValidationReport,
): GPUValidationArtifactBundleWriteResult {
    val report = try {
        produceReport()
    } catch (cause: Throwable) {
        val failureReport = diagnosticFailureReport(cause)
        val failureGate = GPUPromotionGateCheck().evaluate(failureReport)
        val writeResult = failureReport
            .artifactBundle(promotionGateResult = failureGate)
            .writeTo(outputDirectory = outputDirectory, replaceExisting = replaceExisting)
        throw IllegalStateException(
            "Diagnostic executed first-route PM evidence export failed; " +
                "failure bundle written root=${writeResult.rootDirectory.path}",
            cause,
        )
    }

    return writeCheckedReport(report, outputDirectory, replaceExisting)
}

/**
 * Command-line entry point for the opt-in executed first-route PM evidence export.
 *
 * The command requires a local WebGPU adapter and fails if one is unavailable. It is deliberately
 * not a dependency of `pipelinePmBundle`, so headless PM packaging can remain adapter-independent
 * while developers with local WebGPU can materialize positive diagnostic evidence explicitly.
 */
fun main(args: Array<String>) {
    require(args.size <= 1) {
        "usage: GpuRendererFirstRouteExecutedPMEvidenceExportKt [outputDirectory]"
    }

    val outputDirectory = args.firstOrNull()
        ?.let(::File)
        ?: File("build/reports/gpu-renderer-r6-executed-first-route-pm-evidence")
    val context = WebGpuContext.createOrNull()
        ?: error("WebGPU adapter required for diagnostic executed first-route PM evidence export")

    context.use { ctx ->
        val result = writeGpuRendererFirstRouteExecutedPMEvidenceBundle(ctx, outputDirectory)
        println(
            "gpu-renderer R6 executed first-route PM evidence bundle written " +
                "root=${outputDirectory.path} artifacts=${result.writes.size} manifest=${result.relativePaths.first()}",
        )
    }
}

private fun writeCheckedReport(
    report: GPUValidationReport,
    outputDirectory: File,
    replaceExisting: Boolean,
): GPUValidationArtifactBundleWriteResult {
    val gate = GPUPromotionGateCheck().evaluate(report)
    val writeResult = report
        .artifactBundle(promotionGateResult = gate)
        .writeTo(outputDirectory = outputDirectory, replaceExisting = replaceExisting)

    check(report.status == GPUValidationStatus.Passed && gate.passed) {
        "Diagnostic executed first-route PM evidence did not pass after export: " +
            "root=${writeResult.rootDirectory.path} " +
            "status=${report.status} gatePassed=${gate.passed} " +
            "missing=${gate.missingEvidence.joinToString(",").ifBlank { "none" }} " +
            "diagnostics=${gate.diagnostics.joinToString("; ").ifBlank { "none" }}"
    }
    return writeResult
}

private fun diagnosticFailureReport(cause: Throwable): GPUValidationReport {
    val failure = cause.asPortableFailure()
    return GPUValidationReport(
        name = DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME,
        status = GPUValidationStatus.Failed,
        dumps = listOf(
            GPUContractDump(
                name = "$DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME-09-submission",
                artifactName = "$DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME-09-submission.txt",
                entries = listOf(
                    GPUContractDump.Entry(
                        ownerPackage = "execution",
                        concept = "GPUCommandSubmission.Failed",
                        detail = "$DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME " +
                            "execution.submission:failed stage=report-production failure=$failure",
                    ),
                ),
            ),
        ),
        diagnostics = listOf(
            "diagnostic executed first-route PM evidence export failed before positive report: $failure",
        ),
    )
}

internal fun diagnosticGeneratedSolidRectWgslReflection(): WGSLReflectionResult.Accepted {
    val source = GeneratedSolidRectWgsl.generateDeterministic()
    val parsed = parseWgslResult(source)
    require(parsed.isSuccess) {
        "Generated solid rect WGSL failed validation: " +
            parsed.errors.joinToString("; ") { error -> "${error.message} span=${error.span}" }
    }
    val declarations = parsed.translationUnit.declarations
    val uniformVariable = declarations
        .filterIsInstance<VariableDecl>()
        .singleOrNull { variable -> variable.name == "uniforms" }
        ?: error("Generated solid rect WGSL parser reflection missing uniforms variable")
    val uniformType = uniformVariable.type as? NamedType
        ?: error("Generated solid rect uniforms variable must reference a named struct")
    val uniformStruct = declarations
        .filterIsInstance<StructDecl>()
        .singleOrNull { struct -> struct.name == uniformType.name }
        ?: error("Generated solid rect WGSL parser reflection missing struct ${uniformType.name}")
    val uniformLayout = uniformStruct.toUniformLayout()

    return WGSLReflectionResult.Accepted(
        moduleHash = WGSLModuleHash("wgsl-module:${source.sha256Hex()}"),
        bindings = listOf(
            WGSLBindingLayout(
                group = uniformVariable.requiredAttributeInt("group"),
                binding = uniformVariable.requiredAttributeInt("binding"),
                visibility = setOf("fragment"),
                resourceKind = "${uniformVariable.storageClass}-buffer",
                access = "read",
                minBindingSize = uniformLayout.sizeBytes,
                dynamicOffset = false,
                layoutRole = uniformVariable.name,
                diagnosticLabel = "parser-reflected:${uniformVariable.name}",
            ),
        ),
        uniforms = listOf(uniformLayout),
        storage = emptyList(),
        parserState = WGSLParserState(
            status = "parser-backed",
            toolName = "wgsl4k",
            message = "parseWgslResult produced AST used for diagnostic solid FillRect reflection",
        ),
        reflectionSource = "wgsl4k-parser-ast:generated-solid-rect",
    )
}

private fun VariableDecl.requiredAttributeInt(name: String): Int =
    attributes
        .firstOrNull { attribute -> attribute.name == name }
        ?.singleIntArgument()
        ?: error("Generated solid rect WGSL parser reflection missing @$name attribute on $this")

private fun Attribute.singleIntArgument(): Int? =
    (args.singleOrNull() as? IntLiteral)
        ?.value
        ?.toInt()

private fun StructDecl.toUniformLayout(): WGSLUniformLayout {
    var offset = 0L
    var structAlignment = 1
    val fields = members.map { member ->
        val layout = member.type.layout()
        offset = offset.alignTo(layout.alignment.toLong())
        val field = WGSLUniformFieldLayout(
            name = member.name,
            type = member.type.wgslTypeName(),
            offset = offset,
            sizeBytes = layout.sizeBytes,
            alignment = layout.alignment,
        )
        offset += layout.sizeBytes
        structAlignment = maxOf(structAlignment, layout.alignment)
        field
    }
    val sizeBytes = offset.alignTo(structAlignment.toLong())
    return WGSLUniformLayout(
        layoutHash = "layout:${name}:${fields.joinToString(",") { field -> "${field.name}@${field.offset}" }.sha256Hex()}",
        fields = fields.map { field -> field.name },
        fieldLayouts = fields,
        sizeBytes = sizeBytes,
        alignment = structAlignment,
        numericRepresentation = fields.joinToString(",") { field -> field.type }.ifBlank { "none" },
    )
}

private data class ParsedTypeLayout(
    val sizeBytes: Long,
    val alignment: Int,
)

private fun TypeDecl.layout(): ParsedTypeLayout =
    when (this) {
        is ScalarType ->
            when (kind) {
                ScalarKind.F32, ScalarKind.I32, ScalarKind.U32 -> ParsedTypeLayout(sizeBytes = 4L, alignment = 4)
                else -> error("Unsupported generated solid rect scalar WGSL type: $kind")
            }
        is VectorType -> {
            val elementLayout = elementType.layout()
            val sizeBytes = elementLayout.sizeBytes * size
            ParsedTypeLayout(
                sizeBytes = sizeBytes,
                alignment = when (size) {
                    2 -> elementLayout.alignment * 2
                    3, 4 -> elementLayout.alignment * 4
                    else -> error("Unsupported generated solid rect vector size: $size")
                },
            )
        }
        else -> error("Unsupported generated solid rect WGSL type: $this")
    }

private fun TypeDecl.wgslTypeName(): String =
    when (this) {
        is ScalarType ->
            when (kind) {
                ScalarKind.F32 -> "f32"
                ScalarKind.I32 -> "i32"
                ScalarKind.U32 -> "u32"
                else -> error("Unsupported generated solid rect scalar WGSL type: $kind")
            }
        is VectorType -> "vec$size<${elementType.wgslTypeName()}>"
        is NamedType -> name
        else -> error("Unsupported generated solid rect WGSL type: $this")
    }

private fun Long.alignTo(alignment: Long): Long =
    if (alignment <= 1L) {
        this
    } else {
        ((this + alignment - 1L) / alignment) * alignment
    }

/**
 * Builds the one solid FillRect diagnostic evidence scenario used by the exporter.
 *
 * The scenario keeps Graphite's useful separation between recording, materialization, execution,
 * and readback as Kanvas-owned contracts. It does not copy Graphite task classes or resource
 * ownership boundaries; every artifact is projected through `:gpu-renderer` validation types.
 */
private class GpuRendererFirstRouteExecutedPMEvidenceScenario(
    private val context: WebGpuContext,
) {
    fun write(
        outputDirectory: File,
        replaceExisting: Boolean,
    ): GPUValidationArtifactBundleWriteResult =
        writeGpuRendererFirstRouteExecutedPMEvidenceBundle(
            outputDirectory = outputDirectory,
            replaceExisting = replaceExisting,
        ) {
            report()
        }

    private fun report(): GPUValidationReport =
        GpuRendererFirstRouteWebGpuSubmitter(context).use { submitter ->
            val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
            val command = shadowFillRectCommand()
            val materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}"))
            val request = firstRouteRequest(
                target = target,
                command = command,
                materialization = materialization,
            )
            val evidence = submitter.submitAndReadback(command, request)
            val recording = GPURecorder(
                recordingId = GPURecordingID("recording.webgpu-submit"),
                capabilities = firstSliceCapabilities(),
            ).also { recorder -> recorder.record(command) }.close()
            val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
                name = DIAGNOSTIC_EXECUTED_PM_EVIDENCE_NAME,
                recording = recording,
                wgslReflection = diagnosticGeneratedSolidRectWgslReflection(),
                materialization = materialization,
                submission = evidence.submission,
                readbacks = evidence.readbacks,
                telemetryLedger = executedTelemetryLedger(),
            )

            report
        }

    private fun shadowFillRectCommand(): NormalizedDrawCommand.FillRect {
        val result = GpuRendererShadowAdapter(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
        ).shadowFillRect(
            GpuRendererShadowFillRectState(
                commandId = 42,
                rect = SkRect.MakeLTRB(1f, 1f, 8f, 8f),
                paint = SkPaint(SK_ColorMAGENTA),
                targetWidth = 16,
                targetHeight = 16,
                targetColorFormat = "rgba8unorm",
                clip = GpuRendererShadowClip.WideOpen,
                paintOrder = 0,
            ),
        )

        require(result.status == GpuRendererShadowHandoffStatus.Native) {
            "Diagnostic first-route shadow command must stay native: ${result.status}"
        }
        return result.normalizedCommand as? NormalizedDrawCommand.FillRect
            ?: error("Diagnostic first-route shadow command did not produce FillRect")
    }

    private fun firstSliceCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "diagnostic-gpu",
                implementationName = "webgpu-submit-export",
                adapterName = "webgpu-adapter",
                deviceName = "webgpu-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "webgpu-submit-export",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-webgpu-submit-export",
                ),
            ),
            snapshotId = "first-route-webgpu-submit-export",
        )

    private fun executedTelemetryLedger(): GPUTelemetryLedger =
        GPUTelemetryLedger.empty()
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandFamily(
                    family = GPUFirstRouteCommandFamily.Rect,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.Route(
                    kind = GPUFirstRouteRouteKind.GPUNative,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.WGSLModuleValidation(
                    outcome = GPUFirstRouteWGSLModuleValidationOutcome.Success,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.ResourceMaterialization(
                    outcome = GPUFirstRouteResourceMaterializationOutcome.Materialized,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandSubmission(
                    outcome = GPUFirstRouteCommandSubmissionOutcome.Submitted,
                ),
            )
            .recordFirstRouteEvent(GPUFirstRouteTelemetryEvent.NegativeCPUFallbackRefusal)
            .recordCacheEvent(
                GPUCacheTelemetryEvent.pipeline(
                    result = GPUCacheEventResult.Miss,
                    keyHash = "render-pipeline:first-fill-rect",
                    subjectHash = "webgpu:first-route-submit",
                ),
            )

    private fun firstRouteRequest(
        target: GpuRendererFirstRouteWebGpuTargetBinding,
        command: NormalizedDrawCommand.FillRect,
        materialization: GPUResourceMaterializationDecision.Materialized,
        pass: GPUDrawPass = drawPass(command),
        pipelinePlan: GPUPipelineCreationPlan = renderPipelinePlan(),
        readbackRequests: List<GPUReadbackRequest> = listOf(readbackRequest()),
    ): GPUFirstRouteRenderSubmitRequest =
        GPUFirstRouteRenderSubmitRequest(
            preflightRequest = GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("surface-write")),
                target = target.surfaceTarget,
                requiredTargetUsageLabels = setOf("render_attachment", "copy_src"),
                materializationDecision = materialization,
                taskIds = materialization.taskIds,
                passIds = listOf(pass.passId),
            ),
            pass = pass,
            materialization = materialization,
            pipelinePlan = pipelinePlan,
            payloadRefs = listOf(solidPayloadRef(command)),
            readbackRequests = readbackRequests,
        )

    private fun drawPass(
        command: NormalizedDrawCommand.FillRect,
        pipelineKeyHash: String = "render-pipeline:first-fill-rect",
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass.root.${command.commandId.value}",
            targetStateHash = "target-state:surface",
            layerScopeId = "root",
            loadStoreLabel = "clear.store",
            invocations = listOf(
                GPUDrawInvocation(
                    commandIdValue = command.commandId.value,
                    analysisRecordId = "analysis-fill",
                    renderStepIndex = 0,
                    renderStepId = GPURenderStepID("fill-rect"),
                    role = "fill",
                    layerScopeId = "root",
                    sortKey = command.ordering.paintOrder.toLong(),
                    pipelineKeyHash = pipelineKeyHash,
                    boundsHash = "bounds:first-fill-rect",
                    scissorBoundsHash = "clip:wide-open",
                    originalPaintOrder = command.ordering.paintOrder,
                ),
            ),
            pipelineKeys = listOf(pipelineKeyHash),
            barriers = emptyList(),
        )

    private fun solidPayloadRef(command: NormalizedDrawCommand.FillRect): GPUDrawPayloadRef {
        val material = command.material as? GPUMaterialDescriptor.SolidColor
            ?: error("Diagnostic first-route command must use solid color material")
        return GPUSolidPayloadGatherer().gather(
            GPUPayloadGatherPlan(
                planHash = "solid-gather-v1",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid-material-assembly-v1",
                renderStepIdentity = "fill-rect",
                writePlanHash = "solid-write-v1",
                bindingPlanHash = "no-resources",
                uploadPlanHash = "no-upload",
                dedupScope = "pass-root",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid-material-key",
                payloadClass = "solid-rgba-rect",
                valueFacts = mapOf(
                    "command.id" to command.commandId.value.toString(),
                    "rect.left" to command.rect.left.toString(),
                    "rect.top" to command.rect.top.toString(),
                    "rect.right" to command.rect.right.toString(),
                    "rect.bottom" to command.rect.bottom.toString(),
                    "radii.topLeft" to "0.0",
                    "radii.topRight" to "0.0",
                    "radii.bottomRight" to "0.0",
                    "radii.bottomLeft" to "0.0",
                    "color.r" to material.r.toString(),
                    "color.g" to material.g.toString(),
                    "color.b" to material.b.toString(),
                    "color.a" to material.a.toString(),
                ),
                resourceFacts = emptyMap(),
                diagnosticLabel = "diagnostic:first-route-fill",
            ),
        )
    }

    private fun renderPipelinePlan(): GPUPipelineCreationPlan =
        GPUPipelineCreationPlan(
            cacheKey = GPUPipelineCacheKey("render-pipeline:first-fill-rect"),
            preimage = GPUPipelineKeyPreimage.Render(
                renderStepIdentity = "fill-rect",
                renderStepVersion = "1",
                primitiveTopology = "triangle-list",
                materialKeyHash = "material:solid",
                materialProgramId = "solid-fill",
                materialDictionaryVersion = "1",
                materialLayoutHash = "layout:solid",
                snippetIdentityHash = "snippet:solid",
                moduleHash = "module:generated-solid-fill",
                vertexLayoutHash = "vertex:fullscreen-triangle",
                targetFormatClass = "rgba8unorm",
                blendStateHash = "blend:src-over",
                sampleStateHash = "sample:1",
                bindGroupLayoutHash = "bind-group:solid-color",
                capabilityClass = "first_slice.fill_rect.native",
                capabilityFacts = listOf("first_slice.fill_rect.native=supported"),
                rendererSalt = "kanvas-diagnostic",
            ),
            moduleHash = "module:generated-solid-fill",
            bindingLayoutHash = "bind-group:solid-color",
            requiredCapabilities = listOf("first_slice.fill_rect.native=supported"),
            creationStage = "render-pipeline",
        )

    private fun readbackRequest(): GPUReadbackRequest =
        GPUReadbackRequest(
            requestId = "readback-1",
            sourceLabel = "first-route-webgpu-submit",
            boundsLabel = "0,0 16x16",
            format = "rgba8unorm",
            synchronizationLabel = "after-submit",
            expectedArtifactLabel = "first-route-fill.png",
        )
}

private fun Throwable.asPortableFailure(): String {
    val type = this::class.simpleName ?: "Throwable"
    val message = message
        ?.replace(Regex("\\s+"), " ")
        ?.take(240)
        ?.takeIf { it.isNotBlank() }
    return if (message == null) type else "$type: $message"
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
