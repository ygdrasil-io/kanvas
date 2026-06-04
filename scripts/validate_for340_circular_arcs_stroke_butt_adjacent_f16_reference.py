#!/usr/bin/env python3
"""Validate the FOR-340 adjacent CircularArcsStrokeButt Skia references."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from PIL import Image


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-340"
SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-reference-for340"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-340-circular-arcs-stroke-butt-adjacent-f16-reference.md"
)
PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
SWEEP45_SKIA = ARTIFACT_DIR / "sweep45-skia.png"
SWEEP130_SKIA = ARTIFACT_DIR / "sweep130-skia.png"

SOURCE = PROJECT_ROOT / "tools/skia-reference/circular_arcs_stroke_butt_adjacent_f16_reference.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-isolated-upstream-skia-references-for-adjacent-circular-arcs-stroke-butt-f16-cells-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-339-circular-arcs-stroke-butt-adjacent-f16-runtime-trace-partial-finding"
)

FOR339_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339"
FOR339_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR339_SCENE_ID / f"{FOR339_SCENE_ID}.json"
)
FOR339_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_PARTIAL_REQUIRES_REFERENCE_SOURCE"
)

DECISION_CAPTURED = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_CAPTURED"
DECISION_PARTIAL = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_PARTIAL_REQUIRES_UPSTREAM_HARNESS"
DECISION_INPUT_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_CAPTURED, DECISION_PARTIAL, DECISION_INPUT_INVALID]

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
TARGET_OUTPUTS = {
    "adjacent_arc_stroke_start0_sweep45_target": SWEEP45_SKIA,
    "adjacent_arc_stroke_start0_sweep130_target": SWEEP130_SKIA,
}
EXPECTED_CELLS = {
    "adjacent_arc_stroke_start0_sweep45_target": {
        "fixtureId": "circular-arcs-stroke-butt-start0-sweep45-usecenter-false-aa-true",
        "sourceGm": "CircularArcsStrokeButtGM",
        "sourceRowId": "circular-arcs-stroke-butt-webgpu",
        "rowIndex": 0,
        "columnIndex": 1,
        "startDegrees": 0,
        "sweepDegrees": 45,
        "complementSweepDegrees": -315,
        "useCenter": False,
        "aa": True,
        "style": "kStroke_Style",
        "strokeWidth": 15,
        "strokeCap": "kButt_Cap",
        "paintAlpha": 100,
        "localCanvasArcRectLTRB": [20, 20, 60, 60],
        "drawArcCalls": [
            {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 45},
            {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -315},
        ],
    },
    "adjacent_arc_stroke_start0_sweep130_target": {
        "fixtureId": "circular-arcs-stroke-butt-start0-sweep130-usecenter-false-aa-true",
        "sourceGm": "CircularArcsStrokeButtGM",
        "sourceRowId": "circular-arcs-stroke-butt-webgpu",
        "rowIndex": 0,
        "columnIndex": 3,
        "startDegrees": 0,
        "sweepDegrees": 130,
        "complementSweepDegrees": -230,
        "useCenter": False,
        "aa": True,
        "style": "kStroke_Style",
        "strokeWidth": 15,
        "strokeCap": "kButt_Cap",
        "paintAlpha": 100,
        "localCanvasArcRectLTRB": [20, 20, 60, 60],
        "drawArcCalls": [
            {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 130},
            {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -230},
        ],
    },
}

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py",
    "rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py",
    "rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-340 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_size(path: Path) -> dict[str, int]:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        width, height = image.size
    return {"width": width, "height": height}


def pixel_rgba(path: Path, x: int, y: int) -> list[int]:
    with Image.open(path) as image:
        width, height = image.size
        require(0 <= x < width and 0 <= y < height, f"{rel(path)} sample out of bounds: {x},{y}")
        return list(image.convert("RGBA").getpixel((x, y)))


def over_white(rgba: list[int]) -> list[int]:
    r, g, b, a = rgba
    return [
        (r * a + 255 * (255 - a) + 127) // 255,
        (g * a + 255 * (255 - a) + 127) // 255,
        (b * a + 255 * (255 - a) + 127) // 255,
        255,
    ]


def delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(x - y) for x, y in zip(a, b)]


def validate_for339_gate(for339: dict[str, Any]) -> None:
    require(for339.get("linear") == "FOR-339", "FOR-339 artifact identity changed")
    require(for339.get("decision") == FOR339_REQUIRED_DECISION, "FOR-339 decision changed")
    status = for339.get("captureStatus")
    require(isinstance(status, dict), "FOR-339 captureStatus missing")
    require(status.get("runtimeCapturedCellCount") == 2, "FOR-339 runtime cell count changed")
    require(status.get("isolatedSkiaReferenceCellCount") == 0, "FOR-339 history must not be rewritten")
    implementation = for339.get("implementation")
    require(isinstance(implementation, dict), "FOR-339 implementation block missing")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "FOR-339 selected-cell flag changed")


def validate_source_and_runner() -> dict[str, Any]:
    if not SOURCE.is_file():
        fail(f"missing source: {rel(SOURCE)}")
    if not RUNNER.is_file():
        fail(f"missing runner: {rel(RUNNER)}")
    source_text = SOURCE.read_text(encoding="utf-8")
    runner_text = RUNNER.read_text(encoding="utf-8")
    for needle in [
        "SkImageInfo::MakeN32Premul(80, 80",
        "SkRect::MakeLTRB(20, 20, 60, 60)",
        "red.setStrokeCap(SkPaint::kButt_Cap)",
        "SkColorSetARGB(100, 255, 0, 0)",
        "SkColorSetARGB(100, 0, 0, 255)",
        "write_cell(sweep45Path, 45)",
        "write_cell(sweep130Path, 130)",
        "canvas->drawArc(arcRect, 0, sweepDegrees, false, red)",
        "canvas->drawArc(arcRect, 0, -(360 - sweepDegrees), false, blue)",
    ]:
        require(needle in source_text, f"source missing `{needle}`")
    for needle in [
        "circular_arcs_stroke_butt_adjacent_f16_reference.cpp",
        "libskia.a",
        "sweep45-skia.png",
        "sweep130-skia.png",
        "isolated-skia-adjacent-cell-render",
        "for327SelectedCellReferenceAcceptedAsAdjacent",
    ]:
        require(needle in runner_text, f"runner missing `{needle}`")
    forbidden = [
        "original-888/circular_arcs_stroke_butt.png",
        "CircularArcsStrokeButtSelectedCellCaptureTest",
        "cpu.png",
        "test-similarity-scores",
        "test-similarity-report",
    ]
    for term in forbidden:
        require(term not in source_text, f"source uses forbidden term `{term}`")
    require(not re.search(r"\bcrop\b", source_text, flags=re.IGNORECASE), "source must not use crop")
    return {
        "source": {"path": rel(SOURCE), "sha256": sha256(SOURCE), "present": True},
        "runner": {"path": rel(RUNNER), "present": True, "headless": True},
    }


def validate_provenance(provenance: dict[str, Any]) -> None:
    require(provenance.get("linear") == LINEAR_ID, "provenance linear id changed")
    require(provenance.get("sourceMemory") == SOURCE_MEMORY, "provenance source memory changed")
    require(provenance.get("sourceFindings") == [SOURCE_FINDING], "provenance source finding changed")
    require(provenance.get("sceneId") == SCENE_ID, "provenance scene id changed")
    require(provenance.get("sourceType") == "isolated-skia-adjacent-cell-render", "provenance sourceType changed")
    require(provenance.get("dimensions") == EXPECTED_DIMENSIONS, "provenance dimensions changed")
    require(provenance.get("fullGmCrop") is False, "provenance must reject full-GM crop")
    require(provenance.get("fullGmSubstitutionAccepted") is False, "provenance accepts full-GM substitution")
    require(provenance.get("cpuKanvasOutputAcceptedAsSkia") is False, "provenance accepts Kanvas CPU")
    require(provenance.get("selectedCellExtrapolationUsed") is False, "provenance uses selected-cell extrapolation")
    require(
        provenance.get("for327SelectedCellReferenceAcceptedAsAdjacent") is False,
        "FOR-327 selected-cell reference must not be accepted as adjacent evidence",
    )
    proof = provenance.get("sourceTypeProof")
    require(isinstance(proof, dict), "provenance sourceTypeProof missing")
    require(proof.get("compiledRepoOwnedSource") is True, "provenance compile proof missing")
    require(proof.get("executedBinary") is True, "provenance execution proof missing")
    require(proof.get("compiledSourcePath") == rel(SOURCE), "provenance source path changed")
    require(str(proof.get("linkedAgainstUpstreamSkiaLib", "")).endswith("libskia.a"), "libskia proof missing")
    cells = provenance.get("targetCells")
    require(isinstance(cells, list) and len(cells) == 2, "provenance target cells missing")
    for cell in cells:
        group_id = cell.get("groupId")
        require(group_id in EXPECTED_CELLS, f"unexpected provenance cell {group_id!r}")
        for key, expected in EXPECTED_CELLS[group_id].items():
            if key in ("localCanvasArcRectLTRB", "drawArcCalls"):
                continue
            require(cell.get(key) == expected, f"provenance {group_id}.{key} changed")
        path = PROJECT_ROOT / cell.get("outputPath", "")
        require(path == TARGET_OUTPUTS[group_id], f"provenance {group_id} output path changed")
        require(cell.get("outputSha256") == sha256(path), f"provenance {group_id} output sha changed")


def reference_status() -> tuple[bool, list[str]]:
    missing = [rel(path) for path in [SWEEP45_SKIA, SWEEP130_SKIA, PROVENANCE] if not path.is_file()]
    if missing:
        return False, [f"missing checked-in reference artifact: {path}" for path in missing]
    return True, []


def cell_samples(group: dict[str, Any], skia_path: Path) -> list[dict[str, Any]]:
    samples: list[dict[str, Any]] = []
    for sample in group["samples"]:
        current = sample["exportReadback"]["skBitmapGetPixelAsSrgbRgba"]
        raw = pixel_rgba(skia_path, sample["localX"], sample["localY"])
        white = over_white(raw)
        raw_delta = delta(current, raw)
        white_delta = delta(current, white)
        samples.append(
            {
                "name": sample["name"],
                "zone": sample["zone"],
                "expectedPaintColor": sample.get("expectedPaintColor"),
                "localX": sample["localX"],
                "localY": sample["localY"],
                "for339RootX": sample["rootX"],
                "for339RootY": sample["rootY"],
                "currentFor339ExportRgba": current,
                "skiaReferenceRawRgba": raw,
                "skiaReferenceOverWhiteRgba": white,
                "rawReferenceDeltaAbs": raw_delta,
                "rawReferenceSumAbsDelta": sum(raw_delta),
                "overWhiteReferenceDeltaAbs": white_delta,
                "overWhiteReferenceSumAbsDelta": sum(white_delta),
                "referenceResidualComputed": True,
            }
        )
    return samples


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    stroke = [sample for sample in samples if str(sample.get("zone", "")).startswith("stroke")]
    return {
        "sampleCount": len(samples),
        "strokeSampleCount": len(stroke),
        "rawReferenceSumAbsDelta": sum(sample["rawReferenceSumAbsDelta"] for sample in samples),
        "rawReferenceStrokeSumAbsDelta": sum(sample["rawReferenceSumAbsDelta"] for sample in stroke),
        "overWhiteReferenceSumAbsDelta": sum(sample["overWhiteReferenceSumAbsDelta"] for sample in samples),
        "overWhiteReferenceStrokeSumAbsDelta": sum(
            sample["overWhiteReferenceSumAbsDelta"] for sample in stroke
        ),
        "rawReferenceMaxSampleDelta": max(sample["rawReferenceSumAbsDelta"] for sample in samples),
        "overWhiteReferenceMaxSampleDelta": max(sample["overWhiteReferenceSumAbsDelta"] for sample in samples),
        "dataComparableForPolicyDecision": True,
    }


def build_cell(group: dict[str, Any], provenance: dict[str, Any]) -> dict[str, Any]:
    group_id = group["groupId"]
    skia_path = TARGET_OUTPUTS[group_id]
    size = image_size(skia_path)
    require(size == EXPECTED_DIMENSIONS, f"{rel(skia_path)} dimensions changed")
    expected = EXPECTED_CELLS[group_id]
    for key, value in expected.items():
        require(group["cell"].get(key) == value, f"FOR-339 {group_id}.{key} changed")
    samples = cell_samples(group, skia_path)
    reference_cell = next(cell for cell in provenance["targetCells"] if cell["groupId"] == group_id)
    return {
        "groupId": group_id,
        "runtimeTraceSource": rel(FOR339_ARTIFACT),
        "runtimeTraceCaptured": group.get("runtimeTraceCaptured"),
        "referenceSourceAvailable": True,
        "dataComparableForPolicyDecision": True,
        "cell": group["cell"],
        "reference": {
            "path": rel(skia_path),
            "provenancePath": rel(PROVENANCE),
            "present": True,
            "accepted": True,
            "status": "available-isolated-skia-adjacent-cell-render",
            "dimensions": size,
            "sha256": sha256(skia_path),
            "sourceType": reference_cell["sourceType"],
            "fullGmCrop": False,
            "selectedCellExtrapolationUsed": False,
            "for327SelectedCellReferenceAcceptedAsAdjacent": False,
            "transparentBackground": pixel_rgba(skia_path, 0, 0) == [0, 0, 0, 0],
        },
        "residualSummary": summarize(samples),
        "samples": samples,
    }


def build_artifact() -> dict[str, Any]:
    for339 = load_json(FOR339_ARTIFACT)
    validate_for339_gate(for339)
    source_runner = validate_source_and_runner()
    available, blocked = reference_status()
    provenance = load_json(PROVENANCE) if available else {}
    if available:
        validate_provenance(provenance)
    groups = for339.get("targetCells")
    require(isinstance(groups, list) and len(groups) == 2, "FOR-339 target cells changed")
    target_cells = [build_cell(group, provenance) for group in groups] if available else []
    decision = DECISION_CAPTURED if len(target_cells) == 2 else DECISION_PARTIAL
    total_raw = sum(cell["residualSummary"]["rawReferenceSumAbsDelta"] for cell in target_cells)
    total_white = sum(cell["residualSummary"]["overWhiteReferenceSumAbsDelta"] for cell in target_cells)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-340 captured two isolated upstream Skia adjacent-cell renders and computed "
            "sample residuals against the current FOR-339 export."
            if decision == DECISION_CAPTURED
            else "FOR-340 could not capture isolated upstream Skia adjacent-cell references."
        ),
        "inputValidation": {
            "valid": True,
            "for339Artifact": rel(FOR339_ARTIFACT),
            "for339Decision": for339.get("decision"),
            "for339RequiredDecision": FOR339_REQUIRED_DECISION,
            "for339HistoricalArtifactRewritten": False,
        },
        "captureStatus": {
            "targetCellCount": 2,
            "isolatedSkiaReferenceCellCount": len(target_cells),
            "referenceBoundaryAccessible": decision == DECISION_CAPTURED,
            "residualsComputed": decision == DECISION_CAPTURED,
            "capturedDecisionAllowed": decision == DECISION_CAPTURED,
            "blockedReasons": blocked,
        },
        **source_runner,
        "provenance": provenance if available else None,
        "targetCells": target_cells,
        "residualTotals": {
            "sampleCount": sum(cell["residualSummary"]["sampleCount"] for cell in target_cells),
            "strokeSampleCount": sum(cell["residualSummary"]["strokeSampleCount"] for cell in target_cells),
            "rawReferenceSumAbsDelta": total_raw,
            "overWhiteReferenceSumAbsDelta": total_white,
            "residualBasis": [
                "raw Skia PNG RGBA compared to current FOR-339 getPixelAsSrgb export RGBA",
                "Skia PNG alpha-composited over white compared to current FOR-339 getPixelAsSrgb export RGBA",
            ],
        },
        "rejectedSources": {
            "selectedCellFor327": {
                "path": "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png",
                "accepted": False,
                "reason": "FOR-327 is the selected 90-degree cell, not either adjacent FOR-339 cell",
            },
            "fullGmPng": {"accepted": False, "reason": "full-GM PNG is not an isolated adjacent-cell render"},
            "fullGmCrop": {"accepted": False, "reason": "crop-based adjacent reference is forbidden"},
            "for339RuntimeExport": {
                "acceptedAsSkiaReference": False,
                "reason": "FOR-339 is Kanvas runtime export data, not upstream Skia",
            },
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "selectedCellExtrapolationUsed": False,
        },
        "nonGoalsPreserved": {
            "colorToF16Premul": True,
            "blendF16PremulMode": True,
            "skBitmapGetPixelInternalOracle": True,
            "skBitmapGetPixelAsSrgbExportBoundary": True,
            "geometry": True,
            "coveragePolicy": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "historicalArtifactsFOR329ToFOR339Rewritten": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_CAPTURED, "FOR-340 expected captured references")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for339Decision") == FOR339_REQUIRED_DECISION, "FOR-339 gate not encoded")
    status = data.get("captureStatus")
    require(isinstance(status, dict), "captureStatus missing")
    require(status.get("targetCellCount") == 2, "target cell count changed")
    require(status.get("isolatedSkiaReferenceCellCount") == 2, "reference count changed")
    require(status.get("referenceBoundaryAccessible") is True, "reference boundary should be accessible")
    require(status.get("residualsComputed") is True, "residuals must be computed")
    cells = data.get("targetCells")
    require(isinstance(cells, list) and len(cells) == 2, "expected two target cells")
    for cell in cells:
        group_id = cell.get("groupId")
        require(group_id in EXPECTED_CELLS, f"unexpected cell {group_id!r}")
        reference = cell.get("reference")
        require(isinstance(reference, dict), f"{group_id} reference missing")
        require(reference.get("accepted") is True, f"{group_id} reference not accepted")
        require(reference.get("fullGmCrop") is False, f"{group_id} accepts full-GM crop")
        require(reference.get("selectedCellExtrapolationUsed") is False, f"{group_id} selected-cell extrapolation")
        summary = cell.get("residualSummary")
        require(isinstance(summary, dict), f"{group_id} residual summary missing")
        require(summary.get("sampleCount") == 6, f"{group_id} sample count changed")
        require(summary.get("strokeSampleCount") == 5, f"{group_id} stroke sample count changed")
        require(summary.get("dataComparableForPolicyDecision") is True, f"{group_id} not comparable")
        samples = cell.get("samples")
        require(isinstance(samples, list) and len(samples) == 6, f"{group_id} samples missing")
        for sample in samples:
            require(sample.get("referenceResidualComputed") is True, f"{group_id}.{sample.get('name')} no residual")
            require(len(sample.get("currentFor339ExportRgba", [])) == 4, "current export RGBA missing")
            require(len(sample.get("skiaReferenceRawRgba", [])) == 4, "raw Skia RGBA missing")
            require(len(sample.get("skiaReferenceOverWhiteRgba", [])) == 4, "over-white Skia RGBA missing")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "colorToF16Premul",
        "blendF16PremulMode",
        "skBitmapGetPixelInternalOracle",
        "skBitmapGetPixelAsSrgbExportBoundary",
        "geometry",
        "coveragePolicy",
        "gpu",
        "wgsl",
        "thresholds",
        "fallbacks",
        "kadre",
        "promotion",
        "score",
    ):
        require(non_goals.get(key) is True, f"non-goal guard changed: {key}")
    require(
        non_goals.get("historicalArtifactsFOR329ToFOR339Rewritten") is False,
        "historical artifact rewrite flag changed",
    )


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def build_report(data: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {group} | {column} | {sweep} | {samples} | {raw} | {white} | {path} |".format(
            group=cell["groupId"],
            column=cell["cell"]["columnIndex"],
            sweep=cell["cell"]["sweepDegrees"],
            samples=cell["residualSummary"]["sampleCount"],
            raw=cell["residualSummary"]["rawReferenceSumAbsDelta"],
            white=cell["residualSummary"]["overWhiteReferenceSumAbsDelta"],
            path=cell["reference"]["path"],
        )
        for cell in data["targetCells"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    provenance = data["provenance"]
    return f"""# FOR-340 CircularArcsStrokeButt Adjacent F16 Skia Reference

Linear: `FOR-340`

Decision: `{data["decision"]}`

FOR-340 captures isolated upstream Skia references for the two exact adjacent
`CircularArcsStrokeButt` cells measured by FOR-339. It does not change renderer
behavior, color conversion, geometry, coverage, GPU, WGSL, thresholds, fallback
policy, Kadre, promotion, or score.

## Result

The reference boundary is accessible for `{data["captureStatus"]["isolatedSkiaReferenceCellCount"]}` /
`{data["captureStatus"]["targetCellCount"]}` cells. Residuals are computed per
sample against the current FOR-339 `SkBitmap.getPixelAsSrgb` export values.

Because the upstream Skia PNGs are transparent N32 premul references while the
FOR-339 export samples are opaque output bytes, the artifact records both raw
Skia RGBA residuals and Skia-over-white residuals.

| group | column | sweep | samples | raw residual | over-white residual | reference |
|---|---:|---:|---:|---:|---:|---|
{rows}

Total raw residual: `{data["residualTotals"]["rawReferenceSumAbsDelta"]}`.
Total Skia-over-white residual: `{data["residualTotals"]["overWhiteReferenceSumAbsDelta"]}`.

## Provenance

| Field | Value |
|---|---|
| sourceType | `{provenance.get("sourceType")}` |
| sourceImplementation | `{provenance.get("sourceImplementation")}` |
| source sha256 | `{provenance.get("sourceSha256")}` |
| upstream Skia root | `{provenance.get("upstreamSkiaRoot")}` |
| upstream Skia revision | `{provenance.get("upstreamSkiaGitRevision")}` |
| command | `{provenance.get("command")}` |
| dimensions | `{md_value(provenance.get("dimensions"))}` |
| fullGmCrop | `{provenance.get("fullGmCrop")}` |
| selectedCellExtrapolationUsed | `{provenance.get("selectedCellExtrapolationUsed")}` |

## Rejected Substitutions

- FOR-327 selected-cell `skia.png` is not used as adjacent evidence.
- Full-GM PNGs and crops are not accepted.
- FOR-339 runtime export is used only as the current Kanvas comparison side,
  never as upstream Skia reference.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- PNG: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/sweep45-skia.png`
- PNG: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/sweep130-skia.png`
- Provenance: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/skia-reference-provenance.json`
- Source: `tools/skia-reference/circular_arcs_stroke_butt_adjacent_f16_reference.cpp`
- Runner: `tools/skia-reference/build_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- Validator: `scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-340-circular-arcs-stroke-butt-adjacent-f16-reference.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact()
    validate_artifact(data)
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-340 validation passed")


if __name__ == "__main__":
    main()
