package org.skia.gpu.webgpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandSubmission
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionPreflightRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUFirstRouteRenderSubmitRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
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
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationFixture
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationReport
import org.graphiks.kanvas.gpu.renderer.validation.GPUValidationStatus
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import java.io.File
import java.nio.file.Files

/** Verifies the opt-in WebGPU bridge that turns first-route Kanvas evidence into a real submit. */
class GpuRendererFirstRouteWebGpuSubmitterTest {
    private val requireWebGpuEvidenceProperty = "kanvas.requireWebGpuFirstRouteEvidence"
    private val requireWebGpuEvidenceEnv = "KANVAS_REQUIRE_WEBGPU_FIRST_ROUTE_EVIDENCE"

    @Test
    fun `submits first route solid fill rect to WebGPU and completes readback`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val request = firstRouteRequest(
                    target = target,
                    command = command,
                    materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}")),
                )

                val evidence = submitter.submitAndReadback(command, request)

                val submitted = assertInstanceOf(
                    GPUCommandSubmission.Submitted::class.java,
                    evidence.submission,
                )
                val readback = assertInstanceOf(
                    GPUReadbackResult.Completed::class.java,
                    evidence.readbacks.single(),
                )
                val pixels = requireNotNull(evidence.readbackBytesByRequestId["readback-1"])
                val dump = (submitted.dumpLines() + readback.dumpLines()).joinToString("\n")

                assertTrue(dump.contains("execution.submission:submitted"))
                assertTrue(dump.contains("routes=GPUNative=1"))
                assertTrue(dump.contains("readbacks=readback-1"))
                assertEquals((16 * 16 * 4).toLong(), readback.byteCount)
                assertTrue(readback.payloadHash.startsWith("sha256:"))
                assertTrue(pixelAlpha(pixels, x = 2, y = 2, width = 16) > 0)
                assertTrue(pixelComponent(pixels, x = 2, y = 2, width = 16, channel = 0) > 200)
                assertTrue(pixelComponent(pixels, x = 2, y = 2, width = 16, channel = 1) < 20)
                assertTrue(pixelComponent(pixels, x = 2, y = 2, width = 16, channel = 2) > 200)
                assertEquals(0, pixelAlpha(pixels, x = 12, y = 12, width = 16))
            }
        }
    }

    @Test
    fun `real WebGPU submitter evidence satisfies executed PM bundle without product route activation`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
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
                    name = "diagnostic-webgpu-first-route-pm-evidence",
                    recording = recording,
                    wgslReflection = diagnosticGeneratedSolidRectWgslReflection(),
                    materialization = materialization,
                    submission = evidence.submission,
                    readbacks = evidence.readbacks,
                    telemetryLedger = executedTelemetryLedger(),
                )
                val gate = GPUPromotionGateCheck().evaluate(report)
                val lines = report.dumps.flatMap { dump -> dump.lines() }

                assertEquals(GPUValidationStatus.Passed, report.status)
                assertTrue(gate.passed, "Executed PM evidence should pass: missing=${gate.missingEvidence}")
                assertTrue(lines.any { line -> "resources:GPUResourceMaterializationDecision.Materialized" in line })
                assertTrue(lines.any { line -> "execution:GPUCommandSubmission.Submitted" in line })
                assertTrue(lines.any { line -> "execution:GPUReadbackResult.Completed" in line && "sha256:" in line })
                assertTrue(lines.any { line -> "telemetry:GPUCacheTelemetry.pipeline" in line })
                assertTrue(lines.any { line -> "routing:NegativeCPUFallbackRefusal" in line })
                assertFalse(lines.any { line -> "GPUCommandSubmission.Refused" in line })
                assertFalse(lines.any { line -> "GPUResourceMaterializationDecision.Refused" in line })
                assertFalse(lines.any { line -> "readbackBytes" in line || "pixel" in line })
            }
        }
    }

    @Test
    fun `exports diagnostic executed PM evidence bundle artifacts`() {
        val context = webGpuContextOrAssume()
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm").toFile()

        try {
            context.use { ctx ->
                val result = writeGpuRendererFirstRouteExecutedPMEvidenceBundle(ctx, outputRoot)

                assertEquals(outputRoot, result.rootDirectory)
                assertEquals(19, result.relativePaths.size)
                assertEquals("diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt", result.relativePaths.first())
                assertEquals(
                    listOf(
                        "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-01-command.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-03-route.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-04-material.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt",
                        "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt",
                    ),
                    result.relativePaths,
                )

                val manifest = outputRoot
                    .resolve("diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt")
                    .readLines()
                assertTrue(manifest.contains("validation.report.status=Passed"))
                assertTrue(manifest.contains("validation.gate.passed=true"))
                assertTrue(manifest.contains("validation.gate.missingEvidence=none"))
                assertTrue(manifest.contains("validation.bundle.scope=pm-evidence-only"))
                assertFalse(
                    manifest.any { line ->
                        line.contains("productRouteActivated=true") ||
                            line.contains("releaseBlocking=true") ||
                            line.contains("supported=true")
                    },
                )

                val readbackLines = outputRoot
                    .resolve("diagnostic-webgpu-first-route-pm-evidence-10-readback.txt")
                    .readLines()
                assertTrue(readbackLines.any { line -> "execution:GPUReadbackResult.Completed" in line && "sha256:" in line })
                assertFalse(readbackLines.any { line -> "readbackBytes" in line || "pixel" in line })
                assertTrue(outputRoot.resolve("diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt").isFile)
                assertTrue(outputRoot.resolve("diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt").isFile)
            }
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `diagnostic PM bundle text guard rejects contaminated text artifact`() {
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-contaminated").toFile()

        try {
            outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-10-readback.txt")
                .writeText("execution:GPUReadbackResult.Completed payloadHash=sha256:clean\nreadbackBytes=[0,1,2,3]\n")

            val failure = assertThrows(AssertionError::class.java) {
                assertDiagnosticExecutedPmBundleContainsOnlyHashesAndNonPromotionalClaims(outputRoot)
            }
            assertTrue(failure.message.orEmpty().contains("readbackBytes"), failure.message)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `diagnostic PM bundle text guard rejects spaced promotional claim text artifact`() {
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-promotional").toFile()

        try {
            outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-01-command.txt")
                .writeText("command: fillRect\nproductRouteActivated = true\n")

            val failure = assertThrows(AssertionError::class.java) {
                assertDiagnosticExecutedPmBundleContainsOnlyHashesAndNonPromotionalClaims(outputRoot)
            }
            assertTrue(failure.message.orEmpty().contains("productRouteActivated"), failure.message)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `diagnostic PM bundle text guard rejects native Kadre CI claim text artifact`() {
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-native-kadre").toFile()

        try {
            outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-01-command.txt")
                .writeText("command: fillRect\nnativeKadreCiRequired=true\n")

            val failure = assertThrows(AssertionError::class.java) {
                assertDiagnosticExecutedPmBundleContainsOnlyHashesAndNonPromotionalClaims(outputRoot)
            }
            assertTrue(failure.message.orEmpty().contains("nativeKadreCiRequired"), failure.message)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `exported diagnostic bundle contains only hashes and non promotional claims`() {
        val context = webGpuContextOrAssume()
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-guard").toFile()

        try {
            context.use { ctx ->
                writeGpuRendererFirstRouteExecutedPMEvidenceBundle(ctx, outputRoot)

                assertDiagnosticExecutedPmBundleContainsOnlyHashesAndNonPromotionalClaims(outputRoot)
            }
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `exports diagnostic PM failure bundle when report production fails`() {
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-failure").toFile()

        try {
            val failure = kotlin.runCatching {
                writeGpuRendererFirstRouteExecutedPMEvidenceBundle(outputRoot) {
                    error("synthetic backend setup failure")
                }
            }.exceptionOrNull() ?: error("expected exporter failure")
            assertTrue(failure.message.orEmpty().contains("Diagnostic executed first-route PM evidence export failed"))

            val manifest = outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt")
                .readLines()
            assertTrue(manifest.contains("validation.report.status=Failed"))
            assertTrue(manifest.contains("validation.gate.passed=false"))
            assertTrue(manifest.contains("validation.bundle.scope=pm-evidence-only"))

            val setupFailure = outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-09-submission.txt")
                .readLines()
            assertTrue(
                setupFailure.any { line ->
                    "GPUCommandSubmission.Failed" in line &&
                        "synthetic backend setup failure" in line
                },
            )
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `exports diagnostic PM failure bundle when produced report does not pass`() {
        val outputRoot = Files.createTempDirectory("gpu-renderer-executed-pm-non-passing").toFile()

        try {
            val failure = kotlin.runCatching {
                writeGpuRendererFirstRouteExecutedPMEvidenceBundle(outputRoot) {
                    GPUValidationReport(
                        name = "diagnostic-webgpu-first-route-pm-evidence",
                        status = GPUValidationStatus.Failed,
                        dumps = listOf(
                            GPUContractDump(
                                name = "diagnostic-webgpu-first-route-pm-evidence-09-submission",
                                artifactName = "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt",
                                entries = listOf(
                                    GPUContractDump.Entry(
                                        ownerPackage = "execution",
                                        concept = "GPUCommandSubmission.Refused",
                                        detail = "synthetic adapter-backed non-passing report",
                                    ),
                                ),
                            ),
                        ),
                        diagnostics = listOf("synthetic adapter-backed report did not pass"),
                    )
                }
            }.exceptionOrNull() ?: error("expected exporter failure")

            val manifest = outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt")
                .readLines()
            assertTrue(manifest.contains("validation.report.status=Failed"))
            assertTrue(manifest.contains("validation.gate.passed=false"))
            assertTrue(manifest.any { line -> line.startsWith("validation.gate.missingEvidence=") })
            assertTrue(manifest.any { line -> line.contains("synthetic adapter-backed report did not pass") })

            val submission = outputRoot
                .resolve("diagnostic-webgpu-first-route-pm-evidence-09-submission.txt")
            assertTrue(submission.isFile)
            assertTrue(
                submission.readLines().any { line ->
                    "GPUCommandSubmission.Refused" in line &&
                        "synthetic adapter-backed non-passing report" in line
                },
            )

            val message = failure.message.orEmpty()
            assertTrue(message.contains("root="), message)
            assertTrue(message.contains("status=Failed"), message)
            assertTrue(message.contains("gatePassed=false"), message)
            assertTrue(message.contains("missing="), message)
            assertTrue(message.contains("diagnostics="), message)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun `refuses unbound materialized resource instead of reporting submitted`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}")).copy(
                    resources = listOf(GPUTextureResourceRef("unbound-resource")),
                )
                val request = firstRouteRequest(
                    target = target,
                    command = command,
                    materialization = materialization,
                )

                val evidence = submitter.submitAndReadback(command, request)

                val refused = assertInstanceOf(
                    GPUCommandSubmission.Refused::class.java,
                    evidence.submission,
                )
                assertEquals("unsupported.execution.first_route_resource_unbound", refused.diagnostic.code)
                assertFalse(refused.dumpLines().joinToString("\n").contains("submission:submitted"))
                assertTrue(evidence.readbacks.none { result -> result is GPUReadbackResult.Completed })
            }
        }
    }

    @Test
    fun `refuses unsupported readback requests before backend submission`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}"))

                val partialBounds = submitter.submitAndReadback(
                    command,
                    firstRouteRequest(
                        target = target,
                        command = command,
                        materialization = materialization,
                        readbackRequests = listOf(
                            readbackRequest(
                                requestId = "readback-partial",
                                boundsLabel = "1,1 2x2",
                            ),
                        ),
                    ),
                )
                val failureReason = submitter.submitAndReadback(
                    command,
                    firstRouteRequest(
                        target = target,
                        command = command,
                        materialization = materialization,
                        readbackRequests = listOf(
                            readbackRequest(
                                requestId = "readback-failure-reason",
                                failureReason = "late-hash-only",
                            ),
                        ),
                    ),
                )

                assertRefused(partialBounds, "unsupported.execution.first_route_readback_bounds")
                assertRefused(failureReason, "unsupported.execution.first_route_readback_failure_reason")
            }
        }
    }

    @Test
    fun `refuses stale target descriptor before backend submission`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val staleSurfaceTarget = target.surfaceTarget.copy(
                    descriptor = target.surfaceTarget.descriptor.copy(targetGeneration = 99),
                )

                val evidence = submitter.submitAndReadback(
                    command,
                    firstRouteRequest(
                        target = target,
                        command = command,
                        materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}")),
                        surfaceTarget = staleSurfaceTarget,
                    ),
                )

                assertRefused(evidence, "unsupported.execution.first_route_target_descriptor_mismatch")
            }
        }
    }

    @Test
    fun `refuses pass pipeline key mismatch before backend submission`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val mismatchedPass = drawPass(command, pipelineKeyHash = "pipeline-key:mismatched")

                val evidence = submitter.submitAndReadback(
                    command,
                    firstRouteRequest(
                        target = target,
                        command = command,
                        materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}")),
                        pass = mismatchedPass,
                    ),
                )

                assertRefused(evidence, "unsupported.execution.first_route_pipeline_key_mismatch")
            }
        }
    }

    @Test
    fun `refuses terminal materialization diagnostics before backend submission`() {
        val context = webGpuContextOrAssume()

        context.use { ctx ->
            GpuRendererFirstRouteWebGpuSubmitter(ctx).use { submitter ->
                val target = submitter.createHeadlessTarget(targetId = "surface", width = 16, height = 16)
                val command = shadowFillRectCommand()
                val diagnostic = GPUResourceDiagnostic.uploadBudgetExceeded(
                    resourceLabel = "webgpu.headless-target.surface",
                    requestedBytes = 4096,
                    budgetBytes = 1024,
                )
                val materialization = target.materialization(taskIds = listOf("task.render.${command.commandId.value}")).copy(
                    diagnostics = listOf(diagnostic),
                )

                val evidence = submitter.submitAndReadback(
                    command,
                    firstRouteRequest(
                        target = target,
                        command = command,
                        materialization = materialization,
                    ),
                )

                assertRefused(evidence, "budget.resource.upload_exceeded")
            }
        }
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

        assertEquals(GpuRendererShadowHandoffStatus.Native, result.status)
        return assertInstanceOf(NormalizedDrawCommand.FillRect::class.java, result.normalizedCommand)
    }

    private fun webGpuContextOrAssume(): WebGpuContext {
        val context = WebGpuContext.createOrNull()
        if (context != null) return context

        if (requireWebGpuFirstRouteEvidence()) {
            throw AssertionError(
                "WebGPU adapter required because $requireWebGpuEvidenceProperty or " +
                    "$requireWebGpuEvidenceEnv is enabled",
            )
        }
        Assumptions.assumeTrue(false, "No WebGPU adapter")
        error("unreachable after JUnit assumption abort")
    }

    private fun requireWebGpuFirstRouteEvidence(): Boolean =
        System.getProperty(requireWebGpuEvidenceProperty).toBoolean() ||
            System.getenv(requireWebGpuEvidenceEnv)?.toBooleanStrictOrNull() == true

    private fun firstSliceCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "webgpu-submit-test",
                adapterName = "webgpu-adapter",
                deviceName = "webgpu-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "webgpu-submit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-webgpu-submit",
                ),
            ),
            snapshotId = "first-route-webgpu-submit-test",
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
        surfaceTarget: org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget = target.surfaceTarget,
        pass: GPUDrawPass = drawPass(command),
        pipelinePlan: GPUPipelineCreationPlan = renderPipelinePlan(),
        readbackRequests: List<GPUReadbackRequest> = listOf(readbackRequest()),
    ): GPUFirstRouteRenderSubmitRequest {
        return GPUFirstRouteRenderSubmitRequest(
            preflightRequest = GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("surface-write")),
                target = surfaceTarget,
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
    }

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
        val material = assertInstanceOf(GPUMaterialDescriptor.SolidColor::class.java, command.material)
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
                diagnosticLabel = "test:first-route-fill",
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
                rendererSalt = "kanvas-test",
            ),
            moduleHash = "module:generated-solid-fill",
            bindingLayoutHash = "bind-group:solid-color",
            requiredCapabilities = listOf("first_slice.fill_rect.native=supported"),
            creationStage = "render-pipeline",
        )

    private fun readbackRequest(
        requestId: String = "readback-1",
        boundsLabel: String = "0,0 16x16",
        failureReason: String? = null,
    ): GPUReadbackRequest =
        GPUReadbackRequest(
            requestId = requestId,
            sourceLabel = "first-route-webgpu-submit",
            boundsLabel = boundsLabel,
            format = "rgba8unorm",
            synchronizationLabel = "after-submit",
            expectedArtifactLabel = "first-route-fill.png",
            failureReason = failureReason,
        )

    private fun assertRefused(
        evidence: GpuRendererFirstRouteWebGpuSubmitEvidence,
        expectedCode: String,
    ) {
        val refused = assertInstanceOf(
            GPUCommandSubmission.Refused::class.java,
            evidence.submission,
        )
        assertEquals(expectedCode, refused.diagnostic.code)
        assertFalse(refused.dumpLines().joinToString("\n").contains("submission:submitted"))
        assertTrue(evidence.readbacks.none { result -> result is GPUReadbackResult.Completed })
    }

    private fun assertDiagnosticExecutedPmBundleContainsOnlyHashesAndNonPromotionalClaims(root: File) {
        val textArtifacts = root
            .walkTopDown()
            .filter { artifact -> artifact.isFile && artifact.extension == "txt" }
            .toList()
        assertTrue(textArtifacts.isNotEmpty(), "Expected diagnostic PM bundle text artifacts under ${root.path}")

        val violations = textArtifacts.flatMap { artifact ->
            artifact.readLines().flatMapIndexed { lineIndex, line ->
                val exactMarkers = forbiddenDiagnosticPmBundleTextMarkers
                    .filter { marker -> line.contains(marker) }
                val patternMarkers = forbiddenDiagnosticPmBundleTextPatterns
                    .filter { pattern -> pattern.containsMatchIn(line) }
                    .map { pattern -> pattern.pattern }
                (exactMarkers + patternMarkers)
                    .map { marker ->
                        DiagnosticPmBundleTextViolation(
                            relativePath = root.toPath().relativize(artifact.toPath()).toString(),
                            lineNumber = lineIndex + 1,
                            marker = marker,
                            line = line,
                        )
                    }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                prefix = "Diagnostic PM bundle text artifacts include forbidden raw/promotional text:\n",
                separator = "\n",
            ) { violation ->
                "${violation.relativePath}:${violation.lineNumber}: ${violation.marker}: " +
                    violation.line.take(240)
            },
        )
    }

    private fun pixelComponent(pixels: ByteArray, x: Int, y: Int, width: Int, channel: Int): Int {
        val index = (y * width + x) * 4 + channel
        return pixels[index].toInt() and 0xff
    }

    private fun pixelAlpha(pixels: ByteArray, x: Int, y: Int, width: Int): Int {
        val index = (y * width + x) * 4 + 3
        return pixels[index].toInt() and 0xff
    }

    private data class DiagnosticPmBundleTextViolation(
        val relativePath: String,
        val lineNumber: Int,
        val marker: String,
        val line: String,
    )

    private companion object {
        val forbiddenDiagnosticPmBundleTextMarkers = listOf(
            "readbackBytes",
            "readbackBytesByRequestId",
            "rawReadback",
            "readbackBuffer",
            "mappedRange",
            "pixel=",
            "pixels=",
            "rawPixel",
            "rawPixels",
            "@vertex",
            "@fragment",
            "@compute",
            "@group(",
            "@binding(",
            "fn ",
            "var<",
            "struct ",
            "supportClaim",
            "productRouteActivated=true",
            "releaseBlocking=true",
            "nativeKadreCiRequired=true",
            "readinessDelta",
            "supported=true",
        )

        val forbiddenDiagnosticPmBundleTextPatterns = listOf(
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?product[\s_-]*route[\s_-]*activated["']?\s*(?:=|:)\s*true\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?release[\s_-]*blocking["']?\s*(?:=|:)\s*true\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?native[\s_-]*kadre[\s_-]*ci[\s_-]*required["']?\s*(?:=|:)\s*true\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?readiness[\s_-]*delta["']?\s*(?:=|:)\s*[+-]?(?:[1-9]\d*(?:\.\d+)?|0?\.\d*[1-9]\d*)\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?support[\s_-]*claim["']?\s*(?:=|:)\s*true\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?supported["']?\s*(?:=|:)\s*true\b""",
                option = RegexOption.IGNORE_CASE,
            ),
            Regex(
                pattern = """(?<![A-Za-z0-9_])["']?web[\s_-]*gpu[\s_-]*adapter[\s_-]*required["']?\s*(?:=|:)\s*false\b""",
                option = RegexOption.IGNORE_CASE,
            ),
        )
    }
}
