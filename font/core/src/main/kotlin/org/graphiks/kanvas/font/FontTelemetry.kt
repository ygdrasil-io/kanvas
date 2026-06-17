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
    val ownerTicket: String,
    val domain: FontTelemetryDomain,
    val trendSeriesPrefix: String,
    val samples: List<FontTelemetrySample>,
    val negativeCases: List<FontTelemetryNegativeCase>,
    val nonClaims: List<String>,
) {
    init {
        require(dumpId.isStableTelemetryToken()) { "dumpId must be a stable one-line token." }
        require(ownerTicket.isNotBlank()) { "ownerTicket must not be blank." }
        require(trendSeriesPrefix.isStableTelemetryToken()) {
            "trendSeriesPrefix must be a stable one-line token."
        }
        require(samples.isNotEmpty()) { "samples must not be empty." }
        require(nonClaims.all { it.isStableTelemetryToken() }) {
            "nonClaims must use stable one-line tokens."
        }
    }

    fun toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": 1,\n")
        append("  \"dumpId\": ").append(dumpId.quoted()).append(",\n")
        append("  \"ownerTickets\": [").append(ownerTicket.quoted()).append("],\n")
        append("  \"domain\": ").append(domain.serializedName.quoted()).append(",\n")
        append("  \"trendSeriesPrefix\": ").append(trendSeriesPrefix.quoted()).append(",\n")
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
        ownerTicket = "KFONT-M12-002",
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
        ownerTicket = "KFONT-M12-002",
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

    private fun defaultEnvironment(): FontTelemetryEnvironment = FontTelemetryEnvironment(
        environmentLabel = "developer-desktop",
        runtimeLabel = "jdk-25-kotlin-2.3",
    )

    private fun defaultNonClaims(): List<String> = listOf(
        "no-complete-target-support-claim",
        "no-performance-release-gate-claim",
        "no-gpu-route-support-claim",
        "no-native-engine-oracle-claim",
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

private fun String.isStableDiagnosticToken(): Boolean =
    (startsWith("font.") || startsWith("text.")) && isStableTelemetryToken()
