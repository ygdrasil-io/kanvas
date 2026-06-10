#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DAG_REASON = "image-filter.dag-or-picture-prepass-required"
CROP_REASON = "image-filter.crop-input-nonnull-prepass-required"
SUPPORTED_DAG_ID = "m61-compose-cf-matrix-transform-dag-v2"
CROP_REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"


def fail(message: str):
    raise SystemExit(f"KAN-008 image-filter DAG refusal validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]):
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        require(snippet in text, f"{relative_path} missing snippet: {snippet}")


def find_by_id(payload: Any, scene_id: str) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []

    def walk(value: Any):
        if isinstance(value, dict):
            if value.get("id") == scene_id:
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


def require_non_claim(text: Any, context: str):
    require(isinstance(text, str) and text.strip(), f"{context} missing non-claim")
    lowered = text.lower()
    require("claim" in lowered, f"{context} non-claim must explicitly discuss claims")
    require("arbitrary image-filter dag" in lowered, f"{context} must refuse arbitrary image-filter DAG support")


def require_graph_refusal(scene: dict[str, Any], scene_id: str, expected_nodes: int):
    require(scene.get("status") == "expected-unsupported", f"{scene_id} must stay expected-unsupported")
    require(scene.get("supportClaim") is not True, f"{scene_id} must not carry a support claim")

    fallback = scene.get("fallbackReason")
    if fallback is None:
        gpu = scene.get("gpu") or {}
        route = gpu.get("route") if isinstance(gpu, dict) else {}
        fallback = route.get("fallbackReason") if isinstance(route, dict) else None
    require(fallback == DAG_REASON, f"{scene_id} fallbackReason changed: {fallback}")

    graph = scene.get("graphDiagnostics")
    require(isinstance(graph, dict), f"{scene_id} must keep embedded graphDiagnostics")
    require(graph.get("sceneId") == scene_id, f"{scene_id} graph sceneId changed")
    require(graph.get("status") == "expected-unsupported", f"{scene_id} graph status changed")
    require(graph.get("fallbackReason") == DAG_REASON, f"{scene_id} graph fallbackReason changed")
    require(graph.get("nodeCount") == expected_nodes, f"{scene_id} node count changed")
    require(graph.get("nodeBudget") == 4, f"{scene_id} node budget changed")
    require(graph.get("childrenPerNodeBudget") == 2, f"{scene_id} children-per-node budget changed")
    require(graph.get("intermediateTextureCount") == 0, f"{scene_id} must not allocate intermediates")
    require(graph.get("intermediateTextureBudget") == 4, f"{scene_id} intermediate budget changed")
    require(graph.get("estimatedIntermediateBytes") == 0, f"{scene_id} must not estimate intermediate bytes")
    require(graph.get("passOrder") == [], f"{scene_id} expected-unsupported row must not expose a pass order")
    require_non_claim(graph.get("nonClaim"), f"{scene_id} graph")
    require_non_claim(scene.get("nonClaim") or graph.get("nonClaim"), scene_id)

    nodes = graph.get("nodes")
    require(isinstance(nodes, list) and len(nodes) == expected_nodes, f"{scene_id} graph nodes changed")
    support_values = {node.get("support") for node in nodes if isinstance(node, dict)}
    require("unsupported" in support_values, f"{scene_id} must keep one unsupported root node")
    require("blocked-by-input" in support_values, f"{scene_id} must keep blocked-by-input dependent nodes")
    require(any("picture" in str(node.get("kind", "")).lower() for node in nodes), f"{scene_id} must expose picture-prepass boundary")

    ownership = graph.get("ownership")
    require(isinstance(ownership, dict), f"{scene_id} graph ownership missing")
    if "intermediatesAllocated" in ownership:
        require(ownership.get("intermediatesAllocated") == 0, f"{scene_id} must allocate zero intermediates")
        require(ownership.get("parentSurfaceOwned") is False, f"{scene_id} must not claim parent surface ownership")
    else:
        require("none allocated" in str(ownership.get("intermediateTextures", "")).lower(), f"{scene_id} must document no intermediates")
        require("not owned" in str(ownership.get("parentSurface", "")).lower(), f"{scene_id} must document no parent ownership")

    tags = set(scene.get("tags", []))
    if tags:
        require("risk.expected-unsupported" in tags, f"{scene_id} must stay marked as expected-unsupported risk")
        require("risk.none" not in tags, f"{scene_id} must not be marked risk.none")


def require_crop_refusal(root: Path):
    results = load_json(root, "reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_by_id(results, CROP_REFUSAL_ID)
    require(scene.get("status") == "expected-unsupported", "Crop refusal must stay expected-unsupported")
    require(scene.get("supportClaim") is not True, "Crop refusal must not carry a support claim")
    stats = scene.get("stats") or {}
    require(stats.get("threshold") == 0, "Crop refusal dashboard threshold must stay zero")
    require(stats.get("pixels") == 0, "Crop refusal dashboard pixel count must stay zero")

    route_gpu = load_json(root, "reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-gpu.json")
    route_cpu = load_json(root, "reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-cpu.json")
    artifact_stats = load_json(root, "reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/stats.json")

    require(route_gpu.get("sceneId") == CROP_REFUSAL_ID, "Crop GPU route scene changed")
    require(route_gpu.get("status") == "expected-unsupported", "Crop GPU route status changed")
    require(route_gpu.get("coverageStrategy") == "webgpu.image-filter.refuse", "Crop GPU route changed")
    require(route_gpu.get("fallbackReason") == CROP_REASON, "Crop GPU fallback reason changed")
    require_non_claim(route_gpu.get("nonClaim"), "Crop GPU route")

    require(route_cpu.get("sceneId") == CROP_REFUSAL_ID, "Crop CPU route scene changed")
    require(route_cpu.get("status") == "pass", "Crop CPU oracle route changed")
    require(route_cpu.get("fallbackReason") == "none", "Crop CPU route fallback changed")
    require("outsideSelectedM38Shape=true" in str(route_cpu.get("coveragePlan", "")), "Crop CPU route must identify out-of-scope shape")
    require_non_claim(route_cpu.get("nonClaim"), "Crop CPU route")

    require(artifact_stats.get("status") == "expected-unsupported", "Crop stats status changed")
    require(artifact_stats.get("fallbackReason") == CROP_REASON, "Crop stats fallback reason changed")
    require(artifact_stats.get("threshold") == 0.0, "Crop stats threshold changed")


def require_supported_boundary(root: Path):
    contract = load_json(root, "reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json")
    scene = find_by_id(contract, SUPPORTED_DAG_ID)
    require(scene.get("status") == "pass", "M61 bounded DAG support row must stay pass")
    require(scene.get("fallbackReason") == "none", "M61 bounded DAG support row fallback changed")
    require(scene.get("supportClaim") is not False, "M61 bounded DAG row must not be turned into refusal")
    graph = scene.get("graphDiagnostics")
    require(isinstance(graph, dict), "M61 bounded DAG graphDiagnostics missing")
    require(graph.get("status") == "pass", "M61 bounded DAG graph status changed")
    require(graph.get("nodeCount") == 3, "M61 bounded DAG node count changed")
    require(graph.get("intermediateTextureCount") == 1, "M61 bounded DAG intermediate count changed")
    require(graph.get("passOrder") == ["matrix-transform-prepass", "color-filter-final-composite"], "M61 bounded DAG pass order changed")
    require_non_claim(graph.get("nonClaim"), "M61 bounded DAG graph")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    m52 = load_json(root, "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json")
    m54 = load_json(root, "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json")

    require_graph_refusal(find_by_id(m52, "m52-big-tile-image-filter-dag-refusal"), "m52-big-tile-image-filter-dag-refusal", 3)
    require_graph_refusal(find_by_id(m54, "m54-imagefilters-graph-boundary"), "m54-imagefilters-graph-boundary", 4)
    require_crop_refusal(root)
    require_supported_boundary(root)

    for path in (
        "reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md",
        "reports/wgsl-pipeline/2026-06-01-m61-image-filter-dag-diagnostics.md",
        "reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md",
    ):
        require_file(root, path)

    require_contains(root, "reports/wgsl-pipeline/2026-06-10-kan-008-image-filter-dag-refusals.md", [
        "m52-big-tile-image-filter-dag-refusal",
        "m54-imagefilters-graph-boundary",
        "image-filter-crop-nonnull-prepass-required",
        DAG_REASON,
        CROP_REASON,
        "No broad DAG support is claimed.",
        "PM message",
    ])
    require_contains(root, "reports/wgsl-pipeline/2026-06-01-m61-image-filter-dag-diagnostics.md", [
        "expected-unsupported",
        "graph artifact",
        "This does not implement arbitrary image-filter DAG scheduling.",
    ])
    require_contains(root, "reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md", [
        "`webgpu.image-filter.color-filter.layer-composite`",
        "Aucun DAG multi-node",
    ])

    print("KAN-008 image-filter DAG refusal validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
