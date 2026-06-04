#!/usr/bin/env python3
"""Audit the FOR-323 CircularArcsStrokeButt selected-cell Skia reference."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from PIL import Image

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-323"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-skia-reference-audit-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for323"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-323-circular-arcs-stroke-butt-selected-cell-skia-reference.md"
)

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
FOR322_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-322-circular-arcs-stroke-butt-selected-cell-harness.md"
)
FOR321_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321.json"
)
FOR320_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320.json"
)
FOR319_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-for319/"
    "circular-arcs-stroke-butt-micro-fixture-for319.json"
)

CIRCULAR_ARCS_GM = (
    PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/CircularArcsGM.kt"
)
FOR322_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    "CircularArcsStrokeButtSelectedCellCaptureTest.kt"
)
FULL_GM_REFERENCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png"
)
FOR322_CPU = FOR322_DIR / "cpu.png"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR323_SKIA = ARTIFACT_DIR / "skia.png"
FOR323_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
DECISION_CONTAMINATED = "CIRCULAR_ARCS_STROKE_BUTT_FULL_GM_CROP_CONTAMINATED"
DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_MISSING"
DECISION_AMBIGUOUS = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_REFERENCE_AMBIGUOUS"

FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
FOR321_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_BLOCKED"
FOR320_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED"
FOR319_DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED"
FOR319_FIXTURE_ID = "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true"
SOURCE_GM = "CircularArcsStrokeButtGM"
SOURCE_ROW_ID = "circular-arcs-stroke-butt-webgpu"
FOR318_TARGET_ID = "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe"
GPU_REFUSAL = "coverage.stroke-cap-join-visual-parity-below-threshold"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
FULL_GM_DIMENSIONS = {"width": 1000, "height": 1000}
FULL_GM_ARC_RECT = [140, 520, 180, 560]
BOUNDED_ARC_RECT = [20, 20, 60, 60]
STROKE_WIDTH = 15
STROKE_MARGIN = STROKE_WIDTH / 2
CELL_STEP = 60
ARC_DIAMETER = 40
FULL_GM_CROP_BOX = [
    FULL_GM_ARC_RECT[0] - BOUNDED_ARC_RECT[0],
    FULL_GM_ARC_RECT[1] - BOUNDED_ARC_RECT[1],
    FULL_GM_ARC_RECT[2] + (EXPECTED_DIMENSIONS["width"] - BOUNDED_ARC_RECT[2]),
    FULL_GM_ARC_RECT[3] + (EXPECTED_DIMENSIONS["height"] - BOUNDED_ARC_RECT[3]),
]

SELECTED_CELL_EXPECTED = {
    "fixtureId": FOR319_FIXTURE_ID,
    "sourceGm": SOURCE_GM,
    "sourceRowId": SOURCE_ROW_ID,
    "sourceFutureTarget": FOR318_TARGET_ID,
    "cellCount": 1,
    "rowIndex": 0,
    "columnIndex": 2,
    "startDegrees": 0,
    "sweepDegrees": 90,
    "complementSweepDegrees": -270,
    "useCenter": False,
    "aa": True,
    "style": "kStroke_Style",
    "strokeWidth": STROKE_WIDTH,
    "strokeCap": "kButt_Cap",
    "includedCaps": ["kButt_Cap"],
    "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
    "includesHairlineStrokeWidth0": False,
    "includesFill": False,
    "includesDash": False,
    "paintAlpha": 100,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-323 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def image_size(path: Path) -> dict[str, int]:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        width, height = image.size
    return {"width": width, "height": height}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def validate_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")
    if cell.get("fullGmCanvasArcRectLTRB") != FULL_GM_ARC_RECT:
        fail(f"{label} must document full-GM rect {FULL_GM_ARC_RECT}")
    if cell.get("boundedCanvasArcRectLTRB") != BOUNDED_ARC_RECT:
        fail(f"{label} must document bounded rect {BOUNDED_ARC_RECT}")


def validate_source_code() -> None:
    gm = read_text(CIRCULAR_ARCS_GM)
    for needle in [
        "public class CircularArcsStrokeButtGM",
        "paint.strokeCap = SkPaint.Cap.kButt_Cap",
        "strokeWidth = 15f",
        "val kStarts: FloatArray = floatArrayOf(0f, 10f, 30f, 45f, 90f, 165f, 180f, 270f)",
        "val kSweeps: FloatArray = floatArrayOf(1f, 45f, 90f, 130f, 180f, 184f, 300f, 355f)",
        "canvas.translate(kRect.width() + pad, 0f)",
        "canvas.translate(0f, kRect.height() + pad)",
    ]:
        if needle not in gm:
            fail(f"CircularArcsGM source missing `{needle}`")

    harness = read_text(FOR322_TEST)
    for needle in [
        "class CircularArcsStrokeButtSelectedCellCaptureTest",
        "class SelectedCellGM",
        "c.translate(20f, 20f)",
        "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
        "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
        "strokeWidth = 15f",
        "strokeCap = SkPaint.Cap.kButt_Cap",
        "File(dir, \"skia.png\").delete()",
        "fullGmSubstitutionAccepted",
    ]:
        if needle not in harness:
            fail(f"FOR-322 harness source missing `{needle}`")


def validate_previous_contracts() -> dict[str, Any]:
    for319 = load_json(FOR319_ARTIFACT)
    for320 = load_json(FOR320_ARTIFACT)
    for321 = load_json(FOR321_ARTIFACT)
    for322 = load_json(FOR322_ARTIFACT)
    for322_report = read_text(FOR322_REPORT)
    route_gpu = load_json(FOR322_ROUTE_GPU)
    stats = load_json(FOR322_STATS)

    if for319.get("linear") != "FOR-319" or for319.get("decision") != FOR319_DECISION_APPLIED:
        fail("FOR-319 preflight contract changed")
    if for320.get("linear") != "FOR-320" or for320.get("decision") != FOR320_DECISION_BLOCKED:
        fail("FOR-320 blocked contract changed")
    if for321.get("linear") != "FOR-321" or for321.get("decision") != FOR321_DECISION_BLOCKED:
        fail("FOR-321 blocked contract changed")
    if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
        fail("FOR-322 harness-ready contract changed")

    cell = for322.get("selectedCell")
    if not isinstance(cell, dict):
        fail("FOR-322 selectedCell missing")
    validate_cell(cell, "FOR-322 selectedCell")

    coverage = for322.get("artifactCoverage")
    if not isinstance(coverage, dict):
        fail("FOR-322 artifactCoverage missing")
    if coverage.get("fullGmSubstitutionRejected") is not True:
        fail("FOR-322 must keep full-GM substitution rejected")
    if "skia.png" not in coverage.get("blockedArtifactIds", []):
        fail("FOR-322 must keep skia.png blocked")

    if stats.get("dimensions") != EXPECTED_DIMENSIONS:
        fail("FOR-322 selected-cell dimensions changed")
    if stats.get("fullGmReferenceAccepted") is not False:
        fail("FOR-322 stats accepts full-GM reference")
    if stats.get("fullGmSubstitutionAccepted") is not False:
        fail("FOR-322 stats accepts full-GM substitution")
    if stats.get("artifacts", {}).get("skia.png") != "blocked-no-selected-cell-upstream-skia-reference":
        fail("FOR-322 stats no longer blocks skia.png")

    if route_gpu.get("status") != "expected-unsupported":
        fail("FOR-322 WebGPU route must remain expected-unsupported")
    if route_gpu.get("refusalReason") != GPU_REFUSAL:
        fail("FOR-322 WebGPU refusal reason changed")
    if route_gpu.get("supportStatus") != "not-supported":
        fail("FOR-322 WebGPU route support status changed")

    for needle in [
        "not-supported",
        "Full-GM substitution rejected",
        "route-gpu.json",
        GPU_REFUSAL,
        "no CircularArcsStrokeButtGM support promotion",
    ]:
        if needle not in for322_report:
            fail(f"FOR-322 report missing `{needle}`")

    return {
        "for319Artifact": rel(FOR319_ARTIFACT),
        "for320Artifact": rel(FOR320_ARTIFACT),
        "for321Artifact": rel(FOR321_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for322Report": rel(FOR322_REPORT),
        "for322Cpu": rel(FOR322_CPU),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
        "for322Stats": rel(FOR322_STATS),
    }


def crop_from_full_gm() -> Image.Image:
    with Image.open(FULL_GM_REFERENCE).convert("RGBA") as image:
        if image.size != (FULL_GM_DIMENSIONS["width"], FULL_GM_DIMENSIONS["height"]):
            fail(f"full-GM PNG dimensions changed: {image.size}")
        return image.crop(tuple(FULL_GM_CROP_BOX))


def non_white_probe(image: Image.Image, box: tuple[int, int, int, int]) -> dict[str, Any]:
    left, top, right, bottom = box
    non_white = 0
    bbox: list[int] | None = None
    for y in range(top, bottom):
        for x in range(left, right):
            red, green, blue, alpha = image.getpixel((x, y))
            if (red, green, blue, alpha) != (255, 255, 255, 255):
                non_white += 1
                if bbox is None:
                    bbox = [x, y, x, y]
                else:
                    bbox = [
                        min(bbox[0], x),
                        min(bbox[1], y),
                        max(bbox[2], x),
                        max(bbox[3], y),
                    ]
    total = (right - left) * (bottom - top)
    return {
        "boxLTRB": [left, top, right, bottom],
        "nonWhitePixels": non_white,
        "totalPixels": total,
        "nonWhiteBoundingBoxLTRB": bbox,
    }


def overlap(
    a: list[float],
    b: list[float],
) -> list[float] | None:
    left = max(a[0], b[0])
    top = max(a[1], b[1])
    right = min(a[2], b[2])
    bottom = min(a[3], b[3])
    if left >= right or top >= bottom:
        return None
    return [left, top, right, bottom]


def stroke_bounds(rect: list[float]) -> list[float]:
    return [
        rect[0] - STROKE_MARGIN,
        rect[1] - STROKE_MARGIN,
        rect[2] + STROKE_MARGIN,
        rect[3] + STROKE_MARGIN,
    ]


def build_full_gm_crop_analysis() -> dict[str, Any]:
    if not FULL_GM_REFERENCE.is_file():
        return {
            "strategy": "crop-full-gm-reference",
            "status": "missing",
            "accepted": False,
            "decisionIfOnlySource": DECISION_MISSING,
            "sourcePng": rel(FULL_GM_REFERENCE),
            "blockedReasons": ["full-GM Skia PNG is missing"],
        }

    crop = crop_from_full_gm()
    if crop.size != (EXPECTED_DIMENSIONS["width"], EXPECTED_DIMENSIONS["height"]):
        fail(f"full-GM crop dimensions changed: {crop.size}")

    crop_rect = [0.0, 0.0, float(EXPECTED_DIMENSIONS["width"]), float(EXPECTED_DIMENSIONS["height"])]
    selected_rect = [float(value) for value in BOUNDED_ARC_RECT]
    selected_bounds = stroke_bounds(selected_rect)
    neighbor_rects = {
        "leftColumnSameRow": [
            selected_rect[0] - CELL_STEP,
            selected_rect[1],
            selected_rect[2] - CELL_STEP,
            selected_rect[3],
        ],
        "rightColumnSameRow": [
            selected_rect[0] + CELL_STEP,
            selected_rect[1],
            selected_rect[2] + CELL_STEP,
            selected_rect[3],
        ],
        "nextRowSameColumn": [
            selected_rect[0],
            selected_rect[1] + CELL_STEP,
            selected_rect[2],
            selected_rect[3] + CELL_STEP,
        ],
    }
    overlaps = []
    for name, rect in neighbor_rects.items():
        bounds = stroke_bounds(rect)
        clipped = overlap(bounds, crop_rect)
        overlaps.append(
            {
                "neighbor": name,
                "arcRectInCropLTRB": rect,
                "strokeBoundsInCropLTRB": bounds,
                "overlapsCrop": clipped is not None,
                "overlapLTRB": clipped,
            }
        )

    pixel_probes = {
        "leftNeighborMargin": non_white_probe(crop, (0, 0, 8, 80)),
        "rightNeighborMargin": non_white_probe(crop, (73, 0, 80, 80)),
        "lowerNeighborMargin": non_white_probe(crop, (0, 73, 80, 80)),
        "topSeparatorMargin": non_white_probe(crop, (0, 0, 80, 8)),
    }
    contaminated = any(item["overlapsCrop"] for item in overlaps) and any(
        probe["nonWhitePixels"] > 0 for probe in pixel_probes.values()
    )
    return {
        "strategy": "crop-full-gm-reference",
        "status": "rejected-contaminated" if contaminated else "rejected-ambiguous",
        "accepted": False,
        "decisionIfOnlySource": DECISION_CONTAMINATED if contaminated else DECISION_AMBIGUOUS,
        "sourcePng": rel(FULL_GM_REFERENCE),
        "sourceDimensions": FULL_GM_DIMENSIONS,
        "sourceSha256": sha256(FULL_GM_REFERENCE),
        "cropBoxLTRB": FULL_GM_CROP_BOX,
        "cropDimensions": EXPECTED_DIMENSIONS,
        "selectedArcRectInCropLTRB": BOUNDED_ARC_RECT,
        "selectedStrokeBoundsInCropLTRB": selected_bounds,
        "strokeMarginPx": STROKE_MARGIN,
        "cellStepPx": CELL_STEP,
        "arcDiameterPx": ARC_DIAMETER,
        "neighborStrokeOverlap": overlaps,
        "pixelProbe": pixel_probes,
        "contaminated": contaminated,
        "blockedReasons": [
            "bounded 80x80 crop requires the selected stroke margin",
            "left/right/lower neighboring stroke bounds overlap that crop margin",
            "full-GM crop pixels are non-white in the overlapped margins",
            "crop provenance is full-GM, not an isolated selected-cell Skia render",
        ],
    }


def validate_candidate_skia(crop_analysis: dict[str, Any]) -> dict[str, Any]:
    if not FOR323_SKIA.exists():
        return {
            "path": rel(FOR323_SKIA),
            "provenancePath": rel(FOR323_PROVENANCE),
            "present": False,
            "accepted": False,
            "status": "blocked-missing",
            "blockedReasons": [
                "no selected-cell skia.png is checked in under the FOR-323 artifact directory",
                "no strict-scope Skia selected-cell renderer artifact is available",
            ],
        }
    if not FOR323_SKIA.is_file():
        fail(f"{rel(FOR323_SKIA)} exists but is not a file")

    size = image_size(FOR323_SKIA)
    full_sha = sha256(FULL_GM_REFERENCE) if FULL_GM_REFERENCE.is_file() else None
    candidate_sha = sha256(FOR323_SKIA)
    cpu_sha = sha256(FOR322_CPU) if FOR322_CPU.is_file() else None
    reasons = []
    provenance: dict[str, Any] | None = None
    if size == FULL_GM_DIMENSIONS:
        reasons.append("candidate skia.png has full-GM dimensions")
    if size != EXPECTED_DIMENSIONS:
        reasons.append(f"candidate skia.png dimensions are {size}, expected {EXPECTED_DIMENSIONS}")
    if full_sha is not None and candidate_sha == full_sha:
        reasons.append("candidate skia.png is byte-identical to the full-GM PNG")
    if cpu_sha is not None and candidate_sha == cpu_sha:
        reasons.append("candidate skia.png is byte-identical to FOR-322 Kanvas CPU output")
    if crop_analysis.get("status") == "rejected-contaminated":
        reasons.append("full-GM crop source is contaminated and cannot prove isolation")
    if not FOR323_PROVENANCE.is_file():
        reasons.append("candidate lacks an isolated Skia selected-cell provenance contract")
    else:
        provenance = load_json(FOR323_PROVENANCE)
        expected = {
            "sourceType": "isolated-skia-selected-cell-render",
            "fixtureId": FOR319_FIXTURE_ID,
            "sourceGm": SOURCE_GM,
            "dimensions": EXPECTED_DIMENSIONS,
            "fullGmCrop": False,
            "fullGmSubstitutionAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
        }
        for key, expected_value in expected.items():
            if provenance.get(key) != expected_value:
                reasons.append(
                    f"provenance.{key} expected {expected_value!r}, got {provenance.get(key)!r}"
                )
        if not provenance.get("command"):
            reasons.append("provenance.command must document the isolated Skia render command")

    if reasons:
        fail(f"{rel(FOR323_SKIA)} is not an acceptable Skia selected-cell reference: {'; '.join(reasons)}")

    return {
        "path": rel(FOR323_SKIA),
        "provenancePath": rel(FOR323_PROVENANCE),
        "present": True,
        "accepted": True,
        "status": "available-isolated-skia-reference",
        "dimensions": size,
        "sha256": candidate_sha,
        "provenance": provenance,
        "blockedReasons": [],
    }


def build_artifact() -> dict[str, Any]:
    validate_source_code()
    follows = validate_previous_contracts()
    full_gm_crop = build_full_gm_crop_analysis()
    candidate = validate_candidate_skia(full_gm_crop)
    full_gm_missing = full_gm_crop.get("status") == "missing"

    decision = DECISION_READY if candidate["accepted"] else full_gm_crop["decisionIfOnlySource"]
    skia_ready = decision == DECISION_READY

    blocked_reasons = []
    if not skia_ready:
        blocked_reasons.extend(candidate["blockedReasons"])
        blocked_reasons.extend(full_gm_crop["blockedReasons"])

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "sceneId": SCENE_ID,
        "skiaPngReady": skia_ready,
        "acceptedSkiaPng": candidate["path"] if skia_ready else None,
        "selectedCell": load_json(FOR322_ARTIFACT)["selectedCell"],
        "follows": follows,
        "auditedStrategies": {
            "isolatedSelectedCellSkiaReference": candidate,
            "fullGmCrop": full_gm_crop,
            "for322CpuOutput": {
                "path": rel(FOR322_CPU),
                "present": FOR322_CPU.is_file(),
                "accepted": False,
                "status": "rejected-cpu-kanvas-not-skia",
                "dimensions": image_size(FOR322_CPU) if FOR322_CPU.is_file() else None,
                "blockedReason": "FOR-322 cpu.png is Kanvas CPU output and must not be called a Skia reference.",
            },
            "fullGmScoreEvidence": {
                "accepted": False,
                "status": "rejected-full-gm-score-not-cell-reference",
                "blockedReason": "Full-GM similarity scores are not isolated selected-cell Skia pixels.",
            },
        },
        "referenceDecision": {
            "decision": decision,
            "readyDecision": DECISION_READY,
            "missingDecision": DECISION_MISSING,
            "contaminatedDecision": DECISION_CONTAMINATED,
            "ambiguousDecision": DECISION_AMBIGUOUS,
            "skiaPngReady": skia_ready,
            "blockedReasons": blocked_reasons,
            "fullGmMissing": full_gm_missing,
        },
        "geometry": {
            "fullGmCanvasArcRectLTRB": FULL_GM_ARC_RECT,
            "boundedCanvasArcRectLTRB": BOUNDED_ARC_RECT,
            "startDegrees": 0,
            "sweepDegrees": 90,
            "complementSweepDegrees": -270,
            "useCenter": False,
            "aa": True,
            "strokeWidth": STROKE_WIDTH,
            "strokeCap": "kButt_Cap",
            "paintAlpha": 100,
            "drawArcCallCount": 2,
        },
        "preservedContracts": {
            "supportStatus": "not-supported",
            "fullGmSubstitutionAccepted": False,
            "fullGmScoreEvidenceAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
            "gpuRouteStatus": "expected-unsupported",
            "gpuRefusalReason": GPU_REFUSAL,
            "readinessMovement": False,
            "releaseGateChanged": False,
        },
        "nonGoals": [
            "no production renderer behavior changed",
            "no WGSL shader changed",
            "no threshold changed",
            "no fallback policy changed",
            "no scene support status changed",
            "no Kadre or native dependency introduced",
            "no full-GM PNG, full-GM score, unproven crop, or CPU Kanvas output accepted as skia.png",
        ],
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    full_gm = data["auditedStrategies"]["fullGmCrop"]
    candidate = data["auditedStrategies"]["isolatedSelectedCellSkiaReference"]
    geometry = data["geometry"]
    blocked = "\n".join(f"- {reason}" for reason in data["referenceDecision"]["blockedReasons"])
    overlaps = "\n".join(
        "| `{neighbor}` | `{overlapsCrop}` | `{overlapLTRB}` |".format(**item)
        for item in full_gm.get("neighborStrokeOverlap", [])
    )
    probes = "\n".join(
        f"| `{name}` | `{probe['nonWhitePixels']}` / `{probe['totalPixels']}` | `{probe['nonWhiteBoundingBoxLTRB']}` |"
        for name, probe in full_gm.get("pixelProbe", {}).items()
    )
    non_goals = "\n".join(f"- {item}" for item in data["nonGoals"])

    report = f"""# FOR-323 CircularArcsStrokeButt Selected-Cell Skia Reference Audit

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-323 audits whether a valid selected-cell `skia.png` reference can be
produced for the FOR-319 `CircularArcsStrokeButtGM` cell. The current decision
is `{data["decision"]}`. `skia.png` ready: `{data["skiaPngReady"]}`.

No selected-cell Skia reference is produced by this ticket. The full-GM PNG is
available, but the 80x80 crop required to match the FOR-322 bounded harness is
rejected because neighboring stroke margins overlap the crop. FOR-322 `cpu.png`
is also rejected as Skia evidence because it is Kanvas CPU output.

## Selected Cell Geometry

| Field | Value |
|---|---|
| full-GM arc rect | `{md_value(geometry["fullGmCanvasArcRectLTRB"])}` |
| bounded arc rect | `{md_value(geometry["boundedCanvasArcRectLTRB"])}` |
| start | `{geometry["startDegrees"]}` |
| sweep | `{geometry["sweepDegrees"]}` |
| complement | `{geometry["complementSweepDegrees"]}` |
| useCenter | `{geometry["useCenter"]}` |
| aa | `{geometry["aa"]}` |
| stroke width | `{geometry["strokeWidth"]}` |
| stroke cap | `{geometry["strokeCap"]}` |
| alpha | `{geometry["paintAlpha"]}` |
| drawArc calls | `{geometry["drawArcCallCount"]}` |

The selected cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`,
alpha `100`, with two arcs.

## Candidate Reference

| Field | Value |
|---|---|
| path | `{candidate["path"]}` |
| present | `{candidate["present"]}` |
| accepted | `{candidate["accepted"]}` |
| status | `{candidate["status"]}` |

## Full-GM Crop Audit

| Field | Value |
|---|---|
| source PNG | `{full_gm["sourcePng"]}` |
| source dimensions | `{md_value(full_gm.get("sourceDimensions"))}` |
| crop box | `{md_value(full_gm.get("cropBoxLTRB"))}` |
| crop dimensions | `{md_value(full_gm.get("cropDimensions"))}` |
| selected stroke bounds in crop | `{md_value(full_gm.get("selectedStrokeBoundsInCropLTRB"))}` |
| stroke margin | `{full_gm.get("strokeMarginPx")}` |
| cell step | `{full_gm.get("cellStepPx")}` |
| contaminated | `{full_gm.get("contaminated")}` |

Neighbor stroke overlap:

| Neighbor | Overlaps crop | Overlap LTRB |
|---|---|---|
{overlaps}

Pixel probes in crop margins:

| Region | Non-white pixels | Non-white bbox |
|---|---:|---|
{probes}

## Blocked Reasons

{blocked}

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `{data["preservedContracts"]["supportStatus"]}` |
| full-GM substitution accepted | `{data["preservedContracts"]["fullGmSubstitutionAccepted"]}` |
| full-GM score evidence accepted | `{data["preservedContracts"]["fullGmScoreEvidenceAccepted"]}` |
| CPU Kanvas output accepted as Skia | `{data["preservedContracts"]["cpuKanvasOutputAcceptedAsSkia"]}` |
| GPU route status | `{data["preservedContracts"]["gpuRouteStatus"]}` |
| GPU refusal reason | `{data["preservedContracts"]["gpuRefusalReason"]}` |
| readiness movement | `{data["preservedContracts"]["readinessMovement"]}` |
| release gate changed | `{data["preservedContracts"]["releaseGateChanged"]}` |

## Non-Goals And Non-Changes

{non_goals}

## Validation

- `rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    if data.get("decision") not in {
        DECISION_READY,
        DECISION_CONTAMINATED,
        DECISION_MISSING,
        DECISION_AMBIGUOUS,
    }:
        fail("artifact decision is not an allowed FOR-323 decision")
    if data.get("decision") == DECISION_READY:
        if data.get("skiaPngReady") is not True or not data.get("acceptedSkiaPng"):
            fail("ready decision must expose acceptedSkiaPng")
    else:
        if data.get("skiaPngReady") is not False or data.get("acceptedSkiaPng") is not None:
            fail("blocked decisions must keep skia.png not ready")

    selected = data.get("selectedCell")
    if not isinstance(selected, dict):
        fail("artifact missing selectedCell")
    validate_cell(selected, "artifact selectedCell")

    strategies = data.get("auditedStrategies")
    if not isinstance(strategies, dict):
        fail("artifact missing auditedStrategies")
    full_gm = strategies.get("fullGmCrop")
    if not isinstance(full_gm, dict):
        fail("artifact missing fullGmCrop strategy")
    if full_gm.get("accepted") is not False:
        fail("full-GM crop must not be accepted")
    if data.get("decision") == DECISION_CONTAMINATED and full_gm.get("contaminated") is not True:
        fail("contaminated decision requires contaminated=true")
    if full_gm.get("cropBoxLTRB") != FULL_GM_CROP_BOX:
        fail("full-GM crop box changed")
    if full_gm.get("selectedArcRectInCropLTRB") != BOUNDED_ARC_RECT:
        fail("full-GM crop selected rect changed")
    overlaps = full_gm.get("neighborStrokeOverlap")
    if not isinstance(overlaps, list) or not any(item.get("overlapsCrop") for item in overlaps):
        fail("full-GM crop must record neighboring stroke overlap")

    candidate = strategies.get("isolatedSelectedCellSkiaReference")
    if not isinstance(candidate, dict):
        fail("artifact missing isolatedSelectedCellSkiaReference strategy")
    if candidate.get("accepted") is not False and data.get("decision") != DECISION_READY:
        fail("candidate acceptance disagrees with blocked decision")

    for322_cpu = strategies.get("for322CpuOutput")
    if not isinstance(for322_cpu, dict) or for322_cpu.get("accepted") is not False:
        fail("FOR-322 CPU output must be rejected as Skia evidence")

    reference = data.get("referenceDecision")
    if not isinstance(reference, dict):
        fail("artifact missing referenceDecision")
    if reference.get("decision") != data.get("decision"):
        fail("referenceDecision disagrees with top-level decision")
    if data.get("decision") != DECISION_READY and not reference.get("blockedReasons"):
        fail("blocked decision must list blocked reasons")

    guard = data.get("preservedContracts")
    if not isinstance(guard, dict):
        fail("artifact missing preservedContracts")
    expected_guard = {
        "supportStatus": "not-supported",
        "fullGmSubstitutionAccepted": False,
        "fullGmScoreEvidenceAccepted": False,
        "cpuKanvasOutputAcceptedAsSkia": False,
        "gpuRouteStatus": "expected-unsupported",
        "gpuRefusalReason": GPU_REFUSAL,
        "readinessMovement": False,
        "releaseGateChanged": False,
    }
    for key, expected in expected_guard.items():
        if guard.get(key) != expected:
            fail(f"preservedContracts.{key} expected {expected!r}, got {guard.get(key)!r}")


def validate_report() -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        DECISION_CONTAMINATED,
        "skia.png` ready",
        "start=0",
        "sweep=90",
        "complement=-270",
        "useCenter=false",
        "aa=true",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        "alpha `100`",
        "two arcs",
        "FOR-322 `cpu.png`",
        "Kanvas CPU output",
        "neighboring stroke margins overlap",
        GPU_REFUSAL,
        "readiness movement",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")


def main() -> None:
    data = build_artifact()
    write_artifact(data)
    write_report(data)
    validate_artifact_shape(load_json(ARTIFACT))
    validate_report()
    print(f"FOR-323 validation passed: {data['decision']}")


if __name__ == "__main__":
    main()
