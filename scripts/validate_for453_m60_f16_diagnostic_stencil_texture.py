#!/usr/bin/env python3
"""Validate FOR-453 M60 F16 diagnostic stencil texture evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-diagnostic-stencil-texture-for453"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-453-m60-f16-diagnostic-stencil-texture.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16DiagnosticStencilTextureFor453.enabled"
FOR452_FLAG = "kanvas.webgpu.m60F16StencilBackendReadbackAuditFor452.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "diagnostic-stencil-texture-direct-copy-available",
    "diagnostic-stencil-texture-shader-read-available",
    "diagnostic-stencil-texture-usage-combination-unsupported",
    "diagnostic-stencil-texture-copy-aspect-rejected",
    "diagnostic-stencil-texture-ordering-unsafe",
    "diagnostic-stencil-texture-audit-inconclusive",
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
    "scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py",
    "scripts/validate_for453_m60_f16_diagnostic_stencil_texture.py",
    "reports/wgsl-pipeline/2026-06-06-for-453-m60-f16-diagnostic-stencil-texture.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-453 validation failed: {message}")


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
        "snapshotExposed": "m60F16DiagnosticStencilTextureFor453Snapshot()" in device and sink,
        "writerCalled": "writeM60F16DiagnosticStencilTextureFor453(" in capture,
        "for452BoundaryStillPresent": FOR452_FLAG in capture and FOR452_FLAG in device,
        "diagnosticTextureCopySrc": (
            "SkWebGpuDevice.m60F16DiagnosticStencilTextureFor453.depthStencil.diagnosticOnly" in device
            and "usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc" in device
        ),
        "stagingCopyDestination": (
            "SkWebGpuDevice.m60F16DiagnosticStencilTextureFor453.staging.diagnosticOnly" in device
            and "usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst" in device
        ),
        "copyStencilAspect": (
            "GPUTextureAspect.StencilOnly" in device
            and "TexelCopyTextureInfo" in device
            and "TexelCopyBufferInfo" in device
            and ".copyTextureToBuffer(" in device
        ),
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "productionShaderDoesNotContainFor453": "for453" not in shader.lower()
        and "diagnostic_stencil_texture" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-453: {unexpected}")
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
    require(data.get("linear") == "FOR-453", "linear must be FOR-453")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "diagnostic-stencil-texture-direct-copy-available",
        "FOR-453 must classify the separate diagnostic texture copy as available",
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

    audit = data.get("diagnosticTextureAudit")
    require(isinstance(audit, dict), "diagnosticTextureAudit must be object")
    require(audit.get("enabled") is True, "audit must be enabled")
    require(
        audit.get("diagnosticTextureUsage") == "GPUTextureUsage.RenderAttachment | GPUTextureUsage.CopySrc",
        "diagnostic texture usage mismatch",
    )
    require(audit.get("diagnosticTextureUsageHasCopySrc") is True, "diagnostic texture must have CopySrc")
    require(
        audit.get("diagnosticTextureUsageHasTextureBinding") is False,
        "diagnostic texture must not claim TextureBinding",
    )
    require(audit.get("mainDepthStencilUsage") == "GPUTextureUsage.RenderAttachment", "main usage mismatch")
    require(audit.get("mainDepthStencilUsageHasCopySrc") is False, "main texture must not claim CopySrc")
    require(audit.get("mainDepthStencilUsageHasTextureBinding") is False, "main texture must not claim TextureBinding")
    require(audit.get("stencilTextureAspectName") == "GPUTextureAspect.StencilOnly", "stencil aspect mismatch")
    require(audit.get("bytesPerStencilPixel") == 1, "bytesPerStencilPixel must be 1")
    require(audit.get("eventCount") == 3, "eventCount must be 3")
    require(audit.get("diagnosticTextureCreatedEventCount") == 3, "diagnostic texture creation count must be 3")
    require(audit.get("copyAttemptedEventCount") == 3, "copy attempted count must be 3")
    require(audit.get("copySucceededEventCount") == 3, "copy succeeded count must be 3")
    events = audit.get("events")
    require(isinstance(events, list) and len(events) == 3, "events must contain three entries")
    for event in events:
        require(event.get("diagnosticTextureCreated") is True, "diagnostic texture must be created")
        require(event.get("stencilPassReplayed") is True, "stencil pass must be replayed")
        require(event.get("copyAttempted") is True, "copy must be attempted")
        require(event.get("copySucceeded") is True, "copy must succeed")
        require(event.get("copyFailureReason") is None, "copyFailureReason must be null")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("directStencilReadbackAvailable") is True, "direct stencil readback must be available")
    require(summary.get("stencilValuesAvailableTargetCount") == 6, "six stencil values must be available")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partialPixels mismatch")
    for pixel in pixels:
        require(pixel.get("targetWithinScissor") is True, "target pixel must be inside scissor")
        require(pixel.get("readbackAttempted") is True, "readback must be attempted")
        require(pixel.get("readbackAvailable") is True, "readback must be available")
        require(pixel.get("stencilValue") == 0, "stencilValue must be 0 for all target pixels")
        require(pixel.get("stencilCovered") is False, "stencilCovered must be false for all target pixels")
        require(
            pixel.get("sampleClassification") == "diagnostic-stencil-texture-direct-copy-sample",
            "sample classification mismatch",
        )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")
    require(non_goals.get("mainDepthStencilTextureModified") is False, "main depth/stencil texture must not change")

    next_action = data.get("nextAction", "")
    lower_next = next_action.lower()
    require("cover-source attribution" in lower_next, "nextAction must name cover-source attribution")
    require("six zero stencil values" in lower_next, "nextAction must use six zero stencil values")
    require("if " not in lower_next, "nextAction must not keep a conditional alternate route")
    require("otherwise" not in lower_next, "nextAction must not keep a second route")
    require("sinon" not in lower_next, "nextAction must not keep a second route")
    require("FOR-442" not in data.get("classificationReason", ""), "classification must not depend on FOR-442")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-453 flag")
    require("FOR-452" in text and "FOR-442" in text, "report must cite source/exclusion tickets")
    require("exclu" in lower, "report must state FOR-442 is excluded")
    require("suite unique" in lower, "report must name a single follow-up")
    require("cover/source" in lower, "report must conclude cover/source attribution follow-up")
    require("stencilvalue=0" in lower, "report must state the zero stencil values")
    require("sinon" not in lower, "report must not keep a conditional alternate follow-up")
    require("deux suites" not in lower, "report must not keep multiple follow-ups")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-453 validation passed: "
        f"classification={data['classification']} "
        f"stencilValues={data['boundarySummary']['stencilValuesAvailableTargetCount']} "
        f"copySucceededEvents={data['diagnosticTextureAudit']['copySucceededEventCount']}"
    )


if __name__ == "__main__":
    main()
