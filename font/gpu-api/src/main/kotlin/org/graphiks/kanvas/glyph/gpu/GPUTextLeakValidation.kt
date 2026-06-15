package org.graphiks.kanvas.glyph.gpu

/**
 * Explicit field entry scanned by the GPU text handoff leakage validator.
 *
 * This validator intentionally works from caller-supplied field facts instead
 * of reflection so tests and future PM evidence can name the exact payload
 * contract under review.
 */
data class TextPayloadField(
    val fieldPath: String,
    val typeName: String,
) {
    init {
        require(fieldPath.isNotBlank()) { "fieldPath must not be blank." }
        require(typeName.isNotBlank()) { "typeName must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextPayloadLeakJsonField("fieldPath", fieldPath, comma = true)
        appendTextPayloadLeakJsonField("typeName", typeName, comma = false)
        append("}")
    }
}

/**
 * Stable finding for a forbidden text payload field.
 */
data class TextPayloadLeakFinding(
    val payloadKind: String,
    val fieldPath: String,
    val typeName: String,
    val forbiddenKind: String,
    val handoffDiagnostic: String,
    val rendererDiagnostic: String,
) {
    init {
        require(payloadKind.isNotBlank()) { "payloadKind must not be blank." }
        require(fieldPath.isNotBlank()) { "fieldPath must not be blank." }
        require(typeName.isNotBlank()) { "typeName must not be blank." }
        require(forbiddenKind.isNotBlank()) { "forbiddenKind must not be blank." }
        require(handoffDiagnostic.isNotBlank()) { "handoffDiagnostic must not be blank." }
        require(rendererDiagnostic.isNotBlank()) { "rendererDiagnostic must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextPayloadLeakJsonField("payloadKind", payloadKind, comma = true)
        appendTextPayloadLeakJsonField("fieldPath", fieldPath, comma = true)
        appendTextPayloadLeakJsonField("typeName", typeName, comma = true)
        appendTextPayloadLeakJsonField("forbiddenKind", forbiddenKind, comma = true)
        appendTextPayloadLeakJsonField("handoffDiagnostic", handoffDiagnostic, comma = true)
        appendTextPayloadLeakJsonField("rendererDiagnostic", rendererDiagnostic, comma = false)
        append("}")
    }
}

/**
 * Deterministic no-Sk leakage report for one GPU text payload shape.
 */
data class TextPayloadLeakReport(
    val payloadKind: String,
    private val scannedFields: List<TextPayloadField>,
    private val leakFindings: List<TextPayloadLeakFinding>,
) {
    val fields: List<TextPayloadField> = scannedFields.toList()
    val findings: List<TextPayloadLeakFinding> = leakFindings.toList()
    val status: String = if (findings.isEmpty()) "pass" else "fail"

    init {
        require(payloadKind.isNotBlank()) { "payloadKind must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextPayloadLeakJsonField("schema", TEXT_PAYLOAD_LEAK_REPORT_SCHEMA, comma = true)
        appendTextPayloadLeakJsonField("payloadKind", payloadKind, comma = true)
        appendTextPayloadLeakJsonField("status", status, comma = true)
        append("\"fields\":")
        append(
            fields
                .sortedWith(textPayloadFieldComparator)
                .joinToString(separator = ",", prefix = "[", postfix = "]") { field ->
                    field.toCanonicalJson()
                },
        )
        append(",")
        append("\"findings\":")
        append(
            findings
                .sortedWith(textPayloadLeakFindingComparator)
                .joinToString(separator = ",", prefix = "[", postfix = "]") { finding ->
                    finding.toCanonicalJson()
                },
        )
        append("}")
    }
}

/**
 * Validates an explicit GPU text payload field list against the M11 no-Sk
 * boundary rule.
 */
fun validateGPUTextNoSkLeakage(
    payloadKind: String,
    fields: List<TextPayloadField>,
): TextPayloadLeakReport {
    val scannedFields = fields.toList()
    val findings = scannedFields.mapNotNull { field ->
        when {
            field.hasCPURenderedTextTextureMarker() -> field.toLeakFinding(
                payloadKind = payloadKind,
                forbiddenKind = "cpu-rendered-texture",
                handoffDiagnostic = TEXT_GPU_CPU_RENDERED_TEXTURE_HANDOFF_DIAGNOSTIC,
                rendererDiagnostic = TEXT_GPU_CPU_RENDERED_TEXTURE_RENDERER_DIAGNOSTIC,
            )
            field.hasSkTypeLeak() || field.hasForbiddenPayloadMarker() -> field.toLeakFinding(
                payloadKind = payloadKind,
                forbiddenKind = "sk-type-or-handle",
                handoffDiagnostic = TEXT_GPU_SK_TYPE_LEAKED_HANDOFF_DIAGNOSTIC,
                rendererDiagnostic = TEXT_GPU_SK_TYPE_LEAKED_RENDERER_DIAGNOSTIC,
            )
            else -> null
        }
    }

    return TextPayloadLeakReport(
        payloadKind = payloadKind,
        scannedFields = scannedFields,
        leakFindings = findings,
    )
}

private const val TEXT_PAYLOAD_LEAK_REPORT_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1"
private const val TEXT_GPU_SK_TYPE_LEAKED_HANDOFF_DIAGNOSTIC =
    "text.gpu.sk-type-leaked"
private const val TEXT_GPU_SK_TYPE_LEAKED_RENDERER_DIAGNOSTIC =
    "unsupported.text.sk_type_leaked"
private const val TEXT_GPU_CPU_RENDERED_TEXTURE_HANDOFF_DIAGNOSTIC =
    "text.gpu.CPU-rendered-texture-forbidden"
private const val TEXT_GPU_CPU_RENDERED_TEXTURE_RENDERER_DIAGNOSTIC =
    "unsupported.text.cpu_rendered_texture_forbidden"

private val textPayloadFieldComparator = compareBy<TextPayloadField>(
    { field -> field.fieldPath },
    { field -> field.typeName },
)

private val textPayloadLeakFindingComparator = compareBy<TextPayloadLeakFinding>(
    { finding -> finding.fieldPath },
    { finding -> finding.typeName },
    { finding -> finding.handoffDiagnostic },
    { finding -> finding.rendererDiagnostic },
)

private fun TextPayloadField.toLeakFinding(
    payloadKind: String,
    forbiddenKind: String,
    handoffDiagnostic: String,
    rendererDiagnostic: String,
): TextPayloadLeakFinding = TextPayloadLeakFinding(
    payloadKind = payloadKind,
    fieldPath = fieldPath,
    typeName = typeName,
    forbiddenKind = forbiddenKind,
    handoffDiagnostic = handoffDiagnostic,
    rendererDiagnostic = rendererDiagnostic,
)

private fun TextPayloadField.hasSkTypeLeak(): Boolean =
    typeName.startsWith("Sk") || typeName.contains(".Sk")

private fun TextPayloadField.hasCPURenderedTextTextureMarker(): Boolean {
    val normalizedType = typeName.normalizedTextPayloadMarker()
    val normalizedPath = fieldPath.normalizedTextPayloadMarker()
    return normalizedType.contains("cpurenderedtexttexture") ||
        normalizedPath.contains("cpurenderedtexttexture")
}

private fun TextPayloadField.hasForbiddenPayloadMarker(): Boolean {
    val normalizedType = typeName.normalizedTextPayloadMarker()
    val normalizedPath = fieldPath.normalizedTextPayloadMarker()
    return FORBIDDEN_TEXT_PAYLOAD_MARKERS.any { marker ->
        normalizedType.contains(marker) || normalizedPath.contains(marker)
    }
}

private val FORBIDDEN_TEXT_PAYLOAD_MARKERS = listOf(
    "fontbytes",
    "nativefonthandle",
    "gpuhandle",
)

private fun String.normalizedTextPayloadMarker(): String =
    filter { char -> char.isLetterOrDigit() }.lowercase()

private fun StringBuilder.appendTextPayloadLeakJsonField(
    name: String,
    value: String,
    comma: Boolean,
) {
    append(textPayloadLeakJsonString(name))
    append(":")
    append(textPayloadLeakJsonString(value))
    if (comma) append(",")
}

private fun textPayloadLeakJsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}
