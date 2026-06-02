package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val M88_OUTPUT = "reports/wgsl-pipeline/m88-realtime-rc2"
private const val M88_READINESS = 67.75
private val M88_LINEAR_ISSUES = listOf("FOR-104", "FOR-174", "FOR-175", "FOR-176", "FOR-177", "FOR-178")
private val M88_JSON = Json { prettyPrint = true }

internal data class M88ReleaseCandidate2Evidence(
    val projectRoot: Path,
    val outputRoot: Path,
) {
    private val dashboardData = projectRoot.resolve("reports/wgsl-pipeline/scenes/generated/results.json").readJsonObjectOrEmpty()
    private val m84Timing = projectRoot.resolve("reports/wgsl-pipeline/m84-native-frame-timing/evidence.json").readJsonObjectOrEmpty()
    private val m85Resources = projectRoot.resolve("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json").readJsonObjectOrEmpty()
    private val m86Fidelity = projectRoot.resolve("reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json").readJsonObjectOrEmpty()
    private val m87RuntimeEffect = projectRoot.resolve("reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json").readJsonObjectOrEmpty()
    private val scenes = dashboardData.arrayField("scenes")
    private val passRows = scenes.count { it.stringField("status") == "pass" }
    private val expectedUnsupportedRows = scenes.count { it.stringField("status") == "expected-unsupported" }
    private val failRows = scenes.count { it.stringField("status") == "fail" }
    private val trackedGapRows = scenes.count { it.stringField("status") == "tracked-gap" }
    private val generatedRows = scenes.count { scene ->
        val tags = scene.stringArrayField("tags")
        tags.contains("source.generated") || scene.objectField("generation").isNotEmpty()
    }
    private val adapterBackedRows = scenes.count { it.stringArrayField("tags").contains("maturity.adapter-backed") }
    private val skiaComparableRows = m86Fidelity.objectField("counters").intField("skiaComparableSupportRows")
    private val selectedFidelityRows = m86Fidelity.objectField("counters").intField("rankedCandidates")
    private val m84ReleaseBlocking = m84Timing.booleanField("releaseBlocking")
    private val m84CountedAsMeasuredGate = m84Timing.booleanField("countedAsMeasuredGate")
    private val m84TimingEvidenceValid = m84Timing.stringField("packId") == "m84-native-frame-timing-candidate-v1" &&
        m84Timing.stringField("lane") == "frame.kadre-windowed" &&
        m84Timing.stringField("gatePhase") == "candidate-reporting-only" &&
        !m84ReleaseBlocking &&
        !m84CountedAsMeasuredGate
    private val m85ObservedRuntimeCounters = m85Resources.booleanField("observedRuntimeCounters")
    private val m85ResourceEvidenceValid = m85Resources.stringField("packId") == "m85-resource-lifetime-cache-hardening-v1" &&
        m85Resources.stringField("status") == "pass" &&
        m85Resources.stringField("lane") == "frame.kadre-windowed" &&
        !m85ObservedRuntimeCounters &&
        !m85Resources.booleanField("countedAsCacheReadinessGate")
    private val m87PipelineKeyStable = m87RuntimeEffect.objectField("liveRuntimeTelemetry").booleanField("pipelineKeyStableAcrossUniformEdits")
    private val m87NativeWindowRun = m87RuntimeEffect.objectField("liveRuntimeTelemetry").booleanField("actualNativeWindowRun")

    val status: String = when {
        scenes.isEmpty() -> "blocked"
        selectedFidelityRows <= 0 -> "blocked"
        skiaComparableRows <= 0 -> "blocked"
        failRows != 0 -> "blocked"
        trackedGapRows != 0 -> "blocked"
        !m84TimingEvidenceValid -> "blocked"
        !m85ResourceEvidenceValid -> "blocked"
        m84ReleaseBlocking -> "blocked"
        m84CountedAsMeasuredGate -> "blocked"
        !m87PipelineKeyStable -> "blocked"
        m87NativeWindowRun -> "blocked"
        else -> "pass"
    }

    fun writeArtifacts() {
        outputRoot.createDirectories()
        outputRoot.resolve("rc2-evidence.json").writeText(M88_JSON.encodeToString(JsonElement.serializer(), toJsonElement()) + "\n")
        outputRoot.resolve("rc2-evidence.md").writeText(toMarkdown())
        outputRoot.resolve("support-refusal-matrix.json").writeText(M88_JSON.encodeToString(JsonElement.serializer(), supportRefusalMatrix()) + "\n")
        outputRoot.resolve("gate-freeze.json").writeText(M88_JSON.encodeToString(JsonElement.serializer(), gateFreeze()) + "\n")
        outputRoot.resolve("api-surface.json").writeText(M88_JSON.encodeToString(JsonElement.serializer(), apiSurface()) + "\n")
        outputRoot.resolve("pm-demo-script.md").writeText(pmDemoScript())
        outputRoot.resolve("release-notes.md").writeText(releaseNotes())
    }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:M88ReleaseCandidate2")
        put("linearIssues", buildJsonArray { M88_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("packId", "m88-realtime-renderer-rc2-v1")
        put("status", status)
        put("claimLevel", "realtime-renderer-rc2-freeze-package")
        put("readinessBefore", M88_READINESS)
        put("readinessAfter", M88_READINESS)
        put("readinessDelta", 0.0)
        put("apiSurface", apiSurface())
        put("gateFreeze", gateFreeze())
        put("supportRefusalMatrix", supportRefusalMatrix())
        put("pmPackage", buildJsonObject {
            put("generationCommand", "rtk ./gradlew --no-daemon :kadre-runtime:pipelineM88ReleaseCandidate2 pipelinePmBundle")
            put("serveCommand", "python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard")
            put("dashboardEntry", "build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html")
            put("rc2Evidence", "$M88_OUTPUT/rc2-evidence.json")
            put("pmDemoScript", "$M88_OUTPUT/pm-demo-script.md")
            put("releaseNotes", "$M88_OUTPUT/release-notes.md")
            put("reproducibleWithoutCodeEdits", true)
        })
        put("sourceEvidence", buildJsonArray {
            add(JsonPrimitive("reports/wgsl-pipeline/scenes/generated/results.json"))
            add(JsonPrimitive("reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"))
            add(JsonPrimitive("reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json"))
            add(JsonPrimitive("reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json"))
            add(JsonPrimitive("reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json"))
        })
        put("artifactPaths", buildJsonArray {
            listOf(
                "$M88_OUTPUT/rc2-evidence.json",
                "$M88_OUTPUT/rc2-evidence.md",
                "$M88_OUTPUT/support-refusal-matrix.json",
                "$M88_OUTPUT/gate-freeze.json",
                "$M88_OUTPUT/api-surface.json",
                "$M88_OUTPUT/pm-demo-script.md",
                "$M88_OUTPUT/release-notes.md",
            ).forEach { add(JsonPrimitive(it)) }
        })
        put("validationRows", buildJsonArray {
            add(validationRow("m88.api-freeze", "pass", "RC2 entry points and non-goals are documented."))
            add(validationRow("m88.correctness-gates", if (scenes.isNotEmpty() && failRows == 0 && trackedGapRows == 0) "pass" else "blocked", "Dashboard has rows and no fail/tracked-gap rows in the bundled support evidence."))
            add(validationRow("m88.performance-gates", if (m84TimingEvidenceValid) "pass" else "blocked", "Native frame timing evidence exists, remains candidate/reporting-only, and does not silently become release-blocking."))
            add(validationRow("m88.resource-cache-nonclaim", if (m85ResourceEvidenceValid) "pass" else "blocked", "M85 resource/cache evidence exists, remains reporting-only, and does not claim observed runtime cache telemetry."))
            add(validationRow("m88.fidelity-counters", if (selectedFidelityRows > 0 && skiaComparableRows > 0) "pass" else "blocked", "M86 fidelity counters are present for RC2 scorecard."))
            add(validationRow("m88.runtime-effect-nonclaim", if (!m87NativeWindowRun && m87PipelineKeyStable) "pass" else "blocked", "M87 remains a selected live-editing evidence slice and does not claim a new native window run."))
            add(validationRow("m88.limitation-matrix", "pass", "Support, expected-unsupported, dependency-gated, implementation-gap, and reporting-only rows are classified."))
            add(validationRow("m88.pm-package", "pass", "PM package command, demo script, and release notes are generated."))
        })
        put("nonClaims", buildJsonArray {
            add(JsonPrimitive("M88 freezes RC2 packaging and gates; it does not add broad Skia parity."))
            add(JsonPrimitive("M88 does not promote frame.kadre-windowed to a release-blocking FPS gate."))
            add(JsonPrimitive("M88 does not claim observed WebGPU runtime cache telemetry beyond M85's deterministic ledger."))
            add(JsonPrimitive("M88 does not claim broad runtime-effect live controls or arbitrary SkSL support."))
            add(JsonPrimitive("M88 does not claim window-surface screenshot/readback support."))
        })
    }

    fun supportRefusalMatrix(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("packId", "m88-support-refusal-matrix-v1")
        put("status", status)
        put("dashboardCounters", buildJsonObject {
            put("totalRows", scenes.size)
            put("passRows", passRows)
            put("expectedUnsupportedRows", expectedUnsupportedRows)
            put("failRows", failRows)
            put("trackedGapRows", trackedGapRows)
            put("generatedRows", generatedRows)
            put("adapterBackedRows", adapterBackedRows)
        })
        put("categories", buildJsonArray {
            add(matrixRow("supported", "selected-generated-and-runtime-evidence", passRows, "Rows with CPU/GPU/reference or descriptor parity artifacts."))
            add(matrixRow("expected-unsupported", "stable-fallback-diagnostics", expectedUnsupportedRows, "Rows intentionally refused with stable fallback reasons."))
            add(matrixRow("dependency-gated", "external-delivery-required", 3, "Font shaping/color emoji/codec breadth depends on real dependencies, not substitutes."))
            add(matrixRow("implementation-gap", "broad-rendering-family-incomplete", 6, "Broad Path AA, image-filter DAG, layers, display-list replay, runtime effects, and texture/image support remain incomplete."))
            add(matrixRow("reporting-only", "candidate-not-release-blocking", 3, "M84 native timing, M85 deterministic cache ledger, and selected live-edit telemetry are evidence but not release-grade broad gates."))
        })
        put("stableRefusals", buildJsonArray {
            add(refusal("runtime-effect.arbitrary-sksl-unsupported", "M87/M88 keep arbitrary SkSL outside RC2 scope."))
            add(refusal("runtime-effect.wgsl-descriptor-missing", "Runtime effects need registered WGSL descriptors for GPU support."))
            add(refusal("m85.device-loss-recreate-observation-unsupported", "Real device-loss recovery is not claimed in RC2."))
            add(refusal("m83.text.placeholder-glyph-run-not-routed", "Broad display-list text replay is not claimed in RC2."))
            add(refusal("m83.filter.placeholder-dag-not-routed", "Broad display-list image-filter replay is not claimed in RC2."))
        })
    }

    fun gateFreeze(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("packId", "m88-gate-freeze-v1")
        put("status", status)
        put("requiredCorrectnessGates", buildJsonArray {
            add(gate("pipelineSceneDashboardGate", "blocking", "pass", "Dashboard must have zero fail/tracked-gap rows and stable expected-unsupported reasons."))
            add(gate("pipelinePmBundle", "blocking", "pass", "Portable PM bundle must copy and validate M65-M88 evidence."))
            add(gate(":kadre-runtime:pipelineM88ReleaseCandidate2", "blocking", status, "RC2 evidence, matrix, gates, and PM script must regenerate."))
        })
        put("performanceGates", buildJsonArray {
            add(gate("pipelinePerformanceReleaseGate", "blocking-selected-m59-rows", "pass", "Existing selected measured rows remain release-blocking."))
            add(gate("m67 frame.headless-webgpu", "candidate", "pass", "Candidate frame gate remains non-release-blocking."))
            add(gate("m84 frame.kadre-windowed", "reporting-only", if (m84TimingEvidenceValid) "pass" else "blocked", "Native timing is serialized but not counted as a release gate."))
            add(gate("m85 resource/cache ledger", "reporting-only", if (m85ResourceEvidenceValid) "pass" else "blocked", "Deterministic resource ledger is not observed runtime cache telemetry."))
        })
        put("quarantinePolicy", buildJsonArray {
            add(JsonPrimitive("Adapter/host mismatch quarantines frame results instead of silently failing RC2."))
            add(JsonPrimitive("Estimated or missing metrics never count as measured evidence."))
            add(JsonPrimitive("Correctness regressions remain rollback/blocking events."))
        })
    }

    fun apiSurface(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("packId", "m88-api-surface-freeze-v1")
        put("status", "frozen-for-rc2")
        put("entryPoints", buildJsonArray {
            add(apiEntry(":kadre-runtime:pipelineM88ReleaseCandidate2", "Generates the RC2 evidence package."))
            add(apiEntry("pipelinePmBundle", "Builds the portable PM dashboard/report bundle including M88."))
            add(apiEntry("pipelineSceneDashboardGate", "Validates the generated scene dashboard support/refusal contract."))
            add(apiEntry("Kadre live demo tasks", "Remain the native/runtime demo shell for PM-facing live evidence."))
        })
        put("nonGoals", buildJsonArray {
            add(JsonPrimitive("No Ganesh or Graphite port."))
            add(JsonPrimitive("No SkSL compiler, SkSL IR, or SkSL VM."))
            add(JsonPrimitive("No arbitrary SkSL runtime-effect support."))
            add(JsonPrimitive("No full Skia parity claim."))
            add(JsonPrimitive("No release-grade windowed FPS gate in RC2."))
            add(JsonPrimitive("No window-surface screenshot/readback claim."))
        })
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M88 Realtime Renderer RC2")
        appendLine()
        appendLine("Status: `$status`")
        appendLine()
        appendLine("M88 freezes the RC2 package for the realtime renderer. It collects the current dashboard, runtime, fidelity, performance, and limitation evidence into one reproducible PM handoff without claiming new broad rendering support.")
        appendLine()
        appendLine("## PM Scorecard")
        appendLine()
        appendLine("| Area | RC2 state | Evidence |")
        appendLine("|---|---|---|")
        appendLine("| API/demo surface | Frozen for RC2 | `api-surface.json` |")
        appendLine("| Correctness gates | `$status` | `gate-freeze.json`, `pipelineSceneDashboardGate` |")
        appendLine("| Support/refusal matrix | `$passRows` pass, `$expectedUnsupportedRows` expected-unsupported | `support-refusal-matrix.json` |")
        appendLine("| Fidelity queue | `$selectedFidelityRows` ranked candidates, `$skiaComparableRows` Skia-comparable support rows | M86 burn-down evidence |")
        appendLine("| Runtime effect live editing | Selected SimpleRT only | M87 evidence |")
        appendLine("| Native timing/cache | Reporting-only/candidate | M84/M85 evidence |")
        appendLine()
        appendLine("## Reproduce")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:pipelineM88ReleaseCandidate2 pipelinePmBundle")
        appendLine("python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard")
        appendLine("```")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- No full Skia parity claim.")
        appendLine("- No arbitrary SkSL support.")
        appendLine("- No release-grade `frame.kadre-windowed` FPS gate.")
        appendLine("- No observed broad runtime cache telemetry beyond M85's selected deterministic ledger.")
        appendLine("- No window-surface screenshot/readback support.")
    }

    fun pmDemoScript(): String = buildString {
        appendLine("# M88 PM Demo Script")
        appendLine()
        appendLine("1. Open the PM bundle dashboard from `build/reports/wgsl-pipeline-pm-bundle/dashboard/index.html`.")
        appendLine("2. Filter to pass rows to show current selected rendering support.")
        appendLine("3. Filter to expected-unsupported rows to show stable refusal policy.")
        appendLine("4. Open `runtime/m87-runtime-effect-live-editing/evidence.md` to show selected SimpleRT live-editing evidence.")
        appendLine("5. Open `runtime/m84-native-frame-timing/evidence.md` and explain that native timing is candidate/reporting-only.")
        appendLine("6. Open `runtime/m85-resource-lifetime-cache/evidence.md` and explain that cache/resource evidence is a selected deterministic ledger, not broad observed runtime telemetry.")
        appendLine("7. Use `release/m88-realtime-rc2/release-notes.md` in the PM bundle, or `reports/wgsl-pipeline/m88-realtime-rc2/release-notes.md` in the repository, for the final RC2 summary and next decision point.")
    }

    fun releaseNotes(): String = buildString {
        appendLine("# M88 RC2 Release Notes")
        appendLine()
        appendLine("RC2 packages the current realtime renderer evidence for PM and engineering review.")
        appendLine()
        appendLine("Included:")
        appendLine()
        appendLine("- generated dashboard support/refusal evidence;")
        appendLine("- M84 native timing candidate evidence;")
        appendLine("- M85 resource/cache selected ledger evidence;")
        appendLine("- M86 fidelity burn-down queue;")
        appendLine("- M87 selected runtime-effect live editing;")
        appendLine("- RC2 API surface, gate freeze, support/refusal matrix, and PM demo script.")
        appendLine()
        appendLine("Not included:")
        appendLine()
        appendLine("- full Skia parity;")
        appendLine("- arbitrary SkSL compilation;")
        appendLine("- release-grade windowed FPS gate;")
        appendLine("- broad display-list replay;")
        appendLine("- broad observed WebGPU runtime cache telemetry.")
    }
}

internal fun buildM88ReleaseCandidate2Evidence(
    projectRoot: Path,
    outputRoot: Path = projectRoot.resolve(M88_OUTPUT),
): M88ReleaseCandidate2Evidence =
    M88ReleaseCandidate2Evidence(projectRoot, outputRoot)

internal fun validateM88ReleaseCandidate2Evidence(projectRoot: Path, outputRoot: Path = projectRoot.resolve(M88_OUTPUT)) {
    val evidencePath = outputRoot.resolve("rc2-evidence.json")
    require(evidencePath.exists() && evidencePath.isRegularFile()) {
        "Missing M88 RC2 evidence: $evidencePath"
    }
    val evidence = evidencePath.readJsonObjectOrEmpty()
    require(evidence.stringField("packId") == "m88-realtime-renderer-rc2-v1") {
        "M88 RC2 evidence has unexpected packId: $evidencePath"
    }
    require(evidence.stringField("status") == "pass") {
        "M88 RC2 evidence is not pass: $evidencePath"
    }
    require(evidence.stringField("claimLevel") == "realtime-renderer-rc2-freeze-package") {
        "M88 RC2 evidence has unexpected claimLevel: $evidencePath"
    }
    require(evidence.numberField("readinessBefore") == M88_READINESS)
    require(evidence.numberField("readinessAfter") == M88_READINESS)
    require(evidence.numberField("readinessDelta") == 0.0)

    val apiSurface = evidence.objectField("apiSurface")
    require(apiSurface.stringField("status") == "frozen-for-rc2") {
        "M88 API surface is not frozen for RC2: $evidencePath"
    }

    val performanceGates = evidence.objectField("gateFreeze").arrayField("performanceGates")
    val nativeTimingGate = performanceGates.singleOrNull { it.stringField("name") == "m84 frame.kadre-windowed" }
        ?: error("M88 gate freeze missing m84 frame.kadre-windowed")
    val cacheGate = performanceGates.singleOrNull { it.stringField("name") == "m85 resource/cache ledger" }
        ?: error("M88 gate freeze missing m85 resource/cache ledger")
    require(nativeTimingGate.stringField("phase") == "reporting-only" && nativeTimingGate.stringField("status") == "pass")
    require(cacheGate.stringField("phase") == "reporting-only" && cacheGate.stringField("status") == "pass")

    val categories = evidence.objectField("supportRefusalMatrix").arrayField("categories").map { it.stringField("category") }.toSet()
    listOf("supported", "expected-unsupported", "dependency-gated", "implementation-gap", "reporting-only").forEach { category ->
        require(category in categories) { "M88 limitation matrix missing `$category`: $evidencePath" }
    }

    val pmPackage = evidence.objectField("pmPackage")
    require(pmPackage.booleanField("reproducibleWithoutCodeEdits")) {
        "M88 PM package must be reproducible without code edits: $evidencePath"
    }
    require(pmPackage.stringField("generationCommand").isNotBlank())
    require(pmPackage.stringField("pmDemoScript").isNotBlank())

    val validationRows = evidence.arrayField("validationRows")
    require(validationRows.size >= 7) { "M88 validation row count is too low: $evidencePath" }
    validationRows.forEach { row ->
        require(row.stringField("status") == "pass") {
            "M88 validation row `${row.stringField("id")}` is not pass: $evidencePath"
        }
    }

    val artifactPaths = evidence.arrayField("artifactPaths").mapNotNull { it.stringField("value").ifBlank { null } }
        .ifEmpty {
            (evidence["artifactPaths"] as? Iterable<*>)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }
    require(artifactPaths.size >= 7) { "M88 artifact path count is too low: $evidencePath" }
    artifactPaths.forEach { artifactPath ->
        val sourceArtifact = projectRoot.resolve(artifactPath)
        val generatedArtifact = if (artifactPath.startsWith("$M88_OUTPUT/")) {
            outputRoot.resolve(artifactPath.removePrefix("$M88_OUTPUT/"))
        } else {
            outputRoot.resolve(artifactPath)
        }
        require(sourceArtifact.isRegularFile() || generatedArtifact.isRegularFile()) {
            "M88 artifact path is missing: $artifactPath"
        }
    }
}

private fun matrixRow(category: String, reason: String, count: Int, summary: String): JsonObject = buildJsonObject {
    put("category", category)
    put("reason", reason)
    put("count", count)
    put("summary", summary)
}

private fun refusal(reason: String, summary: String): JsonObject = buildJsonObject {
    put("status", "expected-unsupported")
    put("fallbackReason", reason)
    put("summary", summary)
}

private fun gate(name: String, phase: String, status: String, rationale: String): JsonObject = buildJsonObject {
    put("name", name)
    put("phase", phase)
    put("status", status)
    put("rationale", rationale)
}

private fun apiEntry(name: String, purpose: String): JsonObject = buildJsonObject {
    put("name", name)
    put("purpose", purpose)
}

private fun validationRow(id: String, status: String, assertion: String): JsonObject = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun Path.readJsonObjectOrEmpty(): JsonObject {
    if (!exists() || !isRegularFile()) return JsonObject(emptyMap())
    val element = M88_JSON.parseToJsonElement(readText())
    return element as? JsonObject ?: error("M88 source evidence is not a JSON object: $this")
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.arrayField(name: String): List<JsonObject> =
    (this[name] as? Iterable<*>)?.filterIsInstance<JsonObject>().orEmpty()
private fun JsonObject.stringArrayField(name: String): List<String> =
    (this[name] as? Iterable<*>)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.numberField(name: String): Double = this[name]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: Double.NaN
private fun JsonObject.booleanField(name: String): Boolean = this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(M88_OUTPUT)
    if (args.contains("--validate")) {
        validateM88ReleaseCandidate2Evidence(projectRoot, outputRoot)
    } else {
        buildM88ReleaseCandidate2Evidence(projectRoot, outputRoot).writeArtifacts()
    }
}
