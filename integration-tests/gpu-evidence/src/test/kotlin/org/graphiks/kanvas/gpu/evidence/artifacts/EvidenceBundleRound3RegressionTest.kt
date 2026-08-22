package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleRound3RegressionTest {
    @Test fun `first failure retains typed artifacts under newly created failed path`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        kotlin.test.assertFailsWith<Throwable> { writer.writeGenerated(renderDescriptor(), rendered(), ByteArray(3), "attempt") }
        val failed = root.resolve("reports/gpu-renderer/evidence/correctness/generated/abc123/_failed/render-scene-attempt")
        assertTrue(Files.isRegularFile(failed.resolve("diagnostics.json")))
        assertTrue(Files.isRegularFile(failed.resolve("environment.json")))
        assertFalse(Files.isSymbolicLink(failed))
    }

    @Test fun `quoted structural counter is invalid after hash refresh`() {
        val path = bundle(renderDescriptor(), rendered())
        replace(path.resolve("route.json"), "\"structuralCounters\":{}", "\"structuralCounters\":{\"draws\":\"1\"}")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, COMMIT))
    }

    @Test fun `successful replacement keeps exact new bundle and all manifest evidence`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        val old = writer.writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        val replacement = writer.writeGenerated(renderDescriptor(), rendered(), byteArrayOf(9, 8, 7, 6), "attempt-2")
        assertEquals(old, replacement)
        val manifest = EvidenceJson.parseToJsonElement(Files.readString(replacement.resolve("manifest.json"))).jsonObject
        assertEquals(GPU_EVIDENCE_SCHEMA, manifest["schemaVersion"]!!.jsonPrimitive.content)
        val expectedFiles = setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
        assertEquals(expectedFiles, Files.list(replacement).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() })
        val files = manifest["files"]!!.jsonObject
        (expectedFiles - "manifest.json").forEach { file -> assertEquals(sha256(Files.readAllBytes(replacement.resolve(file))), files[file]!!.jsonPrimitive.content, file) }
        assertEquals("generated-cpu", manifest["oracleProvenance"]!!.jsonPrimitive.content)
        assertEquals("attempt-2", EvidenceJson.parseToJsonElement(Files.readString(replacement.resolve("route.json"))).jsonObject["attemptId"]!!.jsonPrimitive.content)
        assertEquals(COMMIT, EvidenceJson.parseToJsonElement(Files.readString(replacement.resolve("environment.json"))).jsonObject["sourceCommit"]!!.jsonPrimitive.content)
        assertEquals("pass", EvidenceJson.parseToJsonElement(Files.readString(replacement.resolve("verdict.json"))).jsonObject["verdictKind"]!!.jsonPrimitive.content)
    }

    @Test fun `failed install and restore retain recoverable backup`() {
        var attempts = 0
        val alwaysFail: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
            attempts++
            if (attempts == 1) Files.move(source, destination)
            else if (atomic) throw java.nio.file.AtomicMoveNotSupportedException("source", "destination", "injected")
            else throw java.io.IOException("injected move failure")
        }
        val root = Files.createTempDirectory("gpu-evidence")
        val baselineWriter = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        val old = baselineWriter.writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        val oldBytes = Files.readAllBytes(old.resolve("manifest.json"))
        val failing = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, alwaysFail)
        kotlin.test.assertFailsWith<Throwable> { failing.writeGenerated(renderDescriptor(), rendered(), byteArrayOf(9, 8, 7, 6), "attempt-2") }
        assertTrue(attempts >= 2)
        val backups = Files.list(old.parent).use { stream -> stream.filter { it.fileName.toString().contains(".backup-") }.toList() }
        assertTrue(backups.any { Files.isRegularFile(it.resolve("manifest.json")) && Files.readAllBytes(it.resolve("manifest.json")).contentEquals(oldBytes) })
    }

    private fun bundle(descriptor: EvidenceSceneDescriptor, observation: SceneObservation): Path = EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(descriptor, observation, PIXEL, "attempt")
    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun rendered() = SceneObservation.Rendered(PIXEL, RouteEvidence("route", "attempt", "complete", "rendered", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
    private fun environment() = EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "17", null, null, null, true)
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun refreshHash(path: Path, name: String) { val manifest = path.resolve("manifest.json"); val hash = sha256(Files.readAllBytes(path.resolve(name))); val text = Files.readString(manifest); val key = "\"$name\":\""; val start = text.indexOf(key) + key.length; val end = text.indexOf('"', start); Files.writeString(manifest, text.substring(0, start) + hash + text.substring(end)) }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    companion object { private const val COMMIT = "abc123"; private val PIXEL = byteArrayOf(1, 2, 3, 4); private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC) }
}
