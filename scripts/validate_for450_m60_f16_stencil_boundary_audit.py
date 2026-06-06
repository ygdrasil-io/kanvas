#!/usr/bin/env python3
"""Validate FOR-450 M60 F16 stencil boundary audit evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-stencil-boundary-audit-for450"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-450-m60-f16-stencil-boundary-audit.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16StencilBoundaryAuditFor450.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
ALLOWED_CLASSIFICATIONS = {
    "stencil-boundary-direct-readback-available",
    "stencil-boundary-color-only-readback-available",
    "stencil-boundary-requires-render-pass-split",
    "stencil-boundary-requires-backend-api-extension",
    "stencil-boundary-audit-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py",
    "scripts/validate_for450_m60_f16_stencil_boundary_audit.py",
    "reports/wgsl-pipeline/2026-06-06-for-450-m60-f16-stencil-boundary-audit.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-450 validation failed: {message}")


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
        "writerCalled": "writeM60F16StencilBoundaryAuditFor450(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "memorySources": (
            "brouillon-ticket-m60-f16-exposer-une-frontiere-de-preuve-stencil-write-sure-apres-for-449"
            in capture
            and "for-449-trace-stencil-subpass-m60-f16-reste-inconclusive-sans-lecture-stencil-directe"
            in capture
        ),
        "for442Excluded": (
            '"for442DecisionSourceUsed": false' in capture
            and '"for442UsedAsDecisionSource": false' in capture
        ),
        "depthStencilRenderAttachmentOnly": (
            "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
            and "label = \"SkWebGpuDevice.depthStencil\"" in device
        ),
        "sameRenderPassStencilThenCover": (
            "setPipeline(stencilWritePipeline)" in device
            and "draw((d.stencilVerts.size / 2).toUInt())" in device
            and "aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Inside)" in device
            and "aaStencilCoverPipelineFor(d.mode, d.fillType, CoverageSide.Outside)" in device
        ),
        "productionShaderDoesNotContainFor450": "for450" not in shader.lower() and "stencil_boundary" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-450: {unexpected}")
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


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-450 flag")
    require("FOR-449" in text and "FOR-442" in text, "report must cite source/exclusion tickets")
    require("exclu" in text.lower(), "report must state FOR-442 is excluded")
    require(
        "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-exposer-une-frontiere-de-preuve-stencil-write-sure-apres-for-449"
        in text,
        "report must cite draft memory",
    )


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-450", "linear must be FOR-450")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "stencil-boundary-requires-render-pass-split",
        "FOR-450 must classify the missing boundary as render-pass split",
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
    require(source.get("depthStencilUsage") == "GPUTextureUsage.RenderAttachment", "depth stencil usage mismatch")
    require(source.get("depthStencilUsageHasCopySrc") is False, "depth stencil must not claim CopySrc")
    require(source.get("depthStencilUsageHasTextureBinding") is False, "depth stencil must not claim TextureBinding")
    require(source.get("stencilWriteAndCoverShareRenderPass") is True, "render pass relation must be true")

    summary = data.get("boundarySummary")
    require(isinstance(summary, dict), "boundarySummary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("predrawColorBoundaryObservedTargetCount") == 6, "predraw color boundary must observe six targets")
    require(summary.get("postCoverColorBoundaryObservedTargetCount") == 6, "post-cover color boundary must observe six targets")
    require(summary.get("afterStencilBeforeCoverBoundaryAvailable") is False, "after-stencil boundary must be unavailable")
    require(summary.get("directStencilReadbackAvailable") is False, "direct stencil readback must be unavailable")
    require(summary.get("requiresRenderPassSplit") is True, "render pass split must be required")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")

    boundaries = data.get("boundaries")
    require(isinstance(boundaries, list) and len(boundaries) == 3, "must describe three boundaries")
    by_name = {boundary.get("name"): boundary for boundary in boundaries}
    require(by_name["before-stencil-write"].get("readbackKind") == "color-only", "before boundary must be color-only")
    require(by_name["after-stencil-before-cover"].get("requiresRenderPassSplit") is True, "middle boundary must require split")
    require(by_name["after-cover-before-final-read"].get("readbackKind") == "color-only", "after boundary must be color-only")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    seen = {(pixel.get("x"), pixel.get("y")) for pixel in pixels}
    require(seen == set(EXPECTED_POINTS), f"partialPixels mismatch: {seen}")
    for pixel in pixels:
        require(pixel.get("beforeStencilWriteBoundary", {}).get("available") is True, "predraw boundary must be available")
        require(
            pixel.get("afterStencilBeforeCoverBoundary", {}).get("requiresRenderPassSplit") is True,
            "middle boundary must require render-pass split",
        )
        require(pixel.get("afterCoverBeforeFinalReadBoundary", {}).get("available") is True, "post-cover boundary must be available")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")
    require("render-pass split" in data.get("nextAction", ""), "nextAction must name render-pass split")
    return data


def require_files() -> None:
    for rel_path in ARTIFACT_FILES:
        path = ROOT / rel_path
        require(path.exists(), f"missing artifact file: {rel_path}")


def main() -> None:
    source_audit()
    require_files()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-450 validation passed: "
        f"classification={data['classification']} "
        f"predraw={data['boundarySummary']['predrawColorBoundaryObservedTargetCount']} "
        f"postcover={data['boundarySummary']['postCoverColorBoundaryObservedTargetCount']}"
    )


if __name__ == "__main__":
    main()
