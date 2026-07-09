package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val effectCompositionDashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = root.resolve(effectCompositionDashboardJson)
    require(dashboardPath.exists()) { "Missing dashboard JSON: $dashboardPath" }

    val dashboard = GmDashboardJsonReader.read(dashboardPath)
    val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(dashboard)
    Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")}")
}
