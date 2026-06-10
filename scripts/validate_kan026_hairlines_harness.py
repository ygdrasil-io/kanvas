#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-026-hairlines-harness.md"
EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-026-hairlines-harness/kan-026-hairlines-harness.json"
DASH_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"
M60_ROUTE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
M60_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"
FOR266_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"
FOR267_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"
FOR318_REPORT_PATH = "reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"
HAIRLINES_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"
CROSS_HARNESS_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/testing/CrossBackendHarness.kt"
HAIRLINES_GM_PATH = "skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt"
REFERENCE_PATH = "skia-integration-tests/src/test/resources/original-888/hairlines.png"
BUILD_GRADLE_PATH = "build.gradle.kts"

HAIRLINES_SCENE_ID = "skia-gm-hairlines"
HAIRLINES_FALLBACK = "coverage.hairline.row-specific-artifacts-required"
STROKE_FALLBACK = "coverage.stroke-cap-join-visual-parity-below-threshold"
REMAINING_BOUNDARY = "coverage.stroke-cap-join-aa-residual"
EDGE_BUDGET = 256
PATH_VERB_BUDGET = 96
SUPPORT_THRESHOLD = 99.95


def fail(message: str):
    raise SystemExit(f"KAN-026 hairlines harness validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must be a JSON object")
    return data


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> str:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )
    return text


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def dashboard_row(root: Path) -> dict[str, Any]:
    pack = load_json(root, DASH_PACK_PATH)
    rows = pack.get("scenes")
    require(isinstance(rows, list), f"{DASH_PACK_PATH}.scenes must be a list")
    for row in rows:
        if isinstance(row, dict) and row.get("id") == HAIRLINES_SCENE_ID:
            return row
    fail(f"{DASH_PACK_PATH} missing {HAIRLINES_SCENE_ID}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, EVIDENCE_PATH)
    require(evidence.get("schemaVersion") == 1, "schemaVersion changed")
    require(evidence.get("ticket") == "KAN-026", "ticket id changed")
    require(evidence.get("packId") == "kan-026-hairlines-harness-v1", "packId changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    require(evidence.get("closureDecision") == "visible-non-supportable-harness", "closure decision changed")
    require(evidence.get("claimLevel") == "row-specific-hairlines-harness-boundary", "claimLevel changed")
    require(evidence.get("supportClaim") is False, "KAN-026 must not claim HairlinesGM support")
    require(evidence.get("rendererChanged") is False, "renderer must not change")
    require(evidence.get("sharedShadersChanged") is False, "shared shaders must not change")
    require(evidence.get("thresholdsWeakened") is False, "thresholds must not be weakened")
    require(evidence.get("edgeBudgetChanged") is False, "edge budget must not change")
    require(evidence.get("readinessDelta") == 0, "KAN-026 must not move readiness")

    harness = require_object(evidence, "selectedHarness", EVIDENCE_PATH)
    require(harness.get("testClass") == "org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest", "test class changed")
    require(harness.get("gm") == "HairlinesGM", "GM changed")
    require(harness.get("referenceName") == "hairlines", "reference name changed")
    require(harness.get("referenceFile") == REFERENCE_PATH, "reference file path changed")
    require(harness.get("adapterGate") == "WebGpuContext.createOrNull", "adapter gate changed")
    require(harness.get("rasterFloor") == 97.63, "raster floor changed")
    require(harness.get("gpuFloor") == 98.92, "GPU floor changed")
    require(harness.get("rasterTolerance") == 1, "raster tolerance changed")
    require(harness.get("debugImages") == [
        "gpu-raster/build/debug-images/hairlines-raster.png",
        "gpu-raster/build/debug-images/hairlines-gpu.png",
        "gpu-raster/build/debug-images/hairlines-diff.png",
    ], "debug image convention changed")
    require_file(root, harness["referenceFile"])

    observed = require_object(evidence, "observedFailure", EVIDENCE_PATH)
    require(observed.get("command") == "rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest", "observed command changed")
    require(observed.get("expectedOutcome") == "FAIL_STABLE_REFUSAL", "expected outcome changed")
    require(observed.get("exceptionClass") == "java.lang.IllegalStateException", "exception class changed")
    require(observed.get("route") == "webgpu.coverage.refuse", "observed route changed")
    require(observed.get("strategy") == "RefuseDiagnostic", "observed strategy changed")
    require(observed.get("fallbackReason") == STROKE_FALLBACK, "observed fallback changed")
    require(observed.get("failureBeforeDebugImages") is True, "failure must remain before debug images")
    require(observed.get("pathVerbCount") == 75, "observed path verb count changed")
    require(observed.get("pathVerbBudget") == PATH_VERB_BUDGET, "observed path verb budget changed")
    require(observed.get("coverageEdgeCount") == 60, "observed edge count changed")
    require(observed.get("edgeBudget") == EDGE_BUDGET, "observed edge budget changed")
    require(observed.get("strokeWidth") == 1.0, "observed stroke width changed")
    require(observed.get("strokeCaps") == ["butt"], "observed stroke cap changed")
    require(observed.get("strokeJoins") == ["miter"], "observed stroke join changed")
    require(observed.get("deviceBounds") == [260.0, 4.947735, 340.0, 84.61401], "observed device bounds changed")

    row_contract = require_object(evidence, "dashboardPolicyRow", EVIDENCE_PATH)
    require(row_contract.get("sceneId") == HAIRLINES_SCENE_ID, "dashboard scene id changed")
    require(row_contract.get("status") == "expected-unsupported", "dashboard status changed")
    require(row_contract.get("fallbackReason") == HAIRLINES_FALLBACK, "dashboard fallback changed")
    require(row_contract.get("policyOnlyArtifacts") is True, "dashboard row must stay policy-only")
    require("feature.hairline" in row_contract.get("tags", []), "evidence dashboard row missing feature.hairline")

    row = dashboard_row(root)
    require(row.get("status") == "expected-unsupported", "generated dashboard status changed")
    require(row.get("fallbackReason") == HAIRLINES_FALLBACK, "generated dashboard fallback changed")
    require(row.get("policyOnlyArtifacts") is True, "generated dashboard row must stay policy-only")
    require("feature.hairline" in row.get("tags", []), "generated dashboard row missing feature.hairline")
    require("broad hairline Path AA" in row.get("nonClaim", ""), "generated dashboard nonClaim changed")

    m60 = require_object(evidence, "linkedM60Boundary", EVIDENCE_PATH)
    require(m60.get("sceneId") == "m60-bounded-stroke-cap-join", "M60 linked scene changed")
    require(m60.get("status") == "expected-unsupported", "M60 status changed")
    require(m60.get("fallbackReason") == STROKE_FALLBACK, "M60 fallback changed")
    require(m60.get("remainingBoundary") == REMAINING_BOUNDARY, "M60 remaining boundary changed")
    require(m60.get("edgeCount") == 18, "M60 edge count changed")
    require(m60.get("edgeBudget") == EDGE_BUDGET, "M60 edge budget changed")
    require(m60.get("pathVerbCount") == 9, "M60 path verb count changed")
    require(m60.get("pathVerbBudget") == PATH_VERB_BUDGET, "M60 path verb budget changed")
    require(m60.get("supportThreshold") == SUPPORT_THRESHOLD, "M60 support threshold changed")

    m60_route = load_json(root, M60_ROUTE_PATH)
    require(m60_route.get("status") == "expected-unsupported", "M60 route status changed")
    require(m60_route.get("selectedRoute") == "webgpu.coverage.refuse", "M60 route changed")
    require(m60_route.get("fallbackReason") == STROKE_FALLBACK, "M60 route fallback changed")
    require(m60_route.get("remainingRootCause") == REMAINING_BOUNDARY, "M60 remaining root cause changed")
    require(m60_route.get("edgeBudget") == EDGE_BUDGET, "M60 route edge budget changed")

    m60_stats = load_json(root, M60_STATS_PATH)
    require(m60_stats.get("threshold") == SUPPORT_THRESHOLD, "M60 stats threshold changed")
    require(m60_stats.get("gpuStatus") == "expected-unsupported", "M60 stats status changed")
    require(m60_stats.get("fallbackReason") == STROKE_FALLBACK, "M60 stats fallback changed")

    for266 = load_json(root, FOR266_PROBE_PATH)
    require(for266.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-266 decision changed")
    require(for266.get("fallbackReason") == STROKE_FALLBACK, "FOR-266 fallback changed")
    require(for266.get("remainingBoundary") == REMAINING_BOUNDARY, "FOR-266 root cause changed")

    for267 = load_json(root, FOR267_PROBE_PATH)
    require(for267.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-267 decision changed")
    require(for267.get("boundedCoverageCorrectionStatus") == "REFUSED", "FOR-267 correction status changed")
    require(for267.get("nextMissingCondition") == "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells", "FOR-267 missing condition changed")
    route_diagnostics = require_object(for267, "routeDiagnostics", FOR267_PROBE_PATH)
    require(route_diagnostics.get("normalRoute") == "webgpu.coverage.refuse", "FOR-267 normal route changed")
    require(route_diagnostics.get("fallbackReason") == STROKE_FALLBACK, "FOR-267 fallback changed")

    validations = require_list(evidence, "validationRows", EVIDENCE_PATH)
    require(len(validations) >= 6, "validationRows must summarize the harness, dashboard, linked boundaries, and non-claims")
    require(all(isinstance(row, dict) and row.get("status") == "pass" for row in validations), "all validation rows must pass")

    non_claims = require_list(evidence, "nonClaims", EVIDENCE_PATH)
    for claim in (
        "KAN-026 does not claim HairlinesGM WebGPU support.",
        "KAN-026 does not lower the 99.95 support threshold.",
        "KAN-026 does not change shared renderer or shader behavior.",
        "KAN-026 does not expand broad Path AA, hairline, cap/join, dash, or edge-budget support.",
    ):
        require(claim in non_claims, f"missing non-claim: {claim}")

    require_contains(root, HAIRLINES_TEST_PATH, [
        "HairlinesGM()",
        "rasterFloor = 97.63",
        "gpuFloor = 98.92",
        "rasterTolerance = 1",
    ])
    require_contains(root, CROSS_HARNESS_PATH, [
        "WebGpuContext.createOrNull()",
        "TestUtils.saveDebugImage(rasterBitmap, \"$referenceName-raster\")",
        "TestUtils.saveDebugImage(gpuBitmap, \"$referenceName-gpu\")",
        "saveCrossBackendDiff(rasterBitmap, gpuBitmap, referenceName)",
        "gpu-raster/build/debug-images/$referenceName-{raster,gpu,diff}.png",
    ])
    require_contains(root, HAIRLINES_GM_PATH, [
        "override fun getName(): String = \"hairlines\"",
        "override fun getISize(): SkISize = SkISize.Make(1250, 1250)",
        "val widths = floatArrayOf(0f, 0.5f, 1.5f)",
        "c.drawPath(p, paint)",
    ])
    require_contains(root, FOR318_REPORT_PATH, [
        "strokeWidth=0",
        "preserve-256-edge-budget-and-current-refusals",
        "requires-skia-reference-plus-cpu-and-adapter-backed-gpu-proof",
        "no arc/hairline support claimed",
    ])
    require_contains(root, REPORT_PATH, [
        "visible non supportable",
        STROKE_FALLBACK,
        HAIRLINES_FALLBACK,
        "No renderer or shader change",
        "No threshold is lowered",
    ])
    require_contains(root, BUILD_GRADLE_PATH, [
        "validateKan026HairlinesHarness",
        "scripts/validate_kan026_hairlines_harness.py",
        "coverage.hairline.row-specific-artifacts-required",
    ])

    artifact_paths = require_list(evidence, "artifactPaths", EVIDENCE_PATH)
    for artifact in artifact_paths:
        require_file(root, artifact)

    print("KAN-026 validation passed: HairlinesGM harness boundary is visible, support stays refused, and no thresholds or renderer behavior changed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
