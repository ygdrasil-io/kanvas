package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val materialDashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(materialDashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6MaterialFamilyClassifier.buildEvidence(dashboard)
    Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")}")
}
