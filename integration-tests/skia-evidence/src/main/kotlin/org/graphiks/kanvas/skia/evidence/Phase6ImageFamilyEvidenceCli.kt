package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val dashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(dashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6ImageFamilyClassifier.buildEvidence(dashboard)
    Phase6ImageFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")}")
}
