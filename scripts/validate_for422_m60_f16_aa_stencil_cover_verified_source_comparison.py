#!/usr/bin/env python3
"""Validate FOR-422 M60 F16 verified source/scratch/final comparison evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-verified-source-comparison-for422"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-05-for-422-m60-f16-aa-stencil-cover-verified-source-comparison.md"
FOR421_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421"
    / "m60-f16-aa-stencil-cover-verified-return-path-diagnostic-for421.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

ALLOWED_CLASSIFICATIONS = {
    "verified-source-matches-scratch-and-final-mutation",
    "verified-source-matches-scratch-but-final-blend-diverges",
    "verified-source-diverges-from-scratch",
    "verified-source-comparison-incomplete",
}
EXPECTED_POINTS = {
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (101, 37),
    (102, 37),
    (99, 38),
    (100, 38),
    (101, 38),
    (102, 38),
    (103, 38),
    (104, 38),
    (98, 39),
    (99, 39),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-422 validation failed: {message}")


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


def require_rgba(value: Any, field: str) -> list[float]:
    require(isinstance(value, list) and len(value) == 4, f"{field} must be RGBA")
    out: list[float] = []
    for channel in value:
        require(isinstance(channel, (float, int)), f"{field} channel must be numeric")
        out.append(float(channel))
    return out


def require_delta(delta: Any, field: str, *, expected_within: bool | None = None) -> None:
    require(isinstance(delta, dict), f"{field} delta missing")
    require_rgba(delta.get("signedRgbaFloat"), f"{field}.signedRgbaFloat")
    require_rgba(delta.get("absoluteRgbaFloat"), f"{field}.absoluteRgbaFloat")
    require(isinstance(delta.get("maxChannelFloat"), (float, int)), f"{field}.maxChannelFloat missing")
    require(isinstance(delta.get("withinTolerance"), bool), f"{field}.withinTolerance missing")
    require(isinstance(delta.get("tolerance"), (float, int)), f"{field}.tolerance missing")
    if expected_within is not None:
        require(delta["withinTolerance"] is expected_within, f"{field}.withinTolerance mismatch")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverVerifiedSourceComparison(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "sourceScratchTolerance": "FOR417_RECONSTRUCTION_TOLERANCE" in capture,
        "allowedClassificationPresent": "verified-source-diverges-from-scratch" in capture,
        "noDeviceRuntimeChangeFor422": "FOR-422" not in device and SCENE_ID not in device,
        "noFor422RuntimeFlag": "m60F16AaStencilCoverVerifiedSourceComparison" not in device,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for421 = load_json(FOR421_ARTIFACT)
    require(ARTIFACT.stat().st_size < 300_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-422", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "verified-source-matches-scratch-and-final-mutation",
        "unexpected current classification",
    )
    require(data.get("supportClaim") is False, "support claim must stay false")
    require(data.get("promoted") is False, "M60 F16 must not be promoted")
    require(data.get("defaultRenderingChanged") is False, "default rendering must not change")
    require(data.get("thresholdChanged") is False, "threshold must not change")
    require(data.get("scoringChanged") is False, "scoring must not change")

    allowed = data.get("allowedClassifications")
    require(isinstance(allowed, list) and set(allowed) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
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

    require(for421.get("classification") == "verified-return-path-storage-nonzero", "FOR-421 prerequisite missing")
    for421_summary = for421.get("structuralSummary")
    require(isinstance(for421_summary, dict), "FOR-421 structural summary missing")
    require(for421_summary.get("storageNonzeroSourceCount") == 32, "FOR-421 source count changed")

    summary = data.get("structuralSummary")
    require(isinstance(summary, dict), "structuralSummary missing")
    require(summary.get("diagnosticReturnPathVerified") is True, "return path must be verified")
    require(summary.get("selectedPixelCount") == 16, "selected pixel count mismatch")
    require(summary.get("localComparisonCount") == 48, "local comparison count mismatch")
    require(summary.get("decisiveComparisonCount") == 16, "decisive comparison count mismatch")
    require(summary.get("verifiedSourceSubdrawCount") == 32, "verified source subdraw count mismatch")
    require(summary.get("nonzeroVerifiedSourceSubdrawCount") == 32, "nonzero source count mismatch")
    require(summary.get("scratchColorTargetObservedCount") == 48, "scratch observation count mismatch")
    require(summary.get("nonzeroScratchColorTargetCount") == 16, "nonzero scratch count mismatch")
    require(summary.get("dstBeforeObservedCount") == 48, "dstBefore count mismatch")
    require(summary.get("dstAfterObservedCount") == 48, "dstAfter count mismatch")
    require(summary.get("sourceMatchesScratchCount") == 16, "source/scratch match count mismatch")
    require(summary.get("scratchReconstructsFinalCount") == 48, "scratch/final reconstruction count mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "classificationCounts missing")
    require(counts.get("verified-source-matches-scratch-and-final-mutation") == 16, "mutation match count mismatch")
    require(counts.get("verified-source-comparison-incomplete") == 32, "incomplete unchanged draw count mismatch")

    comparisons = data.get("localComparisons")
    require(isinstance(comparisons, list) and len(comparisons) == 48, "localComparisons size mismatch")
    points = {(item.get("x"), item.get("y")) for item in comparisons}
    require(points == EXPECTED_POINTS, f"selected point set mismatch: {sorted(points)}")
    decisive = [item for item in comparisons if item.get("decisiveForGlobalClassification") is True]
    require(len(decisive) == 16, "expected 16 decisive comparisons")
    for item in decisive:
        require(item.get("classification") == "verified-source-matches-scratch-and-final-mutation", "decisive class mismatch")
        source_subdraws = item.get("sourceSubdraws")
        require(isinstance(source_subdraws, list) and len(source_subdraws) == 2, "decisive source subdraw count mismatch")
        for source in source_subdraws:
            require(source.get("shaderObserved") is True, "decisive source must be observed")
            require(source.get("captureSynthetic") is False, "decisive source must not be synthetic")
            require_rgba(source.get("sourceColorSentToBlend"), "sourceColorSentToBlend")
            require_delta(source.get("sourceVsScratchDelta"), "sourceVsScratchDelta", expected_within=True)
        scratch = item.get("scratchColorTarget")
        require(isinstance(scratch, dict) and scratch.get("available") is True, "scratch must be available")
        require_rgba(scratch.get("scratchOutputRgbaFloat"), "scratchOutputRgbaFloat")
        dest = item.get("destination")
        require(isinstance(dest, dict), "destination missing")
        require_rgba(dest.get("dstBeforeRgbaFloat"), "dstBeforeRgbaFloat")
        require_rgba(dest.get("dstAfterRgbaFloat"), "dstAfterRgbaFloat")
        require_delta(dest.get("dstAfterMinusBeforeDelta"), "dstAfterMinusBeforeDelta", expected_within=False)
        best = item.get("bestSourceVsScratchDelta")
        require(isinstance(best, dict), "best source delta missing")
        require_delta(best.get("delta"), "bestSourceVsScratchDelta.delta", expected_within=True)
        reconstruction = item.get("reconstruction")
        require(isinstance(reconstruction, dict), "reconstruction missing")
        require_rgba(reconstruction.get("reconstructedRgbaFloat"), "reconstructedRgbaFloat")
        require_delta(reconstruction.get("reconstructedVsDstAfterDelta"), "reconstructedVsDstAfterDelta", expected_within=True)

    incomplete = [item for item in comparisons if item.get("classification") == "verified-source-comparison-incomplete"]
    require(len(incomplete) == 32, "expected 32 bounded incomplete unchanged comparisons")
    require(all(item.get("decisiveForGlobalClassification") is False for item in incomplete), "incomplete records must not be decisive")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    require("verified-source-matches-scratch-and-final-mutation" in report, "report missing classification")
    require("16" in report and "32" in report and "48" in report, "report missing evidence counts")
    print("FOR-422 validation passed")


if __name__ == "__main__":
    main()
