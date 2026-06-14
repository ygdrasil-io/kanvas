package org.graphiks.kanvas.gpu.renderer.validation

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandSubmission
import org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheEventResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetryEvent
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteCommandFamily
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteCommandSubmissionOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteResourceMaterializationOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteRouteKind
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteTelemetryEvent
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFirstRouteWGSLModuleValidationOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLBindingLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleHash
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserState
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformFieldLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLValidationDiagnostic

/** Verifies the R6 first-route PM evidence bundle stays promotion-gated. */
class FirstRoutePMEvidenceBundleTest {
    /** Artifact bundles preserve report metadata, diagnostics, and dump text in a stable manifest. */
    @Test
    fun `validation report artifact bundle renders deterministic manifest and artifacts`() {
        val report = GPUValidationReport(
            name = "diagnostic-first-route-pm",
            status = GPUValidationStatus.Incomplete,
            dumps = listOf(
                GPUContractDump(
                    name = "diagnostic-route",
                    artifactName = "diagnostic-route.txt",
                    entries = listOf(
                        GPUContractDump.Entry(
                            ownerPackage = "routing",
                            concept = "GPURouteDecision.Refused",
                            detail = "refused before backend execution",
                        ),
                    ),
                ),
            ),
            diagnostics = listOf("route requires GPURouteDecision.Native"),
        )

        val bundle = report.artifactBundle()

        assertEquals(
            expected = listOf(
                "diagnostic-first-route-pm-00-manifest.txt",
                "diagnostic-route.txt",
            ),
            actual = bundle.artifactNames,
        )
        val manifest = bundle.manifestLines()
        assertEquals(
            expected = listOf(
                "validation.report.name=diagnostic-first-route-pm",
                "validation.report.status=Incomplete",
                "validation.gate.name=first-route-promotion",
                "validation.gate.passed=false",
                "validation.gate.missingEvidence=command,analysis,route,material,wgsl,payload,pipeline-key," +
                    "resource-decision,submission,readback,telemetry,pipeline-cache,negative-cpu-fallback," +
                    "unsupported-route-refusals",
                "validation.bundle.scope=pm-evidence-only",
                "validation.report.artifacts=1",
                "validation.report.diagnostics=1",
                "validation.gate.diagnostics=2",
                "artifact.01.name=diagnostic-route.txt",
                "artifact.01.lines=1",
                "diagnostic.01=route requires GPURouteDecision.Native",
                "gateDiagnostic.01=validation report status is Incomplete",
                "gateDiagnostic.02=route requires GPURouteDecision.Native",
            ),
            actual = manifest.filterNot { line -> line.startsWith("artifact.01.sha256=") },
        )
        assertTrue(
            actual = manifest.single { line -> line.startsWith("artifact.01.sha256=") }.let { line ->
                line.startsWith("artifact.01.sha256=sha256:") &&
                    line.removePrefix("artifact.01.sha256=sha256:").length == 64
            },
        )
        assertEquals(
            expected = bundle.manifestLines(),
            actual = bundle.artifactLines("diagnostic-first-route-pm-00-manifest.txt"),
        )
        assertEquals(
            expected = listOf("routing:GPURouteDecision.Refused:refused before backend execution"),
            actual = bundle.artifactLines("diagnostic-route.txt"),
        )
    }

    /** Artifact bundles materialize manifest-first PM evidence without path traversal. */
    @Test
    fun `validation artifact bundle writes portable manifest and artifacts to directory`() {
        val outputRoot = temporaryArtifactRoot()
        try {
            val bundle = GPUValidationArtifactBundle(
                reportName = "diagnostic-portable-pm",
                status = GPUValidationStatus.Incomplete,
                gatePassed = false,
                missingEvidence = listOf("route"),
                manifestArtifactName = "diagnostic-portable-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(
                        artifactName = "dumps/diagnostic-route.txt",
                        lines = listOf("routing:GPURouteDecision.Refused:portable refusal"),
                    ),
                ),
                diagnostics = listOf("route requires GPURouteDecision.Native"),
                gateDiagnostics = listOf("validation report status is Incomplete"),
            )

            val result = bundle.writeTo(outputRoot)

            assertEquals(outputRoot, result.rootDirectory)
            assertEquals(
                expected = listOf(
                    "diagnostic-portable-pm-00-manifest.txt",
                    "dumps/diagnostic-route.txt",
                ),
                actual = result.relativePaths,
            )
            assertEquals(bundle.manifestLines(), outputRoot.resolve("diagnostic-portable-pm-00-manifest.txt").readLines())
            assertEquals(
                expected = listOf("routing:GPURouteDecision.Refused:portable refusal"),
                actual = outputRoot.resolve("dumps/diagnostic-route.txt").readLines(),
            )
            val routeArtifactHash = outputRoot
                .resolve("dumps/diagnostic-route.txt")
                .readBytes()
                .fileBytesSha256()
            assertEquals(
                expected = routeArtifactHash,
                actual = result.writes.single { write -> write.relativePath == "dumps/diagnostic-route.txt" }.sha256,
            )
            assertContains(
                outputRoot.resolve("diagnostic-portable-pm-00-manifest.txt").readLines(),
                "artifact.01.sha256=$routeArtifactHash",
            )
            assertEquals(
                expected = outputRoot
                    .resolve("diagnostic-portable-pm-00-manifest.txt")
                    .readBytes()
                    .fileBytesSha256(),
                actual = result.writes
                    .single { write -> write.relativePath == "diagnostic-portable-pm-00-manifest.txt" }
                    .sha256,
            )
            assertEquals(
                expected = listOf(bundle.manifestLines().size, 1),
                actual = result.writes.map { write -> write.lineCount },
            )
            assertTrue(result.writes.all { write -> write.sha256.startsWith("sha256:") })

            val refusal = assertFailsWith<IllegalArgumentException> {
                bundle.writeTo(outputRoot)
            }
            assertContains(
                refusal.message.orEmpty(),
                "validation artifact already exists: diagnostic-portable-pm-00-manifest.txt",
            )
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    /** Artifact bundles reject paths that would escape or depend on platform separators. */
    @Test
    fun `validation artifact bundle rejects non portable artifact paths`() {
        val invalidNames = listOf(
            "../escape.txt",
            "/absolute.txt",
            "dumps\\route.txt",
            "dumps//route.txt",
            "dumps/./route.txt",
            "C:/drive.txt",
            "dumps/route name.txt",
            "dumps/route\nname.txt",
        )

        for (invalidName in invalidNames) {
            val artifactFailure = assertFailsWith<IllegalArgumentException> {
                GPUValidationArtifactBundle(
                    reportName = "diagnostic-invalid-path-pm",
                    status = GPUValidationStatus.Incomplete,
                    manifestArtifactName = "diagnostic-invalid-path-pm-00-manifest.txt",
                    artifacts = listOf(GPUValidationArtifact(artifactName = invalidName, lines = emptyList())),
                )
            }
            assertContains(artifactFailure.message.orEmpty(), "validation artifact path must be portable")

            val manifestFailure = assertFailsWith<IllegalArgumentException> {
                GPUValidationArtifactBundle(
                    reportName = "diagnostic-invalid-path-pm",
                    status = GPUValidationStatus.Incomplete,
                    manifestArtifactName = invalidName,
                    artifacts = listOf(GPUValidationArtifact(artifactName = "route.txt", lines = emptyList())),
                )
            }
            assertContains(manifestFailure.message.orEmpty(), "validation manifest artifact path must be portable")
        }
    }

    /** Artifact path checks catch collisions that would overwrite on common PM filesystems. */
    @Test
    fun `validation artifact bundle rejects case insensitive and file directory path collisions`() {
        val caseFailure = assertFailsWith<IllegalArgumentException> {
            GPUValidationArtifactBundle(
                reportName = "diagnostic-path-collision-pm",
                status = GPUValidationStatus.Incomplete,
                manifestArtifactName = "diagnostic-path-collision-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(artifactName = "dumps/Route.txt", lines = emptyList()),
                    GPUValidationArtifact(artifactName = "dumps/route.txt", lines = emptyList()),
                ),
            )
        }
        assertContains(caseFailure.message.orEmpty(), "validation artifact paths conflict case-insensitively")

        val parentFailure = assertFailsWith<IllegalArgumentException> {
            GPUValidationArtifactBundle(
                reportName = "diagnostic-parent-collision-pm",
                status = GPUValidationStatus.Incomplete,
                manifestArtifactName = "diagnostic-parent-collision-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(artifactName = "dumps", lines = emptyList()),
                    GPUValidationArtifact(artifactName = "dumps/route.txt", lines = emptyList()),
                ),
            )
        }
        assertContains(parentFailure.message.orEmpty(), "validation artifact parent conflicts")

        val caseInsensitiveParentFailure = assertFailsWith<IllegalArgumentException> {
            GPUValidationArtifactBundle(
                reportName = "diagnostic-case-parent-collision-pm",
                status = GPUValidationStatus.Incomplete,
                manifestArtifactName = "diagnostic-case-parent-collision-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(artifactName = "Dumps", lines = emptyList()),
                    GPUValidationArtifact(artifactName = "dumps/route.txt", lines = emptyList()),
                ),
            )
        }
        assertContains(caseInsensitiveParentFailure.message.orEmpty(), "validation artifact parent conflicts")
    }

    /** Existing target preflight refuses before any artifact is materialized. */
    @Test
    fun `validation artifact bundle preflights existing target paths before writing`() {
        val outputRoot = temporaryArtifactRoot()
        try {
            outputRoot.resolve("dumps").mkdirs()
            outputRoot.resolve("dumps/route.txt").writeText("existing\n")
            val bundle = GPUValidationArtifactBundle(
                reportName = "diagnostic-preflight-pm",
                status = GPUValidationStatus.Incomplete,
                manifestArtifactName = "diagnostic-preflight-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(
                        artifactName = "command.txt",
                        lines = listOf("commands:NormalizedDrawCommand.FillRect:command evidence"),
                    ),
                    GPUValidationArtifact(
                        artifactName = "dumps/route.txt",
                        lines = listOf("routing:GPURouteDecision.Refused:route evidence"),
                    ),
                ),
            )

            val failure = assertFailsWith<IllegalArgumentException> {
                bundle.writeTo(outputRoot)
            }

            assertContains(failure.message.orEmpty(), "validation artifact already exists: dumps/route.txt")
            assertFalse(outputRoot.resolve("diagnostic-preflight-pm-00-manifest.txt").exists())
            assertFalse(outputRoot.resolve("command.txt").exists())
            assertEquals("existing\n", outputRoot.resolve("dumps/route.txt").readText())
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    /** Replace-mode exports still preflight directory targets before writing any artifact. */
    @Test
    fun `validation artifact bundle preflights existing directory targets when replacing`() {
        val outputRoot = temporaryArtifactRoot()
        try {
            outputRoot.resolve("dumps/route.txt").mkdirs()
            val bundle = GPUValidationArtifactBundle(
                reportName = "diagnostic-replace-preflight-pm",
                status = GPUValidationStatus.Incomplete,
                manifestArtifactName = "diagnostic-replace-preflight-pm-00-manifest.txt",
                artifacts = listOf(
                    GPUValidationArtifact(
                        artifactName = "command.txt",
                        lines = listOf("commands:NormalizedDrawCommand.FillRect:command evidence"),
                    ),
                    GPUValidationArtifact(
                        artifactName = "dumps/route.txt",
                        lines = listOf("routing:GPURouteDecision.Refused:route evidence"),
                    ),
                ),
            )

            val failure = assertFailsWith<IllegalArgumentException> {
                bundle.writeTo(outputRoot, replaceExisting = true)
            }

            assertContains(failure.message.orEmpty(), "validation artifact target is not a file: dumps/route.txt")
            assertFalse(outputRoot.resolve("diagnostic-replace-preflight-pm-00-manifest.txt").exists())
            assertFalse(outputRoot.resolve("command.txt").exists())
            assertTrue(outputRoot.resolve("dumps/route.txt").isDirectory)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    /** The default R6 exporter materializes refusal-first PM evidence without product activation. */
    @Test
    fun `default first route PM evidence exporter writes incomplete bundle artifacts`() {
        val outputRoot = temporaryArtifactRoot()
        try {
            val result = writeFirstRoutePMEvidenceArtifactBundle(outputRoot)

            assertEquals(
                expected = listOf("gpu-renderer-first-route-pm-evidence-00-manifest.txt") +
                    GPUValidationFixture().firstRoutePMEvidenceBundle().dumps.map { dump -> dump.artifactName },
                actual = result.relativePaths,
            )
            val manifest = outputRoot.resolve("gpu-renderer-first-route-pm-evidence-00-manifest.txt").readLines()
            assertContains(manifest, "validation.report.status=Incomplete")
            assertContains(manifest, "validation.gate.passed=false")
            assertContains(manifest, "validation.bundle.scope=pm-evidence-only")
            assertFalse(
                actual = manifest.any { line ->
                    line.contains("productRouteActivated=true") ||
                        line.contains("supported=true") ||
                        line.contains("releaseBlocking=true")
                },
            )
            assertTrue(outputRoot.resolve("gpu-renderer-first-route-pm-evidence-13-negative-cpu-fallback.txt").isFile)
            assertTrue(outputRoot.resolve("gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt").isFile)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    /** Refusal-first runtime evidence remains incomplete and dumpable instead of claiming support. */
    @Test
    fun `current first route PM evidence bundle remains incomplete before materialization and submission`() {
        val report = GPUValidationFixture().firstRoutePMEvidenceBundle()

        assertEquals("gpu-renderer-first-route-pm-evidence", report.name)
        assertEquals(GPUValidationStatus.Incomplete, report.status)
        assertEquals(
            expected = listOf(
                "gpu-renderer-first-route-pm-evidence-01-command.txt",
                "gpu-renderer-first-route-pm-evidence-02-analysis.txt",
                "gpu-renderer-first-route-pm-evidence-03-route.txt",
                "gpu-renderer-first-route-pm-evidence-04-material.txt",
                "gpu-renderer-first-route-pm-evidence-05-wgsl.txt",
                "gpu-renderer-first-route-pm-evidence-06-payload.txt",
                "gpu-renderer-first-route-pm-evidence-07-pipeline-key.txt",
                "gpu-renderer-first-route-pm-evidence-08-resource-decision.txt",
                "gpu-renderer-first-route-pm-evidence-09-submission.txt",
                "gpu-renderer-first-route-pm-evidence-10-readback.txt",
                "gpu-renderer-first-route-pm-evidence-11-telemetry.txt",
                "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt",
                "gpu-renderer-first-route-pm-evidence-13-negative-cpu-fallback.txt",
                "gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt",
            ),
            actual = report.dumps.map { dump -> dump.artifactName },
        )
        assertEquals(
            expected = listOf(
                "commands:GPUDrawCommandID:canonical command identifier",
                "commands:NormalizedDrawCommand.FillRect:first-slice draw command",
                "commands:GPUMaterialDescriptor.SolidColor:first-slice material descriptor",
                "analysis:GPUDrawAnalysisRecord:first-route analysis dump schema",
                "routing:GPURouteDecision.Refused:first-route route dump schema without product promotion",
                "materials:GPUPaintPipelinePlan:first-route solid material dump schema",
                "wgsl:WGSLReflectionResult:first-route WGSL reflection dump schema",
                "payloads:GPUPayloadGatherPlan:first-route payload dump schema",
                "pipelines:GPUPipelineKeyPreimage.Render:first-route pipeline-key preimage dump schema",
                "resources:GPUResourceMaterializationDecision.Refused:first-route resource decision dump schema",
                "execution:GPUCommandSubmission.Refused:first-route submission dump schema refuses before backend work",
                "telemetry:GPUTelemetryLedger:first-route telemetry dump schema",
                "routing:NegativeCPUFallbackRefusal:forbidden CPU-rendered texture fallback remains refused",
                "routing:UnsupportedRouteFamilyRefusal:first-route unsupportedFamilies=perspective-transform,singular-transform,unsupported-target-format,unsupported-blend,non-simple-clip,layer-filter-destination-read,missing-capability,wgsl-validation-or-abi-mismatch diagnostics=none",
            ),
            actual = report.dumps.flatMap { dump -> dump.lines() },
        )
        assertEquals(
            expected = listOf(
                "first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
                "route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
                "resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
                "submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
                "readback requires GPUReadbackResult.Completed",
                "pipeline-cache requires GPUCacheTelemetry.pipeline",
            ),
            actual = report.diagnostics,
        )

        val gate = GPUPromotionGateCheck().evaluate(report)
        assertFalse(gate.passed)
        assertEquals(listOf("route", "resource-decision", "submission", "readback", "pipeline-cache"), gate.missingEvidence)
        assertContains(gate.diagnostics.joinToString("\n"), "validation report status is Incomplete")
        assertContains(
            gate.diagnostics.joinToString("\n"),
            "resource-decision requires GPUResourceMaterializationDecision.Materialized",
        )
        assertContains(
            gate.diagnostics.joinToString("\n"),
            "pipeline-cache requires GPUCacheTelemetry.pipeline",
        )
    }

    /** Synthetic test evidence passes only when every required R6 category is present. */
    @Test
    fun `synthetic complete PM evidence passes the gate only with every required category`() {
        val completeReport = GPUValidationFixture().firstRoutePMEvidenceBundle(
            name = "synthetic-test-complete-first-route-pm-evidence",
            entries = syntheticCompleteEntries.map { evidence -> evidence.entry },
        )

        assertEquals(GPUValidationStatus.Passed, completeReport.status)
        assertTrue(GPUPromotionGateCheck().evaluate(completeReport).passed)
        assertTrue(
            actual = completeReport.dumps.all { dump -> dump.artifactName.startsWith("synthetic-test-") },
            message = "Synthetic exposed evidence must be clearly labeled: ${completeReport.dumps.map { it.artifactName }}",
        )
        assertContains(
            completeReport.dumps.flatMap { dump -> dump.lines() }.joinToString("\n"),
            "synthetic-test complete route evidence",
        )

        for (removed in syntheticCompleteEntries) {
            val report = GPUValidationFixture().firstRoutePMEvidenceBundle(
                name = "synthetic-test-missing-${removed.category}-pm-evidence",
                entries = syntheticCompleteEntries
                    .filterNot { evidence -> evidence.category == removed.category }
                    .map { evidence -> evidence.entry },
            )
            val result = GPUPromotionGateCheck().evaluate(report)

            assertEquals(GPUValidationStatus.Incomplete, report.status, "missing ${removed.category}")
            assertFalse(result.passed, "missing ${removed.category}")
            assertContains(result.missingEvidence, removed.category, "missing ${removed.category}")
        }
    }

    /** Accepted recordings contribute real first-route evidence while staying pre-materialization gated. */
    @Test
    fun `recording backed PM evidence uses accepted fill rect recording without promoting prematerialization`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.accepted-pm", commandIdValue = 3)
        val report = GPUValidationFixture().firstRouteRecordingPMEvidenceBundle(
            recording = recording,
            telemetryLedger = telemetryAndPipelineCacheLedger(),
        )

        assertEquals("gpu-renderer-first-route-recording-pm-evidence", report.name)
        assertEquals(GPUValidationStatus.Incomplete, report.status)
        assertEquals(
            expected = listOf(
                "gpu-renderer-first-route-recording-pm-evidence-01-command.txt",
                "gpu-renderer-first-route-recording-pm-evidence-02-analysis.txt",
                "gpu-renderer-first-route-recording-pm-evidence-03-route.txt",
                "gpu-renderer-first-route-recording-pm-evidence-04-material.txt",
                "gpu-renderer-first-route-recording-pm-evidence-05-wgsl.txt",
                "gpu-renderer-first-route-recording-pm-evidence-06-payload.txt",
                "gpu-renderer-first-route-recording-pm-evidence-07-pipeline-key.txt",
                "gpu-renderer-first-route-recording-pm-evidence-08-resource-decision.txt",
                "gpu-renderer-first-route-recording-pm-evidence-09-submission.txt",
                "gpu-renderer-first-route-recording-pm-evidence-10-readback.txt",
                "gpu-renderer-first-route-recording-pm-evidence-11-telemetry.txt",
                "gpu-renderer-first-route-recording-pm-evidence-12-pipeline-cache.txt",
                "gpu-renderer-first-route-recording-pm-evidence-13-negative-cpu-fallback.txt",
                "gpu-renderer-first-route-recording-pm-evidence-14-unsupported-route-refusals.txt",
                "gpu-renderer-first-route-recording-pm-evidence-15-recording-analysis.txt",
                "gpu-renderer-first-route-recording-pm-evidence-16-recording-task-list.txt",
                "gpu-renderer-first-route-recording-pm-evidence-17-recording-compatibility.txt",
                "gpu-renderer-first-route-recording-pm-evidence-18-recording-replay.txt",
            ),
            actual = report.dumps.map { dump -> dump.artifactName },
        )

        val lines = report.dumps.flatMap { dump -> dump.lines() }
        assertContains(
            lines,
            "commands:NormalizedDrawCommand.FillRect:recording recording.accepted-pm commandIds=3 families=FillRect",
        )
        assertContains(
            lines,
            "routing:GPURouteDecision.Native:recording recording.accepted-pm route:native.fill_rect.solid",
        )
        assertContains(
            lines,
            "materials:GPUPaintPipelinePlan:recording recording.accepted-pm materialKeyHashes=pending.material.solid",
        )
        assertContains(
            lines,
            "wgsl:WGSLReflectionResult:recording recording.accepted-pm pre-materialization WGSL evidence for pipelineKeys=pending.pipeline.fill_rect.solid.rgba8unorm.src_over",
        )
        assertContains(
            lines,
            "payloads:GPUPayloadGatherPlan:recording recording.accepted-pm payload evidence for renderTasks=task.render.3",
        )
        assertContains(
            lines,
            "pipelines:GPUPipelineKeyPreimage.Render:recording recording.accepted-pm pipelineKeyHashes=pending.pipeline.fill_rect.solid.rgba8unorm.src_over",
        )
        assertContains(
            lines,
            "resources:GPUResourceMaterializationDecision.Refused:recording recording.accepted-pm pre-materialization resources are not materialized",
        )
        assertContains(
            lines,
            "execution:GPUCommandSubmission.Refused:recording recording.accepted-pm command submission is absent before execution",
        )
        assertContains(
            lines,
            "recording:GPUAnalysisDecisionDump:recording recording.accepted-pm decision:candidate:analysis.fill_rect.3:native.fill_rect.solid",
        )
        assertContains(
            lines,
            "recording:GPUTaskList:recording recording.accepted-pm task:render:task.render.3:pass.root.3:analysis.fill_rect.3:pre_materialization",
        )
        assertContains(
            lines,
            "recording:GPURecordingCompatibilityKey:recording recording.accepted-pm replayPolicy=one-shot",
        )
        assertContains(
            lines,
            "recording:GPURecordingReplayResult.Refused:recording recording.accepted-pm replay.one_shot_recording",
        )
        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.route.count:kind=GPUNative:1:count",
        )
        assertContains(
            lines,
            "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:key=render-pipeline:accepted:subject=render-pipeline:first-fill-rect",
        )
        assertFalse(
            actual = lines.any { line ->
                "GPUResourceMaterializationDecision.Materialized" in line ||
                    "GPUCommandSubmission.Submitted" in line
            },
            message = "Recording-backed PM evidence must not synthesize materialization or submission success: $lines",
        )

        val gate = GPUPromotionGateCheck().evaluate(report)
        assertFalse(gate.passed)
        assertEquals(listOf("resource-decision", "submission", "readback"), gate.missingEvidence)

        val reportWithoutLedgerFacts = GPUValidationFixture().firstRouteRecordingPMEvidenceBundle(
            recording = recording,
            telemetryLedger = GPUTelemetryLedger.empty(),
        )
        val linesWithoutLedgerFacts = reportWithoutLedgerFacts.dumps.flatMap { dump -> dump.lines() }
        assertFalse(
            actual = linesWithoutLedgerFacts.any { line -> line.startsWith("telemetry:") },
            message = "Telemetry evidence must be absent when the supplied ledger has no first-route or pipeline cache facts.",
        )
        assertContains(GPUPromotionGateCheck().evaluate(reportWithoutLedgerFacts).missingEvidence, "telemetry")
        assertContains(GPUPromotionGateCheck().evaluate(reportWithoutLedgerFacts).missingEvidence, "pipeline-cache")
    }

    /** Executed backend evidence may pass only when real late-stage categories are supplied. */
    @Test
    fun `executed PM evidence passes with materialized submitted completed readback and pipeline cache facts`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val materialization = materializedFirstRouteDecision(commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()
        val submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest)
        val completedReadback = GPUReadbackResult.Completed(
            request = readbackRequest,
            payloadHash = "sha256:0123456789abcdef",
            byteCount = 16L * 16L * 4L,
        )

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materialization,
            submission = submission,
            readbacks = listOf(completedReadback),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )

        assertEquals(GPUValidationStatus.Passed, report.status)
        assertTrue(GPUPromotionGateCheck().evaluate(report).passed)
        assertEquals(
            expected = listOf(
                "synthetic-test-executed-first-route-pm-evidence-01-command.txt",
                "synthetic-test-executed-first-route-pm-evidence-02-analysis.txt",
                "synthetic-test-executed-first-route-pm-evidence-03-route.txt",
                "synthetic-test-executed-first-route-pm-evidence-04-material.txt",
                "synthetic-test-executed-first-route-pm-evidence-05-wgsl.txt",
                "synthetic-test-executed-first-route-pm-evidence-06-payload.txt",
                "synthetic-test-executed-first-route-pm-evidence-07-pipeline-key.txt",
                "synthetic-test-executed-first-route-pm-evidence-08-resource-decision.txt",
                "synthetic-test-executed-first-route-pm-evidence-09-submission.txt",
                "synthetic-test-executed-first-route-pm-evidence-10-readback.txt",
                "synthetic-test-executed-first-route-pm-evidence-11-telemetry.txt",
                "synthetic-test-executed-first-route-pm-evidence-12-pipeline-cache.txt",
                "synthetic-test-executed-first-route-pm-evidence-13-negative-cpu-fallback.txt",
                "synthetic-test-executed-first-route-pm-evidence-14-unsupported-route-refusals.txt",
                "synthetic-test-executed-first-route-pm-evidence-15-recording-analysis.txt",
                "synthetic-test-executed-first-route-pm-evidence-16-recording-task-list.txt",
                "synthetic-test-executed-first-route-pm-evidence-17-recording-compatibility.txt",
                "synthetic-test-executed-first-route-pm-evidence-18-recording-replay.txt",
            ),
            actual = report.dumps.map { dump -> dump.artifactName },
        )

        val lines = report.dumps.flatMap { dump -> dump.lines() }
        assertContains(
            lines,
            "resources:GPUResourceMaterializationDecision.Materialized:synthetic-test-executed-first-route-pm-evidence resource.materialization:materialized target=surface.executed tasks=task.render.9 resourcePlans=webgpu.headless-target.surface resourceCount=1 diagnostics=none",
        )
        assertContains(
            lines,
            "execution:GPUCommandSubmission.Submitted:synthetic-test-executed-first-route-pm-evidence execution.submission:submitted id=submit.executed deviceGeneration=1 targetGeneration=1 scopes=root-pass tasks=task.render.9 passes=pass.root.9 resources=surface.executed:copy_src,render_attachment routes=GPUNative=1 readbacks=readback-1 diagnostics=none",
        )
        assertContains(
            lines,
            "execution:GPUReadbackResult.Completed:synthetic-test-executed-first-route-pm-evidence execution.readback:completed request=readback-1 source=first-route-webgpu-submit bounds=0,0 16x16 format=rgba8unorm sync=after-submit expectedArtifact=first-route-fill.png failureReason=none bytes=1024 payloadHash=sha256:0123456789abcdef diagnostics=none",
        )
        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.resource_materialization.count:outcome=Materialized:1:count",
        )
        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.command_submission.count:outcome=Submitted:1:count",
        )
        assertContains(
            lines,
            "routing:NegativeCPUFallbackRefusal:recording recording.executed-pm has no CPU-rendered texture fallback tasks",
        )
        assertFalse(
            actual = lines.any { line ->
                "GPUResourceMaterializationDecision.Refused" in line ||
                    "GPUCommandSubmission.Refused" in line ||
                    "execution.readback:skipped" in line
            },
            message = "Executed PM evidence must not carry pre-execution refusals or skipped readback as passing evidence: $lines",
        )

        val reportWithoutCompletedReadback = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-no-readback-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materialization,
            submission = submission,
            readbacks = emptyList(),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )
        val gateWithoutCompletedReadback = GPUPromotionGateCheck().evaluate(reportWithoutCompletedReadback)

        assertEquals(GPUValidationStatus.Failed, reportWithoutCompletedReadback.status)
        assertFalse(gateWithoutCompletedReadback.passed)
        assertContains(gateWithoutCompletedReadback.missingEvidence, "readback")
        assertContains(
            gateWithoutCompletedReadback.diagnostics.joinToString("\n"),
            "readback requires GPUReadbackResult.Completed",
        )
        assertContains(
            reportWithoutCompletedReadback.diagnostics.joinToString("\n"),
            "executed PM evidence mismatch: submitted readback requestIds missing results readback-1",
        )
    }

    /** Executed PM evidence requires matching telemetry counters for late positive evidence. */
    @Test
    fun `executed PM evidence fails without materialized and submitted telemetry counters`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-missing-late-telemetry-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = telemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)
        val diagnostics = report.diagnostics.joinToString("\n")

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertFalse(gate.passed)
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.resource_materialization.count outcome=Materialized unit=count positive count",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.command_submission.count outcome=Submitted unit=count positive count",
        )
    }

    /** Executed PM evidence requires command-family telemetry for the native FillRect route. */
    @Test
    fun `executed PM evidence fails without command family telemetry counter`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()
        val completeTelemetryLedger = executedTelemetryAndPipelineCacheLedger()
        val telemetryWithoutCommandFamily = completeTelemetryLedger.copy(
            counters = completeTelemetryLedger.counters.filterNot { counter ->
                counter.name == "first_route.command.count" && counter.scope == "family=Rect"
            },
        )

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-missing-command-family-telemetry-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = telemetryWithoutCommandFamily,
        )
        val diagnostics = report.diagnostics.joinToString("\n")

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.command.count family=Rect unit=count positive count",
        )
    }

    /** Executed PM evidence also requires route, WGSL validation, and negative fallback counters. */
    @Test
    fun `executed PM evidence fails without route wgsl validation and negative fallback telemetry counters`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-missing-promotion-telemetry-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = executedLateOnlyTelemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)
        val lines = report.dumps.flatMap { dump -> dump.lines() }
        val diagnostics = report.diagnostics.joinToString("\n")

        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.resource_materialization.count:outcome=Materialized:1:count",
        )
        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.command_submission.count:outcome=Submitted:1:count",
        )
        assertFalse(lines.any { line -> "first_route.route.count" in line })
        assertFalse(lines.any { line -> "first_route.wgsl_module_validation.count" in line })
        assertFalse(lines.any { line -> "first_route.negative_cpu_fallback.refusal.count" in line })

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertFalse(gate.passed)
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.route.count kind=GPUNative unit=count positive count",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.wgsl_module_validation.count outcome=Success unit=count positive count",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.negative_cpu_fallback.refusal.count policy=forbidden unit=count positive count",
        )
    }

    /** Executed evidence cannot pass when late-stage facts are not tied to the recording. */
    @Test
    fun `executed PM evidence fails when materialization submission or readback do not match recording`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val submittedReadbackRequest = completedReadbackRequest()
        val unsubmittedReadbackRequest = completedReadbackRequest().copy(requestId = "readback-unsubmitted")

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-mismatched-executed-first-route-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 77),
            submission = submittedFirstRouteCommand(commandIdValue = 77, readbackRequest = submittedReadbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = unsubmittedReadbackRequest,
                    payloadHash = "sha256:fedcba9876543210",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)
        val diagnostics = report.diagnostics.joinToString("\n")

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertFalse(gate.passed)
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: materialization taskIds missing recording tasks task.render.9",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: materialization taskIds outside recording task.render.77",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: submission passIds missing recording passes pass.root.9",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: submission passIds outside recording pass.root.77",
        )
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: completed readback requestIds not submitted readback-unsubmitted",
        )
    }

    /** Every submitted readback request must have explicit result evidence. */
    @Test
    fun `executed PM evidence fails when submitted readback request has no result`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val completedReadbackRequest = completedReadbackRequest()
        val missingReadbackRequest = completedReadbackRequest().copy(requestId = "readback-2")
        val submission = submittedFirstRouteCommand(
            commandIdValue = 9,
            readbackRequest = completedReadbackRequest,
        ).copy(readbackRequests = listOf(completedReadbackRequest, missingReadbackRequest))

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-missing-readback-result-first-route-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submission,
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = completedReadbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)
        val diagnostics = report.diagnostics.joinToString("\n")

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertFalse(gate.passed)
        assertContains(
            diagnostics,
            "executed PM evidence mismatch: submitted readback requestIds missing results readback-2",
        )
    }

    /** One submitted readback request must not carry multiple result records. */
    @Test
    fun `executed PM evidence fails when submitted readback request has duplicate results`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()
        val duplicateResultCases = listOf(
            "completed-completed" to listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:fedcba9876543210",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            "completed-skipped" to listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
                GPUReadbackResult.Skipped(
                    request = readbackRequest,
                    reasonCode = "synthetic-duplicate-readback-result",
                ),
            ),
            "completed-refused" to listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
                GPUReadbackResult.Refused(
                    request = readbackRequest,
                    diagnostic = GPUExecutionDiagnostic(
                        code = "synthetic.duplicate_readback_result",
                        stage = "readback",
                        message = "Duplicate readback result for submitted request.",
                        terminal = true,
                    ),
                ),
            ),
        )

        duplicateResultCases.forEach { (label, readbacks) ->
            val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
                name = "synthetic-test-duplicate-readback-result-$label-first-route-pm-evidence",
                recording = recording,
                wgslReflection = syntheticWgslReflection(),
                materialization = materializedFirstRouteDecision(commandIdValue = 9),
                submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
                readbacks = readbacks,
                telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
            )
            val gate = GPUPromotionGateCheck().evaluate(report)
            val diagnostics = report.diagnostics.joinToString("\n")

            assertEquals(GPUValidationStatus.Failed, report.status, label)
            assertFalse(gate.passed, label)
            assertContains(
                diagnostics,
                "executed PM evidence mismatch: readback requestIds with duplicate results readback-1",
                message = label,
            )
        }
    }

    /** Positive executed evidence carrying diagnostics is not clean executed PM evidence. */
    @Test
    fun `executed PM evidence fails when positive executed evidence carries diagnostics`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-diagnostic-executed-first-route-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection().copy(
                diagnostics = listOf(
                    WGSLValidationDiagnostic(
                        code = "diagnostic.wgsl.accepted_reflection",
                        moduleHash = WGSLModuleHash("wgsl-module:synthetic-test-solid-fill"),
                        message = "Accepted reflection kept a diagnostic attached.",
                        terminal = false,
                    ),
                ),
            ),
            materialization = materializedFirstRouteDecision(commandIdValue = 9).copy(
                diagnostics = listOf(
                    GPUResourceDiagnostic(
                        code = "diagnostic.resource.materialized",
                        resourceLabel = "surface.executed",
                        message = "Materialized resource kept a diagnostic attached.",
                        terminal = false,
                    ),
                ),
            ),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest).copy(
                diagnostics = listOf(
                    GPUExecutionDiagnostic(
                        code = "diagnostic.execution.submitted",
                        stage = "submit",
                        message = "Submitted command kept a diagnostic attached.",
                        terminal = false,
                    ),
                ),
            ),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                    diagnostics = listOf(
                        GPUExecutionDiagnostic(
                            code = "diagnostic.execution.completed_readback",
                            stage = "readback",
                            message = "Completed readback kept a diagnostic attached.",
                            terminal = false,
                        ),
                    ),
                ),
            ),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)
        val diagnostics = report.diagnostics.joinToString("\n")

        assertEquals(GPUValidationStatus.Failed, report.status)
        assertFalse(gate.passed)
        assertContains(
            diagnostics,
            "positive executed evidence diagnostics: materialization carries diagnostics diagnostic.resource.materialized",
        )
        assertContains(
            diagnostics,
            "positive executed evidence diagnostics: submission carries diagnostics diagnostic.execution.submitted",
        )
        assertContains(
            diagnostics,
            "positive executed evidence diagnostics: completed readback carries diagnostics diagnostic.execution.completed_readback",
        )
        assertContains(
            diagnostics,
            "positive executed evidence diagnostics: WGSL reflection carries diagnostics diagnostic.wgsl.accepted_reflection",
        )
    }

    /** Executed PM evidence cannot pass on recording-derived WGSL facts alone. */
    @Test
    fun `executed PM evidence requires explicit WGSL reflection result`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()

        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-missing-wgsl-pm-evidence",
            recording = recording,
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )
        val gate = GPUPromotionGateCheck().evaluate(report)

        assertEquals(GPUValidationStatus.Incomplete, report.status)
        assertFalse(gate.passed)
        assertContains(gate.missingEvidence, "wgsl")
        assertContains(gate.diagnostics.joinToString("\n"), "wgsl requires WGSLReflectionResult")
    }

    /** Executed PM evidence packages readback text without bypassing the promotion gate. */
    @Test
    fun `executed PM evidence artifact bundle includes manifest and readback artifact`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.executed-pm", commandIdValue = 9)
        val readbackRequest = completedReadbackRequest()
        val report = GPUValidationFixture().firstRouteExecutedPMEvidenceBundle(
            name = "synthetic-test-executed-first-route-pm-evidence",
            recording = recording,
            wgslReflection = syntheticWgslReflection(),
            materialization = materializedFirstRouteDecision(commandIdValue = 9),
            submission = submittedFirstRouteCommand(commandIdValue = 9, readbackRequest = readbackRequest),
            readbacks = listOf(
                GPUReadbackResult.Completed(
                    request = readbackRequest,
                    payloadHash = "sha256:0123456789abcdef",
                    byteCount = 16L * 16L * 4L,
                ),
            ),
            telemetryLedger = executedTelemetryAndPipelineCacheLedger(),
        )

        val bundle = report.artifactBundle()

        assertTrue(GPUPromotionGateCheck().evaluate(report).passed)
        assertEquals(
            expected = listOf("synthetic-test-executed-first-route-pm-evidence-00-manifest.txt") +
                report.dumps.map { dump -> dump.artifactName },
            actual = bundle.artifactNames,
        )
        assertContains(
            bundle.manifestLines(),
            "artifact.10.name=synthetic-test-executed-first-route-pm-evidence-10-readback.txt",
        )
        assertContains(bundle.manifestLines(), "artifact.10.lines=1")
        assertContains(bundle.manifestLines(), "validation.gate.passed=true")
        assertContains(bundle.manifestLines(), "validation.gate.missingEvidence=none")
        assertContains(bundle.manifestLines(), "validation.bundle.scope=pm-evidence-only")
        assertContains(
            bundle.artifactLines("synthetic-test-executed-first-route-pm-evidence-10-readback.txt"),
            "execution:GPUReadbackResult.Completed:synthetic-test-executed-first-route-pm-evidence " +
                "execution.readback:completed request=readback-1 source=first-route-webgpu-submit " +
                "bounds=0,0 16x16 format=rgba8unorm sync=after-submit " +
                "expectedArtifact=first-route-fill.png failureReason=none bytes=1024 " +
                "payloadHash=sha256:0123456789abcdef diagnostics=none",
        )
    }

    /** Artifact bundles refuse ambiguous duplicate names before PM evidence is exported. */
    @Test
    fun `validation report artifact bundle rejects duplicate artifact names`() {
        val report = GPUValidationReport(
            name = "diagnostic-duplicate-pm",
            status = GPUValidationStatus.Incomplete,
            dumps = listOf(
                GPUContractDump(name = "one", artifactName = "duplicate.txt", entries = emptyList()),
                GPUContractDump(name = "two", artifactName = "duplicate.txt", entries = emptyList()),
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            report.artifactBundle()
        }

        assertContains(failure.message.orEmpty(), "duplicate validation artifact names: duplicate.txt")
    }

    /** Artifact bundles keep the manifest separate from dump artifacts. */
    @Test
    fun `validation report artifact bundle rejects manifest artifact name collisions`() {
        val report = GPUValidationReport(
            name = "diagnostic-manifest-collision-pm",
            status = GPUValidationStatus.Incomplete,
            dumps = listOf(
                GPUContractDump(
                    name = "diagnostic-manifest-collision",
                    artifactName = "diagnostic-manifest-collision.txt",
                    entries = emptyList(),
                ),
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            report.artifactBundle(manifestArtifactName = "diagnostic-manifest-collision.txt")
        }

        assertContains(
            failure.message.orEmpty(),
            "validation manifest artifact name conflicts with dump artifact: diagnostic-manifest-collision.txt",
        )
    }

    /** Artifact bundles snapshot caller-owned mutable collections before rendering. */
    @Test
    fun `validation artifact bundle snapshots mutable constructor inputs`() {
        val lines = mutableListOf("commands:NormalizedDrawCommand.FillRect:stable command evidence")
        val artifacts = mutableListOf(GPUValidationArtifact(artifactName = "command.txt", lines = lines))
        val missingEvidence = mutableListOf("route")
        val diagnostics = mutableListOf("route requires GPURouteDecision.Native")
        val gateDiagnostics = mutableListOf("validation report status is Incomplete")
        val bundle = GPUValidationArtifactBundle(
            reportName = "diagnostic-snapshot-pm",
            status = GPUValidationStatus.Incomplete,
            gateName = "first-route-promotion",
            gatePassed = false,
            missingEvidence = missingEvidence,
            manifestArtifactName = "diagnostic-snapshot-pm-00-manifest.txt",
            artifacts = artifacts,
            diagnostics = diagnostics,
            gateDiagnostics = gateDiagnostics,
        )
        val manifestBeforeMutation = bundle.manifestLines()

        lines += "commands:NormalizedDrawCommand.FillRect:mutated command evidence"
        artifacts += GPUValidationArtifact(
            artifactName = "diagnostic-snapshot-pm-00-manifest.txt",
            lines = listOf("collision"),
        )
        missingEvidence += "submission"
        diagnostics += "submission requires GPUCommandSubmission.Submitted"
        gateDiagnostics += "mutated gate diagnostic"

        assertEquals(manifestBeforeMutation, bundle.manifestLines())
        assertEquals(
            expected = listOf("diagnostic-snapshot-pm-00-manifest.txt", "command.txt"),
            actual = bundle.artifactNames,
        )
        assertEquals(
            expected = listOf("commands:NormalizedDrawCommand.FillRect:stable command evidence"),
            actual = bundle.artifactLines("command.txt"),
        )
        assertEquals(listOf("route"), bundle.missingEvidence)
        assertEquals(listOf("route requires GPURouteDecision.Native"), bundle.diagnostics)
        assertEquals(listOf("validation report status is Incomplete"), bundle.gateDiagnostics)
    }

    /** Prepared route diagnostics remain dumpable but do not satisfy native route promotion evidence. */
    @Test
    fun `recording backed PM evidence keeps prepared route diagnostic non positive`() {
        val recording = acceptedFillRectRecording(
            recordingIdValue = "recording.prepared-route-pm",
            commandIdValue = 5,
        ).copy(
            routeDiagnostics = listOf("route:prepared.fill_rect.solid"),
        )
        val report = GPUValidationFixture().firstRouteRecordingPMEvidenceBundle(
            recording = recording,
            telemetryLedger = telemetryAndPipelineCacheLedger(routeKind = GPUFirstRouteRouteKind.CPUPreparedGPU),
        )

        assertEquals(GPUValidationStatus.Incomplete, report.status)
        val lines = report.dumps.flatMap { dump -> dump.lines() }
        assertContains(
            lines,
            "routing:GPURouteDecision.Prepared:recording recording.prepared-route-pm route:prepared.fill_rect.solid",
        )
        assertFalse(
            actual = lines.any { line -> "routing:GPURouteDecision.Native" in line },
            message = "Prepared route diagnostics must not become positive native evidence: $lines",
        )

        val gate = GPUPromotionGateCheck().evaluate(report)
        assertFalse(gate.passed)
        assertContains(gate.missingEvidence, "route")
        assertContains(
            gate.diagnostics.joinToString("\n"),
            "route requires GPURouteDecision.Native but found GPURouteDecision.Prepared",
        )
    }

    /** Zero-valued aggregate pipeline telemetry is not a real pipeline-cache evidence fact. */
    @Test
    fun `recording backed PM evidence ignores zero aggregate pipeline cache telemetry`() {
        val recording = acceptedFillRectRecording(recordingIdValue = "recording.zero-cache-pm", commandIdValue = 6)
        val report = GPUValidationFixture().firstRouteRecordingPMEvidenceBundle(
            recording = recording,
            telemetryLedger = firstRouteTelemetryLedger().copy(
                cacheTelemetry = listOf(
                    GPUCacheTelemetry(
                        cacheName = "pipeline",
                        hits = 0L,
                        misses = 0L,
                        evictions = 0L,
                        residentBytes = 0L,
                        pressureBytes = 0L,
                    ),
                ),
            ),
        )

        assertEquals(GPUValidationStatus.Incomplete, report.status)
        val lines = report.dumps.flatMap { dump -> dump.lines() }
        assertFalse(
            actual = lines.any { line -> "telemetry:GPUCacheTelemetry.pipeline" in line },
            message = "Zero aggregate pipeline cache telemetry must not become cache evidence: $lines",
        )

        val gate = GPUPromotionGateCheck().evaluate(report)
        assertFalse(gate.passed)
        assertContains(gate.missingEvidence, "pipeline-cache")
        assertContains(
            gate.diagnostics.joinToString("\n"),
            "pipeline-cache requires GPUCacheTelemetry.pipeline",
        )
    }

    /** Refused recordings expose diagnostics without creating positive native route evidence. */
    @Test
    fun `recording backed PM evidence keeps refused recording diagnostic negative`() {
        val recording = refusedFillRectRecording(recordingIdValue = "recording.refused-pm", commandIdValue = 8)
        val report = GPUValidationFixture().firstRouteRecordingPMEvidenceBundle(
            recording = recording,
            telemetryLedger = GPUTelemetryLedger.empty().recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.Route(
                    kind = GPUFirstRouteRouteKind.RefuseDiagnostic,
                    refusalCode = "unsupported.clip.complex_stack",
                ),
            ),
        )

        assertEquals(GPUValidationStatus.Incomplete, report.status)
        val lines = report.dumps.flatMap { dump -> dump.lines() }
        assertContains(
            lines,
            "routing:GPURouteDecision.Refused:recording recording.refused-pm refused:unsupported.clip.complex_stack",
        )
        assertContains(
            lines,
            "recording:GPUAnalysisDecisionDump:recording recording.refused-pm decision:refuse:analysis.fill_rect.8:unsupported.clip.complex_stack",
        )
        assertContains(
            lines,
            "recording:GPUTaskList:recording recording.refused-pm task:refused:task.refused.8:unsupported.clip.complex_stack",
        )
        assertContains(
            lines,
            "telemetry:GPUTelemetryLedger:counter:first_route.route.refusal.count:code=unsupported.clip.complex_stack:1:count",
        )
        assertFalse(
            actual = lines.any { line -> "routing:GPURouteDecision.Native" in line },
            message = "Refused recordings must not become positive native route evidence: $lines",
        )

        val gate = GPUPromotionGateCheck().evaluate(report)
        assertFalse(gate.passed)
        assertContains(gate.missingEvidence, "route")
        assertContains(
            gate.diagnostics.joinToString("\n"),
            "route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
        )
    }

    private data class SyntheticEvidence(
        val category: String,
        val entry: GPUContractDump.Entry,
    )

    /** Creates a temporary artifact root for filesystem materialization tests. */
    private fun temporaryArtifactRoot(): File =
        Files.createTempDirectory("gpu-validation-artifacts").toFile()

    /** Computes the manifest SHA-256 format for exact exported file bytes. */
    private fun ByteArray.fileBytesSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)

        return "sha256:" + digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    /** Builds an accepted first-route recording for validation-owned PM evidence tests. */
    private fun acceptedFillRectRecording(recordingIdValue: String, commandIdValue: Int): GPURecording {
        val recorder = GPURecorder(
            recordingId = GPURecordingID(recordingIdValue),
            capabilities = firstSliceCapabilities(),
        )
        recorder.record(acceptedFillRect(commandIdValue = commandIdValue))
        return recorder.close()
    }

    /** Builds a refused first-route recording for validation-owned PM evidence tests. */
    private fun refusedFillRectRecording(recordingIdValue: String, commandIdValue: Int): GPURecording {
        val recorder = GPURecorder(
            recordingId = GPURecordingID(recordingIdValue),
            capabilities = firstSliceCapabilities(),
        )
        recorder.record(
            acceptedFillRect(commandIdValue = commandIdValue).copy(
                clip = GPUClipFacts.complexStack(bounds = GPUBounds(0f, 0f, 16f, 16f)),
            ),
        )
        return recorder.close()
    }

    /** Accepted first-route command with deterministic solid material and target facts. */
    private fun acceptedFillRect(commandIdValue: Int): NormalizedDrawCommand.FillRect =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandIdValue),
            rect = GPURect(left = 2f, top = 3f, right = 18f, bottom = 21f),
            target = firstRouteTarget,
            material = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            transform = GPUTransformFacts.identity(),
            layer = GPULayerFacts.root(target = firstRouteTarget),
            source = GPUCommandSource(adapter = "validation-test", operation = "fillRect"),
        )

    /** Capability snapshot that enables the first native FillRect route. */
    private fun firstSliceCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "validation-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-fixture",
                ),
            ),
            snapshotId = "first-route-validation-test",
        )

    /** Ledger with first-route counters and pipeline-cache facts that PM evidence may expose. */
    private fun telemetryAndPipelineCacheLedger(
        routeKind: GPUFirstRouteRouteKind = GPUFirstRouteRouteKind.GPUNative,
    ): GPUTelemetryLedger =
        firstRouteTelemetryLedger(routeKind = routeKind)
            .recordCacheEvent(
                GPUCacheTelemetryEvent.pipeline(
                    result = GPUCacheEventResult.Miss,
                    keyHash = "render-pipeline:accepted",
                    subjectHash = "render-pipeline:first-fill-rect",
                ),
            )

    /** Ledger with all late-stage execution counters needed by executed PM evidence. */
    private fun executedTelemetryAndPipelineCacheLedger(): GPUTelemetryLedger =
        telemetryAndPipelineCacheLedger()
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

    /** Ledger with execution counters but without route, WGSL validation, or fallback counters. */
    private fun executedLateOnlyTelemetryAndPipelineCacheLedger(): GPUTelemetryLedger =
        GPUTelemetryLedger.empty()
            .recordCacheEvent(
                GPUCacheTelemetryEvent.pipeline(
                    result = GPUCacheEventResult.Miss,
                    keyHash = "render-pipeline:accepted",
                    subjectHash = "render-pipeline:first-fill-rect",
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

    /** Synthetic materialization evidence matching the executed first-route recording. */
    private fun materializedFirstRouteDecision(commandIdValue: Int): GPUResourceMaterializationDecision.Materialized =
        GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("webgpu.first-route.surface.executed.g1")),
            targetId = "surface.executed",
            taskIds = listOf("task.render.$commandIdValue"),
            resourcePlanLabels = listOf("webgpu.headless-target.surface"),
        )

    /** Synthetic submitted command evidence matching the executed first-route recording. */
    private fun submittedFirstRouteCommand(
        commandIdValue: Int,
        readbackRequest: GPUReadbackRequest,
    ): GPUCommandSubmission.Submitted =
        GPUCommandSubmission.Submitted(
            submissionId = "submit.executed",
            scopeLabel = "root-pass",
            deviceGeneration = GPUDeviceGeneration(1),
            targetGeneration = 1L,
            scopeLabels = listOf("root-pass"),
            taskIds = listOf("task.render.$commandIdValue"),
            passIds = listOf("pass.root.$commandIdValue"),
            resourceUsageSummary = listOf("surface.executed:copy_src,render_attachment"),
            submittedRouteCounts = mapOf("GPUNative" to 1),
            readbackRequests = listOf(readbackRequest),
        )

    /** Synthetic completed readback request for the first-route target. */
    private fun completedReadbackRequest(): GPUReadbackRequest =
        GPUReadbackRequest(
            requestId = "readback-1",
            sourceLabel = "first-route-webgpu-submit",
            boundsLabel = "0,0 16x16",
            format = "rgba8unorm",
            synchronizationLabel = "after-submit",
            expectedArtifactLabel = "first-route-fill.png",
        )

    /** Parser-backed WGSL reflection fixture for executed PM evidence tests. */
    private fun syntheticWgslReflection(): WGSLReflectionResult.Accepted =
        WGSLReflectionResult.Accepted(
            moduleHash = WGSLModuleHash("wgsl-module:synthetic-test-solid-fill"),
            bindings = listOf(
                WGSLBindingLayout(
                    group = 0,
                    binding = 0,
                    visibility = setOf("fragment"),
                    resourceKind = "uniform-buffer",
                    access = "read",
                    minBindingSize = 16L,
                    dynamicOffset = false,
                    layoutRole = "solid-color",
                    diagnosticLabel = "synthetic-test-solid-color",
                ),
            ),
            uniforms = listOf(
                WGSLUniformLayout(
                    layoutHash = "layout:synthetic-test-solid-color",
                    fields = listOf("color"),
                    fieldLayouts = listOf(
                        WGSLUniformFieldLayout(
                            name = "color",
                            type = "vec4<f32>",
                            offset = 0L,
                            sizeBytes = 16L,
                            alignment = 16,
                        ),
                    ),
                    sizeBytes = 16L,
                    alignment = 16,
                    numericRepresentation = "f32",
                ),
            ),
            storage = emptyList(),
            parserState = WGSLParserState(
                status = "parser-backed",
                toolName = "wgsl4k",
                message = "synthetic-test WGSL parsed for executed evidence",
            ),
            reflectionSource = "synthetic-test-parser-backed",
        )

    /** Ledger with first-route counters but no pipeline-cache facts. */
    private fun firstRouteTelemetryLedger(
        routeKind: GPUFirstRouteRouteKind = GPUFirstRouteRouteKind.GPUNative,
    ): GPUTelemetryLedger =
        GPUTelemetryLedger.empty()
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.CommandFamily(
                    family = GPUFirstRouteCommandFamily.Rect,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.Route(
                    kind = routeKind,
                ),
            )
            .recordFirstRouteEvent(
                GPUFirstRouteTelemetryEvent.WGSLModuleValidation(
                    outcome = GPUFirstRouteWGSLModuleValidationOutcome.Success,
                ),
            )
            .recordFirstRouteEvent(GPUFirstRouteTelemetryEvent.NegativeCPUFallbackRefusal)

    private companion object {
        /** Shared accepted target for first-route validation fixtures. */
        val firstRouteTarget = GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

        val syntheticCompleteEntries = listOf(
            SyntheticEvidence(
                category = "command",
                entry = GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "NormalizedDrawCommand.FillRect",
                    detail = "synthetic-test complete command evidence",
                ),
            ),
            SyntheticEvidence(
                category = "analysis",
                entry = GPUContractDump.Entry(
                    ownerPackage = "analysis",
                    concept = "GPUDrawAnalysisRecord",
                    detail = "synthetic-test complete analysis evidence",
                ),
            ),
            SyntheticEvidence(
                category = "route",
                entry = GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "GPURouteDecision.Native",
                    detail = "synthetic-test complete route evidence",
                ),
            ),
            SyntheticEvidence(
                category = "material",
                entry = GPUContractDump.Entry(
                    ownerPackage = "materials",
                    concept = "GPUPaintPipelinePlan",
                    detail = "synthetic-test complete material evidence",
                ),
            ),
            SyntheticEvidence(
                category = "wgsl",
                entry = GPUContractDump.Entry(
                    ownerPackage = "wgsl",
                    concept = "WGSLReflectionResult",
                    detail = "synthetic-test complete WGSL evidence",
                ),
            ),
            SyntheticEvidence(
                category = "payload",
                entry = GPUContractDump.Entry(
                    ownerPackage = "payloads",
                    concept = "GPUPayloadGatherPlan",
                    detail = "synthetic-test complete payload evidence",
                ),
            ),
            SyntheticEvidence(
                category = "pipeline-key",
                entry = GPUContractDump.Entry(
                    ownerPackage = "pipelines",
                    concept = "GPUPipelineKeyPreimage.Render",
                    detail = "synthetic-test complete pipeline key evidence",
                ),
            ),
            SyntheticEvidence(
                category = "resource-decision",
                entry = GPUContractDump.Entry(
                    ownerPackage = "resources",
                    concept = "GPUResourceMaterializationDecision.Materialized",
                    detail = "synthetic-test complete materialized resource evidence",
                ),
            ),
            SyntheticEvidence(
                category = "submission",
                entry = GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUCommandSubmission.Submitted",
                    detail = "synthetic-test complete command submission evidence",
                ),
            ),
            SyntheticEvidence(
                category = "readback",
                entry = GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUReadbackResult.Completed",
                    detail = "synthetic-test complete readback evidence",
                ),
            ),
            SyntheticEvidence(
                category = "telemetry",
                entry = GPUContractDump.Entry(
                    ownerPackage = "telemetry",
                    concept = "GPUTelemetryLedger",
                    detail = "synthetic-test complete telemetry evidence",
                ),
            ),
            SyntheticEvidence(
                category = "pipeline-cache",
                entry = GPUContractDump.Entry(
                    ownerPackage = "telemetry",
                    concept = "GPUCacheTelemetry.pipeline",
                    detail = "synthetic-test complete pipeline cache evidence",
                ),
            ),
            SyntheticEvidence(
                category = "negative-cpu-fallback",
                entry = GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "NegativeCPUFallbackRefusal",
                    detail = "synthetic-test negative CPU fallback refusal evidence",
                ),
            ),
            SyntheticEvidence(
                category = "unsupported-route-refusals",
                entry = GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "UnsupportedRouteFamilyRefusal",
                    detail = "synthetic-test unsupportedFamilies=perspective-transform,singular-transform," +
                        "unsupported-target-format,unsupported-blend,non-simple-clip,layer-filter-destination-read," +
                        "missing-capability,wgsl-validation-or-abi-mismatch diagnostics=none",
                ),
            ),
        )
    }
}
