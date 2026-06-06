#!/usr/bin/env python3
"""Validate FOR-457 production-bound cover stencil diagnostic evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-production-bound-cover-stencil-diagnostic-for457"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-457-m60-f16-production-bound-cover-stencil-diagnostic.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ProductionBoundCoverStencilDiagnosticFor457.enabled"
EXPECTED_CLASSIFICATION = "production-bound-cover-stencil-matches-for453-diagnostic-zero"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "production-bound-cover-stencil-matches-for453-diagnostic-zero",
    "production-bound-cover-stencil-differs-from-for453-diagnostic",
    "production-bound-cover-stencil-resource-unavailable",
    "production-bound-cover-stencil-replay-not-equivalent",
    "production-bound-cover-stencil-diagnostic-inconclusive",
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
    "scripts/validate_for456_m60_f16_production_cover_stencil_vs_diagnostic_texture.py",
    "scripts/validate_for457_m60_f16_production_bound_cover_stencil_diagnostic.py",
    "reports/wgsl-pipeline/2026-06-06-for-457-m60-f16-production-bound-cover-stencil-diagnostic.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-457 validation failed: {message}")


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
    sink = WEBGPU_SINK.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "flagRelayed": FLAG in device and FLAG in build and FLAG in capture,
        "snapshotTransported": (
            "M60F16ProductionBoundCoverStencilDiagnosticFor457Snapshot" in sink
            and "productionBoundCoverStencilDiagnosticFor457Snapshot" in capture
        ),
        "compositeFlag": "m60F16ProductionBoundCoverStencilDiagnosticFor457DiagnosticsEnabled" in device,
        "writerCalled": "writeM60F16ProductionBoundCoverStencilDiagnosticFor457(" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "diagnosticUsesCopySrc": "GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc" in device,
        "normalRenderingDoesNotUseDiagnosticTexture": '"normalRenderingUsesDiagnosticTexture": false' in capture,
        "productionShaderDoesNotContainFor457": "for457" not in shader.lower()
        and "production_bound_cover_stencil" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-457: {unexpected}")
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
            rel(WEBGPU_SINK),
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
    require(data.get("linear") == "FOR-457", "linear must be FOR-457")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-457 classification")

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
        "for447Promoted",
    ):
        require(data.get(field) is False, f"{field} must be false")

    audit = data.get("productionBoundDiagnosticAudit")
    require(isinstance(audit, dict), "productionBoundDiagnosticAudit must be object")
    expected_audit = {
        "bindingMode": "separate-diagnostic-depth-stencil-resource-replayed-with-production-cover-pipelines",
        "productionDepthStencilFormat": "Depth24PlusStencil8",
        "productionDepthStencilTextureLabel": "SkWebGpuDevice.depthStencil",
        "productionDepthStencilUsage": "GPUTextureUsage.RenderAttachment",
        "productionDepthStencilUsageHasCopySrc": False,
        "diagnosticDepthStencilUsage": "GPUTextureUsage.RenderAttachment | GPUTextureUsage.CopySrc",
        "diagnosticTextureUsageHasCopySrc": True,
        "stencilTextureAspect": "GPUTextureAspect.StencilOnly",
        "bytesPerStencilPixel": 1,
        "scratchColorTargetUsed": True,
        "normalRenderingUsesDiagnosticTexture": False,
        "productionDepthStencilTextureModified": False,
        "stencilPassReplayed": True,
        "coverPassReplayedWithProductionPipelines": True,
        "copyFromDiagnosticAttempted": True,
        "copyFromDiagnosticSucceeded": True,
        "for453ComparisonAvailable": True,
    }
    for key, expected in expected_audit.items():
        require(audit.get(key) == expected, f"productionBoundDiagnosticAudit.{key} mismatch")

    pipeline = data.get("coverPipelineAudit")
    require(isinstance(pipeline, dict), "coverPipelineAudit must be object")
    require(pipeline.get("insideCompare") == "GPUCompareFunction.NotEqual", "inside compare mismatch")
    require(pipeline.get("outsideCompare") == "GPUCompareFunction.Equal", "outside compare mismatch")
    require(pipeline.get("stencilReference") == 0, "stencil reference mismatch")
    require(pipeline.get("stencilLoadOpForDiagnosticReplay") == "GPULoadOp.Clear", "diagnostic replay load op mismatch")
    require(pipeline.get("stencilStoreOpForDiagnosticReplay") == "GPUStoreOp.Store", "diagnostic replay store op mismatch")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    expected_summary = {
        "zeroMaskPixelCount": 6,
        "for453DiagnosticStencilZeroTargetCount": 6,
        "productionBoundDiagnosticStencilAvailableTargetCount": 6,
        "for453ComparisonAvailableTargetCount": 6,
        "matchingFor453DiagnosticZeroTargetCount": 6,
        "differingStencilTargetCount": 0,
        "insideShaderEmissionOnDiagnosticZeroStencilCount": 6,
        "isolatedColorTargetEmissionCount": 6,
        "postCoverAvailableTargetCount": 6,
        "for442DecisionSourceUsedCount": 0,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"boundarySummary.{key} mismatch")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partialPixels mismatch")
    for pixel in pixels:
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")
        diagnostic = pixel.get("diagnosticStencilTextureFor453")
        require(isinstance(diagnostic, dict), "diagnosticStencilTextureFor453 must be object")
        require(diagnostic.get("readbackAvailable") is True, "FOR-453 stencil readback must be available")
        require(diagnostic.get("stencilValue") == 0, "FOR-453 stencilValue must be 0")
        require(diagnostic.get("stencilCovered") is False, "FOR-453 stencilCovered must be false")

        production_bound = pixel.get("productionBoundCoverStencilDiagnostic")
        require(isinstance(production_bound, dict), "productionBoundCoverStencilDiagnostic must be object")
        require(production_bound.get("readbackAvailable") is True, "FOR-457 readback must be available")
        require(production_bound.get("stencilValue") == 0, "FOR-457 stencilValue must be 0")
        require(production_bound.get("stencilCovered") is False, "FOR-457 stencilCovered must be false")
        require(production_bound.get("matchesFor453Diagnostic") is True, "FOR-457 must match FOR-453")

        cover = pixel.get("coverSubdraw")
        require(isinstance(cover, dict), "coverSubdraw must be object")
        require(cover.get("subdrawRole") == "inside", "coverSubdraw role must be inside")
        require(cover.get("shaderObserved") is True, "shaderReturn must be observed")
        source_color = cover.get("sourceColorSentToBlend")
        require(isinstance(source_color, list) and any(abs(float(value)) > 0 for value in source_color[:3]), "source color must be non-zero")

        isolated = pixel.get("isolatedColorTarget")
        require(isinstance(isolated, dict), "isolatedColorTarget must be object")
        require(isolated.get("available") is True, "isolatedColorTarget must be available")

        after_cover = pixel.get("afterCover")
        require(isinstance(after_cover, dict), "afterCover must be object")
        require(after_cover.get("available") is True, "afterCover must be available")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")

    next_action = data.get("nextAction", "")
    require("production cover pass emits inside-source color" in next_action, "nextAction must name cover emission")
    require("FOR-453 zero-stencil texture" in next_action, "nextAction must name FOR-453 zero-stencil texture")
    require("FOR-442" not in data.get("classificationReason", ""), "classification must not depend on FOR-442")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-457 flag")
    require("FOR-456" in text and "FOR-453" in text, "report must cite source artifacts")
    require("FOR-442" in text and "FOR-447" in text, "report must cite exclusions")
    require("exclu" in lower, "report must state FOR-442 is excluded")
    require("suite unique" in lower, "report must name a single follow-up")
    require("pas un correctif de rendu" in lower, "report must reject support/fix promotion")
    require("production-bound" in lower or "production" in lower, "report must name production-bound diagnostic scope")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-457 validation passed: "
        f"classification={data['classification']} "
        f"for453Zero={data['boundarySummary']['for453DiagnosticStencilZeroTargetCount']} "
        f"productionBound={data['boundarySummary']['productionBoundDiagnosticStencilAvailableTargetCount']} "
        f"matching={data['boundarySummary']['matchingFor453DiagnosticZeroTargetCount']}"
    )


if __name__ == "__main__":
    main()
