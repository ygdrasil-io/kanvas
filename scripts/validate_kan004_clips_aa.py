#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


REPORT_PATH = "reports/wgsl-pipeline/2026-06-10-kan-004-clips-aa.md"
EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/artifacts/kan-004-clips-aa/kan-004-clips-aa.json"
M57_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json"
M60_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"
M57_REPORT_PATH = "reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md"
M60_REPORT_PATH = "reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_FALLBACKS_PATH = ".upstream/specs/geometry-coverage/05-fallback-diagnostics.md"

SUPPORT_SCENE_ID = "m57-aaclip-bounded-grid"
NESTED_SCENE_ID = "m60-bounded-nested-rrect-clip"
EDGE_BUDGET = 256
CLIP_DEPTH_BUDGET = 4
NESTED_FALLBACK = "coverage.nested-clip-visual-parity-below-threshold"
ARBITRARY_AA_CLIP_FALLBACK = "coverage.arbitrary-aa-clip-unsupported"
CLIP_STACK_FALLBACK = "geometry.clip-stack-unsupported"


def fail(message: str):
    raise SystemExit(f"KAN-004 clips AA validation failed: {message}")


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


def generated_row(root: Path, pack_path: str, scene_id: str) -> dict[str, Any]:
    pack = load_json(root, pack_path)
    rows = pack.get("scenes")
    require(isinstance(rows, list), f"{pack_path}.scenes must be a list")
    for row in rows:
        if isinstance(row, dict) and row.get("id") == scene_id:
            return row
    fail(f"{pack_path} missing scene row: {scene_id}")


def rejected_row(root: Path, pack_path: str, inventory_id: str) -> dict[str, Any]:
    pack = load_json(root, pack_path)
    rows = pack.get("rejectedRows")
    require(isinstance(rows, list), f"{pack_path}.rejectedRows must be a list")
    for row in rows:
        if isinstance(row, dict) and row.get("inventoryId") == inventory_id:
            return row
    fail(f"{pack_path} missing rejected row: {inventory_id}")


def require_route(
    root: Path,
    relative_path: str,
    *,
    scene_id: str,
    backend: str,
    status: str,
    fallback_reason: str,
) -> dict[str, Any]:
    route = load_json(root, relative_path)
    require(route.get("sceneId") == scene_id, f"{relative_path} sceneId changed")
    require(route.get("backend") == backend, f"{relative_path} backend changed")
    require(route.get("status") == status, f"{relative_path} status changed")
    require(route.get("fallbackReason") == fallback_reason, f"{relative_path} fallback changed")
    return route


def require_scene_artifacts(root: Path, scene_id: str, names: list[str]):
    for name in names:
        require_file(root, f"reports/wgsl-pipeline/scenes/artifacts/{scene_id}/{name}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    evidence = load_json(root, EVIDENCE_PATH)
    require(evidence.get("schemaVersion") == 1, "schemaVersion changed")
    require(evidence.get("ticket") == "KAN-004", "ticket id changed")
    require(evidence.get("packId") == "kan-004-clips-aa-bounded-evidence-v1", "packId changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    require(evidence.get("closureDecision") == "supportable-bounded-aa-clip", "closure decision changed")
    require(
        evidence.get("claimLevel") == "bounded-aa-clip-visible-support-with-stable-refusals",
        "claimLevel changed",
    )
    require(evidence.get("supportClaim") == "m57-aaclip-bounded-grid-only", "support claim changed")
    require(evidence.get("broadClipStackSupportClaim") is False, "must not claim broad clip-stack support")
    require(evidence.get("thresholdsWeakened") is False, "thresholds must not be weakened")
    require(evidence.get("sharedCoverageChanged") is False, "shared coverage behavior must not change")
    require(evidence.get("readinessDelta") == 0, "KAN-004 must not move readiness")

    support = require_object(evidence, "supportScene", EVIDENCE_PATH)
    require(support.get("sceneId") == SUPPORT_SCENE_ID, "support scene changed")
    require(support.get("status") == "pass", "support status changed")
    require(support.get("cpuStatus") == "pass", "support CPU status changed")
    require(support.get("gpuStatus") == "pass", "support GPU status changed")
    require(support.get("gpuFallbackReason") == "none", "support fallback changed")
    require(support.get("cpuRouteName") == "cpu.coverage.aaclip-bounded-grid-oracle", "CPU route changed")
    require(support.get("gpuRouteName") == "webgpu.coverage.aaclip-bounded-grid", "GPU route changed")
    require(support.get("coverageStrategy") == "webgpu.coverage.aa-clip-rect-grid", "coverage strategy changed")
    require(
        support.get("pipelineKey") == "pathAA=aaclip clip=aaRectGrid op=intersect budget=current source=AaclipGM",
        "pipeline key changed",
    )
    require(support.get("edgeCount") == 90, "support edge count changed")
    require(support.get("edgeBudget") == EDGE_BUDGET, "support edge budget changed")
    require(support.get("edgeBudgetReason") == "not coverage.edge-count-exceeded", "support budget reason changed")
    require(support.get("pathContours") == 15, "support contour count changed")
    require(support.get("clipOp") == "intersect", "support clip op changed")
    require(support.get("clipShape") == "aa-rect-grid", "support clip shape changed")
    require(support.get("inverseClip") is False, "support must not be inverse clip")
    require(support.get("complexClip") is False, "support must not be complex clip")
    require(support.get("dashPattern") == "none", "support dash pattern changed")
    require(support.get("pixels") == 28800, "support pixel count changed")
    require(support.get("matchingPixels") == 28464, "support matching pixels changed")
    require(support.get("threshold") == 98.78, "support threshold changed")
    require(support.get("gpuSimilarity") == 98.83, "support GPU similarity changed")
    require(support.get("targetColorSpaceBlendSupportClaim") is False, "target-color-space blend must not be support")
    for field in ("reference", "cpuImage", "cpuDiff", "gpuImage", "gpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, support[field])

    support_cpu_route = require_route(
        root,
        support["cpuRoute"],
        scene_id=SUPPORT_SCENE_ID,
        backend="CPU",
        status="pass",
        fallback_reason="none",
    )
    require(support_cpu_route.get("selectedRoute") == "cpu.coverage.aaclip-bounded-grid-oracle", "CPU selected route changed")
    support_gpu_route = require_route(
        root,
        support["gpuRoute"],
        scene_id=SUPPORT_SCENE_ID,
        backend="WebGPU",
        status="pass",
        fallback_reason="none",
    )
    require(support_gpu_route.get("selectedRoute") == "webgpu.coverage.aaclip-bounded-grid", "GPU selected route changed")
    require(support_gpu_route.get("coverageStrategy") == "webgpu.coverage.aa-clip-rect-grid", "GPU strategy changed")
    require(support_gpu_route.get("edgeBudget") == EDGE_BUDGET, "GPU route edge budget changed")
    require(support_gpu_route.get("complexClip") is False, "GPU route must not be complex clip")
    support_stats = load_json(root, support["stats"])
    require(support_stats.get("sceneId") == SUPPORT_SCENE_ID, "support stats scene changed")
    require(support_stats.get("gpuStatus") == "pass", "support stats GPU status changed")
    require(support_stats.get("threshold") == 98.78, "support stats threshold changed")
    require(support_stats.get("gpuSimilarity") == 98.83, "support stats similarity changed")
    require(support_stats.get("targetColorSpaceBlendSupportClaim") is False, "target blend support claim changed")

    support_row = generated_row(root, M57_PACK_PATH, SUPPORT_SCENE_ID)
    require(support_row.get("status") == "pass", "M57 generated support status changed")
    require(support_row.get("fallbackReason") == "none", "M57 generated fallback changed")
    require(support_row.get("gpuRoute") == "webgpu.coverage.aaclip-bounded-grid", "M57 generated GPU route changed")
    require("feature.clip" in support_row.get("tags", []), "M57 row missing feature.clip tag")
    require("feature.coverage.aa" in support_row.get("tags", []), "M57 row missing AA coverage tag")
    m57_gpu_details = require_object(support_row, "gpuRouteDetails", M57_PACK_PATH)
    require(m57_gpu_details.get("edgeCount") == 90, "M57 row edge count changed")
    require(m57_gpu_details.get("edgeBudget") == EDGE_BUDGET, "M57 row edge budget changed")
    require(m57_gpu_details.get("complexClip") is False, "M57 row must not be complex clip")
    rejected_complex = rejected_row(root, M57_PACK_PATH, "skia-gm-complexclip")
    require("Complex clip requires its own clip-stack support proof" in rejected_complex.get("reason", ""), "complex clip rejection changed")

    nested = require_object(evidence, "nestedClipBoundary", EVIDENCE_PATH)
    require(nested.get("sceneId") == NESTED_SCENE_ID, "nested boundary scene changed")
    require(nested.get("status") == "expected-unsupported", "nested boundary status changed")
    require(nested.get("cpuStatus") == "pass", "nested CPU status changed")
    require(nested.get("gpuStatus") == "expected-unsupported", "nested GPU status changed")
    require(nested.get("gpuFallbackReason") == NESTED_FALLBACK, "nested fallback changed")
    require(nested.get("gpuRouteName") == "webgpu.coverage.nested-rrect-clip.expected-unsupported", "nested GPU route changed")
    require(nested.get("coverageStrategy") == "webgpu.coverage.nested-rrect-clip.expected-unsupported", "nested strategy changed")
    require(nested.get("clipDepth") == 3, "nested clip depth changed")
    require(nested.get("clipDepthBudget") == CLIP_DEPTH_BUDGET, "nested clip depth budget changed")
    require(nested.get("clipDepthReason") == "not coverage.clip-depth-exceeded", "nested clip depth reason changed")
    require(nested.get("edgeCount") == 72, "nested edge count changed")
    require(nested.get("edgeBudget") == EDGE_BUDGET, "nested edge budget changed")
    require(nested.get("clipOp") == "intersect+intersect+difference", "nested clip op changed")
    require(nested.get("clipShape") == "rect+rect+rrect-oval", "nested clip shape changed")
    require(nested.get("nestedClip") is True, "nested clip flag changed")
    require(nested.get("complexClip") is False, "nested boundary must not be complex clip")
    require(nested.get("threshold") == 99.95, "nested threshold changed")
    for field in ("reference", "cpuImage", "cpuDiff", "gpuImage", "gpuDiff", "cpuRoute", "gpuRoute", "stats"):
        require_file(root, nested[field])
    nested_cpu_route = require_route(
        root,
        nested["cpuRoute"],
        scene_id=NESTED_SCENE_ID,
        backend="CPU",
        status="pass",
        fallback_reason="none",
    )
    require(nested_cpu_route.get("selectedRoute") == "cpu.coverage.nested-rrect-clip-oracle", "nested CPU route changed")
    nested_gpu_route = require_route(
        root,
        nested["gpuRoute"],
        scene_id=NESTED_SCENE_ID,
        backend="WebGPU",
        status="expected-unsupported",
        fallback_reason=NESTED_FALLBACK,
    )
    require(nested_gpu_route.get("clipDepth") == 3, "nested GPU route clip depth changed")
    require(nested_gpu_route.get("edgeBudget") == EDGE_BUDGET, "nested GPU route edge budget changed")
    nested_stats = load_json(root, nested["stats"])
    require(nested_stats.get("sceneId") == NESTED_SCENE_ID, "nested stats scene changed")
    require(nested_stats.get("gpuStatus") == "expected-unsupported", "nested stats GPU status changed")
    require(nested_stats.get("fallbackReason") == NESTED_FALLBACK, "nested stats fallback changed")
    require(nested_stats.get("threshold") == 99.95, "nested stats threshold changed")
    nested_row = generated_row(root, M60_PACK_PATH, NESTED_SCENE_ID)
    require(nested_row.get("status") == "expected-unsupported", "M60 nested row status changed")
    require(nested_row.get("fallbackReason") == NESTED_FALLBACK, "M60 nested row fallback changed")
    require(nested_row.get("gpuRoute") == "webgpu.coverage.nested-rrect-clip.expected-unsupported", "M60 nested row route changed")
    require("feature.clip.nested" in nested_row.get("tags", []), "M60 nested row missing nested tag")

    policy = require_object(evidence, "clipPolicy", EVIDENCE_PATH)
    require(policy.get("simpleAaClipSupport") == SUPPORT_SCENE_ID, "simple AA clip policy changed")
    require(policy.get("arbitraryAaClipFallback") == ARBITRARY_AA_CLIP_FALLBACK, "arbitrary AA clip fallback changed")
    require(policy.get("clipStackFallback") == CLIP_STACK_FALLBACK, "clip stack fallback changed")
    require(policy.get("nestedVisualFallback") == NESTED_FALLBACK, "nested visual fallback changed")
    require(policy.get("noIntegerScissorSubstitution") is True, "arbitrary AA clips must not become integer scissor")
    require(policy.get("maxClaimedClipDepth") == 1, "claimed support must remain one simple AA clip grid")
    require(policy.get("supportsComplexClip") is False, "complex clip support must remain false")
    require(policy.get("supportsInverseClip") is False, "inverse clip support must remain false")

    validations = require_list(evidence, "validationRows", EVIDENCE_PATH)
    require(len(validations) >= 7, "validationRows missing rows")
    for row in validations:
        require(isinstance(row, dict), "validation row must be object")
        require(row.get("status") == "pass", f"validation row failed: {row.get('id')}")

    non_claims = "\n".join(evidence.get("nonClaims", []))
    for snippet in (
        "does not claim broad AA clip support",
        "does not claim arbitrary clip-stack support",
        "does not claim nested rrect clip WebGPU support",
        "does not support complex clips, inverse clips, shader clips, or perspective clips",
        "does not replace arbitrary AA clips with integer scissor",
        "does not lower thresholds",
        "does not port Ganesh or Graphite",
        "does not add a SkSL compiler",
    ):
        require(snippet in non_claims, f"missing non-claim: {snippet}")

    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    require_scene_artifacts(
        root,
        SUPPORT_SCENE_ID,
        ["skia.png", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"],
    )
    require_scene_artifacts(
        root,
        NESTED_SCENE_ID,
        ["skia.png", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"],
    )

    require_contains(root, REPORT_PATH, [
        "KAN-004 closes bounded AA clips as `supportable-bounded-aa-clip`",
        "`m57-aaclip-bounded-grid`",
        "`webgpu.coverage.aa-clip-rect-grid`",
        "`m60-bounded-nested-rrect-clip`",
        "`coverage.nested-clip-visual-parity-below-threshold`",
        "No arbitrary clip-stack support claim is added.",
        "No threshold is lowered.",
        "No integer scissor substitution is introduced.",
    ])
    require_contains(root, M57_REPORT_PATH, [
        "`m57-aaclip-bounded-grid`",
        "`webgpu.coverage.aaclip-bounded-grid`",
        "M57 does not claim broad Path AA support",
        "complex clip support",
    ])
    require_contains(root, M60_REPORT_PATH, [
        "`m60-bounded-nested-rrect-clip`",
        "`coverage.nested-clip-visual-parity-below-threshold`",
        "FOR-9 does not claim nested clip WebGPU support",
    ])
    require_contains(root, SPEC_REALTIME_PATH, [
        "clip and Path AA support is limited to selected clip difference, AA clip,",
        "broad Path AA, broad clip stacks",
        "Clip stack depth | <= 4 nested clips",
    ])
    require_contains(root, SPEC_LOWERING_PATH, [
        "Arbitrary AA clips may become native CPU `SkAAClip`",
        "unsupported WebGPU diagnostic",
        "Multiple curved clip shapes are unsupported on WebGPU until a list/atlas path is specified.",
    ])
    require_contains(root, SPEC_FALLBACKS_PATH, [
        "`geometry.clip-stack-unsupported`",
        "`coverage.arbitrary-aa-clip-unsupported`",
    ])

    print("KAN-004 clips AA evidence validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
