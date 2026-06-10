#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/dashes-bounded-v1"
OUTPUT_JSON = "kan-038-dashes-bounded-v1.json"
OUTPUT_MARKDOWN = "kan-038-dashes-bounded-v1.md"

DASH_VISIBILITY_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"
RESULTS_PATH = "reports/wgsl-pipeline/scenes/generated/results.json"
M54_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"
M66_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"
ROUTE_CPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-cpu.json"
ROUTE_GPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-gpu.json"
STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/stats.json"
SKIA_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/skia.png"
CPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/cpu.png"
CPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/cpu-diff.png"
GPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/gpu.png"
GPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/gpu-diff.png"
DASHING_GM_PATH = "skia-integration-tests/src/main/kotlin/org/skia/tests/DashingGM.kt"
DASHING_CROSS_BACKEND_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/DashingCrossBackendTest.kt"
CONTRACTS_PATH = "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
CONTRACTS_TEST_PATH = "render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_EDGE_BUDGET_PATH = ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"
M48_REPORT_PATH = "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md"
M60_AUDIT_PATH = "reports/wgsl-pipeline/2026-05-31-m60-path-aa-budget-audit.md"
GRA336_REVIEW_PATH = "reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md"

CANDIDATE_ROW_ID = "skia-gm-dashing"
CANDIDATE_ID = "skia-gm-dashing-width1-pattern1-1-aa"
OVER_BUDGET_SCENE_ID = "path-aa-dashing-edge-budget"
M54_SENTINEL_ID = "m54-dash-circle-boundary"
M66_SENTINEL_ID = "m66-path-aa-dashing-edge-budget-refusal"
DASH_ROW_FALLBACK = "coverage.dashing.row-specific-artifacts-required"
EDGE_FALLBACK = "coverage.edge-count-exceeded"
DASH_BUDGET_FALLBACK = "coverage.dash-budget-exceeded"
EDGE_BUDGET = 256
DASH_INTERVAL_BUDGET = 8


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-038 dashes bounded V1 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> dict[str, Any]:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must contain a JSON object")
    return data


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


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def scene_row(root: Path, pack_path: str, row_id: str) -> dict[str, Any]:
    pack = load_json(root, pack_path)
    scenes = require_list(pack, "scenes", pack_path)
    for scene in scenes:
        if isinstance(scene, dict) and scene.get("id") == row_id:
            return scene
    fail(f"{pack_path} missing {row_id}")


def committed_artifacts() -> list[str]:
    return sorted(
        {
            DASH_VISIBILITY_PACK_PATH,
            RESULTS_PATH,
            M54_PACK_PATH,
            M66_PACK_PATH,
            ROUTE_CPU_PATH,
            ROUTE_GPU_PATH,
            STATS_PATH,
            SKIA_IMAGE_PATH,
            CPU_IMAGE_PATH,
            CPU_DIFF_PATH,
            DASHING_GM_PATH,
            DASHING_CROSS_BACKEND_TEST_PATH,
            CONTRACTS_PATH,
            CONTRACTS_TEST_PATH,
            SPEC_REALTIME_PATH,
            SPEC_LOWERING_PATH,
            SPEC_EDGE_BUDGET_PATH,
            M48_REPORT_PATH,
            M60_AUDIT_PATH,
            GRA336_REVIEW_PATH,
        },
    )


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    candidate_row = scene_row(root, DASH_VISIBILITY_PACK_PATH, CANDIDATE_ROW_ID)
    generated_row = scene_row(root, RESULTS_PATH, OVER_BUDGET_SCENE_ID)
    m54_row = scene_row(root, M54_PACK_PATH, M54_SENTINEL_ID)
    m66_row = scene_row(root, M66_PACK_PATH, M66_SENTINEL_ID)
    route_cpu = load_json(root, ROUTE_CPU_PATH)
    route_gpu = load_json(root, ROUTE_GPU_PATH)
    stats = load_json(root, STATS_PATH)

    require(candidate_row.get("status") == "expected-unsupported", "DashingGM policy row status changed")
    require(candidate_row.get("policyOnlyArtifacts") is True, "DashingGM row is no longer policy-only")
    require(candidate_row.get("fallbackReason") == DASH_ROW_FALLBACK, "DashingGM row fallback changed")
    require(candidate_row.get("gpuRoute") == "webgpu.coverage.dashing.expected-unsupported", "DashingGM GPU route changed")
    require(candidate_row.get("cpuRoute") == "cpu.path-coverage.dashing.expected-unsupported", "DashingGM CPU route changed")
    require("feature.dash" in candidate_row.get("tags", []), "DashingGM row lost feature.dash tag")
    require("route.gpu.expected-unsupported" in candidate_row.get("tags", []), "DashingGM row lost GPU refusal tag")

    require(generated_row.get("id") == OVER_BUDGET_SCENE_ID, "generated dash edge-budget row changed")
    require(generated_row.get("status") == "expected-unsupported", "dash edge-budget generated row status changed")
    require(generated_row.get("referenceKind") == "cpu-oracle", "dash edge-budget reference changed")
    generated_cpu = require_object(generated_row, "cpu", RESULTS_PATH)
    generated_gpu = require_object(generated_row, "gpu", RESULTS_PATH)
    generated_cpu_route = require_object(generated_cpu, "route", RESULTS_PATH)
    generated_gpu_route = require_object(generated_gpu, "route", RESULTS_PATH)
    require(generated_cpu.get("status") == "pass", "dash edge-budget generated CPU status changed")
    require(generated_cpu_route.get("fallbackReason") == "none", "dash edge-budget generated CPU fallback changed")
    require(generated_gpu.get("status") == "expected-unsupported", "dash edge-budget generated GPU status changed")
    require(generated_gpu_route.get("fallbackReason") == EDGE_FALLBACK, "dash edge-budget generated GPU fallback changed")

    require(route_cpu.get("sceneId") == OVER_BUDGET_SCENE_ID, "CPU route scene changed")
    require(route_cpu.get("status") == "pass", "CPU route status changed")
    require(route_cpu.get("selectedRoute") == "cpu.path-coverage.dashing-oracle", "CPU route changed")
    require(route_cpu.get("fallbackReason") == "none", "CPU route fallback changed")
    require("strokeDash=true" in str(route_cpu.get("coveragePlan")), "CPU route lost stroke dash coverage plan")
    require(route_gpu.get("sceneId") == OVER_BUDGET_SCENE_ID, "GPU route scene changed")
    require(route_gpu.get("status") == "expected-unsupported", "GPU route status changed")
    require(route_gpu.get("coverageStrategy") == "webgpu.coverage.refuse", "GPU coverage strategy changed")
    require(route_gpu.get("fallbackReason") == EDGE_FALLBACK, "GPU route fallback changed")
    require("pathStrokeDashOverflow" in str(route_gpu.get("pipelineKey")), "GPU pipeline key lost dash overflow axis")
    require(stats.get("status") == "expected-unsupported", "stats status changed")
    require(stats.get("fallbackReason") == EDGE_FALLBACK, "stats fallback changed")
    require(stats.get("threshold") == 0.0, "over-budget stats threshold changed")

    for row, row_id in [(m54_row, M54_SENTINEL_ID), (m66_row, M66_SENTINEL_ID)]:
        require(row.get("status") == "expected-unsupported", f"{row_id} status changed")
        require(row.get("fallbackReason") == EDGE_FALLBACK, f"{row_id} fallback changed")
        require(row.get("baseArtifactScene") == OVER_BUDGET_SCENE_ID, f"{row_id} base scene changed")

    require_contains(root, DASHING_GM_PATH, [
        "val w = width * width * width",
        "intArrayOf(1, 1)",
        "intArrayOf(4, 1)",
        "paint.isAntiAlias = aa != 0",
        "paint.strokeWidth = w.toFloat()",
        "p.pathEffect = SkDashPathEffect.Make(floatArrayOf(on.toFloat(), off.toFloat()), phase)",
        "canvas.drawLine(startX, startY, finalX, finalY, p)",
    ])
    require_contains(root, DASHING_CROSS_BACKEND_TEST_PATH, [
        "filterPath` before",
        "3 stroke widths {0, 1, 8}",
        "2 patterns {1:1, 4:1}",
        "plus the giant-dash regression line",
    ])
    require_contains(root, CONTRACTS_PATH, [
        'DashBudgetExceeded("coverage.dash-budget-exceeded")',
        'PathEffectUnsupported("geometry.path-effect-unsupported")',
        'EdgeCountExceeded("coverage.edge-count-exceeded")',
    ])
    require_contains(root, CONTRACTS_TEST_PATH, [
        'assertEquals("coverage.dash-budget-exceeded", StandardCoverageReason.DashBudgetExceeded.code)',
        'assertEquals("coverage.edge-count-exceeded", StandardCoverageReason.EdgeCountExceeded.code)',
    ])
    require_contains(root, SPEC_REALTIME_PATH, [
        "Dash interval count | <= 8 intervals",
        "Refuse larger dash arrays with `coverage.dash-budget-exceeded`",
        "bounded dash patterns",
    ])
    require_contains(root, SPEC_LOWERING_PATH, [
        "apply path effect when present",
        "pathEffect -> stroke -> primitive normalization",
        "Apply supported path effects before stroke",
    ])
    require_contains(root, SPEC_EDGE_BUDGET_PATH, [
        "coverage.edge-count-exceeded",
        "It must not drop",
    ])
    require_contains(root, M48_REPORT_PATH, [
        "path-aa-dashing-edge-budget",
        "coverage.edge-count-exceeded",
        "do not remove or expand the WebGPU 256-edge Path AA budget",
    ])
    require_contains(root, M60_AUDIT_PATH, [
        "Dash interval count <= 8 intervals",
        "`coverage.dash-budget-exceeded`",
        "Do not claim broad dash support from direct interval diagnostics",
    ])
    require_contains(root, GRA336_REVIEW_PATH, [
        "Keep as dash/cap/join boundary; do not infer support from other dash tests",
        "GPU image or diff",
        "fallbackReason=none` cannot be",
    ])

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")
    require(not (root / GPU_IMAGE_PATH).exists(), "WebGPU support image now exists; update KAN-038 support policy")
    require(not (root / GPU_DIFF_PATH).exists(), "WebGPU support diff now exists; update KAN-038 support policy")

    candidate = {
        "id": CANDIDATE_ID,
        "role": "bounded-candidate",
        "sourceRow": CANDIDATE_ROW_ID,
        "source": "DashingGM main grid",
        "status": "expected-unsupported",
        "decision": "stable-refusal",
        "fallbackReason": DASH_ROW_FALLBACK,
        "dashIntervals": [1.0, 1.0],
        "dashIntervalCount": 2,
        "dashIntervalBudget": DASH_INTERVAL_BUDGET,
        "phase": 0.0,
        "strokeWidth": 1.0,
        "strokeWidthRange": "0.5..64",
        "antiAlias": True,
        "geometry": "horizontal drawLine 0,0 -> 600,0",
        "pathEffectOrder": "before-stroke",
        "sourceEvidence": [
            DASHING_GM_PATH,
            DASHING_CROSS_BACKEND_TEST_PATH,
            DASH_VISIBILITY_PACK_PATH,
        ],
        "postDashVerbCount": "not-recorded",
        "postDashEdgeCount": "not-recorded",
        "postDashEdgeCountProven": False,
        "supportReady": False,
        "blockingCondition": "row-specific_reference_cpu_gpu_diff_stat_route_artifacts_missing",
    }
    over_budget_sentinel = {
        "sceneId": OVER_BUDGET_SCENE_ID,
        "role": "over-budget-sentinel",
        "source": "DashingGM generated inventory row",
        "cpuStatus": route_cpu["status"],
        "cpuRoute": route_cpu["selectedRoute"],
        "cpuFallbackReason": route_cpu["fallbackReason"],
        "gpuStatus": route_gpu["status"],
        "gpuRoute": route_gpu["coverageStrategy"],
        "fallbackReason": route_gpu["fallbackReason"],
        "pipelineKey": route_gpu["pipelineKey"],
        "coveragePlan": route_cpu["coveragePlan"],
        "dashIntervals": [1.0, 1.0],
        "dashIntervalCount": 2,
        "dashIntervalBudget": DASH_INTERVAL_BUDGET,
        "phase": 0.0,
        "postDashVerbCount": "not-recorded",
        "postDashEdgeCount": "exceeded-current-budget",
        "edgeBudget": EDGE_BUDGET,
        "statsStatus": stats["status"],
        "statsFallbackReason": stats["fallbackReason"],
        "nonClaim": route_gpu["nonClaim"],
    }
    related_sentinels = [
        {
            "id": m54_row["id"],
            "source": m54_row.get("inventoryId"),
            "status": m54_row["status"],
            "fallbackReason": m54_row["fallbackReason"],
            "gpuRoute": m54_row["gpuRoute"],
            "pipelineKey": m54_row["pipelineKey"],
            "baseArtifactScene": m54_row["baseArtifactScene"],
        },
        {
            "id": m66_row["id"],
            "source": m66_row.get("inventoryId"),
            "status": m66_row["status"],
            "fallbackReason": m66_row["fallbackReason"],
            "gpuRoute": m66_row["gpuRoute"],
            "pipelineKey": m66_row["pipelineKey"],
            "baseArtifactScene": m66_row["baseArtifactScene"],
        },
    ]

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-038",
        "packId": "kan-038-dashes-bounded-v1",
        "status": "pass",
        "closureDecision": "stable-refusal-dashes-bounded-v1",
        "claimLevel": "bounded-dash-row-identified-stable-refusal",
        "supportClaim": False,
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "candidate": candidate,
        "overBudgetSentinel": over_budget_sentinel,
        "relatedOverBudgetRows": related_sentinels,
        "fallbackTaxonomy": {
            "validBudgetReasons": [EDGE_FALLBACK, DASH_BUDGET_FALLBACK],
            "observedOverBudgetReasons": [EDGE_FALLBACK],
            "dashBudgetExceededReasonCodePresent": True,
            "dashBudgetExceededObservedInCurrentArtifacts": False,
            "pathEffectUnsupportedReasonCodePresent": True,
        },
        "artifactAvailability": {
            "skiaReference": {"available": True, "path": SKIA_IMAGE_PATH},
            "cpuOracle": {"available": True, "path": CPU_IMAGE_PATH},
            "cpuDiff": {"available": True, "path": CPU_DIFF_PATH},
            "webGpuStableRefusalRoute": {"available": True, "path": ROUTE_GPU_PATH},
            "webGpuSupportImage": {
                "available": False,
                "path": GPU_IMAGE_PATH,
                "reason": EDGE_FALLBACK,
            },
            "webGpuSupportDiff": {
                "available": False,
                "path": GPU_DIFF_PATH,
                "reason": EDGE_FALLBACK,
            },
            "postDashVerbEdgeStats": {
                "available": False,
                "reason": "existing generated dash rows do not record exact post-dash verb and edge counts",
            },
        },
        "supportPolicy": {
            "rowStatus": "expected-unsupported",
            "decision": "stable-refusal",
            "requiredForSupport": [
                "row-specific Skia reference image for the bounded dash candidate",
                "row-specific CPU oracle image/diff/stat/route artifacts",
                "row-specific WebGPU image/diff/stat/route artifacts with fallbackReason=none",
                "diagnostics that expose dash intervals, phase, post-dash verbs, post-dash edges, and stroke facts",
                "over-budget sentinel remains refused with coverage.edge-count-exceeded or coverage.dash-budget-exceeded",
                "no global edge-budget, threshold, or dash interval budget increase",
            ],
        },
        "diagnosticRequirements": {
            "dashIntervals": "recorded for candidate",
            "phase": "recorded for candidate",
            "postDashVerbs": "missing in current artifacts",
            "postDashEdges": "missing for bounded candidate; over-budget sentinel records refusal only",
            "strokeFacts": "recorded from DashingGM source row",
            "pathEffectOrder": "path effect before stroke",
        },
        "nonClaims": [
            "KAN-038 does not claim bounded dash WebGPU support.",
            "KAN-038 does not claim broad DashingGM, DashCircleGM, dashcubics, caps/joins, hairlines, or stroke-outline support.",
            "KAN-038 does not add renderer, shader, selector, threshold, edge-budget, or dash interval budget changes.",
            "KAN-038 does not infer support from existing cross-backend similarity floors or policy-only dashboard rows.",
            "KAN-038 keeps over-budget dashed Path AA rows refused by stable budget diagnostics.",
        ],
        "validationRows": [
            {
                "id": "bounded-dash-candidate-identified",
                "status": "pass",
                "evidence": f"{CANDIDATE_ID} uses 2/{DASH_INTERVAL_BUDGET} dash intervals, phase 0, stroke width 1, path effect before stroke.",
            },
            {
                "id": "bounded-candidate-refused-with-policy-row",
                "status": "pass",
                "evidence": f"{CANDIDATE_ROW_ID} remains expected-unsupported via {DASH_ROW_FALLBACK}.",
            },
            {
                "id": "over-budget-sentinel-preserved",
                "status": "pass",
                "evidence": f"{OVER_BUDGET_SCENE_ID} remains expected-unsupported via {EDGE_FALLBACK}.",
            },
            {
                "id": "dash-budget-taxonomy-visible",
                "status": "pass",
                "evidence": f"{DASH_BUDGET_FALLBACK} remains a stable reason code for dash interval overflow.",
            },
            {
                "id": "support-evidence-gap-explicit",
                "status": "pass",
                "evidence": "Bounded support remains blocked until post-dash verb/edge diagnostics and WebGPU fallbackReason=none artifacts exist.",
            },
            {
                "id": "policy-preserved",
                "status": "pass",
                "evidence": "No renderer, shader, selector, threshold, edge-budget, or dash interval budget change is made.",
            },
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def render_markdown(evidence: dict[str, Any]) -> str:
    candidate = evidence["candidate"]
    sentinel = evidence["overBudgetSentinel"]
    related = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | `{row['fallbackReason']}` | `{row['gpuRoute']}` |"
        for row in evidence["relatedOverBudgetRows"]
    )
    required = "\n".join(f"- {item}" for item in evidence["supportPolicy"]["requiredForSupport"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-038 Dashes Bounded V1

KAN-038 identifies a bounded direct dash candidate from `DashingGM`, but closes
the slice as a stable refusal rather than support. The candidate has a valid
dash interval count and path-effect order, but the committed dashboard evidence
is policy-only and lacks row-specific WebGPU `fallbackReason=none` artifacts and
post-dash verb/edge diagnostics.

## Decision

| Field | Value |
|---|---|
| Closure | `{evidence['closureDecision']}` |
| supportClaim | `{evidence['supportClaim']}` |
| Row status | `{evidence['supportPolicy']['rowStatus']}` |
| Renderer changed | `{evidence['rendererChanged']}` |
| Shader changed | `{evidence['sharedShadersChanged']}` |
| Threshold changed | `{evidence['thresholdsWeakened']}` |
| Edge budget changed | `{evidence['edgeBudgetChanged']}` |

## Candidate

| Fact | Value |
|---|---|
| Candidate | `{candidate['id']}` |
| Source row | `{candidate['sourceRow']}` |
| Dash intervals | `{candidate['dashIntervals']}` |
| Dash interval count | `{candidate['dashIntervalCount']}/{candidate['dashIntervalBudget']}` |
| Phase | `{candidate['phase']}` |
| Stroke width | `{candidate['strokeWidth']}` |
| AA | `{candidate['antiAlias']}` |
| Path effect order | `{candidate['pathEffectOrder']}` |
| Status | `{candidate['status']}` |
| Fallback | `{candidate['fallbackReason']}` |
| Post-dash verbs | `{candidate['postDashVerbCount']}` |
| Post-dash edges | `{candidate['postDashEdgeCount']}` |
| Blocking condition | `{candidate['blockingCondition']}` |

## Over-Budget Sentinel

| Fact | Value |
|---|---|
| Scene | `{sentinel['sceneId']}` |
| CPU route | `{sentinel['cpuRoute']}` |
| GPU route | `{sentinel['gpuRoute']}` |
| GPU status | `{sentinel['gpuStatus']}` |
| Fallback | `{sentinel['fallbackReason']}` |
| Edge budget | `{sentinel['edgeBudget']}` |
| Dash intervals | `{sentinel['dashIntervalCount']}/{sentinel['dashIntervalBudget']}` |
| Post-dash edges | `{sentinel['postDashEdgeCount']}` |
| Pipeline key | `{sentinel['pipelineKey']}` |

## Related Sentinels

| Row | Status | Fallback | GPU route |
|---|---|---|---|
{related}

## Required Before Support

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    candidate = evidence["candidate"]
    sentinel = evidence["overBudgetSentinel"]
    print(
        "KAN-038 validation passed: "
        f"{candidate['id']} remains {candidate['status']} via {candidate['fallbackReason']}; "
        f"{sentinel['sceneId']} remains {sentinel['gpuStatus']} via {sentinel['fallbackReason']}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
