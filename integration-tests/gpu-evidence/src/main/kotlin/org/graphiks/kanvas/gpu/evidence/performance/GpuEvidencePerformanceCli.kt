package org.graphiks.kanvas.gpu.evidence.performance

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceBackendPort
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceRuntimePort
import org.graphiks.kanvas.gpu.evidence.runner.ProductEvidenceBackendPort
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession

fun main(args: Array<String>): Unit = exitProcess(GpuEvidencePerformanceCli(ProductPerformanceRuntime()).run(args))

class GpuEvidencePerformanceCli(private val runtime: PerformanceRuntime, private val parser: (Array<String>) -> PerformanceRequest = PerformanceRequest::parse) {
    fun run(args: Array<String>): Int {
        val request = runCatching { parser(args) }.getOrElse { System.err.println("gpu evidence performance arguments rejected: ${it.message}"); return 2 }
        val backend = runtime.open() ?: run { System.err.println("gpu evidence performance unavailable: GPU backend could not create a session"); runtime.close(); runtime.dispose(); return 1 }
        return try {
            val writer = PerformanceBundleWriter(request.repositoryRoot, request.sourceCommit)
            val selected = GpuEvidenceCatalog.cases.filter { it.descriptor.expectation is org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation.ShouldRender }.let { cases -> request.sceneId?.let { id -> cases.filter { it.descriptor.id.value == id } } ?: cases }
            var code = 0
            selected.forEach { scene ->
                val result = GpuEvidencePerformanceRunner(backend, request.sourceCommit, request.config).run(scene)
                writer.writeGenerated(result)
                println("${scene.descriptor.id.value}: ${result.verdict::class.simpleName} samples=${result.timingSamplesNanos.size} submissions=${result.telemetry.delta["submissions"]?.value ?: "unavailable"}")
                if (result.verdict is PerformanceVerdict.Failed || result.verdict is PerformanceVerdict.Unavailable) code = 1
            }
            code
        } catch (failure: Exception) {
            System.err.println("gpu evidence performance failed: ${failure.message}"); 1
        } finally { runtime.close(); runtime.dispose() }
    }
}

interface PerformanceRuntime { fun open(): EvidenceBackendPort?; fun close(); fun dispose() }
private class ProductPerformanceRuntime : PerformanceRuntime {
    private var session: GPUBackendSession? = null
    override fun open(): EvidenceBackendPort? = GPUBackendRuntimeFactory.createOrNull()?.also { session = it }?.let(::ProductEvidenceBackendPort)
    override fun close() { session?.close(); session = null }
    override fun dispose() = GPUBackendRuntimeFactory.dispose()
}

data class PerformanceRequest(val repositoryRoot: Path, val sourceCommit: String, val config: PerformanceConfig, val sceneId: String?) {
    companion object {
        fun parse(args: Array<String>): PerformanceRequest {
            val values = mutableMapOf<String, String>(); var index = 0
            while (index < args.size) { val key = args[index]; require(key in setOf("--repository-root", "--source-commit", "--warmup-frames", "--measured-frames", "--scene")); require(index + 1 < args.size); require(values.put(key, args[index + 1]) == null); index += 2 }
            val root = Path.of(requireNotNull(values["--repository-root"])); require(root.isAbsolute && Files.isDirectory(root))
            val commit = requireNotNull(values["--source-commit"]); require(commit.matches(Regex("[0-9a-f]{40}")))
            val config = PerformanceConfig(values["--warmup-frames"]?.toInt() ?: 10, values["--measured-frames"]?.toInt() ?: 90)
            val scene = values["--scene"]; require(scene == null || GpuEvidenceCatalog.cases.any { it.descriptor.id.value == scene })
            return PerformanceRequest(root.toAbsolutePath().normalize(), commit, config, scene)
        }
    }
}
