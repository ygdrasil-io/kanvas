#!/usr/bin/env python3
"""Validate FOR-431 M60 F16 WebGPU width-quantized render-fix evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-width-quantized-render-fix-for431"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-431-m60-f16-webgpu-width-quantized-render-fix.md"
FOR430_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430/"
    "m60-f16-webgpu-cpu-width-quantization-alignment-for430.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
FLAG = "kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "opt-in-render-fix-improves-m60-f16",
    "opt-in-render-fix-regresses-scene",
    "opt-in-render-fix-inconclusive",
}
EXPECTED_CLASSIFICATION = "opt-in-render-fix-regresses-scene"
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py",
    "scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py",
    "reports/wgsl-pipeline/2026-06-06-for-431-m60-f16-webgpu-width-quantized-render-fix.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/reference-cpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-webgpu-width-quantized.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-webgpu-width-quantized-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-width-quantized-render-fix-for431.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-width-quantized-render-fix-for431-diff.png",
    "scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py",
    "reports/wgsl-pipeline/2026-06-06-for-432-m60-f16-width-quantized-color-reconstruction.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-width-quantized-color-reconstruction-for432",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-431 validation failed: {message}")


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


def require_alpha(value: Any, expected: float, field: str) -> None:
    actual = require_number(value, field)
    require(math.isclose(actual, expected, abs_tol=0.000001), f"{field} expected {expected}, got {actual}")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 22000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16WebGpuWidthQuantizedRenderFixFor431(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagPresent": FLAG in capture and FLAG in build and FLAG in device,
        "baselineForcedOff": "withM60F16WidthQuantizedRenderFixFor431(false)" in capture,
        "optInForcedOn": "withM60F16WidthQuantizedRenderFixFor431(true)" in capture,
        "inMemoryShaderVariant": "widthQuantizedRenderFixFor431 = true" in device,
        "targetedSixPixels": "m60_f16_width_quantized_render_fix_for431_target" in device,
        "noPipelineKeyMutationInFor431Producer": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for431_m60_f16_webgpu_width_quantized_render_fix.py" in capture,
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
    except (OSError, subprocess.CalledProcessError):
        return
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-431: {unexpected}")


def main() -> None:
    data = load_json(ARTIFACT)
    for430 = load_json(FOR430_ARTIFACT)

    require(ARTIFACT.stat().st_size < 340_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-431", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-430-web-gpu-cpu-width-quantization-diagnostic-matches-cpu-for-m60-f16-1", "FOR-430 finding link missing")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")

    for key in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
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
        "activationByDefault",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require("=false" in policy.get("defaultRender", ""), "default render must be forced off")
    require("=true" in policy.get("optInRender", ""), "opt-in render must be forced on")
    require("production aa_stencil_cover.wgsl is unchanged" in policy.get("webgpuRenderModel", ""), "WGSL policy missing")

    require(for430.get("classification") == "webgpu-cpu-width-quantization-diagnostic-matches-cpu", "FOR-430 prerequisite changed")
    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("fullScenePixels") == 192 * 128, "full scene pixel count mismatch")
    require(summary.get("currentWgslCoveredTotal") == 36, "current WGSL total mismatch")
    require(summary.get("cpuWidthQuantizedCoveredTotal") == 60, "CPU width total mismatch")
    require(summary.get("optInWidthQuantizedCoveredTotal") == 60, "opt-in width total mismatch")
    require(summary.get("currentDeltaToCpuWidthTotal") == 24, "current delta mismatch")
    require(summary.get("optInDeltaToCpuWidthTotal") == 0, "opt-in delta mismatch")
    require(summary.get("currentTotalResidual") == 2014, "current total residual mismatch")
    require(summary.get("optInTotalResidual") == 2044, "opt-in total residual mismatch")
    require(summary.get("residualDeltaOptInMinusCurrent") == 30, "residual delta mismatch")
    require(summary.get("changedPixels") == 6, "changed pixel count mismatch")
    require(summary.get("changedTargetPixels") == 6, "changed target pixel count mismatch")
    require(summary.get("changedOutsideTargetPixels") == 0, "changed outside target pixel count mismatch")
    require(summary.get("improvedPixels") == 0, "improved pixel count mismatch")
    require(summary.get("regressedPixels") == 6, "regressed pixel count mismatch")
    require_alpha(summary.get("currentWgslCoverageAlpha"), 36 / 96, "summary.currentWgslCoverageAlpha")
    require_alpha(summary.get("cpuWidthQuantizedCoverageAlpha"), 60 / 96, "summary.cpuWidthQuantizedCoverageAlpha")
    require_alpha(summary.get("optInWidthQuantizedCoverageAlpha"), 60 / 96, "summary.optInWidthQuantizedCoverageAlpha")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six records")
    require({(p.get("x"), p.get("y")) for p in pixels if isinstance(p, dict)} == EXPECTED_POINTS, "partial point set mismatch")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        require(pixel.get("drawIndex") == 1, "drawIndex mismatch")
        require(pixel.get("subdrawOrdinal") == 0, "subdrawOrdinal mismatch")
        require(pixel.get("subdrawRole") == "inside", "subdrawRole mismatch")
        require(pixel.get("currentWgslCoveredCount") == 6, "current count mismatch")
        require(pixel.get("cpuWidthQuantizedCoveredCount") == 10, "CPU width count mismatch")
        require(pixel.get("optInWidthQuantizedCoveredCount") == 10, "opt-in width count mismatch")
        require(pixel.get("currentDeltaToCpuWidth") == 4, "current pixel delta mismatch")
        require(pixel.get("optInDeltaToCpuWidth") == 0, "opt-in pixel delta mismatch")
        require(pixel.get("currentResidual") == 105, "current pixel residual mismatch")
        require(pixel.get("optInResidual") == 110, "opt-in pixel residual mismatch")
        require(pixel.get("residualDeltaOptInMinusCurrent") == 5, "pixel residual delta mismatch")
        require(pixel.get("pixelChangedByOptIn") is True, "target pixel must change")
        require_alpha(pixel.get("currentWgslCoverageAlpha"), 6 / 16, "pixel.currentWgslCoverageAlpha")
        require_alpha(pixel.get("cpuWidthQuantizedCoverageAlpha"), 10 / 16, "pixel.cpuWidthQuantizedCoverageAlpha")
        require_alpha(pixel.get("optInWidthQuantizedCoverageAlpha"), 10 / 16, "pixel.optInWidthQuantizedCoverageAlpha")

    require(
        summary.get("residualDeltaOptInMinusCurrent", 0) > 0,
        "FOR-431 regression evidence requires residual increase",
    )

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-431",
        FLAG,
        data["classification"],
        "36/96",
        "60/96",
        "no promotion",
        "activation par defaut",
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-431 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
