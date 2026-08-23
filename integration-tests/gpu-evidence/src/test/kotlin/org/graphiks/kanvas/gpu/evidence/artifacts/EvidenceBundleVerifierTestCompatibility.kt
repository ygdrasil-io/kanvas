package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.test.ComparisonUtils
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double

/** Compatibility adapter for legacy fixture assertions; production has only the strict API. */
fun verifyFixtureIntegrity(directory: Path, expectedSourceCommit: String): EvidenceBundleVerification {
    val manifest = EvidenceJson.parseToJsonElement(Files.readString(directory.resolve("manifest.json"))).jsonObject
    val sceneId = manifest["sceneId"]!!.jsonPrimitive.content
    val stats = EvidenceJson.parseToJsonElement(Files.readString(directory.resolve("stats.json"))).jsonObject
    val width = stats["width"]!!.jsonPrimitive.int
    val height = stats["height"]!!.jsonPrimitive.int
    val expectation = manifest["expectation"]!!.jsonPrimitive.content
    val oracleKind = manifest["oracleKind"]!!.jsonPrimitive.content
    val oracleId = manifest["oracleId"]!!.jsonPrimitive.content
    val oracleVersion = manifest["oracleVersion"]!!.jsonPrimitive.int
    val oracle = when {
        oracleKind == "generated-cpu" -> OraclePolicy.GeneratedCpu(oracleId, oracleVersion)
        oracleKind == "stable-refusal" -> OraclePolicy.StableRefusal
        else -> OraclePolicy.CheckedInPng(oracleId, manifest["oracleSha256"]!!.jsonPrimitive.content, manifest["oracleProvenance"]!!.jsonPrimitive.content)
    }
    val comparison = if (expectation == "render") ComparisonPolicy(stats["tolerance"]!!.jsonPrimitive.int, stats["minimumSimilarityPercent"]!!.jsonPrimitive.double, 1, "fixture") else null
    val descriptor = EvidenceSceneDescriptor(EvidenceSceneId(sceneId), "fixture", "fixture", width, height, 1, emptySet(), if (expectation == "render") EvidenceExpectation.ShouldRender else EvidenceExpectation.ShouldRefuse(expectation.removePrefix("refuse:")), oracle, comparison, emptySet())
    val route = EvidenceJson.parseToJsonElement(Files.readString(directory.resolve("route.json"))).jsonObject["routeId"]!!.jsonPrimitive.content
    val expected = if (expectation == "render" && oracleKind == "generated-cpu") ComparisonUtils.loadPngAsSrgbRgba(directory.resolve("cpu.png").toFile()) else null
    val checkedIn = if (oracleKind == "checked-in-png") Files.readAllBytes(directory.resolve("skia.png")) else null
    return EvidenceBundleVerifier.verify(directory, EvidenceVerificationExpectation.fromDescriptor(descriptor, expectedSourceCommit, expected, checkedIn, route))
}
