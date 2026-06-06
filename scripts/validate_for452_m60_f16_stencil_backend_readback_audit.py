#!/usr/bin/env python3
"""Validate FOR-452 M60 F16 stencil backend readback audit evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-stencil-backend-readback-audit-for452"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-452-m60-f16-stencil-backend-readback-audit.md"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16StencilBackendReadbackAuditFor452.enabled"
FOR451_FLAG = "kanvas.webgpu.m60F16StencilRenderPassSplitFor451.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "stencil-backend-direct-readback-available",
    "stencil-backend-copy-aspect-unavailable",
    "stencil-backend-texture-binding-unavailable",
    "stencil-backend-diagnostic-texture-required",
    "stencil-backend-readback-unsafe-for-production-ordering",
    "stencil-backend-readback-audit-inconclusive",
}
EXPECTED_ROUTES = {
    "main-depth-stencil-copy-src",
    "main-depth-stencil-texture-binding",
    "separate-diagnostic-depth-stencil-texture",
    "stencil-aspect-copy-to-buffer",
    "shader-or-compute-stencil-read",
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
    "scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py",
    "scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py",
    "reports/wgsl-pipeline/2026-06-06-for-452-m60-f16-stencil-backend-readback-audit.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-452 validation failed: {message}")


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
        "snapshotExposed": "m60F16StencilBackendReadbackAuditFor452Snapshot()" in device and sink,
        "writerCalled": "writeM60F16StencilBackendReadbackAuditFor452(" in capture,
        "for451BoundaryReused": FOR451_FLAG in capture and FOR451_FLAG in device,
        "mainTextureRenderAttachmentOnly": (
            "label = \"SkWebGpuDevice.depthStencil\"" in device
            and "format = GPUTextureFormat.Depth24PlusStencil8" in device
            and "usage = GPUTextureUsage.RenderAttachment" in device
        ),
        "copyAspectEvidence": (
            "texelCopyTextureInfoAspectAvailable = true" in device
            and "stencilTextureAspectName = \"GPUTextureAspect.StencilOnly\"" in device
        ),
        "directReadbackRefused": (
            "copyFromMainTextureAttempted = false" in device
            and "shaderReadFromMainTextureAttempted = false" in device
            and "directStencilReadbackAvailable = false" in device
        ),
        "diagnosticTextureRequired": "diagnosticTextureRequired = true" in device,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "productionShaderDoesNotContainFor452": "for452" not in shader.lower()
        and "stencil_backend_readback" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-452: {unexpected}")
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
    require(data.get("linear") == "FOR-452", "linear must be FOR-452")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(
        data.get("classification") == "stencil-backend-diagnostic-texture-required",
        "FOR-452 must classify the safe route as diagnostic texture required",
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

    audit = data.get("backendAudit")
    require(isinstance(audit, dict), "backendAudit must be object")
    require(audit.get("enabled") is True, "audit must be enabled")
    require(audit.get("mainDepthStencilUsage") == "GPUTextureUsage.RenderAttachment", "main usage mismatch")
    require(audit.get("mainDepthStencilUsageHasCopySrc") is False, "main texture must not claim CopySrc")
    require(audit.get("mainDepthStencilUsageHasTextureBinding") is False, "main texture must not claim TextureBinding")
    require(audit.get("texelCopyTextureInfoAspectAvailable") is True, "copy texture aspect must be available")
    require(audit.get("stencilTextureAspectAvailable") is True, "stencil aspect must be available")
    require(audit.get("stencilTextureAspectName") == "GPUTextureAspect.StencilOnly", "stencil aspect mismatch")
    require(audit.get("copyFromMainTextureAttempted") is False, "main texture copy must not be attempted")
    require(audit.get("shaderReadFromMainTextureAttempted") is False, "main texture shader read must not be attempted")
    require(audit.get("diagnosticTextureRequired") is True, "diagnostic texture must be required")
    require(audit.get("directStencilReadbackAvailable") is False, "direct stencil readback must be false")
    require(audit.get("productionOrderingChanged") is False, "production ordering must not change")
    routes = audit.get("auditedRoutes")
    require(isinstance(routes, list), "auditedRoutes must be list")
    route_names = {route.get("name") for route in routes if isinstance(route, dict)}
    require(route_names == EXPECTED_ROUTES, f"audited route mismatch: {sorted(route_names)}")
    require(
        any(route.get("candidateForNextTicket") is True for route in routes if isinstance(route, dict)),
        "one route must be a next-ticket candidate",
    )

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
    require(summary.get("stencilValuesAvailable") is False, "stencil values must be unavailable")
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
        require(middle.get("stencilValue") is None, "stencil value must be null")
        require(middle.get("stencilCovered") is None, "stencil covered indicator must be null")
        require(pixel.get("afterCoverBeforeFinalReadBoundary", {}).get("available") is True, "post-cover missing")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")
    next_action = data.get("nextAction", "")
    require("diagnostic-texture" in next_action, "nextAction must name diagnostic texture")
    require("otherwise" not in next_action.lower(), "nextAction must not keep a second conditional route")
    require("production depth/stencil texture" in next_action, "nextAction must forbid production texture readback")
    require("FOR-442" not in data.get("classificationReason", ""), "classification must not depend on FOR-442")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-452 flag")
    require("FOR-451" in text and "FOR-442" in text, "report must cite source/exclusion tickets")
    require("exclu" in text.lower(), "report must state FOR-442 is excluded")
    require("suite unique" in text.lower(), "report must name a single follow-up")
    require("texture diagnostique" in text.lower(), "report must conclude diagnostic texture follow-up")
    require("deux suites" not in text.lower(), "report must not keep multiple follow-ups")
    require("sinon" not in text.lower(), "report must not keep a conditional alternate follow-up")


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-452 validation passed: "
        f"classification={data['classification']} "
        f"split={data['boundarySummary']['afterStencilBeforeCoverColorBoundaryObservedTargetCount']} "
        f"routes={len(data['backendAudit']['auditedRoutes'])}"
    )


if __name__ == "__main__":
    main()
