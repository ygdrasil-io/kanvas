#!/usr/bin/env python3
"""Validate FOR-459 production cover color attachment acceptance evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-production-cover-color-attachment-acceptance-for459"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-459-m60-f16-production-cover-color-attachment-acceptance.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ProductionCoverColorAttachmentAcceptanceFor459.enabled"
EXPECTED_CLASSIFICATION = "production-cover-color-attachment-rejects-zero-stencil-targets"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "production-cover-color-attachment-rejects-zero-stencil-targets",
    "production-cover-color-attachment-accepts-zero-stencil-targets",
    "production-cover-color-attachment-observation-unavailable",
    "production-cover-color-attachment-acceptance-inconclusive",
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
    "scripts/validate_for458_m60_f16_production_cover_state_vs_shader_emission.py",
    "scripts/validate_for459_m60_f16_production_cover_color_attachment_acceptance.py",
    "reports/wgsl-pipeline/2026-06-06-for-459-m60-f16-production-cover-color-attachment-acceptance.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-459 validation failed: {message}")


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
            "M60F16ProductionCoverColorAttachmentAcceptanceFor459Snapshot" in sink
            and "productionCoverColorAttachmentAcceptanceFor459Snapshot" in capture
        ),
        "compositeFlag": "m60F16ProductionCoverColorAttachmentAcceptanceFor459DiagnosticsEnabled" in device,
        "writerCalled": "writeM60F16ProductionCoverColorAttachmentAcceptanceFor459(" in capture,
        "readbackRecorder": "recordM60F16ProductionCoverColorAttachmentAcceptanceFor459Readbacks(" in device,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "normalRenderingDoesNotUseDiagnosticColorAttachment": (
            '"normalRenderingUsesDiagnosticColorAttachment": false' in capture
        ),
        "productionShaderDoesNotContainFor459": "for459" not in shader.lower()
        and "color_attachment_acceptance" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-459: {unexpected}")
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
    require(data.get("linear") == "FOR-459", "linear must be FOR-459")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-459 classification")

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

    audit = data.get("colorAttachmentAudit")
    require(isinstance(audit, dict), "colorAttachmentAudit must be object")
    expected_audit = {
        "colorAttachmentFormat": "RGBA16Float",
        "colorAttachmentUsage": "GPUTextureUsage.RenderAttachment | GPUTextureUsage.TextureBinding",
        "depthStencilFormat": "Depth24PlusStencil8",
        "normalRenderingUsesDiagnosticColorAttachment": False,
        "normalRenderingUsesDiagnosticDepthStencil": False,
        "productionWgslChanged": False,
        "eventCount": 3,
    }
    for key, expected in expected_audit.items():
        require(audit.get(key) == expected, f"colorAttachmentAudit.{key} mismatch")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    expected_summary = {
        "zeroMaskPixelCount": 6,
        "for453DiagnosticStencilZeroTargetCount": 6,
        "productionBoundFor457MatchingZeroTargetCount": 6,
        "productionCoverInsideExpectedRejectStateCount": 6,
        "insideShaderEmissionOnDiagnosticZeroStencilCount": 6,
        "colorAttachmentBeforeAfterAvailableTargetCount": 6,
        "colorAttachmentChangedTargetCount": 0,
        "colorAttachmentUnchangedTargetCount": 6,
        "for442DecisionSourceUsedCount": 0,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"boundarySummary.{key} mismatch")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list), "partialPixels must be a list")
    require(len(pixels) == 6, "partialPixels must contain six target pixels")
    points = {(pixel.get("x"), pixel.get("y")) for pixel in pixels}
    require(points == EXPECTED_POINTS, f"unexpected target points: {points}")
    for pixel in pixels:
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")
        diagnostic = pixel.get("diagnosticStencilTextureFor453")
        require(isinstance(diagnostic, dict), "diagnosticStencilTextureFor453 must be object")
        require(diagnostic.get("readbackAvailable") is True, "FOR-453 readback must be available")
        require(diagnostic.get("stencilValue") == 0, "FOR-453 stencilValue must be 0")
        require(diagnostic.get("stencilCovered") is False, "FOR-453 stencilCovered must be false")
        production_bound = pixel.get("productionBoundCoverStencilDiagnosticFor457")
        require(isinstance(production_bound, dict), "productionBoundCoverStencilDiagnosticFor457 must be object")
        require(production_bound.get("readbackAvailable") is True, "FOR-457 readback must be available")
        require(production_bound.get("stencilValue") == 0, "FOR-457 stencilValue must be 0")
        require(production_bound.get("matchesFor453Diagnostic") is True, "FOR-457 must match FOR-453")
        state = pixel.get("productionCoverInsideStateFor458")
        require(isinstance(state, dict), "productionCoverInsideStateFor458 must be object")
        require(state.get("insideCompare") == "GPUCompareFunction.NotEqual", "inside compare mismatch")
        require(state.get("stencilReference") == 0, "stencil reference mismatch")
        require(state.get("stencilReadMask") == 255, "stencil read mask mismatch")
        require(state.get("fixedFunctionStencilRejectExpectedForZero") is True, "expected reject must be true")
        color = pixel.get("productionCoverColorAttachmentFor459")
        require(isinstance(color, dict), "productionCoverColorAttachmentFor459 must be object")
        require(
            color.get("renderPassEncoding")
            == "split-diagnostic-stencil-then-production-inside-cover-color-attachment",
            "FOR-459 renderPassEncoding mismatch",
        )
        require(color.get("beforeCoverReadbackEncoded") is True, "before readback must be encoded")
        require(color.get("afterCoverReadbackEncoded") is True, "after readback must be encoded")
        require(color.get("beforeReadbackAvailable") is True, "before readback must be available")
        require(color.get("afterReadbackAvailable") is True, "after readback must be available")
        require(color.get("beforeCoverRgbaFloat") == [0.0, 0.0, 0.0, 0.0], "before color must be clear")
        require(color.get("colorChangedByCover") is False, "color must remain unchanged after inside cover")
        after = color.get("afterCoverRgbaFloat")
        require(after == [0.0, 0.0, 0.0, 0.0], "after color must remain clear")
        require(
            color.get("sampleClassification") == "production-cover-color-attachment-sample-unchanged",
            "sample classification mismatch",
        )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"nonGoalsPreserved.{key} must be false")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report file: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(EXPECTED_CLASSIFICATION in text, "report must include classification")
    require(FLAG in text, "report must include FOR-459 flag")
    require("colorattachmentchangedtargetcount=0" in lower, "report must include changed color count")
    require("colorattachmentunchangedtargetcount=6" in lower, "report must include unchanged color count")
    require("ne corrige pas" in lower or "ne modifie pas le rendu par defaut" in lower, "report must avoid fix claim")
    require("suite unique" in lower, "report must include one next-action section")
    require("shader-capture-before-reject" in lower, "report must name the shader-capture follow-up")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-459 validation passed: "
        f"classification={data['classification']} "
        f"changed={data['boundarySummary']['colorAttachmentChangedTargetCount']}",
    )


if __name__ == "__main__":
    main()
