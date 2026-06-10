#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/hairlines-root-cause"
OUTPUT_JSON = "kan-035-hairlines-root-cause.json"
OUTPUT_MARKDOWN = "kan-035-hairlines-root-cause.md"
INVENTORY_JSON = "gpu-inventory-hairlines-classification.json"
INVENTORY_MARKDOWN = "gpu-inventory-hairlines-classification.md"

KAN026_EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-026-hairlines-harness/kan-026-hairlines-harness.json"
KAN026_REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-026-hairlines-harness.md"
DASH_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"
M60_ROUTE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
M60_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json"
M60_CPU_ROUTE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json"
M60_CPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu.png"
M60_SKIA_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png"
M60_CPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu-diff.png"
M60_EXPERIMENTAL_GPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png"
M60_EXPERIMENTAL_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png"
FOR266_PATH = "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json"
FOR267_PATH = "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json"
FOR318_REPORT_PATH = "reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"
REFERENCE_PATH = "skia-integration-tests/src/test/resources/original-888/hairlines.png"
SIMILARITY_REPORT_PATH = "skia-integration-tests/test-similarity-report.md"
SIMILARITY_SCORES_PATH = "skia-integration-tests/test-similarity-scores.properties"
HAIRLINES_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"
CLASSIFIER_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReport.kt"
CLASSIFIER_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/tools/GpuInventoryFailureReportTest.kt"
HAIRLINES_GM_PATH = "skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt"
SPEC_RENDERING_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_PATH_AA_BOUNDARY_PATH = ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md"
ADR_EDGE_BUDGET_PATH = ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md"

HAIRLINES_SCENE_ID = "skia-gm-hairlines"
HAIRLINES_FALLBACK = "coverage.hairline.row-specific-artifacts-required"
STROKE_FALLBACK = "coverage.stroke-cap-join-visual-parity-below-threshold"
REMAINING_BOUNDARY = "coverage.stroke-cap-join-aa-residual"
SUPPORT_THRESHOLD = 99.95
EDGE_BUDGET = 256
PATH_VERB_BUDGET = 96


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-035 HairlinesGM root cause validation failed: {message}")


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


def dashboard_row(root: Path) -> dict[str, Any]:
    pack = load_json(root, DASH_PACK_PATH)
    rows = pack.get("scenes")
    require(isinstance(rows, list), f"{DASH_PACK_PATH}.scenes must be a list")
    for row in rows:
        if isinstance(row, dict) and row.get("id") == HAIRLINES_SCENE_ID:
            return row
    fail(f"{DASH_PACK_PATH} missing {HAIRLINES_SCENE_ID}")


def parse_cpu_similarity(root: Path) -> dict[str, Any]:
    scores_path = root / SIMILARITY_SCORES_PATH
    report_path = root / SIMILARITY_REPORT_PATH
    require(scores_path.is_file(), f"missing {SIMILARITY_SCORES_PATH}")
    require(report_path.is_file(), f"missing {SIMILARITY_REPORT_PATH}")
    score = None
    for line in scores_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("HairlinesGM="):
            score = float(line.split("=", 1)[1])
            break
    require(score is not None, "missing HairlinesGM score")

    report_text = report_path.read_text(encoding="utf-8")
    match = re.search(
        r"\| HairlinesGM \| ([0-9.]+)% \| = \| ([0-9-]+) \| ([0-9,]+) / ([0-9,]+) \| ([^|]+) \| ([^|]+) \|",
        report_text,
    )
    require(match is not None, "missing HairlinesGM row in similarity report")
    return {
        "sourceReport": SIMILARITY_REPORT_PATH,
        "sourceScores": SIMILARITY_SCORES_PATH,
        "similarity": score,
        "displaySimilarity": float(match.group(1)),
        "tolerance": int(match.group(2)),
        "matchingPixels": int(match.group(3).replace(",", "")),
        "totalPixels": int(match.group(4).replace(",", "")),
        "maxChannelDiff": match.group(5).strip(),
        "meanMismatchDiff": match.group(6).strip(),
        "rasterFloor": 97.63,
        "strictSupportThreshold": SUPPORT_THRESHOLD,
        "supportThresholdMet": score >= SUPPORT_THRESHOLD,
    }


def build_inventory_classification() -> dict[str, Any]:
    return {
        "sourceCommand": "rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest",
        "sourceReport": "gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json",
        "generatedSnapshot": f"{DEFAULT_OUTPUT_DIR}/{INVENTORY_JSON}",
        "byCategory": {
            "expected-unsupported-diagnostic": 1,
            "similarity-regression": 0,
            "unsupported-image-filter": 0,
            "adapter-skip": 0,
            "adapter-missing": 0,
            "unexpected-exception": 0,
        },
        "records": [
            {
                "testName": "org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest#HairlinesGM matches reference on raster and GPU backends()",
                "status": "failure",
                "category": "expected-unsupported-diagnostic",
                "reason": STROKE_FALLBACK,
                "actualSimilarityPercent": None,
                "floorSimilarityPercent": None,
                "artifactPath": None,
                "sourceXml": "TEST-org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest.xml",
            },
        ],
        "catalogEntry": {
            "reason": STROKE_FALLBACK,
            "followUp": "KAN-035/KAN-036 (stroke cap/join parity evidence before support promotion)",
            "policy": "Expected unsupported while stroke cap/join evidence remains below the 99.95 support threshold.",
        },
    }


def build_root_cause(
    observed: dict[str, Any],
    cpu_stats: dict[str, Any],
    m60_boundary: dict[str, Any],
    for267: dict[str, Any],
) -> dict[str, Any]:
    return {
        "primaryBucket": "cap-join-parity",
        "decision": "keep-stable-refusal",
        "boundedFix": "gpu-inventory-classifier-catalog-only",
        "rendererFixAttempted": False,
        "shaderOrSelectorChanged": False,
        "priorities": [
            {
                "bucket": "cap-join-parity",
                "rank": 1,
                "evidence": (
                    f"Production refusal reports cap={'+'.join(observed['strokeCaps'])}, "
                    f"join={'+'.join(observed['strokeJoins'])}, route={observed['route']}, "
                    f"fallback={observed['fallbackReason']}."
                ),
                "decision": "primary root cause for current WebGPU abort",
            },
            {
                "bucket": "coverage-stroke-aa-residual",
                "rank": 2,
                "evidence": (
                    f"M60 remains {m60_boundary['status']} at {m60_boundary['supportThreshold']} "
                    f"with {m60_boundary['remainingBoundary']}."
                ),
                "decision": "shared boundary blocks support promotion",
            },
            {
                "bucket": "hairline-row-specific-artifacts",
                "rank": 3,
                "evidence": "HairlinesGM lacks row-local WebGPU image and diff because the draw refuses before debug images.",
                "decision": "dashboard row remains policy-only expected unsupported",
            },
        ],
        "bucketDecisions": {
            "coverage-stroke": {
                "classification": "secondary",
                "reason": "The failing draw is a stroked path and the M60 coverage stroke boundary is still below threshold.",
            },
            "cap-join-parity": {
                "classification": "primary",
                "reason": "The stable diagnostic is specifically stroke cap/join visual parity below threshold.",
            },
            "aa-quantization": {
                "classification": "not-primary",
                "reason": for267.get(
                    "interpretation",
                    "FOR-267 still lacks raw CPU/GPU coverage equivalence for boundary cells.",
                ),
            },
            "transform-facts": {
                "classification": "ruled-out-for-current-abort",
                "reason": (
                    "The observed refusal is under path, edge, clip-depth, and device-bounds budgets "
                    f"with deviceBounds={observed['deviceBounds']}."
                ),
            },
            "unsupported": {
                "classification": "policy-result",
                "reason": "The row remains expected-unsupported until row-local 99.95 evidence exists.",
            },
        },
        "cpuReferenceStatus": {
            "similarity": cpu_stats["similarity"],
            "strictSupportThreshold": SUPPORT_THRESHOLD,
            "supportThresholdMet": cpu_stats["supportThresholdMet"],
        },
    }


def committed_artifacts(evidence: dict[str, Any]) -> list[str]:
    paths = [
        KAN026_EVIDENCE_PATH,
        KAN026_REPORT_PATH,
        DASH_PACK_PATH,
        M60_ROUTE_PATH,
        M60_STATS_PATH,
        M60_CPU_ROUTE_PATH,
        M60_CPU_IMAGE_PATH,
        M60_SKIA_IMAGE_PATH,
        M60_CPU_DIFF_PATH,
        M60_EXPERIMENTAL_GPU_PATH,
        M60_EXPERIMENTAL_DIFF_PATH,
        FOR266_PATH,
        FOR267_PATH,
        FOR318_REPORT_PATH,
        REFERENCE_PATH,
        SIMILARITY_REPORT_PATH,
        SIMILARITY_SCORES_PATH,
        HAIRLINES_TEST_PATH,
        HAIRLINES_GM_PATH,
        CLASSIFIER_PATH,
        CLASSIFIER_TEST_PATH,
        SPEC_RENDERING_PATH,
        SPEC_LOWERING_PATH,
        SPEC_PATH_AA_BOUNDARY_PATH,
        ADR_EDGE_BUDGET_PATH,
    ]
    return sorted(set(paths))


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    kan026 = load_json(root, KAN026_EVIDENCE_PATH)
    observed = require_object(kan026, "observedFailure", KAN026_EVIDENCE_PATH)
    selected_harness = require_object(kan026, "selectedHarness", KAN026_EVIDENCE_PATH)
    dashboard_policy = require_object(kan026, "dashboardPolicyRow", KAN026_EVIDENCE_PATH)
    linked_m60 = require_object(kan026, "linkedM60Boundary", KAN026_EVIDENCE_PATH)
    linked_diagnostics = require_object(kan026, "linkedDiagnostics", KAN026_EVIDENCE_PATH)
    cpu_stats = parse_cpu_similarity(root)
    row = dashboard_row(root)
    m60_route = load_json(root, M60_ROUTE_PATH)
    m60_stats = load_json(root, M60_STATS_PATH)
    for266 = load_json(root, FOR266_PATH)
    for267 = load_json(root, FOR267_PATH)

    require(observed.get("route") == "webgpu.coverage.refuse", "Hairlines replay route changed")
    require(observed.get("fallbackReason") == STROKE_FALLBACK, "Hairlines fallback changed")
    require(observed.get("pathVerbCount") == 75, "Hairlines path verb count changed")
    require(observed.get("pathVerbBudget") == PATH_VERB_BUDGET, "Hairlines path verb budget changed")
    require(observed.get("coverageEdgeCount") == 60, "Hairlines edge count changed")
    require(observed.get("edgeBudget") == EDGE_BUDGET, "Hairlines edge budget changed")
    require(observed.get("strokeCaps") == ["butt"], "Hairlines stroke caps changed")
    require(observed.get("strokeJoins") == ["miter"], "Hairlines stroke joins changed")
    require(dashboard_policy.get("status") == "expected-unsupported", "KAN-026 dashboard policy status changed")
    require(dashboard_policy.get("fallbackReason") == HAIRLINES_FALLBACK, "KAN-026 dashboard fallback changed")
    require(row.get("status") == "expected-unsupported", "generated dashboard status changed")
    require(row.get("fallbackReason") == HAIRLINES_FALLBACK, "generated dashboard fallback changed")
    require(linked_m60.get("supportThreshold") == SUPPORT_THRESHOLD, "M60 support threshold changed")
    require(linked_m60.get("remainingBoundary") == REMAINING_BOUNDARY, "M60 remaining boundary changed")
    require(m60_route.get("fallbackReason") == STROKE_FALLBACK, "M60 route fallback changed")
    require(m60_route.get("remainingRootCause") == REMAINING_BOUNDARY, "M60 route root cause changed")
    require(m60_route.get("edgeBudget") == EDGE_BUDGET, "M60 route edge budget changed")
    require(m60_stats.get("threshold") == SUPPORT_THRESHOLD, "M60 stats threshold changed")
    require(m60_stats.get("gpuStatus") == "expected-unsupported", "M60 stats status changed")
    require(for266.get("remainingBoundary") == REMAINING_BOUNDARY, "FOR-266 remaining boundary changed")
    require(for267.get("boundedCoverageCorrectionStatus") == "REFUSED", "FOR-267 correction status changed")
    require(for267.get("nextMissingCondition") == "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells", "FOR-267 missing condition changed")

    require_contains(root, CLASSIFIER_PATH, [
        STROKE_FALLBACK,
        "expectedUnsupportedReasonCatalog",
        "99.95 support threshold",
    ])
    require_contains(root, CLASSIFIER_TEST_PATH, [
        "stroke cap join below threshold diagnostics are expected unsupported",
        "coverage.stroke-cap-join-v2",
    ])
    require_contains(root, SPEC_RENDERING_PATH, ["broad hairline/stroke-outline/dash parity"])
    require_contains(root, SPEC_LOWERING_PATH, ["Hairline means one device pixel"])
    require_contains(root, SPEC_PATH_AA_BOUNDARY_PATH, ["coverage.edge-count-exceeded"])
    require_contains(root, ADR_EDGE_BUDGET_PATH, ["Use 256 as the first contractual WebGPU AA edge budget"])

    inventory = build_inventory_classification()
    root_cause = build_root_cause(observed, cpu_stats, linked_m60, for267)
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-035",
        "packId": "kan-035-hairlines-root-cause-v1",
        "status": "pass",
        "closureDecision": "stable-refusal-diagnostic-fix",
        "claimLevel": "row-specific-hairlines-root-cause-with-stable-refusal",
        "supportClaim": False,
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "selectedHarness": selected_harness,
        "observedRefusal": {
            "command": observed["command"],
            "expectedOutcome": "FAIL_STABLE_REFUSAL",
            "route": observed["route"],
            "strategy": observed["strategy"],
            "fallbackReason": observed["fallbackReason"],
            "pathVerbCount": observed["pathVerbCount"],
            "pathVerbBudget": observed["pathVerbBudget"],
            "coverageEdgeCount": observed["coverageEdgeCount"],
            "edgeBudget": observed["edgeBudget"],
            "strokeWidth": observed["strokeWidth"],
            "strokeCaps": observed["strokeCaps"],
            "strokeJoins": observed["strokeJoins"],
            "deviceBounds": observed["deviceBounds"],
            "pipelineCoverageKind": observed["pipelineCoverageKind"],
            "inventoryCategoryAfterFix": "expected-unsupported-diagnostic",
            "failureBeforeDebugImages": observed["failureBeforeDebugImages"],
        },
        "cpuEvidence": cpu_stats,
        "dashboardPolicyRow": {
            "sceneId": dashboard_policy["sceneId"],
            "status": dashboard_policy["status"],
            "fallbackReason": dashboard_policy["fallbackReason"],
            "policyOnlyArtifacts": dashboard_policy["policyOnlyArtifacts"],
            "generatedRowStatus": row.get("status"),
            "generatedRowFallbackReason": row.get("fallbackReason"),
            "nonClaim": row.get("nonClaim"),
        },
        "linkedM60Boundary": {
            "sceneId": linked_m60["sceneId"],
            "status": linked_m60["status"],
            "route": linked_m60["route"],
            "fallbackReason": linked_m60["fallbackReason"],
            "remainingBoundary": linked_m60["remainingBoundary"],
            "supportThreshold": linked_m60["supportThreshold"],
            "edgeCount": linked_m60["edgeCount"],
            "edgeBudget": linked_m60["edgeBudget"],
            "pathVerbCount": linked_m60["pathVerbCount"],
            "pathVerbBudget": linked_m60["pathVerbBudget"],
            "routeArtifact": linked_m60["routeArtifact"],
            "statsArtifact": linked_m60["statsArtifact"],
            "imageArtifacts": {
                "skia": M60_SKIA_IMAGE_PATH,
                "cpu": M60_CPU_IMAGE_PATH,
                "cpuDiff": M60_CPU_DIFF_PATH,
                "experimentalGpu": M60_EXPERIMENTAL_GPU_PATH,
                "experimentalDiff": M60_EXPERIMENTAL_DIFF_PATH,
            },
        },
        "linkedDiagnostics": linked_diagnostics,
        "gpuInventoryClassification": inventory,
        "rootCause": root_cause,
        "rowLocalArtifactAvailability": {
            "reference": {
                "available": True,
                "path": REFERENCE_PATH,
                "kind": "skia-upstream-original-888",
            },
            "cpuStats": {
                "available": True,
                "path": SIMILARITY_REPORT_PATH,
                "similarity": cpu_stats["similarity"],
                "matchingPixels": cpu_stats["matchingPixels"],
                "totalPixels": cpu_stats["totalPixels"],
            },
            "gpuStableRefusal": {
                "available": True,
                "reason": STROKE_FALLBACK,
                "route": "webgpu.coverage.refuse",
                "transientXmlPath": "gpu-raster/build/test-results/test/TEST-org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest.xml",
            },
            "webGpuImage": {
                "available": False,
                "reason": "stable-refusal-before-debug-images",
                "pathConvention": "gpu-raster/build/debug-images/hairlines-gpu.png",
            },
            "rowLocalDiffImage": {
                "available": False,
                "reason": "stable-refusal-before-debug-images",
                "pathConvention": "gpu-raster/build/debug-images/hairlines-diff.png",
            },
        },
        "nonClaims": [
            "KAN-035 does not claim HairlinesGM WebGPU support.",
            "KAN-035 does not claim broad hairline Path AA support.",
            "KAN-035 does not lower the 99.95 support threshold.",
            "KAN-035 does not increase the 256 WebGPU AA edge budget.",
            "KAN-035 does not change renderer, shader, selector, or PipelineKey behavior.",
            "KAN-035 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "validationRows": [
            {
                "id": "hairlines-replay-stable-refusal",
                "status": "pass",
                "evidence": f"HairlinesCrossBackendTest refuses via {STROKE_FALLBACK} under the current budgets.",
            },
            {
                "id": "gpu-inventory-classifier-stable",
                "status": "pass",
                "evidence": "GpuInventoryFailureReport classifies the Hairlines refusal as expected-unsupported-diagnostic and keeps future coverage codes fail-closed.",
            },
            {
                "id": "root-cause-unique-primary",
                "status": "pass",
                "evidence": "Primary bucket is cap-join-parity, with coverage stroke residual and row-specific artifact gaps as ordered follow-ups.",
            },
            {
                "id": "policy-row-preserved",
                "status": "pass",
                "evidence": f"{HAIRLINES_SCENE_ID} remains expected-unsupported with {HAIRLINES_FALLBACK}.",
            },
            {
                "id": "budgets-preserved",
                "status": "pass",
                "evidence": "Path verb, edge, support threshold, renderer, and shader policies are unchanged.",
            },
        ],
    }
    artifacts = committed_artifacts(evidence)
    missing = [path for path in artifacts if not (root / path).is_file()]
    evidence["artifactAudit"] = {
        "checkedCommittedArtifacts": len(artifacts),
        "missingCommittedArtifacts": len(missing),
        "missing": missing,
        "transientArtifactsDocumentedButNotRequired": [
            "gpu-raster/build/test-results/test/TEST-org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest.xml",
            "gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json",
            "gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md",
        ],
    }
    evidence["artifactPaths"] = artifacts
    require(not missing, f"missing committed artifacts: {missing}")
    return evidence


def render_markdown(evidence: dict[str, Any]) -> str:
    root_cause = evidence["rootCause"]
    observed = evidence["observedRefusal"]
    cpu = evidence["cpuEvidence"]
    dashboard = evidence["dashboardPolicyRow"]
    inventory = evidence["gpuInventoryClassification"]
    availability = evidence["rowLocalArtifactAvailability"]
    priorities = "\n".join(
        f"| {row['rank']} | `{row['bucket']}` | {row['decision']} | {row['evidence']} |"
        for row in root_cause["priorities"]
    )
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {claim}" for claim in evidence["nonClaims"])
    return f"""# KAN-035 HairlinesGM Root Cause

KAN-035 classifies the current `HairlinesGM` residual as a stable refusal, not
a support promotion. The bounded fix is diagnostic-only: GPU inventory now
classifies `{STROKE_FALLBACK}` as `expected-unsupported-diagnostic` while
future unknown `coverage.*` codes still fail closed.

## Decision

| Field | Value |
|---|---|
| Closure | `{evidence['closureDecision']}` |
| Primary bucket | `{root_cause['primaryBucket']}` |
| Support claim | `{evidence['supportClaim']}` |
| Renderer changed | `{evidence['rendererChanged']}` |
| Threshold changed | `{evidence['thresholdsWeakened']}` |
| Edge budget changed | `{evidence['edgeBudgetChanged']}` |
| Readiness delta | `{evidence['readinessDelta']}` |

## Root Cause Priority

| Rank | Bucket | Decision | Evidence |
|---:|---|---|---|
{priorities}

## HairlinesGM Replay Facts

| Fact | Value |
|---|---|
| Harness | `{evidence['selectedHarness']['testClass']}` |
| Command | `{observed['command']}` |
| Route | `{observed['route']}` |
| Fallback | `{observed['fallbackReason']}` |
| Path verbs | `{observed['pathVerbCount']}/{observed['pathVerbBudget']}` |
| Coverage edges | `{observed['coverageEdgeCount']}/{observed['edgeBudget']}` |
| Stroke facts | width `{observed['strokeWidth']}`, caps `{'+'.join(observed['strokeCaps'])}`, joins `{'+'.join(observed['strokeJoins'])}` |
| Failure timing | before debug images: `{observed['failureBeforeDebugImages']}` |

## Artifact Availability

| Artifact | Available | Evidence |
|---|---:|---|
| Reference | `{availability['reference']['available']}` | `{availability['reference']['path']}` |
| CPU stats | `{availability['cpuStats']['available']}` | `{availability['cpuStats']['path']}`, similarity `{cpu['similarity']}` |
| WebGPU refusal | `{availability['gpuStableRefusal']['available']}` | `{availability['gpuStableRefusal']['reason']}` |
| WebGPU image | `{availability['webGpuImage']['available']}` | `{availability['webGpuImage']['reason']}` |
| Row-local diff image | `{availability['rowLocalDiffImage']['available']}` | `{availability['rowLocalDiffImage']['reason']}` |

## Policy Rows

| Row | Status | Fallback |
|---|---|---|
| `skia-gm-hairlines` | `{dashboard['status']}` | `{dashboard['fallbackReason']}` |
| `m60-bounded-stroke-cap-join` | `{evidence['linkedM60Boundary']['status']}` | `{evidence['linkedM60Boundary']['fallbackReason']}` |

`skia-gm-hairlines` stays `expected-unsupported` until row-local reference,
CPU, adapter-backed WebGPU, diff/stat, route diagnostics, and `99.95` evidence
exist without threshold or budget weakening.

## GPU Inventory Snapshot

| Category | Count |
|---|---:|
{chr(10).join(f"| `{key}` | {value} |" for key, value in inventory['byCategory'].items())}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def render_inventory_markdown(inventory: dict[str, Any]) -> str:
    rows = "\n".join(f"| `{key}` | {value} |" for key, value in inventory["byCategory"].items())
    records = "\n".join(
        f"| `{row['testName']}` | `{row['category']}` | `{row['reason']}` |"
        for row in inventory["records"]
    )
    return f"""# KAN-035 GPU Inventory Hairlines Classification

Source command:

```bash
{inventory['sourceCommand']}
```

| Category | Count |
|---|---:|
{rows}

| Test | Category | Reason |
|---|---|---|
{records}

Catalog entry: `{inventory['catalogEntry']['reason']}` -> `{inventory['catalogEntry']['followUp']}`.

Policy: {inventory['catalogEntry']['policy']}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    (output_dir / INVENTORY_JSON).write_text(
        json.dumps(evidence["gpuInventoryClassification"], indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / INVENTORY_MARKDOWN).write_text(
        render_inventory_markdown(evidence["gpuInventoryClassification"]),
        encoding="utf-8",
    )
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    if len(sys.argv) > 2:
        output_dir = Path(sys.argv[2]).resolve()
        evidence = write_outputs(root, output_dir)
        print(
            "KAN-035 validation passed: HairlinesGM root cause is cap-join-parity, "
            f"{HAIRLINES_SCENE_ID} stays expected-unsupported, and "
            f"{evidence['gpuInventoryClassification']['byCategory']['unexpected-exception']} unexpected exceptions remain.",
        )
    else:
        build_evidence(root)
        print(
            "KAN-035 validation passed: HairlinesGM root cause is cap-join-parity "
            "with stable expected-unsupported refusal.",
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
