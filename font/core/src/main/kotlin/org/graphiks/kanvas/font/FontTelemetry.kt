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
    val trendSeriesId: String? = null,
    val unit: FontTelemetryUnit,
    val median: Long,
    val p90: Long,
    val max: Long,
    val counters: Map<String, Long> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "name must not be blank." }
        require(trendSeriesId == null || trendSeriesId.isStableTelemetryToken()) {
            "trendSeriesId must be a stable one-line token when present."
        }
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
        if (trendSeriesId != null) {
            appendTelemetryField("trendSeriesId", trendSeriesId, comma = true)
        }
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
    val tableTags: List<String> = emptyList(),
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
        require(tableTags.all { it.isStableTelemetryToken() }) {
            "tableTags must use stable one-line tokens."
        }
        require(metrics.isNotEmpty()) { "metrics must not be empty." }
        require(diagnostics.all { it.isStableDiagnosticToken() }) {
            "diagnostics must use stable one-line diagnostic tokens."
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
        appendTelemetryStringArray("tableTags", tableTags, comma = true)
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

private data class FontTelemetryDomainDump(
    val dumpId: String,
    val ownerTickets: List<String>,
    val domain: FontTelemetryDomain,
    val trendSeriesPrefix: String? = null,
    val samples: List<FontTelemetrySample>,
    val negativeCases: List<FontTelemetryNegativeCase>,
    val nonClaims: List<String>,
) {
    init {
        require(dumpId.isStableTelemetryToken()) { "dumpId must be a stable one-line token." }
        require(ownerTickets.isNotEmpty()) { "ownerTickets must not be empty." }
        require(ownerTickets.all { it.isStableTelemetryToken() }) {
            "ownerTickets must use stable one-line tokens."
        }
        require(trendSeriesPrefix == null || trendSeriesPrefix.isStableTelemetryToken()) {
            "trendSeriesPrefix must be a stable one-line token when present."
        }
        require(samples.isNotEmpty()) { "samples must not be empty." }
        require(nonClaims.isNotEmpty()) { "nonClaims must not be empty." }
        require(nonClaims.all { it.isStableTelemetryToken() }) {
            "nonClaims must use stable one-line tokens."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": 1,\n")
        append("  \"dumpId\": ").append(dumpId.quoted()).append(",\n")
        append("  \"ownerTickets\": ")
        append(ownerTickets.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.quoted() })
        append(",\n")
        append("  \"domain\": ").append(domain.serializedName.quoted()).append(",\n")
        if (trendSeriesPrefix != null) {
            append("  \"trendSeriesPrefix\": ").append(trendSeriesPrefix.quoted()).append(",\n")
        }
        append("  \"samples\": [\n")
        append(samples.joinToString(",\n") { "    ${it.toCanonicalJson()}" })
        append("\n  ],\n")
        append("  \"negativeCases\": [\n")
        append(negativeCases.joinToString(",\n") { "    ${it.toCanonicalJson()}" })
        append("\n  ],\n")
        append("  \"nonClaims\": ")
        append(nonClaims.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.quoted() })
        append("\n}")
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
                metricNames = listOf(
                    "shaping.segmentation.time",
                    "shaping.bidi.time",
                    "shaping.script-itemization.time",
                    "shaping.fallback.time",
                    "shaping.gsub.time",
                    "shaping.gpos.time",
                    "shaping.glyph.count",
                    "shaping.cluster.count",
                ),
                gpuAdapterRequired = false,
            ),
            FontTelemetryDomainSchema(
                domain = FontTelemetryDomain.Paragraph,
                requiredDimensions = commonDimensions(),
                metricNames = listOf(
                    "paragraph.layout.time",
                    "paragraph.line-break-opportunity.count",
                    "paragraph.shaped-run.count",
                    "paragraph.line.count",
                    "paragraph.hit-test.time",
                    "paragraph.selection-query.time",
                    "paragraph.ellipsis.attempt.count",
                    "paragraph.placeholder.count",
                ),
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
            "font.telemetry.scaler-domain-missing",
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
            append("  \"diagnosticCodes\": [")
            append(diagnosticCodes.joinToString(separator = ", ") { it.quoted() })
            append("],\n")
            append("  \"dashboardRow\": {\"label\":\"Font telemetry schema\",\"classification\":\"tracked-gap\",\"claimPromotionAllowed\":false},\n")
            append("  \"nonClaims\": ")
            append(defaultNonClaims().joinToString(prefix = "[", postfix = "]", separator = ", ") { it.quoted() })
            append("\n")
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
            append("  \"nonClaims\": ")
            append(defaultNonClaims().joinToString(prefix = "[", postfix = "]", separator = ", ") { it.quoted() })
            append("\n")
            append("}")
        }
    }
    fun writeParserMetricsJson(): String = FontTelemetryDomainDump(
        dumpId = "parser-metrics",
        ownerTickets = listOf("KFONT-M12-002"),
        domain = FontTelemetryDomain.Parser,
        trendSeriesPrefix = "font.parser",
        samples = listOf(
            FontTelemetrySample(
                fixtureId = "font-source-sfnt-single-ttf-provenance",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "cold-baseline",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cmap", "head", "hhea", "hmtx", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 180_000,
                        p90 = 210_000,
                        max = 240_000,
                        counters = mapOf("bytesRead" to 2_048L, "tableCount" to 5L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 980_000,
                        p90 = 1_120_000,
                        max = 1_240_000,
                        counters = mapOf(
                            "tableCacheHit" to 0L,
                            "tableCacheMiss" to 5L,
                            "malformedTableCount" to 0L,
                            "boundsFailureCount" to 0L,
                            "diagnosticCount" to 0L,
                        ),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "font-source-sfnt-ttc-face-index-provenance",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cmap", "head", "hhea", "hmtx", "maxp", "name"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 240_000,
                        p90 = 280_000,
                        max = 320_000,
                        counters = mapOf("bytesRead" to 3_072L, "tableCount" to 6L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_180_000,
                        p90 = 1_340_000,
                        max = 1_480_000,
                        counters = mapOf(
                            "tableCacheHit" to 4L,
                            "tableCacheMiss" to 2L,
                            "malformedTableCount" to 0L,
                            "boundsFailureCount" to 0L,
                            "diagnosticCount" to 0L,
                        ),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "cff-cff2-scaler-selected-table-provenance",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cff", "cmap", "head", "hhea", "hmtx", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 260_000,
                        p90 = 300_000,
                        max = 340_000,
                        counters = mapOf("bytesRead" to 2_816L, "tableCount" to 6L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_240_000,
                        p90 = 1_390_000,
                        max = 1_520_000,
                        counters = mapOf(
                            "tableCacheHit" to 3L,
                            "tableCacheMiss" to 3L,
                            "malformedTableCount" to 0L,
                            "boundsFailureCount" to 0L,
                            "diagnosticCount" to 0L,
                        ),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "truetype-scaler-truetype-avar-coordinate-mapping",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("avar", "cmap", "fvar", "gvar", "head", "hhea", "hmtx", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 300_000,
                        p90 = 340_000,
                        max = 390_000,
                        counters = mapOf("bytesRead" to 3_584L, "tableCount" to 8L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_360_000,
                        p90 = 1_520_000,
                        max = 1_680_000,
                        counters = mapOf(
                            "tableCacheHit" to 4L,
                            "tableCacheMiss" to 4L,
                            "malformedTableCount" to 0L,
                            "boundsFailureCount" to 0L,
                            "diagnosticCount" to 0L,
                        ),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "font-source-sfnt-malformed-directory-diagnostic",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "cold-baseline",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cmap", "head", "hhea", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 210_000,
                        p90 = 250_000,
                        max = 290_000,
                        counters = mapOf("bytesRead" to 1_536L, "tableCount" to 4L, "diagnosticCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_080_000,
                        p90 = 1_220_000,
                        max = 1_340_000,
                        counters = mapOf(
                            "tableCacheHit" to 0L,
                            "tableCacheMiss" to 4L,
                            "malformedTableCount" to 1L,
                            "boundsFailureCount" to 1L,
                            "diagnosticCount" to 1L,
                        ),
                    ),
                ),
                diagnostics = listOf("font.sfnt.table-overlap"),
            ),
            FontTelemetrySample(
                fixtureId = "font-source-sfnt-malformed-optional-table-diagnostic",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "cold-baseline",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cmap", "gpos", "head", "hhea", "hmtx", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 250_000,
                        p90 = 290_000,
                        max = 330_000,
                        counters = mapOf("bytesRead" to 2_688L, "tableCount" to 6L, "diagnosticCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 1_410_000,
                        p90 = 1_590_000,
                        max = 1_760_000,
                        counters = mapOf(
                            "tableCacheHit" to 0L,
                            "tableCacheMiss" to 6L,
                            "malformedTableCount" to 1L,
                            "boundsFailureCount" to 1L,
                            "diagnosticCount" to 1L,
                        ),
                    ),
                ),
                diagnostics = listOf("font.sfnt.optional-table-malformed"),
            ),
            FontTelemetrySample(
                fixtureId = "font-source-sfnt-malformed-required-table-diagnostic",
                domain = FontTelemetryDomain.Parser,
                measurementPhase = "cold-baseline",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-sfnt-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                tableTags = listOf("cmap", "head", "maxp"),
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "parser.scan.time",
                        trendSeriesId = "font.parser.scan.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 170_000,
                        p90 = 200_000,
                        max = 230_000,
                        counters = mapOf("bytesRead" to 1_024L, "tableCount" to 3L, "diagnosticCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "parser.parse.time",
                        trendSeriesId = "font.parser.parse.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 820_000,
                        p90 = 930_000,
                        max = 1_020_000,
                        counters = mapOf(
                            "tableCacheHit" to 0L,
                            "tableCacheMiss" to 3L,
                            "malformedTableCount" to 1L,
                            "boundsFailureCount" to 0L,
                            "diagnosticCount" to 1L,
                        ),
                    ),
                ),
                diagnostics = listOf("font.sfnt.required-table-missing"),
            ),
        ),
        negativeCases = listOf(
            FontTelemetryNegativeCase(
                fixtureId = "parser-metrics-missing-metrics",
                domain = FontTelemetryDomain.Parser.serializedName,
                diagnosticCode = "font.telemetry.dimension-missing",
                missingField = "metrics",
                sampleCount = 1,
            ),
        ),
        nonClaims = defaultNonClaims(),
    ).toCanonicalJson()

    fun writeScalerMetricsJson(): String = FontTelemetryDomainDump(
        dumpId = "scaler-metrics",
        ownerTickets = listOf("KFONT-M12-002"),
        domain = FontTelemetryDomain.Scaler,
        trendSeriesPrefix = "font.scaler",
        samples = listOf(
            FontTelemetrySample(
                fixtureId = "truetype-scaler-truetype-simple-glyph",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 520_000,
                        p90 = 610_000,
                        max = 690_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 5L, "scalerCacheHit" to 4L, "scalerCacheMiss" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.bounds.time",
                        trendSeriesId = "font.scaler.bounds.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 140_000,
                        p90 = 170_000,
                        max = 190_000,
                        counters = mapOf("notdefFallbackCount" to 0L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.metrics.time",
                        trendSeriesId = "font.scaler.metrics.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 120_000,
                        p90 = 150_000,
                        max = 180_000,
                        counters = mapOf("diagnosticCount" to 0L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "truetype-scaler-truetype-composite-glyph-transform",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 760_000,
                        p90 = 880_000,
                        max = 980_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 10L, "scalerCacheHit" to 3L, "scalerCacheMiss" to 2L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.bounds.time",
                        trendSeriesId = "font.scaler.bounds.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 180_000,
                        p90 = 220_000,
                        max = 250_000,
                        counters = mapOf("notdefFallbackCount" to 0L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.metrics.time",
                        trendSeriesId = "font.scaler.metrics.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 150_000,
                        p90 = 190_000,
                        max = 220_000,
                        counters = mapOf("diagnosticCount" to 0L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "truetype-scaler-truetype-gvar-simple-delta",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 680_000,
                        p90 = 790_000,
                        max = 870_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 5L, "scalerCacheHit" to 3L, "scalerCacheMiss" to 2L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.metrics.time",
                        trendSeriesId = "font.scaler.metrics.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 190_000,
                        p90 = 240_000,
                        max = 280_000,
                        counters = mapOf("notdefFallbackCount" to 0L, "diagnosticCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.variation.time",
                        trendSeriesId = "font.scaler.variation.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 210_000,
                        p90 = 260_000,
                        max = 310_000,
                        counters = mapOf("diagnosticCount" to 1L),
                    ),
                ),
                diagnostics = listOf("font.metrics-variation-unavailable"),
            ),
            FontTelemetrySample(
                fixtureId = "cff-cff2-scaler-cff-local-global-subroutines",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 840_000,
                        p90 = 960_000,
                        max = 1_080_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 8L, "scalerCacheHit" to 2L, "scalerCacheMiss" to 3L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.charstring.time",
                        trendSeriesId = "font.scaler.charstring.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 260_000,
                        p90 = 310_000,
                        max = 360_000,
                        counters = mapOf("notdefFallbackCount" to 0L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.metrics.time",
                        trendSeriesId = "font.scaler.metrics.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 170_000,
                        p90 = 210_000,
                        max = 250_000,
                        counters = mapOf("diagnosticCount" to 0L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "cff-cff2-scaler-cff2-variation-store-region",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "warm-repeat",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 900_000,
                        p90 = 1_040_000,
                        max = 1_180_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 9L, "scalerCacheHit" to 2L, "scalerCacheMiss" to 3L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.charstring.time",
                        trendSeriesId = "font.scaler.charstring.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 290_000,
                        p90 = 340_000,
                        max = 390_000,
                        counters = mapOf("notdefFallbackCount" to 0L, "diagnosticCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.variation.time",
                        trendSeriesId = "font.scaler.variation.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 240_000,
                        p90 = 290_000,
                        max = 340_000,
                        counters = mapOf("diagnosticCount" to 0L),
                    ),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "cff-cff2-scaler-malformed-index-dict-refusal",
                domain = FontTelemetryDomain.Scaler,
                measurementPhase = "cold-baseline",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Cold,
                fontSourceSetHash = "fontset-scaler-telemetry-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries(
                        name = "scaler.outline.time",
                        trendSeriesId = "font.scaler.outline.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 430_000,
                        p90 = 500_000,
                        max = 580_000,
                        counters = mapOf("glyphCount" to 1L, "outlineCommandCount" to 0L, "scalerCacheHit" to 0L, "scalerCacheMiss" to 5L),
                    ),
                    FontTelemetryMetricSeries(
                        name = "scaler.metrics.time",
                        trendSeriesId = "font.scaler.metrics.time",
                        unit = FontTelemetryUnit.Nanoseconds,
                        median = 110_000,
                        p90 = 130_000,
                        max = 160_000,
                        counters = mapOf("notdefFallbackCount" to 1L, "diagnosticCount" to 1L),
                    ),
                ),
                diagnostics = listOf("font.cff-table-malformed"),
            ),
        ),
        negativeCases = listOf(
            FontTelemetryNegativeCase(
                fixtureId = "scaler-metrics-unsupported-domain",
                domain = FontTelemetryDomain.Scaler.serializedName,
                diagnosticCode = "font.telemetry.scaler-domain-missing",
                missingField = null,
                sampleCount = 1,
            ),
        ),
        nonClaims = defaultNonClaims(),
    ).toCanonicalJson()

    fun writeShapingMetricsJson(): String = FontTelemetryDomainDump(
        dumpId = "shaping-metrics",
        ownerTickets = listOf("KFONT-M12-003"),
        domain = FontTelemetryDomain.Shaping,
        samples = listOf(
            FontTelemetrySample(
                fixtureId = "telemetry-shaping-repeat",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-latin-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 180_000, 240_000, 260_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 90_000, 120_000, 140_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 110_000, 145_000, 165_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        450_000,
                        650_000,
                        720_000,
                        counters = mapOf("fallbackRunCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        310_000,
                        390_000,
                        430_000,
                        counters = mapOf("gsubLookupCount" to 2L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        150_000,
                        210_000,
                        235_000,
                        counters = mapOf("gposLookupCount" to 1L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 14, 14, 14),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 12, 12, 12),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "arabic-shaped-glyph-run",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-arabic-vendored-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 260_000, 330_000, 360_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 175_000, 240_000, 270_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 130_000, 170_000, 185_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        210_000,
                        260_000,
                        290_000,
                        counters = mapOf("fallbackRunCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        610_000,
                        760_000,
                        820_000,
                        counters = mapOf("gsubLookupCount" to 4L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        490_000,
                        610_000,
                        670_000,
                        counters = mapOf("gposLookupCount" to 3L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 7, 7, 7),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 4, 4, 4),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "devanagari-shaping-report",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-devanagari-vendored-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 240_000, 300_000, 335_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 70_000, 95_000, 110_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 145_000, 190_000, 215_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        220_000,
                        290_000,
                        315_000,
                        counters = mapOf("fallbackRunCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        720_000,
                        890_000,
                        970_000,
                        counters = mapOf("gsubLookupCount" to 5L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        405_000,
                        520_000,
                        575_000,
                        counters = mapOf("gposLookupCount" to 2L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 6, 6, 6),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 3, 3, 3),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "fallback-cluster-thai",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Mixed,
                fontSourceSetHash = "fontset-thai-fallback-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 195_000, 250_000, 280_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 55_000, 80_000, 95_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 115_000, 150_000, 170_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        330_000,
                        420_000,
                        465_000,
                        counters = mapOf("fallbackRunCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        260_000,
                        340_000,
                        375_000,
                        counters = mapOf("gsubLookupCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        210_000,
                        275_000,
                        305_000,
                        counters = mapOf("gposLookupCount" to 2L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 5, 5, 5),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 3, 3, 3),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "fallback-cluster-cjk-vs",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-cjk-vendored-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 155_000, 205_000, 225_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 45_000, 65_000, 75_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 120_000, 150_000, 170_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        140_000,
                        190_000,
                        210_000,
                        counters = mapOf("fallbackRunCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        95_000,
                        125_000,
                        145_000,
                        counters = mapOf("gsubLookupCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        90_000,
                        120_000,
                        140_000,
                        counters = mapOf("gposLookupCount" to 0L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 2, 2, 2),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 1, 1, 1),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "fallback-cluster-mixed-bidi",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Mixed,
                fontSourceSetHash = "fontset-mixed-fallback-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 205_000, 270_000, 300_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 180_000, 235_000, 260_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 145_000, 190_000, 210_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        510_000,
                        650_000,
                        710_000,
                        counters = mapOf("fallbackRunCount" to 2L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        350_000,
                        430_000,
                        470_000,
                        counters = mapOf("gsubLookupCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        240_000,
                        310_000,
                        345_000,
                        counters = mapOf("gposLookupCount" to 1L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 10, 10, 10),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 8, 8, 8),
                ),
                diagnostics = listOf("text.shaping.fallback-missing"),
            ),
            FontTelemetrySample(
                fixtureId = "emoji-sequence-refusal",
                domain = FontTelemetryDomain.Shaping,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Mixed,
                fontSourceSetHash = "fontset-emoji-sequence-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("shaping.segmentation.time", FontTelemetryUnit.Nanoseconds, 215_000, 280_000, 315_000),
                    FontTelemetryMetricSeries("shaping.bidi.time", FontTelemetryUnit.Nanoseconds, 50_000, 72_000, 88_000),
                    FontTelemetryMetricSeries("shaping.script-itemization.time", FontTelemetryUnit.Nanoseconds, 135_000, 175_000, 198_000),
                    FontTelemetryMetricSeries(
                        "shaping.fallback.time",
                        FontTelemetryUnit.Nanoseconds,
                        415_000,
                        540_000,
                        590_000,
                        counters = mapOf("fallbackRunCount" to 1L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gsub.time",
                        FontTelemetryUnit.Nanoseconds,
                        230_000,
                        300_000,
                        330_000,
                        counters = mapOf("gsubLookupCount" to 0L),
                    ),
                    FontTelemetryMetricSeries(
                        "shaping.gpos.time",
                        FontTelemetryUnit.Nanoseconds,
                        120_000,
                        165_000,
                        190_000,
                        counters = mapOf("gposLookupCount" to 0L),
                    ),
                    FontTelemetryMetricSeries("shaping.glyph.count", FontTelemetryUnit.Count, 0, 0, 0),
                    FontTelemetryMetricSeries("shaping.cluster.count", FontTelemetryUnit.Count, 1, 1, 1),
                ),
                diagnostics = listOf("text.shaping.emoji-sequence-unsupported"),
            ),
        ),
        negativeCases = emptyList(),
        nonClaims = commonNonClaims(),
    ).toCanonicalJson()

    fun writeParagraphMetricsJson(): String = FontTelemetryDomainDump(
        dumpId = "paragraph-metrics",
        ownerTickets = listOf("KFONT-M12-003"),
        domain = FontTelemetryDomain.Paragraph,
        samples = listOf(
            FontTelemetrySample(
                fixtureId = "paragraph-shaping-requests",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-rich-style-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("paragraph.layout.time", FontTelemetryUnit.Nanoseconds, 2_100_000, 2_400_000, 2_650_000),
                    FontTelemetryMetricSeries("paragraph.line-break-opportunity.count", FontTelemetryUnit.Count, 11, 11, 11),
                    FontTelemetryMetricSeries("paragraph.shaped-run.count", FontTelemetryUnit.Count, 4, 4, 4),
                    FontTelemetryMetricSeries("paragraph.line.count", FontTelemetryUnit.Count, 3, 3, 3),
                    FontTelemetryMetricSeries("paragraph.hit-test.time", FontTelemetryUnit.Nanoseconds, 170_000, 220_000, 250_000),
                    FontTelemetryMetricSeries("paragraph.selection-query.time", FontTelemetryUnit.Nanoseconds, 160_000, 205_000, 235_000),
                    FontTelemetryMetricSeries("paragraph.ellipsis.attempt.count", FontTelemetryUnit.Count, 0, 0, 0),
                    FontTelemetryMetricSeries("paragraph.placeholder.count", FontTelemetryUnit.Count, 1, 1, 1),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "paragraph-layout",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-rich-style-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("paragraph.layout.time", FontTelemetryUnit.Nanoseconds, 2_350_000, 2_700_000, 2_950_000),
                    FontTelemetryMetricSeries("paragraph.line-break-opportunity.count", FontTelemetryUnit.Count, 15, 15, 15),
                    FontTelemetryMetricSeries("paragraph.shaped-run.count", FontTelemetryUnit.Count, 5, 5, 5),
                    FontTelemetryMetricSeries("paragraph.line.count", FontTelemetryUnit.Count, 4, 4, 4),
                    FontTelemetryMetricSeries("paragraph.hit-test.time", FontTelemetryUnit.Nanoseconds, 190_000, 245_000, 275_000),
                    FontTelemetryMetricSeries("paragraph.selection-query.time", FontTelemetryUnit.Nanoseconds, 175_000, 225_000, 255_000),
                    FontTelemetryMetricSeries("paragraph.ellipsis.attempt.count", FontTelemetryUnit.Count, 0, 0, 0),
                    FontTelemetryMetricSeries("paragraph.placeholder.count", FontTelemetryUnit.Count, 1, 1, 1),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "line-breaks",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-mixed-bidi-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("paragraph.layout.time", FontTelemetryUnit.Nanoseconds, 2_600_000, 2_980_000, 3_200_000),
                    FontTelemetryMetricSeries("paragraph.line-break-opportunity.count", FontTelemetryUnit.Count, 18, 18, 18),
                    FontTelemetryMetricSeries("paragraph.shaped-run.count", FontTelemetryUnit.Count, 6, 6, 6),
                    FontTelemetryMetricSeries("paragraph.line.count", FontTelemetryUnit.Count, 4, 4, 4),
                    FontTelemetryMetricSeries("paragraph.hit-test.time", FontTelemetryUnit.Nanoseconds, 205_000, 260_000, 290_000),
                    FontTelemetryMetricSeries("paragraph.selection-query.time", FontTelemetryUnit.Nanoseconds, 195_000, 250_000, 285_000),
                    FontTelemetryMetricSeries("paragraph.ellipsis.attempt.count", FontTelemetryUnit.Count, 0, 0, 0),
                    FontTelemetryMetricSeries("paragraph.placeholder.count", FontTelemetryUnit.Count, 0, 0, 0),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "hit-test-map",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-rich-style-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("paragraph.layout.time", FontTelemetryUnit.Nanoseconds, 1_950_000, 2_240_000, 2_410_000),
                    FontTelemetryMetricSeries("paragraph.line-break-opportunity.count", FontTelemetryUnit.Count, 9, 9, 9),
                    FontTelemetryMetricSeries("paragraph.shaped-run.count", FontTelemetryUnit.Count, 3, 3, 3),
                    FontTelemetryMetricSeries("paragraph.line.count", FontTelemetryUnit.Count, 2, 2, 2),
                    FontTelemetryMetricSeries("paragraph.hit-test.time", FontTelemetryUnit.Nanoseconds, 95_000, 125_000, 150_000),
                    FontTelemetryMetricSeries("paragraph.selection-query.time", FontTelemetryUnit.Nanoseconds, 130_000, 175_000, 205_000),
                    FontTelemetryMetricSeries("paragraph.ellipsis.attempt.count", FontTelemetryUnit.Count, 0, 0, 0),
                    FontTelemetryMetricSeries("paragraph.placeholder.count", FontTelemetryUnit.Count, 1, 1, 1),
                ),
            ),
            FontTelemetrySample(
                fixtureId = "placeholder-layout",
                domain = FontTelemetryDomain.Paragraph,
                measurementPhase = "steady-state",
                sampleCount = 5,
                cacheState = FontTelemetryCacheState.Warm,
                fontSourceSetHash = "fontset-rich-style-bundled-v1",
                unicodeDataVersion = "16.0.0",
                environment = defaultEnvironment(),
                metrics = listOf(
                    FontTelemetryMetricSeries("paragraph.layout.time", FontTelemetryUnit.Nanoseconds, 2_420_000, 2_760_000, 3_040_000),
                    FontTelemetryMetricSeries("paragraph.line-break-opportunity.count", FontTelemetryUnit.Count, 12, 12, 12),
                    FontTelemetryMetricSeries("paragraph.shaped-run.count", FontTelemetryUnit.Count, 4, 4, 4),
                    FontTelemetryMetricSeries("paragraph.line.count", FontTelemetryUnit.Count, 3, 3, 3),
                    FontTelemetryMetricSeries("paragraph.hit-test.time", FontTelemetryUnit.Nanoseconds, 115_000, 145_000, 175_000),
                    FontTelemetryMetricSeries("paragraph.selection-query.time", FontTelemetryUnit.Nanoseconds, 145_000, 185_000, 215_000),
                    FontTelemetryMetricSeries("paragraph.ellipsis.attempt.count", FontTelemetryUnit.Count, 1, 1, 1),
                    FontTelemetryMetricSeries("paragraph.placeholder.count", FontTelemetryUnit.Count, 2, 2, 2),
                ),
                diagnostics = listOf("text.paragraph.placeholder-ellipsis-conflict"),
            ),
        ),
        negativeCases = emptyList(),
        nonClaims = commonNonClaims(),
    ).toCanonicalJson()

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

    private fun commonNonClaims(): List<String> = listOf(
        "no-complete-target-support-claim",
        "no-performance-release-gate-claim",
        "no-gpu-route-support-claim",
        "no-native-engine-oracle-claim",
    )

    private fun defaultEnvironment(): FontTelemetryEnvironment = FontTelemetryEnvironment(
        environmentLabel = "developer-desktop",
        runtimeLabel = "jdk-25-kotlin-2.3",
    )

    private fun defaultNonClaims(): List<String> = commonNonClaims()
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

private fun String.isStableDiagnosticToken(): Boolean =
    (startsWith("font.") || startsWith("text.")) && isStableTelemetryToken()
