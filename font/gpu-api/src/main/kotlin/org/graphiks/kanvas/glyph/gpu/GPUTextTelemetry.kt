package org.graphiks.kanvas.glyph.gpu

/**
 * Minimal sample metadata for a GPU text artifact telemetry snapshot.
 *
 * This metadata is intentionally advisory-only. It records enough context to
 * make single-run evidence visible without promoting the sample into a release
 * gate.
 */
data class GPUTextTelemetrySampleMetadata(
    val environmentLabel: String,
    val sampleLabel: String,
    val sampleCount: Int,
    val cacheState: String,
    val releaseGatePromoted: Boolean = false,
) {
    init {
        requireNonBlank(environmentLabel, "environmentLabel")
        requireNonBlank(sampleLabel, "sampleLabel")
        requireNonBlank(cacheState, "cacheState")
        requireNonNegative(sampleCount, "sampleCount")
        require(!releaseGatePromoted) {
            "GPU text telemetry snapshots are advisory and must not be promoted as release gates in this contract."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("environmentLabel", environmentLabel, comma = true)
        appendTextTelemetryJsonField("sampleLabel", sampleLabel, comma = true)
        appendTextTelemetryJsonField("sampleCount", sampleCount, comma = true)
        appendTextTelemetryJsonField("cacheState", cacheState, comma = true)
        appendTextTelemetryJsonField("releaseGatePromoted", releaseGatePromoted, comma = false)
        append("\n}")
    }
}

/**
 * Stable counters derived from a [TextGPUArtifactBundle].
 */
data class GPUTextTelemetryCounters(
    val artifactReferenceCount: Int,
    val uploadPlanCount: Int,
    val uploadBytes: Int,
    val uploadRangeCount: Int,
    val glyphUploadPlanCount: Int,
    val glyphCount: Int,
    val a8AtlasCount: Int,
    val sdfAtlasCount: Int,
    val outlinePlanCount: Int,
    val colorPlanCount: Int,
    val bitmapPlanCount: Int,
    val svgPlanCount: Int,
    val diagnosticCount: Int,
    val refusalRequired: Int,
) {
    init {
        requireNonNegative(artifactReferenceCount, "artifactReferenceCount")
        requireNonNegative(uploadPlanCount, "uploadPlanCount")
        requireNonNegative(uploadBytes, "uploadBytes")
        requireNonNegative(uploadRangeCount, "uploadRangeCount")
        requireNonNegative(glyphUploadPlanCount, "glyphUploadPlanCount")
        requireNonNegative(glyphCount, "glyphCount")
        requireNonNegative(a8AtlasCount, "a8AtlasCount")
        requireNonNegative(sdfAtlasCount, "sdfAtlasCount")
        requireNonNegative(outlinePlanCount, "outlinePlanCount")
        requireNonNegative(colorPlanCount, "colorPlanCount")
        requireNonNegative(bitmapPlanCount, "bitmapPlanCount")
        requireNonNegative(svgPlanCount, "svgPlanCount")
        requireNonNegative(diagnosticCount, "diagnosticCount")
        require(refusalRequired == 0 || refusalRequired == 1) {
            "refusalRequired must be encoded as 0 or 1."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("artifactReferenceCount", artifactReferenceCount, comma = true)
        appendTextTelemetryJsonField("uploadPlanCount", uploadPlanCount, comma = true)
        appendTextTelemetryJsonField("uploadBytes", uploadBytes, comma = true)
        appendTextTelemetryJsonField("uploadRangeCount", uploadRangeCount, comma = true)
        appendTextTelemetryJsonField("glyphUploadPlanCount", glyphUploadPlanCount, comma = true)
        appendTextTelemetryJsonField("glyphCount", glyphCount, comma = true)
        appendTextTelemetryJsonField("a8AtlasCount", a8AtlasCount, comma = true)
        appendTextTelemetryJsonField("sdfAtlasCount", sdfAtlasCount, comma = true)
        appendTextTelemetryJsonField("outlinePlanCount", outlinePlanCount, comma = true)
        appendTextTelemetryJsonField("colorPlanCount", colorPlanCount, comma = true)
        appendTextTelemetryJsonField("bitmapPlanCount", bitmapPlanCount, comma = true)
        appendTextTelemetryJsonField("svgPlanCount", svgPlanCount, comma = true)
        appendTextTelemetryJsonField("diagnosticCount", diagnosticCount, comma = true)
        appendTextTelemetryJsonField("refusalRequired", refusalRequired, comma = false)
        append("\n}")
    }
}

/**
 * CPU-side upload plan telemetry for one text artifact payload.
 *
 * This record describes planned bytes and ranges only. It does not claim that
 * a GPU upload happened.
 */
data class GPUTextCPUUploadTelemetryRecord(
    val artifactID: GPUTextArtifactID,
    val generation: GPUTextArtifactGeneration,
    val contentFingerprint: String,
    val byteSize: Int,
    val rangeCount: Int,
    val glyphCount: Int? = null,
) {
    init {
        requireNonNegative(generation.value, "generation")
        requireNonBlank(contentFingerprint, "contentFingerprint")
        requireNonNegative(byteSize, "byteSize")
        requireNonNegative(rangeCount, "rangeCount")
        glyphCount?.let { requireNonNegative(it, "glyphCount") }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("artifactID", artifactID.value.toHexDashString(), comma = true)
        appendTextTelemetryJsonField("generation", generation.value, comma = true)
        appendTextTelemetryJsonField("contentFingerprint", contentFingerprint, comma = true)
        appendTextTelemetryJsonField("byteSize", byteSize, comma = true)
        appendTextTelemetryJsonField("rangeCount", rangeCount, comma = true)
        appendTextTelemetryJsonNullableField("glyphCount", glyphCount, comma = false)
        append("\n}")
    }
}

/**
 * Future GPU upload telemetry supplied by a renderer integration.
 *
 * The font GPU API does not synthesize these records. Empty snapshots mean no
 * GPU upload facts were measured or supplied.
 */
data class GPUTextGPUUploadTelemetryRecord(
    val artifactID: GPUTextArtifactID,
    val generation: GPUTextArtifactGeneration,
    val contentFingerprint: String,
    val byteSize: Int,
    val rangeCount: Int,
    val glyphCount: Int? = null,
    val sourceLabel: String = "caller-supplied",
) {
    init {
        requireNonNegative(generation.value, "generation")
        requireNonBlank(contentFingerprint, "contentFingerprint")
        requireNonNegative(byteSize, "byteSize")
        requireNonNegative(rangeCount, "rangeCount")
        glyphCount?.let { requireNonNegative(it, "glyphCount") }
        requireNonBlank(sourceLabel, "sourceLabel")
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("artifactID", artifactID.value.toHexDashString(), comma = true)
        appendTextTelemetryJsonField("generation", generation.value, comma = true)
        appendTextTelemetryJsonField("contentFingerprint", contentFingerprint, comma = true)
        appendTextTelemetryJsonField("byteSize", byteSize, comma = true)
        appendTextTelemetryJsonField("rangeCount", rangeCount, comma = true)
        appendTextTelemetryJsonNullableField("glyphCount", glyphCount, comma = true)
        appendTextTelemetryJsonField("sourceLabel", sourceLabel, comma = false)
        append("\n}")
    }
}

/**
 * Caller-supplied cache telemetry.
 *
 * The key preimage is preserved for auditability. Creating a snapshot without
 * records leaves cache telemetry empty instead of inventing cache observations.
 */
data class GPUTextCacheTelemetryRecord(
    val cacheName: String,
    val keyPreimage: String,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val residentBytes: Long,
    val generationToken: String,
) {
    init {
        requireNonBlank(cacheName, "cacheName")
        requireNonBlank(keyPreimage, "keyPreimage")
        requireNonNegative(hits, "hits")
        requireNonNegative(misses, "misses")
        requireNonNegative(evictions, "evictions")
        requireNonNegative(residentBytes, "residentBytes")
        requireNonBlank(generationToken, "generationToken")
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("cacheName", cacheName, comma = true)
        appendTextTelemetryJsonField("keyPreimage", keyPreimage, comma = true)
        appendTextTelemetryJsonField("hits", hits, comma = true)
        appendTextTelemetryJsonField("misses", misses, comma = true)
        appendTextTelemetryJsonField("evictions", evictions, comma = true)
        appendTextTelemetryJsonField("residentBytes", residentBytes, comma = true)
        appendTextTelemetryJsonField("generationToken", generationToken, comma = false)
        append("\n}")
    }
}

/**
 * Non-blocking budget observation for advisory performance evidence.
 */
data class GPUTextAdvisoryBudgetRecord(
    val metricName: String,
    val budgetName: String,
    val observedValue: Long,
    val advisoryLimit: Long,
    val unit: String,
    val sampleCount: Int,
    val blockingGate: Boolean = false,
) {
    init {
        requireNonBlank(metricName, "metricName")
        requireNonBlank(budgetName, "budgetName")
        requireNonNegative(observedValue, "observedValue")
        requireNonNegative(advisoryLimit, "advisoryLimit")
        requireNonBlank(unit, "unit")
        requireNonNegative(sampleCount, "sampleCount")
        require(!blockingGate) {
            "GPU text advisory budgets must not be encoded as blocking performance gates."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        appendTextTelemetryJsonField("metricName", metricName, comma = true)
        appendTextTelemetryJsonField("budgetName", budgetName, comma = true)
        appendTextTelemetryJsonField("observedValue", observedValue, comma = true)
        appendTextTelemetryJsonField("advisoryLimit", advisoryLimit, comma = true)
        appendTextTelemetryJsonField("unit", unit, comma = true)
        appendTextTelemetryJsonField("sampleCount", sampleCount, comma = true)
        appendTextTelemetryJsonField("blockingGate", blockingGate, comma = false)
        append("\n}")
    }
}

/**
 * Canonical telemetry snapshot for a [TextGPUArtifactBundle].
 */
class GPUTextTelemetrySnapshot(
    val metadata: GPUTextTelemetrySampleMetadata,
    val counters: GPUTextTelemetryCounters,
    cpuUploadPlans: List<GPUTextCPUUploadTelemetryRecord>,
    gpuUploadFacts: List<GPUTextGPUUploadTelemetryRecord> = emptyList(),
    cacheRecords: List<GPUTextCacheTelemetryRecord> = emptyList(),
    advisoryBudgets: List<GPUTextAdvisoryBudgetRecord> = emptyList(),
) {
    val cpuUploadPlans: List<GPUTextCPUUploadTelemetryRecord> = cpuUploadPlans.toList()
    val gpuUploadFacts: List<GPUTextGPUUploadTelemetryRecord> =
        gpuUploadFacts.sortedWith(gpuUploadTelemetryRecordComparator)
    val cacheRecords: List<GPUTextCacheTelemetryRecord> =
        cacheRecords.sortedWith(cacheTelemetryRecordComparator)
    val advisoryBudgets: List<GPUTextAdvisoryBudgetRecord> =
        advisoryBudgets.sortedWith(advisoryBudgetRecordComparator)

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"metadata\": ")
        append(metadata.toCanonicalJson().prependIndent("  ").trimStart())
        append(",\n")
        append("  \"counters\": ")
        append(counters.toCanonicalJson().prependIndent("  ").trimStart())
        append(",\n")
        append("  \"cpuUploadPlans\": ")
        appendTextTelemetryJsonArray(cpuUploadPlans) { record -> record.toCanonicalJson() }
        append(",\n")
        append("  \"gpuUploadFacts\": ")
        appendTextTelemetryJsonArray(gpuUploadFacts) { record -> record.toCanonicalJson() }
        append(",\n")
        append("  \"cacheRecords\": ")
        appendTextTelemetryJsonArray(cacheRecords) { record -> record.toCanonicalJson() }
        append(",\n")
        append("  \"advisoryBudgets\": ")
        appendTextTelemetryJsonArray(advisoryBudgets) { record -> record.toCanonicalJson() }
        append("\n}\n")
    }
}

/**
 * Builds a deterministic advisory telemetry snapshot from this text artifact bundle.
 */
fun TextGPUArtifactBundle.telemetrySnapshot(
    metadata: GPUTextTelemetrySampleMetadata,
    cacheRecords: List<GPUTextCacheTelemetryRecord> = emptyList(),
    advisoryBudgets: List<GPUTextAdvisoryBudgetRecord> = emptyList(),
    gpuUploadFacts: List<GPUTextGPUUploadTelemetryRecord> = emptyList(),
): GPUTextTelemetrySnapshot {
    val uploadPlanSet = uploadPlans.toSet()
    glyphUploadPlans.forEach { glyphUploadPlan ->
        require(glyphUploadPlan.artifactKey == glyphUploadPlan.uploadPlan.artifactKey) {
            "GlyphUploadPlan artifactKey must match its nested uploadPlan artifactKey."
        }
        require(glyphUploadPlan.uploadPlan in uploadPlanSet) {
            "GlyphUploadPlan nested uploadPlan must belong to bundle uploadPlans."
        }
    }
    val glyphCountsByUploadKey = glyphUploadPlans
        .groupBy { glyphUploadPlan -> glyphUploadPlan.uploadPlan.artifactKey }
        .mapValues { (_, glyphUploadPlans) ->
            glyphUploadPlans.sumOf { glyphUploadPlan -> glyphUploadPlan.glyphIDs.size }
        }
    val cpuUploadRecords = uploadPlans.map { uploadPlan ->
        uploadPlan.requireTelemetryUploadPlanShape()
        GPUTextCPUUploadTelemetryRecord(
            artifactID = uploadPlan.artifactKey.artifactID,
            generation = uploadPlan.artifactKey.generation,
            contentFingerprint = uploadPlan.artifactKey.contentFingerprint,
            byteSize = uploadPlan.byteSize,
            rangeCount = uploadPlan.ranges.size,
            glyphCount = glyphCountsByUploadKey[uploadPlan.artifactKey],
        )
    }

    return GPUTextTelemetrySnapshot(
        metadata = metadata,
        counters = GPUTextTelemetryCounters(
            artifactReferenceCount = artifactReferences().size,
            uploadPlanCount = uploadPlans.size,
            uploadBytes = uploadPlans.sumOf { uploadPlan -> uploadPlan.byteSize },
            uploadRangeCount = uploadPlans.sumOf { uploadPlan -> uploadPlan.ranges.size },
            glyphUploadPlanCount = glyphUploadPlans.size,
            glyphCount = glyphUploadPlans.sumOf { glyphUploadPlan -> glyphUploadPlan.glyphIDs.size },
            a8AtlasCount = atlases.size,
            sdfAtlasCount = sdfAtlases.size,
            outlinePlanCount = outlineGlyphPlans.size,
            colorPlanCount = colorGlyphPlans.size,
            bitmapPlanCount = bitmapGlyphPlans.size,
            svgPlanCount = svgGlyphPlans.size,
            diagnosticCount = diagnostics.diagnostics.size,
            refusalRequired = if (diagnostics.refusalRequired) 1 else 0,
        ),
        cpuUploadPlans = cpuUploadRecords,
        gpuUploadFacts = gpuUploadFacts,
        cacheRecords = cacheRecords,
        advisoryBudgets = advisoryBudgets,
    )
}

private val cacheTelemetryRecordComparator = compareBy<GPUTextCacheTelemetryRecord>(
    { it.cacheName },
    { it.keyPreimage },
    { it.hits },
    { it.misses },
    { it.evictions },
    { it.residentBytes },
    { it.generationToken },
)

private val advisoryBudgetRecordComparator = compareBy<GPUTextAdvisoryBudgetRecord>(
    { it.metricName },
    { it.budgetName },
    { it.observedValue },
    { it.advisoryLimit },
    { it.unit },
    { it.sampleCount },
    { it.blockingGate },
)

private val gpuUploadTelemetryRecordComparator = compareBy<GPUTextGPUUploadTelemetryRecord>(
    { it.artifactID.value.toHexDashString() },
    { it.generation.value },
    { it.contentFingerprint },
    { it.byteSize },
    { it.rangeCount },
    { it.glyphCount },
    { it.sourceLabel },
)

private fun GPUTextUploadPlan.requireTelemetryUploadPlanShape() {
    requireNonNegative(byteSize, "byteSize")
    requireNonBlank(artifactKey.contentFingerprint, "contentFingerprint")
    requireNonNegative(artifactKey.generation.value, "generation")
    ranges.forEach { range ->
        requireNonNegative(range.offset, "uploadRange.offset")
        requireNonNegative(range.size, "uploadRange.size")
        requireNonBlank(range.label, "uploadRange.label")
        val start = range.offset.toLong()
        val end = start + range.size.toLong()
        require(end <= byteSize.toLong()) {
            "Upload range ${range.label} [$start, $end) exceeds byteSize $byteSize."
        }
    }
}

private fun requireNonBlank(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
}

private fun requireNonNegative(value: Int, label: String) {
    require(value >= 0) { "$label must be non-negative." }
}

private fun requireNonNegative(value: Long, label: String) {
    require(value >= 0L) { "$label must be non-negative." }
}

private fun <T> StringBuilder.appendTextTelemetryJsonArray(
    values: List<T>,
    render: (T) -> String,
) {
    if (values.isEmpty()) {
        append("[]")
        return
    }
    append("[\n")
    append(values.joinToString(",\n") { value -> render(value).prependIndent("    ") })
    append("\n  ]")
}

private fun StringBuilder.appendTextTelemetryJsonField(name: String, value: String, comma: Boolean) {
    append("  ")
    append(textTelemetryJsonString(name))
    append(": ")
    append(textTelemetryJsonString(value))
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendTextTelemetryJsonField(name: String, value: Int, comma: Boolean) {
    append("  ")
    append(textTelemetryJsonString(name))
    append(": ")
    append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendTextTelemetryJsonField(name: String, value: Long, comma: Boolean) {
    append("  ")
    append(textTelemetryJsonString(name))
    append(": ")
    append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendTextTelemetryJsonField(name: String, value: Boolean, comma: Boolean) {
    append("  ")
    append(textTelemetryJsonString(name))
    append(": ")
    append(value)
    if (comma) append(",")
    append("\n")
}

private fun StringBuilder.appendTextTelemetryJsonNullableField(name: String, value: Int?, comma: Boolean) {
    append("  ")
    append(textTelemetryJsonString(name))
    append(": ")
    append(value?.toString() ?: "null")
    if (comma) append(",")
    append("\n")
}

private fun textTelemetryJsonString(value: String): String = buildString {
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
