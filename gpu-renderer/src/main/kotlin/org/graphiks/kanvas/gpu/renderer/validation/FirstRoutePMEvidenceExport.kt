package org.graphiks.kanvas.gpu.renderer.validation

import java.io.File

/**
 * Command-line entry point for exporting the default R6 first-route PM evidence bundle.
 *
 * The entry point writes validation-owned refusal-first artifacts only. It exists so Gradle can
 * materialize the bundle for PM review without initializing WebGPU, opening native windows, or
 * changing product route support.
 */
fun main(args: Array<String>) {
    require(args.size <= 1) {
        "usage: FirstRoutePMEvidenceExportKt [outputDirectory]"
    }

    val outputDirectory = args.firstOrNull()
        ?.let(::File)
        ?: File("build/reports/gpu-renderer-r6-first-route-pm-evidence")
    val result = writeFirstRoutePMEvidenceArtifactBundle(outputDirectory)

    println(
        "gpu-renderer R6 first-route PM evidence bundle written " +
            "root=${outputDirectory.path} artifacts=${result.writes.size} manifest=${result.relativePaths.first()}",
    )
}
