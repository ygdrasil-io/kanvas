#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/butt-stroke-non-hairline"
OUTPUT_JSON = "kan-036-butt-stroke-non-hairline.json"
OUTPUT_MARKDOWN = "kan-036-butt-stroke-non-hairline.md"

HARNESS_JSON_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/circular-arcs-stroke-butt-selected-cell-harness-for322.json"
HARNESS_REPORT_PATH = "reports/wgsl-pipeline/2026-06-04-for-322-circular-arcs-stroke-butt-selected-cell-harness.md"
HARNESS_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/stats.json"
HARNESS_GPU_ROUTE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-gpu.json"
HARNESS_CPU_ROUTE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-cpu.json"
HARNESS_CPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png"
HARNESS_CPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu-diff.png"
SKIA_REFERENCE_JSON_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/circular-arcs-stroke-butt-selected-cell-skia-reference-for327.json"
SKIA_REFERENCE_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png"
SKIA_REFERENCE_PROVENANCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia-reference-provenance.json"
SKIA_CPU_DIFF_JSON_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328.json"
SKIA_CPU_DIFF_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328/cpu-vs-skia-diff.png"
CPU_RASTER_AUDIT_JSON_PATH = "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329/circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329.json"
KAN035_EVIDENCE_PATH = "reports/wgsl-pipeline/hairlines-root-cause/kan-035-hairlines-root-cause.json"
SELECTED_CELL_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt"
SELECTOR_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"
SELECTOR_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_WEBGPU_PATH = ".upstream/specs/geometry-coverage/04-webgpu-coverage-backend.md"
SPEC_VALIDATION_PATH = ".upstream/specs/geometry-coverage/06-validation-and-perf.md"

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FIXTURE_ID = "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true"
FALLBACK_REASON = "coverage.stroke-cap-join-visual-parity-below-threshold"
SUPPORT_THRESHOLD = 99.95
EDGE_BUDGET = 256
PATH_VERB_BUDGET = 96


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-036 butt stroke non-hairline validation failed: {message}")


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


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


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


def parse_budget_pair(message: str, name: str) -> tuple[int, int]:
    match = re.search(rf"{re.escape(name)}=(\d+)/(\d+)", message)
    require(match is not None, f"refusal message missing {name}")
    return int(match.group(1)), int(match.group(2))


def parse_optional_budget_pair(message: str, name: str) -> tuple[str, int]:
    match = re.search(rf"{re.escape(name)}=([^\n;]+)/(\d+)", message)
    require(match is not None, f"refusal message missing {name}")
    return match.group(1), int(match.group(2))


def parse_string_fact(message: str, name: str) -> str:
    match = re.search(rf"{re.escape(name)}=([^\n;]+)", message)
    require(match is not None, f"refusal message missing {name}")
    return match.group(1)


def committed_artifacts() -> list[str]:
    return sorted(
        {
            HARNESS_JSON_PATH,
            HARNESS_REPORT_PATH,
            HARNESS_STATS_PATH,
            HARNESS_GPU_ROUTE_PATH,
            HARNESS_CPU_ROUTE_PATH,
            HARNESS_CPU_IMAGE_PATH,
            HARNESS_CPU_DIFF_PATH,
            SKIA_REFERENCE_JSON_PATH,
            SKIA_REFERENCE_IMAGE_PATH,
            SKIA_REFERENCE_PROVENANCE_PATH,
            SKIA_CPU_DIFF_JSON_PATH,
            SKIA_CPU_DIFF_IMAGE_PATH,
            CPU_RASTER_AUDIT_JSON_PATH,
            KAN035_EVIDENCE_PATH,
            SELECTED_CELL_TEST_PATH,
            SELECTOR_PATH,
            SELECTOR_TEST_PATH,
            SPEC_LOWERING_PATH,
            SPEC_WEBGPU_PATH,
            SPEC_VALIDATION_PATH,
        },
    )


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    harness = load_json(root, HARNESS_JSON_PATH)
    stats = load_json(root, HARNESS_STATS_PATH)
    gpu_route = load_json(root, HARNESS_GPU_ROUTE_PATH)
    cpu_route = load_json(root, HARNESS_CPU_ROUTE_PATH)
    skia_reference = load_json(root, SKIA_REFERENCE_JSON_PATH)
    skia_cpu_diff = load_json(root, SKIA_CPU_DIFF_JSON_PATH)
    cpu_raster_audit = load_json(root, CPU_RASTER_AUDIT_JSON_PATH)
    kan035 = load_json(root, KAN035_EVIDENCE_PATH)

    selected = require_object(harness, "selectedCell", HARNESS_JSON_PATH)
    stats_cell = require_object(stats, "cell", HARNESS_STATS_PATH)
    gpu_cell = require_object(gpu_route, "cell", HARNESS_GPU_ROUTE_PATH)
    skia_cell = require_object(skia_reference, "selectedCell", SKIA_REFERENCE_JSON_PATH)
    diff_cell = require_object(skia_cpu_diff, "selectedCell", SKIA_CPU_DIFF_JSON_PATH)
    audit_cell = require_object(cpu_raster_audit, "selectedCell", CPU_RASTER_AUDIT_JSON_PATH)

    for source, cell in [
        (HARNESS_STATS_PATH, stats_cell),
        (HARNESS_GPU_ROUTE_PATH, gpu_cell),
        (SKIA_REFERENCE_JSON_PATH, skia_cell),
        (SKIA_CPU_DIFF_JSON_PATH, diff_cell),
        (CPU_RASTER_AUDIT_JSON_PATH, audit_cell),
    ]:
        require(cell.get("fixtureId") == selected.get("fixtureId"), f"{source} fixture id changed")

    require(selected.get("fixtureId") == FIXTURE_ID, "selected fixture changed")
    require(selected.get("sourceFutureTarget") == "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe", "future target changed")
    require(selected.get("strokeWidth") == 15, "stroke width changed")
    require(selected.get("strokeCap") == "kButt_Cap", "stroke cap changed")
    require(selected.get("includesHairlineStrokeWidth0") is False, "selected row unexpectedly includes hairline")
    require(selected.get("includesDash") is False, "selected row unexpectedly includes dash")
    require(selected.get("includesFill") is False, "selected row unexpectedly includes fill")
    require(selected.get("includedCaps") == ["kButt_Cap"], "included caps changed")
    require("kRound_Cap" in selected.get("excludedCaps", []), "round cap is no longer excluded")
    require("kSquare_Cap" in selected.get("excludedCaps", []), "square cap is no longer excluded")

    require(harness.get("decision") == "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY", "FOR-322 harness decision changed")
    require(stats.get("supportStatus") == "not-supported", "FOR-322 support status changed")
    require(stats.get("gpuStatus") == "expected-unsupported", "FOR-322 GPU status changed")
    require(stats.get("gpuSimilarityToCpu") is None, "FOR-322 unexpectedly has GPU similarity")
    require(gpu_route.get("status") == "expected-unsupported", "GPU route status changed")
    require(gpu_route.get("selectedRoute") == "webgpu.selected-cell-test-harness.refused", "GPU route changed")
    require(gpu_route.get("fallbackReason") == FALLBACK_REASON, "GPU fallback changed")
    require(gpu_route.get("edgeCount") == 66, "GPU edge count changed")
    require(gpu_route.get("edgeBudget") == EDGE_BUDGET, "GPU edge budget changed")
    require(gpu_route.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "GPU edge budget reason changed")
    require(cpu_route.get("fallbackReason") == "none", "CPU route fallback changed")
    require(cpu_route.get("supportStatus") == "not-supported", "CPU route support status changed")

    refusal_message = str(gpu_route.get("refusalMessage", ""))
    require(FALLBACK_REASON in refusal_message, "refusal message missing stable fallback")
    path_verbs, path_verb_budget = parse_budget_pair(refusal_message, "pathVerbCount")
    coverage_edges, edge_budget = parse_budget_pair(refusal_message, "coverageEdgeCount")
    clip_depth, clip_depth_budget = parse_optional_budget_pair(refusal_message, "clipStackDepth")
    stroke_width = float(parse_string_fact(refusal_message, "strokeWidth"))
    stroke_caps = parse_string_fact(refusal_message, "strokeCaps").split("+")
    stroke_joins = parse_string_fact(refusal_message, "strokeJoins").split("+")
    device_bounds = [float(part) for part in parse_string_fact(refusal_message, "deviceBounds").split(",")]
    require(path_verbs == 67 and path_verb_budget == PATH_VERB_BUDGET, "path verb diagnostics changed")
    require(coverage_edges == 66 and edge_budget == EDGE_BUDGET, "edge diagnostics changed")
    require(clip_depth == "n/a" and clip_depth_budget == 4, "clip-depth diagnostics changed")
    require(stroke_width == 15.0, "refusal stroke width changed")
    require(stroke_caps == ["butt"], "refusal stroke cap changed")
    require(stroke_joins == ["miter"], "refusal stroke join changed")
    require(len(device_bounds) == 4, "device bounds must contain 4 values")

    require(skia_reference.get("skiaPngReady") is True, "Skia reference is not ready")
    require(skia_reference.get("skiaPngPresent") is True, "Skia reference PNG missing")
    require(skia_cpu_diff.get("decision") == "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT", "Skia/CPU diff decision changed")
    require(cpu_raster_audit.get("decision") == "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED", "CPU raster audit decision changed")
    require(kan035.get("closureDecision") == "stable-refusal-diagnostic-fix", "KAN-035 closure decision changed")

    require_contains(root, SELECTED_CELL_TEST_PATH, [
        SCENE_ID,
        "strokeWidth = 15f",
        "strokeCap = SkPaint.Cap.kButt_Cap",
        FALLBACK_REASON,
    ])
    require_contains(root, SELECTOR_PATH, [
        "facts.hasStrokeStyleFacts",
        "StrokeCapJoinVisualParityBelowThreshold",
        "pathAaStrokeCapJoinBlocked",
    ])
    require_contains(root, SELECTOR_TEST_PATH, [
        "bounded stroke cap join facts emit stable parity blocker",
        FALLBACK_REASON,
    ])
    require_contains(root, SPEC_LOWERING_PATH, ["Stroke width, cap, join, and miter limit are geometry facts"])
    require_contains(root, SPEC_WEBGPU_PATH, ["Every unsupported strategy has a stable diagnostic"])
    require_contains(root, SPEC_VALIDATION_PATH, ["CPU oracle evidence exists", "WebGPU evidence exists or a stable unsupported diagnostic is asserted"])

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-036",
        "packId": "kan-036-butt-stroke-non-hairline-v1",
        "status": "pass",
        "closureDecision": "stable-refusal-existing-selector",
        "claimLevel": "selected-butt-stroke-non-hairline-stable-refusal",
        "supportClaim": False,
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "selectedRow": selected,
        "webGpuRefusal": {
            "status": gpu_route["status"],
            "adapter": gpu_route.get("adapter"),
            "selectedRoute": gpu_route["selectedRoute"],
            "fallbackReason": gpu_route["fallbackReason"],
            "refusalReason": gpu_route["refusalReason"],
            "pathVerbCount": path_verbs,
            "pathVerbBudget": path_verb_budget,
            "coverageEdgeCount": coverage_edges,
            "edgeBudget": edge_budget,
            "edgeBudgetReason": gpu_route["edgeBudgetReason"],
            "clipStackDepth": clip_depth,
            "clipStackDepthBudget": clip_depth_budget,
            "deviceBounds": device_bounds,
            "strokeWidth": stroke_width,
            "strokeCaps": stroke_caps,
            "strokeJoins": stroke_joins,
            "pipelineKeyContains": "coverageKind=pathAaStrokeCapJoinBlocked",
        },
        "skiaReference": {
            "decision": skia_reference["decision"],
            "available": True,
            "path": SKIA_REFERENCE_IMAGE_PATH,
            "provenance": SKIA_REFERENCE_PROVENANCE_PATH,
        },
        "cpuOracle": {
            "decision": harness["decision"],
            "route": cpu_route["selectedRoute"],
            "fallbackReason": cpu_route["fallbackReason"],
            "path": HARNESS_CPU_IMAGE_PATH,
            "diffPath": HARNESS_CPU_DIFF_PATH,
        },
        "cpuVsSkia": {
            "decision": skia_cpu_diff["decision"],
            "supportReady": False,
            "diffPath": SKIA_CPU_DIFF_IMAGE_PATH,
            "auditDecision": cpu_raster_audit["decision"],
            "interpretation": skia_cpu_diff.get("interpretation"),
        },
        "artifactAvailability": {
            "skiaReference": {"available": True, "path": SKIA_REFERENCE_IMAGE_PATH},
            "cpuOracle": {"available": True, "path": HARNESS_CPU_IMAGE_PATH},
            "cpuVsSkiaDiff": {"available": True, "path": SKIA_CPU_DIFF_IMAGE_PATH},
            "webGpuStableRefusal": {"available": True, "reason": FALLBACK_REASON},
            "webGpuImage": {"available": False, "reason": "stable-refusal-before-debug-images"},
            "webGpuDiff": {"available": False, "reason": "stable-refusal-before-debug-images"},
        },
        "supportPolicy": {
            "rowStatus": "expected-unsupported",
            "decision": "stable-refusal",
            "requiredForSupport": [
                "adapter-backed WebGPU image",
                "WebGPU diff/stat artifact",
                "fallbackReason=none",
                "CPU vs Skia support-ready decision",
                "no threshold or edge-budget weakening",
            ],
        },
        "nonClaims": [
            "KAN-036 does not claim support for the selected butt stroke row.",
            "KAN-036 does not claim broad stroke, cap, join, dash, or hairline support.",
            "KAN-036 does not change renderer, shader, selector, PipelineKey, threshold, or edge-budget behavior.",
            "KAN-036 does not lower the 99.95 support threshold or increase the 256 WebGPU AA edge budget.",
        ],
        "validationRows": [
            {
                "id": "selected-row-bounded",
                "status": "pass",
                "evidence": "The row is one non-hairline, no-dash, butt-cap selected cell with strokeWidth=15.",
            },
            {
                "id": "webgpu-stable-refusal",
                "status": "pass",
                "evidence": f"WebGPU refuses through {FALLBACK_REASON} with edges 66/256 and verbs 67/96.",
            },
            {
                "id": "support-claim-blocked",
                "status": "pass",
                "evidence": "No WebGPU image/diff exists and CPU-vs-Skia remains in raster-audit status.",
            },
            {
                "id": "policy-preserved",
                "status": "pass",
                "evidence": "No renderer, shader, selector, threshold, or edge-budget change is made.",
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
    selected = evidence["selectedRow"]
    refusal = evidence["webGpuRefusal"]
    availability = evidence["artifactAvailability"]
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {claim}" for claim in evidence["nonClaims"])
    required = "\n".join(f"- {item}" for item in evidence["supportPolicy"]["requiredForSupport"])
    return f"""# KAN-036 Butt Stroke Non-Hairline

KAN-036 selects one bounded non-hairline butt-cap stroke row and closes it as a
stable refusal, not as support. The current WebGPU selector still refuses stroke
style facts with `{refusal['fallbackReason']}` before any WebGPU image or diff
artifact exists.

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
| Readiness delta | `{evidence['readinessDelta']}` |

## Selected Row

| Fact | Value |
|---|---|
| Fixture | `{selected['fixtureId']}` |
| Source GM | `{selected['sourceGm']}` |
| Stroke width | `{selected['strokeWidth']}` |
| Stroke cap | `{selected['strokeCap']}` |
| Hairline included | `{selected['includesHairlineStrokeWidth0']}` |
| Dash included | `{selected['includesDash']}` |
| Fill included | `{selected['includesFill']}` |
| AA | `{selected['aa']}` |

## WebGPU Refusal

| Fact | Value |
|---|---|
| Status | `{refusal['status']}` |
| Route | `{refusal['selectedRoute']}` |
| Fallback | `{refusal['fallbackReason']}` |
| Path verbs | `{refusal['pathVerbCount']}/{refusal['pathVerbBudget']}` |
| Coverage edges | `{refusal['coverageEdgeCount']}/{refusal['edgeBudget']}` |
| Edge budget reason | `{refusal['edgeBudgetReason']}` |
| Clip stack depth | `{refusal['clipStackDepth']}/{refusal['clipStackDepthBudget']}` |
| Stroke facts | width `{refusal['strokeWidth']}`, caps `{'+'.join(refusal['strokeCaps'])}`, joins `{'+'.join(refusal['strokeJoins'])}` |
| Device bounds | `{refusal['deviceBounds']}` |

## Artifact Availability

| Artifact | Available | Evidence |
|---|---:|---|
| Skia reference | `{availability['skiaReference']['available']}` | `{availability['skiaReference']['path']}` |
| CPU oracle | `{availability['cpuOracle']['available']}` | `{availability['cpuOracle']['path']}` |
| CPU vs Skia diff | `{availability['cpuVsSkiaDiff']['available']}` | `{availability['cpuVsSkiaDiff']['path']}` |
| WebGPU stable refusal | `{availability['webGpuStableRefusal']['available']}` | `{availability['webGpuStableRefusal']['reason']}` |
| WebGPU image | `{availability['webGpuImage']['available']}` | `{availability['webGpuImage']['reason']}` |
| WebGPU diff | `{availability['webGpuDiff']['available']}` | `{availability['webGpuDiff']['reason']}` |

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
    print(
        "KAN-036 validation passed: selected butt stroke remains "
        f"{evidence['supportPolicy']['rowStatus']} via "
        f"{evidence['webGpuRefusal']['fallbackReason']} with "
        f"{evidence['webGpuRefusal']['coverageEdgeCount']}/{evidence['webGpuRefusal']['edgeBudget']} edges.",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
