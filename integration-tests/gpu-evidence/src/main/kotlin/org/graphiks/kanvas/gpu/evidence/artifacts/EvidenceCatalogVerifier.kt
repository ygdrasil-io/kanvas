package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation

data class EvidenceCatalogVerification(
    val sceneIds: List<String>,
    val sourceCommits: Map<String, String>,
    val environment: EvidenceEnvironmentV2,
)

data class EvidenceCatalogSceneFailure(
    val sceneId: String,
    val errors: List<String>,
)

class EvidenceCatalogVerificationException(
    message: String,
    val sceneFailures: List<EvidenceCatalogSceneFailure>,
) : IllegalArgumentException(message)

object EvidenceCatalogVerifier {
    private val SOURCE_COMMIT = Regex("[0-9a-f]{40}")
    private val SHA256 = Regex("[0-9a-f]{64}")

    fun verify(
        root: Path,
        selection: EvidenceSelection,
        cases: List<EvidenceCase>,
        expectedSourceCommit: String?,
        requirePromotion: Boolean = false,
    ): EvidenceCatalogVerification {
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "evidence root must be an existing directory" }
        require(!Files.isSymbolicLink(root)) { "evidence root cannot be a symlink" }
        val expectedById = cases.associateBy { it.descriptor.id.value }
        require(expectedById.size == cases.size) { "catalog contains duplicate scene ids" }
        val selectedCases = selection.resolve(cases)
        val selectedIds = selectedCases.map { it.descriptor.id.value }.sorted()
        val catalog = readObject(root.resolve("catalog.json"), "catalog")
        catalog.requireKeys(setOf("schemaVersion", "environment", "promotion", "scenes"))
        require(catalog.requiredString("schemaVersion") == GPU_EVIDENCE_CATALOG_SCHEMA_V2) {
            "unsupported catalog schemaVersion"
        }
        require(catalog.requiredString("environment") == "environment.json") {
            "catalog environment must be environment.json"
        }
        val environment = readEnvironment(root.resolve("environment.json"))
        val promotionPath = catalog.optionalNullableString("promotion")
        if (promotionPath == null) {
            require(!requirePromotion) { "promoted v2 evidence root must contain promotion.json" }
            require(!Files.exists(root.resolve("promotion.json"), NOFOLLOW_LINKS)) {
                "generated v2 evidence root must not contain promotion.json"
            }
        } else {
            require(promotionPath == "promotion.json") { "catalog promotion must be promotion.json or null" }
            verifyPromotion(root.resolve(promotionPath), selectedIds.toSet())
        }
        val entries = readCatalogEntries(catalog["scenes"]?.jsonArray ?: error("scenes must be an array"))
        val catalogSceneIds = entries.map { it.sceneId }
        when (selection) {
            EvidenceSelection.All -> require(catalogSceneIds == selectedIds) {
                "scene directory set mismatch: expected=${selectedIds.toSet()} actual=${catalogSceneIds.toSet()}"
            }
            is EvidenceSelection.Explicit -> require(catalogSceneIds == selection.sceneIds) {
                "scene directory set mismatch: expected=${selection.sceneIds.toSet()} actual=${catalogSceneIds.toSet()}"
            }
        }
        val rootEntries = Files.list(root).use { stream -> stream.iterator().asSequence().toList() }
        require(rootEntries.none { Files.isSymbolicLink(it) }) { "evidence root contains a symlink" }
        val regularFiles = rootEntries.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
        val directories = rootEntries.filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
        require(regularFiles.size + directories.size == rootEntries.size) { "evidence root contains a non-regular entry" }
        val expectedFileNames = buildSet {
            add("catalog.json")
            add("environment.json")
            if (promotionPath != null) add("promotion.json")
            addAll(catalogSceneIds)
        }
        require(rootEntries.map { it.fileName.toString() }.toSet() == expectedFileNames) {
            "evidence root entries mismatch: expected=$expectedFileNames actual=${rootEntries.map { it.fileName.toString() }.toSet()}"
        }
        require(directories.map { it.fileName.toString() }.sorted() == catalogSceneIds) {
            "scene directory set mismatch: expected=${catalogSceneIds.toSet()} actual=${directories.map { it.fileName.toString() }.toSet()}"
        }

        val sourceCommits = linkedMapOf<String, String>()
        val sceneFailures = mutableListOf<EvidenceCatalogSceneFailure>()
        entries.forEach { entry ->
            val evidenceCase = requireNotNull(expectedById[entry.sceneId]) { "unknown evidence scene: ${entry.sceneId}" }
            expectedSourceCommit?.let { commit ->
                if (entry.sourceCommit != commit) {
                    sceneFailures += EvidenceCatalogSceneFailure(entry.sceneId, listOf("catalog sourceCommit mismatch"))
                    return@forEach
                }
            }
            val manifestPath = try {
                resolveManifestPath(root, entry)
            } catch (failure: IllegalArgumentException) {
                sceneFailures += EvidenceCatalogSceneFailure(entry.sceneId, listOf(failure.message ?: "invalid manifest path"))
                return@forEach
            }
            if (entry.manifestSha256 != sha256(Files.readAllBytes(manifestPath))) {
                sceneFailures += EvidenceCatalogSceneFailure(entry.sceneId, listOf("manifest sha256 mismatch"))
                return@forEach
            }
            val expected = EvidenceVerificationExpectation.fromCase(
                evidenceCase = evidenceCase,
                sourceCommit = entry.sourceCommit,
                expectedRgba = evidenceCase.oracle?.render(evidenceCase.descriptor.width, evidenceCase.descriptor.height),
            )
            when (val result = EvidenceBundleVerifier.verifyV2(root.resolve(entry.sceneId), expected, environment, entry.sourceCommit)) {
                is EvidenceBundleVerification.Invalid -> {
                    sceneFailures += EvidenceCatalogSceneFailure(entry.sceneId, result.errors)
                }
                is EvidenceBundleVerification.Verified -> {
                    sourceCommits[entry.sceneId] = entry.sourceCommit
                    if (evidenceCase.descriptor.expectation is EvidenceExpectation.ShouldRender) {
                        require(result.environment.available) {
                            ENVIRONMENT_MISMATCH_REQUIRES_REBASELINE
                        }
                    }
                }
            }
        }
        if (sceneFailures.isNotEmpty()) {
            throw EvidenceCatalogVerificationException(
                message = "evidence catalogue contains invalid scenes",
                sceneFailures = sceneFailures.sortedBy(EvidenceCatalogSceneFailure::sceneId),
            )
        }
        return EvidenceCatalogVerification(catalogSceneIds, sourceCommits, environment)
    }

    internal const val ENVIRONMENT_MISMATCH_REQUIRES_REBASELINE =
        "gpu.evidence.environment-mismatch.requires-rebaseline"

    private fun resolveManifestPath(root: Path, entry: CatalogEntry): Path {
        require(isSafeManifestPath(entry.sceneId, entry.manifest)) { "unsafe manifest path for ${entry.sceneId}" }
        val manifestPath = root.resolve(entry.manifest).normalize()
        require(manifestPath.startsWith(root)) { "manifest path escapes evidence root: ${entry.manifest}" }
        require(manifestPath.parent == root.resolve(entry.sceneId)) {
            "manifest path does not match scene directory for ${entry.sceneId}"
        }
        require(Files.isRegularFile(manifestPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(manifestPath)) {
            "manifest must be a regular non-symlink file: ${entry.manifest}"
        }
        return manifestPath
    }

    private fun readCatalogEntries(scenes: JsonArray): List<CatalogEntry> {
        val entries = scenes.map { entry ->
            val scene = entry.jsonObject
            scene.requireKeys(setOf("sceneId", "sourceCommit", "manifest", "manifestSha256"))
            CatalogEntry(
                sceneId = scene.requiredString("sceneId"),
                sourceCommit = scene.requiredString("sourceCommit").also {
                    require(SOURCE_COMMIT.matches(it)) { "catalog sourceCommit must be 40 lowercase hexadecimal characters" }
                },
                manifest = scene.requiredString("manifest"),
                manifestSha256 = scene.requiredString("manifestSha256").also {
                    require(SHA256.matches(it)) { "catalog manifestSha256 must be 64 lowercase hexadecimal characters" }
                },
            )
        }.sortedBy(CatalogEntry::sceneId)
        val duplicates = entries.groupingBy(CatalogEntry::sceneId).eachCount().filterValues { it > 1 }.keys.sorted()
        require(duplicates.isEmpty()) { "duplicate catalog scene ids: ${duplicates.joinToString(",")}" }
        return entries
    }

    private fun readEnvironment(path: Path): EvidenceEnvironmentV2 {
        val environment = readObject(path, "environment")
        environment.requireKeys(
            setOf(
                "schemaVersion",
                "osName",
                "osVersion",
                "osArchitecture",
                "javaVersion",
                "deviceGeneration",
                "capabilityImplementation",
                "available",
                "adapter",
            ),
        )
        require(environment.requiredString("schemaVersion") == GPU_EVIDENCE_CATALOG_SCHEMA_V2) {
            "unsupported environment schemaVersion"
        }
        val adapter = environment["adapter"]?.let {
            if (it is JsonNull) return@let null
            val adapterObject = it.jsonObject
            adapterObject.requireKeys(setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter"))
            org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter(
                summary = adapterObject.optionalNullableString("summary"),
                vendor = adapterObject.optionalNullableString("vendor"),
                device = adapterObject.optionalNullableString("device"),
                architecture = adapterObject.optionalNullableString("architecture"),
                description = adapterObject.optionalNullableString("description"),
                isFallbackAdapter = adapterObject.optionalNullableBoolean("isFallbackAdapter"),
            )
        }
        return EvidenceEnvironmentV2(
            schemaVersion = environment.requiredString("schemaVersion"),
            osName = environment.requiredString("osName"),
            osVersion = environment.requiredString("osVersion"),
            osArchitecture = environment.requiredString("osArchitecture"),
            javaVersion = environment.requiredString("javaVersion"),
            deviceGeneration = environment.optionalNullableLong("deviceGeneration"),
            capabilityImplementation = environment.optionalNullableString("capabilityImplementation"),
            available = environment.requiredBoolean("available"),
            adapter = adapter,
        )
    }

    private fun verifyPromotion(path: Path, catalogSceneIds: Set<String>) {
        val promotion = readObject(path, "promotion")
        promotion.requireKeys(setOf("schemaVersion", "promotedAtUtc", "reviewer", "reason", "rebaseline", "sceneIds", "priorComparison", "newComparison"))
        require(promotion.requiredString("schemaVersion") == GPU_EVIDENCE_PROMOTION_SCHEMA_V2) {
            "unsupported promotion schemaVersion"
        }
        promotion.requiredString("promotedAtUtc")
        require(promotion.requiredString("reviewer").isNotBlank()) { "promotion reviewer must not be blank" }
        require(promotion.requiredString("reason").isNotBlank()) { "promotion reason must not be blank" }
        val rebaseline = promotion.requiredBoolean("rebaseline")
        val sceneIds = promotion["sceneIds"]?.jsonArray?.map { it.jsonPrimitive.asString("promotion sceneId") } ?: error("sceneIds must be an array")
        require(sceneIds.isNotEmpty()) { "promotion sceneIds must not be empty" }
        require(sceneIds.toSet().size == sceneIds.size) { "promotion sceneIds must be unique" }
        require(sceneIds.all { it in catalogSceneIds }) { "promotion sceneIds must be catalog scene ids" }
        val prior = promotion["priorComparison"]
        val next = promotion["newComparison"]
        require((prior is JsonNull) == (next is JsonNull)) { "promotion comparison summaries must be paired" }
        if (rebaseline) {
            require(prior is JsonPrimitive && prior.isString && prior.content.isNotBlank()) {
                "rebaseline requires a nonblank prior comparison summary"
            }
            require(next is JsonPrimitive && next.isString && next.content.isNotBlank()) {
                "rebaseline requires a nonblank new comparison summary"
            }
        } else {
            require(prior is JsonNull && next is JsonNull) {
                "promotion comparison summaries require rebaseline=true"
            }
        }
    }

    private fun readObject(path: Path, label: String): JsonObject {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "missing $label" }
        require(!Files.isSymbolicLink(path)) { "$label cannot be a symlink" }
        val text = Files.readString(path)
        rejectDuplicateKeys(text)
        return EvidenceJson.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.requiredString(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: error("$key must be a string")
        require(primitive.isString) { "$key must be a string" }
        return primitive.contentOrNull ?: error("$key must not be null")
    }

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("$key must be a boolean")

    private fun JsonObject.requireKeys(expected: Set<String>) {
        require(keys == expected) { "unexpected or missing keys: expected=$expected actual=$keys" }
    }

    private fun JsonObject.optionalNullableString(key: String): String? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.asString(key)
    }

    private fun JsonObject.optionalNullableBoolean(key: String): Boolean? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.takeUnless { it.isString }?.booleanOrNull ?: error("$key must be a boolean or null")
    }

    private fun JsonObject.optionalNullableLong(key: String): Long? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.takeUnless { it.isString }?.longOrNull ?: error("$key must be a long or null")
    }

    private fun JsonPrimitive.asString(label: String): String =
        require(isString && contentOrNull != null) { "$label must be a string" }.let { content }

    private fun isSafeManifestPath(sceneId: String, manifest: String): Boolean =
        manifest == "$sceneId/manifest.json" &&
            !manifest.startsWith("/") &&
            !manifest.contains("\\") &&
            manifest.split('/').none { it == ".." || it.isBlank() }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private fun rejectDuplicateKeys(text: String) {
        val objects = ArrayDeque<MutableSet<String>>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    val start = index++
                    while (index < text.length) {
                        if (text[index] == '\\') index += 2
                        else if (text[index++] == '"') break
                    }
                    var next = index
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':' && objects.isNotEmpty()) {
                        val rawKey = text.substring(start, index)
                        val key = runCatching { EvidenceJson.parseToJsonElement(rawKey).jsonPrimitive.content }.getOrElse { rawKey }
                        require(objects.last().add(key)) { "duplicate JSON key: $key" }
                    }
                }
                '{' -> objects.addLast(mutableSetOf())
                '}' -> if (objects.isNotEmpty()) objects.removeLast()
            }
            index++
        }
    }

    private data class CatalogEntry(
        val sceneId: String,
        val sourceCommit: String,
        val manifest: String,
        val manifestSha256: String,
    )
}
