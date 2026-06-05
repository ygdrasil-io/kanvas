#!/usr/bin/env python3
"""Validate FOR-427 M60 F16 AA stencil-cover subsample mask evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-aa-stencil-cover-subsample-mask-for427"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-427-m60-f16-aa-stencil-cover-subsample-mask.md"
FOR426_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-coverage-input-stage-for426"
    / "m60-f16-aa-stencil-cover-coverage-input-stage-for426.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

ALLOWED_CLASSIFICATIONS = {
    "wgsl-misses-cpu-covered-subsamples",
    "wgsl-adds-extra-subsamples",
    "sample-grid-coordinate-shift",
    "winding-rule-disagreement",
    "edge-evaluation-disagreement",
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
    raise SystemExit(f"FOR-427 validation failed: {message}")


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


def require_wgsl_grid(grid: Any, field: str, *, expected_wgsl: int, expected_mask: int) -> int:
    require(isinstance(grid, list) and len(grid) == 16, f"{field} must contain 16 cells")
    seen = set()
    wgsl = 0
    mask = 0
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
        require(isinstance(cell.get("wgslCovered"), bool), f"{field}.wgslCovered missing")
        if cell["wgslCovered"]:
            wgsl += 1
            mask |= 1 << index
    require(seen == set(range(16)), f"{field} incomplete subsample index set")
    require(wgsl == expected_wgsl, f"{field} WGSL covered count mismatch")
    require(mask == expected_mask, f"{field} does not match wgslSubsampleMask4x4")
    return wgsl


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16AaStencilCoverSubsampleMaskFor427(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "validatorCommandPresent": "validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py" in capture,
        "runtimeFlagPresent": "m60F16AaStencilCoverSubsampleMaskFor427.enabled" in capture and "m60F16AaStencilCoverSubsampleMaskFor427.enabled" in device,
        "wgslMaskHelperPresent": "m60_f16_subsample_mask_4x4" in device,
        "for426CounterPreserved": "m60_f16_covered_subsamples_4x4" in device,
        "noSyntheticCpuMask": "M60_F16_FOR427_EXPECTED_CPU_SUBSAMPLE_MASK" not in capture,
        "noPipelineKeyMutation": "pipelineKey" not in device[device.find("m60_f16_subsample_mask_4x4") : device.find("m60_f16_subsample_mask_4x4") + 1200],
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")


def main() -> None:
    data = load_json(ARTIFACT)
    for426 = load_json(FOR426_ARTIFACT)

    require(ARTIFACT.stat().st_size < 260_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-427", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceDraftMemory") == "global/kanvas/tickets/drafts/brouillon-ticket-for-427-m60-f16-comparer-les-sous-echantillons-cpu-et-wgsl-sample-covered", "FOR-427 draft link missing")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-426-raw-path-coverage-already-96-before-clip", "FOR-426 finding link missing")
    require(data.get("sourceArtifacts", {}).get("for426") == rel(FOR426_ARTIFACT), "FOR-426 artifact link missing")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == "subsample-mask-stage-incomplete", "global classification must refuse missing CPU subsample identity")
    for key in ("supportClaim", "promoted", "defaultRenderingChanged", "thresholdChanged", "scoringChanged"):
        require(data.get(key) is False, f"{key} must remain false")

    require(for426.get("classification") == "path-coverage-already-96", "FOR-426 prerequisite changed")
    require(for426.get("structuralSummary", {}).get("partialPixelCount") == 6, "FOR-426 partial count changed")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain exactly six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partial point set mismatch")

    class_counts = {name: 0 for name in ALLOWED_CLASSIFICATIONS}
    cpu_only_total = 0
    wgsl_only_total = 0
    matching_total = 0
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
        require(pixel.get("cpuCoveredSubsamples4x4") == 10, "CPU 10/16 count missing")
        require(pixel.get("wgslCoveredSubsamples4x4") == 6, "WGSL 6/16 count missing")
        require(pixel.get("classification") == "subsample-mask-stage-incomplete", "pixel classification must refuse missing CPU subsample identity")
        class_counts[pixel["classification"]] += 1

        require(pixel.get("cpuSubsampleMask4x4") is None, "CPU mask must not be synthesized")
        require(pixel.get("cpuGrid4x4") is None, "CPU grid must not be synthesized")
        wgsl_mask = pixel.get("wgslSubsampleMask4x4")
        require(isinstance(wgsl_mask, int), "WGSL mask missing")
        require_wgsl_grid(pixel.get("wgslGrid4x4"), "wgslGrid4x4", expected_wgsl=6, expected_mask=wgsl_mask)
        matching = pixel.get("matchingSubsamples")
        cpu_only = pixel.get("cpuOnlySubsamples")
        wgsl_only = pixel.get("wgslOnlySubsamples")
        require(matching is None and cpu_only is None and wgsl_only is None, "comparison counters must stay null without CPU mask")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "summary partial count mismatch")
    require(summary.get("expectedCoverage160Count") == 6, "summary expected coverage mismatch")
    require(summary.get("for426RawPathCoverage96Count") == 6, "summary raw path coverage mismatch")
    require(summary.get("for426CoveredSubsamples6Count") == 6, "summary FOR-426 subsample mismatch")
    require(summary.get("cpuCoveredSubsamples4x4Total") == 60, "summary CPU total mismatch")
    require(summary.get("wgslCoveredSubsamples4x4Total") == 36, "summary WGSL total mismatch")
    require(summary.get("matchingSubsamplesTotal") is None, "summary matching total must stay null")
    require(summary.get("cpuOnlySubsamplesTotal") is None, "summary CPU-only total must stay null")
    require(summary.get("wgslOnlySubsamplesTotal") is None, "summary WGSL-only total must stay null")
    require(summary.get("majorityClassification") == data.get("classification"), "summary/global classification mismatch")
    require(summary.get("majorityHypothesis") == "subsample-mask-stage-incomplete", "majority hypothesis must be incomplete")
    counts = summary.get("classificationCounts")
    require(isinstance(counts, dict), "summary classificationCounts missing")
    require(set(counts.keys()) == ALLOWED_CLASSIFICATIONS, "summary classificationCounts keys mismatch")
    require(sum(int(v) for v in counts.values()) == 6, "summary classification counts must sum to six")
    require(counts.get("subsample-mask-stage-incomplete") == 6, "all pixels must be incomplete without CPU mask")
    require(all(counts[k] == v for k, v in class_counts.items()), "summary classification counts contradict pixels")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in ("FOR-427", "FOR-426", "10/16", "6/16", "sample_covered", data["classification"], "scanFillPath"):
        require(token in report, f"report missing {token}")

    print(f"FOR-427 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
