#!/usr/bin/env python3
"""Validate FOR-426 M60 F16 coverage input-stage diagnostic evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-coverage-input-stage-for426"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-426-m60-f16-aa-stencil-cover-coverage-input-stage.md"
FOR425_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-alpha-conversion-stage-for425"
    / "m60-f16-aa-stencil-cover-alpha-conversion-stage-for425.json"
)
FOR424_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-partial-coverage-alpha-for424"
    / "m60-f16-aa-stencil-cover-partial-coverage-alpha-for424.json"
)
FOR423_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-reference-source-coverage-for423"
    / "m60-f16-aa-stencil-cover-reference-source-coverage-for423.json"
)
FOR422_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-verified-source-comparison-for422"
    / "m60-f16-aa-stencil-cover-verified-source-comparison-for422.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

ALLOWED_CLASSIFICATIONS = {
    "path-coverage-already-96",
    "clip-reduces-160-to-96",
    "coverage-product-matches-160",
    "inside-outside-selection-mismatch",
    "coverage-input-stage-incomplete",
}
EXPECTED_POINTS = {
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-426 validation failed: {message}")


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


def byte_from_alpha(value: Any) -> int | None:
    if not isinstance(value, (float, int)):
        return None
    return max(0, min(255, round(float(value) * 255)))


def derived_classification(item: dict[str, Any]) -> str:
    expected = item.get("expectedCoverageByte")
    for425 = item.get("for425CoverageOrAaAlphaByte")
    raw = item.get("rawPathCoverageByte")
    clip = item.get("clipCoverageByte")
    final = item.get("finalCoverageByte")
    if item.get("subdrawRole") != "inside" or item.get("subdrawOrdinal") != 0:
        return "inside-outside-selection-mismatch"
    if expected != 160 or for425 != 96:
        return "coverage-input-stage-incomplete"
    if not all(isinstance(value, int) for value in (raw, clip, final)):
        return "coverage-input-stage-incomplete"
    if raw == 96 and final == 96:
        return "path-coverage-already-96"
    if raw == 160 and final == 96 and clip < 255:
        return "clip-reduces-160-to-96"
    if raw == 160 and final == 160:
        return "coverage-product-matches-160"
    return "coverage-input-stage-incomplete"


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverCoverageInputStage(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "validatorCommandPresent": "validate_for426_m60_f16_aa_stencil_cover_coverage_input_stage.py" in capture,
        "rawPathCoverageExported": "rawPathCoverage" in capture and "rawPathCoverage" in device,
        "clipCoverageExported": "clipCoverage" in capture and "clipCoverage" in device,
        "finalCoverageExported": "finalCoverage" in capture and "finalCoverage" in device,
        "subsampleCounterPresent": "m60_f16_covered_subsamples_4x4" in device,
        "noDedicatedRuntimeFlag": "CoverageInputStage.enabled" not in device,
        "noPipelineKeyMutation": "coverage-input-stage-for426" not in device,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for425 = load_json(FOR425_ARTIFACT)
    for424 = load_json(FOR424_ARTIFACT)
    for423 = load_json(FOR423_ARTIFACT)
    for422 = load_json(FOR422_ARTIFACT)

    require(ARTIFACT.stat().st_size < 180_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-426", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceDraftMemory"), "source draft memory missing")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-425-alpha-conversion-stage-before-shader-return", "FOR-425 finding link missing")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") != "coverage-input-stage-incomplete", "classification must localize the stage")
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    require(for425.get("classification") == "alpha-drop-before-shader-return", "FOR-425 prerequisite missing")
    require(for424.get("classification") == "partial-coverage-alpha-quantization-mismatch", "FOR-424 prerequisite missing")
    require(for423.get("classification") == "verified-coverage-diverges-from-reference", "FOR-423 prerequisite missing")
    require(for422.get("classification") == "verified-source-matches-scratch-and-final-mutation", "FOR-422 prerequisite missing")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "return path must be verified")
    require(summary.get("coverageInputInstrumentationPresent") is True, "coverage input instrumentation missing")
    require(summary.get("partialPixelCount") == 6, "partial pixel count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "coverage 160 count mismatch")
    require(summary.get("for425CoverageOrAaAlpha96Count") == 6, "FOR-425 coverageOrAaAlpha 96 count mismatch")
    require(summary.get("finalCoverage96Count") == 6, "final coverage 96 count mismatch")
    require(summary.get("localizedPixelCount") == 6, "all pixels must be localized")
    majority = summary.get("majorityStage")
    require(majority in ALLOWED_CLASSIFICATIONS, "majority stage not allowed")
    require(majority == data.get("classification"), "global classification must match majority stage")
    require(summary.get("majorityStageCount") == 6, "majority stage count mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "classificationCounts missing")
    require(set(counts.keys()) == ALLOWED_CLASSIFICATIONS, "classificationCounts keys mismatch")
    require(sum(int(value) for value in counts.values()) == 6, "classification counts must sum to six")
    require(counts.get("coverage-input-stage-incomplete") == 0, "incomplete count must be zero")
    require(counts.get(majority) == 6, "majority count must cover every partial pixel")

    partials = data.get("partialPixels")
    require(isinstance(partials, list) and len(partials) == 6, "partialPixels size mismatch")
    require({(item.get("x"), item.get("y")) for item in partials} == EXPECTED_POINTS, "partial point set mismatch")
    for item in partials:
        require(item.get("drawIndex") == 1, "drawIndex mismatch")
        require(item.get("subdrawOrdinal") == 0, "subdraw ordinal mismatch")
        require(item.get("subdrawRole") == "inside", "subdraw role mismatch")
        require(item.get("entryPoint") == "fs_inside", "entry point mismatch")
        require(isinstance(item.get("edgeCount"), int) and item["edgeCount"] > 0, "edgeCount missing")
        require(
            item.get("fillType") in {"kWinding", "kEvenOdd", "kInverseWinding", "kInverseEvenOdd"},
            "fillType mismatch",
        )
        require(item.get("expectedCoverageByte") == 160, "expected coverage byte mismatch")
        require(item.get("for425CoverageOrAaAlphaByte") == 96, "FOR-425 coverage byte mismatch")
        require(item.get("classification") == majority, "local classification must match majority")
        require(derived_classification(item) == item.get("classification"), "local classification contradicts stage bytes")

        raw = item.get("rawPathCoverage")
        clip = item.get("clipCoverage")
        final = item.get("finalCoverage")
        require(isinstance(raw, (float, int)), "rawPathCoverage missing")
        require(isinstance(clip, (float, int)), "clipCoverage missing")
        require(isinstance(final, (float, int)), "finalCoverage missing")
        require(byte_from_alpha(raw) == item.get("rawPathCoverageByte"), "rawPathCoverageByte mismatch")
        require(byte_from_alpha(clip) == item.get("clipCoverageByte"), "clipCoverageByte mismatch")
        require(byte_from_alpha(final) == item.get("finalCoverageByte"), "finalCoverageByte mismatch")
        require(item.get("finalCoverageByte") == item.get("for425CoverageOrAaAlphaByte"), "final coverage must match FOR-425 coverageOrAaAlpha")
        covered = item.get("coveredSubsamples4x4")
        require(isinstance(covered, int) and 0 <= covered <= 16, "coveredSubsamples4x4 mismatch")
        if item.get("rawPathCoverageByte") == 96:
            require(covered == 6, "96/255 raw coverage should correspond to six covered 4x4 subsamples")
            require(math.isclose(float(raw), 6 / 16, rel_tol=0.0, abs_tol=0.001), "raw coverage alpha mismatch")
        deltas = item.get("stageDeltas")
        require(isinstance(deltas, dict), "stageDeltas missing")
        require(deltas.get("finalMinusFor425CoverageOrAaAlphaByte") == 0, "final/FOR-425 delta mismatch")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "fallbackChanged",
        "pipelineKeyChanged",
        "runtimeDeviceChangedOutsideOptInInstrumentation",
        "renderingFixApplied",
        "wgsl4kModified",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-426",
        "rawPathCoverage",
        "clipCoverage",
        "finalCoverage",
        "96/255",
        str(majority),
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-426 validation passed: {majority}")


if __name__ == "__main__":
    main()
