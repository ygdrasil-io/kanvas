package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawInvocation
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCacheKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCreationPlan
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Verifies execution-context refusal, target validation, submissions, and readback evidence. */
class GPUExecutionContextTest {
    /** Ensures an unconfigured execution double refuses submission and skips readback evidence. */
    @Test
    fun `execution context test double refuses submit and skips readback by default`() {
        val context = RefusingExecutionContextDouble()

        val submission = context.submit(GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("target-write")))
        val readback = context.readback(readbackRequest())

        val refused = assertIs<GPUCommandSubmission.Refused>(submission)
        assertEquals("unsupported.execution.render_unavailable", refused.diagnostic.code)
        assertEquals(
            listOf(
                "execution.submission:refused scope=root-pass class=Render code=unsupported.execution.render_unavailable terminal=true",
                "execution.diagnostic code=unsupported.execution.render_unavailable stage=submit terminal=true facts=commandClass=Render;scopeLabel=root-pass",
            ),
            refused.dumpLines(),
        )

        val skipped = assertIs<GPUReadbackResult.Skipped>(readback)
        assertEquals("unsupported.execution.readback_unavailable", skipped.reasonCode)
        assertEquals("unsupported.execution.readback_unavailable", skipped.diagnostics.single().code)
        assertEquals(
            listOf(
                "execution.readback:skipped request=readback-1 source=pm-evidence bounds=0,0 64x64 format=rgba8unorm sync=after-submit expectedArtifact=first-route.png failureReason=none reason=unsupported.execution.readback_unavailable diagnostics=unsupported.execution.readback_unavailable",
                "execution.diagnostic code=unsupported.execution.readback_unavailable stage=readback terminal=true facts=boundsLabel=0,0 64x64;failureReason=none;format=rgba8unorm;requestId=readback-1;sourceLabel=pm-evidence",
            ),
            skipped.dumpLines(),
        )
    }

    /** Ensures refused submission headers use diagnostic snapshots, not mutable input maps. */
    @Test
    fun `refused command submission dump snapshots diagnostic facts`() {
        val facts = mutableMapOf(
            "commandClass" to "Render",
            "scopeLabel" to "root-pass",
        )
        val submission = GPUCommandSubmission.Refused(
            GPUExecutionDiagnostic(
                code = "unsupported.execution.render_unavailable",
                stage = "submit",
                message = "Render submission unavailable.",
                terminal = true,
                facts = facts,
            ),
        )

        facts["commandClass"] = "Compute"
        facts["scopeLabel"] = "mutated-pass"

        assertEquals(
            listOf(
                "execution.submission:refused scope=root-pass class=Render code=unsupported.execution.render_unavailable terminal=true",
                "execution.diagnostic code=unsupported.execution.render_unavailable stage=submit terminal=true facts=commandClass=Render;scopeLabel=root-pass",
            ),
            submission.dumpLines(),
        )
    }

    /** Ensures readback-capable but unconfigured contexts fail at readback, not submit. */
    @Test
    fun `readback capable unconfigured context emits readback stage diagnostic`() {
        val context = ReadbackCapableUnconfiguredContextDouble()
        val request = readbackRequest(
            requestId = "readback-configured-capability",
            failureReason = "backend-not-configured",
        )

        val readback = context.readback(request)

        val skipped = assertIs<GPUReadbackResult.Skipped>(readback)
        assertEquals("unsupported.execution.readback_unconfigured", skipped.reasonCode)
        assertEquals("readback", skipped.diagnostics.single().stage)
        assertEquals(
            listOf(
                "execution.readback:skipped request=readback-configured-capability source=pm-evidence bounds=0,0 64x64 format=rgba8unorm sync=after-submit expectedArtifact=first-route.png failureReason=backend-not-configured reason=unsupported.execution.readback_unconfigured diagnostics=unsupported.execution.readback_unconfigured",
                "execution.diagnostic code=unsupported.execution.readback_unconfigured stage=readback terminal=true facts=boundsLabel=0,0 64x64;failureReason=backend-not-configured;format=rgba8unorm;requestId=readback-configured-capability;sourceLabel=pm-evidence",
            ),
            skipped.dumpLines(),
        )
    }

    /** Ensures render capability facts do not turn an unconfigured context into fake success. */
    @Test
    fun `render capable unconfigured context refuses submit instead of submitting`() {
        val context = RenderCapableUnconfiguredContextDouble()

        val submission = context.submit(GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("target-write")))

        val refused = assertIs<GPUCommandSubmission.Refused>(submission)
        assertEquals("unsupported.execution.context_unconfigured", refused.diagnostic.code)
        assertEquals("submit", refused.diagnostic.stage)
        assertEquals(
            mapOf(
                "commandClass" to "Render",
                "scopeLabel" to "root-pass",
            ),
            refused.diagnostic.dumpFactsSnapshot,
        )
        assertEquals(
            listOf(
                "execution.submission:refused scope=root-pass class=Render code=unsupported.execution.context_unconfigured terminal=true",
                "execution.diagnostic code=unsupported.execution.context_unconfigured stage=submit terminal=true facts=commandClass=Render;scopeLabel=root-pass",
            ),
            refused.dumpLines(),
        )
    }

    /** Ensures target descriptors are validated before render submission can be considered. */
    @Test
    fun `target descriptor validation reports invalid shape and missing usage`() {
        val descriptor = GPUSurfaceTargetDescriptor(
            width = 0,
            height = 64,
            colorFormat = "",
            surfaceBacked = true,
            targetGeneration = 4,
            usageLabels = setOf("texture_binding"),
        )

        val diagnostics = descriptor.validateForUse(
            requiredUsageLabels = setOf("render_attachment", "copy_src"),
            targetLabel = "surface",
        )

        assertEquals(
            listOf(
                "invalid.execution.target_descriptor",
                "unsupported.execution.usage_missing",
            ),
            diagnostics.map { it.code },
        )
        assertContains(diagnostics.joinToString("\n") { it.message }, "render_attachment")
    }

    /** Ensures preflight preserves refused and deferred materialization evidence without submitting. */
    @Test
    fun `execution preflight reports materialization refused and deferred without submitting`() {
        val context = RenderCapableUnconfiguredContextDouble()
        val refusedMaterialization = GPUResourceMaterializationDecision.Refused(
            diagnostic = GPUResourceDiagnostic.pipelineCreationFailure(
                resourceLabel = "pipeline.fill-solid",
                reason = "blend-state-unavailable",
            ),
            targetId = "surface",
            taskIds = listOf("task-fill"),
            resourcePlanLabels = listOf("pipeline.fill-solid"),
        )
        val deferredMaterialization = GPUResourceMaterializationDecision.Deferred(
            reasonCode = "deferred.resource.promise_unfulfilled",
            targetId = "surface",
            taskIds = listOf("task-image"),
            resourcePlanLabels = listOf("promise.image"),
        )

        val refused = context.preflight(
            GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("target-write")),
                materializationDecision = refusedMaterialization,
                passIds = listOf("pass-root"),
                taskIds = listOf("task-fill"),
            ),
        )
        val deferred = context.preflight(
            GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "image-pass", useTokenLabels = listOf("target-write")),
                materializationDecision = deferredMaterialization,
                passIds = listOf("pass-image"),
                taskIds = listOf("task-image"),
            ),
        )

        assertFalse(refused.readyForSubmission)
        assertFalse(deferred.readyForSubmission)
        assertEquals(
            listOf("capability.pipeline.missing_feature", "unsupported.execution.context_unconfigured"),
            refused.diagnostics.map { it.code }.sorted(),
        )
        assertEquals(
            listOf("deferred.resource.promise_unfulfilled", "unsupported.execution.context_unconfigured"),
            deferred.diagnostics.map { it.code }.sorted(),
        )
        assertEquals(true, deferred.diagnostics.first { it.code == "deferred.resource.promise_unfulfilled" }.terminal)
        assertEquals(
            listOf(
                "execution.preflight:refused scope=root-pass class=Render target=none tasks=task-fill passes=pass-root diagnostics=capability.pipeline.missing_feature,unsupported.execution.context_unconfigured",
                "execution.diagnostic code=capability.pipeline.missing_feature stage=materialization terminal=true facts=materializationOutcome=refused;reason=blend-state-unavailable;resourceLabel=pipeline.fill-solid;resourcePlanLabels=pipeline.fill-solid;targetId=surface;taskIds=task-fill",
                "execution.diagnostic code=unsupported.execution.context_unconfigured stage=submit terminal=true facts=commandClass=Render;scopeLabel=root-pass",
            ),
            refused.dumpLines(),
        )
        assertFalse((refused.dumpLines() + deferred.dumpLines()).joinToString("\n").contains("submission:submitted"))
    }

    /** Ensures preflight reports target shape, usage, and device-generation mismatches. */
    @Test
    fun `execution preflight catches target descriptor and device generation diagnostics`() {
        val context = RenderCapableUnconfiguredContextDouble()
        val target = GPUSurfaceTarget(
            targetId = "surface",
            descriptor = GPUSurfaceTargetDescriptor(
                width = 0,
                height = 64,
                colorFormat = "",
                surfaceBacked = true,
                targetGeneration = 4,
                usageLabels = setOf("texture_binding"),
            ),
            deviceGeneration = GPUDeviceGeneration(10),
        )

        val report = context.preflight(
            GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("target-write")),
                target = target,
                requiredTargetUsageLabels = setOf("render_attachment", "copy_src"),
                passIds = listOf("pass-root"),
                taskIds = listOf("task-fill"),
            ),
        )

        assertFalse(report.readyForSubmission)
        assertEquals(
            listOf(
                "invalid.execution.target_descriptor",
                "unsupported.execution.context_unconfigured",
                "unsupported.execution.device_generation_mismatch",
                "unsupported.execution.usage_missing",
            ),
            report.diagnostics.map { it.code }.sorted(),
        )
        assertContains(report.dumpLines().joinToString("\n"), "expectedDeviceGeneration=11")
        assertContains(report.dumpLines().joinToString("\n"), "actualDeviceGeneration=10")
    }

    /** Ensures backend submit handoff stays refusal-first until a real backend overrides it. */
    @Test
    fun `first route render submit request refuses by default with vertical evidence facts`() {
        val context = RenderCapableUnconfiguredContextDouble()
        val request = firstRouteRenderSubmitRequest()

        val submission = context.submit(request)

        val refused = assertIs<GPUCommandSubmission.Refused>(submission)
        assertEquals("unsupported.execution.context_unconfigured", refused.diagnostic.code)
        assertEquals(
            mapOf(
                "commandClass" to "Render",
                "materializedResourceCount" to "1",
                "passIds" to "pass-root",
                "payloadCommands" to "42",
                "pipelineCacheKey" to "render-pipeline:first-fill-rect",
                "pipelineKeys" to "pipeline-key:first-fill-rect",
                "readbackRequests" to "readback-1",
                "resourcePlans" to "surface",
                "scopeLabel" to "root-pass",
                "targetId" to "surface",
                "tasks" to "task-fill",
            ),
            refused.diagnostic.dumpFactsSnapshot,
        )
        assertEquals(
            listOf(
                "execution.submission:refused scope=root-pass class=Render code=unsupported.execution.context_unconfigured terminal=true",
                "execution.diagnostic code=unsupported.execution.context_unconfigured stage=submit terminal=true facts=commandClass=Render;materializedResourceCount=1;passIds=pass-root;payloadCommands=42;pipelineCacheKey=render-pipeline:first-fill-rect;pipelineKeys=pipeline-key:first-fill-rect;readbackRequests=readback-1;resourcePlans=surface;scopeLabel=root-pass;targetId=surface;tasks=task-fill",
            ),
            refused.dumpLines(),
        )
        assertFalse(refused.dumpLines().joinToString("\n").contains("submission:submitted"))
    }

    /** Ensures submit handoff facts snapshot caller-owned collections before backend work. */
    @Test
    fun `first route render submit request snapshots mutable pass payload and readback inputs`() {
        val invocation = drawInvocation(commandIdValue = 42)
        val invocations = mutableListOf(invocation)
        val pipelineKeys = mutableListOf("pipeline-key:first-fill-rect")
        val pass = drawPass(invocations = invocations, pipelineKeys = pipelineKeys)
        val payloadRefs = mutableListOf(GPUDrawPayloadRef(commandIdValue = 42, renderStepIdentity = "fill-rect"))
        val readbackRequests = mutableListOf(readbackRequest())
        val request = firstRouteRenderSubmitRequest(
            pass = pass,
            payloadRefs = payloadRefs,
            readbackRequests = readbackRequests,
        )

        invocations += drawInvocation(commandIdValue = 99)
        pipelineKeys += "pipeline-key:mutated"
        payloadRefs += GPUDrawPayloadRef(commandIdValue = 99, renderStepIdentity = "fill-rect")
        readbackRequests += readbackRequest(requestId = "readback-mutated")

        val refused = assertIs<GPUCommandSubmission.Refused>(
            RenderCapableUnconfiguredContextDouble().submit(request),
        )
        val dump = refused.dumpLines().joinToString("\n")

        assertContains(dump, "pipelineKeys=pipeline-key:first-fill-rect")
        assertContains(dump, "payloadCommands=42")
        assertContains(dump, "readbackRequests=readback-1")
        assertFalse(dump.contains("pipeline-key:mutated"))
        assertFalse(dump.contains("payloadCommands=42,99"))
        assertFalse(dump.contains("readback-mutated"))
    }

    /** Ensures backend-visible submit request properties are as immutable as their dumps. */
    @Test
    fun `first route render submit request snapshots backend visible properties`() {
        val invocation = drawInvocation(commandIdValue = 42)
        val invocations = mutableListOf(invocation)
        val pipelineKeys = mutableListOf("pipeline-key:first-fill-rect")
        val pass = drawPass(invocations = invocations, pipelineKeys = pipelineKeys)
        val payloadRefs = mutableListOf(GPUDrawPayloadRef(commandIdValue = 42, renderStepIdentity = "fill-rect"))
        val readbackRequests = mutableListOf(readbackRequest())
        val request = firstRouteRenderSubmitRequest(
            pass = pass,
            payloadRefs = payloadRefs,
            readbackRequests = readbackRequests,
        )

        invocations += drawInvocation(commandIdValue = 99)
        pipelineKeys += "pipeline-key:mutated"
        payloadRefs += GPUDrawPayloadRef(commandIdValue = 99, renderStepIdentity = "fill-rect")
        readbackRequests += readbackRequest(requestId = "readback-mutated")

        assertEquals(listOf(invocation), request.pass.invocations)
        assertEquals(listOf("pipeline-key:first-fill-rect"), request.pass.pipelineKeys)
        assertEquals(listOf(42), request.payloadRefs.map { ref -> ref.commandIdValue })
        assertEquals(listOf("readback-1"), request.readbackRequests.map { readback -> readback.requestId })
    }

    /** Ensures preflight request properties snapshot caller-owned collections for backend readers. */
    @Test
    fun `execution preflight request snapshots backend visible collection properties`() {
        val requiredUsage = mutableSetOf("render_attachment")
        val taskIds = mutableListOf("task-fill")
        val passIds = mutableListOf("pass-root")
        val request = GPUExecutionPreflightRequest(
            scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("surface-write")),
            requiredTargetUsageLabels = requiredUsage,
            taskIds = taskIds,
            passIds = passIds,
        )

        requiredUsage += "copy_src"
        taskIds += "task-mutated"
        passIds += "pass-mutated"

        assertEquals(setOf("render_attachment"), request.requiredTargetUsageLabels)
        assertEquals(listOf("task-fill"), request.taskIds)
        assertEquals(listOf("pass-root"), request.passIds)
        val dump = RenderCapableUnconfiguredContextDouble().preflight(request).dumpLines().joinToString("\n")
        assertContains(dump, "tasks=task-fill")
        assertContains(dump, "passes=pass-root")
        assertFalse(dump.contains("task-mutated"))
        assertFalse(dump.contains("pass-mutated"))
    }

    /** Ensures a submit handoff cannot bind a materialized resource to a different target. */
    @Test
    fun `first route render submit request rejects mismatched preflight and materialization targets`() {
        val materialization = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("surface-ref")),
            targetId = "surface-a",
            taskIds = listOf("task-fill"),
            resourcePlanLabels = listOf("surface-a"),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            GPUFirstRouteRenderSubmitRequest(
                preflightRequest = GPUExecutionPreflightRequest(
                    scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("surface-write")),
                    target = GPUSurfaceTarget(
                        targetId = "surface-b",
                        descriptor = GPUSurfaceTargetDescriptor(
                            width = 64,
                            height = 64,
                            colorFormat = "rgba8unorm",
                            surfaceBacked = true,
                            usageLabels = setOf("render_attachment"),
                        ),
                        deviceGeneration = GPUDeviceGeneration(11),
                    ),
                    materializationDecision = materialization,
                    taskIds = listOf("task-fill"),
                    passIds = listOf("pass-root"),
                ),
                pass = drawPass(),
                materialization = materialization,
                pipelinePlan = renderPipelinePlan(),
                payloadRefs = listOf(GPUDrawPayloadRef(commandIdValue = 42, renderStepIdentity = "fill-rect")),
            )
        }

        assertContains(failure.message.orEmpty(), "preflight target must match materialization target")
    }

    /** Ensures terminal preflight diagnostics keep their own facts after submit evidence is attached. */
    @Test
    fun `first route render submit keeps diagnostic facts when submit facts share keys`() {
        val request = firstRouteRenderSubmitRequest()

        val refused = assertIs<GPUCommandSubmission.Refused>(
            TerminalFactCollisionContextDouble().submit(request),
        )

        assertEquals("diagnostic-target", refused.diagnostic.dumpFactsSnapshot["targetId"])
        assertEquals("1", refused.diagnostic.dumpFactsSnapshot["materializedResourceCount"])
        assertEquals("render-pipeline:first-fill-rect", refused.diagnostic.dumpFactsSnapshot["pipelineCacheKey"])
    }

    /** Ensures render preflight reports empty pass and scope facts before any backend work. */
    @Test
    fun `execution preflight reports empty render pass and scope facts`() {
        val context = RenderCapableUnconfiguredContextDouble()

        val report = context.preflight(
            GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "empty-pass", useTokenLabels = emptyList()),
            ),
        )

        assertFalse(report.readyForSubmission)
        assertEquals(
            listOf(
                "diagnostic.execution.empty_render_pass",
                "diagnostic.execution.empty_render_scope",
                "unsupported.execution.context_unconfigured",
            ),
            report.diagnostics.map { it.code }.sorted(),
        )
        assertFalse(report.diagnostics.first { it.code == "diagnostic.execution.empty_render_pass" }.terminal)
        assertFalse(report.diagnostics.first { it.code == "diagnostic.execution.empty_render_scope" }.terminal)
        assertContains(report.dumpLines().joinToString("\n"), "useTokenLabels=none")
        assertContains(report.dumpLines().joinToString("\n"), "passIds=none")
    }

    /** Ensures refused materialization diagnostics are converted from immutable snapshots. */
    @Test
    fun `execution preflight snapshots refused resource diagnostics and preserves secondary diagnostics`() {
        val context = RenderCapableUnconfiguredContextDouble()
        val mutableFacts = mutableMapOf("reason" to "original")
        val primary = GPUResourceDiagnostic(
            code = "unstable.resource.primary",
            resourceLabel = "pipeline.fill",
            message = "Primary refusal.",
            terminal = true,
            facts = mutableFacts,
        )
        val secondary = GPUResourceDiagnostic(
            code = "unstable.resource.secondary",
            resourceLabel = "surface",
            message = "Secondary refusal.",
            terminal = true,
            facts = mapOf("rank" to "2"),
        )
        val materialization = GPUResourceMaterializationDecision.Refused(
            diagnostic = primary,
            targetId = "surface",
            taskIds = listOf("task-fill"),
            resourcePlanLabels = listOf("pipeline.fill"),
            diagnostics = listOf(primary, secondary),
        )

        mutableFacts["reason"] = "mutated"

        val report = context.preflight(
            GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("target-write")),
                materializationDecision = materialization,
                passIds = listOf("pass-root"),
                taskIds = listOf("task-fill"),
            ),
        )

        assertEquals(
            listOf(
                "unstable.resource.primary",
                "unstable.resource.secondary",
                "unsupported.execution.context_unconfigured",
            ),
            report.diagnostics.map { diagnostic -> diagnostic.code }.sorted(),
        )
        val dump = report.dumpLines().joinToString("\n")
        assertContains(dump, "reason=original")
        assertContains(dump, "unstable.resource.secondary")
        assertFalse(dump.contains("reason=mutated"))
    }

    /** Ensures public command scopes snapshot caller-owned label lists. */
    @Test
    fun `command scopes snapshot mutable use token labels`() {
        val renderLabels = mutableListOf("render-write")
        val computeLabels = mutableListOf("compute-read")
        val copyLabels = mutableListOf("copy-upload")

        val render = GPUCommandScope.Render(label = "render", useTokenLabels = renderLabels)
        val compute = GPUCommandScope.Compute(label = "compute", useTokenLabels = computeLabels)
        val copy = GPUCommandScope.CopyUpload(label = "copy", useTokenLabels = copyLabels)

        renderLabels += "render-mutated"
        computeLabels.clear()
        copyLabels[0] = "copy-mutated"

        assertEquals(listOf("render-write"), render.useTokenLabels)
        assertEquals(listOf("compute-read"), compute.useTokenLabels)
        assertEquals(listOf("copy-upload"), copy.useTokenLabels)

        val report = RenderCapableUnconfiguredContextDouble().preflight(
            GPUExecutionPreflightRequest(
                scope = render,
                passIds = listOf("pass-root"),
            ),
        )

        assertFalse(report.dumpLines().joinToString("\n").contains("render-mutated"))
    }

    /** Ensures submitted command records carry target, scope, resource, route, and readback facts. */
    @Test
    fun `command submission object records scope target resources routes and readbacks`() {
        val request = readbackRequest()

        val submission = GPUCommandSubmission.Submitted(
            submissionId = "submit-1",
            scopeLabel = "root-pass",
            scopeLabels = listOf("root-pass"),
            deviceGeneration = GPUDeviceGeneration(11),
            targetGeneration = 4,
            taskIds = listOf("task-fill-rect"),
            passIds = listOf("pass-root"),
            resourceUsageSummary = listOf("surface:render_attachment"),
            submittedRouteCounts = mapOf("GPUNative" to 1),
            readbackRequests = listOf(request),
        )

        assertEquals(4, submission.targetGeneration)
        assertEquals(listOf("root-pass"), submission.scopeLabels)
        assertEquals(listOf("surface:render_attachment"), submission.resourceUsageSummary)
        assertEquals(listOf(request), submission.readbackRequests)
        assertEquals(
            listOf(
                "execution.submission:submitted id=submit-1 deviceGeneration=11 targetGeneration=4 scopes=root-pass tasks=task-fill-rect passes=pass-root resources=surface:render_attachment routes=GPUNative=1 readbacks=readback-1 diagnostics=none",
            ),
            submission.dumpLines(),
        )
    }

    /** Ensures execution sequence fields preserve construction order in submission dumps. */
    @Test
    fun `submitted command dump preserves execution sequence order`() {
        val submission = GPUCommandSubmission.Submitted(
            submissionId = "submit-ordered",
            scopeLabel = "scope-z",
            scopeLabels = listOf("scope-z", "scope-a", "scope-m"),
            deviceGeneration = GPUDeviceGeneration(11),
            targetGeneration = 4,
            taskIds = listOf("task-z", "task-a"),
            passIds = listOf("pass-z", "pass-a"),
            resourceUsageSummary = listOf("res-z", "res-a"),
            submittedRouteCounts = mapOf("Zeta" to 2, "Alpha" to 1),
            readbackRequests = listOf(
                readbackRequest(requestId = "readback-z"),
                readbackRequest(requestId = "readback-a"),
            ),
        )

        assertEquals(
            listOf(
                "execution.submission:submitted id=submit-ordered deviceGeneration=11 targetGeneration=4 scopes=scope-z,scope-a,scope-m tasks=task-z,task-a passes=pass-z,pass-a resources=res-a,res-z routes=Alpha=1,Zeta=2 readbacks=readback-z,readback-a diagnostics=none",
            ),
            submission.dumpLines(),
        )
    }

    /** Ensures mutable submission and diagnostic inputs cannot rewrite dump evidence. */
    @Test
    fun `submitted command dump snapshots mutable submission readback and diagnostic inputs`() {
        val diagnosticFacts = mutableMapOf("rank" to "1")
        val diagnostics = mutableListOf(
            GPUExecutionDiagnostic(
                code = "unstable.execution",
                stage = "submit",
                message = "Original diagnostic.",
                terminal = true,
                facts = diagnosticFacts,
            ),
        )
        val scopeLabels = mutableListOf("scope-b")
        val taskIds = mutableListOf("task-b")
        val passIds = mutableListOf("pass-b")
        val resourceUsageSummary = mutableListOf("res-b")
        val submittedRouteCounts = mutableMapOf("route-b" to 1)
        val readbackRequests = mutableListOf(readbackRequest(requestId = "readback-b"))
        val submission = GPUCommandSubmission.Submitted(
            submissionId = "submit-snapshot",
            scopeLabel = "scope-b",
            scopeLabels = scopeLabels,
            deviceGeneration = GPUDeviceGeneration(11),
            targetGeneration = 4,
            taskIds = taskIds,
            passIds = passIds,
            resourceUsageSummary = resourceUsageSummary,
            submittedRouteCounts = submittedRouteCounts,
            readbackRequests = readbackRequests,
            diagnostics = diagnostics,
        )

        diagnosticFacts["rank"] = "2"
        diagnostics += GPUExecutionDiagnostic(
            code = "mutated.execution",
            stage = "submit",
            message = "Mutated diagnostic.",
            terminal = true,
        )
        scopeLabels += "scope-a"
        taskIds += "task-a"
        passIds += "pass-a"
        resourceUsageSummary += "res-a"
        submittedRouteCounts["route-a"] = 2
        readbackRequests += readbackRequest(requestId = "readback-a")

        val lines = submission.dumpLines()

        assertEquals(
            listOf(
                "execution.submission:submitted id=submit-snapshot deviceGeneration=11 targetGeneration=4 scopes=scope-b tasks=task-b passes=pass-b resources=res-b routes=route-b=1 readbacks=readback-b diagnostics=unstable.execution",
                "execution.diagnostic code=unstable.execution stage=submit terminal=true facts=rank=1",
            ),
            lines,
        )
        assertFalse(lines.joinToString("\n").contains("readback-a"))
    }

    /** Ensures skipped readback evidence includes a stable diagnostic payload. */
    @Test
    fun `readback skipped result carries diagnostic evidence`() {
        val request = readbackRequest()
        val diagnostic = GPUExecutionDiagnostic.readbackUnavailable(
            request = request,
            stage = "unit-test",
        )

        val result = GPUReadbackResult.Skipped(
            request = request,
            reasonCode = diagnostic.code,
            diagnostics = listOf(diagnostic),
        )

        assertEquals("unsupported.execution.readback_unavailable", result.reasonCode)
        assertEquals("unit-test", result.diagnostics.single().stage)
        assertContains(result.diagnostics.single().message, "pm-evidence")
    }

    /** Ensures failed command submissions carry stable diagnostic facts for PM reports. */
    @Test
    fun `failed command submission dump includes diagnostic code stage and facts`() {
        val submission = GPUCommandSubmission.Failed(
            GPUExecutionDiagnostic(
                code = "capability.pipeline.missing_feature",
                stage = "submit",
                message = "Pipeline creation failed for root-pass.",
                terminal = true,
                facts = mapOf(
                    "scopeLabel" to "root-pass",
                    "taskId" to "task-fill-rect",
                ),
            ),
        )

        assertEquals(
            listOf(
                "execution.submission:failed code=capability.pipeline.missing_feature stage=submit terminal=true facts=scopeLabel=root-pass;taskId=task-fill-rect",
                "execution.diagnostic code=capability.pipeline.missing_feature stage=submit terminal=true facts=scopeLabel=root-pass;taskId=task-fill-rect",
            ),
            submission.dumpLines(),
        )
    }

    /** Ensures readback result dumps cover completed and refused late outcomes. */
    @Test
    fun `readback result dumps include completed and refused evidence`() {
        val request = readbackRequest()
        val completed = GPUReadbackResult.Completed(
            request = request,
            payloadHash = "sha256:pm-readback",
            byteCount = 16384,
        )
        val refused = GPUReadbackResult.Refused(
            request = request,
            diagnostic = GPUExecutionDiagnostic(
                code = "unsupported.execution.readback_unavailable",
                stage = "readback",
                message = "Readback unavailable.",
                terminal = true,
                facts = mapOf("requestId" to request.requestId),
            ),
        )

        assertEquals(
            listOf(
                "execution.readback:completed request=readback-1 source=pm-evidence bounds=0,0 64x64 format=rgba8unorm sync=after-submit expectedArtifact=first-route.png failureReason=none bytes=16384 payloadHash=sha256:pm-readback diagnostics=none",
            ),
            completed.dumpLines(),
        )
        assertEquals(
            listOf(
                "execution.readback:refused request=readback-1 source=pm-evidence bounds=0,0 64x64 format=rgba8unorm sync=after-submit expectedArtifact=first-route.png failureReason=none code=unsupported.execution.readback_unavailable terminal=true",
                "execution.diagnostic code=unsupported.execution.readback_unavailable stage=readback terminal=true facts=requestId=readback-1",
            ),
            refused.dumpLines(),
        )
    }

    /** Ensures readback failure reasons are limited to skipped and refused outcomes. */
    @Test
    fun `readback result dumps include request failure reasons only when bytes are absent`() {
        val completedFailure = assertFailsWith<IllegalArgumentException> {
            GPUReadbackResult.Completed(
                request = readbackRequest(requestId = "readback-completed", failureReason = "late-hash-only"),
                payloadHash = "sha256:pm-readback",
                byteCount = 16384,
            )
        }
        val skippedRequest = readbackRequest(requestId = "readback-skipped", failureReason = "policy-skip")
        val skippedDiagnostic = GPUExecutionDiagnostic.readbackUnavailable(
            request = skippedRequest,
            stage = "readback",
        )
        val skipped = GPUReadbackResult.Skipped(
            request = skippedRequest,
            reasonCode = skippedDiagnostic.code,
            diagnostics = listOf(skippedDiagnostic),
        )
        val refused = GPUReadbackResult.Refused(
            request = readbackRequest(requestId = "readback-refused", failureReason = "backend-refused"),
            diagnostic = GPUExecutionDiagnostic(
                code = "unsupported.execution.readback_unavailable",
                stage = "readback",
                message = "Readback unavailable.",
                terminal = true,
                facts = mapOf("requestId" to "readback-refused"),
            ),
        )

        val lines = (skipped.dumpLines() + refused.dumpLines()).joinToString("\n")

        assertContains(completedFailure.message.orEmpty(), "failureReason must be null")
        assertContains(lines, "request=readback-skipped")
        assertContains(lines, "failureReason=policy-skip")
        assertContains(lines, "facts=boundsLabel=0,0 64x64;failureReason=policy-skip;format=rgba8unorm;requestId=readback-skipped;sourceLabel=pm-evidence")
        assertContains(lines, "request=readback-refused")
        assertContains(lines, "failureReason=backend-refused")
    }

    /** Ensures mutable readback diagnostic inputs cannot rewrite dump evidence. */
    @Test
    fun `readback result dump snapshots mutable diagnostics`() {
        val diagnosticFacts = mutableMapOf("rank" to "1")
        val diagnostics = mutableListOf(
            GPUExecutionDiagnostic(
                code = "unstable.readback",
                stage = "readback",
                message = "Original diagnostic.",
                terminal = true,
                facts = diagnosticFacts,
            ),
        )
        val result = GPUReadbackResult.Skipped(
            request = readbackRequest(requestId = "readback-snapshot"),
            reasonCode = "unstable.readback",
            diagnostics = diagnostics,
        )

        diagnosticFacts["rank"] = "2"
        diagnostics += GPUExecutionDiagnostic(
            code = "mutated.readback",
            stage = "readback",
            message = "Mutated diagnostic.",
            terminal = true,
        )

        assertEquals(
            listOf(
                "execution.readback:skipped request=readback-snapshot source=pm-evidence bounds=0,0 64x64 format=rgba8unorm sync=after-submit expectedArtifact=first-route.png failureReason=none reason=unstable.readback diagnostics=unstable.readback",
                "execution.diagnostic code=unstable.readback stage=readback terminal=true facts=rank=1",
            ),
            result.dumpLines(),
        )
    }

    /** Ensures diagnostics with identical code and stage use a complete canonical sort key. */
    @Test
    fun `execution diagnostics sort by complete canonical key`() {
        val later = GPUExecutionDiagnostic(
            code = "same.execution",
            stage = "submit",
            message = "zeta",
            terminal = true,
            facts = mapOf("rank" to "2"),
        )
        val earlier = GPUExecutionDiagnostic(
            code = "same.execution",
            stage = "submit",
            message = "alpha",
            terminal = true,
            facts = mapOf("rank" to "1"),
        )
        val submission = GPUCommandSubmission.Submitted(
            submissionId = "submit-diagnostics",
            scopeLabel = "scope",
            deviceGeneration = GPUDeviceGeneration(11),
            diagnostics = listOf(later, earlier),
        )

        assertEquals(
            listOf(
                "execution.submission:submitted id=submit-diagnostics deviceGeneration=11 targetGeneration=0 scopes=scope tasks=none passes=none resources=none routes=none readbacks=none diagnostics=same.execution,same.execution",
                "execution.diagnostic code=same.execution stage=submit terminal=true facts=rank=1",
                "execution.diagnostic code=same.execution stage=submit terminal=true facts=rank=2",
            ),
            submission.dumpLines(),
        )
    }

    /** Creates a readback request used by execution tests. */
    private fun readbackRequest(
        requestId: String = "readback-1",
        failureReason: String? = null,
    ): GPUReadbackRequest =
        GPUReadbackRequest(
            requestId = requestId,
            sourceLabel = "pm-evidence",
            boundsLabel = "0,0 64x64",
            format = "rgba8unorm",
            synchronizationLabel = "after-submit",
            expectedArtifactLabel = "first-route.png",
            failureReason = failureReason,
        )

    /** Creates a valid first-route submit request for default-refusal tests. */
    private fun firstRouteRenderSubmitRequest(
        pass: GPUDrawPass = drawPass(),
        payloadRefs: List<GPUDrawPayloadRef> = listOf(
            GPUDrawPayloadRef(commandIdValue = 42, renderStepIdentity = "fill-rect"),
        ),
        readbackRequests: List<GPUReadbackRequest> = listOf(readbackRequest()),
    ): GPUFirstRouteRenderSubmitRequest {
        val materialization = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("surface-ref")),
            targetId = "surface",
            taskIds = listOf("task-fill"),
            resourcePlanLabels = listOf("surface"),
        )
        return GPUFirstRouteRenderSubmitRequest(
            preflightRequest = GPUExecutionPreflightRequest(
                scope = GPUCommandScope.Render(label = "root-pass", useTokenLabels = listOf("surface-write")),
                target = GPUSurfaceTarget(
                    targetId = "surface",
                    descriptor = GPUSurfaceTargetDescriptor(
                        width = 64,
                        height = 64,
                        colorFormat = "rgba8unorm",
                        surfaceBacked = true,
                        targetGeneration = 4,
                        usageLabels = setOf("render_attachment", "copy_src"),
                    ),
                    deviceGeneration = GPUDeviceGeneration(11),
                ),
                requiredTargetUsageLabels = setOf("render_attachment", "copy_src"),
                materializationDecision = materialization,
                taskIds = listOf("task-fill"),
                passIds = listOf(pass.passId),
            ),
            pass = pass,
            materialization = materialization,
            pipelinePlan = renderPipelinePlan(),
            payloadRefs = payloadRefs,
            readbackRequests = readbackRequests,
        )
    }

    /** Creates an accepted draw pass shape used by first-route submit tests. */
    private fun drawPass(
        invocations: List<GPUDrawInvocation> = listOf(drawInvocation()),
        pipelineKeys: List<String> = listOf("pipeline-key:first-fill-rect"),
    ): GPUDrawPass =
        GPUDrawPass(
            passId = "pass-root",
            targetStateHash = "target-state:surface",
            layerScopeId = "root",
            loadStoreLabel = "load.store",
            invocations = invocations,
            pipelineKeys = pipelineKeys,
            barriers = emptyList(),
        )

    /** Creates one first-route draw invocation for submit handoff tests. */
    private fun drawInvocation(commandIdValue: Int = 42): GPUDrawInvocation =
        GPUDrawInvocation(
            commandIdValue = commandIdValue,
            analysisRecordId = "analysis-fill",
            renderStepIndex = 0,
            renderStepId = GPURenderStepID("fill-rect"),
            role = "fill",
            layerScopeId = "root",
            sortKey = commandIdValue.toLong(),
            pipelineKeyHash = "pipeline-key:first-fill-rect",
            boundsHash = "bounds:first-fill-rect",
            scissorBoundsHash = "clip:wide-open",
            originalPaintOrder = 0,
        )

    /** Creates a render pipeline creation plan without backend handles. */
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
                moduleHash = "module:solid-fill",
                vertexLayoutHash = "vertex:rect",
                targetFormatClass = "rgba8unorm",
                blendStateHash = "blend:src-over",
                sampleStateHash = "sample:1",
                bindGroupLayoutHash = "bind-group:solid",
                capabilityClass = "first_slice.fill_rect.native",
                capabilityFacts = listOf("first_slice.fill_rect.native=supported"),
                rendererSalt = "kanvas-test",
            ),
            moduleHash = "module:solid-fill",
            bindingLayoutHash = "bind-group:solid",
            requiredCapabilities = listOf("first_slice.fill_rect.native=supported"),
            creationStage = "render-pipeline",
        )

    /** Execution test double that relies on production refuse-by-default behavior. */
    private class RefusingExecutionContextDouble : GPUExecutionContext {
        override val deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(11)
    }

    /** Execution test double with readback capability but no backend implementation. */
    private class ReadbackCapableUnconfiguredContextDouble : GPUExecutionContext {
        override val deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(11)
        override val capabilities: GPUExecutionCapabilities = GPUExecutionCapabilities(readback = true)
    }

    /** Execution test double with render capability facts but no backend implementation. */
    private class RenderCapableUnconfiguredContextDouble : GPUExecutionContext {
        override val deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(11)
        override val capabilities: GPUExecutionCapabilities = GPUExecutionCapabilities(render = true)
    }

    /** Execution test double that returns a terminal diagnostic with colliding fact names. */
    private class TerminalFactCollisionContextDouble : GPUExecutionContext {
        override val deviceGeneration: GPUDeviceGeneration = GPUDeviceGeneration(11)
        override val capabilities: GPUExecutionCapabilities = GPUExecutionCapabilities(render = true)

        override fun preflight(request: GPUExecutionPreflightRequest): GPUExecutionPreflightReport =
            GPUExecutionPreflightReport(
                scopeLabel = request.scope.scopeLabel,
                commandClass = request.scope.commandClass,
                targetId = request.target?.targetId,
                taskIds = request.taskIds,
                passIds = request.passIds,
                diagnostics = listOf(
                    GPUExecutionDiagnostic(
                        code = "backend.execution.terminal",
                        stage = "submit",
                        message = "Backend-specific terminal diagnostic.",
                        terminal = true,
                        facts = mapOf(
                            "targetId" to "diagnostic-target",
                        ),
                    ),
                ),
            )
    }
}
