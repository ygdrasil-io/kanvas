package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val dashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = Path.of(args.firstOrNull() ?: ".").toAbsolutePath().normalize()
    val dashboardPath = root.resolve(dashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = ScoreFirstWave3Classifier.buildEvidence(dashboard)
    val output = ScoreFirstWave3ReportWriter.write(root, evidence)

    println("Wrote ${output.markdownPath}")
    println("Wrote ${output.tsvPath}")
}
