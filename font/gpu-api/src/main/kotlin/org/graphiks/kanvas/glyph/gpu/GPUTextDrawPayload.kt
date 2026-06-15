package org.graphiks.kanvas.glyph.gpu

data class TextTransformFacts(
    val transformClass: String,
    val matrixLabel: String,
)

data class TextClipFacts(
    val clipKind: String,
    val boundsLabel: String,
)

data class TextLayerFacts(
    val layerKind: String,
    val layerLabel: String,
)

data class TextMaterialDescriptor(
    val materialKind: String,
    val materialKey: String,
)

data class TextBlendColorFacts(
    val blendMode: String,
    val colorSpace: String,
)

data class TextEvidenceProvenance(
    val source: String,
    val ticket: String,
)

class DrawTextRunPayload(
    val commandId: String,
    val layoutResultID: GPUTextLayoutResultID?,
    val glyphRunID: GPUGlyphRunID?,
    glyphRuns: List<GPUGlyphRunDescriptor>,
    artifacts: List<GPUTextArtifactReference>,
    val transform: TextTransformFacts,
    val clip: TextClipFacts,
    val layer: TextLayerFacts,
    val material: TextMaterialDescriptor,
    val blendColor: TextBlendColorFacts,
    artifactKeyHashes: List<String>,
    atlasGenerations: List<GPUTextArtifactGeneration>,
    uploadDependencyIds: List<String>,
    diagnostics: List<String>,
    val provenance: TextEvidenceProvenance,
    val routePromotion: String = DRAW_TEXT_RUN_NOT_PROMOTED,
    val productActivation: Boolean = false,
) {
    val glyphRuns: List<GPUGlyphRunDescriptor> = glyphRuns.map { glyphRun -> glyphRun.snapshotForDrawPayload() }
    val artifacts: List<GPUTextArtifactReference> = artifacts.toList()
    val artifactKeyHashes: List<String> = artifactKeyHashes.toList()
    val atlasGenerations: List<GPUTextArtifactGeneration> = atlasGenerations.toList()
    val uploadDependencyIds: List<String> = uploadDependencyIds.toList()
    val diagnostics: List<String> = diagnostics.toList()

    init {
        require(commandId.isNotBlank()) { "commandId must not be blank." }
        require(layoutResultID != null || glyphRunID != null) {
            "DrawTextRunPayload must reference a layout result ID or glyph run ID."
        }
        require(routePromotion == DRAW_TEXT_RUN_NOT_PROMOTED) {
            "DrawTextRunPayload cannot promote renderer text routes."
        }
        require(!productActivation) {
            "DrawTextRunPayload does not activate product renderer support."
        }
        this.glyphRuns.forEach { glyphRun -> glyphRun.requireFiniteCoordinates() }
    }

    fun noSkLeakageReport(): TextPayloadLeakReport = validateGPUTextNoSkLeakage(
        payloadKind = "DrawTextRunPayload",
        fields = listOf(
            TextPayloadField("commandId", "String"),
            TextPayloadField("layoutResultID", "GPUTextLayoutResultID?"),
            TextPayloadField("glyphRunID", "GPUGlyphRunID?"),
            TextPayloadField("glyphRuns[].runID", "GPUGlyphRunID"),
            TextPayloadField("glyphRuns[].layoutResultID", "GPUTextLayoutResultID?"),
            TextPayloadField("glyphRuns[].typefaceID", "TypefaceID?"),
            TextPayloadField("glyphRuns[].glyphIDs", "List<Int>"),
            TextPayloadField("glyphRuns[].advances", "List<Float>"),
            TextPayloadField("glyphRuns[].offsets", "List<Float>"),
            TextPayloadField("glyphRuns[].textRangeStart", "Int"),
            TextPayloadField("glyphRuns[].textRangeEnd", "Int"),
            TextPayloadField("glyphRuns[].script", "String"),
            TextPayloadField("glyphRuns[].bidiLevel", "Int"),
            TextPayloadField("artifacts[].artifactName", "String"),
            TextPayloadField("artifacts[].artifactID", "GPUTextArtifactID"),
            TextPayloadField("artifacts[].generation", "GPUTextArtifactGeneration"),
            TextPayloadField("artifacts[].contentFingerprint", "String"),
            TextPayloadField("artifacts[].sourceLabel", "String"),
            TextPayloadField("transform", "TextTransformFacts"),
            TextPayloadField("clip", "TextClipFacts"),
            TextPayloadField("layer", "TextLayerFacts"),
            TextPayloadField("material", "TextMaterialDescriptor"),
            TextPayloadField("blendColor", "TextBlendColorFacts"),
            TextPayloadField("artifactKeyHashes", "List<String>"),
            TextPayloadField("atlasGenerations", "List<GPUTextArtifactGeneration>"),
            TextPayloadField("uploadDependencyIds", "List<String>"),
            TextPayloadField("diagnostics", "List<String>"),
            TextPayloadField("provenance", "TextEvidenceProvenance"),
            TextPayloadField("routePromotion", "String"),
            TextPayloadField("productActivation", "Boolean"),
        ),
    )

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendDrawTextRunJsonField("schema", DRAW_TEXT_RUN_PAYLOAD_SCHEMA, comma = true)
        appendDrawTextRunJsonField("commandId", commandId, comma = true)
        appendDrawTextRunJsonNullableField("layoutResultID", layoutResultID?.value?.toString(), comma = true)
        appendDrawTextRunJsonNullableField("glyphRunID", glyphRunID?.value?.toString(), comma = true)
        append("\"glyphRuns\":")
        append(glyphRuns.joinToString(separator = ",", prefix = "[", postfix = "]") { glyphRun ->
            glyphRun.toDrawTextRunCanonicalJson()
        })
        append(",")
        append("\"artifacts\":")
        append(artifacts.joinToString(separator = ",", prefix = "[", postfix = "]") { artifact ->
            artifact.toDrawTextRunCanonicalJson()
        })
        append(",")
        appendDrawTextRunRawJsonField("transform", transform.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunRawJsonField("clip", clip.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunRawJsonField("layer", layer.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunRawJsonField("material", material.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunRawJsonField("blendColor", blendColor.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunStringListJsonField("artifactKeyHashes", artifactKeyHashes, comma = true)
        appendDrawTextRunIntListJsonField(
            "atlasGenerations",
            atlasGenerations.map { generation -> generation.value },
            comma = true,
        )
        appendDrawTextRunStringListJsonField("uploadDependencyIds", uploadDependencyIds, comma = true)
        appendDrawTextRunStringListJsonField("diagnostics", diagnostics, comma = true)
        appendDrawTextRunRawJsonField("provenance", provenance.toDrawTextRunCanonicalJson(), comma = true)
        appendDrawTextRunJsonField("routePromotion", routePromotion, comma = true)
        appendDrawTextRunJsonField("productActivation", productActivation, comma = false)
        append("}")
    }
}

private const val DRAW_TEXT_RUN_PAYLOAD_SCHEMA =
    "org.graphiks.kanvas.glyph.gpu.DrawTextRunPayload.v1"
private const val DRAW_TEXT_RUN_NOT_PROMOTED = "not-promoted"

private fun GPUGlyphRunDescriptor.snapshotForDrawPayload(): GPUGlyphRunDescriptor = copy(
    glyphIDs = glyphIDs.toList(),
    advances = advances.toList(),
    offsets = offsets.toList(),
)

private fun GPUGlyphRunDescriptor.requireFiniteCoordinates() {
    require(advances.all { advance -> !advance.isNaN() && !advance.isInfinite() }) {
        "glyph run advances must be finite."
    }
    require(offsets.all { offset -> !offset.isNaN() && !offset.isInfinite() }) {
        "glyph run offsets must be finite."
    }
}

private fun GPUGlyphRunDescriptor.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("runID", runID.value.toString(), comma = true)
    appendDrawTextRunJsonNullableField("layoutResultID", layoutResultID?.value?.toString(), comma = true)
    appendDrawTextRunJsonNullableField("typefaceID", typefaceID?.value?.toString(), comma = true)
    appendDrawTextRunIntListJsonField("glyphIDs", glyphIDs, comma = true)
    appendDrawTextRunFloatListJsonField("advances", advances, comma = true)
    appendDrawTextRunFloatListJsonField("offsets", offsets, comma = true)
    appendDrawTextRunJsonField("textRangeStart", textRangeStart, comma = true)
    appendDrawTextRunJsonField("textRangeEnd", textRangeEnd, comma = true)
    appendDrawTextRunJsonField("script", script, comma = true)
    appendDrawTextRunJsonField("bidiLevel", bidiLevel, comma = false)
    append("}")
}

private fun GPUTextArtifactReference.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("artifactName", artifactName, comma = true)
    appendDrawTextRunJsonField("artifactID", artifactID.value.toString(), comma = true)
    appendDrawTextRunJsonField("generation", generation.value, comma = true)
    appendDrawTextRunJsonField("contentFingerprint", contentFingerprint, comma = true)
    appendDrawTextRunJsonField("sourceLabel", sourceLabel, comma = false)
    append("}")
}

private fun TextTransformFacts.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("transformClass", transformClass, comma = true)
    appendDrawTextRunJsonField("matrixLabel", matrixLabel, comma = false)
    append("}")
}

private fun TextClipFacts.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("clipKind", clipKind, comma = true)
    appendDrawTextRunJsonField("boundsLabel", boundsLabel, comma = false)
    append("}")
}

private fun TextLayerFacts.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("layerKind", layerKind, comma = true)
    appendDrawTextRunJsonField("layerLabel", layerLabel, comma = false)
    append("}")
}

private fun TextMaterialDescriptor.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("materialKind", materialKind, comma = true)
    appendDrawTextRunJsonField("materialKey", materialKey, comma = false)
    append("}")
}

private fun TextBlendColorFacts.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("blendMode", blendMode, comma = true)
    appendDrawTextRunJsonField("colorSpace", colorSpace, comma = false)
    append("}")
}

private fun TextEvidenceProvenance.toDrawTextRunCanonicalJson(): String = buildString {
    append("{")
    appendDrawTextRunJsonField("source", source, comma = true)
    appendDrawTextRunJsonField("ticket", ticket, comma = false)
    append("}")
}

private fun StringBuilder.appendDrawTextRunJsonField(
    name: String,
    value: String,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(drawTextRunJsonString(value))
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunJsonField(
    name: String,
    value: Int,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunJsonField(
    name: String,
    value: Boolean,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value)
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunStringListJsonField(
    name: String,
    value: List<String>,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value.joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> drawTextRunJsonString(entry) })
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunIntListJsonField(
    name: String,
    value: List<Int>,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value.joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> entry.toString() })
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunFloatListJsonField(
    name: String,
    value: List<Float>,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value.joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> entry.toString() })
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunRawJsonField(
    name: String,
    rawJsonValue: String,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(rawJsonValue)
    if (comma) append(",")
}

private fun StringBuilder.appendDrawTextRunJsonNullableField(
    name: String,
    value: String?,
    comma: Boolean,
) {
    append(drawTextRunJsonString(name))
    append(":")
    append(value?.let(::drawTextRunJsonString) ?: "null")
    if (comma) append(",")
}

private fun drawTextRunJsonString(value: String): String = buildString {
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
