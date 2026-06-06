#!/usr/bin/env python3
"""Validate FOR-448 M60 F16 zero-mask neutral path trace evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-zero-mask-neutral-path-trace-for448"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-448-m60-f16-zero-mask-neutral-path-trace.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.enabled"
MODE_FLAG = "kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.mode"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
ALLOWED_CLASSIFICATIONS = {
    "zero-mask-neutral-caused-by-wrong-subpass-target",
    "zero-mask-neutral-caused-by-later-pass-overwrite",
    "zero-mask-neutral-caused-by-preexisting-destination",
    "zero-mask-neutral-caused-by-stencil-selection",
    "zero-mask-neutral-path-trace-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/reference-cpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/inside-webgpu-for447.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/inside-webgpu-for447-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/outside-webgpu-for448.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/outside-webgpu-for448-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/both-webgpu-for448.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/both-webgpu-for448-diff.png",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py",
    "scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py",
    "scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py",
    "reports/wgsl-pipeline/2026-06-06-for-448-m60-f16-zero-mask-neutral-path-trace.md",
    "reports/wgsl-pipeline/2026-06-06-for-449-m60-f16-stencil-write-subpass-trace.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/m60-f16-stencil-write-subpass-trace-for449.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/reference-cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/current-webgpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/current-webgpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/inside-webgpu-for447.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/outside-webgpu-for448.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/both-webgpu-for448.png",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-448 validation failed: {message}")


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
        ["git", "diff", "--name-only", "origin/master"],
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
        "writerCalled": "writeM60F16ZeroMaskNeutralPathTraceFor448(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagsRelayed": FLAG in capture and FLAG in build and FLAG in device and MODE_FLAG in capture and MODE_FLAG in build and MODE_FLAG in device,
        "wrapperPresent": "withM60F16ZeroMaskNeutralPathTraceFor448(true, \"outside\")" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "sourceMemories": (
            "brouillon-ticket-m60-f16-tracer-le-passage-reel-apres-correction-zero-mask-neutre-for-447" in capture
            and "for-447-zero-mask-opt-in-correction-is-neutral-on-m60-f16" in capture
        ),
        "zeroMaskPathTraceShaderVariant": (
            "m60_f16_zero_mask_neutral_path_trace_for448_target" in device
            and "experimental://m60-f16-aa-stencil-cover-zero-mask-neutral-path-trace-for448" in device
            and "zeroMaskNeutralPathTraceFor448Mode" in device
            and "discard;" in device
        ),
        "optInOnlyRuntime": (
            "m60F16ZeroMaskNeutralPathTraceFor448Requested" in device
            and "m60F16ZeroMaskNeutralPathTraceFor448Enabled" in device
            and "m60F16ZeroMaskNeutralPathTraceFor448PipelineFor" in device
        ),
        "for442Excluded": (
            '"for442DecisionSourceUsed": false' in capture
            and '"for442UsedAsDecisionSource": false' in capture
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
        "productionShaderDoesNotContainFor448": "for448" not in shader and "zero_mask_neutral_path_trace" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-448: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

    diff_text = subprocess.run(
        [
            "git",
            "diff",
            "--unified=0",
            "origin/master",
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
    require(FLAG in text and MODE_FLAG in text, "report must include FOR-448 flags")
    require("FOR-447" in text and "FOR-445" in text and "FOR-443" in text, "report must cite source tickets")
    require("FOR-442" in text and "exclu" in text.lower(), "report must state FOR-442 is excluded")
    require("refuse" in text.lower() or "refus" in text.lower(), "report must refuse to conclude when inconclusive")
    require(
        "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-le-passage-reel-apres-correction-zero-mask-neutre-for-447"
        in text,
        "report must cite draft memory",
    )


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-448", "linear must be FOR-448")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("modeProperty") == MODE_FLAG, "mode flag mismatch")
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
        "for448TraceDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("for448TraceOptInOnly") is True, "trace must be opt-in only")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be a decision source")
    for mode in ("inside", "outside", "both"):
        require(summary.get(f"{mode}ChangedPixels") == 0, f"{mode} render must be neutral for this artifact")
        require(summary.get(f"{mode}ChangedTargetPixels") == 0, f"{mode} target pixels must be unchanged")
        require(summary.get(f"{mode}ChangedOutsideTargetPixels") == 0, f"{mode} outside-target pixels must be unchanged")
        require(summary.get(f"{mode}TotalResidual") == summary.get("currentTotalResidual"), f"{mode} residual must match current")
    require(summary.get("predrawDstObservedTargetCount") == 6, "predraw destination must be observed for six targets")
    require(summary.get("postPassObservedTargetCount") == 6, "post-pass destination must be observed for six targets")

    subpass = data.get("subpassTrace")
    require(isinstance(subpass, dict), "subpassTrace must be object")
    require(subpass.get("traceKind") == "final-image-variant-differential", "subpassTrace must name differential trace kind")
    require(
        subpass.get("directSubpassFragmentWriteTraceAvailable") is False,
        "subpassTrace must not claim direct fragment-write visibility",
    )
    for field in (
        "insideDiscardChangedTargetPixels",
        "outsideDiscardChangedTargetPixels",
        "bothDiscardChangedTargetPixels",
        "insideDiscardChangedOutsideTargetPixels",
        "outsideDiscardChangedOutsideTargetPixels",
        "bothDiscardChangedOutsideTargetPixels",
    ):
        require(subpass.get(field) == 0, f"subpassTrace.{field} must be zero")

    stencil_write = data.get("stencilWriteTrace")
    require(isinstance(stencil_write, dict), "stencilWriteTrace must be object")
    require(stencil_write.get("available") is False, "stencilWriteTrace must be marked unavailable")
    require(
        stencil_write.get("fallbackClassification") == "zero-mask-neutral-path-trace-inconclusive",
        "missing stencil-write trace must force inconclusive fallback",
    )

    completeness = data.get("traceCompleteness")
    require(isinstance(completeness, dict), "traceCompleteness must be object")
    require(completeness.get("insideOutsideSubpassTraceComplete") is False, "subpass trace must be marked incomplete")
    require(completeness.get("stencilWriteTraceComplete") is False, "stencil-write trace must be marked incomplete")
    require(completeness.get("destinationBeforeAfterTraceComplete") is True, "destination trace must be complete")
    require(completeness.get("finalImageTraceComplete") is True, "final image trace must be complete")

    destination = data.get("destinationTrace")
    require(isinstance(destination, dict), "destinationTrace must be object")
    require(destination.get("predrawEnabled") is True, "predraw trace must be enabled")
    require(destination.get("postPassEnabled") is True, "post-pass trace must be enabled")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six entries")
    require([(p.get("x"), p.get("y")) for p in pixels] == EXPECTED_POINTS, "partialPixels order mismatch")
    for pixel in pixels:
        point = (pixel.get("x"), pixel.get("y"))
        require(pixel.get("for442DecisionSourceUsed") is False, f"{point} must not use FOR-442")
        for field in ("referenceCpuRgba", "currentWebGpuRgba", "insideDiscardFor447Rgba", "outsideDiscardFor448Rgba", "bothDiscardFor448Rgba"):
            value = pixel.get(field)
            require(isinstance(value, list) and len(value) == 4, f"{point} {field} must be RGBA array")
        require(pixel.get("insideChangedPixel") is False, f"{point} inside must be neutral")
        require(pixel.get("outsideChangedPixel") is False, f"{point} outside must be neutral")
        require(pixel.get("bothChangedPixel") is False, f"{point} both must be neutral")
        predraw = pixel.get("destinationBeforeTrace")
        postpass = pixel.get("destinationAfterTrace")
        stencil = pixel.get("stencilSelectionTrace")
        subpass_pixel = pixel.get("subpassTrace")
        stencil_write_pixel = pixel.get("stencilWriteTrace")
        require(isinstance(predraw, dict) and predraw.get("observedCount", 0) > 0, f"{point} predraw missing")
        require(isinstance(postpass, dict) and postpass.get("selectedRgbaFloat") is not None, f"{point} post-pass missing")
        require(isinstance(stencil, dict) and stencil.get("postPassObservedCount", 0) > 0, f"{point} post-pass observation missing")
        require(
            isinstance(subpass_pixel, dict) and subpass_pixel.get("traceKind") == "final-image-variant-differential",
            f"{point} subpass trace must be differential",
        )
        require(
            isinstance(stencil_write_pixel, dict) and stencil_write_pixel.get("available") is False,
            f"{point} stencil-write trace must be marked unavailable",
        )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for field, value in non_goals.items():
        require(value is False, f"nonGoalsPreserved.{field} must be false")

    if data["classification"] == "zero-mask-neutral-path-trace-inconclusive":
        require(
            "Refuse to name a correction candidate" in data.get("nextCorrectionCandidate", ""),
            "inconclusive classification must refuse to name a correction candidate",
        )
    return data


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-448 validation passed: "
        f"classification={data['classification']} "
        f"residual={data['summary']['currentTotalResidual']} "
        f"postPassObserved={data['summary']['postPassObservedTargetCount']}"
    )


if __name__ == "__main__":
    main()
