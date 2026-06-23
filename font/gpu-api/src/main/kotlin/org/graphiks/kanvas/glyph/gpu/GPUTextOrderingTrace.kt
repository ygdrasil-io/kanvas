package org.graphiks.kanvas.glyph.gpu

data class GPUTextOrderingTask(
    val taskId: String,
    val taskKind: String,
    val resourceRef: String,
    val executionPhase: String,
    val sequence: Int,
) {
    init {
        require(taskId.isNotBlank()) { "taskId must not be blank." }
        require(taskKind.isNotBlank()) { "taskKind must not be blank." }
        require(resourceRef.isNotBlank()) { "resourceRef must not be blank." }
        require(executionPhase.isNotBlank()) { "executionPhase must not be blank." }
        require(sequence >= 0) { "sequence must be non-negative." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("taskId", taskId, comma = true)
        appendOrderingJsonField("taskKind", taskKind, comma = true)
        appendOrderingJsonField("resourceRef", resourceRef, comma = true)
        appendOrderingJsonField("executionPhase", executionPhase, comma = true)
        appendOrderingJsonField("sequence", sequence, comma = false)
        append("}")
    }
}

data class GPUTextOrderingEdge(
    val edgeId: String,
    val fromTaskId: String,
    val toTaskId: String,
    val edgeKind: String,
    val status: String,
) {
    init {
        require(edgeId.isNotBlank()) { "edgeId must not be blank." }
        require(fromTaskId.isNotBlank()) { "fromTaskId must not be blank." }
        require(toTaskId.isNotBlank()) { "toTaskId must not be blank." }
        require(edgeKind.isNotBlank()) { "edgeKind must not be blank." }
        require(status == "satisfied") { "KFONT-M11-008 accepted ordering edges must be satisfied." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("edgeId", edgeId, comma = true)
        appendOrderingJsonField("fromTaskId", fromTaskId, comma = true)
        appendOrderingJsonField("toTaskId", toTaskId, comma = true)
        appendOrderingJsonField("edgeKind", edgeKind, comma = true)
        appendOrderingJsonField("status", status, comma = false)
        append("}")
    }
}

data class GPUTextGenerationCheck(
    val checkId: String,
    val artifactGeneration: String,
    val atlasPageId: String,
    val expectedAtlasGeneration: Int,
    val observedAtlasGeneration: Int,
    val status: String,
) {
    init {
        require(checkId.isNotBlank()) { "checkId must not be blank." }
        require(artifactGeneration.isNotBlank()) { "artifactGeneration must not be blank." }
        require(atlasPageId.isNotBlank()) { "atlasPageId must not be blank." }
        require(expectedAtlasGeneration >= 0) { "expectedAtlasGeneration must be non-negative." }
        require(observedAtlasGeneration >= 0) { "observedAtlasGeneration must be non-negative." }
        require(status == "validated") { "accepted traces must validate atlas generation." }
        require(expectedAtlasGeneration == observedAtlasGeneration) {
            "accepted traces cannot carry stale atlas generations."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("checkId", checkId, comma = true)
        appendOrderingJsonField("artifactGeneration", artifactGeneration, comma = true)
        appendOrderingJsonField("atlasPageId", atlasPageId, comma = true)
        appendOrderingJsonField("expectedAtlasGeneration", expectedAtlasGeneration, comma = true)
        appendOrderingJsonField("observedAtlasGeneration", observedAtlasGeneration, comma = true)
        appendOrderingJsonField("status", status, comma = false)
        append("}")
    }
}

data class GPUTextOrderingResourceState(
    val stateId: String,
    val resourceRef: String,
    val atlasPageId: String,
    val atlasGeneration: Int,
    val residencyState: String,
    val mutationPolicy: String,
) {
    init {
        require(stateId.isNotBlank()) { "stateId must not be blank." }
        require(resourceRef.isNotBlank()) { "resourceRef must not be blank." }
        require(atlasPageId.isNotBlank()) { "atlasPageId must not be blank." }
        require(atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(residencyState.isNotBlank()) { "residencyState must not be blank." }
        require(mutationPolicy.isNotBlank()) { "mutationPolicy must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("stateId", stateId, comma = true)
        appendOrderingJsonField("resourceRef", resourceRef, comma = true)
        appendOrderingJsonField("atlasPageId", atlasPageId, comma = true)
        appendOrderingJsonField("atlasGeneration", atlasGeneration, comma = true)
        appendOrderingJsonField("residencyState", residencyState, comma = true)
        appendOrderingJsonField("mutationPolicy", mutationPolicy, comma = false)
        append("}")
    }
}

data class GPUTextOrderingBarrier(
    val barrierId: String,
    val barrierKind: String,
    val beforeTaskId: String,
    val afterTaskId: String,
    val resourceRef: String,
    val status: String,
) {
    init {
        require(barrierId.isNotBlank()) { "barrierId must not be blank." }
        require(barrierKind.isNotBlank()) { "barrierKind must not be blank." }
        require(beforeTaskId.isNotBlank()) { "beforeTaskId must not be blank." }
        require(afterTaskId.isNotBlank()) { "afterTaskId must not be blank." }
        require(resourceRef.isNotBlank()) { "resourceRef must not be blank." }
        require(status == "recorded") { "accepted ordering barriers must be recorded." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("barrierId", barrierId, comma = true)
        appendOrderingJsonField("barrierKind", barrierKind, comma = true)
        appendOrderingJsonField("beforeTaskId", beforeTaskId, comma = true)
        appendOrderingJsonField("afterTaskId", afterTaskId, comma = true)
        appendOrderingJsonField("resourceRef", resourceRef, comma = true)
        appendOrderingJsonField("status", status, comma = false)
        append("}")
    }
}

class GPUTextOrderingToken(
    val tokenId: String,
    val subRunId: String,
    val resourcePlanId: String,
    val uploadPlanId: String,
    val instanceBufferPlanId: String,
    val drawTaskId: String,
    val artifactGeneration: String,
    val atlasUploadTaskId: String,
    val atlasPageId: String,
    val atlasPageGeneration: Int,
    val instanceUploadTaskId: String,
    val evictionTaskId: String,
    barriers: List<GPUTextOrderingBarrier>,
) {
    val barriers: List<GPUTextOrderingBarrier> = barriers.toList()

    init {
        require(tokenId.isNotBlank()) { "tokenId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(resourcePlanId.isNotBlank()) { "resourcePlanId must not be blank." }
        require(uploadPlanId.isNotBlank()) { "uploadPlanId must not be blank." }
        require(instanceBufferPlanId.isNotBlank()) { "instanceBufferPlanId must not be blank." }
        require(drawTaskId.isNotBlank()) { "drawTaskId must not be blank." }
        require(artifactGeneration.isNotBlank()) { "artifactGeneration must not be blank." }
        require(atlasUploadTaskId.isNotBlank()) { "atlasUploadTaskId must not be blank." }
        require(atlasPageId.isNotBlank()) { "atlasPageId must not be blank." }
        require(atlasPageGeneration >= 0) { "atlasPageGeneration must be non-negative." }
        require(instanceUploadTaskId.isNotBlank()) { "instanceUploadTaskId must not be blank." }
        require(evictionTaskId.isNotBlank()) { "evictionTaskId must not be blank." }
        require(this.barriers.isNotEmpty()) { "barriers must not be empty." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("tokenId", tokenId, comma = true)
        appendOrderingJsonField("subRunId", subRunId, comma = true)
        appendOrderingJsonField("resourcePlanId", resourcePlanId, comma = true)
        appendOrderingJsonField("uploadPlanId", uploadPlanId, comma = true)
        appendOrderingJsonField("instanceBufferPlanId", instanceBufferPlanId, comma = true)
        appendOrderingJsonField("drawTaskId", drawTaskId, comma = true)
        appendOrderingJsonField("artifactGeneration", artifactGeneration, comma = true)
        appendOrderingJsonField("atlasUploadTaskId", atlasUploadTaskId, comma = true)
        appendOrderingJsonField("atlasPageId", atlasPageId, comma = true)
        appendOrderingJsonField("atlasPageGeneration", atlasPageGeneration, comma = true)
        appendOrderingJsonField("instanceUploadTaskId", instanceUploadTaskId, comma = true)
        appendOrderingJsonField("evictionTaskId", evictionTaskId, comma = true)
        append("\"barriers\":")
        append(barriers.joinToString(separator = ",", prefix = "[", postfix = "]") { barrier ->
            barrier.toCanonicalJson()
        })
        append("}")
    }
}

class GPUTextOrderingTrace(
    val traceId: String,
    val token: GPUTextOrderingToken,
    tasks: List<GPUTextOrderingTask>,
    dependencyEdges: List<GPUTextOrderingEdge>,
    generationChecks: List<GPUTextGenerationCheck>,
    resourceStates: List<GPUTextOrderingResourceState>,
    diagnostics: List<String>,
    val routeOutcome: String = "accepted",
    val visualOrderPolicy: String = "source-order-preserved",
) {
    val tasks: List<GPUTextOrderingTask> = tasks.toList()
    val dependencyEdges: List<GPUTextOrderingEdge> = dependencyEdges.toList()
    val generationChecks: List<GPUTextGenerationCheck> = generationChecks.toList()
    val resourceStates: List<GPUTextOrderingResourceState> = resourceStates.toList()
    val diagnostics: List<String> = diagnostics.toList()

    init {
        require(traceId.isNotBlank()) { "traceId must not be blank." }
        require(this.tasks.isNotEmpty()) { "tasks must not be empty." }
        require(this.dependencyEdges.isNotEmpty()) { "dependencyEdges must not be empty." }
        require(this.generationChecks.isNotEmpty()) { "generationChecks must not be empty." }
        require(this.resourceStates.isNotEmpty()) { "resourceStates must not be empty." }
        require(this.diagnostics.all { diagnostic -> diagnostic.startsWith("text.gpu.") }) {
            "diagnostics must use the text.gpu namespace."
        }
        require(routeOutcome == "accepted") { "ordering traces only serialize accepted traces." }
        require(visualOrderPolicy == "source-order-preserved") {
            "KFONT-M11-008 must preserve visual order through ordering traces."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("traceId", traceId, comma = true)
        appendOrderingJsonRawField("token", token.toCanonicalJson(), comma = true)
        append("\"tasks\":")
        append(tasks.joinToString(separator = ",", prefix = "[", postfix = "]") { task ->
            task.toCanonicalJson()
        })
        append(",")
        append("\"dependencyEdges\":")
        append(dependencyEdges.joinToString(separator = ",", prefix = "[", postfix = "]") { edge ->
            edge.toCanonicalJson()
        })
        append(",")
        append("\"generationChecks\":")
        append(generationChecks.joinToString(separator = ",", prefix = "[", postfix = "]") { check ->
            check.toCanonicalJson()
        })
        append(",")
        append("\"resourceStates\":")
        append(resourceStates.joinToString(separator = ",", prefix = "[", postfix = "]") { state ->
            state.toCanonicalJson()
        })
        append(",")
        appendOrderingJsonStringListField("diagnostics", diagnostics, comma = true)
        appendOrderingJsonField("routeOutcome", routeOutcome, comma = true)
        appendOrderingJsonField("visualOrderPolicy", visualOrderPolicy, comma = false)
        append("}")
    }
}

class GPUTextOrderingTraceReport(
    ownerTickets: List<String>,
    val classification: String,
    orderingTraces: List<GPUTextOrderingTrace>,
    refusals: List<GPUTextRouteRefusal>,
    val routePromotion: String = "not-promoted",
    val productActivation: Boolean = false,
    val uploadExecution: String = "not-executed",
    val gpuTaskGraphExecuted: Boolean = false,
) {
    val ownerTickets: List<String> = ownerTickets.toList()
    val orderingTraces: List<GPUTextOrderingTrace> = orderingTraces.toList()
    val refusals: List<GPUTextRouteRefusal> = refusals.toList()

    init {
        require(this.ownerTickets == listOf("KFONT-M11-008")) { "ownerTickets must identify KFONT-M11-008." }
        require(classification == "GPU-gated") { "ordering traces are GPU-gated evidence." }
        require(this.orderingTraces.isNotEmpty()) { "orderingTraces must not be empty." }
        require(this.refusals.isNotEmpty()) { "refusals must not be empty." }
        require(routePromotion == "not-promoted") { "KFONT-M11-008 cannot promote text routes." }
        require(!productActivation) { "KFONT-M11-008 cannot activate product renderer support." }
        require(uploadExecution == "not-executed") { "KFONT-M11-008 must not claim executed uploads." }
        require(!gpuTaskGraphExecuted) { "KFONT-M11-008 must not claim a general task graph execution." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendOrderingJsonField("schema", ORDERING_TRACE_REPORT_SCHEMA, comma = true)
        appendOrderingJsonStringListField("ownerTickets", ownerTickets, comma = true)
        appendOrderingJsonField("classification", classification, comma = true)
        append("\"orderingTraces\":")
        append(orderingTraces.joinToString(separator = ",", prefix = "[", postfix = "]") { trace ->
            trace.toCanonicalJson()
        })
        append(",")
        append("\"refusals\":")
        append(refusals.joinToString(separator = ",", prefix = "[", postfix = "]") { refusal ->
            refusal.toCanonicalJson()
        })
        append(",")
        appendOrderingJsonStringListField("nonClaims", ORDERING_TRACE_NON_CLAIMS, comma = true)
        appendOrderingJsonField("routePromotion", routePromotion, comma = true)
        appendOrderingJsonField("productActivation", productActivation, comma = true)
        appendOrderingJsonField("uploadExecution", uploadExecution, comma = true)
        appendOrderingJsonField("gpuTaskGraphExecuted", gpuTaskGraphExecuted, comma = false)
        append("}\n")
    }
}

data class GPUTextOrderingFixture(
    val commandId: String,
    val subRunId: String,
    val artifactType: String,
    val artifactKeyHash: String,
    val route: String,
    val resourcePlanId: String,
    val uploadPlanId: String,
    val instanceBufferPlanId: String,
    val expectedAtlasGeneration: Int,
    val observedAtlasGeneration: Int,
    val uploadBeforeSampleEdgePresent: Boolean,
    val instanceUploadBeforeDraw: Boolean,
    val evictionBeforeDraw: Boolean,
    val evictionBarrierRecorded: Boolean,
) {
    init {
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(artifactKeyHash.isNotBlank()) { "artifactKeyHash must not be blank." }
        require(route.isNotBlank()) { "route must not be blank." }
        require(resourcePlanId.isNotBlank()) { "resourcePlanId must not be blank." }
        require(uploadPlanId.isNotBlank()) { "uploadPlanId must not be blank." }
        require(instanceBufferPlanId.isNotBlank()) { "instanceBufferPlanId must not be blank." }
        require(expectedAtlasGeneration >= 0) { "expectedAtlasGeneration must be non-negative." }
        require(observedAtlasGeneration >= 0) { "observedAtlasGeneration must be non-negative." }
    }
}

sealed interface GPUTextOrderingPlanningResult {
    data class Accepted(val trace: GPUTextOrderingTrace) : GPUTextOrderingPlanningResult

    data class Refused(val refusal: GPUTextRouteRefusal) : GPUTextOrderingPlanningResult
}

fun planGPUTextOrderingTrace(fixture: GPUTextOrderingFixture): GPUTextOrderingPlanningResult {
    val refusal = when {
        !fixture.uploadBeforeSampleEdgePresent -> fixture.orderingRefusal(
            reasonId = "upload-before-sample-edge-missing",
            blocker = GPUTextRouteBlocker.UPLOAD_PLAN,
            handoffDiagnostic = "text.gpu.upload-before-sample-edge-missing",
            rendererDiagnostic = "unsupported.text.upload_plan_missing",
        )
        fixture.expectedAtlasGeneration != fixture.observedAtlasGeneration -> fixture.orderingRefusal(
            reasonId = "atlas-generation-stale",
            blocker = GPUTextRouteBlocker.STALE_GENERATION,
            handoffDiagnostic = "text.gpu.atlas-generation-stale",
            rendererDiagnostic = "unsupported.text.atlas_generation_stale",
        )
        fixture.evictionBeforeDraw && !fixture.evictionBarrierRecorded -> fixture.orderingRefusal(
            reasonId = "eviction-before-dependent-draw",
            blocker = GPUTextRouteBlocker.EVICTION_BARRIER,
            handoffDiagnostic = "text.gpu.eviction-before-dependent-draw",
            rendererDiagnostic = "unsupported.text.eviction_before_dependent_draw",
        )
        !fixture.instanceUploadBeforeDraw -> fixture.orderingRefusal(
            reasonId = "instance-upload-after-draw",
            blocker = GPUTextRouteBlocker.INSTANCE_UPLOAD_ORDER,
            handoffDiagnostic = "text.gpu.instance-upload-after-draw",
            rendererDiagnostic = "unsupported.text.instance_upload_after_draw",
        )
        else -> null
    }
    return if (refusal == null) {
        GPUTextOrderingPlanningResult.Accepted(fixture.acceptedOrderingTrace())
    } else {
        GPUTextOrderingPlanningResult.Refused(refusal)
    }
}

fun defaultGPUTextOrderingFixture(): GPUTextOrderingFixture {
    val evidence = defaultGPUTextResourceContractEvidence()
    val resourcePlan = evidence.resourcePlan
    return GPUTextOrderingFixture(
        commandId = resourcePlan.commandId,
        subRunId = resourcePlan.subRunId,
        artifactType = resourcePlan.artifactType,
        artifactKeyHash = resourcePlan.artifactKeyHash,
        route = resourcePlan.route,
        resourcePlanId = resourcePlan.resourcePlanId,
        uploadPlanId = evidence.uploadPlan.uploadPlanId,
        instanceBufferPlanId = evidence.instanceBufferPlan.instanceBufferPlanId,
        expectedAtlasGeneration = resourcePlan.atlasEntryRefs.single().atlasGeneration,
        observedAtlasGeneration = resourcePlan.atlasEntryRefs.single().atlasGeneration,
        uploadBeforeSampleEdgePresent = true,
        instanceUploadBeforeDraw = true,
        evictionBeforeDraw = false,
        evictionBarrierRecorded = true,
    )
}

fun defaultGPUTextOrderingTraceReportJson(): String =
    defaultGPUTextOrderingTraceReport().toCanonicalJson()

fun defaultGPUTextOrderingTraceReport(): GPUTextOrderingTraceReport {
    val fixture = defaultGPUTextOrderingFixture()
    val accepted = planGPUTextOrderingTrace(fixture) as GPUTextOrderingPlanningResult.Accepted
    return GPUTextOrderingTraceReport(
        ownerTickets = listOf("KFONT-M11-008"),
        classification = "GPU-gated",
        orderingTraces = listOf(accepted.trace),
        refusals = defaultGPUTextOrderingRefusals(),
    )
}

fun defaultGPUTextOrderingToken(): GPUTextOrderingToken =
    defaultGPUTextOrderingFixture().acceptedOrderingTrace().token

fun defaultGPUTextOrderingTask(taskId: String, taskKind: String): GPUTextOrderingTask =
    GPUTextOrderingTask(
        taskId = taskId,
        taskKind = taskKind,
        resourceRef = "resource:test",
        executionPhase = "test",
        sequence = 0,
    )

fun defaultGPUTextGenerationCheck(): GPUTextGenerationCheck =
    GPUTextGenerationCheck(
        checkId = "check:a8-page-0:generation",
        artifactGeneration = "artifact-generation:sha256:a8-atlas:3",
        atlasPageId = "a8-page-0",
        expectedAtlasGeneration = 3,
        observedAtlasGeneration = 3,
        status = "validated",
    )

fun defaultGPUTextOrderingResourceState(): GPUTextOrderingResourceState =
    GPUTextOrderingResourceState(
        stateId = "state:a8-page-0",
        resourceRef = "texture:text-atlas-a8-page-0",
        atlasPageId = "a8-page-0",
        atlasGeneration = 3,
        residencyState = "resident",
        mutationPolicy = "evict-after-dependent-draw",
    )

private fun defaultGPUTextOrderingRefusals(): List<GPUTextRouteRefusal> {
    val fixture = defaultGPUTextOrderingFixture()
    return listOf(
        fixture.copy(uploadBeforeSampleEdgePresent = false),
        fixture.copy(observedAtlasGeneration = 4),
        fixture.copy(evictionBeforeDraw = true, evictionBarrierRecorded = false),
        fixture.copy(instanceUploadBeforeDraw = false),
    ).map { case ->
        (planGPUTextOrderingTrace(case) as GPUTextOrderingPlanningResult.Refused).refusal
    }
}

private fun GPUTextOrderingFixture.acceptedOrderingTrace(): GPUTextOrderingTrace {
    val uploadTaskId = "task:upload-a8-page-0"
    val generationTaskId = "task:generation-check-a8-page-0"
    val instanceTaskId = "task:instance-a8-0"
    val evictionTaskId = "task:evict-a8-page-0"
    val textureRef = "texture:text-atlas-a8-page-0"
    val atlasPageId = "a8-page-0"
    val artifactGeneration = "artifact-generation:$artifactKeyHash:$expectedAtlasGeneration"
    val token = GPUTextOrderingToken(
        tokenId = "gpu-text-ordering-a8-0",
        subRunId = subRunId,
        resourcePlanId = resourcePlanId,
        uploadPlanId = uploadPlanId,
        instanceBufferPlanId = instanceBufferPlanId,
        drawTaskId = commandId,
        artifactGeneration = artifactGeneration,
        atlasUploadTaskId = uploadTaskId,
        atlasPageId = atlasPageId,
        atlasPageGeneration = expectedAtlasGeneration,
        instanceUploadTaskId = instanceTaskId,
        evictionTaskId = evictionTaskId,
        barriers = listOf(
            GPUTextOrderingBarrier(
                barrierId = "barrier:draw-before-eviction-a8-page-0",
                barrierKind = "draw-before-eviction",
                beforeTaskId = commandId,
                afterTaskId = evictionTaskId,
                resourceRef = textureRef,
                status = "recorded",
            ),
        ),
    )
    return GPUTextOrderingTrace(
        traceId = "gpu-text-ordering-trace-a8-0",
        token = token,
        tasks = listOf(
            GPUTextOrderingTask(
                taskId = uploadTaskId,
                taskKind = "atlas-upload",
                resourceRef = uploadPlanId,
                executionPhase = "resource-prep",
                sequence = 0,
            ),
            GPUTextOrderingTask(
                taskId = generationTaskId,
                taskKind = "atlas-generation-validation",
                resourceRef = textureRef,
                executionPhase = "pre-draw-validation",
                sequence = 1,
            ),
            GPUTextOrderingTask(
                taskId = instanceTaskId,
                taskKind = "instance-buffer-upload",
                resourceRef = instanceBufferPlanId,
                executionPhase = "resource-prep",
                sequence = 2,
            ),
            GPUTextOrderingTask(
                taskId = commandId,
                taskKind = "draw-sample",
                resourceRef = resourcePlanId,
                executionPhase = "draw",
                sequence = 3,
            ),
            GPUTextOrderingTask(
                taskId = evictionTaskId,
                taskKind = "atlas-eviction",
                resourceRef = textureRef,
                executionPhase = "post-dependent-draw",
                sequence = 4,
            ),
        ),
        dependencyEdges = listOf(
            GPUTextOrderingEdge(
                edgeId = "edge:upload-a8-page-0->$commandId",
                fromTaskId = uploadTaskId,
                toTaskId = commandId,
                edgeKind = "upload-before-sample",
                status = "satisfied",
            ),
            GPUTextOrderingEdge(
                edgeId = "edge:generation-check-a8-page-0->$commandId",
                fromTaskId = generationTaskId,
                toTaskId = commandId,
                edgeKind = "generation-validation-before-draw",
                status = "satisfied",
            ),
            GPUTextOrderingEdge(
                edgeId = "edge:instance-a8-0->$commandId",
                fromTaskId = instanceTaskId,
                toTaskId = commandId,
                edgeKind = "instance-upload-before-draw",
                status = "satisfied",
            ),
            GPUTextOrderingEdge(
                edgeId = "edge:$commandId->evict-a8-page-0",
                fromTaskId = commandId,
                toTaskId = evictionTaskId,
                edgeKind = "draw-before-eviction",
                status = "satisfied",
            ),
        ),
        generationChecks = listOf(
            GPUTextGenerationCheck(
                checkId = "check:a8-page-0:generation",
                artifactGeneration = artifactGeneration,
                atlasPageId = atlasPageId,
                expectedAtlasGeneration = expectedAtlasGeneration,
                observedAtlasGeneration = observedAtlasGeneration,
                status = "validated",
            ),
        ),
        resourceStates = listOf(
            GPUTextOrderingResourceState(
                stateId = "state:a8-page-0",
                resourceRef = textureRef,
                atlasPageId = atlasPageId,
                atlasGeneration = expectedAtlasGeneration,
                residencyState = "resident",
                mutationPolicy = "evict-after-dependent-draw",
            ),
        ),
        diagnostics = listOf(
            "text.gpu.upload-before-sample-validated",
            "text.gpu.instance-upload-before-draw-validated",
            "text.gpu.atlas-generation-validated",
            "text.gpu.eviction-barrier-recorded",
        ),
    )
}

private fun GPUTextOrderingFixture.orderingRefusal(
    reasonId: String,
    blocker: GPUTextRouteBlocker,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
): GPUTextRouteRefusal =
    GPUTextRouteRefusal(
        refusalId = "gpu-text-ordering-a8-0.$reasonId",
        commandId = commandId,
        textRange = null,
        glyphRange = "glyphs:0..0",
        artifactType = artifactType,
        artifactKeyHash = artifactKeyHash,
        attemptedRoute = route,
        blocker = blocker,
        handoffDiagnostic = handoffDiagnostic,
        rendererDiagnostic = rendererDiagnostic,
        legacyGates = listOf("dftext"),
    )

private fun StringBuilder.appendOrderingJsonField(name: String, value: String, comma: Boolean) {
    append(name.orderingQuoted())
    append(":")
    append(value.orderingQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendOrderingJsonField(name: String, value: Int, comma: Boolean) {
    append(name.orderingQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendOrderingJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.orderingQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendOrderingJsonRawField(name: String, value: String, comma: Boolean) {
    append(name.orderingQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendOrderingJsonStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.orderingQuoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]") { value -> value.orderingQuoted() })
    if (comma) append(",")
}

private fun String.orderingQuoted(): String = "\"${orderingEscapeJson()}\""

private fun String.orderingEscapeJson(): String = buildString(length) {
    for (ch in this@orderingEscapeJson) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

private const val ORDERING_TRACE_REPORT_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.GPUTextOrderingTraceReport.v1"

private val ORDERING_TRACE_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
    "no-executed-gpu-upload-claim",
    "no-general-gpu-task-graph-scheduler",
    "no-sdf-ordering-claim",
)
