#!/usr/bin/env python3
"""Validate the FOR-326 repo-owned Skia selected-cell renderer source."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-326"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-repo-owned-skia-selected-cell-renderer-source-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-skia-selected-cell-source-for326"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-326-circular-arcs-stroke-butt-skia-selected-cell-source.md"
)
SOURCE = PROJECT_ROOT / "tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp"
FOR326_SKIA = ARTIFACT_DIR / "skia.png"
FOR326_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"

FOR325_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325"
FOR325_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR325_SCENE_ID
    / f"{FOR325_SCENE_ID}.json"
)
FOR324_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324"
FOR324_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR324_SCENE_ID
    / f"{FOR324_SCENE_ID}.json"
)
FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"
FOR322_STATS = FOR322_DIR / "stats.json"

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SKIA_SELECTED_CELL_SOURCE_READY"
DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_SKIA_SELECTED_CELL_SOURCE_MISSING"
DECISION_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_SKIA_SELECTED_CELL_SOURCE_INVALID"
FOR325_DECISION_MISSING = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVISIONING_MISSING"
)
FOR324_DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_HARNESS_MISSING"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
GPU_REFUSAL = "coverage.stroke-cap-join-visual-parity-below-threshold"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
EXPECTED_RECT = [20, 20, 60, 60]
REMAINING_DEPENDENCY = (
    "remaining upstream Skia build/execution wiring is required before producing "
    "skia.png and skia-reference-provenance.json from the repo-owned source"
)
EXPECTED_SELECTED_CELL = {
    "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
    "sourceGm": "CircularArcsStrokeButtGM",
    "sourceRowId": "circular-arcs-stroke-butt-webgpu",
    "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
    "boundedHarnessGm": "circular-arcs-stroke-butt-selected-cell-harness-for322",
    "cellCount": 1,
    "quadrant": "bottom-left",
    "fullGmCanvasArcRectLTRB": [140, 520, 180, 560],
    "boundedCanvasArcRectLTRB": EXPECTED_RECT,
    "rowIndex": 0,
    "columnIndex": 2,
    "startDegrees": 0,
    "sweepDegrees": 90,
    "complementSweepDegrees": -270,
    "useCenter": False,
    "aa": True,
    "style": "kStroke_Style",
    "strokeWidth": 15,
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
FORBIDDEN_SOURCE_TERMS = [
    "crop",
    "original-888/circular_arcs_stroke_butt.png",
    "cpu.png",
    "TestUtils.runGmTest",
    "CircularArcsStrokeButtSelectedCellCaptureTest",
    "test-similarity-scores",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-326 validation failed: {message}")


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


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


def validate_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in EXPECTED_SELECTED_CELL.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def validate_previous_contracts() -> dict[str, Any]:
    for325 = load_json(FOR325_ARTIFACT)
    for324 = load_json(FOR324_ARTIFACT)
    for322 = load_json(FOR322_ARTIFACT)
    route_gpu = load_json(FOR322_ROUTE_GPU)
    stats = load_json(FOR322_STATS)

    if for325.get("linear") != "FOR-325" or for325.get("decision") != FOR325_DECISION_MISSING:
        fail("FOR-325 prerequisite decision changed")
    if for325.get("skiaPngReady") is not False:
        fail("FOR-325 must still keep skia.png not ready")
    if for324.get("linear") != "FOR-324" or for324.get("decision") != FOR324_DECISION_MISSING:
        fail("FOR-324 prerequisite decision changed")
    if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
        fail("FOR-322 selected-cell harness contract changed")

    selected = for322.get("selectedCell")
    if not isinstance(selected, dict):
        fail("FOR-322 selectedCell missing")
    validate_cell(selected, "FOR-322 selectedCell")

    if route_gpu.get("status") != "expected-unsupported":
        fail("FOR-322 WebGPU route must remain expected-unsupported")
    if route_gpu.get("refusalReason") != GPU_REFUSAL:
        fail("FOR-322 WebGPU refusal reason changed")
    if stats.get("fullGmReferenceAccepted") is not False:
        fail("FOR-322 stats accepts the full-GM reference")

    return {
        "for325Artifact": rel(FOR325_ARTIFACT),
        "for324Artifact": rel(FOR324_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
        "for322Stats": rel(FOR322_STATS),
    }


def require_regex(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, flags=re.MULTILINE | re.DOTALL):
        fail(f"source missing {label}")


def validate_source() -> dict[str, Any]:
    source = read_text(SOURCE)
    lowered = source.lower()
    for term in FORBIDDEN_SOURCE_TERMS:
        if term.lower() in lowered:
            fail(f"source mentions forbidden term `{term}`")

    required_needles = [
        "SkImageInfo::MakeN32Premul(80, 80",
        "SkRect::MakeLTRB(20, 20, 60, 60)",
        "red.setAntiAlias(true)",
        "red.setStyle(SkPaint::kStroke_Style)",
        "red.setStrokeWidth(15)",
        "red.setStrokeCap(SkPaint::kButt_Cap)",
        "SkColorSetARGB(100, 255, 0, 0)",
        "SkColorSetARGB(100, 0, 0, 255)",
        "skia.png",
    ]
    for needle in required_needles:
        if needle not in source:
            fail(f"source missing `{needle}`")

    require_regex(
        source,
        r"drawArc\s*\(\s*arcRect\s*,\s*0\s*,\s*90\s*,\s*false\s*,\s*red\s*\)",
        "red drawArc start 0 sweep 90 useCenter false",
    )
    require_regex(
        source,
        r"drawArc\s*\(\s*arcRect\s*,\s*0\s*,\s*-270\s*,\s*false\s*,\s*blue\s*\)",
        "blue drawArc start 0 sweep -270 useCenter false",
    )

    return {
        "path": rel(SOURCE),
        "present": True,
        "sourceReady": True,
        "dimensions": EXPECTED_DIMENSIONS,
        "boundedCanvasArcRectLTRB": EXPECTED_RECT,
        "outputPathDescribed": "skia.png",
        "compileOrExecutionAttempted": False,
        "drawArcCalls": EXPECTED_SELECTED_CELL["drawArcCalls"],
        "forbiddenSourceTermsAbsent": True,
    }


def validate_no_generated_reference() -> dict[str, Any]:
    if FOR326_SKIA.exists():
        fail(f"{rel(FOR326_SKIA)} must not be created by FOR-326")
    if FOR326_PROVENANCE.exists():
        fail(f"{rel(FOR326_PROVENANCE)} must not be created by FOR-326")
    return {
        "path": rel(FOR326_SKIA),
        "provenancePath": rel(FOR326_PROVENANCE),
        "present": False,
        "accepted": False,
        "skiaPngReady": False,
        "status": "blocked-build-execution-not-wired",
        "remainingDependency": REMAINING_DEPENDENCY,
    }


def build_artifact() -> dict[str, Any]:
    follows = validate_previous_contracts()
    source_audit = validate_source()
    output = validate_no_generated_reference()

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_READY,
        "allowedDecisions": [DECISION_READY, DECISION_MISSING, DECISION_INVALID],
        "sceneId": SCENE_ID,
        "sourceReady": True,
        "skiaPngReady": False,
        "acceptedSkiaPng": None,
        "skiaPngPresent": False,
        "remainingDependency": REMAINING_DEPENDENCY,
        "source": source_audit,
        "selectedCell": EXPECTED_SELECTED_CELL,
        "follows": follows,
        "candidateSkiaReference": output,
        "rejectedSources": {
            "fullGmPng": {
                "path": "skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png",
                "accepted": False,
                "reason": "full-GM PNG is not an isolated selected-cell render",
            },
            "fullGmCrop": {
                "accepted": False,
                "reason": "FOR-326 uses dedicated drawing source instead of slicing an existing image",
            },
            "for322CpuPng": {
                "path": "reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png",
                "accepted": False,
                "reason": "FOR-322 cpu.png is Kanvas CPU output, not upstream Skia",
            },
            "testSimilarityScores": {
                "accepted": False,
                "reason": "scores are not selected-cell reference pixels",
            },
        },
        "strictFutureExecutionRequirements": {
            "buildAgainstUpstreamSkiaRequired": True,
            "executeSourceRequired": True,
            "sourcePath": rel(SOURCE),
            "outputRequired": "skia.png",
            "provenanceRequired": "skia-reference-provenance.json",
            "dimensions": EXPECTED_DIMENSIONS,
            "sourceType": "isolated-skia-selected-cell-render",
            "fullGmSubstitutionAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
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
            "productionRendererChanged": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
        },
        "validation": [
            "rtk python3 scripts/validate_for326_circular_arcs_stroke_butt_skia_selected_cell_source.py",
            "rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py",
            "rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py",
            f"rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null",
            "rtk ./gradlew pipelineSceneDashboardGate",
            "rtk git diff --check origin/master...HEAD",
        ],
    }


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("decision") != DECISION_READY:
        fail("artifact decision must be the FOR-326 source-ready decision")
    if data.get("sourceReady") is not True:
        fail("artifact must set sourceReady=true")
    if data.get("skiaPngReady") is not False:
        fail("artifact must keep skiaPngReady=false")
    if data.get("acceptedSkiaPng") is not None:
        fail("artifact must not accept skia.png")
    if data.get("remainingDependency") != REMAINING_DEPENDENCY:
        fail("artifact must name the remaining upstream Skia build/execution dependency")

    source = data.get("source")
    if not isinstance(source, dict):
        fail("artifact missing source audit")
    if source.get("path") != rel(SOURCE) or source.get("sourceReady") is not True:
        fail("artifact source audit must reference the ready source")
    if source.get("dimensions") != EXPECTED_DIMENSIONS:
        fail("artifact source audit must use 80x80 dimensions")
    if source.get("boundedCanvasArcRectLTRB") != EXPECTED_RECT:
        fail("artifact source audit must use the bounded arc rect")

    selected = data.get("selectedCell")
    if not isinstance(selected, dict):
        fail("artifact missing selectedCell")
    validate_cell(selected, "artifact selectedCell")

    candidate = data.get("candidateSkiaReference")
    if not isinstance(candidate, dict):
        fail("artifact missing candidateSkiaReference")
    if candidate.get("present") is not False or candidate.get("skiaPngReady") is not False:
        fail("FOR-326 must not create skia.png")

    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    for key in [
        "fullGmSubstitutionAccepted",
        "fullGmScoreEvidenceAccepted",
        "cpuKanvasOutputAcceptedAsSkia",
        "readinessMovement",
        "releaseGateChanged",
        "productionRendererChanged",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")
    if preserved.get("gpuRouteStatus") != "expected-unsupported":
        fail("GPU route status must remain expected-unsupported")


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def write_report(data: dict[str, Any]) -> None:
    cell = data["selectedCell"]
    validations = "\n".join(f"- `{command}`" for command in data["validation"])
    report = f"""# FOR-326 CircularArcsStrokeButt Skia Selected Cell Source

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-326 adds a repo-owned C++ source file for a future upstream Skia build to
render only the FOR-319 `CircularArcsStrokeButtGM` selected cell. The source is
ready: `{data["sourceReady"]}`. `skia.png` ready: `{data["skiaPngReady"]}`.

No Skia build or execution is performed by this ticket, so no `skia.png` and no
`skia-reference-provenance.json` are created. The remaining dependency is:

`{data["remainingDependency"]}`

## Source Contract

| Field | Value |
|---|---|
| source path | `{data["source"]["path"]}` |
| output described | `{data["source"]["outputPathDescribed"]}` |
| dimensions | `{md_value(data["source"]["dimensions"])}` |
| bounded rect | `{md_value(data["source"]["boundedCanvasArcRectLTRB"])}` |
| compile or execution attempted | `{data["source"]["compileOrExecutionAttempted"]}` |
| forbidden source terms absent | `{data["source"]["forbiddenSourceTermsAbsent"]}` |

The source draws exactly two arcs in an 80x80 surface: red `0..90` and blue
`0..-270`, with `useCenter=false`, `aa=true`, `strokeWidth=15`,
`SkPaint::kStroke_Style`, `SkPaint::kButt_Cap`, and alpha `100`.

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `{cell["fixtureId"]}` |
| source GM | `{cell["sourceGm"]}` |
| source row | `{cell["sourceRowId"]}` |
| row / column | `{cell["rowIndex"]}` / `{cell["columnIndex"]}` |
| start | `{cell["startDegrees"]}` |
| sweep | `{cell["sweepDegrees"]}` |
| complement | `{cell["complementSweepDegrees"]}` |
| useCenter | `{cell["useCenter"]}` |
| aa | `{cell["aa"]}` |
| stroke width | `{cell["strokeWidth"]}` |
| stroke cap | `{cell["strokeCap"]}` |
| alpha | `{cell["paintAlpha"]}` |
| bounded rect | `{md_value(cell["boundedCanvasArcRectLTRB"])}` |
| drawArc calls | `{len(cell["drawArcCalls"])}` |

## Reference Output Status

| Field | Value |
|---|---|
| path | `{data["candidateSkiaReference"]["path"]}` |
| provenance path | `{data["candidateSkiaReference"]["provenancePath"]}` |
| present | `{data["candidateSkiaReference"]["present"]}` |
| accepted | `{data["candidateSkiaReference"]["accepted"]}` |
| status | `{data["candidateSkiaReference"]["status"]}` |

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG | `{data["rejectedSources"]["fullGmPng"]["accepted"]}` | {data["rejectedSources"]["fullGmPng"]["reason"]} |
| image slicing | `{data["rejectedSources"]["fullGmCrop"]["accepted"]}` | {data["rejectedSources"]["fullGmCrop"]["reason"]} |
| FOR-322 `cpu.png` | `{data["rejectedSources"]["for322CpuPng"]["accepted"]}` | {data["rejectedSources"]["for322CpuPng"]["reason"]} |
| similarity scores | `{data["rejectedSources"]["testSimilarityScores"]["accepted"]}` | {data["rejectedSources"]["testSimilarityScores"]["reason"]} |

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `{data["preservedContracts"]["supportStatus"]}` |
| full-GM substitution accepted | `{data["preservedContracts"]["fullGmSubstitutionAccepted"]}` |
| CPU Kanvas output accepted as Skia | `{data["preservedContracts"]["cpuKanvasOutputAcceptedAsSkia"]}` |
| GPU route status | `{data["preservedContracts"]["gpuRouteStatus"]}` |
| GPU refusal reason | `{data["preservedContracts"]["gpuRefusalReason"]}` |
| production renderer changed | `{data["preservedContracts"]["productionRendererChanged"]}` |
| WGSL changed | `{data["preservedContracts"]["wgslChanged"]}` |
| threshold changed | `{data["preservedContracts"]["thresholdChanged"]}` |
| fallback policy changed | `{data["preservedContracts"]["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{data["preservedContracts"]["kadreNativeDependencyAdded"]}` |

## Validation

{validations}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report() -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        DECISION_READY,
        "source is\nready: `True`",
        "`skia.png` ready: `False`",
        "tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp",
        "80x80",
        "[20, 20, 60, 60]",
        "red `0..90`",
        "blue\n`0..-270`",
        "useCenter=false",
        "strokeWidth=15",
        "SkPaint::kStroke_Style",
        "SkPaint::kButt_Cap",
        "alpha `100`",
        "remaining upstream Skia build/execution wiring",
        "not-supported",
        GPU_REFUSAL,
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    unsafe_patterns = [
        r"`skia\.png` ready:\s+`True`",
        r"production renderer changed\s+\|\s+`True`",
        r"WGSL changed\s+\|\s+`True`",
        r"threshold changed\s+\|\s+`True`",
        r"fallback policy changed\s+\|\s+`True`",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, flags=re.IGNORECASE):
            fail(f"report contains unsafe support language matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    validate_artifact_shape(load_json(ARTIFACT))
    write_report(data)
    validate_report()
    print(data["decision"])


if __name__ == "__main__":
    main()
