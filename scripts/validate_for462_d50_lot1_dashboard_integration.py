#!/usr/bin/env python3
"""Validate D50 lot 1 evidence and row-specific refusal contracts."""

from __future__ import annotations

import json
import subprocess
import sys
from collections import Counter
from copy import deepcopy
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SOURCE_LINEAR = "FOR-462"
FORMALIZATION_LINEAR = "FOR-464"
FOR465_LINEAR = "FOR-465"
FOR466_LINEAR = "FOR-466"
FOR467_LINEAR = "FOR-467"
FOR468_LINEAR = "FOR-468"
FOR469_LINEAR = "FOR-469"
INVENTORY = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json"
DASHBOARD = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"
GATE_JSON = ROOT / "build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json"
LOT1_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
FOR465_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-465-drawminibitmaprect-evidence.md"
FOR465_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json"
FOR466_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md"
FOR466_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json"
FOR467_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md"
FOR467_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json"
FOR468_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md"
FOR468_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json"
FOR469_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md"
FOR469_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json"

EXPECTED_LOT1 = [
    "skia-gm-drawbitmaprect",
    "skia-gm-drawminibitmaprect",
    "skia-gm-bitmappremul",
    "skia-gm-image",
    "skia-gm-imagesource",
    "skia-gm-localmatriximageshader",
    "skia-gm-gradientsdegenerate",
    "skia-gm-offsetimagefilter",
    "skia-gm-matriximagefilter",
    "skia-gm-imageblur",
    "skia-gm-simpleaaclip",
    "skia-gm-pathfill",
]
EXPECTED_MATERIALIZED = {
    "skia-gm-drawbitmaprect": ["m66-bitmap-rect-nearest-skia"],
    "skia-gm-bitmappremul": ["m53-bitmap-premul-alpha"],
    "skia-gm-localmatriximageshader": ["m54-local-matrix-blend-composition"],
    "skia-gm-gradientsdegenerate": ["m53-degenerate-gradient-linear"],
    "skia-gm-matriximagefilter": ["m54-matrix-imagefilter-affine"],
    "skia-gm-imageblur": ["m53-imageblur-bounded-prepass"],
    "skia-gm-simpleaaclip": ["m54-simple-aa-clip"],
}
EXPECTED_MISSING: list[str] = []
FOR465_ROW_ID = "skia-gm-drawminibitmaprect"
FOR466_ROW_ID = "skia-gm-image"
FOR467_ROW_ID = "skia-gm-imagesource"
FOR468_ROW_ID = "skia-gm-offsetimagefilter"
FOR469_ROW_ID = "skia-gm-pathfill"
LINEAR_ORDER = {
    FOR465_LINEAR: 465,
    FOR466_LINEAR: 466,
    FOR467_LINEAR: 467,
    FOR468_LINEAR: 468,
    FOR469_LINEAR: 469,
}
STATUS_COUNTS_BY_LINEAR = {
    FOR465_LINEAR: {"diagnostic-only": 4, "expected-unsupported": 1, "supported": 7},
    FOR466_LINEAR: {"diagnostic-only": 3, "expected-unsupported": 2, "supported": 7},
    FOR467_LINEAR: {"diagnostic-only": 2, "expected-unsupported": 3, "supported": 7},
    FOR468_LINEAR: {"diagnostic-only": 1, "expected-unsupported": 4, "supported": 7},
    FOR469_LINEAR: {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7},
}
VALIDATION_COMMANDS_BY_LINEAR = {
    FOR465_LINEAR: [
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for465-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk git diff --check",
    ],
    FOR466_LINEAR: [
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk python3 scripts/validate_for466_skia_gm_image_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for466-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py",
        "rtk git diff --check",
    ],
    FOR467_LINEAR: [
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk python3 scripts/validate_for466_skia_gm_image_evidence.py",
        "rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for467-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py",
        "rtk git diff --check",
    ],
    FOR468_LINEAR: [
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk python3 scripts/validate_for466_skia_gm_image_evidence.py",
        "rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py",
        "rtk python3 scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for468-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
        "rtk git diff --check",
    ],
    FOR469_LINEAR: [
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
        "rtk python3 scripts/validate_for466_skia_gm_image_evidence.py",
        "rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py",
        "rtk python3 scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
        "rtk python3 scripts/validate_for469_skia_gm_pathfill_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for469-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py scripts/validate_for469_skia_gm_pathfill_evidence.py",
        "rtk git diff --check",
    ],
}
ROW_REFUSALS = {
    FOR465_ROW_ID: {
        "linear": FOR465_LINEAR,
        "status": "expected-unsupported",
        "reason": "bitmap.drawminibitmaprect.row-specific-artifacts-required",
        "fallbackReason": "bitmap.drawminibitmaprect.row-specific-artifacts-required",
        "cpuRoute": "cpu.image-rect.drawminibitmaprect.expected-unsupported",
        "gpuRoute": "webgpu.image-rect.drawminibitmaprect.expected-unsupported",
        "sourceFindingMemory": "global/kanvas/findings/for-464-formalise-le-manifeste-strict-du-lot-1-d50",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-4-produire-preuve-drawminibitmaprect",
        "policy": (
            "The existing drawbitmaprect nearest evidence is not row-specific for "
            "DrawMiniBitmapRectGM. The row stays unsupported until a candidate-specific "
            "Skia reference capture, CPU artifact, GPU artifact, diff/stat payload, and "
            "route diagnostics exist without threshold changes."
        ),
        "neighborEvidenceFlag": "drawbitmaprectEvidenceInherited",
        "neighborEvidencePolicy": "The existing drawbitmaprect nearest evidence is not row-specific for DrawMiniBitmapRectGM.",
    },
    FOR466_ROW_ID: {
        "linear": FOR466_LINEAR,
        "status": "expected-unsupported",
        "reason": "image.imagegm.row-specific-artifacts-required",
        "fallbackReason": "image.imagegm.row-specific-artifacts-required",
        "cpuRoute": "cpu.image.imagegm.expected-unsupported",
        "gpuRoute": "webgpu.image.imagegm.expected-unsupported",
        "sourceFindingMemory": "global/kanvas/findings/for-465-formalise-le-refus-drawminibitmaprect-d50-lot-1",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-5-traiter-skia-gm-image",
        "policy": (
            "Historical ImageGM similarity and adjacent bitmap/image rows are not a D50 "
            "dashboard row-specific proof. The row stays unsupported until a candidate-specific "
            "Skia reference capture, CPU artifact, WebGPU artifact, diff/stat payload, route "
            "diagnostics, and fallbackReason=none exist without threshold or scoring changes."
        ),
        "neighborEvidenceFlag": "historicalImageEvidenceInherited",
        "neighborEvidencePolicy": "Historical ImageGM similarity and adjacent bitmap/image rows are not inherited as D50 support.",
        "decodeFixtureProvenance": {
            "imageContent": "static synthetic 64x64 N32 premul raster surface snapshots created by ImageGM before drawImage/drawImageRect",
            "externalEncodedFixture": "none",
            "decodePath": "not exercised; ImageGM creates SkSurface raster snapshots, not codec-decoded input",
            "fixtureAvailability": "Kotlin source fixture exists, but no row-specific D50 reference/CPU/GPU artifacts are generated",
            "kotlinSource": "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt",
            "upstreamSource": "gm/image.cpp",
            "unsupportedBroadClaims": [
                "codec",
                "YUV",
                "animation",
                "EXIF",
                "mipmap",
                "tile-mode",
                "color-managed image decode",
            ],
        },
    },
    FOR467_ROW_ID: {
        "linear": FOR467_LINEAR,
        "status": "expected-unsupported",
        "reason": "image.imagesource.row-specific-artifacts-required",
        "fallbackReason": "image.imagesource.row-specific-artifacts-required",
        "cpuRoute": "cpu.image-source.imagesource.expected-unsupported",
        "gpuRoute": "webgpu.image-source.imagesource.expected-unsupported",
        "sourceFindingMemory": "global/kanvas/findings/for-466-formalise-le-refus-skia-gm-image-d50-lot-1",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-6-traiter-skia-gm-imagesource",
        "policy": (
            "Existing ImageSourceGM similarity reports and adjacent image evidence are not a D50 "
            "dashboard row-specific proof. The row stays unsupported until a candidate-specific "
            "Skia reference capture, CPU image-source route artifact, WebGPU image-source route "
            "artifact, diff/stat payload, and fallbackReason=none exist without threshold or "
            "scoring changes."
        ),
        "neighborEvidenceFlag": "imageSourceEvidenceInherited",
        "neighborEvidencePolicy": "Existing ImageSourceGM reports and adjacent image evidence are not inherited as D50 support.",
        "sourceImageProvenance": {
            "scene": "ImageSourceGM",
            "kotlinSource": "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageSourceGM.kt",
            "upstreamSource": "gm/imagesource.cpp",
            "referenceFixture": "imagesource.png exists for historical integration testing, but is not a D50 dashboard row-specific artifact",
            "fixtureAvailability": "ported Kotlin GM and historical similarity report exist, but no row-specific D50 reference/CPU/GPU artifacts are generated",
            "dynamicSourceImage": "not claimed",
            "unsupportedBroadClaims": [
                "codec",
                "YUV",
                "animation",
                "EXIF",
                "mipmap",
                "tile-mode",
                "dynamic source image",
                "color-managed image",
            ],
        },
    },
    FOR468_ROW_ID: {
        "linear": FOR468_LINEAR,
        "status": "expected-unsupported",
        "reason": "image-filter.offset.row-specific-artifacts-required",
        "fallbackReason": "image-filter.offset.row-specific-artifacts-required",
        "cpuRoute": "cpu.image-filter.offset.expected-unsupported",
        "gpuRoute": "webgpu.image-filter.offset.expected-unsupported",
        "sourceFindingMemory": "global/kanvas/findings/for-467-formalise-le-refus-skia-gm-imagesource-d50-lot-1",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-7-traiter-skia-gm-offsetimagefilter",
        "policy": (
            "Historical OffsetImageFilterGM and SimpleOffsetImageFilterGM reports are not a D50 "
            "dashboard row-specific proof for skia-gm-offsetimagefilter. The row stays unsupported "
            "until a candidate-specific Skia reference capture, CPU prepass/layer artifact, WebGPU "
            "prepass/layer artifact, diff/stat payload, and fallbackReason=none exist without "
            "threshold or scoring changes."
        ),
        "neighborEvidenceFlag": "offsetImageFilterEvidenceInherited",
        "neighborEvidencePolicy": "Historical offset image-filter evidence and adjacent image-filter rows are not inherited as D50 support.",
        "offsetImageFilterProvenance": {
            "scene": "OffsetImageFilterGM",
            "kotlinSource": "skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt",
            "upstreamSource": "gm/offsetimagefilter.cpp",
            "historicalSimilarity": "OffsetImageFilterGM=84.515 in skia-integration-tests/test-similarity-scores.properties; not a D50 row-specific dashboard artifact",
            "adjacentHistoricalScene": "SimpleOffsetImageFilterGM has separate historical crop/prepass evidence and must not be inherited by skia-gm-offsetimagefilter",
            "fixtureAvailability": "ported Kotlin GM and historical similarity reports exist, but no row-specific D50 reference/CPU/GPU artifacts are generated",
            "boundedOffsetFilter": "not claimed as supported",
            "unsupportedBroadClaims": [
                "broad image-filter DAG",
                "crop image-filter DAG",
                "picture prepass",
                "arbitrary layer prepass",
                "color pipeline global",
                "adjacent image-filter support inheritance",
            ],
        },
    },
    FOR469_ROW_ID: {
        "linear": FOR469_LINEAR,
        "status": "expected-unsupported",
        "reason": "path-aa.fill.row-specific-artifacts-required",
        "fallbackReason": "path-aa.fill.row-specific-artifacts-required",
        "cpuRoute": "cpu.path-aa.fill.expected-unsupported",
        "gpuRoute": "webgpu.path-aa.fill.expected-unsupported",
        "sourceFindingMemory": "global/kanvas/findings/for-468-formalise-le-refus-skia-gm-offsetimagefilter-d50-lot-1",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-8-traiter-skia-gm-pathfill",
        "policy": (
            "Historical PathFillGM, Path AA, stroke, cap/join/dash, convex path, and "
            "edge-budget signals are not a D50 dashboard row-specific proof for "
            "skia-gm-pathfill. The row stays unsupported until a candidate-specific "
            "Skia reference capture, CPU Path AA fill artifact, WebGPU bounded coverage "
            "artifact, diff/stat payload, and fallbackReason=none exist without "
            "threshold, scoring, edge-budget, or fallback policy changes."
        ),
        "neighborEvidenceFlag": "pathFillEvidenceInherited",
        "neighborEvidencePolicy": "Historical PathFillGM and adjacent Path AA evidence are not inherited as D50 support.",
        "pathFillProvenance": {
            "scene": "PathFillGM",
            "kotlinSource": "skia-integration-tests/src/main/kotlin/org/skia/tests/PathFillGM.kt",
            "upstreamSource": "gm/pathfill.cpp",
            "fixtureAvailability": "ported Kotlin GM and upstream source exist, but no row-specific D50 reference/CPU/GPU artifacts are generated",
            "boundedFillUnderEdgeBudget": "not claimed as supported",
            "fillRule": "not claimed as supported",
            "historicalEvidence": "Path AA, stroke, cap/join/dash, convex path, edge-budget, and historical scene signals are not D50 row-specific dashboard artifacts",
            "unsupportedBroadClaims": [
                "broad Path AA",
                "stroke",
                "cap/join/dash",
                "convex path",
                "edge-budget promotion",
                "historical path scene support inheritance",
            ],
        },
    },
}
EXPECTED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md",
    "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md",
    "reports/wgsl-pipeline/2026-06-06-for-465-drawminibitmaprect-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-d50-lot1-pm-closeout.md",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json",
    "reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json",
    "scripts/validate_d50_lot1_pm_closeout.py",
    "scripts/validate_for462_d50_lot1_dashboard_integration.py",
    "scripts/validate_for465_drawminibitmaprect_evidence.py",
    "scripts/validate_for466_skia_gm_image_evidence.py",
    "scripts/validate_for467_skia_gm_imagesource_evidence.py",
    "scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
    "scripts/validate_for469_skia_gm_pathfill_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)


def fail(message: str) -> None:
    raise SystemExit(f"{FORMALIZATION_LINEAR} validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def candidate_by_inventory(inventory: dict[str, Any], inventory_id: str) -> dict[str, Any]:
    for candidate in inventory.get("candidates", []):
        if isinstance(candidate, dict) and candidate.get("inventoryId") == inventory_id:
            return candidate
    fail(f"{inventory_id} missing from candidate inventory")


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def ticket_number_from_key(key: str) -> int | None:
    for linear, number in LINEAR_ORDER.items():
        if f"For{number}" in key or linear in key:
            return number
    return None


def snapshot_refusal(refusal: dict[str, Any], linear: str) -> dict[str, Any]:
    ticket_number = LINEAR_ORDER[linear]
    row = deepcopy(refusal)
    for key in list(row):
        key_number = ticket_number_from_key(key)
        if key_number is not None and key_number > ticket_number:
            del row[key]
    non_claims = row.get("nonClaims")
    if isinstance(non_claims, dict):
        for key in list(non_claims):
            key_number = ticket_number_from_key(key)
            if key_number is not None and key_number > ticket_number:
                del non_claims[key]
    return row


def git_changed_paths() -> set[str]:
    diff_result = subprocess.run(
        ["git", "diff", "--name-only", "origin/master"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    status_result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_FILES)
    require(not unexpected, f"unexpected local diffs for {FORMALIZATION_LINEAR}: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"active dashboard/inventory inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def dashboard_rows_by_inventory(scenes: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    rows: dict[str, list[dict[str, Any]]] = {}
    for scene in scenes:
        inventory_id = scene.get("inventoryId")
        if isinstance(inventory_id, str) and inventory_id:
            rows.setdefault(inventory_id, []).append(scene)
    return rows


def build_row_refusal(candidate: dict[str, Any]) -> dict[str, Any]:
    refusal = ROW_REFUSALS[candidate["inventoryId"]]
    inherited_flag = refusal["neighborEvidenceFlag"]
    return {
        "linear": refusal["linear"],
        "inventoryId": candidate["inventoryId"],
        "rank": candidate["rank"],
        "displayName": candidate["displayName"],
        "family": candidate["family"],
        "status": refusal["status"],
        "reason": refusal["reason"],
        "fallbackReason": refusal["fallbackReason"],
        "source": {
            "candidateInventory": rel(INVENTORY),
            "kotlinSource": candidate["kotlinSource"],
            "upstreamSource": candidate["upstreamSource"],
            "findingMemory": refusal["sourceFindingMemory"],
            "draftMemory": refusal["sourceDraftMemory"],
        },
        "reference": {
            "status": "not-generated",
            "required": True,
            "plan": candidate["referencePlan"],
            "refusalReason": "candidate-specific Skia GM reference capture is missing",
        },
        "cpu": {
            "status": "expected-unsupported",
            "required": True,
            "route": refusal["cpuRoute"],
            "expectation": candidate["cpuExpectation"],
            "artifact": "not-generated",
            "refusalReason": "row-specific CPU bitmap oracle artifact is missing",
        },
        "gpu": {
            "status": "expected-unsupported",
            "required": True,
            "route": refusal["gpuRoute"],
            "expectation": candidate["gpuExpectation"],
            "artifact": "not-generated",
            "fallbackReason": refusal["fallbackReason"],
            "refusalReason": "row-specific WebGPU artifact is missing",
        },
        "diffStats": {
            "status": "not-computed",
            "required": True,
            "reason": "diff/stat payload cannot be computed without row-specific reference, CPU, and GPU artifacts",
        },
        "routeDiagnostics": {
            "cpu": refusal["cpuRoute"],
            "gpu": refusal["gpuRoute"],
            "fallbackReason": refusal["fallbackReason"],
            "policy": refusal["policy"],
        },
        **({"decodeFixtureProvenance": refusal["decodeFixtureProvenance"]} if "decodeFixtureProvenance" in refusal else {}),
        **({"sourceImageProvenance": refusal["sourceImageProvenance"]} if "sourceImageProvenance" in refusal else {}),
        **(
            {"offsetImageFilterProvenance": refusal["offsetImageFilterProvenance"]}
            if "offsetImageFilterProvenance" in refusal
            else {}
        ),
        **({"pathFillProvenance": refusal["pathFillProvenance"]} if "pathFillProvenance" in refusal else {}),
        "nonClaims": {
            "supportClaimAddedByFor465": False,
            "supportClaimAddedByFor466": False,
            "supportClaimAddedByFor467": False,
            "supportClaimAddedByFor468": False,
            "supportClaimAddedByFor469": False,
            "skiaComparableClaimAddedByFor465": False,
            "skiaComparableClaimAddedByFor466": False,
            "skiaComparableClaimAddedByFor467": False,
            "skiaComparableClaimAddedByFor468": False,
            "skiaComparableClaimAddedByFor469": False,
            "dashboardRowAddedByFor465": False,
            "dashboardRowAddedByFor466": False,
            "dashboardRowAddedByFor467": False,
            "dashboardRowAddedByFor468": False,
            "dashboardRowAddedByFor469": False,
            "dashboardStatusChangedByFor465": False,
            "dashboardStatusChangedByFor466": False,
            "dashboardStatusChangedByFor467": False,
            "dashboardStatusChangedByFor468": False,
            "dashboardStatusChangedByFor469": False,
            inherited_flag: False,
            "thresholdChanged": False,
            "scoringChanged": False,
            "fallbackPolicyChanged": False,
            "pipelineKeyChanged": False,
            "productionCodeChanged": False,
            "wgslProductionChanged": False,
            "upstreamSourceChanged": False,
            "codecSupportClaimAddedByFor466": False,
            "yuvSupportClaimAddedByFor466": False,
            "animationSupportClaimAddedByFor466": False,
            "exifSupportClaimAddedByFor466": False,
            "mipmapSupportClaimAddedByFor466": False,
            "tileModeSupportClaimAddedByFor466": False,
            "colorManagedImageSupportClaimAddedByFor466": False,
            "codecSupportClaimAddedByFor467": False,
            "yuvSupportClaimAddedByFor467": False,
            "animationSupportClaimAddedByFor467": False,
            "exifSupportClaimAddedByFor467": False,
            "mipmapSupportClaimAddedByFor467": False,
            "tileModeSupportClaimAddedByFor467": False,
            "dynamicSourceImageSupportClaimAddedByFor467": False,
            "colorManagedImageSupportClaimAddedByFor467": False,
            "broadImageFilterDagSupportClaimAddedByFor468": False,
            "cropImageFilterDagSupportClaimAddedByFor468": False,
            "picturePrepassSupportClaimAddedByFor468": False,
            "arbitraryLayerPrepassSupportClaimAddedByFor468": False,
            "colorPipelineGlobalSupportClaimAddedByFor468": False,
            "broadPathAaSupportClaimAddedByFor469": False,
            "strokeSupportClaimAddedByFor469": False,
            "capJoinDashSupportClaimAddedByFor469": False,
            "convexPathSupportClaimAddedByFor469": False,
            "edgeBudgetSupportClaimAddedByFor469": False,
            "historicalPathSceneSupportClaimAddedByFor469": False,
        },
    }


def require_dashboard_gate(gate: dict[str, Any]) -> dict[str, int]:
    failures = gate.get("failures")
    require(failures == [], "pipelineSceneDashboardGate must have zero failures")
    counters = gate.get("counters")
    require(isinstance(counters, dict), "gate counters must be present")
    expected = {
        "total": 93,
        "status.pass": 70,
        "status.expected-unsupported": 23,
        "inventoryDerived": 45,
    }
    normalized: dict[str, int] = {}
    for key, expected_value in expected.items():
        value = counters.get(key)
        require(value == expected_value, f"gate counter {key} mismatch: {value} != {expected_value}")
        normalized[key] = int(value)
    require(counters.get("status.tracked-gap", 0) == 0, "tracked-gap rows must remain 0")
    require(counters.get("status.fail", 0) == 0, "fail rows must remain 0")
    normalized["status.tracked-gap"] = int(counters.get("status.tracked-gap", 0))
    normalized["status.fail"] = int(counters.get("status.fail", 0))
    return normalized


def build_evidence() -> dict[str, Any]:
    inventory = load_json(INVENTORY)
    dashboard = load_json(DASHBOARD)
    gate = load_json(GATE_JSON)
    lot1 = inventory.get("lot1Recommendation", {}).get("candidateIds")
    require(lot1 == EXPECTED_LOT1, "FOR-461 lot 1 order changed")
    scenes = dashboard.get("scenes")
    require(isinstance(scenes, list), "dashboard scenes must be a list")
    scene_dicts = [scene for scene in scenes if isinstance(scene, dict)]
    require(len(scene_dicts) == len(scenes), "all dashboard scenes must be objects")
    gate_counters = require_dashboard_gate(gate)

    rows_by_inventory = dashboard_rows_by_inventory(scene_dicts)
    materialized: list[dict[str, Any]] = []
    refusals: list[dict[str, Any]] = []
    missing: list[dict[str, Any]] = []
    for inventory_id in EXPECTED_LOT1:
        rows = rows_by_inventory.get(inventory_id, [])
        row_ids = [row.get("id") for row in rows]
        expected_rows = EXPECTED_MATERIALIZED.get(inventory_id, [])
        if expected_rows:
            require(row_ids == expected_rows, f"{inventory_id} materialized rows mismatch: {row_ids}")
            row_payloads = []
            for row in rows:
                status = row.get("status")
                gpu_route = row.get("gpu", {}).get("route", {})
                fallback = gpu_route.get("fallbackReason")
                require(status == "pass", f"{row.get('id')} must remain pass")
                require(row.get("gpu", {}).get("status") == "pass", f"{row.get('id')} gpu.status must remain pass")
                require(fallback == "none", f"{row.get('id')} pass row must keep fallbackReason=none")
                row_payloads.append(
                    {
                        "id": row["id"],
                        "status": status,
                        "referenceKind": row.get("referenceKind"),
                        "derivationTask": row.get("generation", {}).get("derivationTask"),
                        "fallbackReason": fallback,
                    }
                )
            materialized.append({"inventoryId": inventory_id, "rows": row_payloads})
        elif inventory_id in ROW_REFUSALS:
            require(not rows, f"{inventory_id} unexpectedly materialized without row-specific proof")
            refusals.append(build_row_refusal(candidate_by_inventory(inventory, inventory_id)))
        else:
            require(not rows, f"{inventory_id} unexpectedly materialized without row-specific proof")
            missing.append(
                {
                    "inventoryId": inventory_id,
                    "status": "missing-row-specific-evidence",
                    "requiredEvidence": [
                        "row-specific reference artifact",
                        "CPU artifact and route diagnostics",
                        "GPU artifact or stable expected-unsupported refusal",
                        "diff and stats artifacts",
                        "fallbackReason=none for support rows",
                        "unchanged dashboard thresholds and gate policy",
                    ],
                }
            )

    reference_counts = Counter()
    for item in materialized:
        for row in item["rows"]:
            reference_counts[row["referenceKind"]] += 1

    dashboard_reference_counts = Counter(
        scene.get("referenceKind")
        for scene in scene_dicts
        if isinstance(scene.get("referenceKind"), str)
    )
    dashboard_status_counts = Counter(
        scene.get("status")
        for scene in scene_dicts
        if isinstance(scene.get("status"), str)
    )

    strict_rows: list[dict[str, Any]] = []
    materialized_by_inventory = {item["inventoryId"]: item for item in materialized}
    refusal_by_inventory = {item["inventoryId"]: item for item in refusals}
    missing_by_inventory = {item["inventoryId"]: item for item in missing}
    for candidate in inventory["candidates"]:
        inventory_id = candidate["inventoryId"]
        if inventory_id not in EXPECTED_LOT1:
            continue
        materialized_item = materialized_by_inventory.get(inventory_id)
        refusal_item = refusal_by_inventory.get(inventory_id)
        missing_item = missing_by_inventory.get(inventory_id)
        if materialized_item is not None:
            row = materialized_item["rows"][0]
            strict_rows.append(
                {
                    "inventoryId": inventory_id,
                    "rank": candidate["rank"],
                    "family": candidate["family"],
                    "status": "supported",
                    "reason": "already-materialized-dashboard-evidence",
                    "dashboardRowId": row["id"],
                    "dashboardStatus": row["status"],
                    "referenceKind": row["referenceKind"],
                    "derivationTask": row["derivationTask"],
                    "fallbackReason": row["fallbackReason"],
                    "supportClaimAddedByFor464": False,
                    "skiaComparableClaimAddedByFor464": False,
                }
            )
        elif refusal_item is not None:
            refusal_contract = ROW_REFUSALS[inventory_id]
            strict_rows.append(
                {
                    "inventoryId": inventory_id,
                    "rank": candidate["rank"],
                    "family": candidate["family"],
                    "status": "expected-unsupported",
                    "reason": refusal_item["reason"],
                    "dashboardRowId": None,
                    "dashboardStatus": None,
                    "strictDashboardStatus": "expected-unsupported",
                    "referenceKind": None,
                    "fallbackReason": refusal_item["fallbackReason"],
                    "cpuRoute": refusal_item["cpu"]["route"],
                    "gpuRoute": refusal_item["gpu"]["route"],
                    "diffStats": refusal_item["diffStats"],
                    "routeDiagnostics": refusal_item["routeDiagnostics"],
                    **(
                        {"decodeFixtureProvenance": refusal_item["decodeFixtureProvenance"]}
                        if "decodeFixtureProvenance" in refusal_item
                        else {}
                    ),
                    **(
                        {"sourceImageProvenance": refusal_item["sourceImageProvenance"]}
                        if "sourceImageProvenance" in refusal_item
                        else {}
                    ),
                    **(
                        {"offsetImageFilterProvenance": refusal_item["offsetImageFilterProvenance"]}
                        if "offsetImageFilterProvenance" in refusal_item
                        else {}
                    ),
                    **(
                        {"pathFillProvenance": refusal_item["pathFillProvenance"]}
                        if "pathFillProvenance" in refusal_item
                        else {}
                    ),
                    "supportClaimAddedByFor464": False,
                    "skiaComparableClaimAddedByFor464": False,
                    "supportClaimAddedByFor465": False,
                    "skiaComparableClaimAddedByFor465": False,
                    "supportClaimAddedByFor466": False,
                    "skiaComparableClaimAddedByFor466": False,
                    "supportClaimAddedByFor467": False,
                    "skiaComparableClaimAddedByFor467": False,
                    "supportClaimAddedByFor468": False,
                    "skiaComparableClaimAddedByFor468": False,
                    "supportClaimAddedByFor469": False,
                    "skiaComparableClaimAddedByFor469": False,
                    refusal_contract["neighborEvidenceFlag"]: False,
                }
            )
        elif missing_item is not None:
            strict_rows.append(
                {
                    "inventoryId": inventory_id,
                    "rank": candidate["rank"],
                    "family": candidate["family"],
                    "status": "diagnostic-only",
                    "reason": "diagnostic.missing-row-specific-evidence",
                    "dashboardRowId": None,
                    "dashboardStatus": None,
                    "referenceKind": None,
                    "requiredEvidence": missing_item["requiredEvidence"],
                    "supportClaimAddedByFor464": False,
                    "skiaComparableClaimAddedByFor464": False,
                }
            )
        else:
            fail(f"{inventory_id}: lot 1 row was neither materialized nor diagnostic-only")

    require(len(strict_rows) == len(EXPECTED_LOT1), "strict lot 1 row count mismatch")
    require(
        all(row["status"] in {"supported", "expected-unsupported", "diagnostic-only"} for row in strict_rows),
        "strict lot 1 rows must use only supported/expected-unsupported/diagnostic-only",
    )

    strict_status_counter = Counter(row["status"] for row in strict_rows)
    strict_status_counts = {
        "diagnostic-only": int(strict_status_counter.get("diagnostic-only", 0)),
        "expected-unsupported": int(strict_status_counter.get("expected-unsupported", 0)),
        "supported": int(strict_status_counter.get("supported", 0)),
    }

    evidence = {
        "schemaVersion": 1,
        "linear": SOURCE_LINEAR,
        "strictManifestFormalizedBy": FORMALIZATION_LINEAR,
        "rowEvidenceUpdatedBy": [FOR465_LINEAR, FOR466_LINEAR, FOR467_LINEAR, FOR468_LINEAR, FOR469_LINEAR],
        "date": "2026-06-06",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-3-formaliser-le-manifeste-strict-du-lot-1-dashboard",
        "rowEvidenceSourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-ticket-d50-4-produire-preuve-drawminibitmaprect",
        "rowEvidenceSourceDraftMemoryFor466": "global/kanvas/tickets/drafts/brouillon-ticket-d50-5-traiter-skia-gm-image",
        "rowEvidenceSourceDraftMemoryFor467": "global/kanvas/tickets/drafts/brouillon-ticket-d50-6-traiter-skia-gm-imagesource",
        "rowEvidenceSourceDraftMemoryFor468": "global/kanvas/tickets/drafts/brouillon-ticket-d50-7-traiter-skia-gm-offsetimagefilter",
        "rowEvidenceSourceDraftMemoryFor469": "global/kanvas/tickets/drafts/brouillon-ticket-d50-8-traiter-skia-gm-pathfill",
        "sourceInventory": rel(INVENTORY),
        "sourceFindingMemory": "global/kanvas/findings/for-462-verrouille-le-lot-1-d50-sans-faux-support",
        "rowEvidenceSourceFindingMemory": "global/kanvas/findings/for-464-formalise-le-manifeste-strict-du-lot-1-d50",
        "rowEvidenceSourceFindingMemoryFor466": "global/kanvas/findings/for-465-formalise-le-refus-drawminibitmaprect-d50-lot-1",
        "rowEvidenceSourceFindingMemoryFor467": "global/kanvas/findings/for-466-formalise-le-refus-skia-gm-image-d50-lot-1",
        "rowEvidenceSourceFindingMemoryFor468": "global/kanvas/findings/for-467-formalise-le-refus-skia-gm-imagesource-d50-lot-1",
        "rowEvidenceSourceFindingMemoryFor469": "global/kanvas/findings/for-468-formalise-le-refus-skia-gm-offsetimagefilter-d50-lot-1",
        "dashboardGate": rel(GATE_JSON),
        "classification": "lot1-row-specific-refusals-no-new-support-claims",
        "lot1CandidateCount": len(EXPECTED_LOT1),
        "materializedCandidateCount": len(materialized),
        "expectedUnsupportedCandidateCount": len(refusals),
        "missingCandidateCount": len(missing),
        "materializedRowsAddedByFor462": 0,
        "supportClaimsAddedByFor462": 0,
        "skiaComparableClaimsAddedByFor462": 0,
        "dashboardRowsAddedByFor464": 0,
        "supportClaimsAddedByFor464": 0,
        "skiaComparableClaimsAddedByFor464": 0,
        "dashboardRowsAddedByFor465": 0,
        "supportClaimsAddedByFor465": 0,
        "skiaComparableClaimsAddedByFor465": 0,
        "dashboardRowsAddedByFor466": 0,
        "supportClaimsAddedByFor466": 0,
        "skiaComparableClaimsAddedByFor466": 0,
        "dashboardRowsAddedByFor467": 0,
        "supportClaimsAddedByFor467": 0,
        "skiaComparableClaimsAddedByFor467": 0,
        "dashboardRowsAddedByFor468": 0,
        "supportClaimsAddedByFor468": 0,
        "skiaComparableClaimsAddedByFor468": 0,
        "dashboardRowsAddedByFor469": 0,
        "dashboardRowsAddedByD50Visibility": 0,
        "supportClaimsAddedByFor469": 0,
        "skiaComparableClaimsAddedByFor469": 0,
        "visualSupportAbove50PercentClaimByFor464": False,
        "visualSupportAbove50PercentClaimByFor465": False,
        "visualSupportAbove50PercentClaimByFor466": False,
        "visualSupportAbove50PercentClaimByFor467": False,
        "visualSupportAbove50PercentClaimByFor468": False,
        "visualSupportAbove50PercentClaimByFor469": False,
        "dashboardStatusChangedByFor462": False,
        "dashboardStatusChangedByFor464": False,
        "dashboardStatusChangedByFor465": False,
        "dashboardStatusChangedByFor466": False,
        "dashboardStatusChangedByFor467": False,
        "dashboardStatusChangedByFor468": False,
        "dashboardStatusChangedByFor469": False,
        "dashboardSupportStatusChangedByD50Visibility": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "fallbackPolicyChanged": False,
        "pipelineKeyChanged": False,
        "productionCodeChanged": False,
        "wgslProductionChanged": False,
        "upstreamSourceChanged": False,
        "broadSkiaGmParityClaim": False,
        "codecSupportClaimAddedByFor466": False,
        "yuvSupportClaimAddedByFor466": False,
        "animationSupportClaimAddedByFor466": False,
        "exifSupportClaimAddedByFor466": False,
        "mipmapSupportClaimAddedByFor466": False,
        "tileModeSupportClaimAddedByFor466": False,
        "colorManagedImageSupportClaimAddedByFor466": False,
        "codecSupportClaimAddedByFor467": False,
        "yuvSupportClaimAddedByFor467": False,
        "animationSupportClaimAddedByFor467": False,
        "exifSupportClaimAddedByFor467": False,
        "mipmapSupportClaimAddedByFor467": False,
        "tileModeSupportClaimAddedByFor467": False,
        "dynamicSourceImageSupportClaimAddedByFor467": False,
        "colorManagedImageSupportClaimAddedByFor467": False,
        "broadImageFilterDagSupportClaimAddedByFor468": False,
        "cropImageFilterDagSupportClaimAddedByFor468": False,
        "picturePrepassSupportClaimAddedByFor468": False,
        "arbitraryLayerPrepassSupportClaimAddedByFor468": False,
        "colorPipelineGlobalSupportClaimAddedByFor468": False,
        "broadPathAaSupportClaimAddedByFor469": False,
        "strokeSupportClaimAddedByFor469": False,
        "capJoinDashSupportClaimAddedByFor469": False,
        "convexPathSupportClaimAddedByFor469": False,
        "edgeBudgetSupportClaimAddedByFor469": False,
        "historicalPathSceneSupportClaimAddedByFor469": False,
        "dashboardCounters": gate_counters,
        "beforeCounters": {
            "source": rel(INVENTORY),
            "selectedRows": inventory["currentCounters"]["localMaterializedDashboardRows"],
            "supportedRows": inventory["currentCounters"]["localMaterializedSupportedRows"],
            "expectedUnsupportedRows": inventory["currentCounters"]["localMaterializedExpectedUnsupportedRows"],
            "diagnosticOnlyRows": inventory["currentCounters"]["localMaterializedDiagnosticOnlyRows"],
            "skiaComparableRows": inventory["currentCounters"]["localMaterializedSkiaComparableRows"],
        },
        "afterCounters": {
            "source": rel(DASHBOARD),
            "selectedRows": gate_counters["total"],
            "supportedRows": gate_counters["status.pass"],
            "expectedUnsupportedRows": gate_counters["status.expected-unsupported"],
            "diagnosticOnlyRows": int(dashboard_status_counts.get("diagnostic-only", 0)),
            "skiaComparableRows": int(dashboard_reference_counts.get("skia-upstream", 0)),
        },
        "materializedReferenceKindCounts": dict(sorted(reference_counts.items())),
        "strictLot1StatusCounts": strict_status_counts,
        "strictLot1Rows": strict_rows,
        "materialized": materialized,
        "refusals": refusals,
        "missing": missing,
        "nextAction": "Lot 1 is now fully classified as supported or expected-unsupported; open renderer work only when row-specific reference, CPU, GPU, diff/stat, and route artifacts are available.",
        "validationCommands": [
            "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
            "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
            "rtk python3 scripts/validate_for465_drawminibitmaprect_evidence.py",
            "rtk python3 scripts/validate_for466_skia_gm_image_evidence.py",
            "rtk python3 scripts/validate_for467_skia_gm_imagesource_evidence.py",
            "rtk python3 scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
            "rtk python3 scripts/validate_for469_skia_gm_pathfill_evidence.py",
            "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
            "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for469-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py scripts/validate_for465_drawminibitmaprect_evidence.py scripts/validate_for466_skia_gm_image_evidence.py scripts/validate_for467_skia_gm_imagesource_evidence.py scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py scripts/validate_for469_skia_gm_pathfill_evidence.py",
            "rtk git diff --check",
        ],
    }
    return evidence


def build_lot1_manifest(evidence: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "linear": FORMALIZATION_LINEAR,
        "sourceLinear": SOURCE_LINEAR,
        "date": "2026-06-06",
        "sourceDraftMemory": evidence["sourceDraftMemory"],
        "rowEvidenceSourceDraftMemory": evidence["rowEvidenceSourceDraftMemory"],
        "rowEvidenceSourceDraftMemoryFor466": evidence["rowEvidenceSourceDraftMemoryFor466"],
        "rowEvidenceSourceDraftMemoryFor467": evidence["rowEvidenceSourceDraftMemoryFor467"],
        "rowEvidenceSourceDraftMemoryFor468": evidence["rowEvidenceSourceDraftMemoryFor468"],
        "rowEvidenceSourceDraftMemoryFor469": evidence["rowEvidenceSourceDraftMemoryFor469"],
        "sourceFindingMemory": evidence["sourceFindingMemory"],
        "rowEvidenceSourceFindingMemory": evidence["rowEvidenceSourceFindingMemory"],
        "rowEvidenceSourceFindingMemoryFor466": evidence["rowEvidenceSourceFindingMemoryFor466"],
        "rowEvidenceSourceFindingMemoryFor467": evidence["rowEvidenceSourceFindingMemoryFor467"],
        "rowEvidenceSourceFindingMemoryFor468": evidence["rowEvidenceSourceFindingMemoryFor468"],
        "rowEvidenceSourceFindingMemoryFor469": evidence["rowEvidenceSourceFindingMemoryFor469"],
        "sourceInventory": evidence["sourceInventory"],
        "dashboardGate": evidence["dashboardGate"],
        "classification": evidence["classification"],
        "dashboardConsumesLotDirectly": False,
        "dashboardConsumptionReason": (
            "Only rows with existing dashboard evidence are active dashboard rows. "
            "Five D50 lot 1 candidates are classified as expected-unsupported in the strict manifest, "
            "but D50 does not add dashboard rows or support claims."
        ),
        "lot": 1,
        "candidateCount": evidence["lot1CandidateCount"],
        "statusCounts": evidence["strictLot1StatusCounts"],
        "rows": evidence["strictLot1Rows"],
        "nonClaims": {
            "dashboardRowsAddedByFor464": 0,
            "supportClaimsAddedByFor464": 0,
            "skiaComparableClaimsAddedByFor464": 0,
            "dashboardRowsAddedByFor465": 0,
            "supportClaimsAddedByFor465": 0,
            "skiaComparableClaimsAddedByFor465": 0,
            "dashboardRowsAddedByFor466": 0,
            "supportClaimsAddedByFor466": 0,
            "skiaComparableClaimsAddedByFor466": 0,
            "dashboardRowsAddedByFor467": 0,
            "supportClaimsAddedByFor467": 0,
            "skiaComparableClaimsAddedByFor467": 0,
            "dashboardRowsAddedByFor468": 0,
            "supportClaimsAddedByFor468": 0,
            "skiaComparableClaimsAddedByFor468": 0,
            "dashboardRowsAddedByFor469": 0,
            "dashboardRowsAddedByD50Visibility": 0,
            "supportClaimsAddedByFor469": 0,
            "skiaComparableClaimsAddedByFor469": 0,
            "supportClaimsAddedByFor462": 0,
            "skiaComparableClaimsAddedByFor462": 0,
            "dashboardStatusChangedByFor462": False,
            "dashboardStatusChangedByFor464": False,
            "dashboardStatusChangedByFor465": False,
            "dashboardStatusChangedByFor466": False,
            "dashboardStatusChangedByFor467": False,
            "dashboardStatusChangedByFor468": False,
            "dashboardStatusChangedByFor469": False,
            "dashboardSupportStatusChangedByD50Visibility": False,
            "broadSkiaGmParityClaim": False,
            "visualSupportAbove50PercentClaim": False,
            "thresholdChanged": False,
            "scoringChanged": False,
            "fallbackPolicyChanged": False,
            "pipelineKeyChanged": False,
            "productionCodeChanged": False,
            "wgslProductionChanged": False,
            "codecSupportClaimAddedByFor466": False,
            "yuvSupportClaimAddedByFor466": False,
            "animationSupportClaimAddedByFor466": False,
            "exifSupportClaimAddedByFor466": False,
            "mipmapSupportClaimAddedByFor466": False,
            "tileModeSupportClaimAddedByFor466": False,
            "colorManagedImageSupportClaimAddedByFor466": False,
            "codecSupportClaimAddedByFor467": False,
            "yuvSupportClaimAddedByFor467": False,
            "animationSupportClaimAddedByFor467": False,
            "exifSupportClaimAddedByFor467": False,
            "mipmapSupportClaimAddedByFor467": False,
            "tileModeSupportClaimAddedByFor467": False,
            "dynamicSourceImageSupportClaimAddedByFor467": False,
            "colorManagedImageSupportClaimAddedByFor467": False,
            "broadImageFilterDagSupportClaimAddedByFor468": False,
            "cropImageFilterDagSupportClaimAddedByFor468": False,
            "picturePrepassSupportClaimAddedByFor468": False,
            "arbitraryLayerPrepassSupportClaimAddedByFor468": False,
            "colorPipelineGlobalSupportClaimAddedByFor468": False,
            "broadPathAaSupportClaimAddedByFor469": False,
            "strokeSupportClaimAddedByFor469": False,
            "capJoinDashSupportClaimAddedByFor469": False,
            "convexPathSupportClaimAddedByFor469": False,
            "edgeBudgetSupportClaimAddedByFor469": False,
            "historicalPathSceneSupportClaimAddedByFor469": False,
        },
        "beforeCounters": evidence["beforeCounters"],
        "afterCounters": evidence["afterCounters"],
    }


def write_report(evidence: dict[str, Any]) -> None:
    lines = [
        "# FOR-462 - D50 lot 1 dashboard integration gate",
        "",
        "## Resultat",
        "",
        f"Classification : `{evidence['classification']}`",
        "",
        "FOR-462 verifie le premier lot D50 sans ajouter de faux support. Le tableau de bord genere reste vert avec 0 `tracked-gap` et 0 `fail`; les 12 candidats du lot 1 sont maintenant classes dans le manifeste strict: 7 lignes `pass` existantes et 5 refus `expected-unsupported` documentes hors dashboard actif. FOR-465 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-drawminibitmaprect`; FOR-466 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-image`; FOR-467 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-imagesource`; FOR-468 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-offsetimagefilter`; FOR-469 ajoute un refus `expected-unsupported` row-specific pour `skia-gm-pathfill`. Aucun de ces refus n'est compte comme support.",
        "",
        "D50 n'ajoute aucune ligne dashboard active. Le nombre de lignes `pass` reste inchange, et les cinq refus restent des decisions de manifeste strict.",
        "",
        "## Compteurs",
        "",
        "| Compteur | Valeur |",
        "|---|---:|",
        f"| Candidats lot 1 | {evidence['lot1CandidateCount']} |",
        f"| Candidats materialises | {evidence['materializedCandidateCount']} |",
        f"| Candidats expected-unsupported row-specific | {evidence['expectedUnsupportedCandidateCount']} |",
        f"| Candidats sans preuve suffisante | {evidence['missingCandidateCount']} |",
        f"| Lignes ajoutees par FOR-462 | {evidence['materializedRowsAddedByFor462']} |",
        f"| Lignes dashboard ajoutees par D50 | {evidence['dashboardRowsAddedByD50Visibility']} |",
        f"| Claims support ajoutes par FOR-462 | {evidence['supportClaimsAddedByFor462']} |",
        f"| Claims Skia-comparable ajoutes par FOR-462 | {evidence['skiaComparableClaimsAddedByFor462']} |",
        f"| Dashboard total | {evidence['dashboardCounters']['total']} |",
        f"| Dashboard pass | {evidence['dashboardCounters']['status.pass']} |",
        f"| Dashboard expected-unsupported | {evidence['dashboardCounters']['status.expected-unsupported']} |",
        f"| Dashboard tracked-gap | {evidence['dashboardCounters']['status.tracked-gap']} |",
        f"| Dashboard fail | {evidence['dashboardCounters']['status.fail']} |",
        "",
        "## Candidats deja materialises",
        "",
        "| Inventory id | Row | Statut | Reference | Derivation | Fallback |",
        "|---|---|---|---|---|---|",
    ]
    for item in evidence["materialized"]:
        for row in item["rows"]:
            lines.append(
                "| `{inventory}` | `{row}` | `{status}` | `{reference}` | `{derivation}` | `{fallback}` |".format(
                    inventory=item["inventoryId"],
                    row=row["id"],
                    status=row["status"],
                    reference=row["referenceKind"],
                    derivation=row["derivationTask"],
                    fallback=row["fallbackReason"],
                )
            )
    lines += [
        "",
        "## Refus row-specific",
        "",
        "| Inventory id | Statut | CPU route | GPU route | Fallback |",
        "|---|---|---|---|---|",
    ]
    for item in evidence["refusals"]:
        lines.append(
            "| `{inventory}` | `{status}` | `{cpu}` | `{gpu}` | `{fallback}` |".format(
                inventory=item["inventoryId"],
                status=item["status"],
                cpu=item["cpu"]["route"],
                gpu=item["gpu"]["route"],
                fallback=item["fallbackReason"],
            )
        )
    lines += [
        "",
        "## Candidats bloques sans refus stable",
        "",
        "| Inventory id | Raison |",
        "|---|---|",
    ]
    for item in evidence["missing"]:
        lines.append(
            f"| `{item['inventoryId']}` | preuve ligne par ligne manquante ; pas de promotion dashboard sans reference, CPU, GPU ou refus stable, diff/stat et diagnostics de route |"
        )
    lines += [
        "",
        "## Non-claims",
        "",
        "- FOR-462 ne change pas les statuts dashboard actifs.",
        "- FOR-462 n'ajoute aucun claim de support.",
        "- D50 n'ajoute aucune ligne dashboard active.",
        "- FOR-462 n'ajoute aucun claim de fidelite Skia-comparable.",
        "- FOR-462 ne modifie pas les seuils, le scoring, la politique de fallback, `PipelineKey`, le code de production ou les sources upstream.",
        "- FOR-462 ne revendique pas broad Skia GM parity.",
        "",
        "## Validation",
        "",
        "```bash",
        "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
        "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for462-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py",
        "rtk git diff --check",
        "```",
        "",
        "## Suite",
        "",
        evidence["nextAction"],
        "",
    ]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def write_lot1_report(evidence: dict[str, Any]) -> None:
    before = evidence["beforeCounters"]
    after = evidence["afterCounters"]
    status_counts = evidence["strictLot1StatusCounts"]
    lines = [
        "# FOR-469 - D50 GM Dashboard Lot 1 pathfill evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-469",
        "Source: FOR-468 finding `global/kanvas/findings/for-468-formalise-le-refus-skia-gm-offsetimagefilter-d50-lot-1`",
        "Manifest: `reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json`",
        "Row evidence: `reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json`",
        "",
        "## Resultat",
        "",
        "FOR-464 a formalise un manifeste PM strict pour le lot 1 D50 dans l'ordre FOR-461. FOR-465 traite `skia-gm-drawminibitmaprect` en refus stable. FOR-466 traite `skia-gm-image` en refus stable. FOR-467 traite `skia-gm-imagesource` en refus stable. FOR-468 traite `skia-gm-offsetimagefilter` en refus stable. FOR-469 traite uniquement `skia-gm-pathfill` et le classe en refus stable `expected-unsupported` parce que les artefacts row-specific D50 requis ne sont pas encore disponibles.",
        "",
        "Sept lignes restent `supported` uniquement parce qu'elles pointent vers des lignes dashboard existantes avec `status=pass`, `gpu.status=pass` et `fallbackReason=none`. Aucune ligne ne reste `diagnostic-only`: les cinq candidats non prouves sont visibles en `expected-unsupported` jusqu'a disposer de reference, CPU, GPU, diff/stat, diagnostics de route et politique de seuil inchangee.",
        "",
        "D50 n'ajoute aucune ligne dashboard active pour ces refus. FOR-465, FOR-466, FOR-467, FOR-468 et FOR-469 ajoutent chacun 0 revendication de support et 0 revendication Skia-comparable. Le score de support ne monte pas: le changement ameliore la lecture du refus, pas le rendu.",
        "",
        "## Statuts Lot 1",
        "",
        "| Statut | Nombre |",
        "|---|---:|",
        f"| `supported` | {status_counts.get('supported', 0)} |",
        f"| `expected-unsupported` | {status_counts.get('expected-unsupported', 0)} |",
        f"| `diagnostic-only` | {status_counts.get('diagnostic-only', 0)} |",
        "",
        "## Compteurs Avant / Apres",
        "",
        "| Compteur | Avant inventaire FOR-461 | Apres porte dashboard courante | Delta |",
        "|---|---:|---:|---:|",
        f"| Lignes selectionnees | {before['selectedRows']} | {after['selectedRows']} | {after['selectedRows'] - before['selectedRows']} |",
        f"| Lignes supportees | {before['supportedRows']} | {after['supportedRows']} | {after['supportedRows'] - before['supportedRows']} |",
        f"| Lignes expected-unsupported | {before['expectedUnsupportedRows']} | {after['expectedUnsupportedRows']} | {after['expectedUnsupportedRows'] - before['expectedUnsupportedRows']} |",
        f"| Lignes diagnostic-only | {before['diagnosticOnlyRows']} | {after['diagnosticOnlyRows']} | {after['diagnosticOnlyRows'] - before['diagnosticOnlyRows']} |",
        f"| Lignes Skia-comparable | {before['skiaComparableRows']} | {after['skiaComparableRows']} | {after['skiaComparableRows'] - before['skiaComparableRows']} |",
        "",
        "Ces compteurs avant/apres donnent le contexte dashboard existant. Les cinq refus sont des decisions strictes `expected-unsupported` dans le manifeste D50, pas des lignes dashboard supplementaires ni des nouvelles revendications de support FOR-464, FOR-465, FOR-466, FOR-467, FOR-468 ou FOR-469.",
        "",
        "## Lignes",
        "",
        "| Inventory id | Statut strict | Ligne dashboard | Reference | Raison | Fallback |",
        "|---|---|---|---|---|---|",
    ]
    for row in evidence["strictLot1Rows"]:
        dashboard_row = row["dashboardRowId"] or "-"
        reference = row["referenceKind"] or "-"
        fallback = row.get("fallbackReason") or "-"
        lines.append(
            f"| `{row['inventoryId']}` | `{row['status']}` | `{dashboard_row}` | `{reference}` | `{row['reason']}` | `{fallback}` |"
        )
    lines += [
        "",
        "## Provenance FOR-468",
        "",
        "`skia-gm-offsetimagefilter` vient de `OffsetImageFilterGM`, porte depuis `gm/offsetimagefilter.cpp`. Les rapports historiques `OffsetImageFilterGM`, la scene voisine `SimpleOffsetImageFilterGM` et les artefacts crop/prepass associes ne sont pas des artefacts D50 row-specific pour cette ligne. Sans reference Skia candidate-specific, artefact CPU prepass/layer, artefact WebGPU prepass/layer, diff/stat et diagnostics de route avec `fallbackReason=none`, FOR-468 formalise un refus stable au lieu de promouvoir la ligne.",
        "",
        "## Provenance FOR-469",
        "",
        "`skia-gm-pathfill` vient de `PathFillGM`, porte depuis `gm/pathfill.cpp`. Les signaux historiques Path AA, stroke, cap/join/dash, convex path, edge-budget et scenes voisines ne sont pas des artefacts D50 row-specific pour cette ligne. Sans reference Skia candidate-specific, artefact CPU Path AA fill, artefact WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`, FOR-469 formalise un refus stable au lieu de promouvoir la ligne.",
        "",
        "## Non-Claims",
        "",
        "- Aucun statut dashboard n'est change par FOR-465.",
        "- Aucun statut dashboard n'est change par FOR-466.",
        "- Aucun statut dashboard n'est change par FOR-467.",
        "- Aucun statut dashboard n'est change par FOR-468.",
        "- Aucun statut dashboard n'est change par FOR-469.",
        "- Aucune ligne dashboard n'est ajoutee par FOR-465.",
        "- FOR-465 ajoute 0 ligne dashboard active.",
        "- Aucune ligne dashboard n'est ajoutee par FOR-466.",
        "- FOR-466 ajoute 0 ligne dashboard active.",
        "- Aucune ligne dashboard n'est ajoutee par FOR-467.",
        "- FOR-467 ajoute 0 ligne dashboard active.",
        "- Aucune ligne dashboard n'est ajoutee par FOR-468.",
        "- FOR-468 ajoute 0 ligne dashboard active.",
        "- Aucune ligne dashboard n'est ajoutee par FOR-469.",
        "- FOR-469 ajoute 0 ligne dashboard active.",
        "- D50 ajoute 0 ligne dashboard active: les cinq refus restent dans le manifeste strict, sans augmenter `status.pass`.",
        "- Aucune nouvelle ligne de support n'est ajoutee par FOR-465.",
        "- Aucune nouvelle ligne de support n'est ajoutee par FOR-466.",
        "- Aucune nouvelle ligne de support n'est ajoutee par FOR-467.",
        "- Aucune nouvelle ligne de support n'est ajoutee par FOR-468.",
        "- Aucune nouvelle ligne de support n'est ajoutee par FOR-469.",
        "- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-465.",
        "- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-466.",
        "- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-467.",
        "- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-468.",
        "- Aucune nouvelle fidelite Skia-comparable n'est ajoutee par FOR-469.",
        "- Aucun seuil global, calcul de score, politique de fallback, `PipelineKey`, WGSL de production, code renderer ou source upstream n'est modifie.",
        "- `skia-gm-drawminibitmaprect` n'herite pas de la preuve `skia-gm-drawbitmaprect`; il reste un refus attendu row-specific.",
        "- `skia-gm-image` n'herite pas des tests historiques ImageGM ni des preuves bitmap/image voisines; il reste un refus attendu row-specific.",
        "- `skia-gm-imagesource` n'herite pas de la fixture historique `imagesource.png`, du rapport `ImageSourceGM`, ni des preuves image voisines; il reste un refus attendu row-specific.",
        "- `skia-gm-offsetimagefilter` n'herite pas des rapports historiques `OffsetImageFilterGM`, de `SimpleOffsetImageFilterGM`, des preuves crop/prepass, ni des preuves image-filter voisines; il reste un refus attendu row-specific.",
        "- `skia-gm-pathfill` n'herite pas des preuves Path AA, stroke, cap/join/dash, convex path, edge-budget ou scenes historiques non bornees; il reste un refus attendu row-specific.",
        "- FOR-466 ne revendique aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed.",
        "- FOR-467 ne revendique aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed.",
        "- FOR-468 ne revendique aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global.",
        "- FOR-469 ne revendique aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique.",
        "- Aucune ligne `diagnostic-only` ne reste dans le lot 1 strict; les refus visibles ne sont pas du support cache.",
        "- Les 7 correspondances `supported` sont des preuves existantes, pas une revendication de support visuel superieur a 50% ni une broad Skia GM parity.",
        "",
        "## Validation",
        "",
        "```bash",
        *evidence["validationCommands"],
        "```",
        "",
    ]
    LOT1_REPORT.parent.mkdir(parents=True, exist_ok=True)
    LOT1_REPORT.write_text("\n".join(lines), encoding="utf-8")


def build_for465_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    matches = [item for item in evidence["refusals"] if item["inventoryId"] == FOR465_ROW_ID]
    require(len(matches) == 1, "FOR-465 must own exactly one refusal row")
    refusal = snapshot_refusal(matches[0], FOR465_LINEAR)
    return {
        "schemaVersion": 1,
        "linear": FOR465_LINEAR,
        "date": "2026-06-06",
        "classification": "row-specific-expected-unsupported-no-support-claim",
        "sourceFindingMemory": evidence["rowEvidenceSourceFindingMemory"],
        "sourceDraftMemory": evidence["rowEvidenceSourceDraftMemory"],
        "sourceManifest": rel(LOT1_MANIFEST),
        "row": refusal,
        "statusCountsAfterFor465": STATUS_COUNTS_BY_LINEAR[FOR465_LINEAR],
        "dashboardCountersUnchanged": evidence["dashboardCounters"],
        "scoreImpact": {
            "supportScoreIncreased": False,
            "reason": "The row is a visible expected-unsupported refusal, not a supported rendering row.",
        },
        "nonClaims": refusal["nonClaims"],
        "validationCommands": VALIDATION_COMMANDS_BY_LINEAR[FOR465_LINEAR],
    }


def build_for466_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    matches = [item for item in evidence["refusals"] if item["inventoryId"] == FOR466_ROW_ID]
    require(len(matches) == 1, "FOR-466 must own exactly one refusal row")
    refusal = snapshot_refusal(matches[0], FOR466_LINEAR)
    return {
        "schemaVersion": 1,
        "linear": FOR466_LINEAR,
        "date": "2026-06-06",
        "classification": "row-specific-expected-unsupported-no-support-claim",
        "sourceFindingMemory": evidence["rowEvidenceSourceFindingMemoryFor466"],
        "sourceDraftMemory": evidence["rowEvidenceSourceDraftMemoryFor466"],
        "sourceManifest": rel(LOT1_MANIFEST),
        "row": refusal,
        "statusCountsAfterFor466": STATUS_COUNTS_BY_LINEAR[FOR466_LINEAR],
        "dashboardCountersUnchanged": evidence["dashboardCounters"],
        "scoreImpact": {
            "supportScoreIncreased": False,
            "reason": "The row is a visible expected-unsupported refusal, not a supported rendering row.",
        },
        "nonClaims": refusal["nonClaims"],
        "validationCommands": VALIDATION_COMMANDS_BY_LINEAR[FOR466_LINEAR],
    }


def build_for467_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    matches = [item for item in evidence["refusals"] if item["inventoryId"] == FOR467_ROW_ID]
    require(len(matches) == 1, "FOR-467 must own exactly one refusal row")
    refusal = snapshot_refusal(matches[0], FOR467_LINEAR)
    return {
        "schemaVersion": 1,
        "linear": FOR467_LINEAR,
        "date": "2026-06-06",
        "classification": "row-specific-expected-unsupported-no-support-claim",
        "sourceFindingMemory": evidence["rowEvidenceSourceFindingMemoryFor467"],
        "sourceDraftMemory": evidence["rowEvidenceSourceDraftMemoryFor467"],
        "sourceManifest": rel(LOT1_MANIFEST),
        "row": refusal,
        "statusCountsAfterFor467": STATUS_COUNTS_BY_LINEAR[FOR467_LINEAR],
        "dashboardCountersUnchanged": evidence["dashboardCounters"],
        "scoreImpact": {
            "supportScoreIncreased": False,
            "reason": "The row is a visible expected-unsupported refusal, not a supported rendering row.",
        },
        "nonClaims": refusal["nonClaims"],
        "validationCommands": VALIDATION_COMMANDS_BY_LINEAR[FOR467_LINEAR],
    }


def build_for468_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    matches = [item for item in evidence["refusals"] if item["inventoryId"] == FOR468_ROW_ID]
    require(len(matches) == 1, "FOR-468 must own exactly one refusal row")
    refusal = snapshot_refusal(matches[0], FOR468_LINEAR)
    return {
        "schemaVersion": 1,
        "linear": FOR468_LINEAR,
        "date": "2026-06-06",
        "classification": "row-specific-expected-unsupported-no-support-claim",
        "sourceFindingMemory": evidence["rowEvidenceSourceFindingMemoryFor468"],
        "sourceDraftMemory": evidence["rowEvidenceSourceDraftMemoryFor468"],
        "sourceManifest": rel(LOT1_MANIFEST),
        "row": refusal,
        "statusCountsAfterFor468": STATUS_COUNTS_BY_LINEAR[FOR468_LINEAR],
        "dashboardCountersUnchanged": evidence["dashboardCounters"],
        "scoreImpact": {
            "supportScoreIncreased": False,
            "reason": "The row is a visible expected-unsupported refusal, not a supported rendering row.",
        },
        "nonClaims": refusal["nonClaims"],
        "validationCommands": VALIDATION_COMMANDS_BY_LINEAR[FOR468_LINEAR],
    }


def build_for469_evidence(evidence: dict[str, Any]) -> dict[str, Any]:
    matches = [item for item in evidence["refusals"] if item["inventoryId"] == FOR469_ROW_ID]
    require(len(matches) == 1, "FOR-469 must own exactly one refusal row")
    refusal = snapshot_refusal(matches[0], FOR469_LINEAR)
    return {
        "schemaVersion": 1,
        "linear": FOR469_LINEAR,
        "date": "2026-06-06",
        "classification": "row-specific-expected-unsupported-no-support-claim",
        "sourceFindingMemory": evidence["rowEvidenceSourceFindingMemoryFor469"],
        "sourceDraftMemory": evidence["rowEvidenceSourceDraftMemoryFor469"],
        "sourceManifest": rel(LOT1_MANIFEST),
        "row": refusal,
        "statusCountsAfterFor469": STATUS_COUNTS_BY_LINEAR[FOR469_LINEAR],
        "dashboardCountersUnchanged": evidence["dashboardCounters"],
        "scoreImpact": {
            "supportScoreIncreased": False,
            "reason": "The row is a visible expected-unsupported refusal, not a supported rendering row.",
        },
        "nonClaims": refusal["nonClaims"],
        "validationCommands": VALIDATION_COMMANDS_BY_LINEAR[FOR469_LINEAR],
    }


def write_for465_report(evidence: dict[str, Any]) -> None:
    for465 = build_for465_evidence(evidence)
    row = for465["row"]
    lines = [
        "# FOR-465 - skia-gm-drawminibitmaprect row evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-465",
        "Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json`",
        "",
        "## Resultat",
        "",
        "`skia-gm-drawminibitmaprect` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts propres a `DrawMiniBitmapRectGM` manquent encore.",
        "",
        "La preuve `skia-gm-drawbitmaprect` / `m66-bitmap-rect-nearest-skia` n'est pas heritee. Elle couvre `DrawBitmapRectGM`, pas les petites dimensions de source et les risques d'arrondi nommes par l'inventaire pour `DrawMiniBitmapRectGM`.",
        "",
        "## Contrat de refus",
        "",
        "| Champ | Valeur |",
        "|---|---|",
        f"| Inventory id | `{row['inventoryId']}` |",
        f"| Statut strict | `{row['status']}` |",
        f"| Fallback | `{row['fallbackReason']}` |",
        f"| Route CPU | `{row['cpu']['route']}` |",
        f"| Route GPU | `{row['gpu']['route']}` |",
        f"| Diff/stat | `{row['diffStats']['status']}` |",
        "",
        "## Artefacts manquants requis pour une future promotion",
        "",
        "- Reference Skia candidate-specific.",
        "- Artefact CPU et diagnostics de route.",
        "- Artefact GPU et diagnostics de route.",
        "- Payload diff/stat.",
        "- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.",
        "",
        "## Impact score",
        "",
        "Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 1 `expected-unsupported`, 4 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.",
        "",
        "## Non-claims",
        "",
        "- 0 ligne dashboard ajoutee.",
        "- 0 support ajoute.",
        "- 0 revendication Skia-comparable ajoutee.",
        "- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.",
        "",
    ]
    FOR465_REPORT.parent.mkdir(parents=True, exist_ok=True)
    FOR465_REPORT.write_text("\n".join(lines), encoding="utf-8")
    write_json(FOR465_EVIDENCE, for465)


def write_for466_report(evidence: dict[str, Any]) -> None:
    for466 = build_for466_evidence(evidence)
    row = for466["row"]
    lines = [
        "# FOR-466 - skia-gm-image row evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-466",
        "Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json`",
        "",
        "## Resultat",
        "",
        "`skia-gm-image` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `ImageGM` manquent encore.",
        "",
        "Les resultats historiques `ImageGM` et les lignes bitmap/image voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU, GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Provenance decode / fixture",
        "",
        "`ImageGM` est une scene statique issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt`, portee de `gm/image.cpp`. Elle fabrique des snapshots 64x64 de surfaces raster N32 premul dans le GM, puis les redessine. Aucun fichier image encode externe n'est decode par cette preuve, et FOR-466 ne revendique donc aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed.",
        "",
        "La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU, rendu GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Contrat de refus",
        "",
        "| Champ | Valeur |",
        "|---|---|",
        f"| Inventory id | `{row['inventoryId']}` |",
        f"| Statut strict | `{row['status']}` |",
        f"| Fallback | `{row['fallbackReason']}` |",
        f"| Route CPU | `{row['cpu']['route']}` |",
        f"| Route GPU | `{row['gpu']['route']}` |",
        f"| Diff/stat | `{row['diffStats']['status']}` |",
        "",
        "## Artefacts manquants requis pour une future promotion",
        "",
        "- Reference Skia candidate-specific pour `ImageGM`.",
        "- Artefact CPU et diagnostics de route.",
        "- Artefact GPU et diagnostics de route.",
        "- Payload diff/stat.",
        "- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.",
        "",
        "## Impact score",
        "",
        "Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 2 `expected-unsupported`, 3 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.",
        "",
        "## Non-claims",
        "",
        "- 0 ligne dashboard ajoutee.",
        "- 0 support ajoute.",
        "- 0 revendication Skia-comparable ajoutee.",
        "- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed n'est revendique.",
        "- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.",
        "",
    ]
    FOR466_REPORT.parent.mkdir(parents=True, exist_ok=True)
    FOR466_REPORT.write_text("\n".join(lines), encoding="utf-8")
    write_json(FOR466_EVIDENCE, for466)


def write_for467_report(evidence: dict[str, Any]) -> None:
    for467 = build_for467_evidence(evidence)
    row = for467["row"]
    lines = [
        "# FOR-467 - skia-gm-imagesource row evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-467",
        "Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json`",
        "",
        "## Resultat",
        "",
        "`skia-gm-imagesource` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `ImageSourceGM` manquent encore.",
        "",
        "La fixture historique `imagesource.png`, les rapports de similarite `ImageSourceGM` et les preuves image voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU, GPU, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Provenance image-source",
        "",
        "`ImageSourceGM` est une scene statique issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/ImageSourceGM.kt`, portee de `gm/imagesource.cpp`. Cette preuve n'ajoute aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed.",
        "",
        "La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU image-source, rendu WebGPU image-source, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Contrat de refus",
        "",
        "| Champ | Valeur |",
        "|---|---|",
        f"| Inventory id | `{row['inventoryId']}` |",
        f"| Statut strict | `{row['status']}` |",
        f"| Fallback | `{row['fallbackReason']}` |",
        f"| Route CPU | `{row['cpu']['route']}` |",
        f"| Route GPU | `{row['gpu']['route']}` |",
        f"| Diff/stat | `{row['diffStats']['status']}` |",
        "",
        "## Artefacts manquants requis pour une future promotion",
        "",
        "- Reference Skia candidate-specific pour `ImageSourceGM`.",
        "- Artefact CPU image-source et diagnostics de route.",
        "- Artefact GPU image-source et diagnostics de route.",
        "- Payload diff/stat.",
        "- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.",
        "",
        "## Impact score",
        "",
        "Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 3 `expected-unsupported`, 2 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.",
        "",
        "## Non-claims",
        "",
        "- 0 ligne dashboard ajoutee.",
        "- 0 support ajoute.",
        "- 0 revendication Skia-comparable ajoutee.",
        "- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed n'est revendique.",
        "- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.",
        "",
    ]
    FOR467_REPORT.parent.mkdir(parents=True, exist_ok=True)
    FOR467_REPORT.write_text("\n".join(lines), encoding="utf-8")
    write_json(FOR467_EVIDENCE, for467)


def write_for468_report(evidence: dict[str, Any]) -> None:
    for468 = build_for468_evidence(evidence)
    row = for468["row"]
    lines = [
        "# FOR-468 - skia-gm-offsetimagefilter row evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-468",
        "Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json`",
        "",
        "## Resultat",
        "",
        "`skia-gm-offsetimagefilter` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `OffsetImageFilterGM` manquent encore.",
        "",
        "Les rapports historiques `OffsetImageFilterGM`, la scene voisine `SimpleOffsetImageFilterGM`, les artefacts crop/prepass et les preuves image-filter voisines ne sont pas herites comme support D50. Ils ne fournissent pas, pour ce ticket, la combinaison reference, CPU prepass/layer, WebGPU prepass/layer, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Provenance offset image-filter",
        "",
        "`OffsetImageFilterGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt`, portee de `gm/offsetimagefilter.cpp`. La similarite historique `OffsetImageFilterGM=84.515` et les audits `simple-offsetimagefilter` restent des signaux historiques; ils ne sont pas une preuve dashboard D50 ligne par ligne.",
        "",
        "Cette preuve n'ajoute aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire, pipeline couleur global ou heritage de support image-filter voisin.",
        "",
        "## Contrat de refus",
        "",
        "| Champ | Valeur |",
        "|---|---|",
        f"| Inventory id | `{row['inventoryId']}` |",
        f"| Statut strict | `{row['status']}` |",
        f"| Fallback | `{row['fallbackReason']}` |",
        f"| Route CPU | `{row['cpu']['route']}` |",
        f"| Route GPU | `{row['gpu']['route']}` |",
        f"| Diff/stat | `{row['diffStats']['status']}` |",
        "",
        "## Artefacts manquants requis pour une future promotion",
        "",
        "- Reference Skia candidate-specific pour `OffsetImageFilterGM`.",
        "- Artefact CPU prepass/layer et diagnostics de route.",
        "- Artefact GPU prepass/layer et diagnostics de route.",
        "- Payload diff/stat.",
        "- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.",
        "",
        "## Impact score",
        "",
        "Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 4 `expected-unsupported`, 1 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.",
        "",
        "## Non-claims",
        "",
        "- 0 ligne dashboard ajoutee.",
        "- 0 support ajoute.",
        "- 0 revendication Skia-comparable ajoutee.",
        "- Aucun support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global n'est revendique.",
        "- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.",
        "",
    ]
    FOR468_REPORT.parent.mkdir(parents=True, exist_ok=True)
    FOR468_REPORT.write_text("\n".join(lines), encoding="utf-8")
    write_json(FOR468_EVIDENCE, for468)


def write_for469_report(evidence: dict[str, Any]) -> None:
    for469 = build_for469_evidence(evidence)
    row = for469["row"]
    lines = [
        "# FOR-469 - skia-gm-pathfill row evidence",
        "",
        "Date: 2026-06-06",
        "Linear: FOR-469",
        "Evidence JSON: `reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json`",
        "",
        "## Resultat",
        "",
        "`skia-gm-pathfill` est traite comme `expected-unsupported` row-specific. Le ticket ne promeut pas la scene: il formalise un refus stable parce que les artefacts D50 propres a `PathFillGM` manquent encore.",
        "",
        "Les preuves Path AA, stroke, cap/join/dash, convex path, edge-budget et scenes historiques non bornees ne sont pas heritees comme support D50. Elles ne fournissent pas, pour ce ticket, la combinaison reference, CPU Path AA fill, WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Provenance path fill",
        "",
        "`PathFillGM` est une scene issue de `skia-integration-tests/src/main/kotlin/org/skia/tests/PathFillGM.kt`, portee de `gm/pathfill.cpp`. Cette preuve n'ajoute aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique.",
        "",
        "La fixture source est identifiable, mais les artefacts D50 propres a la ligne restent manquants: reference Skia candidate-specific, rendu CPU Path AA fill, rendu WebGPU coverage borne, diff/stat et diagnostics de route avec `fallbackReason=none`.",
        "",
        "## Contrat de refus",
        "",
        "| Champ | Valeur |",
        "|---|---|",
        f"| Inventory id | `{row['inventoryId']}` |",
        f"| Statut strict | `{row['status']}` |",
        f"| Fallback | `{row['fallbackReason']}` |",
        f"| Route CPU | `{row['cpu']['route']}` |",
        f"| Route GPU | `{row['gpu']['route']}` |",
        f"| Diff/stat | `{row['diffStats']['status']}` |",
        "",
        "## Artefacts manquants requis pour une future promotion",
        "",
        "- Reference Skia candidate-specific pour `PathFillGM`.",
        "- Artefact CPU Path AA fill et diagnostics de route.",
        "- Artefact WebGPU coverage borne et diagnostics de route.",
        "- Payload diff/stat.",
        "- `fallbackReason=none` uniquement si la ligne devient vraiment supportee.",
        "",
        "## Impact score",
        "",
        "Le score de support ne monte pas. Le manifeste passe a 7 `supported`, 5 `expected-unsupported`, 0 `diagnostic-only`; le changement rend le refus visible sans ajouter de support.",
        "",
        "## Non-claims",
        "",
        "- 0 ligne dashboard ajoutee.",
        "- 0 support ajoute.",
        "- 0 revendication Skia-comparable ajoutee.",
        "- Aucun support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique n'est revendique.",
        "- Aucun changement de seuil, scoring, politique fallback, `PipelineKey`, code production, WGSL production ou source upstream.",
        "",
    ]
    FOR469_REPORT.parent.mkdir(parents=True, exist_ok=True)
    FOR469_REPORT.write_text("\n".join(lines), encoding="utf-8")
    write_json(FOR469_EVIDENCE, for469)


def require_report_and_evidence(evidence: dict[str, Any]) -> None:
    write_json(EVIDENCE, evidence)
    write_json(LOT1_MANIFEST, build_lot1_manifest(evidence))
    write_report(evidence)
    write_lot1_report(evidence)
    write_for465_report(evidence)
    write_for466_report(evidence)
    write_for467_report(evidence)
    write_for468_report(evidence)
    write_for469_report(evidence)
    written = load_json(EVIDENCE)
    manifest = load_json(LOT1_MANIFEST)
    for465 = load_json(FOR465_EVIDENCE)
    for466 = load_json(FOR466_EVIDENCE)
    for467 = load_json(FOR467_EVIDENCE)
    for468 = load_json(FOR468_EVIDENCE)
    for469 = load_json(FOR469_EVIDENCE)
    require(written["classification"] == "lot1-row-specific-refusals-no-new-support-claims", "classification mismatch")
    require(written["strictManifestFormalizedBy"] == FORMALIZATION_LINEAR, "formalization linear mismatch")
    require(written["rowEvidenceUpdatedBy"] == [FOR465_LINEAR, FOR466_LINEAR, FOR467_LINEAR, FOR468_LINEAR, FOR469_LINEAR], "row evidence linear mismatch")
    require(written["materializedCandidateCount"] == 7, "materialized count must be 7")
    require(written["expectedUnsupportedCandidateCount"] == 5, "expected-unsupported count must be 5")
    require(written["missingCandidateCount"] == 0, "missing count must be 0")
    require(written["dashboardRowsAddedByFor464"] == 0, "FOR-464 must not add dashboard rows")
    require(written["supportClaimsAddedByFor464"] == 0, "FOR-464 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor464"] == 0, "FOR-464 must not add Skia-comparable claims")
    require(written["dashboardRowsAddedByFor465"] == 0, "FOR-465 must not add dashboard rows")
    require(written["supportClaimsAddedByFor465"] == 0, "FOR-465 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor465"] == 0, "FOR-465 must not add Skia-comparable claims")
    require(written["dashboardRowsAddedByFor466"] == 0, "FOR-466 must not add dashboard rows")
    require(written["supportClaimsAddedByFor466"] == 0, "FOR-466 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor466"] == 0, "FOR-466 must not add Skia-comparable claims")
    require(written["dashboardRowsAddedByFor467"] == 0, "FOR-467 must not add dashboard rows")
    require(written["supportClaimsAddedByFor467"] == 0, "FOR-467 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor467"] == 0, "FOR-467 must not add Skia-comparable claims")
    require(written["dashboardRowsAddedByFor468"] == 0, "FOR-468 must not add dashboard rows")
    require(written["supportClaimsAddedByFor468"] == 0, "FOR-468 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor468"] == 0, "FOR-468 must not add Skia-comparable claims")
    require(written["dashboardRowsAddedByFor469"] == 0, "FOR-469 must not add dashboard rows")
    require(written["dashboardRowsAddedByD50Visibility"] == 0, "D50 must not add dashboard rows")
    require(written["supportClaimsAddedByFor469"] == 0, "FOR-469 must not add support claims")
    require(written["skiaComparableClaimsAddedByFor469"] == 0, "FOR-469 must not add Skia-comparable claims")
    require(written["visualSupportAbove50PercentClaimByFor464"] is False, "FOR-464 must not claim >50% visual support")
    require(written["visualSupportAbove50PercentClaimByFor465"] is False, "FOR-465 must not claim >50% visual support")
    require(written["visualSupportAbove50PercentClaimByFor466"] is False, "FOR-466 must not claim >50% visual support")
    require(written["visualSupportAbove50PercentClaimByFor467"] is False, "FOR-467 must not claim >50% visual support")
    require(written["visualSupportAbove50PercentClaimByFor468"] is False, "FOR-468 must not claim >50% visual support")
    require(written["visualSupportAbove50PercentClaimByFor469"] is False, "FOR-469 must not claim >50% visual support")
    require(written["dashboardStatusChangedByFor464"] is False, "FOR-464 must not change dashboard status")
    require(written["dashboardStatusChangedByFor465"] is False, "FOR-465 must not change dashboard status")
    require(written["dashboardStatusChangedByFor466"] is False, "FOR-466 must not change dashboard status")
    require(written["dashboardStatusChangedByFor467"] is False, "FOR-467 must not change dashboard status")
    require(written["dashboardStatusChangedByFor468"] is False, "FOR-468 must not change dashboard status")
    require(written["dashboardStatusChangedByFor469"] is False, "FOR-469 must not change dashboard status")
    require(written["thresholdChanged"] is False, "threshold must not change")
    require(written["scoringChanged"] is False, "scoring must not change")
    require(written["fallbackPolicyChanged"] is False, "fallback policy must not change")
    require(written["pipelineKeyChanged"] is False, "PipelineKey must not change")
    require(written["productionCodeChanged"] is False, "production code must not change")
    for key in (
        "codecSupportClaimAddedByFor466",
        "yuvSupportClaimAddedByFor466",
        "animationSupportClaimAddedByFor466",
        "exifSupportClaimAddedByFor466",
        "mipmapSupportClaimAddedByFor466",
        "tileModeSupportClaimAddedByFor466",
        "colorManagedImageSupportClaimAddedByFor466",
        "codecSupportClaimAddedByFor467",
        "yuvSupportClaimAddedByFor467",
        "animationSupportClaimAddedByFor467",
        "exifSupportClaimAddedByFor467",
        "mipmapSupportClaimAddedByFor467",
        "tileModeSupportClaimAddedByFor467",
        "dynamicSourceImageSupportClaimAddedByFor467",
        "colorManagedImageSupportClaimAddedByFor467",
        "broadImageFilterDagSupportClaimAddedByFor468",
        "cropImageFilterDagSupportClaimAddedByFor468",
        "picturePrepassSupportClaimAddedByFor468",
        "arbitraryLayerPrepassSupportClaimAddedByFor468",
        "colorPipelineGlobalSupportClaimAddedByFor468",
        "broadPathAaSupportClaimAddedByFor469",
        "strokeSupportClaimAddedByFor469",
        "capJoinDashSupportClaimAddedByFor469",
        "convexPathSupportClaimAddedByFor469",
        "edgeBudgetSupportClaimAddedByFor469",
        "historicalPathSceneSupportClaimAddedByFor469",
    ):
        require(written[key] is False, f"{key} must remain false")
    require(manifest["linear"] == FORMALIZATION_LINEAR, "lot 1 manifest linear mismatch")
    require(manifest["candidateCount"] == 12, "lot 1 manifest candidate count must be 12")
    require(
        manifest["statusCounts"] == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7},
        "lot 1 manifest status counts mismatch",
    )
    require([row["inventoryId"] for row in manifest["rows"]] == EXPECTED_LOT1, "lot 1 manifest order mismatch")
    require(
        all(row["status"] in {"supported", "expected-unsupported", "diagnostic-only"} for row in manifest["rows"]),
        "lot 1 manifest has non-strict status",
    )
    supported_rows = [row for row in manifest["rows"] if row["status"] == "supported"]
    expected_unsupported_rows = [row for row in manifest["rows"] if row["status"] == "expected-unsupported"]
    diagnostic_rows = [row for row in manifest["rows"] if row["status"] == "diagnostic-only"]
    require(len(supported_rows) == 7, "supported strict row count must be 7")
    require(len(expected_unsupported_rows) == 5, "expected-unsupported strict row count must be 5")
    require(len(diagnostic_rows) == 0, "diagnostic-only strict row count must be 0")
    for row in supported_rows:
        require(row["inventoryId"] in EXPECTED_MATERIALIZED, f"{row['inventoryId']} is not expected as supported")
        require(row["dashboardRowId"] in EXPECTED_MATERIALIZED[row["inventoryId"]], f"{row['inventoryId']} dashboard row mismatch")
        require(row["dashboardStatus"] == "pass", f"{row['inventoryId']} supported row must be pass")
        require(row["fallbackReason"] == "none", f"{row['inventoryId']} supported row must have fallbackReason=none")
        require(row["supportClaimAddedByFor464"] is False, f"{row['inventoryId']} must not add support claim")
        require(row["skiaComparableClaimAddedByFor464"] is False, f"{row['inventoryId']} must not add Skia-comparable claim")
    refusal_by_id = {row["inventoryId"]: row for row in expected_unsupported_rows}
    require(set(refusal_by_id) == {FOR465_ROW_ID, FOR466_ROW_ID, FOR467_ROW_ID, FOR468_ROW_ID, FOR469_ROW_ID}, "expected-unsupported row set mismatch")
    for refusal_id, linear, claim_suffix in (
        (FOR465_ROW_ID, FOR465_LINEAR, "For465"),
        (FOR466_ROW_ID, FOR466_LINEAR, "For466"),
        (FOR467_ROW_ID, FOR467_LINEAR, "For467"),
        (FOR468_ROW_ID, FOR468_LINEAR, "For468"),
        (FOR469_ROW_ID, FOR469_LINEAR, "For469"),
    ):
        refusal_row = refusal_by_id[refusal_id]
        contract = ROW_REFUSALS[refusal_id]
        require(refusal_row["dashboardRowId"] is None, f"{linear} refusal must not point to dashboard row")
        require(refusal_row["dashboardStatus"] is None, f"{linear} refusal must not claim an active dashboard status")
        require(refusal_row["strictDashboardStatus"] == "expected-unsupported", f"{linear} refusal strict status mismatch")
        require(refusal_row["referenceKind"] is None, f"{linear} refusal must not claim reference kind")
        require(refusal_row["fallbackReason"] == contract["fallbackReason"], f"{linear} fallback mismatch")
        require(refusal_row["cpuRoute"] == contract["cpuRoute"], f"{linear} CPU route mismatch")
        require(refusal_row["gpuRoute"] == contract["gpuRoute"], f"{linear} GPU route mismatch")
        require(refusal_row[f"supportClaimAddedBy{claim_suffix}"] is False, f"{linear} must not add support claim")
        require(refusal_row[f"skiaComparableClaimAddedBy{claim_suffix}"] is False, f"{linear} must not add Skia-comparable claim")
        require(refusal_row[contract["neighborEvidenceFlag"]] is False, f"{linear} must not inherit adjacent proof")
    require([row["inventoryId"] for row in diagnostic_rows] == EXPECTED_MISSING, "diagnostic-only row order mismatch")
    for row in diagnostic_rows:
        require(row["dashboardRowId"] is None, f"{row['inventoryId']} diagnostic row must not point to dashboard")
        require(row["dashboardStatus"] is None, f"{row['inventoryId']} diagnostic row must not have dashboard status")
        require(row["referenceKind"] is None, f"{row['inventoryId']} diagnostic row must not claim reference kind")
        require(row["supportClaimAddedByFor464"] is False, f"{row['inventoryId']} must not add support claim")
        require(row["skiaComparableClaimAddedByFor464"] is False, f"{row['inventoryId']} must not add Skia-comparable claim")
        require(len(row["requiredEvidence"]) >= 6, f"{row['inventoryId']} diagnostic row lacks required evidence")
    non_claims = manifest["nonClaims"]
    require(non_claims["dashboardRowsAddedByFor464"] == 0, "manifest must say 0 dashboard rows added")
    require(non_claims["supportClaimsAddedByFor464"] == 0, "manifest must say 0 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor464"] == 0, "manifest must say 0 Skia-comparable claims")
    require(non_claims["dashboardRowsAddedByFor465"] == 0, "manifest must say 0 FOR-465 dashboard rows added")
    require(non_claims["supportClaimsAddedByFor465"] == 0, "manifest must say 0 FOR-465 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor465"] == 0, "manifest must say 0 FOR-465 Skia-comparable claims")
    require(non_claims["dashboardRowsAddedByFor466"] == 0, "manifest must say 0 FOR-466 dashboard rows added")
    require(non_claims["supportClaimsAddedByFor466"] == 0, "manifest must say 0 FOR-466 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor466"] == 0, "manifest must say 0 FOR-466 Skia-comparable claims")
    require(non_claims["dashboardRowsAddedByFor467"] == 0, "manifest must say 0 FOR-467 dashboard rows added")
    require(non_claims["supportClaimsAddedByFor467"] == 0, "manifest must say 0 FOR-467 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor467"] == 0, "manifest must say 0 FOR-467 Skia-comparable claims")
    require(non_claims["dashboardRowsAddedByFor468"] == 0, "manifest must say 0 FOR-468 dashboard rows added")
    require(non_claims["supportClaimsAddedByFor468"] == 0, "manifest must say 0 FOR-468 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor468"] == 0, "manifest must say 0 FOR-468 Skia-comparable claims")
    require(non_claims["dashboardRowsAddedByFor469"] == 0, "manifest must say 0 FOR-469 dashboard rows added")
    require(non_claims["dashboardRowsAddedByD50Visibility"] == 0, "manifest must say D50 adds 0 dashboard rows")
    require(non_claims["supportClaimsAddedByFor469"] == 0, "manifest must say 0 FOR-469 support claims")
    require(non_claims["skiaComparableClaimsAddedByFor469"] == 0, "manifest must say 0 FOR-469 Skia-comparable claims")
    require(non_claims["visualSupportAbove50PercentClaim"] is False, "manifest must reject >50% visual support claim")
    require([item["inventoryId"] for item in written["missing"]] == EXPECTED_MISSING, "missing candidate order mismatch")
    require([item["inventoryId"] for item in written["refusals"]] == [FOR465_ROW_ID, FOR466_ROW_ID, FOR467_ROW_ID, FOR468_ROW_ID, FOR469_ROW_ID], "refusal candidate mismatch")
    require(for465["linear"] == FOR465_LINEAR, "FOR-465 evidence linear mismatch")
    require(for465["row"]["inventoryId"] == FOR465_ROW_ID, "FOR-465 evidence row mismatch")
    require(for465["row"]["status"] == "expected-unsupported", "FOR-465 evidence status mismatch")
    require(for465["row"]["fallbackReason"] == ROW_REFUSALS[FOR465_ROW_ID]["fallbackReason"], "FOR-465 evidence fallback mismatch")
    require(for465["row"]["nonClaims"]["drawbitmaprectEvidenceInherited"] is False, "FOR-465 evidence must not inherit drawbitmaprect")
    require(for465["scoreImpact"]["supportScoreIncreased"] is False, "FOR-465 must not increase support score")
    require(for466["linear"] == FOR466_LINEAR, "FOR-466 evidence linear mismatch")
    require(for466["row"]["inventoryId"] == FOR466_ROW_ID, "FOR-466 evidence row mismatch")
    require(for466["row"]["status"] == "expected-unsupported", "FOR-466 evidence status mismatch")
    require(for466["row"]["fallbackReason"] == ROW_REFUSALS[FOR466_ROW_ID]["fallbackReason"], "FOR-466 evidence fallback mismatch")
    require(for466["row"]["nonClaims"]["historicalImageEvidenceInherited"] is False, "FOR-466 evidence must not inherit historical image proof")
    provenance = for466["row"].get("decodeFixtureProvenance")
    require(isinstance(provenance, dict), "FOR-466 evidence must record decode/fixture provenance")
    require(provenance.get("externalEncodedFixture") == "none", "FOR-466 must not claim an external encoded fixture")
    require("not exercised" in provenance.get("decodePath", ""), "FOR-466 must document decode path as not exercised")
    require("no row-specific D50 reference/CPU/GPU artifacts" in provenance.get("fixtureAvailability", ""), "FOR-466 fixture availability mismatch")
    require(for466["scoreImpact"]["supportScoreIncreased"] is False, "FOR-466 must not increase support score")
    require(for467["linear"] == FOR467_LINEAR, "FOR-467 evidence linear mismatch")
    require(for467["row"]["inventoryId"] == FOR467_ROW_ID, "FOR-467 evidence row mismatch")
    require(for467["row"]["status"] == "expected-unsupported", "FOR-467 evidence status mismatch")
    require(for467["row"]["fallbackReason"] == ROW_REFUSALS[FOR467_ROW_ID]["fallbackReason"], "FOR-467 evidence fallback mismatch")
    require(for467["row"]["nonClaims"]["imageSourceEvidenceInherited"] is False, "FOR-467 evidence must not inherit image-source proof")
    source_provenance = for467["row"].get("sourceImageProvenance")
    require(isinstance(source_provenance, dict), "FOR-467 evidence must record image-source provenance")
    require(source_provenance.get("referenceFixture") == "imagesource.png exists for historical integration testing, but is not a D50 dashboard row-specific artifact", "FOR-467 reference fixture provenance mismatch")
    require("no row-specific D50 reference/CPU/GPU artifacts" in source_provenance.get("fixtureAvailability", ""), "FOR-467 fixture availability mismatch")
    require(source_provenance.get("dynamicSourceImage") == "not claimed", "FOR-467 must not claim dynamic source image support")
    require(for467["scoreImpact"]["supportScoreIncreased"] is False, "FOR-467 must not increase support score")
    require(for468["linear"] == FOR468_LINEAR, "FOR-468 evidence linear mismatch")
    require(for468["row"]["inventoryId"] == FOR468_ROW_ID, "FOR-468 evidence row mismatch")
    require(for468["row"]["status"] == "expected-unsupported", "FOR-468 evidence status mismatch")
    require(for468["row"]["fallbackReason"] == ROW_REFUSALS[FOR468_ROW_ID]["fallbackReason"], "FOR-468 evidence fallback mismatch")
    require(for468["row"]["nonClaims"]["offsetImageFilterEvidenceInherited"] is False, "FOR-468 evidence must not inherit offset image-filter proof")
    offset_provenance = for468["row"].get("offsetImageFilterProvenance")
    require(isinstance(offset_provenance, dict), "FOR-468 evidence must record offset image-filter provenance")
    require(offset_provenance.get("scene") == "OffsetImageFilterGM", "FOR-468 scene provenance mismatch")
    require("84.515" in offset_provenance.get("historicalSimilarity", ""), "FOR-468 historical similarity provenance mismatch")
    require("no row-specific D50 reference/CPU/GPU artifacts" in offset_provenance.get("fixtureAvailability", ""), "FOR-468 fixture availability mismatch")
    require(offset_provenance.get("boundedOffsetFilter") == "not claimed as supported", "FOR-468 must not claim bounded offset filter support")
    require(for468["scoreImpact"]["supportScoreIncreased"] is False, "FOR-468 must not increase support score")
    require(for469["linear"] == FOR469_LINEAR, "FOR-469 evidence linear mismatch")
    require(for469["row"]["inventoryId"] == FOR469_ROW_ID, "FOR-469 evidence row mismatch")
    require(for469["row"]["status"] == "expected-unsupported", "FOR-469 evidence status mismatch")
    require(for469["row"]["fallbackReason"] == ROW_REFUSALS[FOR469_ROW_ID]["fallbackReason"], "FOR-469 evidence fallback mismatch")
    require(for469["row"]["nonClaims"]["pathFillEvidenceInherited"] is False, "FOR-469 evidence must not inherit pathfill proof")
    path_provenance = for469["row"].get("pathFillProvenance")
    require(isinstance(path_provenance, dict), "FOR-469 evidence must record path fill provenance")
    require(path_provenance.get("scene") == "PathFillGM", "FOR-469 scene provenance mismatch")
    require("no row-specific D50 reference/CPU/GPU artifacts" in path_provenance.get("fixtureAvailability", ""), "FOR-469 fixture availability mismatch")
    require(path_provenance.get("boundedFillUnderEdgeBudget") == "not claimed as supported", "FOR-469 must not claim bounded fill support")
    require(path_provenance.get("fillRule") == "not claimed as supported", "FOR-469 must not claim fill-rule support")
    require(for469["scoreImpact"]["supportScoreIncreased"] is False, "FOR-469 must not increase support score")
    report = REPORT.read_text(encoding="utf-8")
    for required in (
        "0 `tracked-gap` et 0 `fail`",
        "D50 n'ajoute aucune ligne dashboard active",
        "FOR-462 n'ajoute aucun claim de support",
        "broad Skia GM parity",
        "5 refus `expected-unsupported` documentes hors dashboard actif",
    ):
        require(required in report, f"report missing: {required}")
    lot_report = LOT1_REPORT.read_text(encoding="utf-8")
    for required in (
        "FOR-465 ajoute 0 ligne dashboard",
        "FOR-466 ajoute 0 ligne dashboard",
        "FOR-467 ajoute 0 ligne dashboard",
        "FOR-468 ajoute 0 ligne dashboard",
        "FOR-469 ajoute 0 ligne dashboard",
        "D50 ajoute 0 ligne dashboard active",
        "0 revendication de support",
        "0 revendication Skia-comparable",
        "score de support ne monte pas",
        "`expected-unsupported`",
        "`diagnostic-only`",
        "Compteurs Avant / Apres",
        "Ces compteurs avant/apres donnent le contexte dashboard existant",
        "codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed",
        "source image dynamique ou image color-managed",
        "support large image-filter DAG",
        "support large Path AA",
        "broad Skia GM parity",
    ):
        require(required in lot_report, f"lot 1 report missing: {required}")
    for465_report = FOR465_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "n'est pas heritee",
        "0 support ajoute",
        "score de support ne monte pas",
    ):
        require(required in for465_report, f"FOR-465 report missing: {required}")
    for466_report = FOR466_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas herites",
        "Provenance decode / fixture",
        "Aucun fichier image encode externe n'est decode",
        "0 support ajoute",
        "score de support ne monte pas",
        "codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed",
    ):
        require(required in for466_report, f"FOR-466 report missing: {required}")
    for467_report = FOR467_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas herites",
        "Provenance image-source",
        "imagesource.png",
        "0 support ajoute",
        "score de support ne monte pas",
        "codec, YUV, animation, EXIF, mipmap, tile-mode, source image dynamique ou image color-managed",
    ):
        require(required in for467_report, f"FOR-467 report missing: {required}")
    for468_report = FOR468_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas herites",
        "Provenance offset image-filter",
        "OffsetImageFilterGM=84.515",
        "0 support ajoute",
        "score de support ne monte pas",
        "support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global",
    ):
        require(required in for468_report, f"FOR-468 report missing: {required}")
    for469_report = FOR469_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas heritees",
        "Provenance path fill",
        "PathFillGM",
        "0 support ajoute",
        "score de support ne monte pas",
        "support large Path AA, stroke, cap/join/dash, convex path, edge-budget ou heritage de support path historique",
    ):
        require(required in for469_report, f"FOR-469 report missing: {required}")


def main() -> None:
    require_scope()
    evidence = build_evidence()
    require_report_and_evidence(evidence)
    require_scope()
    print(
        f"{FOR469_LINEAR} validation passed: "
        f"materialized={evidence['materializedCandidateCount']} "
        f"expectedUnsupported={evidence['expectedUnsupportedCandidateCount']} "
        f"missing={evidence['missingCandidateCount']} "
        f"dashboardTotal={evidence['dashboardCounters']['total']}"
    )


if __name__ == "__main__":
    main()
