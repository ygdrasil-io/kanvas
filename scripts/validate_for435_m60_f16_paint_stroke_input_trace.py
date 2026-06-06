#!/usr/bin/env python3
"""Validate FOR-435 M60 F16 paint/stroke input diagnostic evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-paint-stroke-input-trace-for435"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-435-m60-f16-paint-stroke-input-trace.md"
FOR434_SCENE_ID = "m60-f16-stencil-source-payload-trace-for434"
FOR434_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR434_SCENE_ID / f"{FOR434_SCENE_ID}.json"
FOR434_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-434-m60-f16-stencil-source-payload-trace.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled"
FOR434_FLAG = "kanvas.webgpu.m60F16StencilSourcePayloadTraceFor434.enabled"
FOR433_FLAG = "kanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled"
FOR432_FLAG = "kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled"
FOR431_FLAG = "kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
EXPECTED_CLASSIFICATION = "host-paint-input-mismatch"
ALLOWED_CLASSIFICATIONS = {
    "host-paint-input-mismatch",
    "target-colorspace-conversion-mismatch",
    "bounded-correction-mismatch",
    "wrong-draw-paint-selected",
    "trace-incomplete",
    "paint-input-origin-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py",
    "scripts/validate_for434_m60_f16_stencil_source_payload_trace.py",
    "scripts/validate_for435_m60_f16_paint_stroke_input_trace.py",
    "scripts/validate_for436_m60_f16_host_draw_paint_binding.py",
    "scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py",
    "reports/wgsl-pipeline/2026-06-06-for-435-m60-f16-paint-stroke-input-trace.md",
    "reports/wgsl-pipeline/2026-06-06-for-436-m60-f16-host-draw-paint-binding.md",
    "reports/wgsl-pipeline/2026-06-06-for-437-m60-f16-cpu-reference-source-expectation.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-host-draw-paint-binding-for436",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-host-draw-paint-binding-for436/m60-f16-host-draw-paint-binding-for436.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437/m60-f16-cpu-reference-source-expectation-for437.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-435 validation failed: {message}")


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
    require(isinstance(value, (int, float)) and not isinstance(value, bool), f"{field} must be numeric")
    return float(value)


def require_rgba(value: Any, expected: list[int], field: str) -> None:
    require(value == expected, f"{field} expected {expected}, got {value}")


def require_float_list(value: Any, field: str, length: int = 4) -> list[float]:
    require(isinstance(value, list) and len(value) == length, f"{field} must be a {length}-float list")
    return [require_number(item, f"{field}[{index}]") for index, item in enumerate(value)]


def require_delta_object(value: Any, field: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{field} must be object")
    for key in (
        "signedRgbaFloat",
        "absoluteRgbaFloat",
        "absoluteTotalFloat",
        "maxChannelFloat",
        "withinTolerance",
        "tolerance",
    ):
        require(key in value, f"{field}.{key} missing")
    return value


def require_alpha(value: Any, expected: float, field: str, tol: float = 0.000001) -> None:
    actual = require_number(value, field)
    require(math.isclose(actual, expected, abs_tol=tol), f"{field} expected {expected}, got {actual}")


def delta_max(a: list[float], b: list[float]) -> float:
    return max(abs(left - right) for left, right in zip(a, b))


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 36000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16PaintStrokeInputTraceFor435(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagOptInTestRelayOnly": FLAG in capture and FLAG not in device and FLAG in build,
        "usesExistingTraceFields": all(
            token in scene_window
            for token in (
                "colorAfterColorFilter",
                "colorAfterTargetColorspaceIfNeeded",
                "correctedColorBeforeCoverage",
                "sourceColorBeforeQuantization",
                "sourceColorSentToBlend",
                "dstBeforeRgbaFloat",
            )
        ),
        "doesNotTouchPipelineKey": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for435_m60_f16_paint_stroke_input_trace.py" in capture,
        "for431StillOptIn": "withM60F16WidthQuantizedRenderFixFor431(true)" in capture and FOR431_FLAG in capture,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    try:
        diff_result = subprocess.run(
            ["git", "diff", "--name-only", "HEAD"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        status_result = subprocess.run(
            ["git", "status", "--short"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        capture_diff = subprocess.run(
            ["git", "diff", "--unified=0", "--", rel(CAPTURE_TEST)],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return

    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-435: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden production/spec/external diffs: {forbidden}")
    successor_for436_present = any("for436" in path.lower() for path in changed)
    require(
        successor_for436_present or rel(DEVICE) not in changed,
        "SkWebGpuDevice.kt must not change for FOR-435 unless a successor FOR-436 diagnostic is present",
    )

    dangerous_threshold_lines = [
        line
        for line in capture_diff.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
        )
    ]
    require(not dangerous_threshold_lines, f"threshold/scoring/fallback lines changed: {dangerous_threshold_lines}")


def main() -> None:
    data = load_json(ARTIFACT)
    for434 = load_json(FOR434_ARTIFACT)

    require(ARTIFACT.stat().st_size < 140_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-435", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR434_SCENE_ID, "FOR-434 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-inspecter-entree-paint-stroke-aa-stencil-cover-draw-index-3",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-434-web-gpu-stencil-source-payload-trace-identifies-paint-payload-mismatch",
        "FOR-434 finding link missing",
    )
    require(data.get("sourceArtifact") == rel(FOR434_ARTIFACT), "FOR-434 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR434_REPORT), "FOR-434 report link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")
    require(data.get("diagnosticFlag") == FLAG, "FOR-435 diagnostic flag mismatch")
    require(data.get("sourceFor434DiagnosticFlag") == FOR434_FLAG, "FOR-434 flag mismatch")
    require(data.get("sourceFor433DiagnosticFlag") == FOR433_FLAG, "FOR-433 flag mismatch")
    require(data.get("sourceFor432DiagnosticFlag") == FOR432_FLAG, "FOR-432 flag mismatch")
    require(data.get("sourceFor431OptInFlag") == FOR431_FLAG, "FOR-431 flag mismatch")

    for key in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "renderingFixApplied",
    ):
        require(data.get(key) is False, f"{key} must remain false")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "fallbackChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "for431ActivatedByDefault",
        "renderingFixApplied",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    require(for434.get("classification") == "paint-payload-mismatch", "FOR-434 prerequisite changed")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("hostPaintInputMismatchCount") == 6, "host-paint mismatch count mismatch")
    require(summary.get("targetColorspaceConversionMismatchCount") == 0, "target conversion mismatch count must be zero")
    require(summary.get("boundedCorrectionMismatchCount") == 0, "bounded correction mismatch count must be zero")
    require(summary.get("wrongDrawPaintSelectedCount") == 0, "draw selection mismatch count must be zero")
    require(summary.get("traceIncompletePixelCount") == 0, "trace must be complete")
    require(summary.get("paintInputOriginUnresolvedCount") == 0, "origin should be resolved")
    require(
        require_number(summary.get("maxHostFilteredVsRequiredPaintDelta"), "summary.maxHostFilteredVsRequired")
        > 0.49,
        "host paint input must be far from CPU-required paint payload",
    )
    require(
        require_number(summary.get("maxTargetColorspaceVsRequiredPaintDelta"), "summary.maxTargetVsRequired")
        > 0.49,
        "target colorspace result must remain far from CPU-required paint payload",
    )
    require(
        require_number(summary.get("maxShaderPayloadVsRequiredPaintDelta"), "summary.maxShaderPayloadVsRequired")
        > 0.49,
        "shader payload before coverage must remain far from CPU-required paint payload",
    )
    require(
        require_number(summary.get("maxShaderPayloadVsTargetColorspaceDelta"), "summary.maxShaderPayloadVsTarget")
        <= 0.000002,
        "shader payload before coverage should match target colorspace result",
    )

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require_alpha(policy.get("coverageAlpha"), 10 / 16, "comparisonPolicy.coverageAlpha")
    require(policy.get("noRenderingFixApplied") is True, "policy must be diagnostic-only")
    require(policy.get("boundedToSixPixels") is True, "policy must be bounded")

    host = data.get("hostStrokeInput")
    require(isinstance(host, dict), "hostStrokeInput missing")
    require(host.get("paintColorRgba8") == [0, 138, 76, 255], "host paint fixture color mismatch")
    require(host.get("paintColorHexArgb") == "0xFF008A4C", "host paint hex mismatch")
    require(host.get("strokeWidth") == 10.0, "stroke width mismatch")
    require(host.get("cap") == "round", "cap mismatch")
    require(host.get("join") == "round", "join mismatch")
    require(host.get("rawHostPaintFieldCaptured") is False, "raw host paint field should not be claimed")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six records")
    require({(p.get("x"), p.get("y")) for p in pixels if isinstance(p, dict)} == EXPECTED_POINTS, "partial point set mismatch")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        point = (pixel.get("x"), pixel.get("y"))
        require(pixel.get("drawIndex") == 1, f"{point} drawIndex mismatch")
        require(pixel.get("effectiveRenderDrawIndex") == 3, f"{point} effective render drawIndex mismatch")
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, f"{point} classification mismatch")
        require(pixel.get("missingFields") == [], f"{point} must have complete trace")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], f"{point}.referenceCpuRgba")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], f"{point}.currentWebGpuRgba")
        require_rgba(pixel.get("optInFor431Rgba"), [111, 147, 129, 255], f"{point}.optInFor431Rgba")

        required = pixel.get("requiredPaintPayloadBeforeCoverage")
        require(isinstance(required, dict), f"{point}.requiredPaintPayloadBeforeCoverage missing")
        required_paint = require_float_list(required.get("rgbaFloat"), f"{point}.requiredPaintPayload.rgbaFloat")
        require(math.isclose(required_paint[3], 1.0, abs_tol=0.000001), f"{point} required paint alpha mismatch")
        require(required_paint[2] > 0.79, f"{point} required paint blue should be high")

        host_input = pixel.get("hostPaintStrokeInput")
        require(isinstance(host_input, dict), f"{point}.hostPaintStrokeInput missing")
        filtered = require_float_list(host_input.get("colorAfterColorFilter"), f"{point}.colorAfterColorFilter")
        require(delta_max(filtered, [0.0, 138 / 255, 76 / 255, 1.0]) <= 1 / 255, f"{point} filtered host paint mismatch")
        host_delta = require_delta_object(host_input.get("hostFilteredVsRequiredPaintDelta"), f"{point}.host delta")
        require(host_delta.get("withinTolerance") is False, f"{point} host paint must differ from required paint")

        chain = pixel.get("paintTransformChain")
        require(isinstance(chain, dict), f"{point}.paintTransformChain missing")
        target = require_float_list(chain.get("colorAfterTargetColorspaceIfNeeded"), f"{point}.target colorspace")
        corrected = require_float_list(chain.get("correctedColorBeforeCoverage"), f"{point}.corrected before coverage")
        effective = require_float_list(chain.get("effectivePayloadBeforeCoverage"), f"{point}.effective payload")
        require(delta_max(effective, target) <= 0.000002, f"{point} effective payload should match target colorspace")
        require(delta_max(corrected, filtered) <= 0.000002, f"{point} corrected field should match filtered host paint")
        target_delta = require_delta_object(chain.get("targetColorspaceVsRequiredPaintDelta"), f"{point}.target delta")
        shader_required_delta = require_delta_object(chain.get("shaderPayloadVsRequiredPaintDelta"), f"{point}.shader required delta")
        shader_target_delta = require_delta_object(chain.get("shaderPayloadVsTargetColorspaceDelta"), f"{point}.shader target delta")
        bounded_target_delta = require_delta_object(chain.get("boundedCorrectionVsTargetColorspaceDelta"), f"{point}.bounded target delta")
        require(target_delta.get("withinTolerance") is False, f"{point} target colorspace should differ from required")
        require(shader_required_delta.get("withinTolerance") is False, f"{point} shader payload should differ from required")
        require(shader_target_delta.get("withinTolerance") is True, f"{point} shader payload should match target colorspace")
        require(bounded_target_delta.get("withinTolerance") is False, f"{point} bounded diagnostic field should not be treated as target payload")

        candidate = pixel.get("selectedStencilCandidate")
        require(isinstance(candidate, dict), f"{point}.selectedStencilCandidate missing")
        require(candidate.get("label") == "single-0-inside", f"{point} selected candidate label mismatch")
        require(candidate.get("drawIndex") == 3, f"{point} selected drawIndex mismatch")
        require(candidate.get("subdrawOrdinal") == 0, f"{point} selected subdraw ordinal mismatch")
        require(candidate.get("subdrawRole") == "inside", f"{point} selected subdraw role mismatch")
        require(candidate.get("candidateBranchReached") is True, f"{point} candidate branch must be reached")
        require(candidate.get("ambiguousSingleStencilCandidateCount") == 2, f"{point} candidate count mismatch")
        require(candidate.get("alternateSingleCandidatesShareTarget") is True, f"{point} alternate target should match")

        candidates = pixel.get("candidateSubdraws")
        require(isinstance(candidates, list) and len(candidates) == 2, f"{point} candidate subdraws mismatch")
        identities = {
            (sample.get("drawIndex"), sample.get("subdrawOrdinal"), sample.get("subdrawRole"))
            for sample in candidates
            if isinstance(sample, dict)
        }
        require(identities == {(3, 0, "inside"), (3, 1, "outside")}, f"{point} candidate identities mismatch")
        require(all(sample.get("targetEqualsSelected") is True for sample in candidates), f"{point} targets must match")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-435",
        EXPECTED_CLASSIFICATION,
        FLAG,
        "0xFF008A4C",
        "10/16",
        "diagnostic-only",
        "drawIndex effectif `3`",
        "mémoire",
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-435 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
