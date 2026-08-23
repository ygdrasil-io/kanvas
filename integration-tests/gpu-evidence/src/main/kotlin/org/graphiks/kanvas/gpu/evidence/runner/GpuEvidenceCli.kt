package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.system.exitProcess
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceBundleWriter
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession

fun main(args: Array<String>): Unit = exitProcess(GpuEvidenceCliRunner(ProductEvidenceRuntimePort()).run(args))

interface EvidenceRuntimePort { fun open(): EvidenceBackendPort?; fun close(); fun dispose() }

class GpuEvidenceCliRunner(
    private val runtime: EvidenceRuntimePort,
    private val requestParser: (Array<String>) -> GpuEvidenceCliRequest = GpuEvidenceCliRequest::parse,
) {
    fun run(args: Array<String>): Int = runResult(args).exitCode

    /** Visible to injected contract tests so a primary failure and its cleanup failures remain inspectable. */
    internal fun runResult(args: Array<String>): EvidenceCliRunResult {
        val request = try {
            requestParser(args)
        } catch (failure: Exception) {
            System.err.println("gpu evidence arguments rejected: ${failure.message}")
            return EvidenceCliRunResult(2, null)
        }
        val failures = mutableListOf<Throwable>()
        var primaryFailure: Throwable? = null
        var exitCode = 1
        try {
            val backend = runtime.open()
            if (backend == null) {
                System.err.println("gpu evidence unavailable: unavailable.gpu.backend: GPU backend runtime could not create a session.")
            } else {
                val executor = GPUPreparedEvidenceExecutor(backend, request.sourceCommit)
                val writer = EvidenceBundleWriter(request.repositoryRoot, request.sourceCommit)
                val selected = request.sceneId?.let { id -> GpuEvidenceCatalog.cases.filter { it.descriptor.id.value == id } } ?: GpuEvidenceCatalog.cases
                exitCode = selected.fold(0) { code, evidenceCase ->
                    when (val result = executor.execute(evidenceCase)) {
                        is EvidenceExecutionResult.ExecutionFailure -> { System.err.println("gpu evidence ${evidenceCase.descriptor.id.value} execution failed: ${result.stableReasonCode}: ${result.message}"); 1 }
                        is EvidenceExecutionResult.Observed -> when (val observation = result.observation) {
                            is SceneObservation.Unavailable -> { System.err.println("gpu evidence unavailable: ${observation.stableReasonCode}: ${observation.message}"); 1 }
                            else -> {
                                when (val verdict = EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation)) {
                                    is EvidenceVerdict.Pass -> {
                                        val expected = (observation as? SceneObservation.Rendered)?.let { requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height) }
                                        writer.writeGenerated(evidenceCase, observation, expected)
                                        code
                                    }
                                    is EvidenceVerdict.Fail -> {
                                        System.err.println("gpu evidence ${evidenceCase.descriptor.id.value} failed: ${verdict.reason}")
                                        1
                                    }
                                    is EvidenceVerdict.Unavailable -> {
                                        System.err.println("gpu evidence unavailable: ${verdict.reason}")
                                        1
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            primaryFailure = failure
            recordDistinctFailure(failures, failure)
            System.err.println("gpu evidence failed: ${failure.message}")
            exitCode = 1
        } catch (failure: Error) {
            primaryFailure = failure
            recordDistinctFailure(failures, failure)
        } finally {
            try {
                runtime.close()
            } catch (failure: Exception) {
                recordDistinctFailure(failures, failure)
            } catch (failure: Error) {
                recordDistinctFailure(failures, failure)
            }
            try {
                runtime.dispose()
            } catch (failure: Exception) {
                recordDistinctFailure(failures, failure)
            } catch (failure: Error) {
                recordDistinctFailure(failures, failure)
            }
        }
        val rootFailure = failures.firstOrNull { it is Error } ?: failures.firstOrNull()
        if (rootFailure == null) return EvidenceCliRunResult(exitCode, null)
        attachDistinctFailures(rootFailure, failures)
        if (rootFailure is Error) throw rootFailure
        if (primaryFailure == null) System.err.println("gpu evidence cleanup failed: ${rootFailure.message}")
        return EvidenceCliRunResult(1, rootFailure)
    }
}

private fun recordDistinctFailure(failures: MutableList<Throwable>, failure: Throwable) {
    if (failures.none { it === failure }) failures += failure
}

private fun attachDistinctFailures(root: Throwable, failures: List<Throwable>) {
    failures.forEach { failure ->
        when {
            failure === root -> Unit
            reachesFailureGraph(root, failure) -> Unit
            reachesFailureGraph(failure, root) -> root.addSuppressed(snapshotToAvoidFailureGraphCycle(failure))
            else -> root.addSuppressed(failure)
        }
    }
}

private fun reachesFailureGraph(source: Throwable, target: Throwable): Boolean {
    val pending = ArrayDeque<Throwable>()
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    pending.addLast(source)
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        if (!visited.add(current)) continue
        if (current === target) return true
        current.cause?.let(pending::addLast)
        current.suppressed.forEach(pending::addLast)
    }
    return false
}

private fun snapshotToAvoidFailureGraphCycle(failure: Throwable): Exception =
    Exception("failure snapshotted to avoid a cycle: ${failure.javaClass.name}: ${failure.message}")

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
            val scene = values["--scene"]; require(scene == null || GpuEvidenceCatalog.cases.any { it.descriptor.id.value == scene })
            return GpuEvidenceCliRequest(root.toAbsolutePath().normalize(), commit, scene)
        }
    }
}
