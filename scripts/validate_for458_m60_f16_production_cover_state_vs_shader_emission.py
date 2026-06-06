#!/usr/bin/env python3
"""Validate FOR-458 production cover state vs shader emission evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-production-cover-state-vs-shader-emission-for458"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-458-m60-f16-production-cover-state-vs-shader-emission.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ProductionCoverStateVsShaderEmissionFor458.enabled"
EXPECTED_CLASSIFICATION = "production-cover-shader-capture-before-fixed-function-stencil-reject"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "production-cover-shader-capture-before-fixed-function-stencil-reject",
    "production-cover-fixed-function-stencil-state-mismatch",
    "production-cover-render-pass-attachment-state-mismatch",
    "production-cover-replay-order-or-scissor-mismatch",
    "production-cover-state-vs-shader-emission-inconclusive",
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
    "scripts/validate_for457_m60_f16_production_bound_cover_stencil_diagnostic.py",
    "scripts/validate_for458_m60_f16_production_cover_state_vs_shader_emission.py",
    "reports/wgsl-pipeline/2026-06-06-for-458-m60-f16-production-cover-state-vs-shader-emission.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-458 validation failed: {message}")


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
            "M60F16ProductionCoverStateVsShaderEmissionFor458Snapshot" in sink
            and "productionCoverStateVsShaderEmissionFor458Snapshot" in capture
        ),
        "compositeFlag": "m60F16ProductionCoverStateVsShaderEmissionFor458DiagnosticsEnabled" in device,
        "writerCalled": "writeM60F16ProductionCoverStateVsShaderEmissionFor458(" in capture,
        "stateRecorder": "recordM60F16ProductionCoverStateVsShaderEmissionFor458Event(" in device,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "normalRenderingDoesNotUseDiagnosticTexture": '"normalRenderingUsesDiagnosticTexture": false' in capture,
        "productionShaderDoesNotContainFor458": "for458" not in shader.lower()
        and "production_cover_state" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-458: {unexpected}")
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
    require(data.get("linear") == "FOR-458", "linear must be FOR-458")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-458 classification")

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

    audit = data.get("productionCoverStateAudit")
    require(isinstance(audit, dict), "productionCoverStateAudit must be object")
    expected_audit = {
        "productionDepthStencilTextureLabel": "SkWebGpuDevice.depthStencil",
        "productionDepthStencilUsage": "GPUTextureUsage.RenderAttachment",
        "productionDepthStencilUsageHasCopySrc": False,
        "normalRenderingUsesDiagnosticTexture": False,
        "productionWgslChanged": False,
        "shaderCapturePipelineEventCount": 3,
        "productionEquivalentStencilState": True,
        "fixedFunctionStencilRejectExpectedForZero": True,
    }
    for key, expected in expected_audit.items():
        require(audit.get(key) == expected, f"productionCoverStateAudit.{key} mismatch")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    expected_summary = {
        "zeroMaskPixelCount": 6,
        "for453DiagnosticStencilZeroTargetCount": 6,
        "productionBoundFor457MatchingZeroTargetCount": 6,
        "productionCoverInsideExpectedRejectStateCount": 6,
        "productionCoverRenderPassAttachmentMismatchCount": 0,
        "productionCoverReplayOrderOrScissorMismatchCount": 0,
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

        production_bound = pixel.get("productionBoundCoverStencilDiagnosticFor457")
        require(isinstance(production_bound, dict), "productionBoundCoverStencilDiagnosticFor457 must be object")
        require(production_bound.get("readbackAvailable") is True, "FOR-457 readback must be available")
        require(production_bound.get("stencilValue") == 0, "FOR-457 stencilValue must be 0")
        require(production_bound.get("matchesFor453Diagnostic") is True, "FOR-457 must match FOR-453")

        state = pixel.get("productionCoverInsideState")
        require(isinstance(state, dict), "productionCoverInsideState must be object")
        require(state.get("usesProductionDepthStencilAttachment") is True, "production attachment must be used")
        require(state.get("usesDiagnosticDepthStencilAttachment") is False, "diagnostic attachment must not be used")
        require(state.get("stencilReference") == 0, "stencil reference mismatch")
        require(state.get("stencilReadMask") == 255, "stencil read mask mismatch")
        require(state.get("stencilWriteMask") == 255, "stencil write mask mismatch")
        require(state.get("insideCompare") == "GPUCompareFunction.NotEqual", "inside compare mismatch")
        require(state.get("outsideCompare") == "GPUCompareFunction.Equal", "outside compare mismatch")
        require(state.get("insidePipelineEntryPoint") == "fs_inside", "inside entry point mismatch")
        require(state.get("insideDrawOrdinal") == 0, "inside draw ordinal mismatch")
        require(state.get("targetWithinScissor") is True, "target must be in scissor")
        require(state.get("productionEquivalentStencilState") is True, "production-equivalent stencil state required")
        require(
            state.get("fixedFunctionStencilRejectExpectedForZero") is True,
            "zero-stencil reject expectation required",
        )

        shader = pixel.get("coverShaderEmission")
        require(isinstance(shader, dict), "coverShaderEmission must be object")
        require(shader.get("subdrawRole") == "inside", "shader role must be inside")
        require(shader.get("shaderObserved") is True, "shader emission must be observed")
        require(shader.get("shaderCaptureSynthetic") is False, "shader capture must not be synthetic")
        source_color = shader.get("sourceColorSentToBlend")
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
    require("shader-return" in next_action or "shader return" in next_action, "nextAction must name shader-return")
    require("color-attachment-only" in next_action, "nextAction must name color-attachment-only probe")
    for forbidden in (" if ", "otherwise", "sinon"):
        require(forbidden not in next_action.lower(), f"nextAction must not contain conditional route: {forbidden}")
    require("FOR-442" not in data.get("classificationReason", ""), "classification must not depend on FOR-442")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-458 flag")
    require("FOR-453" in text and "FOR-457" in text, "report must cite source diagnostics")
    require("FOR-442" in text and "FOR-447" in text, "report must cite exclusions")
    require("exclu" in lower, "report must state FOR-442 is excluded")
    require("suite unique" in lower, "report must name a single follow-up")
    require("pas un correctif de rendu" in lower or "n'est pas un correctif de rendu" in lower, "report must reject support/fix promotion")
    require("color-attachment-only" in lower, "report must name the color attachment follow-up")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-458 validation passed: "
        f"classification={data['classification']} "
        f"for453Zero={data['boundarySummary']['for453DiagnosticStencilZeroTargetCount']} "
        f"for457Match={data['boundarySummary']['productionBoundFor457MatchingZeroTargetCount']} "
        f"insideEmission={data['boundarySummary']['insideShaderEmissionOnDiagnosticZeroStencilCount']}"
    )


if __name__ == "__main__":
    main()
