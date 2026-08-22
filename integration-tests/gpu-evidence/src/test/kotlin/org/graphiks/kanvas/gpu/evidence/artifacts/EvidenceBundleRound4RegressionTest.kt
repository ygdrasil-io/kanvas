package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.catalog.StructuralEventEvidence
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleRound4RegressionTest {
    @Test fun `failure retention uses handle relative writes and drains partial channels`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val filesystem = RecordingSecureEvidenceFilesystem(root, maxBytesPerWrite = 2)
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, secureFilesystem = filesystem)

        assertFailsWith<IllegalArgumentException> {
            writer.writeGenerated(renderDescriptor(), rendered(), expectedRgba = ByteArray(3), attemptId = "attempt")
        }

        assertEquals(
            listOf("_failed", "render-scene-attempt"),
            filesystem.createdDirectories,
        )
        assertEquals(
            listOf(
                "reports",
                "reports/gpu-renderer",
                "reports/gpu-renderer/evidence",
                "reports/gpu-renderer/evidence/correctness",
                "reports/gpu-renderer/evidence/correctness/generated",
                "reports/gpu-renderer/evidence/correctness/generated/$COMMIT",
                "reports/gpu-renderer/evidence/correctness/generated/$COMMIT/_failed",
                "reports/gpu-renderer/evidence/correctness/generated/$COMMIT/_failed/render-scene-attempt",
            ),
            filesystem.openedDirectories,
        )
        assertEquals(listOf("diagnostics.json", "environment.json"), filesystem.openedFiles)
        assertTrue(filesystem.partialWrites > 2, "the test channel must have forced multiple writes")
        val failed = root.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT/_failed/render-scene-attempt")
        assertTrue(Files.readString(failed.resolve("diagnostics.json")).contains("CPU RGBA byte count does not match descriptor"))
        assertTrue(Files.readString(failed.resolve("environment.json")).contains("\"sourceCommit\":\"$COMMIT\""))
    }

    @Test fun `writer serializes every v1 field with its complete value`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val telemetry = GPUBackendRuntimeTelemetry(
            renderPasses = 1, offscreenPasses = 2, windowPasses = 3, submissions = 4, commandBuffers = 5,
            buffersCreated = 6, texturesCreated = 7, intermediateTexturesCreated = 8, coverageMasksDestroyed = 9,
            destinationCopies = 10, destinationReadbackSnapshots = 11, msaaTargets = 12, msaaResolves = 13,
            bindGroupsCreated = 14, samplersCreated = 15, queueWrites = 16, uniformSlabsCreated = 17,
            uniformSlabBytesAllocated = 18, uniformSlabFallbacks = 19, passBatchPlans = 20,
            passBatchesAccepted = 21, passBatchCuts = 22, passBatchPackets = 23,
        )
        val observation = SceneObservation.Rendered(
            rgba = PIXELS,
            route = RouteEvidence(
                routeId = "route-id",
                attemptId = "attempt-9",
                furthestPhase = "submitted",
                outcome = "rendered",
                encodedScopeKinds = listOf("clip", "layer"),
                structuralEvents = listOf(StructuralEventEvidence("draw", "record", "first-draw")),
                structuralCounters = mapOf("draws" to 3L, "clips" to 2L),
                runtimeTelemetryDelta = telemetry,
            ),
            diagnostics = listOf("diagnostic-a", "diagnostic-b"),
            environment = EvidenceEnvironment(
                sourceCommit = COMMIT,
                osName = "TestOS",
                osVersion = "42",
                osArchitecture = "test-arch",
                javaVersion = "25",
                adapter = EvidenceAdapter("adapter summary", "vendor", "device", "architecture", "description", false),
                deviceGeneration = 77L,
                capabilityImplementation = "capability-id",
                available = true,
            ),
            comparison = ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(8), 9),
        )
        val path = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGenerated(
            descriptor = EvidenceSceneDescriptor(
                id = EvidenceSceneId("render-scene"), title = "Render", purpose = "Purpose", width = 2, height = 1,
                seed = 1L, tags = emptySet(), expectation = EvidenceExpectation.ShouldRender,
                oracle = OraclePolicy.CheckedInPng("reference.png", sha256(ORACLE_PNG), "release-reference"),
                comparison = ComparisonPolicy(7, 99.0, 9, "test"), requiredCapabilities = emptySet(),
            ),
            observation = observation,
            expectedRgba = PIXELS,
            attemptId = "attempt-9",
            checkedInPngBytes = ORACLE_PNG,
        )

        val manifest = json(path, "manifest.json")
        assertEquals(setOf("schemaVersion", "sceneId", "expectation", "observedOutcome", "sourceCommit", "generatedAtUtc", "oracleKind", "oracleId", "oracleVersion", "oracleProvenance", "oracleSha256", "files"), manifest.keys)
        assertEquals(GPU_EVIDENCE_SCHEMA, manifest.string("schemaVersion")); assertEquals("render-scene", manifest.string("sceneId"))
        assertEquals("render", manifest.string("expectation")); assertEquals("rendered", manifest.string("observedOutcome"))
        assertEquals(COMMIT, manifest.string("sourceCommit")); assertEquals("1970-01-01T00:00:00Z", manifest.string("generatedAtUtc"))
        assertEquals("checked-in-png", manifest.string("oracleKind")); assertEquals("reference.png", manifest.string("oracleId"))
        assertEquals(1, manifest.int("oracleVersion")); assertEquals("release-reference", manifest.string("oracleProvenance"))
        assertEquals(sha256(ORACLE_PNG), manifest.string("oracleSha256"))
        val expectedFiles = setOf("gpu.png", "skia.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
        val hashes = manifest["files"]!!.jsonObject
        assertEquals(expectedFiles, hashes.keys)
        expectedFiles.forEach { name -> assertEquals(sha256(Files.readAllBytes(path.resolve(name))), hashes.string(name), name) }

        val stats = json(path, "stats.json")
        assertEquals(setOf("width", "height", "colorFormat", "colorInterpretation", "tolerance", "minimumSimilarityPercent", "similarityPercent", "differingPixels", "maxChannelDifference", "meanChannelDifference", "pass"), stats.keys)
        assertEquals(2, stats.int("width")); assertEquals(1, stats.int("height")); assertEquals("rgba8unorm", stats.string("colorFormat")); assertEquals("encoded-premul-srgb", stats.string("colorInterpretation"))
        assertEquals(7, stats.int("tolerance")); assertEquals(99.0, stats.double("minimumSimilarityPercent")); assertEquals(100.0, stats.double("similarityPercent")); assertEquals(0, stats.int("differingPixels")); assertEquals(0, stats.int("maxChannelDifference")); assertEquals(0.0, stats.double("meanChannelDifference")); assertEquals(true, stats.boolean("pass"))

        val route = json(path, "route.json")
        assertEquals(setOf("routeId", "attemptId", "furthestPhase", "outcome", "encodedScopeKinds", "structuralEvents", "structuralCounters", "runtimeTelemetryDelta"), route.keys)
        assertEquals("route-id", route.string("routeId")); assertEquals("attempt-9", route.string("attemptId")); assertEquals("submitted", route.string("furthestPhase")); assertEquals("rendered", route.string("outcome"))
        assertEquals(listOf("clip", "layer"), route["encodedScopeKinds"]!!.jsonArray.map { it.jsonPrimitive.content })
        val event = route["structuralEvents"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("kind", "phase", "label"), event.keys); assertEquals("draw", event.string("kind")); assertEquals("record", event.string("phase")); assertEquals("first-draw", event.string("label"))
        val counters = route["structuralCounters"]!!.jsonObject
        assertEquals(mapOf("clips" to 2L, "draws" to 3L), counters.mapValues { it.value.jsonPrimitive.long })
        val telemetryJson = route["runtimeTelemetryDelta"]!!.jsonObject
        assertEquals(TELEMETRY, telemetryJson.mapValues { it.value.jsonPrimitive.long })

        val diagnostics = json(path, "diagnostics.json")
        assertEquals(setOf("attemptId", "diagnostics", "stableReasonCode", "message", "submissionDelta"), diagnostics.keys)
        assertEquals("attempt-9", diagnostics.string("attemptId")); assertEquals(listOf("diagnostic-a", "diagnostic-b"), diagnostics["diagnostics"]!!.jsonArray.map { it.jsonPrimitive.content }); assertEquals(JsonNull, diagnostics["stableReasonCode"]); assertEquals(JsonNull, diagnostics["message"]); assertEquals(4L, diagnostics["submissionDelta"]!!.jsonPrimitive.long)

        val environment = json(path, "environment.json")
        assertEquals(setOf("sourceCommit", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "capabilityImplementation", "available", "adapter"), environment.keys)
        assertEquals(COMMIT, environment.string("sourceCommit")); assertEquals("TestOS", environment.string("osName")); assertEquals("42", environment.string("osVersion")); assertEquals("test-arch", environment.string("osArchitecture")); assertEquals("25", environment.string("javaVersion")); assertEquals(77L, environment["deviceGeneration"]!!.jsonPrimitive.long); assertEquals("capability-id", environment.string("capabilityImplementation")); assertEquals(true, environment.boolean("available"))
        val adapter = environment["adapter"]!!.jsonObject
        assertEquals(setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter"), adapter.keys)
        assertEquals("adapter summary", adapter.string("summary")); assertEquals("vendor", adapter.string("vendor")); assertEquals("device", adapter.string("device")); assertEquals("architecture", adapter.string("architecture")); assertEquals("description", adapter.string("description")); assertEquals(false, adapter.boolean("isFallbackAdapter"))

        val verdict = json(path, "verdict.json")
        assertEquals(setOf("expectation", "observedOutcome", "verdictKind", "reason"), verdict.keys)
        assertEquals("render", verdict.string("expectation")); assertEquals("rendered", verdict.string("observedOutcome")); assertEquals("pass", verdict.string("verdictKind")); assertEquals("rendered image passed comparison", verdict.string("reason"))
    }

    @Test fun `failed replacement restores byte-identical bundle and cleans backup`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val baseline = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        val original: Map<String, ByteArray> = Files.list(baseline).use { stream ->
            stream.iterator().asSequence().associate { it.fileName.toString() to Files.readAllBytes(it) }
        }
        var moves = 0
        val installFailureThenRestore: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
            moves++
            when (moves) {
                1 -> Files.move(source, destination)
                2 -> throw java.nio.file.AtomicMoveNotSupportedException(source.toString(), destination.toString(), "injected")
                3 -> throw IOException("injected install failure")
                4 -> Files.move(source, destination)
                else -> error("unexpected move $moves (atomic=$atomic)")
            }
        }
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, installFailureThenRestore)

        assertFailsWith<IOException> { writer.writeGenerated(renderDescriptor(), rendered(), byteArrayOf(9, 8, 7, 6), "attempt-2") }

        assertEquals(4, moves)
        assertEquals(original.keys, Files.list(baseline).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() })
        original.forEach { (name, bytes) -> assertContentEquals(bytes, Files.readAllBytes(baseline.resolve(name)), name) }
        assertFalse(Files.list(baseline.parent).use { stream -> stream.anyMatch { it.fileName.toString().contains(".backup-") } })
    }

    private fun json(path: Path, name: String) = EvidenceJson.parseToJsonElement(Files.readString(path.resolve(name))).jsonObject
    private fun kotlinx.serialization.json.JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun kotlinx.serialization.json.JsonObject.int(key: String) = this[key]!!.jsonPrimitive.int
    private fun kotlinx.serialization.json.JsonObject.double(key: String) = this[key]!!.jsonPrimitive.double
    private fun kotlinx.serialization.json.JsonObject.boolean(key: String) = this[key]!!.jsonPrimitive.boolean
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun rendered() = SceneObservation.Rendered(PIXEL, RouteEvidence("route", "attempt", "complete", "rendered", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty), emptyList(), EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "25", null, null, null, true), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))

    private class RecordingSecureEvidenceFilesystem(
        private val root: Path,
        private val maxBytesPerWrite: Int,
    ) : SecureEvidenceFilesystem {
        val createdDirectories = mutableListOf<String>()
        val openedDirectories = mutableListOf<String>()
        val openedFiles = mutableListOf<String>()
        var partialWrites = 0

        override fun openRoot(root: Path): SecureEvidenceDirectory = directory(this.root)

        private fun directory(path: Path): SecureEvidenceDirectory = object : SecureEvidenceDirectory {
            override fun openDirectory(name: String): SecureEvidenceDirectory? {
                val child = path.resolve(name)
                return if (Files.isDirectory(child, NOFOLLOW_LINKS) && !Files.isSymbolicLink(child)) {
                    openedDirectories += root.relativize(child).joinToString("/") { it.toString() }
                    directory(child)
                } else null
            }

            override fun createDirectory(name: String): SecureEvidenceDirectory {
                val child = path.resolve(name)
                Files.createDirectory(child)
                createdDirectories += root.relativize(child).joinToString("/") { it.toString() }.substringAfterLast('/')
                return openDirectory(name) ?: error("created directory was not opened through the handle")
            }

            override fun openNewFile(name: String): WritableByteChannel {
                openedFiles += name
                return object : WritableByteChannel {
                    private val delegate = Files.newByteChannel(path.resolve(name), CREATE_NEW, WRITE, NOFOLLOW_LINKS)
                    override fun isOpen(): Boolean = delegate.isOpen
                    override fun close() = delegate.close()
                    override fun write(source: ByteBuffer): Int {
                        val count = minOf(maxBytesPerWrite, source.remaining())
                        val slice = source.slice().apply { limit(count) }
                        val written = delegate.write(slice)
                        source.position(source.position() + written)
                        partialWrites++
                        return written
                    }
                }
            }

            override fun close() = Unit
        }
    }

    companion object {
        private const val COMMIT = "abc123"
        private val PIXEL = byteArrayOf(1, 2, 3, 4)
        private val PIXELS = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        private val ORACLE_PNG = byteArrayOf(42, 43, 44, 45)
        private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        private val TELEMETRY = linkedMapOf(
            "renderPasses" to 1L, "offscreenPasses" to 2L, "windowPasses" to 3L, "submissions" to 4L, "commandBuffers" to 5L,
            "buffersCreated" to 6L, "texturesCreated" to 7L, "intermediateTexturesCreated" to 8L, "coverageMasksDestroyed" to 9L,
            "destinationCopies" to 10L, "destinationReadbackSnapshots" to 11L, "msaaTargets" to 12L, "msaaResolves" to 13L,
            "bindGroupsCreated" to 14L, "samplersCreated" to 15L, "queueWrites" to 16L, "uniformSlabsCreated" to 17L,
            "uniformSlabBytesAllocated" to 18L, "uniformSlabFallbacks" to 19L, "passBatchPlans" to 20L,
            "passBatchesAccepted" to 21L, "passBatchCuts" to 22L, "passBatchPackets" to 23L,
        )
    }
}
