#!/usr/bin/env python3
"""Validate FOR-254 source uniform vs bitmap texel rounding audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "source-texel-rounding-audit-for254.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/source-texel-rounding-audit-for254"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/source-texel-rounding-audit-for254"
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-254-source-texel-rounding-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"

EXPECTED_GLOBAL_HISTOGRAM = {
    "r": {-1: 1696, 0: 208},
    "g": {-1: 112, 0: 1792},
    "b": {-1: 1696, 0: 208},
    "a": {0: 1904},
}

EXPECTED_CASES = {
    "legacy-source-color-uniform.simple-offset-row1-col0": {
        "pathKind": "legacy-source-color-uniform",
        "sceneId": "simple-offsetimagefilter",
        "route": "webgpu.canvas.draw-rect.src-over",
        "bounds": [40, 40, 120, 120],
        "totalPixels": 6400,
        "residualPixels": 1600,
        "maxChannelDelta": 1,
        "inputPackedRgba": [255, 0, 0, 102],
        "inputNormalizedRgba": [1.0, 0.0, 0.0, 0.4],
        "representative": ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0]),
        "histogram": {
            "r": {-1: 1600},
            "g": {0: 1600},
            "b": {-1: 1600},
            "a": {0: 1600},
        },
    },
    "generated-solid-control.solid-rect": {
        "pathKind": "generated-solid-control",
        "sceneId": "solid-rect",
        "route": "webgpu.generated.solid-rect.src-over",
        "bounds": [0, 0, 8, 8],
        "totalPixels": 64,
        "residualPixels": 0,
        "maxChannelDelta": 0,
        "inputPackedRgba": [23, 33, 28, 255],
        "inputNormalizedRgba": [0.09019608, 0.12156863, 0.10980392, 1.0],
        "representative": ([23, 33, 28, 255], [23, 33, 28, 255], [0, 0, 0, 0]),
        "histogram": {"r": {}, "g": {}, "b": {}, "a": {}},
    },
    "generated-gradient-control.linear-gradient-rect": {
        "pathKind": "generated-gradient-control",
        "sceneId": "linear-gradient-rect",
        "route": "webgpu.generated.linear-gradient.rect",
        "bounds": [0, 0, 64, 64],
        "totalPixels": 4096,
        "residualPixels": 0,
        "maxChannelDelta": 0,
        "inputPackedRgba": [],
        "inputNormalizedRgba": [],
        "representative": ([125, 0, 130, 255], [125, 0, 130, 255], [0, 0, 0, 0]),
        "histogram": {"r": {}, "g": {}, "b": {}, "a": {}},
    },
    "bitmap-texel-upload-sample.bitmap-rect-nearest": {
        "pathKind": "bitmap-texel-upload-sample",
        "sceneId": "bitmap-rect-nearest",
        "route": "webgpu.image-rect.strict-nearest",
        "bounds": [0, 0, 64, 64],
        "totalPixels": 4096,
        "residualPixels": 304,
        "maxChannelDelta": 1,
        "inputPackedRgba": [149, 193, 207, 255],
        "inputNormalizedRgba": [0.58431375, 0.75686276, 0.8117647, 1.0],
        "representative": ([149, 193, 207, 255], [148, 193, 207, 255], [-1, 0, 0, 0]),
        "histogram": {
            "r": {-1: 96, 0: 208},
            "g": {-1: 112, 0: 192},
            "b": {-1: 96, 0: 208},
            "a": {0: 304},
        },
    },
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-254 validation failed: {message}")


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


def require_float_array(owner: dict[str, Any], field: str, expected: list[float]) -> None:
    actual = owner.get(field)
    if not isinstance(actual, list) or len(actual) != len(expected):
        fail(f"`{field}` expected {expected}, got {actual}")
    for index, (actual_value, expected_value) in enumerate(zip(actual, expected)):
        if abs(float(actual_value) - expected_value) > 1e-6:
            fail(f"`{field}` index {index} expected {expected_value}, got {actual_value}")


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


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-254")
    require_text(probe, "probe", "source-uniform-vs-bitmap-texel-rounding-audit")
    require_text(probe, "deltaDefinition", "signed channel delta is GPU minus reference")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    if probe.get("correctionApplied") is not False:
        fail("FOR-254 must not apply a correction")
    if probe.get("pathCount") != 4:
        fail(f"pathCount expected 4, got {probe.get('pathCount')}")
    if probe.get("totalPixels") != 14656:
        fail(f"totalPixels expected 14656, got {probe.get('totalPixels')}")
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
        "legacy-source-color-uniform.simple-offset-row1-col0",
        "bitmap-texel-upload-sample.bitmap-rect-nearest",
    ]:
        fail(f"residualCaseIds changed: {probe.get('residualCaseIds')}")
    if probe.get("exactControlKinds") != ["generated-solid-control", "generated-gradient-control"]:
        fail(f"exactControlKinds changed: {probe.get('exactControlKinds')}")

    comparison = probe.get("sourceTexelComparison")
    if not isinstance(comparison, dict):
        fail("sourceTexelComparison must be an object")
    if comparison.get("legacySourceUniformHasResidual") is not True:
        fail("legacy source uniform residual signal missing")
    if comparison.get("bitmapTexelPathHasResidual") is not True:
        fail("bitmap texel residual signal missing")
    if comparison.get("generatedControlsExact") is not True:
        fail("generated solid/gradient controls must be exact")
    for field in (
        "hostSideNormalizationVerdict",
        "uniformBufferWriteVerdict",
        "textureUploadSampleVerdict",
        "correctionReadiness",
        "nextInstrumentation",
    ):
        value = comparison.get(field)
        if not isinstance(value, str) or not value:
            fail(f"missing sourceTexelComparison.{field}")
    if "not ready" not in comparison["correctionReadiness"]:
        fail("FOR-254 must keep correctionReadiness as not ready")

    cases = probe.get("cases")
    if not isinstance(cases, list) or len(cases) != len(EXPECTED_CASES):
        fail("cases must contain exactly the four FOR-254 paths")
    by_id = {case.get("id"): case for case in cases if isinstance(case, dict)}
    if set(by_id) != set(EXPECTED_CASES):
        fail(f"case ids changed: {sorted(by_id)}")

    for case_id, expected in EXPECTED_CASES.items():
        case = by_id[case_id]
        require_text(case, "pathKind", expected["pathKind"])  # type: ignore[arg-type]
        require_text(case, "sceneId", expected["sceneId"])  # type: ignore[arg-type]
        require_text(case, "route", expected["route"])  # type: ignore[arg-type]
        require_array(case, "bounds", expected["bounds"])  # type: ignore[arg-type]
        for field in ("totalPixels", "residualPixels", "maxChannelDelta"):
            if case.get(field) != expected[field]:
                fail(f"{case_id} {field} expected {expected[field]}, got {case.get(field)}")
        if case.get("alphaDeltaNonZeroPixels") != 0:
            fail(f"{case_id} must not have alpha deltas")
        if case.get("rgbOnlyResidualPixels") != expected["residualPixels"]:
            fail(f"{case_id} rgbOnlyResidualPixels changed: {case.get('rgbOnlyResidualPixels')}")
        input_observation = case.get("inputObservation")
        if not isinstance(input_observation, dict):
            fail(f"{case_id} missing inputObservation")
        require_array(input_observation, "packedRgba", expected["inputPackedRgba"])  # type: ignore[arg-type]
        require_float_array(
            input_observation,
            "normalizedRgba",
            expected["inputNormalizedRgba"],  # type: ignore[arg-type]
        )
        if not input_observation.get("observableStatus"):
            fail(f"{case_id} missing observableStatus")

        representative = case.get("representativeOutput")
        if not isinstance(representative, dict):
            fail(f"{case_id} missing representativeOutput")
        expected_ref, expected_gpu, expected_signed_delta = expected["representative"]  # type: ignore[misc]
        require_array(representative, "referenceRgba", expected_ref)
        require_array(representative, "gpuRgba", expected_gpu)
        actual_signed = [
            representative["gpuRgba"][i] - representative["referenceRgba"][i]
            for i in range(4)
        ]
        if actual_signed != expected_signed_delta:
            fail(f"{case_id} representative signed delta changed: {actual_signed}")
        validate_histogram(
            case.get("signedDeltaHistogram", {}),
            expected["histogram"],  # type: ignore[arg-type]
            case_id,
        )


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for254" in json.dumps(route_prepass).lower():
        fail("FOR-254 should not mutate crop route metadata")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-254 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    device_text = require_file_text(WEBGPU_DEVICE, ["cropNonNullOffsetChildPrePassScratch"])
    forbidden = ["kanvas.webgpu.for254", "FOR254", "readback fallback", "cpu fallback"]
    for needle in forbidden:
        if needle in device_text:
            fail(f"FOR-254 must not add renderer-side `{needle}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-254 source uniform and bitmap texel rounding audit compares input paths",
            "source-texel-rounding-audit-for254.json",
            "legacySourceUniformHasResidual",
            "bitmapTexelPathHasResidual",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-254 Source Uniform vs Bitmap Texel Rounding Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "raw uniform-buffer bytes",
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-254 validation passed: legacy source uniform and bitmap texel paths "
        "carry the RGB byte tail, generated controls are exact, and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
