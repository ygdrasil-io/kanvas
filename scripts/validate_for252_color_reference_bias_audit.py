#!/usr/bin/env python3
"""Validate FOR-252 non-image-filter color/reference bias audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "color-reference-bias-audit-for252.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/color-reference-bias-audit-for252"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/color-reference-bias-audit-for252"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
ROUTE_PREPASS = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json"
)
STATIC_ROUTE_PREPASS = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json"
)
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-252-color-reference-bias-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"

EXPECTED_GLOBAL_HISTOGRAM = {
    "r": {-1: 1696, 0: 208},
    "g": {-1: 112, 0: 1792},
    "b": {-1: 1696, 0: 208},
    "a": {0: 1904},
}

EXPECTED_SAMPLES = {
    "simple-offsetimagefilter.row1-col0-no-filter": {
        "sceneId": "simple-offsetimagefilter",
        "route": "webgpu.canvas.draw-rect.src-over",
        "bounds": [40, 40, 120, 120],
        "totalPixels": 6400,
        "residualPixels": 1600,
        "maxChannelDelta": 1,
        "histogram": {
            "r": {-1: 1600},
            "g": {0: 1600},
            "b": {-1: 1600},
            "a": {0: 1600},
        },
        "topPair": ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0], 1600),
    },
    "bitmap-rect-nearest.whole-scene": {
        "sceneId": "bitmap-rect-nearest",
        "route": "webgpu.image-rect.strict-nearest",
        "bounds": [0, 0, 64, 64],
        "totalPixels": 4096,
        "residualPixels": 304,
        "maxChannelDelta": 1,
        "histogram": {
            "r": {-1: 96, 0: 208},
            "g": {-1: 112, 0: 192},
            "b": {-1: 96, 0: 208},
            "a": {0: 304},
        },
        "topPair": ([149, 193, 207, 255], [148, 193, 207, 255], [-1, 0, 0, 0], 64),
    },
    "linear-gradient-rect.whole-scene": {
        "sceneId": "linear-gradient-rect",
        "route": "webgpu.generated.linear-gradient.rect",
        "bounds": [0, 0, 64, 64],
        "totalPixels": 4096,
        "residualPixels": 0,
        "maxChannelDelta": 0,
        "histogram": {"r": {}, "g": {}, "b": {}, "a": {}},
        "topPair": None,
    },
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-252 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_array(owner: dict[str, Any], field: str, expected: list[int]) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def normalize_histogram(histogram: dict[str, Any]) -> dict[str, dict[int, int]]:
    normalized: dict[str, dict[int, int]] = {}
    for channel, entries in histogram.items():
        if not isinstance(entries, list):
            fail(f"histogram channel `{channel}` must be a list")
        normalized[channel] = {}
        for entry in entries:
            if not isinstance(entry, dict):
                fail(f"histogram channel `{channel}` entry must be an object")
            normalized[channel][int(entry.get("delta"))] = int(entry.get("count"))
    return normalized


def validate_histogram(actual: dict[str, Any], expected: dict[str, dict[int, int]], label: str) -> None:
    normalized = normalize_histogram(actual)
    if normalized != expected:
        fail(f"{label} signed delta histogram changed: {normalized}")


def validate_color_pair(group: dict[str, Any], expected: tuple[list[int], list[int], list[int], int]) -> None:
    expected_ref, expected_gpu, expected_delta, expected_count = expected
    require_array(group, "referenceRgba", expected_ref)
    require_array(group, "gpuRgba", expected_gpu)
    require_array(group, "signedDeltaRgba", expected_delta)
    if group.get("count") != expected_count:
        fail(f"color pair count expected {expected_count}, got {group.get('count')}")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-252")
    require_text(probe, "probe", "non-image-filter-color-reference-bias-audit")
    require_text(probe, "deltaDefinition", "signed channel delta is GPU minus reference")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    if probe.get("correctionApplied") is not False:
        fail("FOR-252 must not apply a correction")
    if probe.get("sampleCount") != 3:
        fail(f"sampleCount expected 3, got {probe.get('sampleCount')}")
    if probe.get("totalPixels") != 14592:
        fail(f"totalPixels expected 14592, got {probe.get('totalPixels')}")
    if probe.get("totalResidualPixels") != 1904:
        fail(f"totalResidualPixels expected 1904, got {probe.get('totalResidualPixels')}")
    if probe.get("maxChannelDelta") != 1:
        fail(f"maxChannelDelta expected 1, got {probe.get('maxChannelDelta')}")
    if probe.get("alphaDeltaNonZeroPixels") != 0:
        fail(f"alphaDeltaNonZeroPixels expected 0, got {probe.get('alphaDeltaNonZeroPixels')}")
    if probe.get("rgbOnlyResidualPixels") != 1904:
        fail(f"rgbOnlyResidualPixels expected 1904, got {probe.get('rgbOnlyResidualPixels')}")
    validate_histogram(probe.get("signedDeltaHistogram", {}), EXPECTED_GLOBAL_HISTOGRAM, "global")

    expected_residual_ids = [
        "simple-offsetimagefilter.row1-col0-no-filter",
        "bitmap-rect-nearest.whole-scene",
    ]
    if probe.get("samplesWithRgbByteResidual") != expected_residual_ids:
        fail(f"samplesWithRgbByteResidual changed: {probe.get('samplesWithRgbByteResidual')}")
    if probe.get("samplesWithoutResidual") != ["linear-gradient-rect.whole-scene"]:
        fail(f"samplesWithoutResidual changed: {probe.get('samplesWithoutResidual')}")

    samples = probe.get("samples")
    if not isinstance(samples, list) or len(samples) != len(EXPECTED_SAMPLES):
        fail("samples must contain exactly the three FOR-252 non-image-filter samples")
    by_id = {sample.get("id"): sample for sample in samples if isinstance(sample, dict)}
    if set(by_id) != set(EXPECTED_SAMPLES):
        fail(f"sample ids changed: {sorted(by_id)}")
    for sample_id, expected in EXPECTED_SAMPLES.items():
        sample = by_id[sample_id]
        require_text(sample, "sceneId", expected["sceneId"])  # type: ignore[arg-type]
        require_text(sample, "route", expected["route"])  # type: ignore[arg-type]
        require_array(sample, "bounds", expected["bounds"])  # type: ignore[arg-type]
        if sample.get("imageFilterInPath") is not False:
            fail(f"{sample_id} must be marked imageFilterInPath=false")
        for field in ("totalPixels", "residualPixels", "maxChannelDelta"):
            if sample.get(field) != expected[field]:
                fail(f"{sample_id} {field} expected {expected[field]}, got {sample.get(field)}")
        if sample.get("alphaDeltaNonZeroPixels") != 0:
            fail(f"{sample_id} must not have alpha deltas")
        if sample.get("rgbOnlyResidualPixels") != expected["residualPixels"]:
            fail(f"{sample_id} rgbOnlyResidualPixels changed: {sample.get('rgbOnlyResidualPixels')}")
        validate_histogram(
            sample.get("signedDeltaHistogram", {}),
            expected["histogram"],  # type: ignore[arg-type]
            sample_id,
        )
        top_pairs = sample.get("topColorPairs")
        if expected["topPair"] is None:
            if top_pairs != []:
                fail(f"{sample_id} topColorPairs expected [], got {top_pairs}")
        else:
            if not isinstance(top_pairs, list) or not top_pairs:
                fail(f"{sample_id} must include topColorPairs")
            validate_color_pair(top_pairs[0], expected["topPair"])  # type: ignore[arg-type]


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "for251ColorPremulAuditProbe", "color-premul-audit-for251.json")
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for252" in json.dumps(route_prepass).lower():
        fail("FOR-252 should not mutate crop route metadata")


def find_scene(results: dict[str, Any], scene_id: str) -> dict[str, Any]:
    for scene in results.get("scenes", []):
        if scene.get("id") == scene_id:
            return scene
    fail(f"missing generated scene `{scene_id}`")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-252 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    results = load_json(PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json")
    refusal = find_scene(results, "image-filter-crop-nonnull-prepass-required")
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict) or not isinstance(refusal_gpu.get("route"), dict):
        fail("refusal row must keep GPU route diagnostics")
    require_text(refusal_gpu["route"], "fallbackReason", FALLBACK_REASON)

    device_text = require_file_text(WEBGPU_DEVICE, ["cropNonNullOffsetChildPrePassScratch"])
    forbidden = ["kanvas.webgpu.for252", "FOR252", "readback fallback", "cpu fallback"]
    for needle in forbidden:
        if needle in device_text:
            fail(f"FOR-252 must not add renderer-side `{needle}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-252 color reference bias audit compares non image filter samples",
            "color-reference-bias-audit-for252.json",
            "imageFilterInPath = false",
            "signed channel delta is GPU minus reference",
            "bitmap-rect-nearest",
            "linear-gradient-rect",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-252",
            "simple-offsetimagefilter.row1-col0-no-filter",
            "bitmap-rect-nearest.whole-scene",
            "KEEP_DIAGNOSTIC",
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-252 color/reference bias audit OK: RGB-only byte residual "
        "reproduces outside image-filter in SimpleOffset row1-col0 and "
        "bitmap-rect-nearest; linear-gradient-rect remains exact; no Crop correction"
    )


if __name__ == "__main__":
    main()
