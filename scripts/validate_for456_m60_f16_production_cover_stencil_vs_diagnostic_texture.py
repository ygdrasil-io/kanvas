#!/usr/bin/env python3
"""Validate FOR-456 production cover stencil vs diagnostic texture evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-production-cover-stencil-vs-diagnostic-texture-for456"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-456-m60-f16-production-cover-stencil-vs-diagnostic-texture.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ProductionCoverStencilVsDiagnosticTextureFor456.enabled"
EXPECTED_CLASSIFICATION = "production-cover-stencil-readback-unavailable"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "production-cover-stencil-matches-diagnostic-zero",
    "production-cover-stencil-differs-from-diagnostic",
    "production-cover-stencil-readback-unavailable",
    "production-cover-stencil-compare-state-mismatch",
    "production-cover-stencil-audit-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py",
    "scripts/validate_for456_m60_f16_production_cover_stencil_vs_diagnostic_texture.py",
    "reports/wgsl-pipeline/2026-06-06-for-456-m60-f16-production-cover-stencil-vs-diagnostic-texture.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-456 validation failed: {message}")


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
    build = BUILD.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "flagRelayed": FLAG in device and FLAG in build and FLAG in capture,
        "compositeFlag": "m60F16ProductionCoverStencilVsDiagnosticTextureFor456DiagnosticsEnabled" in device,
        "writerCalled": "writeM60F16ProductionCoverStencilVsDiagnosticTextureFor456(" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "productionTextureNotCopied": '"copyFromProductionAttempted": false' in capture,
        "normalRenderingDoesNotUseDiagnosticTexture": '"normalRenderingUsesDiagnosticTexture": false' in capture,
        "productionShaderDoesNotContainFor456": "for456" not in shader.lower()
        and "production_cover_stencil" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-456: {unexpected}")
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
    require(data.get("linear") == "FOR-456", "linear must be FOR-456")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-456 classification")

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

    audit = data.get("productionCoverStencilAudit")
    require(isinstance(audit, dict), "productionCoverStencilAudit must be object")
    expected_audit = {
        "comparisonMode": "unavailable-production-texture-render-attachment-only",
        "comparisonDirect": False,
        "comparisonCopiedViaTextureOrBuffer": False,
        "comparisonIndirectOnly": True,
        "productionDepthStencilAttachmentBound": True,
        "productionDepthStencilFormat": "Depth24PlusStencil8",
        "productionDepthStencilUsage": "GPUTextureUsage.RenderAttachment",
        "productionDepthStencilUsageHasCopySrc": False,
        "productionDepthStencilUsageHasTextureBinding": False,
        "copyFromProductionAttempted": False,
        "shaderReadFromProductionAttempted": False,
        "diagnosticTextureUsage": "GPUTextureUsage.RenderAttachment | GPUTextureUsage.CopySrc",
        "diagnosticTextureReadbackAvailable": True,
        "productionOrderingChanged": False,
        "normalRenderingUsesDiagnosticTexture": False,
    }
    for key, expected in expected_audit.items():
        require(audit.get(key) == expected, f"productionCoverStencilAudit.{key} mismatch")

    pipeline = data.get("coverPipelineAudit")
    require(isinstance(pipeline, dict), "coverPipelineAudit must be object")
    require(pipeline.get("insideCompare") == "GPUCompareFunction.NotEqual", "inside compare mismatch")
    require(pipeline.get("stencilReference") == 0, "stencil reference mismatch")
    require(pipeline.get("stencilLoadOpForCoverPass") == "GPULoadOp.Load", "cover load op mismatch")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("diagnosticStencilZeroTargetCount") == 6, "six diagnostic zero targets required")
    require(summary.get("productionStencilReadbackAvailableTargetCount") == 0, "production stencil readback must remain unavailable")
    require(summary.get("productionStencilComparisonAvailableTargetCount") == 0, "production stencil comparison must remain unavailable")
    require(summary.get("insideShaderEmissionOnDiagnosticZeroStencilCount") == 6, "six inside shader emissions required")
    require(summary.get("isolatedColorTargetEmissionCount") == 6, "six isolated color emissions required")
    require(summary.get("postCoverAvailableTargetCount") == 6, "six post-cover samples required")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partialPixels mismatch")
    for pixel in pixels:
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")
        diagnostic = pixel.get("diagnosticStencilTexture")
        require(isinstance(diagnostic, dict), "diagnosticStencilTexture must be object")
        require(diagnostic.get("readbackAvailable") is True, "diagnostic stencil readback must be available")
        require(diagnostic.get("stencilValue") == 0, "diagnostic stencilValue must be 0")
        require(diagnostic.get("stencilCovered") is False, "diagnostic stencilCovered must be false")

        production = pixel.get("productionCoverStencilAttachment")
        require(isinstance(production, dict), "productionCoverStencilAttachment must be object")
        require(production.get("readbackAvailable") is False, "production readback must be unavailable")
        require(production.get("comparisonAvailable") is False, "production comparison must be unavailable")
        require(production.get("stencilValue") is None, "production stencil value must be null")
        require(production.get("comparisonMode") == "unavailable-production-texture-render-attachment-only", "comparison mode mismatch")

        cover = pixel.get("coverSubdraw")
        require(isinstance(cover, dict), "coverSubdraw must be object")
        require(cover.get("subdrawRole") == "inside", "coverSubdraw role must be inside")
        require(cover.get("shaderObserved") is True, "shaderReturn must be observed")
        require(cover.get("expectedCompareForRole") == "GPUCompareFunction.NotEqual", "inside compare mismatch")
        require(cover.get("expectedResultForStencilZero") == "reject", "inside zero-stencil behavior mismatch")
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
    require("separate production-bound diagnostic stencil resource" in next_action, "nextAction must name production-bound diagnostic resource")
    require("FOR-453 diagnostic texture" in next_action, "nextAction must name FOR-453 diagnostic texture")
    require("if " not in next_action.lower(), "nextAction must not keep a conditional alternate route")
    require("otherwise" not in next_action.lower(), "nextAction must not keep a second route")
    require("sinon" not in next_action.lower(), "nextAction must not keep a second route")
    require("FOR-442" not in data.get("classificationReason", ""), "classification must not depend on FOR-442")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-456 flag")
    require("FOR-455" in text and "FOR-453" in text, "report must cite source artifacts")
    require("FOR-442" in text and "FOR-447" in text, "report must cite exclusions")
    require("excluded" in lower or "exclu" in lower, "report must state FOR-442 is excluded")
    require("suite unique" in lower or "single next step" in lower, "report must name a single follow-up")
    require("production-bound diagnostic stencil resource" in lower, "report must name production-bound diagnostic resource")
    require("not a rendering fix" in lower, "report must reject support/fix promotion")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-456 validation passed: "
        f"classification={data['classification']} "
        f"diagnosticZero={data['boundarySummary']['diagnosticStencilZeroTargetCount']} "
        f"productionReadback={data['boundarySummary']['productionStencilReadbackAvailableTargetCount']} "
        f"insideEmission={data['boundarySummary']['insideShaderEmissionOnDiagnosticZeroStencilCount']}"
    )


if __name__ == "__main__":
    main()
