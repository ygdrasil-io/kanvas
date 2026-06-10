#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/caps-joins-micro-matrix"
OUTPUT_JSON = "kan-037-caps-joins-micro-matrix.json"
OUTPUT_MARKDOWN = "kan-037-caps-joins-micro-matrix.md"

KAN003_EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-003-caps-joins-aa/kan-003-caps-joins-aa.json"
KAN036_EVIDENCE_PATH = "reports/wgsl-pipeline/butt-stroke-non-hairline/kan-036-butt-stroke-non-hairline.json"
M60_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"
M60_ROUTE_CPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json"
M60_ROUTE_GPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
M60_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"
M60_RESIDUAL_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json"
M60_EXPERIMENTAL_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json"
M60_SKIA_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png"
M60_CPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu.png"
M60_CPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu-diff.png"
M60_GPU_EXPERIMENTAL_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png"
M60_GPU_EXPERIMENTAL_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png"
FOR266_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"
FOR267_PROBE_PATH = "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"
FOR266_REPORT_PATH = "reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md"
FOR267_REPORT_PATH = "reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"
KAN003_REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-003-caps-joins-aa.md"
CAPTURE_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
SELECTOR_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_DIAGNOSTICS_PATH = ".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"

SCENE_ID = "m60-bounded-stroke-cap-join"
FALLBACK_REASON = "coverage.stroke-cap-join-visual-parity-below-threshold"
REMAINING_BOUNDARY = "coverage.stroke-cap-join-aa-residual"
ROUND_BLOCKING_CONDITION = "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells"
SUPPORT_THRESHOLD = 99.95
EDGE_BUDGET = 256
PATH_VERB_BUDGET = 96
M60_STROKE_WIDTH = 10.0
M60_STROKE_MITER = 4.0


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-037 caps/joins micro-matrix validation failed: {message}")


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


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def m60_dashboard_row(root: Path) -> dict[str, Any]:
    pack = load_json(root, M60_PACK_PATH)
    scenes = require_list(pack, "scenes", M60_PACK_PATH)
    for scene in scenes:
        if isinstance(scene, dict) and scene.get("id") == SCENE_ID:
            return scene
    fail(f"{M60_PACK_PATH} missing {SCENE_ID}")


def residual_region(residual: dict[str, Any], region_id: str) -> dict[str, Any]:
    regions = require_list(residual, "regions", M60_RESIDUAL_PATH)
    for region in regions:
        if isinstance(region, dict) and region.get("id") == region_id:
            return region
    fail(f"{M60_RESIDUAL_PATH} missing region {region_id}")


def committed_artifacts() -> list[str]:
    return sorted(
        {
            KAN003_EVIDENCE_PATH,
            KAN036_EVIDENCE_PATH,
            M60_PACK_PATH,
            M60_ROUTE_CPU_PATH,
            M60_ROUTE_GPU_PATH,
            M60_STATS_PATH,
            M60_RESIDUAL_PATH,
            M60_EXPERIMENTAL_PATH,
            M60_SKIA_IMAGE_PATH,
            M60_CPU_IMAGE_PATH,
            M60_CPU_DIFF_PATH,
            M60_GPU_EXPERIMENTAL_IMAGE_PATH,
            M60_GPU_EXPERIMENTAL_DIFF_PATH,
            FOR266_PROBE_PATH,
            FOR267_PROBE_PATH,
            FOR266_REPORT_PATH,
            FOR267_REPORT_PATH,
            KAN003_REPORT_PATH,
            CAPTURE_TEST_PATH,
            SELECTOR_PATH,
            SPEC_REALTIME_PATH,
            SPEC_LOWERING_PATH,
            SPEC_DIAGNOSTICS_PATH,
        },
    )


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    kan003 = load_json(root, KAN003_EVIDENCE_PATH)
    kan036 = load_json(root, KAN036_EVIDENCE_PATH)
    route_cpu = load_json(root, M60_ROUTE_CPU_PATH)
    route_gpu = load_json(root, M60_ROUTE_GPU_PATH)
    stats = load_json(root, M60_STATS_PATH)
    residual = load_json(root, M60_RESIDUAL_PATH)
    experimental = load_json(root, M60_EXPERIMENTAL_PATH)
    for266 = load_json(root, FOR266_PROBE_PATH)
    for267 = load_json(root, FOR267_PROBE_PATH)
    dashboard_row = m60_dashboard_row(root)

    require(kan003.get("ticket") == "KAN-003", "KAN-003 evidence ticket changed")
    require(kan003.get("supportClaim") is False, "KAN-003 must not claim support")
    require(kan003.get("fallbackReason") == FALLBACK_REASON, "KAN-003 fallback changed")
    require(kan036.get("ticket") == "KAN-036", "KAN-036 evidence ticket changed")
    require(kan036.get("supportClaim") is False, "KAN-036 must not claim support")
    require(kan036.get("closureDecision") == "stable-refusal-existing-selector", "KAN-036 closure changed")

    require(route_cpu.get("sceneId") == SCENE_ID, "CPU route scene changed")
    require(route_cpu.get("status") == "pass", "CPU route status changed")
    require(route_cpu.get("selectedRoute") == "cpu.coverage.stroke-cap-join-oracle", "CPU route changed")
    require(route_cpu.get("fallbackReason") == "none", "CPU route fallback changed")
    require(route_cpu.get("coveragePlan") == "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,capJoinMatrix=butt-bevel+round-round+square-bevel)", "CPU coverage plan changed")
    require(route_cpu.get("pathVerbCount") == 9 and route_cpu.get("pathVerbBudget") == PATH_VERB_BUDGET, "CPU path verb diagnostics changed")
    require(route_cpu.get("edgeCount") == 18 and route_cpu.get("edgeBudget") == EDGE_BUDGET, "CPU edge diagnostics changed")
    require(route_cpu.get("strokeWidth") == M60_STROKE_WIDTH, "CPU stroke width changed")
    require(route_cpu.get("strokeCaps") == ["butt", "round", "square"], "CPU stroke caps changed")
    require(route_cpu.get("strokeJoins") == ["bevel", "round", "bevel"], "CPU stroke joins changed")
    require(route_cpu.get("dashIntervalCount") == 0 and route_cpu.get("dashIntervalBudget") == 8, "CPU dash diagnostics changed")

    require(route_gpu.get("sceneId") == SCENE_ID, "GPU route scene changed")
    require(route_gpu.get("status") == "expected-unsupported", "GPU route status changed")
    require(route_gpu.get("selectedRoute") == "webgpu.coverage.refuse", "GPU route changed")
    require(route_gpu.get("fallbackReason") == FALLBACK_REASON, "GPU fallback changed")
    require(route_gpu.get("remainingRootCause") == REMAINING_BOUNDARY, "GPU remaining boundary changed")
    require(route_gpu.get("pathVerbCount") == 9 and route_gpu.get("pathVerbBudget") == PATH_VERB_BUDGET, "GPU path verb diagnostics changed")
    require(route_gpu.get("edgeCount") == 18 and route_gpu.get("edgeBudget") == EDGE_BUDGET, "GPU edge diagnostics changed")
    require(route_gpu.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "GPU edge-budget reason changed")
    require(route_gpu.get("dashIntervalCount") == 0 and route_gpu.get("dashIntervalBudget") == 8, "GPU dash diagnostics changed")
    require("pathAaStrokeCapJoinBlocked" in str(route_gpu.get("pipelineKey", "")), "GPU pipeline key lost cap/join blocked axis")

    require(stats.get("sceneId") == SCENE_ID, "stats scene changed")
    require(stats.get("threshold") == SUPPORT_THRESHOLD, "support threshold changed")
    require(stats.get("experimentalGpuStatus") == "diagnostic-only", "experimental status changed")
    require(stats.get("experimentalGpuSimilarity") == 95.91, "experimental similarity changed")
    require(stats.get("dominantMismatchRegion") == "round-round", "dominant mismatch region changed")
    require(stats.get("fallbackReason") == FALLBACK_REASON, "stats fallback changed")
    require(stats.get("remainingRootCause") == REMAINING_BOUNDARY, "stats remaining root cause changed")

    require(residual.get("status") == "diagnostic-only", "residual status changed")
    require(residual.get("supportClaim") is False, "residual must not claim support")
    require(residual.get("remainingRootCause") == REMAINING_BOUNDARY, "residual remaining boundary changed")
    require(residual.get("greaterThanEightPixels") == 10, "residual >8 count changed")
    require(residual.get("greaterThanThirtyTwoPixels") == 6, "residual >32 count changed")

    round_region = residual_region(residual, "round-round")
    butt_region = residual_region(residual, "butt-bevel")
    square_region = residual_region(residual, "square-bevel")
    require(round_region.get("greaterThanEightPixels") == 8, "round-round >8 count changed")
    require(round_region.get("greaterThanThirtyTwoPixels") == 6, "round-round >32 count changed")
    require(round_region.get("maxChannelDelta") == 48, "round-round max delta changed")
    require(butt_region.get("greaterThanEightPixels") == 2, "butt-bevel >8 count changed")
    require(square_region.get("greaterThanEightPixels") == 0, "square-bevel >8 count changed")

    require(experimental.get("status") == "diagnostic-only", "experimental diagnostic status changed")
    require(experimental.get("supportClaim") is False, "experimental diagnostic must not claim support")
    require(experimental.get("fallbackReason") == FALLBACK_REASON, "experimental fallback changed")
    require(experimental.get("selectedRoute") == "webgpu.coverage.stroke-cap-join.experimental-render", "experimental route changed")

    require(for266.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-266 decision changed")
    require(for266.get("fallbackReason") == FALLBACK_REASON, "FOR-266 fallback changed")
    require(for266.get("remainingBoundary") == REMAINING_BOUNDARY, "FOR-266 boundary changed")

    require(for267.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-267 decision changed")
    require(for267.get("boundedCoverageCorrectionStatus") == "REFUSED", "FOR-267 correction status changed")
    require(for267.get("nextMissingCondition") == ROUND_BLOCKING_CONDITION, "FOR-267 missing condition changed")
    require(for267.get("remainingBoundary") == REMAINING_BOUNDARY, "FOR-267 boundary changed")
    require(for267.get("supportThreshold") == SUPPORT_THRESHOLD, "FOR-267 threshold changed")
    for267_stats = require_object(for267, "coverageStatistics", FOR267_PROBE_PATH)
    require(for267_stats.get("roundCapCellsObserved") is True, "FOR-267 round cap observation changed")
    require(for267_stats.get("roundJoinCellsObserved") is True, "FOR-267 round join observation changed")
    require(for267_stats.get("notEquivalentCells") == 2, "FOR-267 non-equivalent cells changed")
    require(for267_stats.get("safeBoundedCoverageCorrectionProven") is False, "FOR-267 correction proof changed")

    require(dashboard_row.get("status") == "expected-unsupported", "M60 dashboard row status changed")
    require(dashboard_row.get("fallbackReason") == FALLBACK_REASON, "M60 dashboard fallback changed")
    require(dashboard_row.get("remainingRootCause") == REMAINING_BOUNDARY, "M60 dashboard boundary changed")
    row_gpu = require_object(dashboard_row, "gpuRouteDetails", M60_PACK_PATH)
    require(row_gpu.get("edgeCount") == 18 and row_gpu.get("edgeBudget") == EDGE_BUDGET, "M60 dashboard edge diagnostics changed")
    require(row_gpu.get("strokeCaps") == ["butt", "round", "square"], "M60 dashboard caps changed")
    require(row_gpu.get("strokeJoins") == ["bevel", "round", "bevel"], "M60 dashboard joins changed")

    require_contains(root, CAPTURE_TEST_PATH, [
        "StrokeCase(0f, SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join",
        "StrokeCase(48f, SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join",
        "StrokeCase(96f, SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join",
        "strokeMiter = 4f",
        "StrokeRegion(\"butt-bevel\"",
        "StrokeRegion(\"round-round\"",
        "StrokeRegion(\"square-bevel\"",
    ])
    require_contains(root, SELECTOR_PATH, [
        "StandardCoverageReason.StrokeCapJoinVisualParityBelowThreshold",
        "pathAaStrokeCapJoinBlocked",
    ])
    require_contains(root, SPEC_REALTIME_PATH, [
        "common stroke caps: butt, round, square",
        "common joins: miter with limit, round, bevel",
        "no broad Path AA support claim from one bounded subset",
    ])
    require_contains(root, SPEC_LOWERING_PATH, [
        "Stroke width, cap, join, and miter limit are geometry facts",
        "Open-contour caps and closed-contour joins must be tested separately",
    ])
    require_contains(root, SPEC_DIAGNOSTICS_PATH, [
        "Every `Unsupported` plan has a code and action",
        "Unsupported coverage never produces silently wrong pixels",
    ])
    require_contains(root, FOR267_REPORT_PATH, [
        "Decision: `KEEP_DIAGNOSTIC`",
        ROUND_BLOCKING_CONDITION,
    ])
    require_contains(root, KAN003_REPORT_PATH, [
        "No stroke cap/join WebGPU support claim is added.",
        "No threshold is lowered.",
    ])

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    candidate = {
        "id": "round-round",
        "role": "candidate",
        "cap": "round",
        "join": "round",
        "miterLimit": M60_STROKE_MITER,
        "contourEvidence": "openPolyline-source-with-round-cap-and-round-join-boundary-cells",
        "status": "expected-unsupported",
        "decision": "stable-refusal",
        "fallbackReason": FALLBACK_REASON,
        "blockingCondition": ROUND_BLOCKING_CONDITION,
        "notEquivalentCells": for267_stats["notEquivalentCells"],
        "safeBoundedCoverageCorrectionProven": False,
        "residual": {
            "mismatchPixels": round_region["mismatchPixels"],
            "greaterThanEightPixels": round_region["greaterThanEightPixels"],
            "greaterThanThirtyTwoPixels": round_region["greaterThanThirtyTwoPixels"],
            "maxChannelDelta": round_region["maxChannelDelta"],
            "bounds": round_region["bounds"],
        },
    }
    sentinels = [
        {
            "id": "butt-bevel",
            "role": "sentinel",
            "cap": "butt",
            "join": "bevel",
            "miterLimit": M60_STROKE_MITER,
            "contourEvidence": "open-contour-cap-sentinel",
            "status": "expected-unsupported",
            "decision": "stable-refusal",
            "fallbackReason": FALLBACK_REASON,
            "residual": {
                "mismatchPixels": butt_region["mismatchPixels"],
                "greaterThanEightPixels": butt_region["greaterThanEightPixels"],
                "greaterThanThirtyTwoPixels": butt_region["greaterThanThirtyTwoPixels"],
                "maxChannelDelta": butt_region["maxChannelDelta"],
                "bounds": butt_region["bounds"],
            },
        },
        {
            "id": "square-bevel",
            "role": "sentinel",
            "cap": "square",
            "join": "bevel",
            "miterLimit": M60_STROKE_MITER,
            "contourEvidence": "out-of-scope-square-cap-sentinel",
            "status": "expected-unsupported",
            "decision": "stable-refusal",
            "fallbackReason": FALLBACK_REASON,
            "residual": {
                "mismatchPixels": square_region["mismatchPixels"],
                "greaterThanEightPixels": square_region["greaterThanEightPixels"],
                "greaterThanThirtyTwoPixels": square_region["greaterThanThirtyTwoPixels"],
                "maxChannelDelta": square_region["maxChannelDelta"],
                "bounds": square_region["bounds"],
            },
        },
    ]

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-037",
        "packId": "kan-037-caps-joins-micro-matrix-v1",
        "status": "pass",
        "closureDecision": "stable-refusal-micro-matrix",
        "claimLevel": "selected-cap-join-micro-matrix-stable-refusal",
        "supportClaim": False,
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "scene": {
            "sceneId": SCENE_ID,
            "source": "BoundedStrokeCapJoinGM",
            "coveragePlan": route_cpu["coveragePlan"],
            "strokeWidth": M60_STROKE_WIDTH,
            "strokeMiter": M60_STROKE_MITER,
            "supportThreshold": SUPPORT_THRESHOLD,
            "capJoinMatrix": ["butt-bevel", "round-round", "square-bevel"],
        },
        "candidate": candidate,
        "sentinels": sentinels,
        "webGpuRefusal": {
            "status": route_gpu["status"],
            "adapter": route_gpu.get("adapter"),
            "selectedRoute": route_gpu["selectedRoute"],
            "diagnosticRoute": experimental["selectedRoute"],
            "fallbackReason": route_gpu["fallbackReason"],
            "remainingRootCause": route_gpu["remainingRootCause"],
            "pathVerbCount": route_gpu["pathVerbCount"],
            "pathVerbBudget": route_gpu["pathVerbBudget"],
            "coverageEdgeCount": route_gpu["edgeCount"],
            "edgeBudget": route_gpu["edgeBudget"],
            "edgeBudgetReason": route_gpu["edgeBudgetReason"],
            "dashIntervalCount": route_gpu["dashIntervalCount"],
            "dashIntervalBudget": route_gpu["dashIntervalBudget"],
            "strokeWidth": route_gpu["strokeWidth"],
            "strokeCaps": route_gpu["strokeCaps"],
            "strokeJoins": route_gpu["strokeJoins"],
            "miterLimit": M60_STROKE_MITER,
            "transform": route_gpu.get("transform") or "n/a",
            "clipStackDepth": route_gpu.get("clipStackDepth") or "n/a",
            "deviceBounds": route_gpu["deviceBounds"],
            "pipelineKeyContains": "pathAaStrokeCapJoinBlocked",
        },
        "cpuEvidence": {
            "openContourCaps": {
                "available": True,
                "route": route_cpu["selectedRoute"],
                "coveragePlan": route_cpu["coveragePlan"],
                "capJoinMatrix": ["butt-bevel", "round-round", "square-bevel"],
            },
            "closedContourJoins": {
                "available": False,
                "classification": "support-blocker",
                "reason": "No separate closed-contour CPU join oracle artifact is present in the KAN-037 evidence set.",
                "requiredBeforeSupport": True,
            },
        },
        "artifactAvailability": {
            "skiaReference": {"available": True, "path": M60_SKIA_IMAGE_PATH},
            "cpuOracle": {"available": True, "path": M60_CPU_IMAGE_PATH},
            "cpuDiff": {"available": True, "path": M60_CPU_DIFF_PATH},
            "webGpuProductionRoute": {"available": True, "path": M60_ROUTE_GPU_PATH},
            "webGpuDiagnosticImage": {"available": True, "path": M60_GPU_EXPERIMENTAL_IMAGE_PATH},
            "webGpuDiagnosticDiff": {"available": True, "path": M60_GPU_EXPERIMENTAL_DIFF_PATH},
            "webGpuSupportImage": {"available": False, "reason": FALLBACK_REASON},
            "webGpuSupportDiff": {"available": False, "reason": FALLBACK_REASON},
        },
        "supportPolicy": {
            "rowStatus": "expected-unsupported",
            "decision": "stable-refusal",
            "requiredForSupport": [
                "closed-contour CPU join oracle evidence",
                "round-round CPU/GPU coverage equivalence for boundary cells",
                "production WebGPU route with fallbackReason=none",
                "WebGPU support image/diff/stat artifacts",
                "no threshold or edge-budget weakening",
            ],
        },
        "nonClaims": [
            "KAN-037 does not claim support for round-round, butt-bevel, square-bevel, or broad caps/joins.",
            "KAN-037 does not change renderer, shaders, selector, PipelineKey, threshold, or edge-budget behavior.",
            "KAN-037 does not lower the 99.95 support threshold or increase the 256 WebGPU AA edge budget.",
            "KAN-037 does not treat diagnostic-only WebGPU images as production support.",
            "KAN-037 does not satisfy closed-contour join CPU evidence; it records that gap as a support blocker.",
        ],
        "validationRows": [
            {
                "id": "candidate-round-round-stable-refusal",
                "status": "pass",
                "evidence": f"round-round remains {FALLBACK_REASON} with {ROUND_BLOCKING_CONDITION}.",
            },
            {
                "id": "sentinels-visible",
                "status": "pass",
                "evidence": "butt-bevel and square-bevel remain stable-refusal sentinels in the same M60 scene.",
            },
            {
                "id": "route-diagnostics-complete",
                "status": "pass",
                "evidence": "Route exposes cap/join/stroke width/miter/edge/dash/device bounds/pipeline key facts.",
            },
            {
                "id": "closed-contour-gap-explicit",
                "status": "pass",
                "evidence": "Closed-contour join CPU evidence is absent and therefore blocks support.",
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
    candidate = evidence["candidate"]
    refusal = evidence["webGpuRefusal"]
    sentinels = "\n".join(
        (
            f"| `{sentinel['id']}` | `{sentinel['cap']}` | `{sentinel['join']}` | "
            f"`{sentinel['decision']}` | `{sentinel['residual']['maxChannelDelta']}` |"
        )
        for sentinel in evidence["sentinels"]
    )
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    required = "\n".join(f"- {item}" for item in evidence["supportPolicy"]["requiredForSupport"])
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-037 Caps Joins Micro-Matrix

KAN-037 selects one cap/join candidate, keeps two sentinels visible, and closes
the slice as a stable refusal rather than support. The diagnostic-only WebGPU
render remains below the strict support policy because `round-round` boundary
coverage equivalence is not proven and closed-contour join CPU evidence is not
present.

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
| Cap | `{candidate['cap']}` |
| Join | `{candidate['join']}` |
| Miter limit | `{candidate['miterLimit']}` |
| Status | `{candidate['status']}` |
| Fallback | `{candidate['fallbackReason']}` |
| Blocking condition | `{candidate['blockingCondition']}` |
| Residual max delta | `{candidate['residual']['maxChannelDelta']}` |

## Sentinels

| Sentinel | Cap | Join | Decision | Max delta |
|---|---|---|---|---:|
{sentinels}

## WebGPU Refusal

| Fact | Value |
|---|---|
| Route | `{refusal['selectedRoute']}` |
| Diagnostic route | `{refusal['diagnosticRoute']}` |
| Fallback | `{refusal['fallbackReason']}` |
| Path verbs | `{refusal['pathVerbCount']}/{refusal['pathVerbBudget']}` |
| Coverage edges | `{refusal['coverageEdgeCount']}/{refusal['edgeBudget']}` |
| Dash intervals | `{refusal['dashIntervalCount']}/{refusal['dashIntervalBudget']}` |
| Stroke facts | width `{refusal['strokeWidth']}`, caps `{'+'.join(refusal['strokeCaps'])}`, joins `{'+'.join(refusal['strokeJoins'])}`, miter `{refusal['miterLimit']}` |
| Transform | `{refusal['transform']}` |
| Clip stack depth | `{refusal['clipStackDepth']}` |
| Pipeline key axis | `{refusal['pipelineKeyContains']}` |

## CPU Evidence Boundary

- Open-contour caps: `{evidence['cpuEvidence']['openContourCaps']['available']}` via `{evidence['cpuEvidence']['openContourCaps']['route']}`.
- Closed-contour joins: `{evidence['cpuEvidence']['closedContourJoins']['available']}`; classification `{evidence['cpuEvidence']['closedContourJoins']['classification']}`.

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
        "KAN-037 validation passed: round-round candidate and butt/square sentinels remain "
        f"{evidence['supportPolicy']['rowStatus']} via "
        f"{evidence['webGpuRefusal']['fallbackReason']} with "
        f"{evidence['webGpuRefusal']['coverageEdgeCount']}/{evidence['webGpuRefusal']['edgeBudget']} edges.",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
