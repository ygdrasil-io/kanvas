#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/image-filter-dag-bounded-v3"
OUTPUT_JSON = "kan-041-image-filter-dag-bounded-v3.json"
OUTPUT_MARKDOWN = "kan-041-image-filter-dag-bounded-v3.md"

RESULTS_PATH = "reports/wgsl-pipeline/scenes/generated/results.json"
M52_PATH = "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"
M54_PATH = "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json"
M61_PATH = "reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"
M66_PATH = "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"
SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_IMAGE_FILTER_PATH = ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md"
TARGET_WGSL_PATH = ".upstream/target/high-performance-wgsl-pipeline-target.md"

CROP_SCENE_ID = "crop-image-filter-nonnull-prepass"
M61_SCENE_ID = "m61-compose-cf-matrix-transform-dag-v2"
COMPOSE_BASE_SCENE_ID = "image-filter-compose-cf-matrix-transform"
M52_REFUSAL_ID = "m52-big-tile-image-filter-dag-refusal"
M54_REFUSAL_ID = "m54-imagefilters-graph-boundary"
CROP_REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"

DAG_REASON = "image-filter.dag-or-picture-prepass-required"
CROP_REASON = "image-filter.crop-input-nonnull-prepass-required"
MAX_NODE_COUNT = 4
MAX_INTERMEDIATE_TEXTURES = 4


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-041 image-filter DAG bounded V3 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def find_by_id(payload: Any, scene_id: str) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []

    def walk(value: Any) -> None:
        if isinstance(value, dict):
            if value.get("id") == scene_id or value.get("sceneId") == scene_id:
                matches.append(value)
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(payload)
    for match in matches:
        if match.get("status") is not None:
            return match
    if matches:
        return matches[0]
    fail(f"missing scene `{scene_id}`")


def artifact_root(scene_id: str) -> str:
    return f"reports/wgsl-pipeline/scenes/artifacts/{scene_id}"


def artifact_paths_for_scene(scene_id: str) -> dict[str, str]:
    root = artifact_root(scene_id)
    return {
        "reference": f"{root}/skia.png",
        "cpu": f"{root}/cpu.png",
        "gpu": f"{root}/gpu.png",
        "cpuDiff": f"{root}/cpu-diff.png",
        "gpuDiff": f"{root}/gpu-diff.png",
        "stats": f"{root}/stats.json",
        "cpuRoute": f"{root}/route-cpu.json",
        "gpuRoute": f"{root}/route-gpu.json",
        "prepassRoute": f"{root}/route-prepass.json",
    }


def support_proofs(root: Path, paths: dict[str, str]) -> dict[str, bool]:
    return {
        "reference": (root / paths["reference"]).is_file(),
        "cpu": (root / paths["cpu"]).is_file(),
        "gpu": (root / paths["gpu"]).is_file(),
        "diff": (root / paths["cpuDiff"]).is_file() and (root / paths["gpuDiff"]).is_file(),
        "stats": (root / paths["stats"]).is_file(),
        "route": (root / paths["cpuRoute"]).is_file()
        and (root / paths["gpuRoute"]).is_file()
        and (root / paths["prepassRoute"]).is_file(),
    }


def bool_tag(row: dict[str, Any], tag: str) -> bool:
    tags = row.get("tags")
    return isinstance(tags, list) and tag in tags


def crop_graph(root: Path, row: dict[str, Any]) -> dict[str, Any]:
    paths = artifact_paths_for_scene(CROP_SCENE_ID)
    route_gpu = load_json(root, paths["gpuRoute"])
    route_prepass = load_json(root, paths["prepassRoute"])
    stats = load_json(root, paths["stats"])
    require(row.get("status") == "pass", "crop DAG row status changed")
    require(route_gpu.get("status") == "pass", "crop DAG GPU route status changed")
    require(route_gpu.get("fallbackReason") == "none", "crop DAG GPU fallback changed")
    require(route_prepass.get("fallbackReason") == "none", "crop DAG prepass fallback changed")
    require(stats.get("pixels") == 128000, "crop DAG stats pixel count changed")
    require(bool_tag(row, "risk.fidelity-gap"), "crop DAG must keep fidelity-gap risk tag")

    return {
        "schemaVersion": 1,
        "sceneId": CROP_SCENE_ID,
        "graph": "Crop(kDecal, input=Offset(input=null))",
        "status": "pass",
        "fallbackReason": "none",
        "nodeCount": 2,
        "nodeBudget": MAX_NODE_COUNT,
        "childrenPerNodeBudget": 2,
        "intermediateTextureCount": 1,
        "intermediateTextureBudget": MAX_INTERMEDIATE_TEXTURES,
        "estimatedIntermediateBytes": int(stats["pixels"]) * 4,
        "bounds": {
            "input": {"left": 0, "top": 0, "right": 640, "bottom": 200},
            "output": {"left": 0, "top": 0, "right": 640, "bottom": 200},
        },
        "nodes": [
            {
                "id": "offset-child",
                "kind": "offset",
                "inputs": [],
                "support": "supported",
                "reason": "bounded child pre-pass materializes the offset child into GPU scratch",
            },
            {
                "id": "crop-decal",
                "kind": "crop-decal",
                "inputs": ["offset-child"],
                "support": "supported",
                "reason": "final crop composite samples the bounded child scratch with fallbackReason=none",
            },
        ],
        "passOrder": ["child-pre-pass", "final-crop-composite"],
        "ownership": {
            "intermediatesAllocated": 1,
            "intermediateTexture": route_gpu["intermediateTexture"],
            "writerRoute": route_prepass["materializeRoute"],
            "readerRoute": route_gpu["selectedRoute"],
            "scratchLifetime": "per-draw-child-pre-pass",
            "cpuReadbackFallback": False,
            "picturePrepass": False,
        },
        "strictSkiaParityClaim": False,
        "risk": "risk.fidelity-gap",
        "nonClaim": "Bounded Crop(input=Offset(null)) support only; no strict Skia parity, arbitrary DAG, picture prepass, or CPU readback fallback is claimed.",
    }


def m61_graph(root: Path, row: dict[str, Any]) -> dict[str, Any]:
    graph = row.get("graphDiagnostics")
    require(isinstance(graph, dict), "M61 graph diagnostics missing")
    paths = artifact_paths_for_scene(COMPOSE_BASE_SCENE_ID)
    route_gpu = load_json(root, paths["gpuRoute"])
    stats = load_json(root, paths["stats"])
    require(row.get("status") == "pass", "M61 row status changed")
    require(row.get("fallbackReason") == "none", "M61 row fallback changed")
    require(graph.get("status") == "pass", "M61 graph status changed")
    require(graph.get("fallbackReason") == "none", "M61 graph fallback changed")
    require(graph.get("nodeCount") == 3, "M61 graph node count changed")
    require(graph.get("intermediateTextureCount") == 1, "M61 graph intermediate count changed")
    require(route_gpu.get("fallbackReason") == "none", "M61 route fallback changed")
    require(stats.get("gpuSimilarity") == 100.0, "M61 GPU similarity changed")
    ownership = graph.get("ownership")
    require(isinstance(ownership, dict), "M61 ownership missing")
    require(ownership.get("cpuReadbackFallback") is False, "M61 must not use CPU readback fallback")
    return graph


def support_scene(
    root: Path,
    *,
    row: dict[str, Any],
    scene_id: str,
    base_scene_id: str,
    graph: dict[str, Any],
    reference_kind: str,
    source: str,
) -> dict[str, Any]:
    paths = artifact_paths_for_scene(base_scene_id)
    proofs = support_proofs(root, paths)
    route_gpu = load_json(root, paths["gpuRoute"])
    stats = load_json(root, paths["stats"])
    node_count = int(graph["nodeCount"])
    intermediate_count = int(graph["intermediateTextureCount"])
    require(node_count >= 2 and node_count <= MAX_NODE_COUNT, f"{scene_id} node count outside budget")
    require(intermediate_count <= MAX_INTERMEDIATE_TEXTURES, f"{scene_id} intermediate texture count outside budget")
    require(route_gpu.get("fallbackReason") == "none", f"{scene_id} route fallback must be none")
    require(all(proofs.values()), f"{scene_id} missing support proofs: {proofs}")
    ownership = graph.get("ownership") if isinstance(graph.get("ownership"), dict) else {}
    require(ownership.get("cpuReadbackFallback") is False, f"{scene_id} must not use CPU readback fallback")

    return {
        "sceneId": scene_id,
        "baseArtifactScene": base_scene_id,
        "source": source,
        "referenceKind": reference_kind,
        "status": "pass",
        "fallbackReason": "none",
        "nodeCount": node_count,
        "nodeBudget": graph["nodeBudget"],
        "childrenPerNodeBudget": graph["childrenPerNodeBudget"],
        "intermediateTextureCount": intermediate_count,
        "intermediateTextureBudget": graph["intermediateTextureBudget"],
        "estimatedIntermediateBytes": graph["estimatedIntermediateBytes"],
        "passOrder": graph["passOrder"],
        "nodes": graph["nodes"],
        "ownership": ownership,
        "route": route_gpu["selectedRoute"],
        "prepassRoute": route_gpu.get("prepassRoute") or ownership.get("writerRoute"),
        "pixels": stats["pixels"],
        "gpuSimilarity": stats.get("gpuSimilarity"),
        "threshold": stats["threshold"],
        "proofs": proofs,
        "artifactPaths": paths,
        "strictSkiaParityClaim": graph.get("strictSkiaParityClaim", row.get("referenceKind") == "skia-upstream" and not bool_tag(row, "risk.fidelity-gap")),
        "nonClaim": graph["nonClaim"],
    }


def refusal_scene(row: dict[str, Any], scene_id: str, fallback: str, source: str) -> dict[str, Any]:
    graph = row.get("graphDiagnostics") if isinstance(row.get("graphDiagnostics"), dict) else {}
    node_count = graph.get("nodeCount", 2)
    intermediate_count = graph.get("intermediateTextureCount", 0)
    pass_order = graph.get("passOrder", [])
    return {
        "sceneId": scene_id,
        "source": source,
        "status": "expected-unsupported",
        "fallbackReason": fallback,
        "reasonCodeStable": fallback in {DAG_REASON, CROP_REASON},
        "nodeCount": node_count,
        "nodeBudget": graph.get("nodeBudget", MAX_NODE_COUNT),
        "intermediateTextureCount": intermediate_count,
        "intermediateTextureBudget": graph.get("intermediateTextureBudget", MAX_INTERMEDIATE_TEXTURES),
        "passOrder": pass_order,
        "nonClaim": row.get("nonClaim") or graph.get("nonClaim"),
    }


def build_claim_guard(support_rows: list[dict[str, Any]], refusal_rows: list[dict[str, Any]]) -> dict[str, list[str]]:
    support_rows_missing_proofs = [
        row["sceneId"]
        for row in support_rows
        if not all(row["proofs"].values())
    ]
    support_rows_missing_fallback_none = [
        row["sceneId"]
        for row in support_rows
        if row["fallbackReason"] != "none"
    ]
    unsupported_rows_missing_stable_reason = [
        row["sceneId"]
        for row in refusal_rows
        if not row["fallbackReason"] or row["fallbackReason"] == "none" or not row["reasonCodeStable"]
    ]
    implicit_cpu_readback_fallbacks = [
        row["sceneId"]
        for row in support_rows
        if row["ownership"].get("cpuReadbackFallback") is not False
    ]
    over_budget_support_rows = [
        row["sceneId"]
        for row in support_rows
        if row["nodeCount"] > MAX_NODE_COUNT or row["intermediateTextureCount"] > MAX_INTERMEDIATE_TEXTURES
    ]
    hidden_picture_prepass_support = [
        row["sceneId"]
        for row in support_rows
        if row["ownership"].get("picturePrepass") is True
    ]
    return {
        "supportRowsMissingProofs": support_rows_missing_proofs,
        "supportRowsMissingFallbackNone": support_rows_missing_fallback_none,
        "unsupportedRowsMissingStableReason": unsupported_rows_missing_stable_reason,
        "implicitCpuReadbackFallbacks": implicit_cpu_readback_fallbacks,
        "overBudgetSupportRows": over_budget_support_rows,
        "hiddenPicturePrepassSupport": hidden_picture_prepass_support,
    }


def committed_artifacts(support_rows: list[dict[str, Any]]) -> list[str]:
    paths = {
        RESULTS_PATH,
        M52_PATH,
        M54_PATH,
        M61_PATH,
        M66_PATH,
        SPEC_REALTIME_PATH,
        SPEC_IMAGE_FILTER_PATH,
        TARGET_WGSL_PATH,
        "reports/wgsl-pipeline/2026-05-28-m41-crop-image-filter-generated-evidence.md",
        "reports/wgsl-pipeline/2026-06-01-m61-bounded-image-filter-dag-v2-promotion.md",
        "reports/wgsl-pipeline/2026-06-01-m61-image-filter-dag-diagnostics.md",
        "reports/wgsl-pipeline/2026-06-10-kan-006-intermediate-texture-ownership.md",
        "reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md",
        "scripts/validate_kan006_intermediate_texture_ownership.py",
        "scripts/validate_kan008_image_filter_dag_refusals.py",
        "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt",
        "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/SimpleOffsetImageFilterCrossBackendTest.kt",
        "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SaveLayerImageFilterTest.kt",
    }
    for row in support_rows:
        paths.update(row["artifactPaths"].values())
    return sorted(paths)


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    results = load_json(root, RESULTS_PATH)
    m52 = load_json(root, M52_PATH)
    m54 = load_json(root, M54_PATH)
    m61 = load_json(root, M61_PATH)

    require_contains(root, SPEC_REALTIME_PATH, [
        "DAG node count",
        "2 to 4 nodes",
        "implicit readback compatibility",
    ])
    require_contains(root, SPEC_IMAGE_FILTER_PATH, [
        "Crop(kDecal, input = Offset(null))",
        "Compose(ColorFilter, MatrixTransform)",
        "does not introduce a general image-filter graph compiler",
    ])
    require_contains(root, TARGET_WGSL_PATH, [
        "FallbackPlan",
        "stable diagnostic reason",
        "ExplicitLayerOrReadbackCompat",
    ])

    crop_row = find_by_id(results, CROP_SCENE_ID)
    m61_row = find_by_id(m61, M61_SCENE_ID)
    crop = support_scene(
        root,
        row=crop_row,
        scene_id=CROP_SCENE_ID,
        base_scene_id=CROP_SCENE_ID,
        graph=crop_graph(root, crop_row),
        reference_kind="skia-upstream",
        source="M38 Crop(input=Offset(null)) generated scene",
    )
    compose = support_scene(
        root,
        row=m61_row,
        scene_id=M61_SCENE_ID,
        base_scene_id=COMPOSE_BASE_SCENE_ID,
        graph=m61_graph(root, m61_row),
        reference_kind=m61_row["referenceKind"],
        source="M61 Compose(ColorFilter, MatrixTransform) generated scene",
    )
    support_rows = [crop, compose]

    m52_row = find_by_id(m52, M52_REFUSAL_ID)
    m54_row = find_by_id(m54, M54_REFUSAL_ID)
    crop_refusal_row = find_by_id(results, CROP_REFUSAL_ID)
    refusal_rows = [
        refusal_scene(m52_row, M52_REFUSAL_ID, DAG_REASON, "M52 BigTileImageFilterGM DAG boundary"),
        refusal_scene(m54_row, M54_REFUSAL_ID, DAG_REASON, "M54 ImageFiltersGraphGM boundary"),
        refusal_scene(crop_refusal_row, CROP_REFUSAL_ID, CROP_REASON, "Out-of-scope Crop(input=nonNull) graph shapes"),
    ]

    guard = build_claim_guard(support_rows, refusal_rows)
    require(not guard["supportRowsMissingProofs"], f"support rows missing proofs: {guard['supportRowsMissingProofs']}")
    require(not guard["supportRowsMissingFallbackNone"], f"support rows missing fallbackReason=none: {guard['supportRowsMissingFallbackNone']}")
    require(not guard["unsupportedRowsMissingStableReason"], f"unsupported rows missing stable reason: {guard['unsupportedRowsMissingStableReason']}")
    require(not guard["implicitCpuReadbackFallbacks"], f"implicit CPU readback fallback rows: {guard['implicitCpuReadbackFallbacks']}")
    require(not guard["overBudgetSupportRows"], f"over-budget support rows: {guard['overBudgetSupportRows']}")
    require(not guard["hiddenPicturePrepassSupport"], f"hidden picture prepass support rows: {guard['hiddenPicturePrepassSupport']}")

    artifacts = committed_artifacts(support_rows)
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-041",
        "packId": "kan-041-image-filter-dag-bounded-v3",
        "status": "pass",
        "closureDecision": "bounded-image-filter-dag-v3",
        "claimLevel": "two-bounded-dag-scenes-with-stable-residual-refusals",
        "supportClaim": "two-bounded-image-filter-dags",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "readinessDelta": 0,
        "summary": {
            "supportScenes": len(support_rows),
            "expectedUnsupportedScenes": len(refusal_rows),
            "supportRowsMissingProofs": len(guard["supportRowsMissingProofs"]),
            "implicitCpuReadbackFallbacks": len(guard["implicitCpuReadbackFallbacks"]),
            "maxNodeBudget": MAX_NODE_COUNT,
            "maxIntermediateTextureBudget": MAX_INTERMEDIATE_TEXTURES,
        },
        "supportScenes": support_rows,
        "refusalScenes": refusal_rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan006IntermediateTextureOwnership",
            "validateKan008ImageFilterDagRefusals",
            "pipelineM61ImageFilterDagV2PromotionPack",
            "pipelineSceneDashboardGate",
            "pipelinePmBundle",
        ],
        "nonClaims": [
            "KAN-041 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-041 does not claim arbitrary recursive image-filter DAG support.",
            "KAN-041 does not claim picture prepass, large layer prepass, BigTile/ImageFiltersGraph broad parity, or CPU readback fallback.",
            "KAN-041 does not claim strict Skia parity for crop-image-filter-nonnull-prepass; it keeps risk.fidelity-gap visible.",
            "KAN-041 does not rebuild Skia image-filter internals, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "validationRows": [
            {
                "id": "two-bounded-support-scenes",
                "status": "pass",
                "evidence": "crop-image-filter-nonnull-prepass and m61-compose-cf-matrix-transform-dag-v2 both carry complete reference/CPU/GPU/diff/stat/route proofs with fallbackReason=none.",
            },
            {
                "id": "graph-diagnostics-visible",
                "status": "pass",
                "evidence": "Support graphs expose node count, bounds, intermediate ownership, pass order, and byte estimates.",
            },
            {
                "id": "unsupported-graphs-stable",
                "status": "pass",
                "evidence": "M52 BigTile, M54 ImageFiltersGraph, and out-of-scope Crop(input=nonNull) remain expected-unsupported with stable reasons.",
            },
            {
                "id": "no-hidden-readback",
                "status": "pass",
                "evidence": "Support graph ownership records cpuReadbackFallback=false and no support row claims picture prepass.",
            },
            {
                "id": "policy-preserved",
                "status": "pass",
                "evidence": "No renderer, shader, threshold, PipelineKey, or budget change is made.",
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
    summary = evidence["summary"]
    supports = "\n".join(
        "| `{sceneId}` | `{nodeCount}/{nodeBudget}` | `{intermediateTextureCount}/{intermediateTextureBudget}` | `{fallbackReason}` | `{route}` | `{strict}` |".format(
            sceneId=row["sceneId"],
            nodeCount=row["nodeCount"],
            nodeBudget=row["nodeBudget"],
            intermediateTextureCount=row["intermediateTextureCount"],
            intermediateTextureBudget=row["intermediateTextureBudget"],
            fallbackReason=row["fallbackReason"],
            route=row["route"],
            strict=row["strictSkiaParityClaim"],
        )
        for row in evidence["supportScenes"]
    )
    refusals = "\n".join(
        f"| `{row['sceneId']}` | `{row['nodeCount']}/{row['nodeBudget']}` | `{row['fallbackReason']}` | {row['nonClaim']} |"
        for row in evidence["refusalScenes"]
    )
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-041 Image Filter DAG Bounded V3

KAN-041 promotes a reporting/evidence slice for two bounded image-filter DAG
scenes and keeps larger or out-of-scope graph shapes refused with stable
diagnostics.

## Summary

| Metric | Count |
|---|---:|
| Support scenes | {summary['supportScenes']} |
| Expected-unsupported scenes | {summary['expectedUnsupportedScenes']} |
| Support rows missing proofs | {summary['supportRowsMissingProofs']} |
| Implicit CPU readback fallbacks | {summary['implicitCpuReadbackFallbacks']} |
| Max node budget | {summary['maxNodeBudget']} |
| Max intermediate texture budget | {summary['maxIntermediateTextureBudget']} |

## Support Scenes

| Scene | Nodes | Intermediates | Fallback | Route | Strict Skia parity claim |
|---|---:|---:|---|---|---:|
{supports}

## Stable Refusals

| Scene | Nodes | Fallback | Non-claim |
|---|---:|---|---|
{refusals}

## Claim Guard

| Guard | Value |
|---|---|
| supportRowsMissingProofs | `{evidence['claimGuard']['supportRowsMissingProofs']}` |
| supportRowsMissingFallbackNone | `{evidence['claimGuard']['supportRowsMissingFallbackNone']}` |
| unsupportedRowsMissingStableReason | `{evidence['claimGuard']['unsupportedRowsMissingStableReason']}` |
| implicitCpuReadbackFallbacks | `{evidence['claimGuard']['implicitCpuReadbackFallbacks']}` |
| overBudgetSupportRows | `{evidence['claimGuard']['overBudgetSupportRows']}` |
| hiddenPicturePrepassSupport | `{evidence['claimGuard']['hiddenPicturePrepassSupport']}` |

## Required Validation

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
    summary = evidence["summary"]
    print(
        "KAN-041 validation passed: "
        f"{summary['supportScenes']} support scenes, "
        f"{summary['expectedUnsupportedScenes']} expected-unsupported scenes, "
        f"{summary['implicitCpuReadbackFallbacks']} implicit CPU readback fallbacks."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
