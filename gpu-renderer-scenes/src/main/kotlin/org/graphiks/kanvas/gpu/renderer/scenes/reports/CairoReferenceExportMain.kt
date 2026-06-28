package org.graphiks.kanvas.gpu.renderer.scenes.reports

import java.io.File

fun main(args: Array<String>) {
    require(args.size == 1) {
        "Usage: CairoReferenceExportMain <repo-root>"
    }
    val repoRoot = File(args[0]).absoluteFile
    require(repoRoot.isDirectory) { "Not a directory: $repoRoot" }
    CairoReferenceExporter.exportAllScenes(repoRoot)
}
