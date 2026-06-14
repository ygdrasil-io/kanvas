package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextTelemetrySurfaceTest {
    @Test
    fun `telemetry snapshot exposes deterministic advisory text artifact facts`() {
        val bundle = fixtureBundle(refusalRequired = true)
        val metadata = GPUTextTelemetrySampleMetadata(
            environmentLabel = "developer-desktop",
            sampleLabel = "single-fixture-run",
            sampleCount = 1,
            cacheState = "warm",
        )
        val a8Cache = GPUTextCacheTelemetryRecord(
            cacheName = "a8-atlas",
            keyPreimage = "strike=Latn:size=16:route=A8",
            hits = 3,
            misses = 1,
            evictions = 0,
            residentBytes = 1024,
            generationToken = "gen-001",
        )
        val sdfCache = GPUTextCacheTelemetryRecord(
            cacheName = "sdf-atlas",
            keyPreimage = "strike=Latn:size=32:route=SDF",
            hits = 1,
            misses = 2,
            evictions = 1,
            residentBytes = 2048,
            generationToken = "gen-002",
        )
        val uploadBudget = GPUTextAdvisoryBudgetRecord(
            metricName = "upload.bytes",
            budgetName = "warm-ui-text",
            observedValue = 192,
            advisoryLimit = 262_144,
            unit = "bytes",
            sampleCount = 1,
        )
        val cacheBudget = GPUTextAdvisoryBudgetRecord(
            metricName = "cache.resident.bytes",
            budgetName = "developer-desktop",
            observedValue = 3072,
            advisoryLimit = 8_388_608,
            unit = "bytes",
            sampleCount = 1,
        )

        val snapshot = bundle.telemetrySnapshot(
            metadata = metadata,
            cacheRecords = listOf(sdfCache, a8Cache),
            advisoryBudgets = listOf(uploadBudget, cacheBudget),
        )
        val repeat = bundle.telemetrySnapshot(
            metadata = metadata,
            cacheRecords = listOf(a8Cache, sdfCache),
            advisoryBudgets = listOf(cacheBudget, uploadBudget),
        )

        assertEquals(snapshot.toCanonicalJson(), repeat.toCanonicalJson())
        assertEquals(8, snapshot.counters.artifactReferenceCount)
        assertEquals(2, snapshot.counters.uploadPlanCount)
        assertEquals(192, snapshot.counters.uploadBytes)
        assertEquals(3, snapshot.counters.uploadRangeCount)
        assertEquals(1, snapshot.counters.glyphUploadPlanCount)
        assertEquals(3, snapshot.counters.glyphCount)
        assertEquals(1, snapshot.counters.a8AtlasCount)
        assertEquals(1, snapshot.counters.sdfAtlasCount)
        assertEquals(2, snapshot.counters.outlinePlanCount)
        assertEquals(1, snapshot.counters.colorPlanCount)
        assertEquals(1, snapshot.counters.bitmapPlanCount)
        assertEquals(1, snapshot.counters.svgPlanCount)
        assertEquals(2, snapshot.counters.diagnosticCount)
        assertEquals(1, snapshot.counters.refusalRequired)

        val glyphUpload = snapshot.cpuUploadPlans.single { it.contentFingerprint == "glyph-upload-cpu" }
        assertEquals(64, glyphUpload.byteSize)
        assertEquals(2, glyphUpload.rangeCount)
        assertEquals(3, glyphUpload.glyphCount)
        val atlasUpload = snapshot.cpuUploadPlans.single { it.contentFingerprint == "atlas-upload-cpu" }
        assertEquals(128, atlasUpload.byteSize)
        assertEquals(1, atlasUpload.rangeCount)
        assertEquals(null, atlasUpload.glyphCount)
        assertEquals(emptyList(), snapshot.gpuUploadFacts)

        assertEquals(listOf(a8Cache, sdfCache), snapshot.cacheRecords)
        assertEquals(listOf(cacheBudget, uploadBudget), snapshot.advisoryBudgets)
        assertFalse(snapshot.metadata.releaseGatePromoted)
        assertTrue(snapshot.advisoryBudgets.all { !it.blockingGate })

        val dump = snapshot.toCanonicalJson()
        assertTrue(dump.contains("\"keyPreimage\": \"strike=Latn:size=16:route=A8\""), dump)
        assertTrue(dump.contains("\"cpuUploadPlans\""), dump)
        assertTrue(dump.contains("\"gpuUploadFacts\": []"), dump)
        assertTrue(dump.contains("\"blockingGate\": false"), dump)
        assertTrue(dump.contains("\"releaseGatePromoted\": false"), dump)
        assertTrue(dump.indexOf("\"metadata\"") < dump.indexOf("\"counters\""), dump)
        assertTrue(dump.indexOf("\"cpuUploadPlans\"") < dump.indexOf("\"gpuUploadFacts\""), dump)
        assertTrue(dump.indexOf("\"cacheRecords\"") < dump.indexOf("\"advisoryBudgets\""), dump)
        assertTrue(dump.indexOf("\"cacheName\": \"a8-atlas\"") < dump.indexOf("\"cacheName\": \"sdf-atlas\""), dump)
        assertTrue(dump.indexOf("\"metricName\": \"cache.resident.bytes\"") < dump.indexOf("\"metricName\": \"upload.bytes\""), dump)
        assertEvidenceDumpClean(dump)
    }

    @Test
    fun `default snapshot does not invent cache budget or gpu upload facts`() {
        val snapshot = fixtureBundle(refusalRequired = false).telemetrySnapshot(
            metadata = GPUTextTelemetrySampleMetadata(
                environmentLabel = "ci",
                sampleLabel = "no-cache-sample",
                sampleCount = 1,
                cacheState = "unknown",
            ),
        )

        assertEquals(emptyList(), snapshot.cacheRecords)
        assertEquals(emptyList(), snapshot.advisoryBudgets)
        assertEquals(emptyList(), snapshot.gpuUploadFacts)
        assertEquals(0, snapshot.counters.refusalRequired)
        val dump = snapshot.toCanonicalJson()
        assertTrue(dump.contains("\"cacheRecords\": []"), dump)
        assertTrue(dump.contains("\"advisoryBudgets\": []"), dump)
        assertTrue(dump.contains("\"gpuUploadFacts\": []"), dump)
    }

    @Test
    fun `telemetry snapshot ordering covers all serialized caller supplied row fields`() {
        val bundle = fixtureBundle(refusalRequired = false)
        val metadata = fixtureMetadata()
        val cacheLow = GPUTextCacheTelemetryRecord(
            cacheName = "atlas",
            keyPreimage = "strike=Latn:size=16",
            hits = 1,
            misses = 2,
            evictions = 0,
            residentBytes = 512,
            generationToken = "gen",
        )
        val cacheHigh = GPUTextCacheTelemetryRecord(
            cacheName = "atlas",
            keyPreimage = "strike=Latn:size=16",
            hits = 4,
            misses = 0,
            evictions = 1,
            residentBytes = 2048,
            generationToken = "gen",
        )
        val budgetLow = GPUTextAdvisoryBudgetRecord(
            metricName = "upload.bytes",
            budgetName = "warm-ui-text",
            observedValue = 64,
            advisoryLimit = 262_144,
            unit = "bytes",
            sampleCount = 1,
        )
        val budgetHigh = GPUTextAdvisoryBudgetRecord(
            metricName = "upload.bytes",
            budgetName = "warm-ui-text",
            observedValue = 192,
            advisoryLimit = 262_144,
            unit = "bytes",
            sampleCount = 2,
        )
        val gpuUploadSmall = GPUTextGPUUploadTelemetryRecord(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655443001")),
            generation = GPUTextArtifactGeneration(2),
            contentFingerprint = "gpu-upload",
            byteSize = 64,
            rangeCount = 1,
            glyphCount = 1,
            sourceLabel = "renderer-supplied",
        )
        val gpuUploadLarge = GPUTextGPUUploadTelemetryRecord(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655443001")),
            generation = GPUTextArtifactGeneration(2),
            contentFingerprint = "gpu-upload",
            byteSize = 128,
            rangeCount = 2,
            glyphCount = 3,
            sourceLabel = "renderer-supplied",
        )

        val first = bundle.telemetrySnapshot(
            metadata = metadata,
            cacheRecords = listOf(cacheHigh, cacheLow),
            advisoryBudgets = listOf(budgetHigh, budgetLow),
            gpuUploadFacts = listOf(gpuUploadLarge, gpuUploadSmall),
        )
        val second = bundle.telemetrySnapshot(
            metadata = metadata,
            cacheRecords = listOf(cacheLow, cacheHigh),
            advisoryBudgets = listOf(budgetLow, budgetHigh),
            gpuUploadFacts = listOf(gpuUploadSmall, gpuUploadLarge),
        )

        assertEquals(first.toCanonicalJson(), second.toCanonicalJson())
        assertEquals(listOf(cacheLow, cacheHigh), first.cacheRecords)
        assertEquals(listOf(budgetLow, budgetHigh), first.advisoryBudgets)
        assertEquals(listOf(gpuUploadSmall, gpuUploadLarge), first.gpuUploadFacts)
    }

    @Test
    fun `telemetry snapshot rejects upload ranges that exceed declared byte size`() {
        val overflowKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655443010",
            generation = 1,
            contentFingerprint = "overflow-upload",
        )
        val overflowPlan = GPUTextUploadPlan(
            artifactKey = overflowKey,
            ranges = listOf(GPUTextUploadRange(offset = 8, size = 12, label = "overflow")),
            byteSize = 16,
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            fixtureBundle(refusalRequired = false)
                .copy(uploadPlans = listOf(overflowPlan), glyphUploadPlans = emptyList())
                .telemetrySnapshot(metadata = fixtureMetadata())
        }

        assertNotNull(failure.message)
        assertTrue(failure.message!!.contains("exceeds byteSize"), failure.message)
    }

    @Test
    fun `telemetry snapshot rejects inconsistent glyph upload plan attribution`() {
        val bundle = fixtureBundle(refusalRequired = false)
        val knownUploadPlan = bundle.uploadPlans.first()
        val mismatchedGlyphPlan = GlyphUploadPlan(
            artifactKey = fixtureArtifactKey(
                uuid = "550e8400-e29b-41d4-a716-446655443020",
                generation = 2,
                contentFingerprint = "mismatched-glyph-plan",
            ),
            uploadPlan = knownUploadPlan,
            glyphIDs = listOf(7U),
        )
        val mismatchFailure = assertFailsWith<IllegalArgumentException> {
            bundle.copy(glyphUploadPlans = listOf(mismatchedGlyphPlan))
                .telemetrySnapshot(metadata = fixtureMetadata())
        }

        assertNotNull(mismatchFailure.message)
        assertTrue(mismatchFailure.message!!.contains("artifactKey"), mismatchFailure.message)

        val foreignPlan = GPUTextUploadPlan(
            artifactKey = fixtureArtifactKey(
                uuid = "550e8400-e29b-41d4-a716-446655443021",
                generation = 3,
                contentFingerprint = "foreign-upload-plan",
            ),
            ranges = listOf(GPUTextUploadRange(offset = 0, size = 4, label = "foreign")),
            byteSize = 4,
        )
        val foreignGlyphPlan = GlyphUploadPlan(
            artifactKey = foreignPlan.artifactKey,
            uploadPlan = foreignPlan,
            glyphIDs = listOf(8U),
        )
        val foreignFailure = assertFailsWith<IllegalArgumentException> {
            bundle.copy(glyphUploadPlans = listOf(foreignGlyphPlan))
                .telemetrySnapshot(metadata = fixtureMetadata())
        }

        assertNotNull(foreignFailure.message)
        assertTrue(foreignFailure.message!!.contains("bundle uploadPlans"), foreignFailure.message)
    }

    @Test
    fun `telemetry constructors reject negative counters bytes blank labels and blocking gates`() {
        val counterFailure = assertFailsWith<IllegalArgumentException> {
            GPUTextTelemetryCounters(
                artifactReferenceCount = -1,
                uploadPlanCount = 0,
                uploadBytes = 0,
                uploadRangeCount = 0,
                glyphUploadPlanCount = 0,
                glyphCount = 0,
                a8AtlasCount = 0,
                sdfAtlasCount = 0,
                outlinePlanCount = 0,
                colorPlanCount = 0,
                bitmapPlanCount = 0,
                svgPlanCount = 0,
                diagnosticCount = 0,
                refusalRequired = 0,
            )
        }
        assertNotNull(counterFailure.message)

        assertFailsWith<IllegalArgumentException> {
            GPUTextCPUUploadTelemetryRecord(
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655442001")),
                generation = GPUTextArtifactGeneration(1),
                contentFingerprint = "glyph-upload",
                byteSize = -1,
                rangeCount = 1,
                glyphCount = 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextCPUUploadTelemetryRecord(
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655442002")),
                generation = GPUTextArtifactGeneration(1),
                contentFingerprint = " ",
                byteSize = 1,
                rangeCount = 1,
                glyphCount = 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextCacheTelemetryRecord(
                cacheName = " ",
                keyPreimage = "strike",
                hits = 0,
                misses = 0,
                evictions = 0,
                residentBytes = 0,
                generationToken = "gen",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextCacheTelemetryRecord(
                cacheName = "atlas",
                keyPreimage = " ",
                hits = 0,
                misses = 0,
                evictions = 0,
                residentBytes = 0,
                generationToken = "gen",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextAdvisoryBudgetRecord(
                metricName = "upload.bytes",
                budgetName = "warm-ui-text",
                observedValue = 1,
                advisoryLimit = 2,
                unit = "bytes",
                sampleCount = 1,
                blockingGate = true,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextAdvisoryBudgetRecord(
                metricName = " ",
                budgetName = "warm-ui-text",
                observedValue = 1,
                advisoryLimit = 2,
                unit = "bytes",
                sampleCount = 1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextTelemetrySampleMetadata(
                environmentLabel = "developer-desktop",
                sampleLabel = "single-run",
                sampleCount = 1,
                cacheState = "warm",
                releaseGatePromoted = true,
            )
        }
    }

    private fun fixtureBundle(refusalRequired: Boolean): TextGPUArtifactBundle {
        val rootKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441000",
            generation = 0,
            contentFingerprint = "bundle-root",
        )
        val atlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441001",
            generation = 1,
            contentFingerprint = "glyph-atlas-a8",
        )
        val sdfAtlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441002",
            generation = 2,
            contentFingerprint = "glyph-atlas-sdf",
        )
        val glyphUploadKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441003",
            generation = 3,
            contentFingerprint = "glyph-upload-cpu",
        )
        val atlasUploadKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441004",
            generation = 4,
            contentFingerprint = "atlas-upload-cpu",
        )
        val outlineAKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441005",
            generation = 5,
            contentFingerprint = "outline-a",
        )
        val outlineBKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441006",
            generation = 6,
            contentFingerprint = "outline-b",
        )
        val colorKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441007",
            generation = 7,
            contentFingerprint = "color-plan",
        )
        val bitmapKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441008",
            generation = 8,
            contentFingerprint = "bitmap-plan",
        )
        val svgKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655441009",
            generation = 9,
            contentFingerprint = "svg-plan",
        )
        val glyphUploadPlan = GPUTextUploadPlan(
            artifactKey = glyphUploadKey,
            ranges = listOf(
                GPUTextUploadRange(offset = 0, size = 16, label = "glyph-header"),
                GPUTextUploadRange(offset = 16, size = 48, label = "glyph-masks"),
            ),
            byteSize = 64,
        )
        val atlasUploadPlan = GPUTextUploadPlan(
            artifactKey = atlasUploadKey,
            ranges = listOf(GPUTextUploadRange(offset = 0, size = 128, label = "atlas-page")),
            byteSize = 128,
        )

        return TextGPUArtifactBundle(
            artifactKey = rootKey,
            uploadPlans = listOf(glyphUploadPlan, atlasUploadPlan),
            glyphUploadPlans = listOf(
                GlyphUploadPlan(
                    artifactKey = glyphUploadKey,
                    uploadPlan = glyphUploadPlan,
                    glyphIDs = listOf(7U, 8U, 9U),
                ),
            ),
            outlineGlyphPlans = listOf(
                OutlineGlyphPlan(
                    artifactKey = outlineAKey,
                    glyphIDs = listOf(7U),
                    windingRule = "non-zero",
                ),
                OutlineGlyphPlan(
                    artifactKey = outlineBKey,
                    glyphIDs = listOf(8U),
                    windingRule = "even-odd",
                ),
            ),
            colorGlyphPlans = listOf(
                ColorGlyphPlan(
                    artifactKey = colorKey,
                    glyphIDs = listOf(10U),
                    layerCount = 2,
                ),
            ),
            bitmapGlyphPlans = listOf(
                BitmapGlyphPlan(
                    artifactKey = bitmapKey,
                    glyphIDs = listOf(11U),
                    colorFormat = "rgba8888",
                ),
            ),
            svgGlyphPlans = listOf(
                SVGGlyphPlan(
                    artifactKey = svgKey,
                    glyphIDs = listOf(12U),
                    documentCount = 1,
                ),
            ),
            atlases = listOf(
                GlyphAtlasArtifact(
                    artifactKey = atlasKey,
                    width = 128,
                    height = 128,
                    format = "r8",
                ),
            ),
            sdfAtlases = listOf(
                SDFGlyphAtlasArtifact(
                    atlas = GlyphAtlasArtifact(
                        artifactKey = sdfAtlasKey,
                        width = 256,
                        height = 256,
                        format = "r8",
                    ),
                    distanceRange = 4.0f,
                ),
            ),
            diagnostics = GPUTextRouteDiagnostics(
                diagnostics = listOf(
                    GPUTextArtifactDiagnostic(
                        code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
                        message = "Glyph 99 is missing.",
                    ),
                    GPUTextArtifactDiagnostic(
                        code = GPUTextArtifactDiagnosticCode.EXPLICIT_REFUSAL_REQUIRED,
                        message = "The route must refuse unsupported text.",
                    ),
                ),
                refusalRequired = refusalRequired,
            ),
        )
    }

    private fun fixtureArtifactKey(
        uuid: String,
        generation: Int,
        contentFingerprint: String,
    ): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse(uuid)),
        generation = GPUTextArtifactGeneration(generation),
        contentFingerprint = contentFingerprint,
    )

    private fun fixtureMetadata(): GPUTextTelemetrySampleMetadata = GPUTextTelemetrySampleMetadata(
        environmentLabel = "developer-desktop",
        sampleLabel = "single-fixture-run",
        sampleCount = 1,
        cacheState = "warm",
    )

    private fun assertEvidenceDumpClean(dump: String) {
        listOf(
            "Sk",
            "Texture",
            "Sampler",
            "BindGroup",
            "CommandEncoder",
            "GPUHandle",
            "Native",
            "fontParser",
            "renderer=",
        ).forEach { forbiddenToken ->
            assertFalse(
                dump.contains(forbiddenToken),
                "Telemetry dump leaked forbidden token $forbiddenToken: $dump",
            )
        }
    }
}
