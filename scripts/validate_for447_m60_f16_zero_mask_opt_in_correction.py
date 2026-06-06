#!/usr/bin/env python3
"""Validate FOR-447 M60 F16 zero-mask opt-in correction evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-zero-mask-opt-in-correction-for447"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-447-m60-f16-zero-mask-opt-in-correction.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ZeroMaskCorrectionFor447.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
ALLOWED_CLASSIFICATIONS = {
    "zero-mask-opt-in-correction-improves-scene",
    "zero-mask-opt-in-correction-regresses-scene",
    "zero-mask-opt-in-correction-neutral",
    "zero-mask-opt-in-correction-inconclusive",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py",
    "scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py",
    "scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py",
    "reports/wgsl-pipeline/2026-06-06-for-447-m60-f16-zero-mask-opt-in-correction.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/reference-cpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-webgpu-zero-mask-correction.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-webgpu-zero-mask-correction-diff.png",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-447 validation failed: {message}")


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


def git_changed_paths() -> set[str]:
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
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16ZeroMaskOptInCorrectionFor447(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "wrapperPresent": "withM60F16ZeroMaskCorrectionFor447(true)" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "sourceMemories": (
            "brouillon-ticket-m60-f16-appliquer-une-correction-opt-in-basee-sur-les-masques-zero-for-445-for-443"
            in capture
            and "for-446-le-champ-float-for-442-est-retire-des-preuves-de-couverture" in capture
        ),
        "zeroMaskShaderVariant": (
            "m60_f16_zero_mask_correction_for447_target" in device
            and "experimental://m60-f16-aa-stencil-cover-zero-mask-correction-for447" in device
            and "discard;" in device
        ),
        "optInOnlyRuntime": (
            "m60F16ZeroMaskCorrectionFor447Requested" in device
            and "m60F16ZeroMaskCorrectionFor447Enabled" in device
            and "m60F16ZeroMaskCorrectionFor447PipelineFor" in device
        ),
        "for442Excluded": (
            '"for442DecisionSourceUsed": false' in capture
            and '"for442UsedAsDecisionSource": false' in capture
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
        "productionShaderDoesNotContainFor447": "for447" not in shader and "zero_mask_correction" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-447: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

    diff_text = subprocess.run(
        [
            "git",
            "diff",
            "--unified=0",
            "--",
            rel(CAPTURE_TEST),
            rel(DEVICE),
            rel(BUILD),
        ],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    ).stdout
    dangerous_lines = [
        line
        for line in diff_text.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
            or "PipelineKey" in line
            or ("fallbackPolicy" in line and '"fallbackPolicyChanged": false' not in line)
            or "src/main/resources/shaders/aa_stencil_cover.wgsl" in line
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey/shader lines changed: {dangerous_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-447 opt-in flag")
    require("FOR-445" in text and "FOR-443" in text, "report must cite zero-mask sources")
    require("FOR-442" in text and "exclu" in text.lower(), "report must state FOR-442 is excluded")
    require("autres scenes" in text.lower(), "report must state other-scene measurement boundary")
    require(
        "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-appliquer-une-correction-opt-in-basee-sur-les-masques-zero-for-445-for-443"
        in text,
        "report must cite draft memory",
    )


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-447", "linear must be FOR-447")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")

    for field in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "renderingFixAppliedByDefault",
        "for442UsedAsDecisionSource",
        "for447CorrectionDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("for447CorrectionOptInOnly") is True, "correction must be opt-in only")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("for445ZeroMaskPixelCount") == 6, "for445ZeroMaskPixelCount must be 6")
    require(summary.get("for443ZeroMaskPixelCount") == 6, "for443ZeroMaskPixelCount must be 6")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be a decision source")
    changed = summary.get("changedPixels")
    changed_target = summary.get("changedTargetPixels")
    changed_outside = summary.get("changedOutsideTargetPixels")
    require(isinstance(changed, int) and changed >= 0, "changedPixels must be non-negative int")
    require(isinstance(changed_target, int) and 0 <= changed_target <= 6, "changedTargetPixels out of range")
    require(isinstance(changed_outside, int) and changed_outside >= 0, "changedOutsideTargetPixels invalid")
    require(changed == changed_target + changed_outside, "changed pixel counts must add up")
    delta = summary.get("residualDeltaOptInMinusCurrent")
    require(isinstance(delta, int), "residualDeltaOptInMinusCurrent must be int")
    similarity_delta = summary.get("similarityDeltaOptInMinusCurrent")
    require(isinstance(similarity_delta, (int, float)), "similarityDeltaOptInMinusCurrent must be numeric")

    classification = data["classification"]
    if classification == "zero-mask-opt-in-correction-improves-scene":
        require(delta < 0, "improves classification requires negative residual delta")
        require(changed_outside == 0, "improves classification requires no outside-target changes")
    elif classification == "zero-mask-opt-in-correction-regresses-scene":
        require(delta > 0 or changed_outside > 0, "regresses classification requires residual or scope regression")
    elif classification == "zero-mask-opt-in-correction-neutral":
        require(delta == 0 and changed == 0, "neutral classification requires no residual or image change")

    other = data.get("otherSceneEffect")
    require(isinstance(other, dict), "otherSceneEffect must be object")
    require(other.get("otherScenesMeasured") is False, "FOR-447 must not claim other-scene measurement")
    require(
        other.get("changedOutsideTargetPixelsInM60F16") == changed_outside,
        "otherSceneEffect changedOutsideTargetPixelsInM60F16 mismatch",
    )

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six entries")
    require([(p.get("x"), p.get("y")) for p in pixels] == EXPECTED_POINTS, "partialPixels order mismatch")
    for pixel in pixels:
        point = (pixel.get("x"), pixel.get("y"))
        require(pixel.get("drawIndex") == 1, f"{point} drawIndex must be 1")
        require(pixel.get("subdrawOrdinal") == 0, f"{point} subdrawOrdinal must be 0")
        require(pixel.get("subdrawRole") == "inside", f"{point} subdrawRole must be inside")
        require(pixel.get("for445RuntimeIntegerMaskHex") == "0x0000", f"{point} FOR-445 mask mismatch")
        require(pixel.get("for445CoveredCount") == 0, f"{point} FOR-445 covered count mismatch")
        require(pixel.get("for443LowLevelMaskHex") == "0x0000", f"{point} FOR-443 mask mismatch")
        require(pixel.get("for443CoveredCount") == 0, f"{point} FOR-443 covered count mismatch")
        require(pixel.get("for442DecisionSourceUsed") is False, f"{point} must not use FOR-442")
        require(pixel.get("correctionAction") == "discard-inside-fragment", f"{point} correction action mismatch")
        require(pixel.get("modelSource", "").startswith("FOR-447 in-memory WebGPU"), f"{point} model source mismatch")
        for field in ("referenceCpuRgba", "currentWebGpuRgba", "optInWebGpuRgba"):
            value = pixel.get(field)
            require(isinstance(value, list) and len(value) == 4, f"{point} {field} must be RGBA array")
        current_residual = pixel.get("currentResidual")
        opt_in_residual = pixel.get("optInResidual")
        delta_pixel = pixel.get("residualDeltaOptInMinusCurrent")
        require(isinstance(current_residual, int), f"{point} currentResidual must be int")
        require(isinstance(opt_in_residual, int), f"{point} optInResidual must be int")
        require(delta_pixel == opt_in_residual - current_residual, f"{point} residual delta mismatch")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for field, value in non_goals.items():
        require(value is False, f"nonGoalsPreserved.{field} must be false")

    return data


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-447 validation passed: "
        f"classification={data['classification']} "
        f"delta={data['summary']['residualDeltaOptInMinusCurrent']} "
        f"changed={data['summary']['changedPixels']}"
    )


if __name__ == "__main__":
    main()
