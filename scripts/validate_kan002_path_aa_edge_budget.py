#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-002-path-aa-edge-budget.md"
EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-002-path-aa-edge-budget/kan-002-path-aa-edge-budget.json"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_PATH_AA_BOUNDARY_PATH = ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md"
ADR_EDGE_BUDGET_PATH = ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"
CONTRACTS_PATH = "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
SELECTOR_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"
DEVICE_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
GENERATED_RESULTS_PATH = "reports/wgsl-pipeline/scenes/generated/results.json"

EDGE_FALLBACK = "coverage.edge-count-exceeded"
EDGE_BUDGET = 256


def fail(message: str):
    raise SystemExit(f"KAN-002 Path AA edge-budget validation failed: {message}")


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


def require_contains(root: Path, relative_path: str, snippets: list[str]):
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def generated_row(root: Path, scene_id: str) -> dict[str, Any]:
    data = load_json(root, GENERATED_RESULTS_PATH)
    rows = data.get("scenes")
    require(isinstance(rows, list), f"{GENERATED_RESULTS_PATH}.scenes must be a list")
    for row in rows:
        if isinstance(row, dict) and row.get("id") == scene_id:
            return row
        if isinstance(row, dict) and row.get("sceneId") == scene_id:
            return row
    fail(f"{GENERATED_RESULTS_PATH} missing scene row: {scene_id}")


def require_route(
    root: Path,
    relative_path: str,
    *,
    scene_id: str,
    backend: str,
    status: str | None,
    fallback_reason: str | None,
) -> dict[str, Any]:
    route = load_json(root, relative_path)
    require(route.get("sceneId") == scene_id, f"{relative_path} sceneId changed")
    require(route.get("backend") == backend, f"{relative_path} backend changed")
    if status is not None:
        require(route.get("status") == status, f"{relative_path} status changed")
    if fallback_reason is not None:
        require(route.get("fallbackReason") == fallback_reason, f"{relative_path} fallback changed")
    return route


def require_scene_artifacts(root: Path, scene_id: str, names: list[str]):
    for name in names:
        require_file(root, f"reports/wgsl-pipeline/scenes/artifacts/{scene_id}/{name}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, EVIDENCE_PATH)
    require(evidence.get("schemaVersion") == 1, "schemaVersion changed")
    require(evidence.get("ticket") == "KAN-002", "ticket id changed")
    require(evidence.get("packId") == "kan-002-path-aa-edge-budget-v1", "packId changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    require(evidence.get("claimLevel") == "bounded-path-aa-edge-budget-evidence", "claimLevel changed")
    require(evidence.get("edgeBudget") == EDGE_BUDGET, "edgeBudget must remain 256")
    require(evidence.get("budgetChanged") is False, "KAN-002 must not change the edge budget")
    require(evidence.get("thresholdsWeakened") is False, "KAN-002 must not weaken thresholds")
    require(evidence.get("readinessDelta") == 0, "KAN-002 must not move readiness")
    require(evidence.get("supportClaim") == "path-aa-stroke-primitive-only", "support claim changed")
    require(evidence.get("refusalPolicy") == EDGE_FALLBACK, "refusal policy changed")

    bounded = require_object(evidence, "boundedSupportScene", EVIDENCE_PATH)
    require(bounded.get("sceneId") == "path-aa-stroke-primitive", "bounded support scene changed")
    require(bounded.get("gpuStatus") == "pass", "bounded support gpu status changed")
    require(bounded.get("gpuFallbackReason") == "none", "bounded support fallback changed")
    require(bounded.get("coverageStrategy") == "webgpu.coverage.path-aa-stroke-primitive", "bounded support route changed")
    require(bounded.get("budgetReason") == "not coverage.edge-count-exceeded", "bounded support budget reason changed")
    for field in ("reference", "cpuImage", "cpuDiff", "gpuImage", "gpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, bounded[field])
    support_gpu_route = require_route(
        root,
        bounded["gpuRoute"],
        scene_id="path-aa-stroke-primitive",
        backend="WebGPU",
        status="pass",
        fallback_reason="none",
    )
    require(
        support_gpu_route.get("coverageStrategy") == "webgpu.coverage.path-aa-stroke-primitive",
        "path-aa-stroke-primitive route strategy changed",
    )
    require(
        support_gpu_route.get("edgeBudgetReason") == "not coverage.edge-count-exceeded",
        "path-aa-stroke-primitive edge budget reason changed",
    )
    support_row = generated_row(root, "path-aa-stroke-primitive")
    require(support_row.get("status") == "pass", "path-aa-stroke-primitive generated status changed")
    require(support_row.get("gpu", {}).get("status") == "pass", "path-aa-stroke-primitive generated gpu status changed")

    explicit_budget = require_object(evidence, "explicitBudgetScene", EVIDENCE_PATH)
    require(explicit_budget.get("sceneId") == "m57-aaclip-bounded-grid", "explicit budget scene changed")
    require(explicit_budget.get("edgeBudget") == EDGE_BUDGET, "explicit budget scene budget changed")
    require(explicit_budget.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "explicit budget reason changed")
    require(explicit_budget.get("gpuStatus") == "pass", "explicit budget gpu status changed")
    for field in ("reference", "cpuImage", "cpuDiff", "gpuImage", "gpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, explicit_budget[field])
    budget_gpu_route = require_route(
        root,
        explicit_budget["gpuRoute"],
        scene_id="m57-aaclip-bounded-grid",
        backend="WebGPU",
        status="pass",
        fallback_reason="none",
    )
    require(budget_gpu_route.get("edgeBudget") == EDGE_BUDGET, "m57 gpu route budget changed")

    refusal = require_object(evidence, "edgeBudgetRefusalScene", EVIDENCE_PATH)
    require(refusal.get("sceneId") == "path-aa-convexpaths-edge-budget", "edge-budget refusal scene changed")
    require(refusal.get("gpuStatus") == "expected-unsupported", "refusal gpu status changed")
    require(refusal.get("gpuFallbackReason") == EDGE_FALLBACK, "refusal fallback changed")
    require(refusal.get("coverageStrategy") == "webgpu.coverage.refuse", "refusal strategy changed")
    for field in ("reference", "cpuImage", "cpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, refusal[field])
    require_route(
        root,
        refusal["gpuRoute"],
        scene_id="path-aa-convexpaths-edge-budget",
        backend="WebGPU",
        status="expected-unsupported",
        fallback_reason=EDGE_FALLBACK,
    )
    refusal_row = generated_row(root, "path-aa-convexpaths-edge-budget")
    require(refusal_row.get("status") == "expected-unsupported", "generated refusal status changed")
    require(refusal_row.get("gpu", {}).get("status") == "expected-unsupported", "generated refusal gpu status changed")
    require(
        refusal_row.get("gpu", {}).get("route", {}).get("fallbackReason") == EDGE_FALLBACK,
        "generated refusal fallback changed",
    )

    boundary = require_object(evidence, "inventoryBoundaryScene", EVIDENCE_PATH)
    require(boundary.get("sceneId") == "path-aa-edge-budget-boundary", "inventory boundary scene changed")
    require(boundary.get("edgeCountExceededRows") == 46, "edge-count inventory count changed")
    require(boundary.get("expectedUnsupportedRows") == 50, "expected unsupported count changed")
    require(boundary.get("unexpectedExceptions") == 0, "unexpected exceptions changed")
    require(boundary.get("gpuFallbackReason") == EDGE_FALLBACK, "inventory boundary fallback changed")
    for field in ("reference", "cpuImage", "cpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, boundary[field])
    require_route(
        root,
        boundary["gpuRoute"],
        scene_id="path-aa-edge-budget-boundary",
        backend="WebGPU",
        status=None,
        fallback_reason=EDGE_FALLBACK,
    )

    bounded_stroke = require_object(evidence, "underBudgetRefusalScene", EVIDENCE_PATH)
    require(bounded_stroke.get("sceneId") == "m60-bounded-stroke-cap-join", "under-budget refusal scene changed")
    require(bounded_stroke.get("edgeCount") == 18, "under-budget refusal edgeCount changed")
    require(bounded_stroke.get("edgeBudget") == EDGE_BUDGET, "under-budget refusal edgeBudget changed")
    require(bounded_stroke.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "under-budget refusal budget reason changed")
    require(bounded_stroke.get("gpuStatus") == "expected-unsupported", "under-budget refusal status changed")
    require(
        bounded_stroke.get("gpuFallbackReason") == "coverage.stroke-cap-join-visual-parity-below-threshold",
        "under-budget refusal fallback changed",
    )
    for field in ("cpuRoute", "gpuRoute", "stats"):
        require_file(root, bounded_stroke[field])
    bounded_stroke_gpu_route = require_route(
        root,
        bounded_stroke["gpuRoute"],
        scene_id="m60-bounded-stroke-cap-join",
        backend="WebGPU",
        status="expected-unsupported",
        fallback_reason="coverage.stroke-cap-join-visual-parity-below-threshold",
    )
    require(bounded_stroke_gpu_route.get("edgeCount") == 18, "m60 gpu route edgeCount changed")
    require(bounded_stroke_gpu_route.get("edgeBudget") == EDGE_BUDGET, "m60 gpu route edgeBudget changed")
    require(
        bounded_stroke_gpu_route.get("edgeBudgetReason") == "not coverage.edge-count-exceeded",
        "m60 gpu route edge budget reason changed",
    )

    validation_rows = require_list(evidence, "validationRows", EVIDENCE_PATH)
    require(len(validation_rows) >= 7, "validationRows missing rows")
    for row in validation_rows:
        require(isinstance(row, dict), "validation row must be object")
        require(row.get("status") == "pass", f"validation row failed: {row.get('id')}")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "does not claim broad Path AA support",
        "does not support arbitrary complex paths",
        "does not increase the WebGPU edge budget",
        "does not weaken dashboard thresholds",
        "does not port Ganesh or Graphite",
        "does not add a SkSL compiler",
        "does not silently fall back to CPU readback",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")

    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    require_scene_artifacts(
        root,
        "path-aa-stroke-primitive",
        ["skia.png", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"],
    )
    require_scene_artifacts(
        root,
        "path-aa-convexpaths-edge-budget",
        ["skia.png", "cpu.png", "cpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"],
    )

    require_contains(root, REPORT_PATH, [
        "KAN-002 records the Path AA edge budget as `256`",
        "`path-aa-stroke-primitive`",
        "`m57-aaclip-bounded-grid`",
        "`path-aa-convexpaths-edge-budget`",
        "`path-aa-edge-budget-boundary`",
        "`coverage.edge-count-exceeded`",
        "No broad Path AA support claim is added.",
        "No edge budget or dashboard threshold is changed.",
    ])
    require_contains(root, SPEC_REALTIME_PATH, [
        "Coverage edge count | <= 256",
        "reference, CPU, GPU, diff/stat, route, and refusal artifacts",
        "CPU/GPU/reference/diff/stats artifacts",
        "no broad Path AA support claim from one bounded subset",
    ])
    require_contains(root, SPEC_PATH_AA_BOUNDARY_PATH, [
        EDGE_FALLBACK,
        "stable reason",
        "smoke candidate",
    ])
    require_contains(root, ADR_EDGE_BUDGET_PATH, [
        "Use 256",
        EDGE_FALLBACK,
        "CPU readback path",
    ])
    require_contains(root, CONTRACTS_PATH, [
        'EdgeCountExceeded("coverage.edge-count-exceeded")',
    ])
    require_contains(root, SELECTOR_PATH, [
        "public const val WEBGPU_PATH_AA_EDGE_BUDGET: Int = 256",
        "facts.edgeCount > WEBGPU_PATH_AA_EDGE_BUDGET -> StandardCoverageReason.EdgeCountExceeded",
    ])
    require_contains(root, DEVICE_PATH, [
        "const val MAX_AA_EDGES: Int = 256",
    ])

    print("KAN-002 Path AA edge-budget validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
