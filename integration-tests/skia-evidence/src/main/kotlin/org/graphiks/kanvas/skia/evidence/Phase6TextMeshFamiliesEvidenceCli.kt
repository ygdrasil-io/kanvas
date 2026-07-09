package org.graphiks.kanvas.skia.evidence

import java.nio.file.Path
import kotlin.io.path.exists

private val textMeshDashboardJson = Path.of("integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json")

fun main(args: Array<String>) {
    val root = if (args.isEmpty()) Path.of(".") else Path.of(args[0])
    val dashboardPath = args.getOrNull(1)?.let(Path::of)
    runPhase6TextMeshFamiliesEvidence(root, dashboardPath)
}

internal fun runPhase6TextMeshFamiliesEvidence(root: Path, dashboardPath: Path? = null) {
    val resolvedDashboardPath = dashboardPath ?: root.resolve(textMeshDashboardJson)
    require(resolvedDashboardPath.exists()) { "Missing dashboard JSON: $resolvedDashboardPath" }

    val dashboard = GmDashboardJsonReader.read(resolvedDashboardPath)
    val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(dashboard)
    Phase6TextMeshFamilyEvidenceWriter.writeOutputs(root, evidence)
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/evidence.json")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/classification.csv")}")
    println("Wrote ${root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md")}")
}
