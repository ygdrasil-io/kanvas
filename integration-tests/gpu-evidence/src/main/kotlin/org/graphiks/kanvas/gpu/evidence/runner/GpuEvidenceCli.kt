package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceBundleWriter
import org.graphiks.kanvas.gpu.evidence.catalog.BootstrapEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory

/** Strict command-line entry point for generated bootstrap correctness evidence. */
fun main(args: Array<String>) {
    val request = runCatching { GpuEvidenceCliRequest.parse(args) }.getOrElse { failure ->
        System.err.println("gpu evidence arguments rejected: ${failure.message}")
        exitProcess(2)
    }
    val backend = GPUBackendRuntimeFactory.createOrNull()
    if (backend == null) {
        System.err.println("gpu evidence unavailable: unavailable.gpu.backend: GPU backend runtime could not create a session.")
        exitProcess(1)
    }
    try {
        val executor = GPUPreparedEvidenceExecutor(ProductEvidenceBackendPort(backend), request.sourceCommit)
        val selected = request.sceneId?.let { id -> BootstrapEvidenceCatalog.cases.filter { it.descriptor.id.value == id } }
            ?: BootstrapEvidenceCatalog.cases
        val writer = EvidenceBundleWriter(request.repositoryRoot, request.sourceCommit)
        var exitCode = 0
        selected.forEach { evidenceCase ->
            when (val observation = executor.execute(evidenceCase)) {
                is SceneObservation.Unavailable -> {
                    System.err.println("gpu evidence unavailable: ${observation.stableReasonCode}: ${observation.message}")
                    exitCode = 1
                }
                else -> {
                    val expected = if (observation is SceneObservation.Rendered) requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height) else null
                    writer.writeGenerated(evidenceCase.descriptor, observation, expected)
                    val verdict = EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation)
                    if (verdict !is EvidenceVerdict.Pass) {
                        System.err.println("gpu evidence ${evidenceCase.descriptor.id.value}: ${(verdict as Any)}")
                        exitCode = 1
                    }
                }
            }
        }
        if (exitCode != 0) exitProcess(exitCode)
    } finally {
        try { backend.close() } finally { GPUBackendRuntimeFactory.dispose() }
    }
}

data class GpuEvidenceCliRequest(val repositoryRoot: Path, val sourceCommit: String, val sceneId: String?) {
    companion object {
        private val SHA = Regex("[0-9a-f]{40}")

        fun parse(args: Array<String>): GpuEvidenceCliRequest {
            require(args.isNotEmpty()) { "--repository-root and --source-commit are required" }
            val values = mutableMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val flag = args[index]
                require(flag in setOf("--repository-root", "--source-commit", "--scene")) { "unknown argument: $flag" }
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $flag" }
                require(values.put(flag, args[index + 1]) == null) { "duplicate argument: $flag" }
                index += 2
            }
            val root = Path.of(requireNotNull(values["--repository-root"]) { "missing --repository-root" })
            require(root.isAbsolute) { "repository root must be absolute" }
            require(Files.isDirectory(root)) { "repository root must be an existing directory" }
            val commit = requireNotNull(values["--source-commit"]) { "missing --source-commit" }
            require(SHA.matches(commit) && commit.any { it != '0' }) { "source commit must be a non-placeholder lowercase 40-hex value" }
            val scene = values["--scene"]
            require(scene == null || BootstrapEvidenceCatalog.cases.any { it.descriptor.id.value == scene }) { "unknown scene: $scene" }
            return GpuEvidenceCliRequest(root.toAbsolutePath().normalize(), commit, scene)
        }
    }
}
