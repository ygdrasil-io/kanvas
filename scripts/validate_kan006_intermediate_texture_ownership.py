#!/usr/bin/env python3
import json
import sys
from pathlib import Path


SCENE_ID = "m61-compose-cf-matrix-transform-dag-v2"
BASE_SCENE_ID = "image-filter-compose-cf-matrix-transform"
PREPASS_ROUTE = "webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix"
FINAL_ROUTE = "webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite"
SCRATCH_OWNER = "LayerCompositeDraw.materializeTargetTexture"
SCRATCH_VIEW_OWNER = "LayerCompositeDraw.materializeTargetView"


def fail(message: str):
    raise SystemExit(f"KAN-006 intermediate texture ownership validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str):
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets):
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        require(snippet in text, f"{relative_path} missing snippet: {snippet}")


def scene_from_contract(contract):
    scenes = contract.get("scenes")
    require(isinstance(scenes, list), "M61 contract must contain scenes[]")
    for scene in scenes:
        if isinstance(scene, dict) and scene.get("id") == SCENE_ID:
            return scene
    fail(f"missing scene {SCENE_ID} in M61 contract")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    contract_path = "reports/wgsl-pipeline/scenes/generated/m61-image-filter-dag-v2-promotion.json"
    report_path = "reports/wgsl-pipeline/2026-06-10-kan-006-intermediate-texture-ownership.md"
    route_gpu_path = f"reports/wgsl-pipeline/scenes/artifacts/{BASE_SCENE_ID}/route-gpu.json"
    route_prepass_path = f"reports/wgsl-pipeline/scenes/artifacts/{BASE_SCENE_ID}/route-prepass.json"
    stats_path = f"reports/wgsl-pipeline/scenes/artifacts/{BASE_SCENE_ID}/stats.json"
    device_path = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
    test_path = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SaveLayerImageFilterTest.kt"

    contract = load_json(root, contract_path)
    scene = scene_from_contract(contract)
    graph = scene.get("graphDiagnostics") or {}
    ownership = graph.get("ownership") or {}

    require(scene.get("status") == "pass", "M61 ownership scene must be pass")
    require(scene.get("fallbackReason") == "none", "M61 ownership scene must have fallbackReason=none")
    require(scene.get("baseArtifactScene") == BASE_SCENE_ID, "base artifact scene changed")
    require(graph.get("nodeCount") == 3, "node count must stay 3")
    require(graph.get("nodeBudget") == 4, "node budget must stay 4")
    require(graph.get("intermediateTextureCount") == 1, "intermediate texture count must stay 1")
    require(graph.get("intermediateTextureBudget") == 4, "intermediate texture budget must stay 4")
    require(graph.get("estimatedIntermediateBytes") == 4096, "estimated intermediate bytes must stay 4096")
    require(graph.get("fallbackReason") == "none", "graph fallbackReason must stay none")
    require(graph.get("nonClaim"), "graph non-claim must stay visible")

    require(ownership.get("intermediatesAllocated") == 1, "ownership must allocate exactly one intermediate")
    require(ownership.get("parentSurfaceOwned") is True, "parent surface ownership must be explicit")
    require(ownership.get("sourceSurface") == "gpuSrc.intermediateView", "source surface changed")
    require(ownership.get("scratchOwner") == SCRATCH_OWNER, "scratch texture owner changed")
    require(ownership.get("scratchViewOwner") == SCRATCH_VIEW_OWNER, "scratch view owner changed")
    require(ownership.get("scratchAllocator") == "SkWebGpuDevice.enqueueMaterializeMatrixTransformToScratch", "scratch allocator changed")
    require(ownership.get("scratchAllocationLabel") == "SkWebGpuDevice.materializeMatrixTransformTarget", "scratch allocation label changed")
    require(set(ownership.get("scratchUsage", [])) == {"RenderAttachment", "TextureBinding"}, "scratch usage must be render-attachment plus texture-binding")
    require(ownership.get("scratchLifetime") == "per-composite-dispatch", "scratch lifetime changed")
    require(ownership.get("writerPass") == "matrix-transform-prepass", "writer pass changed")
    require(ownership.get("writerRoute") == PREPASS_ROUTE, "writer route changed")
    require(ownership.get("readerPass") == "color-filter-final-composite", "reader pass changed")
    require(ownership.get("readerRoute") == FINAL_ROUTE, "reader route changed")
    require(ownership.get("releaseOwner") == "DrawResources", "release owner changed")
    require(ownership.get("releasePoint") == "closeDrawResources after command submission", "release point changed")
    require(ownership.get("releaseOrder") == ["materializeTargetView.close()", "materializeTargetTexture.close()"], "release order changed")
    require(ownership.get("cpuReadbackFallback") is False, "CPU readback fallback must remain false")
    require(ownership.get("newResourceLifecycle") is False, "KAN-006 must reuse existing resource lifecycle")
    lifecycle_stages = {row.get("stage") for row in ownership.get("lifecycle", []) if isinstance(row, dict)}
    require({"allocate", "write", "consume", "release"} <= lifecycle_stages, "ownership lifecycle must include allocate/write/consume/release")

    route_gpu = load_json(root, route_gpu_path)
    require(route_gpu.get("sceneId") == BASE_SCENE_ID, "route-gpu scene changed")
    require(route_gpu.get("selectedRoute") == FINAL_ROUTE, "route-gpu selected route changed")
    require(route_gpu.get("prepassRoute") == PREPASS_ROUTE, "route-gpu prepass route changed")
    require(route_gpu.get("scratchOwner") == SCRATCH_OWNER, "route-gpu scratch owner changed")
    require(route_gpu.get("scratchLifetime") == "per-composite-dispatch", "route-gpu scratch lifetime changed")
    require(route_gpu.get("materialiseStages") == 1, "route-gpu materialise stage count changed")
    require(route_gpu.get("fallbackReason") == "none", "route-gpu fallback must stay none")

    route_prepass = load_json(root, route_prepass_path)
    require(route_prepass.get("source") == "gpuSrc.intermediateView", "prepass source changed")
    require(route_prepass.get("destination") == SCRATCH_OWNER, "prepass destination changed")
    require(route_prepass.get("materializeRoute") == PREPASS_ROUTE, "prepass materialize route changed")
    require(route_prepass.get("finalComposite") == FINAL_ROUTE, "prepass final composite changed")
    require(route_prepass.get("fallbackReason") == "none", "prepass fallback must stay none")

    stats = load_json(root, stats_path)
    require(stats.get("pixels") == 1024, "stats pixels changed")
    require(stats.get("matchingPixels") == 1024, "stats matchingPixels changed")
    require(stats.get("gpuSimilarity") == 100.0, "GPU similarity must stay 100%")
    require(stats.get("selectedRoute") == FINAL_ROUTE, "stats selected route changed")
    require(stats.get("prepassRoute") == PREPASS_ROUTE, "stats prepass route changed")

    for path in (report_path, contract_path, route_gpu_path, route_prepass_path, stats_path, device_path, test_path):
        require_file(root, path)

    require_contains(root, report_path, [
        "DrawResources.closeDrawResources",
        "No arbitrary image-filter DAG scheduler is claimed.",
        "No new resource manager, runtime cache lane, CPU readback fallback",
    ])
    require_contains(root, device_path, [
        "label = \"SkWebGpuDevice.materializeMatrixTransformTarget\"",
        "usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding",
        "materializeTargetView?.close()",
        "materializeTargetTexture?.close()",
        "transfer ownership of the materialise scratch",
    ])
    require_contains(root, test_path, [
        "saveLayer with Compose ColorFilter MatrixTransform exposes M45 route diagnostics",
        "LayerCompositeDraw.materializeTargetTexture",
        "per-composite-dispatch",
    ])

    print("KAN-006 intermediate texture ownership validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
