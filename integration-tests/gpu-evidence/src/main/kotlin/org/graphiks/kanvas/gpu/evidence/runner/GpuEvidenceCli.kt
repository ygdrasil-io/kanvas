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
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession

fun main(args: Array<String>): Unit = exitProcess(GpuEvidenceCliRunner(ProductEvidenceRuntimePort()).run(args))

interface EvidenceRuntimePort { fun open(): EvidenceBackendPort?; fun close(); fun dispose() }

class GpuEvidenceCliRunner(private val runtime: EvidenceRuntimePort) {
    fun run(args: Array<String>): Int = runResult(args).exitCode

    /** Visible to injected contract tests so a primary failure and its cleanup failures remain inspectable. */
    internal fun runResult(args: Array<String>): EvidenceCliRunResult {
        val request = runCatching { GpuEvidenceCliRequest.parse(args) }.getOrElse {
            System.err.println("gpu evidence arguments rejected: ${it.message}")
            return EvidenceCliRunResult(2, null)
        }
        var primary: Throwable? = null
        var exitCode = 1
        try {
            val backend = runtime.open()
            if (backend == null) {
                System.err.println("gpu evidence unavailable: unavailable.gpu.backend: GPU backend runtime could not create a session.")
            } else {
                val executor = GPUPreparedEvidenceExecutor(backend, request.sourceCommit)
                val writer = EvidenceBundleWriter(request.repositoryRoot, request.sourceCommit)
                val selected = request.sceneId?.let { id -> BootstrapEvidenceCatalog.cases.filter { it.descriptor.id.value == id } } ?: BootstrapEvidenceCatalog.cases
                exitCode = selected.fold(0) { code, evidenceCase ->
                    when (val result = executor.execute(evidenceCase)) {
                        is EvidenceExecutionResult.ExecutionFailure -> { System.err.println("gpu evidence ${evidenceCase.descriptor.id.value} execution failed: ${result.stableReasonCode}: ${result.message}"); 1 }
                        is EvidenceExecutionResult.Observed -> when (val observation = result.observation) {
                            is SceneObservation.Unavailable -> { System.err.println("gpu evidence unavailable: ${observation.stableReasonCode}: ${observation.message}"); 1 }
                            else -> {
                                val expected = (observation as? SceneObservation.Rendered)?.let { requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height) }
                                writer.writeGenerated(evidenceCase.descriptor, observation, expected)
                                if (EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation) is EvidenceVerdict.Pass) code else 1
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; System.err.println("gpu evidence failed: ${failure.message}"); exitCode = 1 }
        finally {
            var cleanupFailure: Throwable? = null
            try { runtime.close() } catch (failure: Throwable) { cleanupFailure = failure }
            try { runtime.dispose() } catch (failure: Throwable) { if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure.addSuppressed(failure) }
            if (cleanupFailure != null) {
                if (primary == null) primary = cleanupFailure else primary.addSuppressed(cleanupFailure)
                System.err.println("gpu evidence cleanup failed: ${cleanupFailure.message}")
                exitCode = 1
            }
        }
        return EvidenceCliRunResult(exitCode, primary)
    }
}

internal data class EvidenceCliRunResult(val exitCode: Int, val failure: Throwable?)

private class ProductEvidenceRuntimePort : EvidenceRuntimePort {
    private var session: GPUBackendSession? = null
    override fun open(): EvidenceBackendPort? = GPUBackendRuntimeFactory.createOrNull()?.also { session = it }?.let(::ProductEvidenceBackendPort)
    override fun close() { session?.close(); session = null }
    override fun dispose() = GPUBackendRuntimeFactory.dispose()
}

data class GpuEvidenceCliRequest(val repositoryRoot: Path, val sourceCommit: String, val sceneId: String?) {
    companion object {
        private val SHA = Regex("[0-9a-f]{40}")
        fun parse(args: Array<String>): GpuEvidenceCliRequest {
            val values = mutableMapOf<String, String>(); var index = 0
            while (index < args.size) { val flag = args[index]; require(flag in setOf("--repository-root", "--source-commit", "--scene")); require(index + 1 < args.size && !args[index + 1].startsWith("--")); require(values.put(flag, args[index + 1]) == null); index += 2 }
            val root = Path.of(requireNotNull(values["--repository-root"])); require(root.isAbsolute && Files.isDirectory(root))
            val commit = requireNotNull(values["--source-commit"]); require(SHA.matches(commit) && commit.any { it != '0' })
            val scene = values["--scene"]; require(scene == null || BootstrapEvidenceCatalog.cases.any { it.descriptor.id.value == scene })
            return GpuEvidenceCliRequest(root.toAbsolutePath().normalize(), commit, scene)
        }
    }
}
