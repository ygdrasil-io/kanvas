#!/usr/bin/env python3
import json
import sys
from pathlib import Path


SCENE_ID = "save-layer.image-filter.color-filter-matrix.v1"
SCOPE_ID = "kan-007.save-layer.simple-color-filter.v1"
GPU_ROUTE = "webgpu.image-filter.color-filter.layer-composite"
CPU_ROUTE = "cpu.save-layer.image-filter.color-filter-matrix"
ARTIFACT_DIR = "reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter"


def fail(message: str):
    raise SystemExit(f"KAN-007 SaveLayer simple filter validation failed: {message}")


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


def require_scene_common(payload, path: str):
    require(payload.get("sceneId") == SCENE_ID, f"{path} sceneId changed")
    require(payload.get("scopeId") == SCOPE_ID, f"{path} scopeId changed")
    require(payload.get("status") == "pass", f"{path} must remain pass")
    require(payload.get("supportClaim") is True, f"{path} must carry a scoped support claim")
    require(
        payload.get("supportScope") == "bounded-saveLayer-colorFilter-matrix-input-null",
        f"{path} support scope changed",
    )


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()

    report_path = "reports/wgsl-pipeline/2026-06-10-kan-007-savelayer-simple-filter.md"
    route_cpu_path = f"{ARTIFACT_DIR}/route-cpu.json"
    route_gpu_path = f"{ARTIFACT_DIR}/route-webgpu.json"
    stats_path = f"{ARTIFACT_DIR}/stats.json"
    device_path = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
    evidence_path = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleSaveLayerImageFilterSceneEvidence.kt"
    test_path = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleSaveLayerImageFilterSceneEvidenceTest.kt"

    for artifact in (
        "reference.png",
        "cpu.png",
        "webgpu.png",
        "cpu-diff.png",
        "webgpu-diff.png",
        "route-cpu.json",
        "route-webgpu.json",
        "stats.json",
    ):
        require_file(root, f"{ARTIFACT_DIR}/{artifact}")

    route_cpu = load_json(root, route_cpu_path)
    route_gpu = load_json(root, route_gpu_path)
    stats = load_json(root, stats_path)

    require_scene_common(route_cpu, route_cpu_path)
    require_scene_common(route_gpu, route_gpu_path)
    require_scene_common(stats, stats_path)

    require(route_cpu.get("backend") == "CPU", "CPU route backend changed")
    require(route_cpu.get("selectedRoute") == CPU_ROUTE, "CPU route changed")
    require(route_cpu.get("fallbackReason") == "none", "CPU fallback must stay none")
    require(route_gpu.get("backend") == "WebGPU", "WebGPU route backend changed")
    require(route_gpu.get("selectedRoute") == GPU_ROUTE, "WebGPU route changed")
    require(route_gpu.get("fallbackReason") == "none", "WebGPU fallback must stay none")
    require(route_gpu.get("imageFilterKind") == "ColorFilter", "image filter kind changed")
    require(route_gpu.get("colorFilterKind") == "Matrix", "color filter kind changed")
    require(route_gpu.get("fallbackPolicy") == "supported-via-layer-composite-color-filter-uniform", "fallback policy changed")

    diagnostics = route_gpu.get("routeDiagnostics") or {}
    require(diagnostics.get("selectedRoute") == GPU_ROUTE, "diagnostic selected route changed")
    require(diagnostics.get("prepassRoute") is None, "simple ColorFilter must not have a prepass route")
    require(diagnostics.get("scratchOwner") is None, "simple ColorFilter must not allocate scratch")
    require(diagnostics.get("scratchLifetime") is None, "simple ColorFilter must not report scratch lifetime")
    require(diagnostics.get("materialiseStages") == 0, "simple ColorFilter must have zero materialise stages")
    require(diagnostics.get("fallbackReason") is None, "simple ColorFilter diagnostic fallback must stay null")

    require(stats.get("cpuRouteIdentifier") == CPU_ROUTE, "stats CPU route changed")
    require(stats.get("webGpuRouteIdentifier") == GPU_ROUTE, "stats WebGPU route changed")
    require(stats.get("layerPixelCount") == 2560, "layer pixel count changed")
    require(stats.get("cpuNonBackgroundPixels") == 2560, "CPU visible pixel count changed")
    require(stats.get("webGpuNonBackgroundPixels") == 2560, "WebGPU visible pixel count changed")
    require(stats.get("globalThresholdChanged") is False, "global threshold must not change")
    require(stats.get("cpuComparison", {}).get("similarity", 0) >= stats.get("cpuSimilarityThreshold", 100), "CPU similarity below threshold")
    require(stats.get("webGpuComparison", {}).get("similarity", 0) >= stats.get("webGpuSimilarityThreshold", 100), "WebGPU similarity below threshold")

    non_claims = set(stats.get("nonClaims", []))
    for claim in (
        "no-arbitrary-layer-stack-claim",
        "no-multi-node-dag-claim",
        "no-broad-image-filter-claim",
        "no-cpu-readback-fallback-claim",
        "no-global-threshold-change",
    ):
        require(claim in non_claims, f"missing non-claim: {claim}")

    require_contains(root, report_path, [
        "SaveLayer + `SkImageFilters.ColorFilter(Matrix, input = null)`",
        "`webgpu.image-filter.color-filter.layer-composite`",
        "Aucune layer stack arbitraire",
        "Aucun DAG multi-node",
        "Aucun CPU readback fallback",
    ])
    require_contains(root, device_path, [
        "selectedSimpleColorFilterRoute",
        "webgpu.image-filter.color-filter.layer-composite",
        "materialiseStages = 0",
    ])
    require_contains(root, evidence_path, [
        "SimpleSaveLayerImageFilterSceneEvidence",
        "SkCanvas.saveLayer",
        "supported-via-layer-composite-color-filter-uniform",
        "no-arbitrary-layer-stack-claim",
    ])
    require_contains(root, test_path, [
        "simple saveLayer color filter emits reference cpu gpu diff stats and route diagnostics",
        "routeDiagnostics.selectedRoute",
        "materialiseStages",
    ])

    print("KAN-007 SaveLayer simple filter validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
