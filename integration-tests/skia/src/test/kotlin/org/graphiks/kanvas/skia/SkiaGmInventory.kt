package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import java.io.File
import java.util.Locale

enum class InventorySetupState { NOT_ATTEMPTED, SUCCEEDED, FAILED }

/** Renderer observations supplied by the inventory replay; deliberately has no fallback semantics. */
data class InventoryRenderEvidence(
    val attempted: Boolean,
    val renderSucceeded: Boolean,
    val terminalFailure: Boolean,
    val operationCount: Int,
    val diagnostics: List<String> = emptyList(),
    val route: String = "gpu",
    val setupState: InventorySetupState = InventorySetupState.SUCCEEDED,
    val setupDiagnostic: String? = null,
    val conformanceDecision: GmConformanceDecision = GmConformanceDecision(GmConformanceScope.ELIGIBLE),
) {
    init {
        require(operationCount >= 0) { "operationCount must be non-negative" }
        when (setupState) {
            InventorySetupState.NOT_ATTEMPTED -> require(!attempted && !renderSucceeded && !terminalFailure) {
                "A non-attempted setup cannot have render evidence"
            }
            InventorySetupState.SUCCEEDED -> {
                if (conformanceDecision.mustAttempt) {
                    require(attempted) { "A must-attempt setup must reach Surface.render()" }
                    require(renderSucceeded.xor(terminalFailure)) {
                        "A render attempt must either succeed or terminally fail"
                    }
                } else {
                    require(!attempted && !renderSucceeded && !terminalFailure) {
                        "An excluded setup cannot have render evidence"
                    }
                }
                require(setupDiagnostic == null) { "A successful setup cannot have a setup diagnostic" }
            }
            InventorySetupState.FAILED -> require(!attempted && !renderSucceeded && !terminalFailure) {
                "A setup failure cannot be a Surface.render failure"
            }
        }
        require(!terminalFailure || attempted && !renderSucceeded && setupState == InventorySetupState.SUCCEEDED) {
            "terminalFailure is reserved for a failed Surface.render() attempt"
        }
    }
}

data class SkiaGmInventoryRow(
    val name: String,
    val family: String,
    val referenceName: String,
    val referenceAvailable: Boolean,
    val renderAvailable: Boolean,
    val attempted: Boolean,
    val terminalFailure: Boolean,
    val score: Double?,
    val operationCount: Int?,
    val route: String,
    val firstDiagnostic: String?,
    val referenceStatus: String,
    val setupState: InventorySetupState = InventorySetupState.NOT_ATTEMPTED,
    val setupDiagnostic: String? = null,
    val conformanceDecision: GmConformanceDecision = GmConformanceDecision(GmConformanceScope.ELIGIBLE),
) {
    val conformanceScope: String get() = conformanceDecision.scope.wireName
    val conformanceReason: String? get() = conformanceDecision.reason
    val conformanceOwner: String? get() = conformanceDecision.owner

    init {
        operationCount?.let { require(it >= 0) { "operationCount must be non-negative" } }
        if (attempted) {
            require(renderAvailable.xor(terminalFailure)) {
                "An attempted inventory row must either render or terminally fail"
            }
            require(setupState == InventorySetupState.SUCCEEDED) {
                "An attempted inventory row requires successful setup"
            }
        }
        require(!terminalFailure || attempted && !renderAvailable) {
            "terminalFailure is reserved for a failed render attempt"
        }
    }
}

data class SkiaGmScoreAudit(val orphanRows: List<String>, val strict: Boolean)

internal fun loadSkiaGmScores(file: File, registeredNames: Set<String>, allowOrphans: Boolean = false): Map<String, Double> {
    require(file.exists()) { "Scores file not found: ${file.path}" }
    val scores = linkedMapOf<String, Double>()
    file.forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val separator = line.indexOf('=')
        require(separator > 0) { "Malformed score row: $line" }
        val name = line.substring(0, separator).trim()
        require(name.isNotEmpty()) { "Empty score name" }
        require(name !in scores) { "Duplicate score row: $name" }
        if (!allowOrphans) require(name in registeredNames) { "Orphan score row: $name" }
        val value = line.substring(separator + 1).trim().toDoubleOrNull()
        require(value != null && value.isFinite()) { "Invalid score for $name" }
        scores[name] = value
    }
    return scores
}

internal fun auditSkiaGmScores(file: File, registeredNames: Set<String>): SkiaGmScoreAudit {
    require(file.exists()) { "Scores file not found: ${file.path}" }
    val orphans = file.readLines().asSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { it.substringBefore('=', "").trim().takeIf(String::isNotEmpty) }
        .filter { it !in registeredNames }.distinct().sorted().toList()
    return SkiaGmScoreAudit(orphans, strict = orphans.isEmpty())
}

fun buildSkiaGmInventory(
    gms: List<SkiaGm>,
    referenceDir: File,
    scoresFile: File,
    renderEvidence: Map<String, InventoryRenderEvidence> = emptyMap(),
    allowOrphanScores: Boolean = false,
): List<SkiaGmInventoryRow> {
    val names = gms.map { it.name }
    require(names.size == names.toSet().size) { "Duplicate registered GM names" }
    require(renderEvidence.keys.all { it in names }) { "Orphan render evidence rows" }
    val scores = loadSkiaGmScores(scoresFile, names.toSet(), allowOrphanScores)
    return gms.map { gm ->
        val evidence = renderEvidence[gm.name]
        val conformanceDecision = evidence?.conformanceDecision ?: SkiaGmConformance.decisionFor(gm)
        SkiaGmInventoryRow(
            name = gm.name,
            family = gm.renderFamily.name,
            referenceName = gm.referenceName,
            referenceAvailable = referenceDir.resolve("${gm.referenceName}.png").isFile && !gm.referenceStatus.untrustable,
            renderAvailable = evidence?.renderSucceeded == true,
            attempted = evidence?.attempted == true,
            terminalFailure = evidence?.terminalFailure == true,
            score = scores[gm.name],
            operationCount = evidence?.operationCount,
            route = evidence?.route ?: "unobserved",
            firstDiagnostic = evidence?.setupDiagnostic ?: evidence?.diagnostics?.firstOrNull(),
            referenceStatus = when {
                !referenceDir.resolve("${gm.referenceName}.png").isFile -> "missing"
                gm.referenceStatus.untrustable -> "untrustable"
                else -> "trusted"
            },
            setupState = evidence?.setupState ?: InventorySetupState.NOT_ATTEMPTED,
            setupDiagnostic = evidence?.setupDiagnostic,
            conformanceDecision = conformanceDecision,
        )
    }
}

fun renderSkiaGmInventoryJson(rows: List<SkiaGmInventoryRow>, scoreAudit: SkiaGmScoreAudit = SkiaGmScoreAudit(emptyList(), true)): String = buildString {
    val orphanRows = scoreAudit.orphanRows.joinToString(",") { "\"${inventoryJsonEscape(it)}\"" }
    appendLine("{")
    appendLine("  \"schemaVersion\": \"gpu-gm-inventory-v3\",")
    appendLine("  \"scoreAudit\": {\"strict\": ${scoreAudit.strict}, \"orphanCount\": ${scoreAudit.orphanRows.size}, \"orphanRows\": [$orphanRows]},")
    appendLine("  \"summary\": ${inventorySummaryJson(rows)},")
    appendLine("  \"rows\": [")
    rows.forEachIndexed { index, row ->
        val comma = if (index + 1 == rows.size) "" else ","
        val score = row.score?.let { String.format(Locale.US, "%.10f", it) } ?: "null"
        val operationCount = row.operationCount?.toString() ?: "null"
        val firstDiagnostic = row.firstDiagnostic?.let { "\"${inventoryJsonEscape(it)}\"" } ?: "null"
        appendLine("    {")
        appendLine("      \"name\": \"${inventoryJsonEscape(row.name)}\",")
        appendLine("      \"family\": \"${row.family}\",")
        appendLine("      \"referenceName\": \"${inventoryJsonEscape(row.referenceName)}\",")
        appendLine("      \"referenceStatus\": \"${row.referenceStatus}\",")
        appendLine("      \"referenceAvailable\": ${row.referenceAvailable},")
        appendLine("      \"renderAvailable\": ${row.renderAvailable},")
        appendLine("      \"attempted\": ${row.attempted},")
        appendLine("      \"terminalFailure\": ${row.terminalFailure},")
        appendLine("      \"score\": $score,")
        appendLine("      \"operationCount\": $operationCount,")
        appendLine("      \"route\": \"${inventoryJsonEscape(row.route)}\",")
        appendLine("      \"firstDiagnostic\": $firstDiagnostic,")
        appendLine("      \"setupState\": \"${row.setupState.name}\",")
        appendLine("      \"setupDiagnostic\": ${row.setupDiagnostic?.let { "\"${inventoryJsonEscape(it)}\"" } ?: "null"},")
        appendLine("      \"conformanceScope\": \"${row.conformanceScope}\",")
        appendLine("      \"conformanceReason\": ${row.conformanceReason?.let { "\"${inventoryJsonEscape(it)}\"" } ?: "null"},")
        appendLine("      \"conformanceOwner\": ${row.conformanceOwner?.let { "\"${inventoryJsonEscape(it)}\"" } ?: "null"}")
        appendLine("    }$comma")
    }
    appendLine("  ]")
    appendLine("}")
}.trimEnd()

private fun inventorySummaryJson(rows: List<SkiaGmInventoryRow>): String {
    fun countsJson(counts: Map<String, Int>): String = counts.toSortedMap().entries.joinToString(",") {
        "\"${inventoryJsonEscape(it.key)}\": ${it.value}"
    }.let { "{$it}" }

    val scopes = rows.groupingBy { it.conformanceScope }.eachCount()
    val families = rows.groupingBy { it.family }.eachCount()
    val terminalDiagnostics = rows.asSequence().filter { it.terminalFailure }.mapNotNull { it.firstDiagnostic }
        .groupingBy { it }.eachCount()
    return "{\"registeredCount\": ${rows.size},\"mustAttemptCount\": ${rows.count { it.conformanceDecision.mustAttempt }},\"byScope\": ${countsJson(scopes)},\"byFamily\": ${countsJson(families)},\"terminalDiagnostics\": ${countsJson(terminalDiagnostics)}}"
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: SkiaGmInventory <output.json>" }
    RuntimeEffectWgsl4kWiring.install()
    try {
        val entries = SkiaGmRegistry.entries()
        val rows = entries.mapNotNull { it.gm }
        val evidence = rows.associate { gm -> gm.name to SkiaGmRenderer.inventoryEvidence(gm) }
        val scoreFile = File("test-similarity-scores.properties")
        val scoreAudit = auditSkiaGmScores(scoreFile, rows.map { it.name }.toSet())
        val inventory = buildSkiaGmInventory(
            gms = rows,
            referenceDir = File("src/test/resources/reference"),
            scoresFile = scoreFile,
            renderEvidence = evidence,
            allowOrphanScores = false,
        )
        require(scoreAudit.strict) {
            "Orphan GM scores: ${scoreAudit.orphanRows.joinToString()}"
        }
        val failedProviders = entries.filter { it.gm == null }
        val allRows = inventory + failedProviders.map(::providerUnloadableInventoryRow)
        writeSkiaGmInventoryJson(File(args[0]), allRows, scoreAudit)
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

internal fun writeSkiaGmInventoryJson(
    output: File,
    rows: List<SkiaGmInventoryRow>,
    scoreAudit: SkiaGmScoreAudit = SkiaGmScoreAudit(emptyList(), true),
) {
    output.parentFile?.mkdirs()
    output.writeText(renderSkiaGmInventoryJson(rows, scoreAudit) + "\n")
}

internal fun providerUnloadableInventoryRow(entry: SkiaGmRegistry.Entry): SkiaGmInventoryRow {
    require(entry.gm == null) { "Provider ${entry.provider} is loadable" }
    return SkiaGmInventoryRow(
        name = entry.provider,
        family = "UNKNOWN",
        referenceName = entry.provider,
        referenceAvailable = false,
        renderAvailable = false,
        attempted = false,
        terminalFailure = false,
        score = null,
        operationCount = 0,
        route = "provider-unloadable",
        firstDiagnostic = entry.diagnostic,
        referenceStatus = "missing",
        setupState = InventorySetupState.FAILED,
        setupDiagnostic = entry.diagnostic,
    )
}

private fun inventoryJsonEscape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001f' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
}
