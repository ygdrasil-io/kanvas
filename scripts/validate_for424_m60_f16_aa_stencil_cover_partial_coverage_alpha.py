#!/usr/bin/env python3
"""Validate FOR-424 M60 F16 partial coverage alpha diagnostic evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-partial-coverage-alpha-for424"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-424-m60-f16-aa-stencil-cover-partial-coverage-alpha.md"
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
    "partial-coverage-alpha-quantization-mismatch",
    "partial-coverage-subdraw-or-band-mismatch",
    "partial-coverage-stencil-sample-mismatch",
    "partial-coverage-diagnostic-incomplete",
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
    raise SystemExit(f"FOR-424 validation failed: {message}")


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


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverPartialCoverageAlpha(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "classificationPresent": "partial-coverage-alpha-quantization-mismatch" in capture,
        "neighborhoodPresent": "coverageNeighborhood3x3" in capture,
        "noDeviceRuntimeChangeFor424": "FOR-424" not in device and SCENE_ID not in device,
        "noFor424RuntimeFlag": "m60F16AaStencilCoverPartialCoverageAlpha" not in device,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for423 = load_json(FOR423_ARTIFACT)
    for422 = load_json(FOR422_ARTIFACT)

    require(ARTIFACT.stat().st_size < 120_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-424", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "partial-coverage-alpha-quantization-mismatch",
        "unexpected current classification",
    )
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    require(for423.get("classification") == "verified-coverage-diverges-from-reference", "FOR-423 prerequisite missing")
    for423_summary = for423.get("structuralSummary")
    require(isinstance(for423_summary, dict), "FOR-423 structural summary missing")
    require(for423_summary.get("coverageDivergenceCount") == 6, "FOR-423 coverage divergence count changed")
    require(for422.get("classification") == "verified-source-matches-scratch-and-final-mutation", "FOR-422 prerequisite missing")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "return path must be verified")
    require(summary.get("partialPixelCount") == 6, "partial pixel count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("sourceRecordCount") == 6, "source record count mismatch")
    require(summary.get("insideSubdrawCount") == 6, "inside subdraw count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "coverage 160 count mismatch")
    require(summary.get("sourceAlpha96Count") == 6, "source alpha 96 count mismatch")
    require(summary.get("constantRatioCount") == 6, "constant ratio count mismatch")
    require(summary.get("coverageNeighborhoodRecordCount") == 6, "neighborhood count mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "classificationCounts missing")
    require(counts.get("partial-coverage-alpha-quantization-mismatch") == 6, "quantization class count mismatch")
    require(counts.get("partial-coverage-subdraw-or-band-mismatch") == 0, "subdraw mismatch count must be zero")
    require(counts.get("partial-coverage-stencil-sample-mismatch") == 0, "stencil/sample mismatch count must be zero")
    require(counts.get("partial-coverage-diagnostic-incomplete") == 0, "incomplete count must be zero")

    partials = data.get("partialPixels")
    require(isinstance(partials, list) and len(partials) == 6, "partialPixels size mismatch")
    require({(item.get("x"), item.get("y")) for item in partials} == EXPECTED_POINTS, "partial point set mismatch")
    for item in partials:
        require(item.get("drawIndex") == 1, "partial pixel drawIndex mismatch")
        require(item.get("classification") == "partial-coverage-alpha-quantization-mismatch", "local classification mismatch")
        source = item.get("source")
        require(isinstance(source, dict), "source block missing")
        require(source.get("available") is True, "source must be available")
        require(source.get("subdrawOrdinal") == 0, "subdraw ordinal mismatch")
        require(source.get("subdrawRole") == "inside", "subdraw role mismatch")
        require(source.get("shaderObserved") is True, "shader observation missing")
        require(source.get("captureSynthetic") is False, "source must not be synthetic")
        rgba = require_rgba_float(source.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        require(source.get("sourceAlphaByte") == 96, "source alpha byte mismatch")
        require(math.isclose(float(source.get("sourceAlpha")), 96 / 255, rel_tol=0.0, abs_tol=0.001), "source alpha mismatch")
        require(math.isclose(rgba[3], 96 / 255, rel_tol=0.0, abs_tol=0.001), "source RGBA alpha mismatch")

        reference = item.get("referenceCoverage")
        require(isinstance(reference, dict), "referenceCoverage block missing")
        require(reference.get("strokeBand") == "round-round", "stroke band mismatch")
        require(reference.get("cap") == "round", "cap mismatch")
        require(reference.get("join") == "round", "join mismatch")
        require(reference.get("coverageExpectedByte") == 160, "expected coverage byte mismatch")
        require(math.isclose(float(reference.get("coverageExpectedAlpha")), 160 / 255, rel_tol=0.0, abs_tol=0.001), "expected coverage alpha mismatch")
        require(math.isclose(float(reference.get("observedToExpectedRatio")), 0.6, rel_tol=0.0, abs_tol=0.001), "ratio mismatch")
        require(reference.get("expectedObservedRatioSignature") == "96/160", "ratio signature mismatch")

        metadata = item.get("bandMetadata")
        require(isinstance(metadata, dict), "bandMetadata missing")
        require(metadata.get("strokeBand") == "round-round", "metadata band mismatch")
        require(metadata.get("strokeWidth") == 10.0, "metadata stroke width mismatch")

        neighborhood = item.get("coverageNeighborhood3x3")
        require(isinstance(neighborhood, dict), "coverage neighborhood missing")
        require(neighborhood.get("centerCoverageByte") == 160, "center coverage mismatch")
        samples = neighborhood.get("samples")
        require(isinstance(samples, list) and len(samples) == 9, "coverage neighborhood must contain 9 samples")
        require(any(sample.get("dx") == 0 and sample.get("dy") == 0 and sample.get("coverageByte") == 160 for sample in samples), "center sample missing")

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
        "partial-coverage-alpha-quantization-mismatch",
        "6 pixels partiels",
        "96/255",
        "160/255",
        "96/160",
    ):
        require(token in report, f"report missing {token}")

    print("FOR-424 validation passed")


if __name__ == "__main__":
    main()
