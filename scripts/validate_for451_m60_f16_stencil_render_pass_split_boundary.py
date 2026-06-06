#!/usr/bin/env python3
"""Validate FOR-451 M60 F16 stencil render-pass split boundary evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-stencil-render-pass-split-boundary-for451"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-451-m60-f16-stencil-render-pass-split-boundary.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16StencilRenderPassSplitFor451.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "stencil-render-pass-split-direct-readback-available",
    "stencil-render-pass-split-color-boundary-only",
    "stencil-render-pass-split-backend-extension-required",
    "stencil-render-pass-split-unsafe-for-production-ordering",
    "stencil-render-pass-split-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for450_m60_f16_stencil_boundary_audit.py",
    "scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py",
    "reports/wgsl-pipeline/2026-06-06-for-451-m60-f16-stencil-render-pass-split-boundary.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-451 validation failed: {message}")


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
    device = DEVICE.read_text(encoding="utf-8")
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    sink = SINK.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "flagRelayed": FLAG in device and FLAG in build and FLAG in capture,
        "snapshotExposed": "m60F16StencilRenderPassSplitBoundarySnapshot()" in device and sink,
        "dedicatedResources": "m60F16StencilRenderPassSplitBoundaryStorage" in device,
        "splitStoreLoad": "stencilStoreOp = GPUStoreOp.Store" in device
        and "stencilLoadOp = GPULoadOp.Load" in device,
        "writerCalled": "writeM60F16StencilRenderPassSplitBoundaryFor451(" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "productionShaderDoesNotContainFor451": "for451" not in shader.lower()
        and "render_pass_split" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-451: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

    diff_text = subprocess.run(
        [
            "git",
            "diff",
            "--unified=0",
            "origin/master",
            "--",
            rel(DEVICE),
            rel(CAPTURE_TEST),
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
            or "m60F16ZeroMaskCorrectionFor447(true)" in line
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey/FOR-447 lines changed: {dangerous_lines}")


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-451", "linear must be FOR-451")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "stencil-render-pass-split-color-boundary-only",
        "FOR-451 must classify color-only split boundary",
    )

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
    ):
        require(data.get(field) is False, f"{field} must be false")

    source = data.get("sourceAudit")
    require(isinstance(source, dict), "sourceAudit must be object")
    require(source.get("splitRenderPassAppliedOnlyWhenOptIn") is True, "split must be opt-in")
    require(source.get("depthStencilUsage") == "GPUTextureUsage.RenderAttachment", "depth stencil usage mismatch")
    require(source.get("depthStencilUsageHasCopySrc") is False, "depth stencil must not claim CopySrc")
    require(source.get("depthStencilUsageHasTextureBinding") is False, "depth stencil must not claim TextureBinding")
    require(source.get("directStencilReadbackAvailable") is False, "direct stencil readback must remain unavailable")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("predrawColorBoundaryObservedTargetCount") == 6, "predraw must observe six targets")
    require(
        summary.get("afterStencilBeforeCoverColorBoundaryObservedTargetCount") == 6,
        "split boundary must observe six targets",
    )
    require(summary.get("postCoverColorBoundaryObservedTargetCount") == 6, "post-cover must observe six targets")
    require(summary.get("afterStencilBeforeCoverBoundaryAvailable") is True, "split boundary must be available")
    require(summary.get("directStencilReadbackAvailable") is False, "direct stencil readback must be false")
    require(summary.get("requiresBackendApiExtensionForDirectStencil") is True, "backend extension must be required")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partialPixels mismatch")
    for pixel in pixels:
        require(pixel.get("beforeStencilWriteBoundary", {}).get("available") is True, "predraw boundary missing")
        middle = pixel.get("afterStencilBeforeCoverBoundary", {})
        require(middle.get("available") is True, "split boundary missing")
        require(middle.get("splitRenderPassApplied") is True, "splitRenderPassApplied must be true")
        require(middle.get("directStencilReadbackAvailable") is False, "direct stencil must remain false")
        require(pixel.get("afterCoverBeforeFinalReadBoundary", {}).get("available") is True, "post-cover missing")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")
    require("backend extension" in data.get("nextAction", ""), "nextAction must name backend extension")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-451 flag")
    require("FOR-450" in text and "FOR-442" in text, "report must cite source/exclusion tickets")
    require("exclu" in text.lower(), "report must state FOR-442 is excluded")
    require("extension backend" in text.lower(), "report must decide backend-extension follow-up")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-451 validation passed: "
        f"classification={data['classification']} "
        f"split={data['boundarySummary']['afterStencilBeforeCoverColorBoundaryObservedTargetCount']}"
    )


if __name__ == "__main__":
    main()
