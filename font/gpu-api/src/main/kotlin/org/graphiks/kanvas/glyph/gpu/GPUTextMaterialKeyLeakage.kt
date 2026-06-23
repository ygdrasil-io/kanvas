package org.graphiks.kanvas.glyph.gpu

class GPUTextMaterialKeyLeakageCase(
    val caseId: String,
    val scenarioLabel: String,
    val materialIdentifierLabel: String,
    val materialIdentifierValue: String,
    forbiddenFields: List<String>,
    val expectedLeakageStatus: String,
) {
    val forbiddenFields: List<String> = forbiddenFields.toList()

    init {
        require(caseId.isNotBlank()) { "caseId must not be blank." }
        require(scenarioLabel.isNotBlank()) { "scenarioLabel must not be blank." }
        require(materialIdentifierLabel.isNotBlank()) { "materialIdentifierLabel must not be blank." }
        require(materialIdentifierValue.isNotBlank()) { "materialIdentifierValue must not be blank." }
        require(forbiddenFields.isNotEmpty()) { "forbiddenFields must not be empty." }
        require(expectedLeakageStatus == "clean" || expectedLeakageStatus == "leak-detected") {
            "expectedLeakageStatus must be clean or leak-detected."
        }
    }

    val actualLeakageStatus: String
        get() = if (hasMaterialKeyLeak()) "leak-detected" else "clean"

    fun hasMaterialKeyLeak(): Boolean =
        forbiddenFields.any { field ->
            materialIdentifierValue.normalizedMaterialKeyField().contains(field.normalizedMaterialKeyField())
        }

    private fun String.normalizedMaterialKeyField(): String =
        filter { char -> char.isLetterOrDigit() }.lowercase()

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendMaterialKeyLeakJsonField("caseId", caseId, comma = true)
        appendMaterialKeyLeakJsonField("scenarioLabel", scenarioLabel, comma = true)
        appendMaterialKeyLeakJsonField("materialIdentifierLabel", materialIdentifierLabel, comma = true)
        appendMaterialKeyLeakJsonField("materialIdentifierValue", materialIdentifierValue, comma = true)
        appendMaterialKeyLeakStringListField("forbiddenFields", forbiddenFields, comma = true)
        appendMaterialKeyLeakJsonField("expectedLeakageStatus", expectedLeakageStatus, comma = true)
        appendMaterialKeyLeakJsonField("actualLeakageStatus", actualLeakageStatus, comma = true)
        appendMaterialKeyLeakJsonField("leakFree", !hasMaterialKeyLeak(), comma = false)
        append("}")
    }
}

data class GPUTextMaterialKeyLeakFinding(
    val caseId: String,
    val scenarioLabel: String,
    val leakedField: String,
    val materialIdentifierLabel: String,
    val materialIdentifierValue: String,
    val diagnostic: String,
) {
    init {
        require(caseId.isNotBlank()) { "caseId must not be blank." }
        require(scenarioLabel.isNotBlank()) { "scenarioLabel must not be blank." }
        require(leakedField.isNotBlank()) { "leakedField must not be blank." }
        require(materialIdentifierLabel.isNotBlank()) { "materialIdentifierLabel must not be blank." }
        require(materialIdentifierValue.isNotBlank()) { "materialIdentifierValue must not be blank." }
        require(diagnostic.isNotBlank()) { "diagnostic must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendMaterialKeyLeakJsonField("caseId", caseId, comma = true)
        appendMaterialKeyLeakJsonField("scenarioLabel", scenarioLabel, comma = true)
        appendMaterialKeyLeakJsonField("leakedField", leakedField, comma = true)
        appendMaterialKeyLeakJsonField("materialIdentifierLabel", materialIdentifierLabel, comma = true)
        appendMaterialKeyLeakJsonField("materialIdentifierValue", materialIdentifierValue, comma = true)
        appendMaterialKeyLeakJsonField("diagnostic", diagnostic, comma = false)
        append("}")
    }
}

class GPUTextMaterialKeyLeakageReport(
    cases: List<GPUTextMaterialKeyLeakageCase>,
    leakageFindings: List<GPUTextMaterialKeyLeakFinding>,
) {
    val cases: List<GPUTextMaterialKeyLeakageCase> = cases.toList()
    val leakageFindings: List<GPUTextMaterialKeyLeakFinding> = leakageFindings.toList()
    val totalCases: Int = cases.size
    val cleanCases: Int = cases.count { case -> !case.hasMaterialKeyLeak() }
    val leakDetectedCases: Int = cases.count { case -> case.hasMaterialKeyLeak() }
    val status: String = if (leakageFindings.isEmpty()) "all-clean" else "leaks-detected"

    init {
        require(cases.isNotEmpty()) { "cases must not be empty." }
        require(cases.map { c -> c.caseId }.distinct().size == cases.size) {
            "case IDs must be unique."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendMaterialKeyLeakJsonField("schema", MATERIAL_KEY_LEAKAGE_REPORT_SCHEMA, comma = true)
        appendMaterialKeyLeakStringListField("ownerTickets", listOf("KFONT-M11-010"), comma = true)
        appendMaterialKeyLeakJsonField("classification", "GPU-gated", comma = true)
        appendMaterialKeyLeakJsonField("status", status, comma = true)
        appendMaterialKeyLeakJsonField("totalCases", totalCases, comma = true)
        appendMaterialKeyLeakJsonField("cleanCases", cleanCases, comma = true)
        appendMaterialKeyLeakJsonField("leakDetectedCases", leakDetectedCases, comma = true)
        append("\"cases\":")
        append(cases.joinToString(separator = ",", prefix = "[", postfix = "]") { c -> c.toCanonicalJson() })
        append(",")
        append("\"leakageFindings\":")
        append(
            leakageFindings.joinToString(separator = ",", prefix = "[", postfix = "]") { f ->
                f.toCanonicalJson()
            },
        )
        append(",")
        appendMaterialKeyLeakStringListField("nonClaims", MATERIAL_KEY_LEAKAGE_NON_CLAIMS, comma = true)
        appendMaterialKeyLeakJsonField("routePromotion", "not-promoted", comma = true)
        appendMaterialKeyLeakJsonField("productActivation", false, comma = false)
        append("}\n")
    }
}

fun validateGPUTextMaterialKeyLeakage(
    cases: List<GPUTextMaterialKeyLeakageCase>,
): GPUTextMaterialKeyLeakageReport {
    val scannedCases = cases.toList()
    val findings = scannedCases.flatMap { case ->
        case.forbiddenFields
            .filter { field -> case.materialIdentifierValue.normalizedMaterialKeyField().contains(field.normalizedMaterialKeyField()) }
            .map { leakedField ->
                GPUTextMaterialKeyLeakFinding(
                    caseId = case.caseId,
                    scenarioLabel = case.scenarioLabel,
                    leakedField = leakedField,
                    materialIdentifierLabel = case.materialIdentifierLabel,
                    materialIdentifierValue = case.materialIdentifierValue,
                    diagnostic = "text.gpu.material-key-field-leaked:$leakedField",
                )
            }
    }
    return GPUTextMaterialKeyLeakageReport(
        cases = scannedCases,
        leakageFindings = findings,
    )
}

private fun String.normalizedMaterialKeyField(): String =
    filter { char -> char.isLetterOrDigit() }.lowercase()

private const val MATERIAL_KEY_LEAKAGE_REPORT_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.GPUTextMaterialKeyLeakageReport.v1"

private val MATERIAL_KEY_LEAKAGE_NON_CLAIMS = listOf(
    "no-complete-target-support-claim",
    "no-broad-gpu-text-support-claim",
    "no-dftext-retirement",
    "no-visual-correctness-claim",
    "no-executed-gpu-upload-claim",
)

private fun StringBuilder.appendMaterialKeyLeakJsonField(name: String, value: String, comma: Boolean) {
    append(name.materialKeyLeakQuoted())
    append(":")
    append(value.materialKeyLeakQuoted())
    if (comma) append(",")
}

private fun StringBuilder.appendMaterialKeyLeakJsonField(name: String, value: Int, comma: Boolean) {
    append(name.materialKeyLeakQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendMaterialKeyLeakJsonField(name: String, value: Boolean, comma: Boolean) {
    append(name.materialKeyLeakQuoted())
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendMaterialKeyLeakStringListField(
    name: String,
    values: List<String>,
    comma: Boolean,
) {
    append(name.materialKeyLeakQuoted())
    append(":")
    append(
        values.joinToString(separator = ",", prefix = "[", postfix = "]") { value ->
            value.materialKeyLeakQuoted()
        },
    )
    if (comma) append(",")
}

private fun String.materialKeyLeakQuoted(): String = "\"${materialKeyLeakEscapeJson()}\""

private fun String.materialKeyLeakEscapeJson(): String = buildString(length) {
    for (ch in this@materialKeyLeakEscapeJson) {
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
