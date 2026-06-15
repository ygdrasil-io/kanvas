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
    val value: String? = null,
) {
    init {
        require(fieldPath.isNotBlank()) { "fieldPath must not be blank." }
        require(typeName.isNotBlank()) { "typeName must not be blank." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTextPayloadLeakJsonField("fieldPath", fieldPath, comma = true)
        appendTextPayloadLeakJsonField("typeName", typeName, comma = value != null)
        value?.let { payloadValue ->
            appendTextPayloadLeakJsonField("value", payloadValue, comma = false)
        }
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
class TextPayloadLeakReport(
    val payloadKind: String,
    scannedFields: List<TextPayloadField>,
    leakFindings: List<TextPayloadLeakFinding>,
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
                .joinToString(separator = ",", prefix = "[", postfix = "]") { field ->
                    field.toCanonicalJson()
                },
        )
        append(",")
        append("\"findings\":")
        append(
            findings
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
            field.hasNondumpablePayloadMarker() -> field.toLeakFinding(
                payloadKind = payloadKind,
                forbiddenKind = "nondumpable-payload",
                handoffDiagnostic = TEXT_GPU_PAYLOAD_NONDUMPABLE_HANDOFF_DIAGNOSTIC,
                rendererDiagnostic = TEXT_GPU_PAYLOAD_NONDUMPABLE_RENDERER_DIAGNOSTIC,
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
private const val TEXT_GPU_PAYLOAD_NONDUMPABLE_HANDOFF_DIAGNOSTIC =
    "text.gpu.payload-nondumpable"
private const val TEXT_GPU_PAYLOAD_NONDUMPABLE_RENDERER_DIAGNOSTIC =
    "unsupported.text.payload_nondumpable"

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
    scanValues().any { scanValue ->
        scanValue.raw.containsGenericSkTypeMarker() ||
            SK_TEXT_PAYLOAD_MARKERS.any { marker -> scanValue.normalized.contains(marker) }
    }

private fun TextPayloadField.hasCPURenderedTextTextureMarker(): Boolean {
    return normalizedScanValues().any { normalizedValue ->
        normalizedValue.contains("cpurenderedtexttexture")
    }
}

private fun TextPayloadField.hasNondumpablePayloadMarker(): Boolean {
    return normalizedScanValues().any { normalizedValue ->
        normalizedValue.contains("nondumpable")
    }
}

private fun TextPayloadField.hasForbiddenPayloadMarker(): Boolean {
    return normalizedScanValues().any { normalizedValue ->
        FORBIDDEN_TEXT_PAYLOAD_MARKERS.any { marker -> normalizedValue.contains(marker) }
    }
}

private val SK_TEXT_PAYLOAD_MARKERS = listOf(
    "skfont",
    "sktypeface",
    "sktextblob",
    "skpaint",
    "skpath",
    "skshaper",
)

private val FORBIDDEN_TEXT_PAYLOAD_MARKERS = listOf(
    "fontbytes",
    "nativefonthandle",
    "gpuhandle",
)

private data class TextPayloadScanValue(
    val raw: String,
    val normalized: String,
)

private fun TextPayloadField.scanValues(): List<TextPayloadScanValue> =
    listOfNotNull(fieldPath, typeName, value).map { scanValue ->
        TextPayloadScanValue(
            raw = scanValue,
            normalized = scanValue.normalizedTextPayloadMarker(),
        )
    }

private fun TextPayloadField.normalizedScanValues(): List<String> =
    scanValues().map { scanValue ->
        scanValue.normalized
    }

private fun String.normalizedTextPayloadMarker(): String =
    filter { char -> char.isLetterOrDigit() }.lowercase()

private fun String.containsGenericSkTypeMarker(): Boolean =
    indices.any { index ->
        index + 2 < length &&
            this[index] == 'S' &&
            this[index + 1] == 'k' &&
            this[index + 2].isUpperCase()
    }

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
