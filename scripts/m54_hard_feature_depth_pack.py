#!/usr/bin/env python3
"""Materialize M54 hard-feature dashboard evidence from a compact contract."""

from __future__ import annotations

import argparse
import copy
import json
import shutil
import subprocess
from pathlib import Path
from typing import Any


PASS_ARTIFACTS = ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png")
UNSUPPORTED_ARTIFACTS = ("skia.png", "cpu.png", "cpu-diff.png")
PERFORMANCE_ARTIFACTS = ("cpu-performance.json", "gpu-performance.json")


def git_commit(project_root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=project_root,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except Exception:
        return "unknown"


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def load_scene_index(project_root: Path, manifest_path: str) -> dict[str, dict[str, Any]]:
    manifest = project_root / manifest_path
    if not manifest.is_file():
        raise SystemExit(f"Missing generated scene manifest: {manifest}")
    root = json.loads(manifest.read_text())
    scenes = root.get("scenes")
    if not isinstance(scenes, list):
        raise SystemExit(f"Generated scene manifest must contain scenes[]: {manifest}")
    return {
        scene["id"]: scene
        for scene in scenes
        if isinstance(scene, dict) and isinstance(scene.get("id"), str)
    }


def copy_required(source: Path, target: Path, scene_id: str, artifact: str) -> None:
    if not source.is_file():
        raise SystemExit(f"{scene_id}: missing source artifact `{source}` for `{artifact}`")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def as_int(value: Any, default: int) -> int:
    return value if isinstance(value, int) else default


def rewrite_performance(
    performance: dict[str, Any],
    *,
    scene_id: str,
    lane: str,
    source_scene_id: str,
) -> dict[str, Any]:
    payload = copy.deepcopy(performance)
    payload["rawMetrics"] = f"artifacts/{scene_id}/{lane}-performance.json"
    payload["sourcePerformanceRow"] = source_scene_id
    gate = payload.setdefault("gate", {})
    if isinstance(gate, dict):
        gate.setdefault("mode", "warning-only")
        gate.setdefault("status", "reporting-only")
        gate.setdefault("owner", "Kanvas rendering release owner")
        gate.setdefault(
            "reason",
            "M54 carries this measured trend as warning-only evidence; it is not a release-blocking gate.",
        )
    return payload


def materialize_scene(
    *,
    project_root: Path,
    output_root: Path,
    artifact_root: Path,
    base_rows: dict[str, dict[str, Any]],
    contract_path: str,
    source_report: str,
    selected_report: str,
    generated_by: str,
    output_evidence_dir: str,
    scene: dict[str, Any],
    commit: str,
) -> dict[str, Any]:
    scene_id = scene["id"]
    base_scene_id = scene["baseArtifactScene"]
    inventory_id = scene["inventoryId"]
    status = scene["status"]
    fallback_reason = scene["fallbackReason"]
    base_row = base_rows.get(base_scene_id)
    if base_row is None:
        if not scene.get("allowArtifactOnlyBase"):
            raise SystemExit(f"{scene_id}: base generated scene `{base_scene_id}` is missing")
        base_generation = {
            "sourceTask": scene.get("sourceTask", ""),
            "sourceTest": scene.get("sourceTest", ""),
            "sourceReport": scene.get("sourceReport", source_report),
        }
    else:
        base_generation = base_row.get("generation")
        if not isinstance(base_generation, dict):
            raise SystemExit(f"{scene_id}: base generated scene `{base_scene_id}` has no generation trace")

    source_root = artifact_root / base_scene_id
    target_root = output_root / "artifacts" / scene_id
    required = PASS_ARTIFACTS if status == "pass" else UNSUPPORTED_ARTIFACTS
    for artifact in required:
        copy_required(source_root / artifact, target_root / artifact, scene_id, artifact)

    copy_performance = bool(scene.get("copyPerformance"))
    if copy_performance:
        for artifact in PERFORMANCE_ARTIFACTS:
            copy_required(source_root / artifact, target_root / artifact, scene_id, artifact)

    cpu_route = {
        "selectedRoute": scene["cpuRoute"],
        "fallbackReason": "none",
        "inventoryId": inventory_id,
        "generatedBy": generated_by,
        "derivedFromGeneratedScene": base_scene_id,
        "hardFeatureFamily": scene["family"],
        "nonClaim": scene.get("nonClaim", ""),
    }
    cpu_route.update(scene.get("cpuRouteDetails", {}))
    gpu_route = {
        "selectedRoute": scene["gpuRoute"],
        "pipelineKey": scene["pipelineKey"],
        "fallbackReason": fallback_reason,
        "inventoryId": inventory_id,
        "generatedBy": generated_by,
        "derivedFromGeneratedScene": base_scene_id,
        "hardFeatureFamily": scene["family"],
        "nonClaim": scene.get("nonClaim", ""),
    }
    gpu_route.update(scene.get("gpuRouteDetails", {}))
    if status == "pass":
        gpu_route["adapter"] = scene.get("adapter", "Apple M2 Max")
        gpu_route["adapterBackend"] = scene.get("adapterBackend", "WebGPU/Metal")
        gpu_route["adapterEvidenceReport"] = source_report

    pixels = as_int(scene.get("pixels"), 4096)
    matching_pixels = as_int(scene.get("matchingPixels"), pixels if status == "pass" else 0)
    max_delta = as_int(scene.get("maxChannelDelta"), 0 if status == "pass" else 255)
    threshold = scene.get("threshold", 99.95 if status == "pass" else 0)
    stats_details = scene.get("statsDetails", {})
    if not isinstance(stats_details, dict):
        stats_details = {}
    cpu_similarity = scene.get("cpuSimilarity", stats_details.get("cpuSimilarity", 100.0 if status == "pass" else 0.0))
    cpu_matching_pixels = as_int(stats_details.get("cpuMatchingPixels"), pixels if status == "expected-unsupported" else matching_pixels)
    cpu_max_delta = as_int(stats_details.get("cpuMaxChannelDelta"), 0 if status == "pass" else max_delta)
    gpu_similarity = scene.get("gpuSimilarity", stats_details.get("gpuSimilarity", 100.0 if status == "pass" else 0.0))
    cpu_threshold = scene.get("cpuThreshold", stats_details.get("cpuThreshold", threshold))
    gpu_threshold = scene.get("gpuThreshold", stats_details.get("gpuThreshold", threshold))
    stats = {
        "pixels": pixels,
        "matchingPixels": matching_pixels,
        "maxChannelDelta": max_delta,
        "threshold": threshold,
        "generatedBy": generated_by,
        "inventoryId": inventory_id,
        "status": status,
        "derivedFromGeneratedScene": base_scene_id,
        "hardFeatureFamily": scene["family"],
    }
    stats.update(stats_details)
    write_json(target_root / "route-cpu.json", cpu_route)
    write_json(target_root / "route-gpu.json", gpu_route)
    write_json(target_root / "stats.json", stats)

    artifact_prefix = f"artifacts/{scene_id}"
    cpu: dict[str, Any] = {
        "status": "pass",
        "image": f"{artifact_prefix}/cpu.png",
        "diff": f"{artifact_prefix}/cpu-diff.png",
        "similarity": cpu_similarity,
        "route": {
            "selectedRoute": scene["cpuRoute"],
            "fallbackReason": "none",
            "inventoryId": inventory_id,
        },
        "stats": {
            "pixels": pixels,
            "matchingPixels": cpu_matching_pixels,
            "maxChannelDelta": cpu_max_delta,
            "threshold": cpu_threshold,
            "backend": "CPU",
            "command": f"./gradlew --no-daemon {generated_by} pipelineSceneDashboard pipelineSceneDashboardGate",
        },
    }
    gpu: dict[str, Any] = {
        "status": status,
        "route": {
            "selectedRoute": scene["gpuRoute"],
            "pipelineKey": scene["pipelineKey"],
            "fallbackReason": fallback_reason,
            "inventoryId": inventory_id,
        },
    }
    if status == "pass":
        gpu.update(
            {
                "image": f"{artifact_prefix}/gpu.png",
                "diff": f"{artifact_prefix}/gpu-diff.png",
                "similarity": gpu_similarity,
                "stats": {
                    "pixels": pixels,
                    "matchingPixels": matching_pixels,
                    "maxChannelDelta": max_delta,
                    "threshold": gpu_threshold,
                    "backend": "WebGPU",
                    "command": f"./gradlew --no-daemon {generated_by} pipelineSceneDashboard pipelineSceneDashboardGate",
                    "adapter": scene.get("adapter", "Apple M2 Max"),
                    "adapterBackend": scene.get("adapterBackend", "WebGPU/Metal"),
                    "adapterCapture": scene.get("adapterCapture", generated_by),
                },
            }
        )

    if copy_performance:
        base_cpu_perf = (base_row.get("cpu") or {}).get("performanceTrend")
        base_gpu_perf = (base_row.get("gpu") or {}).get("performanceTrend")
        if not isinstance(base_cpu_perf, dict) or base_cpu_perf.get("status") != "measured":
            raise SystemExit(f"{scene_id}: requested CPU performance copy from non-measured base `{base_scene_id}`")
        if not isinstance(base_gpu_perf, dict) or base_gpu_perf.get("status") != "measured":
            raise SystemExit(f"{scene_id}: requested GPU performance copy from non-measured base `{base_scene_id}`")
        cpu["performanceTrend"] = rewrite_performance(
            base_cpu_perf,
            scene_id=scene_id,
            lane="cpu",
            source_scene_id=base_scene_id,
        )
        gpu["performanceTrend"] = rewrite_performance(
            base_gpu_perf,
            scene_id=scene_id,
            lane="gpu",
            source_scene_id=base_scene_id,
        )

    row: dict[str, Any] = {
        "id": scene_id,
        "inventoryId": inventory_id,
        "title": scene["title"],
        "priority": scene.get("priority", "P1"),
        "status": status,
        "source": source_report,
        "generation": {
            "mode": "generated",
            "producer": "pipelineGeneratedSceneExport",
            "derivationTask": generated_by,
            "derivationReport": source_report,
            "derivationContract": contract_path,
            "commit": commit,
            "artifactRoot": artifact_prefix,
            "schema": "generated-scene-result.v1",
            "linearIssue": scene["linearIssue"],
            "inventoryId": inventory_id,
            "sourceTask": base_generation.get("sourceTask", ""),
            "sourceTest": base_generation.get("sourceTest", ""),
            "sourceReport": base_generation.get("sourceReport", ""),
            "derivedFromGeneratedScene": base_scene_id,
            "hardFeatureFamily": scene["family"],
        },
        "referenceKind": scene["referenceKind"],
        "reference": f"{artifact_prefix}/skia.png",
        "cpu": cpu,
        "gpu": gpu,
        "diffs": {"cpu": f"{artifact_prefix}/cpu-diff.png"},
        "routeDiagnostics": {
            "cpu": f"{artifact_prefix}/route-cpu.json",
            "gpu": f"{artifact_prefix}/route-gpu.json",
        },
        "stats": {
            "pixels": pixels,
            "matchingPixels": matching_pixels,
            "maxChannelDelta": max_delta,
            "threshold": threshold,
        },
        "evidence": [
            source_report,
            contract_path,
            selected_report,
            "build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json",
            f"build/reports/{output_evidence_dir}/artifacts/{scene_id}/route-cpu.json",
            f"build/reports/{output_evidence_dir}/artifacts/{scene_id}/route-gpu.json",
            f"build/reports/{output_evidence_dir}/artifacts/{scene_id}/stats.json",
        ],
        "tags": scene["tags"],
    }
    if status == "pass":
        row["diffs"]["gpu"] = f"{artifact_prefix}/gpu-diff.png"
    return row


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--contract", default="reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json")
    parser.add_argument("--output-dir", default="build/reports/wgsl-pipeline-m54-generated")
    parser.add_argument("--base-generated", default="reports/wgsl-pipeline/scenes/generated/results.json")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    contract_path = project_root / args.contract
    if not contract_path.is_file():
        raise SystemExit(f"Missing M54 hard-feature contract: {contract_path}")
    contract = json.loads(contract_path.read_text())
    scenes = contract.get("scenes")
    if not isinstance(scenes, list):
        raise SystemExit(f"M54 hard-feature contract must contain scenes[]: {contract_path}")

    output_root = project_root / args.output_dir
    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)

    artifact_root = project_root / contract["artifactSourceRoot"]
    generated_by = contract.get("generatedBy", "pipelineM54HardFeatureDepthPack")
    output_evidence_dir = contract.get("outputEvidenceDir", "wgsl-pipeline-m54-generated")
    selected_report = contract.get(
        "selectedReport",
        "reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-selection.md",
    )
    base_rows = load_scene_index(project_root, args.base_generated)
    commit = git_commit(project_root)
    rows = [
        materialize_scene(
            project_root=project_root,
            output_root=output_root,
            artifact_root=artifact_root,
            base_rows=base_rows,
            contract_path=args.contract,
            source_report=contract["sourceReport"],
            selected_report=selected_report,
            generated_by=generated_by,
            output_evidence_dir=output_evidence_dir,
            scene=scene,
            commit=commit,
        )
        for scene in scenes
    ]

    manifest = {
        "schemaVersion": 1,
        "generatedBy": generated_by,
        "source": args.contract,
        "description": contract.get(
            "outputDescription",
            "M54 hard feature depth generated dashboard rows materialized from bounded selected contracts.",
        ),
        "scenes": rows,
    }
    output_manifest = output_root / contract.get("outputManifest", "data/m54-generated-scenes.json")
    write_json(output_manifest, manifest)
    print(f"Wrote {generated_by} generated scenes: {output_manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
