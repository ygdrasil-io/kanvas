#!/usr/bin/env python3
"""Validate FOR-423 M60 F16 verified source/coverage/reference evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-reference-source-coverage-for423"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-423-m60-f16-aa-stencil-cover-reference-source-coverage.md"
FOR422_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-verified-source-comparison-for422"
    / "m60-f16-aa-stencil-cover-verified-source-comparison-for422.json"
)
FOR421_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421"
    / "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421.json"
)
FOR372_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-effective-coverage-export-for372"
    / "m60-f16-effective-coverage-export-for372.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

ALLOWED_CLASSIFICATIONS = {
    "verified-source-and-coverage-match-reference",
    "verified-source-diverges-from-reference",
    "verified-coverage-diverges-from-reference",
    "reference-comparison-incomplete",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-423 validation failed: {message}")


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


def require_rgba_byte(value: Any, field: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{field} channel must be byte")
        out.append(channel)
    return out


def require_delta(delta: Any, field: str, *, expected_within: bool | None = None) -> None:
    require(isinstance(delta, dict), f"{field} missing")
    require_rgba_float(delta.get("signedRgbaFloat"), f"{field}.signedRgbaFloat")
    require_rgba_float(delta.get("absoluteRgbaFloat"), f"{field}.absoluteRgbaFloat")
    require(isinstance(delta.get("absoluteTotalFloat"), (float, int)), f"{field}.absoluteTotalFloat missing")
    require(isinstance(delta.get("maxChannelFloat"), (float, int)), f"{field}.maxChannelFloat missing")
    require(isinstance(delta.get("withinTolerance"), bool), f"{field}.withinTolerance missing")
    require(isinstance(delta.get("tolerance"), (float, int)), f"{field}.tolerance missing")
    if expected_within is not None:
        require(delta["withinTolerance"] is expected_within, f"{field}.withinTolerance mismatch")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverReferenceSourceCoverage(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "referenceTolerancePresent": "FOR423_REFERENCE_TOLERANCE" in capture,
        "coverageClassificationPresent": "verified-coverage-diverges-from-reference" in capture,
        "noDeviceRuntimeChangeFor423": "FOR-423" not in device and SCENE_ID not in device,
        "noFor423RuntimeFlag": "m60F16AaStencilCoverReferenceSourceCoverage" not in device,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for422 = load_json(FOR422_ARTIFACT)
    for421 = load_json(FOR421_ARTIFACT)
    for372 = load_json(FOR372_ARTIFACT)

    require(ARTIFACT.stat().st_size < 200_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-423", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("classification") == "verified-coverage-diverges-from-reference", "unexpected classification")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    require(for421.get("classification") == "verified-return-path-storage-nonzero", "FOR-421 prerequisite missing")
    require(for422.get("classification") == "verified-source-matches-scratch-and-final-mutation", "FOR-422 prerequisite missing")
    require(for372.get("classification") == "coverage-export-ready-for-candidate-probe", "FOR-372 coverage reference missing")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "return path must be verified")
    require(summary.get("selectedPixelCount") == 16, "selected pixel count mismatch")
    require(summary.get("localComparisonCount") == 48, "local comparison count mismatch")
    require(summary.get("decisiveComparisonCount") == 16, "decisive comparison count mismatch")
    require(summary.get("verifiedSourceRecordCount") == 16, "verified source count mismatch")
    require(summary.get("coverageReferenceCount") == 48, "coverage reference count mismatch")
    require(summary.get("coverageDivergenceCount") == 6, "coverage divergence count mismatch")
    require(summary.get("sourceDivergenceCount") == 10, "source divergence count mismatch")
    require(summary.get("sourceCoverageMatchCount") == 0, "unexpected source/coverage match count")
    require(summary.get("incompleteCount") == 32, "incomplete count mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "classificationCounts missing")
    require(counts.get("verified-coverage-diverges-from-reference") == 6, "coverage class count mismatch")
    require(counts.get("verified-source-diverges-from-reference") == 10, "source class count mismatch")
    require(counts.get("reference-comparison-incomplete") == 32, "incomplete class count mismatch")

    comparisons = data.get("localComparisons")
    require(isinstance(comparisons, list) and len(comparisons) == 48, "localComparisons size mismatch")
    decisive = [item for item in comparisons if item.get("decisiveForGlobalClassification") is True]
    require(len(decisive) == 16, "expected 16 decisive comparisons")
    coverage_divergent = [item for item in decisive if item.get("classification") == "verified-coverage-diverges-from-reference"]
    source_divergent = [item for item in decisive if item.get("classification") == "verified-source-diverges-from-reference"]
    require(len(coverage_divergent) == 6, "expected 6 decisive coverage divergences")
    require(len(source_divergent) == 10, "expected 10 decisive source divergences")

    for item in coverage_divergent:
        reference = item.get("reference")
        source = item.get("verifiedSource")
        final = item.get("finalOutput")
        require(isinstance(reference, dict), "reference block missing")
        require(reference.get("coverageExpectedByte") == 160, "coverage divergent byte must be 160")
        require_rgba_float(reference.get("expectedSourcePremulRgbaFloat"), "expectedSourcePremulRgbaFloat")
        require_rgba_byte(reference.get("referenceRgba"), "referenceRgba")
        require_rgba_byte(reference.get("currentGpuRgba"), "currentGpuRgba")
        require(isinstance(source, dict) and source.get("available") is True, "verified source missing")
        require_rgba_float(source.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
        require(isinstance(source.get("coverageVsReferenceDelta"), (float, int)), "coverage delta missing")
        require(float(source["coverageVsReferenceDelta"]) > 0.24, "coverage delta too small")
        require_delta(source.get("sourceVsReferenceDelta"), "sourceVsReferenceDelta", expected_within=False)
        require(isinstance(final, dict), "finalOutput missing")
        require_delta(final.get("dstAfterVsReferenceDelta"), "dstAfterVsReferenceDelta", expected_within=False)

    for item in source_divergent:
        reference = item.get("reference")
        source = item.get("verifiedSource")
        require(isinstance(reference, dict), "reference block missing")
        require(reference.get("coverageExpectedByte") == 255, "source divergent byte must be 255")
        require(isinstance(source, dict) and source.get("available") is True, "verified source missing")
        require(float(source.get("coverageVsReferenceDelta", -1)) == 0.0, "source divergence should not be coverage divergence")
        require_delta(source.get("sourceVsReferenceDelta"), "sourceVsReferenceDelta", expected_within=False)

    incomplete = [item for item in comparisons if item.get("classification") == "reference-comparison-incomplete"]
    require(len(incomplete) == 32, "expected 32 incomplete bounded records")
    require(all(item.get("decisiveForGlobalClassification") is False for item in incomplete), "incomplete records must not be decisive")

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
        "verified-coverage-diverges-from-reference",
        "6 divergences de couverture",
        "10 divergences de source",
        "32 comparaisons incompletes",
    ):
        require(token in report, f"report missing {token}")

    print("FOR-423 validation passed")


if __name__ == "__main__":
    main()
