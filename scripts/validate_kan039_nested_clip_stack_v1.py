#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/nested-clip-stack-v1"
OUTPUT_JSON = "kan-039-nested-clip-stack-v1.json"
OUTPUT_MARKDOWN = "kan-039-nested-clip-stack-v1.md"

KAN004_EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"
KAN004_REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-004-clips-aa.md"
M57_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"
M60_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"
M60_REPORT_PATH = "reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"
M60_ROUTE_CPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/route-cpu.json"
M60_ROUTE_GPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/route-gpu.json"
M60_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/stats.json"
M60_SKIA_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/skia.png"
M60_CPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu.png"
M60_CPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu-diff.png"
M60_GPU_IMAGE_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/gpu.png"
M60_GPU_DIFF_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/gpu-diff.png"
M57_ROUTE_GPU_PATH = "reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/route-gpu.json"
M57_STATS_PATH = "reports/wgsl-pipeline/scenes/artifacts/m57-aaclip-bounded-grid/stats.json"
FOR301_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-skaaclip-band-trace-for301.json"
FOR301_REPORT_PATH = "reports/wgsl-pipeline/2026-06-04-for-301-m60-skaaclip-band-trace.md"
FOR302_PATH = "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-analytic-clip-model-reconciliation-for302.json"
FOR304_REPORT_PATH = "reports/wgsl-pipeline/2026-06-04-for-304-renderer-feature-conversion-wave-closeout.md"
NESTED_CAPTURE_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/NestedClipSceneCaptureTest.kt"
CONTRACTS_PATH = "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
CONTRACTS_TEST_PATH = "render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"
SELECTOR_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt"
SELECTOR_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt"
SPEC_CONTRACTS_PATH = ".upstream/specs/geometry-coverage/01-contracts-geometry-coverage.md"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_WEBGPU_PATH = ".upstream/specs/geometry-coverage/04-webgpu-coverage-backend.md"
SPEC_FALLBACKS_PATH = ".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"

M57_SCENE_ID = "m57-aaclip-bounded-grid"
M60_SCENE_ID = "m60-bounded-nested-rrect-clip"
NESTED_FALLBACK = "coverage.nested-clip-visual-parity-below-threshold"
ARBITRARY_AA_CLIP_FALLBACK = "coverage.arbitrary-aa-clip-unsupported"
CLIP_STACK_FALLBACK = "geometry.clip-stack-unsupported"
EDGE_BUDGET = 256
CLIP_DEPTH_BUDGET = 4
SUPPORT_THRESHOLD = 99.95


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-039 nested clip-stack V1 validation failed: {message}")


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


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


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
            KAN004_EVIDENCE_PATH,
            KAN004_REPORT_PATH,
            M57_PACK_PATH,
            M60_PACK_PATH,
            M60_REPORT_PATH,
            M60_ROUTE_CPU_PATH,
            M60_ROUTE_GPU_PATH,
            M60_STATS_PATH,
            M60_SKIA_IMAGE_PATH,
            M60_CPU_IMAGE_PATH,
            M60_CPU_DIFF_PATH,
            M60_GPU_IMAGE_PATH,
            M60_GPU_DIFF_PATH,
            M57_ROUTE_GPU_PATH,
            M57_STATS_PATH,
            FOR301_PATH,
            FOR301_REPORT_PATH,
            FOR302_PATH,
            FOR304_REPORT_PATH,
            NESTED_CAPTURE_TEST_PATH,
            CONTRACTS_PATH,
            CONTRACTS_TEST_PATH,
            SELECTOR_PATH,
            SELECTOR_TEST_PATH,
            SPEC_CONTRACTS_PATH,
            SPEC_LOWERING_PATH,
            SPEC_WEBGPU_PATH,
            SPEC_FALLBACKS_PATH,
        },
    )


def clip_sequence() -> list[dict[str, Any]]:
    return [
        {
            "index": 0,
            "type": "rect",
            "op": "intersect",
            "interaction": "ClipInteraction.DeviceRect-or-AnalyticShape",
            "rawPaintClipOperationExposed": False,
        },
        {
            "index": 1,
            "type": "rect",
            "op": "intersect",
            "interaction": "ClipInteraction.DeviceRect-or-AnalyticShape",
            "rawPaintClipOperationExposed": False,
        },
        {
            "index": 2,
            "type": "rrect-oval",
            "op": "difference",
            "interaction": "ClipInteraction.AaClip",
            "rawPaintClipOperationExposed": False,
        },
    ]


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    kan004 = load_json(root, KAN004_EVIDENCE_PATH)
    m57_row = scene_row(root, M57_PACK_PATH, M57_SCENE_ID)
    m60_row = scene_row(root, M60_PACK_PATH, M60_SCENE_ID)
    route_cpu = load_json(root, M60_ROUTE_CPU_PATH)
    route_gpu = load_json(root, M60_ROUTE_GPU_PATH)
    stats = load_json(root, M60_STATS_PATH)
    m57_route_gpu = load_json(root, M57_ROUTE_GPU_PATH)
    m57_stats = load_json(root, M57_STATS_PATH)
    for301 = load_json(root, FOR301_PATH)
    for302 = load_json(root, FOR302_PATH)

    require(kan004.get("ticket") == "KAN-004", "KAN-004 evidence ticket changed")
    require(kan004.get("supportClaim") == "m57-aaclip-bounded-grid-only", "KAN-004 support claim changed")
    require(kan004.get("broadClipStackSupportClaim") is False, "KAN-004 broad clip-stack claim changed")
    kan004_support = require_object(kan004, "supportScene", KAN004_EVIDENCE_PATH)
    kan004_nested = require_object(kan004, "nestedClipBoundary", KAN004_EVIDENCE_PATH)
    kan004_policy = require_object(kan004, "clipPolicy", KAN004_EVIDENCE_PATH)
    require(kan004_support.get("sceneId") == M57_SCENE_ID, "KAN-004 support scene changed")
    require(kan004_support.get("gpuStatus") == "pass", "M57 GPU status changed")
    require(kan004_support.get("gpuFallbackReason") == "none", "M57 fallback changed")
    require(kan004_nested.get("sceneId") == M60_SCENE_ID, "KAN-004 nested scene changed")
    require(kan004_nested.get("gpuFallbackReason") == NESTED_FALLBACK, "KAN-004 nested fallback changed")
    require(kan004_policy.get("noIntegerScissorSubstitution") is True, "integer scissor policy changed")
    require(kan004_policy.get("arbitraryAaClipFallback") == ARBITRARY_AA_CLIP_FALLBACK, "arbitrary AA clip fallback changed")
    require(kan004_policy.get("clipStackFallback") == CLIP_STACK_FALLBACK, "clip stack fallback changed")

    require(m57_row.get("status") == "pass", "M57 row status changed")
    require(m57_row.get("fallbackReason") == "none", "M57 row fallback changed")
    require(m57_row.get("gpuRoute") == "webgpu.coverage.aaclip-bounded-grid", "M57 GPU route changed")
    require("feature.clip" in m57_row.get("tags", []), "M57 row lost feature.clip tag")
    require(m57_route_gpu.get("fallbackReason") == "none", "M57 route fallback changed")
    require(m57_route_gpu.get("selectedRoute") == "webgpu.coverage.aaclip-bounded-grid", "M57 selected route changed")
    require(m57_stats.get("gpuStatus") == "pass", "M57 stats status changed")

    require(m60_row.get("status") == "expected-unsupported", "M60 row status changed")
    require(m60_row.get("fallbackReason") == NESTED_FALLBACK, "M60 row fallback changed")
    require(m60_row.get("gpuRoute") == "webgpu.coverage.nested-rrect-clip.expected-unsupported", "M60 row GPU route changed")
    require("feature.clip.nested" in m60_row.get("tags", []), "M60 row lost nested clip tag")

    for source, route in [(M60_ROUTE_CPU_PATH, route_cpu), (M60_ROUTE_GPU_PATH, route_gpu)]:
        require(route.get("sceneId") == M60_SCENE_ID, f"{source} scene changed")
        require(route.get("clipDepth") == 3, f"{source} clip depth changed")
        require(route.get("clipDepthBudget") == CLIP_DEPTH_BUDGET, f"{source} clip depth budget changed")
        require(route.get("clipDepthReason") == "not coverage.clip-depth-exceeded", f"{source} clip depth reason changed")
        require(route.get("edgeCount") == 72, f"{source} edge count changed")
        require(route.get("edgeBudget") == EDGE_BUDGET, f"{source} edge budget changed")
        require(route.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", f"{source} edge budget reason changed")
        require(route.get("clipOp") == "intersect+intersect+difference", f"{source} clip op changed")
        require(route.get("clipShape") == "rect+rect+rrect-oval", f"{source} clip shape changed")
        require(route.get("nestedClip") is True, f"{source} nested flag changed")
        require(route.get("inverseClip") is False, f"{source} inverse flag changed")
        require(route.get("complexClip") is False, f"{source} complex flag changed")

    require(route_cpu.get("backend") == "CPU", "CPU route backend changed")
    require(route_cpu.get("status") == "pass", "CPU route status changed")
    require(route_cpu.get("selectedRoute") == "cpu.coverage.nested-rrect-clip-oracle", "CPU route changed")
    require(route_cpu.get("fallbackReason") == "none", "CPU fallback changed")
    require("clipRect+clipRect+clipRRectDifference" in str(route_cpu.get("coveragePlan")), "CPU coverage plan changed")
    require(route_gpu.get("backend") == "WebGPU", "GPU route backend changed")
    require(route_gpu.get("status") == "expected-unsupported", "GPU route status changed")
    require(route_gpu.get("fallbackReason") == NESTED_FALLBACK, "GPU fallback changed")
    require("not a WebGPU selector route dump" in str(route_gpu.get("diagnosticsSource")), "GPU diagnostic source changed")
    require("clipDepth=3" in str(route_gpu.get("pipelineKey")), "GPU pipeline key lost clipDepth")

    require(stats.get("sceneId") == M60_SCENE_ID, "stats scene changed")
    require(stats.get("gpuStatus") == "expected-unsupported", "stats GPU status changed")
    require(stats.get("fallbackReason") == NESTED_FALLBACK, "stats fallback changed")
    require(stats.get("threshold") == SUPPORT_THRESHOLD, "support threshold changed")
    require(float(stats.get("gpuSimilarity")) == 98.48, "GPU similarity changed")
    require(float(stats.get("gpuSimilarity")) < SUPPORT_THRESHOLD, "GPU similarity unexpectedly reached support")
    require(float(stats.get("cpuSimilarity")) == 97.31, "CPU similarity changed")

    require(for301.get("supportDecision") == "KEEP_EXPECTED_UNSUPPORTED", "FOR-301 support decision changed")
    require(for301.get("decision") == "SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE", "FOR-301 decision changed")
    details301 = require_object(for301, "decisionDetails", FOR301_PATH)
    require(details301.get("differenceFormulaMatches") is True, "FOR-301 formula proof changed")
    require(details301.get("safeLocalFixApplied") is False, "FOR-301 safe fix changed")
    route301 = require_object(for301, "route", FOR301_PATH)
    require(route301.get("fallbackReason") == NESTED_FALLBACK, "FOR-301 fallback changed")

    require(for302.get("supportDecision") == "KEEP_EXPECTED_UNSUPPORTED", "FOR-302 support decision changed")
    require(for302.get("decision") == "M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT", "FOR-302 decision changed")
    decisions302 = require_object(for302, "decisions", FOR302_PATH)
    require(decisions302.get("M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT") is True, "FOR-302 reconciliation changed")
    require(decisions302.get("M60_ANALYTIC_RUNTIME_CONTRADICTION_STILL_AMBIGUOUS") is False, "FOR-302 ambiguity changed")
    route302 = require_object(for302, "route", FOR302_PATH)
    require(route302.get("fallbackReason") == NESTED_FALLBACK, "FOR-302 fallback changed")

    require_contains(root, NESTED_CAPTURE_TEST_PATH, [
        "bounded nested clip captures expected unsupported WebGPU evidence",
        "clipRect+clipRect+clipRRectDifference",
        "clipDepth\": 3",
        "clipOp\": \"intersect+intersect+difference\"",
        "coverage.nested-clip-visual-parity-below-threshold",
    ])
    require_contains(root, CONTRACTS_PATH, [
        "sealed interface ClipInteraction",
        "data class DeviceRect",
        "data class AnalyticShape",
        "data class AaClip",
        "data class ShaderClip",
        "data class Unsupported",
        "ClipStackBreadthMatrix",
        "multi-shape-aa-difference",
        "shader-clip",
        "unlowerable-stack",
    ])
    require_contains(root, CONTRACTS_TEST_PATH, [
        'assertEquals("geometry.clip-stack-unsupported", StandardGeometryReason.ClipStackUnsupported.code)',
        '"coverage.arbitrary-aa-clip-unsupported"',
        "StandardCoverageReason.ArbitraryAaClipUnsupported.code",
    ])
    require_contains(root, SELECTOR_PATH, [
        "unsupportedClipReason",
        "ClipInteraction.AaClip",
        "ClipInteraction.ShaderClip",
        "StandardCoverageReason.ArbitraryAaClipUnsupported",
    ])
    require_contains(root, SELECTOR_TEST_PATH, [
        "arbitrary aa clip emits stable gpu diagnostic",
        "clip stack breadth matrix maps webgpu support and refusal diagnostics",
        "coverage.arbitrary-aa-clip-unsupported",
    ])
    require_contains(root, SPEC_CONTRACTS_PATH, [
        "Clip-stack lowering happens before `GeometryPlan`",
        "Paint receives coverage/clip modulation, not raw clip-stack operations",
    ])
    require_contains(root, SPEC_LOWERING_PATH, [
        "lower clip stack to ClipInteraction",
        "Difference clips are represented in `ClipInteraction`, not in paint.",
        "Multiple curved clip shapes are unsupported on WebGPU until a list/atlas path is specified.",
    ])
    require_contains(root, SPEC_WEBGPU_PATH, [
        "Arbitrary clip masks require an explicit mask/atlas strategy.",
        "Stable diagnostic; no silent scissor-only approximation",
        "Every unsupported strategy has a stable diagnostic.",
    ])
    require_contains(root, SPEC_FALLBACKS_PATH, [
        "geometry.clip-stack-unsupported",
        "coverage.arbitrary-aa-clip-unsupported",
        "Must not silently replace arbitrary clip with integer scissor",
    ])
    require_contains(root, M60_REPORT_PATH, [
        "`m60-bounded-nested-rrect-clip`",
        "clipDepth=3",
        "edgeCount=72",
        "FOR-9 therefore does not claim `fallbackReason=none`",
    ])
    require_contains(root, FOR301_REPORT_PATH, [
        "Decision: `SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE`",
        "M60 remains `expected-unsupported`",
    ])
    require_contains(root, FOR304_REPORT_PATH, [
        "`coverage.nested-clip-visual-parity-below-threshold`",
        "Only reopen if a new renderer-side causal hypothesis is available",
    ])
    require_contains(root, KAN004_REPORT_PATH, [
        "`m57-aaclip-bounded-grid`",
        "`m60-bounded-nested-rrect-clip`",
        "No integer scissor substitution is introduced.",
    ])

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    candidate = {
        "sceneId": M60_SCENE_ID,
        "role": "bounded-nested-clip-candidate",
        "source": "BlurredClippedCircleGM",
        "referenceKind": m60_row["referenceKind"],
        "status": route_gpu["status"],
        "decision": "stable-refusal",
        "fallbackReason": route_gpu["fallbackReason"],
        "cpuStatus": route_cpu["status"],
        "gpuStatus": route_gpu["status"],
        "cpuRoute": route_cpu["selectedRoute"],
        "gpuRoute": route_gpu["selectedRoute"],
        "coverageStrategy": route_gpu["coverageStrategy"],
        "pipelineKey": route_gpu["pipelineKey"],
        "clipDepth": route_gpu["clipDepth"],
        "clipDepthBudget": route_gpu["clipDepthBudget"],
        "clipDepthReason": route_gpu["clipDepthReason"],
        "edgeCount": route_gpu["edgeCount"],
        "edgeBudget": route_gpu["edgeBudget"],
        "edgeBudgetReason": route_gpu["edgeBudgetReason"],
        "deviceBounds": route_gpu["deviceBounds"],
        "deviceBoundsBudget": route_gpu["deviceBoundsBudget"],
        "clipOp": route_gpu["clipOp"],
        "clipShape": route_gpu["clipShape"],
        "clipSequence": clip_sequence(),
        "inverseClip": route_gpu["inverseClip"],
        "complexClip": route_gpu["complexClip"],
        "clipShader": False,
        "perspectiveClip": False,
        "pathBooleanParity": False,
        "supportThreshold": stats["threshold"],
        "gpuSimilarity": stats["gpuSimilarity"],
        "cpuSimilarity": stats["cpuSimilarity"],
        "supportReady": False,
        "blockingCondition": "visual-parity-below-threshold-and-selector-route-dump-missing",
    }
    m57_baseline = {
        "sceneId": M57_SCENE_ID,
        "status": m57_row["status"],
        "fallbackReason": m57_row["fallbackReason"],
        "gpuRoute": m57_row["gpuRoute"],
        "cpuRoute": m57_row["cpuRoute"],
        "edgeCount": kan004_support["edgeCount"],
        "edgeBudget": kan004_support["edgeBudget"],
        "clipOp": kan004_support["clipOp"],
        "clipShape": kan004_support["clipShape"],
        "complexClip": kan004_support["complexClip"],
        "inverseClip": kan004_support["inverseClip"],
        "gpuSimilarity": m57_stats["gpuSimilarity"],
    }
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-039",
        "packId": "kan-039-nested-clip-stack-v1",
        "status": "pass",
        "closureDecision": "stable-refusal-nested-clip-stack-v1",
        "claimLevel": "bounded-nested-clip-stack-stable-refusal",
        "supportClaim": False,
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "edgeBudgetChanged": False,
        "readinessDelta": 0,
        "candidate": candidate,
        "m57SupportBaseline": m57_baseline,
        "clipPolicy": {
            "noIntegerScissorSubstitution": True,
            "arbitraryAaClipFallback": ARBITRARY_AA_CLIP_FALLBACK,
            "clipStackFallback": CLIP_STACK_FALLBACK,
            "nestedVisualFallback": NESTED_FALLBACK,
            "supportedClipStackFamilies": ["rect-intersect", "rrect-intersect", "rect-difference"],
            "refusedClipStackFamilies": [
                "arbitrary-aa-path-intersect",
                "multi-shape-aa-difference",
                "shader-clip",
                "unlowerable-stack",
            ],
            "excludedFromCandidate": ["clipShader", "perspectiveClip", "pathBooleanParity"],
        },
        "forensicBlockers": {
            "for301Decision": for301["decision"],
            "for301SupportDecision": for301["supportDecision"],
            "for302Decision": for302["decision"],
            "for302SupportDecision": for302["supportDecision"],
            "exactGap": for302["exactGap"],
            "differenceFormulaMatches": details301["differenceFormulaMatches"],
            "safeLocalFixApplied": details301["safeLocalFixApplied"],
            "analyticRuntimeReconciled": decisions302["M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT"],
        },
        "artifactAvailability": {
            "skiaReference": {"available": True, "path": M60_SKIA_IMAGE_PATH},
            "cpuOracle": {"available": True, "path": M60_CPU_IMAGE_PATH},
            "cpuDiff": {"available": True, "path": M60_CPU_DIFF_PATH},
            "webGpuDiagnosticImage": {"available": True, "path": M60_GPU_IMAGE_PATH},
            "webGpuDiagnosticDiff": {"available": True, "path": M60_GPU_DIFF_PATH},
            "webGpuRoute": {"available": True, "path": M60_ROUTE_GPU_PATH},
            "webGpuSupportRoute": {
                "available": False,
                "reason": NESTED_FALLBACK,
                "required": "row-specific WebGPU selector route dump with fallbackReason=none",
            },
        },
        "supportPolicy": {
            "rowStatus": "expected-unsupported",
            "decision": "stable-refusal",
            "requiredForSupport": [
                "WebGPU route with fallbackReason=none",
                "selector-owned route diagnostics rather than scene-contract-only diagnostics",
                "visual parity at or above 99.95 without threshold weakening",
                "clip sequence/depth/type/fallback diagnostics preserved",
                "m57-aaclip-bounded-grid remains supported",
                "arbitrary AA clips and complex nested clips remain refused with stable reasons",
                "no integer scissor substitution for AA clip support",
            ],
        },
        "nonClaims": [
            "KAN-039 does not claim nested clip WebGPU support.",
            "KAN-039 does not claim arbitrary clip-stack, clipShader, perspective clip, path boolean parity, inverse clip, or complex clip support.",
            "KAN-039 does not replace arbitrary AA clips with integer scissor.",
            "KAN-039 does not lower the 99.95 threshold or raise the 256 edge budget / depth 4 clip budget.",
            "KAN-039 does not port Ganesh or Graphite and does not add SkSL compiler behavior.",
        ],
        "validationRows": [
            {
                "id": "m60-candidate-visible-refusal",
                "status": "pass",
                "evidence": f"{M60_SCENE_ID} remains {route_gpu['status']} via {route_gpu['fallbackReason']}.",
            },
            {
                "id": "clip-sequence-diagnostics-visible",
                "status": "pass",
                "evidence": "Clip sequence is rect/intersect, rect/intersect, rrect-oval/difference with depth 3/4.",
            },
            {
                "id": "budget-diagnostics-preserved",
                "status": "pass",
                "evidence": "Candidate stays under clip depth 3/4 and edge count 72/256; refusal is visual parity, not budget overflow.",
            },
            {
                "id": "m57-support-baseline-preserved",
                "status": "pass",
                "evidence": f"{M57_SCENE_ID} remains pass with WebGPU fallbackReason=none.",
            },
            {
                "id": "forensic-blocker-visible",
                "status": "pass",
                "evidence": "FOR-301/FOR-302 keep M60 expected-unsupported and block reuse of the superseded analytic model.",
            },
            {
                "id": "clip-policy-refusals-visible",
                "status": "pass",
                "evidence": "Arbitrary AA clip and unlowerable clip-stack refusals remain stable; no integer scissor substitution is introduced.",
            },
            {
                "id": "policy-preserved",
                "status": "pass",
                "evidence": "No renderer, shader, threshold, edge-budget, or clip-depth budget change is made.",
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
    baseline = evidence["m57SupportBaseline"]
    forensic = evidence["forensicBlockers"]
    sequence = "\n".join(
        f"| {entry['index']} | `{entry['type']}` | `{entry['op']}` | `{entry['interaction']}` | `{entry['rawPaintClipOperationExposed']}` |"
        for entry in candidate["clipSequence"]
    )
    refused = ", ".join(f"`{item}`" for item in evidence["clipPolicy"]["refusedClipStackFamilies"])
    required = "\n".join(f"- {item}" for item in evidence["supportPolicy"]["requiredForSupport"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-039 Nested Clip-Stack V1

KAN-039 selects the bounded M60 nested clip row and keeps it as a stable
refusal. The row is inside the current clip-depth and edge budgets, and its
clip sequence is explicit, but WebGPU visual parity remains below the support
floor and the route diagnostics are still scene-contract evidence rather than
a selector-owned `fallbackReason=none` support route.

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
| Scene | `{candidate['sceneId']}` |
| Source | `{candidate['source']}` |
| Status | `{candidate['status']}` |
| Fallback | `{candidate['fallbackReason']}` |
| CPU route | `{candidate['cpuRoute']}` |
| GPU route | `{candidate['gpuRoute']}` |
| Clip depth | `{candidate['clipDepth']}/{candidate['clipDepthBudget']}` |
| Edges | `{candidate['edgeCount']}/{candidate['edgeBudget']}` |
| GPU similarity | `{candidate['gpuSimilarity']}` / `{candidate['supportThreshold']}` |
| Blocking condition | `{candidate['blockingCondition']}` |

## Clip Sequence

| Index | Type | Op | ClipInteraction | Raw paint op exposed |
|---:|---|---|---|---|
{sequence}

## M57 Baseline

`{baseline['sceneId']}` remains `{baseline['status']}` through
`{baseline['gpuRoute']}` with `fallbackReason={baseline['fallbackReason']}`.

## Forensic Blockers

| Fact | Value |
|---|---|
| FOR-301 decision | `{forensic['for301Decision']}` |
| FOR-302 decision | `{forensic['for302Decision']}` |
| Support decision | `{forensic['for302SupportDecision']}` |
| Difference formula matches | `{forensic['differenceFormulaMatches']}` |
| Safe local fix applied | `{forensic['safeLocalFixApplied']}` |

## Refusal Policy

Refused clip-stack families: {refused}.

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
    print(
        "KAN-039 validation passed: "
        f"{candidate['sceneId']} remains {candidate['status']} via {candidate['fallbackReason']} "
        f"with clipDepth={candidate['clipDepth']}/{candidate['clipDepthBudget']} and "
        f"edges={candidate['edgeCount']}/{candidate['edgeBudget']}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
