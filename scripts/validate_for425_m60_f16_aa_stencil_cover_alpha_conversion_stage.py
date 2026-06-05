#!/usr/bin/env python3
"""Validate FOR-425 M60 F16 alpha conversion-stage diagnostic evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-alpha-conversion-stage-for425"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-425-m60-f16-aa-stencil-cover-alpha-conversion-stage.md"
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
    "alpha-drop-before-shader-return",
    "alpha-drop-at-source-alpha-application",
    "alpha-drop-at-quantization",
    "alpha-drop-after-source-field",
    "alpha-conversion-stage-incomplete",
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
    raise SystemExit(f"FOR-425 validation failed: {message}")


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


def require_rgba_float(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def require_stage(value: Any, field: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{field} missing")
    require(isinstance(value.get("available"), bool), f"{field}.available missing")
    if value["available"]:
        require(isinstance(value.get("alpha"), (float, int)), f"{field}.alpha missing")
        require(isinstance(value.get("alphaByte"), int), f"{field}.alphaByte missing")
        require(isinstance(value.get("deltaFromExpectedByte"), int), f"{field}.deltaFromExpectedByte missing")
        require(isinstance(value.get("deltaFromObservedByte"), int), f"{field}.deltaFromObservedByte missing")
    return value


def derived_classification(stages: dict[str, Any], expected_byte: int, observed_byte: int) -> str:
    coverage_byte = stages["coverageOrAaAlpha"].get("alphaByte")
    source_after_byte = stages["sourceAlphaAfterCoverage"].get("alphaByte")
    before_quant = stages["sourceColorBeforeQuantization"].get("alphaByte")
    quantized_byte = stages["quantizedAlphaSentToBlend"].get("alphaByte")
    sent_byte = stages["sourceColorSentToBlend"].get("alphaByte")
    if coverage_byte == observed_byte:
        return "alpha-drop-before-shader-return"
    if coverage_byte == expected_byte and source_after_byte == observed_byte:
        return "alpha-drop-at-source-alpha-application"
    if source_after_byte == expected_byte and before_quant == expected_byte and quantized_byte == observed_byte:
        return "alpha-drop-at-quantization"
    if (quantized_byte == expected_byte or before_quant == expected_byte) and sent_byte == observed_byte:
        return "alpha-drop-after-source-field"
    return "alpha-conversion-stage-incomplete"


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverAlphaConversionStage(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "classificationPresent": "alpha-drop-at-source-alpha-application" in capture,
        "noDeviceRuntimeChangeFor425": "FOR-425" not in device and SCENE_ID not in device,
        "noFor425RuntimeFlag": "m60F16AaStencilCoverAlphaConversionStage" not in device,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for424 = load_json(FOR424_ARTIFACT)
    for423 = load_json(FOR423_ARTIFACT)
    for422 = load_json(FOR422_ARTIFACT)

    require(ARTIFACT.stat().st_size < 160_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-425", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") != "alpha-conversion-stage-incomplete", "classification must localize the stage")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    require(for424.get("classification") == "partial-coverage-alpha-quantization-mismatch", "FOR-424 prerequisite missing")
    require(for423.get("classification") == "verified-coverage-diverges-from-reference", "FOR-423 prerequisite missing")
    require(for422.get("classification") == "verified-source-matches-scratch-and-final-mutation", "FOR-422 prerequisite missing")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "return path must be verified")
    require(summary.get("partialPixelCount") == 6, "partial pixel count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "coverage 160 count mismatch")
    require(summary.get("observedSourceAlpha96Count") == 6, "observed source alpha 96 count mismatch")
    require(summary.get("localizedPixelCount") == 6, "all pixels must be localized")
    majority = summary.get("majorityStage")
    require(majority in ALLOWED_CLASSIFICATIONS, "majority stage not allowed")
    require(majority == data.get("classification"), "global classification must match majority stage")
    require(summary.get("majorityStageCount") == 6, "majority stage count mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "classificationCounts missing")
    require(set(counts.keys()) == ALLOWED_CLASSIFICATIONS, "classificationCounts keys mismatch")
    require(sum(int(value) for value in counts.values()) == 6, "classification counts must sum to six")
    require(counts.get("alpha-conversion-stage-incomplete") == 0, "incomplete count must be zero")
    require(counts.get(majority) == 6, "majority count must cover every partial pixel")

    partials = data.get("partialPixels")
    require(isinstance(partials, list) and len(partials) == 6, "partialPixels size mismatch")
    require({(item.get("x"), item.get("y")) for item in partials} == EXPECTED_POINTS, "partial point set mismatch")
    for item in partials:
        require(item.get("drawIndex") == 1, "drawIndex mismatch")
        require(item.get("subdrawOrdinal") == 0, "subdraw ordinal mismatch")
        require(item.get("subdrawRole") == "inside", "subdraw role mismatch")
        classification = item.get("classification")
        require(classification in ALLOWED_CLASSIFICATIONS, "local classification not allowed")
        require(classification == majority, "local classification must match global majority for current FOR-425 evidence")

        reference = item.get("referenceCoverage")
        require(isinstance(reference, dict), "referenceCoverage missing")
        require(reference.get("strokeBand") == "round-round", "stroke band mismatch")
        require(reference.get("cap") == "round", "cap mismatch")
        require(reference.get("join") == "round", "join mismatch")
        require(reference.get("coverageExpectedByte") == 160, "expected coverage byte mismatch")
        require(
            math.isclose(float(reference.get("coverageExpectedAlpha")), 160 / 255, rel_tol=0.0, abs_tol=0.001),
            "expected coverage alpha mismatch",
        )

        observed = item.get("observedSource")
        require(isinstance(observed, dict), "observedSource missing")
        require(observed.get("available") is True, "observed source must be available")
        require(observed.get("shaderObserved") is True, "shader observation missing")
        require(observed.get("captureSynthetic") is False, "source must not be synthetic")
        rgba = require_rgba_float(observed.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        require(observed.get("sourceAlphaByte") == 96, "observed alpha byte mismatch")
        require(math.isclose(rgba[3], 96 / 255, rel_tol=0.0, abs_tol=0.001), "source RGBA alpha mismatch")

        stages = item.get("stages")
        require(isinstance(stages, dict), "stages missing")
        coverage = require_stage(stages.get("coverageOrAaAlpha"), "coverageOrAaAlpha")
        source_after = require_stage(stages.get("sourceAlphaAfterCoverage"), "sourceAlphaAfterCoverage")
        quantized = require_stage(stages.get("quantizedAlphaSentToBlend"), "quantizedAlphaSentToBlend")
        require(coverage.get("available") is True, "coverageOrAaAlpha must be available")
        require(source_after.get("available") is True, "sourceAlphaAfterCoverage must be available")
        require(quantized.get("available") is True, "quantizedAlphaSentToBlend must be available")
        before_quant = stages.get("sourceColorBeforeQuantization")
        require(isinstance(before_quant, dict), "sourceColorBeforeQuantization missing")
        require_rgba_float(before_quant.get("rgbaFloat"), "sourceColorBeforeQuantization.rgbaFloat")
        require(isinstance(before_quant.get("alphaByte"), int), "sourceColorBeforeQuantization.alphaByte missing")
        sent = stages.get("sourceColorSentToBlend")
        require(isinstance(sent, dict), "sourceColorSentToBlend stage missing")
        require_rgba_float(sent.get("rgbaFloat"), "sourceColorSentToBlend.rgbaFloat")
        require(sent.get("alphaByte") == 96, "sourceColorSentToBlend stage alpha mismatch")
        require(derived_classification(stages, 160, 96) == classification, "local classification contradicts stages")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "fallbackChanged",
        "renderingFixApplied",
        "wgsl4kModified",
        "fullWgslDumpStored",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-425",
        "160/255",
        "96/255",
        str(majority),
        "6 pixels partiels",
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-425 validation passed: {majority}")


if __name__ == "__main__":
    main()
