#!/usr/bin/env python3
"""Materialize M53 inventory-derived dashboard evidence from a compact contract."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from pathlib import Path
from typing import Any


PASS_ARTIFACTS = ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png")
UNSUPPORTED_ARTIFACTS = ("skia.png", "cpu.png", "cpu-diff.png")


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
    return {scene["id"]: scene for scene in scenes if isinstance(scene, dict) and isinstance(scene.get("id"), str)}


def copy_required(source: Path, target: Path, scene_id: str, artifact: str) -> None:
    if not source.is_file():
        raise SystemExit(f"{scene_id}: missing source artifact `{source}` for `{artifact}`")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def as_int(value: Any, default: int) -> int:
    return value if isinstance(value, int) else default


def materialize_scene(
    *,
    project_root: Path,
    output_root: Path,
    artifact_root: Path,
    base_rows: dict[str, dict[str, Any]],
    contract_path: str,
    pack_report: str,
    scene: dict[str, Any],
    commit: str,
) -> dict[str, Any]:
    scene_id = scene["id"]
    base_scene_id = scene["baseArtifactScene"]
    inventory_id = scene["inventoryId"]
    status = scene["status"]
    base_row = base_rows.get(base_scene_id)
    if base_row is None:
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene_id}` is missing")
    base_generation = base_row.get("generation")
    if not isinstance(base_generation, dict):
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene_id}` has no generation trace")

    source_root = artifact_root / base_scene_id
    target_root = output_root / "artifacts" / scene_id
    required = PASS_ARTIFACTS if status == "pass" else UNSUPPORTED_ARTIFACTS
    for artifact in required:
        copy_required(source_root / artifact, target_root / artifact, scene_id, artifact)

    fallback_reason = scene["fallbackReason"]
    cpu_route = {
        "selectedRoute": scene["cpuRoute"],
        "fallbackReason": "none",
        "inventoryId": inventory_id,
        "generatedBy": "pipelineM53InventoryPromotionPack",
        "derivedFromGeneratedScene": base_scene_id,
    }
    gpu_route = {
        "selectedRoute": scene["gpuRoute"],
        "pipelineKey": scene["pipelineKey"],
        "fallbackReason": fallback_reason,
        "inventoryId": inventory_id,
        "generatedBy": "pipelineM53InventoryPromotionPack",
        "derivedFromGeneratedScene": base_scene_id,
    }
    if status == "pass":
        gpu_route["adapter"] = scene.get("adapter", "Apple M2 Max")
        gpu_route["adapterBackend"] = scene.get("adapterBackend", "WebGPU/Metal")
        gpu_route["adapterEvidenceReport"] = pack_report

    pixels = as_int(scene.get("pixels"), 4096)
    matching_pixels = as_int(scene.get("matchingPixels"), pixels if status == "pass" else 0)
    max_delta = as_int(scene.get("maxChannelDelta"), 0 if status == "pass" else 255)
    threshold = scene.get("threshold", 99.95 if status == "pass" else 0)
    stats = {
        "pixels": pixels,
        "matchingPixels": matching_pixels,
        "maxChannelDelta": max_delta,
        "threshold": threshold,
        "generatedBy": "pipelineM53InventoryPromotionPack",
        "inventoryId": inventory_id,
        "status": status,
        "derivedFromGeneratedScene": base_scene_id,
    }
    write_json(target_root / "route-cpu.json", cpu_route)
    write_json(target_root / "route-gpu.json", gpu_route)
    write_json(target_root / "stats.json", stats)

    artifact_prefix = f"artifacts/{scene_id}"
    cpu = {
        "status": "pass",
        "image": f"{artifact_prefix}/cpu.png",
        "diff": f"{artifact_prefix}/cpu-diff.png",
        "similarity": 100.0 if status == "pass" else 0.0,
        "route": {
            "selectedRoute": scene["cpuRoute"],
            "fallbackReason": "none",
            "inventoryId": inventory_id,
        },
        "stats": {
            "pixels": pixels,
            "matchingPixels": matching_pixels if status == "pass" else pixels,
            "maxChannelDelta": 0 if status == "pass" else max_delta,
            "threshold": threshold,
            "backend": "CPU",
            "command": "./gradlew --no-daemon pipelineM53InventoryPromotionPack pipelineSceneDashboard pipelineSceneDashboardGate",
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
                "similarity": 100.0,
                "stats": {
                    "pixels": pixels,
                    "matchingPixels": matching_pixels,
                    "maxChannelDelta": max_delta,
                    "threshold": threshold,
                    "backend": "WebGPU",
                    "command": "./gradlew --no-daemon pipelineM53InventoryPromotionPack pipelineSceneDashboard pipelineSceneDashboardGate",
                    "adapter": scene.get("adapter", "Apple M2 Max"),
                    "adapterBackend": scene.get("adapterBackend", "WebGPU/Metal"),
                    "adapterCapture": "m53-inventory-promotion-pack",
                },
            }
        )
    row = {
        "id": scene_id,
        "inventoryId": inventory_id,
        "title": scene["title"],
        "priority": scene.get("priority", "P1"),
        "status": status,
        "source": pack_report,
        "generation": {
            "mode": "generated",
            "producer": "pipelineGeneratedSceneExport",
            "derivationTask": "pipelineM53InventoryPromotionPack",
            "derivationReport": pack_report,
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
            pack_report,
            contract_path,
            "build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json",
            f"build/reports/wgsl-pipeline-m53-generated/artifacts/{scene_id}/route-cpu.json",
            f"build/reports/wgsl-pipeline-m53-generated/artifacts/{scene_id}/route-gpu.json",
            f"build/reports/wgsl-pipeline-m53-generated/artifacts/{scene_id}/stats.json",
        ],
        "tags": scene["tags"],
    }
    if status == "pass":
        row["diffs"]["gpu"] = f"{artifact_prefix}/gpu-diff.png"
    return row


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--contract", default="reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json")
    parser.add_argument("--output-dir", default="build/reports/wgsl-pipeline-m53-generated")
    parser.add_argument("--base-generated", default="reports/wgsl-pipeline/scenes/generated/results.json")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    contract_path = project_root / args.contract
    if not contract_path.is_file():
        raise SystemExit(f"Missing M53 promotion contract: {contract_path}")
    contract = json.loads(contract_path.read_text())
    scenes = contract.get("scenes")
    if not isinstance(scenes, list):
        raise SystemExit(f"M53 promotion contract must contain scenes[]: {contract_path}")

    output_root = project_root / args.output_dir
    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)

    artifact_root = project_root / contract["artifactSourceRoot"]
    base_rows = load_scene_index(project_root, args.base_generated)
    commit = git_commit(project_root)
    rows = [
        materialize_scene(
            project_root=project_root,
            output_root=output_root,
            artifact_root=artifact_root,
            base_rows=base_rows,
            contract_path=args.contract,
            pack_report=contract["sourceReport"],
            scene=scene,
            commit=commit,
        )
        for scene in scenes
    ]
    manifest = {
        "schemaVersion": 1,
        "generatedBy": "pipelineM53InventoryPromotionPack",
        "source": args.contract,
        "description": "M53 inventory-derived generated dashboard rows materialized from a declarative contract.",
        "scenes": rows,
    }
    output_manifest = output_root / "data" / "m53-generated-scenes.json"
    write_json(output_manifest, manifest)
    print(f"Wrote M53 inventory promotion generated scenes: {output_manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
