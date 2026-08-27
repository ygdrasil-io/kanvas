package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.catalog.rootIdentity

class EvidenceCatalogWriter internal constructor(
    repositoryRoot: Path,
    private val moveStrategy: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
        if (atomic) Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        else Files.move(source, destination, REPLACE_EXISTING)
    },
    private val cleanupStrategy: (Path) -> Unit = { Files.deleteIfExists(it) },
) {
    private val repositoryRoot = repositoryRoot.toAbsolutePath().normalize()
    private val repositoryRootReal: Path

    init {
        Files.createDirectories(this.repositoryRoot)
        require(!Files.isSymbolicLink(this.repositoryRoot)) { "repository root cannot be a symlink" }
        repositoryRootReal = this.repositoryRoot.toRealPath(NOFOLLOW_LINKS)
    }

    fun writeGeneratedCatalog(
        root: Path,
        selection: EvidenceSelection,
        observations: Map<String, SceneObservation>,
        bundlePaths: Map<String, Path>,
    ): Path {
        val generatedRoot = root.toAbsolutePath().normalize()
        require(generatedRoot.startsWith(repositoryRoot)) { "generated root escapes repository root" }
        ensureNoSymlinkComponents(generatedRoot.parent ?: repositoryRoot)
        Files.createDirectories(generatedRoot)
        require(!Files.isSymbolicLink(generatedRoot)) { "generated root cannot be a symlink" }
        require(generatedRoot.toRealPath(NOFOLLOW_LINKS).startsWith(repositoryRootReal)) {
            "generated root escapes repository root"
        }

        val selectedSceneIds = when (selection) {
            EvidenceSelection.All -> observations.keys.sorted()
            is EvidenceSelection.Explicit -> selection.sceneIds
        }
        require(selectedSceneIds.isNotEmpty()) { "generated catalog selection must not be empty" }
        require(observations.keys == selectedSceneIds.toSet()) { "observations do not match the selected scene ids" }
        require(bundlePaths.keys == selectedSceneIds.toSet()) { "bundle paths do not match the selected scene ids" }

        val rootEnvironment = observations.getValue(selectedSceneIds.first()).environment
        val expectedIdentity = rootEnvironment.rootIdentity()
        selectedSceneIds.drop(1).forEach { sceneId ->
            require(observations.getValue(sceneId).environment.rootIdentity() == expectedIdentity) {
                "selected observations do not share one environment identity"
            }
        }

        val entries = selectedSceneIds.map { sceneId ->
            val bundle = bundlePaths.getValue(sceneId).toAbsolutePath().normalize()
            require(bundle.fileName.toString() == sceneId) { "bundle path does not match selected scene id: $sceneId" }
            require(bundle.parent == generatedRoot) { "bundle path escapes generated root: $bundle" }
            require(Files.isDirectory(bundle, NOFOLLOW_LINKS) && !Files.isSymbolicLink(bundle)) {
                "bundle path is not a regular scene directory: $bundle"
            }
            val manifest = bundle.resolve("manifest.json")
            require(Files.isRegularFile(manifest, NOFOLLOW_LINKS) && !Files.isSymbolicLink(manifest)) {
                "bundle manifest is not a regular file: $manifest"
            }
            require(manifest.toRealPath(NOFOLLOW_LINKS).startsWith(repositoryRootReal)) {
                "bundle manifest escapes repository root"
            }
            EvidenceCatalogEntry(
                sceneId = sceneId,
                sourceCommit = observations.getValue(sceneId).environment.sourceCommit,
                manifest = "$sceneId/manifest.json",
                manifestSha256 = sha256Hex(Files.readAllBytes(manifest)),
            )
        }

        writeAtomicFile(generatedRoot.resolve("environment.json"), environmentJsonV2(rootEnvironment))
        writeAtomicFile(
            generatedRoot.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                environment = "environment.json",
                promotion = null,
                scenes = entries,
            ).toJson().canonicalBytes(),
        )
        return generatedRoot
    }

    private fun ensureNoSymlinkComponents(path: Path) {
        require(path.startsWith(repositoryRoot)) { "path escapes repository root" }
        var current = repositoryRoot
        val relative = repositoryRoot.relativize(path)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "path contains a symlink" }
        }
        if (Files.exists(path, NOFOLLOW_LINKS)) require(path.toRealPath(NOFOLLOW_LINKS).startsWith(repositoryRootReal)) {
            "path escapes repository root"
        }
    }

    private fun writeAtomicFile(target: Path, bytes: ByteArray) {
        val parent = target.parent ?: error("target has no parent")
        ensureNoSymlinkComponents(parent)
        if (Files.exists(target, NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(target)) { "target cannot be a symlink: $target" }
            require(Files.isRegularFile(target, NOFOLLOW_LINKS)) { "target must be a regular file: $target" }
        }
        val temp = Files.createTempFile(parent, ".${target.fileName}.tmp-", null)
        var primaryFailure: Throwable? = null
        try {
            Files.write(temp, bytes)
            try {
                moveStrategy(temp, target, true)
            } catch (_: AtomicMoveNotSupportedException) {
                moveStrategy(temp, target, false)
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                cleanupStrategy(temp)
            } catch (cleanupFailure: Throwable) {
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }
}
