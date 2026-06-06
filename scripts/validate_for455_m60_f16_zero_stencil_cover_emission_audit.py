#!/usr/bin/env python3
"""Validate FOR-455 M60 F16 zero-stencil cover/source emission evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-zero-stencil-cover-emission-audit-for455"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-455-m60-f16-zero-stencil-cover-emission-audit.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ZeroStencilCoverEmissionAuditFor455.enabled"
EXPECTED_CLASSIFICATION = "zero-stencil-cover-emission-stencil-reference-or-compare-mismatch"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "zero-stencil-cover-emission-stencil-test-disabled-or-not-bound",
    "zero-stencil-cover-emission-cover-subdraw-selects-outside-zero-region",
    "zero-stencil-cover-emission-stencil-reference-or-compare-mismatch",
    "zero-stencil-cover-emission-cover-geometry-or-scissor-overreach",
    "zero-stencil-cover-emission-shader-source-before-stencil-rejection",
    "zero-stencil-cover-emission-audit-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for454_m60_f16_cover_source_attribution.py",
    "scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py",
    "reports/wgsl-pipeline/2026-06-06-for-455-m60-f16-zero-stencil-cover-emission-audit.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-455 validation failed: {message}")


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
        "compositeFlag": "m60F16ZeroStencilCoverEmissionAuditFor455DiagnosticsEnabled" in device,
        "writerCalled": "writeM60F16ZeroStencilCoverEmissionAuditFor455(" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "productionShaderDoesNotContainFor455": "for455" not in shader.lower()
        and "zero_stencil_cover_emission" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-455: {unexpected}")
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
    require(data.get("linear") == "FOR-455", "linear must be FOR-455")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-455 classification")

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

    pipeline = data.get("coverPipelineAudit")
    require(isinstance(pipeline, dict), "coverPipelineAudit must be object")
    expected_pipeline = {
        "fillType": "kWinding",
        "stencilReference": 0,
        "stencilReadMask": 255,
        "insideCompare": "GPUCompareFunction.NotEqual",
        "outsideCompare": "GPUCompareFunction.Equal",
        "expectedInsideBehaviorForStencilZero": "reject",
        "expectedOutsideBehaviorForStencilZero": "accept",
        "depthStencilAttachmentBound": True,
        "depthStencilFormat": "Depth24PlusStencil8",
        "mainDepthStencilUsage": "GPUTextureUsage.RenderAttachment",
        "stencilLoadOpForCoverPass": "GPULoadOp.Load",
        "stencilStoreOpForCoverPass": "GPUStoreOp.Discard",
    }
    for key, expected in expected_pipeline.items():
        require(pipeline.get(key) == expected, f"coverPipelineAudit.{key} mismatch")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("stencilZeroTargetCount") == 6, "six stencil-zero targets required")
    require(summary.get("insideShaderEmissionOnZeroStencilCount") == 6, "six inside shader emissions required")
    require(summary.get("isolatedColorTargetEmissionCount") == 6, "six isolated color emissions required")
    require(summary.get("postCoverAvailableTargetCount") == 6, "six post-cover samples required")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partialPixels mismatch")
    for pixel in pixels:
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")
        stencil = pixel.get("stencilEvidence")
        require(isinstance(stencil, dict), "stencilEvidence must be object")
        require(stencil.get("readbackAvailable") is True, "stencil readback must be available")
        require(stencil.get("stencilValue") == 0, "stencilValue must be 0")
        require(stencil.get("stencilCovered") is False, "stencilCovered must be false")

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
        scratch = isolated.get("scratchOutputRgbaFloat")
        require(isinstance(scratch, list) and any(abs(float(value)) > 0 for value in scratch[:3]), "isolated color must be non-zero")

        after_cover = pixel.get("afterCover")
        require(isinstance(after_cover, dict), "afterCover must be object")
        require(after_cover.get("available") is True, "afterCover must be available")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")

    next_action = data.get("nextAction", "")
    require("production cover-pass stencil attachment" in next_action, "nextAction must name production stencil attachment")
    require("diagnostic stencil texture" in next_action, "nextAction must name diagnostic stencil texture")
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
    require(FLAG in text, "report must include FOR-455 flag")
    require("FOR-454" in text and "FOR-453" in text, "report must cite source artifacts")
    require("FOR-442" in text and "FOR-447" in text, "report must cite exclusions")
    require("excluded" in lower or "exclu" in lower, "report must state FOR-442 is excluded")
    require("suite unique" in lower or "single next step" in lower, "report must name a single follow-up")
    require("production cover-pass stencil attachment" in lower, "report must name production stencil attachment")
    require("diagnostic stencil texture" in lower, "report must name diagnostic stencil texture")
    require("not a rendering fix" in lower, "report must reject support/fix promotion")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-455 validation passed: "
        f"classification={data['classification']} "
        f"stencilZero={data['boundarySummary']['stencilZeroTargetCount']} "
        f"insideEmission={data['boundarySummary']['insideShaderEmissionOnZeroStencilCount']} "
        f"isolatedEmission={data['boundarySummary']['isolatedColorTargetEmissionCount']}"
    )


if __name__ == "__main__":
    main()
