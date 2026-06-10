#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-003-caps-joins-aa.md"
EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-003-caps-joins-aa/kan-003-caps-joins-aa.json"
M60_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"
M60_SCENE = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"
FOR266_REPORT_PATH = "reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md"
FOR267_REPORT_PATH = "reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"
FOR266_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"
FOR267_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
CONTRACTS_PATH = "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
SELECTOR_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"

SCENE_ID = "m60-bounded-stroke-cap-join"
FALLBACK = "coverage.stroke-cap-join-visual-parity-below-threshold"
REMAINING = "coverage.stroke-cap-join-aa-residual"
EDGE_BUDGET = 256
PATH_VERB_BUDGET = 96
SUPPORT_THRESHOLD = 99.95


def fail(message: str):
    raise SystemExit(f"KAN-003 caps/joins AA validation failed: {message}")


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


def require_route(
    root: Path,
    relative_path: str,
    *,
    backend: str,
    status: str,
    fallback_reason: str,
) -> dict[str, Any]:
    route = load_json(root, relative_path)
    require(route.get("sceneId") == SCENE_ID, f"{relative_path} sceneId changed")
    require(route.get("backend") == backend, f"{relative_path} backend changed")
    require(route.get("status") == status, f"{relative_path} status changed")
    require(route.get("fallbackReason") == fallback_reason, f"{relative_path} fallback changed")
    return route


def m60_row(root: Path) -> dict[str, Any]:
    pack = load_json(root, M60_PACK_PATH)
    scenes = pack.get("scenes")
    require(isinstance(scenes, list), f"{M60_PACK_PATH}.scenes must be a list")
    for row in scenes:
        if isinstance(row, dict) and row.get("id") == SCENE_ID:
            return row
    fail(f"{M60_PACK_PATH} missing {SCENE_ID}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, EVIDENCE_PATH)
    require(evidence.get("schemaVersion") == 1, "schemaVersion changed")
    require(evidence.get("ticket") == "KAN-003", "ticket id changed")
    require(evidence.get("packId") == "kan-003-caps-joins-aa-visible-refusal-v1", "packId changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    require(evidence.get("closureDecision") == "visible-non-supportable", "closure decision changed")
    require(evidence.get("claimLevel") == "bounded-stroke-cap-join-visible-refusal", "claimLevel changed")
    require(evidence.get("supportClaim") is False, "KAN-003 must not claim support")
    require(evidence.get("fallbackReason") == FALLBACK, "fallback reason changed")
    require(evidence.get("remainingBoundary") == REMAINING, "remaining boundary changed")
    require(evidence.get("thresholdsWeakened") is False, "thresholds must not be weakened")
    require(evidence.get("sharedCoverageChanged") is False, "shared coverage must not change")
    require(evidence.get("readinessDelta") == 0, "KAN-003 must not move readiness")

    scene = require_object(evidence, "selectedScene", EVIDENCE_PATH)
    require(scene.get("sceneId") == SCENE_ID, "selected scene changed")
    require(scene.get("status") == "expected-unsupported", "selected scene status changed")
    require(scene.get("cpuStatus") == "pass", "CPU status changed")
    require(scene.get("gpuStatus") == "expected-unsupported", "GPU status changed")
    require(scene.get("gpuFallbackReason") == FALLBACK, "GPU fallback changed")
    require(scene.get("normalGpuRoute") == "webgpu.coverage.refuse", "normal GPU route changed")
    require(scene.get("diagnosticGpuRoute") == "webgpu.coverage.stroke-cap-join.experimental-render", "diagnostic GPU route changed")
    require(scene.get("edgeCount") == 18, "edge count changed")
    require(scene.get("edgeBudget") == EDGE_BUDGET, "edge budget changed")
    require(scene.get("pathVerbCount") == 9, "path verb count changed")
    require(scene.get("pathVerbBudget") == PATH_VERB_BUDGET, "path verb budget changed")
    require(scene.get("strokeWidth") == 10.0, "stroke width changed")
    require(scene.get("strokeCaps") == ["butt", "round", "square"], "stroke caps changed")
    require(scene.get("strokeJoins") == ["bevel", "round", "bevel"], "stroke joins changed")
    require(scene.get("supportThreshold") == SUPPORT_THRESHOLD, "support threshold changed")
    require(scene.get("targetColorSpaceBlendDiagnosticOnly") is True, "target-color-space blend must stay diagnostic-only")

    for field in (
        "reference",
        "cpuImage",
        "cpuDiff",
        "gpuExperimentalImage",
        "gpuExperimentalDiff",
        "cpuRoute",
        "gpuRoute",
        "stats",
        "aaResidualDiagnostic",
        "experimentalGpuDiagnostic",
    ):
        require_file(root, scene[field])

    cpu_route = require_route(
        root,
        scene["cpuRoute"],
        backend="CPU",
        status="pass",
        fallback_reason="none",
    )
    require(cpu_route.get("selectedRoute") == "cpu.coverage.stroke-cap-join-oracle", "CPU route changed")
    require(cpu_route.get("edgeCount") == 18, "CPU route edge count changed")
    require(cpu_route.get("edgeBudget") == EDGE_BUDGET, "CPU route edge budget changed")

    gpu_route = require_route(
        root,
        scene["gpuRoute"],
        backend="WebGPU",
        status="expected-unsupported",
        fallback_reason=FALLBACK,
    )
    require(gpu_route.get("selectedRoute") == "webgpu.coverage.refuse", "GPU route changed")
    require(gpu_route.get("coverageStrategy") == "webgpu.coverage.refuse", "GPU coverage strategy changed")
    require(gpu_route.get("edgeCount") == 18, "GPU route edge count changed")
    require(gpu_route.get("edgeBudget") == EDGE_BUDGET, "GPU route edge budget changed")
    require(gpu_route.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "GPU route edge budget reason changed")
    require(gpu_route.get("remainingRootCause") == REMAINING, "GPU remaining root cause changed")

    stats = load_json(root, scene["stats"])
    require(stats.get("sceneId") == SCENE_ID, "stats sceneId changed")
    require(stats.get("backend") == "WebGPU", "stats backend changed")
    require(stats.get("threshold") == SUPPORT_THRESHOLD, "stats threshold changed")
    require(stats.get("fallbackReason") == FALLBACK, "stats fallback changed")
    require(stats.get("remainingRootCause") == REMAINING, "stats remaining root cause changed")

    residual = load_json(root, scene["aaResidualDiagnostic"])
    require(residual.get("status") == "diagnostic-only", "AA residual status changed")
    require(residual.get("supportClaim") is False, "AA residual must not claim support")
    require(residual.get("remainingRootCause") == REMAINING, "AA residual root cause changed")
    require("expected-unsupported" in residual.get("decision", ""), "AA residual decision changed")
    require(residual.get("maxChannelDelta") == 48, "AA residual max delta changed")
    require(residual.get("greaterThanEightPixels") == 10, "AA residual >8 count changed")
    require(residual.get("greaterThanThirtyTwoPixels") == 6, "AA residual >32 count changed")

    experimental = load_json(root, scene["experimentalGpuDiagnostic"])
    require(experimental.get("status") == "diagnostic-only", "experimental diagnostic status changed")
    require(experimental.get("supportClaim") is False, "experimental diagnostic must not claim support")
    require(experimental.get("fallbackReason") == FALLBACK, "experimental diagnostic fallback changed")
    require(experimental.get("normalRoute") == "webgpu.coverage.refuse", "experimental normal route changed")
    require(experimental.get("selectedRoute") == "webgpu.coverage.stroke-cap-join.experimental-render", "experimental selected route changed")
    require(experimental.get("threshold") == SUPPORT_THRESHOLD, "experimental threshold changed")
    require(experimental.get("remainingRootCause") == REMAINING, "experimental remaining root cause changed")
    require(experimental.get("targetColorSpaceBlend") is True, "experimental target-color-space blend changed")
    require(experimental.get("experimentalGpuSimilarity") == 95.91, "experimental similarity changed")

    row = m60_row(root)
    require(row.get("status") == "expected-unsupported", "M60 row status changed")
    require(row.get("fallbackReason") == FALLBACK, "M60 row fallback changed")
    require(row.get("gpuRoute") == "webgpu.coverage.refuse", "M60 row GPU route changed")
    require(row.get("cpuRoute") == "cpu.coverage.stroke-cap-join-oracle", "M60 row CPU route changed")
    require(row.get("remainingRootCause") == REMAINING, "M60 row remaining root cause changed")
    require(row.get("threshold") == 0, "M60 row dashboard threshold changed")
    require("feature.stroke.cap" in row.get("tags", []), "M60 row missing stroke cap tag")
    require("feature.stroke.join" in row.get("tags", []), "M60 row missing stroke join tag")
    row_gpu = require_object(row, "gpuRouteDetails", M60_PACK_PATH)
    require(row_gpu.get("edgeCount") == 18, "M60 row edge count changed")
    require(row_gpu.get("edgeBudget") == EDGE_BUDGET, "M60 row edge budget changed")
    require(row_gpu.get("strokeCaps") == ["butt", "round", "square"], "M60 row stroke caps changed")
    require(row_gpu.get("strokeJoins") == ["bevel", "round", "bevel"], "M60 row stroke joins changed")
    require(row_gpu.get("targetColorSpaceBlendPilot", {}).get("status") == "diagnostic-only", "M60 target blend pilot status changed")

    for267 = load_json(root, FOR267_PROBE_PATH)
    require(for267.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-267 decision changed")
    require(for267.get("boundedCoverageCorrectionStatus") == "REFUSED", "FOR-267 correction status changed")
    require(for267.get("nextMissingCondition") == "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells", "FOR-267 missing condition changed")
    require(for267.get("remainingBoundary") == REMAINING, "FOR-267 remaining boundary changed")
    require(for267.get("supportThreshold") == SUPPORT_THRESHOLD, "FOR-267 threshold changed")
    for267_route = require_object(for267, "routeDiagnostics", FOR267_PROBE_PATH)
    require(for267_route.get("normalRoute") == "webgpu.coverage.refuse", "FOR-267 normal route changed")
    require(for267_route.get("diagnosticRoute") == "webgpu.coverage.stroke-cap-join.experimental-render", "FOR-267 diagnostic route changed")
    require(for267_route.get("fallbackReason") == FALLBACK, "FOR-267 fallback changed")
    for267_stats = require_object(for267, "coverageStatistics", FOR267_PROBE_PATH)
    require(for267_stats.get("cellCount") == 4, "FOR-267 cell count changed")
    require(for267_stats.get("roundCapCellsObserved") is True, "FOR-267 round cap observation changed")
    require(for267_stats.get("roundJoinCellsObserved") is True, "FOR-267 round join observation changed")
    require(for267_stats.get("safeBoundedCoverageCorrectionProven") is False, "FOR-267 correction proof changed")
    require(for267_stats.get("notEquivalentCells") >= 1, "FOR-267 must keep non-equivalent cells")

    for266 = load_json(root, FOR266_PROBE_PATH)
    require(for266.get("sceneId") == SCENE_ID, "FOR-266 scene changed")
    require(for266.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-266 decision changed")
    require(for266.get("fallbackReason") == FALLBACK, "FOR-266 fallback changed")
    require(for266.get("remainingBoundary") == REMAINING, "FOR-266 remaining boundary changed")

    validations = require_list(evidence, "validationRows", EVIDENCE_PATH)
    require(len(validations) >= 7, "validationRows missing rows")
    for row_item in validations:
        require(isinstance(row_item, dict), "validation row must be object")
        require(row_item.get("status") == "pass", f"validation row failed: {row_item.get('id')}")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "does not claim stroke cap/join WebGPU support",
        "does not claim broad stroke support",
        "does not support all stroke caps or joins",
        "does not lower the 99.95 support threshold",
        "does not change shared coverage production behavior",
        "does not enable targetColorSpaceBlend globally",
        "does not port Ganesh or Graphite",
        "does not add a SkSL compiler",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")

    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    require_contains(root, REPORT_PATH, [
        "KAN-003 closes caps/joins AA as `visible-non-supportable`",
        "`m60-bounded-stroke-cap-join`",
        "`coverage.stroke-cap-join-visual-parity-below-threshold`",
        "`coverage.stroke-cap-join-aa-residual`",
        "`KEEP_DIAGNOSTIC`",
        "No stroke cap/join WebGPU support claim is added.",
        "No threshold is lowered.",
    ])
    require_contains(root, FOR266_REPORT_PATH, [
        "Decision",
        "KEEP_DIAGNOSTIC",
        FALLBACK,
        REMAINING,
    ])
    require_contains(root, FOR267_REPORT_PATH, [
        "Decision: `KEEP_DIAGNOSTIC`",
        "Correction de couverture bornée: refusée",
        "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells",
        FALLBACK,
        REMAINING,
    ])
    require_contains(root, SPEC_REALTIME_PATH, [
        "common stroke caps: butt, round, square",
        "common joins: miter with limit, round, bevel",
        "explicit refusal for edge-count and unsupported join/cap combinations",
        "no broad Path AA support claim from one bounded subset",
    ])
    require_contains(root, CONTRACTS_PATH, [
        'StrokeCapJoinVisualParityBelowThreshold("coverage.stroke-cap-join-visual-parity-below-threshold")',
    ])
    selector_text = require_contains(root, SELECTOR_PATH, [
        "StandardCoverageReason.StrokeCapJoinVisualParityBelowThreshold",
        "pathAaStrokeCapJoinBlocked",
    ])
    for forbidden in (
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        require(forbidden not in selector_text, f"selector must not enable diagnostic correction: {forbidden}")

    print("KAN-003 caps/joins AA validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
