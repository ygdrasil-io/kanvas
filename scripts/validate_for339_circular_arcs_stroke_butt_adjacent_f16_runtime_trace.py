#!/usr/bin/env python3
"""Validate the FOR-339 adjacent CircularArcsStrokeButt F16 runtime trace."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-339"
SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-339-circular-arcs-stroke-butt-adjacent-f16-runtime-trace.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-adjacent-circular-arcs-stroke-butt-f16-runtime-trace-instrumentation-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-338-circular-arcs-stroke-butt-f16-comparable-samples-partial-finding"
)

FOR338_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338"
FOR338_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR338_SCENE_ID
    / f"{FOR338_SCENE_ID}.json"
)
FOR338_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_PARTIAL_REQUIRES_MORE_INSTRUMENTATION"
)

DECISION_CAPTURED = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_CAPTURED"
DECISION_PARTIAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_PARTIAL_REQUIRES_REFERENCE_SOURCE"
)
DECISION_INPUT_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_CAPTURED, DECISION_PARTIAL, DECISION_INPUT_INVALID]

TRACE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    / "CircularArcsStrokeButtAdjacentF16RuntimeTraceTest.kt"
)
GPU_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"
SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

COMMAND = (
    "rtk ./gradlew --no-daemon -Dkanvas.for339.runtimeTrace.write=true "
    ":gpu-raster:test --tests "
    "org.skia.gpu.webgpu.CircularArcsStrokeButtAdjacentF16RuntimeTraceTest"
)

EXPECTED_CELLS = {
    "adjacent_arc_stroke_start0_sweep45_target": {
        "rowIndex": 0,
        "columnIndex": 1,
        "startDegrees": 0,
        "sweepDegrees": 45,
        "strokeCap": "kButt_Cap",
        "aa": True,
        "paintAlpha": 100,
    },
    "adjacent_arc_stroke_start0_sweep130_target": {
        "rowIndex": 0,
        "columnIndex": 3,
        "startDegrees": 0,
        "sweepDegrees": 130,
        "strokeCap": "kButt_Cap",
        "aa": True,
        "paintAlpha": 100,
    },
}

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py",
    "rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py",
    "rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py",
    COMMAND,
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-339 validation failed: {message}")


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


def validate_for338_gate() -> None:
    for338 = load_json(FOR338_ARTIFACT)
    require(for338.get("linear") == "FOR-338", "FOR-338 artifact identity changed")
    require(for338.get("decision") == FOR338_REQUIRED_DECISION, "FOR-338 decision changed")
    summary = for338.get("summary")
    require(isinstance(summary, dict), "FOR-338 summary missing")
    require(summary.get("nextTicketMustUseMemoryFirst") is True, "FOR-338 memory-first signal missing")


def validate_sources() -> None:
    required = {
        TRACE_TEST: [
            "kanvas.for339.runtimeTrace.write",
            "CircularArcsStrokeButtAdjacentF16RuntimeTraceTest",
            "SkCpuWriteChronologyTrace.configureForTargets",
            "includeBitmapDirectWrites = true",
            "SkPngEncoder.Encode(bitmap)",
            "getPixelAsSrgb",
            "selectedCellExtrapolationUsed",
            "straight_srgb_quantized_alpha_src_over_white",
            COMMAND,
        ],
        GPU_BUILD: [
            'System.getProperty("kanvas.for339.runtimeTrace.write")',
            'systemProperty("kanvas.for339.runtimeTrace.write", it)',
        ],
        SK_BITMAP_DEVICE: [
            "private fun colorToF16Premul(c: SkColor4f, out: FloatArray)",
            "private fun blendF16PremulMode(",
            "recordF16PremulStore",
            "srcPremulBeforeCoverageF16",
            "dstPremulAfterStoreF16",
        ],
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "[getPixel] preserves the historical internal byte oracle",
        ],
        SK_PNG_ENCODER: [
            "SkBitmap.getPixelAsSrgb",
            "src.getPixelAsSrgb(x, y)",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_sample(group_id: str, sample: dict[str, Any]) -> tuple[bool, bool]:
    for key in (
        "name",
        "zone",
        "localX",
        "localY",
        "rootX",
        "rootY",
        "runtimeValuesCaptured",
        "selectedCellExtrapolationUsed",
        "paintColorXformAndPremul",
        "strokeCoverage",
        "srcOverF16Store",
        "f16Readback",
        "exportReadback",
        "pngEncode",
        "candidateStraightSrgb",
        "boundaryComparison",
    ):
        require(key in sample, f"{group_id}.{sample.get('name', '<sample>')} missing {key}")
    require(sample["selectedCellExtrapolationUsed"] is False, f"{sample['name']} uses selected-cell extrapolation")
    require(sample["f16Readback"].get("captured") is True, f"{sample['name']} missing F16 readback")
    require(sample["exportReadback"].get("captured") is True, f"{sample['name']} missing export readback")
    require(sample["pngEncode"].get("captured") is True, f"{sample['name']} missing PNG boundary")
    require(
        sample["pngEncode"].get("rgbaRowBytes") == sample["exportReadback"].get("skBitmapGetPixelAsSrgbRgba"),
        f"{sample['name']} PNG row does not match getPixelAsSrgb",
    )
    comparison = sample["boundaryComparison"]
    require(comparison.get("referenceSourceAvailable") is False, f"{sample['name']} must not claim reference")
    require(comparison.get("referenceResidualComputed") is False, f"{sample['name']} must not compute residual")

    is_stroke = str(sample.get("zone", "")).startswith("stroke")
    store = sample["srcOverF16Store"]
    if is_stroke and store.get("captured") is True:
        for key in ("paintColorXformAndPremul", "strokeCoverage", "candidateStraightSrgb"):
            block = sample[key]
            require(block.get("captured") is True, f"{sample['name']} missing captured {key}")
        require(store.get("srcPremulAfterCoverageF16"), f"{sample['name']} missing srcPremulAfterCoverageF16")
        require(store.get("dstPremulBeforeStoreF16"), f"{sample['name']} missing dstPremulBeforeStoreF16")
        require(store.get("dstPremulAfterStoreF16"), f"{sample['name']} missing dstPremulAfterStoreF16")
        require(sample["strokeCoverage"].get("coverageSamples") is not None, f"{sample['name']} missing coverage")
    return is_stroke, store.get("captured") is True


def validate_group(group: dict[str, Any]) -> None:
    group_id = group.get("groupId")
    require(group_id in EXPECTED_CELLS, f"unexpected target cell {group_id!r}")
    require(group.get("runtimeTraceCaptured") is True, f"{group_id} runtime trace not captured")
    require(group.get("referenceSourceAvailable") is False, f"{group_id} must not claim reference availability")
    require(group.get("dataComparableForPolicyDecision") is False, f"{group_id} must remain non-comparable")
    require(group.get("implementationAllowedNow") is False, f"{group_id} must not allow implementation")

    cell = group.get("cell")
    require(isinstance(cell, dict), f"{group_id}.cell missing")
    for key, expected in EXPECTED_CELLS[group_id].items():
        require(cell.get(key) == expected, f"{group_id}.cell.{key} expected {expected!r}, got {cell.get(key)!r}")

    samples = group.get("samples")
    require(isinstance(samples, list) and len(samples) >= 6, f"{group_id} needs at least six samples")
    stroke_count = 0
    captured_stroke_count = 0
    for sample in samples:
        require(isinstance(sample, dict), f"{group_id} sample must be object")
        is_stroke, captured = validate_sample(group_id, sample)
        stroke_count += int(is_stroke)
        captured_stroke_count += int(captured)
    require(stroke_count >= 5, f"{group_id} should target at least five stroke samples")
    require(captured_stroke_count >= 4, f"{group_id} captured too few stroke samples")

    summary = group.get("runtimeSummary")
    require(isinstance(summary, dict), f"{group_id}.runtimeSummary missing")
    require(summary.get("strokeSampleCount") == stroke_count, f"{group_id} stroke summary mismatch")
    require(
        summary.get("capturedStrokeSampleCount") == captured_stroke_count,
        f"{group_id} captured stroke summary mismatch",
    )


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "sceneId changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "sourceMemory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") in ALLOWED_DECISIONS, "unexpected decision")
    require(data.get("decision") == DECISION_PARTIAL, "FOR-339 must remain partial until references exist")

    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("valid") is True, "inputValidation must be valid")
    require(
        input_validation.get("requiresFor338Decision") == FOR338_REQUIRED_DECISION,
        "FOR-338 gate not encoded",
    )

    opt_in = data.get("optIn")
    require(isinstance(opt_in, dict), "optIn missing")
    require(opt_in.get("enabledBySystemProperty") == "kanvas.for339.runtimeTrace.write", "opt-in property changed")
    require(opt_in.get("defaultActive") is False, "trace must be inactive by default")
    require(opt_in.get("command") == COMMAND, "opt-in command changed")

    status = data.get("captureStatus")
    require(isinstance(status, dict), "captureStatus missing")
    require(status.get("adjacentTargetCellCount") == 2, "adjacent target count changed")
    require(status.get("runtimeCapturedCellCount") == 2, "runtime capture count changed")
    require(status.get("isolatedSkiaReferenceCellCount") == 0, "reference count must remain zero")
    require(status.get("referenceBoundaryAccessible") is False, "reference boundary must remain inaccessible")
    require(status.get("capturedDecisionAllowed") is False, "captured decision must not be allowed")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "renderer behavior must not change")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "selected-cell extrapolation forbidden")

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
        non_goals.get("historicalArtifactsFOR329ToFOR338Rewritten") is False,
        "historical artifact rewrite flag changed",
    )

    summary = data.get("runtimeCaptureSummary")
    require(isinstance(summary, dict), "runtimeCaptureSummary missing")
    require(summary.get("targetSampleCount") == 12, "target sample count changed")
    require(summary.get("f16StoreEventCount", 0) >= 8, "too few F16 store events")
    require(
        "isolated-upstream-skia-reference-source" in summary.get("boundariesAbsent", []),
        "missing reference boundary not recorded",
    )

    groups = data.get("targetCells")
    require(isinstance(groups, list) and len(groups) == 2, "expected exactly two target cells")
    for group in groups:
        validate_group(group)

    events = data.get("traceEvents")
    require(isinstance(events, list) and events, "trace events missing")
    require(
        any(event.get("srcPremulAfterCoverageF16") for event in events if isinstance(event, dict)),
        "trace events do not include F16 stores",
    )


def build_report(artifact: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {group} | {column} | {sweep} | {samples} | {captured} | {reference} |".format(
            group=group["groupId"],
            column=group["cell"]["columnIndex"],
            sweep=group["cell"]["sweepDegrees"],
            samples=group["runtimeSummary"]["sampleCount"],
            captured=group["runtimeSummary"]["capturedStrokeSampleCount"],
            reference="yes" if group["referenceSourceAvailable"] else "no",
        )
        for group in artifact["targetCells"]
    )
    validation = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    status = artifact["captureStatus"]
    summary = artifact["runtimeCaptureSummary"]
    return f"""# FOR-339 CircularArcsStrokeButt Adjacent F16 Runtime Trace

Linear: `FOR-339`

Decision: `{artifact["decision"]}`

FOR-339 captures real Kanvas Kotlin CPU F16 runtime samples for the two
requested adjacent `CircularArcsStrokeButt` cells. It does not change renderer
behavior, geometry, coverage policy, GPU, WGSL, thresholds, fallback policy,
Kadre, promotion, or score.

## Result

The runtime side is captured for `{status["runtimeCapturedCellCount"]}` /
`{status["adjacentTargetCellCount"]}` adjacent target cells. The decision remains
partial because `{status["isolatedSkiaReferenceCellCount"]}` isolated upstream
Skia reference cells are available in checked-in evidence.

The inaccessible boundary is:

- `isolated-upstream-skia-reference-source`

No selected-cell values are reused as measured adjacent-cell proof.

## Target Cells

| group | column | sweep | samples | captured stroke samples | isolated reference |
|---|---:|---:|---:|---:|---|
{rows}

## Runtime Capture

- Target samples: `{summary["targetSampleCount"]}`
- Trace events: `{summary["traceEventCount"]}`
- F16 store events: `{summary["f16StoreEventCount"]}`
- PNG byte size: `{summary["pngByteSize"]}`

Each captured stroke sample includes paint source, transformed/premul color,
coverage, F16 SrcOver store data, `SkBitmap.getPixel` readback,
`SkBitmap.getPixelAsSrgb` export readback, PNG-row-equivalent bytes, and the
straight-sRGB quantized-alpha candidate value when coverage is present.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- Historical artifacts FOR-329 through FOR-338 are not rewritten.

## Remaining Risk

The runtime data is now real for both adjacent cells, but residual computation
and any renderer color-policy decision remain blocked until isolated upstream
Skia references exist for these exact cells.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-339-circular-arcs-stroke-butt-adjacent-f16-runtime-trace.md`

## Validation

{validation}
"""


def main() -> None:
    validate_for338_gate()
    validate_sources()
    artifact = load_json(ARTIFACT)
    validate_artifact(artifact)
    write_if_changed(REPORT, build_report(artifact))
    print("FOR-339 validation passed")


if __name__ == "__main__":
    main()
