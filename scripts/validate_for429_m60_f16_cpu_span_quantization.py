#!/usr/bin/env python3
"""Validate FOR-429 M60 F16 CPU addSpanCoverage quantization evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-cpu-span-quantization-for429"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-429-m60-f16-cpu-span-quantization.md"
FOR428_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-cpu-scanfill-subsample-mask-for428"
    / "m60-f16-cpu-scanfill-subsample-mask-for428.json"
)
FOR427_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-subsample-mask-for427"
    / "m60-f16-aa-stencil-cover-subsample-mask-for427.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BITMAP_DEVICE = ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_POINTS = {
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
}
ALLOWED_CLASSIFICATIONS = {
    "scanfill-rounded-width-exceeds-center-samples",
    "span-quantization-not-explained",
    "cpu-span-quantization-trace-unavailable",
    "subsample-mask-stage-incomplete",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-429 validation failed: {message}")


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


def require_number(value: Any, field: str) -> float:
    require(isinstance(value, (float, int)) and not isinstance(value, bool), f"{field} must be numeric")
    return float(value)


def require_span_rows(pixel: dict[str, Any]) -> tuple[int, int, int]:
    rows = pixel.get("spanQuantizationRows")
    require(isinstance(rows, list) and len(rows) == 4, "spanQuantizationRows must contain four subrows")
    seen_subrows: set[int] = set()
    rounded_total = 0
    center_total = 0
    delta_total = 0
    for row in rows:
        require(isinstance(row, dict), "span row must be object")
        subrow = row.get("subRow")
        require(isinstance(subrow, int) and 0 <= subrow < 4, "invalid subRow")
        require(subrow not in seen_subrows, f"duplicate subRow {subrow}")
        seen_subrows.add(subrow)

        span_left = require_number(row.get("spanLeft"), "spanLeft")
        span_right = require_number(row.get("spanRight"), "spanRight")
        cell_left = require_number(row.get("cellLeft"), "cellLeft")
        cell_right = require_number(row.get("cellRight"), "cellRight")
        intersection_left = require_number(row.get("intersectionLeft"), "intersectionLeft")
        intersection_right = require_number(row.get("intersectionRight"), "intersectionRight")
        intersection_width = require_number(row.get("intersectionWidth"), "intersectionWidth")
        width_times_supers = require_number(row.get("widthTimesSupers"), "widthTimesSupers")
        rounded = row.get("roundedSamples")
        center_count = row.get("centerCoveredCount")
        delta = row.get("roundedMinusCenter")

        require(span_left <= intersection_left <= intersection_right <= span_right, "intersection outside span")
        require(cell_left <= intersection_left <= intersection_right <= cell_right, "intersection outside cell")
        require(math.isclose(cell_right - cell_left, 1.0, abs_tol=0.00001), "cell width must be one pixel")
        require(math.isclose(intersection_right - intersection_left, intersection_width, abs_tol=0.00001), "intersection width mismatch")
        require(math.isclose(intersection_width * 4.0, width_times_supers, abs_tol=0.00001), "widthTimesSupers mismatch")
        require(isinstance(rounded, int) and 0 <= rounded <= 4, "roundedSamples invalid")
        expected_rounded = max(0, min(4, int(width_times_supers + 0.5)))
        require(rounded == expected_rounded, "roundedSamples does not match addSpanCoverage rule")
        require(isinstance(center_count, int) and 0 <= center_count <= 4, "centerCoveredCount invalid")
        require(isinstance(delta, int), "roundedMinusCenter invalid")
        require(delta == rounded - center_count, "roundedMinusCenter mismatch")

        centers = row.get("sampleCenters")
        require(isinstance(centers, list) and len(centers) == 4, "sampleCenters must contain four entries")
        covered_count = 0
        seen_sx: set[int] = set()
        for center in centers:
            require(isinstance(center, dict), "sample center must be object")
            sx = center.get("sx")
            require(isinstance(sx, int) and 0 <= sx < 4, "invalid sample center sx")
            require(sx not in seen_sx, f"duplicate sample center sx {sx}")
            seen_sx.add(sx)
            local_x = require_number(center.get("localX"), "sampleCenters.localX")
            device_x = require_number(center.get("deviceX"), "sampleCenters.deviceX")
            require(math.isclose(local_x, (sx + 0.5) * 0.25, abs_tol=0.00001), "sample localX mismatch")
            require(math.isclose(device_x, pixel["x"] + local_x, abs_tol=0.00001), "sample deviceX mismatch")
            covered = center.get("covered")
            require(isinstance(covered, bool), "sample center covered flag missing")
            expected_covered = intersection_left <= device_x < intersection_right
            require(covered == expected_covered, "sample center coverage contradicts intersection")
            if covered:
                covered_count += 1
        require(seen_sx == {0, 1, 2, 3}, "sample center set incomplete")
        require(covered_count == center_count, "centerCoveredCount contradicts centers")
        rounded_total += rounded
        center_total += center_count
        delta_total += delta
    require(seen_subrows == {0, 1, 2, 3}, "subrow set incomplete")
    return rounded_total, center_total, delta_total


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = BITMAP_DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 12000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16CpuSpanQuantizationFor429(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagPresent": "m60F16CpuSpanQuantizationFor429.enabled" in capture
        and "m60F16CpuSpanQuantizationFor429.enabled" in build,
        "maskInstrumentationRequiresVariant": "shaderReturnStorageVariants.isNotEmpty()" in scene_window,
        "diagnosticReturnPathRequiresVariant": "nonRuntimeCorrectionSummaries.isNotEmpty()" in scene_window,
        "traceRowsPresent": "data class SpanQuantizationRow" in device,
        "traceCentersPresent": "data class SpanSampleCenter" in device,
        "traceRecordsWidthTimesSupers": "widthTimesSupers = width * supers" in device,
        "traceRecordsRoundedMinusCenter": "roundedMinusCenter = samplesAddedByScanFillPath - centerCoveredCount" in device,
        "validatorCommandPresent": "validate_for429_m60_f16_cpu_span_quantization.py" in capture,
        "noPipelineKeyMutationInFor429": "PipelineKey" not in scene_window,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for428 = load_json(FOR428_ARTIFACT)
    for427 = load_json(FOR427_ARTIFACT)

    require(ARTIFACT.stat().st_size < 260_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-429", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceDraftMemory") == "global/kanvas/tickets/drafts/brouillon-ticket-for-429-m60-f16-diagnostiquer-la-quantification-cpu-add-span-coverage", "FOR-429 draft link missing")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-428-cpu-scan-fill-path-span-count-exceeds-center-mask", "FOR-428 finding link missing")
    require(data.get("previousFindingMemory") == "global/kanvas/findings/for-427-subsample-mask-stage-incomplete-because-cpu-scan-fill-path-identity-is-not-exported", "FOR-427 finding link missing")
    require(data.get("sourceArtifacts", {}).get("for428") == rel(FOR428_ARTIFACT), "FOR-428 artifact link missing")
    require(data.get("sourceArtifacts", {}).get("for427") == rel(FOR427_ARTIFACT), "FOR-427 artifact link missing")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == "scanfill-rounded-width-exceeds-center-samples", "unexpected global classification")
    require(data.get("globalClassification") == data.get("classification"), "global classification mismatch")
    for key in ("supportClaim", "promoted", "defaultRenderingChanged", "thresholdChanged", "scoringChanged"):
        require(data.get(key) is False, f"{key} must remain false")
    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("noSyntheticCpuMask") is True, "CPU mask must be marked non-synthetic")
    require("roundedSamples" in policy.get("spanQuantizationRule", ""), "span quantization rule missing")
    require("SkBitmapDevice.scanFillPath.addSpanCoverage" in policy.get("cpuSpanSource", ""), "CPU span source must name addSpanCoverage")

    require(for428.get("classification") == "scanfill-span-count-exceeds-center-mask", "FOR-428 prerequisite changed")
    require(for427.get("classification") == "subsample-mask-stage-incomplete", "FOR-427 prerequisite changed")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain exactly six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partial point set mismatch")

    class_counts = {name: 0 for name in ALLOWED_CLASSIFICATIONS}
    cpu_scan_total = 0
    span_total = 0
    center_total = 0
    wgsl_total = 0
    delta_total = 0
    for pixel in pixels:
        require(isinstance(pixel, dict), "pixel must be object")
        require(pixel.get("drawIndex") == 1, "drawIndex mismatch")
        require(pixel.get("subdrawOrdinal") == 0, "subdrawOrdinal mismatch")
        require(pixel.get("subdrawRole") == "inside", "subdrawRole mismatch")
        require(pixel.get("entryPoint") == "fs_inside", "entryPoint mismatch")
        require(pixel.get("edgeCount") == 8, "edgeCount mismatch")
        require(pixel.get("fillType") == "kWinding", "fillType mismatch")
        require(pixel.get("expectedCoverageByte") == 160, "expectedCoverageByte mismatch")
        require(pixel.get("classification") == "scanfill-rounded-width-exceeds-center-samples", "pixel classification mismatch")
        class_counts[pixel["classification"]] += 1

        require(pixel.get("cpuScanFillPathSamples") == 10, "scanFillPath span sample total must be 10")
        require(pixel.get("spanQuantizedCoveredCount") == 10, "spanQuantizedCoveredCount must be 10")
        require(pixel.get("cpuCenterMask4x4") == 0x0137, "CPU center mask must be 0x0137")
        require(pixel.get("wgslMask4x4") == 0x0137, "WGSL mask must be 0x0137")
        require(pixel.get("cpuCenterCoveredCount") == 6, "CPU center count must be 6")
        require(pixel.get("wgslCoveredCount") == 6, "WGSL count must be 6")
        require(pixel.get("roundedMinusCenterTotal") == 4, "per-pixel rounded-minus-center must be 4")
        require(pixel.get("cpuTraceSpanCount") == 4, "CPU trace span count must be four")
        require(pixel.get("cpuTraceSource") == "SkBitmapDevice.scanFillPath.addSpanCoverage", "CPU trace source mismatch")

        rounded, center, delta = require_span_rows(pixel)
        require(rounded == pixel["spanQuantizedCoveredCount"], "row rounded total contradicts pixel")
        require(center == pixel["cpuCenterCoveredCount"], "row center total contradicts pixel")
        require(delta == pixel["roundedMinusCenterTotal"], "row delta total contradicts pixel")
        require(delta > 0, "row quantization must explain a positive delta")

        cpu_scan_total += pixel["cpuScanFillPathSamples"]
        span_total += rounded
        center_total += center
        wgsl_total += pixel["wgslCoveredCount"]
        delta_total += delta

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "summary partial count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "summary expected coverage mismatch")
    require(summary.get("cpuScanFillPathSamplesTotal") == cpu_scan_total == 60, "summary CPU scanFill total mismatch")
    require(summary.get("spanQuantizedCoveredTotal") == span_total == 60, "summary span total mismatch")
    require(summary.get("cpuCenterCoveredTotal") == center_total == 36, "summary CPU center total mismatch")
    require(summary.get("wgslCoveredTotal") == wgsl_total == 36, "summary WGSL total mismatch")
    require(summary.get("roundedMinusCenterTotal") == delta_total == 24, "summary delta total mismatch")
    require(summary.get("majorityClassification") == data["classification"], "summary/global classification mismatch")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "summary classificationCounts missing")
    require(set(counts.keys()) == ALLOWED_CLASSIFICATIONS, "summary classificationCounts keys mismatch")
    require(sum(int(v) for v in counts.values()) == 6, "summary classification counts must sum to six")
    require(all(counts[k] == v for k, v in class_counts.items()), "summary classification counts contradict pixels")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in ("FOR-429", "FOR-428", "FOR-427", "scanFillPath", "addSpanCoverage", "60/96", "36/96", "0x0137", data["classification"]):
        require(token in report, f"report missing {token}")

    print(f"FOR-429 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
