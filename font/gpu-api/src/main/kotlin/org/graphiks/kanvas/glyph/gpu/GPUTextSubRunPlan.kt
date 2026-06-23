package org.graphiks.kanvas.glyph.gpu

class GPUTextSubRunPlanReport(
    ownerTickets: List<String>,
    val classification: String,
    scenarios: List<GPUTextSubRunScenario>,
    nonClaims: List<String>,
    val routePromotion: String = SUBRUN_PLAN_NOT_PROMOTED,
    val productActivation: Boolean = false,
) {
    val ownerTickets: List<String> = ownerTickets.toList()
    val scenarios: List<GPUTextSubRunScenario> = scenarios.toList()
    val nonClaims: List<String> = nonClaims.toList()

    init {
        require(ownerTickets.isNotEmpty()) { "ownerTickets must not be empty." }
        require(ownerTickets.all { ticket -> ticket.isNotBlank() }) { "ownerTickets must not contain blanks." }
        require(classification == "GPU-gated") { "GPUTextSubRunPlan is GPU-gated evidence." }
        require(this.scenarios.map { scenario -> scenario.scenarioId }.distinct().size == this.scenarios.size) {
            "scenario IDs must be unique."
        }
        require(routePromotion == SUBRUN_PLAN_NOT_PROMOTED) {
            "GPUTextSubRunPlan cannot promote renderer text routes."
        }
        require(!productActivation) {
            "GPUTextSubRunPlan cannot activate product renderer support."
        }
    }

    fun scenario(scenarioId: String): GPUTextSubRunScenario =
        scenarios.single { scenario -> scenario.scenarioId == scenarioId }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendSubRunJsonField("schema", SUBRUN_PLAN_REPORT_SCHEMA, comma = true)
        appendSubRunStringListField("ownerTickets", ownerTickets, comma = true)
        appendSubRunJsonField("classification", classification, comma = true)
        append("\"scenarios\":")
        append(scenarios.joinToString(separator = ",", prefix = "[", postfix = "]") { scenario ->
            scenario.toCanonicalJson()
        })
        append(",")
        appendSubRunStringListField("nonClaims", nonClaims, comma = true)
        appendSubRunJsonField("routePromotion", routePromotion, comma = true)
        appendSubRunJsonField("productActivation", productActivation, comma = false)
        append("}\n")
    }
}

class GPUTextSubRunScenario(
    val scenarioId: String,
    val splitKind: String,
    subRuns: List<GPUTextSubRunPlan>,
) {
    val subRuns: List<GPUTextSubRunPlan> = subRuns.toList()

    init {
        require(scenarioId.isNotBlank()) { "scenarioId must not be blank." }
        require(splitKind.isNotBlank()) { "splitKind must not be blank." }
        require(subRuns.isNotEmpty()) { "subRuns must not be empty." }
        require(subRuns.map { subRun -> subRun.subRunId }.distinct().size == subRuns.size) {
            "subRun IDs must be unique within a scenario."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendSubRunJsonField("scenarioId", scenarioId, comma = true)
        appendSubRunJsonField("splitKind", splitKind, comma = true)
        append("\"subRuns\":")
        append(subRuns.joinToString(separator = ",", prefix = "[", postfix = "]") { subRun ->
            subRun.toCanonicalJson()
        })
        append("}")
    }
}

class GPUTextSubRunPlan(
    val subRunId: String,
    val parentCommandId: String,
    val sourceGlyphRange: String,
    val representation: String,
    val route: String,
    val renderStep: String,
    val routeOutcome: String,
    val atlasPageId: String?,
    val atlasGeneration: Int?,
    val materialKey: String,
    val clipKey: String,
    val layerKey: String,
    val destinationReadRequired: Boolean,
    val instanceBudgetGlyphLimit: Int,
    val orderingToken: String,
    splitReasons: List<String>,
    val handoffDiagnostic: String?,
    val rendererDiagnostic: String?,
) {
    val splitReasons: List<String> = splitReasons.toList()

    init {
        require(subRunId.isNotBlank()) { "subRunId must not be blank." }
        require(parentCommandId.isNotBlank()) { "parentCommandId must not be blank." }
        require(sourceGlyphRange.startsWith("glyphs:")) { "sourceGlyphRange must be a glyph range label." }
        require(representation.isNotBlank()) { "representation must not be blank." }
        require(route.isNotBlank()) { "route must not be blank." }
        require(renderStep.isNotBlank()) { "renderStep must not be blank." }
        require(routeOutcome == "accepted" || routeOutcome == "refused") {
            "routeOutcome must be accepted or refused."
        }
        require(atlasGeneration == null || atlasGeneration >= 0) { "atlasGeneration must be non-negative." }
        require(materialKey.isNotBlank()) { "materialKey must not be blank." }
        require(clipKey.isNotBlank()) { "clipKey must not be blank." }
        require(layerKey.isNotBlank()) { "layerKey must not be blank." }
        require(instanceBudgetGlyphLimit > 0) { "instanceBudgetGlyphLimit must be positive." }
        require(orderingToken.isNotBlank()) { "orderingToken must not be blank." }
        require(splitReasons.isNotEmpty()) { "splitReasons must not be empty." }
        if (routeOutcome == "refused") {
            require(handoffDiagnostic?.startsWith("text.gpu.") == true) {
                "refused subruns require a text.gpu handoff diagnostic."
            }
            require(rendererDiagnostic?.startsWith("unsupported.text.") == true) {
                "refused subruns require an unsupported.text renderer diagnostic."
            }
        }
        if (routeOutcome == "accepted") {
            require(handoffDiagnostic == null) { "accepted subruns must not carry refusal diagnostics." }
            require(rendererDiagnostic == null) { "accepted subruns must not carry refusal diagnostics." }
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendSubRunJsonField("subRunId", subRunId, comma = true)
        appendSubRunJsonField("parentCommandId", parentCommandId, comma = true)
        appendSubRunJsonField("sourceGlyphRange", sourceGlyphRange, comma = true)
        appendSubRunJsonField("representation", representation, comma = true)
        appendSubRunJsonField("route", route, comma = true)
        appendSubRunJsonField("renderStep", renderStep, comma = true)
        appendSubRunJsonField("routeOutcome", routeOutcome, comma = true)
        appendSubRunNullableStringField("atlasPageId", atlasPageId, comma = true)
        appendSubRunNullableIntField("atlasGeneration", atlasGeneration, comma = true)
        appendSubRunJsonField("materialKey", materialKey, comma = true)
        appendSubRunJsonField("clipKey", clipKey, comma = true)
        appendSubRunJsonField("layerKey", layerKey, comma = true)
        appendSubRunJsonField("destinationReadRequired", destinationReadRequired, comma = true)
        appendSubRunJsonField("instanceBudgetGlyphLimit", instanceBudgetGlyphLimit, comma = true)
        appendSubRunJsonField("orderingToken", orderingToken, comma = true)
        appendSubRunStringListField("splitReasons", splitReasons, comma = true)
        appendSubRunNullableStringField("handoffDiagnostic", handoffDiagnostic, comma = true)
        appendSubRunNullableStringField("rendererDiagnostic", rendererDiagnostic, comma = false)
        append("}")
    }
}

fun defaultGPUTextSubRunPlanReport(): GPUTextSubRunPlanReport =
    GPUTextSubRunPlanReport(
        ownerTickets = listOf("KFONT-M11-006"),
        classification = "GPU-gated",
        scenarios = listOf(
            atlasPageGenerationSplitScenario(),
            clipLayerBudgetSplitScenario(),
            representationRefusalSplitScenario(),
        ),
        nonClaims = DEFAULT_SUBRUN_NON_CLAIMS,
    )

fun defaultGPUTextSubRunPlanReportJson(): String =
    defaultGPUTextSubRunPlanReport().toCanonicalJson()

private fun atlasPageGenerationSplitScenario(): GPUTextSubRunScenario =
    GPUTextSubRunScenario(
        scenarioId = "atlas-page-generation-split",
        splitKind = "atlas-page-generation",
        subRuns = listOf(
            acceptedA8SubRun(
                subRunId = "atlas-page-generation-split.0",
                sourceGlyphRange = "glyphs:0..0",
                atlasPageId = "a8-page-0",
                atlasGeneration = 3,
                orderingToken = "sample-after-upload:0",
                splitReasons = listOf("atlas-page"),
            ),
            acceptedA8SubRun(
                subRunId = "atlas-page-generation-split.1",
                sourceGlyphRange = "glyphs:1..1",
                atlasPageId = "a8-page-1",
                atlasGeneration = 3,
                orderingToken = "sample-after-upload:1",
                splitReasons = listOf("atlas-page"),
            ),
            acceptedA8SubRun(
                subRunId = "atlas-page-generation-split.2",
                sourceGlyphRange = "glyphs:2..2",
                atlasPageId = "a8-page-0",
                atlasGeneration = 4,
                orderingToken = "sample-after-upload:2",
                splitReasons = listOf("atlas-generation"),
            ),
        ),
    )

private fun clipLayerBudgetSplitScenario(): GPUTextSubRunScenario =
    GPUTextSubRunScenario(
        scenarioId = "clip-layer-budget-split",
        splitKind = "clip-layer-destination-budget",
        subRuns = listOf(
            acceptedA8SubRun(
                subRunId = "clip-layer-budget-split.0",
                sourceGlyphRange = "glyphs:0..0",
                clipKey = "clip:scissor-a",
                layerKey = "layer:root",
                splitReasons = listOf("clip"),
            ),
            acceptedA8SubRun(
                subRunId = "clip-layer-budget-split.1",
                sourceGlyphRange = "glyphs:1..1",
                clipKey = "clip:scissor-b",
                layerKey = "layer:root",
                splitReasons = listOf("layer"),
            ),
            acceptedA8SubRun(
                subRunId = "clip-layer-budget-split.2",
                sourceGlyphRange = "glyphs:2..2",
                clipKey = "clip:scissor-b",
                layerKey = "layer:savelayer-a",
                destinationReadRequired = true,
                splitReasons = listOf("destination-read"),
            ),
            refusedSubRun(
                subRunId = "clip-layer-budget-split.3",
                sourceGlyphRange = "glyphs:3..3",
                representation = "A8MaskAtlas",
                route = "AtlasMaskSample",
                renderStep = "A8TextMaskStep",
                clipKey = "clip:scissor-b",
                layerKey = "layer:savelayer-a",
                destinationReadRequired = true,
                splitReasons = listOf("instance-budget"),
                handoffDiagnostic = "text.gpu.artifact-budget-exceeded",
                rendererDiagnostic = "unsupported.text.instance_buffer_budget_exceeded",
            ),
        ),
    )

private fun representationRefusalSplitScenario(): GPUTextSubRunScenario =
    GPUTextSubRunScenario(
        scenarioId = "representation-refusal-split",
        splitKind = "representation-route",
        subRuns = listOf(
            acceptedA8SubRun(
                subRunId = "representation-refusal-split.0",
                sourceGlyphRange = "glyphs:0..0",
                splitReasons = listOf("representation"),
            ),
            refusedSubRun(
                subRunId = "representation-refusal-split.1",
                sourceGlyphRange = "glyphs:1..1",
                representation = "SDFMaskAtlas",
                route = "AtlasSDFSample",
                renderStep = "SDFTextMaskStep",
                splitReasons = listOf("representation"),
                handoffDiagnostic = "text.gpu.capability-missing",
                rendererDiagnostic = "unsupported.text.sdf_route_unavailable",
            ),
            refusedSubRun(
                subRunId = "representation-refusal-split.2",
                sourceGlyphRange = "glyphs:2..2",
                representation = "COLRColorGlyph",
                route = "ColorGlyphCompositeRoute",
                renderStep = "ColorGlyphCompositeStep",
                splitReasons = listOf("representation"),
                handoffDiagnostic = "text.gpu.color-plan-unsupported",
                rendererDiagnostic = "unsupported.text.color_plan_unsupported",
            ),
            refusedSubRun(
                subRunId = "representation-refusal-split.3",
                sourceGlyphRange = "glyphs:3..3",
                representation = "BitmapGlyph",
                route = "BitmapGlyphTextureRoute",
                renderStep = "BitmapGlyphTextureStep",
                splitReasons = listOf("representation"),
                handoffDiagnostic = "text.gpu.capability-missing",
                rendererDiagnostic = "unsupported.text.bitmap_route_unsupported",
            ),
        ),
    )

private fun acceptedA8SubRun(
    subRunId: String,
    sourceGlyphRange: String,
    atlasPageId: String = "a8-page-0",
    atlasGeneration: Int = 3,
    materialKey: String = "material:text-black",
    clipKey: String = "clip:scissor-a",
    layerKey: String = "layer:root",
    destinationReadRequired: Boolean = false,
    orderingToken: String = "sample-after-upload:0",
    splitReasons: List<String>,
): GPUTextSubRunPlan =
    GPUTextSubRunPlan(
        subRunId = subRunId,
        parentCommandId = "draw-text-a8-001",
        sourceGlyphRange = sourceGlyphRange,
        representation = "A8MaskAtlas",
        route = "AtlasMaskSample",
        renderStep = "A8TextMaskStep",
        routeOutcome = "accepted",
        atlasPageId = atlasPageId,
        atlasGeneration = atlasGeneration,
        materialKey = materialKey,
        clipKey = clipKey,
        layerKey = layerKey,
        destinationReadRequired = destinationReadRequired,
        instanceBudgetGlyphLimit = 1,
        orderingToken = orderingToken,
        splitReasons = splitReasons,
        handoffDiagnostic = null,
        rendererDiagnostic = null,
    )

private fun refusedSubRun(
    subRunId: String,
    sourceGlyphRange: String,
    representation: String,
    route: String,
    renderStep: String,
    clipKey: String = "clip:scissor-a",
    layerKey: String = "layer:root",
    destinationReadRequired: Boolean = false,
    splitReasons: List<String>,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
): GPUTextSubRunPlan =
    GPUTextSubRunPlan(
        subRunId = subRunId,
        parentCommandId = "draw-text-a8-001",
        sourceGlyphRange = sourceGlyphRange,
        representation = representation,
        route = route,
        renderStep = renderStep,
        routeOutcome = "refused",
        atlasPageId = if (representation == "A8MaskAtlas") "a8-page-0" else null,
        atlasGeneration = if (representation == "A8MaskAtlas") 3 else null,
        materialKey = "material:text-black",
        clipKey = clipKey,
        layerKey = layerKey,
        destinationReadRequired = destinationReadRequired,
        instanceBudgetGlyphLimit = 1,
        orderingToken = "sample-after-upload:${subRunId.substringAfterLast('.')}",
        splitReasons = splitReasons,
        handoffDiagnostic = handoffDiagnostic,
        rendererDiagnostic = rendererDiagnostic,
    )

private fun StringBuilder.appendSubRunJsonField(name: String, value: String, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(value.subRunQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendSubRunJsonField(name: String, value: Int, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendSubRunJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendSubRunNullableStringField(name: String, value: String?, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(value?.subRunQuoted() ?: "null")
    if (comma) append(",")
}

private fun StringBuilder.appendSubRunNullableIntField(name: String, value: Int?, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(value?.toString() ?: "null")
    if (comma) append(",")
}

private fun StringBuilder.appendSubRunStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.subRunQuoted())
    append(":")
    append(values.joinToString(separator = ",", prefix = "[", postfix = "]") { value -> value.subRunQuoted() })
    if (comma) append(",")
}

private fun String.subRunQuoted(): String = "\"${subRunEscapeJson()}\""

private fun String.subRunEscapeJson(): String = buildString(length) {
    for (ch in this@subRunEscapeJson) {
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

private const val SUBRUN_PLAN_REPORT_SCHEMA = "org.graphiks.kanvas.glyph.gpu.GPUTextSubRunPlanReport.v1"
private const val SUBRUN_PLAN_NOT_PROMOTED = "not-promoted"

private val DEFAULT_SUBRUN_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
    "no-executed-gpu-upload-claim",
)
