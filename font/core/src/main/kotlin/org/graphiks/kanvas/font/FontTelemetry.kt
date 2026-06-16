package org.graphiks.kanvas.font

enum class FontTelemetryDomain(val serializedName: String) {
    Parser("parser"),
    Scaler("scaler"),
    Shaping("shaping"),
    Paragraph("paragraph"),
    GlyphArtifact("glyph-artifact"),
    GPUTextHandoff("gpu-text-handoff"),
}

enum class FontTelemetryUnit(val serializedName: String) {
    Nanoseconds("nanoseconds"),
    Count("count"),
    Bytes("bytes"),
}

enum class FontTelemetryCacheState(val serializedName: String) {
    Cold("cold"),
    Warm("warm"),
    Mixed("mixed"),
}

data class FontTelemetryEvidenceBundle(
    val schemaJson: String,
    val fixtureJson: String,
)

private data class FontTelemetryDomainSchema(
    val domain: FontTelemetryDomain,
    val requiredDimensions: List<String>,
    val metricNames: List<String>,
    val gpuAdapterRequired: Boolean,
) {
    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTelemetryField("domain", domain.serializedName, comma = true)
        appendTelemetryStringArray("requiredDimensions", requiredDimensions, comma = true)
        appendTelemetryStringArray("metricNames", metricNames, comma = true)
        appendTelemetryField("gpuAdapterRequired", gpuAdapterRequired, comma = false)
        append("}")
    }
}

private data class FontTelemetryEnvironment(
    val environmentLabel: String,
    val runtimeLabel: String,
    val gpuAdapter: String? = null,
    val gpuBackend: String? = null,
) {
    init {
        require(environmentLabel.isNotBlank()) { "environmentLabel must not be blank." }
        require(runtimeLabel.isNotBlank()) { "runtimeLabel must not be blank." }
        require(gpuAdapter == null || gpuAdapter.isNotBlank()) { "gpuAdapter must be non-blank when present." }
        require(gpuBackend == null || gpuBackend.isNotBlank()) { "gpuBackend must be non-blank when present." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTelemetryField("environmentLabel", environmentLabel, comma = true)
        appendTelemetryField("runtimeLabel", runtimeLabel, comma = true)
        append("gpuAdapter".quoted()).append(":").append(gpuAdapter.toTelemetryNullableString()).append(",")
        append("gpuBackend".quoted()).append(":").append(gpuBackend.toTelemetryNullableString())
        append("}")
    }
}

private data class FontTelemetryMetricSeries(
    val name: String,
    val unit: FontTelemetryUnit,
    val median: Long,
    val p90: Long,
    val max: Long,
    val counters: Map<String, Long> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(median >= 0) { "median must be non-negative." }
        require(p90 >= 0) { "p90 must be non-negative." }
        require(max >= 0) { "max must be non-negative." }
        require(counters.keys.all { it.isStableTelemetryToken() }) {
            "counter keys must be stable one-line tokens."
        }
        require(counters.values.all { it >= 0 }) { "counter values must be non-negative." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTelemetryField("name", name, comma = true)
        appendTelemetryField("unit", unit.serializedName, comma = true)
        appendTelemetryField("median", median, comma = true)
        appendTelemetryField("p90", p90, comma = true)
        appendTelemetryField("max", max, comma = true)
        append("counters".quoted()).append(":")
        append(
            counters.toSortedMap().entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                "${key.quoted()}:$value"
            },
        )
        append("}")
    }
}

private data class FontTelemetrySample(
    val fixtureId: String,
    val domain: FontTelemetryDomain,
    val measurementPhase: String,
    val sampleCount: Int,
    val cacheState: FontTelemetryCacheState,
    val fontSourceSetHash: String,
    val unicodeDataVersion: String,
    val environment: FontTelemetryEnvironment,
    val metrics: List<FontTelemetryMetricSeries>,
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(fixtureId.isNotBlank()) { "fixtureId must not be blank." }
        require(measurementPhase.isStableTelemetryToken()) {
            "measurementPhase must be a stable one-line token."
        }
        require(sampleCount > 1) {
            "sampleCount must be greater than one for repeated-run telemetry evidence."
        }
        require(fontSourceSetHash.isNotBlank()) { "fontSourceSetHash must not be blank." }
        require(unicodeDataVersion.isNotBlank()) { "unicodeDataVersion must not be blank." }
        require(metrics.isNotEmpty()) { "metrics must not be empty." }
        require(diagnostics.all { it.isStableTelemetryDiagnosticCode() }) {
            "diagnostics must use stable telemetry diagnostic codes."
        }
        if (domain == FontTelemetryDomain.GPUTextHandoff) {
            require(environment.gpuAdapter != null) {
                "GPU text handoff samples must record gpuAdapter."
            }
            require(environment.gpuBackend != null) {
                "GPU text handoff samples must record gpuBackend."
            }
        } else {
            require(environment.gpuAdapter == null) {
                "Only GPU text handoff samples may record gpuAdapter."
            }
            require(environment.gpuBackend == null) {
                "Only GPU text handoff samples may record gpuBackend."
            }
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTelemetryField("fixtureId", fixtureId, comma = true)
        appendTelemetryField("domain", domain.serializedName, comma = true)
        appendTelemetryField("measurementPhase", measurementPhase, comma = true)
        appendTelemetryField("sampleCount", sampleCount, comma = true)
        appendTelemetryField("cacheState", cacheState.serializedName, comma = true)
        appendTelemetryField("fontSourceSetHash", fontSourceSetHash, comma = true)
        appendTelemetryField("unicodeDataVersion", unicodeDataVersion, comma = true)
        append("environment".quoted()).append(":").append(environment.toCanonicalJson()).append(",")
        append("metrics".quoted()).append(":")
        append(metrics.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toCanonicalJson() })
        append(",")
        appendTelemetryStringArray("diagnostics", diagnostics, comma = false)
        append("}")
    }
}

private data class FontTelemetryNegativeCase(
    val fixtureId: String,
    val domain: String,
    val diagnosticCode: String,
    val missingField: String? = null,
    val sampleCount: Int = 1,
) {
    init {
        require(fixtureId.isNotBlank()) { "fixtureId must not be blank." }
        require(domain.isStableTelemetryToken()) { "domain must be a stable one-line token." }
        require(diagnosticCode.isStableTelemetryDiagnosticCode()) {
            "diagnosticCode must be a stable telemetry diagnostic code."
        }
        require(missingField == null || missingField.isStableTelemetryToken()) {
            "missingField must be a stable one-line token when present."
        }
        require(sampleCount >= 1) { "sampleCount must be positive." }
    }

    fun toCanonicalJson(): String = buildString {
        append("{")
        appendTelemetryField("fixtureId", fixtureId, comma = true)
        appendTelemetryField("domain", domain, comma = true)
        appendTelemetryField("diagnosticCode", diagnosticCode, comma = true)
        append("missingField".quoted()).append(":").append(missingField.toTelemetryNullableString()).append(",")
        appendTelemetryField("sampleCount", sampleCount, comma = false)
        append("}")
    }
}

object FontTelemetryEvidenceWriter {
    fun writeBundle(): FontTelemetryEvidenceBundle = FontTelemetryEvidenceBundle(
        schemaJson = writeSchemaJson(),
        fixtureJson = writeFixtureJson(),
    )

    fun writeSchemaJson(): String {
        val domains = listOf(
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.Parser,
                requiredDimensions = commonDimensions(),
                metricNames = listOf("parser.scan.time", "parser.parse.time", "parser.table-cache.hit"),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.Scaler,
                requiredDimensions = commonDimensions(),
                metricNames = listOf("scaler.outline.time", "scaler.bounds.time", "scaler.variation.time"),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.Shaping,
                requiredDimensions = commonDimensions(),
                metricNames = listOf("shaping.segmentation.time", "shaping.fallback.time", "shaping.glyph.count"),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.Paragraph,
                requiredDimensions = commonDimensions(),
                metricNames = listOf("paragraph.layout.time", "paragraph.line.count", "paragraph.hit-test.time"),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.GlyphArtifact,
                requiredDimensions = commonDimensions(),
                metricNames = listOf("glyph-artifact.route.count", "glyph-artifact.a8.time", "glyph-artifact.cache.bytes"),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.GPUTextHandoff,
                requiredDimensions = commonDimensions() + listOf("gpuAdapter", "gpuBackend"),
                metricNames = listOf("gpu-text-handoff.registry.lookup.time", "gpu-text-handoff.upload.bytes", "gpu-text-handoff.route.count"),
                gpuAdapterRequired = true,
            ),
        )
        val diagnosticCodes = listOf(
            "font.telemetry.dimension-missing",
            "font.telemetry.schema-domain-missing",
            "font.telemetry.single-run-budget-refused",
        )
        return buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"schemaId\": \"font-telemetry-schema\",\n")
            append("  \"ownerTickets\": [\"KFONT-M12-001\"],\n")
            append("  \"domains\": [\n")
            append(domains.joinToString(",\n") { "    ${it.toCanonicalJson()}" })
            append("\n  ],\n")
            append("  \"requiredAggregationFields\": [\"median\", \"p90\", \"max\", \"sampleCount\", \"cacheState\"],\n")
            append("  \"diagnosticCodes\": [\"${diagnosticCodes[0]}\", \"${diagnosticCodes[1]}\", \"${diagnosticCodes[2]}\"],\n")
            append("  \"dashboardRow\": {\"label\":\"Font telemetry schema\",\"classification\":\"tracked-gap\",\"claimPromotionAllowed\":false},\n")
            append("  \"nonClaims\": [\"no-complete-target-support-claim\", \"no-performance-release-gate-claim\", \"no-gpu-route-support-claim\", \"no-native-engine-oracle-claim\"]\n")
            append("}")
        }
    }

    fun writeFixtureJson(): String {
        val repeatedSamples = listOf(
            FontTelemetrySample(
                fixtureId = "telemetry-parser-repeat",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_200_000,
                        p90 = 1_600_000,
                        max = 1_800_000,
                        counters = mapOf("diagnosticCount" to 0L, "tableCacheMiss" to 1L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "telemetry-scaler-repeat",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 900_000,
                        p90 = 1_100_000,
                        max = 1_250_000,
                        counters = mapOf("glyphCount" to 32L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "telemetry-shaping-repeat",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "shaping.fallback.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 450_000,
                        p90 = 650_000,
                        max = 720_000,
                        counters = mapOf("clusterCount" to 12L, "diagnosticCount" to 1L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "telemetry-paragraph-repeat",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "paragraph.layout.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 2_100_000,
                        p90 = 2_400_000,
                        max = 2_650_000,
                        counters = mapOf("lineCount" to 3L, "styleRunCount" to 4L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "telemetry-glyph-artifact-repeat",
                domain = FontTelemetryDomain.GlyphArtifact,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Mixed,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "glyph-artifact.cache.bytes",
                        unit = FontTelemetryUnit.Bytes,
                        median = 4_096,
                        p90 = 6_144,
                        max = 8_192,
                        counters = mapOf("routeCount" to 6L, "uploadCount" to 0L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "telemetry-gpu-handoff-repeat",
                domain = FontTelemetryDomain.GPUTextHandoff,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = FontTelemetryEnvironment(
                    environmentLabel = "developer-desktop",
                    runtimeLabel = "jdk-25-kotlin-2.3",
                    gpuAdapter = "wgpu-nvidia-rtx-3070",
                    gpuBackend = "webgpu",
                ),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "gpu-text-handoff.upload.bytes",
                        unit = FontTelemetryUnit.Bytes,
                        median = 262_144,
                        p90 = 327_680,
                        max = 393_216,
                        counters = mapOf("routeCount" to 2L, "uploadCount" to 1L),
                    ),
                ),
            ),
        )
        val negativeCases = listOf(
            FontTelemetryNegativeCase(
                fixtureId = "telemetry-missing-measurement-phase",
                domain = FontTelemetryDomain.Parser.serializedName,
                diagnosticCode = "font.telemetry.dimension-missing",
                missingField = "measurementPhase",
                sampleCount = 1,
            ),
            FontTelemetryNegativeCase(
                fixtureId = "telemetry-single-run-budget-refusal",
                domain = FontTelemetryDomain.Shaping.serializedName,
                diagnosticCode = "font.telemetry.single-run-budget-refused",
                missingField = null,
                sampleCount = 1,
            ),
        )
        return buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"dumpId\": \"font-telemetry-schema-fixture\",\n")
            append("  \"ownerTickets\": [\"KFONT-M12-001\"],\n")
            append("  \"samples\": [\n")
            append(repeatedSamples.joinToString(",\n") { "    ${it.toCanonicalJson()}" })
            append("\n  ],\n")
            append("  \"negativeCases\": [\n")
            append(negativeCases.joinToString(",\n") { "    ${it.toCanonicalJson()}" })
            append("\n  ],\n")
            append("  \"nonClaims\": [\"no-complete-target-support-claim\", \"no-performance-release-gate-claim\", \"no-gpu-route-support-claim\", \"no-native-engine-oracle-claim\"]\n")
            append("}")
        }
    }

    private fun commonDimensions(): List<String> = listOf(
        "environmentLabel",
        "runtimeLabel",
        "fontSourceSetHash",
        "unicodeDataVersion",
        "cacheState",
        "fixtureId",
        "sampleCount",
        "measurementPhase",
    )
}

private fun String.quoted(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun String?.toTelemetryNullableString(): String = this?.quoted() ?: "null"

private fun StringBuilder.appendTelemetryField(name: String, value: String, comma: Boolean) {
    append(name.quoted()).append(": ").append(value.quoted())
    if (comma) {
        append(",")
    }
}

private fun StringBuilder.appendTelemetryField(name: String, value: Boolean, comma: Boolean) {
    append(name.quoted()).append(": ").append(value)
    if (comma) {
        append(",")
    }
}

private fun StringBuilder.appendTelemetryField(name: String, value: Int, comma: Boolean) {
    append(name.quoted()).append(": ").append(value)
    if (comma) {
        append(",")
    }
}

private fun StringBuilder.appendTelemetryField(name: String, value: Long, comma: Boolean) {
    append(name.quoted()).append(": ").append(value)
    if (comma) {
        append(",")
    }
}

private fun StringBuilder.appendTelemetryStringArray(name: String, values: List<String>, comma: Boolean) {
    append(name.quoted()).append(": ")
    append(values.joinToString(prefix = "[", postfix = "]", separator = ",") { it.quoted() })
    if (comma) {
        append(",")
    }
}

private fun String.isStableTelemetryToken(): Boolean = all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }

private fun String.isStableTelemetryDiagnosticCode(): Boolean =
    startsWith("font.telemetry.") && isStableTelemetryToken()
