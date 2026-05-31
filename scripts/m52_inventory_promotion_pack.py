#!/usr/bin/env python3
"""Materialize inventory-derived dashboard evidence from a contract file.

The promotion pack is a layer over existing generated dashboard evidence:
each selected inventory row must name a generated base scene. This task verifies
that base row, carries forward its real generation trace, and writes a distinct
inventory-derived row plus build-local artifacts. It must not invent tests or
turn inventory-only rows into support claims.
"""

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


def copy_required(source: Path, target: Path, scene_id: str, name: str) -> None:
    if not source.is_file():
        raise SystemExit(f"{scene_id}: missing source artifact `{source}` for `{name}`")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def load_generated_scene_index(project_root: Path, manifest_path: str) -> dict[str, dict[str, Any]]:
    path = project_root / manifest_path
    if not path.is_file():
        raise SystemExit(f"Missing generated scene manifest: {path}")
    root = json.loads(path.read_text())
    scenes = root.get("scenes")
    if not isinstance(scenes, list):
        raise SystemExit(f"Generated scene manifest must contain scenes[]: {path}")
    index: dict[str, dict[str, Any]] = {}
    for scene in scenes:
        if isinstance(scene, dict) and isinstance(scene.get("id"), str):
            index[scene["id"]] = scene
    return index


def require_generated_base(scene_id: str, base_scene: str, base_rows: dict[str, dict[str, Any]]) -> dict[str, Any]:
    base_row = base_rows.get(base_scene)
    if base_row is None:
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene}` is not present in generated results")
    generation = base_row.get("generation")
    if not isinstance(generation, dict):
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene}` has no generation trace")
    missing = [
        field for field in ("producer", "commit", "artifactRoot", "schema")
        if not isinstance(generation.get(field), str) or not generation[field]
    ]
    if missing:
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene}` is missing generation fields: {', '.join(missing)}")
    if not any(isinstance(generation.get(field), str) and generation[field] for field in ("sourceTask", "sourceTest", "sourceReport")):
        raise SystemExit(f"{scene_id}: base generated scene `{base_scene}` has no sourceTask/sourceTest/sourceReport trace")
    return base_row


def materialize_scene(
    *,
    project_root: Path,
    output_root: Path,
    source_artifact_root: Path,
    base_rows: dict[str, dict[str, Any]],
    scene_contract: dict[str, Any],
    commit: str,
    derivation_task: str,
    derivation_contract: str,
    output_artifact_prefix: str,
) -> dict[str, Any]:
    row = json.loads(json.dumps(scene_contract["row"]))
    scene_id = row["id"]
    status = row["status"]
    base_scene = scene_contract["baseArtifactScene"]
    base_row = require_generated_base(scene_id, base_scene, base_rows)
    base_generation = base_row["generation"]
    base_root = source_artifact_root / base_scene
    target_root = output_root / "artifacts" / scene_id

    if not base_root.is_dir():
        raise SystemExit(f"{scene_id}: missing base artifact scene `{base_root}`")

    required = PASS_ARTIFACTS if status == "pass" else UNSUPPORTED_ARTIFACTS
    for name in required:
        copy_required(base_root / name, target_root / name, scene_id, name)

    font_diagnostics = base_root / "font-diagnostics.json"
    if font_diagnostics.is_file():
        copy_required(font_diagnostics, target_root / "font-diagnostics.json", scene_id, "font-diagnostics.json")

    generation = row.setdefault("generation", {})
    generation["commit"] = commit
    generation["producer"] = "pipelineGeneratedSceneExport"
    generation["sourceTask"] = base_generation.get("sourceTask", "")
    generation["sourceTest"] = base_generation.get("sourceTest", "")
    generation["sourceReport"] = base_generation.get("sourceReport", "")
    generation["artifactRoot"] = f"artifacts/{scene_id}"
    generation["derivedFromGeneratedScene"] = base_scene
    generation["derivationTask"] = derivation_task
    generation["derivationContract"] = derivation_contract

    route_cpu = dict(row["cpu"]["route"])
    route_cpu["generatedBy"] = derivation_task
    route_cpu["derivedFromGeneratedScene"] = base_scene
    write_json(target_root / "route-cpu.json", route_cpu)

    route_gpu = dict(row["gpu"]["route"])
    route_gpu["generatedBy"] = derivation_task
    route_gpu["derivedFromGeneratedScene"] = base_scene
    write_json(target_root / "route-gpu.json", route_gpu)

    stats = dict(row["stats"])
    stats["generatedBy"] = derivation_task
    stats["inventoryId"] = row["inventoryId"]
    stats["status"] = status
    stats["derivedFromGeneratedScene"] = base_scene
    write_json(target_root / "stats.json", stats)

    row["evidence"] = [
        evidence for evidence in row.get("evidence", [])
        if not evidence.startswith("reports/wgsl-pipeline/scenes/artifacts/")
    ] + [
        derivation_contract,
        f"{output_artifact_prefix}/artifacts/{scene_id}/route-cpu.json",
        f"{output_artifact_prefix}/artifacts/{scene_id}/route-gpu.json",
        f"{output_artifact_prefix}/artifacts/{scene_id}/stats.json",
    ]

    return row


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument(
        "--contract",
        default="reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    )
    parser.add_argument(
        "--output-dir",
        default="build/reports/wgsl-pipeline-m52-generated",
    )
    parser.add_argument(
        "--base-generated",
        default="reports/wgsl-pipeline/scenes/generated/results.json",
    )
    parser.add_argument("--generated-by", default="pipelineM52InventoryPromotionPack")
    parser.add_argument("--output-manifest", default="data/m52-generated-scenes.json")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    contract_path = project_root / args.contract
    output_root = project_root / args.output_dir
    if not contract_path.is_file():
        raise SystemExit(f"Missing promotion contract: {contract_path}")

    contract = json.loads(contract_path.read_text())
    source_artifact_root = project_root / contract["artifactSourceRoot"]
    if not source_artifact_root.is_dir():
        raise SystemExit(f"Missing source artifact root: {source_artifact_root}")
    base_rows = load_generated_scene_index(project_root, args.base_generated)

    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)

    commit = git_commit(project_root)
    scenes = [
        materialize_scene(
            project_root=project_root,
            output_root=output_root,
            source_artifact_root=source_artifact_root,
            base_rows=base_rows,
            scene_contract=scene_contract,
            commit=commit,
            derivation_task=args.generated_by,
            derivation_contract=args.contract,
            output_artifact_prefix=args.output_dir,
        )
        for scene_contract in contract["scenes"]
    ]

    manifest = {
        "schemaVersion": 1,
        "generatedBy": args.generated_by,
        "source": args.contract,
        "description": "Inventory-derived generated dashboard rows materialized from a declarative contract.",
        "scenes": scenes,
    }
    output_manifest = output_root / args.output_manifest
    write_json(output_manifest, manifest)
    print(f"Wrote inventory promotion generated scenes: {output_manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
