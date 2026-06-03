#!/usr/bin/env python3
"""Validate FOR-253 bitmap/source rounding stage audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "bitmap-source-rounding-audit-for253.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-source-rounding-audit-for253"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/bitmap-source-rounding-audit-for253"
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-253-bitmap-source-rounding-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"

EXPECTED_GLOBAL_HISTOGRAM = {
    "r": {-1: 1696, 0: 208},
    "g": {-1: 112, 0: 1792},
    "b": {-1: 1696, 0: 208},
    "a": {0: 1904},
}

EXPECTED_CASES = {
    "source-color-constant.simple-offset-row1-col0": {
        "sceneId": "simple-offsetimagefilter",
        "route": "webgpu.canvas.draw-rect.src-over",
        "stageIsolation": "appears-before-bitmap-sampling",
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
        "representative": ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0]),
        "topPair": ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0], 1600),
    },
    "bitmap-nearest.generated-whole-scene": {
        "sceneId": "bitmap-rect-nearest",
        "route": "webgpu.image-rect.strict-nearest",
        "stageIsolation": "appears-during-bitmap-nearest-path",
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
        "representative": ([149, 193, 207, 255], [148, 193, 207, 255], [-1, 0, 0, 0]),
        "topPair": ([149, 193, 207, 255], [148, 193, 207, 255], [-1, 0, 0, 0], 64),
    },
    "linear-gradient.generated-whole-scene": {
        "sceneId": "linear-gradient-rect",
        "route": "webgpu.generated.linear-gradient.rect",
        "stageIsolation": "exact-final-pack-store-control",
        "bounds": [0, 0, 64, 64],
        "totalPixels": 4096,
        "residualPixels": 0,
        "maxChannelDelta": 0,
        "histogram": {"r": {}, "g": {}, "b": {}, "a": {}},
        "representative": ([], [], []),
        "topPair": None,
    },
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-253 validation failed: {message}")


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
    require_text(probe, "linear", "FOR-253")
    require_text(probe, "probe", "bitmap-source-rounding-stage-audit")
    require_text(probe, "deltaDefinition", "signed channel delta is GPU minus reference")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    if probe.get("correctionApplied") is not False:
        fail("FOR-253 must not apply a correction")
    if probe.get("microCaseCount") != 3:
        fail(f"microCaseCount expected 3, got {probe.get('microCaseCount')}")
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

    if probe.get("residualCaseIds") != [
        "source-color-constant.simple-offset-row1-col0",
        "bitmap-nearest.generated-whole-scene",
    ]:
        fail(f"residualCaseIds changed: {probe.get('residualCaseIds')}")
    if probe.get("exactControlCaseIds") != ["linear-gradient.generated-whole-scene"]:
        fail(f"exactControlCaseIds changed: {probe.get('exactControlCaseIds')}")

    stage = probe.get("stageIsolation")
    if not isinstance(stage, dict):
        fail("stageIsolation must be an object")
    if stage.get("appearsBeforeBitmapSampling") is not True:
        fail("source-color case must prove residual before bitmap sampling")
    if stage.get("appearsDuringBitmapNearestPath") is not True:
        fail("bitmap-nearest case must keep reproducing the residual")
    if stage.get("onlyFinalPackOrStore") is not False:
        fail("linear-gradient exact control must rule out pack/store-only diagnosis")
    require_text(
        stage,
        "inferredProducer",
        "input color normalization/rounding before or at source-color uniform and bitmap texel sample, not Crop and not a global final pack/store-only stage",
    )
    require_text(
        stage,
        "nextSubProblem",
        "compare legacy source-color uniform packing and bitmap texture upload/sample rounding against generated solid/gradient exact controls",
    )

    cases = probe.get("microCases")
    if not isinstance(cases, list) or len(cases) != len(EXPECTED_CASES):
        fail("microCases must contain exactly the three FOR-253 cases")
    by_id = {case.get("id"): case for case in cases if isinstance(case, dict)}
    if set(by_id) != set(EXPECTED_CASES):
        fail(f"micro case ids changed: {sorted(by_id)}")

    for case_id, expected in EXPECTED_CASES.items():
        case = by_id[case_id]
        require_text(case, "sceneId", expected["sceneId"])  # type: ignore[arg-type]
        require_text(case, "route", expected["route"])  # type: ignore[arg-type]
        require_text(case, "stageIsolation", expected["stageIsolation"])  # type: ignore[arg-type]
        require_array(case, "bounds", expected["bounds"])  # type: ignore[arg-type]
        for field in ("totalPixels", "residualPixels", "maxChannelDelta"):
            if case.get(field) != expected[field]:
                fail(f"{case_id} {field} expected {expected[field]}, got {case.get(field)}")
        if case.get("alphaDeltaNonZeroPixels") != 0:
            fail(f"{case_id} must not have alpha deltas")
        if case.get("rgbOnlyResidualPixels") != expected["residualPixels"]:
            fail(f"{case_id} rgbOnlyResidualPixels changed: {case.get('rgbOnlyResidualPixels')}")
        expected_ref, expected_gpu, expected_delta = expected["representative"]  # type: ignore[misc]
        require_array(case, "representativeReferenceRgba", expected_ref)
        require_array(case, "representativeGpuRgba", expected_gpu)
        require_array(case, "representativeSignedDeltaRgba", expected_delta)
        validate_histogram(
            case.get("signedDeltaHistogram", {}),
            expected["histogram"],  # type: ignore[arg-type]
            case_id,
        )
        top_pairs = case.get("topColorPairs")
        if expected["topPair"] is None:
            if top_pairs != []:
                fail(f"{case_id} topColorPairs expected [], got {top_pairs}")
        else:
            if not isinstance(top_pairs, list) or not top_pairs:
                fail(f"{case_id} must include topColorPairs")
            validate_color_pair(top_pairs[0], expected["topPair"])  # type: ignore[arg-type]


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "for251ColorPremulAuditProbe", "color-premul-audit-for251.json")
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for253" in json.dumps(route_prepass).lower():
        fail("FOR-253 should not mutate crop route metadata")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-253 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    device_text = require_file_text(WEBGPU_DEVICE, ["cropNonNullOffsetChildPrePassScratch"])
    forbidden = ["kanvas.webgpu.for253", "FOR253", "readback fallback", "cpu fallback"]
    for needle in forbidden:
        if needle in device_text:
            fail(f"FOR-253 must not add renderer-side `{needle}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-253 bitmap source rounding audit isolates residual stage",
            "bitmap-source-rounding-audit-for253.json",
            "appearsBeforeBitmapSampling",
            "onlyFinalPackOrStore",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-253 Bitmap/Source Rounding Stage Audit",
            "input color normalization/rounding",
            "Decision: `KEEP_DIAGNOSTIC`",
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-253 validation passed: source-color and bitmap-nearest carry the "
        "RGB byte tail, linear-gradient is exact, and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
