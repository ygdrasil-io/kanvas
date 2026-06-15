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
        fields = textPayloadLeakageFields(),
    )

    private fun textPayloadLeakageFields(): List<TextPayloadField> {
        val fields = mutableListOf<TextPayloadField>()
        fields += TextPayloadField("commandId", "String", commandId)
        fields += TextPayloadField("layoutResultID", "GPUTextLayoutResultID?", layoutResultID?.value?.toString())
        fields += TextPayloadField("glyphRunID", "GPUGlyphRunID?", glyphRunID?.value?.toString())
        fields += TextPayloadField("glyphRuns", "List<GPUGlyphRunDescriptor>")
        glyphRuns.forEachIndexed { index, glyphRun ->
            fields += TextPayloadField("glyphRuns[$index].runID", "GPUGlyphRunID", glyphRun.runID.value.toString())
            fields += TextPayloadField(
                "glyphRuns[$index].layoutResultID",
                "GPUTextLayoutResultID?",
                glyphRun.layoutResultID?.value?.toString(),
            )
            fields += TextPayloadField(
                "glyphRuns[$index].typefaceID",
                "TypefaceID?",
                glyphRun.typefaceID?.value?.toString(),
            )
            fields += TextPayloadField("glyphRuns[$index].glyphIDs", "List<Int>", glyphRun.glyphIDs.toLeakageListValue())
            fields += TextPayloadField("glyphRuns[$index].advances", "List<Float>", glyphRun.advances.toLeakageListValue())
            fields += TextPayloadField("glyphRuns[$index].offsets", "List<Float>", glyphRun.offsets.toLeakageListValue())
            fields += TextPayloadField("glyphRuns[$index].textRangeStart", "Int", glyphRun.textRangeStart.toString())
            fields += TextPayloadField("glyphRuns[$index].textRangeEnd", "Int", glyphRun.textRangeEnd.toString())
            fields += TextPayloadField("glyphRuns[$index].script", "String", glyphRun.script)
            fields += TextPayloadField("glyphRuns[$index].bidiLevel", "Int", glyphRun.bidiLevel.toString())
        }
        fields += TextPayloadField("artifacts", "List<GPUTextArtifactReference>")
        artifacts.forEachIndexed { index, artifact ->
            fields += TextPayloadField("artifacts[$index].artifactName", "String", artifact.artifactName)
            fields += TextPayloadField("artifacts[$index].artifactID", "GPUTextArtifactID", artifact.artifactID.value.toString())
            fields += TextPayloadField(
                "artifacts[$index].generation",
                "GPUTextArtifactGeneration",
                artifact.generation.value.toString(),
            )
            fields += TextPayloadField("artifacts[$index].contentFingerprint", "String", artifact.contentFingerprint)
            fields += TextPayloadField("artifacts[$index].sourceLabel", "String", artifact.sourceLabel)
        }
        fields += TextPayloadField("transform", "TextTransformFacts")
        fields += TextPayloadField("transform.transformClass", "String", transform.transformClass)
        fields += TextPayloadField("transform.matrixLabel", "String", transform.matrixLabel)
        fields += TextPayloadField("clip", "TextClipFacts")
        fields += TextPayloadField("clip.clipKind", "String", clip.clipKind)
        fields += TextPayloadField("clip.boundsLabel", "String", clip.boundsLabel)
        fields += TextPayloadField("layer", "TextLayerFacts")
        fields += TextPayloadField("layer.layerKind", "String", layer.layerKind)
        fields += TextPayloadField("layer.layerLabel", "String", layer.layerLabel)
        fields += TextPayloadField("material", "TextMaterialDescriptor")
        fields += TextPayloadField("material.materialKind", "String", material.materialKind)
        fields += TextPayloadField("material.materialKey", "String", material.materialKey)
        fields += TextPayloadField("blendColor", "TextBlendColorFacts")
        fields += TextPayloadField("blendColor.blendMode", "String", blendColor.blendMode)
        fields += TextPayloadField("blendColor.colorSpace", "String", blendColor.colorSpace)
        fields += TextPayloadField("artifactKeyHashes", "List<String>")
        artifactKeyHashes.forEachIndexed { index, hash ->
            fields += TextPayloadField("artifactKeyHashes[$index]", "String", hash)
        }
        fields += TextPayloadField("atlasGenerations", "List<GPUTextArtifactGeneration>")
        atlasGenerations.forEachIndexed { index, generation ->
            fields += TextPayloadField("atlasGenerations[$index]", "GPUTextArtifactGeneration", generation.value.toString())
        }
        fields += TextPayloadField("uploadDependencyIds", "List<String>")
        uploadDependencyIds.forEachIndexed { index, dependencyID ->
            fields += TextPayloadField("uploadDependencyIds[$index]", "String", dependencyID)
        }
        fields += TextPayloadField("diagnostics", "List<String>")
        diagnostics.forEachIndexed { index, diagnostic ->
            fields += TextPayloadField("diagnostics[$index]", "String", diagnostic)
        }
        fields += TextPayloadField("provenance", "TextEvidenceProvenance")
        fields += TextPayloadField("provenance.source", "String", provenance.source)
        fields += TextPayloadField("provenance.ticket", "String", provenance.ticket)
        fields += TextPayloadField("routePromotion", "String", routePromotion)
        fields += TextPayloadField("productActivation", "Boolean", productActivation.toString())
        return fields
    }

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

private fun <T> List<T>.toLeakageListValue(): String =
    joinToString(separator = ",", prefix = "[", postfix = "]") { entry -> entry.toString() }

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
