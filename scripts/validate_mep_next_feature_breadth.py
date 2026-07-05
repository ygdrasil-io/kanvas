#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def fail(message: str):
    raise SystemExit(f"MEP-NEXT feature breadth validation failed: {message}")


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


def scene_by_id(generated):
    scenes = generated.get("scenes")
    require(isinstance(scenes, list), "generated results must contain scenes[]")
    return {scene.get("id"): scene for scene in scenes if isinstance(scene, dict)}


def require_file(root: Path, relative_path: str):
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_supported_scene(root: Path, scenes, scene_id: str):
    scene = scenes.get(scene_id)
    require(scene is not None, f"missing generated scene {scene_id}")
    require(scene.get("status") == "pass", f"{scene_id} must be pass")
    gpu_route = ((scene.get("gpu") or {}).get("route") or {})
    require(gpu_route.get("fallbackReason") == "none", f"{scene_id} must have fallbackReason=none")
    for suffix in ("skia.png", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"):
        require_file(root, f"reports/wgsl-pipeline/scenes/artifacts/{scene_id}/{suffix}")


def require_refusal(scenes, scene_id: str, expected_reason: str):
    scene = scenes.get(scene_id)
    require(scene is not None, f"missing refusal scene {scene_id}")
    require(scene.get("status") == "expected-unsupported", f"{scene_id} must be expected-unsupported")
    gpu_route = ((scene.get("gpu") or {}).get("route") or {})
    require(gpu_route.get("fallbackReason") == expected_reason, f"{scene_id} fallback reason changed")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    evidence_path = "reports/wgsl-pipeline/m89-feature-breadth/evidence.json"
    evidence = load_json(root, evidence_path)
    generated = load_json(root, "reports/wgsl-pipeline/scenes/generated/results.json")
    scenes = scene_by_id(generated)

    require(evidence.get("packId") == "m89-mep-next-feature-breadth-v1", "unexpected packId")
    require(evidence.get("status") == "pass", "evidence status must be pass")
    require(evidence.get("claimLevel") == "post-rc-mep-bounded-feature-breadth-evidence", "unexpected claimLevel")
    require(set(evidence.get("scopeIds", [])) == {"FOR-189", "FOR-190", "FOR-191", "FOR-192"}, "scope ids changed")
    require(evidence.get("sourceCommit") == "fbadbd3d4bd7ab8b86ffc2eabf01a02707b9068e", "RC-MEP source commit changed")

    dashboard = evidence.get("dashboardExpectation", {})
    require(dashboard.get("failRows") == 0, "dashboard failRows expectation must stay zero")
    require(dashboard.get("trackedGapRows") == 0, "dashboard trackedGapRows expectation must stay zero")

    required_supported = [
        "crop-image-filter-nonnull-prepass",
        "image-filter-compose-cf-matrix-transform",
        "clip-rect-difference",
        "path-aa-stroke-primitive",
        "bitmap-subset-local-matrix-repeat",
        "bitmap-shader-local-matrix",
        "runtime-effect-simple",
    ]
    for scene_id in required_supported:
        require_supported_scene(root, scenes, scene_id)

    require_refusal(scenes, "image-filter-crop-nonnull-prepass-required", "image-filter.crop-input-nonnull-prepass-required")
    require_refusal(scenes, "path-aa-dashing-edge-budget", "coverage.edge-count-exceeded")

    m79 = load_json(root, "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json")
    require(m79.get("packId") == "m79-bitmap-replay-v1", "M79 bitmap replay packId changed")
    require(m79.get("unsupportedBitmapReason") == "m79.bitmap.unsupported-sampler.mipmap", "M79 unsupported bitmap reason changed")

    m87 = load_json(root, "reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json")
    require(m87.get("packId") == "m87-runtime-effect-live-editing-v1", "M87 packId changed")
    require(((m87.get("effect") or {}).get("stableId")) == "runtime.simple_rt", "M87 stable runtime effect changed")
    require(((m87.get("reflectionValidation") or {}).get("layoutVerified")) is True, "M87 WGSL reflection must remain verified")
    require(((m87.get("liveRuntimeTelemetry") or {}).get("pipelineKeyStableAcrossUniformEdits")) is True, "M87 PipelineKey stability changed")
    refusal_reasons = {row.get("fallbackReason") for row in m87.get("stableRefusals", []) if isinstance(row, dict)}
    require({"runtime-effect.arbitrary-sksl-unsupported", "runtime-effect.wgsl-descriptor-missing"} <= refusal_reasons, "M87 stable runtime-effect refusals missing")

    m88 = load_json(root, "reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json")
    counters = m88.get("dashboardCounters", {})
    require(counters.get("failRows") == 0, "M88 matrix failRows must stay zero")
    require(counters.get("trackedGapRows") == 0, "M88 matrix trackedGapRows must stay zero")

    for source in evidence.get("sourceEvidence", []):
        require_file(root, source)
    for artifact in evidence.get("artifactPaths", []):
        require_file(root, artifact)

    validation_rows = evidence.get("validationRows")
    require(isinstance(validation_rows, list) and validation_rows, "validationRows missing")
    failed = [row.get("id", "<unknown>") for row in validation_rows if row.get("status") != "pass"]
    require(not failed, "validation rows failed: " + ", ".join(failed))

    non_claims = "\n".join(evidence.get("nonClaims", []))
    require("No SkSL compiler" in non_claims, "SkSL compiler non-claim missing")
    require("No global threshold" in non_claims, "threshold non-claim missing")

    print("MEP-NEXT feature breadth evidence validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
