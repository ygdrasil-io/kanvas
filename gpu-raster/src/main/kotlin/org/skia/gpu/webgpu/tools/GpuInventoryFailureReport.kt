package org.skia.gpu.webgpu.tools

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

public enum class GpuInventoryFailureCategory(public val wireName: String) {
    ExpectedUnsupportedDiagnostic("expected-unsupported-diagnostic"),
    SimilarityRegression("similarity-regression"),
    UnsupportedImageFilter("unsupported-image-filter"),
    AdapterSkip("adapter-skip"),
    AdapterMissing("adapter-missing"),
    UnexpectedException("unexpected-exception"),
}

public data class GpuInventoryFailureRecord(
    val testName: String,
    val status: String,
    val category: GpuInventoryFailureCategory,
    val reason: String,
    val actualSimilarityPercent: Double?,
    val floorSimilarityPercent: Double?,
    val artifactPath: String?,
    val sourceXml: String,
)

public data class GpuInventoryFailureSummary(
    val records: List<GpuInventoryFailureRecord>,
) {
    public val total: Int get() = records.size

    public val byCategory: Map<GpuInventoryFailureCategory, Int> = GpuInventoryFailureCategory.entries
        .associateWith { category -> records.count { it.category == category } }
}

public object GpuInventoryFailureReport {
    private val expectedUnsupportedReasonAllowlist: Set<String> = setOf(
        "coverage.edge-count-exceeded",
        "coverage.arbitrary-aa-clip-unsupported",
        "coverage.alpha-mask-unsupported",
        "coverage.span-runs-unsupported",
        "coverage.atlas-policy-unavailable",
        "coverage.stencil-cover-unavailable",
        "coverage.glyph-mask-dependency-unavailable",
    )

    private val unsupportedImageFilterMarkers: List<String> = listOf(
        "SkImageFilters.Crop(input = nonNull)",
    )

    private val reasonKeyRegex = Regex("""reason=(coverage\.[a-z0-9-]+)""")
    private val reasonCodeRegex = Regex("""coverage\.[a-z0-9-]+""")
    private val similarityFloorRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)%\s*<\s*([0-9]+(?:[.,][0-9]+)?)%""")
    private val debugImageRegex = Regex("""(?:gpu-raster/)?build/debug-images/[A-Za-z0-9._/\-{},]+""")

    public fun run(testResultsRoot: Path): GpuInventoryFailureSummary {
        require(testResultsRoot.isDirectory()) { "testResultsRoot must be a directory: $testResultsRoot" }
        val records = Files.walk(testResultsRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension == "xml" && it.name.startsWith("TEST-") }
                .sortedBy { it.toString() }
                .flatMap { parseSuite(it).asSequence() }
                .sortedWith(compareBy<GpuInventoryFailureRecord> { it.testName }.thenBy { it.category.wireName })
                .toList()
        }
        return GpuInventoryFailureSummary(records)
    }

    public fun writeOutputs(summary: GpuInventoryFailureSummary, outputDir: Path): Pair<Path, Path> {
        outputDir.createDirectories()
        val markdownPath = outputDir.resolve("gpu-inventory-failure-classification.md")
        val jsonPath = outputDir.resolve("gpu-inventory-failure-classification.json")
        Files.writeString(markdownPath, toMarkdown(summary))
        Files.writeString(jsonPath, toJson(summary))
        return markdownPath to jsonPath
    }

    public fun toMarkdown(summary: GpuInventoryFailureSummary): String {
        val categoryRows = GpuInventoryFailureCategory.entries.joinToString("\n") { category ->
            "| `${category.wireName}` | ${summary.byCategory.getValue(category)} |"
        }
        val recordRows = if (summary.records.isEmpty()) {
            "| _none_ | _none_ | _none_ | _none_ | _none_ | _none_ |"
        } else {
            summary.records.joinToString("\n") { record ->
                "| `${record.testName}` | `${record.category.wireName}` | ${escapeMarkdown(record.reason)} | " +
                    "${record.actualSimilarityPercent?.let { formatPercent(it) } ?: "-"} | " +
                    "${record.floorSimilarityPercent?.let { formatPercent(it) } ?: "-"} | " +
                    "${record.artifactPath?.let { "`$it`" } ?: "-"} |"
            }
        }
        return buildString {
            appendLine("# GPU Inventory Failure Classification")
            appendLine()
            appendLine("Total classified records: `${summary.total}`")
            appendLine()
            appendLine("## Category Summary")
            appendLine()
            appendLine("| Category | Count |")
            appendLine("|---|---:|")
            appendLine(categoryRows)
            appendLine()
            appendLine("## Classified Records")
            appendLine()
            appendLine("| Test | Category | Reason | Actual % | Floor % | Artifact |")
            appendLine("|---|---|---|---:|---:|---|")
            append(recordRows)
        }.trim()
    }

    public fun toJson(summary: GpuInventoryFailureSummary): String = buildString {
        append("{\n")
        append("  \"total\": ${summary.total},\n")
        append("  \"byCategory\": {\n")
        append(
            GpuInventoryFailureCategory.entries.joinToString(",\n") { category ->
                "    \"${category.wireName}\": ${summary.byCategory.getValue(category)}"
            },
        )
        append("\n  },\n")
        append("  \"records\": [\n")
        append(
            summary.records.joinToString(",\n") { record ->
                buildString {
                    append("    {")
                    append("\"testName\": \"${escapeJson(record.testName)}\", ")
                    append("\"status\": \"${escapeJson(record.status)}\", ")
                    append("\"category\": \"${record.category.wireName}\", ")
                    append("\"reason\": \"${escapeJson(record.reason)}\", ")
                    append("\"actualSimilarityPercent\": ${record.actualSimilarityPercent ?: "null"}, ")
                    append("\"floorSimilarityPercent\": ${record.floorSimilarityPercent ?: "null"}, ")
                    append("\"artifactPath\": ${record.artifactPath?.let { "\"${escapeJson(it)}\"" } ?: "null"}, ")
                    append("\"sourceXml\": \"${escapeJson(record.sourceXml)}\"")
                    append("}")
                }
            },
        )
        append("\n  ]\n")
        append("}\n")
    }

    private fun parseSuite(xmlFile: Path): List<GpuInventoryFailureRecord> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile.toFile())
        val testCases = document.getElementsByTagName("testcase")
        val records = mutableListOf<GpuInventoryFailureRecord>()
        for (index in 0 until testCases.length) {
            val testCase = testCases.item(index) as? org.w3c.dom.Element ?: continue
            val className = testCase.getAttribute("classname").ifBlank { "unknown-class" }
            val methodName = testCase.getAttribute("name").ifBlank { "unknown-test" }
            val testName = "$className#$methodName"
            val failureNode = firstChildElement(testCase, "failure")
            val errorNode = firstChildElement(testCase, "error")
            val skippedNode = firstChildElement(testCase, "skipped")
            val statusAndNode = when {
                failureNode != null -> "failure" to failureNode
                errorNode != null -> "error" to errorNode
                skippedNode != null -> "skipped" to skippedNode
                else -> null
            } ?: continue

            val status = statusAndNode.first
            val detailNode = statusAndNode.second
            val detailText = detailNode.textContent?.trim().orEmpty()
            val messageText = detailNode.getAttribute("message").ifBlank { detailText }
            val classification = classify(status, detailText, messageText)
            records += GpuInventoryFailureRecord(
                testName = testName,
                status = status,
                category = classification.category,
                reason = classification.reason,
                actualSimilarityPercent = classification.actualSimilarityPercent,
                floorSimilarityPercent = classification.floorSimilarityPercent,
                artifactPath = classification.artifactPath,
                sourceXml = xmlFile.name,
            )
        }
        return records
    }

    private fun firstChildElement(node: org.w3c.dom.Element, tagName: String): org.w3c.dom.Element? {
        val childNodes = node.getElementsByTagName(tagName)
        if (childNodes.length == 0) return null
        return childNodes.item(0) as? org.w3c.dom.Element
    }

    private fun classify(
        status: String,
        detailText: String,
        messageText: String,
    ): Classification {
        val mergedText = sequenceOf(messageText, detailText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
        val artifactPath = extractArtifactPath(mergedText)
        if (status == "skipped") {
            val adapterMissing = mergedText.contains("No WebGPU adapter", ignoreCase = true)
            return if (adapterMissing) {
                Classification(
                    category = GpuInventoryFailureCategory.AdapterMissing,
                    reason = "No WebGPU adapter available",
                    artifactPath = artifactPath,
                )
            } else {
                Classification(
                    category = GpuInventoryFailureCategory.AdapterSkip,
                    reason = firstReasonLine(mergedText, fallback = "Adapter-dependent test skipped"),
                    artifactPath = artifactPath,
                )
            }
        }

        unsupportedImageFilterMarkers.firstOrNull { marker -> mergedText.contains(marker) }?.let { marker ->
            return Classification(
                category = GpuInventoryFailureCategory.UnsupportedImageFilter,
                reason = marker,
                artifactPath = artifactPath,
            )
        }

        val preferredReasonCode = reasonKeyRegex.find(mergedText)?.groupValues?.get(1)
        val reasonCode = preferredReasonCode
            ?: reasonCodeRegex.findAll(mergedText).map { it.value }.firstOrNull { it in expectedUnsupportedReasonAllowlist }
            ?: reasonCodeRegex.find(mergedText)?.value
        if (reasonCode != null && reasonCode in expectedUnsupportedReasonAllowlist) {
            return Classification(
                category = GpuInventoryFailureCategory.ExpectedUnsupportedDiagnostic,
                reason = reasonCode,
                artifactPath = artifactPath,
            )
        }

        val similarity = similarityFloorRegex.find(mergedText)
        if (similarity != null) {
            val actual = parsePercentValue(similarity.groupValues[1])
            val floor = parsePercentValue(similarity.groupValues[2])
            return Classification(
                category = GpuInventoryFailureCategory.SimilarityRegression,
                reason = "similarity regression",
                actualSimilarityPercent = actual,
                floorSimilarityPercent = floor,
                artifactPath = artifactPath,
            )
        }

        return Classification(
            category = GpuInventoryFailureCategory.UnexpectedException,
            reason = reasonCode?.let { "unknown diagnostic code: $it" }
                ?: firstReasonLine(mergedText, fallback = "unexpected exception"),
            artifactPath = artifactPath,
        )
    }

    private fun extractArtifactPath(text: String): String? =
        debugImageRegex.find(text)?.value?.trimEnd('.', ',', ';', ':')

    private fun firstReasonLine(text: String, fallback: String): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: fallback

    private fun escapeMarkdown(value: String): String =
        value.replace("|", "\\|").replace("\n", "<br/>")

    private fun escapeJson(value: String): String = buildString {
        value.forEach { ch ->
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

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun parsePercentValue(value: String): Double? =
        value.replace(',', '.').toDoubleOrNull()

    private data class Classification(
        val category: GpuInventoryFailureCategory,
        val reason: String,
        val actualSimilarityPercent: Double? = null,
        val floorSimilarityPercent: Double? = null,
        val artifactPath: String? = null,
    )
}

public fun main(args: Array<String>) {
    val testResultsRoot = args.getOrNull(0)?.let(Path::of)
        ?: Path.of("build/test-results/test")
    val outputDir = args.getOrNull(1)?.let(Path::of)
        ?: Path.of("build/reports/gpu-inventory")
    val summary = GpuInventoryFailureReport.run(testResultsRoot)
    val (markdownPath, jsonPath) = GpuInventoryFailureReport.writeOutputs(summary, outputDir)
    println(
        "gpu-inventory-report root=$testResultsRoot total=${summary.total} " +
            "markdown=${markdownPath.toAbsolutePath()} json=${jsonPath.toAbsolutePath()}",
    )
}
