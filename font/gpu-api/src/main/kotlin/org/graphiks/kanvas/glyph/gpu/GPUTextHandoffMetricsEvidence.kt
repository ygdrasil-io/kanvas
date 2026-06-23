package org.graphiks.kanvas.glyph.gpu

import kotlin.uuid.Uuid

fun defaultGPUTextHandoffMetricsJson(): String {
    val snapshot = defaultGPUTextHandoffBundle().telemetrySnapshot(
        metadata = GPUTextTelemetrySampleMetadata(
            environmentLabel = "developer-desktop",
            sampleLabel = "gpu-handoff-telemetry",
            sampleCount = 1,
            cacheState = "warm",
        ),
        cacheRecords = listOf(
            GPUTextCacheTelemetryRecord(
                cacheName = "a8-atlas",
                keyPreimage = "strike=Latn:size=16:route=A8",
                hits = 3,
                misses = 1,
                evictions = 0,
                residentBytes = 1024,
                generationToken = "gen-001",
            ),
            GPUTextCacheTelemetryRecord(
                cacheName = "sdf-atlas",
                keyPreimage = "strike=Latn:size=32:route=SDF",
                hits = 1,
                misses = 2,
                evictions = 1,
                residentBytes = 2048,
                generationToken = "gen-002",
            ),
        ),
        advisoryBudgets = listOf(
            GPUTextAdvisoryBudgetRecord(
                metricName = "cache.resident.bytes",
                budgetName = "developer-desktop",
                observedValue = 3072,
                advisoryLimit = 8_388_608,
                unit = "bytes",
                sampleCount = 1,
            ),
            GPUTextAdvisoryBudgetRecord(
                metricName = "upload.bytes",
                budgetName = "warm-ui-text",
                observedValue = 192,
                advisoryLimit = 262_144,
                unit = "bytes",
                sampleCount = 1,
            ),
        ),
    )
    val acceptedPlan = defaultGPUTextA8RoutePlan()
    val routeRows = buildList {
        add(
            mapOf(
                "commandId" to acceptedPlan.commandId,
                "routeOutcome" to "selected",
                "artifactType" to acceptedPlan.artifact.artifactType,
                "selectedRoute" to acceptedPlan.selectedRoute,
                "gpuRouteClass" to acceptedPlan.renderStep,
                "artifactKeyHash" to acceptedPlan.artifact.artifactKeyHash,
                "uploadDependencyCount" to acceptedPlan.uploadDependencyLabels.size,
                "uploadByteCount" to 512,
                "artifactReuseCount" to 1,
                "uploadPlanReuseCount" to 1,
                "gpuAdapter" to "wgpu-nvidia-rtx-3070",
                "gpuBackend" to "webgpu",
            ),
        )
        defaultGPUTextRouteRefusalReport().refusals.forEach { refusal ->
            add(
                mapOf(
                    "commandId" to refusal.commandId,
                    "routeOutcome" to "refused",
                    "artifactType" to refusal.artifactType,
                    "attemptedRoute" to refusal.attemptedRoute,
                    "blocker" to refusal.blocker.name,
                    "classification" to refusal.classification,
                    "artifactKeyHash" to refusal.artifactKeyHash,
                    "handoffDiagnostic" to refusal.handoffDiagnostic,
                    "rendererDiagnostic" to refusal.rendererDiagnostic,
                ),
            )
        }
        defaultGPUTextA8RouteRefusalReport().refusals.forEach { refusal ->
            add(
                mapOf(
                    "commandId" to refusal.commandId,
                    "routeOutcome" to "refused",
                    "artifactType" to refusal.artifactType,
                    "attemptedRoute" to refusal.attemptedRoute,
                    "blocker" to refusal.blocker.name,
                    "classification" to refusal.classification,
                    "artifactKeyHash" to refusal.artifactKeyHash,
                    "handoffDiagnostic" to refusal.handoffDiagnostic,
                    "rendererDiagnostic" to refusal.rendererDiagnostic,
                ),
            )
        }
        add(
            mapOf(
                "commandId" to "draw-text-budget-001",
                "routeOutcome" to "refused",
                "artifactType" to "GlyphAtlasArtifact",
                "attemptedRoute" to "AtlasMaskSample",
                "blocker" to "ARTIFACT_REGISTRY",
                "classification" to "GPU-gated",
                "artifactKeyHash" to "sha256:a8-atlas-budget",
                "handoffDiagnostic" to "text.gpu.artifact-budget-exceeded",
                "rendererDiagnostic" to "unsupported.text.artifact_budget_exceeded",
            ),
        )
        add(
            mapOf(
                "commandId" to "draw-text-key-001",
                "routeOutcome" to "refused",
                "artifactType" to "GlyphAtlasArtifact",
                "attemptedRoute" to "ArtifactRegistryLookup",
                "blocker" to "ARTIFACT_REGISTRY",
                "classification" to "GPU-gated",
                "artifactKeyHash" to "sha256:nondeterministic-key-fixture",
                "handoffDiagnostic" to "text.gpu.artifact-key-nondeterministic",
                "rendererDiagnostic" to "unsupported.text.artifact_key_nondeterministic",
            ),
        )
    }

    return buildString {
        append("{")
        appendCompactField("schemaVersion", 1, comma = true)
        appendCompactField("dumpId", "gpu-text-handoff-metrics", comma = true)
        appendCompactStringListField("ownerTickets", listOf("KFONT-M12-005"), comma = true)
        appendCompactField("classification", "tracked-gap", comma = true)
        appendCompactField("gpuAdapter", "wgpu-nvidia-rtx-3070", comma = true)
        appendCompactField("gpuBackend", "webgpu", comma = true)
        append("\"aggregateCounters\":")
        append(
            compactObject(
                linkedMapOf(
                    "artifactReferenceCount" to snapshot.counters.artifactReferenceCount,
                    "uploadPlanCount" to snapshot.counters.uploadPlanCount,
                    "uploadBytes" to snapshot.counters.uploadBytes,
                    "uploadRangeCount" to snapshot.counters.uploadRangeCount,
                    "glyphUploadPlanCount" to snapshot.counters.glyphUploadPlanCount,
                    "glyphCount" to snapshot.counters.glyphCount,
                    "typedArtifactCounts" to linkedMapOf(
                        "GlyphAtlasArtifact" to snapshot.counters.a8AtlasCount,
                        "SDFGlyphAtlasArtifact" to snapshot.counters.sdfAtlasCount,
                        "GlyphUploadPlan" to snapshot.counters.glyphUploadPlanCount,
                        "OutlineGlyphPlan" to snapshot.counters.outlinePlanCount,
                        "ColorGlyphPlan" to snapshot.counters.colorPlanCount,
                        "BitmapGlyphPlan" to snapshot.counters.bitmapPlanCount,
                        "SVGGlyphPlan" to snapshot.counters.svgPlanCount,
                    ),
                    "selectedRouteCount" to routeRows.count { row -> row["routeOutcome"] == "selected" },
                    "refusedRouteCount" to routeRows.count { row -> row["routeOutcome"] == "refused" },
                ),
            ),
        )
        append(",")
        appendCompactRawListField("routeRows", routeRows.map(::compactObject), comma = true)
        appendCompactRawListField("cacheRecords", snapshot.cacheRecords.map { it.toCanonicalJson().trim() }, comma = true)
        appendCompactRawListField("advisoryBudgets", snapshot.advisoryBudgets.map { it.toCanonicalJson().trim() }, comma = true)
        appendCompactStringListField(
            "evidenceRefs",
            listOf(
                "reports/pure-kotlin-text/gpu-text-a8-route-plan.json",
                "reports/pure-kotlin-text/gpu-text-a8-route-refusals.json",
                "reports/pure-kotlin-text/draw-text-run-upload-plan.json",
            ),
            comma = true,
        )
        appendCompactStringListField(
            "nonClaims",
            listOf(
                "no-complete-target-support-claim",
                "no-performance-release-gate-claim",
                "no-gpu-route-support-claim",
                "no-native-engine-oracle-claim",
            ),
            comma = false,
        )
        append("}\n")
    }
}

fun defaultDrawTextRunUploadPlanJson(): String {
    val payload = defaultDrawTextRunPayloadFixture()
    val leakageReport = payload.noSkLeakageReport()
    val selectedFields = leakageReport.fields.filter { field ->
        field.fieldPath == "material.materialKey" ||
            field.fieldPath == "uploadDependencies[0].label" ||
            field.fieldPath == "artifactKeyHashes[0]"
    }

    return buildString {
        append("{")
        appendCompactField("schemaVersion", 1, comma = true)
        appendCompactField("dumpId", "draw-text-run-upload-plan", comma = true)
        appendCompactStringListField("ownerTickets", listOf("KFONT-M12-005"), comma = true)
        appendCompactNoSpaceField("classification", "tracked-gap", comma = true)
        appendCompactNoSpaceField("commandId", payload.commandId, comma = true)
        appendCompactNoSpaceField("payloadKind", "DrawTextRunPayload", comma = true)
        appendCompactNoSpaceField("materialKey", payload.material.materialKey, comma = true)
        appendCompactNoSpaceStringListField("artifactKeyHashes", payload.artifactKeyHashes, comma = true)
        appendCompactNoSpaceStringListField(
            "uploadDependencyLabels",
            payload.uploadDependencies.map { dependency -> dependency.label },
            comma = true,
        )
        appendCompactNoSpaceField("uploadByteCount", 512, comma = true)
        appendCompactNoSpaceField("uploadDependencyCount", payload.uploadDependencies.size, comma = true)
        appendCompactNoSpaceField("artifactReuseCount", 1, comma = true)
        appendCompactNoSpaceField("uploadPlanReuseCount", 1, comma = true)
        appendCompactNoSpaceField("routePromotion", payload.routePromotion, comma = true)
        appendCompactNoSpaceField("productActivation", payload.productActivation, comma = true)
        append("\"leakageAudit\":")
        append(
            compactObject(
                linkedMapOf(
                    "payloadKind" to "DrawTextRunPayload",
                    "payloadHash" to leakageReport.payloadHash,
                    "status" to leakageReport.status,
                    "fields" to selectedFields.map { field ->
                        linkedMapOf(
                            "fieldPath" to field.fieldPath,
                            "typeName" to field.typeName,
                            "value" to field.value,
                        )
                    },
                    "findings" to leakageReport.findings.map { finding ->
                        linkedMapOf(
                            "payloadKind" to finding.payloadKind,
                            "fieldPath" to finding.fieldPath,
                            "typeName" to finding.typeName,
                            "forbiddenKind" to finding.forbiddenKind,
                            "handoffDiagnostic" to finding.handoffDiagnostic,
                            "rendererDiagnostic" to finding.rendererDiagnostic,
                        )
                    },
                ),
            ),
        )
        append(",")
        appendCompactStringListField(
            "nonClaims",
            listOf(
                "no-complete-target-support-claim",
                "no-performance-release-gate-claim",
                "no-gpu-route-support-claim",
                "no-native-engine-oracle-claim",
            ),
            comma = false,
        )
        append("}\n")
    }
}

private fun defaultGPUTextHandoffBundle(): TextGPUArtifactBundle {
    val rootKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441000",
        generation = 0,
        contentFingerprint = "bundle-root",
    )
    val atlasKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441001",
        generation = 1,
        contentFingerprint = "glyph-atlas-a8",
    )
    val sdfAtlasKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441002",
        generation = 2,
        contentFingerprint = "glyph-atlas-sdf",
    )
    val glyphUploadKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441003",
        generation = 3,
        contentFingerprint = "glyph-upload-cpu",
    )
    val atlasUploadKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441004",
        generation = 4,
        contentFingerprint = "atlas-upload-cpu",
    )
    val outlineAKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441005",
        generation = 5,
        contentFingerprint = "outline-a",
    )
    val outlineBKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441006",
        generation = 6,
        contentFingerprint = "outline-b",
    )
    val colorKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441007",
        generation = 7,
        contentFingerprint = "color-plan",
    )
    val bitmapKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441008",
        generation = 8,
        contentFingerprint = "bitmap-plan",
    )
    val svgKey = telemetryArtifactKey(
        uuid = "550e8400-e29b-41d4-a716-446655441009",
        generation = 9,
        contentFingerprint = "svg-plan",
    )
    val glyphUploadPlan = GPUTextUploadPlan(
        artifactKey = glyphUploadKey,
        ranges = listOf(
            GPUTextUploadRange(offset = 0, size = 16, label = "glyph-header"),
            GPUTextUploadRange(offset = 16, size = 48, label = "glyph-masks"),
        ),
        byteSize = 64,
    )
    val atlasUploadPlan = GPUTextUploadPlan(
        artifactKey = atlasUploadKey,
        ranges = listOf(
            GPUTextUploadRange(offset = 0, size = 128, label = "atlas-page"),
        ),
        byteSize = 128,
    )
    return TextGPUArtifactBundle(
        artifactKey = rootKey,
        uploadPlans = listOf(glyphUploadPlan, atlasUploadPlan),
        glyphUploadPlans = listOf(
            GlyphUploadPlan(
                artifactKey = glyphUploadKey,
                uploadPlan = glyphUploadPlan,
                glyphIDs = listOf(7U, 8U, 9U),
            ),
        ),
        outlineGlyphPlans = listOf(
            OutlineGlyphPlan(
                artifactKey = outlineAKey,
                glyphIDs = listOf(7U),
                windingRule = "non-zero",
            ),
            OutlineGlyphPlan(
                artifactKey = outlineBKey,
                glyphIDs = listOf(8U),
                windingRule = "even-odd",
            ),
        ),
        colorGlyphPlans = listOf(
            ColorGlyphPlan(
                artifactKey = colorKey,
                glyphIDs = listOf(10U),
                layerCount = 2,
            ),
        ),
        bitmapGlyphPlans = listOf(
            BitmapGlyphPlan(
                artifactKey = bitmapKey,
                glyphIDs = listOf(11U),
                colorFormat = "rgba8888",
            ),
        ),
        svgGlyphPlans = listOf(
            SVGGlyphPlan(
                artifactKey = svgKey,
                glyphIDs = listOf(12U),
                documentCount = 1,
            ),
        ),
        atlases = listOf(
            GlyphAtlasArtifact(
                artifactKey = atlasKey,
                width = 128,
                height = 128,
                format = "r8",
            ),
        ),
        sdfAtlases = listOf(
            SDFGlyphAtlasArtifact(
                atlas = GlyphAtlasArtifact(
                    artifactKey = sdfAtlasKey,
                    width = 256,
                    height = 256,
                    format = "r8",
                ),
                distanceRange = 4.0f,
            ),
        ),
        diagnostics = GPUTextRouteDiagnostics(
            diagnostics = listOf(
                GPUTextArtifactDiagnostic(
                    code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
                    message = "Glyph 99 is missing.",
                ),
                GPUTextArtifactDiagnostic(
                    code = GPUTextArtifactDiagnosticCode.EXPLICIT_REFUSAL_REQUIRED,
                    message = "The route must refuse unsupported text.",
                ),
            ),
            refusalRequired = true,
        ),
    )
}

private fun defaultDrawTextRunPayloadFixture(): DrawTextRunPayload =
    DrawTextRunPayload(
        commandId = "draw-text-001",
        layoutResultID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441100")),
        glyphRunID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
        glyphRuns = listOf(
            GPUGlyphRunDescriptor(
                runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
                layoutResultID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441100")),
                glyphIDs = listOf(42, 43),
                advances = listOf(8.0f, 9.0f),
                offsets = emptyList(),
                textRangeStart = 0,
                textRangeEnd = 2,
                script = "Latn",
                bidiLevel = 0,
            ),
        ),
        artifacts = listOf(
            GPUTextArtifactReference(
                artifactName = "GlyphAtlasArtifact",
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441102")),
                generation = GPUTextArtifactGeneration(3),
                contentFingerprint = "sha256:a8-atlas",
                sourceLabel = "fixture.atlas",
                diagnostics = listOf("text.gpu.upload-plan-ready"),
            ),
        ),
        transform = TextTransformFacts("axis-aligned", "matrix:identity"),
        clip = TextClipFacts("rect", "0,0 64x32"),
        layer = TextLayerFacts("root", "none"),
        material = TextMaterialDescriptor("solid", "material:text-black"),
        blendColor = TextBlendColorFacts("src-over", "srgb-premul"),
        artifactKeyHashes = listOf("sha256:a8-atlas"),
        atlasGenerations = listOf(GPUTextArtifactGeneration(3)),
        uploadDependencies = listOf(
            GPUTextUploadDependencyRef(
                id = GPUTextUploadDependencyID(Uuid.parse("550e8400-e29b-41d4-a716-446655441110")),
                label = "upload-a8-page-0",
            ),
        ),
        diagnostics = emptyList(),
        provenance = TextEvidenceProvenance("fixture", "KFONT-M11-003"),
        routePromotion = "not-promoted",
        productActivation = false,
    )

private fun telemetryArtifactKey(
    uuid: String,
    generation: Int,
    contentFingerprint: String,
): GPUTextArtifactKey = GPUTextArtifactKey(
    artifactID = GPUTextArtifactID(Uuid.parse(uuid)),
    generation = GPUTextArtifactGeneration(generation),
    contentFingerprint = contentFingerprint,
)

private fun compactObject(entries: Map<String, Any?>): String = buildString {
    append("{")
    entries.entries.forEachIndexed { index, entry ->
        append(entry.key.compactQuoted())
        append(":")
        append(compactValue(entry.value))
        if (index < entries.size - 1) append(",")
    }
    append("}")
}

private fun compactValue(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> value.compactQuoted()
        is Boolean, is Int, is Long -> value.toString()
        is Map<*, *> -> compactObject(
            value.entries.associate { entry -> entry.key.toString() to entry.value }
                .toMap(LinkedHashMap()),
        )
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { entry -> compactValue(entry) }
        else -> error("Unsupported compact JSON value: ${value::class}")
    }

private fun StringBuilder.appendCompactField(name: String, value: String, comma: Boolean) {
    append(name.compactQuoted())
    append(": ")
    append(value.compactQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendCompactField(name: String, value: Int, comma: Boolean) {
    append(name.compactQuoted())
    append(": ")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendCompactField(name: String, value: Boolean, comma: Boolean) {
    append(name.compactQuoted())
    append(": ")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendCompactNoSpaceField(name: String, value: String, comma: Boolean) {
    append(name.compactQuoted())
    append(":")
    append(value.compactQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendCompactNoSpaceField(name: String, value: Int, comma: Boolean) {
    append(name.compactQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendCompactNoSpaceField(name: String, value: Boolean, comma: Boolean) {
    append(name.compactQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendCompactStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.compactQuoted())
    append(": ")
    append(values.joinToString(prefix = "[", postfix = "]") { value -> value.compactQuoted() })
    if (comma) append(",")
}

private fun StringBuilder.appendCompactNoSpaceStringListField(name: String, values: List<String>, comma: Boolean) {
    append(name.compactQuoted())
    append(":")
    append(values.joinToString(prefix = "[", postfix = "]") { value -> value.compactQuoted() })
    if (comma) append(",")
}

private fun StringBuilder.appendCompactRawListField(name: String, values: List<String>, comma: Boolean) {
    append(name.compactQuoted())
    append(": ")
    append(values.joinToString(prefix = "[", postfix = "]"))
    if (comma) append(",")
}

private fun String.compactQuoted(): String = "\"${compactEscapeJson()}\""

private fun String.compactEscapeJson(): String = buildString(length) {
    for (ch in this@compactEscapeJson) {
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
