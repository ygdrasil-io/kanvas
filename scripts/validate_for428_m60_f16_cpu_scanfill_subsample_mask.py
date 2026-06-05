#!/usr/bin/env python3
"""Validate FOR-428 M60 F16 CPU scanFillPath subsample mask evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-cpu-scanfill-subsample-mask-for428"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-428-m60-f16-cpu-scanfill-subsample-mask.md"
FOR427_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-subsample-mask-for427"
    / "m60-f16-aa-stencil-cover-subsample-mask-for427.json"
)
FOR426_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-coverage-input-stage-for426"
    / "m60-f16-aa-stencil-cover-coverage-input-stage-for426.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BITMAP_DEVICE = ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

ALLOWED_CLASSIFICATIONS = {
    "wgsl-misses-cpu-covered-subsamples",
    "wgsl-adds-extra-subsamples",
    "sample-grid-coordinate-shift",
    "winding-rule-disagreement",
    "edge-evaluation-disagreement",
    "scanfill-span-count-exceeds-center-mask",
    "cpu-scanfill-subsample-mask-unavailable",
    "subsample-mask-stage-incomplete",
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
    raise SystemExit(f"FOR-428 validation failed: {message}")


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


def require_grid(
    grid: Any,
    field: str,
    *,
    expected_cpu: int,
    expected_wgsl: int,
    expected_cpu_mask: int,
    expected_wgsl_mask: int,
) -> tuple[int, int, int, int, int]:
    require(isinstance(grid, list) and len(grid) == 16, f"{field} must contain 16 cells")
    seen = set()
    cpu_mask = 0
    wgsl_mask = 0
    matching = 0
    cpu_only = 0
    wgsl_only = 0
    for cell in grid:
        require(isinstance(cell, dict), f"{field} cell must be object")
        index = cell.get("subsampleIndex")
        require(isinstance(index, int) and 0 <= index < 16, f"{field} invalid subsampleIndex")
        require(index not in seen, f"{field} duplicate subsampleIndex {index}")
        seen.add(index)
        require(cell.get("sx") == index % 4, f"{field} sx mismatch")
        require(cell.get("sy") == index // 4, f"{field} sy mismatch")
        for coord in ("localX", "localY", "deviceX", "deviceY"):
            require(isinstance(cell.get(coord), (float, int)), f"{field}.{coord} missing")
        cpu_covered = cell.get("cpuCovered")
        wgsl_covered = cell.get("wgslCovered")
        divergent = cell.get("divergent")
        require(isinstance(cpu_covered, bool), f"{field}.cpuCovered missing")
        require(isinstance(wgsl_covered, bool), f"{field}.wgslCovered missing")
        require(isinstance(divergent, bool), f"{field}.divergent missing")
        require(divergent == (cpu_covered != wgsl_covered), f"{field}.divergent contradicts cells")
        if cpu_covered:
            cpu_mask |= 1 << index
        if wgsl_covered:
            wgsl_mask |= 1 << index
        if cpu_covered == wgsl_covered:
            matching += 1
        elif cpu_covered:
            cpu_only += 1
        else:
            wgsl_only += 1
    require(seen == set(range(16)), f"{field} incomplete subsample index set")
    require(cpu_mask.bit_count() == expected_cpu, f"{field} CPU covered count mismatch")
    require(wgsl_mask.bit_count() == expected_wgsl, f"{field} WGSL covered count mismatch")
    require(cpu_mask == expected_cpu_mask, f"{field} does not match cpuSubsampleMask4x4")
    require(wgsl_mask == expected_wgsl_mask, f"{field} does not match wgslSubsampleMask4x4")
    return cpu_mask.bit_count(), wgsl_mask.bit_count(), matching, cpu_only, wgsl_only


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = BITMAP_DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16CpuScanFillSubsampleMaskFor428(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagPresent": "m60F16ScanFillSubsampleMaskFor428.enabled" in capture
        and "m60F16ScanFillSubsampleMaskFor428.enabled" in build,
        "traceObjectPresent": "object SkScanFillPathSubsampleTrace" in device,
        "traceRecordsScanFillSamples": "samplesAddedByScanFillPath" in device,
        "traceUsesSubsampleCenter": "val sampleX = pixelX + (sx + 0.5f) / supers" in device,
        "traceHookInsideAddSpanCoverage": "SkScanFillPathSubsampleTrace.recordSpanCoverage" in device,
        "noOldSyntheticConstant": "M60_F16_FOR427_EXPECTED_CPU_SUBSAMPLE_MASK" not in capture,
        "validatorCommandPresent": "validate_for428_m60_f16_cpu_scanfill_subsample_mask.py" in capture,
        "noPipelineKeyMutation": "PipelineKey" not in capture[capture.find(SCENE_ID) : capture.find(SCENE_ID) + 8000],
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for427 = load_json(FOR427_ARTIFACT)
    for426 = load_json(FOR426_ARTIFACT)

    require(ARTIFACT.stat().st_size < 320_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-428", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceDraftMemory") == "global/kanvas/tickets/drafts/brouillon-ticket-for-428-m60-f16-exporter-le-masque-4x4-cpu-scan-fill-path-pour-comparaison-subsample", "FOR-428 draft link missing")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-427-subsample-mask-stage-incomplete-because-cpu-scan-fill-path-identity-is-not-exported", "FOR-427 finding link missing")
    require(data.get("previousFindingMemory") == "global/kanvas/findings/for-426-raw-path-coverage-already-96-before-clip", "FOR-426 finding link missing")
    require(data.get("sourceArtifacts", {}).get("for427") == rel(FOR427_ARTIFACT), "FOR-427 artifact link missing")
    require(data.get("sourceArtifacts", {}).get("for426") == rel(FOR426_ARTIFACT), "FOR-426 artifact link missing")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == "scanfill-span-count-exceeds-center-mask", "unexpected global classification")
    require(data.get("classification") != "subsample-mask-stage-incomplete", "FOR-428 must not stay incomplete")
    for key in ("supportClaim", "promoted", "defaultRenderingChanged", "thresholdChanged", "scoringChanged"):
        require(data.get(key) is False, f"{key} must remain false")
    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("noSyntheticCpuMask") is True, "CPU mask must be marked non-synthetic")
    require("SkBitmapDevice.scanFillPath.addSpanCoverage" in policy.get("cpuMaskSource", ""), "CPU mask source must name scanFillPath")
    require("x-center" in policy.get("cpuMaskSamplingRule", ""), "CPU sampling rule must name subsample centers")

    require(for427.get("classification") == "subsample-mask-stage-incomplete", "FOR-427 prerequisite changed")
    require(for426.get("classification") == "path-coverage-already-96", "FOR-426 prerequisite changed")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain exactly six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partial point set mismatch")

    class_counts = {name: 0 for name in ALLOWED_CLASSIFICATIONS}
    matching_total = 0
    cpu_only_total = 0
    wgsl_only_total = 0
    cpu_total = 0
    wgsl_total = 0
    for pixel in pixels:
        require(pixel.get("drawIndex") == 1, "drawIndex mismatch")
        require(pixel.get("subdrawOrdinal") == 0, "subdrawOrdinal mismatch")
        require(pixel.get("subdrawRole") == "inside", "subdrawRole mismatch")
        require(pixel.get("entryPoint") == "fs_inside", "entryPoint mismatch")
        require(pixel.get("edgeCount") == 8, "edgeCount mismatch")
        require(pixel.get("fillType") == "kWinding", "fillType mismatch")
        require(pixel.get("expectedCoverageByte") == 160, "expectedCoverageByte mismatch")
        require(pixel.get("for426RawPathCoverageByte") == 96, "for426RawPathCoverageByte mismatch")
        require(pixel.get("for426CoveredSubsamples4x4") == 6, "for426CoveredSubsamples4x4 mismatch")
        require(pixel.get("classification") == "scanfill-span-count-exceeds-center-mask", "pixel classification mismatch")
        class_counts[pixel["classification"]] += 1

        cpu_mask = pixel.get("cpuSubsampleMask4x4")
        wgsl_mask = pixel.get("wgslSubsampleMask4x4")
        require(isinstance(cpu_mask, int), "CPU mask missing")
        require(isinstance(wgsl_mask, int), "WGSL mask missing")
        require(cpu_mask.bit_count() == 6, "CPU center mask must cover 6 subsamples")
        require(wgsl_mask.bit_count() == 6, "WGSL mask must cover 6 subsamples")
        require(cpu_mask == wgsl_mask, "CPU center mask must match WGSL mask")
        require(pixel.get("cpuCoveredSubsamples4x4") == 6, "CPU center 6/16 count missing")
        require(pixel.get("cpuScanFillPathSamples") == 10, "scanFillPath span sample total must be 10")
        require(pixel.get("cpuTraceSource") == "SkBitmapDevice.scanFillPath.addSpanCoverage", "CPU trace source mismatch")
        require(isinstance(pixel.get("cpuTraceSpanCount"), int) and pixel["cpuTraceSpanCount"] > 0, "CPU trace span count missing")
        require(pixel.get("wgslCoveredSubsamples4x4") == 6, "WGSL 6/16 count missing")

        cpu_grid = require_grid(
            pixel.get("cpuGrid4x4"),
            "cpuGrid4x4",
            expected_cpu=6,
            expected_wgsl=6,
            expected_cpu_mask=cpu_mask,
            expected_wgsl_mask=wgsl_mask,
        )
        wgsl_grid = require_grid(
            pixel.get("wgslGrid4x4"),
            "wgslGrid4x4",
            expected_cpu=6,
            expected_wgsl=6,
            expected_cpu_mask=cpu_mask,
            expected_wgsl_mask=wgsl_mask,
        )
        require(cpu_grid == wgsl_grid, "CPU and WGSL grids disagree on cell facts")
        _, _, matching, cpu_only, wgsl_only = cpu_grid
        require(pixel.get("matchingSubsamples") == matching, "matchingSubsamples contradicts grid")
        require(pixel.get("cpuOnlySubsamples") == cpu_only, "cpuOnlySubsamples contradicts grid")
        require(pixel.get("wgslOnlySubsamples") == wgsl_only, "wgslOnlySubsamples contradicts grid")
        require(cpu_only == 0, "expected no CPU-only subsamples when comparing center masks")
        require(wgsl_only == 0, "expected no WGSL-only subsamples")
        matching_total += matching
        cpu_only_total += cpu_only
        wgsl_only_total += wgsl_only
        cpu_total += cpu_mask.bit_count()
        wgsl_total += wgsl_mask.bit_count()

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "summary partial count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "summary expected coverage mismatch")
    require(summary.get("for426RawPathCoverage96Count") == 6, "summary raw path coverage mismatch")
    require(summary.get("for426CoveredSubsamples6Count") == 6, "summary FOR-426 subsample mismatch")
    require(summary.get("cpuCoveredSubsamples4x4Total") == cpu_total == 36, "summary CPU center-mask total mismatch")
    require(summary.get("cpuScanFillPathSamplesTotal") == 60, "summary CPU scanFill sample total mismatch")
    require(summary.get("wgslCoveredSubsamples4x4Total") == wgsl_total == 36, "summary WGSL total mismatch")
    require(summary.get("matchingSubsamplesTotal") == matching_total == 96, "summary matching total mismatch")
    require(summary.get("cpuOnlySubsamplesTotal") == cpu_only_total == 0, "summary CPU-only total mismatch")
    require(summary.get("wgslOnlySubsamplesTotal") == wgsl_only_total == 0, "summary WGSL-only total mismatch")
    require(summary.get("majorityClassification") == data.get("classification"), "summary/global classification mismatch")
    require(summary.get("majorityHypothesis") == "scanfill-span-quantization-disagreement", "majority hypothesis mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "summary classificationCounts missing")
    require(set(counts.keys()) == ALLOWED_CLASSIFICATIONS, "summary classificationCounts keys mismatch")
    require(sum(int(v) for v in counts.values()) == 6, "summary classification counts must sum to six")
    require(all(counts[k] == v for k, v in class_counts.items()), "summary classification counts contradict pixels")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in ("FOR-428", "FOR-427", "scanFillPath", "span", "0x0137", data["classification"]):
        require(token in report, f"report missing {token}")

    print(f"FOR-428 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
