package org.graphiks.kanvas.gpu.renderer.scenes.reports

import kotlin.io.path.Path
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneHumanDocs
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

fun main(args: Array<String>) {
    require(args.size == 1) {
        "Usage: GPURendererScenesCatalogReportMain <output-dir>"
    }
    val outputDir = Path(args[0])
    SceneCatalogReport(
        scenes = GPURendererSceneRegistry.scenes,
        humanDocs = GPURendererSceneHumanDocs.docs,
        candidateScenes = GPURendererSceneHumanDocs.candidateScenes,
    ).writeTo(outputDir)
    println("Wrote GPU renderer scenes catalog report to $outputDir")
}
